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
package de.cuioss.sheriff.oauth.quarkus.interceptor;

import de.cuioss.sheriff.oauth.core.domain.token.AccessTokenContent;
import de.cuioss.sheriff.oauth.quarkus.producer.BearerTokenProducer;
import de.cuioss.tools.logging.CuiLogger;
import jakarta.annotation.Priority;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * JAX-RS {@link ContainerRequestFilter} that enforces standard Jakarta Security
 * annotations ({@link RolesAllowed}, {@link DenyAll}, {@link PermitAll}) on
 * JAX-RS resource methods and classes.
 * <p>
 * Per the MicroProfile JWT specification, {@code @RolesAllowed} maps to the
 * {@code groups} claim in the JWT token. This filter extracts the token via
 * {@link BearerTokenProducer#getAccessTokenContent()}, retrieves the groups,
 * and checks whether any of the required roles are present.
 * </p>
 * <p>
 * Annotation resolution order (method takes precedence over class):
 * <ol>
 *   <li>Check method for {@code @DenyAll} — always return 403</li>
 *   <li>Check method for {@code @PermitAll} — pass through</li>
 *   <li>Check method for {@code @RolesAllowed} — validate groups</li>
 *   <li>Check class for the same annotations in the same order</li>
 *   <li>If no annotation found — pass through (no security constraint)</li>
 * </ol>
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class RolesAllowedFilter implements ContainerRequestFilter {

    private static final CuiLogger LOGGER = new CuiLogger(RolesAllowedFilter.class);

    @Inject
    BearerTokenProducer bearerTokenProducer;

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        Method method = resourceInfo.getResourceMethod();
        if (method == null) {
            return;
        }
        Class<?> resourceClass = resourceInfo.getResourceClass();

        // Method-level annotations take precedence
        if (method.isAnnotationPresent(DenyAll.class)) {
            LOGGER.debug("@DenyAll on method %s — returning 403", method.getName());
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
            return;
        }
        if (method.isAnnotationPresent(PermitAll.class)) {
            LOGGER.debug("@PermitAll on method %s — allowing access", method.getName());
            return;
        }

        RolesAllowed rolesAllowed = method.getAnnotation(RolesAllowed.class);
        if (rolesAllowed == null && resourceClass != null) {
            // Fall back to class-level annotations
            if (resourceClass.isAnnotationPresent(DenyAll.class)) {
                LOGGER.debug("@DenyAll on class %s — returning 403", resourceClass.getSimpleName());
                requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
                return;
            }
            if (resourceClass.isAnnotationPresent(PermitAll.class)) {
                LOGGER.debug("@PermitAll on class %s — allowing access", resourceClass.getSimpleName());
                return;
            }
            rolesAllowed = resourceClass.getAnnotation(RolesAllowed.class);
        }

        if (rolesAllowed == null) {
            // No security annotation — pass through
            return;
        }

        // @RolesAllowed found — check token groups
        Set<String> allowedRoles = Set.of(rolesAllowed.value());
        LOGGER.debug("@RolesAllowed check for roles: %s", allowedRoles);

        Optional<AccessTokenContent> tokenOpt = bearerTokenProducer.getAccessTokenContent();
        if (tokenOpt.isEmpty()) {
            LOGGER.debug("No valid token present — returning 401 for @RolesAllowed");
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
            return;
        }

        AccessTokenContent token = tokenOpt.get();
        Set<String> tokenGroups = token.getGroups();

        // Check if token groups contain any of the allowed roles (per MP-JWT spec)
        boolean hasRole = allowedRoles.stream().anyMatch(tokenGroups::contains);

        if (!hasRole) {
            LOGGER.debug("Token groups %s do not contain any of required roles %s — returning 403",
                    tokenGroups, allowedRoles);
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
            return;
        }

        LOGGER.debug("@RolesAllowed check passed — token has required role(s)");
    }
}
