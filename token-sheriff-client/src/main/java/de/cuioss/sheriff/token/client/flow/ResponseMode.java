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

/**
 * The closed set of {@code response_mode} values an authorization request may carry.
 * <p>
 * {@link #FORM_POST} is the default and is what every request carries unless a caller opts into
 * {@link #QUERY} through {@link AuthorizationRequestOptions}. JARM modes and {@code fragment} are not
 * supported.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://openid.net/specs/oauth-v2-form-post-response-mode-1_0.html">OAuth 2.0 Form Post Response Mode</a>
 * @see <a href="https://openid.net/specs/oauth-v2-multiple-response-types-1_0.html">OAuth 2.0 Multiple Response Type Encoding Practices</a>
 */
public enum ResponseMode {

    /**
     * {@code form_post}: the authorization response — {@code code}, {@code state} and the RFC 9207
     * {@code iss} — is delivered in the body of a POST to the redirect URI, so it never appears in a URL
     * ({@code T-URL-LEAK} closed). This is the default.
     */
    FORM_POST("form_post"),

    /**
     * {@code query}: the authorization response is delivered as query parameters on the redirect URL.
     * <p>
     * <strong>Security consequence:</strong> {@code query} places {@code code}, {@code state} and
     * {@code iss} in the redirect URL, where they can leak into browser history, server and proxy logs
     * and the {@code Referer} header. It re-opens {@code T-URL-LEAK}, which {@link #FORM_POST} closes,
     * and leaves the mitigation to the caller. Choose it only when the redirect target cannot accept a
     * form POST.
     */
    QUERY("query");

    private final String wireValue;

    ResponseMode(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * @return the exact {@code response_mode} value sent on the wire
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Resolves a wire value to its constant, for a caller that maps configuration strings.
     *
     * @param value the {@code response_mode} wire value; must be an exact, case-sensitive match of a
     *              supported value
     * @return the matching constant
     * @throws IllegalArgumentException if {@code value} is {@code null}, blank, unsupported, in another
     *                                  letter case or carries a delimiter ({@code &}, {@code #},
     *                                  {@code =}, space, {@code CR}, {@code LF})
     */
    public static ResponseMode of(String value) {
        return WireValues.parse("response_mode", value, values(), ResponseMode::wireValue);
    }
}
