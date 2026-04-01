/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.oauth.core.cache;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Configuration for AccessTokenCache that provides all defaults and factory methods.
 * <p>
 * This configuration class encapsulates all cache settings and provides a convenient
 * way to create configured cache instances. When cache size is set to 0, caching
 * is effectively disabled.
 * <p>
 * Usage example:
 * <pre>
 * // Default configuration
 * AccessTokenCacheConfig config = AccessTokenCacheConfig.defaultConfig();
 *
 * // Custom configuration
 * AccessTokenCacheConfig config = AccessTokenCacheConfig.builder()
 *     .maxSize(500)
 *     .evictionIntervalSeconds(600L)
 *     .build();
 *
 * // Disabled cache
 * AccessTokenCacheConfig config = AccessTokenCacheConfig.builder()
 *     .maxSize(0)
 *     .build();
 *
 * // Create cache instance
 * AccessTokenCache cache = new AccessTokenCache(config, securityEventCounter, 60);
 * </pre>
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@Builder
@Getter
public class AccessTokenCacheConfig {

    /**
     * Default maximum number of tokens to cache.
     */
    public static final int DEFAULT_MAX_SIZE = 1000;

    /**
     * Default interval for background eviction in seconds.
     */
    public static final long DEFAULT_EVICTION_INTERVAL_SECONDS = 10;

    /**
     * The maximum number of tokens to cache.
     * When set to 0, caching is disabled and createCache returns null.
     */
    @Builder.Default
    private final int maxSize = DEFAULT_MAX_SIZE;

    /**
     * The interval in seconds between background eviction runs.
     * Only relevant when maxSize > 0.
     */
    @Builder.Default
    private final long evictionIntervalSeconds = DEFAULT_EVICTION_INTERVAL_SECONDS;

    /**
     * The scheduled executor service for background tasks.
     * If not provided, a default executor will be created when maxSize > 0.
     */
    @Getter
    @EqualsAndHashCode.Exclude
    private final ScheduledExecutorService scheduledExecutorService;

    /**
     * Creates a default configuration with standard settings.
     *
     * @return a default AccessTokenCacheConfig with maxSize=1000 and evictionInterval=300s
     */
    public static AccessTokenCacheConfig defaultConfig() {
        return AccessTokenCacheConfig.builder().build();
    }

    /**
     * Creates a disabled cache configuration.
     *
     * @return a configuration that will create no cache (maxSize=0)
     */
    public static AccessTokenCacheConfig disabled() {
        return AccessTokenCacheConfig.builder()
                .maxSize(0)
                .build();
    }


    /**
     * Checks if caching is enabled based on the configuration.
     *
     * @return true if caching is enabled (maxSize > 0), false otherwise
     */
    public boolean isCachingEnabled() {
        return maxSize > 0;
    }

    /**
     * Gets the configured or default scheduled executor service.
     * Creates a default executor if none was provided and caching is enabled.
     *
     * @return the scheduled executor service or null if caching is disabled
     */
    public ScheduledExecutorService getOrCreateScheduledExecutorService() {
        if (!isCachingEnabled()) {
            return null;
        }
        return Objects.requireNonNullElseGet(scheduledExecutorService, () -> Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "AccessTokenCache-Eviction");
            thread.setDaemon(true);
            return thread;
        }));
    }
}