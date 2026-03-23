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
package de.cuioss.sheriff.oauth.core.pipeline.validator;

import de.cuioss.sheriff.oauth.core.JWTValidationLogMessages;
import de.cuioss.sheriff.oauth.core.domain.claim.ClaimName;
import de.cuioss.sheriff.oauth.core.domain.token.TokenContent;
import de.cuioss.sheriff.oauth.core.exception.TokenValidationException;
import de.cuioss.sheriff.oauth.core.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * Validator for JWT authorized party (azp) claims.
 * <p>
 * This class validates the authorized party claim which is used to prevent client confusion attacks
 * where tokens issued for one client are used with a different client.
 * <p>
 * The azp claim identifies the client that the token was issued for and must match
 * one of the expected client IDs if client ID validation is configured.
 *
 * @apiNote This class is internal to OAuth Sheriff and not part of the public API.
 * @since 1.0
 * @author Oliver Wolff
 */
@RequiredArgsConstructor
public class AuthorizedPartyValidator {

    private static final CuiLogger LOGGER = new CuiLogger(AuthorizedPartyValidator.class);


    private final Set<String> expectedClientId;


    private final SecurityEventCounter securityEventCounter;

    /**
     * Validates the authorized party claim.
     * <p>
     * The "azp" (authorized party) claim identifies the client that the token was issued for.
     * This claim is used to prevent client confusion attacks where tokens issued for one client
     * are used with a different client.
     * <p>
     * If the expected client ID is provided, this method checks if the token's azp claim
     * matches the expected client ID.
     * <p>
     * If the azp claim is missing but expected client ID is provided, the validation fails.
     *
     * @param token the JWT claims
     * @throws TokenValidationException if the authorized party is invalid
     */
    public void validateAuthorizedParty(TokenContent token) {
        if (expectedClientId.isEmpty()) {
            LOGGER.debug("No expectedClientId configured to check against");
            return;
        }

        var azpObj = token.getClaimOption(ClaimName.AUTHORIZED_PARTY);
        if (azpObj.isEmpty() || azpObj.get().isEmpty()) {
            LOGGER.warn(JWTValidationLogMessages.WARN.MISSING_CLAIM, ClaimName.AUTHORIZED_PARTY.getName());
            securityEventCounter.increment(SecurityEventCounter.EventType.MISSING_CLAIM);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.MISSING_CLAIM,
                    "Missing required authorized party (azp) claim"
            );
        }

        String azp = azpObj.get().getOriginalString();
        if (!expectedClientId.contains(azp)) {
            LOGGER.warn(JWTValidationLogMessages.WARN.AZP_MISMATCH, azp, expectedClientId);
            securityEventCounter.increment(SecurityEventCounter.EventType.AZP_MISMATCH);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.AZP_MISMATCH,
                    "Authorized party mismatch: token azp '%s' does not match any expected client ID %s".formatted(azp, expectedClientId)
            );
        }
        LOGGER.debug("Successfully validated authorized party: %s", azp);
    }
}