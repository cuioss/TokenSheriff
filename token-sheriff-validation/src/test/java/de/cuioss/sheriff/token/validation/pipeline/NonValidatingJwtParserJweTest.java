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
package de.cuioss.sheriff.token.validation.pipeline;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.JwkKey;
import de.cuioss.sheriff.token.validation.JWTValidationLogMessages;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptor;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.test.JweTestTokenFactory;
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

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@DisplayName("NonValidatingJwtParser JWE Integration Tests")
class NonValidatingJwtParserJweTest {

    private static final String ISSUER = "https://test-issuer.example.com";
    private KeyPair rsaEncryptionKeyPair;
    private SecurityEventCounter counter;

    @BeforeEach
    void setUp() {
        rsaEncryptionKeyPair = JweTestTokenFactory.generateRsaKeyPair();
        counter = new SecurityEventCounter();
    }

    @Nested
    @DisplayName("JWE Token Handling")
    class JweTokenHandling {

        @Test
        @DisplayName("Should decrypt JWE and return inner JWS as DecodedJwt")
        void shouldDecryptJweAndReturnInnerJws() {
            // Create JWE token
            String jwe = JweTestTokenFactory.createJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    rsaEncryptionKeyPair.getPublic(),
                    "RS256", "RSA-OAEP", "A256GCM", ISSUER, null);

            // Create parser with JWE config
            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(rsaEncryptionKeyPair.getPrivate())
                    .build();

            NonValidatingJwtParser parser = NonValidatingJwtParser.builder()
                    .securityEventCounter(counter)
                    .jweDecryptionConfig(config)
                    .build();

            // Decode
            DecodedJwt decoded = parser.decode(jwe);

            assertNotNull(decoded);
            assertNotNull(decoded.header());
            assertNotNull(decoded.body());
            // The rawToken should be the original JWE string (for caching)
            assertEquals(jwe, decoded.rawToken());
            // The inner JWS header should have the signing algorithm
            assertEquals("RS256", decoded.header().alg());
            // Should have issuer from inner JWS
            assertTrue(decoded.getIssuer().isPresent());
            assertEquals(ISSUER, decoded.getIssuer().get());
        }

        @Test
        @DisplayName("Should throw when JWE received but no config")
        void shouldThrowWhenJweReceivedButNoConfig() {
            String jwe = JweTestTokenFactory.createJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    rsaEncryptionKeyPair.getPublic(),
                    "RS256", "RSA-OAEP", "A256GCM", ISSUER, null);

            // Parser without JWE config
            NonValidatingJwtParser parser = NonValidatingJwtParser.builder()
                    .securityEventCounter(counter)
                    .build();

            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> parser.decode(jwe));
            assertEquals(SecurityEventCounter.EventType.JWE_DECRYPTION_NOT_CONFIGURED, ex.getEventType());
            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_NOT_CONFIGURED));
        }

        @Test
        @DisplayName("Should still parse JWS tokens normally when JWE is configured")
        void shouldStillParseJwsNormally() {
            // Create a regular JWS token
            String jws = JweTestTokenFactory.createSignedJws(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(), "RS256", ISSUER);

            // Create parser with JWE config (should not interfere with JWS)
            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(rsaEncryptionKeyPair.getPrivate())
                    .build();

            NonValidatingJwtParser parser = NonValidatingJwtParser.builder()
                    .securityEventCounter(counter)
                    .jweDecryptionConfig(config)
                    .build();

            DecodedJwt decoded = parser.decode(jws);
            assertNotNull(decoded);
            assertEquals("RS256", decoded.header().alg());
            assertTrue(decoded.getIssuer().isPresent());
            assertEquals(ISSUER, decoded.getIssuer().get());
        }
    }

    @Nested
    @DisplayName("ECDH-ES JWE Token Handling")
    class EcdhEsTokenHandling {

        @ParameterizedTest
        @CsvSource({
                "secp256r1, A128GCM",
                "secp256r1, A256GCM",
                "secp256r1, A128CBC-HS256",
                "secp256r1, A256CBC-HS512",
                "secp384r1, A128GCM",
                "secp384r1, A256GCM",
                "secp384r1, A128CBC-HS256",
                "secp384r1, A256CBC-HS512"
        })
        @DisplayName("Should decode an ECDH-ES JWE end to end through the parser")
        void shouldDecodeEcdhEsJwe(String curve, String enc) {
            KeyPair recipientKeyPair = JweTestTokenFactory.generateEcKeyPair(curve);
            String jwe = JweTestTokenFactory.createEcdhEsJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    JweTestTokenFactory.generateEcKeyPair(curve),
                    (ECPublicKey) recipientKeyPair.getPublic(),
                    enc, ISSUER);

            NonValidatingJwtParser parser = NonValidatingJwtParser.builder()
                    .securityEventCounter(counter)
                    .jweDecryptionConfig(JweDecryptionConfig.builder()
                            .defaultDecryptionKey(recipientKeyPair.getPrivate())
                            .build())
                    .build();

            DecodedJwt decoded = parser.decode(jwe);

            assertAll("ECDH-ES " + curve + " / " + enc,
                    () -> assertEquals("RS256", decoded.header().alg()),
                    () -> assertEquals(ISSUER, decoded.getIssuer().orElseThrow()),
                    () -> assertEquals(jwe, decoded.rawToken()),
                    () -> assertEquals(0, counter.getCount(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT)));
        }

        @Test
        @DisplayName("Should reject an ECDH-ES header whose epk is a JSON string")
        void shouldRejectStringEncodedEpk() {
            String headerJson = "{\"alg\":\"ECDH-ES\",\"enc\":\"A128GCM\","
                    + "\"epk\":\"{\\\"kty\\\":\\\"EC\\\",\\\"crv\\\":\\\"P-256\\\"}\"}";
            String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String token = encodedHeader + ".AA.AA.AA.AA";

            NonValidatingJwtParser parser = NonValidatingJwtParser.builder()
                    .securityEventCounter(counter)
                    .jweDecryptionConfig(JweDecryptionConfig.builder()
                            .defaultDecryptionKey(JweTestTokenFactory.generateEcKeyPair("secp256r1").getPrivate())
                            .build())
                    .build();

            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> parser.decode(token));
            assertEquals(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT, ex.getEventType());
            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT));
        }
    }

    @Nested
    @DisplayName("Decode Mode Honoured by JWE Decryption")
    class DecodeModeHandling {

        private NonValidatingJwtParser parser;
        private String undecryptableJwe;

        @BeforeEach
        void setUpParser() {
            parser = NonValidatingJwtParser.builder()
                    .securityEventCounter(counter)
                    .jweDecryptionConfig(JweDecryptionConfig.builder()
                            .defaultDecryptionKey(rsaEncryptionKeyPair.getPrivate())
                            .build())
                    .build();
            undecryptableJwe = JweTestTokenFactory.createJweWithTamperedAuthTag(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    rsaEncryptionKeyPair.getPublic(),
                    ISSUER);
        }

        @Test
        @DisplayName("An undecryptable JWE decoded as an opaque token is neither logged nor counted")
        void opaqueDecodeShouldNotLogOrCount() {
            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> parser.decodeOpaqueToken(undecryptableJwe));

            assertEquals(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED, ex.getEventType());
            assertEquals(0, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED),
                    "An opaque decode must not count the decryption failure");
            LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, JweDecryptor.class);
            LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, NonValidatingJwtParser.class);
        }

        @Test
        @DisplayName("An undecryptable JWE decoded quietly is counted but not logged")
        void quietDecodeShouldCountButNotLog() {
            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> parser.decodeQuietly(undecryptableJwe));

            assertEquals(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED, ex.getEventType());
            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED),
                    "A quiet decode still counts the decryption failure exactly once");
            LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, JweDecryptor.class);
        }

        @Test
        @DisplayName("An undecryptable JWE on the normal decode path is logged and counted once")
        void normalDecodeShouldLogAndCountOnce() {
            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> parser.decode(undecryptableJwe));

            assertEquals(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED, ex.getEventType());
            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED),
                    "The normal decode path counts the decryption failure exactly once");
            assertNotNull(ex.getCause(), "The decryption failure should carry its cause");
            LogAsserts.assertSingleLogMessagePresent(TestLogLevel.WARN,
                    JWTValidationLogMessages.WARN.JWE_DECRYPTION_FAILED.format(ex.getCause().getMessage()));
        }
    }

    /**
     * Drives every other {@code JweDecryptor} rejection site (unsupported algorithm, key not found,
     * unsupported compression, an ECDH-ES structural rejection, a malformed {@code epk} coordinate)
     * through the three parser decode modes.
     */
    @Nested
    @DisplayName("Decode Mode Honoured by Every JWE Rejection Site")
    class DecodeModeRejectionSites {

        private static final String KNOWN_KID = "known-kid";
        private static final String EC_EPK = "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"AQ\",\"y\":\"AQ\"}";

        private NonValidatingJwtParser parser;

        @BeforeEach
        void setUpParser() {
            parser = NonValidatingJwtParser.builder()
                    .securityEventCounter(counter)
                    .jweDecryptionConfig(JweDecryptionConfig.builder()
                            .decryptionKey(KNOWN_KID, rsaEncryptionKeyPair.getPrivate())
                            .build())
                    .build();
        }

        static Stream<Arguments> rejectionSites() {
            return Stream.of(
                    Arguments.of("unsupported algorithm",
                            token("{\"alg\":\"RSA1_5\",\"enc\":\"A256GCM\",\"kid\":\"" + KNOWN_KID + "\"}", "AA"),
                            SecurityEventCounter.EventType.JWE_UNSUPPORTED_ALGORITHM,
                            JWTValidationLogMessages.WARN.JWE_UNSUPPORTED_ALGORITHM, ""),
                    Arguments.of("key not found",
                            token("{\"alg\":\"RSA-OAEP\",\"enc\":\"A256GCM\",\"kid\":\"unknown-kid\"}", "AA"),
                            SecurityEventCounter.EventType.JWE_DECRYPTION_KEY_NOT_FOUND,
                            JWTValidationLogMessages.WARN.JWE_DECRYPTION_KEY_NOT_FOUND, ""),
                    Arguments.of("unsupported compression",
                            token("{\"alg\":\"RSA-OAEP\",\"enc\":\"A256GCM\",\"zip\":\"GZIP\",\"kid\":\""
                                    + KNOWN_KID + "\"}", "AA"),
                            SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED,
                            JWTValidationLogMessages.WARN.JWE_DECRYPTION_FAILED, ""),
                    // The fixture pairs an ECDH-ES header with an RSA recipient key, so later epk /
                    // key checks would also reject it: the message fragment pins the
                    // non-empty-Encrypted-Key guard itself.
                    Arguments.of("ECDH-ES non-empty encrypted key",
                            token("{\"alg\":\"ECDH-ES\",\"enc\":\"A128GCM\",\"kid\":\"" + KNOWN_KID
                                    + "\",\"epk\":" + EC_EPK + "}", "AA"),
                            SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED,
                            JWTValidationLogMessages.WARN.JWE_DECRYPTION_FAILED,
                            "ECDH-ES requires an empty JWE Encrypted Key"),
                    Arguments.of("malformed epk coordinate",
                            token("{\"alg\":\"ECDH-ES\",\"enc\":\"A128GCM\",\"kid\":\"" + KNOWN_KID
                                    + "\",\"epk\":{\"kty\":\"EC\",\"crv\":\"P-256\","
                                    + "\"x\":\"AQ\\r\\nWARN forged coordinate\",\"y\":\"AQ\"}}", ""),
                            SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED,
                            JWTValidationLogMessages.WARN.JWE_DECRYPTION_FAILED, ""));
        }

        /**
         * Asserts the rejection carries the row's guard-specific message fragment; an empty
         * fragment pins nothing beyond the event type.
         */
        private static void assertGuardMessage(TokenValidationException ex, String expectedMessageFragment,
                String site) {
            if (!expectedMessageFragment.isEmpty()) {
                assertTrue(ex.getMessage().contains(expectedMessageFragment),
                        "Rejection for " + site + " must come from its guard, but was: " + ex.getMessage());
            }
        }

        private static String token(String headerJson, String encryptedKey) {
            String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            return encodedHeader + "." + encryptedKey + ".AA.AA.AA";
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("rejectionSites")
        @DisplayName("An opaque decode neither logs nor counts the rejection")
        void opaqueDecodeShouldNotLogOrCount(String site, String token,
                SecurityEventCounter.EventType eventType, LogRecord logRecord, String expectedMessageFragment) {
            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> parser.decodeOpaqueToken(token));

            assertEquals(eventType, ex.getEventType(), site);
            assertGuardMessage(ex, expectedMessageFragment, site);
            assertEquals(0, counter.getCount(eventType), "An opaque decode must not count: " + site);
            assertNoWarnings(site);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("rejectionSites")
        @DisplayName("A quiet decode counts the rejection once but does not log it")
        void quietDecodeShouldCountButNotLog(String site, String token,
                SecurityEventCounter.EventType eventType, LogRecord logRecord, String expectedMessageFragment) {
            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> parser.decodeQuietly(token));

            assertEquals(eventType, ex.getEventType(), site);
            assertGuardMessage(ex, expectedMessageFragment, site);
            assertEquals(1, counter.getCount(eventType), "A quiet decode counts exactly once: " + site);
            assertNoWarnings(site);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("rejectionSites")
        @DisplayName("A normal decode logs and counts the rejection exactly once")
        void normalDecodeShouldLogAndCountOnce(String site, String token,
                SecurityEventCounter.EventType eventType, LogRecord logRecord, String expectedMessageFragment) {
            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> parser.decode(token));

            assertEquals(eventType, ex.getEventType(), site);
            assertGuardMessage(ex, expectedMessageFragment, site);
            assertEquals(1, counter.getCount(eventType), "A normal decode counts exactly once: " + site);
            LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                    logRecord.resolveIdentifierString());
            assertEquals(1, TestLoggerFactory.getTestHandler().resolveLogMessages(TestLogLevel.WARN).size(),
                    "Exactly one WARN for: " + site);
            LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, JwkKey.class);
            assertTrue(TestLoggerFactory.getTestHandler()
                            .resolveLogMessagesContaining(TestLogLevel.WARN, "forged coordinate").isEmpty(),
                    "No raw header text may reach the log: " + site);
        }

        private static void assertNoWarnings(String site) {
            assertTrue(TestLoggerFactory.getTestHandler().resolveLogMessages(TestLogLevel.WARN).isEmpty(),
                    "No WARN may be written for: " + site);
        }
    }

    /**
     * A JWE header DSL-JSON cannot bind (here a string-valued {@code epk} followed by raw CR/LF and
     * forged log text) fails in the header-decode step. DSL-JSON's parse-error message quotes the
     * input around the failure position, so the raw header bytes must stay out of the exception
     * message and the WARN, while the cause chain is kept.
     */
    @Nested
    @DisplayName("JWE Header-Decode Failure Sanitization")
    class HeaderDecodeFailureSanitization {

        /** DSL-JSON quotes about 20 bytes either side of the failure position, so keep the marker short. */
        private static final String FORGED_TEXT = "WARN forged";

        private NonValidatingJwtParser parser;
        private String malformedHeaderJwe;

        @BeforeEach
        void setUpParser() {
            parser = NonValidatingJwtParser.builder()
                    .securityEventCounter(counter)
                    .jweDecryptionConfig(JweDecryptionConfig.builder()
                            .defaultDecryptionKey(rsaEncryptionKeyPair.getPrivate())
                            .build())
                    .build();
            String headerJson = "{\"alg\":\"ECDH-ES\",\"enc\":\"A128GCM\",\"epk\":\"\r\n" + FORGED_TEXT + "\"}";
            malformedHeaderJwe = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8)) + ".AA.AA.AA.AA";
        }

        @Test
        @DisplayName("The exception message carries no raw header text while the cause chain is kept")
        void exceptionMessageShouldNotCarryRawHeaderText() {
            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> parser.decode(malformedHeaderJwe));

            assertEquals(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT, ex.getEventType());
            assertNotNull(ex.getCause(), "The DSL-JSON failure must stay on the cause chain");
            assertTrue(ex.getCause().getMessage().contains("\r\n" + FORGED_TEXT),
                    "Non-vacuity: the DSL-JSON message must quote the raw CR/LF and forged header text: "
                            + ex.getCause().getMessage());
            assertFalse(ex.getMessage().contains(FORGED_TEXT), ex.getMessage());
            assertFalse(ex.getMessage().contains("\r") || ex.getMessage().contains("\n"), ex.getMessage());
        }

        @Test
        @DisplayName("A normal decode logs one WARN free of the raw header text and counts once")
        void normalDecodeShouldLogSanitizedWarnOnce() {
            assertThrows(TokenValidationException.class, () -> parser.decode(malformedHeaderJwe));

            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT));
            var warnings = TestLoggerFactory.getTestHandler().resolveLogMessages(TestLogLevel.WARN);
            assertEquals(1, warnings.size(), "Exactly one WARN");
            var warning = warnings.getFirst();
            assertTrue(warning.getMessage().contains(
                    JWTValidationLogMessages.WARN.FAILED_TO_DECODE_JWT.resolveIdentifierString()), warning.getMessage());
            assertFalse(warning.getMessage().contains(FORGED_TEXT), warning.getMessage());
            assertNull(warning.getThrown(), "The WARN must not attach the DSL-JSON exception quoting raw header text");
        }

        @Test
        @DisplayName("A quiet decode counts once but does not log")
        void quietDecodeShouldCountButNotLog() {
            assertThrows(TokenValidationException.class, () -> parser.decodeQuietly(malformedHeaderJwe));

            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT));
            assertTrue(TestLoggerFactory.getTestHandler().resolveLogMessages(TestLogLevel.WARN).isEmpty());
        }

        @Test
        @DisplayName("An opaque decode neither logs nor counts")
        void opaqueDecodeShouldNotLogOrCount() {
            assertThrows(TokenValidationException.class, () -> parser.decodeOpaqueToken(malformedHeaderJwe));

            assertEquals(0, counter.getCount(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT));
            assertTrue(TestLoggerFactory.getTestHandler().resolveLogMessages(TestLogLevel.WARN).isEmpty());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should reject nested JWE (5-part inner content)")
        void shouldRejectNestedJwe() {
            // Create a JWE that wraps another JWE instead of a JWS
            // First create inner JWE
            String innerJwe = JweTestTokenFactory.createJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    rsaEncryptionKeyPair.getPublic(),
                    "RS256", "RSA-OAEP", "A256GCM", ISSUER, null);

            // Then wrap in outer JWE
            String outerJwe = JweTestTokenFactory.encryptAsJwe(
                    innerJwe, rsaEncryptionKeyPair.getPublic(), "RSA-OAEP", "A256GCM", null);

            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(rsaEncryptionKeyPair.getPrivate())
                    .build();

            NonValidatingJwtParser parser = NonValidatingJwtParser.builder()
                    .securityEventCounter(counter)
                    .jweDecryptionConfig(config)
                    .build();

            assertThrows(TokenValidationException.class,
                    () -> parser.decode(outerJwe));
        }

        @Test
        @DisplayName("Should reject 4-part tokens")
        void shouldReject4PartTokens() {
            NonValidatingJwtParser parser = NonValidatingJwtParser.builder()
                    .securityEventCounter(counter)
                    .build();

            assertThrows(TokenValidationException.class,
                    () -> parser.decode("a.b.c.d"));
        }

        @Test
        @DisplayName("Should reject 6-part tokens")
        void shouldReject6PartTokens() {
            NonValidatingJwtParser parser = NonValidatingJwtParser.builder()
                    .securityEventCounter(counter)
                    .build();

            assertThrows(TokenValidationException.class,
                    () -> parser.decode("a.b.c.d.e.f"));
        }

        @Test
        @DisplayName("Should reject 5-part token without enc field")
        void shouldReject5PartTokenWithoutEnc() {
            // Create a 5-part token with a header that has no 'enc' field
            String headerJson = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
            String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String fakeToken = encodedHeader + ".b.c.d.e";

            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(rsaEncryptionKeyPair.getPrivate())
                    .build();

            NonValidatingJwtParser parser = NonValidatingJwtParser.builder()
                    .securityEventCounter(counter)
                    .jweDecryptionConfig(config)
                    .build();

            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> parser.decode(fakeToken));
            assertTrue(ex.getMessage().contains("enc"));
        }

        @Test
        @DisplayName("Should decrypt JWE with compression (zip=DEF)")
        void shouldDecryptJweWithCompression() {
            String jwe = JweTestTokenFactory.createCompressedJwe(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    rsaEncryptionKeyPair.getPublic(),
                    "RSA-OAEP", "A256GCM", ISSUER);

            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(rsaEncryptionKeyPair.getPrivate())
                    .compressionEnabled(true)
                    .build();

            NonValidatingJwtParser parser = NonValidatingJwtParser.builder()
                    .securityEventCounter(counter)
                    .jweDecryptionConfig(config)
                    .build();

            DecodedJwt decoded = parser.decode(jwe);
            assertNotNull(decoded);
            assertEquals("RS256", decoded.header().alg());
            assertTrue(decoded.getIssuer().isPresent());
            assertEquals(ISSUER, decoded.getIssuer().get());
        }
    }
}
