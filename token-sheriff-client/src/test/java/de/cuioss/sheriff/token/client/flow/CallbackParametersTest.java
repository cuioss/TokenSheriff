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
package de.cuioss.sheriff.token.client.flow;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableTestLogger
@EnableGeneratorController
@DisplayName("CallbackParameters redirect query-string parsing")
class CallbackParametersTest {

    private static final char CR = 13;
    private static final char LF = 10;
    private static final char LS = (char) 0x2028;

    /** A single backslash, used to build expected escape-sequence needles without source escapes. */
    private static final String BACKSLASH = String.valueOf((char) 92);

    /** CR, LF and U+2028 as {@code LogSanitizer} renders them. */
    private static final String ESCAPED_BREAKS = BACKSLASH + "r" + BACKSLASH + "n" + BACKSLASH + "u2028";

    @Test
    @DisplayName("Should parse a successful callback query into code, state, and issuer")
    void shouldParseSuccessCallback() {
        CallbackParameters parameters =
                CallbackParameters.parse("code=abc123&state=xyz789&iss=https%3A%2F%2Fissuer.example.com");

        assertAll("parsed success callback",
                () -> assertEquals(Optional.of("abc123"), parameters.getCode(), "the authorization code must be parsed"),
                () -> assertEquals("xyz789", parameters.state(), "the state must be parsed"),
                () -> assertEquals(Optional.of("https://issuer.example.com"), parameters.getIssuer(),
                        "the RFC 9207 iss must be percent-decoded"),
                () -> assertFalse(parameters.hasError(), "a success callback must not signal an error"));
    }

    @Test
    @DisplayName("Should parse an error callback and surface the error signal")
    void shouldParseErrorCallback() {
        CallbackParameters parameters =
                CallbackParameters.parse("error=access_denied&error_description=user%20cancelled");

        assertAll("parsed error callback",
                () -> assertTrue(parameters.hasError(), "an error callback must be recognised"),
                () -> assertEquals("access_denied", parameters.error(), "the error code must be parsed"),
                () -> assertEquals("user cancelled", parameters.errorDescription(),
                        "the error description must be percent-decoded"),
                () -> assertTrue(parameters.getCode().isEmpty(), "an error callback carries no authorization code"));
    }

    @Test
    @DisplayName("Should percent-decode both keys and values and tolerate a valueless parameter")
    void shouldDecodeAndTolerateValuelessPair() {
        CallbackParameters parameters = CallbackParameters.parse("code=a%2Bb&state=&iss");

        assertAll("decoding",
                () -> assertEquals(Optional.of("a+b"), parameters.getCode(), "a percent-encoded value must be decoded"),
                () -> assertEquals("", parameters.state(), "an empty value must be preserved as empty"));
    }

    @Test
    @DisplayName("Should ignore empty segments produced by leading or doubled separators")
    void shouldIgnoreEmptySegments() {
        CallbackParameters parameters = CallbackParameters.parse("&code=only&&");

        assertEquals(Optional.of("only"), parameters.getCode(), "the single real parameter must survive empty segments");
    }

    @Test
    @DisplayName("Should reject a duplicated parameter rather than silently letting the last one win")
    void shouldRejectDuplicateParameter() {
        assertAll("duplicate rejection (RFC 9700 §4.7.3)",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CallbackParameters.parse("state=good&code=abc&state=evil"),
                        "a smuggled second state must be rejected, not override the first"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CallbackParameters.parse("code=abc&code=def"),
                        "a duplicated code must be rejected"));
    }

    @Test
    @DisplayName("Should reject a null query string")
    void shouldRejectNullQuery() {
        assertThrows(NullPointerException.class, () -> CallbackParameters.parse(null));
    }

    @Test
    @DisplayName("Should map an already-decoded parameter map onto the record via of(Map) (L8)")
    void shouldBuildFromDecodedMap() {
        CallbackParameters parameters = CallbackParameters.of(Map.of(
                "code", "abc123",
                "state", "xyz789",
                "iss", "https://issuer.example.com"));

        assertAll("of(Map) mapping",
                () -> assertEquals(Optional.of("abc123"), parameters.getCode(), "code must be taken from the map"),
                () -> assertEquals("xyz789", parameters.state(), "state must be taken from the map"),
                () -> assertEquals(Optional.of("https://issuer.example.com"), parameters.getIssuer(),
                        "iss must be taken from the map"),
                () -> assertFalse(parameters.hasError(), "no error key means no error signal"));
    }

    @Test
    @DisplayName("Should not re-apply the duplicate-parameter defense in of(Map) — a Map has unique keys (L8)")
    void shouldDocumentDuplicateDefenseLossForOfMap() {
        // A Map cannot carry a duplicate key, so of(Map) has no wire-level duplicate to reject: the
        // RFC 9700 §4.7.3 duplicate-parameter defense lives in parse(String), the untrusted-input entry
        // point. of(Map) accepts the already-collapsed map without throwing (documented-loss contract).
        Map<String, String> collapsed = new HashMap<>();
        collapsed.put("state", "good");
        collapsed.put("code", "abc");
        CallbackParameters parameters = assertDoesNotThrow(() -> CallbackParameters.of(collapsed),
                "of(Map) accepts a pre-decoded map without re-checking for duplicates");
        assertEquals("good", parameters.state(), "of(Map) maps the map's state value verbatim");

        // The real defense remains enforced by parse(String): a duplicate on the wire is still rejected.
        assertThrows(IllegalArgumentException.class,
                () -> CallbackParameters.parse("state=good&code=abc&state=evil"),
                "the duplicate-parameter defense remains enforced by parse(String)");
    }

    @Test
    @DisplayName("Should reject a null map in of(Map)")
    void shouldRejectNullMap() {
        assertThrows(NullPointerException.class, () -> CallbackParameters.of(null));
    }

    @Test
    @DisplayName("Should sanitize CR/LF and U+2028 in error, errorDescription and issuer in toString() (C-5)")
    void shouldSanitizeUntrustedFieldsInToString() {
        String breaks = Character.toString(CR) + Character.toString(LF) + Character.toString(LS);
        var parameters = new CallbackParameters(null, null, "access_denied" + breaks + "WARN forged error",
                "denied" + breaks + "WARN forged description",
                "https://issuer.example.com" + breaks + "WARN forged iss");

        String rendered = parameters.toString();

        assertAll("sanitized CallbackParameters rendering",
                () -> assertFalse(rendered.indexOf(CR) >= 0, "no raw carriage return survives"),
                () -> assertFalse(rendered.indexOf(LF) >= 0, "no raw line feed survives"),
                () -> assertFalse(rendered.indexOf(LS) >= 0, "no raw U+2028 line separator survives"),
                () -> assertTrue(rendered.contains("error=access_denied" + ESCAPED_BREAKS + "WARN forged error"),
                        "the error is shown with its line breaks escaped"),
                () -> assertTrue(rendered.contains("errorDescription=denied" + ESCAPED_BREAKS),
                        "the error description is shown with its line breaks escaped"),
                () -> assertTrue(rendered.contains("issuer=https://issuer.example.com" + ESCAPED_BREAKS),
                        "the issuer is shown with its line breaks escaped"));
    }

    @Test
    @DisplayName("Should truncate an over-long errorDescription in toString() (C-5)")
    void shouldTruncateOverlongErrorDescriptionInToString() {
        String overlong = "d".repeat(300);
        var parameters = new CallbackParameters(null, null, "access_denied", overlong, null);

        String rendered = parameters.toString();

        assertAll("bounded CallbackParameters rendering",
                () -> assertTrue(rendered.contains("...[truncated]"), "an over-long value is marked truncated"),
                () -> assertFalse(rendered.contains(overlong), "the full over-long value is not rendered"));
    }
}
