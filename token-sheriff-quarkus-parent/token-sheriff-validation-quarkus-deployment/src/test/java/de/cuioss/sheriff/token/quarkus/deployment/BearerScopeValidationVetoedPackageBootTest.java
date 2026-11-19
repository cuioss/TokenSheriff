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
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.enterprise.context.ApplicationScoped;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins how ArC treats a concrete class in a package that carries {@code @Vetoed} in its {@code package-info}:
 * ArC registers no bean for it, as the CDI specification states for a package-level veto. The deployment-time
 * scope check ({@code TokenSheriffProcessor}) follows ArC: it reads the same package annotation, so it never
 * merges the class-level scopes of a class ArC does not register, and never skips a class ArC registers. The
 * fixture's class-level and method-level scopes exceed the challenge bound only when merged, so the application
 * boots only while the scope check agrees with ArC that the class is no bean.
 * <p>
 * The positive control is a class of the same shape outside the vetoed package, booted in the same application:
 * ArC registers it.
 */
@Tag("quarkus-boot")
@DisplayName("ArC registers no bean for a class in a @Vetoed package")
class BearerScopeValidationVetoedPackageBootTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar
                    .addClasses(VetoedPackageEndpoint.class, ControlEndpoint.class)
                    .addClass(VetoedPackageEndpoint.class.getPackageName() + ".package-info"))
            .withConfigurationResource("application-integration.properties")
            // Unused-bean removal is off, so a bean that is missing was never registered, not removed.
            .overrideConfigKey("quarkus.arc.remove-unused-beans", "none");

    @Test
    @DisplayName("Should register no bean for the class in the @Vetoed package")
    void shouldRegisterNoBeanInVetoedPackage() {
        assertFalse(Arc.container().select(VetoedPackageEndpoint.class).isResolvable(),
                "ArC must honour the package-level @Vetoed and register no bean for the class in that package");
    }

    @Test
    @DisplayName("positive control: should register the class of the same shape outside the vetoed package")
    void shouldRegisterBeanOutsideVetoedPackage() {
        assertEquals("control", Arc.container().select(ControlEndpoint.class).get().answer(),
                "ArC registers a concrete class with a bean-defining annotation outside the vetoed package");
    }

    /** The control: the shape of {@link VetoedPackageEndpoint}, in a package that carries no {@code @Vetoed}. */
    @ApplicationScoped
    public static class ControlEndpoint {

        /** @return a fixed value, so a resolved instance can be told apart from a missing one */
        public String answer() {
            return "control";
        }
    }
}
