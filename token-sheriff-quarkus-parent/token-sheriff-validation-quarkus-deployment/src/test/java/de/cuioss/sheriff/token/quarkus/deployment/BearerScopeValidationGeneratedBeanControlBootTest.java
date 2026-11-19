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

import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Negative control for {@link BearerScopeValidationGeneratedBeanBootTest}: the same generated bean with a scope
 * name inside the {@code scope-token} grammar boots.
 */
@Tag("quarkus-boot")
@DisplayName("An application whose generated bean declares a valid required scope boots")
class BearerScopeValidationGeneratedBeanControlBootTest {

    // The archive carries the refusal test class because the shared customizer is declared there.
    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClass(BearerScopeValidationGeneratedBeanBootTest.class))
            .withConfigurationResource("application-integration.properties")
            .addBuildChainCustomizer(BearerScopeValidationGeneratedBeanBootTest.generatedBean(
                    "de.cuioss.sheriff.token.quarkus.deployment.GeneratedValidScopeHolder", "read"));

    @Test
    @DisplayName("Should boot the application with the generated bean")
    void shouldBoot() {
        assertTrue(Arc.container().isRunning(),
                "the application must have built and booted with the valid-scope generated bean");
    }
}
