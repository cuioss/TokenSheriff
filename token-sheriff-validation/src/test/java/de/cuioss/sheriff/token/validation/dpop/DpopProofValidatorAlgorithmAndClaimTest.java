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
package de.cuioss.sheriff.token.validation.dpop;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter.EventType;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.pipeline.DecodedJwt;
import de.cuioss.sheriff.token.validation.pipeline.SignatureTemplateManager;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler.Algorithm;
import de.cuioss.sheriff.token.validation.util.JwkThumbprintUtil;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DpopProofValidator} tests grouped by proof content: signature algorithm variants, missing
 * required claims, the htu/htm binding and the low/info security sweep.
 */
class DpopProofValidatorAlgorithmAndClaimTest extends DpopProofValidatorSupport {

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

            String dpopProof = buildDpopProofWithJjwt(ecPrivateKey, jwkMap, Jwts.SIG.ES256, rawAccessToken,
                    "GET", "https://resource.example.org/protectedresource");

            AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)),
                    "https://resource.example.org/protectedresource", "GET");

            assertDoesNotThrow(() -> validator.validate(request, accessToken, rawAccessToken));
        }

        @Test
        @DisplayName("Should reject an all-zero ES256 DPoP proof signature at the ECDSA range check (CVE-2022-21449)")
        void shouldRejectAllZeroEs256ProofSignature() {
            ECPublicKey ecPublicKey = (ECPublicKey) InMemoryKeyMaterialHandler.getDefaultPublicKey(Algorithm.ES256);
            Map<String, Object> jwkMap = ecPublicKeyToJwkMap(ecPublicKey, "P-256", 32);
            String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

            String rawAccessToken = "some.access.token";
            DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

            String headerJson = """
                    {"typ":"dpop+jwt","alg":"ES256","jwk":%s}""".formatted(mapToJson(jwkMap));
            String bodyJson = """
                    {"jti":"%s","iat":%d,"ath":"%s","htm":"GET","htu":"https://resource.example.org/protectedresource"}"""
                    .formatted(UUID.randomUUID().toString(), System.currentTimeMillis() / 1000, computeAth(rawAccessToken));
            String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String encodedBody = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(bodyJson.getBytes(StandardCharsets.UTF_8));
            String zeroSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[64]);
            String dpopProof = encodedHeader + "." + encodedBody + "." + zeroSignature;

            AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)),
                    "https://resource.example.org/protectedresource", "GET");

            var ex = assertThrows(TokenValidationException.class,
                    () -> validator.validate(request, accessToken, rawAccessToken));
            assertAll("All-zero ES256 proof must be rejected by the range check",
                    () -> assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType()),
                    () -> assertEquals(1, securityEventCounter.getCount(EventType.DPOP_PROOF_INVALID),
                            "DPOP_PROOF_INVALID must be counted exactly once"),
                    () -> assertTrue(ex.getMessage().contains("ECDSA signature component out of range"),
                            "Message must name the range check: " + ex.getMessage()));
        }

        static Stream<Arguments> mismatchedEcdsaAlgorithmAndCurve() {
            return Stream.of(
                    Arguments.of("ES256", "P-384"),
                    Arguments.of("ES256", "P-521"),
                    Arguments.of("ES384", "P-256"),
                    Arguments.of("ES384", "P-521"),
                    Arguments.of("ES512", "P-256"),
                    Arguments.of("ES512", "P-384"));
        }

        @ParameterizedTest(name = "Should reject a {0} DPoP proof whose JWK uses curve {1}")
        @MethodSource("mismatchedEcdsaAlgorithmAndCurve")
        void shouldRejectEcdsaProofWhoseAlgorithmDoesNotMatchJwkCurve(String alg, String curve) {
            String rawAccessToken = "some.access.token";
            String requestUri = "https://resource.example.org/protectedresource";
            KeyPair keyPair = newEcKeyPair(curve);
            Map<String, Object> jwkMap = ecPublicKeyToJwkMap((ECPublicKey) keyPair.getPublic(), curve,
                    ecCoordinateSize(curve));
            DecodedJwt accessToken = createAccessTokenJwt(JwkThumbprintUtil.computeThumbprint(jwkMap));
            String dpopProof = buildEcdsaDpopProof(keyPair, jwkMap, alg, rawAccessToken, requestUri);
            AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)), requestUri, "GET");

            var ex = assertThrows(TokenValidationException.class,
                    () -> validator.validate(request, accessToken, rawAccessToken));
            assertAll("A proof whose ES* alg disagrees with its JWK curve must be rejected by the curve binding",
                    () -> assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType()),
                    () -> assertTrue(ex.getMessage().contains(
                                    "DPoP proof algorithm '%s' does not match the proof JWK curve '%s'".formatted(alg, curve)),
                            "Message must name the alg/curve mismatch: " + ex.getMessage()),
                    () -> assertFalse(ex.getMessage().contains("ECDSA signature component out of range"),
                            "Message must not be the range-check message: " + ex.getMessage()));
        }

        @ParameterizedTest(name = "Should pass a {0} DPoP proof whose JWK uses the matching curve {1}")
        @CsvSource({"ES256, P-256", "ES384, P-384", "ES512, P-521"})
        void shouldPassEcdsaProofWhoseAlgorithmMatchesJwkCurve(String alg, String curve) {
            String rawAccessToken = "some.access.token";
            String requestUri = "https://resource.example.org/protectedresource";
            KeyPair keyPair = newEcKeyPair(curve);
            Map<String, Object> jwkMap = ecPublicKeyToJwkMap((ECPublicKey) keyPair.getPublic(), curve,
                    ecCoordinateSize(curve));
            DecodedJwt accessToken = createAccessTokenJwt(JwkThumbprintUtil.computeThumbprint(jwkMap));
            String dpopProof = buildEcdsaDpopProof(keyPair, jwkMap, alg, rawAccessToken, requestUri);
            AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)), requestUri, "GET");

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

            String dpopProof = buildDpopProofWithJjwt(edPrivateKey, jwkMap, Jwts.SIG.EdDSA, rawAccessToken,
                    "GET", "https://resource.example.org/protectedresource");

            AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)),
                    "https://resource.example.org/protectedresource", "GET");

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

            AccessTokenRequest request = AccessTokenRequest.of("dummy-token",
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
            KeyPair keyPair = FIXTURE_KEY_PAIR;
            Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
            String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

            String rawAccessToken = "some.access.token";
            DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

            String dpopProof = buildDpopProofWithSelectiveClaims(keyPair, jwkMap, "RS256",
                    rawAccessToken, includeJti, includeIat, includeAth);

            AccessTokenRequest request = AccessTokenRequest.of(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)));

            var ex = assertThrows(TokenValidationException.class,
                    () -> validator.validate(request, accessToken, rawAccessToken));
            assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
            assertTrue(ex.getMessage().contains(expectedClaim));
        }
    }

    @Nested
    @DisplayName("Low/info DPoP security sweep (L1, L3, L5)")
    class DpopSecuritySweepTests {

        @Test
        @DisplayName("L1: an operator-tightened maxTokenSize is applied to the DPoP proof")
        void shouldRejectDpopProofExceedingTightenedParserBounds() {
            ParserConfig tightConfig = ParserConfig.builder().maxTokenSize(200).build();
            IssuerConfig issuerConfig = IssuerConfig.builder()
                    .issuerIdentifier(TEST_ISSUER)
                    .dpopConfig(DpopConfig.builder().build())
                    .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                    .audienceValidationDisabled(true)
                    .build();
            var tightValidator = new DpopProofValidator(issuerConfig, securityEventCounter, replayProtection,
                    new SignatureTemplateManager(issuerConfig.getAlgorithmPreferences()), tightConfig,
                    tightConfig.getDslJson());

            KeyPair keyPair = FIXTURE_KEY_PAIR;
            Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
            String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);
            String rawAccessToken = "some.access.token";
            DecodedJwt accessToken = createAccessTokenJwt(thumbprint);
            String dpopProof = buildDpopProofWithHtuHtm(keyPair, jwkMap, "RS256", rawAccessToken,
                    "GET", "https://resource.example.org/protectedresource");
            AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)),
                    "https://resource.example.org/protectedresource", "GET");

            var ex = assertThrows(TokenValidationException.class,
                    () -> tightValidator.validate(request, accessToken, rawAccessToken));
            assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
            assertTrue(ex.getMessage().contains("exceeds maximum size"),
                    "A proof larger than the operator-tightened maxTokenSize must be rejected");
        }

        @Test
        @DisplayName("L5: an htu differing only by case, default port, and dot-segments normalizes equal")
        void shouldAcceptHtuDifferingOnlyByCasePortAndDotSegments() {
            KeyPair keyPair = FIXTURE_KEY_PAIR;
            Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
            String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);
            String rawAccessToken = "some.access.token";
            DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

            // Upper-case host, explicit default port, and a dot-segment — all must normalize away.
            String messyHtu = "https://RESOURCE.Example.ORG:443/a/../protectedresource";
            String cleanRequestUri = "https://resource.example.org/protectedresource";
            String dpopProof = buildDpopProofWithHtuHtm(keyPair, jwkMap, "RS256", rawAccessToken, "GET", messyHtu);
            AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)), cleanRequestUri, "GET");

            assertDoesNotThrow(() -> validator.validate(request, accessToken, rawAccessToken),
                    "An htu differing only by case, default port, and dot-segments must normalize equal");
        }

        @Test
        @DisplayName("L5: an htu with a genuinely different host is still rejected")
        void shouldRejectHtuWithDifferentHost() {
            KeyPair keyPair = FIXTURE_KEY_PAIR;
            Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
            String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);
            String rawAccessToken = "some.access.token";
            DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

            String dpopProof = buildDpopProofWithHtuHtm(keyPair, jwkMap, "RS256", rawAccessToken, "GET",
                    "https://attacker.example.org/protectedresource");
            AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)),
                    "https://resource.example.org/protectedresource", "GET");

            var ex = assertThrows(TokenValidationException.class,
                    () -> validator.validate(request, accessToken, rawAccessToken));
            assertEquals(EventType.DPOP_HTU_MISMATCH, ex.getEventType(),
                    "Normalization must not collapse genuinely different hosts");
        }

        @Test
        @DisplayName("L3: in-window jtis survive flood eviction and are still detected as replays")
        void shouldNotEvictInWindowJtisUnderFlood() {
            try (DpopReplayProtection floodProtection = new DpopReplayProtection(300, 2)) {
                assertTrue(floodProtection.checkAndStore("first-jti"), "A fresh jti is accepted");
                for (int i = 0; i < 20; i++) {
                    assertTrue(floodProtection.checkAndStore("flood-jti-" + i),
                            "Each flooding jti is fresh and accepted");
                }
                assertFalse(floodProtection.checkAndStore("first-jti"),
                        "An in-window jti must survive flood eviction and still be detected as a replay");
            }
        }
    }

    // === HTU/HTM Validation Tests ===

    @Nested
    @DisplayName("HTU/HTM Validation Tests")
    class HtuHtmValidationTests {

        @Test
        @DisplayName("Should reject when requestUri is null (htu/htm required for DPoP)")
        void shouldRejectWhenRequestUriIsNull() {
            KeyPair keyPair = FIXTURE_KEY_PAIR;
            Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
            String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

            String rawAccessToken = "some.access.token";
            DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

            // Proof without htm/htu claims — now rejected because request URI and method are required
            String dpopProof = buildDpopProof(keyPair, jwkMap, "RS256", rawAccessToken);

            AccessTokenRequest request = AccessTokenRequest.of(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)));

            var ex = assertThrows(TokenValidationException.class,
                    () -> validator.validate(request, accessToken, rawAccessToken));
            assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
            assertTrue(ex.getMessage().contains("request URI and method are required"));
        }

        @Test
        @DisplayName("Should reject when requestMethod is blank (htu/htm required for DPoP)")
        void shouldRejectWhenRequestMethodIsBlank() {
            KeyPair keyPair = FIXTURE_KEY_PAIR;
            Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
            String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

            String rawAccessToken = "some.access.token";
            DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

            String dpopProof = buildDpopProof(keyPair, jwkMap, "RS256", rawAccessToken);

            // requestUri present but requestMethod blank -> now rejected
            AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)), "https://api.example.com/resource", "");

            var ex = assertThrows(TokenValidationException.class,
                    () -> validator.validate(request, accessToken, rawAccessToken));
            assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
            assertTrue(ex.getMessage().contains("request URI and method are required"));
        }

        @Test
        @DisplayName("Should reject proof with missing htm claim when request has URI and method")
        void shouldRejectMissingHtmClaim() {
            KeyPair keyPair = FIXTURE_KEY_PAIR;
            Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
            String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

            String rawAccessToken = "some.access.token";
            DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

            // Proof without htm/htu — will fail because request carries URI and method
            String dpopProof = buildDpopProof(keyPair, jwkMap, "RS256", rawAccessToken);

            AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)),
                    "https://api.example.com/resource", "GET");

            var ex = assertThrows(TokenValidationException.class,
                    () -> validator.validate(request, accessToken, rawAccessToken));
            assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
            assertTrue(ex.getMessage().contains("htm"));
        }

        @Test
        @DisplayName("Should reject proof with mismatched htm (POST vs GET)")
        void shouldRejectMismatchedHtm() {
            KeyPair keyPair = FIXTURE_KEY_PAIR;
            Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
            String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

            String rawAccessToken = "some.access.token";
            DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

            // Proof says POST, request says GET
            String dpopProof = buildDpopProofWithHtuHtm(keyPair, jwkMap, "RS256", rawAccessToken,
                    "POST", "https://api.example.com/resource");

            AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)),
                    "https://api.example.com/resource", "GET");

            var ex = assertThrows(TokenValidationException.class,
                    () -> validator.validate(request, accessToken, rawAccessToken));
            assertEquals(EventType.DPOP_HTM_MISMATCH, ex.getEventType());
        }

        @Test
        @DisplayName("Should reject proof with mismatched htu")
        void shouldRejectMismatchedHtu() {
            KeyPair keyPair = FIXTURE_KEY_PAIR;
            Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
            String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

            String rawAccessToken = "some.access.token";
            DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

            // Proof says different URI than request
            String dpopProof = buildDpopProofWithHtuHtm(keyPair, jwkMap, "RS256", rawAccessToken,
                    "GET", "https://api.example.com/other");

            AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)),
                    "https://api.example.com/resource", "GET");

            var ex = assertThrows(TokenValidationException.class,
                    () -> validator.validate(request, accessToken, rawAccessToken));
            assertEquals(EventType.DPOP_HTU_MISMATCH, ex.getEventType());
        }

        @Test
        @DisplayName("Should accept proof with matching htm and htu")
        void shouldAcceptMatchingHtmAndHtu() {
            KeyPair keyPair = FIXTURE_KEY_PAIR;
            Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
            String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

            String rawAccessToken = "some.access.token";
            DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

            String dpopProof = buildDpopProofWithHtuHtm(keyPair, jwkMap, "RS256", rawAccessToken,
                    "GET", "https://api.example.com/resource");

            AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)),
                    "https://api.example.com/resource", "GET");

            assertDoesNotThrow(() -> validator.validate(request, accessToken, rawAccessToken));
        }

        @Test
        @DisplayName("Should strip query string from htu for comparison")
        void shouldStripQueryStringFromHtu() {
            KeyPair keyPair = FIXTURE_KEY_PAIR;
            Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
            String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

            String rawAccessToken = "some.access.token";
            DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

            // Proof htu has no query, request URI has query — should match after stripping
            String dpopProof = buildDpopProofWithHtuHtm(keyPair, jwkMap, "RS256", rawAccessToken,
                    "GET", "https://api.example.com/resource");

            AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                    Map.of("dpop", List.of(dpopProof)),
                    "https://api.example.com/resource?page=1&size=10", "GET");

            assertDoesNotThrow(() -> validator.validate(request, accessToken, rawAccessToken));
        }
    }
}
