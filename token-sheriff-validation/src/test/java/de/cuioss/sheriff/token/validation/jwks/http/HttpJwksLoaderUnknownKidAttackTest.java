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
import de.cuioss.sheriff.token.commons.transport.EgressPolicy;
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.validation.test.InMemoryJWKSFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial behaviour of the opt-in unknown-kid refresh of {@link HttpJwksLoader}. The lookup is
 * reachable by any unauthenticated caller presenting a token header, so each case attacks one property
 * and pairs the negative assertion with a positive control: a flood of invented key ids reaches the
 * server at most once per window, a spray cannot displace the installed keys, a failing or hostile
 * response changes nothing and is not counted as a failed refresh cycle, and the lifecycle never lets a
 * fetch of a closed generation install keys or block the caller.
 */
@EnableTestLogger
@EnableMockWebServer
@DisplayName("HttpJwksLoader unknown-kid refresh under attack")
class HttpJwksLoaderUnknownKidAttackTest {

    private static final String DEFAULT_KID = InMemoryJWKSFactory.DEFAULT_KEY_ID;
    private static final String ROTATED_KID = UnknownKidJwksDispatcher.ROTATED_KEY_ID;
    private static final Duration WINDOW = Duration.ofSeconds(30);

    @Getter
    private final UnknownKidJwksDispatcher moduleDispatcher = new UnknownKidJwksDispatcher();

    private final AtomicLong clock = new AtomicLong(1_000L);
    private ScheduledExecutorService executor;

    @BeforeEach
    void setUp() {
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.DEFAULT_KEY);
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private HttpJwksLoaderConfig.HttpJwksLoaderConfigBuilder configBuilder(URIBuilder uriBuilder) {
        return HttpJwksLoaderConfig.builder()
                .allowLoopbackEgress(true)
                .allowInsecureHttp(true)
                .jwksUrl(uriBuilder.addPathSegment(UnknownKidJwksDispatcher.LOCAL_PATH).buildAsString())
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(3600)
                .readTimeoutSeconds(1)
                .retryConfig(RetryConfig.builder().initialDelay(Duration.ofMillis(1)).build())
                .scheduledExecutorService(executor)
                .unknownKidRefreshWindow(WINDOW);
    }

    private HttpJwksLoader initializedLoader(HttpJwksLoaderConfig config, EgressPolicy egressPolicy) {
        HttpJwksLoader loader = new HttpJwksLoader(config, egressPolicy, clock::get);
        assertEquals(LoaderStatus.OK, loader.initJWKSLoader(new SecurityEventCounter()).join(),
                "the initial load must install the default key set");
        return loader;
    }

    private HttpJwksLoader initializedLoader(HttpJwksLoaderConfig config) {
        return initializedLoader(config, config.getEgressPolicy());
    }

    private void drainExecutor() throws Exception {
        executor.submit(() -> {
        }).get(60, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("A flood of invented kids from many threads reaches the server once per window")
    void shouldBoundFloodToOneFetchPerWindow(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).build());
        int threads = 8;
        int kidsPerThread = 50;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(threads);
        List<Future<?>> lookups = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int thread = t;
            lookups.add(callers.submit(() -> {
                start.await();
                for (int k = 0; k < kidsPerThread; k++) {
                    assertTrue(loader.getKeyInfo("invented-" + thread + "-" + k).isEmpty());
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> lookup : lookups) {
            lookup.get(30, TimeUnit.SECONDS);
        }
        callers.shutdownNow();
        drainExecutor();
        int withinWindow = moduleDispatcher.getRequestCount();

        clock.addAndGet(WINDOW.toNanos());
        loader.getKeyInfo("invented-after-expiry");
        drainExecutor();

        assertAll("one fetch per window, whatever the request rate",
                () -> assertEquals(2, withinWindow, "the initial load and exactly one unknown-kid fetch"),
                () -> assertEquals(3, moduleDispatcher.getRequestCount(), "exactly one more after expiry"),
                () -> assertEquals(Set.of(DEFAULT_KID), loader.getKeyIds(), "nothing was displaced"),
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()));
        loader.close();
    }

    @Test
    @DisplayName("A kid spray during a legitimate rotation displaces nothing; the rotation follows one window later")
    void shouldNotLetSprayDisplaceKeys(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).build());
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.ROTATED_KEY);

        loader.getKeyInfo("sprayed-kid");
        drainExecutor();
        boolean rotatedBeforeExpiry = loader.getKeyInfo(ROTATED_KID).isPresent();
        drainExecutor();
        int requestsWithinWindow = moduleDispatcher.getRequestCount();

        clock.addAndGet(WINDOW.toNanos());
        loader.getKeyInfo(ROTATED_KID);
        drainExecutor();

        assertAll(
                () -> assertFalse(rotatedBeforeExpiry, "the sprayed window is consumed, the rotation waits"),
                () -> assertEquals(2, requestsWithinWindow, "the legitimate lookup in the window fetched nothing"),
                () -> assertTrue(loader.getKeyInfo(ROTATED_KID).isPresent(), "served within one window"),
                () -> assertTrue(loader.getKeyInfo(DEFAULT_KID).isPresent(), "the previous set stays in grace"),
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()));
        loader.close();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = UnknownKidJwksDispatcher.Mode.class,
            names = {"SERVER_ERROR", "SLOW", "OVERSIZED", "MALFORMED", "EMPTY_KEY_SET"})
    @DisplayName("A failing or hostile response changes nothing and is not counted")
    void shouldIgnoreFailingResponse(UnknownKidJwksDispatcher.Mode failure, URIBuilder uriBuilder) throws Exception {
        // A maximum staleness this small degrades the loader on the first counted failed cycle
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).maxStaleness(Duration.ofMillis(1)).build());
        moduleDispatcher.setSlowResponseSeconds(3);
        moduleDispatcher.serve(failure);

        assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty());
        drainExecutor();

        assertAll("state unchanged and the failure uncounted",
                () -> assertTrue(moduleDispatcher.getRequestCount() > 1, "the fetch reached the server"),
                () -> assertEquals(Set.of(DEFAULT_KID), loader.getKeyIds()),
                () -> assertTrue(loader.getKeyInfo(DEFAULT_KID).isPresent()),
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus(),
                        "an uncounted failure cannot trigger the staleness degradation"),
                () -> assertTrue(loader.getJwksFetchUri().isPresent()));
        loader.close();
    }

    @Test
    @DisplayName("Positive control: the same failure in a periodic cycle is counted and degrades a stale loader")
    void shouldCountFailingPeriodicCycle(URIBuilder uriBuilder) {
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).maxStaleness(Duration.ofMillis(1)).build());
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.SERVER_ERROR);

        loader.performBackgroundRefresh();

        assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus(),
                "a counted failed cycle applies the staleness bound");
        loader.close();
    }

    @Test
    @DisplayName("A DNS rebind between claim and fetch is blocked by the egress check")
    void shouldBlockReboundHost(URIBuilder uriBuilder) throws Exception {
        AtomicBoolean rebound = new AtomicBoolean(false);
        EgressPolicy.HostResolver resolver = host -> new InetAddress[]{
                InetAddress.getByName(rebound.get() ? "169.254.169.254" : "93.184.216.34")};
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .allowInsecureHttp(true)
                .jwksUrl(uriBuilder.addPathSegment(UnknownKidJwksDispatcher.LOCAL_PATH).buildAsString())
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(3600)
                .scheduledExecutorService(executor)
                .unknownKidRefreshWindow(WINDOW)
                .build();
        HttpJwksLoader loader = initializedLoader(config, EgressPolicy.builder().resolver(resolver).build());
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.ROTATED_KEY);

        rebound.set(true);
        loader.getKeyInfo(ROTATED_KID);
        drainExecutor();

        assertAll(
                () -> assertEquals(1, moduleDispatcher.getRequestCount(), "no fetch after the rebind"),
                () -> assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty()),
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()));
        loader.close();
    }

    @Test
    @DisplayName("A close() during the fetch installs nothing")
    void shouldInstallNothingAfterCloseDuringFetch(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).build());
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.ROTATED_KEY);
        CountDownLatch arrived = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        moduleDispatcher.holdResponses(arrived, release);

        loader.getKeyInfo(ROTATED_KID);
        assertTrue(arrived.await(10, TimeUnit.SECONDS), "the fetch reached the server");
        loader.close();
        release.countDown();
        drainExecutor();

        assertAll(
                () -> assertTrue(loader.getKeyIds().isEmpty(), "the closed loader installed nothing"),
                () -> assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty()),
                () -> assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus()));
    }

    @Test
    @DisplayName("A loader initialized again after close() refreshes on an unknown kid again")
    void shouldRefreshAfterReinitialization(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).build());
        loader.close();
        assertEquals(LoaderStatus.OK, loader.initJWKSLoader(new SecurityEventCounter()).join());
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.ROTATED_KEY);

        loader.getKeyInfo(ROTATED_KID);
        drainExecutor();

        assertTrue(loader.getKeyInfo(ROTATED_KID).isPresent());
        loader.close();
    }

    @Test
    @DisplayName("A shut-down executor never throws on the caller and releases the in-flight guard")
    void shouldTolerateShutDownExecutor(URIBuilder uriBuilder) {
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).build());
        executor.shutdownNow();

        assertDoesNotThrow(() -> assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty()));
        int afterRejection = moduleDispatcher.getRequestCount();
        loader.performBackgroundRefresh();

        assertEquals(afterRejection + 1, moduleDispatcher.getRequestCount(),
                "the guard was released, so a periodic cycle still fetches");
        loader.close();
    }

    @Test
    @DisplayName("A busy single-thread executor never blocks the lookup")
    void shouldNotBlockOnBusyExecutor(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).build());
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.ROTATED_KEY);
        CountDownLatch busy = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                busy.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        long started = System.nanoTime();
        assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        busy.countDown();
        drainExecutor();

        assertAll(
                () -> assertTrue(elapsedMillis < 1_000, "the lookup returned without waiting: " + elapsedMillis + " ms"),
                () -> assertTrue(loader.getKeyInfo(ROTATED_KID).isPresent(), "the queued fetch ran afterwards"));
        loader.close();
    }
}
