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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The three {@link NonValidatingJwtParser} decode modes, applied to a JWE that fails before or
 * during decryption: the normal decode logs one WARN and counts once, a quiet decode only counts,
 * an opaque decode does neither.
 * <ul>
 *   <li>An undecryptable JWE (tampered authentication tag).</li>
 *   <li>A JWE header DSL-JSON cannot bind (here a string-valued {@code epk} followed by raw CR/LF
 *   and forged log text). DSL-JSON's parse-error message quotes the input around the failure
 *   position, so the raw header bytes must stay out of the exception message and the WARN, while
 *   the cause chain is kept.</li>
 *   <li>A JWE header that is the top-level JSON literal {@code null}, or that is not valid
 *   Base64Url, rejected as {@code FAILED_TO_DECODE_JWT} (see token-decryption.adoc, Header Value
 *   Sanitization).</li>
 * </ul>
 */
@EnableTestLogger
@DisplayName("NonValidatingJwtParser JWE decode-mode accounting of decryption and header failures")
class NonValidatingJwtParserJweDecodeModeTest {

    private static final String ISSUER = "https://test-issuer.example.com";

    /** DSL-JSON quotes about 20 bytes either side of the failure position, so keep the marker short. */
    private static final String FORGED_TEXT = "WARN forged";

    /** {@code '*'} lies outside the Base64Url alphabet, so the JDK decoder rejects the part. */
    private static final String INVALID_BASE64URL_HEADER = "eyJ*bGciOiJSU0EtT0FFUCJ9";

    private SecurityEventCounter counter;
    private NonValidatingJwtParser parser;
    private String undecryptableJwe;
    private String malformedHeaderJwe;

    @BeforeEach
    void setUp() {
        KeyPair rsaEncryptionKeyPair = JweTestTokenFactory.generateRsaKeyPair();
        counter = new SecurityEventCounter();
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
        String headerJson = "{\"alg\":\"ECDH-ES\",\"enc\":\"A128GCM\",\"epk\":\"\r\n" + FORGED_TEXT + "\"}";
        malformedHeaderJwe = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8)) + ".AA.AA.AA.AA";
    }

    @Test
    @DisplayName("An undecryptable JWE decoded as an opaque token is neither logged nor counted")
    void undecryptableJweOpaqueDecodeShouldNotLogOrCount() {
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
    void undecryptableJweQuietDecodeShouldCountButNotLog() {
        TokenValidationException ex = assertThrows(TokenValidationException.class,
                () -> parser.decodeQuietly(undecryptableJwe));

        assertEquals(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED, ex.getEventType());
        assertEquals(1, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED),
                "A quiet decode still counts the decryption failure exactly once");
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, JweDecryptor.class);
    }

    @Test
    @DisplayName("An undecryptable JWE on the normal decode path is logged and counted once")
    void undecryptableJweNormalDecodeShouldLogAndCountOnce() {
        TokenValidationException ex = assertThrows(TokenValidationException.class,
                () -> parser.decode(undecryptableJwe));

        assertEquals(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED, ex.getEventType());
        assertEquals(1, counter.getCount(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED),
                "The normal decode path counts the decryption failure exactly once");
        assertNotNull(ex.getCause(), "The decryption failure should carry its cause");
        LogAsserts.assertSingleLogMessagePresent(TestLogLevel.WARN,
                JWTValidationLogMessages.WARN.JWE_DECRYPTION_FAILED.format(ex.getCause().getMessage()));
    }

    @Test
    @DisplayName("A malformed JWE header: the exception message carries no raw header text while the cause chain is kept")
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
    @DisplayName("A malformed JWE header: a normal decode logs one WARN free of the raw header text and counts once")
    void normalDecodeShouldLogSanitizedWarnOnce() {
        assertThrows(TokenValidationException.class, () -> parser.decode(malformedHeaderJwe));

        assertEquals(1, counter.getCount(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT));
        var warnings = TestLoggerFactory.getTestHandler().resolveLogMessages(TestLogLevel.WARN);
        assertEquals(1, warnings.size(), "Exactly one WARN");
        var warning = warnings.getFirst();
        assertTrue(warning.getMessage().contains(
                JWTValidationLogMessages.WARN.FAILED_TO_DECODE_JWT.resolveIdentifierString()), warning.getMessage());
        assertFalse(warning.getMessage().contains(FORGED_TEXT), warning.getMessage());
        assertFalse(warning.getMessage().contains("\r") || warning.getMessage().contains("\n"),
                warning.getMessage());
        assertNull(warning.getThrown(), "The WARN must not attach the DSL-JSON exception quoting raw header text");
    }

    @Test
    @DisplayName("A malformed JWE header: a quiet decode counts once but does not log")
    void malformedHeaderQuietDecodeShouldCountButNotLog() {
        assertThrows(TokenValidationException.class, () -> parser.decodeQuietly(malformedHeaderJwe));

        assertEquals(1, counter.getCount(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT));
        assertTrue(TestLoggerFactory.getTestHandler().resolveLogMessages(TestLogLevel.WARN).isEmpty());
    }

    @Test
    @DisplayName("A malformed JWE header: an opaque decode neither logs nor counts")
    void malformedHeaderOpaqueDecodeShouldNotLogOrCount() {
        assertThrows(TokenValidationException.class, () -> parser.decodeOpaqueToken(malformedHeaderJwe));

        assertEquals(0, counter.getCount(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT));
        assertTrue(TestLoggerFactory.getTestHandler().resolveLogMessages(TestLogLevel.WARN).isEmpty());
    }

    static Stream<Arguments> undecodableJweHeaders() {
        String nullHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("null".getBytes(StandardCharsets.UTF_8));
        return Stream.of(
                Arguments.of("JSON null header", nullHeader + ".AA.AA.AA.AA", false),
                Arguments.of("invalid Base64Url header", INVALID_BASE64URL_HEADER + ".AA.AA.AA.AA", true));
    }

    private static void assertUndecodableHeaderRejection(TokenValidationException ex, String site,
            boolean decoderCause) {
        assertEquals(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT, ex.getEventType(), site);
        if (decoderCause) {
            assertInstanceOf(IllegalArgumentException.class, ex.getCause(),
                    "Non-vacuity: the header must be rejected by the Base64Url decoder: " + site);
            assertFalse(ex.getMessage().contains(INVALID_BASE64URL_HEADER), ex.getMessage());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("undecodableJweHeaders")
    @DisplayName("An undecodable JWE header: a normal decode logs one WARN without a throwable and counts once")
    void undecodableHeaderNormalDecodeShouldLogAndCountOnce(String site, String token, boolean decoderCause) {
        TokenValidationException ex = assertThrows(TokenValidationException.class, () -> parser.decode(token));

        assertUndecodableHeaderRejection(ex, site, decoderCause);
        assertEquals(1, counter.getCount(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT),
                "Counted exactly once: " + site);
        var warnings = TestLoggerFactory.getTestHandler().resolveLogMessages(TestLogLevel.WARN);
        assertEquals(1, warnings.size(), "Exactly one WARN: " + site);
        var warning = warnings.getFirst();
        assertTrue(warning.getMessage().contains(
                JWTValidationLogMessages.WARN.FAILED_TO_DECODE_JWT.resolveIdentifierString()), warning.getMessage());
        assertNull(warning.getThrown(), "The WARN must not attach the decoder exception: " + site);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("undecodableJweHeaders")
    @DisplayName("An undecodable JWE header: a quiet decode counts once but does not log")
    void undecodableHeaderQuietDecodeShouldCountButNotLog(String site, String token, boolean decoderCause) {
        TokenValidationException ex = assertThrows(TokenValidationException.class,
                () -> parser.decodeQuietly(token));

        assertUndecodableHeaderRejection(ex, site, decoderCause);
        assertEquals(1, counter.getCount(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT),
                "Counted exactly once: " + site);
        assertTrue(TestLoggerFactory.getTestHandler().resolveLogMessages(TestLogLevel.WARN).isEmpty(),
                "No WARN may be written: " + site);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("undecodableJweHeaders")
    @DisplayName("An undecodable JWE header: an opaque decode neither logs nor counts")
    void undecodableHeaderOpaqueDecodeShouldNotLogOrCount(String site, String token, boolean decoderCause) {
        TokenValidationException ex = assertThrows(TokenValidationException.class,
                () -> parser.decodeOpaqueToken(token));

        assertUndecodableHeaderRejection(ex, site, decoderCause);
        assertEquals(0, counter.getCount(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT),
                "Not counted: " + site);
        assertTrue(TestLoggerFactory.getTestHandler().resolveLogMessages(TestLogLevel.WARN).isEmpty(),
                "No WARN may be written: " + site);
    }
}
