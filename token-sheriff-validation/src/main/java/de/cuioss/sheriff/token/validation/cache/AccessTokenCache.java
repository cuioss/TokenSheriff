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
package de.cuioss.sheriff.token.validation.cache;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.validation.JWTValidationLogMessages;
import de.cuioss.sheriff.token.validation.domain.context.ValidationContext;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.metrics.MeasurementType;
import de.cuioss.sheriff.token.validation.metrics.MetricsTicker;
import de.cuioss.sheriff.token.validation.metrics.MetricsTickerFactory;
import de.cuioss.sheriff.token.validation.metrics.TokenValidatorMonitor;
import de.cuioss.sheriff.token.validation.util.Sha256Util;
import de.cuioss.tools.logging.CuiLogger;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe cache for validated access tokens using optimistic caching strategy.
 * <p>
 * This cache stores successfully validated access tokens to avoid redundant validation
 * of the same tokens. It uses integer hashCode for cache keys to optimize performance
 * while maintaining security through raw token verification on cache hits.
 * <p>
 * Features:
 * <ul>
 *   <li>SHA-256 digest of tokens for cache keys (collision-resistant)</li>
 *   <li>Simple get/put API for explicit caching control</li>
 *   <li>Lock-free concurrent access via ConcurrentHashMap</li>
 *   <li>Configurable maximum cache size with automatic overflow eviction</li>
 *   <li>Issuer time policy enforcement on retrieval (expiry and maximum token age)</li>
 *   <li>Background thread for periodic cleanup of expired and too-old tokens</li>
 *   <li>Security event tracking for cache hits</li>
 *   <li>No external dependencies (Quarkus compatible)</li>
 * </ul>
 * <p>
 * Each entry carries the time policy of the issuer that validated it — that issuer's own
 * clock skew tolerance and maximum token age, taken from the per-issuer
 * {@link ValidationContext} passed to {@link #put}. Every cache hit re-applies that policy,
 * so the cache never returns a token the uncached validation pipeline would reject at the
 * same instant.
 * <p>
 * The cache verifies token content on each hit to handle potential hash collisions.
 * JWTs self-expire via their exp claim, reducing need for complex eviction strategies.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public class AccessTokenCache {

    private static final CuiLogger LOGGER = new CuiLogger(AccessTokenCache.class);

    /**
     * The maximum number of tokens to cache.
     */
    private final int maxSize;

    /**
     * The main cache storage using ConcurrentHashMap for thread safety.
     * Key: SHA-256 hex digest of token string (collision-resistant)
     * Value: CachedToken wrapper
     */
    private final Map<String, CachedToken> cache;

    /**
     * Security event counter for tracking cache hits.
     */

    private final SecurityEventCounter securityEventCounter;

    /**
     * Background executor for expired token eviction.
     */
    private final ScheduledExecutorService evictionExecutor;

    /**
     * Whether this cache created {@link #evictionExecutor} itself.
     * Only internally created executors are shut down by {@link #shutdown()};
     * caller-supplied executors are left running.
     */
    private final boolean ownsEvictionExecutor;

    /**
     * The scheduled eviction task, cancelled on {@link #shutdown()} when the
     * executor is caller-supplied (and therefore not shut down).
     */
    private final ScheduledFuture<?> evictionTask;

    /**
     * Creates a new AccessTokenCache with the specified configuration.
     *
     * @param config the cache configuration
     * @param securityEventCounter the security event counter for tracking cache hits
     */
    public AccessTokenCache(
            AccessTokenCacheConfig config,
            SecurityEventCounter securityEventCounter) {

        config.validate();
        this.maxSize = config.getMaxSize();
        this.securityEventCounter = securityEventCounter;

        if (config.isCachingEnabled()) {
            // Only initialize cache structures when caching is enabled
            this.cache = new ConcurrentHashMap<>(this.maxSize);

            // Use the caller-supplied executor if configured, otherwise create an internally
            // owned one. Only internally created executors are shut down by shutdown().
            ScheduledExecutorService configuredExecutor = config.getScheduledExecutorService();
            if (configuredExecutor != null) {
                this.evictionExecutor = configuredExecutor;
                this.ownsEvictionExecutor = false;
            } else {
                this.evictionExecutor = config.createScheduledExecutorService();
                this.ownsEvictionExecutor = true;
            }

            this.evictionTask = this.evictionExecutor.scheduleWithFixedDelay(
                    this::evictExpiredTokens,
                    config.getEvictionIntervalSeconds(),
                    config.getEvictionIntervalSeconds(),
                    TimeUnit.SECONDS);

            LOGGER.debug("AccessTokenCache initialized with maxSize=%s, evictionInterval=%ss",
                    this.maxSize, config.getEvictionIntervalSeconds());
        } else {
            // Cache disabled - no cache structures or background threads needed
            this.cache = null;
            this.evictionExecutor = null;
            this.ownsEvictionExecutor = false;
            this.evictionTask = null;
            LOGGER.debug("AccessTokenCache disabled (maxSize=0) - no executor started");
        }
    }

    /**
     * Retrieves a cached access token if present and valid.
     * <p>
     * This method checks if a token exists in cache and whether its raw token matches.
     * A verified hit is then evaluated against the time policy of the issuer that
     * validated it (captured at store time):
     * <ul>
     *   <li>If expired under the issuer's clock skew tolerance, the entry is removed and a
     *   {@link SecurityEventCounter.EventType#TOKEN_EXPIRED} exception is thrown.</li>
     *   <li>If older than the issuer's maximum token age (plus clock skew), the entry is
     *   removed and a {@link SecurityEventCounter.EventType#TOKEN_AGE_EXCEEDED} exception
     *   is thrown.</li>
     *   <li>Otherwise the cached token is returned and the cache hit counter is incremented.</li>
     * </ul>
     *
     * @param tokenString the raw JWT token string to look up
     * @param performanceMonitor the monitor for recording CACHE_LOOKUP metrics
     * @param now the current time to use for the time policy checks (avoids per-call OffsetDateTime.now())
     * @return Optional containing the cached token if found and valid, empty otherwise
     * @throws TokenValidationException if the cached token is expired or exceeds its issuer's maximum token age
     */
    public Optional<AccessTokenContent> get(
            String tokenString,
            TokenValidatorMonitor performanceMonitor,
            OffsetDateTime now) {

        if (cache == null) {
            return Optional.empty();
        }

        // Create metrics ticker for cache lookup
        MetricsTicker lookupTicker = MetricsTickerFactory.createStartedTicker(MeasurementType.CACHE_LOOKUP, performanceMonitor);

        // Generate collision-resistant cache key from token string
        String cacheKey = sha256Hex(tokenString);

        // Try to get existing cached value
        CachedToken existing = cache.get(cacheKey);
        lookupTicker.stopAndRecord();

        if (existing != null) {
            if (!existing.verifyToken(tokenString)) {
                // Token verification failed - defense-in-depth for SHA-256 collision (negligible probability)
                cache.remove(cacheKey, existing);
                LOGGER.debug("Cached token verification failed - hash collision detected");
            } else if (existing.isExpired(now)) {
                // Token is expired under its issuer's clock skew - remove from cache and throw exception
                cache.remove(cacheKey, existing);
                LOGGER.warn(JWTValidationLogMessages.WARN.TOKEN_EXPIRED);
                securityEventCounter.increment(SecurityEventCounter.EventType.TOKEN_EXPIRED);
                throw new TokenValidationException(
                        SecurityEventCounter.EventType.TOKEN_EXPIRED,
                        "Cached token is expired"
                );
            } else if (existing.isTooOld(now)) {
                // Token exceeds its issuer's maximum token age - remove from cache and throw exception
                cache.remove(cacheKey, existing);
                LOGGER.warn(JWTValidationLogMessages.WARN.TOKEN_AGE_EXCEEDED);
                securityEventCounter.increment(SecurityEventCounter.EventType.TOKEN_AGE_EXCEEDED);
                throw new TokenValidationException(
                        SecurityEventCounter.EventType.TOKEN_AGE_EXCEEDED,
                        "Cached token exceeds the maximum token age"
                );
            } else {
                // True cache hit - valid cached token under its issuer's time policy
                securityEventCounter.increment(SecurityEventCounter.EventType.ACCESS_TOKEN_CACHE_HIT);
                return Optional.of(existing.getContent());
            }
        }

        // Cache miss
        return Optional.empty();
    }

    /**
     * Stores a validated access token in the cache.
     * <p>
     * Wraps the token in a CachedToken and stores it using putIfAbsent to handle
     * concurrent storage attempts. If multiple threads attempt to store the same
     * token simultaneously, only the first succeeds.
     * <p>
     * The issuer time policy of the given validation context — its clock skew tolerance
     * and maximum token age — is stored with the entry and re-applied on every cache hit
     * and by the background eviction.
     *
     * @param tokenString the raw JWT token string as cache key
     * @param content the validated access token content to cache
     * @param issuerContext the per-issuer validation context the token was validated with,
     *                      providing the issuer's clock skew and maximum token age
     * @param performanceMonitor the monitor for recording CACHE_STORE metrics
     * @throws TokenValidationException if the token cannot be cached
     */
    public void put(
            String tokenString,
            AccessTokenContent content,
            ValidationContext issuerContext,
            TokenValidatorMonitor performanceMonitor) {

        if (cache == null) {
            return;
        }

        // Start metrics for cache store operation
        MetricsTicker storeTicker = MetricsTickerFactory.createStartedTicker(MeasurementType.CACHE_STORE, performanceMonitor);

        // Use try-finally to ensure ticker is always stopped, even on unexpected exceptions
        try {
            // Generate collision-resistant cache key from token string
            String cacheKey = sha256Hex(tokenString);

            // Wrap validated token in CachedToken for storage
            // Note: expirationTime is guaranteed to be present because tokens are validated
            // before caching, and validation requires a valid exp claim
            OffsetDateTime expirationTime = content.getExpirationDateTime();

            CachedToken newCachedToken = CachedToken.builder()
                    .rawToken(tokenString)
                    .content(content)
                    .expirationTime(expirationTime)
                    .clockSkewSeconds(issuerContext.getClockSkewSeconds())
                    .maxTokenAgeSeconds(issuerContext.getMaxTokenAgeSeconds())
                    .build();

            // putIfAbsent: only store if no value exists (handles concurrent validation races)
            // If another thread already stored, their value wins - we silently discard ours
            CachedToken previous = cache.putIfAbsent(cacheKey, newCachedToken);

            if (previous == null) {
                // Successfully stored - we won the race (or no race occurred)
                LOGGER.debug("Token cached, current size: %s", cache.size());

                // Enforce size limit after successful insertion
                if (cache.size() > maxSize) {
                    enforceSize();
                }
            } else {
                // Another thread won the race and already stored this token
                LOGGER.debug("Token already cached by concurrent thread");
            }
        } finally {
            // Always stop and record metrics, even if exceptions occur
            storeTicker.stopAndRecord();
        }
    }


    /**
     * Enforces cache size limit by evicting entries when cache exceeds maxSize.
     * <p>
     * Evicts 10% of entries (minimum 1) when cache is full to reduce eviction frequency.
     * Uses iteration order for eviction. Since JWTs self-expire, complex eviction
     * strategies are unnecessary - expired tokens are removed by background cleanup.
     */
    private void enforceSize() {
        if (cache != null && cache.size() >= maxSize) {
            // Calculate batch size: evict 10% or at least 1 entry
            int batchSize = Math.max(1, maxSize / 10);
            int evicted = 0;

            // Iterate and remove first N entries (random based on HashMap iteration order)
            var iterator = cache.entrySet().iterator();
            while (iterator.hasNext() && evicted < batchSize) {
                iterator.next();
                iterator.remove();
                evicted++;
            }

            LOGGER.debug("Evicted %s tokens from cache due to size limit (current size: %s)", evicted, cache.size());
        }
    }

    /**
     * Background task to evict expired and too-old tokens.
     * <p>
     * Scans cache for tokens that are expired or exceed their maximum token age under the
     * time policy of their own issuer, and removes them in batches.
     * Only called when cache is enabled (maxSize > 0) and executor is configured.
     * <p>
     * Note: ConcurrentHashMap operations are thread-safe and don't throw
     * IllegalStateException or SecurityException under normal circumstances.
     */
    private void evictExpiredTokens() {
        OffsetDateTime now = OffsetDateTime.now();
        List<String> expiredKeys = new ArrayList<>();

        // Collect expired or too-old keys (thread-safe iteration)
        for (Map.Entry<String, CachedToken> entry : cache.entrySet()) {
            CachedToken cachedToken = entry.getValue();
            if (cachedToken.isExpired(now) || cachedToken.isTooOld(now)) {
                expiredKeys.add(entry.getKey());
            }
        }

        if (!expiredKeys.isEmpty()) {
            // Batch remove from cache (thread-safe operations)
            expiredKeys.forEach(cache::remove);
            LOGGER.debug("Evicted %s expired or too-old tokens from cache", expiredKeys.size());
        }
    }

    /**
     * Shuts down the cache and its background eviction.
     * Should be called when the cache is no longer needed.
     * <p>
     * The eviction executor is only shut down if it was created internally by this cache.
     * A caller-supplied executor (configured via
     * {@link AccessTokenCacheConfig#getScheduledExecutorService()}) is left running —
     * only the periodic eviction task scheduled on it is cancelled.
     */
    public void shutdown() {
        if (ownsEvictionExecutor && evictionExecutor != null) {
            evictionExecutor.shutdown();
            try {
                if (!evictionExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    evictionExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                evictionExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        } else if (evictionTask != null) {
            // Caller-supplied executor: cancel our periodic task but leave the executor running
            evictionTask.cancel(false);
        }

        if (cache != null) {
            cache.clear();
        }
        LOGGER.debug("AccessTokenCache shut down");
    }

    /**
     * Computes the SHA-256 digest of the given token string and returns it as a hex string.
     * SHA-256 provides collision-resistant cache keys, eliminating the risk of
     * accidental collisions that {@code String.hashCode()} (32-bit) would have.
     *
     * @param tokenString the raw JWT token string
     * @return hex-encoded SHA-256 digest
     */
    private static String sha256Hex(String tokenString) {
        byte[] hash = Sha256Util.digest(tokenString.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    /**
     * Gets the current cache size.
     * Package-private for testing purposes.
     *
     * @return the number of tokens currently cached
     */
    int size() {
        return cache != null ? cache.size() : 0;
    }

    /**
     * Gets the eviction executor.
     * Package-private for testing purposes.
     *
     * @return the eviction executor, or null if caching is disabled
     */
    ScheduledExecutorService evictionExecutor() {
        return evictionExecutor;
    }


}