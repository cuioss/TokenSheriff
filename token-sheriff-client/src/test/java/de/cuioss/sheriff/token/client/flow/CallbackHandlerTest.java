/*
 * Copyright © 2022 CUI-OpenSource-Software (info@cuioss.de)
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
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnableTestLogger
@EnableGeneratorController
@DisplayName("CallbackHandler authorization_code redirect validation (RFC 6749 §4.1.2)")
class CallbackHandlerTest {

    private final CallbackHandler handler = new CallbackHandler();

    private static FlowContext context() {
        return FlowContext.create("https://rp.example.com/callback");
    }

    private static CallbackParameters success(String code, String state) {
        return new CallbackParameters(code, state, null, null, null);
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("Should return the authorization code when the state echoes the flow context")
        void shouldReturnCodeOnMatchingState() {
            var context = context();
            String code = Generators.nonBlankStrings().next();
            var parameters = success(code, context.state());

            String result = handler.handle(context, parameters);

            assertEquals(code, result, "the validated authorization code is returned for redemption");
        }
    }

    @Nested
    @DisplayName("Fail-closed")
    class FailClosed {

        @Test
        @DisplayName("Should reject an error callback before any code is trusted")
        void shouldRejectErrorCallback() {
            var context = context();
            var parameters = new CallbackParameters(null, context.state(), "access_denied", "user said no", null);

            assertThrows(IllegalStateException.class, () -> handler.handle(context, parameters));
        }

        @Test
        @DisplayName("Should reject an error callback even when a code is smuggled alongside it")
        void shouldRejectErrorCallbackWithCode() {
            var context = context();
            var parameters = new CallbackParameters(Generators.nonBlankStrings().next(),
                    context.state(), "invalid_request", null, null);

            assertThrows(IllegalStateException.class, () -> handler.handle(context, parameters));
        }

        @Test
        @DisplayName("Should reject a callback whose state does not match the flow context (CSRF defence)")
        void shouldRejectStateMismatch() {
            var context = context();
            var parameters = success(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());

            assertThrows(IllegalStateException.class, () -> handler.handle(context, parameters));
        }

        @Test
        @DisplayName("Should reject a callback with an absent state")
        void shouldRejectAbsentState() {
            var context = context();
            var parameters = success(Generators.nonBlankStrings().next(), null);

            assertThrows(IllegalStateException.class, () -> handler.handle(context, parameters));
        }

        @Test
        @DisplayName("Should reject a state that differs only in length from the expected value")
        void shouldRejectLengthMismatch() {
            var context = context();
            var parameters = success(Generators.nonBlankStrings().next(), context.state() + "x");

            assertThrows(IllegalStateException.class, () -> handler.handle(context, parameters));
        }

        @Test
        @DisplayName("Should reject a matching-state callback that carries no code")
        void shouldRejectMissingCode() {
            var context = context();
            var parameters = success(null, context.state());

            assertThrows(IllegalStateException.class, () -> handler.handle(context, parameters));
        }

        @Test
        @DisplayName("Should reject a matching-state callback whose code is blank")
        void shouldRejectBlankCode() {
            var context = context();
            var parameters = success("   ", context.state());

            assertThrows(IllegalStateException.class, () -> handler.handle(context, parameters));
        }

        @Test
        @DisplayName("Should reject null arguments")
        void shouldRejectNullArguments() {
            var context = context();
            var parameters = success(Generators.nonBlankStrings().next(), context.state());

            assertAll("null-guards",
                    () -> assertThrows(NullPointerException.class, () -> handler.handle(null, parameters)),
                    () -> assertThrows(NullPointerException.class, () -> handler.handle(context, null)));
        }
    }
}
