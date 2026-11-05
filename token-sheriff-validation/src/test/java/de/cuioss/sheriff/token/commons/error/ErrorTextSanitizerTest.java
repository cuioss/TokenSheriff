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
package de.cuioss.sheriff.token.commons.error;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.JwkKey;
import de.cuioss.sheriff.token.commons.transport.TransportLogMessages;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.json.JwtHeader;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptor;
import de.cuioss.sheriff.token.validation.test.JweTestTokenFactory;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.TestLoggerFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the single shared sanitize behaviour once, character by character: every escaped code point
 * with its exact rendering, the neighbours just outside the escaped ranges, null handling, and the
 * truncation boundary — for the default escaping form and for the replacing variant, which the JWE
 * header and JWK field sanitisers share.
 */
@EnableTestLogger
@DisplayName("ErrorTextSanitizer pins the shared CWE-117 escaping and truncation contract")
class ErrorTextSanitizerTest {

    /** A single backslash, used to build expected escape sequences without source escapes. */
    private static final String BACKSLASH = String.valueOf((char) 92);

    static Stream<Arguments> escapedCharacters() {
        return Stream.of(
                Arguments.of((char) 0x000D, "r"),
                Arguments.of((char) 0x000A, "n"),
                Arguments.of((char) 0x0009, "t"),
                Arguments.of((char) 0x0000, "u0000"),
                Arguments.of((char) 0x0007, "u0007"),
                Arguments.of((char) 0x001F, "u001f"),
                Arguments.of((char) 0x007F, "u007f"),
                Arguments.of((char) 0x0080, "u0080"),
                Arguments.of((char) 0x0085, "u0085"),
                Arguments.of((char) 0x009F, "u009f"),
                Arguments.of((char) 0x2028, "u2028"),
                Arguments.of((char) 0x2029, "u2029"));
    }

    @ParameterizedTest(name = "renders as escape {1}")
    @MethodSource("escapedCharacters")
    @DisplayName("Should render every escaped character as its exact escape sequence")
    void shouldRenderExactEscape(char escaped, String escapeBody) {
        assertEquals("left" + BACKSLASH + escapeBody + "right", ErrorTextSanitizer.sanitize("left" + escaped + "right"),
                "the character is replaced by exactly its documented escape, surrounding text untouched");
    }

    static Stream<Character> keptCharacters() {
        return Stream.of((char) 0x0020, (char) 0x007E, (char) 0x00A0, (char) 0x2027, (char) 0x202A, 'z');
    }

    @ParameterizedTest
    @MethodSource("keptCharacters")
    @DisplayName("Should keep the characters just outside the escaped ranges verbatim")
    void shouldKeepBoundaryNeighbours(char kept) {
        String single = String.valueOf(kept);

        assertEquals(single, ErrorTextSanitizer.sanitize(single), "a character outside the escaped ranges is not escaped");
    }

    @Test
    @DisplayName("Should map null to null")
    void shouldMapNullToNull() {
        assertNull(ErrorTextSanitizer.sanitize(null), "null stays null, never an empty string");
    }

    @Test
    @DisplayName("Should keep a value of exactly MAX_LENGTH characters whole and truncate one character more")
    void shouldTruncateExactlyAboveTheBoundary() {
        String atLimit = "a".repeat(ErrorTextSanitizer.MAX_LENGTH);
        String overLimit = atLimit + "b";

        assertAll("truncation boundary",
                () -> assertEquals(atLimit, ErrorTextSanitizer.sanitize(atLimit),
                        "a value of exactly MAX_LENGTH characters is not truncated"),
                () -> assertEquals(atLimit + "...[truncated]", ErrorTextSanitizer.sanitize(overLimit),
                        "one character more is cut to MAX_LENGTH and carries the marker"),
                () -> assertEquals("...[truncated]", ErrorTextSanitizer.TRUNCATION_MARKER,
                        "the marker text is unchanged"));
    }

    @Test
    @DisplayName("Should truncate on source characters before escaping")
    void shouldTruncateBeforeEscaping() {
        String lineFeeds = String.valueOf((char) 0x000A).repeat(ErrorTextSanitizer.MAX_LENGTH + 1);

        assertEquals((BACKSLASH + "n").repeat(ErrorTextSanitizer.MAX_LENGTH) + ErrorTextSanitizer.TRUNCATION_MARKER,
                ErrorTextSanitizer.sanitize(lineFeeds),
                "the cap counts source characters, so escaping can lengthen the retained prefix");
    }

    @ParameterizedTest(name = "replaces escape {1}")
    @MethodSource("escapedCharacters")
    @DisplayName("The replacing variant should replace every character the default form escapes")
    void replacingVariantShouldReplaceEveryEscapedCharacter(char unsafe, String escapeBody) {
        assertEquals("left?right", ErrorTextSanitizer.sanitize("left" + unsafe + "right", '?', 64, "..."),
                "the character the default form renders as escape " + escapeBody + " becomes the replacement");
    }

    @ParameterizedTest
    @MethodSource("keptCharacters")
    @DisplayName("The replacing variant should keep the characters just outside the unsafe ranges verbatim")
    void replacingVariantShouldKeepBoundaryNeighbours(char kept) {
        String single = String.valueOf(kept);

        assertEquals(single, ErrorTextSanitizer.sanitize(single, '?', 64, "..."));
    }

    @Test
    @DisplayName("The replacing variant should keep exactly maxLength characters whole and truncate one more")
    void replacingVariantShouldTruncateExactlyAboveTheBoundary() {
        String atLimit = "a".repeat(64);

        assertAll("variant truncation boundary",
                () -> assertEquals(atLimit, ErrorTextSanitizer.sanitize(atLimit, '?', 64, "...")),
                () -> assertEquals(atLimit + "...", ErrorTextSanitizer.sanitize(atLimit + "b", '?', 64, "...")),
                () -> assertEquals("?".repeat(64) + "...",
                        ErrorTextSanitizer.sanitize(String.valueOf((char) 0x000A).repeat(65), '?', 64, "..."),
                        "the cap counts source characters; a replacement never lengthens the value"));
    }

    /**
     * The JWE header and the JWK field sanitisers both route through the replacing variant, so the
     * same hostile header text renders identically at both sites. Each site is reached through its
     * public behaviour: the JWE {@code kid} through the key-not-found rejection message, the JWK
     * coordinate through the invalid-Base64URL WARN.
     */
    @Test
    @DisplayName("The JWE header and JWK field sanitisers should render the same hostile text identically")
    void jweHeaderAndJwkFieldShouldRenderHostileTextIdentically() {
        String hostile = "evil" + (char) 0x000D + (char) 0x000A + (char) 0x2028 + (char) 0x2029 + (char) 0x0085
                + "WARN forged " + "c".repeat(100);
        String expected = ("evil?????WARN forged " + "c".repeat(100)).substring(0, 64) + "...";
        assertEquals(expected, ErrorTextSanitizer.sanitize(hostile, '?', 64, "..."));

        JwtHeader header = new JwtHeader("RSA-OAEP", null, hostile, null, null,
                "A256GCM", null, null, null, null);
        JweDecryptionConfig config = JweDecryptionConfig.builder()
                .decryptionKey("other-kid", JweTestTokenFactory.generateRsaKeyPair().getPrivate())
                .build();
        String[] parts = {"header", "encKey", "iv", "ciphertext", "authTag"};
        TokenValidationException rejection = assertThrows(TokenValidationException.class,
                () -> new JweDecryptor().decrypt(parts, header, config, new SecurityEventCounter()));
        assertEquals("No decryption key found for kid: " + expected, rejection.getMessage(),
                "the JWE kid is rendered by the shared variant");

        TestLoggerFactory.getTestHandler().clearRecords();
        assertTrue(new JwkKey("EC", null, null, null, null, "P-256", hostile, "AQ")
                .getXCoordinateAsBigInteger().isEmpty(), "the hostile coordinate is not valid Base64URL");
        LogAsserts.assertSingleLogMessagePresent(TestLogLevel.WARN,
                TransportLogMessages.WARN.INVALID_BASE64_URL_ENCODING.format(expected));
    }
}
