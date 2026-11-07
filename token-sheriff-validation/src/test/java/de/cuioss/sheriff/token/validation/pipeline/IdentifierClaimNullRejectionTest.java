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
package de.cuioss.sheriff.token.validation.pipeline;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter.EventType;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An explicit JSON {@code null} for one of the identifier claims {@code iss}, {@code sub},
 * {@code jti}, {@code client_id} and {@code azp} is refused at {@link TokenValidator#createAccessToken}
 * as a typed, counted {@code MISSING_CLAIM} rejection instead of being read as an absent claim. An
 * absent optional claim keeps validating.
 * <p>
 * The tokens are signed here from a hand-built payload, because a JWT builder drops null-valued
 * claims and could not produce the explicit null under test.
 */
@EnableTestLogger
@DisplayName("Explicit JSON null for identifier claims at the TokenValidator entry point")
class IdentifierClaimNullRejectionTest {

    private TokenValidator validator;

    @BeforeEach
    void setUp() {
        TestTokenHolder holder = TestTokenGenerators.accessTokens().next();
        validator = TokenValidator.builder().issuerConfig(holder.getIssuerConfig()).build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    /** @return the claims of a valid access token for the default test issuer, as JSON literals */
    private static Map<String, String> validClaims() {
        long now = Instant.now().getEpochSecond();
        Map<String, String> claims = new LinkedHashMap<>();
        claims.put("iss", quoted(TestTokenHolder.TEST_ISSUER));
        claims.put("sub", quoted("test-subject"));
        claims.put("aud", "[" + quoted(TestTokenHolder.TEST_AUDIENCE) + "]");
        claims.put("azp", quoted(TestTokenHolder.TEST_CLIENT_ID));
        claims.put("exp", String.valueOf(now + 3600));
        claims.put("iat", String.valueOf(now));
        claims.put("jti", quoted(UUID.randomUUID().toString()));
        claims.put("scope", quoted("openid profile"));
        claims.put("typ", quoted("Bearer"));
        return claims;
    }

    private static String quoted(String value) {
        return "\"" + value + "\"";
    }

    private static String signedToken(Map<String, String> claims) {
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + InMemoryKeyMaterialHandler.DEFAULT_KEY_ID + "\"}";
        String payload = claims.entrySet().stream()
                .map(entry -> quoted(entry.getKey()) + ":" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
        String signingInput = base64Url(header) + "." + base64Url(payload);
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(InMemoryKeyMaterialHandler.getDefaultPrivateKey(InMemoryKeyMaterialHandler.Algorithm.RS256));
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign the test token", e);
        }
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Non-vacuity: the hand-built token validates when no claim is null")
    void shouldValidateHandBuiltToken() {
        var token = validator.createAccessToken(AccessTokenRequest.of(signedToken(validClaims())));

        assertEquals("test-subject", token.getSubject().orElseThrow());
        assertEquals(0, validator.getSecurityEventCounter().getCount(EventType.MISSING_CLAIM));
    }

    @ParameterizedTest(name = "{0}: null")
    @ValueSource(strings = {"sub", "jti", "client_id", "azp"})
    @DisplayName("An explicit null for an identifier claim is a counted MISSING_CLAIM rejection")
    void shouldRefuseExplicitNullIdentifierClaim(String claimName) {
        Map<String, String> claims = validClaims();
        claims.put(claimName, "null");
        String token = signedToken(claims);
        var request = AccessTokenRequest.of(token);

        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> validator.createAccessToken(request),
                "An explicit null " + claimName + " must not be read as an absent claim");

        assertEquals(EventType.MISSING_CLAIM, exception.getEventType());
        assertEquals("Malformed claim '" + claimName + "': expected a JSON string but got null",
                exception.getMessage());
        assertEquals(1, validator.getSecurityEventCounter().getCount(EventType.MISSING_CLAIM));
        assertEquals(0, validator.getSecurityEventCounter().getCount(EventType.ACCESS_TOKEN_CREATED));
    }

    /**
     * An explicit null {@code iss} is refused at issuer extraction, before the token builder runs,
     * exactly as an absent {@code iss} already was.
     */
    @Test
    @DisplayName("An explicit null iss is a counted MISSING_CLAIM rejection")
    void shouldRefuseExplicitNullIssuer() {
        Map<String, String> claims = validClaims();
        claims.put("iss", "null");
        var request = AccessTokenRequest.of(signedToken(claims));

        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> validator.createAccessToken(request));

        assertEquals(EventType.MISSING_CLAIM, exception.getEventType());
        assertEquals("Missing required issuer (iss) claim in token", exception.getMessage());
        assertEquals(1, validator.getSecurityEventCounter().getCount(EventType.MISSING_CLAIM));
    }

    @ParameterizedTest(name = "{0}: absent")
    @ValueSource(strings = {"jti", "client_id"})
    @DisplayName("An absent optional identifier claim still validates")
    void shouldAcceptAbsentOptionalIdentifierClaim(String claimName) {
        Map<String, String> claims = validClaims();
        claims.remove(claimName);

        var token = validator.createAccessToken(AccessTokenRequest.of(signedToken(claims)));

        assertTrue(token.getClaimOption(ClaimName.fromString(claimName).orElseThrow()).isEmpty());
        assertEquals(0, validator.getSecurityEventCounter().getCount(EventType.MISSING_CLAIM));
    }

    @Test
    @DisplayName("An explicit null for a claim outside the identifier rule still reads as absent")
    void shouldAcceptExplicitNullForOtherClaim() {
        Map<String, String> claims = validClaims();
        claims.put("name", "null");

        var token = validator.createAccessToken(AccessTokenRequest.of(signedToken(claims)));

        assertFalse(token.getClaims().containsKey("name"));
        assertEquals(0, validator.getSecurityEventCounter().getCount(EventType.MISSING_CLAIM));
    }
}
