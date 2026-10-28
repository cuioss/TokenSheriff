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
import de.cuioss.sheriff.token.validation.test.dispatcher.JwksResolveDispatcher;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@DisplayName("HttpJwksLoader Async Initialization Tests")
@EnableMockWebServer
class HttpJwksLoaderAsyncInitializationTest {

    @Getter
    private final JwksResolveDispatcher moduleDispatcher = new JwksResolveDispatcher();

    @BeforeEach
    void setUp() {
        moduleDispatcher.setCallCounter(0);
        moduleDispatcher.returnDefault();
    }

    @Test
    @DisplayName("Constructor should not block or perform I/O operations")
    void constructorShouldNotBlockOrPerformIO(URIBuilder uriBuilder) {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                .jwksUrl(jwksEndpoint)
                .issuerIdentifier("test-issuer")
                .build();

        // Measure constructor execution time
        long startTime = System.nanoTime();
        try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
            long constructorDurationNanos = System.nanoTime() - startTime;

            // Constructor must not perform blocking I/O. The 200ms bound is a proxy for that
            // (a real network call would take seconds via DNS/connect timeouts) and is loose
            // enough to survive a GC pause under parallel surefire forks; the "no I/O" contract is
            // asserted precisely below via the call counter and UNDEFINED status.
            long maxAllowedNanos = TimeUnit.MILLISECONDS.toNanos(200);
            assertTrue(constructorDurationNanos < maxAllowedNanos,
                    "Constructor took %d ns (%.2f ms), should be < %d ns (200 ms)".formatted(
                            constructorDurationNanos,
                            constructorDurationNanos / 1_000_000.0,
                            maxAllowedNanos));

            // Status should be UNDEFINED immediately after construction (no loading yet)
            assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus(),
                    "Status should be UNDEFINED immediately after construction");

            // Verify no HTTP calls were made during construction
            assertEquals(0, moduleDispatcher.getCallCounter(),
                    "No HTTP calls should be made during construction");
        }
    }

    @Test
    @DisplayName("initJWKSLoader should return non-completed CompletableFuture")
    void initJWKSLoaderShouldReturnAsyncCompletableFuture(URIBuilder uriBuilder) {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                .jwksUrl(jwksEndpoint)
                .issuerIdentifier("test-issuer")
                .build();

        try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
            SecurityEventCounter counter = new SecurityEventCounter();

            // Call initJWKSLoader - should return CompletableFuture
            CompletableFuture<LoaderStatus> initFuture = loader.initJWKSLoader(counter);

            // Verify it returns a CompletableFuture
            assertNotNull(initFuture, "initJWKSLoader should return a CompletableFuture");

            // The future should complete eventually with OK status
            LoaderStatus finalStatus = initFuture.join();
            assertEquals(LoaderStatus.OK, finalStatus, "Initialization should complete with OK status");

            // After completion, loader should be in OK state
            assertEquals(LoaderStatus.OK, loader.getLoaderStatus(),
                    "Loader status should be OK after successful initialization");
        }
    }

    @Test
    @DisplayName("Status should transition atomically during async initialization")
    void statusShouldTransitionAtomicallyDuringAsyncInit(URIBuilder uriBuilder) {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                .jwksUrl(jwksEndpoint)
                .issuerIdentifier("test-issuer")
                .build();

        try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
            SecurityEventCounter counter = new SecurityEventCounter();

            // Initial status should be UNDEFINED
            assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus(),
                    "Initial status should be UNDEFINED");

            // Start async initialization
            CompletableFuture<LoaderStatus> initFuture = loader.initJWKSLoader(counter);

            // Wait for status to transition to LOADING, then to OK
            Awaitility.await()
                    .atMost(Duration.ofSeconds(5))
                    .until(() -> loader.getLoaderStatus() == LoaderStatus.OK);

            // Verify final status
            assertEquals(LoaderStatus.OK, loader.getLoaderStatus(),
                    "Final status should be OK");

            // Verify future completed with OK
            assertTrue(initFuture.isDone(), "Future should be completed");
            assertEquals(LoaderStatus.OK, initFuture.join(), "Future should complete with OK");
        }
    }

    @Test
    @DisplayName("Concurrent initJWKSLoader calls share one future and start exactly one init fetch and one refresh task")
    void concurrentInitShouldStartExactlyOneInitialization(URIBuilder uriBuilder) throws Exception {
        int callers = 8;
        CountingScheduledExecutor executor = new CountingScheduledExecutor();
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                .jwksUrl(uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString())
                .issuerIdentifier("test-issuer")
                .scheduledExecutorService(executor)
                .refreshIntervalSeconds(60)
                .build();
        // Holding the init fetch keeps every caller inside the initialization window.
        CountDownLatch responseGate = new CountDownLatch(1);
        moduleDispatcher.setResponseGate(responseGate);
        ExecutorService callerPool = Executors.newFixedThreadPool(callers);

        try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
            SecurityEventCounter counter = new SecurityEventCounter();
            CountDownLatch start = new CountDownLatch(1);
            List<Future<CompletableFuture<LoaderStatus>>> calls = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                calls.add(callerPool.submit(() -> {
                    start.await();
                    return loader.initJWKSLoader(counter);
                }));
            }
            start.countDown();
            List<CompletableFuture<LoaderStatus>> futures = new ArrayList<>();
            for (Future<CompletableFuture<LoaderStatus>> call : calls) {
                futures.add(call.get(10, TimeUnit.SECONDS));
            }
            responseGate.countDown();

            CompletableFuture<LoaderStatus> first = futures.getFirst();
            assertEquals(LoaderStatus.OK, first.get(10, TimeUnit.SECONDS));
            assertAll("one initialization serves every concurrent caller",
                    () -> assertTrue(futures.stream().allMatch(future -> future == first),
                            "every caller must receive the same future"),
                    () -> assertEquals(1, moduleDispatcher.getCallCounter(), "exactly one init fetch"),
                    () -> assertEquals(1, executor.getFixedRateSchedules(), "exactly one refresh task"));
        } finally {
            responseGate.countDown();
            moduleDispatcher.setResponseGate(null);
            callerPool.shutdownNow();
            executor.shutdownNow();
        }
    }

    private HttpJwksLoaderConfig.HttpJwksLoaderConfigBuilder baseConfig(URIBuilder uriBuilder, int refreshIntervalSeconds) {
        return HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                .jwksUrl(uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString())
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(refreshIntervalSeconds);
    }

    @Test
    @DisplayName("Initializing again after close() fails cleanly when the loader owns the shut-down refresh executor")
    void initAfterCloseWithOwnedExecutorFailsCleanly(URIBuilder uriBuilder) {
        // No executor supplied: the config builds and owns one, and close() shuts it down.
        try (HttpJwksLoader loader = new HttpJwksLoader(baseConfig(uriBuilder, 60).build())) {
            SecurityEventCounter counter = new SecurityEventCounter();
            assertEquals(LoaderStatus.OK, loader.initJWKSLoader(counter).join());
            loader.close();
            int callsBeforeReinit = moduleDispatcher.getCallCounter();

            CompletableFuture<LoaderStatus> rejected = loader.initJWKSLoader(counter);
            CompletableFuture<LoaderStatus> rejectedAgain = loader.initJWKSLoader(counter);

            ExecutionException failure = assertThrows(ExecutionException.class, () -> rejected.get(5, TimeUnit.SECONDS));
            assertAll("the unusable loader reports its state instead of stranding itself",
                    () -> assertInstanceOf(IllegalStateException.class, failure.getCause()),
                    () -> assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus(),
                            "the status must not be left in LOADING"),
                    () -> assertNotSame(rejected, rejectedAgain, "the failed future must not be cached"),
                    () -> assertEquals(callsBeforeReinit, moduleDispatcher.getCallCounter(), "no fetch is started"),
                    () -> assertTrue(loader.getKeyIds().isEmpty(), "no key may be installed"),
                    () -> assertFalse(loader.isBackgroundRefreshActive(), "no refresh task may run"));
        }
    }

    @Test
    @DisplayName("A closed loader without background refresh can be initialized again")
    void initAfterCloseWithoutRefreshReinitializes(URIBuilder uriBuilder) {
        try (HttpJwksLoader loader = new HttpJwksLoader(baseConfig(uriBuilder, 0).build())) {
            SecurityEventCounter counter = new SecurityEventCounter();
            assertEquals(LoaderStatus.OK, loader.initJWKSLoader(counter).join());
            loader.close();
            assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus(), "close() resets the loader");

            LoaderStatus reinitialized = loader.initJWKSLoader(counter).join();

            assertAll("the second initialization is a full one",
                    () -> assertEquals(LoaderStatus.OK, reinitialized),
                    () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()),
                    () -> assertFalse(loader.getKeyIds().isEmpty(), "keys are installed again"));
        }
    }

    @Test
    @DisplayName("A closed loader with a caller-supplied running executor can be initialized again")
    void initAfterCloseWithSuppliedExecutorReinitializes(URIBuilder uriBuilder) {
        CountingScheduledExecutor executor = new CountingScheduledExecutor();
        try (HttpJwksLoader loader = new HttpJwksLoader(baseConfig(uriBuilder, 60).scheduledExecutorService(executor).build())) {
            SecurityEventCounter counter = new SecurityEventCounter();
            assertEquals(LoaderStatus.OK, loader.initJWKSLoader(counter).join());
            loader.close();

            LoaderStatus reinitialized = loader.initJWKSLoader(counter).join();

            assertAll("the caller's executor survives close() and serves the second initialization",
                    () -> assertEquals(LoaderStatus.OK, reinitialized),
                    () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()),
                    () -> assertTrue(loader.isBackgroundRefreshActive(), "the second initialization schedules a refresh"),
                    () -> assertEquals(2, executor.getFixedRateSchedules(), "one refresh task per initialization"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("close() during an initialization in flight keeps the loader closed")
    void closeDuringInitializationIsNotUndone(URIBuilder uriBuilder) throws Exception {
        CountingScheduledExecutor executor = new CountingScheduledExecutor();
        CountDownLatch responseGate = new CountDownLatch(1);
        moduleDispatcher.setResponseGate(responseGate);

        try (HttpJwksLoader loader = new HttpJwksLoader(baseConfig(uriBuilder, 60).scheduledExecutorService(executor).build())) {
            CompletableFuture<LoaderStatus> inFlight = loader.initJWKSLoader(new SecurityEventCounter());
            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> moduleDispatcher.getCallCounter() >= 1);

            loader.close();
            responseGate.countDown();
            inFlight.get(10, TimeUnit.SECONDS);

            assertAll("the initialization that was in flight leaves nothing behind",
                    () -> assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus()),
                    () -> assertTrue(loader.getKeyIds().isEmpty(), "no key may be installed after close()"),
                    () -> assertEquals(0, executor.getFixedRateSchedules(), "no refresh task may be scheduled"),
                    () -> assertFalse(loader.isBackgroundRefreshActive()));
        } finally {
            responseGate.countDown();
            moduleDispatcher.setResponseGate(null);
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("A second initialization after close() mid-initialization leaves exactly one refresh task")
    void initAfterCloseDuringInitializationLeavesOneRefreshTask(URIBuilder uriBuilder) throws Exception {
        CountingScheduledExecutor executor = new CountingScheduledExecutor();
        CountDownLatch responseGate = new CountDownLatch(1);
        moduleDispatcher.setResponseGate(responseGate);

        try (HttpJwksLoader loader = new HttpJwksLoader(baseConfig(uriBuilder, 60).scheduledExecutorService(executor).build())) {
            SecurityEventCounter counter = new SecurityEventCounter();
            CompletableFuture<LoaderStatus> first = loader.initJWKSLoader(counter);
            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> moduleDispatcher.getCallCounter() >= 1);
            loader.close();
            CompletableFuture<LoaderStatus> second = loader.initJWKSLoader(counter);
            assertNotSame(first, second, "close() discards the reservation, so the second call starts a new initialization");

            responseGate.countDown();
            first.get(10, TimeUnit.SECONDS);
            LoaderStatus secondStatus = second.get(10, TimeUnit.SECONDS);

            assertAll("only the initialization claimed after close() takes effect",
                    () -> assertEquals(LoaderStatus.OK, secondStatus),
                    () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()),
                    () -> assertEquals(1, executor.getFixedRateSchedules(), "the stale initialization must not schedule a refresh"),
                    () -> assertTrue(loader.isBackgroundRefreshActive()));
        } finally {
            responseGate.countDown();
            moduleDispatcher.setResponseGate(null);
            executor.shutdownNow();
        }
    }

    /**
     * Scheduled executor that counts the fixed-rate tasks scheduled on it.
     */
    private static final class CountingScheduledExecutor extends ScheduledThreadPoolExecutor {

        private final AtomicInteger fixedRateSchedules = new AtomicInteger();

        CountingScheduledExecutor() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            fixedRateSchedules.incrementAndGet();
            return super.scheduleAtFixedRate(command, initialDelay, period, unit);
        }

        int getFixedRateSchedules() {
            return fixedRateSchedules.get();
        }
    }
}
