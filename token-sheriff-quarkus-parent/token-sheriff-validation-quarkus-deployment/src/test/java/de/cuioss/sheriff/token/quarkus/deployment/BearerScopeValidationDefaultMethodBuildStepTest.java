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
package de.cuioss.sheriff.token.quarkus.deployment;

import de.cuioss.sheriff.token.quarkus.annotation.BearerAuth;
import org.jboss.jandex.Index;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the interface-default-method branch of the deployment-time scope check
 * ({@link TokenSheriffProcessor#findScopeViolations}): a method-level {@code @BearerAuth} on an interface
 * default method is reached and checked on its own, and - the documented limit of the check - it is not merged
 * with the class-level {@code @BearerAuth} of the implementing bean class, so a merged set over the bound
 * passes the deployment. The request path answers such a set with the defined 500 response.
 */
@DisplayName("Deployment-time scope check of a @BearerAuth on an interface default method")
class BearerScopeValidationDefaultMethodBuildStepTest {

    private static final String NOT_A_SCOPE_TOKEN = "is not an RFC 6749 section 3.3 scope-token";

    @Test
    @DisplayName("Should reach an interface default method and refuse its scope name outside the grammar")
    void shouldRefuseInvalidScopeOnDefaultMethod() throws Exception {
        List<String> violations = TokenSheriffProcessor
                .findScopeViolations(Index.of(InvalidDefaultMethod.class, InvalidDefaultMethodBean.class));
        List<String> control = TokenSheriffProcessor
                .findScopeViolations(Index.of(ValidDefaultMethod.class, ValidDefaultMethodBean.class));

        assertEquals(1, violations.size(), "exactly the default method must be refused: " + violations);
        assertTrue(violations.getFirst().startsWith("@BearerAuth on method " + InvalidDefaultMethod.class.getName()
                + "#endpoint"), violations.getFirst());
        assertTrue(violations.getFirst().contains(NOT_A_SCOPE_TOKEN), violations.getFirst());
        assertEquals(List.of(), control, "control: a default method with valid scopes is not refused");
    }

    @Test
    @DisplayName("Documented limit: a default method is not merged with the implementing bean class's scopes")
    void shouldNotMergeDefaultMethodWithBeanClassScopes() throws Exception {
        List<String> violations = TokenSheriffProcessor
                .findScopeViolations(Index.of(ValidDefaultMethod.class, OverBoundWhenMerged.class));

        assertEquals(List.of(), violations,
                "32 default-method scopes plus 33 class-level scopes are 65 at run time, yet pass the deployment");
    }

    /** An interface whose default method declares a scope name holding a space. */
    interface InvalidDefaultMethod {

        @BearerAuth(requiredScopes = {"read", "has space"})
        default String endpoint() {
            return "refused";
        }
    }

    /** The bean class running {@link InvalidDefaultMethod#endpoint()}. */
    static class InvalidDefaultMethodBean implements InvalidDefaultMethod {
    }

    /** An interface whose default method declares 32 valid scopes. */
    interface ValidDefaultMethod {

        @BearerAuth(requiredScopes = {"m01", "m02", "m03", "m04", "m05", "m06", "m07", "m08", "m09", "m10", "m11",
                "m12", "m13", "m14", "m15", "m16", "m17", "m18", "m19", "m20", "m21", "m22", "m23", "m24", "m25",
                "m26", "m27", "m28", "m29", "m30", "m31", "m32"})
        default String endpoint() {
            return "default";
        }
    }

    /** The bean class running {@link ValidDefaultMethod#endpoint()} without a class-level annotation. */
    static class ValidDefaultMethodBean implements ValidDefaultMethod {
    }

    /** Runs the 32-scope default method and adds 33 class-level scopes: 65 when the interceptor merges them. */
    @BearerAuth(requiredScopes = {"c01", "c02", "c03", "c04", "c05", "c06", "c07", "c08", "c09", "c10", "c11",
            "c12", "c13", "c14", "c15", "c16", "c17", "c18", "c19", "c20", "c21", "c22", "c23", "c24", "c25", "c26",
            "c27", "c28", "c29", "c30", "c31", "c32", "c33"})
    static class OverBoundWhenMerged implements ValidDefaultMethod {
    }
}
