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
import de.cuioss.sheriff.oauth.core.domain.context.ValidationContext;
import de.cuioss.sheriff.oauth.core.domain.token.TokenContent;
import de.cuioss.sheriff.oauth.core.exception.TokenValidationException;
import de.cuioss.sheriff.oauth.core.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import lombok.RequiredArgsConstructor;

/**
 * Validator for JWT expiration and time-based claims.
 * <p>
 * This class validates:
 * <ul>
 *   <li>Expiration time (exp) - tokens must not be expired</li>
 *   <li>Not before time (nbf) - tokens must not be used before their valid time</li>
 * </ul>
 * <p>
 * The validator includes a 60-second clock skew tolerance for the not-before validation
 * to account for time differences between token issuer and validator.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@RequiredArgsConstructor
public class ExpirationValidator {

    private static final CuiLogger LOGGER = new CuiLogger(ExpirationValidator.class);


    private final SecurityEventCounter securityEventCounter;

    /**
     * Validates that the token is not expired using the provided validation context.
     * <p>
     * This method eliminates synchronous OffsetDateTime.now() calls by using the cached
     * current time from the ValidationContext, significantly improving performance under
     * concurrent load.
     *
     * @param token the token to validate
     * @param context the validation context containing cached current time
     * @throws TokenValidationException if the token is expired
     */
    public void validateNotExpired(TokenContent token, ValidationContext context) {
        LOGGER.debug("validate expiration. Can be done directly, because %s", token);
        if (token.isExpired(context)) {
            LOGGER.warn(JWTValidationLogMessages.WARN.TOKEN_EXPIRED);
            securityEventCounter.increment(SecurityEventCounter.EventType.TOKEN_EXPIRED);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.TOKEN_EXPIRED,
                    "Token is expired. Current time: " + context.getCurrentTime() + " (with " + context.getClockSkewSeconds() + "s clock skew tolerance)"
            );
        }
        LOGGER.debug("Token is not expired");
    }

    /**
     * Validates the "not before time" claim using the provided validation context.
     * <p>
     * This method eliminates synchronous OffsetDateTime.now() calls by using the cached
     * current time from the ValidationContext, significantly improving performance under
     * concurrent load.
     * <p>
     * The "nbf" (not before) claim identifies the time before which the JWT must not be accepted for processing.
     * This claim is optional, so if it's not present, the validation passes.
     * <p>
     * If the claim is present, this method checks if the token's not-before time is more than the configured
     * clock skew seconds in the future. This window allows for clock skew between the token issuer and the token validator.
     * If the not-before time is more than the clock skew in the future, the token is considered invalid.
     * If the not-before time is in the past or within the clock skew tolerance, the token is considered valid.
     *
     * @param token the JWT claims
     * @param context the validation context containing cached current time
     * @throws TokenValidationException if the "not before" time is invalid
     */
    public void validateNotBefore(TokenContent token, ValidationContext context) {
        var notBefore = token.getNotBefore();
        if (notBefore.isEmpty()) {
            LOGGER.debug("Not before claim is optional, so if it's not present, validation passes");
            return;
        }

        if (context.isNotBeforeInvalid(notBefore.get())) {
            LOGGER.warn(JWTValidationLogMessages.WARN.TOKEN_NBF_FUTURE);
            securityEventCounter.increment(SecurityEventCounter.EventType.TOKEN_NBF_FUTURE);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.TOKEN_NBF_FUTURE,
                    "Token not valid yet: not before time is more than " + context.getClockSkewSeconds() + " seconds in the future. Not before time: " + notBefore.get() + ", Current time: " + context.getCurrentTime() + " (with " + context.getClockSkewSeconds() + "s clock skew tolerance)"
            );
        }
        LOGGER.debug("Not before claim is present, and not more than " + context.getClockSkewSeconds() + " seconds in the future");
    }
}