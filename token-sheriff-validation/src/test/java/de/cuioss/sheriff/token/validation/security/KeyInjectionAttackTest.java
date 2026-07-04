/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.token.validation.security;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.ParserConfig;
import de.cuioss.sheriff.token.validation.TokenType;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.sheriff.token.validation.test.junit.TestTokenSource;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.logging.CuiLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KID injection vulnerabilities.
 * <p>
 * These tests verify that the library correctly rejects tokens with malicious
 * KID (Key ID) headers that attempt to exploit path traversal, SQL injection,
 * or other injection techniques.
 * <p>
 * What is tested:
 * <ul>
 *   <li>Path traversal via KID header</li>
 *   <li>SQL injection via KID header</li>
 *   <li>Null byte injection via KID header</li>
 *   <li>Other key injection techniques</li>
 * </ul>
 * <p>
 * Why it's important:
 * <p>
 * The KID header in a JWT token is used to identify which key should be used to
 * verify the token's signature. If this value is not properly validated and sanitized,
 * attackers can manipulate it to perform various attacks:
 * <ul>
 *   <li>Path traversal to read arbitrary files on the server</li>
 *   <li>SQL injection to extract data or bypass authentication</li>
 *   <li>Command injection to execute arbitrary commands</li>
 * </ul>
 * <p>
 * How testing is performed:
 * <p>
 * Testing involves creating tokens with various malicious KID values and verifying
 * that the library rejects them with appropriate error messages and increments
 * security event counters.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("KID Injection Attack Tests")
class KeyInjectionAttackTest {

    private static final CuiLogger LOGGER = new CuiLogger(KeyInjectionAttackTest.class);

    private TokenValidator tokenValidator;

    @BeforeEach
    void setUp() {

        // Create a valid token
        TestTokenHolder validToken = TestTokenGenerators.accessTokens().next();

        // Get the issuer config from the token
        IssuerConfig issuerConfig = validToken.getIssuerConfig();
        // Create the token validator
        ParserConfig config = ParserConfig.builder().build();
        tokenValidator = TokenValidator.builder().parserConfig(config).issuerConfig(issuerConfig).build();
    }

    /**
     * Creates a token with a malicious KID header by modifying the header of a valid token.
     *
     * @param maliciousKid the malicious KID value to inject
     * @return a JWT token string with the malicious KID header
     */
    private String createTokenWithMaliciousKid(String maliciousKid) {
        // Create a new token with the malicious kid
        // This approach is more reliable than trying to modify an existing token

        // Get a new token holder
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();

        // Set the malicious kid
        tokenHolder.withKeyId(maliciousKid);

        // Get the raw token
        return tokenHolder.getRawToken();
    }

    /**
     * Provides test cases for KID injection attacks.
     * Each test case consists of:
     * - A malicious KID value
     * - A display name for the test
     * - The expected security event type
     * - Whether to check for "Key not found" in the error message
     *
     * @return a stream of test cases
     */
    static Stream<Arguments> kidInjectionTestCases() {
        return Stream.of(
                // Path traversal attack
                Arguments.of(
                        "../../../etc/passwd",
                        "path traversal",
                        SecurityEventCounter.EventType.KEY_NOT_FOUND,
                        true
                ),
                // SQL injection attack
                Arguments.of(
                        "' OR 1=1 --",
                        "SQL injection",
                        SecurityEventCounter.EventType.KEY_NOT_FOUND,
                        true
                ),
                // Null byte injection attack
                Arguments.of(
                        "valid-key-id\0malicious-suffix",
                        "null byte injection",
                        SecurityEventCounter.EventType.KEY_NOT_FOUND,
                        false
                ),
                // Command injection attack
                Arguments.of(
                        "$(rm -rf /tmp/*)",
                        "command injection",
                        SecurityEventCounter.EventType.KEY_NOT_FOUND,
                        true
                ),
                // Very long KID (potential DoS attack)
                Arguments.of(
                        "a".repeat(10000),
                        "very long",
                        SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED,
                        false
                )
        );
    }

    /**
     * Parameterized test for various KID injection attacks.
     * This test verifies that tokens with malicious KID headers are properly rejected.
     *
     * @param maliciousKid the malicious KID value to inject
     * @param attackType a description of the attack type for logging
     * @param expectedEventType the expected security event type
     * @param checkKeyNotFoundMessage whether to check for "Key not found" in the error message
     */
    @ParameterizedTest(name = "Should reject token with {1} in KID header")
    @MethodSource("kidInjectionTestCases")
    void shouldRejectTokenWithMaliciousKidHeader(String maliciousKid, String attackType,
            SecurityEventCounter.EventType expectedEventType,
            boolean checkKeyNotFoundMessage) {
        // Create a token with the malicious KID
        String token = createTokenWithMaliciousKid(maliciousKid);

        LOGGER.debug("Created token with %s KID: %s", attackType, token);

        // Reset the security event counter for this test
        tokenValidator.getSecurityEventCounter().reset();

        // Verify that the token is rejected
        var kidRequest = AccessTokenRequest.of(token);
        var exception = assertThrows(TokenValidationException.class,
                () -> tokenValidator.createAccessToken(kidRequest));

        // Verify the error message if needed
        LOGGER.debug("Exception message: %s", exception.getMessage());
        if (checkKeyNotFoundMessage) {
            assertTrue(exception.getMessage().contains("Key not found"),
                    "Error message should indicate key not found");
        }

        // Verify that the security event counter is incremented
        assertEquals(1, tokenValidator.getSecurityEventCounter().getCount(expectedEventType),
                "Security event counter should be incremented for " + expectedEventType);
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 3)
    @DisplayName("Should accept token with valid KID header")
    void shouldAcceptTokenWithValidKidHeader(TestTokenHolder tokenHolder) {
        String token = tokenHolder.getRawToken();

        LOGGER.debug("Using valid token: %s", token);

        var accessToken = tokenValidator.createAccessToken(AccessTokenRequest.of(token));
        assertNotNull(accessToken, "Token with valid KID should be accepted");
        assertEquals(0, tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.KEY_NOT_FOUND),
                "No KEY_NOT_FOUND security events should be recorded for valid token");
    }
}
