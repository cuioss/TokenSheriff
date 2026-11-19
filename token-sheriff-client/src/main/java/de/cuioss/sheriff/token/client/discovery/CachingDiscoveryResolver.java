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
 * <strong>Failures.</strong> Every runtime exception the delegate throws counts as a failed refresh,
 * not only a {@link TransportException}: it is recorded as {@link DiscoveryCacheStatus.State#FAILED}
 * with its class name as the failure cause, and the minimum refresh interval and the stale policy
 * apply to it exactly as to a transport failure. A cache that has never resolved a document rethrows
 * the recorded exception itself; past the grace period the fail-closed {@link TransportException}
 * carries it as its cause. The same stored exception instance is rethrown to every caller while
 * nothing has been resolved; the cache never mutates it.
 * <p>
 * <strong>Interrupts.</strong> A refresh whose delegate call fails while the calling thread's
 * interrupt flag is set is aborted rather than recorded: the interrupt belongs to the caller, not to
 * the authorization server. No state is written, so neither the status nor the retry backoff changes;
 * the delegate's exception propagates to the interrupted caller with its interrupt flag kept, and
 * every caller that joined the aborted refresh re-evaluates, so one of them starts a fresh refresh.
 * This includes a caller whose interrupt flag is already set when it calls {@link #resolve()}: a
 * failing delegate's refusal is rethrown to it unrecorded, so that thread never writes a failure state
 * or a retry backoff, however often it calls; the next caller without a set flag records the failure.
 * An {@link Error} thrown by the delegate aborts the refresh in the same way, and so does a superseded
 * read: a caller that claims the slot but finds that another caller published a newer state since its
 * read does not call the delegate, and no joined caller is handed that newer state, because a different
 * state is not necessarily a fresh one.
 * <p>
 * <strong>Bounds.</strong> {@code ttl}, {@code gracePeriod} and {@code minRefreshInterval} must each
 * be at most 365 days, and {@code jitter} must be smaller than {@code ttl}; {@link #checkSettings}
 * holds these rules for every caller that validates the settings before constructing a cache. It
 * refuses a value with an {@link InvalidSettingException} that names the offending {@link Setting},
 * so a caller can map the refusal to its own configuration property without parsing the message.
 * <p>
 * <strong>Accepted behaviours.</strong> A caller that joins an in-flight refresh waits for the whole
 * refresh, even when a stale document could be served; this bounds the upstream load to one request
 * per refresh. The bound does not hold for a caller whose interrupt flag is set while the delegate
 * fails: its refusal is rethrown unrecorded on every call, so no failure state and no retry backoff
 * is written, and each of its calls reaches the delegate again.
 * {@link ClientLogMessages.WARN#DISCOVERY_CACHE_EXPIRED_FAILING_CLOSED} can be logged
 * more than once in one failure episode: a caller past the grace period marks the current state as
 * having logged the expiry, but a refresh winner that read the state before that mark derives its
 * failed state from its own read and publishes it with an unconditional {@code state.set(refreshed)},
 * replacing the marked state with one that is not marked, so the next caller past the grace period
 * logs the expiry again. This affects the log volume only and never an answer.
 * <p>
 * <strong>Status.</strong> {@link #status()} and {@link #getLoaderStatus()} read an immutable
 * in-memory snapshot; they never perform I/O and never wait for an in-flight refresh.
 * <p>
 * <strong>Logging.</strong> Every refresh runs through the delegate, which logs
 * {@link ClientLogMessages.INFO#DISCOVERY_RESOLVED} at {@code INFO} on every successful refresh. Of
 * the thirteen refusals {@link DiscoveryResolver#resolve()} throws itself, five log a delegate line
 * first: a non-TLS issuer ({@link ClientLogMessages.WARN#NON_TLS_ISSUER}), a non-success status
 * ({@link ClientLogMessages.WARN#DISCOVERY_STATUS}), an I/O failure and an unparseable document (both
 * {@link ClientLogMessages.ERROR#DISCOVERY_FAILED}) and an issuer mismatch
 * ({@link ClientLogMessages.WARN#ISSUER_MISMATCH}). The other eight are thrown without a delegate
 * line: a malformed issuer URL, a content type other than {@code application/json}, an interrupt, an
 * empty body, a body the size check after the read refuses, a document that parses to nothing, a
 * non-{@code https} endpoint in an {@code https} issuer's document and a missing
 * {@code token_endpoint}. A body the bounded read stops is an I/O failure and is logged as one. The
 * refusals of the shared back-channel transport — a malformed endpoint URL, a path that fails the
 * egress validation, a host the egress policy refuses, an endpoint the HTTP handler refuses and a
 * response the HTTP client refuses as malformed, which {@code BackChannelHttp.send} maps to its fixed
 * malformed-response text — log at most a {@code DEBUG} line of the transport. A request the HTTP
 * client refuses before sending is not such a refusal: its {@link IllegalArgumentException} propagates
 * unchanged. The cache adds a {@code DEBUG} line per recorded
 * failed refresh and per successful refresh, except that the first successful refresh after a
 * failure logs {@link ClientLogMessages.INFO#DISCOVERY_CACHE_RECOVERED} instead of the {@code DEBUG}
 * line. An interrupted refresh is not recorded and writes no cache line at all. Two more records
 * mark a failure episode: entering it while a document is still servable logs
 * {@link ClientLogMessages.WARN#DISCOVERY_REFRESH_FAILED_SERVING_STALE}, and the end of the grace
 * period logs {@link ClientLogMessages.WARN#DISCOVERY_CACHE_EXPIRED_FAILING_CLOSED}. A failure cause
 * is rendered as its exception class name; a message only ever reaches the {@code DEBUG} log through
 * {@link LogSanitizer}, and no response body is ever logged.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public class CachingDiscoveryResolver implements LoadingStatusProvider {

    private static final CuiLogger LOGGER = new CuiLogger(CachingDiscoveryResolver.class);

    /**
     * The upper bound of {@code ttl}, {@code gracePeriod} and {@code minRefreshInterval}. With every
     * duration at most this long, the instant arithmetic of the cache cannot overflow.
     */
    private static final Duration MAX_DURATION = Duration.ofDays(365);

    /**
     * The completion of a claim whose refresh was aborted — by a superseded read, an interrupt, an
     * {@link Error} or a throwing test hook; a joined caller that sees it re-evaluates instead of
     * failing. It is the only exceptional completion a claim ever receives, and a claim only ever
     * completes normally with the state its refresh published.
     */
    private static final RuntimeException REFRESH_ABORTED = new AbortedRefreshMarker();

    private static final Runnable NO_HOOK = () -> {
    };

    private final DiscoveryResolver delegate;
    private final Duration ttl;
    private final Duration gracePeriod;
    private final Duration minRefreshInterval;
    private final Duration jitter;
    private final InstantSource clock;
    private final Runnable afterClaim;

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
     * @throws IllegalArgumentException if {@code ttl} is not positive, {@code gracePeriod} is
     *                                  negative, or either is longer than 365 days
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
     *                                  {@code minRefreshInterval} or {@code jitter} is negative,
     *                                  {@code ttl}, {@code gracePeriod} or {@code minRefreshInterval}
     *                                  is longer than 365 days, or {@code jitter} is not smaller than
     *                                  {@code ttl}
     * @see #checkSettings(Duration, Duration, Duration, Duration)
     */
    public CachingDiscoveryResolver(DiscoveryResolver delegate, Duration ttl, Duration gracePeriod,
            Duration minRefreshInterval, Duration jitter, InstantSource clock) {
        this(delegate, ttl, gracePeriod, minRefreshInterval, jitter, clock, NO_HOOK);
    }

    /**
     * Creates a caching resolver with a hook that runs right after a caller has claimed the refresh
     * slot and before it re-reads the cache state, so a test can publish a competing state in exactly
     * that window.
     * <p>
     * The hook runs inside the claimed region: an exception it throws aborts the refresh like any
     * other abort — no state is written, the claim is released, every joined caller re-evaluates, and
     * the exception propagates to the caller that claimed the slot.
     * <p>
     * The other parameters and every rule on them are those of
     * {@link #CachingDiscoveryResolver(DiscoveryResolver, Duration, Duration, Duration, Duration, InstantSource)}.
     *
     * @param afterClaim the hook run between a successful claim and the re-read of the cache state;
     *                   must not be {@code null}
     */
    CachingDiscoveryResolver(DiscoveryResolver delegate, Duration ttl, Duration gracePeriod,
            Duration minRefreshInterval, Duration jitter, InstantSource clock, Runnable afterClaim) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        checkSettings(ttl, gracePeriod, minRefreshInterval, jitter);
        this.ttl = ttl;
        this.gracePeriod = gracePeriod;
        this.minRefreshInterval = minRefreshInterval;
        this.jitter = jitter;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.afterClaim = Objects.requireNonNull(afterClaim, "afterClaim must not be null");
    }

    /**
     * Checks the cache settings against the rules the constructor enforces, without creating a
     * cache. A caller that reads the settings from configuration can refuse an invalid value before
     * the first resolution.
     * <p>
     * Every refusal of a value is an {@link InvalidSettingException} whose {@link InvalidSettingException#setting()}
     * names the offending {@link Setting}, so a caller can map the refusal to its own setting without
     * reading the message. The rule that {@code jitter} must be smaller than {@code ttl} is attributed
     * to {@link Setting#JITTER}. Every message starts with the offending setting's
     * {@link Setting#parameterName()} — {@code ttl}, {@code gracePeriod}, {@code minRefreshInterval}
     * or {@code jitter} — followed by a space.
     *
     * @param ttl                how long a resolved document is served without a refresh; must be
     *                           positive and at most 365 days
     * @param gracePeriod        how long past its time to live a document is still served while
     *                           refreshes fail; must not be negative and at most 365 days
     * @param minRefreshInterval the minimum time between a failed refresh and the next refresh
     *                           attempt; must not be negative and at most 365 days
     * @param jitter             the upper bound of the random shortening of each freshness window;
     *                           must not be negative and must be smaller than {@code ttl}
     * @throws NullPointerException    if any argument is {@code null}
     * @throws InvalidSettingException if any argument breaks the rule stated for it
     */
    public static void checkSettings(Duration ttl, Duration gracePeriod, Duration minRefreshInterval,
            Duration jitter) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        Objects.requireNonNull(gracePeriod, "gracePeriod must not be null");
        Objects.requireNonNull(minRefreshInterval, "minRefreshInterval must not be null");
        Objects.requireNonNull(jitter, "jitter must not be null");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new InvalidSettingException(Setting.TTL, "ttl must be positive, was " + ttl);
        }
        requireAtMostMax(ttl, Setting.TTL);
        requireNonNegative(gracePeriod, Setting.GRACE_PERIOD);
        requireAtMostMax(gracePeriod, Setting.GRACE_PERIOD);
        requireNonNegative(minRefreshInterval, Setting.MIN_REFRESH_INTERVAL);
        requireAtMostMax(minRefreshInterval, Setting.MIN_REFRESH_INTERVAL);
        requireNonNegative(jitter, Setting.JITTER);
        if (jitter.compareTo(ttl) >= 0) {
            throw new InvalidSettingException(Setting.JITTER,
                    "jitter must be smaller than ttl, was " + jitter + " for ttl " + ttl);
        }
    }

    private static void requireNonNegative(Duration value, Setting setting) {
        if (value.isNegative()) {
            throw new InvalidSettingException(setting,
                    setting.parameterName() + " must not be negative, was " + value);
        }
    }

    private static void requireAtMostMax(Duration value, Setting setting) {
        if (value.compareTo(MAX_DURATION) > 0) {
            throw new InvalidSettingException(setting,
                    setting.parameterName() + " must be at most 365 days, was " + value);
        }
    }

    /**
     * Returns the provider metadata, from the cache while it is fresh and through a refresh once it
     * is stale.
     *
     * @return an independent copy of the cached provider metadata; a mutation of it never reaches
     *         the cache
     * @throws TransportException if the refresh fails and the last resolved document is past
     *         {@code lastSuccess + ttl + gracePeriod} (fail closed, with the recorded failure as the
     *         cause), or if no document has ever been resolved and the delegate's refresh failed with
     *         a {@code TransportException}
     * @throws RuntimeException   if no document has ever been resolved and the delegate's refresh
     *         failed with another runtime exception, which is rethrown as recorded; or if the refresh
     *         was aborted because the calling thread was interrupted, in which case the delegate's
     *         exception propagates and the interrupt flag stays set
     */
    public ProviderMetadata resolve() {
        while (true) {
            CacheState current = state.get();
            Instant now = clock.instant();
            ProviderMetadata fresh = freshCopy(current, now);
            if (fresh != null) {
                return fresh;
            }
            if (current.inRetryBackoff(now, minRefreshInterval)) {
                return serveStaleOrFail(current, now);
            }
            CompletableFuture<CacheState> claim = new CompletableFuture<>();
            CacheState outcome = inFlight.compareAndSet(null, claim)
                    ? refreshAsWinner(current, claim)
                    : joinRunning();
            if (outcome != null) {
                return answer(outcome, clock.instant());
            }
            // No outcome to answer from: the claimed or the joined refresh was aborted — among other
            // causes because its winner found a newer state than the one it read — and wrote no
            // state, or the winner cleared the slot between the failed claim and the read. In every
            // case re-evaluate against the current state, so one caller claims a fresh refresh when
            // one is still needed.
        }
    }

    /**
     * Returns a copy of the cached document while it is fresh.
     *
     * @return an independent copy of the cached document, or {@code null} if there is none or it is
     *         no longer fresh at {@code now}
     */
    private static @Nullable ProviderMetadata freshCopy(CacheState current, Instant now) {
        ProviderMetadata cached = current.metadata;
        Instant fresh = current.freshUntil;
        if (cached != null && fresh != null && now.isBefore(fresh)) {
            return cached.copy();
        }
        return null;
    }

    /**
     * Runs the claimed region of the caller that won the refresh slot: the test hook, the re-read of
     * the cache state and the refresh itself. A normal refresh completes the claim with the state it
     * published; every other exit — a superseded read, a throwing hook, an interrupt or an
     * {@link Error} — aborts it, so every joined caller re-evaluates. Every exit releases the slot.
     *
     * @return the state the refresh published, or {@code null} if the refresh was aborted because
     *         another caller published a newer state between the caller's read and its claim
     */
    private @Nullable CacheState refreshAsWinner(CacheState previous, CompletableFuture<CacheState> claim) {
        boolean completed = false;
        try {
            afterClaim.run();
            if (state.get() != previous) {
                // Another caller published a newer state between the read and the claim: refreshing
                // against the outdated read could replace that newer entry with a failure derived from
                // the old one. A different state is not necessarily a fresh one, so the newer state is
                // not handed to anyone: the claim is aborted, and the caller and every joined caller
                // re-evaluate against the current state.
                return null;
            }
            CacheState refreshed = refresh(previous);
            // Publish the new cache entry before the slot is cleared, so a caller that finds the slot
            // empty always sees the result of the refresh that just ended.
            state.set(refreshed);
            claim.complete(refreshed);
            completed = true;
            return refreshed;
        } finally {
            if (!completed) {
                // refresh() records every runtime failure of the delegate, so only a superseded read,
                // an interrupted refresh, an Error or a throwing test hook gets here. No state is
                // written; the joined callers must not wait forever on the aborted refresh, so they
                // are told to re-evaluate.
                claim.completeExceptionally(REFRESH_ABORTED);
            }
            inFlight.compareAndSet(claim, null);
        }
    }

    /**
     * Waits for the refresh another caller has claimed.
     *
     * @return the state the refresh published, or {@code null} if it was aborted — including a
     *         superseded read — or the winner had already cleared the slot
     */
    private @Nullable CacheState joinRunning() {
        CompletableFuture<CacheState> running = inFlight.get();
        return running == null ? null : join(running);
    }

    /**
     * Waits for the in-flight refresh.
     *
     * @return the state the refresh published, or {@code null} if it was aborted
     */
    private static @Nullable CacheState join(CompletableFuture<CacheState> running) {
        try {
            return running.join();
        } catch (CompletionException e) {
            // A claim only ever completes exceptionally with REFRESH_ABORTED.
            return null;
        }
    }

    private CacheState refresh(CacheState previous) {
        ProviderMetadata metadata;
        try {
            metadata = delegate.resolve();
        }
        // cui-rewrite:disable InvalidExceptionUsageRecipe
        catch (RuntimeException e) {
            // Deliberately broad: DiscoveryResolver.resolve() is public on a non-final class, so a
            // subclass can throw any unchecked exception, and every one of them must be recorded as a
            // failed refresh rather than escape with no state written. An Error is not caught.
            if (Thread.currentThread().isInterrupted()) {
                // The interrupt belongs to the caller, not to the authorization server: abort without
                // recording a failure, and let the exception reach the caller with the flag kept.
                throw e;
            }
            return recordFailure(previous, e);
        }
        Instant success = clock.instant();
        CacheState resolved = CacheState.resolved(metadata, success, success.plus(ttl).minus(randomJitter()));
        if (previous.status == DiscoveryCacheStatus.State.FAILED) {
            LOGGER.info(ClientLogMessages.INFO.DISCOVERY_CACHE_RECOVERED, issuerOf(metadata));
        } else {
            LOGGER.debug("Discovery cache for issuer '%s' refreshed; fresh until %s",
                    issuerOf(metadata), resolved.freshUntil);
        }
        return resolved;
    }

    private CacheState recordFailure(CacheState previous, RuntimeException e) {
        // The delegate reports a refused or failed resolution as a TransportException; any other
        // runtime exception is recorded the same way, so the backoff and the stale policy apply.
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

    /**
     * Answers from a failed state, which always carries the failure of the most recent attempt.
     */
    private ProviderMetadata serveStaleOrFail(CacheState current, Instant now) {
        RuntimeException failure = Objects.requireNonNull(current.lastFailure, "a failed state carries its failure");
        ProviderMetadata cached = current.metadata;
        if (cached == null) {
            // Nothing was ever resolved, so there is nothing to serve: propagate the failure itself.
            throw failure;
        }
        Instant graceEnd = current.graceEnd(ttl, gracePeriod);
        if (now.isBefore(graceEnd)) {
            return cached.copy();
        }
        String causeClass = failure.getClass().getName();
        if (!current.expiryLogged && state.compareAndSet(current, current.withExpiryLogged())) {
            LOGGER.warn(ClientLogMessages.WARN.DISCOVERY_CACHE_EXPIRED_FAILING_CLOSED, issuerOf(cached), causeClass);
        }
        String message = ClientLogMessages.WARN.DISCOVERY_CACHE_EXPIRED_FAILING_CLOSED.format(issuerOf(cached), causeClass);
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
     * The cache settings {@link #checkSettings} validates, each identified by the name of its
     * parameter.
     *
     * @since 1.0
     */
    public enum Setting {

        /** The time to live of a resolved document, parameter {@code ttl}. */
        TTL("ttl"),

        /** The grace period past the time to live, parameter {@code gracePeriod}. */
        GRACE_PERIOD("gracePeriod"),

        /** The minimum interval between a failed refresh and the next one, parameter {@code minRefreshInterval}. */
        MIN_REFRESH_INTERVAL("minRefreshInterval"),

        /** The upper bound of the freshness-window jitter, parameter {@code jitter}. */
        JITTER("jitter");

        private final String parameterName;

        Setting(String parameterName) {
            this.parameterName = parameterName;
        }

        /**
         * Returns the name of the parameter this setting is passed as.
         *
         * @return the parameter name, for example {@code minRefreshInterval}; never {@code null}
         */
        public String parameterName() {
            return parameterName;
        }
    }

    /**
     * The refusal of a cache setting by {@link #checkSettings}: it names the offending
     * {@link Setting} structurally, so a caller can map the refusal to its own configuration property
     * without reading the message. The message starts with the setting's
     * {@link Setting#parameterName()} followed by a space.
     *
     * @since 1.0
     */
    public static final class InvalidSettingException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        private final Setting setting;

        private InvalidSettingException(Setting setting, String message) {
            super(message);
            this.setting = setting;
        }

        /**
         * Returns the setting whose value was refused.
         *
         * @return the offending setting; never {@code null}
         */
        public Setting setting() {
            return setting;
        }
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

    /**
     * The marker a claim is completed with when its refresh was aborted. It never reaches a caller,
     * so it carries no stack trace and is never suppressed into another exception.
     */
    private static final class AbortedRefreshMarker extends RuntimeException {

        private static final long serialVersionUID = 1L;

        AbortedRefreshMarker() {
            super("Discovery refresh was aborted", null, false, false);
        }
    }
}
