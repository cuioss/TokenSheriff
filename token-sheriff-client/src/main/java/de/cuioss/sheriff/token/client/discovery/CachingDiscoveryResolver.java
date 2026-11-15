/*
 * Copyright © 2025-present CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.sheriff.token.client.discovery;

import de.cuioss.sheriff.token.client.internal.ClientLogMessages;
import de.cuioss.sheriff.token.client.internal.LogSanitizer;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.commons.transport.LoadingStatusProvider;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An opt-in caching wrapper around a {@link DiscoveryResolver}: it serves the last resolved
 * discovery document while it is fresh and refreshes it on demand once it is stale.
 * <p>
 * <strong>Freshness.</strong> A successfully resolved document is fresh for the configured time to
 * live, measured from the instant of the successful resolution. With a non-zero jitter, each
 * successful resolution shortens that window by a random amount below the jitter, so clients sharing
 * a TTL do not all refresh at the same instant. There is no TTL default — the caller always states
 * how long a document may be reused.
 * <p>
 * <strong>Refresh.</strong> The first {@link #resolve()} call on a stale (or never resolved) cache
 * refreshes through {@link DiscoveryResolver#resolve()}, so every check the delegate performs — TLS,
 * the issuer match, the endpoint schemes, the media type, the size bound — runs again on every
 * refreshed document. A refused document therefore never replaces the cached one. Concurrent callers
 * share one refresh: the first caller claims the in-flight slot by a single atomic compare-and-set,
 * every other caller joins that refresh's result, and the new cache entry is published before the
 * slot is cleared. No background thread is started; a refresh only ever runs on a caller's thread.
 * <p>
 * <strong>Stale policy.</strong> When a refresh fails, the last successfully resolved document is
 * still served until {@code lastSuccess + ttl + gracePeriod}. Once that instant has passed, a
 * resolution fails closed with a {@link TransportException} stating that the cached metadata expired
 * and naming the class of the last failure. A cache that has never resolved a document has nothing
 * to serve and propagates the refresh failure. After a failed refresh no further refresh is attempted
 * before the minimum refresh interval has elapsed; within that interval the stale policy above
 * answers from the cache.
 * <p>
 * <strong>Status.</strong> {@link #status()} and {@link #getLoaderStatus()} read an immutable
 * in-memory snapshot; they never perform I/O and never wait for an in-flight refresh.
 * <p>
 * <strong>Logging.</strong> A successful refresh logs at {@code DEBUG}; entering a failure episode
 * while a document is still servable logs
 * {@link ClientLogMessages.WARN#DISCOVERY_REFRESH_FAILED_SERVING_STALE}; the end of the grace period
 * logs {@link ClientLogMessages.WARN#DISCOVERY_CACHE_EXPIRED_FAILING_CLOSED}; the first successful
 * refresh after a failure logs {@link ClientLogMessages.INFO#DISCOVERY_CACHE_RECOVERED}. A failure
 * cause is rendered as its exception class name; a message only ever reaches the {@code DEBUG} log
 * through {@link LogSanitizer}, and no response body is ever logged.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public class CachingDiscoveryResolver implements LoadingStatusProvider {

    private static final CuiLogger LOGGER = new CuiLogger(CachingDiscoveryResolver.class);

    private final DiscoveryResolver delegate;
    private final Duration ttl;
    private final Duration gracePeriod;
    private final Duration minRefreshInterval;
    private final Duration jitter;
    private final InstantSource clock;

    private final AtomicReference<CacheState> state = new AtomicReference<>(CacheState.INITIAL);
    private final AtomicReference<@Nullable CompletableFuture<CacheState>> inFlight = new AtomicReference<>();

    /**
     * Creates a caching resolver with no minimum refresh interval and no jitter.
     *
     * @param delegate    the resolver that fetches and validates the discovery document; must not be
     *                    {@code null}
     * @param ttl         how long a resolved document is served without a refresh; must be positive
     * @param gracePeriod how long past its time to live a document is still served while refreshes
     *                    fail; must not be negative
     * @param clock       the source of the current instant; must not be {@code null}
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code ttl} is not positive or {@code gracePeriod} is negative
     */
    public CachingDiscoveryResolver(DiscoveryResolver delegate, Duration ttl, Duration gracePeriod,
            InstantSource clock) {
        this(delegate, ttl, gracePeriod, Duration.ZERO, Duration.ZERO, clock);
    }

    /**
     * Creates a caching resolver.
     *
     * @param delegate           the resolver that fetches and validates the discovery document; must
     *                           not be {@code null}
     * @param ttl                how long a resolved document is served without a refresh; must be
     *                           positive
     * @param gracePeriod        how long past its time to live a document is still served while
     *                           refreshes fail; must not be negative
     * @param minRefreshInterval the minimum time between a failed refresh and the next refresh
     *                           attempt; must not be negative, {@link Duration#ZERO} retries on the
     *                           next call
     * @param jitter             the upper bound of the random amount by which each freshness window is
     *                           shortened; must not be negative and must be smaller than {@code ttl},
     *                           {@link Duration#ZERO} disables it
     * @param clock              the source of the current instant; must not be {@code null}
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code ttl} is not positive, {@code gracePeriod},
     *                                  {@code minRefreshInterval} or {@code jitter} is negative, or
     *                                  {@code jitter} is not smaller than {@code ttl}
     */
    public CachingDiscoveryResolver(DiscoveryResolver delegate, Duration ttl, Duration gracePeriod,
            Duration minRefreshInterval, Duration jitter, InstantSource clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        this.gracePeriod = Objects.requireNonNull(gracePeriod, "gracePeriod must not be null");
        this.minRefreshInterval = Objects.requireNonNull(minRefreshInterval, "minRefreshInterval must not be null");
        this.jitter = Objects.requireNonNull(jitter, "jitter must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive, was " + ttl);
        }
        requireNonNegative(gracePeriod, "gracePeriod");
        requireNonNegative(minRefreshInterval, "minRefreshInterval");
        requireNonNegative(jitter, "jitter");
        if (jitter.compareTo(ttl) >= 0) {
            throw new IllegalArgumentException("jitter must be smaller than ttl, was " + jitter + " for ttl " + ttl);
        }
    }

    private static void requireNonNegative(Duration value, String name) {
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative, was " + value);
        }
    }

    /**
     * Returns the provider metadata, from the cache while it is fresh and through a refresh once it
     * is stale.
     *
     * @return an independent copy of the cached provider metadata; a mutation of it never reaches
     *         the cache
     * @throws TransportException if no document has ever been resolved and the refresh fails (the
     *         delegate's failure is propagated), or if the refresh fails and the last resolved
     *         document is past {@code lastSuccess + ttl + gracePeriod} (fail closed)
     */
    public ProviderMetadata resolve() {
        while (true) {
            CacheState current = state.get();
            Instant now = clock.instant();
            ProviderMetadata cached = current.metadata;
            Instant fresh = current.freshUntil;
            if (cached != null && fresh != null && now.isBefore(fresh)) {
                return cached.copy();
            }
            if (current.inRetryBackoff(now, minRefreshInterval)) {
                return serveStaleOrFail(current, now);
            }
            CompletableFuture<CacheState> claim = new CompletableFuture<>();
            if (inFlight.compareAndSet(null, claim)) {
                CacheState latest = state.get();
                if (latest != current) {
                    // Another caller published a newer state between the read above and the claim:
                    // refreshing against the outdated read could replace that newer entry with a
                    // failure derived from the old one. Hand the newer state to anyone already joined,
                    // release the slot and re-evaluate against it.
                    claim.complete(latest);
                    inFlight.compareAndSet(claim, null);
                    continue;
                }
                CacheState refreshed = refreshAsWinner(current, claim);
                return answer(refreshed, clock.instant());
            }
            CompletableFuture<CacheState> running = inFlight.get();
            if (running != null) {
                return answer(join(running), clock.instant());
            }
            // The winner cleared the slot between the failed claim and the read: its result is
            // already published, so re-evaluate against the new state.
        }
    }

    private CacheState refreshAsWinner(CacheState previous, CompletableFuture<CacheState> claim) {
        boolean completed = false;
        try {
            CacheState refreshed = refresh(previous);
            // Publish the new cache entry before the slot is cleared, so a caller that finds the slot
            // empty always sees the result of the refresh that just ended.
            state.set(refreshed);
            claim.complete(refreshed);
            completed = true;
            return refreshed;
        } finally {
            if (!completed) {
                // refresh() turns every TransportException into a failed state, so only an unexpected
                // exception gets here; the joined callers must not wait forever on an abandoned refresh.
                claim.completeExceptionally(new TransportException("Discovery refresh was aborted"));
            }
            inFlight.compareAndSet(claim, null);
        }
    }

    private static CacheState join(CompletableFuture<CacheState> running) {
        try {
            return running.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }

    private CacheState refresh(CacheState previous) {
        try {
            ProviderMetadata metadata = delegate.resolve();
            Instant success = clock.instant();
            CacheState resolved = CacheState.resolved(metadata, success, success.plus(ttl).minus(randomJitter()));
            if (previous.status == DiscoveryCacheStatus.State.FAILED) {
                LOGGER.info(ClientLogMessages.INFO.DISCOVERY_CACHE_RECOVERED, issuerOf(metadata));
            } else {
                LOGGER.debug("Discovery cache for issuer '%s' refreshed; fresh until %s",
                        issuerOf(metadata), resolved.freshUntil);
            }
            return resolved;
        } catch (TransportException e) {
            // The delegate reports every refused or failed resolution as a TransportException; any
            // other exception is unexpected and propagates instead of being cached as a failure.
            Instant failedAt = clock.instant();
            CacheState failed = previous.failed(e, failedAt);
            LOGGER.debug("Discovery refresh failed with %s: %s", e.getClass().getName(),
                    LogSanitizer.sanitize(e.getMessage()));
            if (previous.status != DiscoveryCacheStatus.State.FAILED && failed.metadata != null
                    && failedAt.isBefore(failed.graceEnd(ttl, gracePeriod))) {
                LOGGER.warn(ClientLogMessages.WARN.DISCOVERY_REFRESH_FAILED_SERVING_STALE,
                        issuerOf(failed.metadata), e.getClass().getName(), failed.graceEnd(ttl, gracePeriod));
            }
            return failed;
        }
    }

    private Duration randomJitter() {
        if (jitter.isZero()) {
            return Duration.ZERO;
        }
        // Spreads refreshes over time; the value has no security relevance.
        long bound = Math.max(1L, jitter.toMillis());
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(bound)); // NOSONAR java:S2245
    }

    private ProviderMetadata answer(CacheState refreshed, Instant now) {
        if (refreshed.status == DiscoveryCacheStatus.State.RESOLVED && refreshed.metadata != null) {
            return refreshed.metadata.copy();
        }
        return serveStaleOrFail(refreshed, now);
    }

    private ProviderMetadata serveStaleOrFail(CacheState current, Instant now) {
        ProviderMetadata cached = current.metadata;
        if (cached == null) {
            // Nothing was ever resolved, so there is nothing to serve: propagate the failure itself.
            RuntimeException failure = current.lastFailure;
            if (failure != null) {
                throw failure;
            }
            throw new TransportException("No discovery metadata has been resolved");
        }
        Instant graceEnd = current.graceEnd(ttl, gracePeriod);
        if (now.isBefore(graceEnd)) {
            return cached.copy();
        }
        String causeClass = current.lastFailureCause == null ? "unknown" : current.lastFailureCause;
        if (!current.expiryLogged && state.compareAndSet(current, current.withExpiryLogged())) {
            LOGGER.warn(ClientLogMessages.WARN.DISCOVERY_CACHE_EXPIRED_FAILING_CLOSED, issuerOf(cached), causeClass);
        }
        String message = ClientLogMessages.WARN.DISCOVERY_CACHE_EXPIRED_FAILING_CLOSED.format(issuerOf(cached), causeClass);
        RuntimeException failure = current.lastFailure;
        if (failure == null) {
            throw new TransportException(message);
        }
        throw new TransportException(message, failure);
    }

    private static @Nullable String issuerOf(ProviderMetadata metadata) {
        return LogSanitizer.sanitize(metadata.issuer);
    }

    /**
     * Returns the current state of the cache, read from memory without any I/O and without waiting
     * for an in-flight refresh.
     *
     * @return the current cache status, never {@code null}
     */
    public DiscoveryCacheStatus status() {
        return state.get().toStatus();
    }

    /**
     * Maps the cache state to a loader status without any I/O: {@link LoaderStatus#UNDEFINED} before
     * the first attempt, {@link LoaderStatus#OK} after a successful one,
     * {@link LoaderStatus#UNDEFINED} after a failed one while a document is still served within the
     * grace period, and {@link LoaderStatus#ERROR} after a failed one with nothing servable.
     *
     * @return the current loader status, never {@code null}
     */
    @Override
    public LoaderStatus getLoaderStatus() {
        CacheState current = state.get();
        return switch (current.status) {
            case NOT_ATTEMPTED -> LoaderStatus.UNDEFINED;
            case RESOLVED -> LoaderStatus.OK;
            case FAILED -> current.metadata != null
                    && clock.instant().isBefore(current.graceEnd(ttl, gracePeriod))
                    ? LoaderStatus.UNDEFINED
                    : LoaderStatus.ERROR;
        };
    }

    /**
     * The immutable cache entry. Every transition creates a new instance, so a reader holding one
     * reference sees a consistent snapshot.
     *
     * @param status           the cache state
     * @param metadata         the last successfully resolved document, owned by the cache and never
     *                         handed out; {@code null} before the first success
     * @param lastSuccess      the instant of the last success; {@code null} before the first success
     * @param freshUntil       the instant until which {@code metadata} is served without a refresh;
     *                         {@code null} before the first success
     * @param lastFailure      the failure of the most recent attempt; {@code null} unless it failed
     * @param lastFailureCause the class name of {@code lastFailure}; {@code null} unless it failed
     * @param lastFailureAt    the instant of the most recent failed attempt; {@code null} unless it
     *                         failed
     * @param expiryLogged     whether the grace-period expiry was already logged in this episode
     */
    private record CacheState(DiscoveryCacheStatus.State status, @Nullable ProviderMetadata metadata,
    @Nullable Instant lastSuccess, @Nullable Instant freshUntil, @Nullable RuntimeException lastFailure,
    @Nullable String lastFailureCause, @Nullable Instant lastFailureAt, boolean expiryLogged) {

        static final CacheState INITIAL = new CacheState(DiscoveryCacheStatus.State.NOT_ATTEMPTED,
                null, null, null, null, null, null, false);

        static CacheState resolved(ProviderMetadata resolvedMetadata, Instant success, Instant fresh) {
            return new CacheState(DiscoveryCacheStatus.State.RESOLVED, resolvedMetadata, success, fresh,
                    null, null, null, false);
        }

        CacheState failed(RuntimeException failure, Instant failedAt) {
            boolean keepExpiryLogged = status == DiscoveryCacheStatus.State.FAILED && expiryLogged;
            return new CacheState(DiscoveryCacheStatus.State.FAILED, metadata, lastSuccess, freshUntil,
                    failure, failure.getClass().getName(), failedAt, keepExpiryLogged);
        }

        CacheState withExpiryLogged() {
            return new CacheState(status, metadata, lastSuccess, freshUntil, lastFailure, lastFailureCause,
                    lastFailureAt, true);
        }

        boolean inRetryBackoff(Instant now, Duration minRefreshInterval) {
            return status == DiscoveryCacheStatus.State.FAILED && lastFailureAt != null
                    && now.isBefore(lastFailureAt.plus(minRefreshInterval));
        }

        Instant graceEnd(Duration ttl, Duration gracePeriod) {
            return lastSuccess == null ? Instant.MIN : lastSuccess.plus(ttl).plus(gracePeriod);
        }

        DiscoveryCacheStatus toStatus() {
            return new DiscoveryCacheStatus(status, lastSuccess, lastFailureCause);
        }
    }
}
