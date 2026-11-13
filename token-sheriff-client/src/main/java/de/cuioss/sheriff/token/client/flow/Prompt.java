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
 * The closed set of OpenID Connect {@code prompt} values an authorization request may carry.
 * <p>
 * A request carries no {@code prompt} parameter unless a caller opts into one through
 * {@link AuthorizationRequestOptions}. Exactly one value is sent per request.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">OpenID Connect Core 1.0 §3.1.2.1</a>
 */
public enum Prompt {

    /** {@code none}: the authorization server must not display any authentication or consent page. */
    NONE("none"),

    /** {@code login}: the authorization server should prompt the end-user to re-authenticate. */
    LOGIN("login"),

    /** {@code consent}: the authorization server should prompt the end-user for consent. */
    CONSENT("consent"),

    /** {@code select_account}: the authorization server should prompt the end-user to select an account. */
    SELECT_ACCOUNT("select_account");

    private final String wireValue;

    Prompt(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * @return the exact {@code prompt} value sent on the wire
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Resolves a wire value to its constant, for a caller that maps configuration strings.
     *
     * @param value the {@code prompt} wire value; must be an exact, case-sensitive match of a supported
     *              value
     * @return the matching constant
     * @throws IllegalArgumentException if {@code value} is {@code null}, blank, unsupported, in another
     *                                  letter case or carries a delimiter ({@code &}, {@code #},
     *                                  {@code =}, space, {@code CR}, {@code LF})
     */
    public static Prompt of(String value) {
        return WireValues.parse("prompt", value, values(), Prompt::wireValue);
    }
}
