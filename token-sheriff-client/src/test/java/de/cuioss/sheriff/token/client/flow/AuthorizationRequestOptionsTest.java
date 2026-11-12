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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the per-call {@link AuthorizationRequestOptions} and the closed-set wire-value parsing of
 * {@link ResponseMode} and {@link Prompt}.
 */
@EnableGeneratorController
@DisplayName("AuthorizationRequestOptions and its closed-set values")
class AuthorizationRequestOptionsTest {

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

            assertThrows(NullPointerException.class, optionsBuilder::build,
                    "the response mode is never null");
        }
    }

    static Stream<List<String>> invalidScopeOverrides() {
        return Stream.of(
                Arrays.asList("openid", null),
                List.of("openid", " "),
                List.of(""),
                List.of("openid profile"),
                List.of("openid\tprofile"),
                List.of("openid\r\nprofile"),
                List.of());
    }

    @Nested
    @DisplayName("Scope override")
    class ScopeOverride {

        @ParameterizedTest
        @MethodSource("de.cuioss.sheriff.token.client.flow.AuthorizationRequestOptionsTest#invalidScopeOverrides")
        @DisplayName("Should refuse a null, blank, multi-token or empty scope override")
        void shouldRefuseInvalidOverride(List<String> scopes) {
            var optionsBuilder = AuthorizationRequestOptions.builder().scopes(scopes);

            assertThrows(IllegalArgumentException.class, optionsBuilder::build,
                    "an invalid scope override never reaches a request: " + scopes);
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
        @ValueSource(strings = {" ", "fragment", "query.jwt", "jwt", "FORM_POST", "Form_Post", "QUERY", "Query",
                " query", "query ", "query\r\n"})
        @DisplayName("Should refuse an unsupported, blank or case-mismatched value")
        void shouldRefuseUnsupportedValue(String value) {
            assertThrows(IllegalArgumentException.class, () -> ResponseMode.of(value),
                    "response_mode refuses " + value);
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
        @ValueSource(strings = {" ", "create", "login consent", "NONE", "Login", "SELECT_ACCOUNT", "select-account",
                " login", "login ", "login\n"})
        @DisplayName("Should refuse an unsupported, blank or case-mismatched value")
        void shouldRefuseUnsupportedValue(String value) {
            assertThrows(IllegalArgumentException.class, () -> Prompt.of(value), "prompt refuses " + value);
        }
    }
}
