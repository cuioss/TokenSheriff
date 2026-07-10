/*
 * Copyright © 2022 CUI-OpenSource-Software (info@cuioss.de)
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

import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
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
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9470">RFC 9470 - OAuth 2.0 Step Up Authentication Challenge</a>
 */
public class StepUpChallengeParser {

    /** The RFC 9470 error code that signals a step-up is required. */
    public static final String INSUFFICIENT_USER_AUTHENTICATION = "insufficient_user_authentication";

    private static final CuiLogger LOGGER = new CuiLogger(StepUpChallengeParser.class);
    private static final String BEARER_PREFIX = "bearer ";

    /**
     * Parses the {@code WWW-Authenticate} header value into a step-up challenge.
     *
     * @param wwwAuthenticate the raw header value, or {@code null}
     * @return the parsed challenge, or {@link Optional#empty()} if the header is absent or is not an
     *         {@code insufficient_user_authentication} Bearer challenge
     */
    public Optional<StepUpChallenge> parse(@Nullable String wwwAuthenticate) {
        if (wwwAuthenticate == null || wwwAuthenticate.isBlank()) {
            return Optional.empty();
        }
        String trimmed = wwwAuthenticate.strip();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        Map<String, String> params = parseParams(trimmed.substring(BEARER_PREFIX.length()));
        if (!INSUFFICIENT_USER_AUTHENTICATION.equals(params.get("error"))) {
            return Optional.empty();
        }
        String acrValues = params.get("acr_values");
        Integer maxAge = parseMaxAge(params.get("max_age"));
        if (acrValues == null && maxAge == null) {
            LOGGER.debug("Ignoring step-up challenge without acr_values or max_age");
            return Optional.empty();
        }
        return Optional.of(new StepUpChallenge(acrValues, maxAge));
    }

    private static Map<String, String> parseParams(String paramList) {
        Map<String, String> params = new HashMap<>();
        for (String segment : paramList.split(",")) {
            String pair = segment.strip();
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = pair.substring(0, eq).strip().toLowerCase(Locale.ROOT);
            params.put(key, unquote(pair.substring(eq + 1).strip()));
        }
        return params;
    }

    private static @Nullable Integer parseMaxAge(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.strip());
        } catch (NumberFormatException e) {
            LOGGER.debug("Ignoring non-numeric step-up max_age '%s'", value);
            return null;
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
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
