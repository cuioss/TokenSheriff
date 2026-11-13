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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the opt-in per-issuer {@code jwks.http.unknown-kid-refresh-window-seconds} and
 * {@code jwks.http.initial-load-retry} keys of {@link IssuerConfigResolver}: absent they leave both loader
 * behaviours off, set they reach the loader configuration, and an invalid, out-of-range, conflicting or
 * orphaned value fails startup (ADR-0026, ADR-0030).
 */
@DisplayName("IssuerConfigResolver unknown-kid refresh window and initial-load retry")
class IssuerConfigResolverJwksLoaderOptInTest {

    private static final String ISSUER = "optin";
    private static final String ISSUER_IDENTIFIER = "https://optin.example.com";
    private static final String WINDOW_PROPERTY =
            JwtPropertyKeys.ISSUERS.UNKNOWN_KID_REFRESH_WINDOW_SECONDS.formatted(ISSUER);
    private static final String RETRY_PROPERTY = JwtPropertyKeys.ISSUERS.INITIAL_LOAD_RETRY.formatted(ISSUER);
    private static final String REFRESH_INTERVAL_PROPERTY =
            JwtPropertyKeys.ISSUERS.REFRESH_INTERVAL_SECONDS.formatted(ISSUER);
    private static final String EMPTY_JWKS = "{\"keys\":[]}";

    private static Map<String, String> issuerWithJwksUrl() {
        Map<String, String> properties = new HashMap<>();
        properties.put(JwtPropertyKeys.ISSUERS.ENABLED.formatted(ISSUER), "true");
        properties.put(JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(ISSUER), ISSUER_IDENTIFIER);
        properties.put(JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(ISSUER), "https://optin.example.com/jwks");
        properties.put(JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(ISSUER), "true");
        return properties;
    }

    private static Map<String, String> issuerWithWellKnownUrl() {
        Map<String, String> properties = issuerWithJwksUrl();
        properties.remove(JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(ISSUER));
        properties.put(JwtPropertyKeys.ISSUERS.WELL_KNOWN_URL.formatted(ISSUER),
                "https://optin.example.com/.well-known/openid-configuration");
        return properties;
    }

    private static Map<String, String> issuerWithFilePath(Path jwksFile) {
        Map<String, String> properties = issuerWithJwksUrl();
        properties.remove(JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(ISSUER));
        properties.put(JwtPropertyKeys.ISSUERS.JWKS_FILE_PATH.formatted(ISSUER), jwksFile.toString());
        return properties;
    }

    private static Map<String, String> issuerWithInlineContent() {
        Map<String, String> properties = issuerWithJwksUrl();
        properties.remove(JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(ISSUER));
        properties.put(JwtPropertyKeys.ISSUERS.JWKS_CONTENT.formatted(ISSUER), EMPTY_JWKS);
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

    /**
     * Each HTTP-only property with a value that is valid on an HTTP issuer.
     */
    static Stream<Arguments> httpOnlyProperties() {
        return Stream.of(
                Arguments.of(WINDOW_PROPERTY, "60"),
                Arguments.of(RETRY_PROPERTY, "true"));
    }

    @Nested
    @DisplayName("Default off")
    class DefaultOff {

        @Test
        @DisplayName("should configure neither opt-in when both keys are absent")
        void shouldConfigureNeitherWhenAbsent() {
            HttpJwksLoaderConfig loaderConfig = loaderConfigFor(issuerWithJwksUrl());

            assertAll("Loader opt-ins",
                    () -> assertTrue(loaderConfig.getUnknownKidRefreshWindow().isEmpty(),
                            "Without the key an unknown kid must never cause a fetch"),
                    () -> assertFalse(loaderConfig.isInitialLoadRetry(),
                            "Without the key a failed initial load must be left to the periodic refresh"));
        }

        @Test
        @DisplayName("should resolve the issuer when both keys are absent")
        void shouldResolveIssuerWhenAbsent() {
            List<IssuerConfig> result = resolverFor(issuerWithJwksUrl()).resolveIssuerConfigs();

            assertEquals(1, result.size(), "The issuer must resolve without the keys");
        }
    }

    @Nested
    @DisplayName("Keys set")
    class KeysSet {

        @Test
        @DisplayName("should pass the configured window to the loader configuration")
        void shouldPassWindowToLoaderConfig() {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.put(WINDOW_PROPERTY, "60");

            HttpJwksLoaderConfig loaderConfig = loaderConfigFor(properties);

            assertEquals(Optional.of(Duration.ofSeconds(60)), loaderConfig.getUnknownKidRefreshWindow(),
                    "The configured window must reach the loader configuration");
        }

        @Test
        @DisplayName("should pass the configured window for a well-known issuer")
        void shouldPassWindowForWellKnownIssuer() {
            Map<String, String> properties = issuerWithWellKnownUrl();
            properties.put(WINDOW_PROPERTY, " 120 ");

            HttpJwksLoaderConfig loaderConfig = loaderConfigFor(properties);

            assertEquals(Optional.of(Duration.ofSeconds(120)), loaderConfig.getUnknownKidRefreshWindow(),
                    "The window applies to well-known discovery as well, surrounding whitespace ignored");
        }

        @Test
        @DisplayName("should enable the initial-load retry on the loader configuration")
        void shouldEnableRetryOnLoaderConfig() {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.put(RETRY_PROPERTY, "true");

            HttpJwksLoaderConfig loaderConfig = loaderConfigFor(properties);

            assertTrue(loaderConfig.isInitialLoadRetry(), "The configured retry must reach the loader configuration");
        }

        @Test
        @DisplayName("should enable the initial-load retry for a well-known issuer")
        void shouldEnableRetryForWellKnownIssuer() {
            Map<String, String> properties = issuerWithWellKnownUrl();
            properties.put(RETRY_PROPERTY, " TRUE ");

            HttpJwksLoaderConfig loaderConfig = loaderConfigFor(properties);

            assertTrue(loaderConfig.isInitialLoadRetry(),
                    "The retry applies to well-known discovery as well, case and surrounding whitespace ignored");
        }

        @Test
        @DisplayName("should keep the initial-load retry off for an explicit false")
        void shouldKeepRetryOffForExplicitFalse() {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.put(RETRY_PROPERTY, "false");

            HttpJwksLoaderConfig loaderConfig = loaderConfigFor(properties);

            assertFalse(loaderConfig.isInitialLoadRetry(), "An explicit false must leave the retry off");
        }

        @Test
        @DisplayName("should resolve the issuer when both keys are set")
        void shouldResolveIssuerWhenSet() {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.put(WINDOW_PROPERTY, "60");
            properties.put(RETRY_PROPERTY, "true");

            List<IssuerConfig> result = resolverFor(properties).resolveIssuerConfigs();

            assertEquals(1, result.size(), "Valid opt-ins must not prevent the issuer from resolving");
        }
    }

    @Nested
    @DisplayName("Startup failure on the value")
    class InvalidValue {

        @ParameterizedTest(name = "value ''{0}''")
        @ValueSource(strings = {"abc", "1.5", "PT1M", "0", "-1", "-60"})
        @DisplayName("should fail startup for an unparseable or non-positive window")
        void shouldFailForUnparseableOrNonPositiveWindow(String value) {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.put(WINDOW_PROPERTY, value);
            IssuerConfigResolver resolver = resolverFor(properties);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    resolver::resolveIssuerConfigs);

            assertTrue(exception.getMessage().contains(WINDOW_PROPERTY),
                    "The failure must name the offending property: " + exception.getMessage());
        }

        @ParameterizedTest(name = "value ''{0}''")
        @ValueSource(strings = {"yes", "1", "on", "enabled"})
        @DisplayName("should fail startup for a retry value that is not a boolean")
        void shouldFailForNonBooleanRetry(String value) {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.put(RETRY_PROPERTY, value);
            IssuerConfigResolver resolver = resolverFor(properties);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    resolver::resolveIssuerConfigs);

            assertTrue(exception.getMessage().contains(RETRY_PROPERTY),
                    "The failure must name the offending property: " + exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Builder validation")
    class BuilderValidation {

        @ParameterizedTest(name = "{0} seconds")
        @ValueSource(strings = {"4", "3601"})
        @DisplayName("should fail startup for a window outside 5 to 3600 seconds")
        void shouldFailForWindowOutOfRange(String value) {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.put(WINDOW_PROPERTY, value);
            IssuerConfigResolver resolver = resolverFor(properties);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    resolver::resolveIssuerConfigs);

            assertTrue(exception.getMessage().contains("unknownKidRefreshWindow"),
                    "The builder must refuse the out-of-range window: " + exception.getMessage());
        }

        @ParameterizedTest(name = "{0} seconds")
        @ValueSource(longs = {5, 3600})
        @DisplayName("should accept a window on the range boundaries")
        void shouldAcceptWindowOnBoundary(long seconds) {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.put(WINDOW_PROPERTY, String.valueOf(seconds));

            HttpJwksLoaderConfig loaderConfig = loaderConfigFor(properties);

            assertEquals(Optional.of(Duration.ofSeconds(seconds)), loaderConfig.getUnknownKidRefreshWindow(),
                    "Both range boundaries are inclusive");
        }

        @Test
        @DisplayName("should fail startup for a window combined with a refresh interval of 0")
        void shouldFailForWindowWithZeroRefreshInterval() {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.put(WINDOW_PROPERTY, "60");
            properties.put(REFRESH_INTERVAL_PROPERTY, "0");
            IssuerConfigResolver resolver = resolverFor(properties);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    resolver::resolveIssuerConfigs);

            assertAll("Conflict message",
                    () -> assertTrue(exception.getMessage().contains("unknownKidRefreshWindow(...)"),
                            "The failure must name the window: " + exception.getMessage()),
                    () -> assertTrue(exception.getMessage().contains("refreshIntervalSeconds(0)"),
                            "The failure must name the conflicting refresh interval: " + exception.getMessage()));
        }

        @Test
        @DisplayName("should fail startup for the retry combined with a refresh interval of 0")
        void shouldFailForRetryWithZeroRefreshInterval() {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.put(RETRY_PROPERTY, "true");
            properties.put(REFRESH_INTERVAL_PROPERTY, "0");
            IssuerConfigResolver resolver = resolverFor(properties);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    resolver::resolveIssuerConfigs);

            assertAll("Conflict message",
                    () -> assertTrue(exception.getMessage().contains("initialLoadRetry(true)"),
                            "The failure must name the retry: " + exception.getMessage()),
                    () -> assertTrue(exception.getMessage().contains("refreshIntervalSeconds(0)"),
                            "The failure must name the conflicting refresh interval: " + exception.getMessage()));
        }

        @Test
        @DisplayName("should resolve a refresh interval of 0 when neither opt-in is enabled")
        void shouldResolveZeroRefreshIntervalWithoutOptIns() {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.put(RETRY_PROPERTY, "false");
            properties.put(REFRESH_INTERVAL_PROPERTY, "0");

            List<IssuerConfig> result = resolverFor(properties).resolveIssuerConfigs();

            assertEquals(1, result.size(),
                    "Only an enabled opt-in conflicts with a refresh interval of 0, an explicit false does not");
        }
    }

    @Nested
    @DisplayName("Orphaned on an issuer without HTTP JWKS loading")
    class Orphaned {

        @TempDir
        Path tempDir;

        private Path jwksFile() throws IOException {
            return Files.writeString(tempDir.resolve("jwks.json"), EMPTY_JWKS);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.cuioss.sheriff.token.quarkus.config.IssuerConfigResolverJwksLoaderOptInTest#httpOnlyProperties")
        @DisplayName("should fail startup for the property on an issuer with a JWKS file path")
        void shouldFailWhenOrphanedOnFilePathIssuer(String property, String value) throws IOException {
            Map<String, String> properties = issuerWithFilePath(jwksFile());
            properties.put(property, value);
            IssuerConfigResolver resolver = resolverFor(properties);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    resolver::resolveIssuerConfigs);

            assertTrue(exception.getMessage().contains(property),
                    "The failure must name the orphaned property: " + exception.getMessage());
        }

        @Test
        @DisplayName("should resolve the issuer with a JWKS file path when neither property is set")
        void shouldResolveFilePathIssuerWithoutProperties() throws IOException {
            List<IssuerConfig> result = resolverFor(issuerWithFilePath(jwksFile())).resolveIssuerConfigs();

            assertEquals(1, result.size(), "A file-path issuer without the HTTP-only properties must resolve");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.cuioss.sheriff.token.quarkus.config.IssuerConfigResolverJwksLoaderOptInTest#httpOnlyProperties")
        @DisplayName("should fail startup for the property on an issuer with inline JWKS content")
        void shouldFailWhenOrphanedOnContentIssuer(String property, String value) {
            Map<String, String> properties = issuerWithInlineContent();
            properties.put(property, value);
            IssuerConfigResolver resolver = resolverFor(properties);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    resolver::resolveIssuerConfigs);

            assertTrue(exception.getMessage().contains(property),
                    "The failure must name the orphaned property: " + exception.getMessage());
        }

        @Test
        @DisplayName("should resolve the issuer with inline JWKS content when neither property is set")
        void shouldResolveContentIssuerWithoutProperties() {
            List<IssuerConfig> result = resolverFor(issuerWithInlineContent()).resolveIssuerConfigs();

            assertEquals(1, result.size(), "An inline-content issuer without the HTTP-only properties must resolve");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.cuioss.sheriff.token.quarkus.config.IssuerConfigResolverJwksLoaderOptInTest#httpOnlyProperties")
        @DisplayName("should resolve the issuer for the same property on an HTTP issuer")
        void shouldResolveSamePropertyOnHttpIssuer(String property, String value) {
            Map<String, String> properties = issuerWithJwksUrl();
            properties.put(property, value);

            List<IssuerConfig> result = resolverFor(properties).resolveIssuerConfigs();

            assertEquals(1, result.size(), "The same property is valid on an issuer that loads its JWKS over HTTP");
        }
    }
}
