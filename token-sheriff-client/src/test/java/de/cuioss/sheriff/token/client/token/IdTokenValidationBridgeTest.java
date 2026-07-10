/*
 * Copyright © 2022 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.token.client.token;

import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.domain.token.IdTokenContent;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnableTestLogger
@EnableGeneratorController
@DisplayName("IdTokenValidationBridge pipeline validation with nonce binding")
class IdTokenValidationBridgeTest {

    private static final String NONCE_CLAIM = "nonce";

    private TestTokenHolder holder;
    private IdTokenValidationBridge bridge;

    @BeforeEach
    void setUp() {
        holder = TestTokenGenerators.idTokens().next();
        TokenValidator validator = TokenValidator.builder().issuerConfig(holder.getIssuerConfig()).build();
        bridge = new IdTokenValidationBridge(validator);
    }

    @Test
    @DisplayName("Should validate an ID token whose nonce matches the flow nonce")
    void shouldValidateMatchingNonce() {
        String nonce = Generators.letterStrings(20, 40).next();
        holder.withClaim(NONCE_CLAIM, ClaimValue.forPlainString(nonce));

        IdTokenContent content = bridge.validateIdToken(holder.getRawToken(), nonce);

        assertNotNull(content, "the validated ID token content must be returned on a nonce match");
    }

    @Test
    @DisplayName("Should reject an ID token whose nonce does not match the flow nonce")
    void shouldRejectNonceMismatch() {
        holder.withClaim(NONCE_CLAIM, ClaimValue.forPlainString(Generators.letterStrings(20, 40).next()));
        String rawToken = holder.getRawToken();
        String expectedNonce = Generators.letterStrings(20, 40).next();

        var thrown = assertThrows(IllegalStateException.class,
                () -> bridge.validateIdToken(rawToken, expectedNonce));
        assertNotNull(thrown.getMessage(), "the fail-closed rejection must carry a message");
    }

    @Test
    @DisplayName("Should reject an ID token that carries no nonce claim")
    void shouldRejectMissingNonce() {
        holder.withoutClaim(NONCE_CLAIM);
        String rawToken = holder.getRawToken();
        String expectedNonce = Generators.letterStrings(20, 40).next();

        assertThrows(IllegalStateException.class,
                () -> bridge.validateIdToken(rawToken, expectedNonce));
    }

    @Test
    @DisplayName("Should reject an ID token that fails pipeline validation")
    void shouldRejectTamperedToken() {
        String nonce = Generators.letterStrings(20, 40).next();
        holder.withClaim(NONCE_CLAIM, ClaimValue.forPlainString(nonce));
        holder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("https://attacker.example.com"));
        String rawToken = holder.getRawToken();

        assertThrows(TokenValidationException.class, () -> bridge.validateIdToken(rawToken, nonce));
    }

    @Test
    @DisplayName("Should reject null construction and validation arguments")
    void shouldRejectNullArguments() {
        String nonce = Generators.letterStrings(20, 40).next();
        holder.withClaim(NONCE_CLAIM, ClaimValue.forPlainString(nonce));
        String rawToken = holder.getRawToken();

        assertAll("null-guards",
                () -> assertThrows(NullPointerException.class, () -> new IdTokenValidationBridge(null)),
                () -> assertThrows(NullPointerException.class, () -> bridge.validateIdToken(null, nonce)),
                () -> assertThrows(NullPointerException.class, () -> bridge.validateIdToken(rawToken, null)));
    }
}
