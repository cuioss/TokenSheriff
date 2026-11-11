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
package de.cuioss.sheriff.token.validation.dpop;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class DpopReplayProtectionTest {

    private DpopReplayProtection replayProtection;

    @BeforeEach
    void setUp() {
        replayProtection = new DpopReplayProtection(300, 10_000);
    }

    @AfterEach
    void tearDown() {
        replayProtection.close();
    }

    @Test
    void shouldAcceptNewJti() {
        assertTrue(replayProtection.checkAndStore("unique-jti-1"));
    }

    @Test
    void shouldRejectReplayedJti() {
        String jti = "replayed-jti";
        assertTrue(replayProtection.checkAndStore(jti));
        assertFalse(replayProtection.checkAndStore(jti));
    }

    @Test
    void shouldAcceptDifferentJtiValues() {
        assertTrue(replayProtection.checkAndStore("jti-1"));
        assertTrue(replayProtection.checkAndStore("jti-2"));
        assertTrue(replayProtection.checkAndStore("jti-3"));
    }

    @Test
    void shouldAcceptJtiAfterTtlExpires() {
        // Use a very short TTL for testing
        try (var shortTtl = new DpopReplayProtection(1, 10_000)) {
            String jti = "short-lived-jti";
            assertTrue(shortTtl.checkAndStore(jti));
            assertFalse(shortTtl.checkAndStore(jti));

            // Wait for TTL to expire, then verify the same jti is accepted again
            Awaitility.await().atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> assertTrue(shortTtl.checkAndStore(jti)));
        }
    }

    @Test
    void shouldEvictOldestWhenMaxSizeExceeded() {
        try (var smallCache = new DpopReplayProtection(300, 5)) {
            // Fill cache
            for (int i = 0; i < 5; i++) {
                assertTrue(smallCache.checkAndStore("jti-" + i));
            }

            // This should trigger eviction of oldest
            assertTrue(smallCache.checkAndStore("jti-new"));

            // The oldest entries should have been evicted
            // New entry should still be detected as replay
            assertFalse(smallCache.checkAndStore("jti-new"));
        }
    }

    @Test
    void shouldBeThreadSafe() throws Exception {
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger acceptCount = new AtomicInteger(0);

        // Each thread tries to store unique jti values
        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < operationsPerThread; i++) {
                        String jti = "thread-" + threadId + "-jti-" + i;
                        if (replayProtection.checkAndStore(jti)) {
                            acceptCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // All unique jti values should have been accepted
        assertEquals(threadCount * operationsPerThread, acceptCount.get());
    }

    @Test
    void shouldDetectReplayAcrossThreads() throws Exception {
        String sharedJti = UUID.randomUUID().toString();
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger acceptCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (replayProtection.checkAndStore(sharedJti)) {
                        acceptCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // Exactly one thread should have succeeded
        assertEquals(1, acceptCount.get());
    }

    @Test
    void shouldNeverForgetInWindowJtisPastSoftCap() {
        try (var smallCache = new DpopReplayProtection(300, 5)) {
            List<String> jtis = IntStream.range(0, 50).mapToObj(i -> "in-window-" + i).toList();
            jtis.forEach(jti -> assertTrue(smallCache.checkAndStore(jti), "first use must be accepted: " + jti));

            // Every jti is still inside its window, so none may be forgotten even though the
            // cache is ten times over its soft cap.
            jtis.forEach(jti -> assertFalse(smallCache.checkAndStore(jti), "in-window replay must be refused: " + jti));
        }
    }

    @Test
    void shouldReclaimOverCapEntriesOnceWindowCloses() {
        try (var shortTtl = new DpopReplayProtection(1, 5)) {
            for (int i = 0; i < 20; i++) {
                assertTrue(shortTtl.checkAndStore("expiring-" + i));
            }

            // Once the window has closed, an over-cap insert reclaims the expired entries
            // oldest-first: 21 entries against a cap of 5 means 16 are evicted.
            Awaitility.await().pollDelay(Duration.ofMillis(1100)).atMost(Duration.ofSeconds(3))
                    .untilAsserted(() -> assertTrue(shortTtl.checkAndStore("after-window-" + UUID.randomUUID())));
            assertEquals(16, shortTtl.lastOverCapExamined(),
                    "the over-cap insert must evict exactly the overflow of expired entries");
        }
    }

    @Test
    void shouldExamineBoundedEntriesWhenNothingIsEvictable() {
        try (var smallCache = new DpopReplayProtection(300, 5)) {
            for (int i = 0; i < 1_000; i++) {
                assertTrue(smallCache.checkAndStore("flood-" + i));
            }

            // All 1,000 entries are in-window: the over-cap insert must stop at the first
            // in-window head instead of scanning the whole cache.
            assertTrue(smallCache.checkAndStore("one-more"));
            assertEquals(1, smallCache.lastOverCapExamined(),
                    "an over-cap insert with nothing evictable must examine a constant number of entries");
        }
    }

    @Test
    void shouldNotForgetInWindowJtiUnderConcurrentInsertsPastSoftCap() throws Exception {
        int threadCount = 8;
        int operationsPerThread = 200;
        try (var smallCache = new DpopReplayProtection(300, 10)) {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger acceptCount = new AtomicInteger(0);
            for (int t = 0; t < threadCount; t++) {
                int threadId = t;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < operationsPerThread; i++) {
                            if (smallCache.checkAndStore("concurrent-" + threadId + "-" + i)) {
                                acceptCount.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(threadCount * operationsPerThread, acceptCount.get());
            for (int t = 0; t < threadCount; t++) {
                for (int i = 0; i < operationsPerThread; i++) {
                    String jti = "concurrent-" + t + "-" + i;
                    assertFalse(smallCache.checkAndStore(jti), "in-window jti forgotten: " + jti);
                }
            }
        }
    }

    @Test
    void shouldCleanUpOnClose() {
        DpopReplayProtection protection = new DpopReplayProtection(300, 10_000);
        protection.checkAndStore("some-jti");
        protection.close();
        // After close, the scheduler is shut down - this should not throw
        assertDoesNotThrow(protection::close);
    }
}
