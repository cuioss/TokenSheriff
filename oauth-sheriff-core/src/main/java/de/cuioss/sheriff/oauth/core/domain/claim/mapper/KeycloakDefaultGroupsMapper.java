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
import de.cuioss.tools.logging.CuiLogger;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A {@link ClaimMapper} implementation for mapping Keycloak's default groups structure.
 * <p>
 * This mapper extracts groups from Keycloak's standard {@code groups} claim and ensures
 * they are properly formatted for the OAuth Sheriff library's authorization mechanisms.
 * <p>
 * Keycloak typically includes groups in the token as:
 * <pre>
 * {
 *   "groups": ["/test-group", "/admin-group"]
 * }
 * </pre>
 * <p>
 * This mapper processes the groups array and optionally removes leading path separators
 * if configured with full path set to false in Keycloak's group membership mapper.
 * <p>
 * The mapper handles:
 * <ul>
 *   <li>Missing {@code groups} claim - returns empty list</li>
 *   <li>Empty groups array - returns empty list</li>
 *   <li>Non-array groups value - returns empty list</li>
 *   <li>Group names with or without path prefixes</li>
 * </ul>
 * <p>
 * This mapper provides a bridge between Keycloak's default group claim structure
 * and the OAuth Sheriff library's expected format, ensuring seamless integration
 * without requiring custom protocol mappers in Keycloak.
 *
 * @since 1.0
 * @see <a href="https://www.keycloak.org/docs/latest/server_admin/index.html#_group-mappers">Keycloak Group Mappers</a>
 */
public class KeycloakDefaultGroupsMapper implements ClaimMapper {

    private static final CuiLogger LOGGER = new CuiLogger(KeycloakDefaultGroupsMapper.class);
    private static final String GROUPS_CLAIM = "groups";

    @Override
    public ClaimValue map(MapRepresentation mapRepresentation, String claimName) {
        LOGGER.debug("KeycloakDefaultGroupsMapper.map called for claim: %s", claimName);
        LOGGER.debug("Input MapRepresentation: %s", mapRepresentation.toString());

        Optional<List<String>> groupsValue = mapRepresentation.getStringList(GROUPS_CLAIM);
        if (groupsValue.isEmpty()) {
            LOGGER.debug("No groups claim found in token");
            return ClaimValue.createEmptyClaimValue(ClaimValueType.STRING_LIST);
        }

        List<String> groupsList = groupsValue.get();
        String originalValue = groupsList.toString();

        LOGGER.debug("Successfully mapped groups: %s", groupsList);
        return ClaimValue.forList(originalValue, Collections.unmodifiableList(groupsList));
    }
}