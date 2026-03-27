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
import de.cuioss.sheriff.oauth.core.domain.claim.ClaimValueType;
import de.cuioss.sheriff.oauth.core.domain.context.ValidationContext;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Base interface for JWT Token content.
 * <p>
 * This interface defines the common contract for all JWT token objects,
 * providing structured access to token claims and metadata. It extends
 * {@link MinimalTokenContent} for basic token information and
 * {@link JsonWebToken} for MicroProfile JWT Auth 2.1 compatibility,
 * making every validated token a {@link java.security.Principal}.
 * <p>
 * The interface provides:
 * <ul>
 *   <li>All standard {@link JsonWebToken} methods (getName, getSubject, getGroups, etc.)</li>
 *   <li>Rich-typed convenience methods for time claims (getExpirationDateTime, etc.)</li>
 *   <li>Access to all claims in the token via {@link #getClaims()}</li>
 *   <li>Type-safe claim retrieval via {@link #getClaimOption(ClaimName)}</li>
 *   <li>Expiration checking</li>
 * </ul>
 * <p>
 * JWT tokens implementing this interface follow the standards defined in
 * <a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC 7519</a> and
 * <a href="https://download.eclipse.org/microprofile/microprofile-jwt-auth-2.1/microprofile-jwt-auth-spec-2.1.html">MicroProfile JWT Auth 2.1</a>.
 * <p>
 * For more details on token structure, see the
 * <a href="https://github.com/cuioss/OAuthSheriff/tree/main/doc/architecture.adoc#token-structure">Token Structure</a>
 * specification.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
// Design Decision: MP-JWT coupling (extends JsonWebToken) is intentional. Provides
// java.security.Principal integration and MicroProfile JWT Auth injection compatibility.
public interface TokenContent extends MinimalTokenContent, JsonWebToken {

    /**
     * Bridge method to resolve the {@code getRawToken()} conflict between
     * {@link MinimalTokenContent} and {@link JsonWebToken}.
     * Both interfaces define {@code getRawToken()} with the same signature,
     * so this override satisfies both contracts.
     *
     * @return the raw JWT token string
     */
    @Override
    String getRawToken();

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

    // ============================================================
    // JsonWebToken / Principal interface methods
    // ============================================================

    /**
     * Returns the unique name of this principal using the MP-JWT fallback chain:
     * {@code upn} -> {@code preferred_username} -> {@code sub}.
     *
     * @return the principal name, or null if none of the fallback claims are present
     */
    @Override
    default String getName() {
        return getClaimOption(ClaimName.UPN)
                .map(ClaimValue::getOriginalString)
                .or(() -> getClaimOption(ClaimName.PREFERRED_USERNAME).map(ClaimValue::getOriginalString))
                .or(() -> getClaimOption(ClaimName.SUBJECT).map(ClaimValue::getOriginalString))
                .orElse(null);
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
    @Override
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
     * <strong>Note:</strong> This method returns a nullable String per the
     * {@link JsonWebToken#getSubject()} contract. For null-safe access, use
     * {@link #getSubjectOption()}.
     *
     * @return the subject if present, or null if not present
     * @see IssuerConfig.IssuerConfigBuilder#claimSubOptional(boolean)
     * @see #getSubjectOption()
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.2">RFC 7519 - 4.1.2. "sub" (Subject) Claim</a>
     */
    @Override
    default String getSubject() {
        return getClaimOption(ClaimName.SUBJECT)
                .map(ClaimValue::getOriginalString)
                .orElse(null);
    }

    /**
     * Gets the audience claim as a Set of strings.
     *
     * @return the audience set, or an empty set if not present
     */
    @Override
    default Set<String> getAudience() {
        return getClaimOption(ClaimName.AUDIENCE)
                .map(ClaimValue::getAsList)
                .<Set<String>>map(list -> new LinkedHashSet<>(list))
                .orElse(Collections.emptySet());
    }

    /**
     * Gets the token ID (jti) claim.
     *
     * @return the token ID, or null if not present
     */
    @Override
    default String getTokenID() {
        return getClaimOption(ClaimName.TOKEN_ID)
                .map(ClaimValue::getOriginalString)
                .orElse(null);
    }

    /**
     * Gets the expiration time as Unix epoch seconds.
     * <p>
     * This is the MP-JWT spec signature. For rich-typed access, use
     * {@link #getExpirationDateTime()}.
     *
     * @return the expiration time in epoch seconds
     * @throws IllegalStateException if the expiration claim is not present
     * @see #getExpirationDateTime()
     */
    @Override
    default long getExpirationTime() {
        return getExpirationDateTime().toEpochSecond();
    }

    /**
     * Gets the issued-at time as Unix epoch seconds.
     * <p>
     * This is the MP-JWT spec signature. For rich-typed access, use
     * {@link #getIssuedAtDateTime()}.
     *
     * @return the issued-at time in epoch seconds
     * @throws IllegalStateException if the issued-at claim is not present
     * @see #getIssuedAtDateTime()
     */
    @Override
    default long getIssuedAtTime() {
        return getIssuedAtDateTime().toEpochSecond();
    }

    /**
     * Gets the groups claim as a Set of strings.
     *
     * @return the groups set, or an empty set if not present
     */
    @Override
    default Set<String> getGroups() {
        return getClaimOption(ClaimName.GROUPS)
                .map(ClaimValue::getAsList)
                .<Set<String>>map(list -> new LinkedHashSet<>(list))
                .orElse(Collections.emptySet());
    }

    /**
     * Gets the set of all claim names in this token.
     *
     * @return a set of claim name strings
     */
    @Override
    default Set<String> getClaimNames() {
        return getClaims().keySet();
    }

    /**
     * Checks if this token contains the specified claim.
     *
     * @param claimName the claim name to check
     * @return true if the claim is present
     */
    @Override
    default boolean containsClaim(String claimName) {
        return getClaims().containsKey(claimName);
    }

    /**
     * Gets the value of the specified claim.
     * <p>
     * Returns the claim value converted to the appropriate type:
     * <ul>
     *   <li>String claims return String</li>
     *   <li>DateTime claims return Long (epoch seconds)</li>
     *   <li>List claims return Set&lt;String&gt;</li>
     * </ul>
     *
     * @param claimName the name of the claim
     * @param <T> the expected type
     * @return the claim value, or null if not present
     */
    @Override
    @SuppressWarnings("unchecked")
    default <T> T getClaim(String claimName) {
        var claimValue = getClaims().get(claimName);
        if (claimValue == null) {
            return null;
        }
        // Determine type from ClaimName enum if available, otherwise return original string
        var knownClaim = ClaimName.fromString(claimName);
        if (knownClaim.isPresent()) {
            var type = knownClaim.get().getValueType();
            return (T) switch (type) {
                case DATETIME -> claimValue.getDateTime() != null
                        ? claimValue.getDateTime().toEpochSecond()
                        : null;
                case STRING_LIST -> new LinkedHashSet<>(claimValue.getAsList());
                default -> claimValue.getOriginalString();
            };
        }
        return (T) claimValue.getOriginalString();
    }

    // ============================================================
    // OAuthSheriff convenience methods (rich-typed alternatives)
    // ============================================================

    /**
     * Gets the subject claim as an Optional.
     * <p>
     * Null-safe alternative to {@link #getSubject()}.
     *
     * @return an Optional containing the subject if present, or empty otherwise
     */
    default Optional<String> getSubjectOption() {
        return Optional.ofNullable(getSubject());
    }

    /**
     * Gets the expiration time as an OffsetDateTime.
     * <p>
     * Rich-typed alternative to {@link #getExpirationTime()}.
     *
     * @return the expiration time
     * @throws IllegalStateException if the expiration claim is not present
     */
    default OffsetDateTime getExpirationDateTime() {
        return getClaimOption(ClaimName.EXPIRATION)
                .map(ClaimValue::getDateTime)
                .orElseThrow(() -> new IllegalStateException("ExpirationTime claim not present in token"));
    }

    /**
     * Gets the issued-at time as an OffsetDateTime.
     * <p>
     * Rich-typed alternative to {@link #getIssuedAtTime()}.
     *
     * @return the issued-at time
     * @throws IllegalStateException if the issued-at claim is not present
     */
    default OffsetDateTime getIssuedAtDateTime() {
        return getClaimOption(ClaimName.ISSUED_AT)
                .map(ClaimValue::getDateTime)
                .orElseThrow(() -> new IllegalStateException("issued at time claim not present in token"));
    }

    /**
     * Gets the optional not-before claim value as an OffsetDateTime.
     *
     * @return an Optional containing the not-before time if present
     */
    default Optional<OffsetDateTime> getNotBeforeDateTime() {
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
        return context.isExpired(getExpirationDateTime());
    }
}
