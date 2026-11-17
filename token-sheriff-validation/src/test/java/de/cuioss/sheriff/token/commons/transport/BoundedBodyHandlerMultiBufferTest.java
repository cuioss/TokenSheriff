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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins that {@link BoundedBodyHandler} reads every buffer of an {@code onNext} call that delivers several
 * buffers at once, as an HTTP client does: the assembled body is checked byte for byte below the ceiling,
 * exactly at it, and crossing it in the middle of the list under both policies. Each case is paired with
 * the same bytes delivered one buffer per {@code onNext} call as the control.
 */
@DisplayName("BoundedBodyHandler several buffers in one onNext call")
class BoundedBodyHandlerMultiBufferTest {

    private static final String ORIGIN = "TestConfig.ceiling; fixed";

    private static final byte[][] CHUNKS = {ascii("abc"), ascii("de"), ascii("fgh"), ascii("ij")};

    private static final String WHOLE = "abcdefghij";

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static ManualDrive<BoundedBody> subscribed(long maxBytes, OverLimitPolicy policy) {
        return BoundedBodyHandlerTestSupport.subscribe(BoundedBodyHandler.ofBytes(maxBytes, policy, ORIGIN), null);
    }

    private static ManualDrive<BoundedBody> fedOneBufferPerCall(long maxBytes, OverLimitPolicy policy) {
        ManualDrive<BoundedBody> drive = subscribed(maxBytes, policy);
        for (byte[] chunk : CHUNKS) {
            drive.feed(chunk);
        }
        drive.complete();
        return drive;
    }

    private static String content(BoundedBody body) {
        return body.asString(StandardCharsets.US_ASCII);
    }

    @Nested
    @DisplayName("Within the ceiling")
    class WithinCeiling {

        @ParameterizedTest(name = "{0}")
        @EnumSource(OverLimitPolicy.class)
        @DisplayName("should assemble every buffer of the list below the ceiling")
        void shouldAssembleEveryBufferBelowCeiling(OverLimitPolicy policy) {
            ManualDrive<BoundedBody> drive = subscribed(100, policy);

            List<ByteBuffer> buffers = drive.feedInOneCall(CHUNKS);
            drive.complete();

            BoundedBody body = drive.body().join();
            BoundedBody control = fedOneBufferPerCall(100, policy).body().join();
            assertAll("Below the ceiling",
                    () -> assertEquals(WHOLE, content(body), "Every buffer of the list is in the body, in order"),
                    () -> assertFalse(body.truncated(), "Nothing past the ceiling arrived"),
                    () -> assertTrue(buffers.stream().noneMatch(ByteBuffer::hasRemaining),
                            "Every buffer of the list is read to its end"),
                    () -> assertEquals(content(control), content(body),
                            "control: the same bytes in one buffer per call give the same body"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(OverLimitPolicy.class)
        @DisplayName("should assemble a list ending exactly at the ceiling without truncation")
        void shouldAssembleListEndingAtCeiling(OverLimitPolicy policy) {
            ManualDrive<BoundedBody> drive = subscribed(WHOLE.length(), policy);

            drive.feedInOneCall(CHUNKS);
            drive.complete();

            BoundedBody body = drive.body().join();
            BoundedBody control = fedOneBufferPerCall(WHOLE.length(), policy).body().join();
            assertAll("Exactly at the ceiling",
                    () -> assertEquals(WHOLE, content(body), "The whole body is delivered"),
                    () -> assertFalse(body.truncated(), "A body of exactly the ceiling is not truncated"),
                    () -> assertFalse(drive.cancelled(), "An at-ceiling body is read to its end"),
                    () -> assertEquals(content(control), content(body), "control: one buffer per call"));
        }
    }

    @Nested
    @DisplayName("Crossing the ceiling in the middle of the list")
    class CrossingMidList {

        private static final long CEILING = 6;

        @Test
        @DisplayName("should fail at the buffer that crosses under FAIL and read no later buffer")
        void shouldFailAtCrossingBuffer() {
            ManualDrive<BoundedBody> drive = subscribed(CEILING, OverLimitPolicy.FAIL);

            List<ByteBuffer> buffers = drive.feedInOneCall(CHUNKS);
            drive.complete();

            ManualDrive<BoundedBody> control = fedOneBufferPerCall(CEILING, OverLimitPolicy.FAIL);
            IOException failure = assertInstanceOf(IOException.class, drive.failure(), "FAIL fails the read");
            assertAll("FAIL crossing in the list",
                    () -> assertEquals("Response body exceeds maximum allowed size of 6 bytes (" + ORIGIN + ")",
                            failure.getMessage(), "The over-limit message is unchanged"),
                    () -> assertTrue(drive.cancelled(), "The subscription is cancelled at the crossing"),
                    () -> assertEquals(0, buffers.get(1).remaining(), "The buffer before the crossing was read"),
                    () -> assertEquals(3, buffers.get(2).remaining(), "The crossing buffer is not copied"),
                    () -> assertEquals(2, buffers.get(3).remaining(), "A buffer after the crossing is not read"),
                    () -> assertEquals(failure.getMessage(), control.failure().getMessage(),
                            "control: one buffer per call fails the same way"));
        }

        @Test
        @DisplayName("should keep exactly the ceiling bytes across the buffers under TRUNCATE")
        void shouldTruncateAcrossBuffers() {
            ManualDrive<BoundedBody> drive = subscribed(CEILING, OverLimitPolicy.TRUNCATE);

            List<ByteBuffer> buffers = drive.feedInOneCall(CHUNKS);
            drive.complete();

            BoundedBody body = drive.body().join();
            BoundedBody control = fedOneBufferPerCall(CEILING, OverLimitPolicy.TRUNCATE).body().join();
            assertAll("TRUNCATE crossing in the list",
                    () -> assertEquals("abcdef", content(body), "The first six bytes, taken from three buffers"),
                    () -> assertTrue(body.truncated(), "The dropped bytes are signalled"),
                    () -> assertTrue(drive.cancelled(), "The subscription is cancelled at the crossing"),
                    () -> assertEquals(2, buffers.get(2).remaining(), "Only the prefix of the crossing buffer is copied"),
                    () -> assertEquals(2, buffers.get(3).remaining(), "A buffer after the crossing is not read"),
                    () -> assertEquals(content(control), content(body), "control: one buffer per call"));
        }
    }
}
