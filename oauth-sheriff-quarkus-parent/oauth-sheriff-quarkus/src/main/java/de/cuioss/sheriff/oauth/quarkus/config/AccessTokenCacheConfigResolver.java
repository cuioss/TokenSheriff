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
package de.cuioss.sheriff.oauth.quarkus.config;

import de.cuioss.sheriff.oauth.core.cache.AccessTokenCacheConfig;
import de.cuioss.tools.logging.CuiLogger;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.Config;

import static de.cuioss.sheriff.oauth.quarkus.OAuthSheriffQuarkusLogMessages.INFO;
import static de.cuioss.sheriff.oauth.quarkus.config.JwtPropertyKeys.CACHE;

/**
 * Resolves {@link AccessTokenCacheConfig} from Quarkus configuration.
 * <p>
 * This resolver reads cache configuration properties and creates
 * AccessTokenCacheConfig instances. The configuration includes:
 * <ul>
 *   <li>Maximum cache size (maxSize)</li>
 *   <li>Eviction interval in seconds</li>
 * </ul>
 *
 * @since 1.0
 */
@RequiredArgsConstructor
public class AccessTokenCacheConfigResolver {

    private static final CuiLogger LOGGER = new CuiLogger(AccessTokenCacheConfigResolver.class);

    private final Config config;

    /**
     * Resolves the access token cache configuration from the Quarkus configuration.
     * <p>
     * Uses default values from the library if properties are not configured.
     * If maxSize is configured as 0, caching is disabled.
     * </p>
     *
     * @return The resolved AccessTokenCacheConfig
     */
    public AccessTokenCacheConfig resolveCacheConfig() {
        LOGGER.info(INFO.RESOLVING_ACCESS_TOKEN_CACHE_CONFIG);

        // Get max size with default value of 1000
        int maxSize = config.getOptionalValue(CACHE.MAX_SIZE, Integer.class)
                .orElse(1000);

        // Get eviction interval with default value of 10 seconds
        long evictionIntervalSeconds = config.getOptionalValue(CACHE.EVICTION_INTERVAL_SECONDS, Long.class)
                .orElse(10L);

        if (maxSize == 0) {
            LOGGER.info(INFO.ACCESS_TOKEN_CACHE_DISABLED);
            return AccessTokenCacheConfig.disabled();
        }

        AccessTokenCacheConfig cacheConfig = AccessTokenCacheConfig.builder()
                .maxSize(maxSize)
                .evictionIntervalSeconds(evictionIntervalSeconds)
                .build();

        LOGGER.info(INFO.ACCESS_TOKEN_CACHE_CONFIGURED, maxSize, evictionIntervalSeconds);

        return cacheConfig;
    }
}