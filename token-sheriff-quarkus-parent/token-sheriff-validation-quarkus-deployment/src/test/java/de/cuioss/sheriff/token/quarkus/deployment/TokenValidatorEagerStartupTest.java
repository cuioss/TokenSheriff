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

import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that an invalid property-configured issuer fails the application start instead of
 * surfacing on the first validated request.
 * <p>
 * The configuration is supplied through config overrides only: one enabled issuer without any JWKS
 * source. It deliberately does not reuse {@code application-test.properties} or
 * {@code application-integration.properties}, whose issuers carry a valid JWKS source so that their
 * boot tests start.
 * </p>
 */
@Tag("quarkus-boot")
@EnableTestLogger
@DisplayName("Eager TokenValidator startup fails on an invalid issuer configuration")
class TokenValidatorEagerStartupTest {

    private static final String EXPECTED_MESSAGE = "No JwksLoader configuration is present for enabled issuer";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withEmptyApplication()
            .overrideConfigKey("sheriff.token.issuers.broken.issuer-identifier", "https://broken.example.com")
            .overrideConfigKey("sheriff.token.issuers.broken.enabled", "true")
            .overrideConfigKey("sheriff.token.issuers.broken.audience-validation-disabled", "true")
            .assertException(TokenValidatorEagerStartupTest::assertConfigurationFailure);

    private static void assertConfigurationFailure(Throwable thrown) {
        assertNotNull(thrown, "application startup must fail for an enabled issuer without a JWKS source");
        Throwable configurationFailure = findConfigurationFailure(thrown);
        assertNotNull(configurationFailure,
                "startup must fail with the producer's configuration exception, but the cause chain was: " + thrown);
        assertInstanceOf(IllegalArgumentException.class, configurationFailure,
                "the configuration failure must be an IllegalArgumentException, was: " + configurationFailure);
    }

    private static Throwable findConfigurationFailure(Throwable thrown) {
        Throwable current = thrown;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(EXPECTED_MESSAGE)) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }

    @Test
    @DisplayName("Should not start the application")
    void shouldFailStartup() {
        // Never invoked: QuarkusExtensionTest skips test methods once assertException is set, and the
        // startup failure itself is asserted in assertConfigurationFailure.
        fail("Application startup should have failed for an enabled issuer without a JWKS source");
    }
}
