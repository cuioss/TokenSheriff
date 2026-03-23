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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A {@link ClaimMapper} implementation for mapping JSON values to collections.
 * This mapper handles the following cases:
 * <ul>
 *   <li>JSON arrays: Converts each element to a string and adds it to the list</li>
 *   <li>JSON strings: Wraps the string in a single-element list</li>
 *   <li>Other JSON types: Converts to string and wraps in a single-element list</li>
 * </ul>
 * This is particularly useful for {@link ClaimValueType#STRING_LIST} claims.
 *
 * @since 1.0
 */
public class JsonCollectionMapper implements ClaimMapper {
    @Override
    public ClaimValue map(MapRepresentation mapRepresentation, String claimName) {
        Optional<Object> optionalValue = mapRepresentation.getValue(claimName);
        if (optionalValue.isEmpty()) {
            return ClaimValue.createEmptyClaimValue(ClaimValueType.STRING_LIST);
        }
        Object value = optionalValue.get();

        String originalValue;
        List<String> values;

        if (value instanceof List<?> list) {
            // Handle List (array) - create JSON array representation
            originalValue = list.stream()
                    .map(Object::toString)
                    .map(s -> "\"" + s + "\"")
                    .collect(Collectors.joining(",", "[", "]"));
            values = list.stream()
                    .map(Object::toString)
                    .toList();
        } else {
            // Handle all other types by wrapping them in a single-element list
            originalValue = value.toString();

            // Add the single value to the list
            values = new ArrayList<>();
            values.add(originalValue);
        }

        return ClaimValue.forList(originalValue, Collections.unmodifiableList(values));
    }
}
