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

import com.dslplatform.json.DslJson;
import de.cuioss.sheriff.token.commons.events.SecurityEventCounter.EventType;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.pipeline.DecodedJwt;
import de.cuioss.sheriff.token.validation.pipeline.SignatureTemplateManager;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.util.JwkThumbprintUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DpopProofValidatorTest extends DpopProofValidatorSupport {

    // === Happy Path Tests ===

    @Test
    void shouldPassWhenNoDpopHeaderAndNotRequiredAndNoCnfJkt() {
        // Bearer mode: no DPoP header, not required, no cnf.jkt in access token
        DecodedJwt accessToken = createAccessTokenJwt(null); // no cnf.jkt
        AccessTokenRequest request = AccessTokenRequest.of("dummy-token");

        assertDoesNotThrow(() -> validator.validate(request, accessToken, "dummy-token"));
    }

    @Test
    void shouldPassWithValidDpopProof() {
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

        assertDoesNotThrow(() -> validator.validate(request, accessToken, rawAccessToken));
    }

    // === Rejection Tests ===

    @Test
    void shouldRejectWhenDpopRequiredButNoCnfJkt() {
        IssuerConfig requiredConfig = IssuerConfig.builder()
                .issuerIdentifier(TEST_ISSUER)
                .dpopConfig(DpopConfig.builder().required(true).build())
                .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                .audienceValidationDisabled(true)
                .build();
        var requiredValidator = new DpopProofValidator(requiredConfig, securityEventCounter, replayProtection, new SignatureTemplateManager(requiredConfig.getAlgorithmPreferences()), ParserConfig.builder().build(), new DslJson<>(new DslJson.Settings<>()));

        DecodedJwt accessToken = createAccessTokenJwt(null); // no cnf.jkt
        AccessTokenRequest request = AccessTokenRequest.of("dummy-token");

        var ex = assertThrows(TokenValidationException.class,
                () -> requiredValidator.validate(request, accessToken, "dummy-token"));
        assertEquals(EventType.DPOP_CNF_MISSING, ex.getEventType());
    }

    @Test
    void shouldRejectWhenCnfJktPresentButNoDpopHeader() {
        DecodedJwt accessToken = createAccessTokenJwt("some-thumbprint");
        AccessTokenRequest request = AccessTokenRequest.of("dummy-token");

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, "dummy-token"));
        assertEquals(EventType.DPOP_PROOF_MISSING, ex.getEventType());
    }

    @Test
    void shouldRejectWhenDpopHeaderPresentButNoCnfJkt() {
        DecodedJwt accessToken = createAccessTokenJwt(null);
        AccessTokenRequest request = AccessTokenRequest.of("dummy-token",
                Map.of("dpop", List.of("some.dpop.proof")));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, "dummy-token"));
        assertEquals(EventType.DPOP_CNF_MISSING, ex.getEventType());
    }

    @Test
    void shouldRejectInvalidDpopProofFormat() {
        DecodedJwt accessToken = createAccessTokenJwt("some-thumbprint");
        AccessTokenRequest request = AccessTokenRequest.of("dummy-token",
                Map.of("dpop", List.of("not-a-jwt")));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, "dummy-token"));
        assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
    }

    @Test
    @DisplayName("Should reject an otherwise valid proof with a trailing dot (V-17b)")
    void shouldRejectProofWithTrailingDot() {
        KeyPair keyPair = FIXTURE_KEY_PAIR;
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);
        String rawAccessToken = "some.access.token";
        String requestUri = "https://resource.example.org/protectedresource";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        String dpopProof = buildDpopProofWithHtuHtm(keyPair, jwkMap, "RS256", rawAccessToken, "GET", requestUri) + ".";
        AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(dpopProof)), requestUri, "GET");

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, rawAccessToken));
        assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
        assertTrue(ex.getMessage().contains("must have 3 parts"), ex.getMessage());
    }

    @ParameterizedTest(name = "should reject truncated escape in {0}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "header | `{\"typ\":\"dpop+jwt\\`",
            "header | `{\"typ\":\"dpop+jwt\",\"alg\":\"\\u12`",
            "payload | `{\"jti\":\"abc\\`",
            "payload | `{\"jti\":\"\\u12`"
    })
    @DisplayName("Should reject a proof whose header or payload JSON ends inside a string escape as DPOP_PROOF_INVALID")
    void shouldRejectProofWithTruncatedEscape(String segment, String truncatedJson) {
        DecodedJwt accessToken = createAccessTokenJwt("some-thumbprint");
        String validHeader = "{\"typ\":\"dpop+jwt\",\"alg\":\"RS256\"}";
        String validBody = "{\"jti\":\"test-jti\"}";
        boolean truncateHeader = "header".equals(segment);
        String proof = base64Url(truncateHeader ? truncatedJson : validHeader) + "."
                + base64Url(truncateHeader ? validBody : truncatedJson) + ".dummy-sig";
        AccessTokenRequest request = AccessTokenRequest.of("dummy-token", Map.of("dpop", List.of(proof)));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, "dummy-token"),
                "A truncated escape in the DPoP proof must be a declared rejection, not an unchecked exception");

        assertAll(
                () -> assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType()),
                () -> assertTrue(ex.getMessage().contains("Failed to decode DPoP proof JWT"), ex.getMessage()),
                () -> assertEquals(1, securityEventCounter.getCount(EventType.DPOP_PROOF_INVALID),
                        "DPOP_PROOF_INVALID must be counted exactly once"));
    }

    @Test
    void shouldRejectWrongTypHeader() {
        KeyPair keyPair = FIXTURE_KEY_PAIR;
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

        String rawAccessToken = "some.access.token";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        // Build proof with wrong typ
        String dpopProof = buildDpopProofWithCustomTyp(keyPair, jwkMap, "RS256", rawAccessToken, "jwt");

        AccessTokenRequest request = AccessTokenRequest.of(rawAccessToken,
                Map.of("dpop", List.of(dpopProof)));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, rawAccessToken));
        assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
    }

    @Test
    void shouldRejectReplayedJti() {
        KeyPair keyPair = FIXTURE_KEY_PAIR;
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

        String rawAccessToken = "some.access.token";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        // Use the same jti twice — need htm/htu in proofs since validateHtuHtm now rejects missing values
        String fixedJti = UUID.randomUUID().toString();
        String dpopProof1 = buildDpopProofWithJtiAndHtuHtm(keyPair, jwkMap, "RS256", rawAccessToken, fixedJti,
                "GET", "https://resource.example.org/protectedresource");
        String dpopProof2 = buildDpopProofWithJtiAndHtuHtm(keyPair, jwkMap, "RS256", rawAccessToken, fixedJti,
                "GET", "https://resource.example.org/protectedresource");

        AccessTokenRequest request1 = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(dpopProof1)),
                "https://resource.example.org/protectedresource", "GET");
        AccessTokenRequest request2 = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(dpopProof2)),
                "https://resource.example.org/protectedresource", "GET");

        // First should pass
        assertDoesNotThrow(() -> validator.validate(request1, accessToken, rawAccessToken));

        // Second should fail (replay)
        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request2, accessToken, rawAccessToken));
        assertEquals(EventType.DPOP_REPLAY_DETECTED, ex.getEventType());
    }

    @Test
    @DisplayName("A thumbprint-mismatch proof leaves no replay entry: a bound proof with the same jti is accepted (V-12)")
    void shouldNotStoreJtiOfThumbprintMismatchProof() {
        KeyPair keyPair = FIXTURE_KEY_PAIR;
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);
        String rawAccessToken = "some.access.token";
        String requestUri = "https://resource.example.org/protectedresource";
        String sharedJti = UUID.randomUUID().toString();

        DecodedJwt mismatchedToken = createAccessTokenJwt("wrong-thumbprint-value");
        String rejectedProof = buildDpopProofWithJtiAndHtuHtm(keyPair, jwkMap, "RS256", rawAccessToken, sharedJti,
                "GET", requestUri);
        AccessTokenRequest rejectedRequest = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(rejectedProof)), requestUri, "GET");

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(rejectedRequest, mismatchedToken, rawAccessToken));
        assertEquals(EventType.DPOP_THUMBPRINT_MISMATCH, ex.getEventType());

        DecodedJwt boundToken = createAccessTokenJwt(thumbprint);
        String validProof = buildDpopProofWithJtiAndHtuHtm(keyPair, jwkMap, "RS256", rawAccessToken, sharedJti,
                "GET", requestUri);
        AccessTokenRequest validRequest = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(validProof)), requestUri, "GET");

        assertDoesNotThrow(() -> validator.validate(validRequest, boundToken, rawAccessToken),
                "The rejected proof must not have consumed the jti of the legitimate proof");
        assertEquals(0, securityEventCounter.getCount(EventType.DPOP_REPLAY_DETECTED),
                "No replay must be detected");
    }

    @Test
    @DisplayName("An ath-mismatch proof leaves no replay entry: a valid proof with the same jti is accepted (V-12)")
    void shouldNotStoreJtiOfAthMismatchProof() {
        KeyPair keyPair = FIXTURE_KEY_PAIR;
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);
        String rawAccessToken = "some.access.token";
        String requestUri = "https://resource.example.org/protectedresource";
        String sharedJti = UUID.randomUUID().toString();
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        String rejectedProof = buildDpopProofInternalWithHtuHtm(keyPair, jwkMap, "RS256", "dpop+jwt", sharedJti,
                System.currentTimeMillis() / 1000, computeAth("different.access.token"), "GET", requestUri);
        AccessTokenRequest rejectedRequest = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(rejectedProof)), requestUri, "GET");

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(rejectedRequest, accessToken, rawAccessToken));
        assertEquals(EventType.DPOP_ATH_MISMATCH, ex.getEventType());

        String validProof = buildDpopProofWithJtiAndHtuHtm(keyPair, jwkMap, "RS256", rawAccessToken, sharedJti,
                "GET", requestUri);
        AccessTokenRequest validRequest = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(validProof)), requestUri, "GET");

        assertDoesNotThrow(() -> validator.validate(validRequest, accessToken, rawAccessToken),
                "The rejected proof must not have consumed the jti of the legitimate proof");
        assertEquals(0, securityEventCounter.getCount(EventType.DPOP_REPLAY_DETECTED),
                "No replay must be detected");
    }

    @Test
    @DisplayName("DpopConfig build should enforce the replay-window invariant ttl >= proofMaxAge + clockSkew (M3)")
    void shouldEnforceReplayWindowInvariantOnBuild() {
        // Under defaults the effective replay TTL must cover the full proof freshness window
        // (proofMaxAge + clock skew), so a jti cannot expire while its proof is still fresh.
        DpopConfig defaults = DpopConfig.builder().build();
        assertTrue(
                defaults.getNonceCacheTtlSeconds() >= defaults.getProofMaxAgeSeconds() + DpopConfig.DEFAULT_CLOCK_SKEW_SECONDS,
                "Default replay TTL must cover proofMaxAge + clock skew");

        // A configured TTL smaller than the freshness window is widened up to it, closing the window.
        DpopConfig widened = DpopConfig.builder()
                .proofMaxAgeSeconds(600)
                .nonceCacheTtlSeconds(120)
                .build();
        assertEquals(600 + DpopConfig.DEFAULT_CLOCK_SKEW_SECONDS, widened.getNonceCacheTtlSeconds(),
                "A TTL shorter than proofMaxAge + clock skew must be widened to the freshness window");

        // A configured TTL that already covers the freshness window is preserved unchanged.
        DpopConfig preserved = DpopConfig.builder()
                .proofMaxAgeSeconds(300)
                .nonceCacheTtlSeconds(1000)
                .build();
        assertEquals(1000, preserved.getNonceCacheTtlSeconds(),
                "A TTL that already covers the freshness window must be preserved");
    }

    @ParameterizedTest(name = "Should honor a DPoP proof supplied under the \"{0}\" header (M4)")
    @ValueSource(strings = {"DPoP", "DPOP", "dpop", "Dpop"})
    @DisplayName("Should honor a DPoP proof regardless of header-name casing (M4)")
    void shouldHonorDpopHeaderRegardlessOfCasing(String headerName) {
        KeyPair keyPair = FIXTURE_KEY_PAIR;
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

        String rawAccessToken = "some.access.token";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        String dpopProof = buildDpopProofWithHtuHtm(keyPair, jwkMap, "RS256", rawAccessToken,
                "GET", "https://resource.example.org/protectedresource");

        // Supply the proof under a non-canonical header casing; case-insensitive lookup must still
        // resolve it so DPoP is enforced rather than being silently downgraded to bearer mode (M4).
        AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                Map.of(headerName, List.of(dpopProof)),
                "https://resource.example.org/protectedresource", "GET");

        assertDoesNotThrow(() -> validator.validate(request, accessToken, rawAccessToken),
                "A DPoP proof under the '" + headerName + "' header must be honored, not downgraded to bearer mode");
    }

    @Test
    void shouldRejectExpiredIat() {
        KeyPair keyPair = FIXTURE_KEY_PAIR;
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

        String rawAccessToken = "some.access.token";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        // Build proof with iat 10 minutes in the past (exceeds default 300s max age)
        long staleIat = (System.currentTimeMillis() / 1000) - 600;
        String dpopProof = buildDpopProofWithIat(keyPair, jwkMap, "RS256", rawAccessToken, staleIat);

        AccessTokenRequest request = AccessTokenRequest.of(rawAccessToken,
                Map.of("dpop", List.of(dpopProof)));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, rawAccessToken));
        assertEquals(EventType.DPOP_PROOF_EXPIRED, ex.getEventType());
    }

    @Test
    void shouldRejectWrongAth() {
        KeyPair keyPair = FIXTURE_KEY_PAIR;
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

        String rawAccessToken = "some.access.token";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        // Build proof with ath for a different token
        String dpopProof = buildDpopProofWithAth(keyPair, jwkMap, "RS256", "different.access.token");

        AccessTokenRequest request = AccessTokenRequest.of(rawAccessToken,
                Map.of("dpop", List.of(dpopProof)));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, rawAccessToken));
        assertEquals(EventType.DPOP_ATH_MISMATCH, ex.getEventType());
    }

    @Test
    void shouldRejectThumbprintMismatch() {
        KeyPair keyPair = FIXTURE_KEY_PAIR;
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());

        String rawAccessToken = "some.access.token";
        // Use a different thumbprint in the access token
        DecodedJwt accessToken = createAccessTokenJwt("wrong-thumbprint-value");

        String dpopProof = buildDpopProofWithHtuHtm(keyPair, jwkMap, "RS256", rawAccessToken,
                "GET", "https://resource.example.org/protectedresource");

        AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(dpopProof)),
                "https://resource.example.org/protectedresource", "GET");

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, rawAccessToken));
        assertEquals(EventType.DPOP_THUMBPRINT_MISMATCH, ex.getEventType());
    }

    @Test
    void shouldRejectInvalidSignature() {
        KeyPair keyPairForHeader = FIXTURE_KEY_PAIR;
        KeyPair keyPairForSigning = newRsaKeyPair(); // Different key: the wrong key is the subject here
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPairForHeader.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

        String rawAccessToken = "some.access.token";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        // Build proof with header JWK from one key but signed with different key
        String dpopProof = buildDpopProofWithMismatchedKey(keyPairForSigning, jwkMap, "RS256", rawAccessToken);

        AccessTokenRequest request = AccessTokenRequest.of(rawAccessToken,
                Map.of("dpop", List.of(dpopProof)));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, rawAccessToken));
        assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
    }

    @Test
    void shouldRejectMultipleDpopHeaders() {
        DecodedJwt accessToken = createAccessTokenJwt("some-thumbprint");
        AccessTokenRequest request = AccessTokenRequest.of("dummy-token",
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
        AccessTokenRequest request = AccessTokenRequest.of("dummy-token",
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
                .audienceValidationDisabled(true)
                .build();
        var requiredValidator = new DpopProofValidator(requiredConfig, securityEventCounter, replayProtection, new SignatureTemplateManager(requiredConfig.getAlgorithmPreferences()), ParserConfig.builder().build(), new DslJson<>(new DslJson.Settings<>()));

        // Required mode, access token has cnf.jkt but no DPoP header
        DecodedJwt accessToken = createAccessTokenJwt("some-thumbprint");
        AccessTokenRequest request = AccessTokenRequest.of("dummy-token");

        var ex = assertThrows(TokenValidationException.class,
                () -> requiredValidator.validate(request, accessToken, "dummy-token"));
        assertEquals(EventType.DPOP_PROOF_MISSING, ex.getEventType());
    }

    @Test
    void shouldRejectFutureIat() {
        KeyPair keyPair = FIXTURE_KEY_PAIR;
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

        String rawAccessToken = "some.access.token";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        // Build proof with iat 2 minutes in the future (exceeds -60s tolerance)
        long futureIat = (System.currentTimeMillis() / 1000) + 120;
        String dpopProof = buildDpopProofWithIat(keyPair, jwkMap, "RS256", rawAccessToken, futureIat);

        AccessTokenRequest request = AccessTokenRequest.of(rawAccessToken,
                Map.of("dpop", List.of(dpopProof)));

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(request, accessToken, rawAccessToken));
        assertEquals(EventType.DPOP_PROOF_EXPIRED, ex.getEventType());
    }

    static Stream<Arguments> invalidJwkHeaderProvider() {
        return Stream.of(
                Arguments.of("{\"typ\":\"dpop+jwt\",\"alg\":\"RS256\"}", "jwk", "missing jwk"),
                Arguments.of("{\"typ\":\"dpop+jwt\",\"alg\":\"RS256\",\"jwk\":{\"kty\":\"oct\",\"k\":\"secret\"}}", "Unsupported", "unsupported kty"),
                Arguments.of("{\"typ\":\"dpop+jwt\",\"alg\":\"RS256\",\"jwk\":{\"n\":\"abc\",\"e\":\"AQAB\"}}", "kty", "missing kty"));
    }

    @ParameterizedTest(name = "should reject proof with {2}")
    @MethodSource("invalidJwkHeaderProvider")
    void shouldRejectProofWithInvalidJwkHeader(String headerJson, String expectedMessageFragment, String description) {
        String thumbprint = "some-thumbprint";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

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
        assertTrue(ex.getMessage().contains(expectedMessageFragment));
    }

    @Test
    void shouldAcceptCaseInsensitiveTyp() {
        KeyPair keyPair = FIXTURE_KEY_PAIR;
        Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) keyPair.getPublic());
        String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

        String rawAccessToken = "some.access.token";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);

        // Use uppercase typ with htm/htu
        String dpopProof = buildDpopProofWithCustomTypAndHtuHtm(keyPair, jwkMap, "RS256", rawAccessToken, "DPoP+JWT",
                "GET", "https://resource.example.org/protectedresource");

        AccessTokenRequest request = new AccessTokenRequest(rawAccessToken,
                Map.of("dpop", List.of(dpopProof)),
                "https://resource.example.org/protectedresource", "GET");

        assertDoesNotThrow(() -> validator.validate(request, accessToken, rawAccessToken));
    }
}
