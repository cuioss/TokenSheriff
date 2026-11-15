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
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the opt-in loader settings of {@link HttpJwksLoaderConfig} — the maximum staleness, the
 * unknown-{@code kid} refresh window and the initial-load retry — and the
 * {@link HttpJwksLoaderConfig#withIssuerIdentifier(String)} copy that must carry them and every other
 * setting. The endpoint, egress and parser settings are covered by {@link HttpJwksLoaderConfigTest}, the
 * timeout and TLS settings by {@link HttpJwksLoaderConfigTransportTest}.
 */
@EnableTestLogger
@DisplayName("Tests HttpJwksLoaderConfig opt-in settings and the issuer copy")
@SuppressWarnings("java:S5778")
// owolff: Suppressing because for a builder this is not a problem
class HttpJwksLoaderConfigUnknownKidTest {

    private static final String VALID_URL = "https://example.com/.well-known/jwks.json";
    private static final String WELL_KNOWN_URL = "https://example.com/.well-known/openid-configuration";
    private static final int REFRESH_INTERVAL = 60;

    @Test
    @DisplayName("withIssuerIdentifier should return a copy carrying the identifier and every other setting")
    void withIssuerIdentifierShouldCopyAllOtherSettings() throws Exception {
        var retryConfig = RetryConfig.builder().initialDelay(Duration.ofMillis(1)).build();
        var gracePeriod = Duration.ofMinutes(7);
        int maxRetiredKeySets = 5;
        SSLContext configured = SSLContext.getInstance("TLS");
        configured.init(null, null, null);
        // A cleartext discovery handler carries no TLS settings, so the SSL context of a handler derived
        // for a TLS jwks_uri can only come from the copied TLS settings.
        HttpJwksLoaderConfig original = HttpJwksLoaderConfig.builder()
                .wellKnownUrl("http://example.com/.well-known/openid-configuration")
                .allowInsecureHttp(true)
                .sslContext(configured)
                .refreshIntervalSeconds(REFRESH_INTERVAL)
                .retryConfig(retryConfig)
                .keyRotationGracePeriod(gracePeriod)
                .maxRetiredKeySets(maxRetiredKeySets)
                .allowLoopbackEgress(true)
                .build();

        HttpJwksLoaderConfig copy = original.withIssuerIdentifier("https://example.com");

        assertAll("only the issuer identifier differs",
                () -> assertSame(configured, original.getHttpHandler(VALID_URL).getSslContext(),
                        "the original must carry the configured TLS settings"),
                () -> assertSame(configured, copy.getHttpHandler(VALID_URL).getSslContext(),
                        "the copy must carry the TLS settings of the original"),
                () -> assertEquals(gracePeriod, copy.getKeyRotationGracePeriod()),
                () -> assertEquals(maxRetiredKeySets, copy.getMaxRetiredKeySets()),
                () -> assertNotSame(original, copy, "a new instance must be returned"),
                () -> assertNull(original.getIssuerIdentifier(), "the original must stay unchanged"),
                () -> assertEquals("https://example.com", copy.getIssuerIdentifier()),
                () -> assertEquals(REFRESH_INTERVAL, copy.getRefreshIntervalSeconds()),
                () -> assertSame(original.getWellKnownConfig(), copy.getWellKnownConfig()),
                () -> assertEquals(JwksType.WELL_KNOWN, copy.getJwksType()),
                () -> assertSame(retryConfig, copy.getRetryConfig()),
                () -> assertSame(original.getEgressPolicy(), copy.getEgressPolicy()),
                () -> assertSame(original.getScheduledExecutorService(), copy.getScheduledExecutorService()),
                () -> assertEquals(original.isOwnsExecutor(), copy.isOwnsExecutor()),
                () -> assertSame(original.getParserConfig(), copy.getParserConfig()),
                () -> assertEquals(original.getKeyRotationGracePeriod(), copy.getKeyRotationGracePeriod()),
                () -> assertEquals(original.getMaxRetiredKeySets(), copy.getMaxRetiredKeySets()));
    }

    @Test
    @DisplayName("withIssuerIdentifier should replace an identifier already present")
    void withIssuerIdentifierShouldReplaceExistingIdentifier() {
        HttpJwksLoaderConfig original = HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer")
                .build();

        HttpJwksLoaderConfig copy = original.withIssuerIdentifier("other-issuer");

        assertAll(
                () -> assertEquals("other-issuer", copy.getIssuerIdentifier()),
                () -> assertSame(original.getHttpHandler(), copy.getHttpHandler(),
                        "the direct JWKS handler must be shared"),
                () -> assertEquals("test-issuer", original.getIssuerIdentifier()));
    }

    @Test
    @DisplayName("withIssuerIdentifier should reject a null or blank identifier")
    void withIssuerIdentifierShouldRejectNullOrBlank() {
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .wellKnownUrl(WELL_KNOWN_URL)
                .build();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> config.withIssuerIdentifier(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> config.withIssuerIdentifier("")),
                () -> assertThrows(IllegalArgumentException.class, () -> config.withIssuerIdentifier("  ")));
    }

    @Nested
    @DisplayName("maxStaleness")
    class MaxStaleness {

        @Test
        @DisplayName("Should leave maxStaleness absent by default")
        void shouldLeaveMaxStalenessAbsentByDefault() {
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .jwksUrl(VALID_URL)
                    .issuerIdentifier("test-issuer")
                    .build();

            assertTrue(config.getMaxStaleness().isEmpty(), "maxStaleness must be opt-in");
        }

        @Test
        @DisplayName("Should accept a positive maxStaleness")
        void shouldAcceptPositiveMaxStaleness() {
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .jwksUrl(VALID_URL)
                    .issuerIdentifier("test-issuer")
                    .maxStaleness(Duration.ofHours(2))
                    .build();

            assertEquals(Optional.of(Duration.ofHours(2)), config.getMaxStaleness());
        }

        @Test
        @DisplayName("Should reject a null, zero or negative maxStaleness")
        void shouldRejectNonPositiveMaxStaleness() {
            var builder = HttpJwksLoaderConfig.builder();

            assertAll("non-positive bounds",
                    () -> assertThrows(IllegalArgumentException.class, () -> builder.maxStaleness(null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> builder.maxStaleness(Duration.ZERO)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> builder.maxStaleness(Duration.ofSeconds(-1))));
        }

        @Test
        @DisplayName("Should reject maxStaleness combined with refreshIntervalSeconds(0)")
        void shouldRejectMaxStalenessWithoutBackgroundRefresh() {
            var builder = HttpJwksLoaderConfig.builder()
                    .jwksUrl(VALID_URL)
                    .issuerIdentifier("test-issuer")
                    .refreshIntervalSeconds(0)
                    .maxStaleness(Duration.ofMinutes(30));

            var exception = assertThrows(IllegalArgumentException.class, builder::build);

            assertTrue(exception.getMessage().contains("maxStaleness(...) cannot be combined with refreshIntervalSeconds(0)"),
                    "the guard must name the conflict, but was: " + exception.getMessage());
        }

        @Test
        @DisplayName("withIssuerIdentifier should preserve maxStaleness")
        void withIssuerIdentifierShouldPreserveMaxStaleness() {
            HttpJwksLoaderConfig original = HttpJwksLoaderConfig.builder()
                    .wellKnownUrl(WELL_KNOWN_URL)
                    .maxStaleness(Duration.ofMinutes(45))
                    .build();

            HttpJwksLoaderConfig copy = original.withIssuerIdentifier("https://example.com");

            assertEquals(Optional.of(Duration.ofMinutes(45)), copy.getMaxStaleness());
        }
    }

    static Stream<Duration> invalidUnknownKidRefreshWindows() {
        return Stream.of(
                null,
                Duration.ZERO,
                Duration.ofSeconds(-1),
                Duration.ofSeconds(4),
                Duration.ofMillis(4999),
                Duration.ofSeconds(3601),
                Duration.ofSeconds(3600).plusNanos(1));
    }

    @Nested
    @DisplayName("unknownKidRefreshWindow")
    class UnknownKidRefreshWindow {

        @Test
        @DisplayName("Should leave the unknown-kid refresh window absent by default")
        void shouldLeaveWindowAbsentByDefault() {
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .jwksUrl(VALID_URL)
                    .issuerIdentifier("test-issuer")
                    .build();

            assertTrue(config.getUnknownKidRefreshWindow().isEmpty(), "the unknown-kid refresh must be opt-in");
        }

        @ParameterizedTest(name = "window {0}")
        @MethodSource("de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfigUnknownKidTest#invalidUnknownKidRefreshWindows")
        @DisplayName("Should reject a null, non-positive or out-of-range window")
        void shouldRejectInvalidWindow(Duration window) {
            var builder = HttpJwksLoaderConfig.builder();

            assertThrows(IllegalArgumentException.class, () -> builder.unknownKidRefreshWindow(window),
                    "only windows from 5 s to 3600 s inclusive are accepted");
        }

        @Test
        @DisplayName("Should accept the 5 s and 3600 s boundaries")
        void shouldAcceptBoundaryWindows() {
            HttpJwksLoaderConfig lower = HttpJwksLoaderConfig.builder()
                    .jwksUrl(VALID_URL)
                    .issuerIdentifier("test-issuer")
                    .unknownKidRefreshWindow(Duration.ofSeconds(5))
                    .build();
            HttpJwksLoaderConfig upper = HttpJwksLoaderConfig.builder()
                    .jwksUrl(VALID_URL)
                    .issuerIdentifier("test-issuer")
                    .unknownKidRefreshWindow(Duration.ofSeconds(3600))
                    .build();

            assertAll("both boundaries are inclusive",
                    () -> assertEquals(Optional.of(Duration.ofSeconds(5)), lower.getUnknownKidRefreshWindow()),
                    () -> assertEquals(Optional.of(Duration.ofSeconds(3600)), upper.getUnknownKidRefreshWindow()));
        }

        @Test
        @DisplayName("Should reject the window combined with refreshIntervalSeconds(0)")
        void shouldRejectWindowWithoutBackgroundRefresh() {
            var builder = HttpJwksLoaderConfig.builder()
                    .jwksUrl(VALID_URL)
                    .issuerIdentifier("test-issuer")
                    .refreshIntervalSeconds(0)
                    .unknownKidRefreshWindow(Duration.ofSeconds(30));

            var exception = assertThrows(IllegalArgumentException.class, builder::build);

            assertTrue(exception.getMessage().contains(
                            "unknownKidRefreshWindow(...) cannot be combined with refreshIntervalSeconds(0)"),
                    "the guard must name the conflict, but was: " + exception.getMessage());
        }

        @Test
        @DisplayName("Should accept the window with a positive refresh interval")
        void shouldAcceptWindowWithBackgroundRefresh() {
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .jwksUrl(VALID_URL)
                    .issuerIdentifier("test-issuer")
                    .refreshIntervalSeconds(REFRESH_INTERVAL)
                    .unknownKidRefreshWindow(Duration.ofSeconds(30))
                    .build();

            assertEquals(Optional.of(Duration.ofSeconds(30)), config.getUnknownKidRefreshWindow());
        }
    }

    @Nested
    @DisplayName("initialLoadRetry")
    class InitialLoadRetry {

        @Test
        @DisplayName("Should leave the initial-load retry disabled by default")
        void shouldLeaveRetryDisabledByDefault() {
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .jwksUrl(VALID_URL)
                    .issuerIdentifier("test-issuer")
                    .build();

            assertFalse(config.isInitialLoadRetry(), "the initial-load retry must be opt-in");
        }

        @Test
        @DisplayName("Should enable the initial-load retry when set")
        void shouldEnableRetryWhenSet() {
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                    .jwksUrl(VALID_URL)
                    .issuerIdentifier("test-issuer")
                    .initialLoadRetry(true)
                    .build();

            assertTrue(config.isInitialLoadRetry());
        }

        @Test
        @DisplayName("Should reject initialLoadRetry(true) combined with refreshIntervalSeconds(0)")
        void shouldRejectRetryWithoutBackgroundRefresh() {
            var builder = HttpJwksLoaderConfig.builder()
                    .jwksUrl(VALID_URL)
                    .issuerIdentifier("test-issuer")
                    .refreshIntervalSeconds(0)
                    .initialLoadRetry(true);

            var exception = assertThrows(IllegalArgumentException.class, builder::build);

            assertTrue(exception.getMessage().contains(
                            "initialLoadRetry(true) cannot be combined with refreshIntervalSeconds(0)"),
                    "the guard must name the conflict, but was: " + exception.getMessage());
        }

        @Test
        @DisplayName("Should accept initialLoadRetry(false) combined with refreshIntervalSeconds(0)")
        void shouldAcceptDisabledRetryWithoutBackgroundRefresh() {
            var builder = HttpJwksLoaderConfig.builder()
                    .jwksUrl(VALID_URL)
                    .issuerIdentifier("test-issuer")
                    .refreshIntervalSeconds(0)
                    .initialLoadRetry(false);

            assertDoesNotThrow(builder::build, "only an enabled retry needs the background refresh");
        }

        @Test
        @DisplayName("Should document the fixed retry delays")
        void shouldExposeFixedRetryDelays() {
            assertAll("1 s doubling to a 30 s cap",
                    () -> assertEquals(HttpJwksLoaderConfig.INITIAL_LOAD_RETRY_INITIAL_DELAY, Duration.ofSeconds(1)),
                    () -> assertEquals(2, HttpJwksLoaderConfig.INITIAL_LOAD_RETRY_BACKOFF_FACTOR),
                    () -> assertEquals(HttpJwksLoaderConfig.INITIAL_LOAD_RETRY_MAX_DELAY, Duration.ofSeconds(30)));
        }
    }

    @Test
    @DisplayName("withIssuerIdentifier should preserve the unknown-kid refresh window and the initial-load retry")
    void withIssuerIdentifierShouldPreserveOptInSettings() {
        HttpJwksLoaderConfig original = HttpJwksLoaderConfig.builder()
                .wellKnownUrl(WELL_KNOWN_URL)
                .unknownKidRefreshWindow(Duration.ofSeconds(42))
                .initialLoadRetry(true)
                .build();
        HttpJwksLoaderConfig defaults = HttpJwksLoaderConfig.builder()
                .wellKnownUrl(WELL_KNOWN_URL)
                .build();

        HttpJwksLoaderConfig copy = original.withIssuerIdentifier("https://example.com");
        HttpJwksLoaderConfig defaultsCopy = defaults.withIssuerIdentifier("https://example.com");

        assertAll("the copy carries both opt-in settings unchanged",
                () -> assertEquals(Optional.of(Duration.ofSeconds(42)), copy.getUnknownKidRefreshWindow()),
                () -> assertTrue(copy.isInitialLoadRetry()),
                () -> assertTrue(defaultsCopy.getUnknownKidRefreshWindow().isEmpty()),
                () -> assertFalse(defaultsCopy.isInitialLoadRetry()));
    }
}
