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
package de.cuioss.sheriff.token.client.flow;

import de.cuioss.sheriff.token.client.internal.HeaderValues;
import de.cuioss.sheriff.token.client.internal.LogSanitizer;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the RFC 9110 {@code auth-param} list of a single {@code Bearer} challenge off a
 * {@code WWW-Authenticate} header value, shared by {@link StepUpChallengeParser} and
 * {@link InsufficientScopeChallengeParser}.
 * <p>
 * The header guard refuses the whole value — it never repairs it — when the value:
 * <ul>
 *   <li>is longer than {@value #MAX_HEADER_LENGTH} characters, checked before any other work;</li>
 *   <li>carries {@code CR}, {@code LF} or {@code NUL} ({@link HeaderValues#containsLineBreakOrNul}),
 *       checked before any {@code strip()};</li>
 *   <li>carries any other C0 control character or {@code DEL}; horizontal tab is tolerated as RFC 9110
 *       optional whitespace and as {@code qdtext};</li>
 *   <li>does not start with the {@code Bearer} scheme (case-insensitive);</li>
 *   <li>shows a following challenge as a segment: a non-empty segment without {@code =} (for example
 *       {@code Basic} or {@code realm}), or a segment whose name is not an RFC 9110 {@code token} (for
 *       example {@code basic realm});</li>
 *   <li>names an {@code auth-param} twice (RFC 9110 §11.2).</li>
 * </ul>
 * An empty segment from {@code ,,} is dropped, as RFC 9110 list syntax allows. The list is split on
 * commas outside a quoted-string; a quoted value is unescaped per RFC 9110 §5.6.4.
 * <p>
 * The two modes differ only where the grammar is broken: {@link Mode#LENIENT} skips a segment with an
 * empty name and keeps an unterminated quoted value verbatim; {@link Mode#STRICT} refuses the whole
 * value for an empty name, an unterminated quoted value, a trailing backslash or a quote or backslash
 * in an unquoted value.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
final class BearerChallengeParams {

    /** Longest {@code WWW-Authenticate} value that is read at all, in characters (8 KiB). */
    static final int MAX_HEADER_LENGTH = 8192;

    private static final CuiLogger LOGGER = new CuiLogger(BearerChallengeParams.class);
    private static final String BEARER_PREFIX = "bearer ";
    private static final char HORIZONTAL_TAB = '\t';
    private static final char DELETE = 0x7F;
    private static final String TOKEN_SPECIALS = "!#$%&'*+-.^_`|~";

    /**
     * How malformed grammar inside an otherwise well-formed challenge is treated.
     */
    enum Mode {
        /** Skip an empty-name segment and keep an unterminated quoted value verbatim. */
        LENIENT,
        /** Refuse the whole value for an empty name, an unterminated quoted value or a stray backslash. */
        STRICT
    }

    private BearerChallengeParams() {
        // utility class — never instantiated
    }

    /**
     * Reads the {@code auth-param}s of a {@code Bearer} challenge.
     *
     * @param wwwAuthenticate the raw header value, or {@code null}
     * @param mode            how malformed grammar is treated
     * @return the parameters keyed by lower-case name, or {@link Optional#empty()} if the value is absent,
     *         blank or refused by the header guard described on this class
     */
    static Optional<Map<String, String>> parse(@Nullable String wwwAuthenticate, Mode mode) {
        if (wwwAuthenticate == null || wwwAuthenticate.isBlank()) {
            return Optional.empty();
        }
        if (wwwAuthenticate.length() > MAX_HEADER_LENGTH) {
            LOGGER.debug("Ignoring WWW-Authenticate challenge longer than %s characters", MAX_HEADER_LENGTH);
            return Optional.empty();
        }
        if (HeaderValues.containsLineBreakOrNul(wwwAuthenticate)) {
            // Checked before any strip(): stripping would remove the separator from between two
            // auth-params and turn a split or folded header into an actionable challenge (fail closed).
            LOGGER.debug("Ignoring WWW-Authenticate challenge carrying CR, LF or NUL");
            return Optional.empty();
        }
        if (containsOtherControlCharacter(wwwAuthenticate)) {
            LOGGER.debug("Ignoring WWW-Authenticate challenge carrying a control character");
            return Optional.empty();
        }
        String trimmed = wwwAuthenticate.strip();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return parseParams(trimmed.substring(BEARER_PREFIX.length()), mode);
    }

    private static Optional<Map<String, String>> parseParams(String paramList, Mode mode) {
        Map<String, String> params = new HashMap<>();
        for (String segment : splitTopLevel(paramList)) {
            String pair = segment.strip();
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq < 0) {
                LOGGER.debug("Ignoring WWW-Authenticate challenge with a valueless segment or a second scheme");
                return Optional.empty();
            }
            if (eq == 0) {
                if (mode == Mode.STRICT) {
                    LOGGER.debug("Ignoring WWW-Authenticate challenge with an empty auth-param name");
                    return Optional.empty();
                }
                continue;
            }
            String name = pair.substring(0, eq).strip();
            if (!isToken(name)) {
                LOGGER.debug("Ignoring WWW-Authenticate challenge with auth-param name '%s', not a token",
                        LogSanitizer.sanitize(name));
                return Optional.empty();
            }
            Optional<String> value = readValue(pair.substring(eq + 1).strip(), mode);
            if (value.isEmpty()) {
                LOGGER.debug("Ignoring WWW-Authenticate challenge with a malformed value for '%s'",
                        LogSanitizer.sanitize(name));
                return Optional.empty();
            }
            String key = name.toLowerCase(Locale.ROOT);
            if (params.putIfAbsent(key, value.get()) != null) {
                LOGGER.debug("Ignoring WWW-Authenticate challenge with duplicate auth-param '%s'",
                        LogSanitizer.sanitize(key));
                return Optional.empty();
            }
        }
        return Optional.of(params);
    }

    /**
     * Splits {@code input} on top-level commas — commas that are not inside a double-quoted string.
     * Backslash escapes are honoured inside quotes so {@code \"} does not prematurely close the
     * quoted-string and a comma inside the quotes stays with its value.
     */
    private static List<String> splitTopLevel(String input) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\' && inQuotes) {
                current.append(c);
                escaped = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == ',' && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private static Optional<String> readValue(String rawValue, Mode mode) {
        if (mode == Mode.LENIENT) {
            return Optional.of(unquote(rawValue));
        }
        if (rawValue.isEmpty() || rawValue.charAt(0) != '"') {
            boolean clean = rawValue.indexOf('"') < 0 && rawValue.indexOf('\\') < 0;
            return clean ? Optional.of(rawValue) : Optional.empty();
        }
        return unquoteStrict(rawValue);
    }

    /**
     * Strips the surrounding double-quotes from a {@code quoted-string} value and unescapes its
     * backslash escapes ({@code \"} → {@code "}, {@code \\} → {@code \}), per RFC 9110 §5.6.4.
     * An unquoted {@code token} value, and a value that never closes its quote, is returned unchanged.
     */
    private static String unquote(String value) {
        if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            return value;
        }
        String inner = value.substring(1, value.length() - 1);
        StringBuilder sb = new StringBuilder(inner.length());
        boolean escaped = false;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Reads a {@code quoted-string} that must be the whole value: the closing quote is the last
     * character and is not escaped. An unterminated quote or a trailing backslash yields empty.
     */
    private static Optional<String> unquoteStrict(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i == value.length() - 1 ? Optional.of(sb.toString()) : Optional.empty();
            } else {
                sb.append(c);
            }
        }
        return Optional.empty();
    }

    private static boolean containsOtherControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < 0x20 && c != HORIZONTAL_TAB) || c == DELETE) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether {@code name} is an RFC 9110 {@code token}: one or more {@code tchar}
     */
    private static boolean isToken(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean tchar = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || TOKEN_SPECIALS.indexOf(c) >= 0;
            if (!tchar) {
                return false;
            }
        }
        return true;
    }
}
