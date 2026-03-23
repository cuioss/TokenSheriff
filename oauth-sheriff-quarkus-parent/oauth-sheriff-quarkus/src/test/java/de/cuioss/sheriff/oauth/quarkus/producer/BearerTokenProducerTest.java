/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.oauth.quarkus.producer;

import de.cuioss.sheriff.oauth.core.TokenValidator;
import de.cuioss.sheriff.oauth.core.domain.claim.ClaimName;
import de.cuioss.sheriff.oauth.core.domain.claim.ClaimValue;
import de.cuioss.sheriff.oauth.core.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.oauth.core.domain.token.AccessTokenContent;
import de.cuioss.sheriff.oauth.core.exception.TokenValidationException;
import de.cuioss.sheriff.oauth.core.json.MapRepresentation;
import de.cuioss.sheriff.oauth.core.security.SecurityEventCounter;
import de.cuioss.sheriff.oauth.quarkus.servlet.HttpServletRequestResolver;
import de.cuioss.sheriff.oauth.quarkus.servlet.HttpServletRequestResolverMock;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.*;

import static de.cuioss.sheriff.oauth.quarkus.OAuthSheriffQuarkusLogMessages.WARN;
import static de.cuioss.test.juli.LogAsserts.assertSingleLogMessagePresentContaining;
import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BearerTokenProducer}.
 * Tests the bearer token extraction and validation logic.
 *
 * Note: This test focuses on the basic flow and error handling.
 * Full integration testing with real tokens is done in QuarkusTest classes.
 */
@EnableTestLogger(rootLevel = TestLogLevel.DEBUG)
class BearerTokenProducerTest {

    private BearerTokenProducer producer;
    private TokenValidator tokenValidator;
    private HttpServletRequestResolverMock servletResolverMock;

    @BeforeEach
    void setUp() {
        tokenValidator = createMock(TokenValidator.class);
        servletResolverMock = new HttpServletRequestResolverMock();
        HttpServletRequestResolver servletResolver = servletResolverMock;
        producer = new BearerTokenProducer(tokenValidator, servletResolver);
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
    @DisplayName("Should return constraint violation when missing required scopes")
    void missingRequiredScopes() {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature";
        servletResolverMock.setBearerToken(token);

        // Token has "write" scope but not "admin"
        AccessTokenContent tokenContent = createAccessToken(Map.of(
                ClaimName.SCOPE.getName(), ClaimValue.forList("write", List.of("write"))));
        Set<String> requiredScopes = Set.of("admin", "write");

        expect(tokenValidator.createAccessToken(anyObject(AccessTokenRequest.class))).andReturn(tokenContent);
        replay(tokenValidator);

        BearerTokenResult result = producer.getBearerTokenResult(requiredScopes, Set.of(), Set.of());

        assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, result.getStatus());
        assertFalse(result.getAccessTokenContent().isPresent());
        assertEquals(Set.of("admin"), result.getMissingScopes());
        assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.BEARER_TOKEN_REQUIREMENTS_NOT_MET_DETAILED.resolveIdentifierString());

        verify(tokenValidator);
    }

    @Test
    @DisplayName("Should return constraint violation when missing required roles")
    void missingRequiredRoles() {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature";
        servletResolverMock.setBearerToken(token);

        // Token has "user" role but not "admin"
        AccessTokenContent tokenContent = createAccessToken(Map.of(
                ClaimName.ROLES.getName(), ClaimValue.forList("user", List.of("user"))));
        Set<String> requiredRoles = Set.of("user", "admin");

        expect(tokenValidator.createAccessToken(anyObject(AccessTokenRequest.class))).andReturn(tokenContent);
        replay(tokenValidator);

        BearerTokenResult result = producer.getBearerTokenResult(Set.of(), requiredRoles, Set.of());

        assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, result.getStatus());
        assertFalse(result.getAccessTokenContent().isPresent());
        assertEquals(Set.of("admin"), result.getMissingRoles());
        assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.BEARER_TOKEN_REQUIREMENTS_NOT_MET_DETAILED.resolveIdentifierString());

        verify(tokenValidator);
    }

    @Test
    @DisplayName("Should return constraint violation when missing required groups")
    void missingRequiredGroups() {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature";
        servletResolverMock.setBearerToken(token);

        // Token has "developers" group but not "managers"
        AccessTokenContent tokenContent = createAccessToken(Map.of(
                ClaimName.GROUPS.getName(), ClaimValue.forList("developers", List.of("developers"))));
        Set<String> requiredGroups = Set.of("developers", "managers");

        expect(tokenValidator.createAccessToken(anyObject(AccessTokenRequest.class))).andReturn(tokenContent);
        replay(tokenValidator);

        BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), requiredGroups);

        assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, result.getStatus());
        assertFalse(result.getAccessTokenContent().isPresent());
        assertEquals(Set.of("managers"), result.getMissingGroups());
        assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.BEARER_TOKEN_REQUIREMENTS_NOT_MET_DETAILED.resolveIdentifierString());

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
        assertSingleLogMessagePresentContaining(TestLogLevel.DEBUG,
                "Bearer token validation failed: Invalid signature");

        verify(tokenValidator);
    }

    @Test
    @DisplayName("Should handle getAccessTokenContent convenience method")
    void getAccessTokenContentMethod() {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature";
        servletResolverMock.setBearerToken(token);

        AccessTokenContent tokenContent = createAccessToken(Map.of());
        expect(tokenValidator.createAccessToken(anyObject(AccessTokenRequest.class))).andReturn(tokenContent);
        replay(tokenValidator);

        Optional<AccessTokenContent> result = producer.getAccessTokenContent();

        assertTrue(result.isPresent());
        assertEquals(tokenContent, result.get());

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

    /**
     * Creates an {@link AccessTokenContent} with mandatory claims and optional additional claims.
     */
    private static AccessTokenContent createAccessToken(Map<String, ClaimValue> additionalClaims) {
        Map<String, ClaimValue> claims = new HashMap<>();
        claims.put(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("test-issuer"));
        claims.put(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString("test-subject"));
        claims.put(ClaimName.EXPIRATION.getName(), ClaimValue.forDateTime(
                String.valueOf(OffsetDateTime.now().plusHours(1).toEpochSecond()),
                OffsetDateTime.now().plusHours(1)));
        claims.put(ClaimName.ISSUED_AT.getName(), ClaimValue.forDateTime(
                String.valueOf(OffsetDateTime.now().toEpochSecond()),
                OffsetDateTime.now()));
        claims.putAll(additionalClaims);
        return new AccessTokenContent(claims, "raw-token", null, MapRepresentation.empty());
    }
}
