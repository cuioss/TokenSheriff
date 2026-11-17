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
package de.cuioss.sheriff.token.commons.transport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EgressHostValidator bare-host contract")
class EgressHostValidatorTest {

    static Stream<Arguments> admittedEntries() {
        return Stream.of(
                Arguments.of("keycloak", "keycloak"),
                Arguments.of("idp.internal", "idp.internal"),
                Arguments.of("Keycloak.Example.COM", "keycloak.example.com"),
                Arguments.of("10.0.0.1", "10.0.0.1"),
                Arguments.of("::1", "[::1]"),
                Arguments.of("2001:db8::1", "[2001:db8::1]"),
                Arguments.of("2001:DB8::1", "[2001:db8::1]"),
                Arguments.of("[::1]", "[::1]"));
    }

    static Stream<Arguments> refusedEntries() {
        return Stream.of(
                Arguments.of("host:8080", EgressHostValidator.PORT),
                Arguments.of("[::1]:443", EgressHostValidator.PORT),
                Arguments.of("host/path", EgressHostValidator.URL_SYNTAX),
                Arguments.of("user@host", EgressHostValidator.URL_SYNTAX),
                Arguments.of("host?query", EgressHostValidator.URL_SYNTAX),
                Arguments.of("host#fragment", EgressHostValidator.URL_SYNTAX),
                Arguments.of("host%20", EgressHostValidator.URL_SYNTAX),
                Arguments.of("https://keycloak", EgressHostValidator.URL_SYNTAX),
                Arguments.of("key\tcloak", EgressHostValidator.CONTROL_CHARACTER),
                Arguments.of("key\rcloak", EgressHostValidator.CONTROL_CHARACTER),
                Arguments.of("key\ncloak", EgressHostValidator.CONTROL_CHARACTER),
                Arguments.of("key\u0000cloak", EgressHostValidator.CONTROL_CHARACTER),
                Arguments.of("key\u007Fcloak", EgressHostValidator.CONTROL_CHARACTER),
                Arguments.of("key cloak", EgressHostValidator.WHITESPACE),
                Arguments.of("kéycloak", EgressHostValidator.NON_ASCII),
                Arguments.of("１０.０.０.１", EgressHostValidator.NON_ASCII),
                Arguments.of("[::1", EgressHostValidator.INVALID_IPV6),
                Arguments.of("[keycloak]", EgressHostValidator.INVALID_IPV6),
                Arguments.of("1:2:3:4:5:6:7:8:9", EgressHostValidator.INVALID_IPV6),
                Arguments.of("-keycloak", EgressHostValidator.INVALID_HOST),
                Arguments.of("keycloak-", EgressHostValidator.INVALID_HOST),
                Arguments.of("idp..internal", EgressHostValidator.INVALID_HOST),
                Arguments.of("idp_internal", EgressHostValidator.INVALID_HOST),
                Arguments.of("256.0.0.1", EgressHostValidator.INVALID_HOST),
                Arguments.of("10.0.0", EgressHostValidator.INVALID_HOST),
                Arguments.of("a".repeat(64) + ".example", EgressHostValidator.INVALID_HOST),
                Arguments.of("a.".repeat(127) + "ab", EgressHostValidator.INVALID_HOST));
    }

    @Nested
    @DisplayName("Admitted entries")
    class Admitted {

        @ParameterizedTest(name = "admits {0} as {1}")
        @MethodSource("de.cuioss.sheriff.token.commons.transport.EgressHostValidatorTest#admittedEntries")
        @DisplayName("Should admit a bare host and return its canonical form")
        void shouldAdmitBareHost(String entry, String canonical) {
            assertEquals(Optional.empty(), EgressHostValidator.refusal(entry),
                    "a bare host must carry no refusal");
            assertEquals(canonical, EgressHostValidator.requireBareHost(entry),
                    "the canonical form is lower-case with an IPv6 literal bracketed");
        }

        @Test
        @DisplayName("Should admit a host name of exactly 253 characters")
        void shouldAdmitMaximumLengthHostName() {
            String maximal = "a.".repeat(126) + "a";

            assertEquals(253, maximal.length(), "fixture must sit exactly on the length bound");
            assertEquals(Optional.empty(), EgressHostValidator.refusal(maximal));
        }
    }

    @Nested
    @DisplayName("Refused entries")
    class Refused {

        @Test
        @DisplayName("Should refuse a null entry")
        void shouldRefuseNull() {
            assertEquals(Optional.of(EgressHostValidator.NULL_ENTRY), EgressHostValidator.refusal(null));
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> EgressHostValidator.requireBareHost(null));
            assertEquals(EgressHostValidator.NULL_ENTRY, exception.getMessage());
        }

        @ParameterizedTest(name = "refuses blank \"{0}\"")
        @ValueSource(strings = {"", " ", "   ", "\t"})
        @DisplayName("Should refuse a blank entry")
        void shouldRefuseBlank(String entry) {
            assertEquals(Optional.of(EgressHostValidator.BLANK_ENTRY), EgressHostValidator.refusal(entry));
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> EgressHostValidator.requireBareHost(entry));
            assertEquals(EgressHostValidator.BLANK_ENTRY, exception.getMessage());
        }

        @ParameterizedTest(name = "refuses [{0}]")
        @MethodSource("de.cuioss.sheriff.token.commons.transport.EgressHostValidatorTest#refusedEntries")
        @DisplayName("Should refuse a malformed entry with its fixed text")
        void shouldRefuseMalformedEntry(String entry, String expectedRefusal) {
            assertEquals(Optional.of(expectedRefusal), EgressHostValidator.refusal(entry));
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> EgressHostValidator.requireBareHost(entry));
            assertEquals(expectedRefusal, exception.getMessage());
            assertNull(exception.getCause(), "the refusal carries no cause that could echo the entry");
        }

        @ParameterizedTest(name = "does not echo [{0}]")
        @MethodSource("de.cuioss.sheriff.token.commons.transport.EgressHostValidatorTest#refusedEntries")
        @DisplayName("Should never echo the refused entry")
        void shouldNotEchoEntry(String entry, String expectedRefusal) {
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> EgressHostValidator.requireBareHost(entry));

            assertFalse(exception.getMessage().contains(entry),
                    "the refusal must not contain the entry, but was: " + exception.getMessage());
            assertFalse(expectedRefusal.contains(entry),
                    "the fixed refusal text must not contain the entry");
        }
    }
}
