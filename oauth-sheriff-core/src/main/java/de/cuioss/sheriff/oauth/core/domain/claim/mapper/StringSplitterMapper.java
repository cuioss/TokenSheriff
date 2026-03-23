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
import de.cuioss.tools.string.Splitter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A {@link ClaimMapper} implementation for splitting string claims by a specified character.
 * <p>
 * This mapper only works with string values and splits the string
 * using the provided split character. It trims the results and omits empty strings.
 * <p>
 * This is particularly useful for claims that contain multiple values in a single string
 * separated by a specific character (e.g., comma-separated roles, colon-separated groups).
 * <p>
 * Example usage:
 * <pre>
 * // Create a mapper that splits by comma
 * StringSplitterMapper commaMapper = new StringSplitterMapper(',');
 * 
 * // Use with a claim that contains comma-separated values
 * // e.g., "roles": "admin,user,manager"
 * ClaimValue roles = commaMapper.map(jsonObject, "roles");
 * </pre>
 *
 * @since 1.0
 */
public class StringSplitterMapper implements ClaimMapper {

    /**
     * The character to split the string by.
     */
   
    private final Character splitChar;

    public StringSplitterMapper(Character splitChar) {
        this.splitChar = splitChar;
    }

    @Override
    public ClaimValue map(MapRepresentation mapRepresentation, String claimName) {
        Optional<Object> claimValue = mapRepresentation.getValue(claimName);

        // If claim doesn't exist, return empty
        if (claimValue.isEmpty()) {
            return ClaimValue.createEmptyClaimValue(ClaimValueType.STRING_LIST);
        }

        // Check if the claim value is a string
        Optional<String> optionalStringValue = mapRepresentation.getString(claimName);
        if (optionalStringValue.isEmpty()) {
            throw new IllegalArgumentException("Claim '" + claimName + "' exists but is not a string value");
        }

        String originalValue = optionalStringValue.get();
        List<String> values = Splitter.on(splitChar).trimResults().omitEmptyStrings().splitToList(originalValue);
        return ClaimValue.forList(originalValue, Collections.unmodifiableList(values));
    }
}