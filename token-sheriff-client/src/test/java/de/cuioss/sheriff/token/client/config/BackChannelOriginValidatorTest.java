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
package de.cuioss.sheriff.token.client.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests pinning the {@link BackChannelOriginValidator} contract: which values are bare
 * {@code http}/{@code https} origins, the fixed refusal text of every other value, and that no
 * refusal echoes the input or a credential it carries.
 */
@DisplayName("BackChannelOriginValidator")
class BackChannelOriginValidatorTest {

    private static final String USER = "operator";
    private static final String SECRET = "s3cr3t-pa55";

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "http://keycloak:8080",
            "https://idp.example.com",
            "https://idp.example.com:8443",
            "https://idp.example.com/",
            "HTTPS://IDP.EXAMPLE.COM",
            "https://[::1]:8443",
            "http://[2001:db8::1]/"
    })
    @DisplayName("Should accept a bare http(s) origin")
    void shouldAcceptBareOrigin(String origin) {
        assertEquals(Optional.empty(), BackChannelOriginValidator.refusal(origin),
                "a bare http(s) origin must be accepted");
    }

    static Stream<Arguments> refusedOrigins() {
        return Stream.of(
                Arguments.of("credential-bearing user-info", "https://" + USER + ":" + SECRET + "@idp.example.com",
                        BackChannelOriginValidator.USER_INFO),
                Arguments.of("query", "https://idp.example.com?realm=demo", BackChannelOriginValidator.QUERY),
                Arguments.of("query after a trailing slash", "https://idp.example.com/?realm=demo",
                        BackChannelOriginValidator.QUERY),
                Arguments.of("fragment", "https://idp.example.com#section", BackChannelOriginValidator.FRAGMENT),
                Arguments.of("path", "https://idp.example.com/realms/demo", BackChannelOriginValidator.PATH),
                Arguments.of("double slash path", "https://idp.example.com//", BackChannelOriginValidator.PATH),
                Arguments.of("ftp scheme", "ftp://idp.example.com", BackChannelOriginValidator.NOT_HTTP_ORIGIN),
                Arguments.of("relative host", "idp.example.com", BackChannelOriginValidator.NOT_HTTP_ORIGIN),
                Arguments.of("relative path", "/realms/demo", BackChannelOriginValidator.NOT_HTTP_ORIGIN),
                Arguments.of("scheme without host", "https:///realms", BackChannelOriginValidator.NOT_HTTP_ORIGIN),
                Arguments.of("Unicode host", "https://idp.exämple.com", BackChannelOriginValidator.NOT_HTTP_ORIGIN),
                Arguments.of("CR/LF header injection", "https://idp.example.com\r\nX-Injected: 1",
                        BackChannelOriginValidator.UNPARSABLE_URL),
                Arguments.of("trailing LF", "https://idp.example.com\n", BackChannelOriginValidator.UNPARSABLE_URL),
                Arguments.of("NUL character", "https://idp.example.com\u0000", BackChannelOriginValidator.UNPARSABLE_URL),
                Arguments.of("space in the host", "https://idp example.com", BackChannelOriginValidator.UNPARSABLE_URL),
                Arguments.of("credential in an unparsable value", "https://" + USER + ":" + SECRET + "@idp example.com",
                        BackChannelOriginValidator.UNPARSABLE_URL),
                Arguments.of("empty", "", BackChannelOriginValidator.BLANK_ORIGIN),
                Arguments.of("blank", "  \t ", BackChannelOriginValidator.BLANK_ORIGIN));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedOrigins")
    @DisplayName("Should refuse a value that is not a bare http(s) origin with its fixed text")
    void shouldRefuseWithFixedText(String scenario, String origin, String expectedRefusal) {
        Optional<String> refusal = BackChannelOriginValidator.refusal(origin);

        assertEquals(Optional.of(expectedRefusal), refusal,
                "the %s must be refused with its fixed text".formatted(scenario));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedOrigins")
    @DisplayName("Should never echo the refused value or a credential it carries")
    void shouldNotEchoInputOrCredential(String scenario, String origin, String expectedRefusal) {
        String refusal = BackChannelOriginValidator.refusal(origin).orElseThrow();

        assertAll(scenario,
                () -> assertFalse(!origin.isBlank() && refusal.contains(origin),
                        "the refusal must not echo the input"),
                () -> assertFalse(refusal.contains(SECRET), "the refusal must not carry the password"),
                () -> assertFalse(refusal.contains(USER), "the refusal must not carry the user name"),
                () -> assertFalse(refusal.contains("idp."), "the refusal must not carry the host"));
    }

    @Test
    @DisplayName("Should refuse null with its fixed text")
    void shouldRefuseNull() {
        assertEquals(Optional.of(BackChannelOriginValidator.NULL_ORIGIN), BackChannelOriginValidator.refusal(null),
                "null must be refused with its fixed text");
    }

    @Test
    @DisplayName("Should carry neither the parser reason nor an index in the unparsable-URL refusal")
    void shouldNotCarryParserDetail() {
        String refusal = BackChannelOriginValidator.refusal("https://idp example.com").orElseThrow();

        assertAll("parser detail",
                () -> assertFalse(refusal.contains("index"), "the refusal must not carry the error index"),
                () -> assertFalse(refusal.contains("Illegal character"), "the refusal must not carry the parser reason"),
                () -> assertFalse(refusal.contains("("), "the refusal must stay the bare fixed text"));
    }
}
