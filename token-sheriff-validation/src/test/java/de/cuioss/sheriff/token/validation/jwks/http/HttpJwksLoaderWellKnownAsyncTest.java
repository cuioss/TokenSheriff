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

import de.cuioss.http.client.adapter.RetryConfig;
import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.validation.JWTValidationLogMessages;
import de.cuioss.sheriff.token.validation.test.dispatcher.JwksResolveDispatcher;
import de.cuioss.sheriff.token.validation.test.dispatcher.WellKnownDispatcher;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.dispatcher.CombinedDispatcher;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@DisplayName("HttpJwksLoader Well-Known Discovery Async Tests")
@EnableMockWebServer
class HttpJwksLoaderWellKnownAsyncTest {

    /**
     * The default number of attempts, so the retry path is exercised in full, with millisecond delays
     * instead of the default 1 + 2 + 4 + 8 s backoff.
     */
    private static final RetryConfig FAST_RETRY = RetryConfig.builder()
            .initialDelay(Duration.ofMillis(1))
            .maxDelay(Duration.ofMillis(5))
            .build();

    @Test
    @DisplayName("Constructor should not perform well-known discovery")
    void constructorShouldNotPerformWellKnownDiscovery() {
        // Create a well-known configuration with invalid URL to test constructor behavior
        String invalidWellKnownUrl = "https://invalid-host.example.com/.well-known/openid_configuration";

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                .wellKnownUrl(invalidWellKnownUrl)
                .issuerIdentifier("https://invalid-host.example.com")
                .build();

        // Measure constructor time
        long startTime = System.nanoTime();
        try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
            long constructorDurationNanos = System.nanoTime() - startTime;

            // Constructor must not perform well-known discovery (blocking I/O). The 200ms bound is
            // a proxy (real discovery of an invalid host would take seconds) that tolerates a GC
            // pause under parallel surefire forks; the UNDEFINED status below is the precise check.
            assertTrue(constructorDurationNanos < TimeUnit.MILLISECONDS.toNanos(200),
                    "Constructor should complete quickly without well-known discovery");

            assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus(),
                    "Status should remain UNDEFINED until initialization");
        }
    }

    @Test
    @DisplayName("Transient well-known discovery failure with background refresh leaves UNDEFINED and schedules the refresh")
    void wellKnownDiscoveryFailureShouldBeHandledAsync() {
        HttpJwksLoaderConfig config = unreachableWellKnownConfig().build();

        try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
            SecurityEventCounter counter = new SecurityEventCounter();

            // Constructor should still be fast even with invalid well-known URL. A config of its own:
            // closing a loader shuts down the executor its config owns.
            HttpJwksLoaderConfig probeConfig = unreachableWellKnownConfig().build();
            long startTime = System.nanoTime();
            try (HttpJwksLoader ignored = new HttpJwksLoader(probeConfig)) {
                long constructorDuration = System.nanoTime() - startTime;

                assertTrue(constructorDuration < TimeUnit.MILLISECONDS.toNanos(10),
                        "Constructor should be fast even with invalid well-known configuration");
            }

            // Async initialization should handle the failure
            CompletableFuture<LoaderStatus> initFuture = loader.initJWKSLoader(counter);

            LoaderStatus status = initFuture.join();
            assertAll("a transient discovery failure is retried by the background refresh",
                    () -> assertEquals(LoaderStatus.UNDEFINED, status),
                    () -> assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus()),
                    () -> assertTrue(loader.isBackgroundRefreshActive(), "the background refresh must be scheduled"));

            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    JWTValidationLogMessages.WARN.JWKS_URI_RESOLUTION_FAILED.resolveIdentifierString());
        }
    }

    @Test
    @DisplayName("Well-known discovery failure without background refresh is a terminal ERROR")
    void wellKnownDiscoveryFailureWithoutRefreshShouldBeError() {
        HttpJwksLoaderConfig config = unreachableWellKnownConfig().refreshIntervalSeconds(0).build();

        try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
            LoaderStatus status = loader.initJWKSLoader(new SecurityEventCounter()).join();

            assertAll("without refresh nothing can recover the loader",
                    () -> assertEquals(LoaderStatus.ERROR, status),
                    () -> assertEquals(LoaderStatus.ERROR, loader.getLoaderStatus()),
                    () -> assertFalse(loader.isBackgroundRefreshActive(), "no background refresh may be scheduled"));
        }
    }

    @Test
    @DisplayName("Background refresh resolves the adapter once discovery recovers and reaches OK")
    void backgroundRefreshShouldRecoverFromTransientDiscoveryFailure() throws Exception {
        WellKnownDispatcher wellKnownDispatcher = new WellKnownDispatcher();
        JwksResolveDispatcher jwksDispatcher = new JwksResolveDispatcher();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new CombinedDispatcher(wellKnownDispatcher, jwksDispatcher));
            server.start();
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                    .wellKnownUrl(server.url(WellKnownDispatcher.LOCAL_PATH).toString())
                    .retryConfig(FAST_RETRY)
                    .build();
            SecurityEventCounter counter = new SecurityEventCounter();
            wellKnownDispatcher.returnError();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                assertEquals(LoaderStatus.UNDEFINED, loader.initJWKSLoader(counter).join(),
                        "a discovery endpoint that is down at init leaves the loader UNDEFINED");
                assertTrue(loader.isBackgroundRefreshActive(), "the background refresh must be scheduled");

                wellKnownDispatcher.returnDefault();
                loader.performBackgroundRefresh();

                assertAll("the refresh cycle resolves the adapter and loads the keys",
                        () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()),
                        () -> assertFalse(loader.getKeyIds().isEmpty(), "keys must be loaded"),
                        () -> assertTrue(jwksDispatcher.getCallCounter() >= 1, "the advertised jwks_uri must be fetched"),
                        () -> assertEquals(0, counter.getCount(SecurityEventCounter.EventType.ISSUER_MISMATCH)));
            }
        }
    }

    @Test
    @DisplayName("A discovery document without an issuer is a terminal ERROR with no refresh, counted as ISSUER_MISMATCH")
    void issuerlessDocumentIsTerminalRejection() throws Exception {
        WellKnownDispatcher wellKnownDispatcher = new WellKnownDispatcher().returnMissingIssuer();
        JwksResolveDispatcher jwksDispatcher = new JwksResolveDispatcher();
        try (MockWebServer server = startServer(wellKnownDispatcher, jwksDispatcher)) {
            HttpJwksLoaderConfig config = wellKnownConfig(server)
                    .issuerIdentifier(server.url("/").toString())
                    .build();
            SecurityEventCounter counter = new SecurityEventCounter();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                LoaderStatus status = loader.initJWKSLoader(counter).join();

                assertAll("an absent advertised issuer is rejected, not retried",
                        () -> assertEquals(LoaderStatus.ERROR, status),
                        () -> assertEquals(LoaderStatus.ERROR, loader.getLoaderStatus()),
                        () -> assertFalse(loader.isBackgroundRefreshActive(), "no background refresh may be scheduled"),
                        () -> assertEquals(0, jwksDispatcher.getCallCounter(), "the advertised jwks_uri must never be fetched"),
                        () -> assertEquals(1, counter.getCount(SecurityEventCounter.EventType.ISSUER_MISMATCH)));
                LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                        JWTValidationLogMessages.WARN.ISSUER_MISMATCH.resolveIdentifierString());
            }
        }
    }

    @Test
    @DisplayName("An issuer-less document found by a refresh cycle cancels the refresh and ends in ERROR")
    void issuerlessDocumentOnRefreshCancelsRefresh() throws Exception {
        WellKnownDispatcher wellKnownDispatcher = new WellKnownDispatcher().returnError();
        JwksResolveDispatcher jwksDispatcher = new JwksResolveDispatcher();
        try (MockWebServer server = startServer(wellKnownDispatcher, jwksDispatcher)) {
            try (HttpJwksLoader loader = new HttpJwksLoader(wellKnownConfig(server).build())) {
                assertEquals(LoaderStatus.UNDEFINED, loader.initJWKSLoader(new SecurityEventCounter()).join(),
                        "a discovery endpoint that is down at init is retried");
                assertTrue(loader.isBackgroundRefreshActive(), "the background refresh must be scheduled");

                wellKnownDispatcher.returnMissingIssuer();
                loader.performBackgroundRefresh();

                assertAll("the rejection found by the refresh is terminal",
                        () -> assertEquals(LoaderStatus.ERROR, loader.getLoaderStatus()),
                        () -> assertFalse(loader.isBackgroundRefreshActive(), "the refresh task must be cancelled"),
                        () -> assertEquals(0, jwksDispatcher.getCallCounter(), "the advertised jwks_uri must never be fetched"));
            }
        }
    }

    @Test
    @DisplayName("A discovery document without jwks_uri stays retryable and recovers once fixed")
    void documentWithoutJwksUriStaysRetryable() throws Exception {
        assertRetryableAndRecovers(new WellKnownDispatcher().returnMissingJwksUri());
    }

    @Test
    @DisplayName("A discovery document lacking both issuer and jwks_uri is the missing jwks_uri case and stays retryable")
    void documentWithoutIssuerAndJwksUriStaysRetryable() throws Exception {
        assertRetryableAndRecovers(new WellKnownDispatcher().returnMissingIssuerAndJwksUri());
    }

    private static void assertRetryableAndRecovers(WellKnownDispatcher wellKnownDispatcher) throws Exception {
        JwksResolveDispatcher jwksDispatcher = new JwksResolveDispatcher();
        try (MockWebServer server = startServer(wellKnownDispatcher, jwksDispatcher)) {
            try (HttpJwksLoader loader = new HttpJwksLoader(wellKnownConfig(server).build())) {
                LoaderStatus status = loader.initJWKSLoader(new SecurityEventCounter()).join();

                assertAll("an unusable document without the issuer signal is retried",
                        () -> assertEquals(LoaderStatus.UNDEFINED, status),
                        () -> assertTrue(loader.isBackgroundRefreshActive(), "the background refresh must be scheduled"));

                wellKnownDispatcher.returnDefault();
                loader.performBackgroundRefresh();

                assertAll("the fixed IdP brings the loader to OK",
                        () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()),
                        () -> assertFalse(loader.getKeyIds().isEmpty(), "keys must be loaded"));
            }
        }
    }

    private static MockWebServer startServer(WellKnownDispatcher wellKnownDispatcher, JwksResolveDispatcher jwksDispatcher)
            throws Exception {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new CombinedDispatcher(wellKnownDispatcher, jwksDispatcher));
        server.start();
        return server;
    }

    private static HttpJwksLoaderConfig.HttpJwksLoaderConfigBuilder wellKnownConfig(MockWebServer server) {
        return HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                .wellKnownUrl(server.url(WellKnownDispatcher.LOCAL_PATH).toString())
                .retryConfig(FAST_RETRY);
    }

    private static HttpJwksLoaderConfig.HttpJwksLoaderConfigBuilder unreachableWellKnownConfig() {
        return HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                .wellKnownUrl("https://invalid-host.example.com/.well-known/openid_configuration")
                .issuerIdentifier("https://invalid-host.example.com")
                .retryConfig(FAST_RETRY);
    }
}
