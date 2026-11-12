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
package de.cuioss.sheriff.token.validation.logout;

import de.cuioss.sheriff.token.validation.logout.LogoutTokenReplayGuard.Admission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link LogoutTokenReplayGuard}: admission, replay refusal, retention expiry, the fail-closed capacity
 * bound that never evicts a live entry, and the atomicity of admission under concurrency.
 */
@DisplayName("LogoutTokenReplayGuard")
class LogoutTokenReplayGuardTest {

    private static final Instant START = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Duration RETENTION = Duration.ofMinutes(5);
    private static final int THREADS = 32;

    @Nested
    @DisplayName("Admission and replay")
    class AdmissionAndReplay {

        @Test
        @DisplayName("A new jti is admitted and the same jti is then refused as REPLAYED")
        void shouldAdmitThenRefuseReplay() {
            LogoutTokenReplayGuard guard = new LogoutTokenReplayGuard(10, Clock.fixed(START, ZoneOffset.UTC));

            assertEquals(Admission.ADMITTED, guard.admit("jti-1", START.plus(RETENTION)));
            assertEquals(Admission.REPLAYED, guard.admit("jti-1", START.plus(RETENTION)));
            assertEquals(Admission.ADMITTED, guard.admit("jti-2", START.plus(RETENTION)), "a different jti");
        }

        @Test
        @DisplayName("A jti is retained up to and including its retention instant and readmitted afterwards")
        void shouldReadmitAfterExpiry() {
            MutableClock clock = new MutableClock(START);
            LogoutTokenReplayGuard guard = new LogoutTokenReplayGuard(10, clock);
            Instant retainUntil = START.plus(RETENTION);
            assertEquals(Admission.ADMITTED, guard.admit("jti-1", retainUntil));

            clock.set(retainUntil);
            assertEquals(Admission.REPLAYED, guard.admit("jti-1", retainUntil), "still retained at the instant");

            clock.set(retainUntil.plusSeconds(1));
            assertEquals(Admission.ADMITTED, guard.admit("jti-1", retainUntil.plus(RETENTION)),
                    "readmitted once the retention has passed");
        }
    }

    @Nested
    @DisplayName("Capacity")
    class Capacity {

        @Test
        @DisplayName("At capacity a new jti is refused as FULL and every live jti is still refused as REPLAYED")
        void shouldRefuseWhenFullWithoutEvicting() {
            LogoutTokenReplayGuard guard = new LogoutTokenReplayGuard(3, Clock.fixed(START, ZoneOffset.UTC));
            List<String> live = List.of("jti-1", "jti-2", "jti-3");
            live.forEach(jti -> assertEquals(Admission.ADMITTED, guard.admit(jti, START.plus(RETENTION))));

            assertEquals(Admission.FULL, guard.admit("jti-4", START.plus(RETENTION)));

            live.forEach(jti -> assertEquals(Admission.REPLAYED, guard.admit(jti, START.plus(RETENTION)),
                    () -> jti + " must not have been evicted"));
            assertEquals(Admission.FULL, guard.admit("jti-4", START.plus(RETENTION)), "still full");
        }

        @Test
        @DisplayName("Capacity is regained once retained entries expire")
        void shouldRegainCapacityAfterExpiry() {
            MutableClock clock = new MutableClock(START);
            LogoutTokenReplayGuard guard = new LogoutTokenReplayGuard(2, clock);
            assertEquals(Admission.ADMITTED, guard.admit("jti-1", START.plusSeconds(10)));
            assertEquals(Admission.ADMITTED, guard.admit("jti-2", START.plus(RETENTION)));
            assertEquals(Admission.FULL, guard.admit("jti-3", START.plus(RETENTION)));

            clock.set(START.plusSeconds(11));

            assertEquals(Admission.ADMITTED, guard.admit("jti-3", START.plus(RETENTION)), "jti-1 expired");
            assertEquals(Admission.REPLAYED, guard.admit("jti-2", START.plus(RETENTION)), "jti-2 still live");
            assertEquals(Admission.FULL, guard.admit("jti-4", START.plus(RETENTION)));
        }
    }

    @Nested
    @DisplayName("Concurrency")
    class Concurrency {

        @Test
        @DisplayName("Many threads admitting the same jti yield exactly one admission")
        void shouldAdmitSameJtiExactlyOnce() throws Exception {
            LogoutTokenReplayGuard guard = new LogoutTokenReplayGuard(100, Clock.fixed(START, ZoneOffset.UTC));
            List<Callable<Admission>> calls = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                calls.add(() -> guard.admit("shared-jti", START.plus(RETENTION)));
            }

            Map<Admission, Integer> outcomes = runConcurrently(calls);

            assertEquals(1, outcomes.get(Admission.ADMITTED));
            assertEquals(THREADS - 1, outcomes.get(Admission.REPLAYED));
            assertEquals(0, outcomes.get(Admission.FULL));
        }

        @Test
        @DisplayName("Many threads filling the last free entries never exceed the capacity")
        void shouldNeverExceedCapacity() throws Exception {
            int capacity = 10;
            int prefilled = 7;
            LogoutTokenReplayGuard guard = new LogoutTokenReplayGuard(capacity, Clock.fixed(START, ZoneOffset.UTC));
            for (int i = 0; i < prefilled; i++) {
                assertEquals(Admission.ADMITTED, guard.admit("prefilled-" + i, START.plus(RETENTION)));
            }
            List<Callable<Admission>> calls = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                String jti = "concurrent-" + i;
                calls.add(() -> guard.admit(jti, START.plus(RETENTION)));
            }

            Map<Admission, Integer> outcomes = runConcurrently(calls);

            assertEquals(capacity - prefilled, outcomes.get(Admission.ADMITTED));
            assertEquals(THREADS - (capacity - prefilled), outcomes.get(Admission.FULL));
            assertEquals(0, outcomes.get(Admission.REPLAYED));
            for (int i = 0; i < prefilled; i++) {
                assertEquals(Admission.REPLAYED, guard.admit("prefilled-" + i, START.plus(RETENTION)),
                        "no prefilled entry was evicted");
            }
        }

        private Map<Admission, Integer> runConcurrently(List<Callable<Admission>> calls) throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(calls.size());
            CountDownLatch ready = new CountDownLatch(calls.size());
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Future<Admission>> futures = new ArrayList<>();
                for (Callable<Admission> call : calls) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(10, TimeUnit.SECONDS), "start signal");
                        return call.call();
                    }));
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS), "all threads ready");
                start.countDown();
                Map<Admission, Integer> outcomes = new EnumMap<>(Admission.class);
                for (Admission admission : Admission.values()) {
                    outcomes.put(admission, 0);
                }
                for (Future<Admission> future : futures) {
                    outcomes.merge(future.get(10, TimeUnit.SECONDS), 1, Integer::sum);
                }
                return outcomes;
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("Construction and arguments")
    class Construction {

        @Test
        @DisplayName("A capacity of zero or less is rejected")
        void shouldRejectNonPositiveCapacity() {
            Clock clock = Clock.fixed(START, ZoneOffset.UTC);

            assertThrows(IllegalArgumentException.class, () -> new LogoutTokenReplayGuard(0, clock));
            assertThrows(IllegalArgumentException.class, () -> new LogoutTokenReplayGuard(-1, clock));
            assertNotNull(new LogoutTokenReplayGuard(1, clock), "positive control: capacity 1");
        }

        @Test
        @DisplayName("A null clock is rejected")
        void shouldRejectNullClock() {
            assertThrows(NullPointerException.class, () -> new LogoutTokenReplayGuard(1, null));
        }

        @Test
        @DisplayName("A null jti or retention instant is rejected")
        void shouldRejectNullArguments() {
            LogoutTokenReplayGuard guard = new LogoutTokenReplayGuard(1, Clock.fixed(START, ZoneOffset.UTC));
            Instant retainUntil = START.plus(RETENTION);

            assertThrows(NullPointerException.class, () -> guard.admit(null, retainUntil));
            assertThrows(NullPointerException.class, () -> guard.admit("jti-1", null));
            assertEquals(Admission.ADMITTED, guard.admit("jti-1", retainUntil), "nothing was stored by a rejection");
        }
    }

    /**
     * A clock whose instant the test sets.
     */
    private static final class MutableClock extends Clock {

        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant newInstant) {
            this.instant = newInstant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
