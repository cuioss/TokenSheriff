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

import de.cuioss.sheriff.token.quarkus.deployment.BearerScopeValidationBuildStepTest.InvalidMethodLevelScope;
import de.cuioss.sheriff.token.quarkus.deployment.BearerScopeValidationBuildStepTest.NonAsciiScope;
import de.cuioss.sheriff.token.quarkus.deployment.BearerScopeValidationBuildStepTest.ValidScopesResource;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.enterprise.inject.spi.DeploymentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins that the deployment-time scope validation refuses an invalid annotation through Quarkus augmentation
 * itself: an archive holding one method-level {@code @BearerAuth} whose scope name is outside the RFC 6749
 * §3.3 {@code scope-token} grammar fails the build with a {@link DeploymentException} naming the declaring
 * class and method and the reason.
 * <p>
 * This is the connection the other two tests cannot see: {@link BearerScopeValidationBuildStepTest} calls
 * the build-step method directly and pins the full refusal matrix, and {@link BearerScopeValidationBootTest}
 * boots only valid fixtures. Disconnecting the build step from augmentation - its {@code @BuildStep}, its
 * validation-phase ordering input or its validation-error producer - fails this test alone. One invalid
 * fixture is enough for that, so the boot cost stays one application start.
 */
@Tag("quarkus-boot")
@DisplayName("An application with an invalid annotation-declared required scope fails augmentation")
class BearerScopeValidationRefusalBootTest {

    private static final String NOT_A_SCOPE_TOKEN = "is not an RFC 6749 section 3.3 scope-token";

    private static final String SCOPE_REFUSAL = "declares requiredScopes that an insufficient_scope challenge cannot carry";

    private static final String REFUSED_MEMBER = " on method " + InvalidMethodLevelScope.class.getName() + "#endpoint ";

    private static final String REFUSED_NON_ASCII_MEMBER = " on method " + NonAsciiScope.class.getName()
            + "#endpoint ";

    private static final String VALID_MEMBER = " " + ValidScopesResource.class.getName();

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(InvalidMethodLevelScope.class, NonAsciiScope.class,
                    ValidScopesResource.class))
            .withConfigurationResource("application-integration.properties")
            .assertException(BearerScopeValidationRefusalBootTest::assertScopeRefusal);

    private static void assertScopeRefusal(Throwable thrown) {
        assertNotNull(thrown, "augmentation must fail for a scope name outside the scope-token grammar");
        Throwable refusal = findScopeRefusal(thrown, REFUSED_MEMBER, NOT_A_SCOPE_TOKEN);
        assertNotNull(refusal, "augmentation must fail with a DeploymentException naming " + REFUSED_MEMBER.strip()
                + " and the scope-token reason, but the failure was: " + thrown);
        assertInstanceOf(DeploymentException.class, refusal,
                "the scope refusal must be a DeploymentException, was: " + refusal);
        assertNotNull(findScopeRefusal(thrown, REFUSED_NON_ASCII_MEMBER, NOT_A_SCOPE_TOKEN),
                "a scope name holding a non-ASCII letter must be refused as well, but the failure was: " + thrown);
        assertNull(findScopeRefusal(thrown, VALID_MEMBER, SCOPE_REFUSAL),
                "control: the fixture with valid scope names in the same archive must not be refused for any scope reason");
    }

    /**
     * Walks the cause chain, and the suppressed exceptions Quarkus attaches when it collects validation
     * errors, for the DeploymentException whose message names {@code member} and contains {@code reason}.
     */
    private static Throwable findScopeRefusal(Throwable thrown, String member, String reason) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> pending = new ArrayDeque<>();
        pending.push(thrown);
        while (!pending.isEmpty()) {
            Throwable current = pending.pop();
            if (!seen.add(current)) {
                continue;
            }
            String message = current.getMessage();
            if (current instanceof DeploymentException && message != null && message.contains(member)
                    && message.contains(reason)) {
                return current;
            }
            if (current.getCause() != null) {
                pending.push(current.getCause());
            }
            for (Throwable suppressed : current.getSuppressed()) {
                pending.push(suppressed);
            }
        }
        return null;
    }

    @Test
    @DisplayName("Should not build the application")
    void shouldFailAugmentation() {
        // Never invoked: QuarkusExtensionTest skips test methods once assertException is set, and the
        // augmentation failure itself is asserted in assertScopeRefusal.
        fail("Augmentation should have failed for a scope name outside the scope-token grammar");
    }
}
