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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the per-call {@link AuthorizationRequestOptions} and the closed-set wire-value parsing of
 * {@link ResponseMode} and {@link Prompt}.
 */
@EnableGeneratorController
@DisplayName("AuthorizationRequestOptions and its closed-set values")
class AuthorizationRequestOptionsTest {

    /** The message prefix every scope-token rejection keeps. */
    private static final String SCOPE_TOKEN_PREFIX = "scopes must hold one scope-token per entry (RFC 6749 section 3.3)";

    /** The message a {@code null} or blank entry is refused with. */
    private static final String NULL_OR_BLANK = "scopes must not contain null or blank entries";

    /** The message an empty override is refused with. */
    private static final String EMPTY_OVERRIDE =
            "a scope override must not be empty; omit it to request the configured scopes";

    private static String tokenRefusal(int entry, String characterClass, String codePoint) {
        return SCOPE_TOKEN_PREFIX + ", but entry " + entry + " holds a " + characterClass + " (U+" + codePoint + ")";
    }

    @Nested
    @DisplayName("Defaults")
    class Defaults {

        @Test
        @DisplayName("Should request the configured scopes, form_post and no prompt by default")
        void shouldHaveNoOptionSet() {
            var defaults = AuthorizationRequestOptions.DEFAULTS;

            assertAll("defaults",
                    () -> assertNull(defaults.getScopes(), "no scope override"),
                    () -> assertEquals(ResponseMode.FORM_POST, defaults.getResponseMode(), "form_post"),
                    () -> assertNull(defaults.getPrompt(), "no prompt"));
        }

        @Test
        @DisplayName("Should equal an options instance built with nothing set")
        void shouldEqualEmptyBuilder() {
            assertEquals(AuthorizationRequestOptions.DEFAULTS, AuthorizationRequestOptions.builder().build(),
                    "the builder's defaults are the DEFAULTS constant");
        }

        @Test
        @DisplayName("Should refuse a null response mode")
        void shouldRefuseNullResponseMode() {
            var optionsBuilder = AuthorizationRequestOptions.builder().responseMode(null);

            var exception = assertThrows(NullPointerException.class, optionsBuilder::build,
                    "the response mode is never null");

            assertEquals("responseMode must not be null", exception.getMessage());
        }
    }

    /**
     * Each scope override the shared scope-token rule refuses, paired with the exact message the options
     * builder refuses it with: a null, blank, multi-token or empty list, and entries holding a character
     * outside the RFC 6749 §3.3 scope-token set {@code %x21 / %x23-5B / %x5D-7E}.
     */
    static Stream<Arguments> invalidScopeOverridesWithMessage() {
        return Stream.of(
                Arguments.of(Arrays.asList("openid", null), NULL_OR_BLANK),
                Arguments.of(List.of("openid", " "), NULL_OR_BLANK),
                Arguments.of(List.of(""), NULL_OR_BLANK),
                Arguments.of(List.of("openid profile"), tokenRefusal(0, "whitespace character", "0020")),
                Arguments.of(List.of("openid\tprofile"), tokenRefusal(0, "whitespace character", "0009")),
                Arguments.of(List.of("openid\r\nprofile"), tokenRefusal(0, "whitespace character", "000D")),
                Arguments.of(List.of(), EMPTY_OVERRIDE),
                Arguments.of(List.of("openid", "say\"hello"), tokenRefusal(1, "double quote", "0022")),
                Arguments.of(List.of("openid", "back\\slash"), tokenRefusal(1, "backslash", "005C")),
                Arguments.of(List.of("openid", "nul\u0000byte"), tokenRefusal(1, "control character", "0000")),
                Arguments.of(List.of("openid", "del\u007Fbyte"), tokenRefusal(1, "control character", "007F")),
                Arguments.of(List.of("openid", "ctl\u0001byte"), tokenRefusal(1, "control character", "0001")),
                Arguments.of(List.of("openid", "caf" + Character.toString(0x00E9)),
                        tokenRefusal(1, "non-ASCII character", "00E9")),
                Arguments.of(List.of("openid", "smile" + Character.toString(0x1F600)),
                        tokenRefusal(1, "non-ASCII character", "1F600")),
                Arguments.of(List.of("openid", "non" + Character.toString(0x00A0) + "breaking"),
                        tokenRefusal(1, "whitespace character", "00A0")));
    }

    /**
     * The scope overrides of {@link #invalidScopeOverridesWithMessage()} without their messages. Shared
     * with {@code RefreshFlowScopeOverrideTest}, so both entry points are exercised against the same
     * variants.
     */
    @SuppressWarnings("unchecked")
    static Stream<List<String>> invalidScopeOverrides() {
        return invalidScopeOverridesWithMessage().map(arguments -> (List<String>) arguments.get()[0]);
    }

    @Nested
    @DisplayName("Scope override")
    class ScopeOverride {

        // The display name carries the index only: the variants hold control characters that must not
        // reach a test report.
        @ParameterizedTest(name = "refuses variant #{index}")
        @MethodSource("de.cuioss.sheriff.token.client.flow.AuthorizationRequestOptionsTest#invalidScopeOverridesWithMessage")
        @DisplayName("Should refuse a null, blank, multi-token, empty or non-scope-token-character scope override")
        void shouldRefuseInvalidOverride(List<String> scopes, String expectedMessage) {
            var optionsBuilder = AuthorizationRequestOptions.builder().scopes(scopes);

            var exception = assertThrows(IllegalArgumentException.class, optionsBuilder::build,
                    "an invalid scope override never reaches a request");

            assertEquals(expectedMessage, exception.getMessage());
        }

        @Test
        @DisplayName("Should keep the scopes in request order")
        void shouldKeepScopesInOrder() {
            List<String> scopes = List.of(Generators.letterStrings(3, 10).next(), "openid",
                    Generators.letterStrings(3, 10).next());

            var options = AuthorizationRequestOptions.builder().scopes(scopes).build();

            assertEquals(scopes, options.getScopes(), "the override keeps every scope in order");
        }

        @Test
        @DisplayName("Should copy the input collection defensively")
        void shouldCopyDefensively() {
            List<String> input = new ArrayList<>(List.of("openid", "profile"));
            var options = AuthorizationRequestOptions.builder().scopes(input).build();

            input.add("admin");
            input.set(0, "email");

            assertEquals(List.of("openid", "profile"), options.getScopes(),
                    "a later change to the caller's list does not reach the options");
        }

        @Test
        @DisplayName("Should expose an unmodifiable scope list")
        void shouldExposeUnmodifiableScopes() {
            var options = AuthorizationRequestOptions.builder().scopes(List.of("openid")).build();
            List<String> scopes = options.getScopes();

            assertThrows(UnsupportedOperationException.class, () -> scopes.add("admin"),
                    "the scope list cannot be changed through the getter");
        }
    }

    @Nested
    @DisplayName("ResponseMode.of")
    class ResponseModeOf {

        @ParameterizedTest
        @EnumSource(ResponseMode.class)
        @DisplayName("Should map every supported wire value to its constant")
        void shouldMapSupportedValue(ResponseMode mode) {
            assertSame(mode, ResponseMode.of(mode.wireValue()), "round trip of " + mode.wireValue());
        }

        @Test
        @DisplayName("Should carry the exact wire values")
        void shouldCarryExactWireValues() {
            assertAll("wire values",
                    () -> assertEquals("form_post", ResponseMode.FORM_POST.wireValue()),
                    () -> assertEquals("query", ResponseMode.QUERY.wireValue()));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = " ")
        @DisplayName("Should refuse a null or blank value, naming the parameter")
        void shouldRefuseBlankValue(String value) {
            var exception = assertThrows(IllegalArgumentException.class, () -> ResponseMode.of(value),
                    "response_mode refuses a blank value");

            assertEquals("response_mode must not be null or blank", exception.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"fragment", "query.jwt", "jwt", "FORM_POST", "Form_Post", "QUERY", "Query",
                " query", "query "})
        @DisplayName("Should refuse an unsupported or case-mismatched value, naming it")
        void shouldRefuseUnsupportedValue(String value) {
            var exception = assertThrows(IllegalArgumentException.class, () -> ResponseMode.of(value),
                    "response_mode refuses " + value);

            assertEquals("unsupported response_mode value: " + value, exception.getMessage());
        }

        @Test
        @DisplayName("Should refuse a value carrying CR LF and render it escaped in the message")
        void shouldRefuseControlCharacterValue() {
            var exception = assertThrows(IllegalArgumentException.class, () -> ResponseMode.of("query\r\n"));

            assertEquals("unsupported response_mode value: query\\r\\n", exception.getMessage(),
                    "the refused value is log-sanitized, so it cannot forge a log line");
        }
    }

    @Nested
    @DisplayName("Prompt.of")
    class PromptOf {

        @ParameterizedTest
        @EnumSource(Prompt.class)
        @DisplayName("Should map every supported wire value to its constant")
        void shouldMapSupportedValue(Prompt prompt) {
            assertSame(prompt, Prompt.of(prompt.wireValue()), "round trip of " + prompt.wireValue());
        }

        @Test
        @DisplayName("Should carry the exact wire values")
        void shouldCarryExactWireValues() {
            assertAll("wire values",
                    () -> assertEquals("none", Prompt.NONE.wireValue()),
                    () -> assertEquals("login", Prompt.LOGIN.wireValue()),
                    () -> assertEquals("consent", Prompt.CONSENT.wireValue()),
                    () -> assertEquals("select_account", Prompt.SELECT_ACCOUNT.wireValue()));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = " ")
        @DisplayName("Should refuse a null or blank value, naming the parameter")
        void shouldRefuseBlankValue(String value) {
            var exception = assertThrows(IllegalArgumentException.class, () -> Prompt.of(value),
                    "prompt refuses a blank value");

            assertEquals("prompt must not be null or blank", exception.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"create", "login consent", "NONE", "Login", "SELECT_ACCOUNT", "select-account",
                " login", "login "})
        @DisplayName("Should refuse an unsupported or case-mismatched value, naming it")
        void shouldRefuseUnsupportedValue(String value) {
            var exception = assertThrows(IllegalArgumentException.class, () -> Prompt.of(value),
                    "prompt refuses " + value);

            assertEquals("unsupported prompt value: " + value, exception.getMessage());
        }

        @Test
        @DisplayName("Should refuse a value carrying LF and render it escaped in the message")
        void shouldRefuseControlCharacterValue() {
            var exception = assertThrows(IllegalArgumentException.class, () -> Prompt.of("login\n"));

            assertEquals("unsupported prompt value: login\\n", exception.getMessage(),
                    "the refused value is log-sanitized, so it cannot forge a log line");
        }
    }
}
