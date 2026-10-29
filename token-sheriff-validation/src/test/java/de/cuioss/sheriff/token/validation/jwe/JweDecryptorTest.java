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

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@DisplayName("JweDecryptor Tests")
class JweDecryptorTest {

    /**
     * The recipient key of every test in which the key itself is not the subject, generated once for
     * the class. {@code shouldFailWithWrongKey} generates its own, differing key.
     */
    private static final KeyPair RSA_ENCRYPTION_KEY_PAIR = JweTestTokenFactory.generateRsaKeyPair();

    /**
     * A second RSA key pair whose public key wraps a CEK the recipient key cannot unwrap.
     */
    private static final KeyPair FOREIGN_RSA_KEY_PAIR = JweTestTokenFactory.generateRsaKeyPair();

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
    @DisplayName("RSA-OAEP Algorithm Tests")
    class RsaOaepTests {

        @ParameterizedTest
        @CsvSource({
                "RSA-OAEP, A128GCM",
                "RSA-OAEP, A256GCM",
                "RSA-OAEP, A128CBC-HS256",
                "RSA-OAEP, A256CBC-HS512",
                "RSA-OAEP-256, A128GCM",
                "RSA-OAEP-256, A256GCM",
                "RSA-OAEP-256, A128CBC-HS256",
                "RSA-OAEP-256, A256CBC-HS512"
        })
        @DisplayName("Should decrypt JWE with RSA-OAEP variants")
        void shouldDecryptRsaOaep(String alg, String enc) {
            String jwe = JweTestTokenFactory.createJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    RSA_ENCRYPTION_KEY_PAIR.getPublic(),
                    "RS256", alg, enc, ISSUER, "test-kid");

            String[] parts = jwe.split("\\.");
            assertEquals(5, parts.length);

            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .decryptionKey("test-kid", RSA_ENCRYPTION_KEY_PAIR.getPrivate())
                    .defaultDecryptionKey(RSA_ENCRYPTION_KEY_PAIR.getPrivate())
                    .build();

            JwtHeader header = parseHeader(parts[0]);
            assertTrue(header.isJwe(), "Decoded header should carry enc");

            String innerJws = decryptor.decrypt(parts, header, config, counter);

            assertNotNull(innerJws);
            assertEquals(3, innerJws.split("\\.").length, "Decrypted content should be a 3-part JWS");
            assertEquals(0, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED));
        }
    }

    @Nested
    @DisplayName("Uniform Content-Authentication Failure Tests")
    class UniformFailureTests {

        @ParameterizedTest
        @CsvSource({
                "RSA-OAEP, A128GCM",
                "RSA-OAEP, A256GCM",
                "RSA-OAEP, A128CBC-HS256",
                "RSA-OAEP, A256CBC-HS512",
                "RSA-OAEP-256, A128GCM",
                "RSA-OAEP-256, A256GCM",
                "RSA-OAEP-256, A128CBC-HS256",
                "RSA-OAEP-256, A256CBC-HS512"
        })
        @DisplayName("A bad CEK unwrap and a bad authentication tag should fail identically")
        void badUnwrapAndBadTagShouldFailIdentically(String alg, String enc) {
            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(RSA_ENCRYPTION_KEY_PAIR.getPrivate())
                    .build();

            String[] badUnwrapParts = JweTestTokenFactory.createJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    FOREIGN_RSA_KEY_PAIR.getPublic(),
                    "RS256", alg, enc, ISSUER, null).split("\\.");
            String[] badTagParts = JweTestTokenFactory.createJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    RSA_ENCRYPTION_KEY_PAIR.getPublic(),
                    "RS256", alg, enc, ISSUER, null).split("\\.");
            badTagParts[4] = flipLastBit(badTagParts[4]);

            JwtHeader badUnwrapHeader = parseHeader(badUnwrapParts[0]);
            TokenValidationException unwrapFailure = assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(badUnwrapParts, badUnwrapHeader, config, counter));
            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED),
                    "A bad unwrap should record exactly one JWE_DECRYPTION_FAILED event");
            assertNotNull(unwrapFailure.getCause(), "A bad unwrap should carry the authentication failure");
            String detail = unwrapFailure.getCause().getMessage();
            String expectedLog = JWTValidationLogMessages.WARN.JWE_DECRYPTION_FAILED.format(detail);
            LogAsserts.assertSingleLogMessagePresent(TestLogLevel.WARN, expectedLog);

            TestLoggerFactory.getTestHandler().clearRecords();

            JwtHeader badTagHeader = parseHeader(badTagParts[0]);
            TokenValidationException tagFailure = assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(badTagParts, badTagHeader, config, counter));
            assertEquals(2, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED),
                    "A bad tag should record exactly one further JWE_DECRYPTION_FAILED event");
            LogAsserts.assertSingleLogMessagePresent(TestLogLevel.WARN, expectedLog);

            assertAll("Uniform failure for " + alg + " / " + enc,
                    () -> assertEquals(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED,
                            unwrapFailure.getEventType()),
                    () -> assertEquals(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED,
                            tagFailure.getEventType()),
                    () -> assertEquals(unwrapFailure.getMessage(), tagFailure.getMessage()),
                    () -> assertEquals(unwrapFailure.getCause().getClass(), tagFailure.getCause().getClass()),
                    () -> assertEquals(detail, tagFailure.getCause().getMessage()));
        }

        private static String flipLastBit(String base64UrlPart) {
            byte[] bytes = Base64.getUrlDecoder().decode(base64UrlPart);
            bytes[bytes.length - 1] ^= 0x01;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
    }

    @Nested
    @DisplayName("Security Tests")
    class SecurityTests {

        @Test
        @DisplayName("Should reject RSA1_5 algorithm")
        void shouldRejectRsa15() {
            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(RSA_ENCRYPTION_KEY_PAIR.getPrivate())
                    .build();

            JwtHeader header = new JwtHeader("RSA1_5", null, null, null, null,
                    "A128GCM", null, null, null, null);

            String[] parts = {"header", "encKey", "iv", "ciphertext", "authTag"};

            assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(parts, header, config, counter));
            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.JWE_UNSUPPORTED_ALGORITHM));
        }

        @Test
        @DisplayName("Should reject unsupported enc algorithm")
        void shouldRejectUnsupportedEnc() {
            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(RSA_ENCRYPTION_KEY_PAIR.getPrivate())
                    .build();

            JwtHeader header = new JwtHeader("RSA-OAEP", null, null, null, null,
                    "A192GCM", null, null, null, null);

            String[] parts = {"header", "encKey", "iv", "ciphertext", "authTag"};

            assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(parts, header, config, counter));
            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.JWE_UNSUPPORTED_ALGORITHM));
        }

        @Test
        @DisplayName("Should fail with wrong decryption key")
        void shouldFailWithWrongKey() {
            KeyPair wrongKeyPair = JweTestTokenFactory.generateRsaKeyPair();

            String jwe = JweTestTokenFactory.createJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    RSA_ENCRYPTION_KEY_PAIR.getPublic(),
                    "RS256", "RSA-OAEP", "A256GCM", ISSUER, null);

            String[] parts = jwe.split("\\.");
            JwtHeader header = parseHeader(parts[0]);

            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(wrongKeyPair.getPrivate())
                    .build();

            assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(parts, header, config, counter));
        }

        @Test
        @DisplayName("Should fail with tampered auth tag")
        void shouldFailWithTamperedAuthTag() {
            String jwe = JweTestTokenFactory.createJweWithTamperedAuthTag(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    RSA_ENCRYPTION_KEY_PAIR.getPublic(),
                    ISSUER);

            String[] parts = jwe.split("\\.");
            JwtHeader header = parseHeader(parts[0]);

            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(RSA_ENCRYPTION_KEY_PAIR.getPrivate())
                    .build();

            assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(parts, header, config, counter));
        }

        @Test
        @DisplayName("Should fail with tampered ciphertext")
        void shouldFailWithTamperedCiphertext() {
            String jwe = JweTestTokenFactory.createJweWithTamperedCiphertext(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    RSA_ENCRYPTION_KEY_PAIR.getPublic(),
                    ISSUER);

            String[] parts = jwe.split("\\.");
            JwtHeader header = parseHeader(parts[0]);

            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(RSA_ENCRYPTION_KEY_PAIR.getPrivate())
                    .build();

            assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(parts, header, config, counter));
        }

        @Test
        @DisplayName("Should fail when no key matches kid")
        void shouldFailWhenNoKeyMatchesKid() {
            String jwe = JweTestTokenFactory.createJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    RSA_ENCRYPTION_KEY_PAIR.getPublic(),
                    "RS256", "RSA-OAEP", "A256GCM", ISSUER, "unknown-kid");

            String[] parts = jwe.split("\\.");
            JwtHeader header = parseHeader(parts[0]);

            // Config with different kid, no default
            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .decryptionKey("other-kid", RSA_ENCRYPTION_KEY_PAIR.getPrivate())
                    .build();

            assertThrows(TokenValidationException.class,
                    () -> decryptor.decrypt(parts, header, config, counter));
            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_KEY_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("Key Resolution Tests")
    class KeyResolutionTests {

        @Test
        @DisplayName("Should resolve key by kid")
        void shouldResolveKeyByKid() {
            String jwe = JweTestTokenFactory.createJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    RSA_ENCRYPTION_KEY_PAIR.getPublic(),
                    "RS256", "RSA-OAEP", "A256GCM", ISSUER, "my-kid");

            String[] parts = jwe.split("\\.");
            JwtHeader header = parseHeader(parts[0]);

            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .decryptionKey("my-kid", RSA_ENCRYPTION_KEY_PAIR.getPrivate())
                    .build();

            String innerJws = decryptor.decrypt(parts, header, config, counter);
            assertNotNull(innerJws);
            assertEquals(3, innerJws.split("\\.").length);
        }

        @Test
        @DisplayName("Should fall back to default key when kid not found")
        void shouldFallBackToDefaultKey() {
            String jwe = JweTestTokenFactory.createJweWrappedAccessToken(
                    InMemoryKeyMaterialHandler.getDefaultPrivateKey(),
                    RSA_ENCRYPTION_KEY_PAIR.getPublic(),
                    "RS256", "RSA-OAEP", "A256GCM", ISSUER, "unknown-kid");

            String[] parts = jwe.split("\\.");
            JwtHeader header = parseHeader(parts[0]);

            JweDecryptionConfig config = JweDecryptionConfig.builder()
                    .defaultDecryptionKey(RSA_ENCRYPTION_KEY_PAIR.getPrivate())
                    .build();

            String innerJws = decryptor.decrypt(parts, header, config, counter);
            assertNotNull(innerJws);
        }
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

            String innerJws = decryptor.decrypt(parts, header, config, counter);
            assertNotNull(innerJws);
            assertEquals(3, innerJws.split("\\.").length);
            assertEquals(0, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED));
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
            // Move the embedded ephemeral point off the curve by mutating the first character of its
            // x coordinate. Curve parameters still match the recipient key, so only the independent
            // on-curve check can catch this — exactly the provider-independent guard under test.
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            int keyIdx = headerJson.indexOf("\"x\"");
            assertTrue(keyIdx >= 0, "The ECDH-ES header must embed an epk x coordinate");
            int valueStart = headerJson.indexOf('"', headerJson.indexOf(':', keyIdx) + 1) + 1;
            char original = headerJson.charAt(valueStart);
            char replacement = original == 'A' ? 'B' : 'A';
            String tamperedJson = headerJson.substring(0, valueStart) + replacement
                    + headerJson.substring(valueStart + 1);
            parts[0] = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(tamperedJson.getBytes(StandardCharsets.UTF_8));

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
