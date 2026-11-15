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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the truncate policy of {@link BoundedBodyHandler#ofBytes(long, OverLimitPolicy, String)}: the
 * result holds exactly the first {@code maxBytes} bytes, {@link BoundedBody#truncated()} is set only when
 * bytes past the ceiling arrived, the subscription is cancelled at the first crossing and no byte past the
 * ceiling is copied (ADR-0031). The fail policy lives in {@link BoundedBodyHandlerTest}.
 */
@DisplayName("BoundedBodyHandler truncate policy")
class BoundedBodyHandlerTruncateTest {

    private static final String ORIGIN = "TestConfig.ceiling; fixed";

    /** A body of {@code length} bytes counting up from {@code '0'}, so a prefix is distinguishable. */
    private static byte[] numbered(int length) {
        byte[] body = new byte[length];
        for (int i = 0; i < length; i++) {
            body[i] = (byte) ('0' + i % 10);
        }
        return body;
    }

    private static String prefix(int length) {
        return new String(numbered(length), StandardCharsets.US_ASCII);
    }

    private static ManualDrive<BoundedBody> truncating(long maxBytes, Long advertisedContentLength) {
        return BoundedBodyHandlerTestSupport.subscribe(
                BoundedBodyHandler.ofBytes(maxBytes, OverLimitPolicy.TRUNCATE, ORIGIN), advertisedContentLength);
    }

    private static String content(BoundedBody body) {
        return body.asString(StandardCharsets.US_ASCII);
    }

    @Nested
    @DisplayName("Prefix and signal")
    class PrefixAndSignal {

        @Test
        @DisplayName("should deliver exactly the first maxBytes bytes and copy none past the ceiling")
        void shouldDeliverExactPrefix() {
            ManualDrive<BoundedBody> drive = truncating(15, null);
            drive.feed(numbered(10));
            ByteBuffer crossing = drive.feed(numbered(10));
            BoundedBody body = drive.body().join();

            assertAll("Truncated prefix",
                    () -> assertEquals(prefix(10) + prefix(5), content(body), "Exactly the first 15 bytes"),
                    () -> assertTrue(body.truncated(), "Bytes past the ceiling arrived"),
                    () -> assertEquals(5, crossing.remaining(),
                            "Zero bytes past the ceiling are copied: the overshoot stays in the in-flight chunk"),
                    () -> assertEquals("BoundedBody[length=15, truncated=true]", body.toString(),
                            "toString never shows the content"));
        }

        @Test
        @DisplayName("should not report truncation for a body of exactly maxBytes")
        void shouldNotTruncateBodyOfExactlyTheCeiling() {
            ManualDrive<BoundedBody> drive = truncating(20, null);
            drive.feedAll(numbered(20), 7);
            drive.complete();
            BoundedBody body = drive.body().join();

            assertAll("At-ceiling body",
                    () -> assertEquals(prefix(20), content(body), "The whole body is delivered"),
                    () -> assertFalse(body.truncated(), "No byte past the ceiling arrived"),
                    () -> assertFalse(drive.cancelled(), "An at-ceiling body is read to its end"));
        }

        @Test
        @DisplayName("should cancel at the first crossing and ignore every later chunk")
        void shouldCancelAtFirstCrossing() {
            ManualDrive<BoundedBody> drive = truncating(8, null);
            drive.feed(numbered(9));
            ByteBuffer later = drive.feed(numbered(4));
            drive.complete();
            BoundedBody body = drive.body().join();

            assertAll("Crossing",
                    () -> assertTrue(drive.cancelled(), "The subscription is cancelled at the crossing"),
                    () -> assertEquals(4, later.remaining(), "A later chunk is not read"),
                    () -> assertEquals(prefix(8), content(body), "The result is fixed at the crossing"));
        }

        @Test
        @DisplayName("should read an over-advertised body up to the ceiling instead of refusing it")
        void shouldReadOverAdvertisedBody() {
            ManualDrive<BoundedBody> drive = truncating(10, 1_000_000L);

            boolean cancelledBeforeReading = drive.cancelled();
            boolean requested = drive.requested();
            drive.feed(numbered(12));
            BoundedBody body = drive.body().join();

            assertAll("Over-advertised Content-Length",
                    () -> assertFalse(cancelledBeforeReading, "The truncate policy runs no pre-check"),
                    () -> assertTrue(requested, "The body is requested"),
                    () -> assertEquals(prefix(10), content(body), "The body is read up to the ceiling"),
                    () -> assertTrue(body.truncated(), "The dropped bytes are signalled"),
                    () -> assertTrue(drive.cancelled(), "The read is cancelled at the ceiling"));
        }

        @Test
        @DisplayName("should return a copy from bytes()")
        void shouldReturnCopy() {
            ManualDrive<BoundedBody> drive = truncating(4, null);
            drive.feed(numbered(6));
            BoundedBody body = drive.body().join();
            byte[] copy = body.bytes();
            copy[0] = 'X';

            assertEquals(prefix(4), content(body), "Mutating the returned array leaves the body unchanged");
        }
    }

    @Nested
    @DisplayName("Truncate-policy edges")
    class TruncateEdges {

        @Test
        @DisplayName("should deliver an empty body under a zero ceiling, truncated only when a byte arrived")
        void shouldHandleZeroCeiling() {
            ManualDrive<BoundedBody> empty = truncating(0, null);
            empty.complete();
            ManualDrive<BoundedBody> oneByte = truncating(0, null);
            oneByte.feed(numbered(1));

            BoundedBody emptyBody = empty.body().join();
            BoundedBody oneByteBody = oneByte.body().join();
            assertAll("Zero ceiling",
                    () -> assertEquals(0, emptyBody.bytes().length, "An empty body stays empty"),
                    () -> assertFalse(emptyBody.truncated(), "No byte arrived"),
                    () -> assertEquals(0, oneByteBody.bytes().length, "No byte is delivered"),
                    () -> assertTrue(oneByteBody.truncated(), "The one byte was dropped"));
        }

        @Test
        @DisplayName("should drop the last byte under a ceiling one below the body length")
        void shouldHandleCeilingOneBelow() {
            ManualDrive<BoundedBody> drive = truncating(29, null);
            drive.feedAll(numbered(30), 10);
            BoundedBody body = drive.body().join();

            assertAll("One below",
                    () -> assertEquals(prefix(29), content(body), "The first 29 bytes"),
                    () -> assertTrue(body.truncated(), "The last byte was dropped"));
        }

        @Test
        @DisplayName("should truncate the same way in one chunk and in many chunks")
        void shouldTruncateOneAndManyChunks() {
            ManualDrive<BoundedBody> oneChunk = truncating(50, null);
            ByteBuffer only = oneChunk.feed(numbered(80));
            ManualDrive<BoundedBody> manyChunks = truncating(50, null);
            manyChunks.feedAll(numbered(80), 3);

            BoundedBody fromOne = oneChunk.body().join();
            BoundedBody fromMany = manyChunks.body().join();
            assertAll("Chunking",
                    () -> assertEquals(prefix(50), content(fromOne), "One chunk"),
                    () -> assertEquals(30, only.remaining(), "Only the ceiling is copied from the one chunk"),
                    () -> assertEquals(prefix(50), content(fromMany), "Many chunks"),
                    () -> assertTrue(fromOne.truncated() && fromMany.truncated(), "Both signal the drop"));
        }

        @Test
        @DisplayName("should propagate a stream error before the ceiling and deliver no partial body")
        void shouldPropagateStreamError() {
            IOException reset = new IOException("connection reset");
            ManualDrive<BoundedBody> drive = truncating(100, null);
            drive.feed(numbered(10));
            drive.error(reset);
            drive.complete();

            assertSame(reset, drive.failure(), "The stream error completes the body future");
        }

        @Test
        @DisplayName("should keep the truncated body when the cancelled stream later errors")
        void shouldKeepTruncatedBodyOnLaterError() {
            ManualDrive<BoundedBody> drive = truncating(5, null);
            drive.feed(numbered(6));
            drive.error(new IOException("cancelled by the client"));

            BoundedBody body = drive.body().join();
            assertAll("Error after truncation",
                    () -> assertEquals(prefix(5), content(body), "The prefix stands"),
                    () -> assertTrue(body.truncated(), "The truncation signal stands"));
        }
    }
}
