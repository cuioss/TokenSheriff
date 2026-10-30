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

/**
 * Joins an encoded query string to a front-channel endpoint URL such as the authorization endpoint or
 * the {@code end_session_endpoint} (C-4).
 * <p>
 * The query is inserted in front of any {@code #fragment} of the endpoint: parameters appended after
 * the {@code #} would be part of the fragment and never reach the authorization server. The separator
 * is chosen from the part before the fragment only, so a {@code ?} that occurs inside the fragment is
 * not mistaken for the start of an existing query. The fragment is kept unchanged at the end.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public final class EndpointQuery {

    private EndpointQuery() {
        // utility class — never instantiated
    }

    /**
     * Appends an already-encoded query to the endpoint, before any fragment.
     *
     * @param endpoint     the endpoint URL; must not be {@code null}
     * @param encodedQuery the {@code application/x-www-form-urlencoded} query to append, for example
     *                     the result of {@link FormEncoder#encode}; must not be {@code null}
     * @return the endpoint with the query joined by {@code ?} (no existing query) or {@code &} (an
     *         existing query), followed by the unchanged fragment, if any
     */
    public static String append(String endpoint, String encodedQuery) {
        int fragmentStart = endpoint.indexOf('#');
        String base = fragmentStart < 0 ? endpoint : endpoint.substring(0, fragmentStart);
        String fragment = fragmentStart < 0 ? "" : endpoint.substring(fragmentStart);
        return base + (base.indexOf('?') < 0 ? '?' : '&') + encodedQuery + fragment;
    }
}
