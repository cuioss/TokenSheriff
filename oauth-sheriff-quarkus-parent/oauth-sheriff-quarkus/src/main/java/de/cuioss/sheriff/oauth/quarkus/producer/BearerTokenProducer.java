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
package de.cuioss.sheriff.oauth.quarkus.producer;

import de.cuioss.sheriff.oauth.core.TokenValidator;
import de.cuioss.sheriff.oauth.core.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.oauth.core.domain.token.AccessTokenContent;
import de.cuioss.sheriff.oauth.core.exception.TokenValidationException;
import de.cuioss.sheriff.oauth.quarkus.annotation.BearerToken;
import de.cuioss.sheriff.oauth.quarkus.annotation.ServletObjectsResolver;
import de.cuioss.sheriff.oauth.quarkus.metrics.MetricIdentifier;
import de.cuioss.sheriff.oauth.quarkus.servlet.HttpServletRequestResolver;
import de.cuioss.tools.logging.CuiLogger;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

import java.util.*;

import static de.cuioss.sheriff.oauth.quarkus.OAuthSheriffQuarkusLogMessages.WARN.*;

/**
 * CDI producer for extracting and validating bearer tokens from HTTP Authorization headers.
 * <p>
 * This producer extracts bearer tokens from the HTTP Authorization header using HttpServletRequestResolver,
 * validates them using the configured TokenValidator, and checks for required scopes, roles, and groups.
 * <p>
 * The producer provides both service methods that return {@link BearerTokenResult} with comprehensive
 * validation information and a CDI producer method that returns {@link BearerTokenResult} directly for injection.
 * <p>
 * CDI injection usage example:
 * <pre>{@code
 * @RequestScoped
 * @Path("/api")
 * public class MyResource {
 *
 *     @Inject
 *     @BearerToken(requiredScopes = {"read", "write"})
 *     BearerTokenResult tokenResult;
 *
 *     @GET
 *     public Response getData() {
 *         if (tokenResult.isNotSuccessfullyAuthorized()) {
 *             return tokenResult.createErrorResponse();
 *         }
 *         AccessTokenContent token = tokenResult.getAccessTokenContent()
 *                 .orElseThrow(() -> new IllegalStateException("Token content missing after successful authorization"));
 *         // Use validated token - getSubject() returns String (nullable)
 *         return Response.ok(Objects.toString(token.getSubject(), "unknown")).build();
 *     }
 * }
 * }</pre>
 * <p>
 * <strong>Important:</strong> For Application-Scoped beans, use {@link jakarta.inject.Provider} since
 * BearerTokenResult is RequestScoped. The preferred way is for the containing class to be RequestScoped
 * as well, then you can use constructor injection:
 * <pre>{@code
 * @RequestScoped
 * public class MyService {
 *     private final BearerTokenResult tokenResult;
 *
 *     @Inject
 *     public MyService(@BearerToken(requiredRoles = {"admin"}) BearerTokenResult tokenResult) {
 *         this.tokenResult = tokenResult;
 *     }
 * }
 * }</pre>
 * <p>
 * For direct service usage, use the CDI producer method with annotations:
 * <pre>{@code
 * @Inject
 * @BearerToken(requiredScopes = {"read"}, requiredRoles = {"user"})
 * BearerTokenResult tokenResult;
 *
 * public Response someMethod() {
 *     if (tokenResult.isNotSuccessfullyAuthorized()) {
 *         return tokenResult.createErrorResponse();
 *     }
 *     AccessTokenContent content = tokenResult.getAccessTokenContent()
 *             .orElseThrow(() -> new IllegalStateException("Token content missing after successful authorization"));
 *     // Use validated token
 *     return Response.ok().build();
 * }
 * }</pre>
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@ApplicationScoped
public class BearerTokenProducer {

    private static final CuiLogger LOGGER = new CuiLogger(BearerTokenProducer.class);
    static final String BEARER_PREFIX = "Bearer ";

    private final TokenValidator tokenValidator;
    private final HttpServletRequestResolver servletObjectsResolver;

    @Inject
    public BearerTokenProducer(TokenValidator tokenValidator,
            @ServletObjectsResolver(ServletObjectsResolver.Variant.VERTX) HttpServletRequestResolver servletObjectsResolver) {
        this.tokenValidator = tokenValidator;
        this.servletObjectsResolver = servletObjectsResolver;
    }

    /**
     * Gets the current request's AccessTokenContent from the HTTP Authorization header.
     * This method handles tokens without specific requirements.
     *
     * @return Optional containing validated AccessTokenContent, or empty if validation fails
     */
    public Optional<AccessTokenContent> getAccessTokenContent() {
        return getBearerTokenResult(Collections.emptySet(), Collections.emptySet(), Collections.emptySet()).getAccessTokenContent();
    }

    /**
     * Gets comprehensive bearer token validation result with detailed status information.
     *
     * @param requiredScopes Required scopes for the token
     * @param requiredRoles Required roles for the token
     * @param requiredGroups Required groups for the token
     * @return BearerTokenResult containing detailed validation information
     */

    @Timed(value = MetricIdentifier.BEARERTOKEN.VALIDATION, description = "Bearer token validation duration")
    public BearerTokenResult getBearerTokenResult(
            Set<String> requiredScopes, Set<String> requiredRoles, Set<String> requiredGroups) {

        LOGGER.debug("Validating bearer token with required scopes: %s, roles: %s, groups: %s",
                requiredScopes, requiredRoles, requiredGroups);

        // Resolve headers once and reuse for both extraction and validation request
        Map<String, List<String>> headerMap = servletObjectsResolver.resolveHeaderMap();

        Optional<String> tokenResult = extractBearerTokenFromHeaderMap(headerMap);
        if (tokenResult.isEmpty()) {
            LOGGER.debug("Bearer token missing or invalid in Authorization header");
            return BearerTokenResult.noTokenGiven(requiredScopes, requiredRoles, requiredGroups);
        }

        String bearerToken = tokenResult.get();

        // Check for empty bearer token (RFC 6750 violation - should return 400 Bad Request)
        if (bearerToken.trim().isEmpty()) {
            LOGGER.debug("Bearer token is empty - invalid request per RFC 6750");
            return BearerTokenResult.invalidRequest(
                    "Bearer token is empty", requiredScopes, requiredRoles, requiredGroups);
        }

        try {
            // Pass HTTP headers through to the validation pipeline for DPoP and typ support
            AccessTokenContent tokenContent = tokenValidator.createAccessToken(
                    new AccessTokenRequest(bearerToken, headerMap));

            // Determine missing scopes, roles, and groups
            Set<String> missingScopes = tokenContent.determineMissingScopes(requiredScopes);
            Set<String> missingRoles = tokenContent.determineMissingRoles(requiredRoles);
            Set<String> missingGroups = tokenContent.determineMissingGroups(requiredGroups);

            if (missingScopes.isEmpty() && missingRoles.isEmpty() && missingGroups.isEmpty()) {
                LOGGER.debug("Bearer token validation successful");
                return BearerTokenResult.builder()
                        .status(BearerTokenStatus.FULLY_VERIFIED)
                        .accessTokenContent(tokenContent)
                        .build();
            } else {
                LOGGER.warn(BEARER_TOKEN_REQUIREMENTS_NOT_MET_DETAILED, missingScopes, missingRoles, missingGroups);
                return BearerTokenResult.builder()
                        .status(BearerTokenStatus.CONSTRAINT_VIOLATION)
                        .missingScopes(missingScopes)
                        .missingRoles(missingRoles)
                        .missingGroups(missingGroups)
                        .build();
            }
        } catch (TokenValidationException e) {
            // No need to use logger.warn, because precise logging already took place in the library
            LOGGER.debug(e, "Bearer token validation failed: %s", e.getMessage());
            return BearerTokenResult.parsingError(e, requiredScopes, requiredRoles, requiredGroups);
        }
    }


    /**
     * Extracts the bearer token from the provided HTTP header map.
     * <p>
     * Two-state return model:
     * <ul>
     *   <li>Optional.empty() - No token found, missing token, or infrastructure error</li>
     *   <li>Optional.of(token) - Token found (may be empty string for "Bearer ")</li>
     * </ul>
     *
     * @param headerMap the resolved HTTP header map
     * @return Optional containing the bearer token, or empty Optional if no token found
     */
    private Optional<String> extractBearerTokenFromHeaderMap(Map<String, List<String>> headerMap) {
        // Header names are normalized to lowercase by HttpServletRequestResolver per RFC 9113 (HTTP/2)
        // and RFC 7230 (HTTP/1.1). Direct lookup with lowercase key is sufficient.
        List<String> authHeaders = headerMap.get("authorization");

        if (authHeaders == null || authHeaders.isEmpty()) {
            LOGGER.debug("Authorization header not found in headerMap");
            return Optional.empty(); // No Authorization header - missing token
        }

        String authHeader = authHeaders.getFirst();
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty(); // Not a Bearer token - missing token
        }

        // Bearer token found - extract the token part (may be empty string for "Bearer ")
        String token = authHeader.substring(BEARER_PREFIX.length());
        return Optional.of(token);
    }


    /**
     * Produces the current request's BearerTokenResult as a CDI bean.
     * <p>
     * This producer method provides comprehensive bearer token validation information
     * including detailed status, error information, and the validated token content.
     * This method extracts the bearer token from the HTTP Authorization header
     * and validates it using the configured TokenValidator.
     * <p>
     * The producer method is @Dependent scoped, which means it will be created fresh
     * for each injection point. Unlike the AccessTokenContent producer, this method
     * always returns a valid BearerTokenResult object containing detailed information
     * about the validation outcome.
     * <p>
     * Usage example:
     * <pre>{@code
     * @Inject
     * @BearerToken(requiredScopes = {"read", "write"})
     * BearerTokenResult tokenResult;
     *
     * public void someMethod() {
     *     switch (tokenResult.getStatus()) {
     *         case FULLY_VERIFIED:
     *             AccessTokenContent token = tokenResult.getAccessTokenContent().get();
     *             // Use validated token
     *             break;
     *         case PARSING_ERROR:
     *             // Handle parsing errors with detailed information
     *             EventType eventType = tokenResult.getEventType().get();
     *             String message = tokenResult.getMessage().get();
     *             break;
     *         default:
     *             // Handle other validation failures
     *     }
     * }
     * }</pre>
     *
     * @param injectionPoint the CDI injection point containing the BearerToken annotation
     * @return the BearerTokenResult containing detailed validation information
     */
   
    @Produces
    @BearerToken
    public BearerTokenResult produceBearerTokenResult(InjectionPoint injectionPoint) {
        BearerToken annotation = injectionPoint.getAnnotated().getAnnotation(BearerToken.class);

        Set<String> requiredScopes = annotation != null ? Set.copyOf(List.of(annotation.requiredScopes())) : Collections.emptySet();
        Set<String> requiredRoles = annotation != null ? Set.copyOf(List.of(annotation.requiredRoles())) : Collections.emptySet();
        Set<String> requiredGroups = annotation != null ? Set.copyOf(List.of(annotation.requiredGroups())) : Collections.emptySet();

        return getBearerTokenResult(requiredScopes, requiredRoles, requiredGroups);
    }
}