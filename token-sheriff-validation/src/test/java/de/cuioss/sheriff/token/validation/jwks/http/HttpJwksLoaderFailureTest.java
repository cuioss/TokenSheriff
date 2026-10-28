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
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.validation.JWTValidationLogMessages;
import de.cuioss.sheriff.token.validation.jwks.key.KeyInfo;
import de.cuioss.sheriff.token.validation.test.InMemoryJWKSFactory;
import de.cuioss.sheriff.token.validation.test.dispatcher.JwksResolveDispatcher;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import mockwebserver3.MockWebServer;
import org.awaitility.Awaitility;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

            // cui-http 2.0.0 classifies an unreachable host as a network error and logs
            // REQUEST_FAILED_MAX_ATTEMPTS at WARN once retries are exhausted.
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    HttpLogMessages.WARN.REQUEST_FAILED_MAX_ATTEMPTS.resolveIdentifierString());
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
