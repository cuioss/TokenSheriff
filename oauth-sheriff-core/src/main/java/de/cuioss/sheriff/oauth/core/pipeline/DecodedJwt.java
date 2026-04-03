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
package de.cuioss.sheriff.oauth.core.pipeline;

import de.cuioss.sheriff.oauth.core.domain.claim.ClaimName;
import de.cuioss.sheriff.oauth.core.exception.TokenValidationException;
import de.cuioss.sheriff.oauth.core.json.JwtHeader;
import de.cuioss.sheriff.oauth.core.json.MapRepresentation;
import de.cuioss.sheriff.oauth.core.security.SecurityEventCounter;

import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Record representing a decoded JWT token.
 * <p>
 * This record holds the parsed components of a JWT token after Base64 decoding and DSL-JSON parsing,
 * but before any validation occurs. It contains:
 * <ul>
 *   <li>The decoded header as a JwtHeader record</li>
 *   <li>The decoded payload (body) as a MapRepresentation</li>
 *   <li>The signature part as a String</li>
 *   <li>Convenience methods for accessing common JWT fields</li>
 *   <li>The original token parts and raw token string</li>
 * </ul>
 * <p>
 * <strong>Security Note:</strong> This record is not guaranteed to contain a validated token.
 * It is usually created by {@link NonValidatingJwtParser} and should be passed to
 * {@link de.cuioss.sheriff.oauth.core.pipeline.validator.TokenHeaderValidator}, {@link de.cuioss.sheriff.oauth.core.pipeline.validator.TokenSignatureValidator}, and {@link de.cuioss.sheriff.oauth.core.pipeline.validator.TokenClaimValidator}
 * for proper validation.
 * <p>
 * The record provides immutability guarantees and value-based equality by default, making it
 * ideal for representing decoded JWT data in the validation pipeline.
 * <p>
 * For more details on the token validation process, see the
 * <a href="https://github.com/cuioss/OAuthSheriff/tree/main/doc/architecture.adoc#token-validation-pipeline">Token Validation Pipeline</a>
 *
 * @param header the decoded header as a JwtHeader
 * @param body the decoded payload (body) as a MapRepresentation
 * @param signature the signature part as a String
 * @param parts the original token parts (header.payload.signature)
 * @param rawToken the original raw token string
 *
 * @apiNote This record is internal to OAuth Sheriff and not part of the public API.
 * @since 1.0
 * @author Oliver Wolff
 */
public record DecodedJwt(
JwtHeader header,
MapRepresentation body,
String signature,
String[] parts,
String rawToken
) {
    /**
     * Compact constructor that validates the body is non-null and parts format.
     * A properly decoded JWT must always have a parseable body.
     * If parts are provided, they must contain exactly 3 elements (header.payload.signature).
     */
    public DecodedJwt {
        Objects.requireNonNull(header, "header must not be null");
        Objects.requireNonNull(body, "body must not be null");
        if (parts != null && parts.length != 3) {
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.INVALID_JWT_FORMAT,
                    "JWT format is invalid: expected 3 parts (header.payload.signature) but found %s"
                            .formatted(parts.length)
            );
        }
    }

    /**
     * Gets the issuer of the JWT token extracted from the body.
     *
     * @return an Optional containing the issuer if present
     */
    public Optional<String> getIssuer() {
        return body.getString(ClaimName.ISSUER.getName());
    }

    /**
     * Gets the kid (key ID) from the JWT token header.
     *
     * @return an Optional containing the kid if present
     */
    public Optional<String> getKid() {
        return header.getKid();
    }

    /**
     * Gets the alg (algorithm) from the JWT token header.
     *
     * @return an Optional containing the algorithm if present
     */
    public Optional<String> getAlg() {
        return Optional.ofNullable(header.alg());
    }

    /**
     * Gets the decoded signature bytes from the JWT token.
     * <p>
     * This method decodes the Base64URL-encoded signature string to raw bytes.
     * <p>
     * <strong>Preconditions:</strong>
     * <ul>
     *   <li>The JWT must have been properly parsed with 3 parts (header.payload.signature)</li>
     *   <li>The parts array must contain exactly 3 elements</li>
     *   <li>The signature part (parts[2]) must be a valid Base64URL-encoded string</li>
     * </ul>
     *
     * @return the decoded signature bytes, never null
     * @throws IllegalStateException if the JWT format is invalid (not 3 parts) or if the signature
     *                               cannot be decoded from Base64URL format
     */
    public byte[] getSignatureAsDecodedBytes() {
        if (parts == null) {
            throw new IllegalStateException(
                    "JWT format is invalid: parts array is null"
            );
        }

        // Decode the signature from Base64URL
        try {
            return Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Failed to decode signature from Base64URL format: %s".formatted(e.getMessage()),
                    e
            );
        }
    }

    /**
     * Gets the data to verify for signature validation.
     * <p>
     * This method returns the concatenated header and payload parts (header.payload) that should
     * be used for signature verification according to the JWT specification.
     * <p>
     * <strong>Preconditions:</strong>
     * <ul>
     *   <li>The JWT must have been properly parsed with 3 parts (header.payload.signature)</li>
     *   <li>The parts array must contain exactly 3 elements</li>
     * </ul>
     *
     * @return the data to verify as a string in the format "header.payload", never null
     * @throws IllegalStateException if the JWT format is invalid (not 3 parts)
     */
    public String getDataToVerify() {
        if (parts == null) {
            throw new IllegalStateException(
                    "JWT format is invalid: parts array is null"
            );
        }

        // Return the concatenated header and payload
        return "%s.%s".formatted(parts[0], parts[1]);
    }

    /**
     * Overrides equals to properly handle array comparison for the parts field.
     * Uses Arrays.equals() for proper content-based equality comparison of the array.
     *
     * @param obj the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        DecodedJwt that = (DecodedJwt) obj;
        return Objects.equals(header, that.header) &&
                Objects.equals(body, that.body) &&
                Objects.equals(signature, that.signature) &&
                Arrays.equals(parts, that.parts) &&
                Objects.equals(rawToken, that.rawToken);
    }

    /**
     * Overrides hashCode to properly handle array hashing for the parts field.
     * Uses Arrays.hashCode() for consistent hash generation with the equals method.
     *
     * @return the hash code of this object
     */
    @Override
    public int hashCode() {
        return Objects.hash(header, body, signature, Arrays.hashCode(parts), rawToken);
    }

    /**
     * Overrides toString to properly handle array representation for the parts field.
     * Uses Arrays.toString() for proper array content representation.
     * <p>
     * <strong>Security:</strong> The rawToken is redacted to prevent accidental token
     * leakage in log output. Only the first 10 characters are shown.
     *
     * @return a string representation of this object
     */
    @Override
    public String toString() {
        return "DecodedJwt[" +
                "header=" + header +
                ", body=" + body +
                ", signature=" + signature +
                ", parts=" + Arrays.toString(parts) +
                ", rawToken=" + redactToken(rawToken) +
                ']';
    }

    /**
     * Redacts a token string to prevent leakage in logs.
     * Shows only the first 10 characters followed by "...".
     *
     * @param token the token to redact
     * @return the redacted token string
     */
    private static String redactToken(String token) {
        if (token == null) {
            return "null";
        }
        if (token.length() <= 10) {
            return token;
        }
        return token.substring(0, 10) + "...";
    }

}
