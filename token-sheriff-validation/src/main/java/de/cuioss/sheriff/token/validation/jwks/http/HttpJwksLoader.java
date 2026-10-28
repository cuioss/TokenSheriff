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
package de.cuioss.sheriff.token.validation.jwks.http;

import de.cuioss.http.client.adapter.CacheKeyHeaderFilter;
import de.cuioss.http.client.adapter.ETagAwareHttpAdapter;
import de.cuioss.http.client.adapter.HttpAdapter;
import de.cuioss.http.client.adapter.ResilientHttpAdapter;
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.result.HttpResult;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.EgressPolicy;
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.commons.transport.HttpWellKnownResolver;
import de.cuioss.sheriff.token.commons.transport.Jwks;
import de.cuioss.sheriff.token.commons.transport.JwksHttpContentConverter;
import de.cuioss.sheriff.token.commons.transport.JwksType;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.commons.transport.LoadingStatusProvider;
import de.cuioss.sheriff.token.validation.jwks.JwksLoader;
import de.cuioss.sheriff.token.validation.jwks.key.JWKSKeyLoader;
import de.cuioss.sheriff.token.validation.jwks.key.KeyInfo;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static de.cuioss.sheriff.token.validation.JWTValidationLogMessages.ERROR;
import static de.cuioss.sheriff.token.validation.JWTValidationLogMessages.INFO;
import static de.cuioss.sheriff.token.validation.JWTValidationLogMessages.WARN;

/**
 * JWKS loader that loads from HTTP endpoint with caching and background refresh support.
 * Supports both direct HTTP endpoints and well-known discovery.
 * Uses HttpAdapter composition (ETagAwareHttpAdapter + ResilientHttpAdapter) for bandwidth
 * optimization via ETag caching and resilient retry behavior with optional scheduled background refresh.
 * Background refresh, when enabled, is started after the initial load attempt regardless of its
 * outcome, so a loader whose first load failed recovers on its own.
 * <p>
 * <strong>Status contract:</strong> {@link LoaderStatus#OK} is reported only while a usable key set
 * is installed. A fetch that succeeds at the HTTP level but yields no usable key is not a successful
 * load. A failed initial load or a transient well-known discovery failure leaves the status
 * {@link LoaderStatus#UNDEFINED} when background refresh is enabled (a later refresh cycle that
 * installs usable keys publishes {@code OK}) and {@link LoaderStatus#ERROR} when it is disabled.
 * A security rejection (issuer mismatch, absent advertised issuer, egress block) is a terminal
 * {@code ERROR}. A failed refresh never downgrades an existing {@code OK}, since the installed keys
 * remain usable. After {@link #close()} no refresh still in flight publishes {@code OK}.
 * <p>
 * This implementation follows a clean architecture with:
 * <ul>
 *   <li>Simple constructor with no I/O operations</li>
 *   <li>Async initialization via CompletableFuture</li>
 *   <li>Lock-free status checks using AtomicReference</li>
 *   <li>Key rotation grace period support for Issue #110</li>
 *   <li>Proper separation of concerns</li>
 *   <li>URI-only cache keys for public OAuth endpoints (no header pollution)</li>
 * </ul>
 * <p>
 * Implements Requirement <a href="../../../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-4.5">VALIDATION-4.5: Key Rotation Grace Period</a>
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see HttpJwksLoaderConfig
 * @see <a href="https://github.com/cuioss/TokenSheriff/issues/110">Issue #110: Key rotation grace period</a>
 */
public class HttpJwksLoader implements JwksLoader, LoadingStatusProvider, AutoCloseable {

    private static final CuiLogger LOGGER = new CuiLogger(HttpJwksLoader.class);
    private static final String ISSUER_NOT_CONFIGURED = "not-configured";

    /**
     * Rendering of the advertised issuer in the ISSUER_MISMATCH warning when the discovery document
     * advertises no issuer.
     */
    static final String ISSUER_ABSENT = "<absent>";

    private final HttpJwksLoaderConfig config;

    /**
     * SSRF egress guard applied to the resolved JWKS fetch URL. Normally sourced from the config;
     * a package-private constructor lets tests inject a policy with a deterministic (rebinding)
     * {@link EgressPolicy.HostResolver} to exercise the per-refresh re-validation (C1 follow-up).
     */
    private final EgressPolicy egressPolicy;

    /**
     * The resolved JWKS fetch URI captured at adapter resolution. The egress guard re-resolves and
     * re-checks this URI on <em>every</em> fetch — initial and each scheduled background refresh — so
     * a DNS rebind that flips the host into a blocked range after the initial fetch is rejected
     * rather than silently trusted by the reused adapter.
     */
    private final AtomicReference<URI> jwksFetchUri = new AtomicReference<>();

    private final AtomicReference<LoaderStatus> status = new AtomicReference<>(LoaderStatus.UNDEFINED);
    private final AtomicReference<JWKSKeyLoader> currentKeys = new AtomicReference<>();
    private final ConcurrentLinkedDeque<RetiredKeySet> retiredKeys = new ConcurrentLinkedDeque<>();
    private final AtomicReference<HttpAdapter<Jwks>> httpAdapter = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> refreshTask = new AtomicReference<>();
    private SecurityEventCounter securityEventCounter;
    private final AtomicReference<String> resolvedIssuerIdentifier = new AtomicReference<>();
    private final AtomicReference<Jwks> currentJwksContent = new AtomicReference<>();

    /**
     * Lifecycle monitor. {@link #close()} marks the loader closed and resets it while holding this
     * lock; every key installation, status publication and refresh scheduling holds it too and is
     * skipped once {@link #closed} is set or the {@link #epoch} it started under has passed. This
     * makes "still live" and "publish {@code OK}" one atomic step, so a refresh that is still in
     * flight when the loader closes cannot re-publish {@code OK}. The same lock guards the
     * initialization reservation, so claiming it and clearing {@link #closed} is one step that
     * {@link #close()} cannot interleave with.
     */
    private final ReentrantLock lifecycleLock = new ReentrantLock();

    /**
     * The reserved initialization future, {@code null} before the first
     * {@link #initJWKSLoader(SecurityEventCounter)} and after {@link #close()}. Guarded by
     * {@link #lifecycleLock}.
     */
    private @Nullable CompletableFuture<LoaderStatus> initFuture;

    /**
     * Set by {@link #close()}, cleared when {@link #initJWKSLoader(SecurityEventCounter)} claims a
     * new initialization. Guarded by {@link #lifecycleLock}.
     */
    private boolean closed;

    /**
     * Incremented by every {@link #close()}. An initialization or refresh cycle captures the value it
     * started under and stops installing keys, publishing a status or scheduling a task once the value
     * has moved on, so work that was in flight when the loader closed cannot leak into a later
     * initialization of the same loader. Guarded by {@link #lifecycleLock}.
     */
    private long epoch;

    /**
     * Constructor using HttpJwksLoaderConfig.
     * Simple constructor with no I/O operations - all loading happens asynchronously in initJWKSLoader.
     *
     * @param config the configuration for this loader
     */
    public HttpJwksLoader(HttpJwksLoaderConfig config) {
        this(config, config.getEgressPolicy());
    }

    /**
     * Constructor allowing an explicit {@link EgressPolicy} override.
     * <p>
     * Package-private test seam: production callers use {@link #HttpJwksLoader(HttpJwksLoaderConfig)},
     * which delegates here with the config's policy. Tests inject a policy backed by a deterministic
     * {@link EgressPolicy.HostResolver} to drive the per-refresh DNS-rebinding re-check (C1 follow-up)
     * without depending on real DNS.
     *
     * @param config the configuration for this loader
     * @param egressPolicy the SSRF egress guard to apply on every JWKS fetch
     */
    HttpJwksLoader(HttpJwksLoaderConfig config, EgressPolicy egressPolicy) {
        this.config = config;
        this.egressPolicy = egressPolicy;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Idempotent and race-free: the caller first reserves an uncompleted future by winning the
     * {@code initFuture} compare-and-set, and only the winner starts the asynchronous initialization,
     * whose outcome (including an exceptional one) completes the reserved future. Every concurrent or
     * later caller receives the same future and starts no work, so at most one initial fetch and one
     * background refresh task are started per initialization. Claiming the reservation and clearing a
     * previous {@link #close()} is one step under the lifecycle lock, so a concurrent {@code close()}
     * is ordered strictly before or strictly after the claim and is never silently undone.
     * <p>
     * <strong>Re-initialization after {@link #close()}:</strong> a closed loader can be initialized
     * again, with one exception. When background refresh is enabled and the refresh executor has been
     * shut down, no refresh can be scheduled any more. That is the case after {@code close()} on a
     * loader that owns its executor (the default when the configuration builds one) and for an
     * executor the caller shut down. Such an initialization is rejected: the returned future is
     * already completed exceptionally with an {@link IllegalStateException}, it is not cached, and the
     * loader neither changes its status nor claims the reservation, so it is not left in
     * {@link LoaderStatus#LOADING}. To use a loader again after {@code close()}, configure a
     * caller-supplied executor that stays running, disable background refresh, or create a new loader.
     *
     * @return the initialization future; completes exceptionally when this loader can no longer be
     *         initialized, as described above
     */
    @Override
    public CompletableFuture<LoaderStatus> initJWKSLoader(SecurityEventCounter counter) {
        this.securityEventCounter = counter;

        // Claim before acting (V-16): reserve the future first, start the work only if we won.
        CompletableFuture<LoaderStatus> reserved = new CompletableFuture<>();
        long claimedEpoch;
        lifecycleLock.lock();
        try {
            CompletableFuture<LoaderStatus> existing = initFuture;
            if (existing != null) {
                LOGGER.debug("initJWKSLoader already called, returning existing future (status: %s)", status.get());
                return existing;
            }
            if (config.isBackgroundRefreshEnabled() && config.getScheduledExecutorService().isShutdown()) {
                LOGGER.debug("initJWKSLoader rejected, the refresh executor has been shut down (status: %s)", status.get());
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "HttpJwksLoader cannot be initialized: its background refresh executor has been shut down, "
                                + "for example by close(). Create a new loader or use a caller-supplied executor."));
            }
            initFuture = reserved;
            closed = false;
            claimedEpoch = epoch;
        } finally {
            lifecycleLock.unlock();
        }

        CompletableFuture.supplyAsync(() -> initialize(claimedEpoch)).whenComplete((loaderStatus, failure) -> {
            if (failure != null) {
                reserved.completeExceptionally(failure);
            } else {
                reserved.complete(loaderStatus);
            }
        });
        return reserved;
    }

    /**
     * Initialization body, run asynchronously by the single winner of
     * {@link #initJWKSLoader(SecurityEventCounter)}: resolves the adapter, performs the initial fetch,
     * starts the background refresh when enabled and publishes the resulting status.
     *
     * @param startEpoch the {@link #epoch} this initialization was claimed under; once a
     *                   {@link #close()} moved it on, this initialization publishes and schedules nothing
     * @return the status this initialization reached
     */
    private LoaderStatus initialize(long startEpoch) {
        publishStatus(LoaderStatus.LOADING, startEpoch);

        // Resolve the adapter (may involve well-known discovery)
        AdapterResolution resolution = resolveJWKSAdapter();
        HttpAdapter<Jwks> adapter = resolution.adapter();
        if (adapter == null) {
            return handleAdapterResolutionFailure(resolution, startEpoch);
        }
        httpAdapter.set(adapter);

        // Load JWKS via HttpAdapter (blocking for initialization)
        HttpResult<Jwks> result = adapter.getBlocking();

        // Start background refresh if configured (regardless of initial load status to enable retries)
        boolean backgroundRefreshEnabled = config.isBackgroundRefreshEnabled();
        if (backgroundRefreshEnabled) {
            startBackgroundRefresh(startEpoch);
        }

        if (result.isSuccess()) {
            if (installKeysAndPublishOk(result.getContent().orElse(null), true, startEpoch)) {
                return LoaderStatus.OK;
            }
        } else {
            LOGGER.error(ERROR.JWKS_LOAD_FAILED, result.getErrorMessage().orElse("Unknown error"), getResolvedIssuer());
        }

        // Failed load (HTTP failure or no usable keys): UNDEFINED keeps the loader retrying through
        // the background refresh, ERROR marks a permanent failure when no refresh will run.
        LoaderStatus finalStatus = backgroundRefreshEnabled ? LoaderStatus.UNDEFINED : LoaderStatus.ERROR;
        publishStatus(finalStatus, startEpoch);
        return finalStatus;
    }

    /**
     * Handles the case where JWKS adapter resolution failed (well-known discovery or HTTP handler).
     * <p>
     * A retryable well-known discovery failure with background refresh enabled leaves the status
     * {@link LoaderStatus#UNDEFINED} and schedules the background refresh, which re-attempts the
     * discovery. Every other failure (a security rejection, or any failure without background
     * refresh) is a terminal {@link LoaderStatus#ERROR} and schedules nothing.
     *
     * @param resolution the failed resolution
     * @param startEpoch the {@link #epoch} the calling initialization was claimed under
     * @return the status this initialization reached
     */
    private LoaderStatus handleAdapterResolutionFailure(AdapterResolution resolution, long startEpoch) {
        boolean isWellKnownFailure = config.getWellKnownConfig() != null;
        String errorDetail = isWellKnownFailure
                ? "Well-known discovery failed"
                : "No HTTP handler configured";

        if (isWellKnownFailure) {
            LOGGER.warn(WARN.JWKS_URI_RESOLUTION_FAILED);
        }
        LOGGER.error(ERROR.JWKS_INITIALIZATION_FAILED, errorDetail, getIssuerIdentifier().orElse(ISSUER_NOT_CONFIGURED));

        if (resolution.retryable() && config.isBackgroundRefreshEnabled()) {
            publishStatus(LoaderStatus.UNDEFINED, startEpoch);
            startBackgroundRefresh(startEpoch);
            return LoaderStatus.UNDEFINED;
        }
        publishStatus(LoaderStatus.ERROR, startEpoch);
        return LoaderStatus.ERROR;
    }

    /**
     * Resolves the JWKS adapter based on configuration.
     * For well-known: performs discovery to get JWKS URL and validates issuer
     * For direct: uses the configured HTTP handler
     *
     * @return the resolution: the adapter on success; on failure no adapter, flagged retryable when
     *         the well-known discovery yielded no JWKS URI (a transient condition) and not retryable
     *         for a security rejection (issuer mismatch, absent issuer, egress block)
     */
    private AdapterResolution resolveJWKSAdapter() {
        HttpHandler handler;

        if (config.getWellKnownConfig() != null) {
            // Well-known discovery - the resolver itself uses HttpAdapter for retry!
            HttpWellKnownResolver resolver = config.getWellKnownConfig().createResolver(securityEventCounter);

            // This call may block but we're in async context
            Optional<String> jwksUri = resolver.getJwksUri();
            if (jwksUri.isEmpty()) {
                return AdapterResolution.RETRYABLE_FAILURE;
            }

            // Resolve issuer from well-known document
            Optional<String> discoveredIssuer = resolver.getIssuer();
            String configuredIssuer = config.getIssuerIdentifier();

            Optional<String> issuer = resolveIssuer(discoveredIssuer.orElse(null), configuredIssuer, securityEventCounter);
            if (issuer.isEmpty()) {
                return AdapterResolution.REJECTED;
            }
            resolvedIssuerIdentifier.set(issuer.get());

            // Use overloaded method to create handler for discovered JWKS URL
            handler = config.getHttpHandler(jwksUri.get());
        } else {
            // Direct HTTP configuration - use existing handler from config
            handler = config.getHttpHandler();
            resolvedIssuerIdentifier.set(config.getIssuerIdentifier());
        }

        // SSRF egress guard (C1): resolve the advertised/configured JWKS URL and reject it
        // if it lands on an internal/loopback/metadata address BEFORE any fetch is issued.
        // Resolving here — immediately before the fetch — also closes the DNS-rebinding window.
        // The URI is retained so the guard can be re-applied on every scheduled background
        // refresh (C1 follow-up), not just this initial resolution.
        URI fetchUri = handler.getUri();
        jwksFetchUri.set(fetchUri);
        try {
            egressPolicy.check(fetchUri);
        } catch (TransportException e) {
            LOGGER.error(e, ERROR.JWKS_INITIALIZATION_FAILED, e.getMessage(),
                    getIssuerIdentifier().orElse(ISSUER_NOT_CONFIGURED));
            return AdapterResolution.REJECTED;
        }

        // Create base adapter with ETag caching
        HttpAdapter<Jwks> baseAdapter = ETagAwareHttpAdapter.<Jwks>builder()
                .httpHandler(handler)
                .responseConverter(new JwksHttpContentConverter(config.getParserConfig()))
                .cacheKeyHeaderFilter(CacheKeyHeaderFilter.NONE)  // URI only - public OAuth endpoints
                .build();

        // Wrap with retry behavior
        return new AdapterResolution(ResilientHttpAdapter.wrap(baseAdapter, config.getRetryConfig()), false);
    }

    @Override
    public Optional<KeyInfo> getKeyInfo(String kid) {
        // Check current keys
        JWKSKeyLoader current = currentKeys.get();
        if (current != null) {
            Optional<KeyInfo> key = current.getKeyInfo(kid);
            if (key.isPresent()) return key;
        }

        // Check retired keys (grace period for Issue #110)
        // Skip checking retired keys if grace period is zero
        if (!config.getKeyRotationGracePeriod().isZero()) {
            Instant cutoff = Instant.now().minus(config.getKeyRotationGracePeriod());
            for (RetiredKeySet retired : retiredKeys) {
                if (retired.retiredAt.isAfter(cutoff)) {
                    Optional<KeyInfo> key = retired.loader.getKeyInfo(kid);
                    if (key.isPresent()) return key;
                }
            }
        }

        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Reads the active key set exactly once, so the snapshot cannot mix key ids from before and
     * after a concurrent key rotation. Retired key sets that {@link #getKeyInfo(String)} still
     * consults during the grace period are not included.
     *
     * @return an immutable snapshot of the active key set's key ids, empty before the first
     *         successful load
     */
    @Override
    public Set<String> getKeyIds() {
        JWKSKeyLoader current = currentKeys.get();
        if (current == null) {
            return Set.of();
        }
        return current.getKeyIds();
    }

    @Override
    public LoaderStatus getLoaderStatus() {
        return status.get(); // Pure atomic read
    }

    @Override
    public JwksType getJwksType() {
        return config.getJwksType(); // Delegate to config
    }

    @Override
    public Optional<String> getIssuerIdentifier() {
        // Return resolved issuer if available, otherwise fall back to config
        String resolved = resolvedIssuerIdentifier.get();
        if (resolved != null) {
            return Optional.of(resolved);
        }
        return Optional.ofNullable(config.getIssuerIdentifier());
    }

    /**
     * Installs the keys of a freshly fetched JWKS and publishes {@link LoaderStatus#OK} when a usable
     * key set is installed, as one atomic step under {@link #lifecycleLock}. Nothing is installed or
     * published once the loader is closed or the caller's epoch has passed.
     *
     * @param newJwks          the fetched JWKS, {@code null} when the response carried no content
     *                         (counts as not usable)
     * @param logInitialLoad   whether a successful outcome is logged as the initial load
     * @param startEpoch       the {@link #epoch} the calling initialization or refresh cycle started under
     * @return {@code true} if a usable key set is installed and {@code OK} was published
     */
    private boolean installKeysAndPublishOk(@Nullable Jwks newJwks, boolean logInitialLoad, long startEpoch) {
        lifecycleLock.lock();
        try {
            if (!isLive(startEpoch) || newJwks == null || !updateKeys(newJwks)) {
                return false;
            }
            if (logInitialLoad) {
                LOGGER.info(INFO.JWKS_LOADED, getResolvedIssuer());
            }
            status.set(LoaderStatus.OK);
            return true;
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Publishes a status unless the loader has been closed or the caller's epoch has passed, so a
     * closed loader stays {@link LoaderStatus#UNDEFINED}.
     *
     * @param newStatus  the status to publish
     * @param startEpoch the {@link #epoch} the calling initialization or refresh cycle started under
     */
    private void publishStatus(LoaderStatus newStatus, long startEpoch) {
        lifecycleLock.lock();
        try {
            if (isLive(startEpoch)) {
                status.set(newStatus);
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Reads the current {@link #epoch}, the value a refresh cycle captures when it starts.
     *
     * @return the current epoch
     */
    private long currentEpoch() {
        lifecycleLock.lock();
        try {
            return epoch;
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Whether work that started under {@code startEpoch} may still change the loader: the loader is
     * not closed and no {@link #close()} ran since the work started. The caller must hold
     * {@link #lifecycleLock}.
     *
     * @param startEpoch the {@link #epoch} the work started under
     * @return {@code true} if the work may still install keys, publish a status or schedule a task
     */
    private boolean isLive(long startEpoch) {
        return !closed && startEpoch == epoch;
    }

    /**
     * Installs the key set of the given JWKS if it is usable.
     * <p>
     * A JWKS whose content equals the installed one is not re-parsed. A JWKS that yields no usable key
     * never replaces the installed key set (L9): the current keys stay active and the content
     * reference is left unchanged so the next refresh retries.
     *
     * @param newJwks the fetched JWKS
     * @return {@code true} if a usable key set is installed after this call — either installed by
     *         this call or, for unchanged content, already installed; {@code false} if the JWKS
     *         yielded no usable key
     */
    private boolean updateKeys(Jwks newJwks) {
        // Check if content has actually changed (Issue #110)
        Jwks currentJwks = currentJwksContent.get();
        if (currentJwks != null && currentJwks.equals(newJwks)) {
            LOGGER.debug("JWKS content unchanged, skipping key rotation");
            return currentKeys.get() != null;
        }

        JWKSKeyLoader newLoader = JWKSKeyLoader.builder()
                .jwksContent(newJwks)
                .jwksType(config.getJwksType())
                .build();
        newLoader.initJWKSLoader(securityEventCounter);

        // Never retire a good key set for an empty/ERROR refresh (L9). If the freshly-loaded key
        // set produced no usable keys, keep the current keys and leave the content reference
        // unchanged so the next scheduled refresh retries against the same content.
        if (newLoader.getLoaderStatus() != LoaderStatus.OK) {
            LOGGER.warn(WARN.JWKS_REFRESH_RETURNED_NO_KEYS, getResolvedIssuer());
            return false;
        }

        // Content has changed and the new key set is usable — commit the reference.
        currentJwksContent.set(newJwks);

        // Use a single timestamp to avoid timing issues (Issue #110)
        Instant now = Instant.now();

        // Retire old keys with grace period
        JWKSKeyLoader oldLoader = currentKeys.getAndSet(newLoader);
        if (oldLoader != null && !config.getKeyRotationGracePeriod().isZero()) {
            retiredKeys.addFirst(new RetiredKeySet(oldLoader, now));

            // Clean up expired retired keys
            Instant cutoff = now.minus(config.getKeyRotationGracePeriod());
            retiredKeys.removeIf(retired -> retired.retiredAt.isBefore(cutoff));

            // Keep max N retired sets
            while (retiredKeys.size() > config.getMaxRetiredKeySets()) {
                retiredKeys.removeLast();
            }
        }

        // Log keys update
        LOGGER.info(INFO.JWKS_KEYS_UPDATED, status.get());
        return true;
    }

    /**
     * Schedules the background refresh, unless the loader has been closed in the meantime (an
     * initialization still in flight when {@link #close()} ran must not leave a running task behind,
     * not even when the loader has been initialized again since).
     *
     * @param startEpoch the {@link #epoch} the calling initialization was claimed under
     */
    private void startBackgroundRefresh(long startEpoch) {
        lifecycleLock.lock();
        try {
            if (!isLive(startEpoch)) {
                return;
            }
            refreshTask.set(config.getScheduledExecutorService().scheduleAtFixedRate(
                    this::performBackgroundRefresh,
                    config.getRefreshIntervalSeconds(),
                    config.getRefreshIntervalSeconds(),
                    TimeUnit.SECONDS));
        } finally {
            lifecycleLock.unlock();
        }

        LOGGER.info(INFO.JWKS_BACKGROUND_REFRESH_STARTED, config.getRefreshIntervalSeconds());
    }

    /**
     * Re-validates SSRF egress against the supplied fetch URI for one background refresh cycle.
     *
     * @param fetchUri the resolved JWKS fetch URI to re-check
     * @return {@code true} if the egress check passed and the caller should proceed with the fetch;
     *         {@code false} if the check failed (already logged) and the caller should return early
     */
    private boolean egressCheckPassed(URI fetchUri) {
        try {
            egressPolicy.check(fetchUri);
            return true;
        } catch (TransportException e) {
            LOGGER.warn(e, WARN.BACKGROUND_REFRESH_FAILED, e.getMessage());
            return false;
        }
    }

    /**
     * Executes one background refresh cycle: re-validates SSRF egress against the resolved JWKS URI,
     * fetches the JWKS, and rotates keys on success.
     * <p>
     * Status contract: a cycle that installs a usable key set publishes {@link LoaderStatus#OK}, which
     * lets a loader whose first load failed recover. A failed cycle never downgrades an existing
     * {@code OK}, since the installed keys remain usable. Once the loader is closed, a cycle that is
     * still in flight neither installs keys nor publishes {@code OK}.
     * <p>
     * When no adapter is available because the initial well-known discovery failed transiently, the
     * cycle re-attempts the discovery and, on success, continues with the fetch in the same cycle. A
     * security rejection on that re-attempt is terminal: the status becomes {@link LoaderStatus#ERROR}
     * and the refresh task is cancelled.
     * <p>
     * The egress re-check (C1 follow-up) is the crux: the cached {@link HttpAdapter} is reused across
     * every scheduled refresh, so without re-resolving and re-checking the fetch URI here, a DNS answer
     * that rebinds an initially-public host into an internal/loopback/metadata range after the first
     * fetch would be silently trusted. Re-running {@link EgressPolicy#check(URI)} on each cycle resolves
     * the host fresh and blocks the rebound target before any bytes are fetched.
     * <p>
     * Package-private so the per-refresh re-validation can be driven deterministically in tests without
     * relying on the {@link java.util.concurrent.ScheduledExecutorService} timing.
     */
    void performBackgroundRefresh() {
        long cycleEpoch = currentEpoch();
        try {
            HttpAdapter<Jwks> adapter = httpAdapter.get();
            if (adapter == null) {
                adapter = reResolveAdapter(cycleEpoch);
                if (adapter == null) {
                    return;
                }
            }

            // Re-validate egress on every refresh (C1 follow-up): reject a rebound host before fetching.
            URI fetchUri = jwksFetchUri.get();
            if (fetchUri != null && !egressCheckPassed(fetchUri)) {
                return;
            }

            // Blocking load in background thread
            HttpResult<Jwks> result = adapter.getBlocking();

            if (result.isSuccess()) {
                installKeysAndPublishOk(result.getContent().orElse(null), false, cycleEpoch);
            } else {
                // Handle error (network, server, etc.)
                String statusDesc = result.getErrorMessage()
                        .orElseGet(() -> "HTTP status: " + result.getHttpStatus().map(String::valueOf).orElse("N/A"));
                LOGGER.warn(WARN.BACKGROUND_REFRESH_FAILED, statusDesc);
            }
        } catch (IllegalArgumentException e) {
            // JSON parsing or validation errors
            LOGGER.warn(WARN.BACKGROUND_REFRESH_PARSE_ERROR, e.getMessage(), getResolvedIssuer());
        } catch (IllegalStateException e) {
            // State errors (e.g., from orElseThrow when issuer not resolved), includes CancellationException
            LOGGER.warn(WARN.BACKGROUND_REFRESH_FAILED, e.getMessage());
        }
        // cui-rewrite:disable InvalidExceptionUsageRecipe
        // Intentional catch-all safety net: ScheduledExecutorService silently cancels the task
        // on ANY uncaught exception (including NPE, ClassCastException, etc.) with no recovery.
        // Without this, a single unexpected failure permanently kills JWKS background refresh,
        // causing keys to never rotate — a silent availability degradation.
        catch (Exception e) {
            LOGGER.warn(e, WARN.BACKGROUND_REFRESH_FAILED, e.getMessage());
        }
    }

    /**
     * Re-attempts the adapter resolution for a refresh cycle that found no adapter.
     * <p>
     * Only a well-known configuration can lack an adapter after initialization (its discovery failed
     * transiently); in direct mode the cycle is skipped as before. A retryable failure is logged and
     * leaves the status unchanged for the next cycle. A security rejection is terminal: the status
     * becomes {@link LoaderStatus#ERROR} and the refresh task is cancelled, so the rejection is not
     * re-evaluated (and not re-counted) on every cycle.
     *
     * @param cycleEpoch the {@link #epoch} the calling refresh cycle started under
     * @return the resolved adapter, already stored for later cycles, or {@code null} if this cycle has
     *         nothing to fetch from
     */
    private @Nullable HttpAdapter<Jwks> reResolveAdapter(long cycleEpoch) {
        if (config.getWellKnownConfig() == null) {
            LOGGER.warn(WARN.BACKGROUND_REFRESH_NO_HANDLER);
            return null;
        }
        AdapterResolution resolution = resolveJWKSAdapter();
        HttpAdapter<Jwks> adapter = resolution.adapter();
        if (adapter != null) {
            httpAdapter.set(adapter);
            return adapter;
        }
        LOGGER.warn(WARN.JWKS_URI_RESOLUTION_FAILED);
        if (!resolution.retryable()) {
            lifecycleLock.lock();
            try {
                // A cycle from before a close() must not cancel the task of a later initialization.
                if (isLive(cycleEpoch)) {
                    status.set(LoaderStatus.ERROR);
                    ScheduledFuture<?> task = refreshTask.get();
                    if (task != null) {
                        task.cancel(false);
                    }
                }
            } finally {
                lifecycleLock.unlock();
            }
        }
        return null;
    }

    /**
     * Stops the background refresh, releases a loader-owned executor and resets the loader to
     * {@link LoaderStatus#UNDEFINED} with no installed keys.
     * <p>
     * The loader is marked closed and reset under the same lifecycle lock that guards every key
     * installation and {@code OK} publication, so a refresh cycle still in flight cannot re-publish
     * {@code OK} afterwards. Resetting the initialization reservation is part of the same critical
     * section, so a concurrent {@link #initJWKSLoader(SecurityEventCounter)} is ordered strictly
     * before or after the whole close and an initialization still in flight is invalidated by the
     * epoch bump. A later {@code initJWKSLoader} starts a fresh initialization, unless the refresh
     * executor released here prevents it (see {@link #initJWKSLoader(SecurityEventCounter)}).
     */
    @Override
    public void close() {
        lifecycleLock.lock();
        try {
            closed = true;
            epoch++;
            ScheduledFuture<?> task = refreshTask.get();
            if (task != null) {
                task.cancel(false);
            }
            if (config.isOwnsExecutor() && config.getScheduledExecutorService() != null) {
                config.getScheduledExecutorService().shutdownNow();
            }
            currentKeys.set(null);
            retiredKeys.clear();
            httpAdapter.set(null);
            jwksFetchUri.set(null);
            currentJwksContent.set(null);
            initFuture = null;
            status.set(LoaderStatus.UNDEFINED);
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Checks if background refresh is enabled and running.
     * Package-private for testing purposes only.
     *
     * @return true if background refresh is active, false otherwise
     */
    boolean isBackgroundRefreshActive() {
        ScheduledFuture<?> task = refreshTask.get();
        return task != null && !task.isCancelled() && !task.isDone();
    }

    /**
     * Gets the resolved issuer identifier.
     * This method is guaranteed to return a non-null value after resolveJWKSAdapter() succeeds.
     *
     * @return the resolved issuer identifier
     */
    private String getResolvedIssuer() {
        return resolvedIssuerIdentifier.get();
    }

    /**
     * Resolves the issuer identifier from discovered and configured sources.
     * Implements the issuer resolution logic with precedence rules.
     * <p>
     * Design Decision (M7): The configured issuer is the operator's trust anchor. When a configured
     * issuer is present, the advertised (discovered) issuer must be present and must match it. An
     * advertised issuer that does not match, or an absent advertised issuer, is a
     * <em>hard discovery failure</em> — this method returns empty,
     * {@link #resolveJWKSAdapter()} aborts, and the advertised {@code jwks_uri} is never fetched.
     * Gating here, before the advertised URL is fetched, closes the SSRF-relevant window (COMMONS-5):
     * a tampered discovery document cannot steer the loader to fetch key material from an
     * attacker-chosen endpoint under the authority of an issuer we do not trust. A mismatch is
     * audited (WARN log + ISSUER_MISMATCH counter). A discovery document that omits {@code issuer}
     * is already rejected by the well-known document parser before this method is reached, so that
     * case produces no ISSUER_MISMATCH event; the absent-issuer branch here is a defensive gate
     * (reached directly in unit tests) that logs the advertised value as {@code <absent>} and
     * counts ISSUER_MISMATCH. Comparison tolerates a trailing-slash difference, which is a common,
     * benign formatting variance in real IdP deployments (e.g., Keycloak) and not a genuine issuer
     * mismatch.
     * <p>
     * Without a configured issuer (standalone use of the loader) the advertised issuer is adopted
     * as-is, and the absence of both is a failure.
     * <p>
     * Package-private so the gate can be exercised directly in tests.
     *
     * @param discoveredIssuer the issuer from well-known discovery (nullable)
     * @param configuredIssuer the configured issuer from configuration (nullable)
     * @param counter          the security event counter that records an ISSUER_MISMATCH, never null
     * @return Optional containing the resolved issuer, or empty if no issuer is available, the
     *         advertised issuer is absent while an issuer is configured, or the advertised issuer does
     *         not match the configured trust anchor
     */
    static Optional<String> resolveIssuer(String discoveredIssuer, String configuredIssuer,
            SecurityEventCounter counter) {
        // Case 1: No issuer at all - fail
        if (discoveredIssuer == null && configuredIssuer == null) {
            LOGGER.error(ERROR.JWKS_INITIALIZATION_FAILED, "No issuer identifier found", "well-known");
            return Optional.empty();
        }

        // Case 2: Configured issuer is the trust anchor — an absent or mismatching advertised
        // issuer is a hard failure (M7)
        if (configuredIssuer != null) {
            if (discoveredIssuer == null || !issuersMatch(configuredIssuer, discoveredIssuer)) {
                LOGGER.warn(WARN.ISSUER_MISMATCH, configuredIssuer,
                        discoveredIssuer == null ? ISSUER_ABSENT : discoveredIssuer);
                counter.increment(SecurityEventCounter.EventType.ISSUER_MISMATCH);
                return Optional.empty();
            }
            return Optional.of(configuredIssuer);
        }

        // Case 3: Use discovered issuer
        return Optional.of(discoveredIssuer);
    }

    /**
     * Compares a configured and an advertised issuer, tolerating only a benign trailing-slash
     * difference. Any other difference is treated as a genuine mismatch (M7).
     *
     * @param configuredIssuer the configured trust-anchor issuer, never null
     * @param discoveredIssuer the advertised issuer from discovery, never null
     * @return true if the two issuers are equivalent after trailing-slash normalization
     */
    private static boolean issuersMatch(String configuredIssuer, String discoveredIssuer) {
        return normalizeIssuer(configuredIssuer).equals(normalizeIssuer(discoveredIssuer));
    }

    /**
     * Normalizes an issuer for comparison by trimming surrounding whitespace and removing a single
     * trailing slash. Deliberately conservative: it does not lowercase or otherwise rewrite the
     * value, so distinct issuers are never collapsed into a false match.
     *
     * @param issuer the issuer string, never null
     * @return the normalized issuer string
     */
    private static String normalizeIssuer(String issuer) {
        String trimmed = issuer.strip();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Private record to hold retired key sets with their retirement timestamp.
     */
    private record RetiredKeySet(JWKSKeyLoader loader, Instant retiredAt) {
    }

    /**
     * Outcome of {@link #resolveJWKSAdapter()}.
     *
     * @param adapter   the resolved adapter, {@code null} if the resolution failed
     * @param retryable for a failed resolution, whether the failure is transient (the well-known
     *                  discovery yielded no JWKS URI) and may succeed on a later refresh cycle, as
     *                  opposed to a security rejection
     */
    private record AdapterResolution(@Nullable HttpAdapter<Jwks> adapter, boolean retryable) {

        static final AdapterResolution RETRYABLE_FAILURE = new AdapterResolution(null, true);
        static final AdapterResolution REJECTED = new AdapterResolution(null, false);
    }
}
