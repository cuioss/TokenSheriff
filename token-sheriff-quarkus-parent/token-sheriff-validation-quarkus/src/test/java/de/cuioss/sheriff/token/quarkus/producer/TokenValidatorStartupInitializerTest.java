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
package de.cuioss.sheriff.token.quarkus.producer;

import de.cuioss.sheriff.token.quarkus.config.JwtPropertyKeys;
import de.cuioss.sheriff.token.quarkus.test.TestConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link TokenValidatorStartupInitializer}, exercising the enabled-issuer gate and the
 * fail-boot contract of the startup observer without a CDI container.
 */
@DisplayName("TokenValidatorStartupInitializer")
@EnableTestLogger
class TokenValidatorStartupInitializerTest {

    private static final String TEST_ISSUER = "test";

    @Nested
    @DisplayName("No enabled issuer")
    class NoEnabledIssuer {

        @Test
        @DisplayName("should not resolve the validator when no issuer property is configured")
        void shouldNotResolveWithoutIssuerProperties() {
            Instance<TokenValidator> validatorHandle = untouchedHandle();
            TokenValidatorStartupInitializer initializer = new TokenValidatorStartupInitializer(
                    new TestConfig(Map.of()), validatorHandle);

            initializer.onStartup(new StartupEvent());

            verify(validatorHandle);
        }

        @Test
        @DisplayName("should not resolve the validator when the only issuer is disabled")
        void shouldNotResolveWhenIssuerDisabled() {
            Instance<TokenValidator> validatorHandle = untouchedHandle();
            TokenValidatorStartupInitializer initializer = new TokenValidatorStartupInitializer(
                    issuerConfig("false"), validatorHandle);

            initializer.onStartup(new StartupEvent());

            verify(validatorHandle);
        }
    }

    @Nested
    @DisplayName("Enabled issuer")
    class EnabledIssuer {

        @Test
        @DisplayName("should resolve the validator exactly once when one issuer is enabled")
        void shouldResolveValidatorExactlyOnce() {
            TokenValidator validator = createNiceMock(TokenValidator.class);
            replay(validator);
            @SuppressWarnings("unchecked") Instance<TokenValidator> validatorHandle = createMock(Instance.class);
            expect(validatorHandle.get()).andReturn(validator).once();
            replay(validatorHandle);
            TokenValidatorStartupInitializer initializer = new TokenValidatorStartupInitializer(
                    issuerConfig("true"), validatorHandle);

            initializer.onStartup(new StartupEvent());

            verify(validatorHandle);
        }

        @Test
        @DisplayName("should propagate a failure to build the validator out of the startup observer")
        void shouldPropagateValidatorFailure() {
            IllegalStateException failure = new IllegalStateException("invalid issuer configuration");
            @SuppressWarnings("unchecked") Instance<TokenValidator> validatorHandle = createMock(Instance.class);
            expect(validatorHandle.get()).andThrow(failure).once();
            replay(validatorHandle);
            TokenValidatorStartupInitializer initializer = new TokenValidatorStartupInitializer(
                    issuerConfig("true"), validatorHandle);
            StartupEvent event = new StartupEvent();

            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> initializer.onStartup(event),
                    "A validator build failure must abort the application start");

            assertSame(failure, thrown, "The original failure must propagate unwrapped");
            verify(validatorHandle);
        }
    }

    /**
     * A strict handle with no recorded expectations: any {@code get()} call fails the test, so
     * {@code verify} after the observer ran proves the handle was never resolved.
     */
    @SuppressWarnings("unchecked")
    private static Instance<TokenValidator> untouchedHandle() {
        Instance<TokenValidator> handle = createMock(Instance.class);
        replay(handle);
        return handle;
    }

    private static Config issuerConfig(String enabled) {
        return new TestConfig(Map.of(
                JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), enabled,
                JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "https://example.com"));
    }
}
