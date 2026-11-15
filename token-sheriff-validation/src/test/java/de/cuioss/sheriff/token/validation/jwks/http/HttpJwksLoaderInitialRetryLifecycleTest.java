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

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.validation.jwks.http.HttpJwksLoaderInitialRetryTest.RecordingScheduler;
import de.cuioss.sheriff.token.validation.test.InMemoryJWKSFactory;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static de.cuioss.sheriff.token.validation.jwks.http.HttpJwksLoaderInitialRetryTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lifecycle stops of the opt-in initial-load retry of {@link HttpJwksLoader}: {@link HttpJwksLoader#close()}
 * and a closed generation, the terminal {@code ERROR} reached at initialization or by a periodic cycle, and
 * the retry of a well-known loader, whose discovery can be unavailable, rejected or followed by a failing
 * JWKS fetch. The backoff sequence and the direct-endpoint behaviour are covered by
 * {@link HttpJwksLoaderInitialRetryTest}, whose {@link RecordingScheduler} this class shares: every attempt
 * is recorded with its delay and run by the test itself.
 */
@EnableTestLogger
@EnableMockWebServer
@DisplayName("HttpJwksLoader initial-load retry lifecycle")
class HttpJwksLoaderInitialRetryLifecycleTest {

    private static final String WELL_KNOWN_PATH = "/.well-known/openid-configuration";
    private static final String WELL_KNOWN_JWKS_PATH = "/jwks.json";
    private static final String FOREIGN_ISSUER = "https://foreign-issuer.example.com";

    @Getter
    private final UnknownKidJwksDispatcher moduleDispatcher = new UnknownKidJwksDispatcher();

    private RecordingScheduler scheduler;

    @BeforeEach
    void setUp() {
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.SERVER_ERROR);
        scheduler = new RecordingScheduler();
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    @DisplayName("close() ends the retries: a pending attempt fetches and schedules nothing")
    void shouldStopAtClose(URIBuilder uriBuilder) {
        HttpJwksLoader loader = initializedLoader(directConfig(uriBuilder, scheduler).initialLoadRetry(true).build(),
                LoaderStatus.UNDEFINED);
        int afterInit = moduleDispatcher.getRequestCount();

        loader.close();
        scheduler.runNextOneShot();

        assertAll(
                () -> assertEquals(afterInit, moduleDispatcher.getRequestCount(), "the closed attempt fetches nothing"),
                () -> assertEquals(seconds(1), scheduler.getOneShotDelays(), "the closed attempt schedules nothing"),
                () -> assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus()));
    }

    @Test
    @DisplayName("An attempt of a closed generation does not leak into a re-initialized loader")
    void shouldNotLetClosedGenerationScheduleAttempts(URIBuilder uriBuilder) {
        HttpJwksLoader loader = initializedLoader(directConfig(uriBuilder, scheduler).initialLoadRetry(true).build(),
                LoaderStatus.UNDEFINED);
        loader.close();
        assertEquals(LoaderStatus.UNDEFINED, loader.initJWKSLoader(new SecurityEventCounter()).join());
        int afterReInit = moduleDispatcher.getRequestCount();

        scheduler.runNextOneShot();

        assertAll("the old attempt is inert, the new generation's own attempt is still pending",
                () -> assertEquals(afterReInit, moduleDispatcher.getRequestCount()),
                () -> assertEquals(seconds(1, 1), scheduler.getOneShotDelays()),
                () -> assertEquals(1, scheduler.pendingOneShots()));
        scheduler.runNextOneShot();
        assertAll("the new generation keeps retrying",
                () -> assertEquals(afterReInit + 1, moduleDispatcher.getRequestCount()),
                () -> assertEquals(seconds(1, 1, 2), scheduler.getOneShotDelays()));
        loader.close();
    }

    @Test
    @DisplayName("Well-known mode: a failed initial JWKS fetch is retried until keys are installed")
    void shouldRetryFailedFetchInWellKnownMode() throws Exception {
        WellKnownDispatcher dispatcher = new WellKnownDispatcher();
        dispatcher.setJwksFailing(true);
        try (MockWebServer server = startServer(dispatcher)) {
            HttpJwksLoader loader = initializedLoader(wellKnownConfig(server), LoaderStatus.UNDEFINED);
            scheduler.runNextOneShot();
            dispatcher.setJwksFailing(false);

            scheduler.runNextOneShot();

            assertAll(
                    () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()),
                    () -> assertEquals(3, dispatcher.getJwksFetches(), "initial fetch and one per attempt"),
                    () -> assertEquals(seconds(1, 2), scheduler.getOneShotDelays()),
                    () -> assertEquals(0, scheduler.pendingOneShots()));
            loader.close();
        }
    }

    @Test
    @DisplayName("Well-known mode: a rejected re-discovery during the retries ends them in a terminal ERROR")
    void shouldStopAtTerminalErrorInWellKnownMode() throws Exception {
        WellKnownDispatcher dispatcher = new WellKnownDispatcher();
        dispatcher.setDiscovery(WellKnownDispatcher.Discovery.NO_JWKS_URI);
        try (MockWebServer server = startServer(dispatcher)) {
            HttpJwksLoader loader = initializedLoader(wellKnownConfig(server), LoaderStatus.UNDEFINED);
            scheduler.runNextOneShot();
            assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus(), "an unavailable discovery keeps retrying");
            dispatcher.setDiscovery(WellKnownDispatcher.Discovery.FOREIGN_ISSUER);

            scheduler.runNextOneShot();

            assertAll("terminal ERROR, nothing scheduled any more",
                    () -> assertEquals(LoaderStatus.ERROR, loader.getLoaderStatus()),
                    () -> assertFalse(loader.isBackgroundRefreshActive(), "the periodic task is cancelled"),
                    () -> assertEquals(seconds(1, 2), scheduler.getOneShotDelays()),
                    () -> assertEquals(0, scheduler.pendingOneShots()),
                    () -> assertEquals(0, dispatcher.getJwksFetches(), "no JWKS was ever fetched"));
            loader.close();
        }
    }

    @Test
    @DisplayName("Well-known mode: a terminal rejection at initialization schedules no attempt")
    void shouldNotRetryTerminalRejectionAtInitialization() throws Exception {
        WellKnownDispatcher dispatcher = new WellKnownDispatcher();
        dispatcher.setDiscovery(WellKnownDispatcher.Discovery.FOREIGN_ISSUER);
        try (MockWebServer server = startServer(dispatcher)) {
            HttpJwksLoader loader = initializedLoader(wellKnownConfig(server), LoaderStatus.ERROR);

            assertAll(
                    () -> assertTrue(scheduler.getOneShotDelays().isEmpty()),
                    () -> assertEquals(0, dispatcher.getJwksFetches(), "no JWKS was fetched"));
            loader.close();
        }
    }

    @Test
    @DisplayName("A pending attempt runs no cycle once a periodic cycle reached the terminal ERROR")
    void shouldNotRunPendingAttemptAfterTerminalError() throws Exception {
        WellKnownDispatcher dispatcher = new WellKnownDispatcher();
        dispatcher.setDiscovery(WellKnownDispatcher.Discovery.NO_JWKS_URI);
        try (MockWebServer server = startServer(dispatcher)) {
            HttpJwksLoader loader = initializedLoader(wellKnownConfig(server), LoaderStatus.UNDEFINED);
            dispatcher.setDiscovery(WellKnownDispatcher.Discovery.FOREIGN_ISSUER);
            scheduler.getPeriodicTask().run();
            assertEquals(LoaderStatus.ERROR, loader.getLoaderStatus(), "the periodic cycle's rejection is terminal");
            int discoveriesAtError = dispatcher.getDiscoveryFetches();

            assertTrue(scheduler.runNextOneShot(), "the attempt scheduled at initialization was still pending");

            assertAll("the pending attempt is inert",
                    () -> assertEquals(discoveriesAtError, dispatcher.getDiscoveryFetches(),
                            "the discovery is not repeated"),
                    () -> assertEquals(0, dispatcher.getJwksFetches(), "no JWKS was fetched"),
                    () -> assertEquals(LoaderStatus.ERROR, loader.getLoaderStatus()),
                    () -> assertEquals(seconds(1), scheduler.getOneShotDelays(), "no successor is scheduled"),
                    () -> assertEquals(0, scheduler.pendingOneShots()));
            loader.close();
        }
    }

    @Test
    @DisplayName("Well-known mode: a pending attempt of a closed loader repeats no discovery")
    void shouldNotRediscoverInPendingAttemptAfterClose() throws Exception {
        WellKnownDispatcher dispatcher = new WellKnownDispatcher();
        dispatcher.setDiscovery(WellKnownDispatcher.Discovery.NO_JWKS_URI);
        try (MockWebServer server = startServer(dispatcher)) {
            HttpJwksLoader loader = initializedLoader(wellKnownConfig(server), LoaderStatus.UNDEFINED);
            int discoveriesAtClose = dispatcher.getDiscoveryFetches();
            loader.close();

            assertTrue(scheduler.runNextOneShot(), "the attempt scheduled at initialization was still pending");

            assertAll("the pending attempt is inert",
                    () -> assertEquals(discoveriesAtClose, dispatcher.getDiscoveryFetches(),
                            "the discovery is not repeated"),
                    () -> assertEquals(0, dispatcher.getJwksFetches(), "no JWKS was fetched"),
                    () -> assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus()),
                    () -> assertEquals(seconds(1), scheduler.getOneShotDelays(), "no successor is scheduled"),
                    () -> assertEquals(0, scheduler.pendingOneShots()));
        }
    }

    private static MockWebServer startServer(WellKnownDispatcher dispatcher) throws IOException {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(dispatcher);
        server.start();
        return server;
    }

    private HttpJwksLoaderConfig wellKnownConfig(MockWebServer server) {
        String serverUrl = server.url("/").toString();
        return HttpJwksLoaderConfig.builder()
                .allowLoopbackEgress(true)
                .allowInsecureHttp(true)
                .wellKnownUrl(server.url(WELL_KNOWN_PATH).toString())
                .issuerIdentifier(serverUrl.substring(0, serverUrl.length() - 1))
                .retryConfig(SINGLE_ATTEMPT)
                .refreshIntervalSeconds(LONG_REFRESH_INTERVAL_SECONDS)
                .scheduledExecutorService(scheduler)
                .initialLoadRetry(true)
                .build();
    }

    /**
     * Well-known endpoint whose discovery document can advertise a matching issuer, a foreign issuer or no
     * {@code jwks_uri} at all (an unavailable discovery), and whose JWKS endpoint can fail with HTTP 500.
     * Discovery requests and JWKS fetches are counted.
     */
    private static final class WellKnownDispatcher extends Dispatcher {

        enum Discovery {
            MATCHING, NO_JWKS_URI, FOREIGN_ISSUER
        }

        private final AtomicInteger discoveryFetches = new AtomicInteger();
        private final AtomicInteger jwksFetches = new AtomicInteger();
        private volatile Discovery discovery = Discovery.MATCHING;
        private volatile boolean jwksFailing;

        void setDiscovery(Discovery discovery) {
            this.discovery = discovery;
        }

        void setJwksFailing(boolean jwksFailing) {
            this.jwksFailing = jwksFailing;
        }

        int getJwksFetches() {
            return jwksFetches.get();
        }

        int getDiscoveryFetches() {
            return discoveryFetches.get();
        }

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            HttpUrl url = request.getUrl();
            String path = url.encodedPath();
            Headers json = Headers.of("Content-Type", "application/json");
            if (WELL_KNOWN_PATH.equals(path)) {
                discoveryFetches.incrementAndGet();
                String baseUrl = url.newBuilder().encodedPath("/").build().toString();
                String serverIssuer = baseUrl.substring(0, baseUrl.length() - 1);
                String jwksUri = serverIssuer + WELL_KNOWN_JWKS_PATH;
                return new MockResponse(200, json, switch (discovery) {
                    case MATCHING -> document(serverIssuer, jwksUri);
                    case FOREIGN_ISSUER -> document(FOREIGN_ISSUER, jwksUri);
                    case NO_JWKS_URI -> "{\"issuer\": \"%s\"}".formatted(serverIssuer);
                });
            }
            if (WELL_KNOWN_JWKS_PATH.equals(path)) {
                jwksFetches.incrementAndGet();
                return jwksFailing
                        ? new MockResponse(500, Headers.of(), "")
                        : new MockResponse(200, json, InMemoryJWKSFactory.createDefaultJwks());
            }
            return new MockResponse(404, Headers.of(), "");
        }

        private static String document(String issuer, String jwksUri) {
            return """
                    {
                      "issuer": "%s",
                      "jwks_uri": "%s"
                    }
                    """.formatted(issuer, jwksUri);
        }
    }
}
