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
package de.cuioss.sheriff.token.validation.domain.token;

import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A token whose <strong>signature</strong> was verified and whose <strong>claims were not</strong>.
 * <p>
 * <strong>WARNING:</strong> this is not an authentication result. It is produced by
 * {@code TokenValidator#verifySignature(String)}, which establishes only that the token was signed by
 * a key of an enabled issuer whose JWKS loader is healthy, and that its header passes the algorithm,
 * key ID and embedded-JWK checks. No claim verdict is reached: the token may be expired, issued for a
 * different audience, lack a subject or authorized party, or be of any token type. The issuer's
 * configured expected token type is not checked either; the {@code typ} header is returned verbatim
 * through {@link #getTypHeader()} for the caller to judge. A caller that needs a verdict must check
 * every claim it relies on itself, or use {@code createAccessToken} / {@code createIdToken} instead.
 * <p>
 * The type deliberately implements neither {@link TokenContent} nor {@link MinimalTokenContent}, so a
 * signature-only result cannot reach code that accepts a validated token. It is never cached.
 * <p>
 * Instances are immutable and thread-safe.
 *
 * @since 1.0
 */
@ToString
@EqualsAndHashCode
public final class SignatureVerifiedToken {

    /**
     * The claims mapped with the issuer's claim mappers, not validated.
     */
    @ToString.Exclude
    private final Map<String, ClaimValue> claims;

    /**
     * The payload as a deep-immutable native JSON map, not validated.
     */
    @ToString.Exclude
    private final Map<String, Object> rawClaims;

    /**
     * The names of the top-level payload members whose JSON value is {@code null}, omitted from
     * {@link #rawClaims}.
     */
    @ToString.Exclude
    private final Set<String> nullClaimNames;

    /**
     * The protected-header {@code typ} value verbatim, or {@code null} when the header carries none.
     */
    private final @Nullable String typHeader;

    /**
     * The raw token string as presented; for a JWE this is the outer encrypted token.
     */
    @Getter
    @ToString.Exclude
    private final String rawToken;

    /**
     * Creates a new signature-verified token.
     * <p>
     * The given claims map is defensively copied into an unmodifiable map. It must not contain
     * {@code null} keys or values. The raw claims are retained as given and must already be
     * deep-immutable, as {@code MapRepresentation#toNativeClaims()} produces them. The null claim names
     * are copied into an unmodifiable set.
     *
     * @param claims         the mapped, unvalidated claims of the token, may be empty
     * @param rawClaims      the payload as a deep-immutable native JSON map, may be empty
     * @param nullClaimNames the names of the top-level payload members whose JSON value is {@code null},
     *                       which {@code rawClaims} omits; may be empty
     * @param typHeader      the protected-header {@code typ} value verbatim, {@code null} when absent
     * @param rawToken       the raw token string as presented
     */
    public SignatureVerifiedToken(Map<String, ClaimValue> claims, Map<String, Object> rawClaims,
            Set<String> nullClaimNames, @Nullable String typHeader, String rawToken) {
        this.claims = Map.copyOf(Objects.requireNonNull(claims, "claims must not be null"));
        this.rawClaims = Objects.requireNonNull(rawClaims, "rawClaims must not be null");
        this.nullClaimNames = Set.copyOf(Objects.requireNonNull(nullClaimNames, "nullClaimNames must not be null"));
        this.typHeader = typHeader;
        this.rawToken = Objects.requireNonNull(rawToken, "rawToken must not be null");
    }

    /**
     * Returns the claims of the token, mapped with the issuer's claim mappers.
     * <p>
     * None of them has been validated. Never {@code null}, may be empty; the returned map is
     * unmodifiable.
     *
     * @return unmodifiable map of unvalidated claims
     */
    public Map<String, ClaimValue> getClaims() {
        return claims;
    }

    /**
     * Returns the payload of the token as a native JSON map.
     * <p>
     * JSON objects are unmodifiable maps in member order, JSON arrays unmodifiable lists in element
     * order, and numbers, booleans and strings keep their JSON type; a {@code null} nested inside an
     * object or array is kept, a top-level {@code null} member is omitted and named by
     * {@link #getNullClaimNames()}. None of the values has been validated. For a JWE this is the payload of
     * the inner, decrypted token.
     *
     * @return the deep-immutable native claims, never {@code null}, may be empty
     */
    public Map<String, Object> getRawClaims() {
        return rawClaims;
    }

    /**
     * Returns the names of the top-level payload members whose JSON value is {@code null}.
     * <p>
     * {@link #getRawClaims()} omits these members, so a caller that must know whether a claim is present
     * at all, whatever its value, checks both: a member is present when it is a key of the raw claims or
     * one of these names.
     *
     * @return the unmodifiable set of null member names, never {@code null}, may be empty
     */
    public Set<String> getNullClaimNames() {
        return nullClaimNames;
    }

    /**
     * Returns the protected-header {@code typ} value exactly as the token carries it.
     * <p>
     * The value is neither normalized nor compared with the issuer's expected token type.
     *
     * @return the {@code typ} header value, or empty when the header carries none
     */
    public Optional<String> getTypHeader() {
        return Optional.ofNullable(typHeader);
    }
}
