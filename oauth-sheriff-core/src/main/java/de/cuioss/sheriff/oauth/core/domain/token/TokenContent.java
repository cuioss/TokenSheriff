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

import de.cuioss.sheriff.oauth.core.IssuerConfig;
import de.cuioss.sheriff.oauth.core.domain.claim.ClaimName;
import de.cuioss.sheriff.oauth.core.domain.claim.ClaimValue;
import de.cuioss.sheriff.oauth.core.domain.context.ValidationContext;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Base interface for JWT Token content.
 * <p>
 * This interface defines the common contract for all JWT token objects,
 * providing structured access to token claims and metadata. It extends
 * {@link MinimalTokenContent} to include basic token information.
 * <p>
 * The interface provides:
 * <ul>
 *   <li>Access to all claims in the token via {@link #getClaims()}</li>
 *   <li>Convenience methods for common claims (subject, issuer, expiration time)</li>
 *   <li>Type-safe claim retrieval</li>
 *   <li>Expiration checking</li>
 * </ul>
 * <p>
 * JWT tokens implementing this interface follow the standards defined in
 * <a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC 7519</a> for claim names
 * and semantic behavior.
 * <p>
 * For more details on token structure, see the
 * <a href="https://github.com/cuioss/OAuthSheriff/tree/main/doc/architecture.adoc#token-structure">Token Structure</a>
 * specification.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public interface TokenContent extends MinimalTokenContent {

    /**
     * Gets all claims in this token.
     *
     * @return a map of claim names to claim objects
     */
   
    Map<String, ClaimValue> getClaims();

    /**
     * Gets a specific claim by name.
     *
     * @param name the claim name
     * @return an Optional containing the claim if present, or empty otherwise
     */
    default Optional<ClaimValue> getClaimOption(ClaimName name) {
        return Optional.ofNullable(getClaims().get(name.getName()));
    }

    /**
     * Gets the issuer claim value.
     * <p>
     * Since 'iss' is a mandatory claim, this method will never return null.
     *
     * @return the issuer
     * @throws IllegalStateException if the issuer claim is not present (should never happen
     *                               for a properly constructed validation)
     */
   
    default String getIssuer() {
        return getClaimOption(ClaimName.ISSUER)
                .map(ClaimValue::getOriginalString)
                .orElseThrow(() -> new IllegalStateException("Issuer claim not present in token"));
    }

    /**
     * Gets the subject claim value.
     * <p>
     * The 'sub' (subject) claim is required by RFC 7519 specification, but this implementation
     * allows it to be optional when the issuer configuration has claimSubOptional
     * set to {@code true}. This provides compatibility with token issuers like Keycloak
     * that may not include the subject claim in certain token types (e.g., access tokens).
     * <p>
     * <strong>Specification compliance:</strong>
     * <ul>
     *   <li>RFC 7519 requires the 'sub' claim to be present</li>
     *   <li>When {@code claimSubOptional=false} (default), this method returns a present Optional for RFC compliance</li>
     *   <li>When {@code claimSubOptional=true}, this method may return an empty Optional if the claim is missing</li>
     * </ul>
     *
     * @return an Optional containing the subject if present, or empty if not present and configured as optional
     * @see IssuerConfig.IssuerConfigBuilder#claimSubOptional(boolean)
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.2">RFC 7519 - 4.1.2. "sub" (Subject) Claim</a>
     */
    default Optional<String> getSubject() {
        return getClaimOption(ClaimName.SUBJECT)
                .map(ClaimValue::getOriginalString);
    }

    /**
     * Gets the expiration time claim value.
     * <p>
     * Since 'exp' is a mandatory claim, this method will never return null.
     *
     * @return the expiration time
     * @throws IllegalStateException if the expiration claim is not present (should never happen
     *                               for a properly constructed validation)
     */
   
    default OffsetDateTime getExpirationTime() {
        return getClaimOption(ClaimName.EXPIRATION)
                .map(ClaimValue::getDateTime)
                .orElseThrow(() -> new IllegalStateException("ExpirationTime claim not present in token"));
    }

    /**
     * Gets the issued at time claim value.
     * <p>
     * Since 'iat' is a mandatory claim, this method will never return null.
     *
     * @return the issued at time
     * @throws IllegalStateException if the issued at claim is not present (should never happen
     *                               for a properly constructed validation)
     */
   
    default OffsetDateTime getIssuedAtTime() {
        return getClaimOption(ClaimName.ISSUED_AT)
                .map(ClaimValue::getDateTime)
                .orElseThrow(() -> new IllegalStateException("issued at time claim not present in token"));
    }

    /**
     * Gets the optional not before claim value.
     * <p>
     * Since 'nbf' is optional, this method may return an empty Optional.
     *
     * @return the 'not before time'
     */
    default Optional<OffsetDateTime> getNotBefore() {
        return getClaimOption(ClaimName.NOT_BEFORE)
                .map(ClaimValue::getDateTime);
    }

    /**
     * Checks if the token has expired using the provided validation context.
     * <p>
     * This method eliminates synchronous OffsetDateTime.now() calls by using the cached
     * current time from the ValidationContext, significantly improving performance under
     * concurrent load.
     *
     * @param context the validation context containing cached current time
     * @return true if the token has expired, false otherwise
     */
    default boolean isExpired(ValidationContext context) {
        return context.isExpired(getExpirationTime());
    }
}
