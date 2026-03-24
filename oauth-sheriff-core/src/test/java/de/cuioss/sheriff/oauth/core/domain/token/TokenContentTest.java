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
package de.cuioss.sheriff.oauth.core.domain.token;

import de.cuioss.sheriff.oauth.core.TokenType;
import de.cuioss.sheriff.oauth.core.domain.claim.ClaimName;
import de.cuioss.sheriff.oauth.core.domain.claim.ClaimValue;
import de.cuioss.sheriff.oauth.core.domain.context.ValidationContext;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import lombok.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for {@link TokenContent} interface.
 * <p>
 * Tests the interface contract using concrete implementations.
 *
 * @author Oliver Wolff
 */
@EnableGeneratorController
class TokenContentTest {

    private final ValidationContext validationContext = new ValidationContext(60);

    @Test
    @DisplayName("Should provide access to claims")
    void shouldProvideAccessToClaims() {
        TestTokenContent token = createTestToken();

        Map<String, ClaimValue> claims = token.getClaims();
        assertNotNull(claims);
        assertFalse(claims.isEmpty());
        assertTrue(claims.containsKey("iss"));
        assertTrue(claims.containsKey("sub"));
        assertTrue(claims.containsKey("exp"));
    }

    @Test
    @DisplayName("Should get claim by name")
    void shouldGetClaimByName() {
        TestTokenContent token = createTestToken();

        Optional<ClaimValue> issuerClaim = token.getClaimOption(ClaimName.ISSUER);
        assertTrue(issuerClaim.isPresent());
        assertEquals("test-issuer", issuerClaim.get().getOriginalString());

        Optional<ClaimValue> nonExistentClaim = token.getClaimOption(ClaimName.AUDIENCE);
        assertFalse(nonExistentClaim.isPresent());
    }

    @Test
    @DisplayName("Should get issuer from mandatory claim")
    void shouldGetIssuerFromMandatoryClaim() {
        TestTokenContent token = createTestToken();

        String issuer = token.getIssuer();
        assertEquals("test-issuer", issuer);
    }

    @Test
    @DisplayName("Should throw exception when issuer claim is missing")
    void shouldThrowExceptionWhenIssuerClaimIsMissing() {
        TestTokenContent token = createTokenWithoutIssuer();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                token::getIssuer);
        assertTrue(exception.getMessage().contains("Issuer claim not present in token"));
    }

    @Test
    @DisplayName("Should get subject when claim is present")
    void shouldGetSubjectWhenClaimIsPresent() {
        TestTokenContent token = createTestToken();

        Optional<String> subject = token.getSubjectOption();
        assertTrue(subject.isPresent());
        assertEquals("test-subject", subject.get());
    }

    @Test
    @DisplayName("Should return empty Optional when subject claim is missing")
    void shouldReturnEmptyOptionalWhenSubjectClaimIsMissing() {
        TestTokenContent token = createTokenWithoutSubject();

        Optional<String> subject = token.getSubjectOption();
        assertFalse(subject.isPresent());
    }

    @Test
    @DisplayName("Should get expiration time from mandatory claim")
    void shouldGetExpirationTimeFromMandatoryClaim() {
        TestTokenContent token = createTestToken();

        OffsetDateTime expirationTime = token.getExpirationDateTime();
        assertNotNull(expirationTime);
        assertTrue(expirationTime.isAfter(OffsetDateTime.now()));
    }

    @Test
    @DisplayName("Should throw exception when expiration claim is missing")
    void shouldThrowExceptionWhenExpirationClaimIsMissing() {
        TestTokenContent token = createTokenWithoutExpiration();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                token::getExpirationDateTime);
        assertTrue(exception.getMessage().contains("ExpirationTime claim not present in token"));
    }

    @Test
    @DisplayName("Should get issued at time from mandatory claim")
    void shouldGetIssuedAtTimeFromMandatoryClaim() {
        TestTokenContent token = createTestToken();

        OffsetDateTime issuedAtTime = token.getIssuedAtDateTime();
        assertNotNull(issuedAtTime);
        assertTrue(issuedAtTime.isBefore(OffsetDateTime.now()));
    }

    @Test
    @DisplayName("Should throw exception when issued at claim is missing")
    void shouldThrowExceptionWhenIssuedAtClaimIsMissing() {
        TestTokenContent token = createTokenWithoutIssuedAt();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                token::getIssuedAtDateTime);
        assertTrue(exception.getMessage().contains("issued at time claim not present in token"));
    }

    @Test
    @DisplayName("Should handle optional not before claim")
    void shouldHandleOptionalNotBeforeClaim() {
        TestTokenContent tokenWithNotBefore = createTestTokenWithNotBefore();
        Optional<OffsetDateTime> notBefore = tokenWithNotBefore.getNotBeforeDateTime();
        assertTrue(notBefore.isPresent());

        TestTokenContent tokenWithoutNotBefore = createTestToken();
        Optional<OffsetDateTime> notBeforeEmpty = tokenWithoutNotBefore.getNotBeforeDateTime();
        assertFalse(notBeforeEmpty.isPresent());
    }

    @Test
    @DisplayName("Should check expiration correctly")
    void shouldCheckExpirationCorrectly() {
        TestTokenContent validToken = createTestToken();
        assertFalse(validToken.isExpired(validationContext));

        TestTokenContent expiredToken = createExpiredTestToken();
        assertTrue(expiredToken.isExpired(validationContext));
    }

    @Test
    @DisplayName("Should extend MinimalTokenContent interface")
    void shouldExtendMinimalTokenContentInterface() {
        TestTokenContent token = createTestToken();

        // Should have MinimalTokenContent methods
        assertEquals("raw-token-string", token.getRawToken());
        assertEquals(TokenType.ACCESS_TOKEN, token.getTokenType());
    }

    // Test implementation

    private static class TestTokenContent implements TokenContent {
        private final Map<String, ClaimValue> claims;
        private final String rawToken;
        private final TokenType tokenType;

        public TestTokenContent(Map<String, ClaimValue> claims, String rawToken, TokenType tokenType) {
            this.claims = claims;
            this.rawToken = rawToken;
            this.tokenType = tokenType;
        }

        @Override
        public @NonNull Map<String, ClaimValue> getClaims() {
            return claims;
        }

        @Override
        public String getRawToken() {
            return rawToken;
        }

        @Override
        public TokenType getTokenType() {
            return tokenType;
        }
    }

    // Helper methods for creating test tokens

    private TestTokenContent createTestToken() {
        Map<String, ClaimValue> claims = new HashMap<>();
        claims.put("iss", ClaimValue.forPlainString("test-issuer"));
        claims.put("sub", ClaimValue.forPlainString("test-subject"));
        claims.put("exp", ClaimValue.forDateTime("exp-value", OffsetDateTime.now().plusHours(1)));
        claims.put("iat", ClaimValue.forDateTime("iat-value", OffsetDateTime.now().minusMinutes(5)));

        return new TestTokenContent(claims, "raw-token-string", TokenType.ACCESS_TOKEN);
    }

    private TestTokenContent createTokenWithoutIssuer() {
        Map<String, ClaimValue> claims = new HashMap<>();
        claims.put("sub", ClaimValue.forPlainString("test-subject"));
        claims.put("exp", ClaimValue.forDateTime("exp-value", OffsetDateTime.now().plusHours(1)));
        claims.put("iat", ClaimValue.forDateTime("iat-value", OffsetDateTime.now().minusMinutes(5)));

        return new TestTokenContent(claims, "raw-token-string", TokenType.ACCESS_TOKEN);
    }

    private TestTokenContent createTokenWithoutSubject() {
        Map<String, ClaimValue> claims = new HashMap<>();
        claims.put("iss", ClaimValue.forPlainString("test-issuer"));
        claims.put("exp", ClaimValue.forDateTime("exp-value", OffsetDateTime.now().plusHours(1)));
        claims.put("iat", ClaimValue.forDateTime("iat-value", OffsetDateTime.now().minusMinutes(5)));

        return new TestTokenContent(claims, "raw-token-string", TokenType.ACCESS_TOKEN);
    }

    private TestTokenContent createTokenWithoutExpiration() {
        Map<String, ClaimValue> claims = new HashMap<>();
        claims.put("iss", ClaimValue.forPlainString("test-issuer"));
        claims.put("sub", ClaimValue.forPlainString("test-subject"));
        claims.put("iat", ClaimValue.forDateTime("iat-value", OffsetDateTime.now().minusMinutes(5)));

        return new TestTokenContent(claims, "raw-token-string", TokenType.ACCESS_TOKEN);
    }

    private TestTokenContent createTokenWithoutIssuedAt() {
        Map<String, ClaimValue> claims = new HashMap<>();
        claims.put("iss", ClaimValue.forPlainString("test-issuer"));
        claims.put("sub", ClaimValue.forPlainString("test-subject"));
        claims.put("exp", ClaimValue.forDateTime("exp-value", OffsetDateTime.now().plusHours(1)));

        return new TestTokenContent(claims, "raw-token-string", TokenType.ACCESS_TOKEN);
    }

    private TestTokenContent createTestTokenWithNotBefore() {
        Map<String, ClaimValue> claims = new HashMap<>();
        claims.put("iss", ClaimValue.forPlainString("test-issuer"));
        claims.put("sub", ClaimValue.forPlainString("test-subject"));
        claims.put("exp", ClaimValue.forDateTime("exp-value", OffsetDateTime.now().plusHours(1)));
        claims.put("iat", ClaimValue.forDateTime("iat-value", OffsetDateTime.now().minusMinutes(5)));
        claims.put("nbf", ClaimValue.forDateTime("nbf-value", OffsetDateTime.now().minusMinutes(2)));

        return new TestTokenContent(claims, "raw-token-string", TokenType.ACCESS_TOKEN);
    }

    private TestTokenContent createExpiredTestToken() {
        Map<String, ClaimValue> claims = new HashMap<>();
        claims.put("iss", ClaimValue.forPlainString("test-issuer"));
        claims.put("sub", ClaimValue.forPlainString("test-subject"));
        claims.put("exp", ClaimValue.forDateTime("exp-value", OffsetDateTime.now().minusHours(1)));
        claims.put("iat", ClaimValue.forDateTime("iat-value", OffsetDateTime.now().minusHours(2)));

        return new TestTokenContent(claims, "raw-token-string", TokenType.ACCESS_TOKEN);
    }
}