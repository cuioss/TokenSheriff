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
package de.cuioss.sheriff.oauth.core.domain.token;

import de.cuioss.sheriff.oauth.core.TokenType;
import de.cuioss.sheriff.oauth.core.domain.claim.ClaimValue;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serial;
import java.util.Map;

/**
 * Represents an <strong>unvalidated</strong> OAuth 2.0 Refresh Token container.
 * <p>
 * <strong>WARNING:</strong> The contents of this class have NOT been validated.
 * Refresh tokens are often opaque strings and cannot be verified by the client.
 * Never trust claims from this container for authorization decisions.
 * <p>
 * While most OAuth 2.0 implementations use opaque tokens for refresh tokens,
 * some authorization servers issue JWT-formatted refresh tokens. This class
 * provides a container for such tokens regardless of format.
 * <p>
 * Unlike {@link AccessTokenContent} and {@link IdTokenContent} which extend {@link BaseTokenContent},
 * this class implements {@link MinimalTokenContent} directly because:
 * <ul>
 *   <li>Refresh tokens often have minimal to no claims</li>
 *   <li>The structure can vary significantly between authorization servers</li>
 *   <li>Validation requirements are minimal for refresh tokens</li>
 * </ul>
 * <p>
 * This implementation follows guidance from:
 * <ul>
 *   <li><a href="https://datatracker.ietf.org/doc/html/rfc6749">RFC 6749 - OAuth 2.0</a></li>
 *   <li><a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC 7519 - JWT</a></li>
 * </ul>
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@ToString
@EqualsAndHashCode
public final class UnvalidatedRefreshToken implements MinimalTokenContent {

    @Serial
    private static final long serialVersionUID = 1L;

    @Getter
    private final String rawToken;

    private final Map<String, ClaimValue> claims;

    public UnvalidatedRefreshToken(String rawToken, Map<String, ClaimValue> claims) {
        this.rawToken = rawToken;
        this.claims = claims;
    }

    /**
     * Returns the claims extracted from the refresh token JWT, if applicable.
     * <p>
     * When the identity provider returns a JWT-formatted refresh token, this provides
     * access to the raw claims. These claims are <em>not</em> validated in any way.
     * <p>
     * Never {@code null}, but may be empty.
     *
     * @return unvalidated claims map (package-private access)
     */
    public Map<String, ClaimValue> getClaims() {
        return claims;
    }

    /**
     * Gets the token type.
     *
     * @return always TokenType.REFRESH_TOKEN
     */
    @Override
    public TokenType getTokenType() {
        return TokenType.REFRESH_TOKEN;
    }

}
