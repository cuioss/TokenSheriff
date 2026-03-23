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
import de.cuioss.sheriff.oauth.core.json.MapRepresentation;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.util.Map;

/**
 * Base implementation of {@link TokenContent}.
 * <p>
 * This abstract class provides common functionality for token content implementations,
 * including storage of claims, the raw token string, and token type. It serves as
 * the foundation for specific token type implementations like:
 * <ul>
 *   <li>{@link AccessTokenContent} - For OAuth2/OIDC access tokens</li>
 *   <li>{@link IdTokenContent} - For OIDC ID tokens</li>
 *   <li>{@link RefreshTokenContent} - For OAuth2 refresh tokens</li>
 * </ul>
 * <p>
 * The class is immutable and thread-safe, implementing equality and string representation
 * via Lombok's {@code @EqualsAndHashCode} and {@code @ToString}.
 * <p>
 * For more details on token structure, see the
 * <a href="https://github.com/cuioss/OAuthSheriff/tree/main/doc/architecture.adoc#token-structure">Token Structure</a>
 * specification.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@ToString
@EqualsAndHashCode
@SuperBuilder
public abstract sealed class BaseTokenContent implements TokenContent
        permits AccessTokenContent, IdTokenContent {

    @Serial
    private static final long serialVersionUID = 1L;

    @Getter
    private final Map<String, ClaimValue> claims;

    @Getter
    @ToString.Exclude
    private final String rawToken;

    @Getter
    private final TokenType tokenType;

    /**
     * Raw JSON payload as Map representation for ClaimMapper processing.
     * This contains the original deserialized JSON structure from DSL-JSON
     * before it's processed into typed ClaimValue objects.
     */
    @Getter
    private final MapRepresentation rawPayload;


    /**
     * Constructor for BaseTokenContent.
     *
     * @param claims     the token claims
     * @param rawToken   the raw token string
     * @param tokenType  the token type
     * @param rawPayload the raw JSON payload for ClaimMapper processing
     */
    protected BaseTokenContent(Map<String, ClaimValue> claims, String rawToken,
            TokenType tokenType, MapRepresentation rawPayload) {
        this.claims = claims;
        this.rawToken = rawToken;
        this.tokenType = tokenType;
        this.rawPayload = rawPayload;
    }
}
