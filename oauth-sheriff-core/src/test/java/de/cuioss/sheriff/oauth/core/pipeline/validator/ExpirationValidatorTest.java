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
package de.cuioss.sheriff.oauth.core.pipeline.validator;

import com.dslplatform.json.DslJson;
import de.cuioss.sheriff.oauth.core.ParserConfig;
import de.cuioss.sheriff.oauth.core.domain.claim.ClaimName;
import de.cuioss.sheriff.oauth.core.domain.claim.ClaimValue;
import de.cuioss.sheriff.oauth.core.domain.context.ValidationContext;
import de.cuioss.sheriff.oauth.core.domain.token.AccessTokenContent;
import de.cuioss.sheriff.oauth.core.exception.TokenValidationException;
import de.cuioss.sheriff.oauth.core.json.MapRepresentation;
import de.cuioss.sheriff.oauth.core.security.SecurityEventCounter;
import de.cuioss.sheriff.oauth.core.test.TestTokenHolder;
import de.cuioss.sheriff.oauth.core.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for {@link ExpirationValidator}.
 *
 * @author Oliver Wolff
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("ExpirationValidator")
class ExpirationValidatorTest {

    /**
     * Creates an empty MapRepresentation for tests that don't need specific payload data.
     */
    private static MapRepresentation createEmptyMapRepresentation() {
        try {
            DslJson<Object> dslJson = ParserConfig.builder().build().getDslJson();
            return MapRepresentation.fromJson(dslJson, "{}");
        } catch (IOException e) {
            throw new AssertionError("Failed to create empty MapRepresentation", e);
        }
    }

    private SecurityEventCounter securityEventCounter;
    private ExpirationValidator validator;
    private ValidationContext context;

    @BeforeEach
    void setup() {
        securityEventCounter = new SecurityEventCounter();
        validator = new ExpirationValidator(securityEventCounter);
        context = new ValidationContext(60, null); // 60 seconds clock skew
    }

    @Test
    @DisplayName("Should pass validation for non-expired token")
    void shouldPassValidationForNonExpiredToken() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        OffsetDateTime futureExpiration = OffsetDateTime.now().plusHours(1);
        tokenHolder.withClaim(ClaimName.EXPIRATION.getName(),
                ClaimValue.forDateTime(String.valueOf(futureExpiration.toEpochSecond()), futureExpiration));
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), createEmptyMapRepresentation());

        assertDoesNotThrow(() -> validator.validateNotExpired(token, context));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_EXPIRED));
    }

    @Test
    @DisplayName("Should fail validation for expired token")
    void shouldFailValidationForExpiredToken() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        OffsetDateTime pastExpiration = OffsetDateTime.now().minusHours(1);
        tokenHolder.withClaim(ClaimName.EXPIRATION.getName(),
                ClaimValue.forDateTime(String.valueOf(pastExpiration.toEpochSecond()), pastExpiration));
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), createEmptyMapRepresentation());

        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> validator.validateNotExpired(token, context));
        assertEquals(SecurityEventCounter.EventType.TOKEN_EXPIRED, exception.getEventType());
        assertTrue(exception.getMessage().contains("Token is expired"));
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_EXPIRED));
    }

    @Test
    @DisplayName("Should pass validation when no not-before claim present")
    void shouldPassValidationWhenNoNotBeforeClaimPresent() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        Map<String, ClaimValue> claims = new HashMap<>(tokenHolder.getClaims());
        claims.remove(ClaimName.NOT_BEFORE.getName());
        AccessTokenContent token = new AccessTokenContent(claims, tokenHolder.getRawToken(), createEmptyMapRepresentation());

        assertDoesNotThrow(() -> validator.validateNotBefore(token, context));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_NBF_FUTURE));
    }

    @Test
    @DisplayName("Should pass validation for not-before time in the past")
    void shouldPassValidationForNotBeforeTimeInThePast() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        OffsetDateTime pastNotBefore = OffsetDateTime.now().minusHours(1);
        tokenHolder.withClaim(ClaimName.NOT_BEFORE.getName(),
                ClaimValue.forDateTime(String.valueOf(pastNotBefore.toEpochSecond()), pastNotBefore));
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), createEmptyMapRepresentation());

        assertDoesNotThrow(() -> validator.validateNotBefore(token, context));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_NBF_FUTURE));
    }

    @Test
    @DisplayName("Should pass validation for not-before time within clock skew tolerance")
    void shouldPassValidationForNotBeforeTimeWithinClockSkewTolerance() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        OffsetDateTime nearFutureNotBefore = OffsetDateTime.now().plusSeconds(30);
        tokenHolder.withClaim(ClaimName.NOT_BEFORE.getName(),
                ClaimValue.forDateTime(String.valueOf(nearFutureNotBefore.toEpochSecond()), nearFutureNotBefore));
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), createEmptyMapRepresentation());

        assertDoesNotThrow(() -> validator.validateNotBefore(token, context));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_NBF_FUTURE));
    }

    @Test
    @DisplayName("Should fail validation for not-before time beyond clock skew tolerance")
    void shouldFailValidationForNotBeforeTimeBeyondClockSkewTolerance() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        OffsetDateTime farFutureNotBefore = OffsetDateTime.now().plusSeconds(120);
        tokenHolder.withClaim(ClaimName.NOT_BEFORE.getName(),
                ClaimValue.forDateTime(String.valueOf(farFutureNotBefore.toEpochSecond()), farFutureNotBefore));
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), createEmptyMapRepresentation());

        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> validator.validateNotBefore(token, context));
        assertEquals(SecurityEventCounter.EventType.TOKEN_NBF_FUTURE, exception.getEventType());
        assertTrue(exception.getMessage().contains("Token not valid yet"));
        assertTrue(exception.getMessage().contains("more than 60 seconds in the future"));
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_NBF_FUTURE));
    }

    @Test
    @DisplayName("Should pass validation at exact clock skew boundary")
    void shouldPassValidationAtExactClockSkewBoundary() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        // Use a fixed time for both the context and the token to avoid timing issues
        OffsetDateTime fixedTime = OffsetDateTime.now();
        OffsetDateTime exactBoundaryNotBefore = fixedTime.plusSeconds(60);

        // Create a context with the fixed time
        ValidationContext testContext = ValidationContext.forTesting(fixedTime, 60, null);

        tokenHolder.withClaim(ClaimName.NOT_BEFORE.getName(),
                ClaimValue.forDateTime(String.valueOf(exactBoundaryNotBefore.toEpochSecond()), exactBoundaryNotBefore));
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), createEmptyMapRepresentation());

        assertDoesNotThrow(() -> validator.validateNotBefore(token, testContext));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_NBF_FUTURE));
    }

    @Test
    @DisplayName("Should skip token age validation when disabled")
    void shouldSkipTokenAgeValidationWhenDisabled() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        OffsetDateTime fixedTime = OffsetDateTime.now();
        OffsetDateTime issuedAt = fixedTime.minusHours(24); // very old token
        tokenHolder.withClaim(ClaimName.ISSUED_AT.getName(),
                ClaimValue.forDateTime(String.valueOf(issuedAt.toEpochSecond()), issuedAt));
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), createEmptyMapRepresentation());

        // Context without maxTokenAgeSeconds -> validation disabled
        ValidationContext noAgeContext = ValidationContext.forTesting(fixedTime, 60, null);
        assertDoesNotThrow(() -> validator.validateTokenAge(token, noAgeContext));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_AGE_EXCEEDED));
    }

    @Test
    @DisplayName("Should pass token age validation for young token")
    void shouldPassTokenAgeValidationForYoungToken() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        OffsetDateTime fixedTime = OffsetDateTime.now();
        OffsetDateTime issuedAt = fixedTime.minusSeconds(100); // 100s old, max age 300 + 60 skew
        tokenHolder.withClaim(ClaimName.ISSUED_AT.getName(),
                ClaimValue.forDateTime(String.valueOf(issuedAt.toEpochSecond()), issuedAt));
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), createEmptyMapRepresentation());

        ValidationContext ageContext = ValidationContext.forTesting(fixedTime, 60, 300);
        assertDoesNotThrow(() -> validator.validateTokenAge(token, ageContext));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_AGE_EXCEEDED));
    }

    @Test
    @DisplayName("Should fail token age validation for too old token")
    void shouldFailTokenAgeValidationForTooOldToken() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        OffsetDateTime fixedTime = OffsetDateTime.now();
        OffsetDateTime issuedAt = fixedTime.minusSeconds(400); // 400s old, max age 300 + 60 skew = 360
        tokenHolder.withClaim(ClaimName.ISSUED_AT.getName(),
                ClaimValue.forDateTime(String.valueOf(issuedAt.toEpochSecond()), issuedAt));
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), createEmptyMapRepresentation());

        ValidationContext ageContext = ValidationContext.forTesting(fixedTime, 60, 300);
        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> validator.validateTokenAge(token, ageContext));
        assertEquals(SecurityEventCounter.EventType.TOKEN_AGE_EXCEEDED, exception.getEventType());
        assertTrue(exception.getMessage().contains("Token is too old"));
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_AGE_EXCEEDED));
    }

    @Test
    @DisplayName("Should handle edge case of current time as not-before")
    void shouldHandleEdgeCaseOfCurrentTimeAsNotBefore() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        OffsetDateTime currentTime = OffsetDateTime.now();
        tokenHolder.withClaim(ClaimName.NOT_BEFORE.getName(),
                ClaimValue.forDateTime(String.valueOf(currentTime.toEpochSecond()), currentTime));
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), createEmptyMapRepresentation());

        assertDoesNotThrow(() -> validator.validateNotBefore(token, context));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_NBF_FUTURE));
    }
}