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
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.JWTValidationLogMessages;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.domain.context.IdTokenRequest;
import de.cuioss.sheriff.token.validation.domain.context.RefreshTokenRequest;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig;
import de.cuioss.sheriff.token.validation.test.JweTestTokenFactory;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.TestLoggerFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the raw token size limits at the production entry points of {@link TokenValidator} for a
 * JWE-enabled validator: a JWE is bound by {@code maxEncryptedTokenSize} (at most 32 KiB) on the
 * access, ID and refresh paths alike, while a 3-part JWS stays bound by {@code maxTokenSize}. Every
 * size refusal is a typed {@link TokenValidationException}; none escapes as an
 * {@link IllegalArgumentException}.
 */
@EnableTestLogger
@DisplayName("JWE token size at the TokenValidator entry points")
class JweTokenSizeEntryPointTest {

    private static final int JWS_LIMIT = ParserConfig.DEFAULT_MAX_TOKEN_SIZE;
    private static final int JWE_LIMIT = JweDecryptionConfig.DEFAULT_MAX_ENCRYPTED_TOKEN_SIZE;

    /** The entry points that accept a JWE, each with the token type it validates. */
    enum EntryPoint {
        ACCESS(TestTokenGenerators::accessTokens,
            (validator, token) -> validator.createAccessToken(AccessTokenRequest.of(token)).getClaims()),
        ID(TestTokenGenerators::idTokens,
                (validator, token) -> validator.createIdToken(IdTokenRequest.of(token)).getClaims()),
        REFRESH(TestTokenGenerators::refreshTokens,
                (validator, token) -> validator.createRefreshToken(RefreshTokenRequest.of(token)).getClaims());

        private final Supplier<TypedGenerator<TestTokenHolder>> tokens;
        private final BiFunction<TokenValidator, String, Map<String, ClaimValue>> create;

        EntryPoint(Supplier<TypedGenerator<TestTokenHolder>> tokens,
                BiFunction<TokenValidator, String, Map<String, ClaimValue>> create) {
            this.tokens = tokens;
            this.create = create;
        }

        TestTokenHolder nextToken() {
            return tokens.get().next();
        }

        Map<String, ClaimValue> create(TokenValidator validator, String token) {
            return create.apply(validator, token);
        }
    }

    private KeyPair encryptionKeyPair;

    /** Validators created by the test, closed after it so their cache eviction tasks stop. */
    private final List<TokenValidator> validators = new ArrayList<>();

    @BeforeEach
    void setUp() {
        encryptionKeyPair = JweTestTokenFactory.generateRsaKeyPair();
    }

    @AfterEach
    void closeValidators() {
        validators.forEach(TokenValidator::close);
        validators.clear();
    }

    private TokenValidator jweValidator(TestTokenHolder holder) {
        TokenValidator validator = TokenValidator.builder()
                .issuerConfig(holder.getIssuerConfig())
                .jweDecryptionConfig(JweDecryptionConfig.builder()
                        .defaultDecryptionKey(encryptionKeyPair.getPrivate())
                        .build())
                .build();
        validators.add(validator);
        return validator;
    }

    /**
     * Adds {@code count} claims of 1000 characters each, staying below the per-string and per-part
     * parser limits while growing the signed token.
     */
    private static TestTokenHolder padded(TestTokenHolder holder, int count) {
        for (int i = 0; i < count; i++) {
            holder.withClaim("pad" + i, ClaimValue.forPlainString("p".repeat(1000)));
        }
        return holder;
    }

    private static long sizeExceededWarnings() {
        String identifier = JWTValidationLogMessages.WARN.TOKEN_SIZE_EXCEEDED.resolveIdentifierString();
        return TestLoggerFactory.getTestHandler().resolveLogMessages(TestLogLevel.WARN).stream()
                .filter(warning -> warning.getMessage().contains(identifier))
                .count();
    }

    @ParameterizedTest
    @EnumSource(EntryPoint.class)
    @DisplayName("A valid JWE of about 10 KiB validates on every entry point")
    void shouldValidateJweAboveJwsLimit(EntryPoint entryPoint) {
        TestTokenHolder holder = padded(entryPoint.nextToken(), 4);
        String innerJws = holder.getRawToken();
        String jwe = JweTestTokenFactory.encryptAsJwe(
                innerJws, encryptionKeyPair.getPublic(), "RSA-OAEP", "A256GCM", null);
        assertTrue(innerJws.length() <= JWS_LIMIT,
                "Non-vacuity: the inner JWS must stay within maxTokenSize, was " + innerJws.length());
        assertTrue(jwe.length() > JWS_LIMIT && jwe.length() <= JWE_LIMIT,
                "Non-vacuity: the JWE must lie between the two limits, was " + jwe.length());
        TokenValidator validator = jweValidator(holder);

        Map<String, ClaimValue> claims = entryPoint.create(validator, jwe);

        assertEquals("p".repeat(1000), claims.get("pad0").getOriginalString(),
                "The claims must come from the decrypted inner token");
        assertEquals(0, validator.getSecurityEventCounter().getCount(EventType.TOKEN_SIZE_EXCEEDED));
    }

    @ParameterizedTest
    @EnumSource(EntryPoint.class)
    @DisplayName("A 5-part token above maxEncryptedTokenSize is a counted TOKEN_SIZE_EXCEEDED rejection")
    void shouldRejectJweAboveJweLimit(EntryPoint entryPoint) {
        TestTokenHolder holder = entryPoint.nextToken();
        TokenValidator validator = jweValidator(holder);
        String tail = ".b.c.d.e";
        String oversized = "a".repeat(JWE_LIMIT + 1 - tail.length()) + tail;

        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> entryPoint.create(validator, oversized));

        assertEquals(EventType.TOKEN_SIZE_EXCEEDED, exception.getEventType());
        assertEquals("Token size %d exceeds maximum %d".formatted(JWE_LIMIT + 1, JWE_LIMIT),
                exception.getMessage());
        assertEquals(1, validator.getSecurityEventCounter().getCount(EventType.TOKEN_SIZE_EXCEEDED),
                "Counted exactly once on " + entryPoint);
        assertEquals(1, sizeExceededWarnings(), "Exactly one TOKEN_SIZE_EXCEEDED WARN on " + entryPoint);
    }

    @ParameterizedTest
    @EnumSource(value = EntryPoint.class, names = {"ACCESS", "ID"})
    @DisplayName("A 3-part JWS above maxTokenSize stays a counted TOKEN_SIZE_EXCEEDED rejection")
    void shouldRejectJwsAboveJwsLimit(EntryPoint entryPoint) {
        TestTokenHolder holder = padded(entryPoint.nextToken(), 6);
        String jws = holder.getRawToken();
        assertTrue(jws.length() > JWS_LIMIT && jws.length() <= JWE_LIMIT,
                "Non-vacuity: the JWS must lie between the two limits, was " + jws.length());
        TokenValidator validator = jweValidator(holder);

        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> entryPoint.create(validator, jws),
                "The parser, not the token string check, refuses this JWS; it must not escape as IllegalArgumentException");

        assertEquals(EventType.TOKEN_SIZE_EXCEEDED, exception.getEventType());
        assertEquals("Token size %d exceeds maximum %d".formatted(jws.length(), JWS_LIMIT),
                exception.getMessage());
        assertEquals(1, validator.getSecurityEventCounter().getCount(EventType.TOKEN_SIZE_EXCEEDED),
                "Counted exactly once on " + entryPoint);
        assertEquals(1, sizeExceededWarnings(), "Exactly one TOKEN_SIZE_EXCEEDED WARN on " + entryPoint);
    }

    @ParameterizedTest
    @EnumSource(value = EntryPoint.class, names = {"ACCESS", "ID"})
    @DisplayName("A 3-part JWS within maxTokenSize is accepted although maxEncryptedTokenSize is smaller")
    void shouldAcceptJwsWithinJwsLimitWhenJweLimitIsSmaller(EntryPoint entryPoint) {
        int smallJweLimit = 4096;
        TestTokenHolder holder = padded(entryPoint.nextToken(), 3);
        String jws = holder.getRawToken();
        assertTrue(jws.length() > smallJweLimit && jws.length() <= JWS_LIMIT,
                "Non-vacuity: the JWS must lie between the two limits, was " + jws.length());
        TokenValidator validator = TokenValidator.builder()
                .issuerConfig(holder.getIssuerConfig())
                .jweDecryptionConfig(JweDecryptionConfig.builder()
                        .defaultDecryptionKey(encryptionKeyPair.getPrivate())
                        .maxEncryptedTokenSize(smallJweLimit)
                        .build())
                .build();
        validators.add(validator);

        Map<String, ClaimValue> claims = entryPoint.create(validator, jws);

        assertEquals("p".repeat(1000), claims.get("pad0").getOriginalString());
        assertEquals(0, validator.getSecurityEventCounter().getCount(EventType.TOKEN_SIZE_EXCEEDED));
    }

    /**
     * The refresh path decodes opaquely: a JWS the parser refuses for size is kept as an opaque
     * refresh token without claims, neither logged nor counted, instead of escaping as an
     * {@link IllegalArgumentException}.
     */
    @Test
    @DisplayName("A 3-part JWS above maxTokenSize is kept as an opaque refresh token")
    void shouldKeepOversizedJwsAsOpaqueRefreshToken() {
        TestTokenHolder holder = padded(EntryPoint.REFRESH.nextToken(), 6);
        String jws = holder.getRawToken();
        assertTrue(jws.length() > JWS_LIMIT && jws.length() <= JWE_LIMIT,
                "Non-vacuity: the JWS must lie between the two limits, was " + jws.length());
        TokenValidator validator = jweValidator(holder);

        Map<String, ClaimValue> claims = EntryPoint.REFRESH.create(validator, jws);

        assertTrue(claims.isEmpty(), "The oversized JWS is not decoded, so no claims are extracted");
        assertEquals(0, validator.getSecurityEventCounter().getCount(EventType.TOKEN_SIZE_EXCEEDED));
        assertEquals(0, sizeExceededWarnings());
    }
}
