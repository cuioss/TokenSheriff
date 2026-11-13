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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Behaviour of the opt-in initial-load retry of {@link HttpJwksLoader}: the backoff sequence and its
 * caps, the stop conditions (first installed key set, terminal {@code ERROR}, {@link HttpJwksLoader#close()}),
 * the shared in-flight guard with the periodic cycle, the status contract while retrying, and the
 * unchanged cadence when the setting is off.
 * <p>
 * Most tests inject a {@link RecordingScheduler}: it records every one-shot attempt with its delay
 * instead of running it, and records the fixed-rate task without letting it fire, so the test runs each
 * attempt itself on its own thread and the delay sequence is asserted without waiting. Every JWKS
 * request is counted on the server side.
 */
@EnableTestLogger
@EnableMockWebServer
@DisplayName("HttpJwksLoader initial-load retry")
class HttpJwksLoaderInitialRetryTest {

    private static final int LONG_REFRESH_INTERVAL_SECONDS = 3600;
    private static final String WELL_KNOWN_PATH = "/.well-known/openid-configuration";
    private static final String WELL_KNOWN_JWKS_PATH = "/jwks.json";
    private static final String FOREIGN_ISSUER = "https://foreign-issuer.example.com";

    /** One attempt per fetch, so every refresh cycle issues exactly one request. */
    private static final RetryConfig SINGLE_ATTEMPT = RetryConfig.builder()
            .maxAttempts(1)
            .initialDelay(Duration.ofMillis(1))
            .maxDelay(Duration.ofMillis(5))
            .build();

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

    private static HttpJwksLoaderConfig.HttpJwksLoaderConfigBuilder directConfig(URIBuilder uriBuilder,
            ScheduledExecutorService executor) {
        return HttpJwksLoaderConfig.builder()
                .allowLoopbackEgress(true)
                .allowInsecureHttp(true)
                .jwksUrl(uriBuilder.addPathSegment(UnknownKidJwksDispatcher.LOCAL_PATH).buildAsString())
                .issuerIdentifier("test-issuer")
                .retryConfig(SINGLE_ATTEMPT)
                .refreshIntervalSeconds(LONG_REFRESH_INTERVAL_SECONDS)
                .scheduledExecutorService(executor);
    }

    private static HttpJwksLoader initializedLoader(HttpJwksLoaderConfig config, LoaderStatus expected) {
        HttpJwksLoader loader = new HttpJwksLoader(config);
        assertEquals(expected, loader.initJWKSLoader(new SecurityEventCounter()).join(),
                "unexpected status after the initial load");
        return loader;
    }

    private static List<Duration> seconds(long... values) {
        return Arrays.stream(values).mapToObj(Duration::ofSeconds).toList();
    }

    @Test
    @DisplayName("Attempts back off from 1 s, doubling up to the 30 s cap")
    void shouldBackOffUpToMaximumDelay(URIBuilder uriBuilder) {
        HttpJwksLoader loader = initializedLoader(directConfig(uriBuilder, scheduler).initialLoadRetry(true).build(),
                LoaderStatus.UNDEFINED);
        int afterInit = moduleDispatcher.getRequestCount();

        for (int attempt = 0; attempt < 6; attempt++) {
            assertTrue(scheduler.runNextOneShot(), "every failed attempt schedules its successor");
            assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus(), "a failed attempt keeps UNDEFINED");
        }

        assertAll("one fetch per attempt, delays doubling and capped at 30 s",
                () -> assertEquals(seconds(1, 2, 4, 8, 16, 30, 30), scheduler.getOneShotDelays()),
                () -> assertEquals(afterInit + 6, moduleDispatcher.getRequestCount()),
                () -> assertEquals(Duration.ofSeconds(LONG_REFRESH_INTERVAL_SECONDS), scheduler.getFixedRatePeriod(),
                        "the periodic task keeps its interval"),
                () -> assertTrue(loader.isBackgroundRefreshActive(), "the periodic task stays scheduled"));
        loader.close();
    }

    @Test
    @DisplayName("The delay never exceeds a refresh interval shorter than 30 s")
    void shouldCapDelayAtRefreshInterval(URIBuilder uriBuilder) {
        HttpJwksLoader loader = initializedLoader(directConfig(uriBuilder, scheduler)
                .refreshIntervalSeconds(5).initialLoadRetry(true).build(), LoaderStatus.UNDEFINED);

        for (int attempt = 0; attempt < 4; attempt++) {
            scheduler.runNextOneShot();
        }

        assertAll(
                () -> assertEquals(seconds(1, 2, 4, 5, 5), scheduler.getOneShotDelays()),
                () -> assertEquals(Duration.ofSeconds(5), scheduler.getFixedRatePeriod()));
        loader.close();
    }

    @Test
    @DisplayName("The first attempt that installs keys publishes OK and ends the retries")
    void shouldStopAtFirstInstalledKeySet(URIBuilder uriBuilder) {
        HttpJwksLoader loader = initializedLoader(directConfig(uriBuilder, scheduler).initialLoadRetry(true).build(),
                LoaderStatus.UNDEFINED);
        scheduler.runNextOneShot();
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.DEFAULT_KEY);

        scheduler.runNextOneShot();

        assertAll("keys installed, no further attempt",
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()),
                () -> assertTrue(loader.getKeyInfo(InMemoryJWKSFactory.DEFAULT_KEY_ID).isPresent()),
                () -> assertEquals(seconds(1, 2), scheduler.getOneShotDelays(), "nothing scheduled after the success"),
                () -> assertEquals(0, scheduler.pendingOneShots()));
        loader.close();
    }

    @Test
    @DisplayName("A successful initial load schedules no attempt")
    void shouldNotRetrySuccessfulInitialLoad(URIBuilder uriBuilder) {
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.DEFAULT_KEY);

        HttpJwksLoader loader = initializedLoader(directConfig(uriBuilder, scheduler).initialLoadRetry(true).build(),
                LoaderStatus.OK);

        assertTrue(scheduler.getOneShotDelays().isEmpty());
        loader.close();
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
    @DisplayName("An attempt and the periodic cycle never fetch at the same time, and the status stays UNDEFINED")
    void shouldShareInFlightGuardWithPeriodicCycle(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(directConfig(uriBuilder, scheduler).initialLoadRetry(true).build(),
                LoaderStatus.UNDEFINED);
        int afterInit = moduleDispatcher.getRequestCount();
        CountDownLatch arrived = new CountDownLatch(1);
        CountDownLatch gate = new CountDownLatch(1);
        moduleDispatcher.holdResponses(arrived, gate);
        CompletableFuture<Boolean> attempt;
        try {
            attempt = CompletableFuture.supplyAsync(scheduler::runNextOneShot);
            assertTrue(arrived.await(10, TimeUnit.SECONDS), "the attempt's fetch must reach the server");

            scheduler.getPeriodicTask().run();

            assertAll("while the attempt fetches",
                    () -> assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus(), "never LOADING"),
                    () -> assertEquals(afterInit + 1, moduleDispatcher.getRequestCount(),
                            "the periodic cycle skipped its fetch"));
        } finally {
            gate.countDown();
        }
        assertTrue(attempt.get(10, TimeUnit.SECONDS));

        assertAll("after the attempt",
                () -> assertEquals(afterInit + 1, moduleDispatcher.getRequestCount()),
                () -> assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus()),
                () -> assertEquals(seconds(1, 2), scheduler.getOneShotDelays()));
        loader.close();
    }

    @Test
    @DisplayName("Unset, a failed initial load schedules no attempt next to the periodic task")
    void shouldScheduleNothingWhenUnset(URIBuilder uriBuilder) {
        HttpJwksLoader loader = initializedLoader(directConfig(uriBuilder, scheduler).build(), LoaderStatus.UNDEFINED);

        assertAll(
                () -> assertTrue(scheduler.getOneShotDelays().isEmpty()),
                () -> assertEquals(Duration.ofSeconds(LONG_REFRESH_INTERVAL_SECONDS), scheduler.getFixedRatePeriod()),
                () -> assertTrue(loader.isBackgroundRefreshActive()));
        loader.close();
    }

    @Test
    @DisplayName("Unset, the server sees no request inside the refresh interval; set, it sees the first attempt")
    void shouldKeepTodaysCadenceWhenUnset(URIBuilder uriBuilder) {
        ScheduledExecutorService realExecutor = Executors.newSingleThreadScheduledExecutor();
        try {
            HttpJwksLoader unset = initializedLoader(directConfig(uriBuilder, realExecutor).build(),
                    LoaderStatus.UNDEFINED);
            int afterUnsetInit = moduleDispatcher.getRequestCount();

            await("no request inside the refresh interval")
                    .during(Duration.ofMillis(1500))
                    .atMost(Duration.ofSeconds(3))
                    .until(() -> moduleDispatcher.getRequestCount() == afterUnsetInit);
            unset.close();

            HttpJwksLoader set = initializedLoader(directConfig(uriBuilder, realExecutor).initialLoadRetry(true).build(),
                    LoaderStatus.UNDEFINED);
            int afterSetInit = moduleDispatcher.getRequestCount();

            await("the first attempt after 1 s")
                    .atMost(Duration.ofSeconds(5))
                    .until(() -> moduleDispatcher.getRequestCount() > afterSetInit);
            set.close();
        } finally {
            realExecutor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Well-known mode: a failed initial JWKS fetch is retried until keys are installed")
    void shouldRetryFailedFetchInWellKnownMode() throws IOException {
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
    void shouldStopAtTerminalErrorInWellKnownMode() throws IOException {
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
    void shouldNotRetryTerminalRejectionAtInitialization() throws IOException {
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
     * Scheduler that records instead of running: a one-shot task with a positive delay (an initial-load
     * retry attempt) is queued with its delay until the test runs it, and the fixed-rate task is recorded
     * together with its period while a placeholder that never fires keeps the periodic task observable as
     * scheduled. Tasks without a delay run as usual.
     */
    private static final class RecordingScheduler extends ScheduledThreadPoolExecutor {

        @Getter
        private final List<Duration> oneShotDelays = new CopyOnWriteArrayList<>();
        private final Queue<Runnable> oneShots = new ConcurrentLinkedQueue<>();
        @Getter
        private volatile @Nullable Duration fixedRatePeriod;
        private volatile @Nullable Runnable periodicTask;

        RecordingScheduler() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            if (delay <= 0) {
                return super.schedule(command, delay, unit);
            }
            oneShotDelays.add(Duration.ofNanos(unit.toNanos(delay)));
            oneShots.add(command);
            return super.schedule(() -> {
            }, 1, TimeUnit.DAYS);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
                TimeUnit unit) {
            periodicTask = command;
            fixedRatePeriod = Duration.ofNanos(unit.toNanos(period));
            return super.scheduleAtFixedRate(() -> {
            }, 1, 1, TimeUnit.DAYS);
        }

        /**
         * Runs the oldest recorded one-shot task on the calling thread.
         *
         * @return {@code true} if a task was run, {@code false} if none was pending
         */
        boolean runNextOneShot() {
            Runnable next = oneShots.poll();
            if (next == null) {
                return false;
            }
            next.run();
            return true;
        }

        int pendingOneShots() {
            return oneShots.size();
        }

        Runnable getPeriodicTask() {
            Runnable task = periodicTask;
            assertNotNull(task, "the periodic task must have been scheduled");
            return task;
        }
    }

    /**
     * Well-known endpoint whose discovery document can advertise a matching issuer, a foreign issuer or no
     * {@code jwks_uri} at all (an unavailable discovery), and whose JWKS endpoint can fail with HTTP 500.
     * JWKS fetches are counted.
     */
    private static final class WellKnownDispatcher extends Dispatcher {

        enum Discovery {
            MATCHING, NO_JWKS_URI, FOREIGN_ISSUER
        }

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

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            HttpUrl url = request.getUrl();
            String path = url.encodedPath();
            Headers json = Headers.of("Content-Type", "application/json");
            if (WELL_KNOWN_PATH.equals(path)) {
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
