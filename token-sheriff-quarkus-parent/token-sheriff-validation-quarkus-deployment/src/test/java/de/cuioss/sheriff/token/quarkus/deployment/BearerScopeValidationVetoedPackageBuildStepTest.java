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
import io.quarkus.arc.deployment.BeanArchiveIndexBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import jakarta.enterprise.inject.spi.DeploymentException;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins which index the package-veto lookup of the deployment-time scope check
 * ({@link TokenSheriffProcessor#findScopeViolations}) reads, and that the veto covers the {@code @Vetoed} package
 * alone.
 * <p>
 * The build step scans the index {@link TokenSheriffProcessor#scannedIndex} builds: a {@link CompositeIndex} of the
 * bean-archive index and the combined index. That method is the composite under test here. A class is read as
 * being in a {@code @Vetoed} package when the composite holds its {@code {package}.package-info} with
 * {@code @Vetoed}, whichever of the two indexes that {@code package-info} comes from. A subpackage of a
 * {@code @Vetoed} package is not vetoed, as CDI applies a package-level {@code @Vetoed} to the package alone.
 * The build step itself is driven with a {@code BeanArchiveIndexBuildItem} and a {@code CombinedIndexBuildItem}
 * as well, so its call of {@link TokenSheriffProcessor#scannedIndex} is pinned, not only that method.
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

    /**
     * Each ordering catches one single-index form of {@link TokenSheriffProcessor#scannedIndex}: with the
     * {@code package-info} in the bean-archive index, returning the combined index alone leaves the class without
     * its veto; with the {@code package-info} in the combined index, returning the bean-archive index alone does.
     */
    @ParameterizedTest(name = "package-info in the {0} only")
    @EnumSource(PackageInfoIndex.class)
    @DisplayName("Should veto the class through scannedIndex whichever of the two indexes holds the package-info")
    void shouldVetoThroughScannedIndexWhicheverIndexHoldsPackageInfo(PackageInfoIndex packageInfoIndex)
            throws Exception {
        Index classIndex = Index.of(DependencyPackageEndpoint.class);
        Index packageInfoOnly = Index.of(packageInfo(DependencyPackageEndpoint.class));

        IndexView scanned = switch (packageInfoIndex) {
            case BEAN_ARCHIVE_INDEX -> TokenSheriffProcessor.scannedIndex(packageInfoOnly, classIndex);
            case COMBINED_INDEX -> TokenSheriffProcessor.scannedIndex(classIndex, packageInfoOnly);
        };
        List<String> vetoed = TokenSheriffProcessor.findScopeViolations(scanned);
        List<String> control = TokenSheriffProcessor.findScopeViolations(classIndex);

        assertEquals(List.of(), vetoed,
                "the package-info of the " + packageInfoIndex + " vetoes the class of the other index: " + vetoed);
        assertEquals(1, control.size(), "control: the class index alone is refused: " + control);
        assertTrue(control.getFirst().contains(OVER_BOUND), control.getFirst());
    }

    /**
     * Pins the call site in {@link TokenSheriffProcessor#validateAnnotationScopes}: the build step, driven with its
     * two index build items, reads the package veto across both, so replacing {@link TokenSheriffProcessor#scannedIndex}
     * there by either index alone fails one ordering. The negative control runs the same step with the
     * {@code package-info} left out, which produces the deployment error.
     */
    @ParameterizedTest(name = "package-info in the {0} item only")
    @EnumSource(PackageInfoIndex.class)
    @DisplayName("Should produce no validation error from the build step whichever index item holds the package-info")
    void shouldProduceNoValidationErrorWhicheverBuildItemHoldsPackageInfo(PackageInfoIndex packageInfoIndex)
            throws Exception {
        Index classIndex = Index.of(DependencyPackageEndpoint.class);
        Index packageInfoOnly = Index.of(packageInfo(DependencyPackageEndpoint.class));
        Index empty = new Indexer().complete();

        List<ValidationErrorBuildItem> vetoed = runStepWithOtherIndex(packageInfoIndex, packageInfoOnly, classIndex);
        List<ValidationErrorBuildItem> control = runStepWithOtherIndex(packageInfoIndex, empty, classIndex);

        assertEquals(List.of(), vetoed,
                "the package-info of the " + packageInfoIndex + " item vetoes the class of the other item");
        assertEquals(1, control.size(), "control: without the package-info the step produces one error item");
        List<Throwable> errors = control.getFirst().getValues();
        assertEquals(1, errors.size(), "control: exactly the merge in the bean class: " + errors);
        assertInstanceOf(DeploymentException.class, errors.getFirst());
        assertTrue(errors.getFirst().getMessage().contains(OVER_BOUND), errors.getFirst().getMessage());
    }

    /**
     * Runs {@link TokenSheriffProcessor#validateAnnotationScopes} with {@code packageInfoSide} in the index item
     * {@code packageInfoIndex} names and {@code classIndex} in the other one, and returns the produced items.
     */
    private static List<ValidationErrorBuildItem> runStepWithOtherIndex(PackageInfoIndex packageInfoIndex,
            Index packageInfoSide, Index classIndex) {
        return switch (packageInfoIndex) {
            case BEAN_ARCHIVE_INDEX -> runStep(packageInfoSide, classIndex);
            case COMBINED_INDEX -> runStep(classIndex, packageInfoSide);
        };
    }

    private static List<ValidationErrorBuildItem> runStep(Index beanArchiveIndex, Index combinedIndex) {
        List<ValidationErrorBuildItem> produced = new ArrayList<>();
        new TokenSheriffProcessor().validateAnnotationScopes(null,
                new BeanArchiveIndexBuildItem(beanArchiveIndex, beanArchiveIndex, Set.of()),
                new CombinedIndexBuildItem(combinedIndex, combinedIndex), produced::add);
        return produced;
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

    /** The index of the two {@link TokenSheriffProcessor#scannedIndex} composes that holds the package-info. */
    enum PackageInfoIndex {
        BEAN_ARCHIVE_INDEX, COMBINED_INDEX;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
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
