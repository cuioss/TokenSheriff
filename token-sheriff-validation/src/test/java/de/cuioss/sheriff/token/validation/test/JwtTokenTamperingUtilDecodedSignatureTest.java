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
package de.cuioss.sheriff.token.validation.test;

import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests for {@link JwtTokenTamperingUtil} ensuring that
 * {@link JwtTokenTamperingUtil.TamperingStrategy#MODIFY_SIGNATURE_LAST_CHAR}
 * actually changes the decoded signature bytes, not just the base64url characters.
 * <p>
 * The cases cover the two inputs on which a character-level change alone would leave the tampered
 * signature decoding to the original bytes:
 * <ol>
 *   <li>The last base64url character of a 256-byte RS256 signature carries padding bits, so
 *       characters that differ only in those bits (e.g., 'A' and 'B') decode to the same last
 *       byte</li>
 *   <li>A middle character drawn from a 2-value generator ('X' or 'Y') can equal the character it
 *       replaces</li>
 * </ol>
 */
@EnableGeneratorController
@DisplayName("JwtTokenTamperingUtil MODIFY_SIGNATURE_LAST_CHAR changes the decoded signature")
class JwtTokenTamperingUtilDecodedSignatureTest {

    /**
     * Creates a minimal valid JWT string with a controlled signature.
     */
    private static String createTokenWithSignature(String signature) {
        // Base64url-encoded header: alg=RS256, typ=JWT
        String header = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9";
        // Base64url-encoded payload: sub=test, iss=test-issuer, exp=9999999999
        String payload = "eyJzdWIiOiJ0ZXN0IiwiaXNzIjoidGVzdC1pc3N1ZXIiLCJleHAiOjk5OTk5OTk5OTl9";
        return header + "." + payload + "." + signature;
    }

    /**
     * Creates a 342-char base64url signature (simulating RS256 / 256 bytes)
     * with the specified last character and middle character.
     */
    private static String createSignature(char lastChar, char middleChar) {
        char[] sig = new char[342];
        Arrays.fill(sig, 'a'); // fill with valid base64url
        sig[sig.length - 1] = lastChar;
        sig[sig.length / 2] = middleChar;
        return new String(sig);
    }

    /**
     * Tests all critical combinations of last/middle chars that trigger either cause.
     * <p>
     * The last character values 'A', 'B', 'Q', 'g', 'w' represent the four possible
     * significant-bit patterns for the trailing base64url character of a 256-byte signature.
     * Middle characters 'X' and 'Y' are the values from CHAR_GENERATOR_X_Y which could
     * cause generateDifferentChar to return the original character.
     */
    @ParameterizedTest(name = "last=''{0}'', middle=''{1}''")
    @CsvSource({
            "A, X",
            "A, Y",
            "B, X",
            "B, Y",
            "A, a",
            "B, a",
            "Q, X",
            "Q, Y",
            "g, X",
            "w, X"
    })
    @DisplayName("Tampered signature must decode to different bytes")
    void shouldChangeDecodedBytes(char lastChar, char middleChar) {
        String signature = createSignature(lastChar, middleChar);
        String token = createTokenWithSignature(signature);

        String tampered = JwtTokenTamperingUtil.applyTamperingStrategy(
                token, JwtTokenTamperingUtil.TamperingStrategy.MODIFY_SIGNATURE_LAST_CHAR);

        String originalSig = token.split("\\.")[2];
        String tamperedSig = tampered.split("\\.")[2];

        byte[] originalBytes = Base64.getUrlDecoder().decode(originalSig);
        byte[] tamperedBytes = Base64.getUrlDecoder().decode(tamperedSig);

        assertFalse(Arrays.equals(originalBytes, tamperedBytes),
                "Tampered signature must decode to different bytes. " +
                        "Last: '" + originalSig.charAt(originalSig.length() - 1) + "'->'" +
                        tamperedSig.charAt(tamperedSig.length() - 1) + "', " +
                        "Middle: '" + originalSig.charAt(originalSig.length() / 2) + "'->'" +
                        tamperedSig.charAt(tamperedSig.length() / 2) + "'");
    }

    @Test
    @DisplayName("Tampered real token signature must decode to different bytes")
    void shouldChangeDecodedBytesWithRealToken() {
        var tokenHolder = TestTokenGenerators.accessTokens().next();
        String token = tokenHolder.getRawToken();
        String originalSig = token.split("\\.")[2];

        String tampered = JwtTokenTamperingUtil.applyTamperingStrategy(
                token, JwtTokenTamperingUtil.TamperingStrategy.MODIFY_SIGNATURE_LAST_CHAR);
        String tamperedSig = tampered.split("\\.")[2];

        byte[] originalBytes = Base64.getUrlDecoder().decode(originalSig);
        byte[] tamperedBytes = Base64.getUrlDecoder().decode(tamperedSig);

        assertFalse(Arrays.equals(originalBytes, tamperedBytes),
                "Tampered signature must decode to different bytes");
    }
}
