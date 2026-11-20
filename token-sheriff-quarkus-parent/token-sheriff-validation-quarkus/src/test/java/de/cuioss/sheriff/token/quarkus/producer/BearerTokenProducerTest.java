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

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.quarkus.servlet.HttpRequestResolver;
import de.cuioss.sheriff.token.quarkus.servlet.HttpRequestResolverMock;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.easymock.Capture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static de.cuioss.sheriff.token.quarkus.producer.ProducerTestTokens.createAccessToken;
import static de.cuioss.test.juli.LogAsserts.assertSingleLogMessagePresentContaining;
import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BearerTokenProducer}.
 * Tests the bearer token extraction and validation logic.
 *
 * Note: This test focuses on the basic flow and error handling.
 * Full integration testing with real tokens is done in QuarkusTest classes. The requirement-set checks
 * are in {@link BearerTokenProducerRequirementsTest}, the JWE size checks with a real validator in
 * {@link BearerTokenProducerJweSizeTest}.
 */
@EnableTestLogger(rootLevel = TestLogLevel.DEBUG)
class BearerTokenProducerTest {

    private BearerTokenProducer producer;
    private TokenValidator tokenValidator;
    private HttpRequestResolverMock servletResolverMock;

    @BeforeEach
    void setUp() {
        tokenValidator = createMock(TokenValidator.class);
        servletResolverMock = new HttpRequestResolverMock();
        HttpRequestResolver servletResolver = servletResolverMock;
        producer = new BearerTokenProducer(tokenValidator, servletResolver,
                () -> EmptyJsonWebToken.INSTANCE, "Authorization", "Bearer");
    }

    @Test
    @DisplayName("Authorization 'Bearer ' with an empty token yields INVALID_REQUEST without the cookie fallback")
    void emptyBearerTokenDoesNotFallBackToCookie() {
        BearerTokenProducer cookieProducer = new BearerTokenProducer(tokenValidator, servletResolverMock,
                () -> EmptyJsonWebToken.INSTANCE, "Cookie", "Bearer");
        servletResolverMock.setHeader("Authorization", "Bearer ");
        servletResolverMock.setHeader("Cookie", "Bearer=eyJhbGciOiJSUzI1NiJ9.e30.c2ln");
        replay(tokenValidator);

        BearerTokenResult result = cookieProducer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

        assertEquals(BearerTokenStatus.INVALID_REQUEST, result.getStatus());
        verify(tokenValidator);
    }

    @Test
    @DisplayName("producePrincipal resolves the request-scoped JsonWebToken from the provider")
    void producePrincipalDelegatesToProvider() {
        assertSame(EmptyJsonWebToken.INSTANCE, producer.producePrincipal(),
                "Principal must be the provider-resolved JsonWebToken (single validation per request)");
    }

    @Test
    @DisplayName("Should reject invalid token header configuration at construction time")
    void invalidTokenHeaderConfiguration() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new BearerTokenProducer(tokenValidator, servletResolverMock,
                        () -> EmptyJsonWebToken.INSTANCE, "X-Custom-Header", "Bearer"),
                "Unsupported token.header values must fail fast at startup");
        assertTrue(exception.getMessage().contains("X-Custom-Header"),
                "Exception should name the invalid value: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("sheriff.token.token.header"),
                "Exception should name the property: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("Authorization")
                && exception.getMessage().contains("Cookie"),
                "Exception should list the supported values: " + exception.getMessage());
    }

    @Test
    @DisplayName("Should accept supported token header values case-insensitively")
    void supportedTokenHeaderValuesCaseInsensitive() {
        assertDoesNotThrow(() -> new BearerTokenProducer(tokenValidator, servletResolverMock,
                () -> EmptyJsonWebToken.INSTANCE, "authorization", "Bearer"));
        assertDoesNotThrow(() -> new BearerTokenProducer(tokenValidator, servletResolverMock,
                () -> EmptyJsonWebToken.INSTANCE, "COOKIE", "Bearer"));
    }

    @Test
    @DisplayName("Should return no token given when Authorization header is missing")
    void missingAuthorizationHeader() {
        // No header set - httpServletRequestMock starts with empty headers
        BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

        assertEquals(BearerTokenStatus.NO_TOKEN_GIVEN, result.getStatus());
        assertTrue(result.getAccessTokenContent().isEmpty());
        assertSingleLogMessagePresentContaining(TestLogLevel.DEBUG,
                "Bearer token missing or invalid in Authorization header");
    }

    @Test
    @DisplayName("Should return no token given when Authorization header doesn't have Bearer prefix")
    void wrongAuthorizationPrefix() {
        servletResolverMock.setHeader("Authorization", "Basic sometoken");

        BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

        assertEquals(BearerTokenStatus.NO_TOKEN_GIVEN, result.getStatus());
        assertTrue(result.getAccessTokenContent().isEmpty());
        assertSingleLogMessagePresentContaining(TestLogLevel.DEBUG,
                "Bearer token missing or invalid in Authorization header");
    }

    @Test
    @DisplayName("Should successfully validate token without requirements")
    void successfulValidationNoRequirements() {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature";
        servletResolverMock.setBearerToken(token);

        AccessTokenContent tokenContent = createAccessToken(Map.of());
        expect(tokenValidator.createAccessToken(anyObject(AccessTokenRequest.class))).andReturn(tokenContent);
        replay(tokenValidator);

        BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

        assertEquals(BearerTokenStatus.FULLY_VERIFIED, result.getStatus());
        assertTrue(result.getAccessTokenContent().isPresent());
        assertSingleLogMessagePresentContaining(TestLogLevel.DEBUG,
                "Bearer token validation successful");

        verify(tokenValidator);
    }

    @Test
    @DisplayName("Should handle token validation exception")
    void tokenValidationException() {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature";
        servletResolverMock.setBearerToken(token);

        TokenValidationException exception = new TokenValidationException(
                SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED, "Invalid signature");
        expect(tokenValidator.createAccessToken(anyObject(AccessTokenRequest.class))).andThrow(exception);
        replay(tokenValidator);

        BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

        assertEquals(BearerTokenStatus.PARSING_ERROR, result.getStatus());
        assertFalse(result.getAccessTokenContent().isPresent());
        assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                "Bearer token validation failed: Invalid signature");

        verify(tokenValidator);
    }

    @Test
    @DisplayName("Should handle getBearerTokenResult with no requirements")
    void getBearerTokenResultWithNoRequirements() {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature";
        servletResolverMock.setBearerToken(token);

        AccessTokenContent tokenContent = createAccessToken(Map.of());
        expect(tokenValidator.createAccessToken(anyObject(AccessTokenRequest.class))).andReturn(tokenContent);
        replay(tokenValidator);

        BearerTokenResult result = producer.getBearerTokenResult(
                Set.of(), Set.of(), Set.of());

        assertTrue(result.getAccessTokenContent().isPresent());
        assertEquals(tokenContent, result.getAccessTokenContent().get());

        verify(tokenValidator);
    }

    @Test
    @DisplayName("Should handle empty Authorization header value")
    void emptyAuthorizationHeader() {
        servletResolverMock.setHeader("Authorization", "");

        BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

        assertEquals(BearerTokenStatus.NO_TOKEN_GIVEN, result.getStatus());
        assertTrue(result.getAccessTokenContent().isEmpty());
        assertSingleLogMessagePresentContaining(TestLogLevel.DEBUG,
                "Bearer token missing or invalid in Authorization header");
    }

    @Test
    @DisplayName("Should handle Bearer prefix with empty token as INVALID_REQUEST per RFC 6750")
    void bearerPrefixWithEmptyToken() {
        servletResolverMock.setHeader("Authorization", "Bearer ");

        // According to RFC 6750, empty bearer token should return 400 Bad Request
        // No need to call tokenValidator - we detect this before validation
        BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

        assertEquals(BearerTokenStatus.INVALID_REQUEST, result.getStatus());
        assertTrue(result.getAccessTokenContent().isEmpty());
        assertEquals("Bearer token is empty", result.getErrorMessage().orElse(null));
        assertSingleLogMessagePresentContaining(TestLogLevel.DEBUG,
                "Bearer token is empty - invalid request per RFC 6750");
    }

    @Test
    @DisplayName("Should handle Bearer prefix with whitespace-only token as INVALID_REQUEST")
    void bearerPrefixWithWhitespaceOnlyToken() {
        servletResolverMock.setHeader("Authorization", "Bearer    ");

        // Whitespace-only token should also be treated as invalid request
        BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

        assertEquals(BearerTokenStatus.INVALID_REQUEST, result.getStatus());
        assertTrue(result.getAccessTokenContent().isEmpty());
        assertEquals("Bearer token is empty", result.getErrorMessage().orElse(null));
        assertSingleLogMessagePresentContaining(TestLogLevel.DEBUG,
                "Bearer token is empty - invalid request per RFC 6750");
    }

    @Nested
    @DisplayName("Cookie-based token extraction")
    @EnableTestLogger(rootLevel = TestLogLevel.DEBUG)
    class CookieExtractionTests {

        @BeforeEach
        void configureCookieMode() throws Exception {
            setField(producer, "tokenHeader", "Cookie");
            setField(producer, "tokenCookieName", "Bearer");
        }

        @Test
        @DisplayName("Should extract token from cookie with default cookie name")
        void extractTokenFromCookieDefaultName() {
            String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature";
            servletResolverMock.setHeader("Cookie", "Bearer=" + token);

            AccessTokenContent tokenContent = createAccessToken(Map.of());
            expect(tokenValidator.createAccessToken(anyObject(AccessTokenRequest.class))).andReturn(tokenContent);
            replay(tokenValidator);

            BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

            assertEquals(BearerTokenStatus.FULLY_VERIFIED, result.getStatus());
            assertTrue(result.getAccessTokenContent().isPresent());
            verify(tokenValidator);
        }

        @Test
        @DisplayName("Should extract token from cookie with custom cookie name")
        void extractTokenFromCookieCustomName() throws Exception {
            setField(producer, "tokenCookieName", "jwt_token");

            String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature";
            servletResolverMock.setHeader("Cookie", "other=abc; jwt_token=" + token + "; session=xyz");

            AccessTokenContent tokenContent = createAccessToken(Map.of());
            expect(tokenValidator.createAccessToken(anyObject(AccessTokenRequest.class))).andReturn(tokenContent);
            replay(tokenValidator);

            BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

            assertEquals(BearerTokenStatus.FULLY_VERIFIED, result.getStatus());
            assertTrue(result.getAccessTokenContent().isPresent());
            verify(tokenValidator);
        }

        @Test
        @DisplayName("Should return no token when cookie header is missing")
        void missingCookieHeader() {
            // No cookie header set
            BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

            assertEquals(BearerTokenStatus.NO_TOKEN_GIVEN, result.getStatus());
            assertTrue(result.getAccessTokenContent().isEmpty());
        }

        @Test
        @DisplayName("Should return no token when target cookie is not present")
        void cookieHeaderWithoutTargetCookie() {
            servletResolverMock.setHeader("Cookie", "session=abc; other=xyz");

            BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

            assertEquals(BearerTokenStatus.NO_TOKEN_GIVEN, result.getStatus());
            assertTrue(result.getAccessTokenContent().isEmpty());
        }

        @Test
        @DisplayName("Authorization header takes precedence over cookie")
        void authorizationHeaderTakesPrecedence() {
            String authToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.auth.signature";
            String cookieToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.cookie.signature";
            servletResolverMock.setBearerToken(authToken);
            servletResolverMock.setHeader("Cookie", "Bearer=" + cookieToken);

            AccessTokenContent tokenContent = createAccessToken(Map.of());
            // Capture the request to verify which token was used
            Capture<AccessTokenRequest> capture = Capture.newInstance();
            expect(tokenValidator.createAccessToken(capture(capture))).andReturn(tokenContent);
            replay(tokenValidator);

            BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

            assertEquals(BearerTokenStatus.FULLY_VERIFIED, result.getStatus());
            assertEquals(authToken, capture.getValue().tokenString());
            verify(tokenValidator);
        }

        @Test
        @DisplayName("Should handle quoted cookie values per RFC 6265")
        void quotedCookieValue() {
            String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature";
            servletResolverMock.setHeader("Cookie", "Bearer=\"" + token + "\"");

            AccessTokenContent tokenContent = createAccessToken(Map.of());
            expect(tokenValidator.createAccessToken(anyObject(AccessTokenRequest.class))).andReturn(tokenContent);
            replay(tokenValidator);

            BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

            assertEquals(BearerTokenStatus.FULLY_VERIFIED, result.getStatus());
            assertTrue(result.getAccessTokenContent().isPresent());
            verify(tokenValidator);
        }

        @Test
        @DisplayName("Should return no token when cookie value is empty")
        void emptyCookieValue() {
            servletResolverMock.setHeader("Cookie", "Bearer=");

            BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

            assertEquals(BearerTokenStatus.NO_TOKEN_GIVEN, result.getStatus());
            assertTrue(result.getAccessTokenContent().isEmpty());
        }

        @Test
        @DisplayName("Should not use cookie extraction when tokenHeader is Authorization (default)")
        void defaultHeaderDoesNotUseCookies() throws Exception {
            setField(producer, "tokenHeader", "Authorization");

            String cookieToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.cookie.signature";
            servletResolverMock.setHeader("Cookie", "Bearer=" + cookieToken);
            // No Authorization header

            BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

            assertEquals(BearerTokenStatus.NO_TOKEN_GIVEN, result.getStatus());
            assertTrue(result.getAccessTokenContent().isEmpty());
        }
    }

    /**
     * Sets a field value on the target object using reflection.
     */
    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
