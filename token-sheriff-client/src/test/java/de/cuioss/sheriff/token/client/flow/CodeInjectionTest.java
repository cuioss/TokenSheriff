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

import de.cuioss.sheriff.token.commons.error.ClientProtocolException;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Authorization-code-injection defence ({@code CLIENT-2}): an attacker who obtains an authorization
 * code in their own session cannot redeem it inside a victim's flow, because {@link CallbackHandler}
 * binds the code to the exact high-entropy {@code state} of the originating {@link FlowContext} and
 * every flow mints a fresh {@code state}, {@code nonce}, and PKCE verifier.
 * <p>
 * This class holds the two cases that need more than one flow. The single-flow cases — a forged
 * {@code state}, an absent {@code state}, and the genuine {@code state} — are asserted by
 * {@link CallbackHandlerTest}.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("Authorization code-injection fail-closed defence")
class CodeInjectionTest {

    private static final String REDIRECT_URI = "https://rp.example.com/callback";

    private final CallbackHandler handler = new CallbackHandler();

    @Test
    @DisplayName("Should reject an injected code carrying another flow's state (cross-session injection)")
    void shouldRejectCrossSessionInjection() {
        var victimContext = FlowContext.create(REDIRECT_URI);
        var attackerContext = FlowContext.create(REDIRECT_URI);
        var injected = new CallbackParameters(Generators.nonBlankStrings().next(),
                attackerContext.state(), null, null, null);

        assertThrows(ClientProtocolException.class, () -> handler.handle(victimContext, injected),
                "a code bound to the attacker's own flow state cannot be replayed onto the victim's context");
    }

    @Test
    @DisplayName("Should bind every concurrent flow to a distinct state and PKCE verifier so codes are not interchangeable")
    void shouldBindDistinctVerifiersPerFlow() {
        var first = FlowContext.create(REDIRECT_URI);
        var second = FlowContext.create(REDIRECT_URI);

        assertAll("per-flow uniqueness",
                () -> assertNotEquals(first.state(), second.state(),
                        "distinct flows must never share the CSRF state"),
                () -> assertNotEquals(first.pkceChallenge().codeVerifier(), second.pkceChallenge().codeVerifier(),
                        "distinct flows must never share the PKCE verifier"));
    }
}
