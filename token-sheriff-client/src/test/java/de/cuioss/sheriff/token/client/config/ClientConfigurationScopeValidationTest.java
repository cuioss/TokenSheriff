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
package de.cuioss.sheriff.token.client.config;

import de.cuioss.sheriff.token.client.internal.LogSanitizer;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the construction-time scope-token rule of {@link ClientConfiguration}
 * ({@link ClientConfiguration#validateScopes(List)}, RFC 6749 §3.3): which entries it accepts, which it
 * refuses, and that a refusal names the rule and the entry index but never the entry.
 */
@EnableGeneratorController
@DisplayName("ClientConfiguration scope-token validation (RFC 6749 §3.3)")
class ClientConfigurationScopeValidationTest {

    /** The message prefix every scope-token rejection keeps. */
    private static final String SCOPE_TOKEN_MESSAGE_PREFIX =
            "scopes must hold one scope-token per entry (RFC 6749 section 3.3)";

    /** The message a {@code null} or blank entry is refused with. */
    private static final String NULL_OR_BLANK_MESSAGE = "scopes must not contain null or blank entries";

    /** U+00A0 NO-BREAK SPACE: whitespace to {@link Character#isSpaceChar(int)}, outside the scope-token set. */
    private static final String NO_BREAK_SPACE = Character.toString(0x00A0);

    /** U+00E9 LATIN SMALL LETTER E WITH ACUTE: a non-ASCII letter. */
    private static final String E_ACUTE = Character.toString(0x00E9);

    /** U+1F600 GRINNING FACE: a supplementary code point, a surrogate pair in UTF-16. */
    private static final String GRINNING_FACE = Character.toString(0x1F600);

    private static ClientConfiguration.ClientConfigurationBuilder builder() {
        return ClientConfiguration.builder()
                .issuer("https://" + Generators.letterStrings(3, 12).next() + ".example.com")
                .clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .clientSecret(Generators.nonBlankStrings().next());
    }

    /**
     * Entries that each hold at least one character outside the RFC 6749 §3.3 scope-token set
     * {@code %x21 / %x23-5B / %x5D-7E}, none of them blank.
     */
    static Stream<String> nonScopeTokenEntries() {
        return Stream.of(
                "read write",
                "read\twrite",
                "read\r\nwrite",
                "say\"hello",
                "back\\slash",
                "nul\u0000byte",
                "del\u007Fbyte",
                "ctl\u0001byte",
                "caf" + E_ACUTE,
                "smile" + GRINNING_FACE,
                "non" + NO_BREAK_SPACE + "breaking");
    }

    @Test
    @DisplayName("Should reject a blank scope entry that would otherwise serialise into the scope parameter")
    void shouldRejectBlankScopeEntry() {
        var blankScope = builder().scope("openid").scope("   ");

        var exception = assertThrows(IllegalArgumentException.class, blankScope::build,
                "a blank scope would serialise as stray whitespace in the scope parameter");

        assertEquals(NULL_OR_BLANK_MESSAGE, exception.getMessage());
    }

    @Test
    @DisplayName("Should reject a null scope entry that would otherwise serialise as the literal \"null\"")
    void shouldRejectNullScopeEntry() {
        var scopes = new ArrayList<String>();
        scopes.add("openid");
        scopes.add(null);
        var nullScope = builder().scopes(scopes);

        var exception = assertThrows(IllegalArgumentException.class, nullScope::build,
                "a null scope would serialise as the literal \"null\" in the scope parameter");

        assertEquals(NULL_OR_BLANK_MESSAGE, exception.getMessage());
    }

    @Test
    @DisplayName("Should reject a scope entry holding more than one scope-token (RFC 6749 §3.3)")
    void shouldRejectScopeEntryWithEmbeddedWhitespace() {
        var multiTokenScope = builder().scope("openid").scope("read write");

        var exception = assertThrows(IllegalArgumentException.class, multiTokenScope::build,
                "'read write' goes out as two scope-tokens but counts as one requested member, so an "
                        + "exactly-as-requested grant would reconcile as BROADENED");

        assertEquals(SCOPE_TOKEN_MESSAGE_PREFIX + ", but entry 1 holds a whitespace character (U+0020)",
                exception.getMessage());
    }

    @Test
    @DisplayName("Should name a no-break space as whitespace by its code point")
    void shouldNameNoBreakSpaceByCodePoint() {
        var noBreakSpace = builder().scope("non" + NO_BREAK_SPACE + "breaking");

        var exception = assertThrows(IllegalArgumentException.class, noBreakSpace::build);

        assertEquals(SCOPE_TOKEN_MESSAGE_PREFIX + ", but entry 0 holds a whitespace character (U+00A0)",
                exception.getMessage());
    }

    @Test
    @DisplayName("Should accept a scope list whose entries are each a single scope-token")
    void shouldAcceptSingleTokenScopeEntries() {
        var config = builder().scope("openid").scope("read").scope("write").build();

        assertEquals(List.of("openid", "read", "write"), config.getScopes(),
                "single scope-token entries are accepted as configured");
    }

    @ParameterizedTest(name = "rejects entry #{index}")
    @MethodSource("nonScopeTokenEntries")
    @DisplayName("Should reject an entry holding a character outside the RFC 6749 §3.3 scope-token set, without echoing it")
    void shouldRejectNonScopeTokenCharacter(String entry) {
        var builder = builder().scope("openid").scope(entry);

        var exception = assertThrows(IllegalArgumentException.class, builder::build,
                "only %x21 / %x23-5B / %x5D-7E may appear in a scope-token");

        String message = exception.getMessage();
        assertAll("the rejection names the rule and the entry index, never the entry",
                () -> assertTrue(message.startsWith(SCOPE_TOKEN_MESSAGE_PREFIX + ", but entry 1 holds a "),
                        "the message must keep the scope-token prefix and name entry 1, but was: " + message),
                () -> assertTrue(message.matches(".* \\(U\\+[0-9A-F]{4,6}\\)"),
                        "the message must end with the offending code point, but was: " + message),
                () -> assertFalse(message.contains(entry),
                        "the rejected entry must never be echoed into the exception message"));
    }

    @Test
    @DisplayName("Should name a rejected control character by code point and keep it out of the message")
    void shouldNotEchoControlCharacterEntry() {
        String entry = "openid\r\nX-Injected: " + Generators.letterStrings(8, 16).next() + "\u0000";
        var builder = builder().scope(entry);

        var exception = assertThrows(IllegalArgumentException.class, builder::build);

        String message = exception.getMessage();
        assertAll("a control character never reaches the exception message",
                () -> assertEquals(SCOPE_TOKEN_MESSAGE_PREFIX + ", but entry 0 holds a whitespace character (U+000D)",
                        message, "CR is whitespace and is the first offending character"),
                () -> assertFalse(message.contains(entry), "the raw entry must not be echoed"),
                () -> assertTrue(message.chars().noneMatch(Character::isISOControl),
                        "the message must not carry any control character, but was: " + message));
    }

    @Test
    @DisplayName("Should keep an attacker-controlled entry out of the message even in its log-sanitized form")
    void shouldKeepSanitizedEntryOutOfMessage() {
        String forged = Generators.letterStrings(8, 16).next();
        String entry = "openid\r\n" + forged + "\u0085";
        var builder = builder().scope(entry);

        var exception = assertThrows(IllegalArgumentException.class, builder::build);

        String message = exception.getMessage();
        String sanitizedEntry = LogSanitizer.sanitize(entry);
        assertAll("the message is already log-safe and carries no form of the entry",
                () -> assertEquals("openid\\r\\n" + forged + "\\u0085", sanitizedEntry,
                        "a log site would render the entry with CR, LF and NEL escaped"),
                () -> assertEquals(message, LogSanitizer.sanitize(message),
                        "sanitizing the message changes nothing, so it is safe to log verbatim"),
                () -> assertFalse(message.contains(sanitizedEntry),
                        "not even the escaped entry is echoed"),
                () -> assertFalse(message.contains(forged),
                        "the attacker-chosen text never reaches the message"));
    }

    @Test
    @DisplayName("Should accept every printable ASCII character of the scope-token set, alone and combined")
    void shouldAcceptEveryScopeTokenCharacter() {
        List<String> singleCharacterScopes = IntStream.rangeClosed(0x21, 0x7E)
                .filter(codePoint -> codePoint != '"' && codePoint != '\\')
                .mapToObj(Character::toString)
                .toList();
        String allCharacters = String.join("", singleCharacterScopes);

        var single = builder().scopes(singleCharacterScopes).build();
        var combined = builder().scope(allCharacters).build();

        assertAll("the whole %x21 / %x23-5B / %x5D-7E range is a valid scope-token",
                () -> assertEquals(92, singleCharacterScopes.size(), "the set holds 92 characters"),
                () -> assertTrue(singleCharacterScopes.containsAll(List.of("&", "=", "#", "%", "+", "/")),
                        "delimiter-like characters are inside the range and are percent-encoded, not refused"),
                () -> assertEquals(singleCharacterScopes, single.getScopes()),
                () -> assertEquals(List.of(allCharacters), combined.getScopes()));
    }

    @Test
    @DisplayName("Should accept realistic URL, URN and punctuated scope values")
    void shouldAcceptRealisticScopes() {
        List<String> realistic = List.of("openid", "https://api.example.com/read", "urn:example:scope", "a.b-c_d");

        var config = builder().scopes(realistic).build();

        assertEquals(realistic, config.getScopes(), "realistic scope values are accepted as configured");
    }

    @Test
    @DisplayName("Should refuse a null scope list passed to validateScopes directly")
    void shouldRefuseNullScopeList() {
        var exception = assertThrows(NullPointerException.class, () -> ClientConfiguration.validateScopes(null));

        assertEquals("scopes must not be null", exception.getMessage());
    }
}
