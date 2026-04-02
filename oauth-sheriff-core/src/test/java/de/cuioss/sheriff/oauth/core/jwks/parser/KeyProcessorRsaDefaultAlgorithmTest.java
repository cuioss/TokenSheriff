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
package de.cuioss.sheriff.oauth.core.jwks.parser;

import de.cuioss.sheriff.oauth.core.ParserConfig;
import de.cuioss.sheriff.oauth.core.json.Jwks;
import de.cuioss.sheriff.oauth.core.security.JwkAlgorithmPreferences;
import de.cuioss.sheriff.oauth.core.security.SecurityEventCounter;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests RSA key processing when the JWK does not specify an algorithm.
 * The KeyProcessor should default to RS256 and log a warning.
 */
@EnableTestLogger
@DisplayName("KeyProcessor RSA default algorithm")
class KeyProcessorRsaDefaultAlgorithmTest {

    // Valid RSA public key components (from test JWKS)
    private static final String TEST_KID = "test-rsa-no-alg";
    private static final String RSA_N = "nzyis1ZjfNB0bBgKFMSvvkTtwlvBsaJq7S5wA-kzeVOVpVWwkWdVha4s38XM_pa_yr47av7-z3VTmvDRyAHcaT92whREFpLv9cj5lTeJSibyr_Mrm_YtjCZVWgaOYIhwrXwKLqPr_11inWsAkfIytvHWTxZYEcXLgAXFuUuaS3uF9gEiNQwzGTU1v0FqkqTBr4B8nW3HCN47XUu0t8Y0e3zvAIhySnxIZi9aDaPvSlAeZ7VVl5ivy_43QvTRpM3eBFs9A1Y9a9aCtHSP8KXRTYhH2TvPxLOOFg0Lu-pwrps6CqvbeZjQlqCh9cGowQ";
    private static final String RSA_E = "AQAB";

    @Test
    @DisplayName("Should default to RS256 when RSA key has no algorithm specified")
    void shouldDefaultToRs256WhenNoAlgorithm() {
        var processor = new KeyProcessor(new SecurityEventCounter(), new JwkAlgorithmPreferences());

        // Create a JWKS JSON without "alg" for the RSA key
        String jwksJson = """
                {"keys":[{"kty":"RSA","use":"sig","kid":"%s","n":"%s","e":"%s"}]}"""
                .formatted(TEST_KID, RSA_N, RSA_E);

        // Parse via JwksParser to get JwkKey objects
        var dslJson = ParserConfig.builder().build().getDslJson();
        try {
            byte[] bytes = jwksJson.getBytes();
            var jwks = dslJson.deserialize(Jwks.class, bytes, bytes.length);

            var jwkKey = jwks.getKeys().orElseThrow().stream()
                    .filter(k -> TEST_KID.equals(k.getKid().orElse(null)))
                    .findFirst()
                    .orElseThrow();

            // Process — should succeed with RS256 default
            var result = processor.processKey(jwkKey);

            assertTrue(result.isPresent(), "RSA key without alg should be processed successfully");
            assertEquals("RS256", result.get().algorithm(), "Should default to RS256");
            assertEquals(TEST_KID, result.get().keyId());

            // Verify warning was logged
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "RS256");
        } catch (Exception e) {
            throw new AssertionError("Failed to parse test JWKS", e);
        }
    }
}
