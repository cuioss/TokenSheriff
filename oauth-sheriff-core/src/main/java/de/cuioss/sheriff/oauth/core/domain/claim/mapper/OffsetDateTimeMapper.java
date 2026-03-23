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
package de.cuioss.sheriff.oauth.core.domain.claim.mapper;

import de.cuioss.sheriff.oauth.core.domain.claim.ClaimValue;
import de.cuioss.sheriff.oauth.core.domain.claim.ClaimValueType;
import de.cuioss.sheriff.oauth.core.json.MapRepresentation;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * A {@link ClaimMapper} implementation for mapping date-time claims.
 * According to JWT specification (RFC 7519), date-time values are represented as NumericDate,
 * which is the number of seconds from 1970-01-01T00:00:00Z UTC until the specified UTC date/time.
 *
 * @since 1.0
 */
public class OffsetDateTimeMapper implements ClaimMapper {
    @Override
    public ClaimValue map(MapRepresentation mapRepresentation, String claimName) {
        Optional<Object> claimValue = mapRepresentation.getValue(claimName);

        // If claim doesn't exist, return empty
        if (claimValue.isEmpty()) {
            return ClaimValue.createEmptyClaimValue(ClaimValueType.DATETIME);
        }

        // Check if the claim value is a number
        Optional<Number> optionalNumber = mapRepresentation.getNumber(claimName);
        if (optionalNumber.isEmpty()) {
            throw new IllegalArgumentException("Claim '" + claimName + "' exists but is not a numeric value");
        }

        Number numberValue = optionalNumber.get();

        // According to JWT specification (RFC 7519), date-time values are represented as NumericDate,
        // which is the number of seconds from 1970-01-01T00:00:00Z UTC until the specified UTC date/time.
        // Handle numeric timestamp (seconds since epoch) - this is the standard format
        long epochSeconds = numberValue.longValue();
        String originalValue = String.valueOf(epochSeconds);

        OffsetDateTime offsetDateTime = OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(epochSeconds),
                ZoneId.systemDefault()
        );
        return ClaimValue.forDateTime(originalValue, offsetDateTime);
    }
}
