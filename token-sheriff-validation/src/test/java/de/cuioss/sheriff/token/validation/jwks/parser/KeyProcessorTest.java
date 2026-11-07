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
import de.cuioss.test.juli.TestLoggerFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.logging.LogRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * Key ids, key types, algorithm and curve names come from a remote JWKS document, and a key
     * parser's exception message can quote them. Every log record renders them sanitised: no line
     * break, no raw exception object, the forged text still visible in escaped form.
     */
    @Nested
    @DisplayName("Remote text in log records (CWE-117)")
    class RemoteTextInLogRecords {

        private static final String FORGED = "evil\r\nWARN forged line";

        static Stream<Arguments> forgedKeys() {
            RSAPublicKey rsa = (RSAPublicKey) InMemoryKeyMaterialHandler.getDefaultPublicKey(Algorithm.RS256);
            String n = unsigned(rsa.getModulus());
            String e = unsigned(rsa.getPublicExponent());
            return Stream.of(
                    Arguments.of("forged kty", new JwkKey(FORGED, KID, null, n, e, null, null, null),
                            JWTValidationLogMessages.WARN.JWK_UNSUPPORTED_KEY_TYPE, true),
                    Arguments.of("forged alg", new JwkKey("RSA", KID, FORGED, n, e, null, null, null),
                            JWTValidationLogMessages.WARN.JWK_INVALID_ALGORITHM, true),
                    Arguments.of("forged kid on an undersized RSA key", new JwkKey("RSA", FORGED, "RS256", "AQ", e, null, null, null),
                            JWTValidationLogMessages.WARN.RSA_KEY_PARSE_FAILED, true),
                    Arguments.of("forged kid on an RSA key that fails to parse",
                            new JwkKey("RSA", FORGED, "RS256", "!!", e, null, null, null),
                            JWTValidationLogMessages.WARN.RSA_KEY_PARSE_FAILED, true),
                    Arguments.of("forged kid on an RSA key without alg", new JwkKey("RSA", FORGED, null, n, e, null, null, null),
                            JWTValidationLogMessages.WARN.JWK_RSA_DEFAULTING_TO_RS256, true),
                    Arguments.of("forged kid on an EC key with a mismatching alg",
                            withKid(ecJwk(Algorithm.ES256, "P-256", "ES384"), FORGED),
                            JWTValidationLogMessages.WARN.EC_KEY_PARSE_FAILED, true),
                    Arguments.of("forged crv on an EC key", withCurve(ecJwk(Algorithm.ES256, "P-256", null), FORGED),
                            JWTValidationLogMessages.WARN.EC_KEY_PARSE_FAILED, false),
                    Arguments.of("forged kid on an OKP key that fails to parse",
                            new JwkKey("OKP", FORGED, null, null, null, "Ed25519", "!!", null),
                            JWTValidationLogMessages.WARN.OKP_KEY_PARSE_FAILED, true));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("forgedKeys")
        @DisplayName("Should log remote key text escaped and bounded, without the raw exception object")
        void shouldLogRemoteTextSanitised(String site, JwkKey jwk, LogRecord expectedRecord, boolean forgedTextLogged) {
            processor.processKey(jwk);

            List<java.util.logging.LogRecord> records = TestLoggerFactory.getTestHandler()
                    .resolveLogMessagesContaining(TestLogLevel.WARN, expectedRecord.resolveIdentifierString());
            assertEquals(1, records.size(), site + ": exactly one " + expectedRecord.resolveIdentifierString());
            String message = records.getFirst().getMessage();
            assertTrue(message.chars().noneMatch(c -> c == '\r' || c == '\n' || c == ' '),
                    site + ": the record must carry no line break: " + message);
            assertNull(records.getFirst().getThrown(),
                    site + ": no exception object whose message may quote the remote input");
            if (forgedTextLogged) {
                assertTrue(message.contains("evil??WARN forged?line"),
                        site + ": the diagnostic must stay, escaped: " + message);
            }
            assertFalse(message.contains("WARN forged\r"), message);
        }

        @Test
        @DisplayName("Should bound a long remote kid to the sanitiser's limit")
        void shouldBoundLongRemoteKid() {
            String longKid = "evil\r\n" + "k".repeat(90);

            processor.processKey(withKid(ecJwk(Algorithm.ES256, "P-256", "ES384"), longKid));

            String message = TestLoggerFactory.getTestHandler()
                    .resolveLogMessagesContaining(TestLogLevel.WARN,
                            JWTValidationLogMessages.WARN.EC_KEY_PARSE_FAILED.resolveIdentifierString())
                    .getFirst().getMessage();
            assertTrue(message.contains("evil??" + "k".repeat(58) + "..."), message);
            assertFalse(message.contains("k".repeat(59)), "the kid must be cut at the bound: " + message);
        }

        private static JwkKey withKid(JwkKey jwk, String kid) {
            return new JwkKey(jwk.kty(), kid, jwk.alg(), jwk.n(), jwk.e(), jwk.crv(), jwk.x(), jwk.y());
        }

        private static JwkKey withCurve(JwkKey jwk, String crv) {
            return new JwkKey(jwk.kty(), jwk.kid(), jwk.alg(), jwk.n(), jwk.e(), crv, jwk.x(), jwk.y());
        }

        private static String unsigned(BigInteger value) {
            byte[] bytes = value.toByteArray();
            int start = bytes[0] == 0 && bytes.length > 1 ? 1 : 0;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOfRange(bytes, start, bytes.length));
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
