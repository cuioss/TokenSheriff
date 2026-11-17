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

import de.cuioss.sheriff.token.commons.http.BearerChallenge;
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
import java.util.Set;

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

    private static final String OVER_LENGTH = "at most 8192 characters long";

    private static final String DIGITS = "0123456789";

    private static final String CHARS_100 = DIGITS + DIGITS + DIGITS + DIGITS + DIGITS + DIGITS + DIGITS + DIGITS
            + DIGITS + DIGITS;

    private static final String CHARS_1000 = CHARS_100 + CHARS_100 + CHARS_100 + CHARS_100 + CHARS_100 + CHARS_100
            + CHARS_100 + CHARS_100 + CHARS_100 + CHARS_100;

    private static final String CHARS_4000 = CHARS_1000 + CHARS_1000 + CHARS_1000 + CHARS_1000;

    /**
     * Two scopes joined to 8103 characters: a bare challenge is 8146 characters long, the one sent with the
     * realm and the error_description 8268.
     */
    private static final String OVER_LENGTH_A = "a" + CHARS_4000;

    private static final String OVER_LENGTH_B = "b" + CHARS_4000 + CHARS_100;

    private static final Set<String> OVER_LENGTH_SCOPES = Set.of(OVER_LENGTH_A, OVER_LENGTH_B);

    /**
     * Two scopes joined to 8003 characters: the challenge sent with the realm and the error_description is
     * 8168 characters long.
     */
    private static final String WITHIN_LENGTH_B = "b" + CHARS_4000;

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
    @DisplayName("Should refuse scopes that fit a bare challenge but not the one sent with realm and description")
    void shouldRefuseScopesTooLongOnceRealmAndDescriptionAreAdded() throws Exception {
        assertDoesNotThrow(() -> BearerChallenge.insufficientScope().scopes(OVER_LENGTH_SCOPES).build(),
                "precondition: the scopes alone must fit a challenge without realm and error_description");

        List<String> violations = TokenSheriffProcessor
                .findScopeViolations(Index.of(ChallengeOverLengthWithRealm.class));

        assertEquals(1, violations.size(), "exactly the class-level annotation must be refused: " + violations);
        assertTrue(violations.getFirst().startsWith("@BearerAuth on class "
                + ChallengeOverLengthWithRealm.class.getName() + " "), violations.getFirst());
        assertTrue(violations.getFirst().contains(OVER_LENGTH), violations.getFirst());
    }

    @Test
    @DisplayName("Should accept scopes whose challenge with realm and description stays within the length bound")
    void shouldAcceptScopesWithinLengthWithRealmAndDescription() throws Exception {
        assertEquals(List.of(), TokenSheriffProcessor.findScopeViolations(
                        Index.of(ChallengeWithinLengthWithRealm.class)),
                "a challenge of at most 8192 characters with realm and error_description must not be refused");
    }

    @Test
    @DisplayName("Should merge an inherited method with the bean class's own class-level scopes, naming both")
    void shouldRefuseInheritedMethodMergedWithBeanClassScopes() throws Exception {
        List<String> violations = TokenSheriffProcessor
                .findScopeViolations(Index.of(InheritedEndpointBase.class, InheritingOverBound.class));

        // The base class runs the method with its 32 scopes alone; only the subclass's union of 65 is refused.
        assertEquals(1, violations.size(), "exactly the merge in the subclass must be refused: " + violations);
        assertTrue(violations.getFirst().startsWith("@BearerAuth on method "
                + InheritedEndpointBase.class.getName() + "#endpoint together with the class-level @BearerAuth"
                + " of bean class " + InheritingOverBound.class.getName() + " "), violations.getFirst());
        assertTrue(violations.getFirst().contains(OVER_BOUND), violations.getFirst());
    }

    @Test
    @DisplayName("Should accept inherited methods whose run-time union fits, ignoring a non-bean declaring class")
    void shouldAcceptInheritedMethodsWhoseRunTimeUnionFits() {
        assertAll("inherited methods merged as the interceptor merges them",
                () -> assertEquals(List.of(), TokenSheriffProcessor.findScopeViolations(
                                Index.of(InheritedEndpointBase.class, InheritingAtBound.class)),
                        "a subclass union of exactly 64 must not be refused"),
                () -> assertEquals(List.of(), TokenSheriffProcessor.findScopeViolations(
                                Index.of(AbstractDeclaringBase.class, PlainSubclass.class)),
                        "the class-level scopes of an abstract declaring class never reach the request path"));
    }

    @Test
    @DisplayName("Should not merge a subclass that overrides the method, refusing one that inherits it")
    void shouldSkipOverridingSubclass() throws Exception {
        List<String> overridden = TokenSheriffProcessor
                .findScopeViolations(Index.of(LengthBase.class, OverridingSubclass.class));
        List<String> inherited = TokenSheriffProcessor
                .findScopeViolations(Index.of(LengthBase.class, InheritingSubclass.class));

        assertEquals(List.of(), overridden, "an overriding subclass never runs the annotated method");
        assertMergedOverLength(inherited, InheritingSubclass.class);
    }

    @Test
    @DisplayName("Should not merge an abstract subclass, refusing a concrete one that inherits the method")
    void shouldSkipAbstractSubclass() throws Exception {
        List<String> abstractSubclass = TokenSheriffProcessor
                .findScopeViolations(Index.of(LengthBase.class, AbstractInheritingSubclass.class));
        List<String> concreteSubclass = TokenSheriffProcessor
                .findScopeViolations(Index.of(LengthBase.class, InheritingSubclass.class));

        assertEquals(List.of(), abstractSubclass, "an abstract subclass is no bean class");
        assertMergedOverLength(concreteSubclass, InheritingSubclass.class);
    }

    @Test
    @DisplayName("Should merge a grandchild that inherits the method unchanged, not one below an override")
    void shouldResolveGrandchildBeanClasses() throws Exception {
        List<String> belowOverride = TokenSheriffProcessor.findScopeViolations(
                Index.of(LengthBase.class, OverridingSubclass.class, GrandchildBelowOverride.class));
        List<String> belowPlainClass = TokenSheriffProcessor.findScopeViolations(
                Index.of(LengthBase.class, PlainMiddle.class, GrandchildBelowPlainClass.class));

        assertEquals(List.of(), belowOverride, "a class between the grandchild and the declaring class overrides it");
        assertMergedOverLength(belowPlainClass, GrandchildBelowPlainClass.class);
    }

    private static void assertMergedOverLength(List<String> violations, Class<?> beanClass) {
        assertEquals(1, violations.size(), "exactly the merge in " + beanClass.getName() + ": " + violations);
        assertTrue(violations.getFirst().startsWith("@BearerAuth on method " + LengthBase.class.getName()
                        + "#endpoint together with the class-level @BearerAuth of bean class " + beanClass.getName() + " "),
                violations.getFirst());
        assertTrue(violations.getFirst().contains(OVER_LENGTH), violations.getFirst());
    }

    @Test
    @DisplayName("Should report a bad class-level scope at the class, not at its methods")
    void shouldReportBadClassLevelScopeAtTheClassOnly() throws Exception {
        List<String> violations = TokenSheriffProcessor
                .findScopeViolations(Index.of(InvalidClassLevelScopeWithMethod.class));

        assertEquals(1, violations.size(), "only the class-level annotation must be refused: " + violations);
        assertTrue(violations.getFirst().startsWith("@BearerAuth on class "
                + InvalidClassLevelScopeWithMethod.class.getName() + " "), violations.getFirst());
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

    /**
     * Valid scope names that fit a bare challenge but not the one sent with the realm and the error_description.
     */
    @BearerAuth(requiredScopes = {OVER_LENGTH_A, OVER_LENGTH_B})
    static class ChallengeOverLengthWithRealm {
    }

    /**
     * Valid scope names whose challenge with the realm and the error_description stays within 8192 characters.
     */
    @BearerAuth(requiredScopes = {OVER_LENGTH_A, WITHIN_LENGTH_B})
    static class ChallengeWithinLengthWithRealm {
    }

    /**
     * Declares a method-level {@code @BearerAuth} of 32 scopes and carries no class-level annotation itself.
     */
    static class InheritedEndpointBase {

        @BearerAuth(requiredScopes = {"m01", "m02", "m03", "m04", "m05", "m06", "m07", "m08", "m09", "m10", "m11",
                "m12", "m13", "m14", "m15", "m16", "m17", "m18", "m19", "m20", "m21", "m22", "m23", "m24", "m25",
                "m26", "m27", "m28", "m29", "m30", "m31", "m32"})
        public String endpoint() {
            return "inherited";
        }
    }

    /**
     * Inherits the 32-scope method and adds 33 class-level scopes the declaring class does not carry: 65 at run
     * time.
     */
    @BearerAuth(requiredScopes = {"c01", "c02", "c03", "c04", "c05", "c06", "c07", "c08", "c09", "c10", "c11",
            "c12", "c13", "c14", "c15", "c16", "c17", "c18", "c19", "c20", "c21", "c22", "c23", "c24", "c25", "c26",
            "c27", "c28", "c29", "c30", "c31", "c32", "c33"})
    static class InheritingOverBound extends InheritedEndpointBase {
    }

    /**
     * Inherits the 32-scope method and adds 32 class-level scopes: exactly 64 at run time.
     */
    @BearerAuth(requiredScopes = {"c01", "c02", "c03", "c04", "c05", "c06", "c07", "c08", "c09", "c10", "c11",
            "c12", "c13", "c14", "c15", "c16", "c17", "c18", "c19", "c20", "c21", "c22", "c23", "c24", "c25", "c26",
            "c27", "c28", "c29", "c30", "c31", "c32"})
    static class InheritingAtBound extends InheritedEndpointBase {
    }

    /**
     * An abstract class whose 33 class-level scopes plus its method's 32 would be 65; it is no bean class, so the
     * interceptor never merges its class-level annotation.
     */
    @BearerAuth(requiredScopes = {"c01", "c02", "c03", "c04", "c05", "c06", "c07", "c08", "c09", "c10", "c11",
            "c12", "c13", "c14", "c15", "c16", "c17", "c18", "c19", "c20", "c21", "c22", "c23", "c24", "c25", "c26",
            "c27", "c28", "c29", "c30", "c31", "c32", "c33"})
    abstract static class AbstractDeclaringBase {

        @BearerAuth(requiredScopes = {"m01", "m02", "m03", "m04", "m05", "m06", "m07", "m08", "m09", "m10", "m11",
                "m12", "m13", "m14", "m15", "m16", "m17", "m18", "m19", "m20", "m21", "m22", "m23", "m24", "m25",
                "m26", "m27", "m28", "m29", "m30", "m31", "m32"})
        public String endpoint() {
            return "inherited";
        }
    }

    /**
     * The bean class running {@link AbstractDeclaringBase#endpoint()}, without a class-level annotation.
     */
    static class PlainSubclass extends AbstractDeclaringBase {
    }

    /**
     * Declares a method-level scope that fits on its own but not merged with {@link #OVER_LENGTH_B}.
     */
    static class LengthBase {

        @BearerAuth(requiredScopes = OVER_LENGTH_A)
        public String endpoint() {
            return "base";
        }
    }

    /** Overrides the annotated method, so its class-level scope is never merged with it. */
    @BearerAuth(requiredScopes = OVER_LENGTH_B)
    static class OverridingSubclass extends LengthBase {

        @Override
        public String endpoint() {
            return "override";
        }
    }

    /** Inherits the annotated method unchanged: the merged challenge is over the length bound. */
    @BearerAuth(requiredScopes = OVER_LENGTH_B)
    static class InheritingSubclass extends LengthBase {
    }

    /** Inherits the annotated method but is abstract, so it is no bean class. */
    @BearerAuth(requiredScopes = OVER_LENGTH_B)
    abstract static class AbstractInheritingSubclass extends LengthBase {
    }

    /** Inherits the annotated method below a class that overrides it. */
    @BearerAuth(requiredScopes = OVER_LENGTH_B)
    static class GrandchildBelowOverride extends OverridingSubclass {
    }

    /** Inherits the annotated method unchanged and carries no class-level annotation. */
    static class PlainMiddle extends LengthBase {
    }

    /** Inherits the annotated method unchanged through {@link PlainMiddle}. */
    @BearerAuth(requiredScopes = OVER_LENGTH_B)
    static class GrandchildBelowPlainClass extends PlainMiddle {
    }

    /**
     * A class-level scope name outside the grammar on a class whose method declares valid scopes only.
     */
    @BearerAuth(requiredScopes = {"quote\"d"})
    static class InvalidClassLevelScopeWithMethod {

        @BearerAuth(requiredScopes = {"read"})
        public String endpoint() {
            return "refused";
        }
    }
}
