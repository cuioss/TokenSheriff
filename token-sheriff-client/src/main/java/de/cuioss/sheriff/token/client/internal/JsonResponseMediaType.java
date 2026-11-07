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

import java.net.http.HttpHeaders;
import java.util.List;

/**
 * The single {@code Content-Type} predicate every back-channel response the client parses must pass
 * before its body is read: discovery, token endpoint, PAR and userinfo.
 * <p>
 * A response carries a media type only when at least one {@code Content-Type} field line is present
 * and <em>every</em> one of them names that media type. The {@code type/subtype} part — everything
 * before the first {@code ;} — is compared case-insensitively after trimming, so parameters such as
 * {@code charset} are allowed. The predicate refuses:
 * <ul>
 *   <li>an absent {@code Content-Type};</li>
 *   <li>any other media type, a structured-syntax suffix such as {@code application/jrd+json}
 *       included;</li>
 *   <li>a response with a second, conflicting {@code Content-Type} line, rather than judging it by
 *       whichever line comes first;</li>
 *   <li>a value carrying {@code CR}, {@code LF} or {@code NUL}. An obs-fold continuation
 *       (RFC 9112 §5.2) is a {@code CRLF} followed by whitespace, so a value still carrying one is
 *       refused by the same rule, whatever its parameters.</li>
 * </ul>
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public final class JsonResponseMediaType {

    /** The {@code Content-Type} header name. */
    public static final String CONTENT_TYPE = "Content-Type";

    /** The JSON media type every JSON response the client parses must carry. */
    public static final String APPLICATION_JSON = "application/json";

    private JsonResponseMediaType() {
        // utility class — never instantiated
    }

    /**
     * @param headers the response headers; must not be {@code null}
     * @return whether every {@code Content-Type} line carries {@value #APPLICATION_JSON}, as described
     *         on the class
     */
    public static boolean isJson(HttpHeaders headers) {
        return carriesOnly(headers, APPLICATION_JSON);
    }

    /**
     * @param headers   the response headers; must not be {@code null}
     * @param mediaType the expected {@code type/subtype}, without parameters; must not be {@code null}
     * @return whether at least one {@code Content-Type} line is present and every one carries
     *         {@code mediaType}, compared as described on the class
     */
    public static boolean carriesOnly(HttpHeaders headers, String mediaType) {
        List<String> values = headers.allValues(CONTENT_TYPE);
        return !values.isEmpty() && values.stream()
                .allMatch(value -> !containsLineBreakOrNul(value)
                        && mediaType.equalsIgnoreCase(value.split(";", 2)[0].trim()));
    }

    private static boolean containsLineBreakOrNul(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\0') >= 0;
    }
}
