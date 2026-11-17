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

import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.validation.jwks.http.HttpJwksLoaderInitialRetryTest.RecordingScheduler;
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

import java.util.concurrent.atomic.AtomicInteger;

import static de.cuioss.sheriff.token.validation.jwks.http.HttpJwksLoaderInitialRetryTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Order of the opt-in initial-load retry of {@link HttpJwksLoader}: an attempt claims the in-flight guard
 * first and re-checks its eligibility only while it holds the guard. A periodic cycle that completes
 * between the moment an attempt becomes due and the moment it claims the guard is therefore seen by the
 * re-check, and the attempt fetches nothing.
 * <p>
 * The periodic cycle is placed at exactly that point through the package-private seam
 * {@code HttpJwksLoader#beforeInitialLoadRetryClaim}, whose action runs right before the claim, and the
 * {@link RecordingScheduler} of {@link HttpJwksLoaderInitialRetryTest}, whose recorded periodic task the
 * seam runs on the attempt's own thread, so no sleep is involved. Were the eligibility check moved back
 * in front of the claim, it would run before the seam and the attempt would fetch after the periodic
 * cycle had installed keys or published {@code ERROR}; both interleaving tests fail on that order.
 */
@EnableTestLogger
@EnableMockWebServer
@DisplayName("HttpJwksLoader initial-load retry claims the in-flight guard before it re-checks eligibility")
class HttpJwksLoaderInitialRetryGuardOrderTest {

    private static final String WELL_KNOWN_PATH = "/.well-known/openid-configuration";
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
    @DisplayName("A periodic cycle that installs keys before the claim leaves the attempt without a JWKS request")
    void shouldNotFetchWhenPeriodicCycleInstalledKeysBeforeClaim(URIBuilder uriBuilder) {
        HttpJwksLoader loader = initializedLoader(directConfig(uriBuilder, scheduler).initialLoadRetry(true).build(),
                LoaderStatus.UNDEFINED);
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.DEFAULT_KEY);
        int afterInit = moduleDispatcher.getRequestCount();
        loader.beforeInitialLoadRetryClaim.set(() -> scheduler.getPeriodicTask().run());

        assertTrue(scheduler.runNextOneShot(), "the attempt scheduled at initialization is due");

        assertAll("the periodic cycle installed the keys, the attempt fetched nothing and the chain ended",
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus(), "the periodic cycle installed keys"),
                () -> assertEquals(afterInit + 1, moduleDispatcher.getRequestCount(),
                        "only the periodic cycle requested the JWKS"),
                () -> assertEquals(seconds(1), scheduler.getOneShotDelays(), "no successor is scheduled"),
                () -> assertEquals(0, scheduler.pendingOneShots()));
        loader.close();
    }

    @Test
    @DisplayName("A periodic cycle that reaches the terminal ERROR before the claim leaves the attempt without a discovery")
    void shouldNotRediscoverWhenPeriodicCycleReachedErrorBeforeClaim() throws Exception {
        DiscoveryDispatcher dispatcher = new DiscoveryDispatcher();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(dispatcher);
            server.start();
            HttpJwksLoader loader = initializedLoader(wellKnownConfig(server), LoaderStatus.UNDEFINED);
            dispatcher.foreignIssuer = true;
            int discoveriesAfterInit = dispatcher.discoveries.get();
            loader.beforeInitialLoadRetryClaim.set(() -> scheduler.getPeriodicTask().run());

            assertTrue(scheduler.runNextOneShot(), "the attempt scheduled at initialization is due");

            assertAll("the periodic cycle's rejection is terminal and the attempt repeated no discovery",
                    () -> assertEquals(LoaderStatus.ERROR, loader.getLoaderStatus()),
                    () -> assertEquals(discoveriesAfterInit + 1, dispatcher.discoveries.get(),
                            "only the periodic cycle requested the discovery document"),
                    () -> assertEquals(seconds(1), scheduler.getOneShotDelays(), "no successor is scheduled"),
                    () -> assertEquals(0, scheduler.pendingOneShots()));
            loader.close();
        }
    }

    @Test
    @DisplayName("With no cycle before the claim, the attempt issues exactly one JWKS request")
    void shouldFetchOnceWithoutCycleBeforeClaim(URIBuilder uriBuilder) {
        HttpJwksLoader loader = initializedLoader(directConfig(uriBuilder, scheduler).initialLoadRetry(true).build(),
                LoaderStatus.UNDEFINED);
        int afterInit = moduleDispatcher.getRequestCount();

        assertTrue(scheduler.runNextOneShot(), "the attempt scheduled at initialization is due");

        assertAll("the attempt fetched once and, still without keys, scheduled its successor",
                () -> assertEquals(afterInit + 1, moduleDispatcher.getRequestCount()),
                () -> assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus()),
                () -> assertEquals(seconds(1, 2), scheduler.getOneShotDelays()));
        loader.close();
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
     * Discovery endpoint that first advertises no {@code jwks_uri} (an unavailable discovery, which the
     * retry repeats) and, once {@link #foreignIssuer} is set, a foreign issuer (a terminal rejection).
     * Discovery requests are counted.
     */
    private static final class DiscoveryDispatcher extends Dispatcher {

        private final AtomicInteger discoveries = new AtomicInteger();
        private volatile boolean foreignIssuer;

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            HttpUrl url = request.getUrl();
            if (!WELL_KNOWN_PATH.equals(url.encodedPath())) {
                return new MockResponse(404, Headers.of(), "");
            }
            discoveries.incrementAndGet();
            String baseUrl = url.newBuilder().encodedPath("/").build().toString();
            String serverIssuer = baseUrl.substring(0, baseUrl.length() - 1);
            String body = foreignIssuer
                    ? "{\"issuer\": \"%s\", \"jwks_uri\": \"%s/jwks.json\"}".formatted(FOREIGN_ISSUER, serverIssuer)
                    : "{\"issuer\": \"%s\"}".formatted(serverIssuer);
            return new MockResponse(200, Headers.of("Content-Type", "application/json"), body);
        }
    }
}
