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
package de.cuioss.sheriff.token.validation;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.JwksType;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.cache.AccessTokenCacheConfig;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.domain.context.IdTokenRequest;
import de.cuioss.sheriff.token.validation.domain.context.RefreshTokenRequest;
import de.cuioss.sheriff.token.validation.dpop.DpopConfig;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.jwks.JwksLoader;
import de.cuioss.sheriff.token.validation.jwks.key.KeyInfo;
import de.cuioss.sheriff.token.validation.test.InMemoryJWKSFactory;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.sheriff.token.validation.test.junit.TestTokenSource;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for {@link TokenValidator}.
 * <p>
 * Verifies requirements:
 * <ul>
 *   <li><a href="../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-1.1">VALIDATION-1.1: Token Structure</a></li>
 *   <li><a href="../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-1.2">VALIDATION-1.2: Token Types</a></li>
 *   <li><a href="../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-1.3">VALIDATION-1.3: Signature Validation</a></li>
 *   <li><a href="../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-3.1">VALIDATION-3.1: Issuer Configuration</a></li>
 *   <li><a href="../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-3.2">VALIDATION-3.2: Issuer Selection</a></li>
 *   <li><a href="../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-3.3">VALIDATION-3.3: Issuer Validation</a></li>
 *   <li><a href="../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-5.1">VALIDATION-5.1: Token Parsing Methods</a></li>
 *   <li><a href="../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-6.1">VALIDATION-6.1: Configuration Flexibility</a></li>
 *   <li><a href="../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-7.1">VALIDATION-7.1: Log Levels</a></li>
 *   <li><a href="../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-7.2">VALIDATION-7.2: Log Content</a></li>
 *   <li><a href="../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-8.1">VALIDATION-8.1: Token Size Limits</a></li>
 *   <li><a href="../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-8.2">VALIDATION-8.2: Safe Parsing</a></li>
 * </ul>
 *
 * @author Oliver Wolff
 */
@EnableTestLogger
@EnableGeneratorController
class TokenValidatorTest {
    private TokenValidator tokenValidator;
    private IssuerConfig issuerConfig;

    private IssuerConfig createDefaultIssuerConfig() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        return tokenHolder.getIssuerConfig();
    }

    @BeforeEach
    void setUp() {
        issuerConfig = createDefaultIssuerConfig();
        tokenValidator = TokenValidator.builder().issuerConfig(issuerConfig).build();
    }

    @Test
    @DisplayName("Should log TOKEN_FACTORY_INITIALIZED when creating TokenValidator")
    void shouldLogTokenFactoryInitialized() {
        // Given/When - create a new TokenValidator
        TokenValidator newValidator = TokenValidator.builder().issuerConfig(issuerConfig).build();

        // Then
        assertNotNull(newValidator);
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                JWTValidationLogMessages.INFO.TOKEN_FACTORY_INITIALIZED.resolveIdentifierString());
    }

    @Test
    @DisplayName("Should run every release step, propagate the later close failure and keep the earlier as suppressed")
    void shouldKeepEveryCloseFailureWhenReleasing() {
        // The release order close() uses: the pipeline, then the DPoP replay protection, then the
        // JWKS loaders. Both the first and the second step fail; neither failure may be lost.
        SecurityException pipelineFailure = new SecurityException("pipeline shutdown refused");
        SecurityException dpopFailure = new SecurityException("dpop shutdown refused");
        Set<String> ran = new HashSet<>();

        SecurityException thrown = assertThrows(SecurityException.class, () -> TokenValidator.releaseInOrder(
                () -> {
                    ran.add("pipeline");
                    throw pipelineFailure;
                },
                () -> {
                    ran.add("dpop");
                    throw dpopFailure;
                },
                () -> ran.add("loaders")));

        assertSame(dpopFailure, thrown, "The later failure must be the one propagated");
        assertArrayEquals(new Throwable[]{pipelineFailure}, thrown.getSuppressed(),
                "The earlier failure must be attached as suppressed, not dropped");
        assertEquals(Set.of("pipeline", "dpop", "loaders"), ran, "Every release step must run");
    }

    @Test
    @DisplayName("Should attach every earlier close failure to the last one when all release steps fail")
    void shouldAttachEveryEarlierCloseFailureToTheLastOne() {
        IllegalStateException first = new IllegalStateException("first");
        IllegalStateException second = new IllegalStateException("second");
        IllegalStateException third = new IllegalStateException("third");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> TokenValidator.releaseInOrder(
                () -> {
                    throw first;
                },
                () -> {
                    throw second;
                },
                () -> {
                    throw third;
                }));

        assertSame(third, thrown, "The last failure must be the one propagated");
        assertArrayEquals(new Throwable[]{first, second}, thrown.getSuppressed(),
                "Every earlier failure must be attached to the last one, in release order");
    }

    @Test
    @DisplayName("Should run the later release steps after an Error and rethrow the Error when it is the last failure")
    void shouldKeepReleasingAfterAnError() {
        AssertionError pipelineError = new AssertionError("pipeline shutdown broke");
        Set<String> ran = new HashSet<>();

        AssertionError thrown = assertThrows(AssertionError.class, () -> TokenValidator.releaseInOrder(
                () -> {
                    ran.add("pipeline");
                    throw pipelineError;
                },
                () -> ran.add("dpop"),
                () -> ran.add("loaders")));

        assertSame(pipelineError, thrown, "The Error must be rethrown unwrapped");
        assertEquals(0, thrown.getSuppressed().length, "A single failure carries nothing suppressed");
        assertEquals(Set.of("pipeline", "dpop", "loaders"), ran, "An Error must not stop the later release steps");
    }

    @Test
    @DisplayName("Should propagate a later failure with an earlier Error attached as suppressed")
    void shouldAttachAnEarlierErrorToTheLaterFailure() {
        StackOverflowError pipelineError = new StackOverflowError("pipeline shutdown overflowed");
        IllegalStateException loaderFailure = new IllegalStateException("loader close refused");
        Set<String> ran = new HashSet<>();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> TokenValidator.releaseInOrder(
                () -> {
                    ran.add("pipeline");
                    throw pipelineError;
                },
                () -> ran.add("dpop"),
                () -> {
                    ran.add("loaders");
                    throw loaderFailure;
                }));

        assertSame(loaderFailure, thrown, "The later failure must be the one propagated");
        assertArrayEquals(new Throwable[]{pipelineError}, thrown.getSuppressed(),
                "The earlier Error must be attached as suppressed, not dropped");
        assertEquals(Set.of("pipeline", "dpop", "loaders"), ran, "Every release step must run");
    }

    @Test
    @DisplayName("Should propagate the original failure when two release steps throw the same instance")
    void shouldNotAttachAFailureToItself() {
        IllegalStateException shared = new IllegalStateException("shared executor shutdown refused");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> TokenValidator.releaseInOrder(
                () -> {
                    throw shared;
                },
                () -> {
                    throw shared;
                }));

        assertSame(shared, thrown, "The original failure must propagate, not an IllegalArgumentException");
        assertEquals(0, thrown.getSuppressed().length, "A failure must never be attached to itself");
    }

    @Test
    @DisplayName("Should ignore disabled issuer configurations during construction")
    void shouldIgnoreDisabledIssuerConfigs() {
        // Disabled configs skip build-time validation and have no JwksLoader —
        // their presence must not break validator construction
        IssuerConfig disabled = IssuerConfig.builder().enabled(false).build();

        TokenValidator validator = TokenValidator.builder()
                .issuerConfig(issuerConfig)
                .issuerConfig(disabled)
                .build();

        assertNotNull(validator);
    }

    @Test
    @DisplayName("Should reject construction when all issuer configurations are disabled")
    void shouldRejectAllDisabledIssuerConfigs() {
        IssuerConfig disabled = IssuerConfig.builder().enabled(false).build();
        var builder = TokenValidator.builder().issuerConfig(disabled);

        var exception = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(exception.getMessage().contains("enabled issuer configuration"));
    }

    @Nested
    class TokenCreationTests {

        @ParameterizedTest
        @TestTokenSource(value = TokenType.REFRESH_TOKEN, count = 3)
        @DisplayName("Create refresh token successfully")
        void shouldCreateRefreshToken(TestTokenHolder tokenHolder) {
            var token = tokenHolder.getRawToken();

            var parsedToken = tokenValidator.createRefreshToken(RefreshTokenRequest.of(token));

            assertNotNull(parsedToken, "Parsed token should not be null");
            assertEquals(token, parsedToken.getRawToken(), "Raw token should match");
            assertEquals(TokenType.REFRESH_TOKEN, parsedToken.getTokenType(), "Token type should be REFRESH_TOKEN");
        }

        @Test
        @DisplayName("Create refresh token with empty claims for non-JWT")
        void shouldCreateRefreshTokenWithEmptyClaimsForNonJwtToken() {
            var token = "not-a-jwt-validation";
            var parsedToken = tokenValidator.createRefreshToken(RefreshTokenRequest.of(token));

            assertNotNull(parsedToken, "Parsed token should not be null");
            assertEquals(token, parsedToken.getRawToken(), "Raw token should match");
            assertEquals(TokenType.REFRESH_TOKEN, parsedToken.getTokenType(), "Token type should be REFRESH_TOKEN");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 2)
        @DisplayName("Fail access token validation with invalid issuer")
        void shouldFailAccessTokenValidationWithInvalidIssuer(TestTokenHolder tokenHolder) {
            tokenHolder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("invalid-issuer"));
            var token = tokenHolder.getRawToken();

            var request = AccessTokenRequest.of(token);
            assertThrows(TokenValidationException.class, () -> tokenValidator.createAccessToken(request),
                    "Should throw exception for invalid issuer");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ID_TOKEN, count = 2)
        @DisplayName("Fail ID token validation with invalid issuer")
        void shouldFailIdTokenValidationWithInvalidIssuer(TestTokenHolder tokenHolder) {
            tokenHolder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("invalid-issuer"));
            var token = tokenHolder.getRawToken();

            var request = IdTokenRequest.of(token);
            assertThrows(TokenValidationException.class, () -> tokenValidator.createIdToken(request),
                    "Should throw exception for invalid issuer");
        }
    }

    @Nested
    class TokenSizeValidationTests {

        @Test
        @DisplayName("Respect custom token size limits")
        void shouldRespectCustomTokenSizeLimits() {
            int customMaxSize = 1024;
            String largeToken = "a".repeat(customMaxSize + 1);

            ParserConfig customConfig = ParserConfig.builder()
                    .maxTokenSize(customMaxSize)
                    .build();
            var factory = TokenValidator.builder().parserConfig(customConfig).issuerConfig(issuerConfig).build();

            var request = AccessTokenRequest.of(largeToken);
            var exception = assertThrows(TokenValidationException.class,
                    () -> factory.createAccessToken(request));

            assertEquals(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED, exception.getEventType(),
                    "Should indicate token size exceeded");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 2)
        @DisplayName("Respect custom payload size limits")
        void shouldRespectCustomPayloadSizeLimits(TestTokenHolder tokenHolder) {
            ParserConfig customConfig = ParserConfig.builder()
                    .maxPayloadSize(100)
                    .build();
            var factory = TokenValidator.builder().parserConfig(customConfig).issuerConfig(issuerConfig).build();

            tokenHolder.withClaim("large-claim", ClaimValue.forPlainString("a".repeat(200)));
            String token = tokenHolder.getRawToken();

            var request = AccessTokenRequest.of(token);
            var exception = assertThrows(TokenValidationException.class,
                    () -> factory.createAccessToken(request));

            assertEquals(SecurityEventCounter.EventType.DECODED_PART_SIZE_EXCEEDED, exception.getEventType(),
                    "Should indicate payload size exceeded");
        }
    }

    @Nested
    class TokenValidationErrorTests {

        @Test
        @DisplayName("Handle empty or blank token strings")
        void shouldHandleEmptyOrBlankTokenStrings() {
            var emptyRequest = AccessTokenRequest.of("");
            var emptyException = assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(emptyRequest));
            assertEquals(SecurityEventCounter.EventType.TOKEN_EMPTY, emptyException.getEventType(),
                    "Should indicate empty token");

            var blankRequest = AccessTokenRequest.of("   ");
            var blankException = assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(blankRequest));
            assertEquals(SecurityEventCounter.EventType.TOKEN_EMPTY, blankException.getEventType(),
                    "Should indicate empty token");
        }

        @Test
        @DisplayName("Handle invalid token format")
        void shouldHandleInvalidTokenFormat() {
            var invalidToken = Generators.letterStrings(10, 20).next();

            var request = AccessTokenRequest.of(invalidToken);
            var exception = assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(request));

            assertEquals(SecurityEventCounter.EventType.INVALID_JWT_FORMAT, exception.getEventType(),
                    "Should indicate invalid JWT format");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 2)
        @DisplayName("Handle unknown issuer")
        void shouldHandleUnknownIssuer(TestTokenHolder tokenHolder) {
            tokenHolder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("https://unknown-issuer.com"));
            String token = tokenHolder.getRawToken();

            var request = AccessTokenRequest.of(token);
            var exception = assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(request));

            assertEquals(SecurityEventCounter.EventType.NO_ISSUER_CONFIG, exception.getEventType(),
                    "Should indicate no issuer config");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    JWTValidationLogMessages.WARN.NO_ISSUER_CONFIG.resolveIdentifierString());
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 2)
        @DisplayName("Wrap malformed claim values as TokenValidationException")
        void shouldWrapMalformedClaimAsTokenValidationException(TestTokenHolder tokenHolder) {
            // scope must be a string or array; a numeric value (serialized from a DATETIME
            // claim) is malformed and makes ScopeMapper throw IllegalArgumentException,
            // which must not escape from TokenValidator
            OffsetDateTime anyTime = OffsetDateTime.now();
            tokenHolder.withClaim(ClaimName.SCOPE.getName(),
                    ClaimValue.forDateTime(String.valueOf(anyTime.toEpochSecond()), anyTime));
            String token = tokenHolder.getRawToken();

            var request = AccessTokenRequest.of(token);
            var exception = assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(request),
                    "Malformed claim must surface as TokenValidationException");

            assertTrue(exception.getMessage().contains("scope"),
                    "Exception message should name the malformed claim: " + exception.getMessage());
        }
    }

    @Nested
    class TokenLoggingTests {
        private static final String INVALID_TOKEN = "invalid.validation.string";
        private static final String EMPTY_TOKEN = "";

        @Test
        @DisplayName("Log warning when token is empty")
        void shouldLogWarningWhenTokenIsEmpty() {
            var emptyRequest = AccessTokenRequest.of(EMPTY_TOKEN);
            assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(emptyRequest));

            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, JWTValidationLogMessages.WARN.TOKEN_IS_EMPTY.resolveIdentifierString());
        }

        @Test
        @DisplayName("Log warning when token format is invalid")
        void shouldLogWarningWhenTokenFormatIsInvalid() {
            var invalidRequest = AccessTokenRequest.of(INVALID_TOKEN);
            assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(invalidRequest));

            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, JWTValidationLogMessages.WARN.FAILED_TO_DECODE_JWT.resolveIdentifierString());
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 2)
        @DisplayName("Log warning when token validation fails")
        void shouldLogWarningWhenTokenValidationFails(TestTokenHolder tokenHolder) {
            tokenHolder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("unknown-issuer"));
            String token = tokenHolder.getRawToken();

            var request = AccessTokenRequest.of(token);
            assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(request));

            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "No configuration found for issuer");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 2)
        @DisplayName("Log warning when token is missing claims")
        void shouldLogWarningWhenTokenIsMissingClaims(TestTokenHolder tokenHolder) {
            tokenHolder.withoutClaim(ClaimName.SUBJECT.getName());
            String validToken = tokenHolder.getRawToken();

            var request = AccessTokenRequest.of(validToken);
            var exception = assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(request));

            assertEquals(SecurityEventCounter.EventType.MISSING_CLAIM, exception.getEventType(),
                    "Should indicate missing claim");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "missing required claim");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ID_TOKEN, count = 2)
        @DisplayName("Log warning when ID token is missing claims")
        void shouldLogWarningWhenIdTokenIsMissingClaims(TestTokenHolder tokenHolder) {
            tokenHolder.withoutClaim(ClaimName.AUDIENCE.getName());
            String validToken = tokenHolder.getRawToken();

            var idRequest = IdTokenRequest.of(validToken);
            var exception = assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createIdToken(idRequest));

            assertEquals(SecurityEventCounter.EventType.MISSING_CLAIM, exception.getEventType(),
                    "Should indicate missing claim");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "missing required claim");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 2)
        @DisplayName("Log warning when key is not found")
        void shouldLogWarningWhenKeyIsNotFound(TestTokenHolder tokenHolder) {
            String token = tokenHolder.getRawToken();

            String issuer = TestTokenHolder.TEST_ISSUER;
            if (tokenHolder.getClaims().containsKey(ClaimName.ISSUER.getName())) {
                issuer = tokenHolder.getClaims().get(ClaimName.ISSUER.getName()).getOriginalString();
            }

            String jwksContent = InMemoryJWKSFactory.createValidJwksWithKeyId("different-key-id");

            IssuerConfig newIssuerConfig = IssuerConfig.builder()
                    .issuerIdentifier(issuer)
                    .jwksContent(jwksContent)
                    .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                    .expectedClientId(TestTokenHolder.TEST_CLIENT_ID)
                    .build();

            TokenValidator newTokenValidator = TokenValidator.builder().issuerConfig(newIssuerConfig).build();

            var request = AccessTokenRequest.of(token);
            var exception = assertThrows(TokenValidationException.class,
                    () -> newTokenValidator.createAccessToken(request));

            assertEquals(SecurityEventCounter.EventType.KEY_NOT_FOUND, exception.getEventType(),
                    "Should indicate key not found");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "key");
        }

    }

    @Nested
    @DisplayName("DPoP Lifecycle Tests")
    class DpopLifecycleTests {

        @Test
        @DisplayName("Should create TokenValidator with DPoP config and close cleanly")
        void shouldCreateAndCloseWithDpopConfig() {
            IssuerConfig dpopIssuerConfig = IssuerConfig.builder()
                    .issuerIdentifier("https://dpop-issuer.example.com")
                    .dpopConfig(DpopConfig.builder().build())
                    .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                    .audienceValidationDisabled(true)
                    .build();

            try (var dpopValidator = TokenValidator.builder()
                         .issuerConfig(dpopIssuerConfig)
                         .build()) {
                assertNotNull(dpopValidator);
                assertNotNull(dpopValidator.getSecurityEventCounter());
                assertNotNull(dpopValidator.getPerformanceMonitor());
            }
        }

        @Test
        @DisplayName("Should close cleanly without DPoP config")
        void shouldCloseWithoutDpopConfig() {
            try (var validator = TokenValidator.builder()
                         .issuerConfig(issuerConfig)
                         .build()) {
                assertNotNull(validator);
            }
        }

        @Test
        @DisplayName("Should create TokenValidator with multiple DPoP-enabled issuers")
        void shouldCreateWithMultipleDpopIssuers() {
            IssuerConfig issuer1 = IssuerConfig.builder()
                    .issuerIdentifier("https://issuer1.example.com")
                    .dpopConfig(DpopConfig.builder().nonceCacheSize(5000).nonceCacheTtlSeconds(120).build())
                    .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                    .audienceValidationDisabled(true)
                    .build();
            IssuerConfig issuer2 = IssuerConfig.builder()
                    .issuerIdentifier("https://issuer2.example.com")
                    .dpopConfig(DpopConfig.builder().nonceCacheSize(8000).nonceCacheTtlSeconds(200).build())
                    .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                    .audienceValidationDisabled(true)
                    .build();

            try (var multiValidator = TokenValidator.builder()
                         .issuerConfig(issuer1)
                         .issuerConfig(issuer2)
                         .build()) {
                assertNotNull(multiValidator);
            }
        }

        @Test
        @DisplayName("A failing pipeline shutdown still releases the DPoP replay protection and the JWKS loaders")
        void failingCloseStillReleasesRemainingResources() {
            RecordingCloseableLoader loader = new RecordingCloseableLoader();
            CancelFailingExecutor cacheExecutor = new CancelFailingExecutor();
            Set<Thread> dpopThreadsBefore = dpopEvictionThreads();
            TokenValidator validator = TokenValidator.builder()
                    .issuerConfig(IssuerConfig.builder()
                            .issuerIdentifier(RecordingCloseableLoader.ISSUER)
                            .dpopConfig(DpopConfig.builder().build())
                            .jwksLoader(loader)
                            .audienceValidationDisabled(true)
                            .build())
                    .cacheConfig(AccessTokenCacheConfig.builder()
                            .maxSize(10)
                            .evictionIntervalSeconds(300L)
                            .scheduledExecutorService(cacheExecutor)
                            .build())
                    .build();
            Set<Thread> dpopThreads = new HashSet<>(dpopEvictionThreads());
            dpopThreads.removeAll(dpopThreadsBefore);

            try {
                assertFalse(dpopThreads.isEmpty(), "the DPoP replay protection runs its eviction thread before close()");

                SecurityException failure = assertThrows(SecurityException.class, validator::close,
                        "the failure of the pipeline shutdown is still reported to the caller");

                assertEquals(CancelFailingExecutor.FAILURE_MESSAGE, failure.getMessage());
                assertTrue(loader.isClosed(), "the JWKS loader must be closed despite the failing pipeline shutdown");
                Awaitility.await().atMost(Duration.ofSeconds(5))
                        .until(() -> dpopThreads.stream().noneMatch(Thread::isAlive));
            } finally {
                cacheExecutor.shutdownNow();
            }
        }

        private static Set<Thread> dpopEvictionThreads() {
            return Thread.getAllStackTraces().keySet().stream()
                    .filter(thread -> DPOP_EVICTION_THREAD_NAME.equals(thread.getName()))
                    .collect(Collectors.toSet());
        }
    }

    /** Name of the eviction thread a {@code DpopReplayProtection} runs until it is closed. */
    private static final String DPOP_EVICTION_THREAD_NAME = "dpop-replay-eviction";

    /**
     * Caller-supplied cache eviction executor that schedules nothing: the eviction task it hands back
     * throws a {@link SecurityException} when cancelled, as a security manager may refuse an executor
     * operation, so the access token cache's shutdown fails.
     */
    private static final class CancelFailingExecutor extends ScheduledThreadPoolExecutor {

        static final String FAILURE_MESSAGE = "cancelling the eviction task is not permitted";

        CancelFailingExecutor() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            return new CancelFailingFuture();
        }

        private static final class CancelFailingFuture implements ScheduledFuture<Object> {

            @Override
            public long getDelay(TimeUnit unit) {
                return 0;
            }

            @Override
            public int compareTo(Delayed other) {
                return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
            }

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                throw new SecurityException(FAILURE_MESSAGE);
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public Object get() {
                return null;
            }

            @Override
            public Object get(long timeout, TimeUnit unit) {
                return null;
            }
        }
    }

    /**
     * An {@link AutoCloseable} JWKS loader without keys that records whether it was closed.
     */
    private static final class RecordingCloseableLoader implements JwksLoader, AutoCloseable {

        static final String ISSUER = "https://closing-issuer.example.com";

        private volatile boolean closed;

        boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public LoaderStatus getLoaderStatus() {
            return LoaderStatus.OK;
        }

        @Override
        public Optional<KeyInfo> getKeyInfo(String kid) {
            return Optional.empty();
        }

        @Override
        public Set<String> getKeyIds() {
            return Set.of();
        }

        @Override
        public JwksType getJwksType() {
            return JwksType.MEMORY;
        }

        @Override
        public Optional<String> getIssuerIdentifier() {
            return Optional.of(ISSUER);
        }

        @Override
        public CompletableFuture<LoaderStatus> initJWKSLoader(SecurityEventCounter securityEventCounter) {
            return CompletableFuture.completedFuture(LoaderStatus.OK);
        }
    }

}
