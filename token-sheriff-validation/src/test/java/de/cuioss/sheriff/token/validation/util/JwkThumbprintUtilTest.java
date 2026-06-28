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
package de.cuioss.sheriff.token.validation.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwkThumbprintUtilTest {

    /**
     * Test vector from RFC 7638 Section 3.1.
     * The example RSA key produces a known thumbprint.
     */
    @Test
    void shouldComputeRfc7638TestVector() {
        // RFC 7638 Section 3.1 test vector
        Map<String, Object> rsaJwk = Map.of(
                "kty", "RSA",
                "n", "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
                "e", "AQAB"
        );

        String thumbprint = JwkThumbprintUtil.computeThumbprint(rsaJwk);

        // Expected thumbprint from RFC 7638 Section 3.1
        assertEquals("NzbLsXh8uDCcd-6MNwXF4W_7noWXFZAfHkxZsRGC9Xs", thumbprint);
    }

    @Test
    void shouldComputeThumbprintForEcKey() {
        Map<String, Object> ecJwk = Map.of(
                "kty", "EC",
                "crv", "P-256",
                "x", "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU",
                "y", "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0"
        );

        String thumbprint = JwkThumbprintUtil.computeThumbprint(ecJwk);

        assertNotNull(thumbprint);
        assertFalse(thumbprint.isEmpty());
        // Base64url without padding should not contain '='
        assertFalse(thumbprint.contains("="));
    }

    @Test
    void shouldComputeThumbprintForOkpKey() {
        Map<String, Object> okpJwk = Map.of(
                "kty", "OKP",
                "crv", "Ed25519",
                "x", "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo"
        );

        String thumbprint = JwkThumbprintUtil.computeThumbprint(okpJwk);

        assertNotNull(thumbprint);
        assertFalse(thumbprint.isEmpty());
        assertFalse(thumbprint.contains("="));
    }

    @Test
    void shouldProduceLexicographicOrdering() {
        // Verify that the same key with different map ordering produces the same thumbprint
        Map<String, Object> jwk1 = Map.of("kty", "RSA", "n", "abc", "e", "AQAB");
        Map<String, Object> jwk2 = Map.of("e", "AQAB", "n", "abc", "kty", "RSA");

        assertEquals(
                JwkThumbprintUtil.computeThumbprint(jwk1),
                JwkThumbprintUtil.computeThumbprint(jwk2)
        );
    }

    @Test
    void shouldRejectMissingKty() {
        Map<String, Object> jwk = Map.of("n", "abc", "e", "AQAB");

        assertThrows(IllegalArgumentException.class, () -> JwkThumbprintUtil.computeThumbprint(jwk));
    }

    @Test
    void shouldRejectUnsupportedKeyType() {
        Map<String, Object> jwk = Map.of("kty", "oct", "k", "abc");

        assertThrows(IllegalArgumentException.class, () -> JwkThumbprintUtil.computeThumbprint(jwk));
    }

    @Test
    void shouldRejectMissingRequiredRsaFields() {
        Map<String, Object> jwk = Map.of("kty", "RSA", "n", "abc");
        // Missing 'e'

        assertThrows(IllegalArgumentException.class, () -> JwkThumbprintUtil.computeThumbprint(jwk));
    }

    @Test
    void shouldRejectMissingRequiredEcFields() {
        Map<String, Object> jwk = Map.of("kty", "EC", "crv", "P-256", "x", "abc");
        // Missing 'y'

        assertThrows(IllegalArgumentException.class, () -> JwkThumbprintUtil.computeThumbprint(jwk));
    }

    @Test
    void shouldIgnoreExtraFields() {
        // Extra fields should be ignored - thumbprint should only use required members
        Map<String, Object> jwkWithExtras = Map.of(
                "kty", "RSA",
                "n", "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
                "e", "AQAB",
                "kid", "extra-field",
                "alg", "RS256"
        );
        Map<String, Object> jwkMinimal = Map.of(
                "kty", "RSA",
                "n", "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
                "e", "AQAB"
        );

        assertEquals(
                JwkThumbprintUtil.computeThumbprint(jwkMinimal),
                JwkThumbprintUtil.computeThumbprint(jwkWithExtras)
        );
    }
}
