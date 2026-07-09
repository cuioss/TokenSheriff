/*
 * Copyright © 2022 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.token.client.boundary;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Enforces the framework-agnostic boundary of the client engine ({@code CLIENT-21}).
 * <p>
 * The engine is pure Java: it must not depend on any CDI / MicroProfile / JAX-RS / Quarkus API so
 * it can be wired into any runtime. The framework binding lives outside the engine (for example the
 * {@code token-sheriff-client-quarkus} extension), never inside it.
 */
@AnalyzeClasses(packages = "de.cuioss.sheriff.token.client", importOptions = ImportOption.DoNotIncludeTests.class)
class ClientArchitectureTest {

    @ArchTest
    static final ArchRule engine_is_framework_agnostic = noClasses()
            .that().resideInAPackage("de.cuioss.sheriff.token.client..")
            .should().dependOnClassesThat().resideInAnyPackage(
            "jakarta.enterprise..",
            "jakarta.inject..",
            "jakarta.ws.rs..",
            "org.eclipse.microprofile..",
            "io.quarkus..")
            .because("the client engine must stay framework-agnostic — no CDI/MicroProfile/JAX-RS/Quarkus (CLIENT-21)");

    @ArchTest
    static final ArchRule client_slices_are_free_of_cycles = slices()
            .matching("de.cuioss.sheriff.token.client.(*)..")
            .should().beFreeOfCycles();
}
