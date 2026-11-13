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
import de.cuioss.sheriff.token.commons.error.ErrorTextSanitizer;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

import static de.cuioss.sheriff.token.validation.JWTValidationLogMessages.ERROR;
import static de.cuioss.sheriff.token.validation.JWTValidationLogMessages.INFO;
import static de.cuioss.sheriff.token.validation.JWTValidationLogMessages.WARN;

/**
 * JWKS loader that loads from HTTP endpoint with caching and background refresh support.
 * Supports both direct HTTP endpoints and well-known discovery.
 * Uses HttpAdapter composition (ETagAwareHttpAdapter + ResilientHttpAdapter) for bandwidth
 * optimization via ETag caching and resilient retry behavior with optional scheduled background refresh.
 * Background refresh, when enabled, is started after the initial load attempt, also when the load
 * failed, so a loader whose first load failed recovers on its own. A terminal security rejection
 * of the endpoint resolution (see below) starts no refresh.
 * <p>
 * <strong>Status contract:</strong> {@link LoaderStatus#OK} is reported only while a usable key set
 * is installed. A fetch that succeeds at the HTTP level but yields no usable key is not a successful
 * load. A failed initial load or a transient well-known discovery failure leaves the status
 * {@link LoaderStatus#UNDEFINED} when background refresh is enabled (a later refresh cycle that
 * installs usable keys publishes {@code OK}) and {@link LoaderStatus#ERROR} when it is disabled.
 * An unexpected failure of the initialization itself (for example the initial fetch or key loading
 * throws) never leaves the status
 * {@link LoaderStatus#LOADING}: it ends in {@link LoaderStatus#UNDEFINED} only when background refresh
 * is enabled, a refresh cycle can recover (an endpoint is already published, or the loader is in
 * well-known mode and re-attempts the discovery) and the refresh task is actually scheduled; otherwise
 * it ends in a terminal {@link LoaderStatus#ERROR}. Such a failure installs no key and publishes no
 * {@code OK}. A security rejection (issuer mismatch, absent advertised issuer, or an egress block of the JWKS
 * fetch URI) is a terminal {@code ERROR}, and so is an advertised {@code jwks_uri} no HTTP handler can be
 * built for (a malformed URI, or a cleartext {@code http} URI while insecure HTTP is not allowed) when
 * no endpoint is published yet: it is logged once as {@code JWKS_URI_UNUSABLE} and the loader stops
 * retrying. A blocked discovery endpoint is not terminal: it is an
 * unavailable discovery and is handled like a transient discovery failure. A failed refresh never
 * downgrades an existing {@code OK}, since the installed keys remain usable — unless an opt-in
 * {@linkplain HttpJwksLoaderConfig#getMaxStaleness() maximum staleness} is configured and the time since
 * the last successful load exceeds it: then a refresh cycle that installs no keys moves the status to
 * {@link LoaderStatus#UNDEFINED} and logs {@code JWKS_KEYS_STALE} once, while the installed and retired
 * keys stay in place; the next refresh that installs usable keys publishes {@code OK} again. After
 * {@link #close()} no refresh still in flight publishes {@code OK}.
 * <p>
 * <strong>Unknown-kid refresh (opt-in):</strong> when an
 * {@linkplain HttpJwksLoaderConfig#getUnknownKidRefreshWindow() unknown-kid refresh window} is configured
 * and a key set is installed, a {@link #getKeyInfo(String)} miss claims the window with one
 * compare-and-set on a monotonic clock and hands one fetch to the refresh executor; the lookup performs no
 * I/O, does not wait and still misses. At most one such fetch runs per window and per loader. The fetched
 * key set is installed through the same commit step as a periodic refresh, and only when it holds the
 * requested key id and the generation is still live; installing it publishes {@code OK} like any other
 * installation. A failed, unchanged or non-matching fetch is discarded: keys, retired key sets, status,
 * endpoint and the time of the last successful load stay unchanged, and the outcome is not counted as a
 * failed refresh cycle, so it neither drives re-discovery nor the staleness bound. One in-flight guard
 * serialises the periodic and the unknown-kid fetches; a periodic cycle that finds it held skips its fetch
 * without counting the cycle.
 * <p>
 * <strong>Re-discovery:</strong> in well-known mode, after {@value #REDISCOVERY_FAILURE_THRESHOLD}
 * consecutive refresh cycles that installed no keys, the next cycle repeats the well-known discovery
 * before fetching, so a {@code jwks_uri} the IdP has moved is followed. Every re-discovery re-applies
 * the issuer gate — against the configured issuer or, for a standalone loader without one, the issuer
 * resolved so far — and the egress guard. An accepted re-discovery publishes the new endpoint and the
 * same cycle fetches from it. An unavailable discovery keeps the current endpoint and is retried after
 * the same number of further failures. A rejected re-discovery (issuer mismatch, absent advertised
 * issuer, egress block of the new {@code jwks_uri}, or a new {@code jwks_uri} no HTTP handler can be built
 * for) adopts nothing and keeps the current endpoint, keys, status and refresh task, and the cycle still
 * fetches from the current endpoint. Re-discovery is then suppressed until keys are installed successfully
 * again or the current endpoint has failed {@value #REDISCOVERY_FAILURE_THRESHOLD} further refresh cycles
 * after the rejecting one; the cycle after that consults the discovery document again, so a corrected
 * document is eventually followed.
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

    /**
     * Fixed reason of the {@code JWKS_URI_UNUSABLE} warning. The refusal of the HTTP handler
     * configuration echoes the advertised {@code jwks_uri}, which is remote document content and can
     * carry userinfo or query secrets, so the refusal message is never written; this text names every
     * cause the configuration refuses an advertised URI for.
     */
    static final String UNUSABLE_URI_REASON =
            "the advertised jwks_uri is malformed, uses a scheme other than http or https, "
                    + "or uses cleartext http while insecure HTTP is not allowed";

    /**
     * Fixed detail logged when the egress guard blocks the {@code jwks_uri} advertised by the discovery
     * document. The rejection message of the guard embeds the full URI, which is remote document content,
     * so neither the message nor the exception (whose stack trace would print it) is logged; this text
     * names both causes the guard blocks a URI for.
     */
    static final String ADVERTISED_URI_EGRESS_BLOCKED =
            "SSRF egress blocked: the advertised jwks_uri has no host or resolves to a disallowed address";

    /**
     * Number of consecutive refresh cycles without installed keys after which a well-known loader
     * repeats the discovery before its next fetch.
     */
    static final int REDISCOVERY_FAILURE_THRESHOLD = 3;

    // Fixed reasons of the UNKNOWN_KID_REFRESH_DISCARDED warning; no remote content is ever logged with it
    static final String DISCARD_CLOSED = "the loader was closed or re-initialized meanwhile";
    static final String DISCARD_NO_ENDPOINT = "no JWKS endpoint is published";
    static final String DISCARD_EGRESS_BLOCKED = "the egress check of the JWKS fetch URI failed";
    static final String DISCARD_FETCH_FAILED = "the JWKS fetch failed or returned no content";
    static final String DISCARD_UNCHANGED = "the fetched JWKS is unchanged";
    static final String DISCARD_NO_USABLE_KEY = "the fetched JWKS yields no usable key";
    static final String DISCARD_KID_ABSENT = "the fetched JWKS does not hold the requested key id";
    static final String DISCARD_PARSE_FAILED = "the fetched JWKS could not be parsed";

    /** Upper bound for the length of a failure description rendered into a log line. */
    private static final int MAX_LOGGED_FAILURE_LENGTH = 256;

    /**
     * Renders a failure for a log line without the raw exception object: the exception type and its
     * message, with every control character and Unicode line separator replaced by {@code '?'} and the
     * text cut to {@value #MAX_LOGGED_FAILURE_LENGTH} characters (CWE-117, ADR-0022). The message of a
     * transport or parsing failure can quote remote content, which an attached exception would print raw
     * with its stack trace; the type keeps the diagnostic the stack trace's first line would have given.
     *
     * @param failure the failure to render
     * @return the sanitised {@code type: message} rendering
     */
    private static String sanitizeFailure(Throwable failure) {
        return ErrorTextSanitizer.sanitize(failure.getClass().getName() + ": " + failure.getMessage(), '?',
                MAX_LOGGED_FAILURE_LENGTH, "...");
    }

    private final HttpJwksLoaderConfig config;

    /**
     * SSRF egress guard applied to the resolved JWKS fetch URL. Normally sourced from the config;
     * a package-private constructor lets tests inject a policy with a deterministic (rebinding)
     * {@link EgressPolicy.HostResolver} to exercise the per-refresh re-validation (C1 follow-up).
     */
    private final EgressPolicy egressPolicy;

    /**
     * The JWKS endpoint of the current generation: the adapter and the fetch URI of one successful
     * resolution, published together so a reader always obtains both from the same resolution.
     * {@code null} before the first successful resolution and after {@link #close()}. Only a resolution
     * that passed the egress guard is ever published, and only while the work that resolved it is
     * still live (see {@link #epoch}). The egress guard re-resolves and re-checks the fetch URI on
     * <em>every</em> background refresh, so a DNS rebind that flips the host into a blocked range after
     * the initial fetch and is visible when the check runs is rejected rather than silently trusted by
     * the reused adapter.
     */
    private final AtomicReference<JwksEndpoint> endpoint = new AtomicReference<>();

    private final AtomicReference<LoaderStatus> status = new AtomicReference<>(LoaderStatus.UNDEFINED);
    private final AtomicReference<JWKSKeyLoader> currentKeys = new AtomicReference<>();
    private final ConcurrentLinkedDeque<RetiredKeySet> retiredKeys = new ConcurrentLinkedDeque<>();
    private final AtomicReference<ScheduledFuture<?>> refreshTask = new AtomicReference<>();
    private SecurityEventCounter securityEventCounter;
    private final AtomicReference<String> resolvedIssuerIdentifier = new AtomicReference<>();
    private final AtomicReference<Jwks> currentJwksContent = new AtomicReference<>();

    /**
     * When the last load that installed (or confirmed) a usable key set finished, {@code null} before the
     * first successful load and after {@link #close()}. Read by the opt-in staleness check
     * ({@link #degradeIfStale(long)}).
     */
    private final AtomicReference<Instant> lastSuccessfulLoad = new AtomicReference<>();

    /**
     * Refresh cycles without installed keys since the last successful key installation or the last
     * re-discovery; reset by both and by {@link #close()}.
     */
    private final AtomicInteger consecutiveRefreshFailures = new AtomicInteger();

    /**
     * Set when a re-discovery was rejected (by the issuer gate, the egress guard, or because no HTTP handler
     * can be built for the advertised {@code jwks_uri}). Cleared by the next successful key installation, by
     * {@link #close()}, and once the retained endpoint has failed {@value #REDISCOVERY_FAILURE_THRESHOLD}
     * further refresh cycles after the rejecting one (see {@link #recordFailedCycle(long)}). While set, no
     * re-discovery is attempted.
     */
    private final AtomicBoolean rediscoverySuppressed = new AtomicBoolean();

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
     * Incremented by every {@link #close()}. An initialization captures the value it was claimed under,
     * and the background refresh task it schedules carries that same value, fixed when the task is
     * scheduled rather than read when a cycle begins. Once the value has moved on, such work installs
     * no keys, publishes no status or endpoint, schedules no task and issues no fetch, so work of a
     * closed generation cannot leak into a later initialization of the same loader. Written only under
     * {@link #lifecycleLock}; volatile so that {@link #getKeyInfo(String)} can capture it for an
     * unknown-kid fetch without taking the lock, which an installation may hold while it parses a key set.
     * The fetch re-checks it under the lock before it commits anything.
     */
    private volatile long epoch;

    /**
     * The monotonic clock, in nanoseconds, the unknown-kid refresh window is measured on. Only
     * differences of its values are meaningful, so comparisons are overflow-safe subtractions.
     */
    private final LongSupplier monotonicClock;

    /**
     * The {@link #monotonicClock} value from which the next unknown-kid refresh window may be claimed.
     * Claimed by a single compare-and-set, so concurrent misses claim one window at most once.
     */
    private final AtomicLong nextAllowedNanos;

    /**
     * The in-flight guard shared by every fetch path that runs on the refresh executor: the periodic
     * refresh cycle and the unknown-kid fetch. Taken by compare-and-set and released in a
     * {@code finally} block, so at most one of these fetches runs at a time, whatever the executor's
     * thread count.
     */
    private final AtomicBoolean fetchInFlight = new AtomicBoolean();

    /**
     * The {@link #epoch} of the initialization whose background refresh task was scheduled last,
     * {@code -1} before the first one. Lets {@link #startBackgroundRefresh(long)} schedule at most one
     * task per initialization. Guarded by {@link #lifecycleLock}.
     */
    private long refreshScheduledEpoch = -1;

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
        this(config, egressPolicy, System::nanoTime);
    }

    /**
     * Constructor allowing an explicit {@link EgressPolicy} and monotonic clock.
     * <p>
     * Package-private test seam: tests inject a clock to drive the expiry of the unknown-kid refresh
     * window deterministically, including values around the {@code long} overflow. The first window can
     * be claimed at once.
     *
     * @param config         the configuration for this loader
     * @param egressPolicy   the SSRF egress guard to apply on every JWKS fetch
     * @param monotonicClock the monotonic clock in nanoseconds, {@code System::nanoTime} in production
     */
    HttpJwksLoader(HttpJwksLoaderConfig config, EgressPolicy egressPolicy, LongSupplier monotonicClock) {
        this.config = config;
        this.egressPolicy = egressPolicy;
        this.monotonicClock = monotonicClock;
        this.nextAllowedNanos = new AtomicLong(monotonicClock.getAsLong());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Idempotent and race-free: the caller first reserves an uncompleted future by winning the
     * {@code initFuture} compare-and-set, and only the winner starts the asynchronous initialization,
     * whose outcome completes the reserved future. An unexpected failure of the initialization does not
     * complete the future exceptionally: it is logged as {@code JWKS_INITIALIZATION_FAILED}, a status is
     * published (see the class-level status contract, never {@link LoaderStatus#LOADING}) and the future
     * completes normally with the status in effect. Every concurrent or
     * later caller receives the same future and starts no work, so at most one initial fetch and one
     * background refresh task are started per initialization. Claiming the reservation and clearing a
     * previous {@link #close()} is one step under the lifecycle lock, so a concurrent {@code close()}
     * is ordered strictly before or strictly after the claim and is never silently undone. An
     * initialization that is still in flight when {@code close()} runs belongs to the closed
     * generation: it publishes no endpoint, status or task and its future completes with
     * {@link LoaderStatus#UNDEFINED}, with background refresh enabled or disabled, once its running
     * endpoint resolution or initial fetch returns.
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

        CompletableFuture.supplyAsync(() -> initialize(claimedEpoch))
                .exceptionally(failure -> handleUnexpectedInitializationFailure(failure, claimedEpoch))
                .whenComplete((loaderStatus, failure) -> {
                    if (failure != null) {
                        reserved.completeExceptionally(failure);
                    } else {
                        reserved.complete(completionStatus(loaderStatus, claimedEpoch));
                    }
                });
        return reserved;
    }

    /**
     * Decides the value an initialization's future completes with, re-checking under
     * {@link #lifecycleLock} whether the initialization is still live. The status returned by the
     * initialization body or by {@link #handleUnexpectedInitializationFailure(Throwable, long)} is
     * decided under the lock, but a {@link #close()} can run after that lock is released and before the
     * future completes; the closed loader then reports {@link LoaderStatus#UNDEFINED}, and so must the
     * future. The decision is the point at which the initialization is ordered against
     * {@code close()}: a {@code close()} that runs after it closes a loader whose initialization had
     * already finished. The future itself is completed outside the lock, so its dependent stages never
     * run while the lock is held.
     *
     * @param loaderStatus the status the initialization reached
     * @param claimedEpoch the {@link #epoch} the initialization was claimed under
     * @return {@code loaderStatus} while the initialization is still live, otherwise
     *         {@link LoaderStatus#UNDEFINED}
     */
    private LoaderStatus completionStatus(LoaderStatus loaderStatus, long claimedEpoch) {
        lifecycleLock.lock();
        try {
            return isLive(claimedEpoch) ? loaderStatus : LoaderStatus.UNDEFINED;
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Initialization body, run asynchronously by the single winner of
     * {@link #initJWKSLoader(SecurityEventCounter)}: resolves the endpoint, publishes it, performs the
     * initial fetch, starts the background refresh when enabled and publishes the resulting status. A
     * failed initial fetch does not overwrite an {@link LoaderStatus#OK} that a background refresh cycle
     * published in the meantime (see {@link #publishFailedLoadStatus(LoaderStatus, long)}).
     * <p>
     * When a {@link #close()} ran while the endpoint was being resolved, the resolution is discarded:
     * no endpoint or issuer is published, no fetch is issued, no status is published, nothing is
     * scheduled, and {@link LoaderStatus#UNDEFINED} is returned, the status {@code close()} leaves.
     * <p>
     * An unchecked exception thrown here (by the endpoint resolution, the initial fetch or the key
     * loading) is not handled in this method: it completes the asynchronous stage exceptionally and
     * {@link #handleUnexpectedInitializationFailure(Throwable, long)} publishes the final status, so the
     * {@link LoaderStatus#LOADING} published first is never left in place.
     *
     * @param startEpoch the {@link #epoch} this initialization was claimed under; once a
     *                   {@link #close()} moved it on, this initialization publishes and schedules nothing
     * @return the status in effect when this initialization finished, which is what
     *         {@link #getLoaderStatus()} reports while the initialization is still live, or
     *         {@link LoaderStatus#UNDEFINED} when it was superseded by {@code close()} during the
     *         endpoint resolution or the initial fetch, whether background refresh is enabled or not
     */
    private LoaderStatus initialize(long startEpoch) {
        publishStatus(LoaderStatus.LOADING, startEpoch);

        // Resolve the endpoint (may involve well-known discovery) and publish it only if still live
        AdapterResolution resolution = resolveJWKSAdapter(config.getIssuerIdentifier());
        if (!adoptResolution(resolution, startEpoch)) {
            LOGGER.debug("Initialization superseded by close() during endpoint resolution, discarding it");
            return LoaderStatus.UNDEFINED;
        }
        JwksEndpoint resolved = resolution.endpoint();
        if (resolved == null) {
            return handleAdapterResolutionFailure(resolution, startEpoch);
        }

        // Load JWKS via HttpAdapter (blocking for initialization)
        HttpResult<Jwks> result = resolved.adapter().getBlocking();

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
            LOGGER.error(ERROR.JWKS_LOAD_FAILED, loggableResolvedIssuer(), result.getErrorMessage().orElse("Unknown error"));
        }

        // Failed load (HTTP failure or no usable keys): UNDEFINED keeps the loader retrying through
        // the background refresh, ERROR marks a permanent failure when no refresh will run. The refresh
        // is already scheduled at this point, so a cycle may have installed keys and published OK in
        // the meantime; that OK is kept.
        LoaderStatus failedStatus = backgroundRefreshEnabled ? LoaderStatus.UNDEFINED : LoaderStatus.ERROR;
        return publishFailedLoadStatus(failedStatus, startEpoch);
    }

    /**
     * Ends an initialization whose body failed with an unexpected exception in a published status, so
     * the {@link LoaderStatus#LOADING} published at its start is never left in place.
     * <p>
     * The failure is logged as {@code JWKS_INITIALIZATION_FAILED} with its cause. The status is then
     * decided as one atomic step under {@link #lifecycleLock}, and only while the initialization is
     * still live:
     * <ul>
     *   <li>a {@link #close()} superseded the initialization: nothing is published or scheduled and
     *       {@link LoaderStatus#UNDEFINED} is returned, the status a closed loader reports;</li>
     *   <li>a refresh cycle already published {@link LoaderStatus#OK} or a terminal
     *       {@link LoaderStatus#ERROR}: that status is kept;</li>
     *   <li>background refresh is enabled and a refresh cycle can recover (an endpoint is already
     *       published, or the loader is in well-known mode, where a cycle re-attempts the discovery):
     *       the refresh task of this initialization is scheduled unless it already is, and
     *       {@link LoaderStatus#UNDEFINED} is published once the task is in place;</li>
     *   <li>otherwise (background refresh disabled, direct mode without a published endpoint, or the
     *       refresh task cannot be scheduled): a terminal {@link LoaderStatus#ERROR} is published.</li>
     * </ul>
     * No key is installed and no {@code OK} is published here.
     *
     * @param failure      the failure of the initialization stage, possibly wrapped in a
     *                     {@link CompletionException}
     * @param claimedEpoch the {@link #epoch} the failed initialization was claimed under
     * @return the status in effect for this initialization, with which its future completes
     */
    private LoaderStatus handleUnexpectedInitializationFailure(Throwable failure, long claimedEpoch) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        LOGGER.error(ERROR.JWKS_INITIALIZATION_FAILED, sanitizeFailure(cause), loggableIssuerIdentifier());

        lifecycleLock.lock();
        try {
            if (!isLive(claimedEpoch)) {
                return LoaderStatus.UNDEFINED;
            }
            LoaderStatus current = status.get();
            if (current == LoaderStatus.OK || current == LoaderStatus.ERROR) {
                return current;
            }
            boolean recoverable = config.isBackgroundRefreshEnabled()
                    && (endpoint.get() != null || config.getWellKnownConfig() != null);
            LoaderStatus failedStatus = recoverable && ensureBackgroundRefresh(claimedEpoch)
                    ? LoaderStatus.UNDEFINED
                    : LoaderStatus.ERROR;
            status.set(failedStatus);
            return failedStatus;
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Makes sure the background refresh of the given live initialization is scheduled, scheduling it
     * when the failure struck before {@link #startBackgroundRefresh(long)} ran. A task already
     * scheduled for this initialization is never scheduled a second time. The caller must hold
     * {@link #lifecycleLock}.
     *
     * @param claimedEpoch the {@link #epoch} of the live initialization
     * @return {@code true} if a refresh task of this initialization is scheduled and active;
     *         {@code false} if none could be scheduled (the refresh executor rejected it) or the
     *         task has been cancelled
     */
    private boolean ensureBackgroundRefresh(long claimedEpoch) {
        if (refreshScheduledEpoch != claimedEpoch) {
            try {
                startBackgroundRefresh(claimedEpoch);
            } catch (RejectedExecutionException e) {
                LOGGER.error(ERROR.JWKS_INITIALIZATION_FAILED, sanitizeFailure(e), loggableIssuerIdentifier());
                return false;
            }
        }
        return isBackgroundRefreshActive();
    }

    /**
     * Handles the case where JWKS adapter resolution failed (well-known discovery or HTTP handler).
     * <p>
     * A retryable well-known discovery failure (the discovery was unavailable: a transport failure or
     * a document without a {@code jwks_uri}) with background refresh enabled leaves the status
     * {@link LoaderStatus#UNDEFINED} and schedules the background refresh, which re-attempts the
     * discovery. A blocked discovery endpoint is such an unavailable discovery and therefore retryable.
     * Every other failure (a security rejection — an absent advertised issuer, an issuer mismatch or
     * an egress block of the JWKS fetch URI —, an advertised {@code jwks_uri} no HTTP handler can be
     * built for, or any failure without background refresh) is a terminal {@link LoaderStatus#ERROR}
     * and schedules nothing. An unusable advertised {@code jwks_uri} additionally logs
     * {@code JWKS_URI_UNUSABLE} once, when the {@code ERROR} is published.
     *
     * @param resolution the failed resolution
     * @param startEpoch the {@link #epoch} the calling initialization was claimed under
     * @return the status this initialization reached; {@link LoaderStatus#UNDEFINED} when a
     *         {@link #close()} superseded the initialization before the status was published
     */
    private LoaderStatus handleAdapterResolutionFailure(AdapterResolution resolution, long startEpoch) {
        boolean isWellKnownFailure = config.getWellKnownConfig() != null;
        String errorDetail = isWellKnownFailure
                ? "Well-known discovery failed"
                : "No HTTP handler configured";

        if (isWellKnownFailure) {
            LOGGER.warn(WARN.JWKS_URI_RESOLUTION_FAILED);
        }
        LOGGER.error(ERROR.JWKS_INITIALIZATION_FAILED, errorDetail, loggableIssuerIdentifier());

        if (resolution.retryable() && config.isBackgroundRefreshEnabled()) {
            publishStatus(LoaderStatus.UNDEFINED, startEpoch);
            startBackgroundRefresh(startEpoch);
            return LoaderStatus.UNDEFINED;
        }
        // Check-and-publish is one step: a close() that ran since the resolution was adopted leaves
        // UNDEFINED, for the loader and for the future alike.
        LoaderStatus published = publishFailedLoadStatus(LoaderStatus.ERROR, startEpoch);
        if (published == LoaderStatus.ERROR && resolution.unusableUri()) {
            logUnusableUri(resolution);
        }
        return published;
    }

    /**
     * Resolves the JWKS endpoint based on configuration.
     * For well-known: performs discovery to get JWKS URL and validates issuer
     * For direct: uses the configured HTTP handler
     * <p>
     * Stores nothing: the caller publishes the returned issuer and endpoint, and only while the work
     * that requested the resolution is still live (see {@link #adoptResolution(AdapterResolution, long)}).
     * A URI rejected by the egress guard is never part of the result.
     *
     * <p>
     * A well-known discovery that yields no JWKS URI is retryable when the discovery layer reports it
     * as {@link HttpWellKnownResolver.FailureReason#UNAVAILABLE} (a transport failure, an error
     * response, a blocked discovery endpoint, or a document that is unreadable or lacks a
     * {@code jwks_uri}, also when it lacks the issuer as well). A document that carries a
     * {@code jwks_uri} but advertises no issuer is reported as
     * {@link HttpWellKnownResolver.FailureReason#ISSUER_ABSENT}; it goes through the issuer gate of
     * {@link #resolveIssuer(String, String, SecurityEventCounter)} with an absent advertised issuer
     * and is a terminal rejection. An advertised {@code jwks_uri} for which the configuration cannot
     * build an HTTP handler (a malformed URI, a scheme other than {@code http} or {@code https}, or a
     * cleartext {@code http} URI while insecure HTTP is not allowed) is a terminal rejection as well: the resolution carries the resolved issuer and is marked
     * as such ({@link AdapterResolution#unusableUri()}), and nothing is fetched. The refusal itself is not
     * kept, since its message echoes the advertised URI.
     * <p>
     * An egress block is logged as {@code JWKS_INITIALIZATION_FAILED}. In well-known mode the blocked URI
     * is the advertised {@code jwks_uri}, so the record carries the fixed
     * {@value #ADVERTISED_URI_EGRESS_BLOCKED} detail and no exception; in direct mode the URI is the
     * operator-configured one and the rejection is logged with its message and cause.
     *
     * @param trustAnchor the issuer the advertised issuer must match, {@code null} to adopt the
     *                    advertised issuer as-is (standalone loader without a configured or resolved
     *                    issuer); ignored in direct mode, which uses the configured issuer
     * @return the resolution: on success the endpoint (adapter and fetch URI of this one resolution)
     *         and the resolved issuer; on failure no endpoint, flagged retryable for an unavailable
     *         discovery as described above and not retryable for a security rejection (absent
     *         advertised issuer, issuer mismatch, egress block) or an unusable advertised
     *         {@code jwks_uri}. An egress-rejected or unusable-URI resolution still carries the issuer
     *         it resolved.
     */
    private AdapterResolution resolveJWKSAdapter(@Nullable String trustAnchor) {
        HttpHandler handler;
        @Nullable String issuer;

        if (config.getWellKnownConfig() != null) {
            // Well-known discovery - the resolver itself uses HttpAdapter for retry!
            HttpWellKnownResolver resolver = config.getWellKnownConfig().createResolver(securityEventCounter);

            // This call may block but we're in async context
            Optional<String> jwksUri = resolver.getJwksUri();
            if (jwksUri.isEmpty()) {
                if (resolver.getFailureReason().orElse(null) == HttpWellKnownResolver.FailureReason.ISSUER_ABSENT) {
                    // The discovery document advertises no issuer: the issuer gate rejects an absent
                    // advertised issuer (counting ISSUER_MISMATCH when one is configured), so this is
                    // a terminal rejection, never retried.
                    resolveIssuer(null, trustAnchor, securityEventCounter);
                    return AdapterResolution.REJECTED;
                }
                // Transport failure, missing jwks_uri or an unreadable document: may succeed later
                return AdapterResolution.RETRYABLE_FAILURE;
            }

            // Resolve issuer from well-known document
            Optional<String> discoveredIssuer = resolver.getIssuer();

            Optional<String> resolvedIssuer = resolveIssuer(discoveredIssuer.orElse(null), trustAnchor, securityEventCounter);
            if (resolvedIssuer.isEmpty()) {
                return AdapterResolution.REJECTED;
            }
            issuer = resolvedIssuer.get();

            // Use overloaded method to create handler for discovered JWKS URL. The configuration refuses
            // an advertised jwks_uri it cannot use (malformed, a scheme other than http or https, or
            // cleartext http while insecure HTTP is not allowed); that refusal does not go away by retrying, so it is a terminal rejection.
            // The refusal is dropped on purpose: its message echoes the advertised URI.
            try {
                handler = config.getHttpHandler(jwksUri.get());
            } catch (IllegalArgumentException | IllegalStateException refusal) {
                return AdapterResolution.unusableUriRejection(issuer);
            }
        } else {
            // Direct HTTP configuration - use existing handler from config
            handler = config.getHttpHandler();
            issuer = config.getIssuerIdentifier();
        }

        // SSRF egress guard (C1): resolve the advertised/configured JWKS URL and reject it
        // if it lands on an internal/loopback/metadata address BEFORE any fetch is issued.
        // Resolving here — immediately before the fetch — narrows the DNS-rebinding window; it does not
        // close it, since the HTTP client resolves the host again when it connects.
        // The URI travels with the adapter in the endpoint so the guard can be re-applied on every
        // scheduled background refresh (C1 follow-up), not just this initial resolution.
        URI fetchUri = handler.getUri();
        try {
            egressPolicy.check(fetchUri);
        } catch (TransportException e) {
            String loggableIssuer = ErrorTextSanitizer.sanitize(Optional.ofNullable(issuer).orElse(ISSUER_NOT_CONFIGURED));
            if (isFetchUriAdvertised()) {
                // The rejection message embeds the advertised jwks_uri: neither it nor the exception is logged
                LOGGER.error(ERROR.JWKS_INITIALIZATION_FAILED, ADVERTISED_URI_EGRESS_BLOCKED, loggableIssuer);
            } else {
                LOGGER.error(ERROR.JWKS_INITIALIZATION_FAILED, sanitizeFailure(e), loggableIssuer);
            }
            return new AdapterResolution(null, issuer, false, false);
        }

        // Create base adapter with ETag caching
        HttpAdapter<Jwks> baseAdapter = ETagAwareHttpAdapter.<Jwks>builder()
                .httpHandler(handler)
                .responseConverter(new JwksHttpContentConverter(config.getParserConfig()))
                .cacheKeyHeaderFilter(CacheKeyHeaderFilter.NONE)  // URI only - public OAuth endpoints
                .build();

        // Wrap with retry behavior
        JwksEndpoint resolved = new JwksEndpoint(ResilientHttpAdapter.wrap(baseAdapter, config.getRetryConfig()), fetchUri);
        return new AdapterResolution(resolved, issuer, false, false);
    }

    /**
     * Whether the JWKS fetch URI is the {@code jwks_uri} advertised by a discovery document, which is
     * remote content, rather than the operator-configured {@code jwksUrl}.
     *
     * @return {@code true} in well-known mode
     */
    private boolean isFetchUriAdvertised() {
        return config.getWellKnownConfig() != null;
    }

    /**
     * Publishes what a resolution yielded — its issuer and, on success, its endpoint — as one atomic
     * step under {@link #lifecycleLock}, but only while the work that requested it is still live.
     *
     * @param resolution the resolution to publish
     * @param startEpoch the {@link #epoch} the requesting initialization or refresh cycle started under
     * @return {@code true} if the work is still live and the resolution was published; {@code false}
     *         if a {@link #close()} ran since the work started, in which case nothing was published
     */
    private boolean adoptResolution(AdapterResolution resolution, long startEpoch) {
        lifecycleLock.lock();
        try {
            if (!isLive(startEpoch)) {
                return false;
            }
            String issuer = resolution.issuer();
            if (issuer != null) {
                resolvedIssuerIdentifier.set(issuer);
            }
            JwksEndpoint resolved = resolution.endpoint();
            if (resolved != null) {
                endpoint.set(resolved);
            }
            return true;
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Looks the key id up in the installed key set and then in the retired key sets still within the
     * grace period. When both miss, a key set is installed and an
     * {@linkplain HttpJwksLoaderConfig#getUnknownKidRefreshWindow() unknown-kid refresh window} is
     * configured, the miss may schedule one background fetch (see the class documentation); the lookup
     * itself performs no I/O, never waits for that fetch and still returns empty.
     */
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

        Optional<Duration> window = config.getUnknownKidRefreshWindow();
        if (window.isPresent() && current != null) {
            scheduleUnknownKidRefresh(kid, window.get());
        }
        return Optional.empty();
    }

    /**
     * Claims the unknown-kid refresh window and, when won, hands one fetch for {@code kid} to the
     * refresh executor, without I/O or waiting on the caller's thread.
     * <p>
     * Two claims are taken, each a single compare-and-set, so there is no check-then-act gap: the window
     * claim on {@link #nextAllowedNanos} (compared overflow-safely with the monotonic clock and moved on
     * by one window) and the in-flight guard {@link #fetchInFlight}. A lost claim means another caller
     * owns the window or a fetch is already running; it is logged at DEBUG only. The fetch carries the
     * {@link #epoch} read here, so it commits nothing once a {@link #close()} moved it on. An executor
     * that rejects the fetch releases the guard and never throws on the caller.
     *
     * @param kid    the key id that was not found
     * @param window the configured unknown-kid refresh window
     */
    private void scheduleUnknownKidRefresh(String kid, Duration window) {
        long now = monotonicClock.getAsLong();
        long allowedFrom = nextAllowedNanos.get();
        if (now - allowedFrom < 0) {
            LOGGER.debug("Unknown-kid refresh window still claimed, no fetch scheduled");
            return;
        }
        if (!nextAllowedNanos.compareAndSet(allowedFrom, now + window.toNanos())) {
            LOGGER.debug("Unknown-kid refresh window claimed concurrently, no fetch scheduled");
            return;
        }
        if (!fetchInFlight.compareAndSet(false, true)) {
            LOGGER.debug("A JWKS fetch is already in flight, no unknown-kid fetch scheduled");
            return;
        }
        long claimEpoch = epoch;
        try {
            config.getScheduledExecutorService().execute(() -> fetchForUnknownKid(kid, claimEpoch));
        } catch (RejectedExecutionException e) {
            fetchInFlight.set(false);
            LOGGER.debug("Refresh executor rejected the unknown-kid fetch (%s)", e.getClass().getName());
            return;
        }
        LOGGER.info(INFO.UNKNOWN_KID_REFRESH_SCHEDULED, ErrorTextSanitizer.sanitize(kid), loggableIssuerIdentifier());
    }

    /**
     * The off-to-the-side fetch scheduled by {@link #scheduleUnknownKidRefresh(String, Duration)}, run on
     * the refresh executor while holding the in-flight guard, which it releases when it ends.
     * <p>
     * It re-runs the egress check on the current endpoint's fetch URI, fetches with that endpoint's
     * adapter and builds a candidate key set. The candidate is committed through the shared commit step,
     * under {@link #lifecycleLock}, only when it holds {@code kid} and the generation {@code claimEpoch}
     * is still live. Every other outcome is discarded with {@code UNKNOWN_KID_REFRESH_DISCARDED}: keys,
     * retired key sets, the installed JWKS content, the time of the last successful load, status and
     * endpoint stay unchanged, and {@link #recordFailedCycle(long)} is never called, so the outcome
     * counts towards neither re-discovery nor the staleness bound. Only the failures a fetch or a parse
     * can raise are caught.
     *
     * @param kid        the key id the fetched key set must hold
     * @param claimEpoch the {@link #epoch} read when the window was claimed
     */
    private void fetchForUnknownKid(String kid, long claimEpoch) {
        try {
            JwksEndpoint current;
            lifecycleLock.lock();
            try {
                if (!isLive(claimEpoch)) {
                    discardUnknownKidFetch(DISCARD_CLOSED);
                    return;
                }
                current = endpoint.get();
            } finally {
                lifecycleLock.unlock();
            }
            if (current == null) {
                discardUnknownKidFetch(DISCARD_NO_ENDPOINT);
                return;
            }
            if (!egressCheckPassed(current.fetchUri())) {
                discardUnknownKidFetch(DISCARD_EGRESS_BLOCKED);
                return;
            }
            HttpResult<Jwks> result = current.adapter().getBlocking();
            Jwks fetched = result.isSuccess() ? result.getContent().orElse(null) : null;
            if (fetched == null) {
                discardUnknownKidFetch(DISCARD_FETCH_FAILED);
                return;
            }
            if (fetched.equals(currentJwksContent.get())) {
                discardUnknownKidFetch(DISCARD_UNCHANGED);
                return;
            }
            JWKSKeyLoader candidate = buildUsableKeySet(fetched);
            if (candidate == null) {
                discardUnknownKidFetch(DISCARD_NO_USABLE_KEY);
                return;
            }
            if (candidate.getKeyInfo(kid).isEmpty()) {
                discardUnknownKidFetch(DISCARD_KID_ABSENT);
                return;
            }
            if (!commitKeySetAndPublishOk(fetched, candidate, claimEpoch)) {
                discardUnknownKidFetch(DISCARD_CLOSED);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            discardUnknownKidFetch(DISCARD_PARSE_FAILED);
            LOGGER.debug("Unknown-kid fetch failed (%s)", e.getClass().getName());
        } finally {
            fetchInFlight.set(false);
        }
    }

    /**
     * Logs a discarded unknown-kid fetch; nothing else is changed.
     *
     * @param reason the fixed reason text
     */
    private void discardUnknownKidFetch(String reason) {
        LOGGER.warn(WARN.UNKNOWN_KID_REFRESH_DISCARDED, loggableIssuerIdentifier(), reason);
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
                LOGGER.info(INFO.JWKS_LOADED, loggableResolvedIssuer());
            }
            publishOk();
            return true;
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Commits a usable candidate key set through the shared commit step and publishes
     * {@link LoaderStatus#OK}, as one atomic step under {@link #lifecycleLock}, but only while the
     * generation {@code startEpoch} is still live.
     *
     * @param newJwks    the JWKS the candidate was built from
     * @param candidate  the usable key set built by {@link #buildUsableKeySet(Jwks)}
     * @param startEpoch the {@link #epoch} the calling work started under
     * @return {@code true} if the key set was committed and {@code OK} published, {@code false} if a
     *         {@link #close()} moved the epoch on, in which case nothing was changed
     */
    private boolean commitKeySetAndPublishOk(Jwks newJwks, JWKSKeyLoader candidate, long startEpoch) {
        lifecycleLock.lock();
        try {
            if (!isLive(startEpoch)) {
                return false;
            }
            commitKeySet(newJwks, candidate);
            publishOk();
            return true;
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Records a successful load and publishes {@link LoaderStatus#OK}. The caller must hold
     * {@link #lifecycleLock} and must have installed, or confirmed, a usable key set.
     */
    private void publishOk() {
        lastSuccessfulLoad.set(Instant.now());
        consecutiveRefreshFailures.set(0);
        rediscoverySuppressed.set(false);
        status.set(LoaderStatus.OK);
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
     * Publishes the status of a failed initial load, as one atomic step under {@link #lifecycleLock}.
     * <p>
     * The background refresh is scheduled before the failed-load status is published, so a refresh
     * cycle may already have installed a usable key set and published {@link LoaderStatus#OK}. That
     * {@code OK} is kept rather than overwritten: only {@link #installKeysAndPublishOk} ever sets
     * {@code OK}, so keeping it never reports a loader without usable keys as {@code OK}. Nothing is
     * published once the loader is closed or the caller's epoch has passed.
     *
     * @param failedStatus the status of the failed load, {@link LoaderStatus#UNDEFINED} or
     *                     {@link LoaderStatus#ERROR}
     * @param startEpoch   the {@link #epoch} the calling initialization was claimed under
     * @return the status in effect for this initialization: {@code OK} when a refresh cycle installed
     *         usable keys first, {@link LoaderStatus#UNDEFINED} when a {@link #close()} superseded the
     *         initialization (whatever {@code failedStatus} is, as that is what a closed loader
     *         reports), otherwise {@code failedStatus}
     */
    private LoaderStatus publishFailedLoadStatus(LoaderStatus failedStatus, long startEpoch) {
        lifecycleLock.lock();
        try {
            if (!isLive(startEpoch)) {
                // Superseded by close(): the loader reports UNDEFINED, so the future must not claim ERROR
                return LoaderStatus.UNDEFINED;
            }
            if (status.get() == LoaderStatus.OK) {
                return LoaderStatus.OK;
            }
            status.set(failedStatus);
            return failedStatus;
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

        // Never retire a good key set for an empty/ERROR refresh (L9). If the freshly-loaded key
        // set produced no usable keys, keep the current keys and leave the content reference
        // unchanged so the next scheduled refresh retries against the same content.
        JWKSKeyLoader newLoader = buildUsableKeySet(newJwks);
        if (newLoader == null) {
            LOGGER.warn(WARN.JWKS_REFRESH_RETURNED_NO_KEYS, loggableResolvedIssuer());
            return false;
        }

        commitKeySet(newJwks, newLoader);
        return true;
    }

    /**
     * Builds the key set of the given JWKS and returns it if it is usable. Touches no shared state of
     * this loader, so it may run outside {@link #lifecycleLock}.
     *
     * @param newJwks the fetched JWKS
     * @return the usable key set, {@code null} if the JWKS yields no usable key
     */
    private @Nullable JWKSKeyLoader buildUsableKeySet(Jwks newJwks) {
        JWKSKeyLoader newLoader = JWKSKeyLoader.builder()
                .jwksContent(newJwks)
                .jwksType(config.getJwksType())
                .build();
        newLoader.initJWKSLoader(securityEventCounter);
        return newLoader.getLoaderStatus() == LoaderStatus.OK ? newLoader : null;
    }

    /**
     * The commit step shared by every installation path: makes a usable key set the current one,
     * retires the previous set within the grace period and the {@code maxRetiredKeySets} bound, and logs
     * {@code JWKS_KEYS_UPDATED}. The caller must hold {@link #lifecycleLock} and must have checked that
     * its generation is still live.
     *
     * @param newJwks   the JWKS the key set was built from
     * @param newLoader the usable key set built by {@link #buildUsableKeySet(Jwks)}
     */
    private void commitKeySet(Jwks newJwks, JWKSKeyLoader newLoader) {
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

        // Log keys update: the issuer and the key ids of the installed key set, sorted so the message
        // is deterministic. The key ids come from the remote JWKS and are only length-bounded, and the
        // issuer can be the one the discovery document advertised, so both are neutralized against log
        // injection (CWE-117) before they are written.
        List<String> installedKeyIds = newLoader.getKeyIds().stream().sorted()
                .map(ErrorTextSanitizer::sanitize).toList();
        LOGGER.info(INFO.JWKS_KEYS_UPDATED, loggableResolvedIssuer(), installedKeyIds);
    }

    /**
     * Schedules the background refresh, unless the loader has been closed in the meantime (an
     * initialization still in flight when {@link #close()} ran must not leave a running task behind,
     * not even when the loader has been initialized again since). The scheduled task carries
     * {@code startEpoch}, so each of its cycles belongs to this initialization's generation, also when
     * it begins only after a {@code close()} and a later re-initialization. An initialization schedules
     * at most one task: a second call for the same {@code startEpoch} schedules nothing.
     *
     * @param startEpoch the {@link #epoch} the calling initialization was claimed under
     * @throws RejectedExecutionException if the refresh executor rejects the task
     */
    private void startBackgroundRefresh(long startEpoch) {
        lifecycleLock.lock();
        try {
            if (!isLive(startEpoch) || refreshScheduledEpoch == startEpoch) {
                return;
            }
            refreshTask.set(config.getScheduledExecutorService().scheduleAtFixedRate(
                    () -> performBackgroundRefresh(startEpoch),
                    config.getRefreshIntervalSeconds(),
                    config.getRefreshIntervalSeconds(),
                    TimeUnit.SECONDS));
            refreshScheduledEpoch = startEpoch;
        } finally {
            lifecycleLock.unlock();
        }

        LOGGER.info(INFO.JWKS_BACKGROUND_REFRESH_STARTED, config.getRefreshIntervalSeconds());
    }

    /**
     * Re-validates SSRF egress against the supplied fetch URI for one background refresh cycle.
     * <p>
     * A block is logged as {@code BACKGROUND_REFRESH_FAILED}. In well-known mode the fetch URI is the
     * advertised {@code jwks_uri}, so the record carries the fixed {@value #ADVERTISED_URI_EGRESS_BLOCKED}
     * detail and no exception, because the rejection message embeds the URI; in direct mode the URI is
     * the operator-configured one and the rejection is logged with its message and cause.
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
            if (isFetchUriAdvertised()) {
                LOGGER.warn(WARN.BACKGROUND_REFRESH_FAILED, ADVERTISED_URI_EGRESS_BLOCKED);
            } else {
                LOGGER.warn(WARN.BACKGROUND_REFRESH_FAILED, sanitizeFailure(e));
            }
            return false;
        }
    }

    /**
     * Executes one background refresh cycle under the current {@link #epoch}.
     * <p>
     * Package-private test entry point, so a cycle can be driven deterministically in tests without
     * relying on the {@link java.util.concurrent.ScheduledExecutorService} timing. It reads the current
     * epoch and delegates to {@link #performBackgroundRefresh(long)}; the scheduled task does not use
     * it, it carries the epoch of the initialization that scheduled it.
     */
    void performBackgroundRefresh() {
        performBackgroundRefresh(currentEpoch());
    }

    /**
     * Executes one background refresh cycle of the generation {@code cycleEpoch}: re-validates SSRF
     * egress against the fetch URI of the current endpoint, fetches the JWKS with that same endpoint's
     * adapter, and rotates keys on success.
     * <p>
     * Status contract: a cycle that installs a usable key set publishes {@link LoaderStatus#OK}, which
     * lets a loader whose first load failed recover. A failed cycle never downgrades an existing
     * {@code OK}, since the installed keys remain usable — unless a
     * {@linkplain HttpJwksLoaderConfig#getMaxStaleness() maximum staleness} is configured and exceeded,
     * in which case the cycle moves the status to {@link LoaderStatus#UNDEFINED} without dropping any
     * key (see {@link #degradeIfStale(long)}). A cycle of a closed generation — one whose
     * {@code cycleEpoch} a {@link #close()} has moved on, even if the loader has been initialized again
     * since — issues no fetch, installs no keys, publishes neither a status nor an endpoint and leaves the
     * re-discovery failure counter alone. This also holds for a {@code close()} that happens while the
     * cycle is re-discovering its endpoint: the cycle then returns before the egress check and the fetch.
     * A {@code close()} during the fetch itself cannot stop the fetch, but its outcome is discarded and
     * counted nowhere.
     * <p>
     * When no endpoint is available because the initial well-known discovery failed transiently, the
     * cycle re-attempts the discovery and, on success, continues with the fetch in the same cycle. A
     * security rejection on that re-attempt is terminal: the status becomes {@link LoaderStatus#ERROR}
     * and the refresh task is cancelled.
     * <p>
     * When an endpoint is available in well-known mode and {@value #REDISCOVERY_FAILURE_THRESHOLD}
     * consecutive cycles installed no keys, the cycle first repeats the discovery (see
     * {@link #rediscoverEndpoint(long, JwksEndpoint)}). Unlike the no-endpoint case, a rejected
     * re-discovery is not terminal: the current endpoint, keys, status and refresh task are kept, and the
     * cycle still fetches from the current endpoint. That includes an advertised {@code jwks_uri} for
     * which no HTTP handler can be built. After a rejection the next re-discovery follows only once the
     * current endpoint has failed {@value #REDISCOVERY_FAILURE_THRESHOLD} further cycles, or not at all
     * when a cycle installs keys first.
     * <p>
     * The egress re-check (C1 follow-up) is the crux: the cached {@link HttpAdapter} is reused across
     * every scheduled refresh, so without re-resolving and re-checking the fetch URI here, a DNS answer
     * that rebinds an initially-public host into an internal/loopback/metadata range after the first
     * fetch would be silently trusted. Re-running {@link EgressPolicy#check(URI)} on each cycle resolves
     * the host fresh and blocks a target that is already rebound when the check runs, which narrows the
     * DNS-rebinding window. It does not close it: the HTTP client resolves the host again when it
     * connects, and a rebind between the check and that connection is not seen. The endpoint is read
     * once, so the URI that is checked is the URI the adapter fetches.
     *
     * <p>
     * The cycle takes the in-flight guard shared with the unknown-kid fetch first. When another fetch
     * holds it, the cycle skips its fetch and is not counted as a failed cycle.
     *
     * @param cycleEpoch the {@link #epoch} of the generation this cycle belongs to
     */
    private void performBackgroundRefresh(long cycleEpoch) {
        if (!fetchInFlight.compareAndSet(false, true)) {
            LOGGER.debug("Skipping a background refresh cycle, another JWKS fetch is in flight");
            return;
        }
        try {
            runRefreshCycle(cycleEpoch);
        } finally {
            fetchInFlight.set(false);
        }
    }

    /**
     * The body of one background refresh cycle, run while holding the in-flight guard (see
     * {@link #performBackgroundRefresh(long)}).
     *
     * @param cycleEpoch the {@link #epoch} of the generation this cycle belongs to
     */
    private void runRefreshCycle(long cycleEpoch) {
        JwksEndpoint current;
        lifecycleLock.lock();
        try {
            if (!isLive(cycleEpoch)) {
                LOGGER.debug("Skipping a background refresh cycle of a closed loader generation");
                return;
            }
            current = endpoint.get();
        } finally {
            lifecycleLock.unlock();
        }
        boolean installed = false;
        try {
            if (current == null) {
                current = reResolveEndpoint(cycleEpoch);
                if (current == null) {
                    return;
                }
            } else if (isRediscoveryDue()) {
                current = rediscoverEndpoint(cycleEpoch, current);
                if (current == null) {
                    return;
                }
            }

            // Re-validate egress on every refresh (C1 follow-up): reject a rebound host before fetching.
            if (!egressCheckPassed(current.fetchUri())) {
                return;
            }

            // Blocking load in background thread
            HttpResult<Jwks> result = current.adapter().getBlocking();

            if (result.isSuccess()) {
                installed = installKeysAndPublishOk(result.getContent().orElse(null), false, cycleEpoch);
            } else {
                // Handle error (network, server, etc.)
                String statusDesc = result.getErrorMessage()
                        .orElseGet(() -> "HTTP status: " + result.getHttpStatus().map(String::valueOf).orElse("N/A"));
                LOGGER.warn(WARN.BACKGROUND_REFRESH_FAILED, statusDesc);
            }
        } catch (IllegalArgumentException e) {
            // JSON parsing or validation errors
            LOGGER.warn(WARN.BACKGROUND_REFRESH_PARSE_ERROR, ErrorTextSanitizer.sanitize(e.getMessage()),
                    loggableResolvedIssuer());
        } catch (IllegalStateException e) {
            // State errors (e.g., from orElseThrow when issuer not resolved), includes CancellationException.
            // The message is neutralized against log injection (CWE-117) like the parse error above.
            LOGGER.warn(WARN.BACKGROUND_REFRESH_FAILED, ErrorTextSanitizer.sanitize(e.getMessage()));
        }
        // cui-rewrite:disable InvalidExceptionUsageRecipe
        // Intentional catch-all safety net: ScheduledExecutorService silently cancels the task
        // on ANY uncaught exception (including NPE, ClassCastException, etc.) with no recovery.
        // Without this, a single unexpected failure permanently kills JWKS background refresh,
        // causing keys to never rotate — a silent availability degradation.
        catch (Exception e) {
            LOGGER.warn(WARN.BACKGROUND_REFRESH_FAILED, sanitizeFailure(e));
        } finally {
            if (!installed) {
                recordFailedCycle(cycleEpoch);
            }
        }
    }

    /**
     * Records a refresh cycle that installed no keys: counts it towards the re-discovery threshold, lifts
     * a re-discovery suppression that has run its course, and applies the opt-in staleness bound (see
     * {@link #degradeIfStale(long)}).
     * <p>
     * A re-discovery resets the counter, so the cycle that made a rejected re-discovery is counted as the
     * first failure after it. Once the counter exceeds {@value #REDISCOVERY_FAILURE_THRESHOLD}, the retained
     * endpoint has failed that many further cycles and the suppression is lifted, so the next cycle consults
     * the discovery document again.
     * <p>
     * All of this happens only while the cycle's generation is live. The failure counter is incremented under
     * {@link #lifecycleLock}, so a cycle that started before a {@link #close()} cannot add a failure to the
     * counter that {@code close()} reset, and therefore not to the counter of a later initialization.
     *
     * @param cycleEpoch the {@link #epoch} of the generation the calling refresh cycle belongs to
     */
    private void recordFailedCycle(long cycleEpoch) {
        lifecycleLock.lock();
        try {
            if (!isLive(cycleEpoch)) {
                return;
            }
            if (consecutiveRefreshFailures.incrementAndGet() > REDISCOVERY_FAILURE_THRESHOLD) {
                // The rejecting cycle and the threshold of further cycles failed: lift the suppression
                rediscoverySuppressed.set(false);
            }
        } finally {
            lifecycleLock.unlock();
        }
        degradeIfStale(cycleEpoch);
    }

    /**
     * Whether the current refresh cycle should repeat the well-known discovery before fetching: the
     * loader is in well-known mode, {@value #REDISCOVERY_FAILURE_THRESHOLD} consecutive cycles installed no
     * keys, and re-discovery is not suppressed by an earlier rejection. A suppression lasts until keys are
     * installed or the current endpoint has failed {@value #REDISCOVERY_FAILURE_THRESHOLD} further cycles
     * after the rejecting one (see {@link #recordFailedCycle(long)}).
     *
     * @return {@code true} if the cycle should re-discover its endpoint first
     */
    private boolean isRediscoveryDue() {
        return config.getWellKnownConfig() != null
                && consecutiveRefreshFailures.get() >= REDISCOVERY_FAILURE_THRESHOLD
                && !rediscoverySuppressed.get();
    }

    /**
     * Repeats the well-known discovery for a cycle that still has a published endpoint, so a
     * {@code jwks_uri} the IdP has moved is followed.
     * <p>
     * The discovery goes through {@link #resolveJWKSAdapter(String)}, so the issuer gate and the egress
     * guard are applied again. The trust anchor is the configured issuer or, for a standalone loader
     * without one, the issuer resolved so far, so a re-discovery never widens what is accepted. The
     * failure counter is reset first, so the next attempt follows only after as many further failures.
     * <ul>
     *   <li>Accepted: the new endpoint and issuer are published while the cycle's generation is live,
     *       and the cycle fetches from the new endpoint.</li>
     *   <li>Unavailable discovery: the current endpoint is kept.</li>
     *   <li>Rejected (issuer mismatch, absent advertised issuer, egress block of the new
     *       {@code jwks_uri}): nothing is adopted, the current endpoint, keys, status and refresh task are
     *       kept, and re-discovery is suppressed.</li>
     *   <li>Unusable advertised {@code jwks_uri} (no HTTP handler can be built for it: a malformed URI, or
     *       a cleartext {@code http} URI while insecure HTTP is not allowed): logged as
     *       {@code JWKS_URI_RESOLUTION_FAILED} and handled as a rejection. The configuration refuses the
     *       advertised endpoint, so repeating the discovery would only meet the same refusal until the
     *       IdP changes its document; the current endpoint is kept and still fetched in this cycle, and
     *       re-discovery is suppressed.</li>
     * </ul>
     * A suppression lasts until keys are installed successfully again or the current endpoint has failed
     * {@value #REDISCOVERY_FAILURE_THRESHOLD} further cycles after the rejecting one; the cycle after that
     * consults the discovery document again, so a document the IdP has corrected is eventually followed.
     * When a {@link #close()} moved the cycle's generation on before the outcome is applied, nothing is
     * adopted or suppressed and {@code null} is returned, so the cycle issues no fetch.
     *
     * @param cycleEpoch the {@link #epoch} of the generation the calling refresh cycle belongs to
     * @param current    the endpoint the cycle would otherwise fetch from
     * @return the endpoint this cycle fetches from: the re-discovered one when accepted, otherwise
     *         {@code current}; {@code null} when the cycle's generation was closed, in which case the
     *         cycle must fetch nothing
     */
    private @Nullable JwksEndpoint rediscoverEndpoint(long cycleEpoch, JwksEndpoint current) {
        lifecycleLock.lock();
        try {
            if (!isLive(cycleEpoch)) {
                return null;
            }
            consecutiveRefreshFailures.set(0);
        } finally {
            lifecycleLock.unlock();
        }
        String trustAnchor = config.getIssuerIdentifier() != null ? config.getIssuerIdentifier() : getResolvedIssuer();
        LOGGER.debug("Re-discovering the JWKS endpoint after %s consecutive failed refresh cycles",
                REDISCOVERY_FAILURE_THRESHOLD);
        AdapterResolution resolution;
        try {
            resolution = resolveJWKSAdapter(trustAnchor);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // The re-discovery failed with a refusal: keep fetching the current endpoint
            logRefusedAdvertisedUri(e);
            return keepCurrentEndpoint(cycleEpoch, current, true);
        }
        JwksEndpoint resolved = resolution.endpoint();
        if (resolved != null) {
            // A cycle of a closed generation publishes nothing and fetches nothing
            return adoptResolution(resolution, cycleEpoch) ? resolved : null;
        }
        // Nothing to adopt: keep fetching the current endpoint. A rejection, including an advertised jwks_uri
        // no handler can be built for, is not retryable and suppresses further re-discovery.
        LOGGER.warn(WARN.JWKS_URI_RESOLUTION_FAILED);
        return keepCurrentEndpoint(cycleEpoch, current, !resolution.retryable());
    }

    /**
     * Logs {@code JWKS_URI_RESOLUTION_FAILED} for a re-discovery that failed with a refusal. Neither the
     * refusal nor its message is logged: the message can echo the IdP-advertised {@code jwks_uri}, which is
     * remote document content and can carry userinfo or query secrets. Only the type of the refusal is
     * kept, at debug level.
     *
     * @param failure the refusal that ended the re-discovery
     */
    private static void logRefusedAdvertisedUri(RuntimeException failure) {
        LOGGER.warn(WARN.JWKS_URI_RESOLUTION_FAILED);
        LOGGER.debug("Advertised jwks_uri refused during re-discovery (%s)", failure.getClass().getName());
    }

    /**
     * Keeps the current endpoint after a re-discovery that adopted nothing, suppressing further
     * re-discovery when the outcome was a rejection; both only while the cycle's generation is live. The
     * suppression is lifted by a successful key installation, by {@link #close()}, or by
     * {@link #recordFailedCycle(long)} once the current endpoint has failed
     * {@value #REDISCOVERY_FAILURE_THRESHOLD} further cycles.
     *
     * @param cycleEpoch the {@link #epoch} of the generation the calling refresh cycle belongs to
     * @param current    the endpoint the cycle fetches from when its generation is still live
     * @param rejected   whether the re-discovery was rejected, which suppresses further re-discovery
     * @return {@code current} while the generation is live, {@code null} once a {@link #close()} moved it on
     */
    private @Nullable JwksEndpoint keepCurrentEndpoint(long cycleEpoch, JwksEndpoint current, boolean rejected) {
        lifecycleLock.lock();
        try {
            if (!isLive(cycleEpoch)) {
                return null;
            }
            if (rejected) {
                rediscoverySuppressed.set(true);
            }
            return current;
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Applies the opt-in staleness bound after a refresh cycle that did not end with installed keys.
     * <p>
     * Acts only when a {@linkplain HttpJwksLoaderConfig#getMaxStaleness() maximum staleness} is
     * configured, the cycle's generation is still live, the status is {@link LoaderStatus#OK} and the
     * time since the last successful load exceeds the bound. It then sets the status to
     * {@link LoaderStatus#UNDEFINED} and logs {@code JWKS_KEYS_STALE}; since the status is no longer
     * {@code OK} afterwards, the warning is logged once per degradation. Installed and retired keys are
     * not touched, so {@link #getKeyInfo(String)} keeps answering, and the next cycle that installs
     * usable keys publishes {@code OK} again.
     *
     * @param cycleEpoch the {@link #epoch} of the generation the calling refresh cycle belongs to
     */
    private void degradeIfStale(long cycleEpoch) {
        Optional<Duration> maxStaleness = config.getMaxStaleness();
        if (maxStaleness.isEmpty()) {
            return;
        }
        lifecycleLock.lock();
        try {
            Instant lastLoad = lastSuccessfulLoad.get();
            if (!isLive(cycleEpoch) || status.get() != LoaderStatus.OK || lastLoad == null) {
                return;
            }
            if (Duration.between(lastLoad, Instant.now()).compareTo(maxStaleness.get()) > 0) {
                status.set(LoaderStatus.UNDEFINED);
                LOGGER.warn(WARN.JWKS_KEYS_STALE, loggableResolvedIssuer(), lastLoad, maxStaleness.get());
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Re-attempts the endpoint resolution for a refresh cycle that found no endpoint.
     * <p>
     * Only a well-known configuration can lack an endpoint after initialization (its discovery failed
     * transiently); in direct mode there is nothing to re-resolve, so the cycle is skipped with a
     * warning and fetches nothing. The resolved endpoint and issuer are
     * published only while the cycle's generation is still live. A retryable failure is logged and
     * leaves the status unchanged for the next cycle. A security rejection is terminal: the status
     * becomes {@link LoaderStatus#ERROR} and the refresh task is cancelled, so the rejection is not
     * re-evaluated (and not re-counted) on every cycle. An advertised {@code jwks_uri} no HTTP handler
     * can be built for is terminal in the same way and additionally logs {@code JWKS_URI_UNUSABLE} once,
     * when the status turns {@code ERROR}. Publishing {@code ERROR} and cancelling the task is one step
     * under {@link #lifecycleLock}. A resolution that completes after a
     * {@link #close()} moved the epoch on publishes nothing, changes no status and cancels nothing.
     *
     * @param cycleEpoch the {@link #epoch} of the generation the calling refresh cycle belongs to
     * @return the resolved endpoint, already published for later cycles, or {@code null} if this cycle
     *         has nothing to fetch from
     */
    private @Nullable JwksEndpoint reResolveEndpoint(long cycleEpoch) {
        if (config.getWellKnownConfig() == null) {
            LOGGER.warn(WARN.BACKGROUND_REFRESH_NO_HANDLER);
            return null;
        }
        AdapterResolution resolution = resolveJWKSAdapter(config.getIssuerIdentifier());
        boolean turnedTerminal = false;
        lifecycleLock.lock();
        try {
            // A cycle from before a close() must neither publish into nor cancel the task of a later
            // initialization.
            if (!adoptResolution(resolution, cycleEpoch)) {
                return null;
            }
            JwksEndpoint resolved = resolution.endpoint();
            if (resolved != null) {
                return resolved;
            }
            if (!resolution.retryable()) {
                // Publishing ERROR and cancelling the task is one step under the lock, gated by isLive
                turnedTerminal = status.getAndSet(LoaderStatus.ERROR) != LoaderStatus.ERROR;
                ScheduledFuture<?> task = refreshTask.get();
                if (task != null) {
                    task.cancel(false);
                }
            }
        } finally {
            lifecycleLock.unlock();
        }
        LOGGER.warn(WARN.JWKS_URI_RESOLUTION_FAILED);
        if (turnedTerminal && resolution.unusableUri()) {
            logUnusableUri(resolution);
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
            endpoint.set(null);
            currentJwksContent.set(null);
            lastSuccessfulLoad.set(null);
            consecutiveRefreshFailures.set(0);
            rediscoverySuppressed.set(false);
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
     * Returns the fetch URI of the current JWKS endpoint.
     * Package-private for testing purposes only.
     *
     * @return the fetch URI of the published endpoint, empty before the first successful resolution
     *         and after {@link #close()}
     */
    Optional<URI> getJwksFetchUri() {
        JwksEndpoint current = endpoint.get();
        return current == null ? Optional.empty() : Optional.of(current.fetchUri());
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
     * The resolved issuer as it is written into a log record. Without a configured issuer the loader
     * adopts the issuer the discovery document advertises, which is remote document content, so the
     * value is neutralized against log injection (CWE-117). {@link #getResolvedIssuer()} keeps the raw
     * value, since the issuer is also a functional value.
     *
     * @return the sanitized resolved issuer, {@code null} when none is resolved yet
     */
    private @Nullable String loggableResolvedIssuer() {
        return ErrorTextSanitizer.sanitize(getResolvedIssuer());
    }

    /**
     * The issuer of {@link #getIssuerIdentifier()} as it is written into a log record, neutralized
     * against log injection (CWE-117) for the same reason as {@link #loggableResolvedIssuer()}.
     *
     * @return the sanitized issuer, or {@code not-configured} when none is known
     */
    private @Nullable String loggableIssuerIdentifier() {
        return ErrorTextSanitizer.sanitize(getIssuerIdentifier().orElse(ISSUER_NOT_CONFIGURED));
    }

    /**
     * Resolves the issuer identifier from discovered and configured sources.
     * Implements the issuer resolution logic with precedence rules.
     * <p>
     * Design Decision (M7): The configured issuer is the operator's trust anchor. When a configured
     * issuer is present, the advertised (discovered) issuer must be present and must match it. An
     * advertised issuer that does not match, or an absent advertised issuer, is a
     * <em>hard discovery failure</em> — this method returns empty,
     * {@link #resolveJWKSAdapter(String)} aborts, and the advertised {@code jwks_uri} is never fetched.
     * Gating here, before the advertised URL is fetched, closes the SSRF-relevant window (COMMONS-5):
     * a tampered discovery document cannot steer the loader to fetch key material from an
     * attacker-chosen endpoint under the authority of an issuer we do not trust. A mismatch is
     * audited (WARN log + ISSUER_MISMATCH counter). A discovery document that carries a
     * {@code jwks_uri} but omits {@code issuer} is reported by the discovery layer as
     * {@link HttpWellKnownResolver.FailureReason#ISSUER_ABSENT}, and {@link #resolveJWKSAdapter(String)}
     * passes it here as an absent advertised issuer: with a configured issuer the advertised value is
     * logged as {@code <absent>} and ISSUER_MISMATCH is counted, and the resolution is a terminal
     * rejection. Comparison tolerates a trailing-slash difference, which is a common,
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
                // The configured issuer is operator-controlled; the advertised one is remote document
                // content and is neutralized against log injection (CWE-117).
                LOGGER.warn(WARN.ISSUER_MISMATCH, configuredIssuer,
                        discoveredIssuer == null ? ISSUER_ABSENT : ErrorTextSanitizer.sanitize(discoveredIssuer));
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
     * A resolved JWKS endpoint: the adapter and the fetch URI of one resolution, published together.
     *
     * @param adapter  the adapter fetching the JWKS
     * @param fetchUri the URI the adapter fetches, which passed the egress guard at resolution
     */
    private record JwksEndpoint(HttpAdapter<Jwks> adapter, URI fetchUri) {
    }

    /**
     * Outcome of {@link #resolveJWKSAdapter(String)}.
     *
     * @param endpoint  the resolved endpoint, {@code null} if the resolution failed
     * @param issuer    the resolved issuer, {@code null} when none was resolved; an egress-rejected
     *                  resolution still carries the issuer it resolved
     * @param retryable for a failed resolution, whether the failure is transient (the well-known
     *                  discovery yielded no JWKS URI) and may succeed on a later refresh cycle, as
     *                  opposed to a security rejection or an unusable advertised {@code jwks_uri}
     * @param unusableUri whether the resolution was rejected because the configuration cannot build an
     *                  HTTP handler for the advertised {@code jwks_uri} (a malformed URI, or a cleartext
     *                  {@code http} URI while insecure HTTP is not allowed); the refusal itself is not
     *                  kept, since its message echoes the advertised URI
     */
    private record AdapterResolution(@Nullable JwksEndpoint endpoint, @Nullable String issuer, boolean retryable,
    boolean unusableUri) {

        static final AdapterResolution RETRYABLE_FAILURE = new AdapterResolution(null, null, true, false);
        static final AdapterResolution REJECTED = new AdapterResolution(null, null, false, false);

        /**
         * A non-retryable rejection because no HTTP handler can be built for the advertised
         * {@code jwks_uri}.
         *
         * @param issuer the issuer the discovery resolved before the advertised URI was refused
         * @return the rejected resolution, carrying the issuer
         */
        static AdapterResolution unusableUriRejection(String issuer) {
            return new AdapterResolution(null, issuer, false, true);
        }
    }

    /**
     * Logs {@code JWKS_URI_UNUSABLE} for a resolution rejected because the advertised {@code jwks_uri}
     * cannot be used. The caller invokes it once per terminal transition to {@link LoaderStatus#ERROR}.
     * The reason is the fixed {@value #UNUSABLE_URI_REASON}: the refusal of the HTTP handler configuration
     * echoes the advertised URI, which is remote document content, so it is never written. The issuer is
     * neutralized against log injection (CWE-117), since it can be the advertised one.
     *
     * @param resolution the rejected resolution, {@link AdapterResolution#unusableUri()} must be true
     */
    private static void logUnusableUri(AdapterResolution resolution) {
        LOGGER.warn(WARN.JWKS_URI_UNUSABLE,
                ErrorTextSanitizer.sanitize(
                        Optional.ofNullable(resolution.issuer()).orElse(ISSUER_NOT_CONFIGURED)),
                UNUSABLE_URI_REASON);
    }
}
