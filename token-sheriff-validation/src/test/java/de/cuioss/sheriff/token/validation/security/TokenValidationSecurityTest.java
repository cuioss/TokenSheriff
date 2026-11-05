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
package de.cuioss.sheriff.token.validation.security;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenType;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.test.InMemoryJWKSFactory;
import de.cuioss.sheriff.token.validation.test.JwtTokenTamperingUtil;
import de.cuioss.sheriff.token.validation.test.JwtTokenTamperingUtil.TamperingStrategy;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.junit.TestTokenSource;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the security aspects of token validation.
 * <p>
 * These tests verify that the token validation library correctly rejects
 * tampered tokens and is resistant to various token cracking attempts.
 * <p>
 * What is tested:
 * <ul>
 *   <li>Rejection of tokens with tampered headers</li>
 *   <li>Rejection of tokens with tampered payloads</li>
 *   <li>Rejection of tokens with tampered signatures</li>
 *   <li>Rejection of tokens with invalid algorithms</li>
 * </ul>
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("Token Validation Security Tests")
class TokenValidationSecurityTest {

    private TokenValidator tokenValidator;

    @BeforeEach
    void setUp() {
        // Create issuer config with JWKS content
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier("Token-Test-testIssuer")
                .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                .jwksContent(InMemoryJWKSFactory.createDefaultJwks())
                .build();

        // Create validation factory
        ParserConfig config = ParserConfig.builder().build();
        tokenValidator = TokenValidator.builder().parserConfig(config).issuerConfig(issuerConfig).build();
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 3)
    @DisplayName("Should reject tokens with tampered payloads")
    void shouldRejectTokensWithTamperedPayloads(TestTokenHolder tokenHolder) {
        String validToken = tokenHolder.getRawToken();

        String[] parts = validToken.split("\\.");
        String payload = parts[1];
        byte[] payloadBytes = Base64.getUrlDecoder().decode(payload);
        String payloadJson = new String(payloadBytes);

        String tamperedPayloadJson = payloadJson.replaceAll("\"sub\":\"[^\"]*\"", "\"sub\":\"tampered-subject\"");
        assertNotEquals(payloadJson, tamperedPayloadJson, "Payload should be tampered");
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedPayloadJson.getBytes());
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        var request1 = AccessTokenRequest.of(tamperedToken);
        TokenValidationException exception = assertThrows(TokenValidationException.class, () ->
                        tokenValidator.createAccessToken(request1),
                "Should reject token with tampered payload");
        assertEquals(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED, exception.getEventType(),
                "Tampered payload under the original signature should fail signature validation");
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 3)
    @DisplayName("Should reject tokens with tampered signatures")
    void shouldRejectTokensWithTamperedSignatures(TestTokenHolder tokenHolder) {
        assertRejectedWithExpectedEvent(tokenHolder, TamperingStrategy.MODIFY_SIGNATURE_RANDOM_CHAR);
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 3)
    @DisplayName("Should reject tokens with algorithm 'none'")
    void shouldRejectTokensWithAlgorithmNone(TestTokenHolder tokenHolder) {
        assertRejectedWithExpectedEvent(tokenHolder, TamperingStrategy.ALGORITHM_NONE);
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 5)
    @DisplayName("Should accept valid tokens")
    void shouldAcceptValidTokens(TestTokenHolder tokenHolder) {
        String validToken = tokenHolder.getRawToken();
        AccessTokenContent tokenContent = tokenValidator.createAccessToken(AccessTokenRequest.of(validToken));

        assertNotNull(tokenContent, "Token content should not be null");
        assertEquals("Token-Test-testIssuer", tokenContent.getIssuer(), "Issuer should match expected");
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 3)
    @DisplayName("Should reject tokens with algorithm downgrade")
    void shouldRejectTokensWithAlgorithmDowngrade(TestTokenHolder tokenHolder) {
        assertRejectedWithExpectedEvent(tokenHolder, TamperingStrategy.ALGORITHM_DOWNGRADE);
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 3)
    @DisplayName("Should reject tokens with invalid key ID")
    void shouldRejectTokensWithInvalidKeyId(TestTokenHolder tokenHolder) {
        assertRejectedWithExpectedEvent(tokenHolder, TamperingStrategy.INVALID_KID);
    }

    private void assertRejectedWithExpectedEvent(TestTokenHolder tokenHolder, TamperingStrategy strategy) {
        String validToken = tokenHolder.getRawToken();
        String tamperedToken = JwtTokenTamperingUtil.applyTamperingStrategy(validToken, strategy);
        assertNotEquals(validToken, tamperedToken,
                "Token should be tampered using strategy: " + strategy.getDescription());

        var request = AccessTokenRequest.of(tamperedToken);
        TokenValidationException exception = assertThrows(TokenValidationException.class, () ->
                        tokenValidator.createAccessToken(request),
                "Should reject token tampered with strategy: " + strategy.getDescription());
        assertEquals(strategy.getExpectedEvent(), exception.getEventType(),
                "Tampered token should be rejected with the strategy's event. Strategy: " + strategy.getDescription());
    }
}
