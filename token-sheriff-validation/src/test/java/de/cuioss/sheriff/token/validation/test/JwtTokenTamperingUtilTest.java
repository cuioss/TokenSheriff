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
package de.cuioss.sheriff.token.validation.test;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.domain.context.IdTokenRequest;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import de.cuioss.sheriff.token.validation.domain.token.IdTokenContent;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.pipeline.DecodedJwt;
import de.cuioss.sheriff.token.validation.pipeline.NonValidatingJwtParser;
import de.cuioss.sheriff.token.validation.security.SignatureAlgorithmPreferences;
import de.cuioss.sheriff.token.validation.test.JwtTokenTamperingUtil.TamperingStrategy;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for {@link JwtTokenTamperingUtil}.
 * Demonstrates how to use the utility with TestTokenGenerators.
 */
@EnableGeneratorController
@EnableTestLogger
class JwtTokenTamperingUtilTest {

    private static final String ISSUER = "Token-Test-testIssuer";

    private TokenValidator tokenValidator;

    @BeforeEach
    void setUp() {
        // Create validation factory with default configuration
        ParserConfig config = ParserConfig.builder().build();
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier(ISSUER)
                .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                .expectedClientId(TestTokenHolder.TEST_CLIENT_ID)
                .jwksContent(InMemoryJWKSFactory.createDefaultJwks())
                .algorithmPreferences(new SignatureAlgorithmPreferences())
                .build();
        tokenValidator = TokenValidator.builder().parserConfig(config).issuerConfig(issuerConfig).build();

    }

    @Test
    @DisplayName("Should validate untampered access token")
    void shouldValidateUntamperedAccessToken() {

        String token = TestTokenGenerators.accessTokens().next().getRawToken();
        AccessTokenContent result = tokenValidator.createAccessToken(AccessTokenRequest.of(token));
        assertNotNull(result, "Untampered token should be valid");
    }

    @Test
    @DisplayName("Should validate untampered ID-Token")
    void shouldValidateUntamperedIdToken() {

        String token = TestTokenGenerators.idTokens().next().getRawToken();
        IdTokenContent result = tokenValidator.createIdToken(IdTokenRequest.of(token));
        assertNotNull(result, "Untampered token should be valid");
    }

    @ParameterizedTest
    @EnumSource(TamperingStrategy.class)
    @DisplayName("Should reject tampered access token")
    void shouldRejectTamperedAccessToken(TamperingStrategy strategy) {

        String originalToken = TestTokenGenerators.accessTokens().next().getRawToken();
        String tamperedToken = JwtTokenTamperingUtil.applyTamperingStrategy(originalToken, strategy);

        assertNotEquals(originalToken, tamperedToken,
                "Token should be tampered using strategy: " + strategy.getDescription());
        assertEquals(3, tamperedToken.split("\\.", -1).length,
                "Tampered token should keep three segments. Strategy: " + strategy.getDescription());
        var accessTokenRequest = AccessTokenRequest.of(tamperedToken);
        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> tokenValidator.createAccessToken(accessTokenRequest),
                "Tampered token should be rejected. Strategy: " + strategy.getDescription());

        assertEquals(strategy.getExpectedEvent(), exception.getEventType(),
                "Tampered token should be rejected with the strategy's event. Strategy: " + strategy.getDescription());
    }

    @ParameterizedTest
    @EnumSource(TamperingStrategy.class)
    @DisplayName("Should reject tampered ID-Token")
    void shouldRejectTamperedIdToken(TamperingStrategy strategy) {

        String originalToken = TestTokenGenerators.idTokens().next().getRawToken();
        String tamperedToken = JwtTokenTamperingUtil.applyTamperingStrategy(originalToken, strategy);

        assertNotEquals(originalToken, tamperedToken,
                "Token should be tampered using strategy: " + strategy.getDescription());
        assertEquals(3, tamperedToken.split("\\.", -1).length,
                "Tampered token should keep three segments. Strategy: " + strategy.getDescription());
        var idTokenRequest = IdTokenRequest.of(tamperedToken);
        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> tokenValidator.createIdToken(idTokenRequest),
                "Tampered token should be rejected. Strategy: " + strategy.getDescription());

        assertEquals(strategy.getExpectedEvent(), exception.getEventType(),
                "Tampered token should be rejected with the strategy's event. Strategy: " + strategy.getDescription());
    }

    @Test
    @DisplayName("Should remove kid from header while keeping a parseable header and the original payload and signature")
    void shouldRemoveKidAndKeepParseableHeader() {
        String originalToken = TestTokenGenerators.accessTokens().next().getRawToken();
        String[] originalParts = originalToken.split("\\.");

        String tamperedToken = JwtTokenTamperingUtil.applyTamperingStrategy(originalToken, TamperingStrategy.REMOVE_KID);

        String[] tamperedParts = tamperedToken.split("\\.", -1);
        assertEquals(3, tamperedParts.length, "Tampered token should keep three segments");
        assertEquals(originalParts[1], tamperedParts[1], "Payload should be unchanged");
        assertEquals(originalParts[2], tamperedParts[2], "Signature should be unchanged");
        String decodedHeader = new String(Base64.getUrlDecoder().decode(tamperedParts[0]), StandardCharsets.UTF_8);
        assertFalse(decodedHeader.contains("\"kid\""), "Header should no longer contain kid: " + decodedHeader);
        DecodedJwt decodedJwt = NonValidatingJwtParser.builder()
                .securityEventCounter(new SecurityEventCounter()).build().decode(tamperedToken);
        assertAll("Parsed header",
                () -> assertTrue(decodedJwt.getKid().isEmpty(), "Parsed header should carry no kid"),
                () -> assertTrue(decodedJwt.getAlg().isPresent(), "Parsed header should keep alg"));
    }

    @Test
    @DisplayName("Should cut kid and exactly one adjacent comma in every member position")
    void shouldCutKidMemberInEveryPosition() {
        assertAll("kid member positions",
                () -> assertEquals("{\"alg\":\"RS256\",\"typ\":\"JWT\"}",
                        JwtTokenTamperingUtil.removeKidMember("{\"alg\":\"RS256\",\"kid\":\"k1\",\"typ\":\"JWT\"}")),
                () -> assertEquals("{\"alg\":\"RS256\"}",
                        JwtTokenTamperingUtil.removeKidMember("{\"kid\":\"k1\",\"alg\":\"RS256\"}")),
                () -> assertEquals("{\"alg\":\"RS256\" }",
                        JwtTokenTamperingUtil.removeKidMember("{\"alg\":\"RS256\" , \"kid\" : \"k1\"}")),
                () -> assertEquals("{}",
                        JwtTokenTamperingUtil.removeKidMember("{\"kid\":\"k\\\"1\"}")),
                () -> assertEquals("{\"alg\":\"RS256\"}",
                        JwtTokenTamperingUtil.removeKidMember("{\"alg\":\"RS256\"}")));
    }

    @Test
    @DisplayName("Should apply all tampering strategies to a validation")
    void shouldApplyAllTamperingStrategiesToToken() {

        String originalToken = TestTokenGenerators.accessTokens().next().getRawToken();
        for (TamperingStrategy strategy : TamperingStrategy.values()) {
            String tamperedToken = JwtTokenTamperingUtil.applyTamperingStrategy(originalToken, strategy);
            assertNotEquals(originalToken, tamperedToken,
                    "Token should be tampered using strategy: " + strategy.getDescription());
        }
    }
}
