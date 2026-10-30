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
import de.cuioss.sheriff.token.validation.test.InMemoryJWKSFactory;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static de.cuioss.sheriff.token.validation.JWTValidationLogMessages.WARN;
import static de.cuioss.sheriff.token.validation.jwks.http.HttpJwksLoader.REDISCOVERY_FAILURE_THRESHOLD;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that a well-known {@link HttpJwksLoader} re-runs the discovery after
 * {@value HttpJwksLoader#REDISCOVERY_FAILURE_THRESHOLD} consecutive failed refresh cycles, so a
 * {@code jwks_uri} the IdP has moved is followed (V-10b), and that a re-discovery rejected by the egress
 * guard or the issuer gate fails closed: the current endpoint, keys, status and refresh task are kept and
 * no further re-discovery is attempted.
 * <p>
 * Refresh cycles are driven deterministically through {@link HttpJwksLoader#performBackgroundRefresh()};
 * the long refresh interval keeps the scheduled task from running a cycle of its own during a test.
 */
@EnableTestLogger
@DisplayName("HttpJwksLoader re-discovery after consecutive refresh failures")
class HttpJwksLoaderRediscoveryTest {

    private static final String TEST_KID = InMemoryJWKSFactory.DEFAULT_KEY_ID;
    private static final String MOVED_KID = "moved-endpoint-key";
    private static final String JWKS_A_PATH = "/jwks-a.json";
    private static final String JWKS_B_PATH = "/jwks-b.json";
    private static final String METADATA_JWKS_URI = "http://169.254.169.254/jwks.json";
    /** A jwks_uri no HTTP handler can be built for. */
    private static final String MALFORMED_JWKS_URI = "http://[malformed";
    private static final String FOREIGN_ISSUER = "https://foreign-issuer.example.com";
    private static final int LONG_REFRESH_INTERVAL_SECONDS = 3600;

    /** Millisecond backoff, so a failing fetch exhausts its retries quickly. */
    private static final RetryConfig FAST_RETRY = RetryConfig.builder()
            .initialDelay(Duration.ofMillis(1))
            .maxDelay(Duration.ofMillis(5))
            .build();

    private SecurityEventCounter securityEventCounter;
    private MockWebServer server;

    @BeforeEach
    void setUp() {
        securityEventCounter = new SecurityEventCounter();
        server = new MockWebServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    /**
     * Starts the server with the given dispatcher and builds a well-known loader against it.
     */
    private HttpJwksLoader startLoader(DiscoveryGenerationDispatcher dispatcher) throws IOException {
        return new HttpJwksLoader(startServer(dispatcher).build());
    }

    /**
     * Starts the server with the given dispatcher and returns a well-known loader configuration against it.
     */
    private HttpJwksLoaderConfig.HttpJwksLoaderConfigBuilder startServer(DiscoveryGenerationDispatcher dispatcher)
            throws IOException {
        server.setDispatcher(dispatcher);
        server.start();
        return HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                .wellKnownUrl(server.url(DiscoveryGenerationDispatcher.WELL_KNOWN_PATH).toString())
                .retryConfig(FAST_RETRY)
                .refreshIntervalSeconds(LONG_REFRESH_INTERVAL_SECONDS);
    }

    /**
     * Initializes the loader against {@link #JWKS_A_PATH} and then lets that path fail.
     */
    private void initializeAndFailJwksA(HttpJwksLoader loader, DiscoveryGenerationDispatcher dispatcher) {
        assertEquals(LoaderStatus.OK, loader.initJWKSLoader(securityEventCounter).join(),
                "the initial load must succeed against the first advertised jwks_uri");
        assertEquals(Optional.of(JWKS_A_PATH), fetchPath(loader));
        assertEquals(1, dispatcher.discoveryRequests(), "initialization discovers exactly once");
        dispatcher.failJwks(JWKS_A_PATH);
    }

    private static void refreshCycles(HttpJwksLoader loader, int cycles) {
        for (int cycle = 0; cycle < cycles; cycle++) {
            loader.performBackgroundRefresh();
        }
    }

    private static Optional<String> fetchPath(HttpJwksLoader loader) {
        return loader.getJwksFetchUri().map(URI::getPath);
    }

    @Test
    @DisplayName("A moved jwks_uri is followed after the failure threshold and the loader recovers to OK")
    void shouldFollowMovedJwksUriAfterThreshold() throws Exception {
        DiscoveryGenerationDispatcher dispatcher = new DiscoveryGenerationDispatcher(List.of(JWKS_A_PATH, JWKS_B_PATH));
        dispatcher.serveJwks(JWKS_B_PATH, InMemoryJWKSFactory.createValidJwksWithKeyId(MOVED_KID));
        try (HttpJwksLoader loader = startLoader(dispatcher)) {
            initializeAndFailJwksA(loader, dispatcher);
            assertFalse(loader.getKeyInfo(MOVED_KID).isPresent(), "the key of the moved endpoint is not known before the move");
            refreshCycles(loader, REDISCOVERY_FAILURE_THRESHOLD);
            int jwksAFetchesBeforeRediscovery = dispatcher.jwksFetches(JWKS_A_PATH);

            loader.performBackgroundRefresh();

            assertAll("the cycle after the threshold re-discovers and fetches the moved endpoint",
                    () -> assertEquals(2, dispatcher.discoveryRequests(), "exactly one re-discovery"),
                    () -> assertEquals(Optional.of(JWKS_B_PATH), fetchPath(loader), "the moved endpoint is published"),
                    () -> assertEquals(1, dispatcher.jwksFetches(JWKS_B_PATH), "the moved endpoint is fetched in the same cycle"),
                    () -> assertEquals(jwksAFetchesBeforeRediscovery, dispatcher.jwksFetches(JWKS_A_PATH),
                            "the old endpoint is not fetched again"),
                    () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()),
                    () -> assertTrue(loader.getKeyInfo(MOVED_KID).isPresent(), "the key of the moved endpoint is installed"),
                    () -> assertEquals(Set.of(MOVED_KID), loader.getKeyIds(), "the active key set is the moved endpoint's"));
        }
    }

    @Test
    @DisplayName("A re-discovered jwks_uri no handler can be built for keeps fetching the current endpoint")
    void shouldKeepFetchingCurrentEndpointOnUnusableRediscoveredJwksUri() throws Exception {
        DiscoveryGenerationDispatcher dispatcher = new DiscoveryGenerationDispatcher(List.of(JWKS_A_PATH, MALFORMED_JWKS_URI));
        try (HttpJwksLoader loader = startLoader(dispatcher)) {
            initializeAndFailJwksA(loader, dispatcher);
            refreshCycles(loader, REDISCOVERY_FAILURE_THRESHOLD);
            int jwksAFetchesBeforeRediscovery = dispatcher.jwksFetches(JWKS_A_PATH);

            loader.performBackgroundRefresh();

            assertTrue(dispatcher.jwksFetches(JWKS_A_PATH) > jwksAFetchesBeforeRediscovery,
                    "the re-discovering cycle still fetches the current endpoint");
            assertRejectedRediscoveryKeepsLoader(loader, dispatcher);
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    WARN.JWKS_URI_RESOLUTION_FAILED.resolveIdentifierString());

            refreshCycles(loader, REDISCOVERY_FAILURE_THRESHOLD);

            assertAll("re-discovery stays suppressed after the unusable jwks_uri",
                    () -> assertEquals(2, dispatcher.discoveryRequests(), "no further discovery request"),
                    () -> assertEquals(Optional.of(JWKS_A_PATH), fetchPath(loader)));
        }
    }

    @Test
    @DisplayName("A generation closed during re-discovery issues no fetch")
    void shouldNotFetchWhenClosedDuringRediscovery() throws Exception {
        DiscoveryGenerationDispatcher dispatcher = new DiscoveryGenerationDispatcher(List.of(JWKS_A_PATH, JWKS_B_PATH));
        try (HttpJwksLoader loader = startLoader(dispatcher)) {
            initializeAndFailJwksA(loader, dispatcher);
            refreshCycles(loader, REDISCOVERY_FAILURE_THRESHOLD);
            int jwksAFetchesBeforeRediscovery = dispatcher.jwksFetches(JWKS_A_PATH);
            dispatcher.onDiscovery(1, loader::close);

            loader.performBackgroundRefresh();

            assertAll("the closed generation adopts nothing and fetches nothing",
                    () -> assertEquals(2, dispatcher.discoveryRequests(), "the re-discovery was made"),
                    () -> assertEquals(jwksAFetchesBeforeRediscovery, dispatcher.jwksFetches(JWKS_A_PATH),
                            "the current endpoint is not fetched"),
                    () -> assertEquals(0, dispatcher.jwksFetches(JWKS_B_PATH), "the re-discovered endpoint is not fetched"),
                    () -> assertEquals(Optional.empty(), fetchPath(loader), "no endpoint is published"),
                    () -> assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus()));
        }
    }

    @Test
    @DisplayName("A refresh cycle of a closed generation does not count towards the next generation's re-discovery")
    void shouldNotCountClosedGenerationCycleInNextGeneration() throws Exception {
        DiscoveryGenerationDispatcher dispatcher = new DiscoveryGenerationDispatcher(List.of(JWKS_A_PATH));
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try (HttpJwksLoader loader = new HttpJwksLoader(startServer(dispatcher).scheduledExecutorService(executor).build())) {
            initializeAndFailJwksA(loader, dispatcher);
            dispatcher.onNextJwksFetch(JWKS_A_PATH, loader::close);

            loader.performBackgroundRefresh();

            assertEquals(LoaderStatus.UNDEFINED, loader.initJWKSLoader(securityEventCounter).join(),
                    "the next generation starts against the still failing endpoint");
            assertEquals(2, dispatcher.discoveryRequests(), "the re-initialization discovers once");

            refreshCycles(loader, REDISCOVERY_FAILURE_THRESHOLD);

            assertEquals(2, dispatcher.discoveryRequests(),
                    "the next generation re-discovers only after its own failure threshold");

            loader.performBackgroundRefresh();

            assertEquals(3, dispatcher.discoveryRequests(),
                    "the next generation re-discovers once its own failures reach the threshold");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Below the failure threshold no re-discovery is made")
    void shouldNotRediscoverBelowThreshold() throws Exception {
        DiscoveryGenerationDispatcher dispatcher = new DiscoveryGenerationDispatcher(List.of(JWKS_A_PATH, JWKS_B_PATH));
        try (HttpJwksLoader loader = startLoader(dispatcher)) {
            initializeAndFailJwksA(loader, dispatcher);

            refreshCycles(loader, REDISCOVERY_FAILURE_THRESHOLD - 1);

            assertAll("failures below the threshold keep the current endpoint",
                    () -> assertEquals(1, dispatcher.discoveryRequests(), "no second discovery request"),
                    () -> assertEquals(Optional.of(JWKS_A_PATH), fetchPath(loader)),
                    () -> assertEquals(0, dispatcher.jwksFetches(JWKS_B_PATH)),
                    () -> assertTrue(dispatcher.jwksFetches(JWKS_A_PATH) > 1, "the failing endpoint is still fetched"));
        }
    }

    @Test
    @DisplayName("An egress-blocked re-discovered jwks_uri is not adopted and re-discovery is suppressed")
    void shouldFailClosedOnEgressBlockedRediscovery() throws Exception {
        DiscoveryGenerationDispatcher dispatcher = new DiscoveryGenerationDispatcher(List.of(JWKS_A_PATH, METADATA_JWKS_URI));
        try (HttpJwksLoader loader = startLoader(dispatcher)) {
            initializeAndFailJwksA(loader, dispatcher);
            refreshCycles(loader, REDISCOVERY_FAILURE_THRESHOLD + 1);

            assertRejectedRediscoveryKeepsLoader(loader, dispatcher);

            refreshCycles(loader, REDISCOVERY_FAILURE_THRESHOLD);

            assertAll("re-discovery stays suppressed after the rejection",
                    () -> assertEquals(2, dispatcher.discoveryRequests(), "no further discovery request"),
                    () -> assertEquals(Optional.of(JWKS_A_PATH), fetchPath(loader)),
                    () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()),
                    () -> assertTrue(loader.isBackgroundRefreshActive()),
                    () -> assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.ISSUER_MISMATCH)));
        }
    }

    @Test
    @DisplayName("A re-discovery advertising a mismatching issuer is not adopted and counted exactly once")
    void shouldFailClosedOnIssuerMismatchRediscovery() throws Exception {
        DiscoveryGenerationDispatcher dispatcher = new DiscoveryGenerationDispatcher(List.of(JWKS_A_PATH, JWKS_B_PATH));
        dispatcher.advertiseIssuerFrom(1, FOREIGN_ISSUER);
        try (HttpJwksLoader loader = startLoader(dispatcher)) {
            initializeAndFailJwksA(loader, dispatcher);
            Optional<String> issuerBefore = loader.getIssuerIdentifier();
            refreshCycles(loader, REDISCOVERY_FAILURE_THRESHOLD + 1);

            assertRejectedRediscoveryKeepsLoader(loader, dispatcher);
            assertEquals(0, dispatcher.jwksFetches(JWKS_B_PATH), "the endpoint of the foreign issuer is never fetched");

            refreshCycles(loader, REDISCOVERY_FAILURE_THRESHOLD);

            assertAll("re-discovery stays suppressed after the rejection",
                    () -> assertEquals(2, dispatcher.discoveryRequests(), "no further discovery request"),
                    () -> assertEquals(Optional.of(JWKS_A_PATH), fetchPath(loader)),
                    () -> assertEquals(issuerBefore, loader.getIssuerIdentifier(), "the resolved issuer is unchanged"),
                    () -> assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.ISSUER_MISMATCH),
                            "the mismatch is counted exactly once"),
                    () -> assertEquals(0, dispatcher.jwksFetches(JWKS_B_PATH)));
        }
    }

    /**
     * Asserts the fail-closed state after a rejected re-discovery: the re-discovery happened once, and
     * the endpoint, the served keys, the status and the refresh task are those of before.
     */
    private static void assertRejectedRediscoveryKeepsLoader(HttpJwksLoader loader, DiscoveryGenerationDispatcher dispatcher) {
        assertAll("a rejected re-discovery adopts nothing",
                () -> assertEquals(2, dispatcher.discoveryRequests(), "exactly one re-discovery"),
                () -> assertEquals(Optional.of(JWKS_A_PATH), fetchPath(loader), "the current endpoint is kept"),
                () -> assertTrue(loader.getKeyInfo(TEST_KID).isPresent(), "the installed keys are still served"),
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus(), "the status is unchanged"),
                () -> assertTrue(loader.isBackgroundRefreshActive(), "the refresh task stays active"));
    }
}
