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

import de.cuioss.sheriff.token.commons.http.BearerCredentials.Result;
import de.cuioss.sheriff.token.commons.http.BearerCredentials.State;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the RFC 6750 §2.1 extraction rules of {@link BearerCredentials} edge by edge.
 * <p>
 * Each refused input is asserted by its {@link State}, never by a message, and each is paired with the
 * nearest accepted input as its positive control, so a case fails only when the one rule it names changes.
 * The rules under test are OD-1 (the scheme matches case-insensitively, RFC 9110 §11.1) and OD-2 (the
 * field value is trimmed, the scheme is followed by {@code 1*SP}, a tab after the scheme is not
 * {@code Bearer}, and a token holding SP, HTAB or a control character is malformed).
 */
@DisplayName("BearerCredentials extracts RFC 6750 Bearer credentials")
class BearerCredentialsTest {

    private static final String TOKEN = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.c2lnbmF0dXJl";

    private static void assertPresent(String expectedToken, Result result, String rule) {
        assertAll(rule,
                () -> assertEquals(State.PRESENT, result.state(), rule),
                () -> assertEquals(Optional.of(expectedToken), result.token(), "the token is returned unchanged"));
    }

    private static void assertRefused(State expected, Result result, String rule) {
        assertAll(rule,
                () -> assertEquals(expected, result.state(), rule),
                () -> assertTrue(result.token().isEmpty(), "a refused value never yields a token"));
    }

    @Nested
    @DisplayName("ABSENT: the value carries no Bearer credentials")
    class Absent {

        @Test
        @DisplayName("null, empty and whitespace-only values are absent")
        void shouldTreatMissingValuesAsAbsent() {
            assertRefused(State.ABSENT, BearerCredentials.extract((String) null), "a missing field is absent");
            assertRefused(State.ABSENT, BearerCredentials.extract(""), "an empty field value is absent");
            assertRefused(State.ABSENT, BearerCredentials.extract("  \t "), "an OWS-only field value is absent");
            assertPresent(TOKEN, BearerCredentials.extract("Bearer " + TOKEN), "positive control: credentials");
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {"Basic abc", "Bearerx abc", "Bearer", "Bearer\tabc", "Bearer\t", "Bear abc",
                "DPoP abc"})
        @DisplayName("Another scheme, a longer scheme, the bare scheme and a tab separator are absent")
        void shouldTreatOtherSchemesAsAbsent(String value) {
            assertRefused(State.ABSENT, BearerCredentials.extract(value),
                    "OD-2: only the Bearer scheme followed by 1*SP carries Bearer credentials");
        }

        @Test
        @DisplayName("Positive control: the Bearer scheme followed by one SP carries the token")
        void shouldAcceptSchemeFollowedBySpace() {
            assertPresent("abc", BearerCredentials.extract("Bearer abc"),
                    "OD-2: one SP separates the scheme from the token");
        }
    }

    @Nested
    @DisplayName("MALFORMED: the Bearer scheme with unusable credentials")
    class Malformed {

        @ParameterizedTest(name = "[{index}] trailing SP count {0}")
        @ValueSource(ints = {1, 2, 5})
        @DisplayName("The scheme followed only by spaces is malformed, not absent")
        void shouldTreatSpaceOnlyRemainderAsMalformed(int spaces) {
            String value = "Bearer" + " ".repeat(spaces);

            assertRefused(State.MALFORMED, BearerCredentials.extract(value),
                    "OD-2: the scheme is presented but the token is empty");
            assertRefused(State.ABSENT, BearerCredentials.extract("Bearer"),
                    "control: the bare scheme without a space is absent, not malformed");
        }

        @Test
        @DisplayName("A remainder of spaces and tabs only is malformed")
        void shouldTreatWhitespaceOnlyRemainderAsMalformed() {
            assertRefused(State.MALFORMED, BearerCredentials.extract("Bearer \t \t"),
                    "OD-2: a whitespace-only token is malformed");
            assertPresent("a", BearerCredentials.extract("Bearer a \t"),
                    "control: a token followed by trailing OWS is present");
        }

        static Stream<Arguments> refusedTokenCharacters() {
            return Stream.of(
                    Arguments.of("internal SP", ' '),
                    Arguments.of("HTAB", '\t'),
                    Arguments.of("CR", '\r'),
                    Arguments.of("LF", '\n'),
                    Arguments.of("NUL", (char) 0x0000),
                    Arguments.of("other C0 (US)", (char) 0x001F),
                    Arguments.of("DEL", (char) 0x007F),
                    Arguments.of("first C1", (char) 0x0080),
                    Arguments.of("NEL", (char) 0x0085),
                    Arguments.of("last C1", (char) 0x009F));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("refusedTokenCharacters")
        @DisplayName("A token holding SP, HTAB or a control character is malformed")
        void shouldTreatRefusedCharacterInTokenAsMalformed(String name, char refused) {
            assertRefused(State.MALFORMED, BearerCredentials.extract("Bearer ab" + refused + "cd"),
                    "OD-2: a token holding " + name + " is malformed");
            assertPresent("abcd", BearerCredentials.extract("Bearer abcd"),
                    "control: the same token without the character is present");
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(chars = {'!', '~', (char) 0x00A0, '-', '.', '_', '+', '/', '='})
        @DisplayName("Positive control: characters just outside the refused ranges stay in the token")
        void shouldKeepCharactersOutsideRefusedRanges(char kept) {
            String token = "ab" + kept + "cd";

            assertPresent(token, BearerCredentials.extract("Bearer " + token),
                    "only SP, HTAB and control characters are refused; the b64token grammar is not checked here");
        }
    }

    @Nested
    @DisplayName("PRESENT: scheme case and surrounding whitespace")
    class Present {

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {"Bearer", "bearer", "BEARER", "bEaReR"})
        @DisplayName("The scheme matches case-insensitively")
        void shouldMatchSchemeCaseInsensitively(String scheme) {
            assertPresent(TOKEN, BearerCredentials.extract(scheme + " " + TOKEN),
                    "OD-1: the scheme name is case-insensitive (RFC 9110 §11.1)");
            assertRefused(State.ABSENT, BearerCredentials.extract(scheme + "x " + TOKEN),
                    "control: the same spelling with an extra character is another scheme");
        }

        static Stream<Arguments> surroundingWhitespace() {
            return Stream.of(
                    Arguments.of("one SP after the scheme", "Bearer " + TOKEN),
                    Arguments.of("several SP after the scheme", "Bearer    " + TOKEN),
                    Arguments.of("trailing SP", "Bearer " + TOKEN + "   "),
                    Arguments.of("trailing HTAB", "Bearer " + TOKEN + "\t"),
                    Arguments.of("leading SP before the scheme", "   Bearer " + TOKEN),
                    Arguments.of("leading HTAB before the scheme", "\tBearer " + TOKEN),
                    Arguments.of("leading and trailing OWS", " \t Bearer  " + TOKEN + " \t "));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("surroundingWhitespace")
        @DisplayName("Surrounding optional whitespace and 1*SP after the scheme are ignored")
        void shouldIgnoreSurroundingWhitespace(String name, String value) {
            assertPresent(TOKEN, BearerCredentials.extract(value),
                    "OD-2: " + name + " is not part of the token");
        }

        @Test
        @DisplayName("A token of the configured maximum token size passes unchanged")
        void shouldNotLimitTokenSize() {
            String token = "a".repeat(ParserConfig.DEFAULT_MAX_TOKEN_SIZE);

            Result result = BearerCredentials.extract("Bearer " + token);

            assertPresent(token, result, "the extractor imposes no size limit; TokenStringValidator does");
            assertEquals(ParserConfig.DEFAULT_MAX_TOKEN_SIZE, result.token().orElseThrow().length(),
                    "every character of the maximum-size token is kept");
        }
    }

    @Nested
    @DisplayName("Header map lookup")
    class HeaderMap {

        @Test
        @DisplayName("Only the first Authorization value is used")
        void shouldUseFirstValue() {
            Map<String, List<String>> headers = Map.of("authorization", List.of("Bearer first", "Bearer second"));

            assertPresent("first", BearerCredentials.extract(headers), "the first value is the field value read");
        }

        @Test
        @DisplayName("A refused first value is not rescued by a later value")
        void shouldNotFallBackToLaterValue() {
            Map<String, List<String>> headers = Map.of("authorization", List.of("Basic abc", "Bearer second"));

            assertRefused(State.ABSENT, BearerCredentials.extract(headers), "later values are never read");
        }

        @Test
        @DisplayName("The lower-case key is used")
        void shouldUseLowerCaseKey() {
            assertPresent(TOKEN, BearerCredentials.extract(Map.of("authorization", List.of("Bearer " + TOKEN))),
                    "the normalised lower-case key is looked up first");
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {"Authorization", "AUTHORIZATION", "AuThOrIzAtIoN"})
        @DisplayName("A case-variant key is used when the lower-case key is missing")
        void shouldUseCaseVariantKey(String key) {
            assertPresent(TOKEN, BearerCredentials.extract(Map.of(key, List.of("Bearer " + TOKEN))),
                    "field names are case-insensitive (RFC 9110 §5.1)");
        }

        @Test
        @DisplayName("The lower-case key wins over a case-variant key")
        void shouldPreferLowerCaseKey() {
            Map<String, List<String>> headers = new TreeMap<>();
            headers.put("Authorization", List.of("Bearer variant"));
            headers.put("authorization", List.of("Bearer lower"));

            assertPresent("lower", BearerCredentials.extract(headers), "the lower-case key is looked up first");
        }

        @Test
        @DisplayName("A missing field, an empty value list and a null first value are absent")
        void shouldTreatMissingFieldAsAbsent() {
            Map<String, List<String>> nullFirst = new HashMap<>();
            nullFirst.put("authorization", Arrays.asList(null, "Bearer later"));
            Map<String, List<String>> nullKey = new LinkedHashMap<>();
            nullKey.put(null, List.of("Bearer " + TOKEN));

            assertRefused(State.ABSENT, BearerCredentials.extract(Map.of()), "no field is absent");
            assertRefused(State.ABSENT, BearerCredentials.extract(Map.of("accept", List.of("Bearer " + TOKEN))),
                    "another field is never read");
            assertRefused(State.ABSENT, BearerCredentials.extract(Map.of("authorization", List.of())),
                    "an empty value list is absent");
            assertRefused(State.ABSENT, BearerCredentials.extract(nullFirst), "a null first value is absent");
            assertRefused(State.ABSENT, BearerCredentials.extract(nullKey), "a null key is skipped");
        }

        @Test
        @DisplayName("A null header map is refused")
        void shouldRefuseNullMap() {
            assertThrows(NullPointerException.class, () -> BearerCredentials.extract((Map<String, List<String>>) null),
                    "the header map is required");
        }
    }

    @Nested
    @DisplayName("Result contract")
    class ResultContract {

        @Test
        @DisplayName("toString names the state and never the token or a refused value")
        void shouldNotPrintValueInToString() {
            String present = BearerCredentials.extract("Bearer " + TOKEN).toString();
            String malformed = BearerCredentials.extract("Bearer secret\r\nInjected").toString();

            assertAll("toString",
                    () -> assertTrue(present.contains("PRESENT"), "the state is printed"),
                    () -> assertFalse(present.contains(TOKEN), "the token is a credential and is never printed"),
                    () -> assertTrue(malformed.contains("MALFORMED"), "the state is printed"),
                    () -> assertFalse(malformed.contains("secret"), "a refused value is never printed"),
                    () -> assertFalse(malformed.contains("Injected"), "a refused value is never printed"));
        }
    }
}
