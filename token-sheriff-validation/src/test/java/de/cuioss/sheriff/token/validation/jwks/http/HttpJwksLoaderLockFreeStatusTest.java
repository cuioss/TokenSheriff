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
import de.cuioss.sheriff.token.validation.test.InMemoryJWKSFactory;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@DisplayName("HttpJwksLoader Lock-Free Status Check Tests")
@EnableMockWebServer
class HttpJwksLoaderLockFreeStatusTest {

    /** Status reads an observer thread still performs once the initialisation has completed. */
    private static final int READS_AFTER_COMPLETION = 20;

    /**
     * Backstop for the observer loops. They normally end on the completion signal; the deadline only
     * ends a loop whose signal never arrives. It lies well above every bounded wait of the tests, so
     * it never ends the loop of a healthy run early.
     */
    private static final Duration OBSERVER_DEADLINE = Duration.ofSeconds(60);

    /** Upper bound for one initialisation; an initialisation that never completes fails the test. */
    private static final int INIT_TIMEOUT_SECONDS = 30;

    @Getter
    private final JwksResolveDispatcher moduleDispatcher = new JwksResolveDispatcher();

    @BeforeEach
    void setUp() {
        moduleDispatcher.setCallCounter(0);
        moduleDispatcher.returnDefault();
    }

    @Test
    @DisplayName("getLoaderStatus should be lock-free under high contention")
    void getLoaderStatusShouldBeLockFreeUnderHighContention(URIBuilder uriBuilder) throws Exception {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                .jwksUrl(jwksEndpoint)
                .issuerIdentifier("lock-free-test-issuer")
                .build();

        try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
            SecurityEventCounter counter = new SecurityEventCounter();

            // Start async initialization to get loader into active state
            CompletableFuture<LoaderStatus> initFuture = loader.initJWKSLoader(counter);

            int threadCount = 100;
            try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
                CyclicBarrier barrier = new CyclicBarrier(threadCount);
                CountDownLatch endLatch = new CountDownLatch(threadCount);
                AtomicInteger successCount = new AtomicInteger(0);
                AtomicReference<BrokenBarrierException> barrierException = new AtomicReference<>();
                AtomicReference<InterruptedException> interruptedException = new AtomicReference<>();

                // Launch 100 threads that will hammer getLoaderStatus() simultaneously
                for (int i = 0; i < threadCount; i++) {
                    executor.submit(() -> {
                        try {
                            // Synchronize all threads to start at exactly the same time
                            barrier.await();

                            // Each thread calls getLoaderStatus() multiple times rapidly
                            for (int j = 0; j < 50; j++) {
                                LoaderStatus status = loader.getLoaderStatus();
                                assertNotNull(status, "Status should never be null");
                                // Status should be one of the valid enum values
                                assertTrue(
                                        status == LoaderStatus.UNDEFINED ||
                                                status == LoaderStatus.LOADING ||
                                                status == LoaderStatus.OK ||
                                                status == LoaderStatus.ERROR,
                                        "Status should be a valid LoaderStatus value: " + status
                                );
                            }
                            successCount.incrementAndGet();

                        } catch (BrokenBarrierException e) {
                            barrierException.compareAndSet(null, e);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            interruptedException.compareAndSet(null, e);
                        } finally {
                            endLatch.countDown();
                        }
                    });
                }

                // Wait for all threads to complete
                boolean completed = endLatch.await(10, TimeUnit.SECONDS);
                executor.shutdown();

                assertTrue(completed, "All threads should complete within timeout");
                assertNull(barrierException.get(), "No barrier exceptions should occur during concurrent access");
                assertNull(interruptedException.get(), "No interruption exceptions should occur during concurrent access");
                assertEquals(threadCount, successCount.get(), "All threads should successfully read status");

                // Ensure initialization completes properly
                LoaderStatus finalStatus = initFuture.get(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                assertTrue(finalStatus == LoaderStatus.OK || finalStatus == LoaderStatus.ERROR,
                        "Initialization should complete with OK or ERROR status");
            }
        }
    }

    @Test
    @DisplayName("Status transitions should be atomic and consistent")
    void statusTransitionsShouldBeAtomicAndConsistent(URIBuilder uriBuilder) throws Exception {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                .jwksUrl(jwksEndpoint)
                .issuerIdentifier("atomic-transition-test-issuer")
                .build();

        try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
            SecurityEventCounter counter = new SecurityEventCounter();

            // Initial status should be UNDEFINED
            assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus(),
                    "Initial status should be UNDEFINED");

            // The JWKS response stays behind this gate until an observer has recorded LOADING. Without
            // it the initialisation can complete between two reads of every observer, and the transient
            // state would go unobserved.
            CountDownLatch responseGate = new CountDownLatch(1);
            moduleDispatcher.setResponseGate(responseGate);

            int observerThreadCount = 50;
            try (ExecutorService observerExecutor = Executors.newFixedThreadPool(observerThreadCount)) {
                long observerDeadline = System.nanoTime() + OBSERVER_DEADLINE.toNanos();
                CountDownLatch observerLatch = new CountDownLatch(observerThreadCount);
                CountDownLatch observersStarted = new CountDownLatch(observerThreadCount);
                CompletableFuture<LoaderStatus> initCompleted = new CompletableFuture<>();
                AtomicInteger undefinedObservations = new AtomicInteger(0);
                AtomicInteger loadingObservations = new AtomicInteger(0);
                AtomicInteger okObservations = new AtomicInteger(0);
                AtomicInteger errorObservations = new AtomicInteger(0);
                AtomicInteger invalidTransitions = new AtomicInteger(0);

                // Start multiple threads observing status transitions
                for (int i = 0; i < observerThreadCount; i++) {
                    observerExecutor.submit(() -> {
                        try {
                            LoaderStatus previousStatus = null;

                            // Observe status changes until the initialisation has completed, then
                            // perform a fixed number of further reads
                            int readsAfterCompletion = 0;
                            while (readsAfterCompletion < READS_AFTER_COMPLETION
                                    && System.nanoTime() < observerDeadline) {
                                if (initCompleted.isDone()) {
                                    readsAfterCompletion++;
                                }
                                LoaderStatus currentStatus = loader.getLoaderStatus();
                                observersStarted.countDown();

                                // Count observations of each status
                                switch (currentStatus) {
                                    case UNDEFINED -> undefinedObservations.incrementAndGet();
                                    case LOADING -> loadingObservations.incrementAndGet();
                                    case OK -> okObservations.incrementAndGet();
                                    case ERROR -> errorObservations.incrementAndGet();
                                }

                                // Check for invalid transitions
                                // Valid transitions: UNDEFINED -> LOADING, LOADING -> OK/ERROR
                                // Invalid: OK -> anything, ERROR -> anything, LOADING -> UNDEFINED
                                if ((previousStatus == LoaderStatus.OK && currentStatus != LoaderStatus.OK) ||
                                        (previousStatus == LoaderStatus.ERROR && currentStatus != LoaderStatus.ERROR) ||
                                        (previousStatus == LoaderStatus.LOADING && currentStatus == LoaderStatus.UNDEFINED)) {
                                    invalidTransitions.incrementAndGet();
                                }

                                previousStatus = currentStatus;

                                // Brief pause to allow other threads to observe
                                Awaitility.await().pollDelay(Duration.ofMillis(1)).until(() -> true);
                            }
                        } finally {
                            observerLatch.countDown();
                        }
                    });
                }

                try {
                    // Start async initialization once every observer has read the status at least once
                    assertTrue(observersStarted.await(10, TimeUnit.SECONDS), "All observer threads should start");
                    CompletableFuture<LoaderStatus> initFuture = loader.initJWKSLoader(counter);

                    // The loader is LOADING while the response is held back; release it once seen
                    Awaitility.await("an observer records LOADING").atMost(Duration.ofSeconds(10))
                            .until(() -> loadingObservations.get() > 0);
                    responseGate.countDown();

                    // Wait for initialization to complete and let the observers finish their further reads
                    LoaderStatus finalStatus = initFuture.get(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    initCompleted.complete(finalStatus);

                    // Wait for all observer threads to complete
                    boolean observersCompleted = observerLatch.await(10, TimeUnit.SECONDS);

                    assertTrue(observersCompleted, "All observer threads should complete");
                    assertEquals(0, invalidTransitions.get(),
                            "No invalid status transitions should be observed");

                    // Verify expected transition pattern
                    assertTrue(undefinedObservations.get() > 0,
                            "Should observe UNDEFINED status");
                    assertTrue(loadingObservations.get() > 0,
                            "Should observe LOADING status during initialization");
                    assertEquals(LoaderStatus.OK, finalStatus,
                            "Initialization against the default JWKS should complete with OK");
                    assertTrue(okObservations.get() > 0,
                            "Should observe the final status OK");
                    assertEquals(0, errorObservations.get(),
                            "Should not observe ERROR during a successful initialization");
                } finally {
                    // Release everything a failed or never-completing start-up would leave waiting, so
                    // that closing the executor cannot block and the real failure is reported
                    responseGate.countDown();
                    initCompleted.cancel(false);
                    observerExecutor.shutdownNow();
                    moduleDispatcher.setResponseGate(null);
                }
            }
        }
    }

    @Test
    @DisplayName("Concurrent status checks during multiple initializations should be safe")
    void concurrentStatusChecksDuringMultipleInitializationsShouldBeSafe(URIBuilder uriBuilder) throws Exception {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                .jwksUrl(jwksEndpoint)
                .issuerIdentifier("multi-init-test-issuer")
                .build();

        try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
            SecurityEventCounter counter = new SecurityEventCounter();

            int initThreadCount = 10;
            int statusCheckThreadCount = 50;
            try (ExecutorService executor = Executors.newFixedThreadPool(initThreadCount + statusCheckThreadCount)) {
                long checkerDeadline = System.nanoTime() + OBSERVER_DEADLINE.toNanos();
                CountDownLatch startLatch = new CountDownLatch(1);
                CountDownLatch endLatch = new CountDownLatch(initThreadCount + statusCheckThreadCount);
                CountDownLatch initDone = new CountDownLatch(initThreadCount);
                AtomicInteger statusCheckSuccesses = new AtomicInteger(0);
                AtomicInteger initSuccesses = new AtomicInteger(0);
                AtomicInteger initOkResults = new AtomicInteger(0);
                AtomicReference<InterruptedException> initInterruptedException = new AtomicReference<>();
                AtomicReference<Exception> initFailure = new AtomicReference<>();

                // Launch multiple threads calling initJWKSLoader concurrently
                for (int i = 0; i < initThreadCount; i++) {
                    executor.submit(() -> {
                        try {
                            startLatch.await();

                            CompletableFuture<LoaderStatus> future = loader.initJWKSLoader(counter);
                            LoaderStatus result = future.get(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                            assertTrue(result == LoaderStatus.OK || result == LoaderStatus.ERROR,
                                    "Init should complete with OK or ERROR");
                            initSuccesses.incrementAndGet();
                            if (result == LoaderStatus.OK) {
                                initOkResults.incrementAndGet();
                            }

                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            initInterruptedException.compareAndSet(null, e);
                        } catch (ExecutionException | TimeoutException e) {
                            // Counted neither as success nor as OK result; the assertions below report it
                            initFailure.compareAndSet(null, e);
                        } finally {
                            initDone.countDown();
                            endLatch.countDown();
                        }
                    });
                }

                // Launch many threads continuously checking status
                for (int i = 0; i < statusCheckThreadCount; i++) {
                    executor.submit(() -> {
                        try {
                            startLatch.await();

                            // Check status repeatedly until every initialisation has completed, then
                            // perform a fixed number of further reads
                            int readsAfterCompletion = 0;
                            while (readsAfterCompletion < READS_AFTER_COMPLETION
                                    && System.nanoTime() < checkerDeadline) {
                                if (initDone.getCount() == 0) {
                                    readsAfterCompletion++;
                                }
                                LoaderStatus status = loader.getLoaderStatus();
                                assertNotNull(status, "Status should never be null");

                                // Use Awaitility instead of Thread.sleep for better testing
                                Awaitility.await().pollDelay(Duration.ofMillis(1)).until(() -> true);
                            }
                            // A loop that the deadline ended did not see the initialisations complete
                            if (readsAfterCompletion == READS_AFTER_COMPLETION) {
                                statusCheckSuccesses.incrementAndGet();
                            }

                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            endLatch.countDown();
                        }
                    });
                }

                try {
                    // Start all threads simultaneously
                    startLatch.countDown();

                    // Wait for completion
                    boolean completed = endLatch.await(15, TimeUnit.SECONDS);

                    assertTrue(completed, "All threads should complete");
                    assertNull(initInterruptedException.get(), "No interruption exceptions should occur");
                    assertNull(initFailure.get(), "No initialization should fail or time out");
                    assertTrue(initSuccesses.get() > 0, "Some initializations should succeed");
                    assertEquals(statusCheckThreadCount, statusCheckSuccesses.get(),
                            "All status check threads should complete successfully");
                    assertEquals(initThreadCount, initOkResults.get(),
                            "Every concurrent initialization should complete with OK");
                    assertEquals(LoaderStatus.OK, loader.getLoaderStatus(),
                            "Loader status should be OK after concurrent initialization");
                    assertTrue(loader.getKeyInfo(InMemoryJWKSFactory.DEFAULT_KEY_ID).isPresent(),
                            "Keys should be available after initialization");
                } finally {
                    // Interrupts an initialisation that never completes and with it releases the status
                    // checkers, so that closing the executor cannot block and the real failure is reported
                    executor.shutdownNow();
                }
            }
        }
    }
}
