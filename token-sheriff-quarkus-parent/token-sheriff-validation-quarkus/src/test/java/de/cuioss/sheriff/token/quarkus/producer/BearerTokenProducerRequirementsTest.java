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

import de.cuioss.sheriff.token.quarkus.servlet.HttpRequestResolver;
import de.cuioss.sheriff.token.quarkus.servlet.HttpRequestResolverMock;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static de.cuioss.sheriff.token.quarkus.TokenSheriffQuarkusLogMessages.WARN;
import static de.cuioss.sheriff.token.quarkus.producer.ProducerTestTokens.createAccessToken;
import static de.cuioss.test.juli.LogAsserts.assertSingleLogMessagePresentContaining;
import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Requirement-set checks of {@link BearerTokenProducer}: a token missing a required scope, role or group
 * yields a constraint violation naming the required and the missing values, and the result does not
 * depend on requirement sets the caller changes after the call.
 */
@EnableTestLogger(rootLevel = TestLogLevel.DEBUG)
class BearerTokenProducerRequirementsTest {

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
        assertEquals(requiredScopes, result.getRequiredScopes());
        assertEquals(Set.of("admin"), result.getMissingScopes());
        assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.BEARER_TOKEN_REQUIREMENTS_NOT_MET_DETAILED.resolveIdentifierString());

        verify(tokenValidator);
    }

    @Test
    @DisplayName("The result is independent of requirement sets the caller clears afterwards")
    void resultIsIndependentOfCallerRequirementSets() {
        BearerTokenResult result = scopeViolationFor(new HashSet<>(Set.of("admin", "write")),
                new HashSet<>(Set.of("reader")), new HashSet<>(Set.of("staff")), true);

        assertAll("result after the caller cleared its sets",
                () -> assertEquals(Set.of("admin", "write"), result.getRequiredScopes(), "required scopes"),
                () -> assertEquals(Set.of("reader"), result.getRequiredRoles(), "required roles"),
                () -> assertEquals(Set.of("staff"), result.getRequiredGroups(), "required groups"),
                () -> assertEquals(Set.of("admin"), result.getMissingScopes(), "missing scopes"));
    }

    @Test
    @DisplayName("negative control: the result for untouched requirement sets carries the same values")
    void resultForUntouchedRequirementSets() {
        BearerTokenResult result = scopeViolationFor(new HashSet<>(Set.of("admin", "write")),
                new HashSet<>(Set.of("reader")), new HashSet<>(Set.of("staff")), false);

        assertAll("result for untouched sets",
                () -> assertEquals(Set.of("admin", "write"), result.getRequiredScopes(), "required scopes"),
                () -> assertEquals(Set.of("reader"), result.getRequiredRoles(), "required roles"),
                () -> assertEquals(Set.of("staff"), result.getRequiredGroups(), "required groups"),
                () -> assertEquals(Set.of("admin"), result.getMissingScopes(), "missing scopes"));
    }

    /**
     * Validates a token carrying only the {@code write} scope against the caller's mutable requirement
     * sets and, when {@code clearAfterwards} is set, clears every one of those sets after the call.
     */
    private BearerTokenResult scopeViolationFor(Set<String> requiredScopes, Set<String> requiredRoles,
            Set<String> requiredGroups, boolean clearAfterwards) {
        servletResolverMock.setBearerToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature");
        AccessTokenContent tokenContent = createAccessToken(Map.of(
                ClaimName.SCOPE.getName(), ClaimValue.forList("write", List.of("write"))));
        expect(tokenValidator.createAccessToken(anyObject(AccessTokenRequest.class))).andReturn(tokenContent);
        replay(tokenValidator);

        BearerTokenResult result = producer.getBearerTokenResult(requiredScopes, requiredRoles, requiredGroups);
        if (clearAfterwards) {
            requiredScopes.clear();
            requiredRoles.clear();
            requiredGroups.clear();
        }

        assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, result.getStatus(),
                "the token lacks the admin scope, so the requirements are not met");
        verify(tokenValidator);
        return result;
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
        assertEquals(requiredRoles, result.getRequiredRoles());
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
        assertEquals(requiredGroups, result.getRequiredGroups());
        assertEquals(Set.of("managers"), result.getMissingGroups());
        assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.BEARER_TOKEN_REQUIREMENTS_NOT_MET_DETAILED.resolveIdentifierString());

        verify(tokenValidator);
    }
}
