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

import de.cuioss.sheriff.token.quarkus.deployment.BearerScopeValidationBuildStepTest.ValidInjectionPoint;
import de.cuioss.sheriff.token.quarkus.deployment.BearerScopeValidationBuildStepTest.ValidScopesResource;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that the deployment-time scope validation lets an application build and boot whose archive holds
 * valid scope names and a class-level plus method-level {@code @BearerAuth} union of exactly 64 distinct
 * scopes.
 * <p>
 * The fixtures are the valid ones of {@link BearerScopeValidationBuildStepTest}, which pins the refusals.
 * The refused fixtures are deliberately not in this archive: they would fail the very boot asserted here.
 */
@Tag("quarkus-boot")
@DisplayName("An application with valid annotation-declared required scopes builds and boots")
class BearerScopeValidationBootTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(ValidScopesResource.class, ValidInjectionPoint.class))
            .withConfigurationResource("application-integration.properties");

    @Test
    @DisplayName("Should build and boot with valid scopes and a class plus method union of exactly 64")
    void shouldBootWithValidScopes() {
        assertTrue(Arc.container().isRunning(),
                "the application must have built and booted with the valid-scope fixtures in its archive");
    }
}
