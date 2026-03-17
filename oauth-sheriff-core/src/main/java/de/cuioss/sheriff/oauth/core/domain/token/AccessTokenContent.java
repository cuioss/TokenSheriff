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

import de.cuioss.sheriff.oauth.core.TokenType;
import de.cuioss.sheriff.oauth.core.domain.claim.ClaimName;
import de.cuioss.sheriff.oauth.core.domain.claim.ClaimValue;
import de.cuioss.sheriff.oauth.core.domain.claim.CollectionClaimHandler;
import de.cuioss.sheriff.oauth.core.json.MapRepresentation;
import de.cuioss.tools.logging.CuiLogger;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.util.*;

/**
 * Represents the content of an OAuth 2.0 access token.
 * <p>
 * This class provides access to access token specific claims and functionality, including:
 * <ul>
 *   <li>Scope validation with detailed logging capabilities</li>
 *   <li>Role and group validation for role-based and group-based access control</li>
 *   <li>Access to audience claims</li>
 *   <li>User identity information (email, preferred username)</li>
 * </ul>
 * <p>
 * Access tokens typically contain:
 * <ul>
 *   <li>Standard JWT claims (iss, sub, exp, iat)</li>
 *   <li>OAuth-specific claims like scope/scopes and audience</li>
 *   <li>Optional identity claims depending on the authorization server</li>
 * </ul>
 * <p>
 * This implementation follows the standards defined in:
 * <ul>
 *   <li><a href="https://datatracker.ietf.org/doc/html/rfc6749">RFC 6749 - OAuth 2.0</a></li>
 *   <li><a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC 7519 - JWT</a></li>
 *   <li><a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - Token Exchange</a></li>
 * </ul>
 * <p>
 * For more details on token structure and usage, see the
 * <a href="https://github.com/cuioss/OAuthSheriff/tree/main/doc/architecture.adoc#token-types">Token Types</a>
 * specification.
 *
 * @author Oliver Wolff
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class AccessTokenContent extends BaseTokenContent {

    private static final CuiLogger LOGGER = new CuiLogger(AccessTokenContent.class);

    @Serial
    private static final long serialVersionUID = 1L;

    private final String email;

    /**
     * Constructs a new AccessTokenContent with the given claims, raw token, and email.
     *
     * @param claims     the token claims
     * @param rawToken   the raw token string
     * @param email      the user's email address
     * @param rawPayload the raw JSON payload for ClaimMapper processing
     */
    public AccessTokenContent(Map<String, ClaimValue> claims, String rawToken, String email,
            MapRepresentation rawPayload) {
        super(claims, rawToken, TokenType.ACCESS_TOKEN, rawPayload);
        this.email = email;
    }

    /**
     * Gets the audience claim value.
     * <p>
     * 'aud' is optional for {@link TokenType#ACCESS_TOKEN}.
     *
     * @return the audience as a list of strings, or throws exception if it's not present
     * @throws IllegalStateException if the audience claim is not present
     */
    public Optional<List<String>> getAudience() {
        return getClaimOption(ClaimName.AUDIENCE)
                .map(ClaimValue::getAsList);
    }

    /**
     * Gets the scopes from the token claims.
     * <p>
     * The scope claim is optional per RFC 9068 Section 2.2. If the scope claim is not
     * present in the token, an empty list is returned.
     *
     * @return a List of scope strings, or an empty list if the scope claim is not present
     */

    public List<String> getScopes() {
        return getClaimOption(ClaimName.SCOPE)
                .map(ClaimValue::getAsList)
                .orElse(Collections.emptyList());
    }

    /**
     * Gets the roles from the token claims.
     * <p>
     * The "roles" claim is a common but not standardized claim used for role-based access control.
     *
     * @return a List of role strings, or an empty list if the roles claim is not present
     */
   
    public List<String> getRoles() {
        return getClaimOption(ClaimName.ROLES)
                .map(ClaimValue::getAsList)
                .orElse(Collections.emptyList());
    }

    /**
     * Gets the groups from the token claims.
     * <p>
     * The "groups" claim is a common but not standardized claim used for group-based access control.
     *
     * @return a List of group strings, or an empty list if the groups claim is not present
     */
   
    public List<String> getGroups() {
        return getClaimOption(ClaimName.GROUPS)
                .map(ClaimValue::getAsList)
                .orElse(Collections.emptyList());
    }

    /**
     * Gets the email address associated with this token.
     * If not provided in the constructor, tries to extract from the claims.
     *
     * @return an Optional containing the email if present, or empty otherwise
     */
    public Optional<String> getEmail() {
        if (email != null) {
            return Optional.of(email);
        }
        return getClaimOption(ClaimName.EMAIL).map(ClaimValue::getOriginalString);
    }

    /**
     * Gets the preferred username from the token claims.
     *
     * @return an Optional containing the preferred username if present, or empty otherwise
     */
    public Optional<String> getPreferredUsername() {
        return getClaimOption(ClaimName.PREFERRED_USERNAME).map(ClaimValue::getOriginalString);
    }

    /**
     * @param expectedScopes to be checked
     * @return boolean indicating whether the token provides all given Scopes
     */
    public boolean providesScopes(Collection<String> expectedScopes) {
        return getClaimOption(ClaimName.SCOPE)
                .map(claimValue -> new CollectionClaimHandler(claimValue).providesValues(expectedScopes))
                .orElse(false);
    }

    /**
     * Checks if the token provides all expected scopes and logs debug information if any are missing.
     *
     * @param expectedScopes to be checked
     * @param logContext     additional context information for logging
     * @param logger         the logger to use for logging
     * @return boolean indicating whether the token provides all given Scopes. In contrast to
     * {@link #providesScopes(Collection)} it log on debug the corresponding scopes
     */
    public boolean providesScopesAndDebugIfScopesAreMissing(Collection<String> expectedScopes, String logContext,
            CuiLogger logger) {
        return getClaimOption(ClaimName.SCOPE)
                .map(claimValue -> new CollectionClaimHandler(claimValue)
                        .providesValuesAndDebugIfValuesMissing(expectedScopes, logContext, logger))
                .orElse(false);
    }

    /**
     * @param expectedScopes to be checked
     * @return an empty-Set in case the token provides all expectedScopes, otherwise a
     * {@link TreeSet} containing all missing scopes.
     */
   
    public Set<String> determineMissingScopes(Collection<String> expectedScopes) {
        return getClaimOption(ClaimName.SCOPE)
                .map(claimValue -> new CollectionClaimHandler(claimValue).determineMissingValues(expectedScopes))
                .orElse(new TreeSet<>(expectedScopes));
    }

    /**
     * Checks if the token provides all expected roles.
     *
     * @param expectedRoles the roles to check for
     * @return true if the token contains all expected roles, false otherwise
     */
    public boolean providesRoles(Collection<String> expectedRoles) {
        return getClaimOption(ClaimName.ROLES)
                .map(claimValue -> new CollectionClaimHandler(claimValue).providesValues(expectedRoles))
                .orElse(false);
    }

    /**
     * Checks if the token provides all expected roles and logs debug information if any are missing.
     *
     * @param expectedRoles the roles to check for
     * @param logContext additional context information for logging
     * @param logger the logger to use for logging
     * @return true if the token contains all expected roles, false otherwise
     */
    public boolean providesRolesAndDebugIfRolesMissing(Collection<String> expectedRoles, String logContext,
            CuiLogger logger) {
        return getClaimOption(ClaimName.ROLES)
                .map(claimValue -> new CollectionClaimHandler(claimValue)
                        .providesValuesAndDebugIfValuesMissing(expectedRoles, logContext, logger))
                .orElse(false);
    }

    /**
     * Determines which expected roles are missing from the token.
     *
     * @param expectedRoles the roles to check for
     * @return an empty Set if the token provides all expected roles, otherwise a
     *         {@link TreeSet} containing all missing roles
     */
   
    public Set<String> determineMissingRoles(Collection<String> expectedRoles) {
        return getClaimOption(ClaimName.ROLES)
                .map(claimValue -> new CollectionClaimHandler(claimValue).determineMissingValues(expectedRoles))
                .orElse(new TreeSet<>(expectedRoles));
    }

    /**
     * Checks if the token provides all expected groups.
     *
     * @param expectedGroups the groups to check for
     * @return true if the token contains all expected groups, false otherwise
     */
    public boolean providesGroups(Collection<String> expectedGroups) {
        return getClaimOption(ClaimName.GROUPS)
                .map(claimValue -> new CollectionClaimHandler(claimValue).providesValues(expectedGroups))
                .orElse(false);
    }

    /**
     * Checks if the token provides all expected groups and logs debug information if any are missing.
     *
     * @param expectedGroups the groups to check for
     * @param logContext additional context information for logging
     * @param logger the logger to use for logging
     * @return true if the token contains all expected groups, false otherwise
     */
    public boolean providesGroupsAndDebugIfGroupsMissing(Collection<String> expectedGroups, String logContext,
            CuiLogger logger) {
        return getClaimOption(ClaimName.GROUPS)
                .map(claimValue -> new CollectionClaimHandler(claimValue)
                        .providesValuesAndDebugIfValuesMissing(expectedGroups, logContext, logger))
                .orElse(false);
    }

    /**
     * Determines which expected groups are missing from the token.
     *
     * @param expectedGroups the groups to check for
     * @return an empty Set if the token provides all expected groups, otherwise a
     *         {@link TreeSet} containing all missing groups
     */
   
    public Set<String> determineMissingGroups(Collection<String> expectedGroups) {
        return getClaimOption(ClaimName.GROUPS)
                .map(claimValue -> new CollectionClaimHandler(claimValue).determineMissingValues(expectedGroups))
                .orElse(new TreeSet<>(expectedGroups));
    }
}
