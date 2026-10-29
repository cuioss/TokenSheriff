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

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.cache.AccessTokenCacheConfig;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.domain.context.ValidationContext;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.pipeline.validator.TokenValidationRule;
import de.cuioss.sheriff.token.validation.security.SignatureAlgorithmPreferences;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AccessTokenValidationPipeline}'s cache-hit re-validation contracts.
 * <p>
 * A cache hit must never serve a token the uncached pipeline would reject at the same instant:
 * <ul>
 *   <li><strong>Custom rules (M5):</strong> custom validation rules can be stateful (revocation,
 *   dynamic policy, rate limiting). They must run on every request — including cache hits — so a
 *   token that passed once cannot be served indefinitely after a rule flips to reject it.</li>
 *   <li><strong>Issuer time policy (V-2, V-9):</strong> every cache hit re-applies the issuing
 *   issuer's own time policy — its own clock skew for {@code exp} and its own maximum token age for
 *   {@code iat}. A token that ages past the issuer's maximum token age is rejected with
 *   {@code TOKEN_AGE_EXCEEDED}, and a token past its expiry is rejected with {@code TOKEN_EXPIRED}
 *   under its own issuer's skew, even when another configured issuer is more lenient.</li>
 * </ul>
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("AccessTokenValidationPipeline cache-hit re-validation tests")
class AccessTokenValidationPipelineTest {

    /**
     * Upper bound for waiting on a time-policy boundary to pass; the boundaries are a few seconds out.
     */
    private static final Duration BOUNDARY_WAIT = Duration.ofSeconds(10);

    @Test
    @SuppressWarnings("java:S5778")
    // AccessTokenRequest.of(rawToken) is a pure factory call that never throws
    @DisplayName("Should re-run custom rules on a cache hit and reject a previously-cached token when a rule flips (M5)")
    void shouldReRunCustomRulesOnCacheHit() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        IssuerConfig issuerConfig = tokenHolder.getIssuerConfig();
        String rawToken = tokenHolder.getRawToken();

        FlippingRule flippingRule = new FlippingRule();

        try (TokenValidator validator = TokenValidator.builder()
                     .issuerConfig(issuerConfig)
                     .tokenValidationRule(flippingRule)
                     .cacheConfig(AccessTokenCacheConfig.defaultConfig())
                     .build()) {

            // First validation: cache miss — the rule passes and the token is cached.
            AccessTokenContent first = validator.createAccessToken(AccessTokenRequest.of(rawToken));
            assertNotNull(first, "First validation should succeed and prime the cache");

            // Second validation: cache hit — the rule has flipped to reject, so the cached token
            // must be re-evaluated and rejected rather than served from cache.
            var exception = assertThrows(TokenValidationException.class,
                    () -> validator.createAccessToken(AccessTokenRequest.of(rawToken)),
                    "A cached token must be rejected once a stateful custom rule flips to reject");
            assertEquals(SecurityEventCounter.EventType.CUSTOM_RULE_REJECTED, exception.getEventType(),
                    "The rejection must originate from the custom rule");
        }

        assertEquals(2, flippingRule.callCount(),
                "The custom rule must run on both the cache-miss and the subsequent cache-hit");
    }

    @Test
    @SuppressWarnings("java:S5778")
    // AccessTokenRequest.of(rawToken) is a pure factory call that never throws
    @DisplayName("Should reject a cached token once it exceeds its issuer's maximum token age (V-9)")
    void shouldRejectCachedTokenExceedingIssuerMaxTokenAge() {
        int maxTokenAgeSeconds = 6;
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        // iat close to the age limit, exp far in the future: only the age check can reject the token
        OffsetDateTime issuedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(1);
        withTimeClaim(tokenHolder, ClaimName.ISSUED_AT, issuedAt);
        withTimeClaim(tokenHolder, ClaimName.EXPIRATION, issuedAt.plusHours(1));
        String rawToken = tokenHolder.getRawToken();
        String issuer = tokenHolder.getIssuer();

        try (TokenValidator cachingValidator = TokenValidator.builder()
                     .issuerConfig(issuerConfig(tokenHolder, issuer, 0, maxTokenAgeSeconds))
                     .cacheConfig(AccessTokenCacheConfig.defaultConfig())
                     .build()) {

            // First validation: cache miss — the token is within its age limit and gets cached.
            assertNotNull(cachingValidator.createAccessToken(AccessTokenRequest.of(rawToken)),
                    "First validation should succeed and prime the cache");

            // Second validation: cache hit — the token is still within its age limit and is served.
            assertNotNull(cachingValidator.createAccessToken(AccessTokenRequest.of(rawToken)),
                    "A still-valid cached token must be served from the cache");
            assertEquals(1, cachingValidator.getSecurityEventCounter()
                            .getCount(SecurityEventCounter.EventType.ACCESS_TOKEN_CACHE_HIT),
                    "The still-valid second validation must be a cache hit");

            // Once now - iat exceeds maxTokenAge + skew (0), the issuer policy rejects the token.
            awaitPast(issuedAt.plusSeconds(maxTokenAgeSeconds + 1L));

            // Third validation: cache hit — the cached token must be rejected, not served.
            var cachedRejection = assertThrows(TokenValidationException.class,
                    () -> cachingValidator.createAccessToken(AccessTokenRequest.of(rawToken)),
                    "A cached token past its issuer's maximum token age must be rejected");
            assertEquals(SecurityEventCounter.EventType.TOKEN_AGE_EXCEEDED, cachedRejection.getEventType());
            SecurityEventCounter cachingCounter = cachingValidator.getSecurityEventCounter();
            assertEquals(1, cachingCounter.getCount(SecurityEventCounter.EventType.TOKEN_AGE_EXCEEDED),
                    "The cache-hit rejection must be counted exactly once");
            assertEquals(1, cachingCounter.getCount(SecurityEventCounter.EventType.ACCESS_TOKEN_CACHE_HIT),
                    "Only the valid second validation may be counted as a cache hit");
        }

        // The uncached pipeline rejects the same token at this point under the same event type.
        try (TokenValidator uncachedValidator = TokenValidator.builder()
                     .issuerConfig(issuerConfig(tokenHolder, issuer, 0, maxTokenAgeSeconds))
                     .cacheConfig(AccessTokenCacheConfig.disabled())
                     .build()) {
            var uncachedRejection = assertThrows(TokenValidationException.class,
                    () -> uncachedValidator.createAccessToken(AccessTokenRequest.of(rawToken)));
            assertEquals(SecurityEventCounter.EventType.TOKEN_AGE_EXCEEDED, uncachedRejection.getEventType(),
                    "The cache-hit rejection must match the uncached rejection");
            assertEquals(1, uncachedValidator.getSecurityEventCounter()
                    .getCount(SecurityEventCounter.EventType.TOKEN_AGE_EXCEEDED));
        }
    }

    @Test
    @SuppressWarnings("java:S5778")
    // AccessTokenRequest.of(rawToken) is a pure factory call that never throws
    @DisplayName("Should reject an expired cached token under its own issuer's skew, not the most lenient one (V-2)")
    void shouldRejectExpiredCachedTokenUnderOwnIssuerSkew() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        // exp a moment ahead; the token's issuer tolerates no skew, the other issuer 60 s
        OffsetDateTime expiration = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(5);
        withTimeClaim(tokenHolder, ClaimName.EXPIRATION, expiration);
        String rawToken = tokenHolder.getRawToken();
        String issuer = tokenHolder.getIssuer();
        String lenientIssuer = issuer + "-lenient";

        try (TokenValidator cachingValidator = TokenValidator.builder()
                     .issuerConfig(issuerConfig(tokenHolder, issuer, 0, null))
                     .issuerConfig(issuerConfig(tokenHolder, lenientIssuer, 60, null))
                     .cacheConfig(AccessTokenCacheConfig.defaultConfig())
                     .build()) {

            // First validation: cache miss — the token is not yet expired and gets cached.
            assertNotNull(cachingValidator.createAccessToken(AccessTokenRequest.of(rawToken)),
                    "First validation should succeed and prime the cache");

            // Second validation: cache hit — the token is not yet expired and is served.
            assertNotNull(cachingValidator.createAccessToken(AccessTokenRequest.of(rawToken)),
                    "A still-valid cached token must be served from the cache");
            assertEquals(1, cachingValidator.getSecurityEventCounter()
                            .getCount(SecurityEventCounter.EventType.ACCESS_TOKEN_CACHE_HIT),
                    "The still-valid second validation must be a cache hit");

            // Past exp, but well within the lenient issuer's 60 s skew.
            awaitPast(expiration);

            // Third validation: cache hit — the strict issuer's own skew (0 s) applies.
            var cachedRejection = assertThrows(TokenValidationException.class,
                    () -> cachingValidator.createAccessToken(AccessTokenRequest.of(rawToken)),
                    "A cached token past exp must be rejected under its own issuer's skew");
            assertEquals(SecurityEventCounter.EventType.TOKEN_EXPIRED, cachedRejection.getEventType());
            SecurityEventCounter cachingCounter = cachingValidator.getSecurityEventCounter();
            assertEquals(1, cachingCounter.getCount(SecurityEventCounter.EventType.TOKEN_EXPIRED),
                    "The cache-hit rejection must be counted exactly once");
            assertEquals(1, cachingCounter.getCount(SecurityEventCounter.EventType.ACCESS_TOKEN_CACHE_HIT),
                    "Only the valid second validation may be counted as a cache hit");
        }

        // The uncached pipeline rejects the same token at this point under the same event type.
        try (TokenValidator uncachedValidator = TokenValidator.builder()
                     .issuerConfig(issuerConfig(tokenHolder, issuer, 0, null))
                     .issuerConfig(issuerConfig(tokenHolder, lenientIssuer, 60, null))
                     .cacheConfig(AccessTokenCacheConfig.disabled())
                     .build()) {
            var uncachedRejection = assertThrows(TokenValidationException.class,
                    () -> uncachedValidator.createAccessToken(AccessTokenRequest.of(rawToken)));
            assertEquals(SecurityEventCounter.EventType.TOKEN_EXPIRED, uncachedRejection.getEventType(),
                    "The cache-hit rejection must match the uncached rejection");
            assertEquals(1, uncachedValidator.getSecurityEventCounter()
                    .getCount(SecurityEventCounter.EventType.TOKEN_EXPIRED));
        }
    }

    /**
     * Waits (bounded, polling) until the wall clock is strictly after the given instant.
     */
    private static void awaitPast(OffsetDateTime instant) {
        await().atMost(BOUNDARY_WAIT)
                .pollInterval(Duration.ofMillis(50))
                .until(() -> OffsetDateTime.now().isAfter(instant));
    }

    private static void withTimeClaim(TestTokenHolder tokenHolder, ClaimName claimName, OffsetDateTime value) {
        tokenHolder.withClaim(claimName.getName(),
                ClaimValue.forDateTime(String.valueOf(value.toEpochSecond()), value));
    }

    /**
     * Builds an issuer configuration that accepts the token holder's signing key under the given
     * issuer identifier and time policy. A fresh instance per validator, since a validator owns
     * the JWKS loaders of its issuer configurations.
     */
    private static IssuerConfig issuerConfig(TestTokenHolder tokenHolder, String issuerIdentifier,
            int clockSkewSeconds, Integer maxTokenAgeSeconds) {
        return IssuerConfig.builder()
                .issuerIdentifier(issuerIdentifier)
                .jwksContent(InMemoryKeyMaterialHandler.createJwks(tokenHolder.getSigningAlgorithm(), tokenHolder.getKeyId()))
                .algorithmPreferences(new SignatureAlgorithmPreferences())
                .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                .expectedClientId(TestTokenHolder.TEST_CLIENT_ID)
                .clockSkewSeconds(clockSkewSeconds)
                .maxTokenAgeSeconds(maxTokenAgeSeconds)
                .build();
    }

    /**
     * A stateful custom rule that accepts its first invocation and rejects every subsequent one,
     * modelling a rule whose decision changes between requests (e.g. a token revoked after issuance).
     */
    private static final class FlippingRule implements TokenValidationRule {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public void validate(AccessTokenContent token, ValidationContext context) {
            if (calls.getAndIncrement() > 0) {
                throw new TokenValidationException(SecurityEventCounter.EventType.CUSTOM_RULE_REJECTED,
                        "Custom rule flipped to reject on re-validation");
            }
        }

        int callCount() {
            return calls.get();
        }
    }
}
