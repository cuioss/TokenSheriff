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
package de.cuioss.sheriff.token.validation.domain.claim.mapper;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.json.MapRepresentation;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A {@link ClaimMapper} that accepts a claim only when its JSON value is a string.
 * <p>
 * Unlike {@link IdentityMapper}, which stringifies any JSON value, this mapper rejects a number,
 * boolean, array or object. It is used for identifier claims that are compared against configured
 * values, where a stringified non-string value (for example the number {@code 12345} becoming
 * {@code "12345"}) must never satisfy the comparison.
 * <ul>
 *   <li>JSON string: mapped to {@link ClaimValue#forPlainString(String)}</li>
 *   <li>Claim absent: {@code null}</li>
 *   <li>Explicit JSON {@code null} or any other JSON type: {@link TokenValidationException} with
 *       {@link SecurityEventCounter.EventType#MISSING_CLAIM}</li>
 * </ul>
 * The explicit-null refusal is also applied by the token builder to every identifier claim whose
 * {@link de.cuioss.sheriff.token.validation.domain.claim.ClaimName#isExplicitNullRefused()} holds,
 * including those mapped by {@link IdentityMapper}.
 *
 * @since 1.0
 */
public class StrictStringMapper implements ClaimMapper {

    @Override
    @Nullable
    public ClaimValue map(MapRepresentation mapRepresentation, String claimName) {
        Optional<Object> optionalValue = mapRepresentation.getValue(claimName);
        if (optionalValue.isEmpty()) {
            if (mapRepresentation.isExplicitNull(claimName)) {
                throw malformed(claimName, "null");
            }
            return null;
        }
        if (optionalValue.get() instanceof String stringValue) {
            return ClaimValue.forPlainString(stringValue);
        }
        throw malformed(claimName, optionalValue.get().getClass().getSimpleName());
    }

    private static TokenValidationException malformed(String claimName, String actualType) {
        return new TokenValidationException(
                SecurityEventCounter.EventType.MISSING_CLAIM,
                "Malformed claim '" + claimName + "': expected a JSON string but got " + actualType
        );
    }
}
