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
package de.cuioss.sheriff.token.client.internal;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableGeneratorController
@DisplayName("LogSanitizer neutralizes log-injection control characters (CWE-117)")
class LogSanitizerTest {

    /** The truncation marker documented on {@code LogSanitizer.TRUNCATION_MARKER}. */
    private static final String TRUNCATION_MARKER = "...[truncated]";

    /** The retained-prefix cap documented on {@code LogSanitizer.MAX_LENGTH}. */
    private static final int MAX_LENGTH = 256;

    /** A single backslash, used to build expected escape-sequence needles without source escapes. */
    private static final String BACKSLASH = String.valueOf((char) 92);

    private static final char TAB = 9;
    private static final char LF = 10;
    private static final char CR = 13;
    private static final char NUL = 0;
    private static final char DEL = 127;
    private static final char NEL = (char) 0x0085;
    private static final char LS = (char) 0x2028;
    private static final char PS = (char) 0x2029;

    @Test
    @DisplayName("Should return null for a null value")
    void shouldReturnNullForNull() {
        assertNull(LogSanitizer.sanitize(null), "a null value sanitizes to null, never an empty string");
    }

    @Test
    @DisplayName("Should pass a printable value through unchanged")
    void shouldPassPrintableThrough() {
        String value = Generators.letterStrings(5, 40).next();

        assertEquals(value, LogSanitizer.sanitize(value),
                "a value with no control characters must be returned verbatim");
    }

    @Test
    @DisplayName("Should escape CR and LF so an attacker cannot forge a new log line")
    void shouldEscapeCrLf() {
        String forged = Generators.letterStrings(3, 10).next()
                + Character.toString(CR) + Character.toString(LF) + "INFO forged log entry";

        String sanitized = LogSanitizer.sanitize(forged);

        assertAll("no raw line breaks survive",
                () -> assertFalse(sanitized.indexOf(CR) >= 0, "a raw carriage return must not survive"),
                () -> assertFalse(sanitized.indexOf(LF) >= 0, "a raw line feed must not survive"),
                () -> assertTrue(sanitized.contains(BACKSLASH + "r"), "CR is rendered as a visible escape sequence"),
                () -> assertTrue(sanitized.contains(BACKSLASH + "n"), "LF is rendered as a visible escape sequence"));
    }

    @Test
    @DisplayName("Should escape tab and other ASCII control characters as unicode escapes")
    void shouldEscapeControlChars() {
        String value = "a" + Character.toString(TAB) + "b" + Character.toString(NUL)
                + "c" + Character.toString(DEL) + "d";

        String sanitized = LogSanitizer.sanitize(value);

        assertAll("control characters escaped, none surviving raw",
                () -> assertTrue(sanitized.contains(BACKSLASH + "t"), "tab is escaped"),
                () -> assertTrue(sanitized.contains(BACKSLASH + "u0000"), "NUL is escaped as a unicode sequence"),
                () -> assertTrue(sanitized.contains(BACKSLASH + "u007f"), "DEL is escaped as a unicode sequence"),
                () -> assertFalse(sanitized.indexOf(NUL) >= 0, "no raw NUL survives"),
                () -> assertFalse(sanitized.indexOf(DEL) >= 0, "no raw DEL survives"));
    }

    @Test
    @DisplayName("Should escape the Unicode line separators NEL, LS and PS (C-8a)")
    void shouldEscapeUnicodeLineSeparators() {
        String value = Generators.letterStrings(3, 10).next() + Character.toString(NEL) + "a"
                + Character.toString(LS) + "b" + Character.toString(PS) + "INFO forged log entry";

        String sanitized = LogSanitizer.sanitize(value);

        assertAll("no raw Unicode line separator survives",
                () -> assertFalse(sanitized.indexOf(NEL) >= 0, "no raw NEL (U+0085) survives"),
                () -> assertFalse(sanitized.indexOf(LS) >= 0, "no raw LS (U+2028) survives"),
                () -> assertFalse(sanitized.indexOf(PS) >= 0, "no raw PS (U+2029) survives"),
                () -> assertTrue(sanitized.contains(BACKSLASH + "u0085"), "NEL is escaped as a unicode sequence"),
                () -> assertTrue(sanitized.contains(BACKSLASH + "u2028"), "LS is escaped as a unicode sequence"),
                () -> assertTrue(sanitized.contains(BACKSLASH + "u2029"), "PS is escaped as a unicode sequence"));
    }

    @ParameterizedTest
    @ValueSource(chars = {(char) 0x007F, (char) 0x0080, (char) 0x009B, (char) 0x009D, (char) 0x009F,
            (char) 0x2028, (char) 0x2029})
    @DisplayName("Should escape C1 controls and the characters at the edges of the escaped ranges")
    void shouldEscapeRangeEdgeAndC1Characters(char escaped) {
        String prefix = Generators.letterStrings(3, 10).next();
        String suffix = "INFO forged log entry";

        String sanitized = LogSanitizer.sanitize(prefix + escaped + suffix);

        assertAll("character escaped, none surviving raw",
                () -> assertFalse(sanitized.indexOf(escaped) >= 0, "no raw character survives"),
                () -> assertEquals(prefix + BACKSLASH + String.format("u%04x", (int) escaped) + suffix,
                        sanitized, "the character is rendered as a unicode escape sequence"));
    }

    @ParameterizedTest
    @ValueSource(chars = {(char) 0x007E, (char) 0x00A0, (char) 0x2027, (char) 0x202A})
    @DisplayName("Should pass the characters just outside the escaped ranges through unescaped")
    void shouldPassRangeBoundaryNeighboursThrough(char neighbour) {
        String value = Generators.letterStrings(3, 10).next() + neighbour + "suffix";

        assertEquals(value, LogSanitizer.sanitize(value),
                "a character just outside the escaped ranges is kept verbatim");
    }

    @Test
    @DisplayName("Should truncate an over-long value and mark the elision (L18)")
    void shouldTruncateOverLongValue() {
        String value = Generators.letterStrings(MAX_LENGTH + 50, MAX_LENGTH + 150).next();

        String sanitized = LogSanitizer.sanitize(value);

        assertAll("bounded and visibly marked",
                () -> assertTrue(sanitized.endsWith(TRUNCATION_MARKER), "truncation is visibly marked in the log"),
                () -> assertEquals(MAX_LENGTH, sanitized.length() - TRUNCATION_MARKER.length(),
                        "exactly MAX_LENGTH source characters are retained before the marker"),
                () -> assertTrue(value.startsWith(sanitized.substring(0, MAX_LENGTH)),
                        "the retained prefix is the original leading characters"));
    }
}
