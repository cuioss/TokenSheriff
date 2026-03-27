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

import de.cuioss.sheriff.oauth.quarkus.annotation.BearerAuth;
import de.cuioss.sheriff.oauth.quarkus.producer.BearerTokenProducer;
import de.cuioss.sheriff.oauth.quarkus.producer.BearerTokenResult;
import de.cuioss.tools.logging.CuiLogger;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.WebApplicationException;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Interceptor for declarative Bearer token validation at method level.
 * <p>
 * This interceptor provides automatic Bearer token validation and error handling
 * when methods or classes are annotated with {@link BearerAuth}. It follows
 * Quarkus 2025 best practices for security interceptors.
 * <p>
 * The interceptor:
 * <ul>
 *   <li>Extracts annotation parameters (requiredScopes, requiredRoles, requiredGroups)</li>
 *   <li>Delegates validation to {@link BearerTokenProducer}</li>
 *   <li>Throws WebApplicationException with error response for failed validation</li>
 *   <li>Proceeds with method execution if validation succeeds</li>
 *   <li>Makes validated token available via CDI event for intercepted methods</li>
 * </ul>
 * <p>
 * Usage example:
 * <pre>{@code
 * @Path("/api")
 * public class SecureResource {
 *
 *     @GET
 *     @BearerAuth(requiredScopes = {"read"}, requiredRoles = {"user"})
 *     public Response getData() {
 *         // Only business logic - security handled by interceptor
 *         return Response.ok(data).build();
 *     }
 * }
 * }</pre>
 * <p>
 * <strong>Priority:</strong> PLATFORM_BEFORE + 200 ensures security validation
 * runs after platform infrastructure but before business logic interceptors.
 * <p>
 * <strong>Performance:</strong> Minimal object allocation, reuses existing
 * BearerTokenProducer infrastructure, fast-path execution for successful validation.
 * <p>
 * <strong>Error Handling:</strong>
 * <ul>
 *   <li>Failed validation throws WebApplicationException with appropriate error response</li>
 *   <li>Works for any method return type (Response, String, DTO, etc.)</li>
 *   <li>Successful validation allows method to proceed and access token via CDI event</li>
 * </ul>
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@BearerAuth
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 200)
@RegisterForReflection
public class BearerTokenInterceptor {

    private static final CuiLogger LOGGER = new CuiLogger(BearerTokenInterceptor.class);

    private final BearerTokenProducer bearerTokenProducer;

    @Inject
    public BearerTokenInterceptor(BearerTokenProducer bearerTokenProducer) {
        this.bearerTokenProducer = bearerTokenProducer;
    }

    /**
     * Intercepts method calls to validate bearer tokens declaratively.
     * <p>
     * This method follows the interceptor pattern for security validation:
     * <ol>
     *   <li>Extract annotation parameters from method or class level</li>
     *   <li>Delegate to BearerTokenProducer for validation</li>
     *   <li>If validation fails, throw WebApplicationException with error response</li>
     *   <li>If validation succeeds, proceed with method execution</li>
     * </ol>
     *
     * @param ctx the invocation context containing method and annotation information
     * @return the result of the intercepted method
     * @throws jakarta.ws.rs.WebApplicationException if validation fails
     * @throws Exception if the intercepted method throws an exception
     */
    @AroundInvoke
    public Object validateBearerToken(InvocationContext ctx) throws Exception {
        // Extract annotation from method or class level
        BearerAuth annotation = extractAnnotation(ctx);
        if (annotation == null) {
            // Fail closed: missing annotation means security configuration error
            throw new IllegalStateException(
                    "@BearerAuth annotation not found on method '%s' or its declaring class. "
                            .formatted(ctx.getMethod().getName())
                            + "Security interceptor cannot proceed without authentication configuration.");
        }

        // Extract requirements from annotation
        Set<String> requiredScopes = annotation.requiredScopes().length > 0
                ? Set.copyOf(List.of(annotation.requiredScopes()))
                : Collections.emptySet();
        Set<String> requiredRoles = annotation.requiredRoles().length > 0
                ? Set.copyOf(List.of(annotation.requiredRoles()))
                : Collections.emptySet();
        Set<String> requiredGroups = annotation.requiredGroups().length > 0
                ? Set.copyOf(List.of(annotation.requiredGroups()))
                : Collections.emptySet();

        LOGGER.debug("Validating bearer token with scopes: %s, roles: %s, groups: %s",
                requiredScopes, requiredRoles, requiredGroups);

        // Delegate validation to BearerTokenProducer
        BearerTokenResult result = bearerTokenProducer.getBearerTokenResult(
                requiredScopes, requiredRoles, requiredGroups);

        // Handle validation failure
        if (result.isNotSuccessfullyAuthorized()) {
            LOGGER.debug("Bearer token validation failed: %s", result.getStatus());
            throw new WebApplicationException(result.createErrorResponse());
        }

        // Validation successful - proceed with method execution
        // Token is available via @BearerToken injection if needed
        return ctx.proceed();
    }

    /**
     * Extracts the BearerAuth annotation from the invocation context.
     * Checks both method level and class level annotations (method takes precedence).
     *
     * @param ctx the invocation context
     * @return the BearerAuth annotation, or null if not found
     */
    private BearerAuth extractAnnotation(InvocationContext ctx) {
        // Check method level first
        BearerAuth annotation = ctx.getMethod().getAnnotation(BearerAuth.class);
        if (annotation != null) {
            return annotation;
        }

        // Fallback to class level
        return ctx.getTarget().getClass().getAnnotation(BearerAuth.class);
    }
}
