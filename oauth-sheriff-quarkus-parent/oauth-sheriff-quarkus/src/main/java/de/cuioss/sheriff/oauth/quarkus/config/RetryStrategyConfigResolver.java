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

import de.cuioss.http.client.adapter.RetryConfig;
import org.eclipse.microprofile.config.Config;

import java.time.Duration;

/**
 * Resolver for creating RetryConfig instances from Quarkus configuration.
 * <p>
 * This resolver creates {@link RetryConfig} instances based on Quarkus configuration properties,
 * providing configurable retry behavior for HTTP operations in JWT validation.
 * </p>
 * <p>
 * The resolver supports configuration of exponential backoff parameters including:
 * <ul>
 *   <li>Maximum retry attempts</li>
 *   <li>Initial delay and maximum delay</li>
 *   <li>Backoff multiplier and jitter factor</li>
 *   <li>Global enable/disable flag</li>
 * </ul>
 *
 * @since 1.0
 */
public class RetryStrategyConfigResolver {

    private final Config config;

    /**
     * Creates a new RetryStrategyConfigResolver with the specified configuration.
     *
     * @param config the configuration instance to use for property resolution
     */
    public RetryStrategyConfigResolver(Config config) {
        this.config = config;
    }

    /**
     * Resolves a RetryConfig instance from configuration properties.
     * <p>
     * Creates a retry configuration with exponential backoff parameters configured
     * through Quarkus properties. If retry is disabled globally, returns a
     * configuration with maxAttempts=0 that performs no retries.
     * </p>
     *
     * @return configured RetryConfig instance
     */

    public RetryConfig resolveRetryConfig() {
        // Check if retry is disabled globally
        boolean retryEnabled = config.getOptionalValue(JwtPropertyKeys.RETRY.ENABLED, Boolean.class)
                .orElse(true);

        if (!retryEnabled) {
            // maxAttempts=1 means "try once, no retries" (effectively disabled)
            return RetryConfig.builder()
                    .maxAttempts(1)
                    .build();
        }

        // Build exponential backoff configuration from properties
        return RetryConfig.builder()
                .maxAttempts(config.getOptionalValue(JwtPropertyKeys.RETRY.MAX_ATTEMPTS, Integer.class)
                        .orElse(5))
                .initialDelay(Duration.ofMillis(config.getOptionalValue(JwtPropertyKeys.RETRY.INITIAL_DELAY_MS, Long.class)
                        .orElse(1000L)))
                .maxDelay(Duration.ofMillis(config.getOptionalValue(JwtPropertyKeys.RETRY.MAX_DELAY_MS, Long.class)
                        .orElse(30000L)))
                .multiplier(config.getOptionalValue(JwtPropertyKeys.RETRY.BACKOFF_MULTIPLIER, Double.class)
                        .orElse(2.0))
                .jitter(config.getOptionalValue(JwtPropertyKeys.RETRY.JITTER_FACTOR, Double.class)
                        .orElse(0.1))
                .build();
    }
}