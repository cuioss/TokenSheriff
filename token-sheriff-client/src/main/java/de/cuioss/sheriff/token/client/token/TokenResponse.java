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
package de.cuioss.sheriff.token.client.token;

import com.dslplatform.json.CompiledJson;
import com.dslplatform.json.JsonAttribute;

import java.util.Optional;

/**
 * The normalized OAuth 2.0 token endpoint response (RFC 6749 §5.1).
 * <p>
 * Produced by {@code flow.TokenEndpointClient} from a successful token-endpoint POST. Mandatory
 * fields ({@code access_token}, {@code token_type}) are always present on a success response;
 * {@code refresh_token}, {@code id_token} and {@code scope} are optional and grant-dependent
 * (a {@code client_credentials} grant, for example, returns neither a refresh token nor an ID
 * token).
 * <p>
 * Parsing uses DSL-JSON's compile-time {@code @CompiledJson} mapping. Fields are {@code public}
 * because DSL-JSON class-based deserialization requires it; after deserialization the object is
 * treated as read-only and callers should use the accessor methods.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-5.1">RFC 6749 §5.1 - Successful Response</a>
 */
@CompiledJson
@SuppressWarnings("java:S1104") // Public fields required by DSL-JSON @CompiledJson for class-based deserialization
public class TokenResponse {

    /** The issued access token. */
    @JsonAttribute(name = "access_token")
    public String accessToken;

    /** The token type (typically {@code Bearer} or {@code DPoP}). */
    @JsonAttribute(name = "token_type")
    public String tokenType;

    /** The access token lifetime in seconds, or {@code 0} when the AS omits it. */
    @JsonAttribute(name = "expires_in")
    public long expiresIn;

    /** The optional refresh token. */
    @JsonAttribute(name = "refresh_token")
    public String refreshToken;

    /** The optional ID token (OpenID Connect). */
    @JsonAttribute(name = "id_token")
    public String idToken;

    /** The optional granted scope, when it differs from the requested scope. */
    public String scope;

    /**
     * @return the issued access token, or {@link Optional#empty()} if absent
     */
    public Optional<String> getAccessToken() {
        return Optional.ofNullable(accessToken);
    }

    /**
     * @return the token type, or {@link Optional#empty()} if absent
     */
    public Optional<String> getTokenType() {
        return Optional.ofNullable(tokenType);
    }

    /**
     * @return the access token lifetime in seconds ({@code 0} when omitted)
     */
    public long getExpiresIn() {
        return expiresIn;
    }

    /**
     * @return the refresh token, or {@link Optional#empty()} if absent
     */
    public Optional<String> getRefreshToken() {
        return Optional.ofNullable(refreshToken);
    }

    /**
     * @return the ID token, or {@link Optional#empty()} if absent
     */
    public Optional<String> getIdToken() {
        return Optional.ofNullable(idToken);
    }

    /**
     * @return the granted scope, or {@link Optional#empty()} if absent
     */
    public Optional<String> getScope() {
        return Optional.ofNullable(scope);
    }
}
