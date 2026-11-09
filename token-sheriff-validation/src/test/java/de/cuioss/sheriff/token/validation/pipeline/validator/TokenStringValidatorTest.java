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
package de.cuioss.sheriff.token.validation.pipeline.validator;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TokenStringValidator}.
 * <p>
 * Tests pre-pipeline token string validation including null, blank, and size checks.
 */
@DisplayName("TokenStringValidator Tests")
@EnableGeneratorController
class TokenStringValidatorTest {

    private TokenStringValidator validator;
    private SecurityEventCounter securityEventCounter;

    @BeforeEach
    void setUp() {
        securityEventCounter = new SecurityEventCounter();
        ParserConfig parserConfig = ParserConfig.builder()
                .maxTokenSize(1000)
                .build();
        validator = new TokenStringValidator(parserConfig, securityEventCounter, null);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Should throw exception for null, empty, or blank token")
    void shouldThrowExceptionForInvalidToken(String token) {
        long initialCount = securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_EMPTY);

        TokenValidationException exception = assertThrows(
                TokenValidationException.class,
                () -> validator.validate(token)
        );

        assertEquals(SecurityEventCounter.EventType.TOKEN_EMPTY, exception.getEventType());
        assertEquals(initialCount + 1, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_EMPTY));
    }

    @Test
    @DisplayName("Should throw exception for token exceeding max size")
    void shouldThrowExceptionForTokenExceedingMaxSize() {
        // Create a token larger than maxTokenSize (1000 bytes)
        String largeToken = Generators.letterStrings(1001, 1001).next();

        TokenValidationException exception = assertThrows(
                TokenValidationException.class,
                () -> validator.validate(largeToken)
        );

        assertEquals(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED, exception.getEventType());
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED));
    }

    @Test
    @DisplayName("Should accept valid token within size limit")
    void shouldAcceptValidTokenWithinSizeLimit() {
        String validToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature";

        assertDoesNotThrow(() -> validator.validate(validToken));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_EMPTY));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED));
    }

    @Test
    @DisplayName("Should accept token exactly at max size")
    void shouldAcceptTokenExactlyAtMaxSize() {
        // Create a token exactly at maxTokenSize (1000 bytes)
        String tokenAtMaxSize = Generators.letterStrings(1000, 1000).next();

        assertDoesNotThrow(() -> validator.validate(tokenAtMaxSize));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED));
    }

    @Test
    @DisplayName("Should count security events correctly")
    void shouldCountSecurityEventsCorrectly() {
        // Multiple violations should increment counters
        String oversizedToken = Generators.letterStrings(1001, 1001).next();

        assertThrows(TokenValidationException.class, () -> validator.validate(null));
        assertThrows(TokenValidationException.class, () -> validator.validate(""));
        assertThrows(TokenValidationException.class, () -> validator.validate(oversizedToken));

        assertEquals(2, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_EMPTY));
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED));
    }

    /**
     * With JWE configured the token string check selects the limit by token shape: a 5-part compact
     * JWE is bound by {@code maxEncryptedTokenSize}, every other token by the larger of the two limits.
     * The check is a coarse pre-check for a non-JWE token: the parser narrows it to {@code maxTokenSize}.
     * The two limits are the same for a JWE, so a JWE the string check admits is never refused by the
     * parser for size.
     */
    @Nested
    @DisplayName("JWE limit")
    class JweLimit {

        private static final int JWE_LIMIT = JweDecryptionConfig.DEFAULT_MAX_ENCRYPTED_TOKEN_SIZE;

        private TokenStringValidator jweValidator(int maxEncryptedTokenSize) {
            JweDecryptionConfig jweConfig = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(InMemoryKeyMaterialHandler.getDefaultPrivateKey(
                            InMemoryKeyMaterialHandler.Algorithm.RS256))
                    .maxEncryptedTokenSize(maxEncryptedTokenSize)
                    .build();
            return new TokenStringValidator(ParserConfig.builder().build(), securityEventCounter, jweConfig);
        }

        @Test
        @DisplayName("Should accept a token of exactly maxEncryptedTokenSize")
        void shouldAcceptTokenAtJweLimit() {
            TokenStringValidator jweValidator = jweValidator(JWE_LIMIT);

            assertDoesNotThrow(() -> jweValidator.validate("a".repeat(JWE_LIMIT)));
            assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED));
        }

        @Test
        @DisplayName("Should reject a token above maxEncryptedTokenSize as TOKEN_SIZE_EXCEEDED")
        void shouldRejectTokenAboveJweLimit() {
            TokenStringValidator jweValidator = jweValidator(JWE_LIMIT);
            String oversized = "a".repeat(JWE_LIMIT + 1);

            TokenValidationException exception = assertThrows(TokenValidationException.class,
                    () -> jweValidator.validate(oversized));

            assertEquals(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED, exception.getEventType());
            assertEquals("Token size %d exceeds maximum %d".formatted(JWE_LIMIT + 1, JWE_LIMIT),
                    exception.getMessage());
            assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED));
        }

        @Test
        @DisplayName("Should apply a configured maxEncryptedTokenSize below the default instead of maxTokenSize")
        void shouldApplyConfiguredJweLimit() {
            int configuredLimit = ParserConfig.DEFAULT_MAX_TOKEN_SIZE + 1024;
            TokenStringValidator jweValidator = jweValidator(configuredLimit);

            assertDoesNotThrow(() -> jweValidator.validate("a".repeat(configuredLimit)),
                    "A token above maxTokenSize but within the JWE limit is left to the parser");
            String oversized = "a".repeat(configuredLimit + 1);
            assertThrows(TokenValidationException.class, () -> jweValidator.validate(oversized));
            assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED));
        }

        @Test
        @DisplayName("Should select the limit by token shape when maxEncryptedTokenSize is below maxTokenSize")
        void shouldSelectLimitByTokenShape() {
            int smallJweLimit = 4096;
            int size = smallJweLimit + 904;
            TokenStringValidator jweValidator = jweValidator(smallJweLimit);
            String jws = "a".repeat(size - 2) + "..";
            String jwe = "a".repeat(size - 4) + "....";

            assertDoesNotThrow(() -> jweValidator.validate(jws),
                    "A 3-part token within maxTokenSize must not be refused by the smaller JWE limit");
            TokenValidationException exception = assertThrows(TokenValidationException.class,
                    () -> jweValidator.validate(jwe));

            assertEquals("Token size %d exceeds maximum %d".formatted(size, smallJweLimit), exception.getMessage());
            assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED));
        }
    }
}
