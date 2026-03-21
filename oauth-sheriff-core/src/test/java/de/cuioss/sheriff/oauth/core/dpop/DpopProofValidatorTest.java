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
package de.cuioss.sheriff.oauth.core.dpop;

import de.cuioss.sheriff.oauth.core.IssuerConfig;
import de.cuioss.sheriff.oauth.core.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.oauth.core.exception.TokenValidationException;
import de.cuioss.sheriff.oauth.core.json.JwtHeader;
import de.cuioss.sheriff.oauth.core.json.MapRepresentation;
import de.cuioss.sheriff.oauth.core.pipeline.DecodedJwt;
import de.cuioss.sheriff.oauth.core.security.SecurityEventCounter;
import de.cuioss.sheriff.oauth.core.security.SecurityEventCounter.EventType;
import de.cuioss.sheriff.oauth.core.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.oauth.core.test.InMemoryKeyMaterialHandler.Algorithm;
import de.cuioss.sheriff.oauth.core.util.JwkThumbprintUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureAlgorithm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECPoint;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DpopProofValidatorTest {

    private static final String TEST_ISSUER = "https://test-issuer.example.com";

    private SecurityEventCounter securityEventCounter;
    private DpopReplayProtection replayProtection;
    private DpopProofValidator validator;

    @BeforeEach
    void setUp() {
        securityEventCounter = new SecurityEventCounter();
        replayProtection = new DpopReplayProtection(300, 10_000);

        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier(TEST_ISSUER)
                .dpopConfig(DpopConfig.builder().build())
                .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                .build();

        validator = new DpopProofValidator(issuerConfig, securityEventCounter, replayProtection);
    }

    @AfterEach
    void tearDown() {
        replayProtection.close();
    }

    // === Happy Path Tests ===

    @Test
    void shouldPassWhenNoDpopHeaderAndNotRequiredAndNoCnfJkt() {
        // Bearer mode: no DPoP header, not required, no cnf.jkt in access token
        DecodedJwt accessToken = createAccessTokenJwt(null); // no cnf.jkt
        AccessTokenRequest request = new AccessTokenRequest("dummy-token", Map.of());

        assertDoesNotThrow(() -> validator.validate(request, accessToken, "dummy-token"));
    }

    @Test
    void shouldPassWithValidDpopProof() {
        KeyPair keyPair = generateRsaKeyPair();
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

        String rawAccessToken = "some.access.token";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        String dpopProof = buildDpopProof(keyPair, jwkMap, "RS256", rawAccessToken);

        AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(dpopProof)));

        assertDoesNotThrow(() -> validator.validate(request, accessToken, rawAccessToken));
    }

    // === Rejection Tests ===

    @Test
    void shouldRejectWhenDpopRequiredButNoCnfJkt() {
        IssuerConfig requiredConfig = IssuerConfig.builder()
                .issuerIdentifier(TEST_ISSUER)
                .dpopConfig(DpopConfig.builder().required(true).build())
                .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                .build();
        var requiredValidator = new DpopProofValidator(requiredConfig, securityEventCounter, replayProtection);

        DecodedJwt accessToken = createAccessTokenJwt(null); // no cnf.jkt
        AccessTokenRequest request = new AccessTokenRequest("dummy-token", Map.of());

        var ex = assertThrows(TokenValidationException.class,
                () -> requiredValidator.validate(request, accessToken, "dummy-token"));
        assertEquals(EventType.DPOP_CNF_MISSING, ex.getEventType());
    }

    @Test
    void shouldRejectWhenCnfJktPresentButNoDpopHeader() {
        DecodedJwt accessToken = createAccessTokenJwt("some-thumbprint");
        AccessTokenRequest request = new AccessTokenRequest("dummy-token", Map.of());

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, "dummy-token"));
        assertEquals(EventType.DPOP_PROOF_MISSING, ex.getEventType());
    }

    @Test
    void shouldRejectWhenDpopHeaderPresentButNoCnfJkt() {
        DecodedJwt accessToken = createAccessTokenJwt(null);
        AccessTokenRequest request = new AccessTokenRequest("dummy-token",
                Map.of("dpop", List.of("some.dpop.proof")));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, "dummy-token"));
        assertEquals(EventType.DPOP_CNF_MISSING, ex.getEventType());
    }

    @Test
    void shouldRejectInvalidDpopProofFormat() {
        DecodedJwt accessToken = createAccessTokenJwt("some-thumbprint");
        AccessTokenRequest request = new AccessTokenRequest("dummy-token",
                Map.of("dpop", List.of("not-a-jwt")));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, "dummy-token"));
        assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
    }

    @Test
    void shouldRejectWrongTypHeader() {
        KeyPair keyPair = generateRsaKeyPair();
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

        String rawAccessToken = "some.access.token";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        // Build proof with wrong typ
        String dpopProof = buildDpopProofWithCustomTyp(keyPair, jwkMap, "RS256", rawAccessToken, "jwt");

        AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(dpopProof)));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, rawAccessToken));
        assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
    }

    @Test
    void shouldRejectReplayedJti() {
        KeyPair keyPair = generateRsaKeyPair();
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

        String rawAccessToken = "some.access.token";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        // Use the same jti twice
        String fixedJti = UUID.randomUUID().toString();
        String dpopProof1 = buildDpopProofWithJti(keyPair, jwkMap, "RS256", rawAccessToken, fixedJti);
        String dpopProof2 = buildDpopProofWithJti(keyPair, jwkMap, "RS256", rawAccessToken, fixedJti);

        AccessTokenRequest request1 = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(dpopProof1)));
        AccessTokenRequest request2 = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(dpopProof2)));

        // First should pass
        assertDoesNotThrow(() -> validator.validate(request1, accessToken, rawAccessToken));

        // Second should fail (replay)
        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request2, accessToken, rawAccessToken));
        assertEquals(EventType.DPOP_REPLAY_DETECTED, ex.getEventType());
    }

    @Test
    void shouldRejectExpiredIat() {
        KeyPair keyPair = generateRsaKeyPair();
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

        String rawAccessToken = "some.access.token";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        // Build proof with iat 10 minutes in the past (exceeds default 300s max age)
        long staleIat = (System.currentTimeMillis() / 1000) - 600;
        String dpopProof = buildDpopProofWithIat(keyPair, jwkMap, "RS256", rawAccessToken, staleIat);

        AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(dpopProof)));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, rawAccessToken));
        assertEquals(EventType.DPOP_PROOF_EXPIRED, ex.getEventType());
    }

    @Test
    void shouldRejectWrongAth() {
        KeyPair keyPair = generateRsaKeyPair();
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

        String rawAccessToken = "some.access.token";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        // Build proof with ath for a different token
        String dpopProof = buildDpopProofWithAth(keyPair, jwkMap, "RS256", "different.access.token");

        AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(dpopProof)));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, rawAccessToken));
        assertEquals(EventType.DPOP_ATH_MISMATCH, ex.getEventType());
    }

    @Test
    void shouldRejectThumbprintMismatch() {
        KeyPair keyPair = generateRsaKeyPair();
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());

        String rawAccessToken = "some.access.token";
        // Use a different thumbprint in the access token
        DecodedJwt accessToken = createAccessTokenJwt("wrong-thumbprint-value");

        String dpopProof = buildDpopProof(keyPair, jwkMap, "RS256", rawAccessToken);

        AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(dpopProof)));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, rawAccessToken));
        assertEquals(EventType.DPOP_THUMBPRINT_MISMATCH, ex.getEventType());
    }

    @Test
    void shouldRejectInvalidSignature() {
        KeyPair keyPairForHeader = generateRsaKeyPair();
        KeyPair keyPairForSigning = generateRsaKeyPair(); // Different key!
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPairForHeader.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

        String rawAccessToken = "some.access.token";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        // Build proof with header JWK from one key but signed with different key
        String dpopProof = buildDpopProofWithMismatchedKey(keyPairForSigning, jwkMap, "RS256", rawAccessToken);

        AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(dpopProof)));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, rawAccessToken));
        assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
    }

    @Test
    void shouldRejectMultipleDpopHeaders() {
        DecodedJwt accessToken = createAccessTokenJwt("some-thumbprint");
        AccessTokenRequest request = new AccessTokenRequest("dummy-token",
                Map.of("dpop", List.of("proof1", "proof2")));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, "dummy-token"));
        assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
        assertTrue(ex.getMessage().contains("Multiple DPoP headers"));
    }

    @Test
    void shouldRejectOversizedDpopProof() {
        DecodedJwt accessToken = createAccessTokenJwt("some-thumbprint");
        // Create a proof string larger than 8192 bytes
        String oversizedProof = "a".repeat(8193);
        AccessTokenRequest request = new AccessTokenRequest("dummy-token",
                Map.of("dpop", List.of(oversizedProof)));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, "dummy-token"));
        assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
        assertTrue(ex.getMessage().contains("maximum size"));
    }

    @Test
    void shouldRejectWhenDpopRequiredWithCnfJktButNoDpopHeader() {
        IssuerConfig requiredConfig = IssuerConfig.builder()
                .issuerIdentifier(TEST_ISSUER)
                .dpopConfig(DpopConfig.builder().required(true).build())
                .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                .build();
        var requiredValidator = new DpopProofValidator(requiredConfig, securityEventCounter, replayProtection);

        // Required mode, access token has cnf.jkt but no DPoP header
        DecodedJwt accessToken = createAccessTokenJwt("some-thumbprint");
        AccessTokenRequest request = new AccessTokenRequest("dummy-token", Map.of());

        var ex = assertThrows(TokenValidationException.class,
                () -> requiredValidator.validate(request, accessToken, "dummy-token"));
        assertEquals(EventType.DPOP_PROOF_MISSING, ex.getEventType());
    }

    @Test
    void shouldRejectFutureIat() {
        KeyPair keyPair = generateRsaKeyPair();
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

        String rawAccessToken = "some.access.token";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        // Build proof with iat 2 minutes in the future (exceeds -60s tolerance)
        long futureIat = (System.currentTimeMillis() / 1000) + 120;
        String dpopProof = buildDpopProofWithIat(keyPair, jwkMap, "RS256", rawAccessToken, futureIat);

        AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(dpopProof)));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, rawAccessToken));
        assertEquals(EventType.DPOP_PROOF_EXPIRED, ex.getEventType());
    }

    @Test
    void shouldRejectProofWithMissingJwk() {
        String thumbprint = "some-thumbprint";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        // Build a proof JWT manually without jwk in header
        String headerJson = """
                {"typ":"dpop+jwt","alg":"RS256"}""";
        String bodyJson = """
                {"jti":"test-jti","iat":%d,"ath":"test-ath"}""".formatted(System.currentTimeMillis() / 1000);
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String body = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bodyJson.getBytes(StandardCharsets.UTF_8));
        String proof = header + "." + body + ".dummy-sig";

        AccessTokenRequest request = new AccessTokenRequest("dummy-token",
                Map.of("dpop", List.of(proof)));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, "dummy-token"));
        assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
        assertTrue(ex.getMessage().contains("jwk"));
    }

    @Test
    void shouldRejectProofWithUnsupportedKeyType() {
        String thumbprint = "some-thumbprint";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        // Build a proof JWT with unsupported kty in jwk
        String headerJson = """
                {"typ":"dpop+jwt","alg":"RS256","jwk":{"kty":"oct","k":"secret"}}""";
        String bodyJson = """
                {"jti":"test-jti","iat":%d,"ath":"test-ath"}""".formatted(System.currentTimeMillis() / 1000);
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String body = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bodyJson.getBytes(StandardCharsets.UTF_8));
        String proof = header + "." + body + ".dummy-sig";

        AccessTokenRequest request = new AccessTokenRequest("dummy-token",
                Map.of("dpop", List.of(proof)));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, "dummy-token"));
        assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
        assertTrue(ex.getMessage().contains("Unsupported"));
    }

    @Test
    void shouldRejectProofWithMissingKty() {
        String thumbprint = "some-thumbprint";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        // Build a proof JWT with jwk that has no kty
        String headerJson = """
                {"typ":"dpop+jwt","alg":"RS256","jwk":{"n":"abc","e":"AQAB"}}""";
        String bodyJson = """
                {"jti":"test-jti","iat":%d,"ath":"test-ath"}""".formatted(System.currentTimeMillis() / 1000);
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String body = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bodyJson.getBytes(StandardCharsets.UTF_8));
        String proof = header + "." + body + ".dummy-sig";

        AccessTokenRequest request = new AccessTokenRequest("dummy-token",
                Map.of("dpop", List.of(proof)));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, "dummy-token"));
        assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
        assertTrue(ex.getMessage().contains("kty"));
    }

    @Test
    void shouldAcceptCaseInsensitiveTyp() {
        KeyPair keyPair = generateRsaKeyPair();
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

        String rawAccessToken = "some.access.token";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        // Use uppercase typ
        String dpopProof = buildDpopProofWithCustomTyp(keyPair, jwkMap, "RS256", rawAccessToken, "DPoP+JWT");

        AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(dpopProof)));

        assertDoesNotThrow(() -> validator.validate(request, accessToken, rawAccessToken));
    }

    // === Algorithm Variant Tests (Tier 1) ===

    @Nested
    @DisplayName("Algorithm Variant Tests")
    class AlgorithmVariantTests {

        @Test
        @DisplayName("Should pass with valid DPoP proof using ES256")
        void shouldPassWithValidDpopProofUsingES256() {
            PrivateKey ecPrivateKey = InMemoryKeyMaterialHandler.getDefaultPrivateKey(Algorithm.ES256);
            ECPublicKey ecPublicKey = (ECPublicKey) InMemoryKeyMaterialHandler.getDefaultPublicKey(Algorithm.ES256);
            Map<String, Object> jwkMap = ecPublicKeyToJwkMap(ecPublicKey, "P-256", 32);
            String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

            String rawAccessToken = "some.access.token";
            DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

            String dpopProof = buildDpopProofWithJjwt(ecPrivateKey, jwkMap, Jwts.SIG.ES256, rawAccessToken);

            AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)));

            assertDoesNotThrow(() -> validator.validate(request, accessToken, rawAccessToken));
        }

        @Test
        @DisplayName("Should pass with valid DPoP proof using EdDSA (Ed25519)")
        void shouldPassWithValidDpopProofUsingEdDSA() {
            PrivateKey edPrivateKey = InMemoryKeyMaterialHandler.getDefaultPrivateKey(Algorithm.ED_DSA);
            EdECPublicKey edPublicKey = (EdECPublicKey) InMemoryKeyMaterialHandler.getDefaultPublicKey(Algorithm.ED_DSA);
            Map<String, Object> jwkMap = edDsaPublicKeyToJwkMap(edPublicKey);
            String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

            String rawAccessToken = "some.access.token";
            DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

            String dpopProof = buildDpopProofWithJjwt(edPrivateKey, jwkMap, Jwts.SIG.EdDSA, rawAccessToken);

            AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)));

            assertDoesNotThrow(() -> validator.validate(request, accessToken, rawAccessToken));
        }

        @Test
        @DisplayName("Should reject DPoP proof with HMAC algorithm (HS256)")
        void shouldRejectHmacAlgorithm() {
            DecodedJwt accessToken = createAccessTokenJwt("some-thumbprint");

            // Build a proof with alg: HS256 — rejected before signature verification
            String headerJson = """
                    {"typ":"dpop+jwt","alg":"HS256","jwk":{"kty":"oct","k":"secret"}}""";
            String bodyJson = """
                    {"jti":"test-jti","iat":%d,"ath":"test-ath"}""".formatted(System.currentTimeMillis() / 1000);
            String header = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String body = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(bodyJson.getBytes(StandardCharsets.UTF_8));
            String proof = header + "." + body + ".dummy-sig";

            AccessTokenRequest request = new AccessTokenRequest("dummy-token",
                    Map.of("dpop", List.of(proof)));

            var ex = assertThrows(TokenValidationException.class,
                    () -> validator.validate(request, accessToken, "dummy-token"));
            assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
            assertTrue(ex.getMessage().contains("not supported"));
        }
    }

    // === Missing Required Claim Tests (Tier 1) ===

    @Nested
    @DisplayName("Missing Required Claim Tests")
    class MissingClaimTests {

        static Stream<Arguments> missingClaimProvider() {
            // Each entry: (includeJti, includeIat, includeAth, expectedClaimInMessage)
            return Stream.of(
                    Arguments.of(false, true, true, "jti"),
                    Arguments.of(true, false, true, "iat"),
                    Arguments.of(true, true, false, "ath")
            );
        }

        @ParameterizedTest(name = "Should reject DPoP proof with missing {3} claim")
        @MethodSource("missingClaimProvider")
        void shouldRejectProofWithMissingRequiredClaim(boolean includeJti, boolean includeIat,
                boolean includeAth, String expectedClaim) {
            KeyPair keyPair = generateRsaKeyPair();
            Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
            String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

            String rawAccessToken = "some.access.token";
            DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

            String dpopProof = buildDpopProofWithSelectiveClaims(keyPair, jwkMap, "RS256",
                    rawAccessToken, includeJti, includeIat, includeAth);

            AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)));

            var ex = assertThrows(TokenValidationException.class,
                    () -> validator.validate(request, accessToken, rawAccessToken));
            assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
            assertTrue(ex.getMessage().contains(expectedClaim));
        }
    }

    // === Helper Methods ===

    private DecodedJwt createAccessTokenJwt(String cnfJkt) {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("iss", TEST_ISSUER);
        bodyMap.put("sub", "test-subject");
        bodyMap.put("exp", (System.currentTimeMillis() / 1000) + 3600);
        bodyMap.put("iat", System.currentTimeMillis() / 1000);
        if (cnfJkt != null) {
            bodyMap.put("cnf", Map.of("jkt", cnfJkt));
        }

        var body = new MapRepresentation(bodyMap);
        var header = new JwtHeader(
                "RS256", null, "default-key-id", null, null, null, null, null, null, null, null, null, null, null, null, null);

        return new DecodedJwt(header, body, "dummy-sig", new String[]{"a", "b", "c"}, "a.b.c");
    }

    private String buildDpopProof(KeyPair keyPair, Map<String, Object> jwkMap, String alg, String accessToken) {
        return buildDpopProofInternal(keyPair, jwkMap, alg, "dpop+jwt",
                UUID.randomUUID().toString(), System.currentTimeMillis() / 1000,
                computeAth(accessToken));
    }

    private String buildDpopProofWithCustomTyp(KeyPair keyPair, Map<String, Object> jwkMap, String alg,
            String accessToken, String typ) {
        return buildDpopProofInternal(keyPair, jwkMap, alg, typ,
                UUID.randomUUID().toString(), System.currentTimeMillis() / 1000,
                computeAth(accessToken));
    }

    private String buildDpopProofWithJti(KeyPair keyPair, Map<String, Object> jwkMap, String alg,
            String accessToken, String jti) {
        return buildDpopProofInternal(keyPair, jwkMap, alg, "dpop+jwt",
                jti, System.currentTimeMillis() / 1000, computeAth(accessToken));
    }

    private String buildDpopProofWithIat(KeyPair keyPair, Map<String, Object> jwkMap, String alg,
            String accessToken, long iat) {
        return buildDpopProofInternal(keyPair, jwkMap, alg, "dpop+jwt",
                UUID.randomUUID().toString(), iat, computeAth(accessToken));
    }

    private String buildDpopProofWithAth(KeyPair keyPair, Map<String, Object> jwkMap, String alg,
            String accessTokenForAth) {
        return buildDpopProofInternal(keyPair, jwkMap, alg, "dpop+jwt",
                UUID.randomUUID().toString(), System.currentTimeMillis() / 1000,
                computeAth(accessTokenForAth));
    }

    private String buildDpopProofWithMismatchedKey(KeyPair signingKeyPair, Map<String, Object> headerJwkMap,
            String alg, String accessToken) {
        return buildDpopProofInternal(signingKeyPair, headerJwkMap, alg, "dpop+jwt",
                UUID.randomUUID().toString(), System.currentTimeMillis() / 1000,
                computeAth(accessToken));
    }

    private String buildDpopProofInternal(KeyPair signingKeyPair, Map<String, Object> headerJwkMap,
            String alg, String typ,
            String jti, long iat, String ath) {
        // Build header JSON
        String jwkJson = mapToJson(headerJwkMap);
        String headerJson = """
                {"typ":"%s","alg":"%s","jwk":%s}""".formatted(typ, alg, jwkJson);

        // Build body JSON
        String bodyJson = """
                {"jti":"%s","iat":%d,"ath":"%s"}""".formatted(jti, iat, ath);

        // Encode
        String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String encodedBody = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bodyJson.getBytes(StandardCharsets.UTF_8));

        String dataToSign = encodedHeader + "." + encodedBody;

        // Sign
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(signingKeyPair.getPrivate());
            signer.update(dataToSign.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signer.sign();

            String encodedSignature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(signatureBytes);

            return dataToSign + "." + encodedSignature;
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new IllegalStateException("Failed to sign DPoP proof", e);
        }
    }

    private String computeAth(String accessToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(accessToken.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String mapToJson(Map<String, Object> map) {
        var sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(entry.getKey()).append("\":\"").append(entry.getValue()).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }

    private Map<String, Object> rsaPublicKeyToJwkMap(RSAPublicKey publicKey) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kty", "RSA");
        map.put("n", Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsignedBytes(publicKey.getModulus())));
        map.put("e", Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsignedBytes(publicKey.getPublicExponent())));
        return map;
    }

    private byte[] toUnsignedBytes(BigInteger bigInteger) {
        byte[] bytes = bigInteger.toByteArray();
        if (bytes[0] == 0) {
            return Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }

    // --- EC / EdDSA helpers ---

    private Map<String, Object> ecPublicKeyToJwkMap(ECPublicKey publicKey, String curve, int coordSize) {
        ECPoint w = publicKey.getW();
        byte[] xBytes = normalizeCoordinate(w.getAffineX().toByteArray(), coordSize);
        byte[] yBytes = normalizeCoordinate(w.getAffineY().toByteArray(), coordSize);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kty", "EC");
        map.put("crv", curve);
        map.put("x", Base64.getUrlEncoder().withoutPadding().encodeToString(xBytes));
        map.put("y", Base64.getUrlEncoder().withoutPadding().encodeToString(yBytes));
        return map;
    }

    private Map<String, Object> edDsaPublicKeyToJwkMap(EdECPublicKey publicKey) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kty", "OKP");
        map.put("crv", publicKey.getParams().getName());
        map.put("x", InMemoryKeyMaterialHandler.encodeEdECPublicKey(publicKey));
        return map;
    }

    private byte[] normalizeCoordinate(byte[] coordinate, int expectedSize) {
        int startIndex = 0;
        while (startIndex < coordinate.length && coordinate[startIndex] == 0) {
            startIndex++;
        }
        byte[] result = new byte[expectedSize];
        int sourceLength = coordinate.length - startIndex;
        int destStart = Math.max(0, expectedSize - sourceLength);
        int copyLength = Math.min(sourceLength, expectedSize);
        System.arraycopy(coordinate, startIndex, result, destStart, copyLength);
        return result;
    }

    /**
     * Builds a DPoP proof JWT using jjwt for proper signature format handling (EC/EdDSA).
     */
    @SuppressWarnings("unchecked")
    private <A extends SignatureAlgorithm> String buildDpopProofWithJjwt(
            PrivateKey privateKey, Map<String, Object> jwkMap,
            A sigAlgorithm, String accessToken) {
        return Jwts.builder()
                .header().type("dpop+jwt").add("jwk", jwkMap).and()
                .claim("jti", UUID.randomUUID().toString())
                .claim("iat", System.currentTimeMillis() / 1000)
                .claim("ath", computeAth(accessToken))
                .signWith(privateKey, sigAlgorithm)
                .compact();
    }

    /**
     * Builds a DPoP proof with selective claims (for testing missing claim validation).
     * Uses RSA signing with valid signature so validation reaches the claim checks.
     */
    private String buildDpopProofWithSelectiveClaims(KeyPair signingKeyPair, Map<String, Object> headerJwkMap,
            String alg, String rawAccessToken, boolean includeJti, boolean includeIat, boolean includeAth) {
        // Build header JSON
        String jwkJson = mapToJson(headerJwkMap);
        String headerJson = """
                {"typ":"dpop+jwt","alg":"%s","jwk":%s}""".formatted(alg, jwkJson);

        // Build body with selective claims
        var parts = new ArrayList<String>();
        if (includeJti) {
            parts.add("\"jti\":\"%s\"".formatted(UUID.randomUUID().toString()));
        }
        if (includeIat) {
            parts.add("\"iat\":%d".formatted(System.currentTimeMillis() / 1000));
        }
        if (includeAth) {
            parts.add("\"ath\":\"%s\"".formatted(computeAth(rawAccessToken)));
        }
        String bodyJson = "{" + String.join(",", parts) + "}";

        // Encode
        String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String encodedBody = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bodyJson.getBytes(StandardCharsets.UTF_8));

        String dataToSign = encodedHeader + "." + encodedBody;

        // Sign with RSA
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(signingKeyPair.getPrivate());
            signer.update(dataToSign.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signer.sign();

            String encodedSignature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(signatureBytes);

            return dataToSign + "." + encodedSignature;
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new IllegalStateException("Failed to sign DPoP proof", e);
        }
    }
}
