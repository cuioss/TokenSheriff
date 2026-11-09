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
package de.cuioss.sheriff.token.quarkus.config;

import de.cuioss.http.client.adapter.RetryConfig;
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.quarkus.test.TestConfig;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the opt-in per-issuer {@code jwks.http.max-staleness-seconds} key of {@link IssuerConfigResolver}:
 * absent it leaves the loader without a staleness bound, set it reaches the loader configuration, and an
 * invalid or orphaned value fails startup (ADR-0017, ADR-0026).
 */
@DisplayName("IssuerConfigResolver maximum key staleness")
class IssuerConfigResolverMaxStalenessTest {

    private static final String ISSUER = "stale";
    private static final String ISSUER_IDENTIFIER = "https://stale.example.com";
    private static final String MAX_STALENESS_PROPERTY = JwtPropertyKeys.ISSUERS.MAX_STALENESS_SECONDS.formatted(ISSUER);

    private static Map<String, String> issuerWithJwksUrl() {
        Map<String, String> properties = new HashMap<>();
        properties.put(JwtPropertyKeys.ISSUERS.ENABLED.formatted(ISSUER), "true");
        properties.put(JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(ISSUER), ISSUER_IDENTIFIER);
        properties.put(JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(ISSUER), "https://stale.example.com/jwks");
        properties.put(JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(ISSUER), "true");
        return properties;
    }

    private static IssuerConfigResolver resolverFor(Map<String, String> properties) {
        return new IssuerConfigResolver(new TestConfig(properties), RetryConfig.defaults(), null, null);
    }

    private static HttpJwksLoaderConfig loaderConfigFor(Map<String, String> properties) {
        return resolverFor(properties).createHttpJwksLoaderConfig(ISSUER,
                properties.get(JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(ISSUER)),
                properties.get(JwtPropertyKeys.ISSUERS.WELL_KNOWN_URL.formatted(ISSUER)),
                Optional.of(ISSUER_IDENTIFIER));
    }

    @Nested
    @DisplayName("Default off")
    class DefaultOff {

        @Test
        @DisplayName("should configure no staleness bound when the key is absent")
        void shouldConfigureNoBoundWhenAbsent() {
            HttpJwksLoaderConfig loaderConfig = loaderConfigFor(issuerWithJwksUrl());

            assertTrue(loaderConfig.getMaxStaleness().isEmpty(),
                    "Without the key the loader must keep serving installed keys while refreshes fail");
        }

        @Test
        @DisplayName("should resolve the issuer when the key is absent")
        void shouldResolveIssuerWhenAbsent() {
            List<IssuerConfig> result = resolverFor(issuerWithJwksUrl()).resolveIssuerConfigs();

            assertEquals(1, result.size(), "The issuer must resolve without the key");
        }
    }

    @Nested
    @DisplayName("Key set")
    class KeySet {

        @Test
        @DisplayName("should pass the configured bound to the loader configuration")
        void shouldPassBoundToLoaderConfig() {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.put(MAX_STALENESS_PROPERTY, "3600");

            HttpJwksLoaderConfig loaderConfig = loaderConfigFor(properties);

            assertEquals(Optional.of(Duration.ofSeconds(3600)), loaderConfig.getMaxStaleness(),
                    "The configured key must reach the loader configuration");
        }

        @Test
        @DisplayName("should pass the configured bound for a well-known issuer")
        void shouldPassBoundForWellKnownIssuer() {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.remove(JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(ISSUER));
            properties.put(JwtPropertyKeys.ISSUERS.WELL_KNOWN_URL.formatted(ISSUER),
                    "https://stale.example.com/.well-known/openid-configuration");
            properties.put(MAX_STALENESS_PROPERTY, " 7200 ");

            HttpJwksLoaderConfig loaderConfig = loaderConfigFor(properties);

            assertEquals(Optional.of(Duration.ofSeconds(7200)), loaderConfig.getMaxStaleness(),
                    "The key applies to well-known discovery as well, surrounding whitespace ignored");
        }

        @Test
        @DisplayName("should resolve the issuer when the key is set")
        void shouldResolveIssuerWhenSet() {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.put(MAX_STALENESS_PROPERTY, "3600");

            List<IssuerConfig> result = resolverFor(properties).resolveIssuerConfigs();

            assertEquals(1, result.size(), "A valid bound must not prevent the issuer from resolving");
        }
    }

    @Nested
    @DisplayName("Startup failure")
    class StartupFailure {

        @ParameterizedTest(name = "value ''{0}''")
        @ValueSource(strings = {"abc", "1.5", "PT1H", "0", "-1", "-3600"})
        @DisplayName("should fail startup for an unparseable or non-positive value")
        void shouldFailForUnparseableOrNonPositiveValue(String value) {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.put(MAX_STALENESS_PROPERTY, value);
            IssuerConfigResolver resolver = resolverFor(properties);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    resolver::resolveIssuerConfigs);

            assertTrue(exception.getMessage().contains(MAX_STALENESS_PROPERTY),
                    "The failure must name the offending property: " + exception.getMessage());
        }

        @Test
        @DisplayName("should fail startup for a value combined with a refresh interval of 0")
        void shouldFailWithZeroRefreshInterval() {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.put(MAX_STALENESS_PROPERTY, "3600");
            properties.put(JwtPropertyKeys.ISSUERS.REFRESH_INTERVAL_SECONDS.formatted(ISSUER), "0");
            IssuerConfigResolver resolver = resolverFor(properties);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    resolver::resolveIssuerConfigs);

            assertTrue(exception.getMessage().contains("refreshIntervalSeconds(0)"),
                    "The failure must name the conflicting refresh interval: " + exception.getMessage());
        }

        @Test
        @DisplayName("should fail startup for a value on an issuer with a JWKS file path")
        void shouldFailWhenOrphanedOnFilePathIssuer() {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.remove(JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(ISSUER));
            properties.put(JwtPropertyKeys.ISSUERS.JWKS_FILE_PATH.formatted(ISSUER), "src/test/resources/jwks.json");
            properties.put(MAX_STALENESS_PROPERTY, "3600");
            IssuerConfigResolver resolver = resolverFor(properties);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    resolver::resolveIssuerConfigs);

            assertTrue(exception.getMessage().contains(MAX_STALENESS_PROPERTY),
                    "The failure must name the orphaned property: " + exception.getMessage());
        }

        @Test
        @DisplayName("should fail startup for a value on an issuer with inline JWKS content")
        void shouldFailWhenOrphanedOnContentIssuer() {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.remove(JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(ISSUER));
            properties.put(JwtPropertyKeys.ISSUERS.JWKS_CONTENT.formatted(ISSUER), "{\"keys\":[]}");
            properties.put(MAX_STALENESS_PROPERTY, "3600");
            IssuerConfigResolver resolver = resolverFor(properties);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    resolver::resolveIssuerConfigs);

            assertTrue(exception.getMessage().contains(MAX_STALENESS_PROPERTY),
                    "The failure must name the orphaned property: " + exception.getMessage());
        }
    }
}
