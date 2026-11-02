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
package de.cuioss.sheriff.token.integration.endpoint;

import de.cuioss.sheriff.token.integration.endpoint.JwtValidationEndpoint.ValidationResponse;
import de.cuioss.sheriff.token.quarkus.annotation.BearerAuth;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Integration endpoint protected by a class-level {@code @BearerAuth} requirement that no test token
 * satisfies (role {@code nonexistent-role}).
 * <p>
 * Every call must be refused. The {@code /union} method's own {@code @BearerAuth} asks only for a scope
 * a valid token carries, so it is refused only when the class-level requirement is honoured alongside
 * the method-level one; the {@code /inherited} method from {@link AbstractClassLevelAuthEndpoint} is
 * refused only when the requirement is resolved from the bean class.
 */
@Path("/jwt/class-level-unsatisfied")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RegisterForReflection
@RunOnVirtualThread
@BearerAuth(requiredRoles = {"nonexistent-role"})
public class ClassLevelUnsatisfiedAuthEndpoint extends AbstractClassLevelAuthEndpoint {

    /**
     * Protected by the union of the unsatisfiable class-level role requirement and a satisfiable
     * method-level scope requirement, so a correct interceptor never lets a call reach this body. A
     * success response here is the observable symptom of a dropped class-level requirement.
     *
     * @return a success response, reached only when the class-level requirement was dropped
     */
    @GET
    @Path("/union")
    @BearerAuth(requiredScopes = {"read"})
    public Response union() {
        return Response.ok(new ValidationResponse(true, "Class-level requirement was not enforced (union)")).build();
    }
}
