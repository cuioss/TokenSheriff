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
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.pipeline.DecodedJwt;
import de.cuioss.sheriff.token.validation.util.JwkThumbprintUtil;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins that each {@link DpopProofValidator} rejection carrying remote text (the proof's {@code jti},
 * {@code htm} and {@code htu}, the request method and URI, and the token's {@code cnf.jkt}) carries only
 * the escaped form of a CR/LF or U+2028 payload, in the WARN line and in the thrown message alike
 * (ADR-0022, CWE-117).
 * <p>
 * A proof header value quoted in a {@code DPOP_PROOF_INVALID} rejection (the {@code typ}, the
 * {@code alg} and the JWK {@code kty}) is pinned the same way, because both are written from the one
 * message the validator rejects with.
 * <p>
 * Kept apart from {@code DpopProofValidatorTest}, which is already past the 500-line test guideline;
 * the shared fixture lives in {@link DpopProofValidatorSupport}.
 */
@EnableTestLogger
@DisplayName("DpopProofValidator log sanitisation")
class DpopProofValidatorLogSanitizationTest extends DpopProofValidatorSupport {

    private static final String RAW_ACCESS_TOKEN = "some.access.token";
    private static final String REQUEST_URI = "https://resource.example.org/protectedresource";
    /** U+2028, built from its code point so the source carries no invisible character. */
    private static final String LINE_SEPARATOR = String.valueOf((char) 0x2028);

    private final Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) FIXTURE_KEY_PAIR.getPublic());
    private final String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

    @Test
    @DisplayName("Replay WARN and thrown message escape a CR/LF in the proof jti")
    void shouldSanitiseJtiInReplayWarning() {
        // JSON escapes: the decoded jti carries a real CR LF
        String jsonJti = "replayed\\r\\nFORGED-REPLAY";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);
        validator.validate(request(proof(jsonJti, "GET", REQUEST_URI), REQUEST_URI, "GET"), accessToken, RAW_ACCESS_TOKEN);
        AccessTokenRequest replay = request(proof(jsonJti, "GET", REQUEST_URI), REQUEST_URI, "GET");

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(replay, accessToken, RAW_ACCESS_TOKEN));

        assertEquals(EventType.DPOP_REPLAY_DETECTED, ex.getEventType());
        assertEscapedInWarningAndMessage(ex, "replayed\\r\\nFORGED-REPLAY", "replayed\r\nFORGED-REPLAY");
    }

    @Test
    @DisplayName("htm-mismatch WARN and thrown message escape a CR/LF in htm and a U+2028 in the request method")
    void shouldSanitiseHtmAndRequestMethodInHtmMismatchWarning() {
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);
        String requestMethod = "POST" + LINE_SEPARATOR + "FORGED-METHOD";
        AccessTokenRequest htmMismatch = request(proof("htm-jti", "GET\\r\\nFORGED-HTM", REQUEST_URI), REQUEST_URI,
                requestMethod);

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(htmMismatch, accessToken, RAW_ACCESS_TOKEN));

        assertEquals(EventType.DPOP_HTM_MISMATCH, ex.getEventType());
        assertEscapedInWarningAndMessage(ex, "GET\\r\\nFORGED-HTM", "GET\r\nFORGED-HTM");
        assertEscapedInWarningAndMessage(ex, "POST\\u2028FORGED-METHOD", requestMethod);
    }

    @Test
    @DisplayName("htu-mismatch WARN and thrown message escape a CR/LF in htu and a U+2028 in the request URI")
    void shouldSanitiseHtuAndRequestUriInHtuMismatchWarning() {
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);
        String requestUri = "https://resource.example.org/other" + LINE_SEPARATOR + "FORGED-URI";
        AccessTokenRequest htuMismatch = request(
                proof("htu-jti", "GET", "https://resource.example.org/p\\nFORGED-HTU"), requestUri, "GET");

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(htuMismatch, accessToken, RAW_ACCESS_TOKEN));

        assertEquals(EventType.DPOP_HTU_MISMATCH, ex.getEventType());
        assertEscapedInWarningAndMessage(ex, "/p\\nFORGED-HTU", "/p\nFORGED-HTU");
        assertEscapedInWarningAndMessage(ex, "/other\\u2028FORGED-URI", requestUri);
    }

    @Test
    @DisplayName("Thumbprint-mismatch WARN and thrown message escape a CR/LF in the token cnf.jkt")
    void shouldSanitiseExpectedThumbprintInThumbprintMismatchWarning() {
        DecodedJwt accessToken = createAccessTokenJwt("wrong\r\nFORGED-JKT");
        AccessTokenRequest jktMismatch = request(proof("jkt-jti", "GET", REQUEST_URI), REQUEST_URI, "GET");

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(jktMismatch, accessToken, RAW_ACCESS_TOKEN));

        assertEquals(EventType.DPOP_THUMBPRINT_MISMATCH, ex.getEventType());
        assertEscapedInWarningAndMessage(ex, "wrong\\r\\nFORGED-JKT", "wrong\r\nFORGED-JKT");
    }

    @Test
    @DisplayName("Invalid-typ rejection escapes a CR/LF in the proof typ, in the WARN and the thrown message")
    void shouldSanitiseTypInInvalidProofRejection() {
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);
        // JSON escapes: the decoded typ carries a real CR LF
        String proof = buildDpopProofWithCustomTyp(FIXTURE_KEY_PAIR, jwkMap, "RS256", RAW_ACCESS_TOKEN,
                "dpop+jwt\\r\\nFORGED-TYP");
        AccessTokenRequest invalidTyp = request(proof, REQUEST_URI, "GET");

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(invalidTyp, accessToken, RAW_ACCESS_TOKEN));

        assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
        assertEscapedInWarningAndMessage(ex, "dpop+jwt\\r\\nFORGED-TYP", "dpop+jwt\r\nFORGED-TYP");
    }

    @Test
    @DisplayName("Unsupported-alg rejection escapes a CR/LF in the proof alg, in the WARN and the thrown message")
    void shouldSanitiseAlgInInvalidProofRejection() {
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);
        String proof = buildDpopProof(FIXTURE_KEY_PAIR, jwkMap, "RS256\\r\\nFORGED-ALG", RAW_ACCESS_TOKEN);
        AccessTokenRequest invalidAlg = request(proof, REQUEST_URI, "GET");

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(invalidAlg, accessToken, RAW_ACCESS_TOKEN));

        assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
        assertEscapedInWarningAndMessage(ex, "RS256\\r\\nFORGED-ALG", "RS256\r\nFORGED-ALG");
    }

    @Test
    @DisplayName("Unsupported-kty rejection escapes a CR/LF in the proof JWK kty, in the WARN and the thrown message")
    void shouldSanitiseKtyInInvalidProofRejection() {
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);
        Map<String, Object> forgedJwk = new LinkedHashMap<>(jwkMap);
        forgedJwk.put("kty", "XYZ\\r\\nFORGED-KTY");
        String proof = buildDpopProof(FIXTURE_KEY_PAIR, forgedJwk, "RS256", RAW_ACCESS_TOKEN);
        AccessTokenRequest invalidKty = request(proof, REQUEST_URI, "GET");

        var ex = assertThrows(TokenValidationException.class,
                () -> validator.validate(invalidKty, accessToken, RAW_ACCESS_TOKEN));

        assertEquals(EventType.DPOP_PROOF_INVALID, ex.getEventType());
        assertEscapedInWarningAndMessage(ex, "XYZ\\r\\nFORGED-KTY", "XYZ\r\nFORGED-KTY");
    }

    /**
     * Asserts the escaped form is present, and the raw form absent, in the WARN log and in the thrown
     * message, and that the thrown message carries no raw control or line-separator character.
     *
     * @param ex      the rejection thrown by the validator
     * @param escaped the remote value in its escaped form, as the sanitiser writes it
     * @param raw     the same value as decoded from the proof, carrying the raw control characters
     */
    private static void assertEscapedInWarningAndMessage(TokenValidationException ex, String escaped, String raw) {
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, escaped);
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, raw);
        assertTrue(ex.getMessage().contains(escaped), "thrown message must carry the escaped value: " + ex.getMessage());
        assertTrue(ex.getMessage().chars().noneMatch(c -> c < 0x20 || c == 0x2028 || c == 0x2029),
                "thrown message must carry no raw control or line-separator character: " + ex.getMessage());
    }

    /**
     * @param jsonJti the jti as it appears inside the JSON string literal (JSON escapes allowed)
     * @param jsonHtm the htm as it appears inside the JSON string literal
     * @param jsonHtu the htu as it appears inside the JSON string literal
     */
    private String proof(String jsonJti, String jsonHtm, String jsonHtu) {
        return buildDpopProofWithJtiAndHtuHtm(FIXTURE_KEY_PAIR, jwkMap, "RS256", RAW_ACCESS_TOKEN, jsonJti,
                jsonHtm, jsonHtu);
    }

    private static AccessTokenRequest request(String proof, String requestUri, String requestMethod) {
        return new AccessTokenRequest(RAW_ACCESS_TOKEN, Map.of("dpop", List.of(proof)), requestUri, requestMethod);
    }
}
