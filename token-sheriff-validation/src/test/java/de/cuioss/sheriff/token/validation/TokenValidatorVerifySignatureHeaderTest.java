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
package de.cuioss.sheriff.token.validation;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter.EventType;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.domain.token.SignatureVerifiedToken;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.test.JweTestTokenFactory;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.cuioss.sheriff.token.validation.TokenValidatorVerifySignatureTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the header handling of {@link TokenValidator#verifySignature(String)}: the header posture it enforces
 * and the one it skips, the {@code typ} header it returns, and the JWE it decrypts before verifying the inner
 * token. The remaining stages of the signature-only entry are covered by
 * {@link TokenValidatorVerifySignatureTest}, whose fixtures this class shares.
 */
@DisplayName("TokenValidator#verifySignature header handling")
class TokenValidatorVerifySignatureHeaderTest {

    private final List<TokenValidator> validators = new ArrayList<>();

    @AfterEach
    void closeValidators() {
        validators.forEach(TokenValidator::close);
    }

    @Nested
    @DisplayName("Header posture")
    class HeaderPosture {

        @Test
        @DisplayName("A token without kid is refused as MISSING_CLAIM")
        void shouldRefuseMissingKeyId() {
            String token = Jwts.builder()
                    .claims(validClaims())
                    .signWith(InMemoryKeyMaterialHandler.getDefaultPrivateKey(), Jwts.SIG.RS256)
                    .compact();
            TokenValidator validator = validator();

            assertRefused(EventType.MISSING_CLAIM, () -> validator.verifySignature(token));
            assertNotNull(validator.verifySignature(signed(validClaims(), null)), "positive control: with kid");
        }

        @Test
        @DisplayName("The issuer's expected token type is not applied, while the verdict paths apply it")
        void shouldSkipExpectedTokenType() {
            TokenValidator validator = register(TokenValidator.builder()
                    .issuerConfig(IssuerConfig.builder()
                            .issuerIdentifier(ISSUER)
                            .expectedAudience(AUDIENCE)
                            .expectedTokenType("at+jwt")
                            .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                            .build())
                    .build());
            String logoutToken = signed(validClaims(), "logout+jwt");

            SignatureVerifiedToken verified = validator.verifySignature(logoutToken);

            assertEquals(Optional.of("logout+jwt"), verified.getTypHeader());
            AccessTokenRequest request = AccessTokenRequest.of(logoutToken);
            assertRefused(EventType.TOKEN_TYPE_MISMATCH, () -> validator.createAccessToken(request));
        }
    }

    @Nested
    @DisplayName("typ header")
    class TypHeader {

        @Test
        @DisplayName("A present typ is returned verbatim")
        void shouldReturnPresentTyp() {
            assertEquals(Optional.of("logout+jwt"),
                    validator().verifySignature(signed(validClaims(), "logout+jwt")).getTypHeader());
        }

        @Test
        @DisplayName("An absent typ is returned as empty")
        void shouldReturnAbsentTyp() {
            assertEquals(Optional.empty(), validator().verifySignature(signed(validClaims(), null)).getTypHeader());
        }

        @Test
        @DisplayName("A mixed-case typ is returned unchanged, not normalised")
        void shouldReturnMixedCaseTypVerbatim() {
            assertEquals(Optional.of("Application/Logout+JWT"),
                    validator().verifySignature(signed(validClaims(), "Application/Logout+JWT")).getTypHeader());
        }
    }

    @Nested
    @DisplayName("JWE")
    class Jwe {

        @Test
        @DisplayName("A JWE is decrypted when JWE is configured and its inner token is verified")
        void shouldVerifyJweWithConfig() {
            KeyPair encryptionKeys = JweTestTokenFactory.generateRsaKeyPair();
            String jwe = JweTestTokenFactory.encryptAsJwe(signed(validClaims(), "logout+jwt"),
                    encryptionKeys.getPublic(), "RSA-OAEP", "A256GCM", null);
            TokenValidator validator = register(TokenValidator.builder()
                    .issuerConfig(issuerConfig())
                    .jweDecryptionConfig(JweDecryptionConfig.builder()
                            .defaultDecryptionKey(encryptionKeys.getPrivate()).build())
                    .build());

            SignatureVerifiedToken verified = validator.verifySignature(jwe);

            assertEquals(ISSUER, verified.getClaims().get(ClaimName.ISSUER.getName()).getOriginalString());
            assertEquals(Optional.of("logout+jwt"), verified.getTypHeader(), "typ of the inner token");
            assertEquals(jwe, verified.getRawToken(), "the raw token is the token as presented");
        }

        @Test
        @DisplayName("A JWE is refused as JWE_DECRYPTION_NOT_CONFIGURED when JWE is not configured")
        void shouldRefuseJweWithoutConfig() {
            KeyPair encryptionKeys = JweTestTokenFactory.generateRsaKeyPair();
            String jwe = JweTestTokenFactory.encryptAsJwe(signed(validClaims(), null),
                    encryptionKeys.getPublic(), "RSA-OAEP", "A256GCM", null);
            TokenValidator validator = validator();

            assertRefused(EventType.JWE_DECRYPTION_NOT_CONFIGURED, () -> validator.verifySignature(jwe));
            assertNotNull(validator.verifySignature(signed(validClaims(), null)), "positive control: a plain JWS");
        }
    }

    // === Fixtures ===

    private TokenValidator validator() {
        return register(TokenValidator.builder().issuerConfig(issuerConfig()).build());
    }

    private TokenValidator register(TokenValidator validator) {
        validators.add(validator);
        return validator;
    }
}
