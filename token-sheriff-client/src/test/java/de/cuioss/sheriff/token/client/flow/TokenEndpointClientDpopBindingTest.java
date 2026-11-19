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
package de.cuioss.sheriff.token.client.flow;

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.dpop.DpopProofGenerator;
import de.cuioss.sheriff.token.client.dpop.SenderConstraint;
import de.cuioss.sheriff.token.client.token.TokenResponse;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issuance-time sender-constraint binding of {@link TokenEndpointClient}.
 * <p>
 * Under a DPoP constraint the issued token must be bound to the proof key — {@code token_type=DPoP}, and
 * a JWT access token's {@code cnf.jkt} equal to the proof-key thumbprint. A {@code Bearer} downgrade, a
 * foreign {@code jkt}, a missing {@code cnf.jkt} and a malformed {@code cnf} (not an object, or a
 * non-string {@code jkt}) are each rejected with {@link RedeemedResponseException}; an access token that
 * is not a JWS with a JSON-object payload (opaque, encrypted, empty or non-base64url payload part,
 * non-object JSON payload) skips only the {@code jkt} comparison; mTLS and unconstrained requests are not
 * checked.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("TokenEndpointClient issuance-time DPoP binding")
class TokenEndpointClientDpopBindingTest {

    /** Cached 2048-bit RSA key pair, generated once for the whole class (DPoP proof material). */
    private static final KeyPair RSA_KEY_PAIR;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            RSA_KEY_PAIR = generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key pair generation failed", e);
        }
    }

    @Getter
    private final TokenEndpointStubDispatcher moduleDispatcher = new TokenEndpointStubDispatcher();

    @BeforeEach
    void setUp() {
        moduleDispatcher.reset();
    }

    private static ClientConfiguration config() {
        return ClientConfiguration.builder()
                .issuer("https://issuer.example.com")
                .clientId(Generators.nonBlankStrings().next())
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .redirectUri("https://rp.example.com/callback")
                .allowInsecureHttp(true)
                .allowLoopbackEgress(true)
                .build();
    }

    private static SenderConstraint dpopConstraint() {
        return SenderConstraint.dpop(new DpopProofGenerator(RSA_KEY_PAIR, "RS256"));
    }

    @Test
    @DisplayName("a Bearer token_type issued under a DPoP constraint is rejected as a redeemed downgrade")
    void shouldRejectBearerTokenTypeUnderDpopConstraint(URIBuilder uriBuilder) {
        SenderConstraint constraint = dpopConstraint();
        moduleDispatcher.respondWith(tokenResponse(jws(cnfPayload(jktOf(constraint))), "Bearer"));

        RedeemedResponseException thrown = redeemExpectingRedeemedRejection(uriBuilder, constraint);

        assertTrue(thrown.getMessage().contains("token_type 'Bearer'"),
                "the rejection must name the downgraded token_type, but was: " + thrown.getMessage());
    }

    @Test
    @DisplayName("a DPoP JWT access token whose cnf.jkt differs from the proof-key thumbprint is rejected")
    void shouldRejectMismatchedJktUnderDpopConstraint(URIBuilder uriBuilder) {
        SenderConstraint constraint = dpopConstraint();
        String foreignJkt = Generators.letterStrings(43, 43).next();
        moduleDispatcher.respondWith(tokenResponse(jws(cnfPayload(foreignJkt)), "DPoP"));

        RedeemedResponseException thrown = redeemExpectingRedeemedRejection(uriBuilder, constraint);

        assertTrue(thrown.getMessage().contains("does not match the DPoP proof-key thumbprint"),
                "the rejection must name the jkt mismatch, but was: " + thrown.getMessage());
    }

    @Test
    @DisplayName("a DPoP JWT access token without a cnf claim is rejected as unbound")
    void shouldRejectJwtWithoutCnfUnderDpopConstraint(URIBuilder uriBuilder) {
        SenderConstraint constraint = dpopConstraint();
        moduleDispatcher.respondWith(tokenResponse(jws(subjectOnlyPayload()), "DPoP"));

        RedeemedResponseException thrown = redeemExpectingRedeemedRejection(uriBuilder, constraint);

        assertTrue(thrown.getMessage().contains("without a cnf.jkt binding"),
                "the rejection must name the missing binding, but was: " + thrown.getMessage());
    }

    @Test
    @DisplayName("a DPoP JWT access token whose cnf carries no jkt member is rejected as unbound")
    void shouldRejectCnfWithoutJktUnderDpopConstraint(URIBuilder uriBuilder) {
        SenderConstraint constraint = dpopConstraint();
        String x5tOnly = "{\"sub\":\"" + Generators.letterStrings(8, 16).next()
                + "\",\"cnf\":{\"x5t#S256\":\"" + Generators.letterStrings(43, 43).next() + "\"}}";
        moduleDispatcher.respondWith(tokenResponse(jws(x5tOnly), "DPoP"));

        RedeemedResponseException thrown = redeemExpectingRedeemedRejection(uriBuilder, constraint);

        assertTrue(thrown.getMessage().contains("without a cnf.jkt binding"),
                "a cnf without jkt is not a DPoP binding, but the rejection was: " + thrown.getMessage());
    }

    @Test
    @DisplayName("a DPoP JWT access token bound to the proof-key thumbprint is accepted")
    void shouldAcceptMatchingJktUnderDpopConstraint(URIBuilder uriBuilder) {
        SenderConstraint constraint = dpopConstraint();
        String accessToken = jws(cnfPayload(jktOf(constraint)));
        moduleDispatcher.respondWith(tokenResponse(accessToken, "DPoP"));

        TokenResponse response = redeem(uriBuilder, constraint);

        assertEquals(accessToken, response.accessToken, "the bound access token must be returned unchanged");
    }

    @Test
    @DisplayName("the DPoP token_type is matched case-insensitively (RFC 6749 §5.1)")
    void shouldAcceptLowerCaseDpopTokenType(URIBuilder uriBuilder) {
        SenderConstraint constraint = dpopConstraint();
        moduleDispatcher.respondWith(tokenResponse(jws(cnfPayload(jktOf(constraint))), "dpop"));

        TokenResponse response = redeem(uriBuilder, constraint);

        assertEquals("dpop", response.tokenType, "a lower-case DPoP token_type is the DPoP type");
    }

    @Test
    @DisplayName("an opaque access token with token_type DPoP is accepted; only the jkt comparison is skipped")
    void shouldAcceptOpaqueAccessTokenUnderDpopConstraint(URIBuilder uriBuilder) {
        String opaque = Generators.letterStrings(30, 60).next();
        moduleDispatcher.respondWith(tokenResponse(opaque, "DPoP"));

        TokenResponse response = redeem(uriBuilder, dpopConstraint());

        assertEquals(opaque, response.accessToken, "an opaque DPoP token carries no cnf to compare");
    }

    @Test
    @DisplayName("an encrypted (five-part JWE) access token with token_type DPoP skips the jkt comparison")
    void shouldAcceptJweAccessTokenUnderDpopConstraint(URIBuilder uriBuilder) {
        String jwe = String.join(".", segment(), segment(), segment(), segment(), segment());
        moduleDispatcher.respondWith(tokenResponse(jwe, "DPoP"));

        TokenResponse response = redeem(uriBuilder, dpopConstraint());

        assertEquals(jwe, response.accessToken, "a JWE payload cannot be read, so only token_type is checked");
    }

    @Test
    @DisplayName("a DPoP JWT access token whose cnf is a string is rejected, not skipped as undecodable")
    void shouldRejectStringCnfUnderDpopConstraint(URIBuilder uriBuilder) {
        assertMalformedCnfRejected(uriBuilder, "\"" + Generators.letterStrings(8, 16).next() + "\"");
    }

    @Test
    @DisplayName("a DPoP JWT access token whose cnf is an array is rejected, not skipped as undecodable")
    void shouldRejectArrayCnfUnderDpopConstraint(URIBuilder uriBuilder) {
        assertMalformedCnfRejected(uriBuilder, "[\"" + Generators.letterStrings(8, 16).next() + "\"]");
    }

    @Test
    @DisplayName("a DPoP JWT access token whose cnf.jkt is a number is rejected, not skipped as undecodable")
    void shouldRejectNumericJktUnderDpopConstraint(URIBuilder uriBuilder) {
        assertMalformedCnfRejected(uriBuilder, "{\"jkt\":" + Generators.integers(1, 100_000).next() + "}");
    }

    @Test
    @DisplayName("a DPoP JWT access token whose cnf.jkt is an array is rejected, not skipped as undecodable")
    void shouldRejectArrayJktUnderDpopConstraint(URIBuilder uriBuilder) {
        SenderConstraint constraint = dpopConstraint();
        assertMalformedCnfRejected(uriBuilder, constraint, "{\"jkt\":[\"" + jktOf(constraint) + "\"]}");
    }

    @Test
    @DisplayName("a DPoP JWT access token whose cnf.jkt is null is rejected as unbound")
    void shouldRejectNullJktUnderDpopConstraint(URIBuilder uriBuilder) {
        SenderConstraint constraint = dpopConstraint();
        moduleDispatcher.respondWith(tokenResponse(jws(payloadWithCnf("{\"jkt\":null}")), "DPoP"));

        RedeemedResponseException thrown = redeemExpectingRedeemedRejection(uriBuilder, constraint);

        assertTrue(thrown.getMessage().contains("without a cnf.jkt binding"),
                "a null jkt is no binding, but the rejection was: " + thrown.getMessage());
    }

    @Test
    @DisplayName("a three-part token with an empty payload part skips only the jkt comparison")
    void shouldAcceptEmptyPayloadPartUnderDpopConstraint(URIBuilder uriBuilder) {
        assertJktComparisonSkipped(uriBuilder, segment() + ".." + segment());
    }

    @Test
    @DisplayName("a three-part token whose payload part is not base64url skips only the jkt comparison")
    void shouldAcceptNonBase64UrlPayloadUnderDpopConstraint(URIBuilder uriBuilder) {
        assertJktComparisonSkipped(uriBuilder, segment() + ".!!not*base64url!!." + segment());
    }

    @Test
    @DisplayName("a JWS whose payload is a JSON array skips only the jkt comparison")
    void shouldAcceptJsonArrayPayloadUnderDpopConstraint(URIBuilder uriBuilder) {
        String foreignJkt = Generators.letterStrings(43, 43).next();
        assertJktComparisonSkipped(uriBuilder, jws("[{\"cnf\":{\"jkt\":\"" + foreignJkt + "\"}}]"));
    }

    @Test
    @DisplayName("a JWS whose payload is a JSON string skips only the jkt comparison")
    void shouldAcceptJsonStringPayloadUnderDpopConstraint(URIBuilder uriBuilder) {
        assertJktComparisonSkipped(uriBuilder, jws("\"" + Generators.letterStrings(8, 16).next() + "\""));
    }

    @Test
    @DisplayName("a JWS whose payload is the JSON literal null skips only the jkt comparison")
    void shouldAcceptJsonNullPayloadUnderDpopConstraint(URIBuilder uriBuilder) {
        String accessToken = jws("null");
        moduleDispatcher.respondWith(tokenResponse(accessToken, "DPoP"));

        TokenResponse response = redeem(uriBuilder, dpopConstraint());

        assertAll("JSON null payload",
                () -> assertEquals(accessToken, response.accessToken,
                        "a null payload carries no readable cnf, so the token is accepted unchanged"),
                () -> assertEquals("DPoP", response.tokenType, "the token_type check still applies"));
    }

    @Test
    @DisplayName("an mTLS-constrained request accepts a Bearer token")
    void shouldAcceptBearerUnderMtlsConstraint(URIBuilder uriBuilder) {
        moduleDispatcher.respondWith(tokenResponse(jws(subjectOnlyPayload()), "Bearer"));
        var mtls = SenderConstraint.mtls(Generators.letterStrings(20, 40).next());

        TokenResponse response = redeem(uriBuilder, mtls);

        assertEquals("Bearer", response.tokenType, "an mTLS-bound token is presented as Bearer");
    }

    @Test
    @DisplayName("an unconstrained request accepts a Bearer JWT without cnf")
    void shouldAcceptBearerWithoutConstraint(URIBuilder uriBuilder) {
        moduleDispatcher.respondWith(tokenResponse(jws(subjectOnlyPayload()), "Bearer"));

        TokenResponse response = redeem(uriBuilder, null);

        assertEquals("Bearer", response.tokenType, "no sender-constraint means no binding check");
    }

    private TokenResponse redeem(URIBuilder uriBuilder, SenderConstraint constraint) {
        var client = new TokenEndpointClient(config());
        String endpoint = uriBuilder.addPathSegment("token").buildAsString();
        return client.requestToken(endpoint, Map.of("grant_type", "authorization_code"), Map.of(), constraint);
    }

    private RedeemedResponseException redeemExpectingRedeemedRejection(URIBuilder uriBuilder,
            SenderConstraint constraint) {
        var client = new TokenEndpointClient(config());
        String endpoint = uriBuilder.addPathSegment("token").buildAsString();
        Map<String, String> requestParams = Map.of("grant_type", "authorization_code");
        Map<String, String> requestHeaders = Map.of();

        RedeemedResponseException thrown = assertThrows(RedeemedResponseException.class,
                () -> client.requestToken(endpoint, requestParams, requestHeaders, constraint),
                "an unbound token issued under a DPoP constraint must be rejected after redemption");
        assertEquals(1, moduleDispatcher.getCallCounter(), "the rejection follows a single, successful redemption");
        return thrown;
    }

    private void assertMalformedCnfRejected(URIBuilder uriBuilder, String cnfJson) {
        assertMalformedCnfRejected(uriBuilder, dpopConstraint(), cnfJson);
    }

    /**
     * A payload that is a JSON object is a JWT, so a {@code cnf} that cannot be read as an object with
     * a string {@code jkt} must fail closed exactly like a missing {@code cnf.jkt} — it must never fall
     * into the "not a JWT" skip, which would accept the token unchecked.
     */
    private void assertMalformedCnfRejected(URIBuilder uriBuilder, SenderConstraint constraint, String cnfJson) {
        moduleDispatcher.respondWith(tokenResponse(jws(payloadWithCnf(cnfJson)), "DPoP"));

        RedeemedResponseException thrown = redeemExpectingRedeemedRejection(uriBuilder, constraint);

        assertTrue(thrown.getMessage().contains("cnf claim is not an object with a string jkt"),
                "a malformed cnf must be rejected as unbound, but the rejection was: " + thrown.getMessage());
    }

    /**
     * An access token that is not a JWS with a JSON-object payload skips only the {@code jkt}
     * comparison: with {@code token_type=DPoP} it is accepted unchanged.
     */
    private void assertJktComparisonSkipped(URIBuilder uriBuilder, String accessToken) {
        moduleDispatcher.respondWith(tokenResponse(accessToken, "DPoP"));

        TokenResponse response = redeem(uriBuilder, dpopConstraint());

        assertEquals(accessToken, response.accessToken, "a token that is not a JWT carries no readable cnf");
    }

    private static String payloadWithCnf(String cnfJson) {
        return "{\"sub\":\"" + Generators.letterStrings(8, 16).next() + "\",\"cnf\":" + cnfJson + "}";
    }

    private static String jktOf(SenderConstraint constraint) {
        return constraint.binding().confirmation();
    }

    private static String tokenResponse(String accessToken, String tokenType) {
        return "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"" + tokenType
                + "\",\"expires_in\":300}";
    }

    private static String cnfPayload(String jkt) {
        return "{\"sub\":\"" + Generators.letterStrings(8, 16).next() + "\",\"cnf\":{\"jkt\":\"" + jkt + "\"}}";
    }

    private static String subjectOnlyPayload() {
        return "{\"sub\":\"" + Generators.letterStrings(8, 16).next() + "\"}";
    }

    /**
     * Builds a compact JWS around {@code payloadJson}. The signature segment is filler: the binding
     * check only decodes the payload and never verifies the signature.
     */
    private static String jws(String payloadJson) {
        return base64Url("{\"alg\":\"RS256\",\"typ\":\"at+jwt\"}") + "." + base64Url(payloadJson) + "." + segment();
    }

    private static String segment() {
        return base64Url(Generators.letterStrings(16, 32).next());
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
