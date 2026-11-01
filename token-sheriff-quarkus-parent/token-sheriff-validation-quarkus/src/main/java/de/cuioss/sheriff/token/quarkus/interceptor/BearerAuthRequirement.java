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
package de.cuioss.sheriff.token.quarkus.interceptor;

import de.cuioss.sheriff.token.quarkus.annotation.BearerAuth;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The requirements a single {@link BearerAuth} declaration places on a bearer token, or the union of
 * several such declarations.
 * <p>
 * Combining requirements only ever adds to them: {@link #union(BearerAuthRequirement)} keeps every scope,
 * role and group of both operands, so merging a further source can never accept a token that one of the
 * sources alone would refuse.
 *
 * @param requiredScopes the scopes the token must carry
 * @param requiredRoles  the roles the token must carry
 * @param requiredGroups the groups the token must carry
 */
record BearerAuthRequirement(Set<String> requiredScopes, Set<String> requiredRoles, Set<String> requiredGroups) {

    BearerAuthRequirement {
        requiredScopes = Set.copyOf(requiredScopes);
        requiredRoles = Set.copyOf(requiredRoles);
        requiredGroups = Set.copyOf(requiredGroups);
    }

    /**
     * Creates the requirement declared by a {@link BearerAuth} annotation instance.
     *
     * @param annotation the annotation to read
     * @return the requirement carrying the annotation's scopes, roles and groups
     */
    static BearerAuthRequirement of(BearerAuth annotation) {
        return of(annotation.requiredScopes(), annotation.requiredRoles(), annotation.requiredGroups());
    }

    /**
     * Creates a requirement from the raw member values of a {@link BearerAuth} declaration.
     *
     * @param requiredScopes the declared scopes
     * @param requiredRoles  the declared roles
     * @param requiredGroups the declared groups
     * @return the requirement carrying the given values
     */
    static BearerAuthRequirement of(String[] requiredScopes, String[] requiredRoles, String[] requiredGroups) {
        return new BearerAuthRequirement(Set.copyOf(List.of(requiredScopes)), Set.copyOf(List.of(requiredRoles)),
                Set.copyOf(List.of(requiredGroups)));
    }

    /**
     * Combines this requirement with another one.
     *
     * @param other the requirement to add
     * @return a requirement holding every scope, role and group of both requirements
     */
    BearerAuthRequirement union(BearerAuthRequirement other) {
        return new BearerAuthRequirement(
                unionOf(requiredScopes, other.requiredScopes),
                unionOf(requiredRoles, other.requiredRoles),
                unionOf(requiredGroups, other.requiredGroups));
    }

    private static Set<String> unionOf(Set<String> first, Set<String> second) {
        return Stream.concat(first.stream(), second.stream()).collect(Collectors.toUnmodifiableSet());
    }
}
