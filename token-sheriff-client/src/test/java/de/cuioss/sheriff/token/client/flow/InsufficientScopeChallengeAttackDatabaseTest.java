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

import de.cuioss.http.security.database.AttackTestCase;
import de.cuioss.http.security.database.ModSecurityCRSAttackDatabase;
import de.cuioss.http.security.database.OWASPTop10AttackDatabase;
import de.cuioss.sheriff.token.client.flow.InsufficientScopeChallengeParser.InsufficientScopeChallenge;
import de.cuioss.sheriff.token.client.hardening.HeaderLineBreakAttackDatabase;
import de.cuioss.sheriff.token.client.hardening.HeaderLineBreakAttackDatabase.HeaderLineBreakAttack;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The attack matrix of CLIENT-25 point (f): drives line-break separators, every C0 control character
 * and {@code DEL}, the curated {@code http.security} attack corpora and broken quoting through
 * {@link InsufficientScopeChallengeParser}, asserting it fails closed — a forged or malformed
 * {@code WWW-Authenticate} header never yields an {@code insufficient_scope} challenge and never throws.
 * The corpora run twice: once as the whole header, and once embedded in the realm, the
 * {@code error_description} and the scope of an otherwise valid challenge, where each payload must
 * refuse the challenge or be stored only escaped. Modelled on {@link StepUpChallengeAttackDatabaseTest}.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("InsufficientScopeChallengeParser fail-closed against header attacks")
class InsufficientScopeChallengeAttackDatabaseTest {

    private static final String CHALLENGE_PREFIX = "Bearer error=\"insufficient_scope\"";
    private static final int HORIZONTAL_TAB = 0x09;
    private static final int DELETE = 0x7F;

    private final InsufficientScopeChallengeParser parser = new InsufficientScopeChallengeParser();

    private static String scopeToken() {
        return Generators.letterStrings(3, 10).next();
    }

    /**
     * @return every C0 control character except horizontal tab, plus {@code DEL}, as code points
     */
    static IntStream refusedControlCharacters() {
        return IntStream.concat(IntStream.range(0x00, 0x20).filter(c -> c != HORIZONTAL_TAB), IntStream.of(DELETE));
    }

    @ParameterizedTest(name = "WWW-Authenticate with {0}")
    @ArgumentsSource(HeaderLineBreakAttackDatabase.ArgumentsProvider.class)
    @DisplayName("Should refuse a challenge carrying a line break between, inside a quoted scope or inside the error")
    void shouldRefuseLineBreak(HeaderLineBreakAttack attack) {
        var scope = scopeToken();

        assertAll(attack.description(),
                () -> assertRefused(attack.inject(CHALLENGE_PREFIX + ",", " scope=\"" + scope + "\""),
                        "between auth-params"),
                () -> assertRefused(attack.inject(CHALLENGE_PREFIX + ", scope=\"" + scope, "admin\""),
                        "inside a quoted scope"),
                () -> assertRefused(attack.inject("Bearer error=\"insufficient", "_scope\""),
                        "inside the error token"));
    }

    @ParameterizedTest(name = "smuggled challenge after {0}")
    @ArgumentsSource(HeaderLineBreakAttackDatabase.ArgumentsProvider.class)
    @DisplayName("Should never derive a challenge smuggled behind a line break")
    void shouldRefuseSmuggledChallenge(HeaderLineBreakAttack attack) {
        assertRefused(attack.inject("Bearer realm=\"api\"",
                "WWW-Authenticate: " + CHALLENGE_PREFIX + ", scope=\"admin\""), "response splitting");
    }

    @ParameterizedTest(name = "code point {0}")
    @MethodSource("refusedControlCharacters")
    @DisplayName("Should refuse a challenge carrying any C0 control character other than HT, or DEL")
    void shouldRefuseControlCharacter(int codePoint) {
        String control = String.valueOf((char) codePoint);
        var scope = scopeToken();

        assertAll("code point " + codePoint,
                () -> assertRefused(CHALLENGE_PREFIX + "," + control + " scope=\"" + scope + "\"",
                        "between auth-params"),
                () -> assertRefused(CHALLENGE_PREFIX + ", scope=\"" + scope + control + "\"", "inside a quoted scope"),
                () -> assertRefused("Bearer error=\"insufficient" + control + "_scope\"", "inside the error token"),
                () -> assertRefused(CHALLENGE_PREFIX + ", error_description=\"x" + control + "y\"",
                        "inside error_description"));
    }

    @Test
    @DisplayName("Should accept horizontal tab as optional whitespace and escape it in error_description")
    void shouldAcceptHorizontalTab() {
        var scope = scopeToken();
        String header = CHALLENGE_PREFIX + ",\tscope=\"" + scope + "\",\terror_description=\"x\ty\"";

        Optional<InsufficientScopeChallenge> challenge = parser.parse(header);

        assertTrue(challenge.isPresent(), "HT is the one tolerated control character");
        assertAll("tolerated HT",
                () -> assertEquals(List.of(scope), List.copyOf(challenge.get().scopes())),
                () -> assertEquals(Optional.of("x\\ty"), challenge.get().getErrorDescription(),
                        "HT is stored only escaped"));
    }

    @ParameterizedTest
    @ArgumentsSource(OWASPTop10AttackDatabase.ArgumentsProvider.class)
    @DisplayName("Should never derive a challenge from an OWASP attack payload used as the raw header")
    void owaspForgedHeaderNeverYieldsChallenge(AttackTestCase testCase) {
        assertRefused(testCase.attackString(), "OWASP payload");
    }

    @ParameterizedTest
    @ArgumentsSource(ModSecurityCRSAttackDatabase.ArgumentsProvider.class)
    @DisplayName("Should never derive a challenge from a ModSecurity attack payload used as the raw header")
    void modSecurityForgedHeaderNeverYieldsChallenge(AttackTestCase testCase) {
        assertRefused(testCase.attackString(), "ModSecurity payload");
    }

    @ParameterizedTest
    @ArgumentsSource(OWASPTop10AttackDatabase.ArgumentsProvider.class)
    @DisplayName("Should refuse or store only escaped an OWASP payload embedded in realm, error_description and scope")
    void owaspEmbeddedPayloadIsContained(AttackTestCase testCase) {
        assertEmbeddedPayloadContained(testCase.attackString());
    }

    @ParameterizedTest
    @ArgumentsSource(ModSecurityCRSAttackDatabase.ArgumentsProvider.class)
    @DisplayName("Should refuse or store only escaped a ModSecurity payload embedded in realm, error_description and scope")
    void modSecurityEmbeddedPayloadIsContained(AttackTestCase testCase) {
        assertEmbeddedPayloadContained(testCase.attackString());
    }

    @Test
    @DisplayName("Should parse the embedding challenge around a benign value")
    void shouldParseEmbeddingChallengeAroundBenignValue() {
        var value = scopeToken();
        Optional<InsufficientScopeChallenge> challenge = parser.parse(embeddingChallenge(value));

        assertTrue(challenge.isPresent(), "positive control: the embedding challenge itself is valid");
        assertAll("benign embedded value",
                () -> assertEquals(Optional.of(value), challenge.get().getRealm()),
                () -> assertEquals(Optional.of(value), challenge.get().getErrorDescription()),
                () -> assertEquals(List.of("read", value), List.copyOf(challenge.get().scopes())));
    }

    @ParameterizedTest(name = "scope part [{0}]")
    @ValueSource(strings = {
            ", scope=\"read",
            ", scope=\"read\\",
            ", scope=\"read\\\"",
            ", scope=\"read\"write\"",
            ", scope=read\\",
            ", scope=re\"ad"})
    @DisplayName("Should yield empty without an exception for an unterminated quote or a stray backslash")
    void shouldRefuseBrokenQuoting(String scopePart) {
        String header = CHALLENGE_PREFIX + scopePart;

        Optional<InsufficientScopeChallenge> challenge = assertDoesNotThrow(() -> parser.parse(header));

        assertTrue(challenge.isEmpty(), "broken quoting is refused in strict mode: " + header);
    }

    /**
     * @return an otherwise valid challenge carrying {@code value} in the realm, the error_description and,
     *         beside the token {@code read}, the scope
     */
    private static String embeddingChallenge(String value) {
        return "Bearer realm=\"" + value + "\", error=\"insufficient_scope\", error_description=\"" + value
                + "\", scope=\"read " + value + "\"";
    }

    /**
     * Asserts that a payload placed inside the quoted values of an otherwise valid challenge either refuses
     * the whole challenge, or is stored with no unescaped control or line-separator character and with
     * every scope token inside the RFC 6749 §3.3 grammar.
     */
    private void assertEmbeddedPayloadContained(String payload) {
        Optional<InsufficientScopeChallenge> challenge = assertDoesNotThrow(
                () -> parser.parse(embeddingChallenge(payload)), "an embedded payload must not throw");
        if (challenge.isEmpty()) {
            return;
        }
        InsufficientScopeChallenge parsed = challenge.get();
        assertAll("embedded payload " + payload,
                () -> assertTrue(parsed.getRealm().map(InsufficientScopeChallengeAttackDatabaseTest::isEscaped)
                        .orElse(true), "realm is stored only escaped"),
                () -> assertTrue(parsed.getErrorDescription()
                                .map(InsufficientScopeChallengeAttackDatabaseTest::isEscaped).orElse(true),
                        "error_description is stored only escaped"),
                () -> assertTrue(parsed.scopes().stream().allMatch(
                                InsufficientScopeChallengeAttackDatabaseTest::isScopeToken),
                        "every stored scope token is inside the scope-token grammar"));
    }

    private static boolean isEscaped(String stored) {
        return stored.codePoints().noneMatch(c -> Character.isISOControl(c) || c == 0x2028 || c == 0x2029);
    }

    private static boolean isScopeToken(String token) {
        return !token.isEmpty() && token.chars().allMatch(c -> c == 0x21 || (c >= 0x23 && c <= 0x5B)
                || (c >= 0x5D && c <= 0x7E));
    }

    private void assertRefused(String header, String reason) {
        Optional<InsufficientScopeChallenge> challenge = assertDoesNotThrow(() -> parser.parse(header),
                reason + " must not throw");
        assertTrue(challenge.isEmpty(), reason + " must not yield an insufficient_scope challenge");
    }
}
