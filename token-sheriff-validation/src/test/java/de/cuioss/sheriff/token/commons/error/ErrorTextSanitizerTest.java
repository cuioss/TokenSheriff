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
package de.cuioss.sheriff.token.commons.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the single shared sanitize behaviour once, character by character: every escaped code point
 * with its exact rendering, the neighbours just outside the escaped ranges, null handling, and the
 * truncation boundary.
 */
@DisplayName("ErrorTextSanitizer pins the shared CWE-117 escaping and truncation contract")
class ErrorTextSanitizerTest {

    /** A single backslash, used to build expected escape sequences without source escapes. */
    private static final String BACKSLASH = String.valueOf((char) 92);

    static Stream<Arguments> escapedCharacters() {
        return Stream.of(
                Arguments.of((char) 0x000D, "r"),
                Arguments.of((char) 0x000A, "n"),
                Arguments.of((char) 0x0009, "t"),
                Arguments.of((char) 0x0000, "u0000"),
                Arguments.of((char) 0x0007, "u0007"),
                Arguments.of((char) 0x001F, "u001f"),
                Arguments.of((char) 0x007F, "u007f"),
                Arguments.of((char) 0x0080, "u0080"),
                Arguments.of((char) 0x0085, "u0085"),
                Arguments.of((char) 0x009F, "u009f"),
                Arguments.of((char) 0x2028, "u2028"),
                Arguments.of((char) 0x2029, "u2029"));
    }

    @ParameterizedTest(name = "renders as escape {1}")
    @MethodSource("escapedCharacters")
    @DisplayName("Should render every escaped character as its exact escape sequence")
    void shouldRenderExactEscape(char escaped, String escapeBody) {
        assertEquals("left" + BACKSLASH + escapeBody + "right", ErrorTextSanitizer.sanitize("left" + escaped + "right"),
                "the character is replaced by exactly its documented escape, surrounding text untouched");
    }

    static Stream<Character> keptCharacters() {
        return Stream.of((char) 0x0020, (char) 0x007E, (char) 0x00A0, (char) 0x2027, (char) 0x202A, 'z');
    }

    @ParameterizedTest
    @MethodSource("keptCharacters")
    @DisplayName("Should keep the characters just outside the escaped ranges verbatim")
    void shouldKeepBoundaryNeighbours(char kept) {
        String single = String.valueOf(kept);

        assertEquals(single, ErrorTextSanitizer.sanitize(single), "a character outside the escaped ranges is not escaped");
    }

    @Test
    @DisplayName("Should map null to null")
    void shouldMapNullToNull() {
        assertNull(ErrorTextSanitizer.sanitize(null), "null stays null, never an empty string");
    }

    @Test
    @DisplayName("Should keep a value of exactly MAX_LENGTH characters whole and truncate one character more")
    void shouldTruncateExactlyAboveTheBoundary() {
        String atLimit = "a".repeat(ErrorTextSanitizer.MAX_LENGTH);
        String overLimit = atLimit + "b";

        assertAll("truncation boundary",
                () -> assertEquals(atLimit, ErrorTextSanitizer.sanitize(atLimit),
                        "a value of exactly MAX_LENGTH characters is not truncated"),
                () -> assertEquals(atLimit + "...[truncated]", ErrorTextSanitizer.sanitize(overLimit),
                        "one character more is cut to MAX_LENGTH and carries the marker"),
                () -> assertEquals("...[truncated]", ErrorTextSanitizer.TRUNCATION_MARKER,
                        "the marker text is unchanged"));
    }

    @Test
    @DisplayName("Should truncate on source characters before escaping")
    void shouldTruncateBeforeEscaping() {
        String lineFeeds = String.valueOf((char) 0x000A).repeat(ErrorTextSanitizer.MAX_LENGTH + 1);

        assertEquals((BACKSLASH + "n").repeat(ErrorTextSanitizer.MAX_LENGTH) + ErrorTextSanitizer.TRUNCATION_MARKER,
                ErrorTextSanitizer.sanitize(lineFeeds),
                "the cap counts source characters, so escaping can lengthen the retained prefix");
    }
}
