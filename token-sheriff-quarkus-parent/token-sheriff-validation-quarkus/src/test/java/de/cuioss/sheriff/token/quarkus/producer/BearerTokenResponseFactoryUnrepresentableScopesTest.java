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
package de.cuioss.sheriff.token.quarkus.producer;

import de.cuioss.sheriff.token.commons.error.ErrorTextSanitizer;
import de.cuioss.sheriff.token.commons.http.BearerChallenge;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static de.cuioss.sheriff.token.quarkus.TokenSheriffQuarkusLogMessages.ERROR;
import static de.cuioss.test.juli.LogAsserts.assertNoLogMessagePresent;
import static de.cuioss.test.juli.LogAsserts.assertSingleLogMessagePresentContaining;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the request-path backstop of {@link BearerTokenResult#createErrorResponse()}: a required scope set no
 * {@code insufficient_scope} challenge can carry is answered with a defined 500 response and one sanitised
 * ERROR log ({@code TokenSheriff_Q-201}) instead of an unhandled {@link IllegalArgumentException}, while a
 * representable set is still answered with the 401 {@code insufficient_scope} challenge.
 */
@EnableTestLogger
@DisplayName("BearerTokenResponseFactory unrepresentable scope backstop")
class BearerTokenResponseFactoryUnrepresentableScopesTest {

    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";

    private static String longestFittingScope() {
        int overhead = BearerTokenResponseFactory.insufficientScopeChallenge(Set.of("x")).length() - 1;
        return "a".repeat(BearerChallenge.MAX_LENGTH - overhead);
    }

    static Stream<Arguments> unrepresentableScopeSets() {
        String longestFitting = longestFittingScope();
        return Stream.of(
                Arguments.of("a scope name holding a space", Set.of("two-words"), Set.of("two words"),
                        "1 scope(s) 'two words'"),
                Arguments.of("a scope name holding a non-ASCII letter", Set.of("cafe"), Set.of("café"),
                        "1 scope(s) 'café'"),
                Arguments.of("more than the maximum number of distinct scopes",
                        scopes(BearerChallenge.MAX_SCOPE_TOKENS), scopes(BearerChallenge.MAX_SCOPE_TOKENS + 1),
                        (BearerChallenge.MAX_SCOPE_TOKENS + 1) + " scope(s)"),
                Arguments.of("a scope that fits the bound only without realm and description",
                        Set.of(longestFitting), Set.of(longestFitting + "a"), "1 scope(s)"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("unrepresentableScopeSets")
    @DisplayName("should answer a scope set the challenge cannot carry with the defined 500 response")
    void shouldAnswerUnrepresentableScopesAsMisconfiguration(String name, Set<String> controlScopes,
            Set<String> unrepresentableScopes, String logFragment) {
        // Arrange
        BearerTokenResult control = scopeViolation(controlScopes);
        BearerTokenResult unrepresentable = scopeViolation(unrepresentableScopes);

        // Act
        Response controlResponse = control.createErrorResponse();
        assertNoLogMessagePresent(TestLogLevel.ERROR, ERROR.UNREPRESENTABLE_REQUIRED_SCOPES.resolveIdentifierString());
        Response response = unrepresentable.createErrorResponse();

        // Assert
        assertInsufficientScopeChallenge(controlResponse);
        assertMisconfigurationResponse(response);
        assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                ERROR.UNREPRESENTABLE_REQUIRED_SCOPES.resolveIdentifierString());
        assertSingleLogMessagePresentContaining(TestLogLevel.ERROR, logFragment);
    }

    @Test
    @DisplayName("should answer 65 required scopes with the 500 response logged as TokenSheriff_Q-201, 64 with the 401 challenge")
    void shouldAnswerOnePastMaximumScopeCountAsMisconfiguration() {
        assertEquals(64, BearerChallenge.MAX_SCOPE_TOKENS, "the challenge carries at most 64 distinct scopes");
        BearerTokenResult atBound = scopeViolation(scopes(64));
        BearerTokenResult pastBound = scopeViolation(scopes(65));

        Response atBoundResponse = atBound.createErrorResponse();
        assertNoLogMessagePresent(TestLogLevel.ERROR, "TokenSheriff_Q-201");
        Response pastBoundResponse = pastBound.createErrorResponse();

        assertInsufficientScopeChallenge(atBoundResponse);
        assertMisconfigurationResponse(pastBoundResponse);
        assertSingleLogMessagePresentContaining(TestLogLevel.ERROR, "TokenSheriff_Q-201");
        assertSingleLogMessagePresentContaining(TestLogLevel.ERROR, "65 scope(s)");
    }

    @Test
    @DisplayName("should answer the longest scope that fits with a challenge of exactly the bound")
    void shouldAnswerLongestFittingScopeWithChallengeAtBound() {
        // Arrange
        String longestFitting = longestFittingScope();
        assertDoesNotThrow(() -> BearerChallenge.insufficientScope().scopes(Set.of(longestFitting + "a")).build(),
                "precondition: one character more fits the bound once realm and error_description are left out");

        // Act
        Response response = scopeViolation(Set.of(longestFitting)).createErrorResponse();

        // Assert
        assertInsufficientScopeChallenge(response);
        assertEquals(BearerChallenge.MAX_LENGTH, response.getHeaderString(WWW_AUTHENTICATE).length(),
                "the longest scope that fits yields a challenge of exactly the bound");
    }

    @Test
    @DisplayName("should truncate the sanitised scope text of the logged ERROR at 256 characters")
    void shouldTruncateLoggedScopesAt256Characters() {
        // Arrange
        String scope = "a".repeat(150) + " " + "b".repeat(150);
        String kept = scope.substring(0, ErrorTextSanitizer.MAX_LENGTH);
        BearerTokenResult unrepresentable = scopeViolation(Set.of(scope));

        // Act
        Response response = unrepresentable.createErrorResponse();

        // Assert
        assertEquals(256, ErrorTextSanitizer.MAX_LENGTH, "the logged scope text is cut at 256 characters");
        assertMisconfigurationResponse(response);
        assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                "1 scope(s) '" + kept + ErrorTextSanitizer.TRUNCATION_MARKER + "'");
        assertNoLogMessagePresent(TestLogLevel.ERROR, scope);
        assertNoLogMessagePresent(TestLogLevel.ERROR, kept + "b");
    }

    @Test
    @DisplayName("should sanitise the scope names in the logged ERROR")
    void shouldSanitiseScopesInLog() {
        // Arrange
        BearerTokenResult unrepresentable = scopeViolation(Set.of("forged\nTokenSheriff_Q-001"));

        // Act
        Response response = unrepresentable.createErrorResponse();

        // Assert
        assertMisconfigurationResponse(response);
        assertSingleLogMessagePresentContaining(TestLogLevel.ERROR, "'forged\\nTokenSheriff_Q-001'");
        assertNoLogMessagePresent(TestLogLevel.ERROR, "forged\nTokenSheriff_Q-001");
    }

    private static void assertInsufficientScopeChallenge(Response response) {
        assertEquals(401, response.getStatus(), "control: a representable scope set yields the 401 challenge");
        String wwwAuthenticate = response.getHeaderString(WWW_AUTHENTICATE);
        assertTrue(wwwAuthenticate.contains("error=\"insufficient_scope\""),
                "control: the challenge is insufficient_scope: " + wwwAuthenticate);
    }

    private static void assertMisconfigurationResponse(Response response) {
        assertEquals(500, response.getStatus(), "an unrepresentable scope set is a server misconfiguration");
        assertNull(response.getHeaderString(WWW_AUTHENTICATE), "no challenge is sent for a misconfiguration");
        assertEquals("no-store, no-cache, must-revalidate", response.getHeaderString("Cache-Control"));
        assertEquals("no-cache", response.getHeaderString("Pragma"));
        BearerTokenResponseFactory.ErrorEntity entity = (BearerTokenResponseFactory.ErrorEntity) response.getEntity();
        assertFalse(entity.valid());
        assertEquals(BearerTokenResponseFactory.SERVER_MISCONFIGURATION, entity.message(),
                "the body is generic and names no scope");
    }

    private static Set<String> scopes(int count) {
        return IntStream.range(0, count).mapToObj(i -> "scope" + i).collect(Collectors.toSet());
    }

    private static BearerTokenResult scopeViolation(Set<String> requiredScopes) {
        return BearerTokenResult.builder()
                .status(BearerTokenStatus.CONSTRAINT_VIOLATION)
                .requiredScopes(requiredScopes)
                .missingScopes(requiredScopes)
                .requiredRoles(Set.of())
                .requiredGroups(Set.of())
                .build();
    }
}
