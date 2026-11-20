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
import de.cuioss.sheriff.token.quarkus.deployment.vetoed.VetoedPackageEndpoint;
import de.cuioss.sheriff.token.quarkus.deployment.vetoed.sub.VetoedSubpackageEndpoint;
import de.cuioss.sheriff.token.quarkus.deployment.vetoeddep.DependencyPackageEndpoint;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.Index;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins which index the package-veto lookup of the deployment-time scope check
 * ({@link TokenSheriffProcessor#findScopeViolations}) reads, and that the veto covers the {@code @Vetoed} package
 * alone.
 * <p>
 * The build step scans a {@link CompositeIndex} of the bean-archive index and the combined index. A class is
 * read as being in a {@code @Vetoed} package when the composite holds its {@code {package}.package-info} with
 * {@code @Vetoed}, whichever of the two indexes that {@code package-info} comes from. A subpackage of a
 * {@code @Vetoed} package is not vetoed, as CDI applies a package-level {@code @Vetoed} to the package alone.
 * <p>
 * Every fixture over the bound declares 33 class-level and 32 method-level scopes, within the bound alone and
 * over it only when merged, so a refusal shows that the check took the class for a bean class.
 */
@DisplayName("Deployment-time scope check of classes in a @Vetoed package read across two indexes")
class BearerScopeValidationVetoedPackageBuildStepTest {

    private static final String OVER_BOUND = "at most 64 scope tokens";

    private static final String MERGED_WITH_BEAN_CLASS = "together with the class-level @BearerAuth of bean class ";

    @Test
    @DisplayName("Should read the package veto from a package-info found only in the second index")
    void shouldReadPackageVetoFromSecondIndex() throws Exception {
        Index classIndex = Index.of(DependencyPackageEndpoint.class);
        Index packageInfoIndex = Index.of(packageInfo(DependencyPackageEndpoint.class));

        List<String> vetoed = TokenSheriffProcessor
                .findScopeViolations(CompositeIndex.create(classIndex, packageInfoIndex));
        List<String> control = TokenSheriffProcessor.findScopeViolations(classIndex);

        assertEquals(List.of(), vetoed,
                "the package-info of the second index vetoes the class of the first, so its scopes are not merged");
        assertEquals(1, control.size(), "control: without the package-info the class is a bean class: " + control);
        assertTrue(control.getFirst().startsWith("@BearerAuth on method " + DependencyPackageEndpoint.class.getName()
                + "#answer " + MERGED_WITH_BEAN_CLASS + DependencyPackageEndpoint.class.getName() + " "),
                control.getFirst());
        assertTrue(control.getFirst().contains(OVER_BOUND), control.getFirst());
    }

    @Test
    @DisplayName("Should merge a class in a subpackage of a @Vetoed package and refuse it over the bound")
    void shouldMergeClassInSubpackageOfVetoedPackage() throws Exception {
        List<String> violations = TokenSheriffProcessor.findScopeViolations(Index.of(
                VetoedSubpackageEndpoint.OverBound.class, packageInfo(VetoedPackageEndpoint.class)));

        assertEquals(1, violations.size(), "a subpackage of a @Vetoed package is not vetoed: " + violations);
        assertTrue(violations.getFirst().startsWith("@BearerAuth on method "
                + VetoedSubpackageEndpoint.OverBound.class.getName() + "#answer " + MERGED_WITH_BEAN_CLASS
                + VetoedSubpackageEndpoint.OverBound.class.getName() + " "), violations.getFirst());
        assertTrue(violations.getFirst().contains(OVER_BOUND), violations.getFirst());
    }

    @Test
    @DisplayName("positive control: should accept the same shape in a non-vetoed package when its scopes fit")
    void shouldAcceptClassInNonVetoedPackageWithinBound() throws Exception {
        List<String> violations = TokenSheriffProcessor.findScopeViolations(Index.of(
                NonVetoedWithinBound.class, packageInfo(VetoedPackageEndpoint.class)));

        assertEquals(List.of(), violations, "1 class-level scope plus 32 method scopes must not be refused");
    }

    private static Class<?> packageInfo(Class<?> classInPackage) throws ClassNotFoundException {
        return Class.forName(classInPackage.getPackageName() + ".package-info");
    }

    /** The shape of {@link VetoedSubpackageEndpoint}, in a package that carries no {@code @Vetoed}: 1 + 32 scopes. */
    @BearerAuth(requiredScopes = "c01")
    static class NonVetoedWithinBound {

        @BearerAuth(requiredScopes = {"m01", "m02", "m03", "m04", "m05", "m06", "m07", "m08", "m09", "m10", "m11",
                "m12", "m13", "m14", "m15", "m16", "m17", "m18", "m19", "m20", "m21", "m22", "m23", "m24", "m25",
                "m26", "m27", "m28", "m29", "m30", "m31", "m32"})
        public String answer() {
            return "within-bound";
        }
    }
}
