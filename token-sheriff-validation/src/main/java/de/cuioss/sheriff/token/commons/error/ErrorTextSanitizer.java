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

import org.jspecify.annotations.Nullable;

/**
 * Neutralizes an untrusted, externally-sourced value before it is written into a log line or a
 * caller-visible message, defending against log injection / log forging (CWE-117).
 * <p>
 * The sanitizer escapes (rather than drops) {@code CR}, {@code LF}, tab, the remaining ASCII control
 * characters, DEL, the C1 control characters U+0080 to U+009F (including U+0085 NEL), and the Unicode
 * line separators U+2028 (LS) and U+2029 (PS) — which Unicode-aware log viewers and appenders treat as
 * line breaks — preserving the value's investigative content while making it impossible to inject a line
 * break. {@code CR}, {@code LF} and tab render as {@code \r}, {@code \n} and {@code \t}; every other
 * escaped character renders as a backslash-{@code u} four-digit hex escape.
 * <p>
 * The value is also truncated to {@value #MAX_LENGTH} characters before escaping, so an attacker cannot
 * flood the output with an arbitrarily long value. When truncation occurs the marker
 * {@value #TRUNCATION_MARKER} is appended so the elision is visible.
 * <p>
 * This is the single implementation of that behaviour. It lives in the {@code commons} base layer so
 * both the base layer itself ({@link InboundErrorNormalizer}) and the client engine, which may depend on
 * the base layer but not the other way round (ADR-0001), share it. The utility is stateless and has no
 * configuration surface.
 *
 * @since 1.0
 * @see <a href="https://owasp.org/www-community/attacks/Log_Injection">OWASP Log Injection</a>
 */
public final class ErrorTextSanitizer {

    /** Maximum number of source characters retained; longer values are truncated to bound flooding. */
    public static final int MAX_LENGTH = 256;

    /** Appended when a value is truncated, so the elision is visible. */
    public static final String TRUNCATION_MARKER = "...[truncated]";

    /** U+007F DELETE, the first character of the DEL plus C1 control range. */
    private static final char DELETE = 0x007F;

    /** U+009F APPLICATION PROGRAM COMMAND, the last C1 control character. */
    private static final char LAST_C1_CONTROL = 0x009F;

    /** U+2028 LINE SEPARATOR (LS). */
    private static final char LINE_SEPARATOR = 0x2028;

    /** U+2029 PARAGRAPH SEPARATOR (PS). */
    private static final char PARAGRAPH_SEPARATOR = 0x2029;

    private ErrorTextSanitizer() {
        // utility class — never instantiated
    }

    /**
     * Escapes {@code CR}, {@code LF}, tab, every other ASCII control character, DEL, the C1 control
     * characters U+0080 to U+009F (including U+0085 NEL), and the Unicode line separators U+2028 (LS)
     * and U+2029 (PS) in {@code value} — all but {@code CR}, {@code LF} and tab as a backslash-{@code u}
     * four-digit hex escape — so it cannot forge a new log or response line, and truncates the value to
     * {@value #MAX_LENGTH} characters to bound flooding.
     *
     * @param value the untrusted value to sanitize; may be {@code null}
     * @return the sanitized (and, when over-long, truncated and marked) value, or {@code null} when
     *         {@code value} is {@code null}
     */
    public static @Nullable String sanitize(@Nullable String value) {
        if (value == null) {
            return null;
        }
        boolean truncated = value.length() > MAX_LENGTH;
        String bounded = truncated ? value.substring(0, MAX_LENGTH) : value;
        StringBuilder sanitized = new StringBuilder(bounded.length());
        for (int i = 0; i < bounded.length(); i++) {
            char c = bounded.charAt(i);
            switch (c) {
                case '\r' -> sanitized.append("\\r");
                case '\n' -> sanitized.append("\\n");
                case '\t' -> sanitized.append("\\t");
                default -> {
                    if (isControlCharacter(c) || isUnicodeLineSeparator(c)) {
                        sanitized.append(String.format("\\u%04x", (int) c));
                    } else {
                        sanitized.append(c);
                    }
                }
            }
        }
        if (truncated) {
            sanitized.append(TRUNCATION_MARKER);
        }
        return sanitized.toString();
    }

    /**
     * @param c the character to test
     * @return whether {@code c} is an ASCII control character (below U+0020), DEL (U+007F) or a C1
     *         control character (U+0080 to U+009F, including U+0085 NEL)
     */
    static boolean isControlCharacter(char c) {
        return c < 0x20 || (c >= DELETE && c <= LAST_C1_CONTROL);
    }

    /**
     * @param c the character to test
     * @return whether {@code c} is one of the Unicode line separators U+2028 (LS) or U+2029 (PS), which
     *         Unicode-aware log viewers and appenders render as a line break
     */
    private static boolean isUnicodeLineSeparator(char c) {
        return c == LINE_SEPARATOR || c == PARAGRAPH_SEPARATOR;
    }
}
