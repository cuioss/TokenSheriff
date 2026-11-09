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
import de.cuioss.sheriff.token.quarkus.annotation.BearerToken;
import de.cuioss.sheriff.token.quarkus.producer.BearerTokenResult;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Integration endpoint protected by a class-level {@code @BearerAuth} requirement that a valid test
 * token satisfies (role {@code user}), deliberately declared <strong>without</strong>
 * {@code @RegisterForReflection}.
 * <p>
 * The sibling class-level endpoints are registered for reflection, so in a native image they cannot show
 * whether the interceptor resolves the class-level requirement without reflection metadata. This one can:
 * if the class-level lookup needed such metadata, the native run would either refuse the satisfied path or
 * let a request without a token through. It exposes one path the class requirement alone protects
 * ({@code /satisfied}) and one path whose own {@code @BearerAuth} adds a requirement no test token meets
 * ({@code /unsatisfied}); the {@code /inherited} method of {@link AbstractClassLevelAuthEndpoint} is
 * protected by the class requirement as well.
 */
@Path("/jwt/class-level-unregistered")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@BearerAuth(requiredRoles = {"user"})
public class ClassLevelUnregisteredAuthEndpoint extends AbstractClassLevelAuthEndpoint {

    private final Instance<BearerTokenResult> bearerToken;

    public ClassLevelUnregisteredAuthEndpoint(@BearerToken Instance<BearerTokenResult> bearerToken) {
        this.bearerToken = bearerToken;
    }

    /**
     * Protected only by the class-level requirement.
     * <p>
     * The response carries the token's {@code azp} claim, which is mapped by the strict string mapper,
     * so a native run also shows that the mapped claim survives native compilation.
     *
     * @return a success response once the class-level requirement is satisfied
     */
    @GET
    @Path("/satisfied")
    public Response satisfied() {
        String authorizedParty = bearerToken.get().getAccessTokenContent()
                .flatMap(token -> token.getClaimOption(ClaimName.AUTHORIZED_PARTY))
                .map(ClaimValue::getOriginalString)
                .orElse(JwtValidationEndpoint.NOT_PRESENT);
        return Response.ok(new ValidationResponse(true, "Class-level validation successful (unregistered)",
                Map.of("azp", authorizedParty))).build();
    }

    /**
     * Protected by the union of the class-level role requirement and an unsatisfiable method-level role
     * requirement, so a correct interceptor never lets a call reach this body. A success response here is
     * the observable symptom of a dropped requirement.
     *
     * @return a success response, reached only when a requirement was dropped
     */
    @GET
    @Path("/unsatisfied")
    @BearerAuth(requiredRoles = {"nonexistent-role"})
    public Response unsatisfied() {
        return Response.ok(new ValidationResponse(true, "Requirement was not enforced (unregistered)")).build();
    }
}
