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
package de.cuioss.sheriff.token.client.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * Decides whether a value is a bare back-channel origin ({@code scheme://host[:port]}).
 * <p>
 * {@link #refusal(String)} accepts a well-formed absolute {@code http}/{@code https} URL with a host
 * and nothing beyond the origin: no user-info, no query, no fragment and an empty path — a single
 * trailing {@code /} is accepted. A value carrying a control character or a character outside the
 * URI grammar does not parse, and a host the URI grammar cannot read as a server-based authority
 * (a Unicode host name, for example) has no host; both are refused.
 * <p>
 * Every refusal is one of the fixed texts declared here. None echoes the refused value — it may carry
 * a credential in its user-info — and none carries the parser's reason or error index. It is the one
 * rule {@link ClientConfiguration#getBackChannelOrigin()} is validated against, so any other entry
 * point (a configuration mapper, for example) accepts and refuses exactly the same values. The class
 * is stateless and safe for concurrent use.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BackChannelOriginValidator {

    /** Refusal text for a {@code null} value. */
    static final String NULL_ORIGIN = "backChannelOrigin must not be null";

    /** Refusal text for a blank value. */
    static final String BLANK_ORIGIN = "backChannelOrigin must not be blank when provided";

    /** Refusal text for a value that does not parse as a URL. */
    static final String UNPARSABLE_URL = "backChannelOrigin must be a valid absolute URL";

    /** Refusal text for a relative URL, a scheme other than http/https, or a URL without a host. */
    static final String NOT_HTTP_ORIGIN = "backChannelOrigin must be an absolute http(s) URL with a host";

    /** Refusal text for a URL carrying user-info. */
    static final String USER_INFO = "backChannelOrigin must not carry user-info";

    /** Refusal text for a URL carrying a query. */
    static final String QUERY = "backChannelOrigin must not carry a query";

    /** Refusal text for a URL carrying a fragment. */
    static final String FRAGMENT = "backChannelOrigin must not carry a fragment";

    /** Refusal text for a URL carrying a path other than a single {@code /}. */
    static final String PATH = "backChannelOrigin must be an origin without a path";

    /**
     * Checks whether a value is a bare absolute {@code http}/{@code https} origin.
     *
     * @param origin the value to check; may be {@code null}
     * @return empty when the value is a bare origin (optionally ending in a single {@code /}),
     *         otherwise the fixed refusal text; the text never contains the value
     */
    public static Optional<String> refusal(@Nullable String origin) {
        if (origin == null) {
            return Optional.of(NULL_ORIGIN);
        }
        if (origin.isBlank()) {
            return Optional.of(BLANK_ORIGIN);
        }
        final URI uri;
        try {
            uri = new URI(origin);
        } catch (URISyntaxException e) {
            // Neither the exception nor its reason or index is propagated: the message repeats the
            // input verbatim, and the reason and index locate content inside it.
            return Optional.of(UNPARSABLE_URL);
        }
        String scheme = uri.getScheme();
        if (!uri.isAbsolute() || uri.getHost() == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return Optional.of(NOT_HTTP_ORIGIN);
        }
        if (uri.getRawUserInfo() != null) {
            return Optional.of(USER_INFO);
        }
        if (uri.getRawQuery() != null) {
            return Optional.of(QUERY);
        }
        if (uri.getRawFragment() != null) {
            return Optional.of(FRAGMENT);
        }
        String path = uri.getRawPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            return Optional.of(PATH);
        }
        return Optional.empty();
    }
}
