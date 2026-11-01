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
import de.cuioss.sheriff.token.quarkus.producer.BearerTokenProducer;
import de.cuioss.sheriff.token.quarkus.producer.BearerTokenResult;
import de.cuioss.tools.logging.CuiLogger;
import io.quarkus.arc.Subclass;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.WebApplicationException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Interceptor for declarative Bearer token validation at method level.
 * <p>
 * This interceptor provides automatic Bearer token validation and error handling
 * when methods or classes are annotated with {@link BearerAuth}. It follows
 * Quarkus 2025 best practices for security interceptors.
 * <p>
 * The interceptor:
 * <ul>
 *   <li>Extracts annotation parameters (requiredScopes, requiredRoles, requiredGroups) from the
 *       method and from the bean class, and enforces their union</li>
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
 * <strong>Class-level requirements:</strong> a class-level {@code @BearerAuth} is not read from runtime
 * reflection alone, because inside a native image the annotation of the bean class is not reliably
 * reachable that way. The deployment processor records every class-level declaration of the application
 * at build time through {@link BearerAuthRecorder}, and the interceptor merges that record with the
 * reflective and interceptor-binding sources (see {@code extractMergedAuth}). A requirement found by any
 * source is enforced; when no source yields one the interceptor refuses with an
 * {@link IllegalStateException}.
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
     *   <li>Extract and merge annotation parameters from method and class level</li>
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
        // Extract and merge annotations from method and class level
        BearerAuthRequirement mergedAuth = extractMergedAuth(ctx);

        LOGGER.debug("Validating bearer token with scopes: %s, roles: %s, groups: %s",
                mergedAuth.requiredScopes(), mergedAuth.requiredRoles(), mergedAuth.requiredGroups());

        // Delegate validation to BearerTokenProducer
        BearerTokenResult result = bearerTokenProducer.getBearerTokenResult(
                mergedAuth.requiredScopes(), mergedAuth.requiredRoles(), mergedAuth.requiredGroups());

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
     * Collects every {@link BearerAuth} requirement that applies to the intercepted invocation
     * and merges them into one: the union of all requiredScopes, requiredRoles and requiredGroups.
     * <p>
     * The requirement is read from four sources, because no single one is reliable for every
     * declaration inside a native image:
     * <ol>
     *   <li>the {@code @BearerAuth} on the invoked method, read reflectively from
     *       {@link InvocationContext#getMethod()};</li>
     *   <li>the class-level requirement the Quarkus build recorded for the bean class in
     *       {@link ClassLevelBearerAuthRegistry} from index data, which needs no runtime reflection;</li>
     *   <li>the {@code @BearerAuth} on the bean class, read reflectively — inside a native image this
     *       may be absent even when the class is annotated, so it only ever adds to the other sources;</li>
     *   <li>the {@code @BearerAuth} interceptor bindings the container exposes through
     *       {@link InvocationContext#getInterceptorBindings()}. They carry the class-level declaration
     *       for a method without an annotation of its own, but CDI lets a method-level binding replace
     *       the class-level one, so they cannot supply the class level of an annotated method.</li>
     * </ol>
     * The bean class is resolved by {@link #resolveBeanClass(Object)}; only that single class is
     * consulted at class level, the hierarchy above it is not walked. Taking the union means that a
     * requirement found by any source is enforced: a source that misses a declaration can never
     * remove a requirement another source found.
     *
     * @param ctx the invocation context
     * @return the merged authentication requirements
     * @throws IllegalStateException if no {@code @BearerAuth} requirement is found by any source
     */
    private BearerAuthRequirement extractMergedAuth(InvocationContext ctx) {
        Method method = ctx.getMethod();
        Class<?> beanClass = resolveBeanClass(ctx.getTarget());

        List<BearerAuthRequirement> requirements = new ArrayList<>();
        addIfPresent(requirements, method.getAnnotation(BearerAuth.class));
        ClassLevelBearerAuthRegistry.lookup(beanClass.getName()).ifPresent(requirements::add);
        addIfPresent(requirements, beanClass.getAnnotation(BearerAuth.class));
        for (Annotation binding : ctx.getInterceptorBindings()) {
            if (binding instanceof BearerAuth bearerAuth) {
                requirements.add(BearerAuthRequirement.of(bearerAuth));
            }
        }

        // Fail closed: missing annotation means security configuration error
        return requirements.stream()
                .reduce(BearerAuthRequirement::union)
                .orElseThrow(() -> new IllegalStateException(
                        "@BearerAuth annotation not found on method '%s' or its declaring class. "
                                .formatted(method.getName())
                                + "Security interceptor cannot proceed without authentication configuration."));
    }

    private static void addIfPresent(List<BearerAuthRequirement> requirements, BearerAuth annotation) {
        if (annotation != null) {
            requirements.add(BearerAuthRequirement.of(annotation));
        }
    }

    /**
     * Resolves the bean class of an intercepted target. ArC intercepts a bean by
     * instantiating a generated subclass that implements {@link Subclass}; for such a
     * target the original bean class is its direct superclass. Any other target is its
     * own bean class. The resolved class is the key for the build-time
     * {@link ClassLevelBearerAuthRegistry} lookup and the class whose annotation is read
     * reflectively.
     *
     * @param target the intercepted target instance
     * @return the bean class whose class-level requirement applies to the target
     */
    private static Class<?> resolveBeanClass(Object target) {
        Class<?> targetClass = target.getClass();
        if (target instanceof Subclass) {
            return targetClass.getSuperclass();
        }
        return targetClass;
    }
}
