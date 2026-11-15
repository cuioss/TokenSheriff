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

import de.cuioss.sheriff.token.client.flow.InsufficientScopeChallengeParser.InsufficientScopeChallenge;
import de.cuioss.sheriff.token.commons.error.ErrorTextSanitizer;
import de.cuioss.sheriff.token.commons.http.BearerChallenge;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trips the commons {@link BearerChallenge} builder through the client's
 * {@link InsufficientScopeChallengeParser}: for every generated input the builder accepts, parsing the
 * built {@code WWW-Authenticate} value returns the same scope set, realm and description.
 * <p>
 * <strong>Generated-input restriction.</strong> The generated {@code realm} and {@code error_description}
 * are restricted to printable ASCII (U+0020 to U+007E, quote and backslash included) of at most
 * {@value ErrorTextSanitizer#MAX_LENGTH} characters. This is deliberate, not a gap: the parser passes both
 * values through {@code LogSanitizer} before storing them, which escapes characters outside that range and
 * truncates beyond that length, so a value outside the restriction is stored escaped or capped by design
 * and cannot compare equal to its input. Scope names are generated from the RFC 6749 §3.3
 * {@code scope-token} alphabet, the only names the builder accepts.
 * <p>
 * Positive controls (the round trip) are paired with negative controls: an input the builder refuses is
 * one the parser refuses as well, so the two ends agree on the boundary and not only on the accepted
 * inside.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("BearerChallenge output parses back through InsufficientScopeChallengeParser")
class InsufficientScopeChallengeRoundTripTest {

    private static final int ITERATIONS = 200;

    private static final int MAX_SCOPE_NAME_LENGTH = 24;

    private final InsufficientScopeChallengeParser parser = new InsufficientScopeChallengeParser();

    /** RFC 6749 §3.3 scope-token characters: {@code %x21 / %x23-5B / %x5D-7E}. */
    private static final String SCOPE_TOKEN_ALPHABET = IntStream.rangeClosed(0x21, 0x7E)
            .filter(c -> c != 0x22 && c != 0x5C)
            .mapToObj(c -> String.valueOf((char) c))
            .collect(Collectors.joining());

    private static String randomString(String alphabet, int minLength, int maxLength) {
        int length = Generators.integers(minLength, maxLength).next();
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(alphabet.charAt(Generators.integers(0, alphabet.length() - 1).next()));
        }
        return value.toString();
    }

    private static String printableAscii(int maxLength) {
        String alphabet = IntStream.rangeClosed(0x20, 0x7E)
                .mapToObj(c -> String.valueOf((char) c))
                .collect(Collectors.joining());
        return randomString(alphabet, 0, maxLength);
    }

    private static @Nullable String optionalPrintable() {
        return Generators.booleans().next() ? printableAscii(ErrorTextSanitizer.MAX_LENGTH) : null;
    }

    private static List<String> scopeNames() {
        int count = Generators.integers(0, BearerChallenge.MAX_SCOPE_TOKENS).next();
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            names.add(randomString(SCOPE_TOKEN_ALPHABET, 1, MAX_SCOPE_NAME_LENGTH));
        }
        return names;
    }

    @Test
    @DisplayName("Every accepted builder input parses back to the same scopes, realm and description")
    void shouldRoundTripGeneratedInputs() {
        for (int i = 0; i < ITERATIONS; i++) {
            List<String> scopes = scopeNames();
            String realm = optionalPrintable();
            String description = optionalPrintable();

            String header = BearerChallenge.insufficientScope()
                    .realm(realm)
                    .errorDescription(description)
                    .scopes(scopes)
                    .build();
            Optional<InsufficientScopeChallenge> parsed = parser.parse(header);

            SortedSet<String> expectedScopes = new TreeSet<>(scopes);
            assertTrue(parsed.isPresent(), "the parser accepts the builder output: " + header);
            InsufficientScopeChallenge challenge = parsed.get();
            assertAll("round trip of " + header,
                    () -> assertEquals(List.copyOf(expectedScopes), List.copyOf(challenge.scopes()),
                            "the same scope set, in the builder's sorted order"),
                    () -> assertEquals(Optional.ofNullable(realm), challenge.getRealm(), "the same realm"),
                    () -> assertEquals(Optional.ofNullable(description), challenge.getErrorDescription(),
                            "the same error_description"));
        }
    }

    @Test
    @DisplayName("Quote and backslash in realm and description survive the round trip")
    void shouldRoundTripEscapedCharacters() {
        String realm = "a\"b\\c";
        String description = "\\\"quoted\" and \\back\\";

        InsufficientScopeChallenge challenge = parser.parse(BearerChallenge.insufficientScope()
                .realm(realm).errorDescription(description).scope("read").build()).orElseThrow();

        assertEquals(Optional.of(realm), challenge.getRealm(), "an escaped quote and backslash unescape to the input");
        assertEquals(Optional.of(description), challenge.getErrorDescription(),
                "escape sequences at the edges unescape to the input");
    }

    @Test
    @DisplayName("Negative control: a scope name the builder refuses is one the parser refuses")
    void shouldAgreeOnRefusedScopeName() {
        String outside = "a\"b";
        BearerChallenge.Builder builder = BearerChallenge.insufficientScope();

        assertThrows(IllegalArgumentException.class, () -> builder.scope(outside),
                "the builder refuses a scope name outside the scope-token grammar");
        assertTrue(parser.parse("Bearer error=\"insufficient_scope\", scope=\"a\\\"b\"").isEmpty(),
                "the parser refuses the same name even when it arrives correctly escaped");
    }

    @Test
    @DisplayName("Negative control: one scope past the bound is refused by both ends")
    void shouldAgreeOnScopeBound() {
        List<String> overBound = IntStream.rangeClosed(0, BearerChallenge.MAX_SCOPE_TOKENS)
                .mapToObj(i -> "s" + i)
                .toList();
        List<String> atBound = overBound.subList(0, BearerChallenge.MAX_SCOPE_TOKENS);
        BearerChallenge.Builder builder = BearerChallenge.insufficientScope().scopes(overBound);

        assertEquals(InsufficientScopeChallengeParser.MAX_SCOPE_TOKENS, BearerChallenge.MAX_SCOPE_TOKENS,
                "the builder and the parser share one scope-token bound");
        assertThrows(IllegalArgumentException.class, builder::build, "the builder refuses 65 scopes");
        assertTrue(parser.parse("Bearer error=\"insufficient_scope\", scope=\"" + String.join(" ", overBound) + "\"")
                .isEmpty(), "the parser refuses 65 scopes");
        assertEquals(BearerChallenge.MAX_SCOPE_TOKENS, parser.parse(BearerChallenge.insufficientScope()
                .scopes(atBound).build()).orElseThrow().scopes().size(),
                "control: 64 scopes round-trip");
    }
}
