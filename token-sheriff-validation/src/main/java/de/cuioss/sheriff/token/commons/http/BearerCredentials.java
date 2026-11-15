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
package de.cuioss.sheriff.token.commons.http;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Extracts RFC 6750 §2.1 {@code Bearer} credentials from an HTTP {@code Authorization} field value.
 * <p>
 * The extraction reports exactly one of three {@link State states}:
 * <ul>
 *   <li>{@link State#ABSENT} - the value carries no {@code Bearer} credentials at all: it is {@code null}
 *       or empty, names another scheme (such as {@code Basic}), names a longer scheme that merely starts
 *       with {@code Bearer} (such as {@code Bearerx}), is the bare scheme {@code Bearer}, or follows the
 *       scheme with a tab instead of a space</li>
 *   <li>{@link State#MALFORMED} - the value names the {@code Bearer} scheme but the credentials are not
 *       usable: the token is empty or whitespace-only, or holds a space, a horizontal tab or a control
 *       character (CR, LF, NUL and the other C0 controls, DEL, or a C1 control)</li>
 *   <li>{@link State#PRESENT} - the value carries a token; {@link Result#token()} returns it</li>
 * </ul>
 * The rules applied, in order:
 * <ol>
 *   <li>Leading optional whitespace (SP and HTAB) of the field value is ignored (RFC 9110 §5.5).</li>
 *   <li>The scheme name matches case-insensitively (RFC 9110 §11.1), so {@code bearer}, {@code BEARER}
 *       and {@code bEaReR} all name the {@code Bearer} scheme.</li>
 *   <li>The scheme must be followed by one or more SP (RFC 6750 §2.1 {@code 1*SP}); a tab after the
 *       scheme does not separate it, so such a value is not {@code Bearer} credentials.</li>
 *   <li>Trailing optional whitespace of the field value is ignored (RFC 9110 §5.5); what remains is the
 *       token.</li>
 * </ol>
 * <p>
 * The extraction is bounded: it scans the value once and copies the token with one
 * {@link String#substring(int, int)}. It imposes no limit on the token size and does not check the
 * token's {@code b64token} grammar; both stay with the token validation pipeline. A refused value is never
 * retained, and {@link Result#toString()} never prints a token, so a result is safe to log.
 * <p>
 * The class is framework-neutral (it depends on {@code java.util} and JSpecify only) and stateless; every
 * method is safe to call from any number of threads, and every {@link Result} is immutable.
 * <p>
 * Example:
 * {@snippet :
 * BearerCredentials.Result credentials = BearerCredentials.extract(request.getHeader("Authorization"));
 * switch (credentials.state()) {
 *     case PRESENT -> validate(credentials.token().orElseThrow());
 *     case MALFORMED -> answerBadRequest();       // RFC 6750 §3.1 invalid_request
 *     case ABSENT -> answerUnauthorized();        // no credentials presented
 * }
 * }
 *
 * @since 1.0
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6750#section-2.1">RFC 6750 §2.1</a>
 */
public final class BearerCredentials {

    /** The authentication scheme name, matched case-insensitively. */
    private static final String SCHEME = "Bearer";

    /** The lower-case HTTP field name the header-map lookup tries first. */
    private static final String AUTHORIZATION = "authorization";

    private static final char SPACE = ' ';

    private static final char HORIZONTAL_TAB = '\t';

    /** U+007F DELETE, the first character of the DEL plus C1 control range. */
    private static final char DELETE = 0x007F;

    /** U+009F APPLICATION PROGRAM COMMAND, the last C1 control character. */
    private static final char LAST_C1_CONTROL = 0x009F;

    private static final Result ABSENT_RESULT = new Result(State.ABSENT, false, null);

    private static final Result MALFORMED_EMPTY_RESULT = new Result(State.MALFORMED, true, null);

    private static final Result MALFORMED_CHARACTER_RESULT = new Result(State.MALFORMED, false, null);

    private BearerCredentials() {
        // utility class - never instantiated
    }

    /**
     * The outcome of an extraction.
     *
     * @since 1.0
     */
    public enum State {

        /** The value carries no {@code Bearer} credentials. */
        ABSENT,

        /** The value names the {@code Bearer} scheme, but its token is empty or holds a refused character. */
        MALFORMED,

        /** The value carries a {@code Bearer} token. */
        PRESENT
    }

    /**
     * The immutable result of an extraction: its {@link State} and, for {@link State#PRESENT} only, the
     * token.
     *
     * @since 1.0
     */
    public static final class Result {

        private final State state;

        private final boolean tokenEmpty;

        private final @Nullable String token;

        private Result(State state, boolean tokenEmpty, @Nullable String token) {
            this.state = state;
            this.tokenEmpty = tokenEmpty;
            this.token = token;
        }

        /**
         * @return the outcome of the extraction, never {@code null}
         */
        public State state() {
            return state;
        }

        /**
         * Tells the two {@link State#MALFORMED} causes apart without retaining the refused value.
         *
         * @return {@code true} when the state is {@link State#MALFORMED} because the token is empty or
         *         whitespace-only; {@code false} when it holds a refused character, and for every other state
         */
        public boolean isTokenEmpty() {
            return tokenEmpty;
        }

        /**
         * @return the token when the state is {@link State#PRESENT}, otherwise empty; a malformed value is
         *         never retained, so it cannot be returned
         */
        public Optional<String> token() {
            return Optional.ofNullable(token);
        }

        /**
         * Prints the state only. The token is a credential and a malformed value is untrusted text, so
         * neither is ever part of the string.
         *
         * @return a description naming the state
         */
        @Override
        public String toString() {
            return "BearerCredentials.Result[state=" + state + "]";
        }
    }

    /**
     * Extracts the {@code Bearer} credentials from one {@code Authorization} field value.
     *
     * @param authorizationValue the field value, may be {@code null} when the field is missing
     * @return the result, never {@code null}
     */
    public static Result extract(@Nullable String authorizationValue) {
        if (authorizationValue == null) {
            return ABSENT_RESULT;
        }
        int length = authorizationValue.length();
        int start = 0;
        while (start < length && isOptionalWhitespace(authorizationValue.charAt(start))) {
            start++;
        }
        int afterScheme = start + SCHEME.length();
        if (afterScheme >= length
                || !authorizationValue.regionMatches(true, start, SCHEME, 0, SCHEME.length())
                || authorizationValue.charAt(afterScheme) != SPACE) {
            return ABSENT_RESULT;
        }
        int tokenStart = afterScheme;
        while (tokenStart < length && authorizationValue.charAt(tokenStart) == SPACE) {
            tokenStart++;
        }
        int tokenEnd = length;
        while (tokenEnd > tokenStart && isOptionalWhitespace(authorizationValue.charAt(tokenEnd - 1))) {
            tokenEnd--;
        }
        if (tokenStart == tokenEnd) {
            return MALFORMED_EMPTY_RESULT;
        }
        for (int i = tokenStart; i < tokenEnd; i++) {
            if (isRefusedInToken(authorizationValue.charAt(i))) {
                return MALFORMED_CHARACTER_RESULT;
            }
        }
        return new Result(State.PRESENT, false, authorizationValue.substring(tokenStart, tokenEnd));
    }

    /**
     * Extracts the {@code Bearer} credentials from the first {@code Authorization} value of a header map.
     * <p>
     * The map is looked up with the lower-case key {@code authorization} first, the form HTTP/2 mandates
     * and most containers normalise to; when that key is missing, the first key that equals
     * {@code Authorization} ignoring case is used. Only the first value of the field is read; further
     * values are ignored. A missing field, an empty value list and a {@code null} first value are all
     * {@link State#ABSENT}.
     *
     * @param headers the request header map, field name to its values
     * @return the result, never {@code null}
     */
    public static Result extract(Map<String, List<String>> headers) {
        Objects.requireNonNull(headers, "headers");
        List<String> values = headers.get(AUTHORIZATION);
        if (values == null) {
            values = findCaseVariant(headers);
        }
        if (values == null || values.isEmpty()) {
            return ABSENT_RESULT;
        }
        return extract(values.getFirst());
    }

    private static @Nullable List<String> findCaseVariant(Map<String, List<String>> headers) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String name = entry.getKey();
            if (AUTHORIZATION.equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean isOptionalWhitespace(char c) {
        return c == SPACE || c == HORIZONTAL_TAB;
    }

    private static boolean isRefusedInToken(char c) {
        return c == SPACE || c < 0x20 || (c >= DELETE && c <= LAST_C1_CONTROL);
    }
}
