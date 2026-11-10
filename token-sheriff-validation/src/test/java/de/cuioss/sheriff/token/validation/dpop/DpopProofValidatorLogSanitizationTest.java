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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins that each {@link DpopProofValidator} WARN carrying remote text (the proof's {@code jti},
 * {@code htm} and {@code htu}, the request method and URI, and the token's {@code cnf.jkt}) logs only
 * the escaped form of a CR/LF or U+2028 payload (ADR-0022, CWE-117).
 * <p>
 * Kept apart from {@code DpopProofValidatorTest}, which is already past the 500-line test guideline;
 * the shared fixture lives in {@link DpopProofValidatorSupport}.
 */
@EnableTestLogger
@DisplayName("DpopProofValidator log sanitisation")
class DpopProofValidatorLogSanitizationTest extends DpopProofValidatorSupport {

    private static final String RAW_ACCESS_TOKEN = "some.access.token";
    private static final String REQUEST_URI = "https://resource.example.org/protectedresource";

    private final Map<String, Object> jwkMap = rsaPublicKeyToJwkMap((RSAPublicKey) FIXTURE_KEY_PAIR.getPublic());
    private final String thumbprint = JwkThumbprintUtil.computeThumbprint(jwkMap);

    @Test
    @DisplayName("Replay WARN escapes a CR/LF in the proof jti")
    void shouldSanitiseJtiInReplayWarning() {
        // JSON escapes: the decoded jti carries a real CR LF
        String jsonJti = "replayed\\r\\nFORGED-REPLAY";
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);
        validator.validate(request(proof(jsonJti, "GET", REQUEST_URI), REQUEST_URI, "GET"), accessToken, RAW_ACCESS_TOKEN);

        var ex = assertThrows(TokenValidationException.class, () -> validator.validate(
                request(proof(jsonJti, "GET", REQUEST_URI), REQUEST_URI, "GET"), accessToken, RAW_ACCESS_TOKEN));

        assertEquals(EventType.DPOP_REPLAY_DETECTED, ex.getEventType());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "replayed\\r\\nFORGED-REPLAY");
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, "replayed\r\nFORGED-REPLAY");
    }

    @Test
    @DisplayName("htm-mismatch WARN escapes a CR/LF in htm and a U+2028 in the request method")
    void shouldSanitiseHtmAndRequestMethodInHtmMismatchWarning() {
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);
        String requestMethod = "POST FORGED-METHOD";

        var ex = assertThrows(TokenValidationException.class, () -> validator.validate(
                request(proof("htm-jti", "GET\\r\\nFORGED-HTM", REQUEST_URI), REQUEST_URI, requestMethod),
                accessToken, RAW_ACCESS_TOKEN));

        assertEquals(EventType.DPOP_HTM_MISMATCH, ex.getEventType());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "GET\\r\\nFORGED-HTM");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "POST\\u2028FORGED-METHOD");
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, "GET\r\nFORGED-HTM");
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, requestMethod);
    }

    @Test
    @DisplayName("htu-mismatch WARN escapes a CR/LF in htu and a U+2028 in the request URI")
    void shouldSanitiseHtuAndRequestUriInHtuMismatchWarning() {
        DecodedJwt accessToken = createAccessTokenJwt(thumbprint);
        String requestUri = "https://resource.example.org/other FORGED-URI";

        var ex = assertThrows(TokenValidationException.class, () -> validator.validate(
                request(proof("htu-jti", "GET", "https://resource.example.org/p\\nFORGED-HTU"), requestUri, "GET"),
                accessToken, RAW_ACCESS_TOKEN));

        assertEquals(EventType.DPOP_HTU_MISMATCH, ex.getEventType());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "/p\\nFORGED-HTU");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "/other\\u2028FORGED-URI");
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, "/p\nFORGED-HTU");
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, requestUri);
    }

    @Test
    @DisplayName("Thumbprint-mismatch WARN escapes a CR/LF in the token cnf.jkt")
    void shouldSanitiseExpectedThumbprintInThumbprintMismatchWarning() {
        DecodedJwt accessToken = createAccessTokenJwt("wrong\r\nFORGED-JKT");

        var ex = assertThrows(TokenValidationException.class, () -> validator.validate(
                request(proof("jkt-jti", "GET", REQUEST_URI), REQUEST_URI, "GET"), accessToken, RAW_ACCESS_TOKEN));

        assertEquals(EventType.DPOP_THUMBPRINT_MISMATCH, ex.getEventType());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "wrong\\r\\nFORGED-JKT");
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, "wrong\r\nFORGED-JKT");
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
