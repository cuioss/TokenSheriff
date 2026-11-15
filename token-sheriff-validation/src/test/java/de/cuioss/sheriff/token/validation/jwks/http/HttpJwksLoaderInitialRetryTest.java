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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Behaviour of the opt-in initial-load retry of {@link HttpJwksLoader} for a direct JWKS endpoint: the
 * backoff sequence and its caps, the stop at the first installed key set, the shared in-flight guard with
 * the periodic cycle, the status contract while retrying, and the unchanged cadence when the setting is off.
 * The lifecycle stops ({@link HttpJwksLoader#close()}, terminal {@code ERROR}) and the well-known mode are
 * covered by {@link HttpJwksLoaderInitialRetryLifecycleTest}.
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

    static final int LONG_REFRESH_INTERVAL_SECONDS = 3600;

    /** One attempt per fetch, so every refresh cycle issues exactly one request. */
    static final RetryConfig SINGLE_ATTEMPT = RetryConfig.builder()
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

    static HttpJwksLoaderConfig.HttpJwksLoaderConfigBuilder directConfig(URIBuilder uriBuilder,
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

    static HttpJwksLoader initializedLoader(HttpJwksLoaderConfig config, LoaderStatus expected) {
        HttpJwksLoader loader = new HttpJwksLoader(config);
        assertEquals(expected, loader.initJWKSLoader(new SecurityEventCounter()).join(),
                "unexpected status after the initial load");
        return loader;
    }

    static List<Duration> seconds(long... values) {
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
    @DisplayName("A pending attempt runs no cycle once a periodic cycle installed keys")
    void shouldNotRunPendingAttemptAfterKeysInstalled(URIBuilder uriBuilder) {
        HttpJwksLoader loader = initializedLoader(directConfig(uriBuilder, scheduler).initialLoadRetry(true).build(),
                LoaderStatus.UNDEFINED);
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.DEFAULT_KEY);
        scheduler.getPeriodicTask().run();
        assertEquals(LoaderStatus.OK, loader.getLoaderStatus(), "the periodic cycle installed the key set");
        int requestsAtInstall = moduleDispatcher.getRequestCount();

        assertTrue(scheduler.runNextOneShot(), "the attempt scheduled at initialization was still pending");

        assertAll("the pending attempt is inert",
                () -> assertEquals(requestsAtInstall, moduleDispatcher.getRequestCount(), "no further JWKS fetch"),
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()),
                () -> assertEquals(seconds(1), scheduler.getOneShotDelays(), "no successor is scheduled"),
                () -> assertEquals(0, scheduler.pendingOneShots()));
        loader.close();
    }

    /**
     * Scheduler that records instead of running: a one-shot task with a positive delay (an initial-load
     * retry attempt) is queued with its delay until the test runs it, and the fixed-rate task is recorded
     * together with its period while a placeholder that never fires keeps the periodic task observable as
     * scheduled. Tasks without a delay run as usual. Shared with {@link HttpJwksLoaderInitialRetryLifecycleTest}.
     */
    static final class RecordingScheduler extends ScheduledThreadPoolExecutor {

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
}
