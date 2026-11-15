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
package de.cuioss.sheriff.token.commons.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the {@code WWW-Authenticate} value {@link BearerChallenge} builds for an RFC 6750 §3.1
 * {@code insufficient_scope} challenge: the exact output form, scope ordering and de-duplication, the OD-3
 * input rules (scope names outside the RFC 6749 §3.3 {@code scope-token} grammar refused, {@code realm} and
 * {@code error_description} escaped per RFC 9110 §5.6.4 with control characters refused), and the bounds
 * at the boundary and one past it. Every refusal is paired with the nearest accepted input.
 */
@DisplayName("BearerChallenge builds RFC 6750 insufficient_scope challenges")
class BearerChallengeTest {

    private static final String REALM = "protected-resource";

    private static final String DESCRIPTION =
            "The request requires higher privileges than provided by the access token";

    @Nested
    @DisplayName("Output form")
    class OutputForm {

        @Test
        @DisplayName("Reproduces the Quarkus producer's existing challenge byte for byte")
        void shouldReproduceProducerOutput() {
            String expected = "Bearer realm=\"protected-resource\", error=\"insufficient_scope\", "
                    + "error_description=\"The request requires higher privileges than provided by the access token\", "
                    + "scope=\"read write\"";

            String actual = BearerChallenge.insufficientScope()
                    .realm(REALM)
                    .errorDescription(DESCRIPTION)
                    .scopes(List.of("read", "write"))
                    .build();

            assertEquals(expected, actual, "the producer's header is reproduced for scope names inside the grammar");
        }

        @Test
        @DisplayName("The minimal form without realm and description is reachable")
        void shouldBuildMinimalForm() {
            assertEquals("Bearer error=\"insufficient_scope\", scope=\"orders:read\"",
                    BearerChallenge.insufficientScope().scope("orders:read").build(),
                    "no realm and no description are written when none is set");
            assertEquals("Bearer error=\"insufficient_scope\"", BearerChallenge.insufficientScope().build(),
                    "the bare challenge carries the error code only");
        }

        @Test
        @DisplayName("Null realm and description omit their attributes")
        void shouldOmitNullAttributes() {
            assertEquals("Bearer error=\"insufficient_scope\"",
                    BearerChallenge.insufficientScope().realm(null).errorDescription(null).build(),
                    "null means absent");
            assertEquals("Bearer realm=\"api\", error=\"insufficient_scope\"",
                    BearerChallenge.insufficientScope().realm("api").build(),
                    "control: a set realm is written before the error");
        }

        @Test
        @DisplayName("Empty realm and description are written as empty quoted strings")
        void shouldWriteEmptyQuotedStrings() {
            assertEquals("Bearer realm=\"\", error=\"insufficient_scope\", error_description=\"\"",
                    BearerChallenge.insufficientScope().realm("").errorDescription("").build(),
                    "an empty quoted-string is valid RFC 9110 syntax and distinct from absent");
        }
    }

    @Nested
    @DisplayName("Scope set")
    class ScopeSet {

        static Stream<Arguments> scopeSets() {
            return Stream.of(
                    Arguments.of("empty", List.of(), "Bearer error=\"insufficient_scope\""),
                    Arguments.of("single", List.of("read"), "Bearer error=\"insufficient_scope\", scope=\"read\""),
                    Arguments.of("many, sorted", List.of("write", "admin", "read"),
                            "Bearer error=\"insufficient_scope\", scope=\"admin read write\""),
                    Arguments.of("duplicates collapsed", List.of("read", "write", "read", "write"),
                            "Bearer error=\"insufficient_scope\", scope=\"read write\""));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("scopeSets")
        @DisplayName("Scopes are sorted, duplicates collapsed and an empty set omits the attribute")
        void shouldSortAndCollapseScopes(String name, List<String> scopes, String expected) {
            assertEquals(expected, BearerChallenge.insufficientScope().scopes(scopes).build(), name);
        }

        @Test
        @DisplayName("scope and scopes accumulate into one set")
        void shouldAccumulateScopes() {
            assertEquals("Bearer error=\"insufficient_scope\", scope=\"a b c\"",
                    BearerChallenge.insufficientScope().scope("c").scopes(Set.of("b", "a")).scope("a").build(),
                    "every added name lands in one sorted, de-duplicated set");
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {"say\"hi", "back\\slash", "two words", "line\rbreak", "line\nbreak", "tab\tname",
                "café", "", "nul\u0000", "del\u007f"})
        @DisplayName("A scope name outside the scope-token grammar is refused")
        void shouldRefuseScopeOutsideGrammar(String scope) {
            BearerChallenge.Builder builder = BearerChallenge.insufficientScope();

            assertThrows(IllegalArgumentException.class, () -> builder.scope(scope),
                    "OD-3: a scope name outside RFC 6749 section 3.3 is refused, never escaped");
            assertThrows(IllegalArgumentException.class,
                    () -> BearerChallenge.insufficientScope().scopes(List.of("read", scope)),
                    "OD-3: the collection form refuses it as well");
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {"!", "#", "[", "]", "~", "orders:read", "https://api.example.com/read"})
        @DisplayName("Positive control: names at the edges of the scope-token grammar are accepted")
        void shouldAcceptScopeTokenEdges(String scope) {
            assertEquals("Bearer error=\"insufficient_scope\", scope=\"" + scope + "\"",
                    BearerChallenge.insufficientScope().scope(scope).build(),
                    "%x21, %x23-5B and %x5D-7E are scope-token characters and are written unchanged");
        }

        @Test
        @DisplayName("A null scope or scope collection is refused")
        void shouldRefuseNullScope() {
            BearerChallenge.Builder builder = BearerChallenge.insufficientScope();

            assertThrows(NullPointerException.class, () -> builder.scope(null), "a scope name is required");
            assertThrows(NullPointerException.class, () -> builder.scopes(null), "a scope collection is required");
        }
    }

    @Nested
    @DisplayName("Quoted attributes")
    class QuotedAttributes {

        @Test
        @DisplayName("A quote and a backslash in realm and description are escaped")
        void shouldEscapeQuoteAndBackslash() {
            String challenge = BearerChallenge.insufficientScope()
                    .realm("say \"hi\" \\ there")
                    .errorDescription("a\\\"b")
                    .build();

            assertEquals("Bearer realm=\"say \\\"hi\\\" \\\\ there\", error=\"insufficient_scope\", "
                    + "error_description=\"a\\\\\\\"b\"", challenge,
                    "OD-3: each quote and backslash is preceded by a backslash (RFC 9110 section 5.6.4)");
        }

        static Stream<Arguments> refusedControls() {
            return Stream.of(
                    Arguments.of("CR", "\r"),
                    Arguments.of("LF", "\n"),
                    Arguments.of("CRLF injection", "x\r\nSet-Cookie: a=b"),
                    Arguments.of("NUL", "\u0000"),
                    Arguments.of("HTAB", "\t"),
                    Arguments.of("other C0 (US)", "\u001f"),
                    Arguments.of("DEL", "\u007f"));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("refusedControls")
        @DisplayName("A control character in realm or description is refused")
        void shouldRefuseControlCharacters(String name, String control) {
            BearerChallenge.Builder builder = BearerChallenge.insufficientScope();

            assertThrows(IllegalArgumentException.class, () -> builder.realm("a" + control + "b"),
                    "OD-3: " + name + " in the realm could split the header");
            assertThrows(IllegalArgumentException.class, () -> builder.errorDescription("a" + control + "b"),
                    "OD-3: " + name + " in the description could split the header");
            assertEquals("Bearer realm=\"ab\", error=\"insufficient_scope\"",
                    BearerChallenge.insufficientScope().realm("ab").build(),
                    "control: the same value without the character is accepted");
        }

        @Test
        @DisplayName("Non-ASCII text and characters next to the refused ranges pass unchanged")
        void shouldKeepNonAsciiAndPrintable() {
            String description = "Zugriff verweigert: höhere Rechte nötig ~   \u0080";

            assertEquals("Bearer error=\"insufficient_scope\", error_description=\"" + description + "\"",
                    BearerChallenge.insufficientScope().errorDescription(description).build(),
                    "only C0 controls and DEL are refused in a quoted attribute");
        }

        @Test
        @DisplayName("A comma and an equals sign inside a quoted value cannot add a parameter")
        void shouldKeepParameterSyntaxInsideQuotes() {
            assertEquals("Bearer realm=\"a\\\", error=\\\"invalid_token\", error=\"insufficient_scope\"",
                    BearerChallenge.insufficientScope().realm("a\", error=\"invalid_token").build(),
                    "the injected quote is escaped, so the text stays inside the realm value");
        }
    }

    @Nested
    @DisplayName("Bounds")
    class Bounds {

        private static List<String> scopes(int count) {
            List<String> scopes = new ArrayList<>();
            IntStream.range(0, count).forEach(i -> scopes.add("scope%03d".formatted(i)));
            return scopes;
        }

        @Test
        @DisplayName("64 scope tokens are accepted, 65 are refused")
        void shouldBoundScopeTokens() {
            String atLimit = BearerChallenge.insufficientScope().scopes(scopes(BearerChallenge.MAX_SCOPE_TOKENS)).build();
            BearerChallenge.Builder overLimit = BearerChallenge.insufficientScope()
                    .scopes(scopes(BearerChallenge.MAX_SCOPE_TOKENS + 1));

            assertEquals(64, BearerChallenge.MAX_SCOPE_TOKENS, "the bound matches the client parser's");
            assertTrue(atLimit.endsWith("scope063\""), "the 64th token is written");
            assertThrows(IllegalArgumentException.class, overLimit::build, "the 65th distinct token is refused");
        }

        @Test
        @DisplayName("Duplicates do not count against the scope token bound")
        void shouldCountDistinctScopes() {
            List<String> duplicated = new ArrayList<>(scopes(BearerChallenge.MAX_SCOPE_TOKENS));
            duplicated.addAll(scopes(BearerChallenge.MAX_SCOPE_TOKENS));

            assertDoesNotThrow(() -> BearerChallenge.insufficientScope().scopes(duplicated).build(),
                    "128 names collapsing to 64 distinct tokens are within the bound");
        }

        @Test
        @DisplayName("8192 characters are accepted, 8193 are refused")
        void shouldBoundLength() {
            int overhead = BearerChallenge.insufficientScope().errorDescription("").build().length();
            String atLimitDescription = "d".repeat(BearerChallenge.MAX_LENGTH - overhead);

            String atLimit = BearerChallenge.insufficientScope().errorDescription(atLimitDescription).build();
            BearerChallenge.Builder overLimit = BearerChallenge.insufficientScope()
                    .errorDescription(atLimitDescription + "d");

            assertEquals(8192, BearerChallenge.MAX_LENGTH, "the bound matches the client parser's");
            assertEquals(BearerChallenge.MAX_LENGTH, atLimit.length(), "a value of exactly the bound is built");
            assertThrows(IllegalArgumentException.class, overLimit::build, "one character more is refused");
        }

        @Test
        @DisplayName("Escaping counts against the length bound")
        void shouldCountEscapesAgainstLength() {
            int overhead = BearerChallenge.insufficientScope().errorDescription("").build().length();
            int room = BearerChallenge.MAX_LENGTH - overhead;
            BearerChallenge.Builder escaped = BearerChallenge.insufficientScope()
                    .errorDescription("\"".repeat(room / 2 + 1));

            assertThrows(IllegalArgumentException.class, escaped::build,
                    "the bound applies to the written value, after escaping");
            assertDoesNotThrow(() -> BearerChallenge.insufficientScope().errorDescription("\"".repeat(room / 2)).build(),
                    "control: escaped characters that fit are accepted");
        }
    }

    @Nested
    @DisplayName("Public API")
    class PublicApi {

        @Test
        @DisplayName("No public member decides or carries an HTTP status")
        void shouldExposeNoStatusMember() {
            List<String> offending = new ArrayList<>();
            for (Class<?> type : List.of(BearerChallenge.class, BearerChallenge.Builder.class)) {
                for (Method method : type.getDeclaredMethods()) {
                    if (Modifier.isPublic(method.getModifiers()) && (mentionsStatus(method.getName())
                            || method.getReturnType() == int.class)) {
                        offending.add(type.getSimpleName() + "#" + method.getName());
                    }
                }
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isPublic(field.getModifiers()) && mentionsStatus(field.getName())) {
                        offending.add(type.getSimpleName() + "." + field.getName());
                    }
                }
            }

            assertTrue(offending.isEmpty(), "the builder returns a value string only, found: " + offending);
            assertEquals(String.class, assertDoesNotThrow(() -> BearerChallenge.Builder.class.getMethod("build"))
                    .getReturnType(), "build returns a String, never a framework response type");
        }

        private static boolean mentionsStatus(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.contains("status") || lower.contains("code");
        }
    }
}
