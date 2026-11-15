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

import java.util.Collection;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Builds the {@code WWW-Authenticate} field value of an RFC 6750 §3.1 {@code insufficient_scope} challenge.
 * <p>
 * The value has the form
 * {@code Bearer realm="...", error="insufficient_scope", error_description="...", scope="..."}, where
 * {@code realm}, {@code error_description} and {@code scope} are optional and appear only when set. The
 * builder returns a plain {@link String}; it never decides an HTTP status, so the caller pairs the value
 * with the status its own contract prescribes (RFC 6750 recommends 403, an integration may answer 401).
 * <p>
 * Inputs are checked so that a caller-supplied value can neither split the header nor add a parameter,
 * and so that the output is always a value the sibling client parser
 * ({@code InsufficientScopeChallengeParser}) accepts:
 * <ul>
 *   <li>Scopes are sorted and duplicates are collapsed; an empty scope set omits the {@code scope}
 *       attribute. Every scope name must match the RFC 6749 §3.3 {@code scope-token} grammar
 *       ({@code %x21 / %x23-5B / %x5D-7E}, at least one character); any other name - one holding a space,
 *       a quote, a backslash, a control character or a non-ASCII character, or an empty name - is refused
 *       with {@link IllegalArgumentException}, never escaped.</li>
 *   <li>{@code realm} and {@code error_description} are written as RFC 9110 §5.6.4 quoted strings:
 *       {@code "} and {@code \} are escaped with a backslash. A value holding CR, LF, NUL, any other C0
 *       control character (horizontal tab included) or DEL is refused with
 *       {@link IllegalArgumentException}.</li>
 *   <li>At most {@value #MAX_SCOPE_TOKENS} distinct scope tokens and at most {@value #MAX_LENGTH}
 *       characters of output are produced, matching the bounds the client parser reads; a challenge
 *       beyond either is refused with {@link IllegalArgumentException} when it is built.</li>
 * </ul>
 * <p>
 * The class is framework-neutral (it depends on {@code java.util} and JSpecify only). The static entry
 * point is stateless and thread-safe; a {@link Builder} is a mutable, single-use object that must not be
 * shared between threads.
 * <p>
 * Example:
 * {@snippet :
 * String wwwAuthenticate = BearerChallenge.insufficientScope()
 *         .realm("protected-resource")
 *         .errorDescription("The request requires higher privileges than provided by the access token")
 *         .scopes(Set.of("write", "read"))
 *         .build();
 * // Bearer realm="protected-resource", error="insufficient_scope",
 * //     error_description="The request requires higher privileges than provided by the access token",
 * //     scope="read write"
 * }
 *
 * @since 1.0
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6750#section-3.1">RFC 6750 §3.1</a>
 */
public final class BearerChallenge {

    /** Largest number of distinct {@code scope} tokens a challenge may carry. */
    public static final int MAX_SCOPE_TOKENS = 64;

    /** Longest challenge value produced, in characters. */
    public static final int MAX_LENGTH = 8192;

    /** The RFC 6750 error code of the challenge. */
    public static final String INSUFFICIENT_SCOPE = "insufficient_scope";

    private static final String SCHEME = "Bearer";

    private static final char DELETE = 0x007F;

    private BearerChallenge() {
        // utility class - never instantiated
    }

    /**
     * Starts an {@code insufficient_scope} challenge.
     *
     * @return a new, empty builder
     */
    public static Builder insufficientScope() {
        return new Builder();
    }

    /**
     * Collects the optional attributes of one {@code insufficient_scope} challenge. Each setter checks its
     * argument immediately; {@link #build()} checks the bounds of the whole value.
     *
     * @since 1.0
     */
    public static final class Builder {

        private @Nullable String realm;

        private @Nullable String errorDescription;

        private final SortedSet<String> scopes = new TreeSet<>();

        private Builder() {
        }

        /**
         * @param realm the protection realm, or {@code null} to omit the attribute
         * @return this builder
         * @throws IllegalArgumentException if {@code realm} holds CR, LF, NUL, another C0 control
         *                                  character or DEL
         */
        public Builder realm(@Nullable String realm) {
            this.realm = checkQuotable(realm, "realm");
            return this;
        }

        /**
         * @param errorDescription the human-readable {@code error_description}, or {@code null} to omit the
         *                         attribute
         * @return this builder
         * @throws IllegalArgumentException if {@code errorDescription} holds CR, LF, NUL, another C0 control
         *                                  character or DEL
         */
        public Builder errorDescription(@Nullable String errorDescription) {
            this.errorDescription = checkQuotable(errorDescription, "error_description");
            return this;
        }

        /**
         * Adds one required scope.
         *
         * @param scope the scope name
         * @return this builder
         * @throws IllegalArgumentException if {@code scope} is not an RFC 6749 §3.3 {@code scope-token}
         */
        public Builder scope(String scope) {
            Objects.requireNonNull(scope, "scope");
            if (!isScopeToken(scope)) {
                throw new IllegalArgumentException(
                        "Scope name is not an RFC 6749 section 3.3 scope-token (length " + scope.length() + ")");
            }
            scopes.add(scope);
            return this;
        }

        /**
         * Adds every given required scope.
         *
         * @param requiredScopes the scope names
         * @return this builder
         * @throws IllegalArgumentException if any name is not an RFC 6749 §3.3 {@code scope-token}
         */
        public Builder scopes(Collection<String> requiredScopes) {
            Objects.requireNonNull(requiredScopes, "requiredScopes");
            for (String scope : requiredScopes) {
                scope(scope);
            }
            return this;
        }

        /**
         * Builds the {@code WWW-Authenticate} field value.
         *
         * @return the challenge value, never {@code null}
         * @throws IllegalArgumentException if more than {@value BearerChallenge#MAX_SCOPE_TOKENS} distinct
         *                                  scopes were added or the value would be longer than
         *                                  {@value BearerChallenge#MAX_LENGTH} characters
         */
        public String build() {
            if (scopes.size() > MAX_SCOPE_TOKENS) {
                throw new IllegalArgumentException("A challenge carries at most " + MAX_SCOPE_TOKENS
                        + " scope tokens, got " + scopes.size());
            }
            StringBuilder value = new StringBuilder(SCHEME).append(' ');
            if (realm != null) {
                appendQuoted(value, "realm", realm).append(", ");
            }
            appendQuoted(value, "error", INSUFFICIENT_SCOPE);
            if (errorDescription != null) {
                appendQuoted(value.append(", "), "error_description", errorDescription);
            }
            if (!scopes.isEmpty()) {
                appendQuoted(value.append(", "), "scope", String.join(" ", scopes));
            }
            if (value.length() > MAX_LENGTH) {
                throw new IllegalArgumentException("A challenge is at most " + MAX_LENGTH
                        + " characters long, got " + value.length());
            }
            return value.toString();
        }
    }

    private static @Nullable String checkQuotable(@Nullable String value, String attribute) {
        if (value == null) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == DELETE) {
                throw new IllegalArgumentException(
                        "The " + attribute + " value holds a control character at index " + i);
            }
        }
        return value;
    }

    private static StringBuilder appendQuoted(StringBuilder target, String name, String value) {
        target.append(name).append("=\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                target.append('\\');
            }
            target.append(c);
        }
        return target.append('"');
    }

    /**
     * @return whether {@code token} is non-empty and every character is in {@code %x21 / %x23-5B / %x5D-7E}
     */
    private static boolean isScopeToken(String token) {
        if (token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            boolean allowed = c == 0x21 || (c >= 0x23 && c <= 0x5B) || (c >= 0x5D && c <= 0x7E);
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
