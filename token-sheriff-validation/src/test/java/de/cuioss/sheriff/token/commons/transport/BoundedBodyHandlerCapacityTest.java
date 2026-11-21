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
package de.cuioss.sheriff.token.commons.transport;

import de.cuioss.sheriff.token.commons.transport.BoundedBodyHandler.BoundedBody;
import de.cuioss.sheriff.token.commons.transport.BoundedBodyHandler.BoundedSubscriber;
import de.cuioss.sheriff.token.commons.transport.BoundedBodyHandler.OverLimitPolicy;
import de.cuioss.sheriff.token.commons.transport.BoundedBodyHandlerTestSupport.RecordingSubscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the memory bound of {@link BoundedBodyHandler} that COMMONS-17, the transport specification and
 * ADR-0031 state in one form: the array holding the bytes read so far never exceeds the ceiling and is at
 * most twice the bytes received, or the first allocation ({@code 1024} bytes, or the ceiling when that is
 * smaller) when that is larger. The capacity is read after every buffer through the subscriber's
 * package-private {@link BoundedSubscriber#capacity()}.
 */
@DisplayName("BoundedBodyHandler array capacity")
class BoundedBodyHandlerCapacityTest {

    private static final String ORIGIN = "TestConfig.ceiling; fixed";

    /** Capacity of the first allocation when the ceiling is not smaller. */
    private static final int INITIAL_CAPACITY = 1024;

    private static BoundedSubscriber subscribed(long maxBytes, OverLimitPolicy policy, AtomicBoolean cancelled) {
        BoundedSubscriber subscriber = new BoundedSubscriber(maxBytes, policy, ORIGIN);
        subscriber.onSubscribe(new RecordingSubscription(cancelled));
        return subscriber;
    }

    private static void feed(BoundedSubscriber subscriber, int size) {
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[size])));
    }

    /**
     * Asserts the bound on the capacity after {@code received} bytes were accepted under {@code maxBytes}.
     */
    private static void assertBound(BoundedSubscriber subscriber, long maxBytes, long received, int index) {
        long firstAllocation = Math.min(INITIAL_CAPACITY, maxBytes);
        long capacity = subscriber.capacity();
        String where = "after buffer " + index + " (" + received + " bytes received, ceiling " + maxBytes + ")";
        assertAll(where,
                () -> assertTrue(capacity <= maxBytes,
                        "The capacity " + capacity + " never exceeds the ceiling " + where),
                () -> assertTrue(capacity <= Math.max(2 * received, firstAllocation),
                        "The capacity " + capacity + " is at most twice the bytes received or the first allocation "
                                + where),
                () -> assertTrue(capacity >= received,
                        "The capacity " + capacity + " holds every byte received " + where));
    }

    private static int[] repeat(int size, int count) {
        int[] sizes = new int[count];
        Arrays.fill(sizes, size);
        return sizes;
    }

    /**
     * Buffer sizes whose running total lands on every doubling boundary and one byte past it, from the
     * first allocation up to {@code limit}.
     */
    private static int[] acrossDoublingSteps(int limit) {
        List<Integer> sizes = new ArrayList<>();
        int total = 0;
        for (int boundary = INITIAL_CAPACITY; boundary <= limit; boundary *= 2) {
            sizes.add(boundary - total);
            sizes.add(1);
            total = boundary + 1;
        }
        return sizes.stream().mapToInt(Integer::intValue).toArray();
    }

    static Stream<Arguments> bufferSequences() {
        return Stream.of(
                Arguments.of("one large buffer", 100_000L, new int[]{100_000}),
                Arguments.of("many one-byte buffers", 5_000L, repeat(1, 5_000)),
                Arguments.of("sizes crossing each doubling step", 70_000L, acrossDoublingSteps(65_536)),
                Arguments.of("a sequence ending exactly at the ceiling", 3_000L, new int[]{1_000, 1_000, 1_000}),
                Arguments.of("a sequence ending exactly at a ceiling between doubling steps", 1_500L,
                        new int[]{1_024, 1, 475}),
                Arguments.of("empty buffers between data", 4_096L, new int[]{0, 1, 0, 2_000, 0, 2_095}),
                Arguments.of("a ceiling smaller than the first allocation", 300L, repeat(10, 30)),
                Arguments.of("a ceiling smaller than the first allocation in one buffer", 700L, new int[]{700}));
    }

    @Nested
    @DisplayName("Bound while streaming")
    class BoundWhileStreaming {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.cuioss.sheriff.token.commons.transport.BoundedBodyHandlerCapacityTest#bufferSequences")
        @DisplayName("should keep the capacity within the bound after every buffer under FAIL")
        void shouldBoundCapacityUnderFail(String variant, long maxBytes, int[] sizes) {
            assertBoundHoldsAndBodyIsComplete(maxBytes, sizes, OverLimitPolicy.FAIL);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.cuioss.sheriff.token.commons.transport.BoundedBodyHandlerCapacityTest#bufferSequences")
        @DisplayName("should keep the capacity within the bound after every buffer under TRUNCATE")
        void shouldBoundCapacityUnderTruncate(String variant, long maxBytes, int[] sizes) {
            assertBoundHoldsAndBodyIsComplete(maxBytes, sizes, OverLimitPolicy.TRUNCATE);
        }

        private void assertBoundHoldsAndBodyIsComplete(long maxBytes, int[] sizes, OverLimitPolicy policy) {
            BoundedSubscriber subscriber = subscribed(maxBytes, policy, new AtomicBoolean(false));
            long received = 0;

            for (int i = 0; i < sizes.length; i++) {
                feed(subscriber, sizes[i]);
                received += sizes[i];
                assertBound(subscriber, maxBytes, received, i);
            }
            subscriber.onComplete();

            long expectedLength = received;
            BoundedBody body = subscriber.getBody().toCompletableFuture().join();
            assertAll("Completed body",
                    () -> assertEquals(expectedLength, body.bytes().length, "Every byte received is delivered"),
                    () -> assertFalse(body.truncated(), "A body within the ceiling is not truncated"),
                    () -> assertEquals(0, subscriber.capacity(), "The array is released at completion"));
        }

        @Test
        @DisplayName("should allocate nothing for an empty buffer")
        void shouldAllocateNothingForEmptyBuffer() {
            BoundedSubscriber subscriber = subscribed(4_096, OverLimitPolicy.FAIL, new AtomicBoolean(false));

            feed(subscriber, 0);

            assertEquals(0, subscriber.capacity(), "An empty buffer costs nothing");
        }
    }

    @Nested
    @DisplayName("Bound at the crossing")
    class BoundAtCrossing {

        private static final long CEILING = 2_000;

        @Test
        @DisplayName("should fail with the over-limit message under FAIL and release the array")
        void shouldFailAtCrossing() {
            AtomicBoolean cancelled = new AtomicBoolean(false);
            BoundedSubscriber subscriber = subscribed(CEILING, OverLimitPolicy.FAIL, cancelled);

            feed(subscriber, 1_500);
            assertBound(subscriber, CEILING, 1_500, 0);
            feed(subscriber, 600);

            CompletableFuture<BoundedBody> future = subscriber.getBody().toCompletableFuture();
            CompletionException thrown = assertThrows(CompletionException.class, future::join);
            assertAll("FAIL at the crossing",
                    () -> assertInstanceOf(IOException.class, thrown.getCause(), "The read fails with an IOException"),
                    () -> assertEquals("Response body exceeds maximum allowed size of 2000 bytes (" + ORIGIN + ")",
                            thrown.getCause().getMessage(), "The over-limit message is unchanged"),
                    () -> assertTrue(cancelled.get(), "The subscription is cancelled at the crossing"),
                    () -> assertEquals(0, subscriber.capacity(), "The buffered bytes are released"));
        }

        @Test
        @DisplayName("should complete with exactly the ceiling bytes under TRUNCATE, never above the ceiling")
        void shouldTruncateAtCrossing() {
            AtomicBoolean cancelled = new AtomicBoolean(false);
            BoundedSubscriber subscriber = subscribed(CEILING, OverLimitPolicy.TRUNCATE, cancelled);

            feed(subscriber, 1_500);
            assertBound(subscriber, CEILING, 1_500, 0);
            feed(subscriber, 600);

            BoundedBody body = subscriber.getBody().toCompletableFuture().join();
            assertAll("TRUNCATE at the crossing",
                    () -> assertEquals(CEILING, body.bytes().length, "Exactly the ceiling bytes are delivered"),
                    () -> assertTrue(body.truncated(), "The dropped bytes are signalled"),
                    () -> assertTrue(cancelled.get(), "The subscription is cancelled at the crossing"),
                    () -> assertEquals(0, subscriber.capacity(), "The array is released once the body is complete"));
        }

        @Test
        @DisplayName("should keep the capacity at the ceiling when TRUNCATE fills a ceiling smaller than the first allocation")
        void shouldTruncateUnderSmallCeiling() {
            AtomicBoolean cancelled = new AtomicBoolean(false);
            BoundedSubscriber subscriber = subscribed(300, OverLimitPolicy.TRUNCATE, cancelled);

            feed(subscriber, 200);
            assertBound(subscriber, 300, 200, 0);
            feed(subscriber, 200);

            BoundedBody body = subscriber.getBody().toCompletableFuture().join();
            assertAll("TRUNCATE under a small ceiling",
                    () -> assertEquals(300, body.bytes().length, "Exactly the ceiling bytes are delivered"),
                    () -> assertTrue(body.truncated(), "The dropped bytes are signalled"),
                    () -> assertTrue(cancelled.get(), "The subscription is cancelled at the crossing"));
        }
    }
}
