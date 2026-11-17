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
import de.cuioss.sheriff.token.quarkus.annotation.BearerToken;
import de.cuioss.sheriff.token.quarkus.producer.BearerTokenResult;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import jakarta.enterprise.inject.spi.DeploymentException;
import org.jboss.jandex.Index;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the deployment-time refusal of {@code requiredScopes} that an RFC 6750 {@code insufficient_scope}
 * challenge cannot carry ({@link TokenSheriffProcessor#validateAnnotationScopes}).
 * <p>
 * The fixtures are indexed into a Jandex index of their own and checked through the scan the build step
 * runs and through the build step itself. The test runs on the application class loader together with the
 * processor, so no call crosses into a Quarkus class loader. That the valid fixtures also build and boot a
 * Quarkus application is pinned by {@link BearerScopeValidationBootTest}.
 */
@DisplayName("Deployment-time validation of annotation-declared required scopes")
class BearerScopeValidationBuildStepTest {

    private static final String NOT_A_SCOPE_TOKEN = "is not an RFC 6749 section 3.3 scope-token";

    private static final String OVER_BOUND = "at most 64 scope tokens";

    @Test
    @DisplayName("Should accept valid scopes and a class plus method union of exactly 64")
    void shouldAcceptValidScopes() throws Exception {
        assertEquals(List.of(), TokenSheriffProcessor.findScopeViolations(
                        Index.of(ValidScopesResource.class, ValidInjectionPoint.class)),
                "valid scope names and a union of exactly 64 distinct scopes must not be refused");
    }

    @Test
    @DisplayName("Should refuse a method-level scope name outside the grammar, naming the method")
    void shouldRefuseInvalidMethodLevelScope() throws Exception {
        List<String> violations = TokenSheriffProcessor.findScopeViolations(Index.of(InvalidMethodLevelScope.class));

        assertEquals(1, violations.size(), "exactly the method-level annotation must be refused: " + violations);
        assertTrue(violations.getFirst().startsWith("@BearerAuth on method "
                + InvalidMethodLevelScope.class.getName() + "#endpoint "), violations.getFirst());
        assertTrue(violations.getFirst().contains(NOT_A_SCOPE_TOKEN), violations.getFirst());
    }

    @Test
    @DisplayName("Should refuse a class-level scope name outside the grammar, naming the class")
    void shouldRefuseInvalidClassLevelScope() throws Exception {
        List<String> violations = TokenSheriffProcessor.findScopeViolations(Index.of(InvalidClassLevelScope.class));

        assertEquals(1, violations.size(), "exactly the class-level annotation must be refused: " + violations);
        assertTrue(violations.getFirst().startsWith("@BearerAuth on class "
                + InvalidClassLevelScope.class.getName() + " "), violations.getFirst());
        assertTrue(violations.getFirst().contains(NOT_A_SCOPE_TOKEN), violations.getFirst());
    }

    @Test
    @DisplayName("Should refuse a scope name outside the grammar on a @BearerToken injection point, naming the field")
    void shouldRefuseInvalidInjectionPointScope() throws Exception {
        List<String> violations = TokenSheriffProcessor
                .findScopeViolations(Index.of(InvalidInjectionPointScope.class));

        assertEquals(1, violations.size(), "exactly the injection point must be refused: " + violations);
        assertTrue(violations.getFirst().startsWith("@BearerToken on field "
                + InvalidInjectionPointScope.class.getName() + "#tokenResult "), violations.getFirst());
        assertTrue(violations.getFirst().contains(NOT_A_SCOPE_TOKEN), violations.getFirst());
    }

    @Test
    @DisplayName("Should refuse a class plus method union of 65 distinct scopes, naming the method")
    void shouldRefuseUnionOverBound() throws Exception {
        List<String> violations = TokenSheriffProcessor.findScopeViolations(Index.of(ScopeUnionOverBound.class));

        // The class-level annotation alone holds 33 scopes and stays valid; only the merged method is refused.
        assertEquals(1, violations.size(), "exactly the merged method must be refused: " + violations);
        assertTrue(violations.getFirst().startsWith("@BearerAuth on method "
                + ScopeUnionOverBound.class.getName() + "#endpoint "), violations.getFirst());
        assertTrue(violations.getFirst().contains(OVER_BOUND), violations.getFirst());
    }

    @Test
    @DisplayName("Should fail the deployment with one DeploymentException per refused annotation")
    void shouldProduceDeploymentErrors() throws Exception {
        Index index = Index.of(InvalidMethodLevelScope.class, InvalidClassLevelScope.class,
                InvalidInjectionPointScope.class, ScopeUnionOverBound.class);
        List<ValidationErrorBuildItem> produced = new ArrayList<>();

        new TokenSheriffProcessor().validateAnnotationScopes(null, new CombinedIndexBuildItem(index, index),
                produced::add);

        assertEquals(1, produced.size(), "the refusals must be produced as one validation error item");
        List<Throwable> errors = produced.getFirst().getValues();
        assertEquals(4, errors.size(), "one error per refused annotation: " + errors);
        errors.forEach(error -> assertInstanceOf(DeploymentException.class, error));
        List<String> messages = errors.stream().map(Throwable::getMessage).toList();
        assertAll("every refused member is named",
                () -> assertNamed(messages, "method " + InvalidMethodLevelScope.class.getName() + "#endpoint"),
                () -> assertNamed(messages, "class " + InvalidClassLevelScope.class.getName()),
                () -> assertNamed(messages, "field " + InvalidInjectionPointScope.class.getName() + "#tokenResult"),
                () -> assertNamed(messages, "method " + ScopeUnionOverBound.class.getName() + "#endpoint"));
    }

    @Test
    @DisplayName("Should produce no deployment error for valid scopes")
    void shouldProduceNoDeploymentErrorForValidScopes() throws Exception {
        Index index = Index.of(ValidScopesResource.class, ValidInjectionPoint.class);
        List<ValidationErrorBuildItem> produced = new ArrayList<>();

        new TokenSheriffProcessor().validateAnnotationScopes(null, new CombinedIndexBuildItem(index, index),
                produced::add);

        assertEquals(List.of(), produced, "valid scopes must not fail the deployment");
    }

    private static void assertNamed(List<String> messages, String member) {
        assertTrue(messages.stream().anyMatch(message -> message.contains(" on " + member + " ")),
                "no error names " + member + ": " + messages);
    }

    /**
     * Valid scope names: 32 distinct scopes at class level and 32 further ones at method level, merged to
     * exactly the bound of 64.
     */
    @BearerAuth(requiredScopes = {"c01", "c02", "c03", "c04", "c05", "c06", "c07", "c08", "c09", "c10", "c11",
            "c12", "c13", "c14", "c15", "c16", "c17", "c18", "c19", "c20", "c21", "c22", "c23", "c24", "c25", "c26",
            "c27", "c28", "c29", "c30", "c31", "c32"})
    static class ValidScopesResource {

        @BearerAuth(requiredScopes = {"m01", "m02", "m03", "m04", "m05", "m06", "m07", "m08", "m09", "m10", "m11",
                "m12", "m13", "m14", "m15", "m16", "m17", "m18", "m19", "m20", "m21", "m22", "m23", "m24", "m25",
                "m26", "m27", "m28", "m29", "m30", "m31", "m32"})
        public String endpoint() {
            return "ok";
        }
    }

    /**
     * A {@code @BearerToken} injection point with valid scope names, including the full range of
     * {@code scope-token} characters.
     */
    static class ValidInjectionPoint {

        @BearerToken(requiredScopes = {"read", "write", "urn:example:scope/a.b-c_d~e!#$%&'()*+,;<=>?@[]^`{|}"})
        BearerTokenResult tokenResult;
    }

    /**
     * A method-level {@code @BearerAuth} whose scope name holds a space.
     */
    static class InvalidMethodLevelScope {

        @BearerAuth(requiredScopes = {"read", "has space"})
        public String endpoint() {
            return "refused";
        }
    }

    /**
     * A class-level {@code @BearerAuth} whose scope name holds a double quote.
     */
    @BearerAuth(requiredScopes = {"quote\"d"})
    static class InvalidClassLevelScope {
    }

    /**
     * A {@code @BearerToken} injection point whose scope name holds a backslash.
     */
    static class InvalidInjectionPointScope {

        @BearerToken(requiredScopes = {"back\\slash"})
        BearerTokenResult tokenResult;
    }

    /**
     * Valid scope names whose class-level plus method-level union holds 65 distinct scopes, one past the bound:
     * 33 at class level and 32 further ones at method level.
     */
    @BearerAuth(requiredScopes = {"c01", "c02", "c03", "c04", "c05", "c06", "c07", "c08", "c09", "c10", "c11",
            "c12", "c13", "c14", "c15", "c16", "c17", "c18", "c19", "c20", "c21", "c22", "c23", "c24", "c25", "c26",
            "c27", "c28", "c29", "c30", "c31", "c32", "c33"})
    static class ScopeUnionOverBound {

        @BearerAuth(requiredScopes = {"m01", "m02", "m03", "m04", "m05", "m06", "m07", "m08", "m09", "m10", "m11",
                "m12", "m13", "m14", "m15", "m16", "m17", "m18", "m19", "m20", "m21", "m22", "m23", "m24", "m25",
                "m26", "m27", "m28", "m29", "m30", "m31", "m32"})
        public String endpoint() {
            return "refused";
        }
    }
}
