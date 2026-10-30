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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InboundErrorNormalizer (COMMONS-11)")
class InboundErrorNormalizerTest {

    /** The BEL control character (0x07), used to prove non-CR/LF control chars are also escaped. */
    private static final char BELL = 0x07;

    /** The Unicode line separators U+0085 (NEL), U+2028 (LS) and U+2029 (PS). */
    private static final char NEL = 0x0085;
    private static final char LS = 0x2028;
    private static final char PS = 0x2029;

    @ParameterizedTest
    @ValueSource(strings = {"invalid_request", "invalid_client", "invalid_grant", "unauthorized_client",
            "unsupported_grant_type", "unsupported_response_type", "access_denied", "invalid_scope",
            "server_error", "temporarily_unavailable"})
    @DisplayName("Should recognize each registered RFC 6749 error code")
    void shouldRecognizeRegisteredErrorCodes(String code) {
        ClientProtocolException exception = InboundErrorNormalizer.normalize(code, null);

        assertAll("recognized error code",
                () -> assertTrue(exception.getMessage().contains(code), "the recognized code is named"),
                () -> assertFalse(exception.getMessage().contains("unrecognized"),
                        "a registered code must not be reported as unrecognized"));
    }

    @Test
    @DisplayName("Should report an unregistered error code as unrecognized")
    void shouldReportUnknownErrorCodeAsUnrecognized() {
        ClientProtocolException exception = InboundErrorNormalizer.normalize("totally_made_up", null);

        assertAll("unrecognized error code",
                () -> assertTrue(exception.getMessage().contains("unrecognized"),
                        "an unregistered code is flagged as unrecognized"),
                () -> assertTrue(exception.getMessage().contains("totally_made_up"),
                        "the (sanitized) offending code is still surfaced for diagnostics"));
    }

    @Test
    @DisplayName("Should report a null error code as a missing error code")
    void shouldReportNullErrorCode() {
        ClientProtocolException exception = InboundErrorNormalizer.normalize(null, "some description");

        assertTrue(exception.getMessage().contains("without an error code"),
                "a null error code produces the missing-code message");
    }

    @Test
    @DisplayName("Should omit a null or blank error_description from the message")
    void shouldOmitBlankDescription() {
        String withNull = InboundErrorNormalizer.normalize("access_denied", null).getMessage();
        String withBlank = InboundErrorNormalizer.normalize("access_denied", "   ").getMessage();

        assertAll("blank description omitted",
                () -> assertFalse(withNull.contains("("), "a null description adds no parenthetical clause"),
                () -> assertFalse(withBlank.contains("("), "a blank description adds no parenthetical clause"));
    }

    @Test
    @DisplayName("Should include a present error_description in the message")
    void shouldIncludePresentDescription() {
        ClientProtocolException exception =
                InboundErrorNormalizer.normalize("access_denied", "the resource owner denied the request");

        assertTrue(exception.getMessage().contains("the resource owner denied the request"),
                "a non-blank description is surfaced alongside the error code");
    }

    @Test
    @DisplayName("Should sanitize CR/LF in both the error code and the error_description (CWE-117)")
    void shouldSanitizeCrlfInBothFields() {
        ClientProtocolException exception = InboundErrorNormalizer.normalize(
                "access_denied\r\nWARN forged-code-line",
                "denied\r\nWARN forged-description-line");

        String message = exception.getMessage();
        assertAll("sanitized fields",
                () -> assertFalse(message.indexOf('\r') >= 0, "no raw CR survives from either field"),
                () -> assertFalse(message.indexOf('\n') >= 0, "no raw LF survives from either field"),
                () -> assertTrue(message.contains("\\r\\n"),
                        "the embedded CR/LF is escaped, preserving the investigative content"));
    }

    @Test
    @DisplayName("Should escape other ASCII control characters in the description")
    void shouldEscapeControlCharacters() {
        ClientProtocolException exception =
                InboundErrorNormalizer.normalize("access_denied", "denied" + BELL + "bell");

        String message = exception.getMessage();
        assertAll("escaped control character",
                () -> assertFalse(message.indexOf(BELL) >= 0, "the raw control character does not survive"),
                () -> assertTrue(message.contains("\\u0007"), "the control character is rendered as an escape"));
    }

    @Test
    @DisplayName("Should escape the Unicode line separators NEL, LS and PS in both fields (C-8a)")
    void shouldEscapeUnicodeLineSeparatorsInBothFields() {
        String separators = Character.toString(NEL) + Character.toString(LS) + Character.toString(PS);
        ClientProtocolException exception = InboundErrorNormalizer.normalize(
                "access_denied" + separators + "WARN forged-code-line",
                "denied" + separators + "WARN forged-description-line");

        String message = exception.getMessage();
        assertAll("escaped Unicode line separators",
                () -> assertFalse(message.indexOf(NEL) >= 0, "no raw NEL (U+0085) survives from either field"),
                () -> assertFalse(message.indexOf(LS) >= 0, "no raw LS (U+2028) survives from either field"),
                () -> assertFalse(message.indexOf(PS) >= 0, "no raw PS (U+2029) survives from either field"),
                () -> assertTrue(message.contains("access_denied\\u0085\\u2028\\u2029WARN forged-code-line"),
                        "the separators in the error code are rendered as escapes"),
                () -> assertTrue(message.contains("denied\\u0085\\u2028\\u2029WARN forged-description-line"),
                        "the separators in the error_description are rendered as escapes"));
    }

    @Test
    @DisplayName("Should treat a separator-only field as absent although the separators are escaped")
    void shouldTreatSeparatorOnlyFieldAsAbsent() {
        String separatorsOnly = Character.toString(LS) + " " + Character.toString(PS);

        String separatorOnlyDescription =
                InboundErrorNormalizer.normalize("access_denied", separatorsOnly).getMessage();
        String separatorOnlyCode = InboundErrorNormalizer.normalize(separatorsOnly, null).getMessage();

        assertAll("separator-only fields carry no content",
                () -> assertFalse(separatorOnlyDescription.contains("("),
                        "a separator-only error_description adds no parenthetical clause"),
                () -> assertTrue(separatorOnlyCode.contains("without an error code"),
                        "a separator-only error code is reported as missing"),
                () -> assertFalse(separatorOnlyCode.contains("unrecognized"),
                        "a separator-only error code is not reported as unrecognized"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\r", "\n", "\t", "\r\n\t", "\t \n"})
    @DisplayName("Should treat a control-character-only value as content, not as blank, in both fields")
    void shouldTreatControlOnlyFieldAsContent(String controlsOnly) {
        String descriptionMessage = InboundErrorNormalizer.normalize("access_denied", controlsOnly).getMessage();
        String codeMessage = InboundErrorNormalizer.normalize(controlsOnly, null).getMessage();

        assertAll("control-only fields",
                () -> assertTrue(descriptionMessage.endsWith("(" + escaped(controlsOnly) + ")"),
                        "a control-only description is reported as an escaped parenthetical clause: "
                                + descriptionMessage),
                () -> assertFalse(descriptionMessage.indexOf('\r') >= 0 || descriptionMessage.indexOf('\n') >= 0
                        || descriptionMessage.indexOf('\t') >= 0, "no raw control character survives"),
                () -> assertFalse(codeMessage.contains("without an error code"),
                        "a control-only code is not reported as missing"),
                () -> assertTrue(codeMessage.endsWith("unrecognized error callback: " + escaped(controlsOnly)),
                        "a control-only code is reported, escaped, as unrecognized: " + codeMessage));
    }

    @ParameterizedTest
    @ValueSource(chars = {(char) 0x007E, (char) 0x00A0, (char) 0x2027, (char) 0x202A})
    @DisplayName("Should pass the characters just outside the escaped ranges through unescaped in both fields")
    void shouldPassRangeBoundaryNeighboursThrough(char neighbour) {
        String message = normalizeWithCharInBothFields(neighbour);

        assertAll("range-boundary neighbour passed through",
                () -> assertTrue(message.contains("access_denied" + neighbour + "code"),
                        "the character in the error code is kept verbatim"),
                () -> assertTrue(message.contains("denied" + neighbour + "description"),
                        "the character in the error_description is kept verbatim"),
                () -> assertFalse(message.contains(unicodeEscape(neighbour)),
                        "the character is not rendered as an escape"));
    }

    @ParameterizedTest
    @ValueSource(chars = {(char) 0x007F, (char) 0x0080, (char) 0x009B, (char) 0x009D, (char) 0x009F,
            (char) 0x2028, (char) 0x2029})
    @DisplayName("Should escape C1 controls and the characters at the edges of the escaped ranges in both fields")
    void shouldEscapeRangeEdgeAndC1Characters(char escapedChar) {
        String expectedEscape = unicodeEscape(escapedChar);

        String message = normalizeWithCharInBothFields(escapedChar);

        assertAll("escaped character",
                () -> assertFalse(message.indexOf(escapedChar) >= 0, "no raw character survives from either field"),
                () -> assertTrue(message.contains("access_denied" + expectedEscape + "code"),
                        "the character in the error code is rendered as an escape"),
                () -> assertTrue(message.contains("denied" + expectedEscape + "description"),
                        "the character in the error_description is rendered as an escape"));
    }

    @Test
    @DisplayName("Should truncate an over-long field to bound log flooding")
    void shouldTruncateOverlongField() {
        String overlong = "x".repeat(300);
        ClientProtocolException exception = InboundErrorNormalizer.normalize(overlong, null);

        String message = exception.getMessage();
        assertAll("bounded field",
                () -> assertNotNull(message),
                () -> assertTrue(message.contains("...[truncated]"), "an over-long value is marked truncated"),
                () -> assertFalse(message.contains(overlong), "the full over-long value is not carried"));
    }

    /**
     * @param controls a value made only of CR, LF, tab and spaces
     * @return the rendering the normalizer documents for it: CR, LF and tab as backslash escapes, spaces
     *         verbatim
     */
    private static String escaped(String controls) {
        return controls.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }

    /**
     * @param character the character to embed
     * @return the normalized message for an error code and an error_description that both carry the character
     */
    private static String normalizeWithCharInBothFields(char character) {
        return InboundErrorNormalizer.normalize("access_denied" + character + "code",
                "denied" + character + "description").getMessage();
    }

    /**
     * @param character the character to render
     * @return the four-hex-digit backslash-u escape the normalizer documents for it
     */
    private static String unicodeEscape(char character) {
        return String.format("\\u%04x", (int) character);
    }
}
