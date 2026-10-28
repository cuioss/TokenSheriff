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

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.JwksType;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.quarkus.config.JwtPropertyKeys;
import de.cuioss.sheriff.token.quarkus.test.TestConfig;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.jwks.JwksLoader;
import de.cuioss.sheriff.token.validation.jwks.key.KeyInfo;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static de.cuioss.sheriff.token.quarkus.TokenSheriffQuarkusLogMessages.INFO;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TokenValidatorProducer} focusing on producer-level functionality.
 * Configuration validation is covered in the config package tests.
 */
@EnableTestLogger
class TokenValidatorProducerUnitTest {

    @Test
    @DisplayName("Should successfully initialize TokenValidator with valid configuration")
    void shouldSuccessfullyInitializeTokenValidator() {
        Map<String, String> props = Map.of(
                JwtPropertyKeys.PARSER.MAX_TOKEN_SIZE, "4096",
                JwtPropertyKeys.ISSUERS.ENABLED.formatted("test"), "true",
                JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted("test"), "https://test.example.com",
                JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted("test"), "true",
                JwtPropertyKeys.ISSUERS.JWKS_CONTENT.formatted("test"), "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"kid\":\"test-key\",\"alg\":\"RS256\",\"n\":\"nzyis1ZjfNB0bBgKFMSvvkTtwlvBsaJq7S5wA-kzeVOVpVWwkWdVha4s38XM_pa_yr47av7-z3VTmvDRyAHcaT92whREFpLv9cj5lTeJSibyr_Mrm_YtjCZVWgaOYIhwrXwKLqPr_11inWsAkfIytvHWTxZYEcXLgAXFuUuaS3uF9gEiNQwzGTU1v0FqkqTBr4B8nW3HCN47XUu0t8Y0e3zvAIhySnxIZi9aDaPvSlAeZ7VVl5ivy_43QvTRpM3eBFs9A1Y9a9aCtHSP8KXRTYhH2TvPxLOOFg0Lu-pwrps6CqvbeZjQlqCh9cGowQ\",\"e\":\"AQAB\"}]}"
        );
        Config testConfig = new TestConfig(props);
        TokenValidatorProducer producer = new TokenValidatorProducer(testConfig, null, null);

        producer.init();

        assertNotNull(producer.tokenValidator, "Should create TokenValidator");
        assertNotNull(producer.issuerConfigs, "Should create issuer configs");
        assertFalse(producer.issuerConfigs.isEmpty(), "Should have at least one issuer config");
        LogAsserts.assertLogMessagePresent(TestLogLevel.INFO, INFO.INITIALIZING_JWT_VALIDATION_COMPONENTS.format());
        LogAsserts.assertLogMessagePresent(TestLogLevel.INFO, INFO.JWT_VALIDATION_COMPONENTS_INITIALIZED.format("1"));
    }

    @Test
    @DisplayName("Should fail when no enabled issuers found")
    void shouldFailWhenNoEnabledIssuersFound() {
        Map<String, String> props = Map.of(
                JwtPropertyKeys.ISSUERS.ENABLED.formatted("test"), "false",
                JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted("test"), "https://test.example.com",
                JwtPropertyKeys.ISSUERS.JWKS_CONTENT.formatted("test"), "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"kid\":\"test-key\",\"alg\":\"RS256\",\"n\":\"nzyis1ZjfNB0bBgKFMSvvkTtwlvBsaJq7S5wA-kzeVOVpVWwkWdVha4s38XM_pa_yr47av7-z3VTmvDRyAHcaT92whREFpLv9cj5lTeJSibyr_Mrm_YtjCZVWgaOYIhwrXwKLqPr_11inWsAkfIytvHWTxZYEcXLgAXFuUuaS3uF9gEiNQwzGTU1v0FqkqTBr4B8nW3HCN47XUu0t8Y0e3zvAIhySnxIZi9aDaPvSlAeZ7VVl5ivy_43QvTRpM3eBFs9A1Y9a9aCtHSP8KXRTYhH2TvPxLOOFg0Lu-pwrps6CqvbeZjQlqCh9cGowQ\",\"e\":\"AQAB\"}]}"
        );
        Config testConfig = new TestConfig(props);
        TokenValidatorProducer producer = new TokenValidatorProducer(testConfig, null, null);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                producer::init);
        assertEquals("No enabled issuer configurations found", exception.getMessage());
    }

    @Nested
    @DisplayName("Destruction closes the produced TokenValidator")
    class Destruction {

        @Test
        @DisplayName("Should close the TokenValidator, and with it its JWKS loaders, when the producer is destroyed")
        void shouldCloseTokenValidatorOnDestroy() {
            RecordingJwksLoader loader = new RecordingJwksLoader();
            TokenValidatorProducer producer = new TokenValidatorProducer(new TestConfig(Map.of()), null, null);
            producer.tokenValidator = validatorOwning(loader);

            producer.destroy();

            assertEquals(1, loader.getCloseCount(), "destroying the producer must close the validator's JWKS loader");
        }

        @Test
        @DisplayName("Should close the TokenValidator at most once")
        void shouldCloseTokenValidatorAtMostOnce() {
            RecordingJwksLoader loader = new RecordingJwksLoader();
            TokenValidatorProducer producer = new TokenValidatorProducer(new TestConfig(Map.of()), null, null);
            producer.tokenValidator = validatorOwning(loader);

            producer.destroy();
            producer.destroy();

            assertEquals(1, loader.getCloseCount(), "a second destruction must not close the validator again");
        }

        @Test
        @DisplayName("Should not propagate a failure while closing, and attempt the close exactly once")
        void shouldNotPropagateFailureWhileClosing() {
            RecordingJwksLoader loader = new RecordingJwksLoader(true);
            TokenValidatorProducer producer = new TokenValidatorProducer(new TestConfig(Map.of()), null, null);
            producer.tokenValidator = validatorOwning(loader);

            assertDoesNotThrow(producer::destroy, "a failure while closing must not abort the container shutdown");
            assertDoesNotThrow(producer::destroy, "a repeated destruction must not abort the container shutdown either");

            assertEquals(1, loader.getCloseCount(), "the failing close must have been attempted exactly once, not retried");
        }

        @Test
        @DisplayName("Should do nothing when init() never created a TokenValidator")
        void shouldIgnoreDestroyWithoutValidator() {
            Map<String, String> props = Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted("test"), "false",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted("test"), "https://test.example.com");
            TokenValidatorProducer producer = new TokenValidatorProducer(new TestConfig(props), null, null);
            assertThrows(IllegalStateException.class, producer::init);

            assertDoesNotThrow(producer::destroy, "a producer whose init() failed must be destroyable");
            assertNull(producer.tokenValidator);
        }

        private static TokenValidator validatorOwning(JwksLoader loader) {
            return TokenValidator.builder()
                    .issuerConfig(IssuerConfig.builder()
                            .issuerIdentifier(RecordingJwksLoader.ISSUER)
                            .expectedAudience("test-audience")
                            .jwksLoader(loader)
                            .build())
                    .build();
        }
    }

    /**
     * An {@link AutoCloseable} JWKS loader that counts how often it was closed, always reporting
     * {@link LoaderStatus#OK} without keys. It can be told to fail every close.
     */
    private static final class RecordingJwksLoader implements JwksLoader, AutoCloseable {

        static final String ISSUER = "https://recording-issuer.example.com";

        private final AtomicInteger closeCount = new AtomicInteger();
        private final boolean failOnClose;

        RecordingJwksLoader() {
            this(false);
        }

        RecordingJwksLoader(boolean failOnClose) {
            this.failOnClose = failOnClose;
        }

        int getCloseCount() {
            return closeCount.get();
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
            if (failOnClose) {
                throw new IllegalStateException("close failed on purpose");
            }
        }

        @Override
        public LoaderStatus getLoaderStatus() {
            return LoaderStatus.OK;
        }

        @Override
        public Optional<KeyInfo> getKeyInfo(String kid) {
            return Optional.empty();
        }

        @Override
        public Set<String> getKeyIds() {
            return Set.of();
        }

        @Override
        public JwksType getJwksType() {
            return JwksType.MEMORY;
        }

        @Override
        public Optional<String> getIssuerIdentifier() {
            return Optional.of(ISSUER);
        }

        @Override
        public CompletableFuture<LoaderStatus> initJWKSLoader(SecurityEventCounter securityEventCounter) {
            return CompletableFuture.completedFuture(LoaderStatus.OK);
        }
    }
}