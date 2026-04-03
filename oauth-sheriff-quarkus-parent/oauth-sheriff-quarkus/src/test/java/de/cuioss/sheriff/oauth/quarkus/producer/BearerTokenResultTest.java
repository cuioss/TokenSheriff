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

import de.cuioss.sheriff.oauth.core.TokenType;
import de.cuioss.sheriff.oauth.core.domain.token.AccessTokenContent;
import de.cuioss.sheriff.oauth.core.exception.TokenValidationException;
import de.cuioss.sheriff.oauth.core.security.SecurityEventCounter.EventType;
import de.cuioss.sheriff.oauth.core.test.TestTokenHolder;
import de.cuioss.sheriff.oauth.core.test.generator.ClaimControlParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for {@link BearerTokenResult} class.
 * Tests all methods, builders, static factory methods, and edge cases.
 *
 * @author Oliver Wolff
 */
@DisplayName("BearerTokenResult Unit Tests")
class BearerTokenResultTest {

    @Nested
    @DisplayName("Static Factory Methods")
    class StaticFactoryMethods {

        @Test
        @DisplayName("builder should create FULLY_VERIFIED result with no missing attributes")
        void builderShouldCreateFullyVerifiedResult() {
            var tokenContent = createTestToken();
            var result = BearerTokenResult.builder()
                    .status(BearerTokenStatus.FULLY_VERIFIED)
                    .accessTokenContent(tokenContent)
                    .build();

            assertEquals(BearerTokenStatus.FULLY_VERIFIED, result.getStatus());
            assertTrue(result.getAccessTokenContent().isPresent());
            assertEquals(tokenContent, result.getAccessTokenContent().get());
            assertTrue(result.getMissingScopes().isEmpty());
            assertTrue(result.getMissingRoles().isEmpty());
            assertTrue(result.getMissingGroups().isEmpty());
            assertFalse(result.getErrorEventType().isPresent());
            assertFalse(result.getErrorMessage().isPresent());
        }

        @Test
        @DisplayName("parsingError() should create PARSING_ERROR result with exception details")
        void parsingErrorShouldCreateParsingErrorResult() {
            var exception = new TokenValidationException(EventType.INVALID_JWT_FORMAT, "Invalid token format");
            var requiredScopes = Set.of("read", "write");
            var requiredRoles = Set.of("admin");
            var requiredGroups = Set.of("managers");

            var result = BearerTokenResult.parsingError(exception, requiredScopes, requiredRoles, requiredGroups);

            assertEquals(BearerTokenStatus.PARSING_ERROR, result.getStatus());
            assertFalse(result.getAccessTokenContent().isPresent());
            assertEquals(requiredScopes, result.getMissingScopes());
            assertEquals(requiredRoles, result.getMissingRoles());
            assertEquals(requiredGroups, result.getMissingGroups());
            assertTrue(result.getErrorEventType().isPresent());
            assertEquals(EventType.INVALID_JWT_FORMAT, result.getErrorEventType().get());
            assertTrue(result.getErrorMessage().isPresent());
            assertEquals("Invalid token format", result.getErrorMessage().get());
        }

        @Test
        @DisplayName("constraintViolation() should create CONSTRAINT_VIOLATION result with missing attributes")
        void constraintViolationShouldCreateConstraintViolationResult() {
            var missingScopes = Set.of("write");
            var missingRoles = Set.of("admin");
            var missingGroups = Set.of("managers");

            var result = BearerTokenResult.constraintViolation(missingScopes, missingRoles, missingGroups);

            assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, result.getStatus());
            assertFalse(result.getAccessTokenContent().isPresent());
            assertEquals(missingScopes, result.getMissingScopes());
            assertEquals(missingRoles, result.getMissingRoles());
            assertEquals(missingGroups, result.getMissingGroups());
            assertFalse(result.getErrorEventType().isPresent());
            assertFalse(result.getErrorMessage().isPresent());
        }

        @Test
        @DisplayName("noTokenGiven() should create NO_TOKEN_GIVEN result with required attributes as missing")
        void noTokenGivenShouldCreateNoTokenGivenResult() {
            var requiredScopes = Set.of("read");
            var requiredRoles = Set.of("user");
            var requiredGroups = Set.of("group");

            var result = BearerTokenResult.noTokenGiven(requiredScopes, requiredRoles, requiredGroups);

            assertEquals(BearerTokenStatus.NO_TOKEN_GIVEN, result.getStatus());
            assertFalse(result.getAccessTokenContent().isPresent());
            assertEquals(requiredScopes, result.getMissingScopes());
            assertEquals(requiredRoles, result.getMissingRoles());
            assertEquals(requiredGroups, result.getMissingGroups());
            assertFalse(result.getErrorEventType().isPresent());
            assertFalse(result.getErrorMessage().isPresent());
        }

    }

    @Nested
    @DisplayName("Builder Helper Methods")
    class BuilderHelperMethods {


        @Test
        @DisplayName("parsingError() should configure result with exception details")
        void parsingErrorShouldConfigureBuilder() {
            var exception = new TokenValidationException(EventType.INVALID_JWT_FORMAT, "Bad signature");

            var result = BearerTokenResult.parsingError(exception, Set.of(), Set.of(), Set.of());

            assertEquals(BearerTokenStatus.PARSING_ERROR, result.getStatus());
            assertTrue(result.getErrorEventType().isPresent());
            assertEquals(EventType.INVALID_JWT_FORMAT, result.getErrorEventType().get());
            assertTrue(result.getErrorMessage().isPresent());
            assertEquals("Bad signature", result.getErrorMessage().get());
        }
    }

    @Nested
    @DisplayName("Authorization Status Methods")
    class AuthorizationStatusMethods {

        @Test
        @DisplayName("isSuccessfullyAuthorized() should return true only for FULLY_VERIFIED")
        void isSuccessfullyAuthorizedShouldReturnTrueOnlyForFullyVerified() {
            var successResult = BearerTokenResult.builder()
                    .status(BearerTokenStatus.FULLY_VERIFIED)
                    .accessTokenContent(createTestToken())
                    .build();
            assertTrue(successResult.isSuccessfullyAuthorized());

            var failureResult = BearerTokenResult.constraintViolation(Set.of("scope"), Set.of(), Set.of());
            assertFalse(failureResult.isSuccessfullyAuthorized());
        }

        @Test
        @DisplayName("isNotSuccessfullyAuthorized() should return true for all non-FULLY_VERIFIED status")
        void isNotSuccessfullyAuthorizedShouldReturnTrueForNonFullyVerified() {
            var successResult = BearerTokenResult.builder()
                    .status(BearerTokenStatus.FULLY_VERIFIED)
                    .accessTokenContent(createTestToken())
                    .build();
            assertFalse(successResult.isNotSuccessfullyAuthorized());

            var failureResult = BearerTokenResult.constraintViolation(Set.of("scope"), Set.of(), Set.of());
            assertTrue(failureResult.isNotSuccessfullyAuthorized());
        }

        @Test
        @DisplayName("authorization status methods should be opposites")
        void authorizationStatusMethodsShouldBeOpposites() {
            for (BearerTokenStatus status : BearerTokenStatus.values()) {
                var result = createResultWithStatus(status);
                assertNotEquals(result.isSuccessfullyAuthorized(), result.isNotSuccessfullyAuthorized(),
                        "Methods should return opposite values for status: " + status);
            }
        }
    }

    @Nested
    @DisplayName("Direct Builder Usage")
    class DirectBuilderUsage {

        @Test
        @DisplayName("builder() should allow direct construction")
        void builderShouldAllowDirectConstruction() {
            var tokenContent = createTestToken();
            var missingScopes = Set.of("write");
            var missingRoles = Set.of("admin");
            var missingGroups = Set.of("managers");

            var result = BearerTokenResult.builder()
                    .status(BearerTokenStatus.CONSTRAINT_VIOLATION)
                    .accessTokenContent(tokenContent)
                    .missingScopes(missingScopes)
                    .missingRoles(missingRoles)
                    .missingGroups(missingGroups)
                    .build();

            assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, result.getStatus());
            assertTrue(result.getAccessTokenContent().isPresent());
            assertEquals(tokenContent, result.getAccessTokenContent().get());
            assertEquals(missingScopes, result.getMissingScopes());
            assertEquals(missingRoles, result.getMissingRoles());
            assertEquals(missingGroups, result.getMissingGroups());
        }

        @Test
        @DisplayName("builder() should use default empty sets for missing attributes")
        void builderShouldUseDefaultEmptySets() {
            var result = BearerTokenResult.builder()
                    .status(BearerTokenStatus.FULLY_VERIFIED)
                    .build();

            assertEquals(BearerTokenStatus.FULLY_VERIFIED, result.getStatus());
            assertTrue(result.getMissingScopes().isEmpty());
            assertTrue(result.getMissingRoles().isEmpty());
            assertTrue(result.getMissingGroups().isEmpty());
        }

        @Test
        @DisplayName("builder() should allow setting error details individually")
        void builderShouldAllowSettingErrorDetailsIndividually() {
            var result = BearerTokenResult.builder()
                    .status(BearerTokenStatus.PARSING_ERROR)
                    .errorEventType(EventType.INVALID_JWT_FORMAT)
                    .errorMessage("Custom error message")
                    .build();

            assertEquals(BearerTokenStatus.PARSING_ERROR, result.getStatus());
            assertTrue(result.getErrorEventType().isPresent());
            assertEquals(EventType.INVALID_JWT_FORMAT, result.getErrorEventType().get());
            assertTrue(result.getErrorMessage().isPresent());
            assertEquals("Custom error message", result.getErrorMessage().get());
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandling {

        @Test
        @DisplayName("factory methods should handle empty sets gracefully")
        void factoryMethodsShouldHandleEmptySets() {
            var emptySet = Collections.<String>emptySet();
            var result = BearerTokenResult.constraintViolation(emptySet, emptySet, emptySet);

            assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, result.getStatus());
            assertTrue(result.getMissingScopes().isEmpty());
            assertTrue(result.getMissingRoles().isEmpty());
            assertTrue(result.getMissingGroups().isEmpty());
        }
    }

    @Nested
    @DisplayName("Serialization and Equality")
    class SerializationAndEquality {

        @Test
        @DisplayName("results with same data should be equal")
        void resultsWithSameDataShouldBeEqual() {
            var result1 = BearerTokenResult.constraintViolation(Set.of("scope"), Set.of("role"), Set.of("group"));
            var result2 = BearerTokenResult.constraintViolation(Set.of("scope"), Set.of("role"), Set.of("group"));

            assertEquals(result1, result2);
            assertEquals(result1.hashCode(), result2.hashCode());
        }

        @Test
        @DisplayName("results with different data should not be equal")
        void resultsWithDifferentDataShouldNotBeEqual() {
            var result1 = BearerTokenResult.constraintViolation(Set.of("scope1"), Set.of(), Set.of());
            var result2 = BearerTokenResult.constraintViolation(Set.of("scope2"), Set.of(), Set.of());

            assertNotEquals(result1, result2);
        }

        @Test
        @DisplayName("toString should provide meaningful output")
        void toStringShouldProvideMeaningfulOutput() {
            var result = BearerTokenResult.constraintViolation(Set.of("scope"), Set.of("role"), Set.of("group"));
            var toString = result.toString();

            assertNotNull(toString);
            assertTrue(toString.contains("CONSTRAINT_VIOLATION"));
            assertTrue(toString.contains("scope"));
            assertTrue(toString.contains("role"));
            assertTrue(toString.contains("group"));
        }
    }

    @Nested
    @DisplayName("createErrorResponse() Method")
    class CreateErrorResponseMethod {

        @Test
        @DisplayName("should throw IllegalStateException when called on successfully authorized token")
        void shouldThrowExceptionForSuccessfullyAuthorizedToken() {
            var result = BearerTokenResult.builder()
                    .status(BearerTokenStatus.FULLY_VERIFIED)
                    .accessTokenContent(createTestToken())
                    .build();

            var exception = assertThrows(IllegalStateException.class, result::createErrorResponse);

            assertTrue(exception.getMessage().contains("Cannot create error response for successfully authorized token"));
            assertTrue(exception.getMessage().contains("status=" + BearerTokenStatus.FULLY_VERIFIED));
        }

        @Test
        @DisplayName("should delegate to BearerTokenResponseFactory for failed authorization")
        void shouldDelegateToResponseFactoryForFailedAuthorization() {
            var result = BearerTokenResult.constraintViolation(Set.of("read"), Set.of(), Set.of());

            var response = result.createErrorResponse();

            assertNotNull(response);
            // Detailed response testing is covered by BearerTokenResponseFactoryTest
        }
    }

    private AccessTokenContent createTestToken() {
        var holder = new TestTokenHolder(TokenType.ACCESS_TOKEN,
                ClaimControlParameter.defaultForTokenType(TokenType.ACCESS_TOKEN));
        return holder.asAccessTokenContent();
    }

    private BearerTokenResult createResultWithStatus(BearerTokenStatus status) {
        return switch (status) {
            case FULLY_VERIFIED -> BearerTokenResult.builder()
                    .status(BearerTokenStatus.FULLY_VERIFIED)
                    .accessTokenContent(createTestToken())
                    .build();
            case NO_TOKEN_GIVEN -> BearerTokenResult.noTokenGiven(Set.of(), Set.of(), Set.of());
            case PARSING_ERROR -> BearerTokenResult.parsingError(
                    new TokenValidationException(EventType.INVALID_JWT_FORMAT, "test"), Set.of(), Set.of(), Set.of());
            case CONSTRAINT_VIOLATION -> BearerTokenResult.constraintViolation(Set.of(), Set.of(), Set.of());
            case INVALID_REQUEST -> BearerTokenResult.invalidRequest("Invalid token format", Set.of(), Set.of(), Set.of());
        };
    }
}