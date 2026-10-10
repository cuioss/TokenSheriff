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
package de.cuioss.sheriff.token.commons.transport;

import de.cuioss.http.client.adapter.RetryConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests HttpJwksLoaderConfig Well-Known Configuration")
class HttpJwksLoaderConfigWellKnownTest {

    private static final String TEST_WELL_KNOWN_URL = "https://example.com/.well-known/openid-configuration";
    private static final URI TEST_WELL_KNOWN_URI = URI.create(TEST_WELL_KNOWN_URL);
    private static final String TEST_JWKS_URL = "https://example.com/jwks";

    @Test
    @DisplayName("Should create config with well-known URL")
    void shouldCreateConfigWithWellKnownUrl() {
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL)
                .build();

        assertNotNull(config.getWellKnownConfig());
        // HttpJwksLoaderConfig now implements HttpHandlerProvider, so getHttpHandler() returns the HttpHandler from WellKnownConfig
        assertNotNull(config.getHttpHandler(), "HttpHandler should be accessible via HttpHandlerProvider interface in well-known mode");
    }

    @Test
    @DisplayName("Should create config with well-known URI")
    void shouldCreateConfigWithWellKnownUri() {
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .wellKnownUri(TEST_WELL_KNOWN_URI)
                .build();

        assertNotNull(config.getWellKnownConfig());
        // HttpJwksLoaderConfig now implements HttpHandlerProvider, so getHttpHandler() returns the HttpHandler from WellKnownConfig
        assertNotNull(config.getHttpHandler(), "HttpHandler should be accessible via HttpHandlerProvider interface in well-known mode");
    }

    @Test
    @DisplayName("Should enforce mutual exclusivity between JWKS URL and well-known URL")
    void shouldEnforceMutualExclusivityJwksAndWellKnown() {
        HttpJwksLoaderConfig.HttpJwksLoaderConfigBuilder builder = HttpJwksLoaderConfig.builder()
                .jwksUrl(TEST_JWKS_URL);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                builder.wellKnownUrl(TEST_WELL_KNOWN_URL));

        assertTrue(exception.getMessage().contains("mutually exclusive"));
    }

    @Test
    @DisplayName("Should enforce mutual exclusivity between well-known URL and JWKS URL")
    void shouldEnforceMutualExclusivityWellKnownAndJwks() {
        HttpJwksLoaderConfig.HttpJwksLoaderConfigBuilder builder = HttpJwksLoaderConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                builder.jwksUrl(TEST_JWKS_URL));

        assertTrue(exception.getMessage().contains("mutually exclusive"));
    }

    @Test
    @DisplayName("Should enforce mutual exclusivity between well-known URL and URI")
    void shouldEnforceMutualExclusivityWellKnownUrlAndUri() {
        HttpJwksLoaderConfig.HttpJwksLoaderConfigBuilder builder = HttpJwksLoaderConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                builder.wellKnownUri(TEST_WELL_KNOWN_URI));

        assertTrue(exception.getMessage().contains("mutually exclusive"));
    }

    @Test
    @DisplayName("Should fail when no endpoint configured")
    void shouldFailWhenNoEndpointConfigured() {
        HttpJwksLoaderConfig.HttpJwksLoaderConfigBuilder builder = HttpJwksLoaderConfig.builder();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(exception.getMessage().contains("No JWKS endpoint configured"));
        assertTrue(exception.getMessage().contains("wellKnownUrl()"));
        assertTrue(exception.getMessage().contains("wellKnownUri()"));
    }

    @Test
    @DisplayName("Should create default executor for well-known configuration")
    void shouldCreateDefaultExecutorForWellKnown() {
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL)
                .refreshIntervalSeconds(60)
                .build();

        assertNotNull(config.getScheduledExecutorService());
    }

    @Test
    @DisplayName("Should hand the configured retry configuration to well-known discovery")
    void shouldPropagateConfiguredRetryConfigToWellKnownDiscovery() {
        RetryConfig configured = RetryConfig.builder()
                .maxAttempts(2)
                .initialDelay(Duration.ofMillis(7))
                .maxDelay(Duration.ofMillis(11))
                .build();

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL)
                .retryConfig(configured)
                .build();

        assertAll("configured retry configuration",
                () -> assertSame(configured, config.getRetryConfig(),
                        "The JWKS fetch must use the configured retry configuration"),
                () -> assertSame(configured, config.getWellKnownConfig().getRetryConfig(),
                        "Well-known discovery must use the configured retry configuration, not the defaults"));
    }

    @Test
    @DisplayName("Should fall back to the default retry configuration for well-known discovery")
    void shouldUseDefaultRetryConfigForWellKnownDiscoveryWhenNoneConfigured() {
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL)
                .build();

        assertAll("default retry configuration",
                () -> assertEquals(RetryConfig.defaults(), config.getRetryConfig(),
                        "The JWKS fetch must default to RetryConfig.defaults()"),
                () -> assertEquals(RetryConfig.defaults(), config.getWellKnownConfig().getRetryConfig(),
                        "Well-known discovery must default to RetryConfig.defaults()"));
    }
}
