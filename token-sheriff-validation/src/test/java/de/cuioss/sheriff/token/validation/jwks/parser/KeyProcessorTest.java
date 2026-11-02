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
package de.cuioss.sheriff.token.validation.jwks.parser;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.events.SecurityEventCounter.EventType;
import de.cuioss.sheriff.token.commons.transport.JwkKey;
import de.cuioss.sheriff.token.validation.JWTValidationLogMessages;
import de.cuioss.sheriff.token.validation.jwks.key.KeyInfo;
import de.cuioss.sheriff.token.validation.security.JwkAlgorithmPreferences;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler.Algorithm;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link KeyProcessor} EC key processing: the {@code alg} of an EC JWK must equal the
 * algorithm its curve binds it to, otherwise the key is dropped.
 */
@EnableTestLogger
@DisplayName("KeyProcessor EC alg versus curve")
class KeyProcessorTest {

    private static final String KID = "ec-key";

    private SecurityEventCounter counter;
    private KeyProcessor processor;

    @BeforeEach
    void setUp() {
        counter = new SecurityEventCounter();
        processor = new KeyProcessor(counter, new JwkAlgorithmPreferences());
    }

    @Nested
    @DisplayName("Mismatching alg (V-17e)")
    class MismatchingAlg {

        @ParameterizedTest(name = "{0} key labelled {1} is dropped")
        @CsvSource({
                "ES256, P-256, ES384",
                "ES384, P-384, ES256",
                "ES256, P-256, RS256"
        })
        @DisplayName("Should drop an EC key whose alg differs from the curve-derived algorithm")
        void shouldDropKeyWithMismatchingAlg(Algorithm keyAlgorithm, String curve, String alg) {
            JwkKey jwk = ecJwk(keyAlgorithm, curve, alg);

            Optional<KeyInfo> result = processor.processKey(jwk);

            assertTrue(result.isEmpty(), "A " + curve + " key labelled " + alg + " must not be installed");
            assertEquals(1, counter.getCount(EventType.JWKS_JSON_PARSE_FAILED), "Counted exactly once");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    JWTValidationLogMessages.WARN.EC_KEY_PARSE_FAILED.resolveIdentifierString());
        }
    }

    @Nested
    @DisplayName("Consistent or absent alg")
    class ConsistentAlg {

        @ParameterizedTest(name = "{1} key labelled {2} is accepted")
        @CsvSource({
                "ES256, P-256, ES256",
                "ES384, P-384, ES384",
                "ES512, P-521, ES512"
        })
        @DisplayName("Should accept an EC key whose alg matches its curve")
        void shouldAcceptKeyWithMatchingAlg(Algorithm keyAlgorithm, String curve, String alg) {
            Optional<KeyInfo> result = processor.processKey(ecJwk(keyAlgorithm, curve, alg));

            assertTrue(result.isPresent(), "A " + curve + " key labelled " + alg + " must be installed");
            assertEquals(alg, result.get().algorithm());
            assertEquals(0, counter.getCount(EventType.JWKS_JSON_PARSE_FAILED));
        }

        @Test
        @DisplayName("Should derive the algorithm from the curve when alg is absent")
        void shouldDeriveAlgorithmWhenAlgAbsent() {
            Optional<KeyInfo> result = processor.processKey(ecJwk(Algorithm.ES384, "P-384", null));

            assertTrue(result.isPresent(), "An EC key without alg must be installed");
            assertEquals("ES384", result.get().algorithm());
            assertEquals(0, counter.getCount(EventType.JWKS_JSON_PARSE_FAILED));
        }
    }

    private static JwkKey ecJwk(Algorithm keyAlgorithm, String curve, String alg) {
        ECPublicKey publicKey = (ECPublicKey) InMemoryKeyMaterialHandler.getDefaultPublicKey(keyAlgorithm);
        int coordinateSize = (publicKey.getParams().getCurve().getField().getFieldSize() + 7) / 8;
        return new JwkKey("EC", KID, alg, null, null, curve,
                coordinate(publicKey.getW().getAffineX(), coordinateSize),
                coordinate(publicKey.getW().getAffineY(), coordinateSize));
    }

    private static String coordinate(BigInteger value, int size) {
        byte[] bytes = value.toByteArray();
        byte[] result = new byte[size];
        int start = Math.max(0, bytes.length - size);
        int length = bytes.length - start;
        System.arraycopy(bytes, start, result, size - length, length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
    }
}
