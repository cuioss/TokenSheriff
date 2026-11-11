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
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.domain.context.IdTokenRequest;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An identifier claim whose JSON value is {@code null} is validated exactly like a token from which
 * that member is omitted.
 */
@EnableTestLogger
@DisplayName("An explicit null identifier claim validates exactly like an omitted member")
class IdentifierClaimNullEquivalenceTest {

    enum TokenKind {
        ACCESS_TOKEN, ID_TOKEN
    }

    /**
     * What a validation run observably produced: the refusal's event type and message, or the claim
     * names of the accepted token, plus the change of the {@code MISSING_CLAIM} counter.
     */
    private record Outcome(EventType eventType, String message, Set<String> claimNames, long missingClaimDelta) {
    }

    private IssuerConfig issuerConfig;

    @BeforeEach
    void setUp() {
        TestTokenHolder holder = TestTokenGenerators.accessTokens().next();
        issuerConfig = holder.getIssuerConfig();
    }

    /**
     * Shared with {@link SubjectClaimTypeRejectionTest}, which builds its non-string {@code sub}
     * tokens the same way.
     *
     * @return the claims of a valid access token for the default test issuer, as JSON literals
     */
    static Map<String, String> validClaims() {
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

    static String quoted(String value) {
        return "\"" + value + "\"";
    }

    /**
     * Signs a hand-built payload, because a JWT builder drops null-valued claims and could not
     * produce the explicit null under test.
     *
     * @param claims the claim names mapped to their JSON literals, written verbatim into the payload
     * @return a compact JWS over the payload, signed with the default RS256 test key
     */
    static String signedToken(Map<String, String> claims) {
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

    /**
     * Validates the token on a validator of its own, so neither the access-token cache nor the
     * counter of one run can influence the other.
     */
    private Outcome validate(TokenKind kind, Map<String, String> claims) {
        String token = signedToken(claims);
        try (TokenValidator validator = TokenValidator.builder().issuerConfig(issuerConfig).build()) {
            long before = validator.getSecurityEventCounter().getCount(EventType.MISSING_CLAIM);
            try {
                Set<String> claimNames = switch (kind) {
                    case ACCESS_TOKEN -> validator.createAccessToken(AccessTokenRequest.of(token)).getClaims().keySet();
                    case ID_TOKEN -> validator.createIdToken(IdTokenRequest.of(token)).getClaims().keySet();
                };
                long delta = validator.getSecurityEventCounter().getCount(EventType.MISSING_CLAIM) - before;
                return new Outcome(null, null, Set.copyOf(claimNames), delta);
            } catch (TokenValidationException e) {
                long delta = validator.getSecurityEventCounter().getCount(EventType.MISSING_CLAIM) - before;
                return new Outcome(e.getEventType(), e.getMessage(), Set.of(), delta);
            }
        }
    }

    @Test
    @DisplayName("Non-vacuity: the hand-built token validates as an access token and as an ID token")
    void shouldValidateHandBuiltToken() {
        Map<String, String> claims = validClaims();

        Outcome accessToken = validate(TokenKind.ACCESS_TOKEN, claims);
        Outcome idToken = validate(TokenKind.ID_TOKEN, claims);

        assertNull(accessToken.eventType(), "The access token must validate: " + accessToken.message());
        assertNull(idToken.eventType(), "The ID token must validate: " + idToken.message());
        assertTrue(accessToken.claimNames().containsAll(Set.of("iss", "sub", "jti", "azp")));
        assertEquals(0, accessToken.missingClaimDelta());
        assertEquals(0, idToken.missingClaimDelta());
    }

    @ParameterizedTest(name = "omitted {0}")
    @ValueSource(strings = {"jti", "client_id"})
    @DisplayName("An access token that omits an optional identifier claim is accepted")
    void shouldAcceptAccessTokenOmittingOptionalIdentifierClaim(String claimName) {
        // Absolute pin: the equivalence test below only proves null == omitted, which would also
        // hold if both were refused.
        Map<String, String> omitted = validClaims();
        omitted.remove(claimName);

        Outcome outcome = validate(TokenKind.ACCESS_TOKEN, omitted);

        assertNull(outcome.eventType(), "An access token without " + claimName + " must validate: " + outcome.message());
        assertFalse(outcome.claimNames().contains(claimName));
        assertEquals(0, outcome.missingClaimDelta());
    }

    static Stream<Arguments> identifierClaims() {
        return Stream.of("iss", "sub", "jti", "client_id", "azp")
                .flatMap(claim -> Stream.of(TokenKind.values()).map(kind -> Arguments.of(claim, kind)));
    }

    @ParameterizedTest(name = "{1}, {0}")
    @MethodSource("identifierClaims")
    @DisplayName("A null identifier claim has the same outcome as the omitted member")
    void shouldValidateNullClaimLikeOmittedMember(String claimName, TokenKind kind) {
        Map<String, String> withNull = validClaims();
        withNull.put(claimName, "null");
        Map<String, String> omitted = new LinkedHashMap<>(withNull);
        omitted.remove(claimName);

        Outcome nullOutcome = validate(kind, withNull);
        Outcome omittedOutcome = validate(kind, omitted);

        assertEquals(omittedOutcome, nullOutcome);
        assertFalse(nullOutcome.claimNames().contains(claimName),
                "An accepted token must not carry the " + claimName + " member");
    }
}
