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

import de.cuioss.sheriff.token.quarkus.annotation.BearerToken;
import de.cuioss.sheriff.token.quarkus.producer.BearerTokenResult;
import io.quarkus.arc.Arc;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.FieldCreator;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins that the deployment-time scope validation sees the beans ArC generates during the build: a bean added
 * through a build-chain customizer as a {@link GeneratedBeanBuildItem}, whose {@code @BearerToken} declares a
 * scope name outside the RFC 6749 §3.3 {@code scope-token} grammar, fails augmentation with a
 * {@link DeploymentException} naming that member.
 * <p>
 * A generated bean is in the bean-archive index the scope check reads, but not in the combined index, so this
 * test fails when the check scans the combined index. The negative control
 * {@link BearerScopeValidationGeneratedBeanControlBootTest} boots the same generated bean with a valid scope.
 */
@Tag("quarkus-boot")
@DisplayName("An application whose generated bean declares an invalid required scope fails augmentation")
class BearerScopeValidationGeneratedBeanBootTest {

    static final String FIELD_NAME = "tokenResult";

    private static final String GENERATED_BEAN = "de.cuioss.sheriff.token.quarkus.deployment.GeneratedInvalidScopeHolder";

    private static final String REFUSED_MEMBER = "@BearerToken on field " + GENERATED_BEAN + "#" + FIELD_NAME + " ";

    private static final String NOT_A_SCOPE_TOKEN = "is not an RFC 6749 section 3.3 scope-token";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withEmptyApplication()
            .withConfigurationResource("application-integration.properties")
            .addBuildChainCustomizer(generatedBean(GENERATED_BEAN, "has space"))
            .assertException(BearerScopeValidationGeneratedBeanBootTest::assertScopeRefusal);

    /**
     * Returns a build-chain customizer that adds one generated {@link Singleton} bean named {@code className}
     * whose field {@value #FIELD_NAME} carries {@code @BearerToken(requiredScopes = scope)}.
     */
    static Consumer<BuildChainBuilder> generatedBean(String className, String scope) {
        return chain -> chain.addBuildStep(context -> {
            try (ClassCreator creator = ClassCreator.builder()
                         .classOutput(new GeneratedBeanGizmoAdaptor(context::produce))
                         .className(className)
                         .build()) {
                creator.addAnnotation(Singleton.class);
                FieldCreator field = creator.getFieldCreator(FIELD_NAME, BearerTokenResult.class);
                field.addAnnotation(BearerToken.class).addValue("requiredScopes", new String[]{scope});
            }
        }).produces(GeneratedBeanBuildItem.class).build();
    }

    private static void assertScopeRefusal(Throwable thrown) {
        assertNotNull(thrown, "augmentation must fail for a generated bean with a scope outside the grammar");
        assertTrue(containsScopeRefusal(thrown),
                "augmentation must fail with a DeploymentException naming " + REFUSED_MEMBER.strip()
                        + " and the scope-token reason, but the failure was: " + thrown);
    }

    /**
     * Walks the cause chain, and the suppressed exceptions Quarkus attaches when it collects validation
     * errors, for the DeploymentException naming the generated member and the scope-token reason.
     */
    private static boolean containsScopeRefusal(Throwable thrown) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> pending = new ArrayDeque<>();
        pending.push(thrown);
        while (!pending.isEmpty()) {
            Throwable current = pending.pop();
            if (!seen.add(current)) {
                continue;
            }
            String message = current.getMessage();
            if (current instanceof DeploymentException && message != null && message.contains(REFUSED_MEMBER)
                    && message.contains(NOT_A_SCOPE_TOKEN)) {
                return true;
            }
            if (current.getCause() != null) {
                pending.push(current.getCause());
            }
            for (Throwable suppressed : current.getSuppressed()) {
                pending.push(suppressed);
            }
        }
        return false;
    }

    @Test
    @DisplayName("Should not build the application")
    void shouldFailAugmentation() {
        // Never invoked: QuarkusExtensionTest skips test methods once assertException is set, and the
        // augmentation failure itself is asserted in assertScopeRefusal.
        fail("Augmentation should have failed for a generated bean with a scope outside the scope-token grammar");
    }
}

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
