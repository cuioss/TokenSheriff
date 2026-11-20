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

import de.cuioss.sheriff.token.quarkus.deployment.vetoed.VetoedPackageEndpoint;
import de.cuioss.sheriff.token.quarkus.deployment.vetoed.sub.VetoedSubpackageEndpoint;
import de.cuioss.sheriff.token.quarkus.deployment.vetoeddep.DependencyPackageEndpoint;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.shrinkwrap.api.asset.ClassAsset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shows how ArC treats a concrete class whose {@code @Vetoed} {@code package-info} sits in a dependency archive
 * rather than the application root, and that the deployment-time scope check ({@code TokenSheriffProcessor})
 * agrees with it.
 * <p>
 * The dependency archive holds {@link DependencyPackageEndpoint} and its {@code @Vetoed} {@code package-info},
 * next to {@link ControlEndpoint}, a class of the same shape in a package that carries no {@code @Vetoed}. The
 * fixture's class-level and method-level scopes exceed the challenge bound only when merged, so the application
 * boots only while the scope check agrees with ArC that the class is no bean. The control shows that ArC scans
 * the dependency archive for beans, so the missing bean is vetoed, not undiscovered.
 * <p>
 * The application root holds {@link VetoedSubpackageEndpoint} and the {@code @Vetoed} {@code package-info} of
 * its parent package: CDI applies a package-level {@code @Vetoed} to that package alone, so ArC registers the
 * class in the subpackage.
 */
@Tag("quarkus-boot")
@DisplayName("ArC and the scope check agree on a @Vetoed package in a dependency archive")
class BearerScopeValidationDependencyVetoedPackageBootTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar
                    // Added as a class asset, so the nested over-bound fixture of the class is left out.
                    .add(new ClassAsset(VetoedSubpackageEndpoint.class), classResource(VetoedSubpackageEndpoint.class))
                    .addClass(VetoedPackageEndpoint.class.getPackageName() + ".package-info"))
            .withAdditionalDependency(jar -> jar
                    .addClasses(DependencyPackageEndpoint.class, ControlEndpoint.class)
                    .addClass(DependencyPackageEndpoint.class.getPackageName() + ".package-info"))
            .withConfigurationResource("application-integration.properties")
            // Unused-bean removal is off, so a bean that is missing was never registered, not removed.
            .overrideConfigKey("quarkus.arc.remove-unused-beans", "none");

    @Test
    @DisplayName("Should register no bean for the class in the @Vetoed package of the dependency archive")
    void shouldRegisterNoBeanInVetoedDependencyPackage() {
        assertFalse(Arc.container().select(DependencyPackageEndpoint.class).isResolvable(),
                "ArC must honour the package-level @Vetoed of the dependency archive and register no bean for the "
                        + "class in that package");
    }

    @Test
    @DisplayName("positive control: should register the class of the same shape outside the vetoed package")
    void shouldRegisterBeanOutsideVetoedPackageInDependency() {
        assertEquals("control", Arc.container().select(ControlEndpoint.class).get().answer(),
                "ArC scans the dependency archive and registers a concrete class with a bean-defining annotation "
                        + "outside the vetoed package");
    }

    @Test
    @DisplayName("Should register the class in a subpackage of a @Vetoed package")
    void shouldRegisterBeanInSubpackageOfVetoedPackage() {
        // Resolved, not called: its methods are guarded by @BearerAuth, which needs a request.
        assertTrue(Arc.container().select(VetoedSubpackageEndpoint.class).isResolvable(),
                "a package-level @Vetoed covers its package alone, so ArC registers the class in the subpackage");
    }

    private static String classResource(Class<?> type) {
        return type.getName().replace('.', '/') + ".class";
    }

    /** The control: a concrete class with a bean-defining annotation, in a package that carries no {@code @Vetoed}. */
    @ApplicationScoped
    public static class ControlEndpoint {

        /** @return a fixed value, so a resolved instance can be told apart from a missing one */
        public String answer() {
            return "control";
        }
    }
}
