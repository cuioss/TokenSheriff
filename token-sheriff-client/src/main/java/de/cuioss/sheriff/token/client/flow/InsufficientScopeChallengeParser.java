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

import de.cuioss.sheriff.token.client.internal.LogSanitizer;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Parses an RFC 6750 §3.1 {@code insufficient_scope} challenge from a resource server's
 * {@code WWW-Authenticate} response header.
 * <p>
 * A resource server that rejects an access token because it lacks a scope responds with
 * {@code WWW-Authenticate: Bearer error="insufficient_scope"} and may name the scopes the request
 * needs, a {@code realm} and an {@code error_description}. This parser reads that challenge into an
 * {@link InsufficientScopeChallenge} the caller can report or act on.
 * <p>
 * Only a value starting with the {@code Bearer} scheme (case-insensitive) whose {@code error} is exactly
 * {@code insufficient_scope} parses. Every other value yields {@link Optional#empty()}, and so does a
 * value the fail-closed header guard refuses: longer than 8192 characters, carrying {@code CR},
 * {@code LF}, {@code NUL}, any other control character or {@code DEL} (horizontal tab is tolerated as
 * optional whitespace), followed by another scheme, carrying a valueless segment or an empty
 * {@code auth-param} name, naming an {@code auth-param} twice, or carrying an unterminated quoted value
 * or a stray backslash. The {@code scope} value is split on single spaces; empty tokens are dropped and
 * duplicates collapsed in first-seen order. Every token must match the RFC 6749 §3.3 {@code scope-token}
 * grammar ({@code %x21 / %x23-5B / %x5D-7E}) and at most {@value #MAX_SCOPE_TOKENS} tokens are read;
 * beyond either the whole challenge yields {@link Optional#empty()}. An absent or blank {@code scope}
 * yields a challenge with an empty scope set. The {@code error_description} is escaped and bounded by
 * {@link LogSanitizer} before it is stored, and it is never logged.
 * <p>
 * Usage example:
 * {@snippet :
 * InsufficientScopeChallengeParser parser = new InsufficientScopeChallengeParser();
 * Optional<InsufficientScopeChallenge> challenge = parser.parse(
 *     "Bearer realm=\"api\", error=\"insufficient_scope\", scope=\"orders:read orders:write\"");
 * challenge.ifPresent(c -> {
 *     Set<String> required = c.scopes();          // [orders:read, orders:write]
 *     Optional<String> realm = c.getRealm();      // Optional[api]
 * });
 * }
 * <p>
 * This parser does not refresh or re-request a token, does not widen or request any scope, performs no
 * I/O, keeps no state between calls, and does not parse the {@code invalid_token} or
 * {@code invalid_request} challenges of RFC 6750 §3.1. Acting on the challenge is the caller's decision.
 * The parser is stateless and safe for concurrent use.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6750#section-3.1">RFC 6750 §3.1 - Error Codes</a>
 */
public final class InsufficientScopeChallengeParser {

    /** The RFC 6750 error code that signals the access token lacks a required scope. */
    public static final String INSUFFICIENT_SCOPE = "insufficient_scope";

    /** Largest number of {@code scope} tokens a challenge may carry. */
    public static final int MAX_SCOPE_TOKENS = 64;

    private static final CuiLogger LOGGER = new CuiLogger(InsufficientScopeChallengeParser.class);

    /**
     * Parses the {@code WWW-Authenticate} header value into an {@code insufficient_scope} challenge.
     *
     * @param wwwAuthenticate the raw header value, or {@code null}
     * @return the parsed challenge, or {@link Optional#empty()} if the header is absent, refused by the
     *         header guard, not an {@code insufficient_scope} Bearer challenge, or carries a {@code scope}
     *         outside the {@code scope-token} grammar or with more than {@value #MAX_SCOPE_TOKENS} tokens
     */
    public Optional<InsufficientScopeChallenge> parse(@Nullable String wwwAuthenticate) {
        Optional<Map<String, String>> parsed = BearerChallengeParams.parse(wwwAuthenticate,
                BearerChallengeParams.Mode.STRICT);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> params = parsed.get();
        if (!INSUFFICIENT_SCOPE.equals(params.get("error"))) {
            return Optional.empty();
        }
        Optional<Set<String>> scopes = parseScopes(params.get("scope"));
        if (scopes.isEmpty()) {
            return Optional.empty();
        }
        String errorDescription = LogSanitizer.sanitize(params.get("error_description"));
        return Optional.of(new InsufficientScopeChallenge(scopes.get(), params.get("realm"), errorDescription));
    }

    private static Optional<Set<String>> parseScopes(@Nullable String scope) {
        Set<String> scopes = new LinkedHashSet<>();
        if (scope == null || scope.isBlank()) {
            return Optional.of(scopes);
        }
        int tokenCount = 0;
        for (String token : scope.split(" ")) {
            if (token.isEmpty()) {
                continue;
            }
            tokenCount++;
            if (tokenCount > MAX_SCOPE_TOKENS) {
                LOGGER.debug("Ignoring insufficient_scope challenge with more than %s scope tokens",
                        MAX_SCOPE_TOKENS);
                return Optional.empty();
            }
            if (!isScopeToken(token)) {
                LOGGER.debug("Ignoring insufficient_scope challenge with a scope token outside RFC 6749 §3.3");
                return Optional.empty();
            }
            scopes.add(token);
        }
        return Optional.of(scopes);
    }

    /**
     * @return whether every character of {@code token} is in {@code %x21 / %x23-5B / %x5D-7E}
     */
    private static boolean isScopeToken(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            boolean allowed = c == 0x21 || (c >= 0x23 && c <= 0x5B) || (c >= 0x5D && c <= 0x7E);
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /**
     * A parsed RFC 6750 {@code insufficient_scope} challenge.
     *
     * @param scopes           the scopes the resource server named, in first-seen order without
     *                         duplicates; empty when the challenge named none, never {@code null}
     * @param realm            the protection realm, or {@code null} when absent
     * @param errorDescription the {@code error_description}, escaped and bounded by {@link LogSanitizer},
     *                         or {@code null} when absent
     */
    public record InsufficientScopeChallenge(Set<String> scopes, @Nullable String realm,
    @Nullable String errorDescription) {

        /**
         * Stores an unmodifiable, insertion-ordered copy of {@code scopes}.
         *
         * @throws NullPointerException if {@code scopes} is {@code null}
         */
        public InsufficientScopeChallenge {
            Objects.requireNonNull(scopes, "scopes");
            scopes = Collections.unmodifiableSet(new LinkedHashSet<>(scopes));
        }

        /**
         * @return the protection realm, if the challenge named one
         */
        public Optional<String> getRealm() {
            return Optional.ofNullable(realm);
        }

        /**
         * @return the escaped and bounded {@code error_description}, if the challenge carried one
         */
        public Optional<String> getErrorDescription() {
            return Optional.ofNullable(errorDescription);
        }
    }
}
