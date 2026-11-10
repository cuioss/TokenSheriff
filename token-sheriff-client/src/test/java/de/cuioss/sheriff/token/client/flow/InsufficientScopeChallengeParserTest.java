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
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the fail-closed contract of {@link InsufficientScopeChallengeParser} (CLIENT-25): which RFC 6750
 * §3.1 {@code insufficient_scope} challenges parse, what the parsed record carries, and every shape that
 * yields {@link Optional#empty()}. The attack matrix of point (f) lives in
 * {@link InsufficientScopeChallengeAttackDatabaseTest}.
 * <p>
 * The header literals are hand-written — the grammar under test is the placement of quotes, commas and
 * spaces — and only the semantically free scope and realm values are generated.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("InsufficientScopeChallengeParser RFC 6750 insufficient_scope contract")
class InsufficientScopeChallengeParserTest {

    private static final String CHALLENGE_PREFIX = "Bearer error=\"insufficient_scope\"";

    private final InsufficientScopeChallengeParser parser = new InsufficientScopeChallengeParser();

    private static String scopeToken() {
        return Generators.letterStrings(3, 10).next();
    }

    private InsufficientScopeChallenge parseRequired(String header) {
        Optional<InsufficientScopeChallenge> challenge = parser.parse(header);
        assertTrue(challenge.isPresent(), "header must yield an insufficient_scope challenge: " + header);
        return challenge.get();
    }

    private void assertRefused(String header, String reason) {
        assertTrue(parser.parse(header).isEmpty(), reason + ": " + header);
    }

    @Nested
    @DisplayName("(a) Scheme, error code, null and blank")
    class SchemeAndError {

        @Test
        @DisplayName("Should parse a Bearer insufficient_scope challenge with a case-insensitive scheme")
        void shouldParseBearerInsufficientScope() {
            var scope = scopeToken();

            assertAll("Bearer scheme",
                    () -> assertEquals(List.of(scope),
                            List.copyOf(parseRequired(CHALLENGE_PREFIX + ", scope=\"" + scope + "\"").scopes())),
                    () -> assertTrue(parser.parse("bEaReR error=\"insufficient_scope\"").isPresent(),
                            "the auth-scheme is case-insensitive (RFC 9110 §11.1)"));
        }

        @Test
        @DisplayName("Should yield empty for a null, empty or blank header")
        void shouldRefuseNullAndBlank() {
            assertAll("absent header",
                    () -> assertTrue(parser.parse(null).isEmpty(), "null"),
                    () -> assertTrue(parser.parse("").isEmpty(), "empty"),
                    () -> assertTrue(parser.parse("   ").isEmpty(), "blank"));
        }

        @Test
        @DisplayName("Should yield empty for a non-Bearer scheme or a Bearer challenge without the scheme separator")
        void shouldRefuseOtherScheme() {
            assertAll("other scheme",
                    () -> assertRefused("Basic error=\"insufficient_scope\"", "Basic scheme"),
                    () -> assertRefused("DPoP error=\"insufficient_scope\"", "DPoP scheme"),
                    () -> assertRefused("Bearer", "scheme without parameters"));
        }

        @Test
        @DisplayName("Should yield empty unless error is exactly insufficient_scope")
        void shouldRefuseOtherErrorCodes() {
            var scope = scopeToken();

            assertAll("error code",
                    () -> assertRefused("Bearer scope=\"" + scope + "\"", "no error"),
                    () -> assertRefused("Bearer error=\"invalid_token\", scope=\"" + scope + "\"", "invalid_token"),
                    () -> assertRefused("Bearer error=\"invalid_request\"", "invalid_request"),
                    () -> assertRefused("Bearer error=\"insufficient_user_authentication\", acr_values=\"x\"",
                            "step-up challenge"),
                    () -> assertRefused("Bearer error=\"INSUFFICIENT_SCOPE\"", "error code is case-sensitive"),
                    () -> assertRefused("Bearer error=\"insufficient_scope \"", "error code with trailing space"));
        }
    }

    @Nested
    @DisplayName("(b) Scope grammar, empty tokens, duplicates and order")
    class ScopeGrammar {

        @Test
        @DisplayName("Should keep scope tokens in first-seen order, dropping empty tokens and collapsing duplicates")
        void shouldNormaliseScopeTokens() {
            String header = CHALLENGE_PREFIX + ", scope=\" write  read write openid \"";

            assertEquals(List.of("write", "read", "openid"), List.copyOf(parseRequired(header).scopes()),
                    "split on single spaces, empty tokens dropped, duplicates collapsed in first-seen order");
        }

        @Test
        @DisplayName("Should accept scope tokens across the whole RFC 6749 §3.3 character range")
        void shouldAcceptScopeTokenRange() {
            String token = "!#[]~orders:read/https://api.example.com";
            String header = CHALLENGE_PREFIX + ", scope=\"" + token + "\"";

            assertEquals(List.of(token), List.copyOf(parseRequired(header).scopes()),
                    "%x21 / %x23-5B / %x5D-7E are all scope-token characters");
        }

        @Test
        @DisplayName("Should accept an unquoted single-token scope")
        void shouldAcceptUnquotedScope() {
            var scope = scopeToken();

            assertEquals(List.of(scope), List.copyOf(parseRequired(CHALLENGE_PREFIX + ", scope=" + scope).scopes()));
        }

        @ParameterizedTest(name = "scope value {0}")
        @ValueSource(strings = {"read \\\"write", "read wr\\\\ite", "read café", "read\twrite"})
        @DisplayName("Should refuse the whole challenge when a scope token leaves the scope-token grammar")
        void shouldRefuseScopeTokenOutsideGrammar(String quotedScope) {
            // A double quote (%x22), a backslash (%x5C), a non-ASCII character and a horizontal tab are
            // all outside %x21 / %x23-5B / %x5D-7E; HT passes the header guard but not the token grammar.
            assertRefused(CHALLENGE_PREFIX + ", scope=\"" + quotedScope + "\"",
                    "a scope token outside RFC 6749 §3.3 refuses the challenge");
        }

        @Test
        @DisplayName("Should return an unmodifiable scope set")
        void shouldReturnUnmodifiableScopes() {
            Set<String> scopes = parseRequired(CHALLENGE_PREFIX + ", scope=\"read\"").scopes();

            assertThrows(UnsupportedOperationException.class, () -> scopes.add("forged"),
                    "the parsed scope set must not be modifiable by the caller");
        }
    }

    @Nested
    @DisplayName("(c) Absent and blank scope")
    class AbsentScope {

        @ParameterizedTest(name = "scope part [{0}]")
        @ValueSource(strings = {"", ", scope=\"\"", ", scope=\"   \""})
        @DisplayName("Should yield a challenge with an empty scope set when scope is absent or blank")
        void shouldYieldEmptyScopeSet(String scopePart) {
            var challenge = parseRequired(CHALLENGE_PREFIX + scopePart);

            assertTrue(challenge.scopes().isEmpty(),
                    "an insufficient_scope refusal that names no scope is still reported, with no scopes");
        }
    }

    @Nested
    @DisplayName("(d) Sanitised realm and error_description")
    class RealmAndDescription {

        @Test
        @DisplayName("Should carry realm and error_description when present")
        void shouldCarryRealmAndDescription() {
            var realm = scopeToken();
            String header = "Bearer realm=\"" + realm + "\", error=\"insufficient_scope\", "
                    + "error_description=\"The request needs more scope\", scope=\"read write\"";

            var challenge = parseRequired(header);

            assertAll("RFC 6750 §3.1 example shape",
                    () -> assertEquals(List.of("read", "write"), List.copyOf(challenge.scopes())),
                    () -> assertEquals(Optional.of(realm), challenge.getRealm()),
                    () -> assertEquals(Optional.of("The request needs more scope"), challenge.getErrorDescription()));
        }

        @Test
        @DisplayName("Should yield empty optionals when realm and error_description are absent")
        void shouldOmitAbsentRealmAndDescription() {
            var challenge = parseRequired(CHALLENGE_PREFIX);

            assertAll("absent optional parameters",
                    () -> assertTrue(challenge.getRealm().isEmpty(), "realm"),
                    () -> assertTrue(challenge.getErrorDescription().isEmpty(), "error_description"));
        }

        @Test
        @DisplayName("Should escape control and line-separator characters in error_description")
        void shouldEscapeDescription() {
            String header = CHALLENGE_PREFIX + ", error_description=\"a\tb\u0085c d\"";

            assertEquals(Optional.of("a\\tb\\u0085c\\u2028d"), parseRequired(header).getErrorDescription(),
                    "HT, NEL and LS pass the header guard and are stored only escaped");
        }

        @Test
        @DisplayName("Should escape a Unicode line separator in realm")
        void shouldEscapeLineSeparatorInRealm() {
            var realm = scopeToken();
            String header = "Bearer realm=\"" + realm + " forged\", error=\"insufficient_scope\"";

            assertEquals(Optional.of(realm + "\\u2028forged"), parseRequired(header).getRealm(),
                    "LS passes the header guard and the realm stores it only escaped");
        }

        @Test
        @DisplayName("Should truncate an over-length error_description and mark the truncation")
        void shouldTruncateDescription() {
            String longDescription = "d".repeat(ErrorTextSanitizer.MAX_LENGTH + 44);
            String header = CHALLENGE_PREFIX + ", error_description=\"" + longDescription + "\"";

            assertEquals(Optional.of("d".repeat(ErrorTextSanitizer.MAX_LENGTH) + ErrorTextSanitizer.TRUNCATION_MARKER),
                    parseRequired(header).getErrorDescription(),
                    "the description is bounded before it is stored");
        }

        @Test
        @DisplayName("Should truncate an over-length realm and mark the truncation")
        void shouldTruncateRealm() {
            String longRealm = "r".repeat(ErrorTextSanitizer.MAX_LENGTH + 44);
            String header = "Bearer realm=\"" + longRealm + "\", error=\"insufficient_scope\"";

            assertEquals(Optional.of("r".repeat(ErrorTextSanitizer.MAX_LENGTH) + ErrorTextSanitizer.TRUNCATION_MARKER),
                    parseRequired(header).getRealm(),
                    "the realm is bounded before it is stored");
        }
    }

    @Nested
    @DisplayName("(e) Duplicate auth-params and quoted separators")
    class DuplicatesAndQuoting {

        @Test
        @DisplayName("Should refuse a challenge naming any auth-param twice (RFC 9110 §11.2)")
        void shouldRefuseDuplicateAuthParam() {
            assertAll("duplicate auth-param",
                    () -> assertRefused(CHALLENGE_PREFIX + ", scope=\"read\", scope=\"admin\"", "duplicate scope"),
                    () -> assertRefused(CHALLENGE_PREFIX + ", error=\"insufficient_scope\"", "duplicate error"),
                    () -> assertRefused(CHALLENGE_PREFIX + ", realm=\"a\", REALM=\"b\"",
                            "auth-param names compare case-insensitively"));
        }

        @Test
        @DisplayName("Should keep a quoted comma and a quoted error= inside their own value")
        void shouldKeepQuotedSeparatorsInValue() {
            String header = "Bearer realm=\"a, error=\\\"invalid_token\\\"\", error=\"insufficient_scope\", "
                    + "error_description=\"x, scope=admin\", scope=\"read\"";

            var challenge = parseRequired(header);

            assertAll("quoted separators",
                    () -> assertEquals(Optional.of("a, error=\"invalid_token\""), challenge.getRealm(),
                            "a quoted comma and an escaped quote stay in the realm value"),
                    () -> assertEquals(Optional.of("x, scope=admin"), challenge.getErrorDescription(),
                            "a quoted 'scope=' never becomes a second scope parameter"),
                    () -> assertEquals(List.of("read"), List.copyOf(challenge.scopes())));
        }
    }

    @Nested
    @DisplayName("(g) Header length and scope-token bounds")
    class Bounds {

        @Test
        @DisplayName("Should parse a header of exactly 8192 characters and refuse one character more")
        void shouldBoundHeaderLength() {
            assertAll("8192-character bound",
                    () -> assertTrue(parser.parse(headerOfLength(BearerChallengeParams.MAX_HEADER_LENGTH)).isPresent(),
                            "at the bound"),
                    () -> assertRefused(headerOfLength(BearerChallengeParams.MAX_HEADER_LENGTH + 1),
                            "beyond the bound"));
        }

        @Test
        @DisplayName("Should parse 64 scope tokens and refuse 65")
        void shouldBoundScopeTokenCount() {
            int max = InsufficientScopeChallengeParser.MAX_SCOPE_TOKENS;

            assertAll("64-token bound",
                    () -> assertEquals(max, parseRequired(headerWithScopes(max)).scopes().size(), "at the bound"),
                    () -> assertRefused(headerWithScopes(max + 1), "beyond the bound"));
        }

        private static String headerOfLength(int length) {
            String prefix = CHALLENGE_PREFIX + ", error_description=\"";
            return prefix + "a".repeat(length - prefix.length() - 1) + "\"";
        }

        private static String headerWithScopes(int count) {
            String scopes = IntStream.rangeClosed(1, count).mapToObj(i -> "s" + i).collect(Collectors.joining(" "));
            return CHALLENGE_PREFIX + ", scope=\"" + scopes + "\"";
        }
    }

    @Nested
    @DisplayName("(h) A second scheme after or before the Bearer challenge")
    class SecondScheme {

        @Test
        @DisplayName("Should refuse a following scheme whose parameter would otherwise be read as scope")
        void shouldRefuseSchemeSmugglingScope() {
            assertRefused(CHALLENGE_PREFIX + ", Basic, scope=\"x\"",
                    "the scope of a following challenge must never be read as a Bearer parameter");
        }

        @Test
        @DisplayName("Should refuse a following scheme with parameters and a valueless segment")
        void shouldRefuseFollowingScheme() {
            assertAll("following scheme",
                    () -> assertRefused(CHALLENGE_PREFIX + ", scope=\"read\", Basic realm=\"x\"", "Basic realm"),
                    () -> assertRefused(CHALLENGE_PREFIX + ", scope=\"read\", realm", "valueless segment"),
                    () -> assertRefused(CHALLENGE_PREFIX + ", =orphan, scope=\"read\"", "empty auth-param name"));
        }

        @Test
        @DisplayName("Should refuse a Bearer challenge preceded by another scheme")
        void shouldRefusePrecedingScheme() {
            assertRefused("Basic realm=\"x\", " + CHALLENGE_PREFIX + ", scope=\"read\"",
                    "only a value starting with the Bearer scheme is read");
        }

        @Test
        @DisplayName("Should parse the same challenge once the second scheme is removed and empty list elements dropped")
        void shouldParseWithoutSecondScheme() {
            assertEquals(List.of("read"),
                    List.copyOf(parseRequired(CHALLENGE_PREFIX + ",, scope=\"read\",").scopes()),
                    "positive control: empty list elements from ',,' are dropped as RFC 9110 allows");
        }
    }

    @Nested
    @DisplayName("(i) Statelessness")
    class Statelessness {

        @Test
        @DisplayName("Should declare no instance fields")
        void shouldHaveNoInstanceState() {
            List<String> instanceFields = Arrays.stream(InsufficientScopeChallengeParser.class.getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .filter(field -> !field.isSynthetic())
                    .map(Field::getName)
                    .toList();

            assertTrue(instanceFields.isEmpty(), "the parser must keep no state: " + instanceFields);
        }

        @Test
        @DisplayName("Should return equal results on repeated calls")
        void shouldBeRepeatable() {
            String header = "Bearer realm=\"api\", error=\"insufficient_scope\", scope=\"" + scopeToken() + "\"";

            assertEquals(parser.parse(header), parser.parse(header),
                    "a stateless parser returns the same challenge for the same header");
        }
    }
}
