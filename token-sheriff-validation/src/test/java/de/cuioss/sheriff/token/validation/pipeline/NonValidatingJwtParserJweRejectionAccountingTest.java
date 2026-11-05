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
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.JWTValidationLogMessages;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig;
import de.cuioss.sheriff.token.validation.test.JweTestTokenFactory;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.TestLoggerFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.logging.LogRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The three {@link NonValidatingJwtParser} decode modes, applied to every {@code JweDecryptor}
 * rejection site and to the parser's own rejections of a decrypted inner token: the normal decode
 * logs one WARN and counts once, a quiet decode only counts, an opaque decode does neither.
 * <ul>
 *   <li>Decryptor rejection sites: unsupported algorithm, key not found, unsupported compression,
 *   an ECDH-ES structural rejection, a malformed {@code epk} coordinate, a non-EC {@code epk}
 *   {@code kty}.</li>
 *   <li>Inner-token rejections: an oversized inner JWS, an inner JWS with a wrong part count or an
 *   empty signature, a nested JWE, and a 5-part token without {@code enc}.</li>
 * </ul>
 */
@EnableTestLogger
@DisplayName("NonValidatingJwtParser JWE decode-mode accounting of rejection sites")
class NonValidatingJwtParserJweRejectionAccountingTest {

    private static final String KNOWN_KID = "known-kid";
    private static final String EC_EPK = "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"AQ\",\"y\":\"AQ\"}";

    /**
     * The marker every forged header value in {@link #rejectionSites()} carries after its raw CR/LF;
     * neither the exception message nor any log line may contain it.
     */
    private static final String FORGED_MARKER = "WARN forged";

    private static final KeyPair RECIPIENT = JweTestTokenFactory.generateRsaKeyPair();
    private static final ParserConfig SMALL_TOKEN_SIZE = ParserConfig.builder().maxTokenSize(4096).build();

    private SecurityEventCounter counter;
    private NonValidatingJwtParser parser;

    @BeforeEach
    void setUp() {
        counter = new SecurityEventCounter();
        parser = NonValidatingJwtParser.builder()
                .securityEventCounter(counter)
                .jweDecryptionConfig(JweDecryptionConfig.builder()
                        .decryptionKey(KNOWN_KID, JweTestTokenFactory.generateRsaKeyPair().getPrivate())
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
                                + "\"x\":\"AQ\\r\\n" + FORGED_MARKER + " coordinate\",\"y\":\"AQ\"}}", ""),
                        SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED,
                        JWTValidationLogMessages.WARN.JWE_DECRYPTION_FAILED, ""),
                Arguments.of("non-EC epk kty",
                        token("{\"alg\":\"ECDH-ES\",\"enc\":\"A128GCM\",\"kid\":\"" + KNOWN_KID
                                + "\",\"epk\":{\"kty\":\"RSA\\r\\n" + FORGED_MARKER + " kty\",\"crv\":\"P-256\","
                                + "\"x\":\"AQ\",\"y\":\"AQ\"}}", ""),
                        SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED,
                        JWTValidationLogMessages.WARN.JWE_DECRYPTION_FAILED,
                        "epk 'kty' must be 'EC'"));
    }

    /**
     * Asserts the rejection carries the row's guard-specific message fragment (an empty fragment
     * pins nothing beyond the event type) and none of the forged header text.
     */
    private static void assertGuardMessage(TokenValidationException ex, String expectedMessageFragment,
            String site) {
        if (!expectedMessageFragment.isEmpty()) {
            assertTrue(ex.getMessage().contains(expectedMessageFragment),
                    "Rejection for " + site + " must come from its guard, but was: " + ex.getMessage());
        }
        assertFalse(ex.getMessage().contains(FORGED_MARKER),
                "No raw header text may reach the exception message for " + site + ": " + ex.getMessage());
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
                        .resolveLogMessagesContaining(TestLogLevel.WARN, FORGED_MARKER).isEmpty(),
                "No raw header text may reach the log: " + site);
    }

    private static void assertNoWarnings(String site) {
        assertTrue(TestLoggerFactory.getTestHandler().resolveLogMessages(TestLogLevel.WARN).isEmpty(),
                "No WARN may be written for: " + site);
    }

    static Stream<Arguments> innerRejections() {
        String noEncHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        return Stream.of(
                // zip=DEF keeps the compact JWE under the token-size limit while the inner JWS exceeds it
                Arguments.of("oversized inner JWS",
                        JweTestTokenFactory.createCompressedJweFromPlaintext(
                                "a".repeat(5000).getBytes(StandardCharsets.UTF_8),
                                RECIPIENT.getPublic(), "RSA-OAEP", "A256GCM"),
                        SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED,
                        JWTValidationLogMessages.WARN.TOKEN_SIZE_EXCEEDED),
                Arguments.of("inner JWS with 2 parts", encrypt("aa.bb"),
                        SecurityEventCounter.EventType.INVALID_JWT_FORMAT,
                        JWTValidationLogMessages.WARN.INVALID_JWT_FORMAT),
                Arguments.of("inner JWS with 4 parts", encrypt("aa.bb.cc.dd"),
                        SecurityEventCounter.EventType.INVALID_JWT_FORMAT,
                        JWTValidationLogMessages.WARN.INVALID_JWT_FORMAT),
                // Decodable header and payload, so only the empty-signature guard rejects it
                Arguments.of("inner JWS with empty signature",
                        encrypt(noEncHeader + "." + Base64.getUrlEncoder().withoutPadding()
                                .encodeToString("{}".getBytes(StandardCharsets.UTF_8)) + "."),
                        SecurityEventCounter.EventType.INVALID_JWT_FORMAT,
                        JWTValidationLogMessages.WARN.INVALID_JWT_FORMAT),
                Arguments.of("nested JWE", encrypt("aa.bb.cc.dd.ee"),
                        SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED,
                        JWTValidationLogMessages.WARN.JWE_NESTED_NOT_ALLOWED),
                Arguments.of("5-part token without enc", noEncHeader + ".b.c.d.e",
                        SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED,
                        JWTValidationLogMessages.WARN.JWE_DECRYPTION_FAILED));
    }

    static Stream<Arguments> modeMatrix() {
        return NonValidatingJwtParserTest.ParserDecodeMode.crossProduct(innerRejections());
    }

    private static String encrypt(String innerToken) {
        return JweTestTokenFactory.encryptAsJwe(innerToken, RECIPIENT.getPublic(), "RSA-OAEP", "A256GCM", null);
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("modeMatrix")
    @DisplayName("An inner-token rejection is logged and counted according to the decode mode")
    void shouldAccountPerDecodeMode(NonValidatingJwtParserTest.ParserDecodeMode mode, String site, String token,
            SecurityEventCounter.EventType eventType, LogRecord warning) {
        NonValidatingJwtParser innerTokenParser = NonValidatingJwtParser.builder()
                .securityEventCounter(counter)
                .config(SMALL_TOKEN_SIZE)
                .jweDecryptionConfig(JweDecryptionConfig.builder()
                        .defaultDecryptionKey(RECIPIENT.getPrivate())
                        .compressionEnabled(true)
                        .build())
                .build();

        TokenValidationException ex = assertThrows(TokenValidationException.class,
                () -> mode.decode(innerTokenParser, token));

        mode.assertAccounted(ex, counter, eventType, warning, site);
    }
}
