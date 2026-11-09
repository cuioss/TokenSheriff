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
package de.cuioss.sheriff.token.validation.jwe;

import com.dslplatform.json.DslJson;
import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.JWTValidationLogMessages;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.json.JwtHeader;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.test.JweTestTokenFactory;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.TestLoggerFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link JweDecryptor} tests of the ECDH-ES key agreement and of compressed payloads. Split out of
 * {@link JweDecryptorTest} by topic.
 */
@EnableTestLogger
@DisplayName("JweDecryptor Tests: ECDH-ES and compression")
class JweDecryptorEcdhEsAndCompressionTest {

    /**
     * The recipient key of every test in which the key itself is not the subject, generated once for
     * the class. {@code shouldFailWithWrongKey} generates its own, differing key.
     */
    private static final KeyPair RSA_ENCRYPTION_KEY_PAIR = JweTestTokenFactory.generateRsaKeyPair();


    private static final String ISSUER = "https://test-issuer.example.com";

    private JweDecryptor decryptor;
    private SecurityEventCounter counter;
    private DslJson<Object> dslJson;

    @BeforeEach
    void setUp() {
        decryptor = new JweDecryptor();
        counter = new SecurityEventCounter();
        dslJson = ParserConfig.builder().build().getDslJson();
    }

    @Nested
    @DisplayName("ECDH-ES Algorithm Tests")
    class EcdhEsTests {

        @ParameterizedTest
        @CsvSource({
                "secp256r1, A128GCM",
                "secp256r1, A256GCM",
                "secp384r1, A128GCM",
                "secp384r1, A256GCM"
        })
        @DisplayName("Should decrypt JWE with ECDH-ES")
        void shouldDecryptEcdhEs(String curve, String enc) {
            KeyPair recipientKeyPair = JweTestTokenFactory.generateEcKeyPair(curve);
            KeyPair ephemeralKeyPair = JweTestTokenFactory.generateEcKeyPair(curve);

            String jwe = JweTestTokenFactory.createEcdhEsJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    ephemeralKeyPair,
                    (ECPublicKey) recipientKeyPair.getPublic(),
                    enc, ISSUER);

            String[] parts = jwe.split("\\.");
            assertEquals(5, parts.length);

            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(recipientKeyPair.getPrivate())
                    .build();

            JwtHeader header = parseHeader(parts[0]);
            assertTrue(header.getEpk().isPresent(), "The production header decode should bind the epk object");
            assertEquals("EC", header.getEpk().orElseThrow().kty(), "The fixture epk must carry kty EC");

            String innerJws = decryptor.decrypt(parts, header, config, counter);
            assertNotNull(innerJws);
            assertEquals(3, innerJws.split("\\.").length);
            assertEquals(0, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED));
        }

        @Test
        @DisplayName("Should decrypt ECDH-ES with apu/apv and fail when the header apv differs from the producer's")
        void shouldBindApuAndApvIntoTheDerivedKey() {
            String curve = "secp256r1";
            KeyPair recipientKeyPair = JweTestTokenFactory.generateEcKeyPair(curve);
            ECPublicKey recipientPublicKey = (ECPublicKey) recipientKeyPair.getPublic();
            String innerJws = JweTestTokenFactory.createSignedJws(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(), "RS256", ISSUER);
            byte[] apu = "producer-party".getBytes(StandardCharsets.UTF_8);
            byte[] apv = "recipient-party".getBytes(StandardCharsets.UTF_8);
            byte[] otherApv = "another-recipient".getBytes(StandardCharsets.UTF_8);
            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(recipientKeyPair.getPrivate())
                    .build();

            String[] matching = JweTestTokenFactory.encryptAsEcdhEsJwe(innerJws,
                    JweTestTokenFactory.generateEcKeyPair(curve), recipientPublicKey, "A128GCM",
                    apu, apv, apv).split("\\.");
            JwtHeader matchingHeader = parseHeader(matching[0]);
            assertTrue(matchingHeader.getApu().isPresent() && matchingHeader.getApv().isPresent(),
                    "The fixture header must carry apu and apv");

            assertEquals(innerJws, decryptor.decrypt(matching, matchingHeader, config, counter),
                    "A token whose header apu/apv match the producer's must decrypt to the inner JWS");
            assertEquals(0, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED));

            // The producer derived the CEK with otherApv but the header advertises apv: the recipient
            // derives a different CEK, so content authentication fails
            String[] mismatched = JweTestTokenFactory.encryptAsEcdhEsJwe(innerJws,
                    JweTestTokenFactory.generateEcKeyPair(curve), recipientPublicKey, "A128GCM",
                    apu, apv, otherApv).split("\\.");
            JwtHeader mismatchedHeader = parseHeader(mismatched[0]);

            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(mismatched, mismatchedHeader, config, counter));
            assertEquals(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED, ex.getEventType());
            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED));
        }

        @Test
        @DisplayName("Should fail ECDH-ES with RSA key")
        void shouldFailEcdhEsWithRsaKey() {
            KeyPair ecKeyPair = JweTestTokenFactory.generateEcKeyPair("secp256r1");
            KeyPair ephemeralKeyPair = JweTestTokenFactory.generateEcKeyPair("secp256r1");

            String jwe = JweTestTokenFactory.createEcdhEsJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    ephemeralKeyPair,
                    (ECPublicKey) ecKeyPair.getPublic(),
                    "A128GCM", ISSUER);

            String[] parts = jwe.split("\\.");
            JwtHeader header = parseHeader(parts[0]);

            // Use RSA key for ECDH-ES — should fail
            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(RSA_ENCRYPTION_KEY_PAIR.getPrivate())
                    .build();

            assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(parts, header, config, counter));
        }

        @Test
        @DisplayName("Should reject ECDH-ES with mismatched curve (Invalid Curve Attack)")
        void shouldRejectMismatchedCurve() {
            // Recipient key on P-384, but ephemeral key on P-256
            KeyPair recipientKeyPair = JweTestTokenFactory.generateEcKeyPair("secp384r1");
            KeyPair ephemeralKeyPair = JweTestTokenFactory.generateEcKeyPair("secp256r1");

            String jwe = JweTestTokenFactory.createEcdhEsJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    ephemeralKeyPair,
                    (ECPublicKey) JweTestTokenFactory.generateEcKeyPair("secp256r1").getPublic(),
                    "A128GCM", ISSUER);

            String[] parts = jwe.split("\\.");
            JwtHeader header = parseHeader(parts[0]);

            // Use P-384 private key for decryption — should reject due to curve mismatch
            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(recipientKeyPair.getPrivate())
                    .build();

            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(parts, header, config, counter));
            assertTrue(ex.getMessage().contains("curve does not match"));
        }

        @Test
        @DisplayName("L6: should reject an off-curve ephemeral point independent of the provider")
        void shouldRejectOffCurveEphemeralPoint() {
            String curve = "secp256r1";
            KeyPair recipientKeyPair = JweTestTokenFactory.generateEcKeyPair(curve);
            KeyPair ephemeralKeyPair = JweTestTokenFactory.generateEcKeyPair(curve);

            String jwe = JweTestTokenFactory.createEcdhEsJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    ephemeralKeyPair,
                    (ECPublicKey) recipientKeyPair.getPublic(),
                    "A128GCM", ISSUER);

            String[] parts = jwe.split("\\.");
            parts[0] = moveEpkOffCurve(parts[0]);

            JwtHeader header = parseHeader(parts[0]);
            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(recipientKeyPair.getPrivate())
                    .build();

            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(parts, header, config, counter));
            assertTrue(ex.getMessage().contains("Ephemeral EC point"),
                    "An off-curve ephemeral point must be rejected by the independent on-curve check, but was: "
                            + ex.getMessage());
        }

        @Test
        @DisplayName("An off-curve epk rejection should be logged and counted exactly once")
        void offCurveRejectionShouldBeLoggedAndCountedOnce() {
            String curve = "secp256r1";
            KeyPair recipientKeyPair = JweTestTokenFactory.generateEcKeyPair(curve);
            String[] parts = JweTestTokenFactory.createEcdhEsJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    JweTestTokenFactory.generateEcKeyPair(curve),
                    (ECPublicKey) recipientKeyPair.getPublic(),
                    "A128GCM", ISSUER).split("\\.");
            parts[0] = moveEpkOffCurve(parts[0]);

            JwtHeader header = parseHeader(parts[0]);
            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(recipientKeyPair.getPrivate())
                    .build();

            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(parts, header, config, counter));

            assertEquals(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED, ex.getEventType());
            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED),
                    "An off-curve epk must record exactly one JWE_DECRYPTION_FAILED event");
            LogAsserts.assertSingleLogMessagePresent(TestLogLevel.WARN,
                    JWTValidationLogMessages.WARN.JWE_DECRYPTION_FAILED.format(ex.getMessage()));
        }

        @Test
        @DisplayName("Should reject ECDH-ES with a non-empty JWE Encrypted Key (RFC 7516 Section 5.2 step 10)")
        void shouldRejectEcdhEsWithNonEmptyEncryptedKey() {
            String curve = "secp256r1";
            KeyPair recipientKeyPair = JweTestTokenFactory.generateEcKeyPair(curve);
            String[] parts = JweTestTokenFactory.createEcdhEsJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    JweTestTokenFactory.generateEcKeyPair(curve),
                    (ECPublicKey) recipientKeyPair.getPublic(),
                    "A128GCM", ISSUER).split("\\.");
            assertTrue(parts[1].isEmpty(), "The fixture must start from a valid ECDH-ES token");
            parts[1] = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("unexpected-key".getBytes(StandardCharsets.UTF_8));

            JwtHeader header = parseHeader(parts[0]);
            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(recipientKeyPair.getPrivate())
                    .build();

            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(parts, header, config, counter));

            assertEquals(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED, ex.getEventType());
            assertTrue(ex.getMessage().contains("empty JWE Encrypted Key"),
                    "A non-empty Encrypted Key must be rejected, but was: " + ex.getMessage());
            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED));
        }

        /**
         * RFC 7518 section 4.6.1.1: the {@code epk} must be an EC key. An otherwise valid ECDH-ES
         * token whose {@code epk} {@code kty} is absent, a different key type, a different case, or
         * forged log text is rejected once by field name, never logging the raw value.
         */
        @ParameterizedTest(name = "kty member: [{0}]")
        @ValueSource(strings = {
                "\"kty\":\"RSA\",",
                "",
                "\"kty\":\"ec\",",
                "\"kty\":\"EC\\r\\nWARN forged kty\","
        })
        @DisplayName("Should reject an epk whose kty is absent or not exactly EC")
        void shouldRejectEpkWithNonEcKty(String ktyMember) {
            String curve = "secp256r1";
            KeyPair recipientKeyPair = JweTestTokenFactory.generateEcKeyPair(curve);
            String[] parts = JweTestTokenFactory.createEcdhEsJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    JweTestTokenFactory.generateEcKeyPair(curve),
                    (ECPublicKey) recipientKeyPair.getPublic(),
                    "A128GCM", ISSUER).split("\\.");
            parts[0] = replaceEpkKtyMember(parts[0], ktyMember);

            JwtHeader header = parseHeader(parts[0]);
            assertTrue(header.getEpk().isPresent(), "The tampered header must still bind an epk object");
            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(recipientKeyPair.getPrivate())
                    .build();

            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(parts, header, config, counter));

            assertEquals(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED, ex.getEventType());
            assertTrue(ex.getMessage().contains("epk 'kty' must be 'EC'"),
                    "The rejection must come from the kty check, but was: " + ex.getMessage());
            assertFalse(ex.getMessage().contains("forged"), ex.getMessage());
            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED));
            LogAsserts.assertSingleLogMessagePresent(TestLogLevel.WARN,
                    JWTValidationLogMessages.WARN.JWE_DECRYPTION_FAILED.format(ex.getMessage()));
            assertTrue(TestLoggerFactory.getTestHandler()
                            .resolveLogMessagesContaining(TestLogLevel.WARN, "forged").isEmpty(),
                    "The raw kty value must not reach any log line");
        }

        /**
         * Replaces the {@code "kty":"EC",} member the test factory writes first into the {@code epk}
         * object with {@code ktyMember} (which may be empty to drop the member).
         */
        private static String replaceEpkKtyMember(String encodedHeader, String ktyMember) {
            String headerJson = new String(Base64.getUrlDecoder().decode(encodedHeader), StandardCharsets.UTF_8);
            String original = "\"kty\":\"EC\",";
            assertTrue(headerJson.contains(original), "The ECDH-ES fixture must embed an epk with kty EC: " + headerJson);
            String tamperedJson = headerJson.replace(original, ktyMember);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(tamperedJson.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * Moves the embedded ephemeral point off the curve by mutating the first character of its x
         * coordinate. Curve parameters still match the recipient key, so only the independent on-curve
         * check can catch this.
         */
        private static String moveEpkOffCurve(String encodedHeader) {
            String headerJson = new String(Base64.getUrlDecoder().decode(encodedHeader), StandardCharsets.UTF_8);
            int keyIdx = headerJson.indexOf("\"x\"");
            assertTrue(keyIdx >= 0, "The ECDH-ES header must embed an epk x coordinate");
            int valueStart = headerJson.indexOf('"', headerJson.indexOf(':', keyIdx) + 1) + 1;
            char original = headerJson.charAt(valueStart);
            char replacement = original == 'A' ? 'B' : 'A';
            String tamperedJson = headerJson.substring(0, valueStart) + replacement
                    + headerJson.substring(valueStart + 1);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(tamperedJson.getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("L7: RSA-OAEP-256 is preferred over the SHA-1 MGF1 RSA-OAEP variant")
        void shouldPreferOaep256OverSha1Mgf1() {
            assertEquals("RSA-OAEP-256", JweAlgorithmPreferences.getPreferredKeyManagementAlgorithm());
            assertEquals("RSA-OAEP-256", JweAlgorithmPreferences.getDefaultKeyManagementAlgorithms().getFirst(),
                    "OAEP-256 must be listed ahead of the SHA-1 MGF1 RSA-OAEP variant");
            assertTrue(JweAlgorithmPreferences.getDefaultKeyManagementAlgorithms().contains("RSA-OAEP"),
                    "RSA-OAEP is retained for interoperability");
        }

        @Test
        @DisplayName("Should fail ECDH-ES without epk in header")
        void shouldFailEcdhEsWithoutEpk() {
            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(JweTestTokenFactory.generateEcKeyPair("secp256r1").getPrivate())
                    .build();

            // Header with ECDH-ES but no epk
            JwtHeader header = new JwtHeader("ECDH-ES", null, null, null, null,
                    "A128GCM", null, null, null, null);

            String[] parts = {"header", "", "iv", "ciphertext", "authTag"};

            assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(parts, header, config, counter));
        }
    }

    @Nested
    @DisplayName("Compression Tests")
    class CompressionTests {

        @Test
        @DisplayName("Should decrypt JWE with DEFLATE compression")
        void shouldDecryptWithDeflateCompression() {
            String jwe = JweTestTokenFactory.createCompressedJwe(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    RSA_ENCRYPTION_KEY_PAIR.getPublic(),
                    "RSA-OAEP", "A256GCM", ISSUER);

            String[] parts = jwe.split("\\.");
            assertEquals(5, parts.length);

            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(RSA_ENCRYPTION_KEY_PAIR.getPrivate())
                    .compressionEnabled(true)
                    .build();

            JwtHeader header = parseHeader(parts[0]);
            assertTrue(header.getZip().isPresent());
            assertEquals("DEF", header.getZip().get());

            String innerJws = decryptor.decrypt(parts, header, config, counter);
            assertNotNull(innerJws);
            assertEquals(3, innerJws.split("\\.").length);
        }

        @Test
        @DisplayName("Should reject unsupported compression algorithm")
        void shouldRejectUnsupportedCompression() {
            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(RSA_ENCRYPTION_KEY_PAIR.getPrivate())
                    .build();

            JwtHeader header = new JwtHeader("RSA-OAEP", null, null, null, null,
                    "A256GCM", "GZIP", null, null, null);

            String[] parts = {"header", "encKey", "iv", "ciphertext", "authTag"};

            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(parts, header, config, counter));
            assertTrue(ex.getMessage().contains("Unsupported compression"));
            assertEquals(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED, ex.getEventType());
            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED),
                    "An unsupported zip value must record exactly one JWE_DECRYPTION_FAILED event");
            LogAsserts.assertSingleLogMessagePresent(TestLogLevel.WARN,
                    JWTValidationLogMessages.WARN.JWE_DECRYPTION_FAILED.format(ex.getMessage()));
        }

        @Test
        @DisplayName("Should sanitize an unsupported zip value before it reaches the log")
        void shouldSanitizeUnsupportedZipValue() {
            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(RSA_ENCRYPTION_KEY_PAIR.getPrivate())
                    .build();
            JwtHeader header = new JwtHeader("RSA-OAEP", null, null, null, null,
                    "A256GCM", "GZIP\r\nWARN forged", null, null, null);
            String[] parts = {"header", "encKey", "iv", "ciphertext", "authTag"};

            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(parts, header, config, counter));

            assertTrue(ex.getMessage().endsWith("GZIP??WARN forged"),
                    "The zip value must be sanitized, but was: " + ex.getMessage());
            LogAsserts.assertSingleLogMessagePresent(TestLogLevel.WARN,
                    JWTValidationLogMessages.WARN.JWE_DECRYPTION_FAILED.format(ex.getMessage()));
        }

        @Test
        @DisplayName("Should reject compression when disabled")
        void shouldRejectCompressionWhenDisabled() {
            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(RSA_ENCRYPTION_KEY_PAIR.getPrivate())
                    .compressionEnabled(false)
                    .build();

            JwtHeader header = new JwtHeader("RSA-OAEP", null, null, null, null,
                    "A256GCM", "DEF", null, null, null);

            String[] parts = {"header", "encKey", "iv", "ciphertext", "authTag"};

            TokenValidationException ex = assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(parts, header, config, counter));
            assertTrue(ex.getMessage().contains("compression is disabled"));
            assertEquals(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED, ex.getEventType());
            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED),
                    "zip=DEF with compression disabled must record exactly one JWE_DECRYPTION_FAILED event");
            LogAsserts.assertSingleLogMessagePresent(TestLogLevel.WARN,
                    JWTValidationLogMessages.WARN.JWE_DECRYPTION_FAILED.format(ex.getMessage()));
        }
    }

    /**
     * Decodes a Base64URL-encoded JWE protected header through the production DSL-JSON path, the
     * same decode {@code NonValidatingJwtParser} performs.
     */
    private JwtHeader parseHeader(String encodedHeader) {
        byte[] json = Base64.getUrlDecoder().decode(encodedHeader);
        JwtHeader header = assertDoesNotThrow(() -> dslJson.deserialize(JwtHeader.class, json, json.length),
                "The JWE header should decode through the production DSL-JSON path");
        assertNotNull(header, "The JWE header should not decode to null");
        return header;
    }
}
