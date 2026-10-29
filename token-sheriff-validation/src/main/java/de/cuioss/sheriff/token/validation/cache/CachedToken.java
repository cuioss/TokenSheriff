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
package de.cuioss.sheriff.token.validation.cache;

import de.cuioss.sheriff.token.validation.domain.context.ValidationContext;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Immutable wrapper for cached access tokens.
 * <p>
 * This class encapsulates a validated access token along with its raw string representation,
 * its expiration time, and the time policy of the issuer that validated it. The raw token is
 * stored to verify cache hits against the original token string, preventing false positives
 * from hash collisions.
 * <p>
 * The issuer time policy — the issuer's own clock skew tolerance and its maximum token age —
 * is captured at store time, so that every cache hit and every background eviction evaluates
 * the entry against exactly the policy the uncached validation pipeline would apply at the
 * same instant:
 * <ul>
 *   <li>{@link #isExpired(OffsetDateTime)} applies the issuer's clock skew to the {@code exp} claim
 *   via {@link ValidationContext#isExpired(OffsetDateTime, int, OffsetDateTime)}</li>
 *   <li>{@link #isTooOld(OffsetDateTime)} applies the issuer's maximum token age (plus clock skew)
 *   to the {@code iat} claim via {@link ValidationContext#isTokenTooOld(OffsetDateTime)}, and is
 *   disabled when the issuer configures no maximum token age</li>
 * </ul>
 * <p>
 * The expiration time is cached to enable efficient background eviction of expired tokens
 * without needing to parse the token content.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@RequiredArgsConstructor
@Builder
@Getter
public final class CachedToken {

    /**
     * The raw JWT token string as received from the client.
     * Used to verify cache hits by comparing against the queried token.
     */
    private final String rawToken;

    /**
     * The validated access token content.
     * This is the result of successful JWT validation and parsing.
     */
    private final AccessTokenContent content;

    /**
     * The expiration time of the token.
     * Used for efficient expiration checks and background eviction.
     */
    private final OffsetDateTime expirationTime;

    /**
     * The clock skew tolerance in seconds of the issuer that validated this token.
     * Applied to both the expiry and the token-age check.
     */
    private final int clockSkewSeconds;

    /**
     * The maximum token age in seconds of the issuer that validated this token,
     * or {@code null} if the issuer has token age validation disabled.
     */
    private final Integer maxTokenAgeSeconds;

    /**
     * Checks if this cached token has expired under its issuer's clock skew tolerance.
     *
     * @param currentTime the current time to check against
     * @return true if the token has expired even with the issuer's clock skew tolerance, false otherwise
     */
    public boolean isExpired(OffsetDateTime currentTime) {
        return ValidationContext.isExpired(expirationTime, clockSkewSeconds, currentTime);
    }

    /**
     * Checks if this cached token exceeds its issuer's maximum token age.
     * <p>
     * A token is too old when {@code currentTime - iat > maxTokenAgeSeconds + clockSkewSeconds}.
     * When the issuer has token age validation disabled ({@code maxTokenAgeSeconds == null}),
     * this check never rejects.
     *
     * @param currentTime the current time to check against
     * @return true if token age validation is enabled for the issuer and the token is too old, false otherwise
     */
    public boolean isTooOld(OffsetDateTime currentTime) {
        ValidationContext policy = new ValidationContext(currentTime, clockSkewSeconds, maxTokenAgeSeconds);
        return policy.isTokenAgeValidationEnabled() && policy.isTokenTooOld(content.getIssuedAtDateTime());
    }

    /**
     * Verifies that the provided token matches the cached raw token.
     * This prevents false cache hits from hash collisions.
     * 
     * @param tokenToVerify the token string to verify
     * @return true if the tokens match, false otherwise
     */
    public boolean verifyToken(String tokenToVerify) {
        return rawToken.equals(tokenToVerify);
    }
}