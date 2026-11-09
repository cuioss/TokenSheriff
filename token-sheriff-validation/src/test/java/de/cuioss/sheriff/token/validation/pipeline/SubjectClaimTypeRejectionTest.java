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
package de.cuioss.sheriff.token.validation.pipeline;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter.EventType;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.domain.context.IdTokenRequest;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static de.cuioss.sheriff.token.validation.pipeline.IdentifierClaimNullEquivalenceTest.signedToken;
import static de.cuioss.sheriff.token.validation.pipeline.IdentifierClaimNullEquivalenceTest.validClaims;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A {@code sub} claim that is not a JSON string is refused at the {@link TokenValidator} entry points
 * for access and ID tokens, as a typed, counted {@code MISSING_CLAIM} rejection. It is never
 * stringified: a number {@code 123} and a string {@code "123"} must not both name the same subject,
 * because every later {@code sub} comparison (the access-token to ID-token binding in the client,
 * the userinfo binding) compares the string form only.
 * <p>
 * The tokens are signed here from a hand-built payload, because a JWT builder types {@code sub} as a
 * string and could not produce the non-string value under test.
 */
@EnableTestLogger
@DisplayName("A non-string sub claim at the TokenValidator entry points")
class SubjectClaimTypeRejectionTest {

    private static final String MALFORMED_SUB_PREFIX = "Malformed claim 'sub': expected a JSON string but got ";

    private TokenValidator validator;

    @BeforeEach
    void setUp() {
        TestTokenHolder holder = TestTokenGenerators.accessTokens().next();
        validator = TokenValidator.builder().issuerConfig(holder.getIssuerConfig()).build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    static Stream<Arguments> nonStringSubjects() {
        return Stream.of(
                Arguments.of("number", "123"),
                Arguments.of("boolean", "true"),
                Arguments.of("object", "{\"id\":\"test-subject\"}"),
                Arguments.of("array", "[\"test-subject\"]"));
    }

    private static String tokenWithSubject(String subjectJson) {
        Map<String, String> claims = validClaims();
        claims.put("sub", subjectJson);
        return signedToken(claims);
    }

    @Test
    @DisplayName("Non-vacuity: a string sub validates as an access token and as an ID token")
    void shouldAcceptStringSubject() {
        String token = tokenWithSubject("\"123\"");

        assertEquals("123", validator.createAccessToken(AccessTokenRequest.of(token)).getSubject().orElseThrow());
        assertEquals("123", validator.createIdToken(IdTokenRequest.of(token)).getSubject().orElseThrow());
        assertEquals(0, validator.getSecurityEventCounter().getCount(EventType.MISSING_CLAIM));
    }

    @ParameterizedTest(name = "access token, sub as {0}")
    @MethodSource("nonStringSubjects")
    @DisplayName("A non-string sub is a counted MISSING_CLAIM rejection for an access token")
    void shouldRefuseNonStringSubjectInAccessToken(String jsonType, String subjectJson) {
        var request = AccessTokenRequest.of(tokenWithSubject(subjectJson));

        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> validator.createAccessToken(request),
                "A sub given as " + jsonType + " must not be stringified into a subject");

        assertEquals(EventType.MISSING_CLAIM, exception.getEventType());
        assertTrue(exception.getMessage().startsWith(MALFORMED_SUB_PREFIX), exception.getMessage());
        assertEquals(1, validator.getSecurityEventCounter().getCount(EventType.MISSING_CLAIM));
        assertEquals(0, validator.getSecurityEventCounter().getCount(EventType.ACCESS_TOKEN_CREATED));
    }

    @ParameterizedTest(name = "ID token, sub as {0}")
    @MethodSource("nonStringSubjects")
    @DisplayName("A non-string sub is a counted MISSING_CLAIM rejection for an ID token")
    void shouldRefuseNonStringSubjectInIdToken(String jsonType, String subjectJson) {
        var request = IdTokenRequest.of(tokenWithSubject(subjectJson));

        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> validator.createIdToken(request),
                "A sub given as " + jsonType + " must not be stringified into a subject");

        assertEquals(EventType.MISSING_CLAIM, exception.getEventType());
        assertTrue(exception.getMessage().startsWith(MALFORMED_SUB_PREFIX), exception.getMessage());
        assertEquals(1, validator.getSecurityEventCounter().getCount(EventType.MISSING_CLAIM));
    }
}
