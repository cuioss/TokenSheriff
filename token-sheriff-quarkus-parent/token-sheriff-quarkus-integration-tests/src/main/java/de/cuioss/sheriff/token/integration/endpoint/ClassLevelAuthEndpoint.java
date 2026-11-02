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
 * Integration endpoint protected by a class-level {@code @BearerAuth} requirement that a valid test
 * token satisfies (role {@code user}).
 * <p>
 * It exercises the three ways a class-level requirement reaches a resource method: a method without an
 * annotation of its own ({@code /class-only}), a method whose own {@code @BearerAuth} adds a scope to
 * the class requirement ({@code /union}), and a method inherited from
 * {@link AbstractClassLevelAuthEndpoint} ({@code /inherited}).
 */
@Path("/jwt/class-level")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RegisterForReflection
@RunOnVirtualThread
@BearerAuth(requiredRoles = {"user"})
public class ClassLevelAuthEndpoint extends AbstractClassLevelAuthEndpoint {

    /**
     * Protected only by the class-level requirement.
     *
     * @return a success response once the class-level requirement is satisfied
     */
    @GET
    @Path("/class-only")
    public Response classOnly() {
        return Response.ok(new ValidationResponse(true, "Class-level validation successful (class-only)")).build();
    }

    /**
     * Protected by the union of the class-level role requirement and the method-level scope requirement.
     *
     * @return a success response once both requirements are satisfied
     */
    @GET
    @Path("/union")
    @BearerAuth(requiredScopes = {"read"})
    public Response union() {
        return Response.ok(new ValidationResponse(true, "Class-level validation successful (union)")).build();
    }
}
