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

import de.cuioss.http.client.HttpLogMessages;
import de.cuioss.http.client.adapter.RetryConfig;
import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.EgressPolicy;
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.validation.JWTValidationLogMessages;
import de.cuioss.sheriff.token.validation.jwks.key.KeyInfo;
import de.cuioss.sheriff.token.validation.test.InMemoryJWKSFactory;
import de.cuioss.sheriff.token.validation.test.dispatcher.JwksResolveDispatcher;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.TestLoggerFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@DisplayName("HttpJwksLoader Failure Tests")
@EnableMockWebServer
class HttpJwksLoaderFailureTest {

    private static final String TEST_KID = InMemoryJWKSFactory.DEFAULT_KEY_ID;

    @Getter
    private final JwksResolveDispatcher moduleDispatcher = new JwksResolveDispatcher();

    private SecurityEventCounter securityEventCounter;

    @BeforeEach
    void setUp() {
        moduleDispatcher.setCallCounter(0);
        moduleDispatcher.returnDefault();
        securityEventCounter = new SecurityEventCounter();
    }

    /**
     * Returns a loopback port that was free a moment ago: an ephemeral port is bound and released again,
     * so a connection attempt to it is refused.
     */
    private static int closedLoopbackPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    @Test
    @DisplayName("Should log JWKS_LOAD_FAILED when HTTP connection cannot be established")
    void shouldLogJwksLoadFailedWhenHttpConnectionFails() throws Exception {
        // Default number of attempts, so the retry path is exercised in full, with millisecond delays
        // instead of the default 1 + 2 + 4 + 8 s backoff.
        RetryConfig fastRetry = RetryConfig.builder()
                .initialDelay(Duration.ofMillis(1))
                .maxDelay(Duration.ofMillis(5))
                .build();

        // A loopback port nothing listens on: the connection is refused without any DNS lookup
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                .jwksUrl("http://127.0.0.1:%d/jwks".formatted(closedLoopbackPort()))
                .issuerIdentifier("test-issuer")
                .retryConfig(fastRetry)
                .build();

        try (HttpJwksLoader failingLoader = new HttpJwksLoader(config)) {
            // Wait for async initialization to complete (even if it fails)
            failingLoader.initJWKSLoader(securityEventCounter).join();

            // Try to get a key, which should fail
            Optional<KeyInfo> keyInfo = failingLoader.getKeyInfo(TEST_KID);
            assertFalse(keyInfo.isPresent(), "Key info should not be present when connection fails");

            // Verify the appropriate error was logged
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                    JWTValidationLogMessages.ERROR.JWKS_LOAD_FAILED.resolveIdentifierString());

            // The rendered record carries the issuer first and a non-blank error detail second
            String renderedPrefix = "Failed to load JWKS for issuer test-issuer: ";
            var loadFailures = TestLoggerFactory.getTestHandler()
                    .resolveLogMessagesContaining(TestLogLevel.ERROR, renderedPrefix);
            assertEquals(1, loadFailures.size(), "JWKS_LOAD_FAILED must render the issuer exactly once");
            String rendered = loadFailures.getFirst().getMessage();
            String errorDetail = rendered.substring(rendered.indexOf(renderedPrefix) + renderedPrefix.length());
            assertAll("JWKS_LOAD_FAILED carries the error detail after the issuer",
                    () -> assertFalse(errorDetail.isBlank(), "the error detail must not be dropped: " + rendered),
                    () -> assertFalse(rendered.contains("%s"), "no placeholder may stay unfilled: " + rendered));

            // cui-http 2.0.0 classifies an unreachable host as a network error and logs
            // REQUEST_FAILED_MAX_ATTEMPTS at WARN once retries are exhausted.
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    HttpLogMessages.WARN.REQUEST_FAILED_MAX_ATTEMPTS.resolveIdentifierString());
        }
    }

    @Test
    @DisplayName("JWKS_LOAD_FAILED renders an advertised issuer carrying CR and LF in escaped form")
    void jwksLoadFailedEscapesLineBreaksInAdvertisedIssuer() throws Exception {
        String jwksPath = "/jwks-forged-issuer.json";
        DiscoveryGenerationDispatcher dispatcher = new DiscoveryGenerationDispatcher(List.of(jwksPath));
        // JSON escapes, so the discovery layer decodes them to a real CR and LF in the advertised issuer
        dispatcher.advertiseIssuerFrom(0, "https://idp.example\\r\\nFORGED-RECORD");
        dispatcher.failJwks(jwksPath);
        RetryConfig fastRetry = RetryConfig.builder()
                .initialDelay(Duration.ofMillis(1))
                .maxDelay(Duration.ofMillis(5))
                .build();

        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(dispatcher);
            server.start();
            // No configured issuer: the loader adopts the issuer the discovery document advertises
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                    .wellKnownUrl(server.url(DiscoveryGenerationDispatcher.WELL_KNOWN_PATH).toString())
                    .retryConfig(fastRetry)
                    .refreshIntervalSeconds(0)
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                assertEquals(LoaderStatus.ERROR, loader.initJWKSLoader(securityEventCounter).get(10, TimeUnit.SECONDS));

                assertEquals(Optional.of("https://idp.example\r\nFORGED-RECORD"), loader.getIssuerIdentifier(),
                        "the advertised issuer reaches the loader with its line break and stays raw as a functional value");
                var loadFailures = TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(TestLogLevel.ERROR,
                        JWTValidationLogMessages.ERROR.JWKS_LOAD_FAILED.resolveIdentifierString());
                assertEquals(1, loadFailures.size(), "JWKS_LOAD_FAILED is logged once");
                String rendered = loadFailures.getFirst().getMessage();
                assertAll("the advertised issuer cannot forge a log line",
                        () -> assertTrue(rendered.contains("for issuer https://idp.example\\r\\nFORGED-RECORD: "),
                                "the issuer must be rendered escaped: " + rendered),
                        () -> assertFalse(rendered.contains("\r"), "no raw CR may be rendered: " + rendered),
                        () -> assertFalse(rendered.contains("\n"), "no raw LF may be rendered: " + rendered));
            }
        }
    }

    @Test
    @DisplayName("Should log JWKS_URI_RESOLUTION_FAILED when well-known resolver cannot resolve JWKS URI")
    void shouldLogJwksUriResolutionFailedWhenWellKnownResolverFails(URIBuilder uriBuilder) {
        // Create a well-known config that will fail to resolve JWKS URI
        // Using an invalid well-known endpoint that returns 404
        String invalidWellKnownUrl = uriBuilder.addPathSegment("invalid-well-known").buildAsString();

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                .wellKnownUrl(invalidWellKnownUrl)
                .issuerIdentifier("test-issuer")
                .build();

        try (HttpJwksLoader failingLoader = new HttpJwksLoader(config)) {
            // Wait for async initialization to complete (even if it fails)
            failingLoader.initJWKSLoader(securityEventCounter).join();

            // Try to get a key, which should fail because JWKS URI cannot be resolved
            Optional<KeyInfo> keyInfo = failingLoader.getKeyInfo(TEST_KID);
            assertFalse(keyInfo.isPresent(), "Key info should not be present when JWKS URI resolution fails");

            // Verify JWKS_URI_RESOLUTION_FAILED was logged
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    JWTValidationLogMessages.WARN.JWKS_URI_RESOLUTION_FAILED.resolveIdentifierString());
        }
    }

    @Test
    @DisplayName("Should not support symmetric keys (oct type)")
    void shouldNotSupportSymmetricKeys(URIBuilder uriBuilder) {
        // Save current dispatcher state
        String previousResponse = moduleDispatcher.getCustomResponse();

        try {
            // Setup mock to return symmetric key JWKS
            moduleDispatcher.setCustomResponse("""
                    {
                        "keys": [{
                            "kty": "oct",
                            "use": "sig",
                            "kid": "symmetric-key-1",
                            "k": "GawgguFyGrWKav7AX4VKUg",
                            "alg": "HS256"
                        }]
                    }
                    """);

            String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                    .jwksUrl(jwksEndpoint)
                    .issuerIdentifier("test-issuer")
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                // Wait for async initialization to complete
                loader.initJWKSLoader(securityEventCounter).join();

                // Try to get the symmetric key - should not be found as oct keys are filtered out
                Optional<KeyInfo> keyInfo = loader.getKeyInfo("symmetric-key-1");
                assertFalse(keyInfo.isPresent(), "Symmetric key should not be supported");

                // The key is simply not found - oct keys are filtered out in JWKSKeyLoader
                // No UNSUPPORTED_JWKS_TYPE error is logged as the filtering happens silently
            }
        } finally {
            // Restore dispatcher state
            moduleDispatcher.setCustomResponse(previousResponse);
        }
    }

    @Test
    @DisplayName("An initialization resolving its endpoint after close() and a re-initialization publishes and fetches nothing")
    void staleInitializationKeepsTheNewGenerationEndpoint() throws Exception {
        String staleJwksPath = "/jwks-stale.json";
        String currentJwksPath = "/jwks-current.json";
        DiscoveryGenerationDispatcher dispatcher = new DiscoveryGenerationDispatcher(List.of(staleJwksPath, currentJwksPath));
        CountDownLatch firstDiscoveryGate = new CountDownLatch(1);
        dispatcher.holdFirstDiscovery(firstDiscoveryGate);

        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(dispatcher);
            server.start();
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                    .wellKnownUrl(server.url(DiscoveryGenerationDispatcher.WELL_KNOWN_PATH).toString())
                    .refreshIntervalSeconds(0)
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                CompletableFuture<LoaderStatus> stale = loader.initJWKSLoader(securityEventCounter);
                Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> dispatcher.discoveryRequests() >= 1);
                loader.close();
                assertEquals(LoaderStatus.OK, loader.initJWKSLoader(securityEventCounter).get(10, TimeUnit.SECONDS),
                        "the re-initialization resolves and loads the current endpoint");

                firstDiscoveryGate.countDown();
                LoaderStatus staleStatus = stale.get(10, TimeUnit.SECONDS);
                loader.performBackgroundRefresh();

                assertAll("the stale initialization leaves the new generation untouched",
                        () -> assertEquals(LoaderStatus.UNDEFINED, staleStatus, "a superseded initialization reports UNDEFINED"),
                        () -> assertEquals(0, dispatcher.jwksFetches(staleJwksPath), "the stale endpoint must never be fetched"),
                        () -> assertEquals(Optional.of(currentJwksPath), loader.getJwksFetchUri().map(URI::getPath)),
                        () -> assertEquals(2, dispatcher.jwksFetches(currentJwksPath),
                                "the initial load and the refresh fetch the current endpoint"),
                        () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()));
            }
        } finally {
            firstDiscoveryGate.countDown();
        }
    }

    /**
     * Pins the status contract: {@link LoaderStatus#OK} only while a usable key set is installed.
     */
    @Nested
    @DisplayName("Status reflects usable keys")
    class StatusContract {

        private static final String UNSUPPORTED_KEYS_ONLY_JWKS = """
                {
                    "keys": [{
                        "kty": "oct",
                        "use": "sig",
                        "kid": "symmetric-key-1",
                        "k": "GawgguFyGrWKav7AX4VKUg",
                        "alg": "HS256"
                    }]
                }
                """;

        private final RetryConfig fastRetry = RetryConfig.builder()
                .initialDelay(Duration.ofMillis(1))
                .maxDelay(Duration.ofMillis(5))
                .build();

        /**
         * Exposes the enclosing class's dispatcher to the MockWebServer extension, which resolves the
         * module dispatcher on the nested test instance.
         *
         * @return the shared JWKS dispatcher
         */
        public JwksResolveDispatcher getModuleDispatcher() {
            return moduleDispatcher;
        }

        private HttpJwksLoaderConfig config(URIBuilder uriBuilder, int refreshIntervalSeconds) {
            return HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                    .jwksUrl(uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString())
                    .issuerIdentifier("test-issuer")
                    .retryConfig(fastRetry)
                    .refreshIntervalSeconds(refreshIntervalSeconds)
                    .build();
        }

        @Test
        @DisplayName("First load without usable keys is UNDEFINED with background refresh enabled, never OK")
        void firstLoadWithoutUsableKeysIsUndefinedWithRefresh(URIBuilder uriBuilder) {
            moduleDispatcher.setCustomResponse(UNSUPPORTED_KEYS_ONLY_JWKS);

            try (HttpJwksLoader loader = new HttpJwksLoader(config(uriBuilder, 60))) {
                LoaderStatus initStatus = loader.initJWKSLoader(securityEventCounter).join();

                assertAll("no usable key is not a successful load",
                        () -> assertEquals(LoaderStatus.UNDEFINED, initStatus),
                        () -> assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus()),
                        () -> assertTrue(loader.getKeyIds().isEmpty(), "no key may be installed"));
                LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO,
                        JWTValidationLogMessages.INFO.JWKS_LOADED.resolveIdentifierString());
            }
        }

        @Test
        @DisplayName("First load without usable keys is ERROR with background refresh disabled")
        void firstLoadWithoutUsableKeysIsErrorWithoutRefresh(URIBuilder uriBuilder) {
            moduleDispatcher.setCustomResponse(UNSUPPORTED_KEYS_ONLY_JWKS);

            try (HttpJwksLoader loader = new HttpJwksLoader(config(uriBuilder, 0))) {
                LoaderStatus initStatus = loader.initJWKSLoader(securityEventCounter).join();

                assertAll("no usable key without refresh is a permanent failure",
                        () -> assertEquals(LoaderStatus.ERROR, initStatus),
                        () -> assertEquals(LoaderStatus.ERROR, loader.getLoaderStatus()));
            }
        }

        @Test
        @DisplayName("Refresh with unchanged content keeps OK")
        void unchangedContentRefreshKeepsOk(URIBuilder uriBuilder) {
            try (HttpJwksLoader loader = new HttpJwksLoader(config(uriBuilder, 60))) {
                assertEquals(LoaderStatus.OK, loader.initJWKSLoader(securityEventCounter).join());
                int callsBeforeRefresh = moduleDispatcher.getCallCounter();

                loader.performBackgroundRefresh();

                assertAll("unchanged content keeps the usable key set",
                        () -> assertTrue(moduleDispatcher.getCallCounter() > callsBeforeRefresh,
                                "the refresh must have fetched the JWKS again"),
                        () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()),
                        () -> assertTrue(loader.getKeyInfo(TEST_KID).isPresent()));
            }
        }

        @Test
        @DisplayName("A refresh that installs keys before the failed initial load publishes its status keeps OK")
        void refreshInstallingKeysBeforeFailedLoadStatusKeepsOk(URIBuilder uriBuilder) {
            moduleDispatcher.returnError();
            // Runs the first refresh cycle synchronously when it is scheduled, i.e. after the failed
            // initial fetch and before initialization publishes the failed-load status; the IdP has
            // recovered by then.
            RefreshOnScheduleExecutor executor = new RefreshOnScheduleExecutor(moduleDispatcher::returnDefault);
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                    .jwksUrl(uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString())
                    .issuerIdentifier("test-issuer")
                    .retryConfig(fastRetry)
                    .refreshIntervalSeconds(60)
                    .scheduledExecutorService(executor)
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                LoaderStatus initStatus = loader.initJWKSLoader(securityEventCounter).join();

                assertAll("the OK installed by the refresh is not overwritten by the failed initial load",
                        () -> assertEquals(1, executor.getImmediateRuns(), "the refresh ran during initialization"),
                        () -> assertEquals(LoaderStatus.OK, initStatus, "the init future reports the status in effect"),
                        () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()),
                        () -> assertTrue(loader.getKeyInfo(TEST_KID).isPresent()));
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @DisplayName("Background refresh recovers a failed first load to OK")
        void backgroundRefreshRecoversFailedFirstLoad(URIBuilder uriBuilder) {
            moduleDispatcher.returnError();

            try (HttpJwksLoader loader = new HttpJwksLoader(config(uriBuilder, 60))) {
                assertEquals(LoaderStatus.UNDEFINED, loader.initJWKSLoader(securityEventCounter).join());

                moduleDispatcher.returnDefault();
                loader.performBackgroundRefresh();

                assertAll("the recovered IdP brings the loader to OK",
                        () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()),
                        () -> assertTrue(loader.getKeyInfo(TEST_KID).isPresent()));
            }
        }

        @Test
        @DisplayName("Background refresh returning unusable keys never sets OK")
        void refreshWithUnusableKeysNeverSetsOk(URIBuilder uriBuilder) {
            moduleDispatcher.returnError();

            try (HttpJwksLoader loader = new HttpJwksLoader(config(uriBuilder, 60))) {
                assertEquals(LoaderStatus.UNDEFINED, loader.initJWKSLoader(securityEventCounter).join());

                moduleDispatcher.setCustomResponse(UNSUPPORTED_KEYS_ONLY_JWKS);
                loader.performBackgroundRefresh();

                assertAll("unusable keys do not recover the loader",
                        () -> assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus()),
                        () -> assertTrue(loader.getKeyIds().isEmpty()));
                LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                        JWTValidationLogMessages.WARN.JWKS_REFRESH_RETURNED_NO_KEYS.resolveIdentifierString());
            }
        }

        @Test
        @DisplayName("Refresh completing after close() leaves the loader UNDEFINED")
        void refreshCompletingAfterCloseDoesNotPublishOk(URIBuilder uriBuilder) throws Exception {
            moduleDispatcher.returnError();
            HttpJwksLoader loader = new HttpJwksLoader(config(uriBuilder, 60));
            assertEquals(LoaderStatus.UNDEFINED, loader.initJWKSLoader(securityEventCounter).join());

            moduleDispatcher.returnDefault();
            int callsBeforeRefresh = moduleDispatcher.getCallCounter();
            CountDownLatch gate = new CountDownLatch(1);
            moduleDispatcher.setResponseGate(gate);
            try {
                CompletableFuture<Void> refresh = CompletableFuture.runAsync(loader::performBackgroundRefresh);
                Awaitility.await().atMost(Duration.ofSeconds(5))
                        .until(() -> moduleDispatcher.getCallCounter() > callsBeforeRefresh);

                loader.close();
                gate.countDown();
                refresh.get(10, TimeUnit.SECONDS);
            } finally {
                gate.countDown();
                moduleDispatcher.setResponseGate(null);
            }

            assertAll("a refresh in flight during close() must not re-publish OK",
                    () -> assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus()),
                    () -> assertTrue(loader.getKeyIds().isEmpty(), "no key may be installed after close()"));
        }
    }

    /**
     * Pins that an unexpected (unchecked) failure of the initialization body never leaves the loader in
     * {@link LoaderStatus#LOADING}: it ends in {@link LoaderStatus#UNDEFINED} only when a background refresh
     * that can recover is actually scheduled, otherwise in a terminal {@link LoaderStatus#ERROR}, and the
     * initialization future completes normally with that status. An advertised {@code jwks_uri} no HTTP
     * handler can be built for is a terminal {@link LoaderStatus#ERROR} whether background refresh is
     * enabled or not.
     */
    @Nested
    @DisplayName("Unexpected initialization failure")
    class UnexpectedInitializationFailure {

        /** A jwks_uri no HTTP handler can be built for. */
        private static final String MALFORMED_JWKS_URI = "http://[malformed";
        private static final String DUPLICATE_KID_JWKS_PATH = "/jwks-duplicate-kid.json";
        private static final int LONG_REFRESH_INTERVAL_SECONDS = 3600;
        private static final int NO_REFRESH = 0;

        private final RetryConfig fastRetry = RetryConfig.builder()
                .initialDelay(Duration.ofMillis(1))
                .maxDelay(Duration.ofMillis(5))
                .build();

        private MockWebServer server;

        /**
         * Exposes the enclosing class's dispatcher to the MockWebServer extension, which resolves the
         * module dispatcher on the nested test instance.
         *
         * @return the shared JWKS dispatcher
         */
        public JwksResolveDispatcher getModuleDispatcher() {
            return moduleDispatcher;
        }

        @BeforeEach
        void startServer() {
            server = new MockWebServer();
        }

        @AfterEach
        void stopServer() {
            server.close();
        }

        private HttpJwksLoaderConfig wellKnownAdvertisingMalformedJwksUri(int refreshIntervalSeconds) throws IOException {
            server.setDispatcher(new DiscoveryGenerationDispatcher(List.of(MALFORMED_JWKS_URI)));
            server.start();
            return HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                    .wellKnownUrl(server.url(DiscoveryGenerationDispatcher.WELL_KNOWN_PATH).toString())
                    .retryConfig(fastRetry)
                    .refreshIntervalSeconds(refreshIntervalSeconds)
                    .build();
        }

        private static LoaderStatus awaitInitialization(HttpJwksLoader loader, SecurityEventCounter counter)
                throws Exception {
            return loader.initJWKSLoader(counter).get(10, TimeUnit.SECONDS);
        }

        private static void assertInitializationFailureLogged() {
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                    JWTValidationLogMessages.ERROR.JWKS_INITIALIZATION_FAILED.resolveIdentifierString());
        }

        @Test
        @DisplayName("No handler for the advertised jwks_uri with background refresh enabled is a terminal ERROR")
        void unusableAdvertisedJwksUriWithRefreshIsTerminalError() throws Exception {
            try (HttpJwksLoader loader = new HttpJwksLoader(wellKnownAdvertisingMalformedJwksUri(LONG_REFRESH_INTERVAL_SECONDS))) {
                LoaderStatus initStatus = awaitInitialization(loader, securityEventCounter);

                assertAll("an unusable advertised jwks_uri is not retried",
                        () -> assertEquals(LoaderStatus.ERROR, initStatus, "the future completes with the status in effect"),
                        () -> assertEquals(LoaderStatus.ERROR, loader.getLoaderStatus()),
                        () -> assertFalse(loader.isBackgroundRefreshActive(), "no refresh may be scheduled"),
                        () -> assertTrue(loader.getKeyIds().isEmpty(), "no key may be installed"));
                assertInitializationFailureLogged();
                LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        JWTValidationLogMessages.WARN.JWKS_URI_UNUSABLE.resolveIdentifierString());
            }
        }

        @Test
        @DisplayName("No handler for the advertised jwks_uri with background refresh disabled is ERROR")
        void unusableAdvertisedJwksUriWithoutRefreshIsError() throws Exception {
            try (HttpJwksLoader loader = new HttpJwksLoader(wellKnownAdvertisingMalformedJwksUri(NO_REFRESH))) {
                LoaderStatus initStatus = awaitInitialization(loader, securityEventCounter);

                assertAll("without a refresh the failure is terminal",
                        () -> assertEquals(LoaderStatus.ERROR, initStatus),
                        () -> assertEquals(LoaderStatus.ERROR, loader.getLoaderStatus()),
                        () -> assertNotEquals(LoaderStatus.LOADING, loader.getLoaderStatus()),
                        () -> assertFalse(loader.isBackgroundRefreshActive(), "no refresh may be scheduled"),
                        () -> assertTrue(loader.getKeyIds().isEmpty(), "no key may be installed"));
                assertInitializationFailureLogged();
            }
        }

        @Test
        @DisplayName("A key-loading failure after the endpoint was published is UNDEFINED with exactly one refresh task")
        void keyLoadingFailureAfterEndpointPublishedIsUndefinedWithOneRefreshTask() throws Exception {
            DiscoveryGenerationDispatcher dispatcher = new DiscoveryGenerationDispatcher(List.of(DUPLICATE_KID_JWKS_PATH));
            dispatcher.serveJwks(DUPLICATE_KID_JWKS_PATH, duplicateKidJwks());
            server.setDispatcher(dispatcher);
            server.start();
            CountingScheduledExecutor executor = new CountingScheduledExecutor();
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                    .jwksUrl(server.url(DUPLICATE_KID_JWKS_PATH).toString())
                    .issuerIdentifier("test-issuer")
                    .retryConfig(fastRetry)
                    .refreshIntervalSeconds(LONG_REFRESH_INTERVAL_SECONDS)
                    .scheduledExecutorService(executor)
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                LoaderStatus initStatus = awaitInitialization(loader, new FailingOnDuplicateKidCounter());

                assertAll("the failure after the fetch ends retry-pending with a single refresh task",
                        () -> assertEquals(1, dispatcher.jwksFetches(DUPLICATE_KID_JWKS_PATH), "the endpoint was fetched"),
                        () -> assertEquals(LoaderStatus.UNDEFINED, initStatus),
                        () -> assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus()),
                        () -> assertNotEquals(LoaderStatus.LOADING, loader.getLoaderStatus()),
                        () -> assertTrue(loader.isBackgroundRefreshActive()),
                        () -> assertEquals(1, executor.getScheduledTasks(), "the refresh task is never scheduled twice"),
                        () -> assertTrue(loader.getKeyIds().isEmpty(), "no key may be installed"));
                assertInitializationFailureLogged();
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @DisplayName("A direct-mode failure before any endpoint was published is ERROR, also with background refresh enabled")
        void directModeFailureWithoutEndpointIsError() throws Exception {
            EgressPolicy failingPolicy = EgressPolicy.builder().resolver(host -> {
                throw new IllegalStateException("injected resolver failure");
            }).build();
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowInsecureHttp(true)
                    .jwksUrl("http://jwks.example.test/jwks")
                    .issuerIdentifier("test-issuer")
                    .refreshIntervalSeconds(LONG_REFRESH_INTERVAL_SECONDS)
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config, failingPolicy)) {
                LoaderStatus initStatus = awaitInitialization(loader, securityEventCounter);

                assertAll("a refresh cannot recover direct mode without an endpoint, so the failure is terminal",
                        () -> assertEquals(LoaderStatus.ERROR, initStatus),
                        () -> assertEquals(LoaderStatus.ERROR, loader.getLoaderStatus()),
                        () -> assertNotEquals(LoaderStatus.LOADING, loader.getLoaderStatus()),
                        () -> assertFalse(loader.isBackgroundRefreshActive(), "no refresh may be scheduled"),
                        () -> assertTrue(loader.getJwksFetchUri().isEmpty(), "no endpoint may be published"));
                assertInitializationFailureLogged();
            }
        }

        /**
         * A JWKS carrying the default key twice under the same {@code kid}, so key loading counts a
         * {@code DUPLICATE_JWKS_KID} event.
         */
        private static String duplicateKidJwks() {
            String jwks = InMemoryJWKSFactory.createDefaultJwks();
            String key = jwks.substring(jwks.indexOf('[') + 1, jwks.lastIndexOf(']')).strip();
            return "{\"keys\":[" + key + "," + key + "]}";
        }
    }

    /**
     * Pins that an advertised {@code jwks_uri} no HTTP handler can be built for ends the loader in a
     * terminal {@link LoaderStatus#ERROR}, logged once as {@code JWKS_URI_UNUSABLE}, and is not retried:
     * the refresh task is cancelled or never scheduled, so the discovery endpoint is not contacted again.
     * The absence of retries is observed on the real scheduler over a short refresh interval, not through
     * {@code performBackgroundRefresh()}, which would bypass a cancelled task.
     */
    @Nested
    @DisplayName("Unusable advertised jwks_uri")
    class UnusableAdvertisedJwksUri {

        /** A jwks_uri no HTTP handler can be built for. */
        private static final String MALFORMED_JWKS_URI = "http://[malformed";
        private static final String LOCALHOST = "localhost";
        private static final int SHORT_REFRESH_INTERVAL_SECONDS = 1;
        private static final int TIMEOUT_SECONDS = 5;
        private static final Duration NO_RETRY_WINDOW = Duration.ofMillis(2500);
        private static final Duration NO_RETRY_DEADLINE = Duration.ofSeconds(5);

        /** Certificate of the HTTPS discovery server, issued for the host name the loader contacts. */
        private static final HeldCertificate SERVER_CERTIFICATE = new HeldCertificate.Builder()
                .commonName(LOCALHOST)
                .addSubjectAlternativeName(LOCALHOST)
                .build();

        private final RetryConfig fastRetry = RetryConfig.builder()
                .initialDelay(Duration.ofMillis(1))
                .maxDelay(Duration.ofMillis(5))
                .build();

        private MockWebServer server;

        /**
         * Exposes the enclosing class's dispatcher to the MockWebServer extension, which resolves the
         * module dispatcher on the nested test instance.
         *
         * @return the shared JWKS dispatcher
         */
        public JwksResolveDispatcher getModuleDispatcher() {
            return moduleDispatcher;
        }

        @BeforeEach
        void createServer() {
            server = new MockWebServer();
        }

        @AfterEach
        void stopServer() {
            server.close();
        }

        @Test
        @DisplayName("A cleartext jwks_uri while insecure HTTP is not allowed ends in ERROR and is not retried")
        void cleartextJwksUriWithoutInsecureHttpIsTerminalAndNotRetried() throws Exception {
            server.useHttps(new HandshakeCertificates.Builder()
                    .heldCertificate(SERVER_CERTIFICATE)
                    .build()
                    .sslSocketFactory());
            UnusableJwksUriDispatcher dispatcher = new UnusableJwksUriDispatcher(
                    port -> "http://%s:%d/jwks.json".formatted(LOCALHOST, port));
            startOnLoopback(dispatcher);
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true)
                    .allowInsecureHttp(false)
                    .sslContext(new HandshakeCertificates.Builder()
                            .addTrustedCertificate(SERVER_CERTIFICATE.certificate())
                            .build()
                            .sslContext())
                    .wellKnownUrl("https://%s:%d%s".formatted(LOCALHOST, server.getPort(),
                            UnusableJwksUriDispatcher.WELL_KNOWN_PATH))
                    .connectTimeoutSeconds(TIMEOUT_SECONDS)
                    .readTimeoutSeconds(TIMEOUT_SECONDS)
                    .retryConfig(fastRetry)
                    .refreshIntervalSeconds(SHORT_REFRESH_INTERVAL_SECONDS)
                    .build();

            assertTerminalAtInitializationAndNotRetried(config, dispatcher);
        }

        @Test
        @DisplayName("A malformed jwks_uri ends in ERROR and is not retried")
        void malformedJwksUriIsTerminalAndNotRetried() throws Exception {
            UnusableJwksUriDispatcher dispatcher = new UnusableJwksUriDispatcher(port -> MALFORMED_JWKS_URI);
            startOnLoopback(dispatcher);
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                    .wellKnownUrl(server.url(UnusableJwksUriDispatcher.WELL_KNOWN_PATH).toString())
                    .retryConfig(fastRetry)
                    .refreshIntervalSeconds(SHORT_REFRESH_INTERVAL_SECONDS)
                    .build();

            assertTerminalAtInitializationAndNotRetried(config, dispatcher);
        }

        @Test
        @DisplayName("A refresh cycle that finds an unusable jwks_uri after an unavailable discovery ends in ERROR and cancels the refresh")
        void unusableJwksUriFoundByRefreshIsTerminalAndCancelsTheRefresh() throws Exception {
            UnusableJwksUriDispatcher dispatcher = new UnusableJwksUriDispatcher(port -> MALFORMED_JWKS_URI);
            dispatcher.setAvailable(false);
            startOnLoopback(dispatcher);
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                    .wellKnownUrl(server.url(UnusableJwksUriDispatcher.WELL_KNOWN_PATH).toString())
                    .retryConfig(fastRetry)
                    .refreshIntervalSeconds(SHORT_REFRESH_INTERVAL_SECONDS)
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                LoaderStatus initStatus = loader.initJWKSLoader(securityEventCounter).join();
                assertAll("an unavailable discovery is retried",
                        () -> assertEquals(LoaderStatus.UNDEFINED, initStatus),
                        () -> assertTrue(loader.isBackgroundRefreshActive(), "the background refresh must be scheduled"));
                LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                        JWTValidationLogMessages.WARN.JWKS_URI_UNUSABLE.resolveIdentifierString());

                dispatcher.setAvailable(true);
                Awaitility.await().atMost(Duration.ofSeconds(10))
                        .until(() -> loader.getLoaderStatus() == LoaderStatus.ERROR && unusableUriLogCount() == 1);

                assertAll("the unusable jwks_uri found by the refresh is terminal",
                        () -> assertEquals(LoaderStatus.ERROR, loader.getLoaderStatus()),
                        () -> assertFalse(loader.isBackgroundRefreshActive(), "the refresh task must be cancelled"),
                        () -> assertTrue(loader.getKeyIds().isEmpty(), "no key may be installed"));
                assertNotRetried(dispatcher);
            }
        }

        private void startOnLoopback(UnusableJwksUriDispatcher dispatcher) throws IOException {
            server.setDispatcher(dispatcher);
            server.start(InetAddress.getLoopbackAddress(), 0);
            dispatcher.setPort(server.getPort());
        }

        private void assertTerminalAtInitializationAndNotRetried(HttpJwksLoaderConfig config,
                UnusableJwksUriDispatcher dispatcher) {
            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                LoaderStatus initStatus = loader.initJWKSLoader(securityEventCounter).join();

                assertAll("an unusable advertised jwks_uri is a terminal rejection",
                        () -> assertTrue(dispatcher.discoveryRequests() >= 1, "the discovery document was fetched"),
                        () -> assertEquals(LoaderStatus.ERROR, initStatus, "the future completes with the status in effect"),
                        () -> assertEquals(LoaderStatus.ERROR, loader.getLoaderStatus()),
                        () -> assertFalse(loader.isBackgroundRefreshActive(), "no refresh may be scheduled"),
                        () -> assertTrue(loader.getKeyIds().isEmpty(), "no key may be installed"),
                        () -> assertEquals(1, unusableUriLogCount(), "JWKS_URI_UNUSABLE is logged exactly once"));
                assertNotRetried(dispatcher);
            }
        }

        /**
         * Asserts that over several refresh intervals the discovery endpoint is not contacted again and
         * neither {@code JWKS_URI_UNUSABLE} nor {@code BACKGROUND_REFRESH_FAILED} is logged again.
         */
        private void assertNotRetried(UnusableJwksUriDispatcher dispatcher) {
            int discoveriesAtTerminal = dispatcher.discoveryRequests();
            int refreshFailuresAtTerminal = refreshFailureLogCount();

            Awaitility.await().during(NO_RETRY_WINDOW).atMost(NO_RETRY_DEADLINE)
                    .until(() -> dispatcher.discoveryRequests() == discoveriesAtTerminal);

            assertAll("the terminal rejection is not retried",
                    () -> assertEquals(discoveriesAtTerminal, dispatcher.discoveryRequests(),
                            "no further discovery request may be sent"),
                    () -> assertEquals(1, unusableUriLogCount(), "JWKS_URI_UNUSABLE must not be logged again"),
                    () -> assertEquals(refreshFailuresAtTerminal, refreshFailureLogCount(),
                            "BACKGROUND_REFRESH_FAILED must not be logged again"));
        }

        private static int unusableUriLogCount() {
            return TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(TestLogLevel.WARN,
                    JWTValidationLogMessages.WARN.JWKS_URI_UNUSABLE.resolveIdentifierString()).size();
        }

        private static int refreshFailureLogCount() {
            return TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(TestLogLevel.WARN,
                    JWTValidationLogMessages.WARN.BACKGROUND_REFRESH_FAILED.resolveIdentifierString()).size();
        }
    }

    /**
     * Discovery dispatcher advertising an unusable {@code jwks_uri}. Discovery requests are counted; while
     * the dispatcher is marked unavailable they are answered with HTTP 500, a transient discovery failure.
     */
    private static final class UnusableJwksUriDispatcher extends Dispatcher {

        static final String WELL_KNOWN_PATH = "/.well-known/openid-configuration";

        private final IntFunction<String> advertisedJwksUri;
        private final AtomicInteger discoveryRequests = new AtomicInteger();
        private volatile boolean available = true;
        private volatile int port;

        /**
         * @param advertisedJwksUri computes the advertised {@code jwks_uri} from the server port
         */
        UnusableJwksUriDispatcher(IntFunction<String> advertisedJwksUri) {
            this.advertisedJwksUri = advertisedJwksUri;
        }

        void setAvailable(boolean available) {
            this.available = available;
        }

        void setPort(int port) {
            this.port = port;
        }

        int discoveryRequests() {
            return discoveryRequests.get();
        }

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            HttpUrl url = request.getUrl();
            if (!WELL_KNOWN_PATH.equals(url.encodedPath())) {
                return new MockResponse(404, Headers.of(), "");
            }
            discoveryRequests.incrementAndGet();
            if (!available) {
                return new MockResponse(500, Headers.of(), "");
            }
            String baseUrl = url.newBuilder().encodedPath("/").build().toString();
            String issuer = baseUrl.substring(0, baseUrl.length() - 1);
            return new MockResponse(200, Headers.of("Content-Type", "application/json"), """
                    {
                      "issuer": "%s",
                      "jwks_uri": "%s"
                    }
                    """.formatted(issuer, advertisedJwksUri.apply(port)));
        }
    }

    /**
     * Counter whose {@code DUPLICATE_JWKS_KID} event throws, injecting an unchecked failure into the key
     * loading of the initial load.
     */
    private static final class FailingOnDuplicateKidCounter extends SecurityEventCounter {

        @Override
        public long increment(EventType eventType) {
            if (eventType == EventType.DUPLICATE_JWKS_KID) {
                throw new IllegalStateException("injected key-loading failure");
            }
            return super.increment(eventType);
        }
    }

    /**
     * Scheduled executor counting the fixed-rate tasks scheduled on it.
     */
    private static final class CountingScheduledExecutor extends ScheduledThreadPoolExecutor {

        private final AtomicInteger scheduledTasks = new AtomicInteger();

        CountingScheduledExecutor() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            scheduledTasks.incrementAndGet();
            return super.scheduleAtFixedRate(command, initialDelay, period, unit);
        }

        int getScheduledTasks() {
            return scheduledTasks.get();
        }
    }

    /**
     * Scheduled executor that, when a fixed-rate task is scheduled, first runs the given preparation and
     * then the task itself once on the scheduling thread, before scheduling it as usual. Lets a test
     * place one refresh cycle deterministically inside the initialization that schedules it.
     */
    private static final class RefreshOnScheduleExecutor extends ScheduledThreadPoolExecutor {

        private final Runnable beforeImmediateRun;
        private final AtomicInteger immediateRuns = new AtomicInteger();

        RefreshOnScheduleExecutor(Runnable beforeImmediateRun) {
            super(1);
            this.beforeImmediateRun = beforeImmediateRun;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            beforeImmediateRun.run();
            command.run();
            immediateRuns.incrementAndGet();
            return super.scheduleAtFixedRate(command, initialDelay, period, unit);
        }

        int getImmediateRuns() {
            return immediateRuns.get();
        }
    }
}
