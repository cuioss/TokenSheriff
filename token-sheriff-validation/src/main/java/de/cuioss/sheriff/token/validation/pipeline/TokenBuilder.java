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
package de.cuioss.sheriff.token.validation.pipeline;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.domain.claim.mapper.ClaimMapper;
import de.cuioss.sheriff.token.validation.domain.claim.mapper.StrictStringMapper;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import de.cuioss.sheriff.token.validation.domain.token.IdTokenContent;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.json.MapRepresentation;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builder for creating token content objects from decoded JWT tokens.
 * <p>
 * This class is responsible for transforming decoded JWT tokens into strongly-typed
 * token content objects for further processing in the application.
 * <p>
 * It supports creating different types of tokens:
 * <ul>
 *   <li>Access Tokens - via {@link #createAccessToken}</li>
 *   <li>ID Tokens - via {@link #createIdToken}</li>
 * </ul>
 * <p>
 * During token creation, the builder extracts and maps claims from the token body
 * using appropriate claim mappers based on the issuer configuration or standard claim names.
 * <p>
 * For more details on the token building process, see the
 * <a href="https://github.com/cuioss/TokenSheriff/tree/main/doc/validation/architecture.adoc#token-validation-pipeline">Token Validation Pipeline</a>
 *
 * @apiNote This class is internal to Token-Sheriff and not part of the public API.
 * @since 1.0
 * @author Oliver Wolff
 */
public class TokenBuilder {

    // Raises the typed non-string rejection for an identifier claim carrying an explicit JSON null, and
    // for a subject whose raw JSON type is not a string, whatever mapper the issuer configures for it
    private static final ClaimMapper STRICT_STRING_GUARD = new StrictStringMapper();

    // Combined mapper lookup to avoid runtime checks
    private final Map<String, ClaimMapper> allMappers;

    // Counts a claim mapper's failure: MISSING_CLAIM for an arbitrary runtime exception, and the
    // carried event type for a TokenValidationException the mapper throws itself
    private final SecurityEventCounter securityEventCounter;

    /**
     * Constructs a TokenBuilder with the specified IssuerConfig.
     *
     * @param issuerConfig         the issuer configuration
     * @param securityEventCounter the counter for security events, incremented with
     *                             {@link SecurityEventCounter.EventType#MISSING_CLAIM} when a claim
     *                             mapper fails on a malformed claim value with a runtime exception,
     *                             and with the event type a mapper-thrown
     *                             {@link TokenValidationException} carries when the mapper rejects
     *                             the claim itself
     */
    public TokenBuilder(IssuerConfig issuerConfig, SecurityEventCounter securityEventCounter) {
        this.securityEventCounter = securityEventCounter;
        // IssuerConfig.claimMappers is never null (defaults to Map.of() in constructor)
        Map<String, ClaimMapper> customMappers = Map.copyOf(issuerConfig.getClaimMappers());

        Map<String, ClaimMapper> tempMappers = new HashMap<>();

        for (ClaimName claimName : ClaimName.values()) {
            tempMappers.put(claimName.getName(), claimName.getClaimMapper());
        }

        // Add custom mappers, which override standard ones
        tempMappers.putAll(customMappers);

        this.allMappers = Map.copyOf(tempMappers);
    }

    /**
     * Creates an AccessTokenContent from a decoded JWT.
     *
     * @param decodedJwt the decoded JWT
     * @return an Optional containing the AccessTokenContent if it could be created, empty otherwise
     * @throws TokenValidationException if a claim value is malformed and cannot be mapped
     */
    public Optional<AccessTokenContent> createAccessToken(DecodedJwt decodedJwt) {
        MapRepresentation body = decodedJwt.body();
        // A body of only explicit nulls is empty, so the refusal runs before the empty-body return
        refuseExplicitNulls(body);
        // Design Decision: Optional.empty() is the correct return for empty body.
        // Caller (AccessTokenValidationPipeline) handles this case explicitly.
        if (body.isEmpty()) {
            return Optional.empty();
        }

        Map<String, ClaimValue> claims = extractClaims(body);

        return Optional.of(new AccessTokenContent(claims, decodedJwt.rawToken()));
    }

    /**
     * Creates an IdTokenContent from a decoded JWT.
     *
     * @param decodedJwt the decoded JWT
     * @return an Optional containing the IdTokenContent if it could be created, empty otherwise
     * @throws TokenValidationException if a claim value is malformed and cannot be mapped
     */
    public Optional<IdTokenContent> createIdToken(DecodedJwt decodedJwt) {
        MapRepresentation body = decodedJwt.body();
        refuseExplicitNulls(body);
        if (body.isEmpty()) {
            return Optional.empty();
        }

        Map<String, ClaimValue> claims = extractClaims(body);

        return Optional.of(new IdTokenContent(claims, decodedJwt.rawToken()));
    }


    /**
     * Refuses the explicit JSON nulls a token body carries, instead of reading them as absent claims.
     * <p>
     * An explicit null for an identifier claim ({@link ClaimName#isExplicitNullRefused()}) is routed
     * through the strict string mapper whatever mapper the claim is configured with. An explicit null
     * for any other claim is routed through the mapper configured for it, so a strict mapper can refuse
     * it; the mapper's result is discarded, because an explicit null stays an absent claim for every
     * mapper that does not refuse it. Either way a refusal is typed and counted like any other
     * malformed claim.
     *
     * @param mapRepresentation the claims of the token body
     * @throws TokenValidationException if a mapper refuses an explicit null
     */
    private void refuseExplicitNulls(MapRepresentation mapRepresentation) {
        for (String key : mapRepresentation.explicitNullKeys()) {
            if (ClaimName.fromString(key).filter(ClaimName::isExplicitNullRefused).isPresent()) {
                mapClaim(STRICT_STRING_GUARD, mapRepresentation, key);
                continue;
            }
            ClaimMapper mapper = allMappers.get(key);
            if (mapper != null) {
                mapClaim(mapper, mapRepresentation, key);
            }
        }
    }

    /**
     * Extracts claims from a MapRepresentation using proper claim mappers.
     *
     * @param mapRepresentation the MapRepresentation containing claims
     * @return a map of claim names to claim values
     */
    private Map<String, ClaimValue> extractClaims(MapRepresentation mapRepresentation) {
        Map<String, ClaimValue> claims = HashMap.newHashMap(mapRepresentation.size());

        for (String key : mapRepresentation.keySet()) {
            // Try to find a configured claim mapper for this key
            ClaimMapper mapper = allMappers.get(key);

            if (mapper != null) {
                if (ClaimName.SUBJECT.getName().equals(key)) {
                    // The raw JSON type of sub is checked before a custom mapper replaces the strict one:
                    // IdentityMapper would otherwise stringify a numeric sub into a valid subject
                    mapClaim(STRICT_STRING_GUARD, mapRepresentation, key);
                }
                ClaimValue claimValue = mapClaim(mapper, mapRepresentation, key);
                if (claimValue != null) {
                    claims.put(key, claimValue);
                }
            } else {
                mapUnknownClaim(mapRepresentation, key).ifPresent(value -> claims.put(key, value));
            }
        }

        return claims;
    }

    /**
     * Maps a claim with its configured mapper (either built-in or custom).
     * <p>
     * Mappers (including custom SPI implementations) can throw arbitrary runtime
     * exceptions on malformed claim values (e.g. a string-typed 'exp'). These are translated
     * to {@link TokenValidationException} to honor the documented contract of TokenValidator,
     * and counted as {@link SecurityEventCounter.EventType#MISSING_CLAIM}. A
     * {@link TokenValidationException} thrown by a mapper is counted under its own event type
     * and rethrown unchanged.
     *
     * @param mapper            the mapper configured for the claim
     * @param mapRepresentation the MapRepresentation containing the claim
     * @param key               the claim name
     * @return the mapped claim value, or {@code null} if the mapper reports the claim as absent
     * @throws TokenValidationException if the mapper fails on a malformed claim value
     */
    private @Nullable ClaimValue mapClaim(ClaimMapper mapper, MapRepresentation mapRepresentation, String key) {
        try {
            return mapper.map(mapRepresentation, key);
        } catch (TokenValidationException e) {
            // A mapper cannot count its own rejection (the SPI hands it no counter), so it is
            // counted here under the event type it carries, and rethrown unchanged.
            securityEventCounter.increment(e.getEventType());
            throw e;
        }
        // cui-rewrite:disable InvalidExceptionUsageRecipe
        // Custom SPI mappers can throw arbitrary runtime exceptions — the broad
        // catch is deliberate; TokenValidationException is rethrown above.
        catch (RuntimeException e) {
            securityEventCounter.increment(SecurityEventCounter.EventType.MISSING_CLAIM);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.MISSING_CLAIM,
                    "Malformed claim '" + key + "': " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Fallback for unknown claims - uses identity mapping, converting non-string values to string.
     *
     * @param mapRepresentation the MapRepresentation containing the claim
     * @param key               the claim name
     * @return the claim as a plain-string claim value, or empty if the claim has no value
     */
    private static Optional<ClaimValue> mapUnknownClaim(MapRepresentation mapRepresentation, String key) {
        Optional<String> stringValue = mapRepresentation.getString(key);
        if (stringValue.isPresent()) {
            return Optional.of(ClaimValue.forPlainString(stringValue.get()));
        }
        // Handle non-string values by converting to string
        return mapRepresentation.getValue(key).map(o -> ClaimValue.forPlainString(o.toString()));
    }


    /**
     * Extracts claims for a Refresh-Token from a MapRepresentation.
     *
     * @param mapRepresentation the MapRepresentation containing claims
     * @return a map of claim names to claim values
     */
    public static Map<String, ClaimValue> extractClaimsForRefreshToken(MapRepresentation mapRepresentation) {
        Map<String, ClaimValue> claims = HashMap.newHashMap(mapRepresentation.size());

        for (String key : mapRepresentation.keySet()) {
            // Convert Map entry to ClaimValue
            Optional<Object> value = mapRepresentation.getValue(key);
            if (value.isPresent()) {
                Object valueObj = value.get();
                if (valueObj instanceof String stringValue) {
                    claims.put(key, ClaimValue.forPlainString(stringValue));
                } else {
                    claims.put(key, ClaimValue.forPlainString(valueObj.toString()));
                }
            }
        }

        return claims;
    }
}
