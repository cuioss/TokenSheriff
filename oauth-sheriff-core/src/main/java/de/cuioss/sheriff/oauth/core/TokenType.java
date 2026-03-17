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
package de.cuioss.sheriff.oauth.core;

import de.cuioss.sheriff.oauth.core.domain.claim.ClaimName;
import de.cuioss.tools.logging.CuiLogger;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import static de.cuioss.sheriff.oauth.core.domain.claim.ClaimName.*;

/**
 * Defines the supported token types within the authentication system.
 * Each type represents a specific OAuth2/OpenID Connect token category with its corresponding type claim name.
 * <p>
 * The supported token types are:
 * <ul>
 *   <li>{@link #ACCESS_TOKEN}: Standard OAuth2 access token with "Bearer" type claim</li>
 *   <li>{@link #ID_TOKEN}: OpenID Connect ID-Token with "ID" type claim</li>
 *   <li>{@link #REFRESH_TOKEN}: OAuth2 Refresh-Token with "Refresh" type claim</li>
 *   <li>{@link #UNKNOWN}: Fallback type for unrecognized or missing type claims</li>
 * </ul>
 * <p>
 * Implements requirements:
 * <ul>
 *   <li><a href="../../../../../../../../../doc/Requirements.adoc#OAUTH-SHERIFF-1.2">OAUTH-SHERIFF-1.2: Token Types</a></li>
 * </ul>
 * <p>
 * For more detailed specifications, see the
 * <a href="https://github.com/cuioss/OAuthSheriff/tree/main/doc/architecture.adoc#_token_architecture_and_types">Technical Components Specification - Token Architecture and Types</a>
 *
 * @author Oliver Wolff
 */
public enum TokenType {

    ACCESS_TOKEN("Bearer", new TreeSet<>(List.of(ISSUER, EXPIRATION, ISSUED_AT, SUBJECT))),
    ID_TOKEN("ID", new TreeSet<>(List.of(ISSUER, EXPIRATION, ISSUED_AT, SUBJECT, AUDIENCE))),
    REFRESH_TOKEN("Refresh", Collections.emptySortedSet()),
    UNKNOWN("unknown", Collections.emptySortedSet());

    private static final CuiLogger LOGGER = new CuiLogger(TokenType.class);

    @Getter
    private final String typeClaimName;
    @Getter
    private final SortedSet<ClaimName> mandatoryClaims;

    /**
     * Constructor for TokenType.
     *
     * @param typeClaimName the type claim name
     * @param mandatoryClaims the mandatory claims
     */
    TokenType(String typeClaimName, SortedSet<ClaimName> mandatoryClaims) {
        this.typeClaimName = typeClaimName;
        this.mandatoryClaims = mandatoryClaims;
    }


    /**
     * Resolves a TokenType from a type claim string value.
     * <p>
     * This method performs a case-insensitive comparison of the provided type claim name
     * against the known token types. If no match is found, it logs a warning and returns
     * the {@link #UNKNOWN} token type.
     *
     * @param typeClaimName the string value of the type claim, must not be null
     * @return the matching TokenType, or {@link #UNKNOWN} if no match is found
     */
    public static TokenType fromTypClaim(String typeClaimName) {
        for (TokenType tokenType : TokenType.values()) {
            if (tokenType.typeClaimName.equalsIgnoreCase(typeClaimName)) {
                return tokenType;
            }
        }
        LOGGER.warn(JWTValidationLogMessages.WARN.UNKNOWN_TOKEN_TYPE, typeClaimName);
        return UNKNOWN;
    }
}
