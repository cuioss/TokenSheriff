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
import de.cuioss.sheriff.token.validation.jwks.key.KeyInfo;
import de.cuioss.sheriff.token.validation.test.InMemoryJWKSFactory;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static de.cuioss.sheriff.token.validation.jwks.http.HttpJwksLoader.REDISCOVERY_FAILURE_THRESHOLD;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Specification cases of the opt-in unknown-kid refresh of {@link HttpJwksLoader} that the functional and
 * the adversarial test classes leave open: changed key material under an existing key id, a window that
 * elapses while a fetch is in flight, an unknown-kid fetch overlapping a periodic cycle, repeated on-demand
 * rotations against the retired-set bound, the separation from well-known re-discovery, the refresh in
 * well-known mode, and a spray sustained across several windows.
 * <p>
 * Each negative assertion is paired with a positive control that shows the same setup can produce the
 * forbidden outcome's opposite. Every fetch is counted on the server side per path by
 * {@link DiscoveryGenerationDispatcher}; the fetch runs on a single-thread executor that the test drains,
 * and the window is measured on an injected monotonic clock.
 */
@EnableTestLogger
@DisplayName("HttpJwksLoader unknown-kid refresh specification gaps")
class HttpJwksLoaderUnknownKidGapsTest {

    private static final String DEFAULT_KID = InMemoryJWKSFactory.DEFAULT_KEY_ID;
    private static final String ROTATED_KID = "gap-rotated-key";
    private static final String JWKS_PATH = "/jwks-a.json";
    private static final Duration WINDOW = Duration.ofSeconds(30);
    private static final int GATE_TIMEOUT_SECONDS = 10;

    /** Millisecond backoff, so a failing fetch exhausts its retries quickly. */
    private static final RetryConfig FAST_RETRY = RetryConfig.builder()
            .initialDelay(Duration.ofMillis(1))
            .maxDelay(Duration.ofMillis(5))
            .build();

    private final AtomicLong clock = new AtomicLong(1_000L);
    private MockWebServer server;
    private DiscoveryGenerationDispatcher dispatcher;
    private ScheduledExecutorService executor;

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        server.close();
    }

    private void startServer(List<String> advertisedJwksUris) throws IOException {
        dispatcher = new DiscoveryGenerationDispatcher(advertisedJwksUris);
        server.setDispatcher(dispatcher);
        server.start();
    }

    private HttpJwksLoaderConfig.HttpJwksLoaderConfigBuilder directConfig() throws IOException {
        startServer(List.of(JWKS_PATH));
        return HttpJwksLoaderConfig.builder()
                .allowLoopbackEgress(true)
                .allowInsecureHttp(true)
                .jwksUrl(server.url(JWKS_PATH).toString())
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(3600)
                .retryConfig(FAST_RETRY)
                .scheduledExecutorService(executor)
                .unknownKidRefreshWindow(WINDOW);
    }

    private HttpJwksLoaderConfig wellKnownConfig() throws IOException {
        startServer(List.of(JWKS_PATH));
        return HttpJwksLoaderConfig.builder()
                .allowLoopbackEgress(true)
                .allowInsecureHttp(true)
                .wellKnownUrl(server.url(DiscoveryGenerationDispatcher.WELL_KNOWN_PATH).toString())
                .refreshIntervalSeconds(3600)
                .retryConfig(FAST_RETRY)
                .scheduledExecutorService(executor)
                .unknownKidRefreshWindow(WINDOW)
                .build();
    }

    private HttpJwksLoader initializedLoader(HttpJwksLoaderConfig config) {
        HttpJwksLoader loader = new HttpJwksLoader(config, config.getEgressPolicy(), clock::get);
        assertEquals(LoaderStatus.OK, loader.initJWKSLoader(new SecurityEventCounter()).join(),
                "the initial load must install the default key set");
        return loader;
    }

    private void drainExecutor() throws Exception {
        executor.submit(() -> {
        }).get(30, TimeUnit.SECONDS);
    }

    private int fetches() {
        return dispatcher.jwksFetches(JWKS_PATH);
    }

    /**
     * Holds the next fetch of {@link #JWKS_PATH} on the server until the returned gate is released.
     */
    private Gate holdNextFetch() {
        Gate gate = new Gate(new CountDownLatch(1), new CountDownLatch(1));
        dispatcher.onNextJwksFetch(JWKS_PATH, () -> {
            gate.arrived().countDown();
            try {
                assertTrue(gate.release().await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS), "the gate was released");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        return gate;
    }

    /**
     * Joins the keys of several single-key JWKS documents into one JWKS document.
     */
    private static String combinedJwks(String... jwks) {
        return Arrays.stream(jwks)
                .map(document -> document.substring(document.indexOf('[') + 1, document.lastIndexOf(']')))
                .collect(Collectors.joining(",", "{\"keys\":[", "]}"));
    }

    @Test
    @DisplayName("Changed key material under an installed kid causes no fetch on a hit and arrives with a set holding the requested kid")
    void shouldNotFetchOnHitWithChangedKeyMaterial() throws Exception {
        HttpJwksLoader loader = initializedLoader(directConfig().build());
        PublicKey original = loader.getKeyInfo(DEFAULT_KID).map(KeyInfo::key).orElseThrow();
        PublicKey changed = InMemoryKeyMaterialHandler.getPublicKey(InMemoryKeyMaterialHandler.Algorithm.RS384, DEFAULT_KID);
        dispatcher.serveJwks(JWKS_PATH, combinedJwks(
                InMemoryKeyMaterialHandler.createJwks(InMemoryKeyMaterialHandler.Algorithm.RS384, DEFAULT_KID),
                InMemoryJWKSFactory.createValidJwksWithKeyId(ROTATED_KID)));

        PublicKey onHit = loader.getKeyInfo(DEFAULT_KID).map(KeyInfo::key).orElseThrow();
        drainExecutor();
        int afterHit = fetches();
        assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty());
        drainExecutor();

        assertAll("a hit never refreshes, a miss installs the changed material with the requested kid",
                () -> assertEquals(original, onHit, "the hit serves the installed material"),
                () -> assertEquals(1, afterHit, "the hit scheduled no fetch"),
                () -> assertEquals(2, fetches(), "positive control: the miss fetched once"),
                () -> assertEquals(changed, loader.getKeyInfo(DEFAULT_KID).map(KeyInfo::key).orElseThrow(),
                        "the installed set now carries the changed material for the existing kid"),
                () -> assertTrue(loader.getKeyInfo(ROTATED_KID).isPresent()));
        loader.close();
    }

    @Test
    @DisplayName("A window elapsing while a fetch is in flight schedules no second fetch and leaves the window claimable")
    void shouldNotStackFetchesWhenWindowElapsesInFlight() throws Exception {
        HttpJwksLoader loader = initializedLoader(directConfig().build());
        Gate gate = holdNextFetch();

        assertTrue(loader.getKeyInfo("invented-first").isEmpty());
        assertTrue(gate.arrived().await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS), "the first fetch is in flight");
        clock.addAndGet(WINDOW.toNanos());
        assertTrue(loader.getKeyInfo("invented-in-second-window").isEmpty());
        gate.release().countDown();
        drainExecutor();
        int afterRelease = fetches();

        assertTrue(loader.getKeyInfo("invented-after-release").isEmpty());
        drainExecutor();
        int afterClaim = fetches();
        assertTrue(loader.getKeyInfo("invented-within-claimed-window").isEmpty());
        drainExecutor();

        assertAll("at most one fetch is ever in flight, and the in-flight miss claimed no window",
                () -> assertEquals(2, afterRelease, "the miss during the in-flight fetch queued nothing"),
                () -> assertEquals(3, afterClaim, "the second window was still claimable after the release"),
                () -> assertEquals(3, fetches(), "positive control: that claim consumed the window"));
        loader.close();
    }

    @Test
    @DisplayName("A periodic cycle overlapping an unknown-kid fetch skips its fetch uncounted; afterwards it fetches")
    void shouldSkipPeriodicCycleOverlappingUnknownKidFetch() throws Exception {
        HttpJwksLoader loader = initializedLoader(directConfig().build());
        dispatcher.serveJwks(JWKS_PATH, InMemoryJWKSFactory.createValidJwksWithKeyId(ROTATED_KID));
        Gate gate = holdNextFetch();

        assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty());
        assertTrue(gate.arrived().await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS), "the unknown-kid fetch is in flight");
        loader.performBackgroundRefresh();
        int duringOverlap = fetches();
        LoaderStatus statusDuringOverlap = loader.getLoaderStatus();
        gate.release().countDown();
        drainExecutor();
        loader.performBackgroundRefresh();

        assertAll("the guard serialises the two fetch paths",
                () -> assertEquals(2, duringOverlap, "the overlapping periodic cycle skipped its fetch"),
                () -> assertEquals(LoaderStatus.OK, statusDuringOverlap, "the skipped cycle changed nothing"),
                () -> assertTrue(loader.getKeyInfo(ROTATED_KID).isPresent(), "the unknown-kid fetch installed its set"),
                () -> assertEquals(3, fetches(), "positive control: a periodic cycle with a free guard fetches"));
        loader.close();
    }

    @Test
    @DisplayName("Repeated on-demand rotations keep the retired key sets within maxRetiredKeySets")
    void shouldBoundRetiredSetsAcrossOnDemandRotations() throws Exception {
        HttpJwksLoader loader = initializedLoader(directConfig().maxRetiredKeySets(2).build());
        List<String> rotations = List.of("rotation-1", "rotation-2", "rotation-3", "rotation-4");

        for (String kid : rotations) {
            dispatcher.serveJwks(JWKS_PATH, InMemoryJWKSFactory.createValidJwksWithKeyId(kid));
            clock.addAndGet(WINDOW.toNanos());
            assertTrue(loader.getKeyInfo(kid).isEmpty(), "the triggering lookup is refused");
            drainExecutor();
            assertTrue(loader.getKeyInfo(kid).isPresent(), "the rotation to " + kid + " was installed");
        }

        assertAll("the two most recent retired sets are kept, older ones are dropped",
                () -> assertEquals(1 + rotations.size(), fetches(), "one fetch per rotation"),
                () -> assertEquals(Set.of("rotation-4"), loader.getKeyIds()),
                () -> assertTrue(loader.getKeyInfo("rotation-3").isPresent(), "positive control: newest retired set"),
                () -> assertTrue(loader.getKeyInfo("rotation-2").isPresent(), "second retired set within the bound"),
                () -> assertTrue(loader.getKeyInfo("rotation-1").isEmpty(), "beyond the bound"),
                () -> assertTrue(loader.getKeyInfo(DEFAULT_KID).isEmpty(), "beyond the bound"));
        loader.close();
    }

    @Test
    @DisplayName("Failed unknown-kid fetches do not drive well-known re-discovery; failed periodic cycles do")
    void shouldNotDriveRediscoveryFromUnknownKidFailures() throws Exception {
        HttpJwksLoader loader = initializedLoader(wellKnownConfig());
        dispatcher.failJwks(JWKS_PATH);

        for (int miss = 0; miss <= REDISCOVERY_FAILURE_THRESHOLD; miss++) {
            clock.addAndGet(WINDOW.toNanos());
            assertTrue(loader.getKeyInfo("invented-" + miss).isEmpty());
            drainExecutor();
        }
        int afterMisses = fetches();
        int discoveriesAfterMisses = dispatcher.discoveryRequests();
        for (int cycle = 0; cycle < REDISCOVERY_FAILURE_THRESHOLD; cycle++) {
            loader.performBackgroundRefresh();
        }
        int discoveriesAtThreshold = dispatcher.discoveryRequests();
        loader.performBackgroundRefresh();

        assertAll("only counted periodic cycles reach the re-discovery threshold",
                () -> assertTrue(afterMisses > 1, "the unknown-kid fetches reached the failing endpoint"),
                () -> assertEquals(1, discoveriesAfterMisses, "failed unknown-kid fetches triggered no discovery"),
                () -> assertEquals(1, discoveriesAtThreshold, "the unknown-kid failures were not counted towards it"),
                () -> assertEquals(2, dispatcher.discoveryRequests(),
                        "positive control: the cycle after the threshold re-discovers"),
                () -> assertEquals(Set.of(DEFAULT_KID), loader.getKeyIds(), "the installed keys stay in place"));
        loader.close();
    }

    @Test
    @DisplayName("In well-known mode an unknown-kid fetch uses the published endpoint without a new discovery")
    void shouldRefreshInWellKnownModeWithoutDiscovery() throws Exception {
        HttpJwksLoader loader = initializedLoader(wellKnownConfig());
        dispatcher.serveJwks(JWKS_PATH, InMemoryJWKSFactory.createValidJwksWithKeyId(ROTATED_KID));

        assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty());
        drainExecutor();

        assertAll("the discovered endpoint is refreshed on demand",
                () -> assertTrue(loader.getKeyInfo(ROTATED_KID).isPresent(), "the rotated key is installed"),
                () -> assertEquals(2, fetches(), "one on-demand fetch of the advertised jwks_uri"),
                () -> assertEquals(1, dispatcher.discoveryRequests(), "no discovery was repeated for it"),
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()));
        loader.close();
    }

    @Test
    @DisplayName("A spray sustained across three windows fetches once per window and displaces nothing")
    void shouldBoundSustainedSprayToOneFetchPerWindow() throws Exception {
        HttpJwksLoader loader = initializedLoader(directConfig().build());
        int windows = 3;
        int missesPerWindow = 10;
        long step = WINDOW.toNanos() / missesPerWindow;

        for (int miss = 0; miss < windows * missesPerWindow; miss++) {
            assertTrue(loader.getKeyInfo("sprayed-" + miss).isEmpty());
            drainExecutor();
            clock.addAndGet(step);
        }
        int afterSpray = fetches();
        Set<String> keyIdsAfterSpray = loader.getKeyIds();

        dispatcher.serveJwks(JWKS_PATH, InMemoryJWKSFactory.createValidJwksWithKeyId(ROTATED_KID));
        assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty());
        drainExecutor();

        assertAll("the spray is bounded by the window, not by its rate",
                () -> assertEquals(1 + windows, afterSpray, "exactly one fetch per window"),
                () -> assertEquals(Set.of(DEFAULT_KID), keyIdsAfterSpray, "no sprayed fetch displaced the keys"),
                () -> assertTrue(loader.getKeyInfo(ROTATED_KID).isPresent(),
                        "positive control: a legitimate rotation in the next window is installed"),
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()));
        loader.close();
    }

    /**
     * A server-side hold of one fetch: {@code arrived} counts down when the fetch reached the server,
     * {@code release} lets its response go.
     *
     * @param arrived signalled when the held fetch arrives
     * @param release released by the test to let the response go
     */
    private record Gate(CountDownLatch arrived, CountDownLatch release) {
    }
}
