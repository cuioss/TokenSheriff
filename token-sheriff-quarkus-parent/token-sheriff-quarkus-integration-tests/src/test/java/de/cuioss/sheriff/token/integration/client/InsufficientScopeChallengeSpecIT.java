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
package de.cuioss.sheriff.token.integration.client;

import de.cuioss.sheriff.token.client.flow.InsufficientScopeChallengeParser;
import de.cuioss.sheriff.token.client.flow.InsufficientScopeChallengeParser.InsufficientScopeChallenge;
import de.cuioss.sheriff.token.integration.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RFC 6750 §3.1 {@code insufficient_scope} challenge parsing ({@code CLIENT-25}) from the consumer
 * side.
 * <p>
 * This spec consumes the public {@link InsufficientScopeChallengeParser} from outside
 * {@code token-sheriff-client}, through the packaged artifact, exactly as an application module would:
 * it uses only the public parser and the accessors of its public record. It drives no Keycloak flow,
 * because no authorization-server flow emits an {@code insufficient_scope} challenge — the challenge
 * comes from a resource server, and the parser is a stateless string function. The full contract and
 * the attack matrix are pinned by the unit tests in {@code token-sheriff-client}.
 */
@DisplayName("RFC 6750 insufficient_scope challenge parsing through the public API")
class InsufficientScopeChallengeSpecIT extends BaseIntegrationTest {

    private final InsufficientScopeChallengeParser parser = new InsufficientScopeChallengeParser();

    @Test
    @DisplayName("Should parse an RFC 6750 §3.1 insufficient_scope challenge into scopes, realm and description")
    void shouldParseInsufficientScopeChallenge() {
        String wwwAuthenticate = "Bearer realm=\"example\", error=\"insufficient_scope\", "
                + "error_description=\"The request requires higher privileges\", scope=\"read write\"";

        Optional<InsufficientScopeChallenge> challenge = parser.parse(wwwAuthenticate);

        assertTrue(challenge.isPresent(), "the public parser must recognise an RFC 6750 insufficient_scope challenge");
        assertAll("parsed challenge",
                () -> assertEquals(List.of("read", "write"), List.copyOf(challenge.get().scopes()),
                        "scopes in the order the resource server named them"),
                () -> assertEquals(Optional.of("example"), challenge.get().getRealm()),
                () -> assertEquals(Optional.of("The request requires higher privileges"),
                        challenge.get().getErrorDescription()));
    }

    @Test
    @DisplayName("Should refuse a second scheme, a CR/LF injection and a different error code")
    void shouldRefuseMalformedOrForeignChallenges() {
        assertAll("refused headers",
                () -> assertTrue(parser.parse("Bearer error=\"insufficient_scope\", Basic, scope=\"admin\"").isEmpty(),
                        "the scope of a following challenge must never be read as a Bearer parameter"),
                () -> assertTrue(parser.parse("Bearer error=\"insufficient_scope\",\r\n scope=\"admin\"").isEmpty(),
                        "a CR/LF in the header value refuses the whole challenge"),
                () -> assertTrue(parser.parse("Bearer error=\"invalid_token\", scope=\"read\"").isEmpty(),
                        "only error=insufficient_scope parses"));
    }
}
