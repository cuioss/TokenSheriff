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
import de.cuioss.sheriff.token.commons.transport.BoundedBodyHandler.OverLimitPolicy;
import de.cuioss.sheriff.token.commons.transport.BoundedBodyHandlerTestSupport.ManualDrive;
import de.cuioss.sheriff.token.commons.transport.BoundedBodyHandlerTestSupport.RecordingSubscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the fail policy of {@link BoundedBodyHandler}: one named case per row of the pinned-difference
 * table that replaced the validation {@code BoundedBodyHandlers} and the client
 * {@code BoundedContentBodyHandler} (ADR-0031), then the fail-policy edges. The truncate policy lives in
 * {@link BoundedBodyHandlerTruncateTest}.
 */
@DisplayName("BoundedBodyHandler fail policy")
class BoundedBodyHandlerTest {

    private static final String ORIGIN = "TestConfig.ceiling; fixed";

    private static byte[] bytes(int length) {
        byte[] body = new byte[length];
        Arrays.fill(body, (byte) 'a');
        return body;
    }

    private static HttpResponse.BodyHandler<String> utf8(long maxBytes) {
        return BoundedBodyHandler.ofString(StandardCharsets.UTF_8, maxBytes, ORIGIN);
    }

    private static void assertOverLimit(Throwable failure, long maxBytes) {
        IOException exception = assertInstanceOf(IOException.class, failure, "An over-limit body fails with IOException");
        assertEquals("Response body exceeds maximum allowed size of " + maxBytes + " bytes (" + ORIGIN + ")",
                exception.getMessage(), "The failure message is the validation form, byte for byte");
    }

    @Nested
    @DisplayName("Pinned differences")
    class PinnedDifferences {

        @ParameterizedTest(name = "ceiling {0}")
        @ValueSource(longs = {0, 1, Integer.MAX_VALUE - 8L})
        @DisplayName("row 1: should accept a long ceiling from 0 to Integer.MAX_VALUE - 8")
        void shouldAcceptCeilingInRange(long maxBytes) {
            assertAll("Ceiling accepted",
                    () -> assertNotNull(utf8(maxBytes), "ofString must accept the ceiling"),
                    () -> assertNotNull(BoundedBodyHandler.ofBytes(maxBytes, OverLimitPolicy.FAIL, ORIGIN),
                            "ofBytes must accept the ceiling"));
        }

        @Test
        @DisplayName("row 1: should publish the largest accepted ceiling as Integer.MAX_VALUE - 8")
        void shouldPublishMaxCeiling() {
            assertEquals(2_147_483_639L, BoundedBodyHandler.MAX_CEILING, "The largest array the result can hold");
        }

        @ParameterizedTest(name = "ceiling {0}")
        @ValueSource(longs = {-1, Long.MIN_VALUE, Integer.MAX_VALUE - 7L, Long.MAX_VALUE})
        @DisplayName("row 2: should refuse a negative or over-array ceiling")
        void shouldRefuseCeilingOutOfRange(long maxBytes) {
            IllegalArgumentException ofString = assertThrows(IllegalArgumentException.class, () -> utf8(maxBytes));
            IllegalArgumentException ofBytes = assertThrows(IllegalArgumentException.class,
                    () -> BoundedBodyHandler.ofBytes(maxBytes, OverLimitPolicy.FAIL, ORIGIN));

            String expected = "maxBytes must be between 0 and 2147483639 inclusive, but was " + maxBytes;
            assertAll("Refusal message",
                    () -> assertEquals(expected, ofString.getMessage(), "ofString names the range"),
                    () -> assertEquals(expected, ofBytes.getMessage(), "ofBytes names the range"));
        }

        @Test
        @DisplayName("row 3: should refuse an over-advertised body before a byte is read")
        void shouldRefuseOverAdvertisedBodyBeforeReading() {
            ManualDrive<String> drive = BoundedBodyHandlerTestSupport.subscribe(utf8(10), 11L);

            assertAll("Pre-check refusal",
                    () -> assertTrue(drive.cancelled(), "The subscription is cancelled in onSubscribe"),
                    () -> assertFalse(drive.requested(), "No byte is ever requested"),
                    () -> assertOverLimit(drive.failure(), 10));
        }

        @Test
        @DisplayName("row 3: should read a body advertising exactly the ceiling")
        void shouldReadBodyAdvertisingExactlyTheCeiling() {
            var result = BoundedBodyHandlerTestSupport.drive(utf8(10), bytes(10), 10L);

            assertAll("At-ceiling advertised body",
                    () -> assertTrue(result.succeeded(), "An advertised length equal to the ceiling is read"),
                    () -> assertEquals("a".repeat(10), result.body(), "The body is returned unchanged"),
                    () -> assertFalse(result.cancelled(), "The read completes without cancellation"));
        }

        @Test
        @DisplayName("row 4: should ignore every chunk after the crossing and read none of it")
        void shouldIgnoreChunksAfterCrossing() {
            ManualDrive<String> drive = BoundedBodyHandlerTestSupport.subscribe(utf8(10), null);
            ByteBuffer first = drive.feed(bytes(6));
            ByteBuffer crossing = drive.feed(bytes(6));
            ByteBuffer later = drive.feed(bytes(4));
            drive.complete();

            assertAll("After the crossing",
                    () -> assertEquals(0, first.remaining(), "The chunk under the ceiling is read"),
                    () -> assertEquals(6, crossing.remaining(), "The crossing chunk is not copied"),
                    () -> assertEquals(4, later.remaining(), "A later chunk is ignored"),
                    () -> assertTrue(drive.cancelled(), "The subscription is cancelled at the crossing"),
                    () -> assertOverLimit(drive.failure(), 10));
        }

        @Test
        @DisplayName("row 5: should decode with the given charset")
        void shouldDecodeWithGivenCharset() {
            byte[] latin1 = "café".getBytes(StandardCharsets.ISO_8859_1);

            var asLatin1 = BoundedBodyHandlerTestSupport.drive(
                    BoundedBodyHandler.ofString(StandardCharsets.ISO_8859_1, 10, ORIGIN), latin1, null);
            var asUtf8 = BoundedBodyHandlerTestSupport.drive(utf8(10), latin1, null);

            assertAll("Charset parameter",
                    () -> assertEquals("café", asLatin1.body(), "ISO-8859-1 decodes the e-acute byte"),
                    () -> assertEquals("caf�", asUtf8.body(), "UTF-8 replaces the lone byte"));
        }

        @Test
        @DisplayName("row 6: should refuse a null charset, policy or bound origin")
        void shouldRefuseNullArguments() {
            assertAll("Null refusals",
                    () -> assertEquals("charset must not be null", assertThrows(NullPointerException.class,
                            () -> BoundedBodyHandler.ofString(null, 10, ORIGIN)).getMessage(), "charset"),
                    () -> assertEquals("boundOrigin must not be null", assertThrows(NullPointerException.class,
                                    () -> BoundedBodyHandler.ofString(StandardCharsets.UTF_8, 10, null)).getMessage(),
                            "ofString boundOrigin"),
                    () -> assertEquals("policy must not be null", assertThrows(NullPointerException.class,
                            () -> BoundedBodyHandler.ofBytes(10, null, ORIGIN)).getMessage(), "policy"),
                    () -> assertEquals("boundOrigin must not be null", assertThrows(NullPointerException.class,
                                    () -> BoundedBodyHandler.ofBytes(10, OverLimitPolicy.FAIL, null)).getMessage(),
                            "ofBytes boundOrigin"));
        }

        @Test
        @DisplayName("row 7: should fail with the validation message byte for byte")
        void shouldFailWithValidationMessage() {
            var result = BoundedBodyHandlerTestSupport.drive(utf8(255), bytes(256), null);

            assertEquals("Response body exceeds maximum allowed size of 255 bytes (TestConfig.ceiling; fixed)",
                    result.failure().getMessage(), "The exact failure message string");
        }

        @Test
        @DisplayName("row 8: should be one public final class")
        void shouldBePublicFinal() {
            int modifiers = BoundedBodyHandler.class.getModifiers();

            assertAll("API surface",
                    () -> assertTrue(Modifier.isPublic(modifiers), "The handler is public"),
                    () -> assertTrue(Modifier.isFinal(modifiers), "The handler is final"));
        }
    }

    @Nested
    @DisplayName("Fail-policy edges")
    class FailEdges {

        @Test
        @DisplayName("should read an empty body under a zero ceiling and fail on its first byte")
        void shouldHandleZeroCeiling() {
            var empty = BoundedBodyHandlerTestSupport.drive(utf8(0), new byte[0], null);
            var oneByte = BoundedBodyHandlerTestSupport.drive(utf8(0), bytes(1), null);

            assertAll("Zero ceiling",
                    () -> assertEquals("", empty.body(), "An empty body fits a zero ceiling"),
                    () -> assertTrue(oneByte.cancelled(), "The first byte crosses a zero ceiling"),
                    () -> assertOverLimit(oneByte.failure(), 0));
        }

        @Test
        @DisplayName("should read a body equal to the ceiling and fail one byte above it")
        void shouldHandleCeilingEqualAndOneBelow() {
            var equal = BoundedBodyHandlerTestSupport.drive(utf8(300), bytes(300), null);
            var oneBelow = BoundedBodyHandlerTestSupport.drive(utf8(299), bytes(300), null);

            assertAll("Ceiling boundary",
                    () -> assertEquals("a".repeat(300), equal.body(), "A body equal to the ceiling is read"),
                    () -> assertFalse(equal.cancelled(), "An at-ceiling body is not cancelled"),
                    () -> assertTrue(oneBelow.cancelled(), "A ceiling one below the body cancels"),
                    () -> assertOverLimit(oneBelow.failure(), 299));
        }

        @Test
        @DisplayName("should read the same body in one chunk and in many chunks")
        void shouldReadOneAndManyChunks() {
            ManualDrive<String> oneChunk = BoundedBodyHandlerTestSupport.subscribe(utf8(100), null);
            oneChunk.feed(bytes(100));
            oneChunk.complete();
            ManualDrive<String> manyChunks = BoundedBodyHandlerTestSupport.subscribe(utf8(100), null);
            manyChunks.feedAll(bytes(100), 7);
            manyChunks.complete();

            assertAll("Chunking",
                    () -> assertEquals("a".repeat(100), oneChunk.body().join(), "One chunk"),
                    () -> assertEquals("a".repeat(100), manyChunks.body().join(), "Many chunks"));
        }

        @Test
        @DisplayName("should fail during streaming when many chunks cross the ceiling")
        void shouldFailWhenManyChunksCross() {
            ManualDrive<String> drive = BoundedBodyHandlerTestSupport.subscribe(utf8(100), null);
            drive.feedAll(bytes(101), 7);

            assertAll("Streaming crossing",
                    () -> assertTrue(drive.cancelled(), "The crossing chunk cancels the subscription"),
                    () -> assertOverLimit(drive.failure(), 100));
        }

        @Test
        @DisplayName("should catch a lying Content-Length smaller than the body while streaming")
        void shouldCatchLyingContentLength() {
            var lying = BoundedBodyHandlerTestSupport.drive(utf8(300), bytes(600), 10L);
            var honest = BoundedBodyHandlerTestSupport.drive(utf8(300), bytes(10), 10L);

            assertAll("Lying Content-Length",
                    () -> assertTrue(lying.cancelled(), "The stream crosses despite the small header"),
                    () -> assertOverLimit(lying.failure(), 300),
                    () -> assertEquals("a".repeat(10), honest.body(), "An honest small body is read"));
        }

        @Test
        @DisplayName("should enforce the ceiling while streaming a body without Content-Length")
        void shouldEnforceCeilingWithoutContentLength() {
            var over = BoundedBodyHandlerTestSupport.drive(utf8(300), bytes(301), null);
            var within = BoundedBodyHandlerTestSupport.drive(utf8(300), bytes(299), null);

            assertAll("No Content-Length",
                    () -> assertOverLimit(over.failure(), 300),
                    () -> assertEquals("a".repeat(299), within.body(), "A body within the ceiling is read"));
        }

        @Test
        @DisplayName("should propagate a stream error and not complete with the partial body")
        void shouldPropagateStreamError() {
            IOException reset = new IOException("connection reset");
            ManualDrive<String> drive = BoundedBodyHandlerTestSupport.subscribe(utf8(100), null);
            drive.feed(bytes(10));
            drive.error(reset);
            drive.complete();

            assertSame(reset, drive.failure(), "The stream error completes the body future");
        }

        @Test
        @DisplayName("should keep the over-limit failure when the stream errors after the crossing")
        void shouldKeepOverLimitFailureOnLaterError() {
            ManualDrive<String> drive = BoundedBodyHandlerTestSupport.subscribe(utf8(5), null);
            drive.feed(bytes(6));
            drive.error(new IOException("cancelled by the client"));

            assertOverLimit(drive.failure(), 5);
        }

        @Test
        @DisplayName("should cancel a second subscription and keep the first one")
        void shouldCancelSecondSubscription() {
            ManualDrive<String> drive = BoundedBodyHandlerTestSupport.subscribe(utf8(10), null);
            AtomicBoolean secondCancelled = new AtomicBoolean(false);
            drive.resubscribe(new RecordingSubscription(secondCancelled));
            drive.feed(bytes(10));
            drive.complete();

            assertAll("Second subscription",
                    () -> assertTrue(secondCancelled.get(), "A second subscription is cancelled"),
                    () -> assertFalse(drive.cancelled(), "The first subscription stays active"),
                    () -> assertEquals("a".repeat(10), drive.body().join(), "The body is read"));
        }

        @Test
        @DisplayName("should return a not-truncated BoundedBody whose bytes are a copy")
        void shouldReturnBoundedBodyCopy() {
            ManualDrive<BoundedBody> drive = BoundedBodyHandlerTestSupport.subscribe(
                    BoundedBodyHandler.ofBytes(10, OverLimitPolicy.FAIL, ORIGIN), null);
            drive.feed("secret".getBytes(StandardCharsets.US_ASCII));
            drive.complete();
            BoundedBody body = drive.body().join();
            body.bytes()[0] = 'X';

            assertAll("BoundedBody",
                    () -> assertFalse(body.truncated(), "A body within the ceiling is not truncated"),
                    () -> assertEquals("secret", body.asString(StandardCharsets.US_ASCII), "A copy was mutated"),
                    () -> assertEquals("BoundedBody[length=6, truncated=false]", body.toString(),
                            "toString never shows the content"),
                    () -> assertThrows(NullPointerException.class, () -> body.asString(null)));
        }
    }
}
