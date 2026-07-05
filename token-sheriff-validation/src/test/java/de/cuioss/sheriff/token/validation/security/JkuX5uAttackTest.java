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
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenType;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.test.InMemoryJWKSFactory;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.junit.TestTokenSource;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for validating protection against JKU/X5U header abuse attacks.
 * <p>
 * This attack allows attackers to host their own key sets and instruct the token
 * validator to fetch and trust those keys by including JKU (JWK Set URL) or X5U
 * (X.509 URL) headers pointing to attacker-controlled URLs.
 * <p>
 * This test verifies that the library correctly rejects tokens with JKU or X5U
 * headers pointing to malicious URLs.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("Tests for JKU/X5U Header Abuse Protection")
class JkuX5uAttackTest {

    private TokenValidator tokenValidator;

    @BeforeEach
    void setUp() {
        // Create issuer config with JWKS content
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier(TestTokenHolder.TEST_ISSUER)
                .expectedAudience("test-client")
                .jwksContent(InMemoryJWKSFactory.createDefaultJwks())
                .build();

        // Create validation factory
        tokenValidator = TokenValidator.builder().parserConfig(ParserConfig.builder().build()).issuerConfig(issuerConfig).build();

    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 3)
    @DisplayName("Should reject tokens with JKU header pointing to malicious URL")
    void shouldRejectTokenWithJkuHeader(TestTokenHolder tokenHolder) {
        String validToken = tokenHolder.getRawToken();
        String[] parts = validToken.split("\\.");

        String header = parts[0];
        byte[] headerBytes = Base64.getUrlDecoder().decode(header);
        String headerJson = new String(headerBytes);

        String maliciousJku = "\"jku\":\"https://attacker-controlled-site.com/jwks.json\"";
        String tamperedHeaderJson = headerJson.substring(0, headerJson.length() - 1) + "," + maliciousJku + "}";
        String tamperedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedHeaderJson.getBytes());
        String tamperedToken = tamperedHeader + "." + parts[1] + "." + parts[2];

        var jkuRequest = AccessTokenRequest.of(tamperedToken);
        assertThrows(TokenValidationException.class,
                () -> tokenValidator.createAccessToken(jkuRequest),
                "Should reject token with malicious JKU header");
        assertTrue(tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED) >= 0,
                "Security event counter should track JKU header attack attempts");
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 3)
    @DisplayName("Should reject tokens with X5U header pointing to malicious URL")
    void shouldRejectTokenWithX5uHeader(TestTokenHolder tokenHolder) {
        String validToken = tokenHolder.getRawToken();
        String[] parts = validToken.split("\\.");

        String header = parts[0];
        byte[] headerBytes = Base64.getUrlDecoder().decode(header);
        String headerJson = new String(headerBytes);

        String maliciousX5u = "\"x5u\":\"https://attacker-controlled-site.com/keys.pem\"";
        String tamperedHeaderJson = headerJson.substring(0, headerJson.length() - 1) + "," + maliciousX5u + "}";
        String tamperedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedHeaderJson.getBytes());
        String tamperedToken = tamperedHeader + "." + parts[1] + "." + parts[2];

        var x5uRequest = AccessTokenRequest.of(tamperedToken);
        assertThrows(TokenValidationException.class,
                () -> tokenValidator.createAccessToken(x5uRequest),
                "Should reject token with malicious X5U header");
        assertTrue(tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED) >= 0,
                "Security event counter should track X5U header attack attempts");
    }
}
