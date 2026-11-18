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

import de.cuioss.sheriff.token.commons.transport.EgressHostValidator.Refusal;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EgressHostValidator bare-host contract")
class EgressHostValidatorTest {

    /**
     * One representative entry per refusal class. The {@link EnumSource} test below draws its cases from
     * {@link Refusal} itself, so a refusal class added without an entry here fails that test.
     */
    private static final Map<Refusal, @Nullable String> REPRESENTATIVE_ENTRIES = representativeEntries();

    private static Map<Refusal, @Nullable String> representativeEntries() {
        Map<Refusal, @Nullable String> entries = new EnumMap<>(Refusal.class);
        entries.put(Refusal.NULL_ENTRY, null);
        entries.put(Refusal.BLANK_ENTRY, "   ");
        entries.put(Refusal.CONTROL_CHARACTER, "key\tcloak");
        entries.put(Refusal.WHITESPACE, "key cloak");
        entries.put(Refusal.NON_ASCII, "kéycloak");
        entries.put(Refusal.URL_SYNTAX, "https://keycloak");
        entries.put(Refusal.PORT, "keycloak:8443");
        entries.put(Refusal.INVALID_IPV6, "[::1");
        entries.put(Refusal.INVALID_HOST, "idp_internal");
        return entries;
    }

    static Stream<Arguments> admittedEntries() {
        return Stream.of(
                Arguments.of("keycloak", "keycloak"),
                Arguments.of("idp.internal", "idp.internal"),
                Arguments.of("Keycloak.Example.COM", "keycloak.example.com"),
                Arguments.of("idp.example.com.", "idp.example.com."),
                Arguments.of("IdP.Example.COM.", "idp.example.com."),
                Arguments.of("idp.1example.com", "idp.1example.com"),
                Arguments.of("1example", "1example"),
                Arguments.of("10.0.0.1", "10.0.0.1"),
                Arguments.of("::1", "[::1]"),
                Arguments.of("2001:db8::1", "[2001:db8::1]"),
                Arguments.of("2001:DB8::1", "[2001:db8::1]"),
                Arguments.of("[::1]", "[::1]"));
    }

    static Stream<Arguments> refusedEntries() {
        return Stream.of(
                Arguments.of("host:8080", Refusal.PORT.text()),
                Arguments.of("[::1]:443", Refusal.PORT.text()),
                Arguments.of("host/path", Refusal.URL_SYNTAX.text()),
                Arguments.of("user@host", Refusal.URL_SYNTAX.text()),
                Arguments.of("host?query", Refusal.URL_SYNTAX.text()),
                Arguments.of("host#fragment", Refusal.URL_SYNTAX.text()),
                Arguments.of("host%20", Refusal.URL_SYNTAX.text()),
                Arguments.of("https://keycloak", Refusal.URL_SYNTAX.text()),
                Arguments.of("key\tcloak", Refusal.CONTROL_CHARACTER.text()),
                Arguments.of("key\rcloak", Refusal.CONTROL_CHARACTER.text()),
                Arguments.of("key\ncloak", Refusal.CONTROL_CHARACTER.text()),
                Arguments.of("key\u0000cloak", Refusal.CONTROL_CHARACTER.text()),
                Arguments.of("key\u007Fcloak", Refusal.CONTROL_CHARACTER.text()),
                Arguments.of("key cloak", Refusal.WHITESPACE.text()),
                Arguments.of("kéycloak", Refusal.NON_ASCII.text()),
                Arguments.of("１０.０.０.１", Refusal.NON_ASCII.text()),
                Arguments.of("[::1", Refusal.INVALID_IPV6.text()),
                Arguments.of("[keycloak]", Refusal.INVALID_IPV6.text()),
                Arguments.of("1:2:3:4:5:6:7:8:9", Refusal.INVALID_IPV6.text()),
                Arguments.of("-keycloak", Refusal.INVALID_HOST.text()),
                Arguments.of("keycloak-", Refusal.INVALID_HOST.text()),
                Arguments.of("idp..internal", Refusal.INVALID_HOST.text()),
                Arguments.of("idp_internal", Refusal.INVALID_HOST.text()),
                Arguments.of("256.0.0.1", Refusal.INVALID_HOST.text()),
                Arguments.of("10.0.0", Refusal.INVALID_HOST.text()),
                Arguments.of("10.0.0.1.", Refusal.INVALID_HOST.text()),
                Arguments.of(".", Refusal.INVALID_HOST.text()),
                Arguments.of("idp.example.com..", Refusal.INVALID_HOST.text()),
                Arguments.of("idp.123", Refusal.INVALID_HOST.text()),
                Arguments.of("idp.1example", Refusal.INVALID_HOST.text()),
                Arguments.of("idp.1example.", Refusal.INVALID_HOST.text()),
                Arguments.of("a".repeat(64) + ".example", Refusal.INVALID_HOST.text()),
                Arguments.of("a.".repeat(127) + "ab", Refusal.INVALID_HOST.text()));
    }

    /**
     * One row per refusal class of the validator, drawn from {@link Refusal} itself and from
     * {@link #REPRESENTATIVE_ENTRIES}, with the fixed text each must produce, followed by boundary rows
     * that reach a class by another route. A refusal class added without a representative entry yields a
     * row whose entry is {@code null}, which the builder tests cannot satisfy. Shared by the builder tests
     * ({@code EgressPolicyTest}, {@code WellKnownConfigTest}, {@code HttpJwksLoaderConfigEgressHostTest}) so
     * every allow-list builder is checked against the same classes.
     */
    static Stream<Arguments> builderRefusalClasses() {
        Stream<Arguments> everyClass = Stream.of(Refusal.values())
                .map(refusal -> Arguments.of(REPRESENTATIVE_ENTRIES.get(refusal), refusal.text()));
        Stream<Arguments> boundaries = Stream.of(
                Arguments.of("", Refusal.BLANK_ENTRY.text()),
                Arguments.of("[::1]:8443", Refusal.PORT.text()),
                Arguments.of("idp.123", Refusal.INVALID_HOST.text()),
                Arguments.of("[fe80::1%eth0]", Refusal.URL_SYNTAX.text()));
        return Stream.concat(everyClass, boundaries);
    }

    /**
     * Asserts that a builder refused an entry with the expected fixed text and that no exception in
     * the cause chain echoes the entry. A {@code null} or empty entry cannot be echoed meaningfully, so
     * only the fixed text is checked for it; a nonempty blank entry is checked like any other.
     */
    static void assertFixedRefusal(IllegalArgumentException exception, @Nullable String entry,
            String expectedRefusal) {
        assertEquals(expectedRefusal, exception.getMessage(), "the refusal must be the validator's fixed text");
        if (entry == null || entry.isEmpty()) {
            return;
        }
        assertFalse(expectedRefusal.contains(entry), "the fixed refusal text must not contain the entry");
        for (Throwable current = exception; current != null; current = current.getCause()) {
            String message = current.getMessage();
            assertFalse(message != null && message.contains(entry),
                    "no exception in the cause chain may echo the entry, but found: " + message);
        }
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
            assertEquals(canonical, URI.create("https://" + canonical + "/token").getHost(),
                    "the canonical form must be the host URI reports, or the entry can never match");
        }

        @Test
        @DisplayName("Should admit a host name of exactly 253 characters")
        void shouldAdmitMaximumLengthHostName() {
            String maximal = "a.".repeat(126) + "a";

            assertEquals(253, maximal.length(), "fixture must sit exactly on the length bound");
            assertEquals(Optional.empty(), EgressHostValidator.refusal(maximal));
        }

        @Test
        @DisplayName("Should keep a terminal dot exactly as URI reports the host")
        void shouldKeepTerminalDotAsUriReportsIt() {
            String host = URI.create("https://idp.example.com./token").getHost();

            assertEquals("idp.example.com.", host, "URI reports a trailing-dot host with its dot");
            assertEquals(host, EgressHostValidator.requireBareHost("idp.example.com."),
                    "the canonical form must match the host URI reports");
        }

        @ParameterizedTest(name = "admits [{0}]")
        @ValueSource(strings = {"idp.example.com", "idp.example.com.", "10.0.0.5", "::1", "[::1]"})
        @DisplayName("Should admit every accepted form the class documentation names")
        void shouldAdmitDocumentedForms(String entry) {
            assertEquals(Optional.empty(), EgressHostValidator.refusal(entry),
                    "a documented accepted form must carry no refusal");
        }
    }

    @Nested
    @DisplayName("Refusal classes")
    class RefusalClasses {

        @ParameterizedTest(name = "refusal class {0}")
        @EnumSource(Refusal.class)
        @DisplayName("Should produce every refusal class from its representative entry")
        void shouldProduceRefusalClassFromRepresentativeEntry(Refusal refusal) {
            assertTrue(REPRESENTATIVE_ENTRIES.containsKey(refusal),
                    "every refusal class needs a representative entry, missing: " + refusal);
            String entry = REPRESENTATIVE_ENTRIES.get(refusal);

            assertEquals(Optional.of(refusal.text()), EgressHostValidator.refusal(entry),
                    "the representative entry must produce exactly its class's fixed text");
        }
    }

    @Nested
    @DisplayName("Refused entries")
    class Refused {

        @Test
        @DisplayName("Should refuse a null entry")
        void shouldRefuseNull() {
            assertEquals(Optional.of(Refusal.NULL_ENTRY.text()), EgressHostValidator.refusal(null));
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> EgressHostValidator.requireBareHost(null));
            assertEquals(Refusal.NULL_ENTRY.text(), exception.getMessage());
        }

        @ParameterizedTest(name = "refuses blank \"{0}\"")
        @ValueSource(strings = {"", " ", "   ", "\t"})
        @DisplayName("Should refuse a blank entry")
        void shouldRefuseBlank(String entry) {
            assertEquals(Optional.of(Refusal.BLANK_ENTRY.text()), EgressHostValidator.refusal(entry));
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> EgressHostValidator.requireBareHost(entry));
            assertEquals(Refusal.BLANK_ENTRY.text(), exception.getMessage());
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

        @ParameterizedTest(name = "URI reports no host for [{0}]")
        @ValueSource(strings = {"idp.123", "idp.1example"})
        @DisplayName("Should refuse a dotted host name URI cannot report because its final label starts with a digit")
        void shouldRefuseHostNameUriReportsNoHostFor(String entry) {
            assertNull(URI.create("https://" + entry + "/token").getHost(),
                    "URI must report no host, so every URL to it is refused before the allow-list lookup");
            assertEquals(Optional.of(Refusal.INVALID_HOST.text()), EgressHostValidator.refusal(entry));
        }

        @Test
        @DisplayName("Should refuse a zoned IPv6 literal by the % rule although URI reports it as the host")
        void shouldRefuseZonedIpv6LiteralThatUriReportsAsHost() throws Exception {
            String host = new URI("http://[fe80::1%eth0]/").getHost();

            assertEquals("[fe80::1%eth0]", host,
                    "URI accepts the zone id and reports it as part of the bracketed host");
            assertEquals(Optional.of(Refusal.URL_SYNTAX.text()), EgressHostValidator.refusal(host),
                    "the entry URI reports as the host is refused by the % rule");
            assertEquals(Optional.of(Refusal.URL_SYNTAX.text()), EgressHostValidator.refusal("fe80::1%eth0"),
                    "the bare zoned literal is refused by the same rule");
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
