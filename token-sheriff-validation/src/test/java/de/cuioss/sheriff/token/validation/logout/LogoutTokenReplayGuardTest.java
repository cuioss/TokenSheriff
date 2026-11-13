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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link LogoutTokenReplayGuard}: admission, replay refusal, retention expiry judged on the instant each
 * admission names, the fail-closed capacity bound that never evicts a live entry, and the atomicity of admission
 * under concurrency.
 * <p>
 * The guard reads no clock; every admission passes the instant it is decided at. That the validator passes the
 * same instant it judged {@code iat} against, and reads its clock only once, is tested in
 * {@code LogoutTokenValidatorTest}.
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
            LogoutTokenReplayGuard guard = new LogoutTokenReplayGuard(10);

            assertEquals(Admission.ADMITTED, guard.admit("jti-1", START.plus(RETENTION), START));
            assertEquals(Admission.REPLAYED, guard.admit("jti-1", START.plus(RETENTION), START));
            assertEquals(Admission.ADMITTED, guard.admit("jti-2", START.plus(RETENTION), START), "a different jti");
        }

        @Test
        @DisplayName("A jti is retained up to and including its retention instant and readmitted afterwards")
        void shouldReadmitAfterExpiry() {
            LogoutTokenReplayGuard guard = new LogoutTokenReplayGuard(10);
            Instant retainUntil = START.plus(RETENTION);
            assertEquals(Admission.ADMITTED, guard.admit("jti-1", retainUntil, START));

            assertEquals(Admission.REPLAYED, guard.admit("jti-1", retainUntil, retainUntil),
                    "still retained at the instant");

            assertEquals(Admission.ADMITTED, guard.admit("jti-1", retainUntil.plus(RETENTION),
                    retainUntil.plusSeconds(1)), "readmitted once the retention has passed");
        }

        @Test
        @DisplayName("The purge is decided on the instant the admission names, not on the current time")
        void shouldDecidePurgeOnTheGivenInstant() {
            LogoutTokenReplayGuard guard = new LogoutTokenReplayGuard(10);
            Instant longPast = START.minus(Duration.ofDays(1));
            Instant retainUntil = longPast.plus(RETENTION);
            assertEquals(Admission.ADMITTED, guard.admit("jti-1", retainUntil, longPast));

            assertEquals(Admission.REPLAYED, guard.admit("jti-1", retainUntil, retainUntil),
                    "retained at its retention instant although that instant lies a day before now");
            assertEquals(Admission.ADMITTED, guard.admit("jti-1", retainUntil, retainUntil.plusNanos(1)),
                    "positive control: purged by an admission decided just past its retention");
        }
    }

    @Nested
    @DisplayName("Capacity")
    class Capacity {

        @Test
        @DisplayName("At capacity a new jti is refused as FULL and every live jti is still refused as REPLAYED")
        void shouldRefuseWhenFullWithoutEvicting() {
            LogoutTokenReplayGuard guard = new LogoutTokenReplayGuard(3);
            List<String> live = List.of("jti-1", "jti-2", "jti-3");
            live.forEach(jti -> assertEquals(Admission.ADMITTED, guard.admit(jti, START.plus(RETENTION), START)));

            assertEquals(Admission.FULL, guard.admit("jti-4", START.plus(RETENTION), START));

            live.forEach(jti -> assertEquals(Admission.REPLAYED, guard.admit(jti, START.plus(RETENTION), START),
                    () -> jti + " must not have been evicted"));
            assertEquals(Admission.FULL, guard.admit("jti-4", START.plus(RETENTION), START), "still full");
        }

        @Test
        @DisplayName("Capacity is regained once retained entries expire")
        void shouldRegainCapacityAfterExpiry() {
            LogoutTokenReplayGuard guard = new LogoutTokenReplayGuard(2);
            assertEquals(Admission.ADMITTED, guard.admit("jti-1", START.plusSeconds(10), START));
            assertEquals(Admission.ADMITTED, guard.admit("jti-2", START.plus(RETENTION), START));
            assertEquals(Admission.FULL, guard.admit("jti-3", START.plus(RETENTION), START));

            Instant later = START.plusSeconds(11);

            assertEquals(Admission.ADMITTED, guard.admit("jti-3", START.plus(RETENTION), later), "jti-1 expired");
            assertEquals(Admission.REPLAYED, guard.admit("jti-2", START.plus(RETENTION), later), "jti-2 still live");
            assertEquals(Admission.FULL, guard.admit("jti-4", START.plus(RETENTION), later));
        }
    }

    @Nested
    @DisplayName("Concurrency")
    class Concurrency {

        @Test
        @DisplayName("Many threads admitting the same jti yield exactly one admission")
        void shouldAdmitSameJtiExactlyOnce() throws Exception {
            LogoutTokenReplayGuard guard = new LogoutTokenReplayGuard(100);
            List<Callable<Admission>> calls = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                calls.add(() -> guard.admit("shared-jti", START.plus(RETENTION), START));
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
            LogoutTokenReplayGuard guard = new LogoutTokenReplayGuard(capacity);
            for (int i = 0; i < prefilled; i++) {
                assertEquals(Admission.ADMITTED, guard.admit("prefilled-" + i, START.plus(RETENTION), START));
            }
            List<Callable<Admission>> calls = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                String jti = "concurrent-" + i;
                calls.add(() -> guard.admit(jti, START.plus(RETENTION), START));
            }

            Map<Admission, Integer> outcomes = runConcurrently(calls);

            assertEquals(capacity - prefilled, outcomes.get(Admission.ADMITTED));
            assertEquals(THREADS - (capacity - prefilled), outcomes.get(Admission.FULL));
            assertEquals(0, outcomes.get(Admission.REPLAYED));
            for (int i = 0; i < prefilled; i++) {
                assertEquals(Admission.REPLAYED, guard.admit("prefilled-" + i, START.plus(RETENTION), START),
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
            assertThrows(IllegalArgumentException.class, () -> new LogoutTokenReplayGuard(0));
            assertThrows(IllegalArgumentException.class, () -> new LogoutTokenReplayGuard(-1));
            assertNotNull(new LogoutTokenReplayGuard(1), "positive control: capacity 1");
        }

        @Test
        @DisplayName("A null jti, retention instant or decision instant is rejected")
        void shouldRejectNullArguments() {
            LogoutTokenReplayGuard guard = new LogoutTokenReplayGuard(1);
            Instant retainUntil = START.plus(RETENTION);

            assertThrows(NullPointerException.class, () -> guard.admit(null, retainUntil, START));
            assertThrows(NullPointerException.class, () -> guard.admit("jti-1", null, START));
            assertThrows(NullPointerException.class, () -> guard.admit("jti-1", retainUntil, null));
            assertEquals(Admission.ADMITTED, guard.admit("jti-1", retainUntil, START),
                    "nothing was stored by a rejection");
        }
    }
}
