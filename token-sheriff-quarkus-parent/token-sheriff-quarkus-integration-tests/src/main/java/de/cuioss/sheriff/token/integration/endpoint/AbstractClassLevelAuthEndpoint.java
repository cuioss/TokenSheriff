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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Shared base of the class-level {@code @BearerAuth} integration endpoints.
 * <p>
 * It declares one resource method without any {@code @BearerAuth} annotation of its own. When a
 * subclass carries a class-level {@code @BearerAuth}, the interceptor sees an invocation whose method is
 * declared here while the intercepted bean is the subclass, so it must resolve the requirement from the
 * bean class rather than from the method's declaring class.
 */
public abstract class AbstractClassLevelAuthEndpoint {

    /**
     * Resource method inherited unchanged by every subclass; protected only by the subclass's
     * class-level {@code @BearerAuth}.
     *
     * @return a success response once the class-level requirement is satisfied
     */
    @GET
    @Path("/inherited")
    @Produces(MediaType.APPLICATION_JSON)
    public Response inherited() {
        return Response.ok(new ValidationResponse(true, "Class-level validation successful (inherited)")).build();
    }
}
