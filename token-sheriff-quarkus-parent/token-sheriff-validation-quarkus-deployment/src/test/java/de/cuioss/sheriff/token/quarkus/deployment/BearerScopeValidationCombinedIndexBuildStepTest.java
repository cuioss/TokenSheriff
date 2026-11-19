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
import io.quarkus.arc.deployment.BeanArchiveIndexBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.arc.processor.BeanArchives;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.spi.DeploymentException;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins that the deployment-time scope check ({@link TokenSheriffProcessor#validateAnnotationScopes}) reads the
 * combined index next to the bean-archive index: a {@code @BearerAuth} method that an application bean class
 * inherits from a base class outside the bean-archive index is merged with the bean class's own class-level
 * scopes, and a refusal found through both indexes is reported once.
 * <p>
 * The bean-archive side is the computing index ArC builds ({@link BeanArchives#buildComputingBeanArchiveIndex}),
 * over an index that holds the application bean class only, as for a base class in a dependency that is no
 * bean archive. That index resolves the base class when asked for it by name, but does not enumerate its
 * annotations; the combined index holds both classes, as it holds every indexed application archive.
 * <p>
 * Because the combined index holds classes that are no beans, the class also pins that a {@code @Vetoed} class,
 * declaring or inheriting the method, is no bean class whose class-level scopes are merged.
 */
@DisplayName("Deployment-time scope check of a @BearerAuth method inherited from outside the bean-archive index")
class BearerScopeValidationCombinedIndexBuildStepTest {

    private static final String OVER_BOUND = "at most 64 scope tokens";

    private static final String NOT_A_SCOPE_TOKEN = "is not an RFC 6749 section 3.3 scope-token";

    private static final DotName BASE = DotName.createSimple(DependencyEndpointBase.class.getName());

    @Test
    @DisplayName("Should refuse an inherited method from outside the bean-archive index merged over the bound")
    void shouldRefuseInheritedMethodFromOutsideBeanArchiveIndex() throws Exception {
        Index beanArchive = Index.of(ApplicationSubclassOverBound.class);
        IndexView computing = computingIndex(beanArchive);
        assertNotNull(computing.getClassByName(BASE),
                "precondition: the computing bean-archive index resolves the base class by name");
        assertTrue(computing.getAnnotations(DotName.createSimple(BearerAuth.class.getName())).stream()
                        .map(AnnotationInstance::target)
                        .noneMatch(target -> target.kind() == AnnotationTarget.Kind.METHOD
                                && BASE.equals(target.asMethod().declaringClass().name())),
                "precondition: the computing bean-archive index does not enumerate the base class's method annotation");

        List<String> messages = runStep(computing, beanArchive,
                Index.of(DependencyEndpointBase.class, ApplicationSubclassOverBound.class));

        assertEquals(1, messages.size(), "exactly the merge in the application bean class: " + messages);
        assertTrue(messages.getFirst().startsWith("@BearerAuth on method " + DependencyEndpointBase.class.getName()
                + "#endpoint together with the class-level @BearerAuth of bean class "
                + ApplicationSubclassOverBound.class.getName() + " "), messages.getFirst());
        assertTrue(messages.getFirst().contains(OVER_BOUND), messages.getFirst());
    }

    @Test
    @DisplayName("Should accept an inherited method from outside the bean-archive index whose union fits")
    void shouldAcceptInheritedMethodFromOutsideBeanArchiveIndexWithinBound() throws Exception {
        Index beanArchive = Index.of(ApplicationSubclassWithinBound.class);

        List<String> messages = runStep(computingIndex(beanArchive), beanArchive,
                Index.of(DependencyEndpointBase.class, ApplicationSubclassWithinBound.class));

        assertEquals(List.of(), messages, "32 inherited method scopes plus 1 class-level scope must not be refused");
    }

    @Test
    @DisplayName("Should report a refusal found through both indexes once")
    void shouldReportRefusalInBothIndexesOnce() throws Exception {
        Index both = Index.of(DependencyEndpointBase.class, ApplicationSubclassOverBound.class,
                InvalidClassLevelScope.class);

        List<String> messages = runStep(computingIndex(both), both, both);

        assertEquals(2, messages.size(), "one error per refused scope set, each once: " + messages);
        assertAll("each refused scope set is named once",
                () -> assertEquals(1, messages.stream().filter(message -> message.contains(OVER_BOUND)
                        && message.contains(ApplicationSubclassOverBound.class.getName())).count(), messages::toString),
                () -> assertEquals(1, messages.stream().filter(message -> message.startsWith(
                        "@BearerAuth on class " + InvalidClassLevelScope.class.getName() + " ")
                        && message.contains(NOT_A_SCOPE_TOKEN)).count(), messages::toString));
    }

    @Test
    @DisplayName("Should not merge an inherited method with a @Vetoed subclass, refusing the same shape without it")
    void shouldSkipVetoedSubclass() throws Exception {
        List<String> vetoed = TokenSheriffProcessor
                .findScopeViolations(Index.of(DependencyEndpointBase.class, VetoedSubclassOverBound.class));
        List<String> control = TokenSheriffProcessor
                .findScopeViolations(Index.of(DependencyEndpointBase.class, ApplicationSubclassOverBound.class));

        assertEquals(List.of(), vetoed, "a @Vetoed class is no bean class, so it never runs the method");
        assertEquals(1, control.size(), "control: exactly the merge in the bean class: " + control);
        assertTrue(control.getFirst().startsWith("@BearerAuth on method " + DependencyEndpointBase.class.getName()
                + "#endpoint together with the class-level @BearerAuth of bean class "
                + ApplicationSubclassOverBound.class.getName() + " "), control.getFirst());
        assertTrue(control.getFirst().contains(OVER_BOUND), control.getFirst());
    }

    @Test
    @DisplayName("Should not merge a method with its @Vetoed declaring class, refusing the same shape without it")
    void shouldSkipVetoedDeclaringClass() throws Exception {
        List<String> vetoed = TokenSheriffProcessor.findScopeViolations(Index.of(VetoedDeclaringOverBound.class));
        List<String> control = TokenSheriffProcessor.findScopeViolations(Index.of(DeclaringOverBound.class));

        assertEquals(List.of(), vetoed, "a @Vetoed declaring class is no bean class, so its scopes are not merged");
        assertEquals(1, control.size(), "control: exactly the merged method: " + control);
        assertTrue(control.getFirst().startsWith("@BearerAuth on method " + DeclaringOverBound.class.getName()
                + "#endpoint together with the class-level @BearerAuth of bean class "
                + DeclaringOverBound.class.getName() + " "), control.getFirst());
        assertTrue(control.getFirst().contains(OVER_BOUND), control.getFirst());
    }

    @Test
    @DisplayName("Should still refuse the own scopes of a method whose only class is @Vetoed")
    void shouldCheckMethodOfVetoedClassAlone() throws Exception {
        List<String> violations = TokenSheriffProcessor
                .findScopeViolations(Index.of(VetoedDeclaringInvalidMethodScope.class));

        assertEquals(1, violations.size(), "exactly the method's own scopes must be refused: " + violations);
        assertTrue(violations.getFirst().startsWith("@BearerAuth on method "
                + VetoedDeclaringInvalidMethodScope.class.getName() + "#endpoint declares "), violations.getFirst());
        assertTrue(violations.getFirst().contains(NOT_A_SCOPE_TOKEN), violations.getFirst());
    }

    private static IndexView computingIndex(Index beanArchive) {
        return BeanArchives.buildComputingBeanArchiveIndex(
                BearerScopeValidationCombinedIndexBuildStepTest.class.getClassLoader(), new ConcurrentHashMap<>(),
                beanArchive);
    }

    /**
     * Runs the build step with the given bean-archive and combined indexes and returns the messages of the
     * produced deployment errors, empty when none were produced.
     */
    private static List<String> runStep(IndexView beanArchiveIndex, Index immutableBeanArchiveIndex,
            Index combinedIndex) {
        List<ValidationErrorBuildItem> produced = new ArrayList<>();

        new TokenSheriffProcessor().validateAnnotationScopes(null,
                new BeanArchiveIndexBuildItem(beanArchiveIndex, immutableBeanArchiveIndex, Set.of()),
                new CombinedIndexBuildItem(combinedIndex, combinedIndex), produced::add);

        if (produced.isEmpty()) {
            return List.of();
        }
        assertEquals(1, produced.size(), "the refusals must be produced as one validation error item");
        List<Throwable> errors = produced.getFirst().getValues();
        errors.forEach(error -> assertInstanceOf(DeploymentException.class, error));
        return errors.stream().map(Throwable::getMessage).toList();
    }

    /**
     * Stands for an abstract base class in a dependency that is no bean archive: no bean-defining annotation,
     * no class-level {@code @BearerAuth}, one method declaring 32 scopes.
     */
    abstract static class DependencyEndpointBase {

        @BearerAuth(requiredScopes = {"m01", "m02", "m03", "m04", "m05", "m06", "m07", "m08", "m09", "m10", "m11",
                "m12", "m13", "m14", "m15", "m16", "m17", "m18", "m19", "m20", "m21", "m22", "m23", "m24", "m25",
                "m26", "m27", "m28", "m29", "m30", "m31", "m32"})
        public String endpoint() {
            return "inherited";
        }
    }

    /** The application bean class: 33 class-level scopes plus the inherited 32, 65 at run time. */
    @BearerAuth(requiredScopes = {"c01", "c02", "c03", "c04", "c05", "c06", "c07", "c08", "c09", "c10", "c11",
            "c12", "c13", "c14", "c15", "c16", "c17", "c18", "c19", "c20", "c21", "c22", "c23", "c24", "c25", "c26",
            "c27", "c28", "c29", "c30", "c31", "c32", "c33"})
    static class ApplicationSubclassOverBound extends DependencyEndpointBase {
    }

    /** The application bean class: 1 class-level scope plus the inherited 32, 33 at run time. */
    @BearerAuth(requiredScopes = "c01")
    static class ApplicationSubclassWithinBound extends DependencyEndpointBase {
    }

    /** A class-level {@code @BearerAuth} whose scope name holds a double quote. */
    @BearerAuth(requiredScopes = {"quote\"d"})
    static class InvalidClassLevelScope {
    }

    /** {@link ApplicationSubclassOverBound} declared {@code @Vetoed}: 33 class-level scopes plus the inherited 32. */
    @Vetoed
    @BearerAuth(requiredScopes = {"c01", "c02", "c03", "c04", "c05", "c06", "c07", "c08", "c09", "c10", "c11",
            "c12", "c13", "c14", "c15", "c16", "c17", "c18", "c19", "c20", "c21", "c22", "c23", "c24", "c25", "c26",
            "c27", "c28", "c29", "c30", "c31", "c32", "c33"})
    static class VetoedSubclassOverBound extends DependencyEndpointBase {
    }

    /** A concrete class whose 33 class-level scopes plus its method's 32 are 65. */
    @BearerAuth(requiredScopes = {"c01", "c02", "c03", "c04", "c05", "c06", "c07", "c08", "c09", "c10", "c11",
            "c12", "c13", "c14", "c15", "c16", "c17", "c18", "c19", "c20", "c21", "c22", "c23", "c24", "c25", "c26",
            "c27", "c28", "c29", "c30", "c31", "c32", "c33"})
    static class DeclaringOverBound {

        @BearerAuth(requiredScopes = {"m01", "m02", "m03", "m04", "m05", "m06", "m07", "m08", "m09", "m10", "m11",
                "m12", "m13", "m14", "m15", "m16", "m17", "m18", "m19", "m20", "m21", "m22", "m23", "m24", "m25",
                "m26", "m27", "m28", "m29", "m30", "m31", "m32"})
        public String endpoint() {
            return "merged";
        }
    }

    /** {@link DeclaringOverBound} declared {@code @Vetoed}. */
    @Vetoed
    @BearerAuth(requiredScopes = {"c01", "c02", "c03", "c04", "c05", "c06", "c07", "c08", "c09", "c10", "c11",
            "c12", "c13", "c14", "c15", "c16", "c17", "c18", "c19", "c20", "c21", "c22", "c23", "c24", "c25", "c26",
            "c27", "c28", "c29", "c30", "c31", "c32", "c33"})
    static class VetoedDeclaringOverBound {

        @BearerAuth(requiredScopes = {"m01", "m02", "m03", "m04", "m05", "m06", "m07", "m08", "m09", "m10", "m11",
                "m12", "m13", "m14", "m15", "m16", "m17", "m18", "m19", "m20", "m21", "m22", "m23", "m24", "m25",
                "m26", "m27", "m28", "m29", "m30", "m31", "m32"})
        public String endpoint() {
            return "vetoed";
        }
    }

    /** A {@code @Vetoed} class whose method-level scope name holds a space. */
    @Vetoed
    @BearerAuth(requiredScopes = "c01")
    static class VetoedDeclaringInvalidMethodScope {

        @BearerAuth(requiredScopes = {"read", "has space"})
        public String endpoint() {
            return "refused";
        }
    }
}
