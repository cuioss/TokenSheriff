/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.oauth.core.json;

import com.dslplatform.json.CompiledJson;
import com.dslplatform.json.JsonAttribute;

import java.util.Optional;

/**
 * Java representation of OpenID Connect Well-Known Configuration response.
 * <p>
 * This class represents the standard OpenID Connect Discovery 1.0 well-known configuration
 * document structure. It uses DSL-JSON's @CompiledJson for compile-time code generation,
 * enabling direct deserialization and optimal performance for native compilation.
 * <p>
 * Key fields from RFC 8414 and OpenID Connect Discovery:
 * <ul>
 *   <li><strong>issuer</strong>: Authorization server's identifier URL</li>
 *   <li><strong>jwks_uri</strong>: URL of the authorization server's JWK Set document</li>
 *   <li><strong>authorization_endpoint</strong>: URL of the authorization endpoint</li>
 *   <li><strong>token_endpoint</strong>: URL of the token endpoint</li>
 * </ul>
 * <p>
 * This implementation prioritizes the core fields needed for JWT validation while
 * maintaining extensibility for additional OpenID Connect discovery fields.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">OpenID Connect Discovery</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8414">RFC 8414 - OAuth 2.0 Authorization Server Metadata</a>
 */
@CompiledJson
public class WellKnownResult {

    @JsonAttribute(name = "issuer")
    public String issuer;

    @JsonAttribute(name = "jwks_uri")
    public String jwksUri;

    @JsonAttribute(name = "authorization_endpoint")
    public String authorizationEndpoint;

    @JsonAttribute(name = "token_endpoint")
    public String tokenEndpoint;

    @JsonAttribute(name = "userinfo_endpoint")
    public String userinfoEndpoint;

    // Default constructor for DSL-JSON
    public WellKnownResult() {
    }

    public WellKnownResult(String issuer, String jwksUri,
            String authorizationEndpoint, String tokenEndpoint, String userinfoEndpoint) {
        this.issuer = issuer;
        this.jwksUri = jwksUri;
        this.authorizationEndpoint = authorizationEndpoint;
        this.tokenEndpoint = tokenEndpoint;
        this.userinfoEndpoint = userinfoEndpoint;
    }

    public static final String ABOUT_EMPTY = "about:empty";
    /**
     * Sentinel value representing an empty/invalid well-known configuration.
     * <p>
     * This special value is used when HttpContentConverter needs to provide a non-null
     * empty value but no valid configuration can be created. It uses a recognizable
     * placeholder that clearly indicates this is not a real configuration.
     * <p>
     * <strong>This should never be used for actual OpenID Connect operations.</strong>
     */
    public static final WellKnownResult EMPTY = new WellKnownResult(
            ABOUT_EMPTY, // RFC 6694 - special URI for empty content
            ABOUT_EMPTY,
            null,
            null,
            null
    );

    public String issuer() {
        return issuer;
    }

    public String jwksUri() {
        return jwksUri;
    }

    public String authorizationEndpoint() {
        return authorizationEndpoint;
    }

    public String tokenEndpoint() {
        return tokenEndpoint;
    }

    public String userinfoEndpoint() {
        return userinfoEndpoint;
    }

    /**
     * Gets the issuer as Optional.
     *
     * @return Optional containing the issuer, empty if null
     */
    public Optional<String> getIssuer() {
        return Optional.ofNullable(issuer);
    }

    /**
     * Gets the JWKS URI as Optional.
     *
     * @return Optional containing the JWKS URI, empty if null
     */
    public Optional<String> getJwksUri() {
        return Optional.ofNullable(jwksUri);
    }

    /**
     * Gets the authorization endpoint as Optional.
     *
     * @return Optional containing the authorization endpoint, empty if null
     */
    public Optional<String> getAuthorizationEndpoint() {
        return Optional.ofNullable(authorizationEndpoint);
    }

    /**
     * Gets the token endpoint as Optional.
     *
     * @return Optional containing the token endpoint, empty if null
     */
    public Optional<String> getTokenEndpoint() {
        return Optional.ofNullable(tokenEndpoint);
    }

    /**
     * Gets the userinfo endpoint as Optional.
     *
     * @return Optional containing the userinfo endpoint, empty if null
     */
    public Optional<String> getUserinfoEndpoint() {
        return Optional.ofNullable(userinfoEndpoint);
    }

    /**
     * Checks if this configuration is the special empty sentinel value.
     *
     * @return true if this is the EMPTY sentinel value
     */
    public boolean isEmpty() {
        return this == EMPTY ||
                ABOUT_EMPTY.equals(issuer) ||
                (issuer == null && jwksUri == null);
    }

    /**
     * Checks if this configuration has all fields required for full OAuth 2.0 flows.
     * <p>
     * Returns true if both authorization_endpoint and token_endpoint are present,
     * indicating this server supports full authorization code flow.
     *
     * @return true if configuration supports full OAuth 2.0 flows
     */
    public boolean supportsFullOAuthFlows() {
        return authorizationEndpoint != null && tokenEndpoint != null;
    }

    /**
     * Checks if this configuration is minimal (contains only required JWT validation fields).
     * <p>
     * Returns true if only issuer and jwks_uri are present, which is sufficient
     * for JWT token validation scenarios.
     *
     * @return true if configuration contains only minimal JWT validation fields
     */
    public boolean isMinimal() {
        return authorizationEndpoint == null && tokenEndpoint == null;
    }
}