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

import java.util.Map;
import java.util.Optional;

/**
 * Parses an RFC 9470 step-up authentication challenge from a resource server's
 * {@code WWW-Authenticate} response header.
 * <p>
 * When a resource server rejects a request because the authentication is insufficient it responds
 * with {@code WWW-Authenticate: Bearer error="insufficient_user_authentication"} and — per RFC 9470
 * — the {@code acr_values} and/or {@code max_age} it requires. This parser extracts that challenge
 * so {@link StepUpHandler} can drive a fresh, stronger authorization request. Any header that is not
 * an {@code insufficient_user_authentication} Bearer challenge yields {@link Optional#empty()}.
 * <p>
 * The {@code auth-param} list is read by the header guard shared with
 * {@link InsufficientScopeChallengeParser}. Besides a value carrying {@code CR}, {@code LF} or
 * {@code NUL} and a duplicate {@code auth-param}, the guard refuses the whole value when it carries any
 * other control character or {@code DEL} (horizontal tab is tolerated), when it is longer than 8192
 * characters, and when the Bearer challenge is followed by another scheme or carries a valueless
 * segment (such as {@code , Basic, acr_values="x"} or {@code , realm}): such a segment is refused, not
 * skipped, so a parameter of a following challenge can never be read as a Bearer parameter. A segment
 * with an empty name ({@code =orphan}) is still skipped and an unterminated quoted value is still kept
 * verbatim.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9470">RFC 9470 - OAuth 2.0 Step Up Authentication Challenge</a>
 */
public class StepUpChallengeParser {

    /** The RFC 9470 error code that signals a step-up is required. */
    public static final String INSUFFICIENT_USER_AUTHENTICATION = "insufficient_user_authentication";

    private static final CuiLogger LOGGER = new CuiLogger(StepUpChallengeParser.class);

    /**
     * Parses the {@code WWW-Authenticate} header value into a step-up challenge.
     *
     * @param wwwAuthenticate the raw header value, or {@code null}
     * @return the parsed challenge, or {@link Optional#empty()} if the header is absent, carries a
     *         {@code CR}, {@code LF} or {@code NUL} anywhere (a split, folded or truncated field value
     *         is refused, never repaired), carries any other control character or {@code DEL} (horizontal
     *         tab excepted), is longer than 8192 characters, is followed by another scheme or carries a
     *         valueless segment, names an {@code auth-param} twice, or is not an
     *         {@code insufficient_user_authentication} Bearer challenge
     */
    public Optional<StepUpChallenge> parse(@Nullable String wwwAuthenticate) {
        Optional<Map<String, String>> parsed = BearerChallengeParams.parse(wwwAuthenticate,
                BearerChallengeParams.Mode.LENIENT);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> params = parsed.get();
        if (!INSUFFICIENT_USER_AUTHENTICATION.equals(params.get("error"))) {
            return Optional.empty();
        }
        // Blank-normalise acr_values exactly as parseMaxAge already does for max_age: a whitespace-only
        // value constrains nothing, so treating it as present would make the challenge actionable while
        // leaving StepUpResultVerifier with no constraint to check — verify() would then return normally
        // having verified nothing, and a caller reading that as proof of elevation would grant a
        // privileged operation on an unelevated authentication. Fail closed instead (CLIENT-12 / H1).
        String acrValues = blankToNull(params.get("acr_values"));
        Integer maxAge = parseMaxAge(params.get("max_age"));
        if (acrValues == null && maxAge == null) {
            LOGGER.debug("Ignoring step-up challenge without acr_values or max_age");
            return Optional.empty();
        }
        return Optional.of(new StepUpChallenge(acrValues, maxAge));
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static @Nullable Integer parseMaxAge(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.strip());
        } catch (NumberFormatException e) {
            LOGGER.debug("Ignoring non-numeric step-up max_age '%s'", LogSanitizer.sanitize(value));
            return null;
        }
    }

    /**
     * A parsed RFC 9470 step-up challenge: the required {@code acr_values} and/or {@code max_age}.
     *
     * @param acrValues the requested authentication context class values, or {@code null}
     * @param maxAge    the maximum authentication age in seconds, or {@code null}
     */
    public record StepUpChallenge(@Nullable
    String acrValues, @Nullable
    Integer maxAge) {

        /**
         * @return the requested {@code acr_values}, if the challenge constrained them
         */
        public Optional<String> getAcrValues() {
            return Optional.ofNullable(acrValues);
        }

        /**
         * @return the maximum authentication age in seconds, if the challenge constrained it
         */
        public Optional<Integer> getMaxAge() {
            return Optional.ofNullable(maxAge);
        }
    }
}
