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
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the printable-ASCII escape form {@link ErrorTextSanitizer#escapeNonPrintableAscii(String, int, String)}
 * (OD-4) character class by character class: each escaped class with its exact upper-case rendering, the
 * printable ASCII range kept verbatim, the backslash escaped, the truncation boundary at zero, at the cap
 * and one past it, and {@code null}. Every escaped class is paired with a printable neighbour that is kept.
 */
@DisplayName("ErrorTextSanitizer.escapeNonPrintableAscii renders printable ASCII only")
class ErrorTextSanitizerPrintableAsciiTest {

    /** A single backslash, used to build expected escape sequences without source escapes. */
    private static final String BACKSLASH = String.valueOf((char) 92);

    private static final int CAP = 64;

    private static final String MARKER = "...";

    private static String escape(String value) {
        return ErrorTextSanitizer.escapeNonPrintableAscii(value, CAP, MARKER);
    }

    private static String u(int codePoint) {
        return BACKSLASH + "u" + String.format("%04X", codePoint);
    }

    static Stream<Arguments> escapedCharacters() {
        return Stream.of(
                Arguments.of("NUL", 0x0000),
                Arguments.of("HTAB", 0x0009),
                Arguments.of("LF", 0x000A),
                Arguments.of("CR", 0x000D),
                Arguments.of("US, last C0", 0x001F),
                Arguments.of("DEL", 0x007F),
                Arguments.of("first C1", 0x0080),
                Arguments.of("NEL", 0x0085),
                Arguments.of("last C1", 0x009F),
                Arguments.of("NBSP", 0x00A0),
                Arguments.of("LINE SEPARATOR", 0x2028),
                Arguments.of("PARAGRAPH SEPARATOR", 0x2029),
                Arguments.of("RIGHT-TO-LEFT OVERRIDE", 0x202E),
                Arguments.of("FIRST STRONG ISOLATE", 0x2068),
                Arguments.of("COMBINING ACUTE ACCENT", 0x0301),
                Arguments.of("ZERO WIDTH SPACE", 0x200B),
                Arguments.of("ZERO WIDTH JOINER", 0x200D),
                Arguments.of("BYTE ORDER MARK", 0xFEFF),
                Arguments.of("LATIN SMALL E WITH ACUTE", 0x00E9),
                Arguments.of("CYRILLIC SMALL A", 0x0430),
                Arguments.of("CJK IDEOGRAPH", 0x4E2D),
                Arguments.of("lone high surrogate", 0xD83D),
                Arguments.of("lone low surrogate", 0xDE00));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("escapedCharacters")
    @DisplayName("Every character outside printable ASCII is escaped with upper-case hex")
    void shouldEscapeNonPrintableAscii(String name, int codePoint) {
        String value = "left" + (char) codePoint + "right";

        assertEquals("left" + u(codePoint) + "right", escape(value),
                name + " is replaced by its upper-case backslash-u escape, surrounding text untouched");
    }

    @Test
    @DisplayName("A surrogate pair is escaped as its two halves")
    void shouldEscapeSurrogatePair() {
        String emoji = new String(Character.toChars(0x1F600));

        assertEquals("a" + u(0xD83D) + u(0xDE00) + "b", escape("a" + emoji + "b"),
                "each UTF-16 half of a supplementary character is escaped separately");
    }

    @Test
    @DisplayName("The backslash itself is escaped, so a backslash in the result always starts an escape")
    void shouldEscapeBackslash() {
        assertEquals("a" + u(0x5C) + "b", escape("a" + BACKSLASH + "b"), "the backslash renders as its escape");
        assertEquals(u(0x5C) + "u0041", escape(BACKSLASH + "u0041"),
                "a literal escape-shaped input cannot pass for an escape in the result");
    }

    @Test
    @DisplayName("Every printable ASCII character other than the backslash is kept verbatim")
    void shouldKeepPrintableAscii() {
        String printable = IntStream.rangeClosed(0x20, 0x7E)
                .filter(c -> c != 0x5C)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();

        assertEquals(printable, ErrorTextSanitizer.escapeNonPrintableAscii(printable, printable.length(), MARKER),
                "U+0020 to U+007E except the backslash pass through, including space and tilde");
    }

    @ParameterizedTest(name = "[{index}] U+{0}")
    @ValueSource(ints = {0x1F, 0x7F})
    @DisplayName("The printable range ends exactly at space and tilde")
    void shouldEscapeJustOutsidePrintableRange(int outside) {
        assertEquals(u(outside), escape(String.valueOf((char) outside)), "the neighbour outside the range is escaped");
        assertEquals(" ~", escape(" ~"), "control: the range's edges are kept");
    }

    @Test
    @DisplayName("maxLength 0 keeps nothing and appends the marker for a non-empty value")
    void shouldTruncateToZero() {
        assertAll("maxLength 0",
                () -> assertEquals(MARKER, ErrorTextSanitizer.escapeNonPrintableAscii("a", 0, MARKER),
                        "a one-character value is cut to the marker alone"),
                () -> assertEquals("", ErrorTextSanitizer.escapeNonPrintableAscii("", 0, MARKER),
                        "an empty value is not truncated and carries no marker"));
    }

    @Test
    @DisplayName("A value of exactly maxLength is not truncated; one character more is")
    void shouldTruncateAtTheCap() {
        String atCap = "a".repeat(CAP);

        assertEquals(atCap, escape(atCap), "exactly maxLength characters carry no marker");
        assertEquals(atCap + MARKER, escape(atCap + "b"), "maxLength + 1 keeps maxLength characters plus the marker");
    }

    @Test
    @DisplayName("The cap counts source characters before escaping")
    void shouldCountSourceCharactersBeforeEscaping() {
        String controls = String.valueOf((char) 0x0001).repeat(CAP);

        String result = escape(controls + "x");

        assertEquals(u(0x0001).repeat(CAP) + MARKER, result,
                "maxLength source characters are kept and each is escaped; the escape length does not count");
    }

    @Test
    @DisplayName("A cut between the halves of a surrogate pair writes no raw surrogate")
    void shouldNotWriteRawSurrogateAtTheCut() {
        String value = "a".repeat(CAP - 1) + new String(Character.toChars(0x1F600));

        String result = escape(value);

        assertEquals("a".repeat(CAP - 1) + u(0xD83D) + MARKER, result,
                "the kept high half is escaped, the low half is cut");
        assertTrue(result.chars().allMatch(c -> c >= 0x20 && c <= 0x7E), "the result is printable ASCII only");
    }

    @Test
    @DisplayName("null in, null out")
    void shouldReturnNullForNull() {
        assertNull(ErrorTextSanitizer.escapeNonPrintableAscii(null, CAP, MARKER), "null is passed through");
        assertEquals("x", escape("x"), "control: a value is returned");
    }

    @Test
    @DisplayName("A negative maxLength is refused")
    void shouldRefuseNegativeMaxLength() {
        assertThrows(IllegalArgumentException.class,
                () -> ErrorTextSanitizer.escapeNonPrintableAscii("x", -1, MARKER), "a negative cap is refused");
        assertThrows(IllegalArgumentException.class,
                () -> ErrorTextSanitizer.escapeNonPrintableAscii(null, -1, MARKER),
                "the cap is checked before null handling");
        assertEquals(MARKER, ErrorTextSanitizer.escapeNonPrintableAscii("x", 0, MARKER), "control: zero is accepted");
    }
}
