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
import de.cuioss.sheriff.token.commons.events.SecurityEventCounter.EventType;
import de.cuioss.sheriff.token.commons.transport.JwksType;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.domain.context.IdTokenRequest;
import de.cuioss.sheriff.token.validation.domain.token.SignatureVerifiedToken;
import de.cuioss.sheriff.token.validation.domain.token.TokenContent;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig;
import de.cuioss.sheriff.token.validation.jwks.JwksLoader;
import de.cuioss.sheriff.token.validation.jwks.key.KeyInfo;
import de.cuioss.sheriff.token.validation.test.InMemoryJWKSFactory;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.test.JweTestTokenFactory;
import io.jsonwebtoken.Jwts;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.security.KeyPair;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link TokenValidator#verifySignature(String)}, the signature-only entry that verifies the token
 * string, the issuer, the header and the signature but reaches no claim verdict.
 * <p>
 * Every refusal is paired with a positive control and asserts the event type of the refusing stage, and
 * the claim-verdict cases are paired with a {@code createIdToken} / {@code createAccessToken} negative
 * control that refuses the same token.
 */
@DisplayName("TokenValidator#verifySignature")
class TokenValidatorVerifySignatureTest {

    static final String ISSUER = "https://signature-only.example.com";
    private static final String OTHER_ISSUER = "https://other-signature-only.example.com";
    static final String AUDIENCE = "test-audience";
    private static final String KEY_ID = InMemoryKeyMaterialHandler.DEFAULT_KEY_ID;

    private final List<TokenValidator> validators = new ArrayList<>();

    @AfterEach
    void closeValidators() {
        validators.forEach(TokenValidator::close);
    }

    @Nested
    @DisplayName("Token string limits")
    class StringLimits {

        @Test
        @DisplayName("A null, empty or blank token is refused as TOKEN_EMPTY")
        void shouldRefuseEmptyToken() {
            TokenValidator validator = validator();

            for (String empty : Arrays.asList(null, "", "   ")) {
                assertRefused(EventType.TOKEN_EMPTY, () -> validator.verifySignature(empty));
            }
            assertEquals(3, validator.getSecurityEventCounter().getCount(EventType.TOKEN_EMPTY));
            assertNotNull(validator.verifySignature(signed(validClaims(), null)), "positive control");
        }

        @Test
        @DisplayName("A token above the configured size limit is refused as TOKEN_SIZE_EXCEEDED")
        void shouldRefuseOversizedToken() {
            String token = signed(validClaims(), null);
            TokenValidator tight = validator(ParserConfig.builder().maxTokenSize(token.length() - 1).build());

            assertRefused(EventType.TOKEN_SIZE_EXCEEDED, () -> tight.verifySignature(token));
            assertNotNull(validator().verifySignature(token), "positive control: the same token within the limit");
        }
    }

    @Nested
    @DisplayName("Issuer gating")
    class IssuerGating {

        @Test
        @DisplayName("A token without iss is refused as MISSING_CLAIM")
        void shouldRefuseMissingIssuer() {
            Map<String, Object> claims = validClaims();
            claims.remove(ClaimName.ISSUER.getName());
            TokenValidator validator = validator();

            assertRefused(EventType.MISSING_CLAIM, () -> validator.verifySignature(signed(claims, null)));
            assertNotNull(validator.verifySignature(signed(validClaims(), null)), "positive control");
        }

        @Test
        @DisplayName("A token of an unknown issuer is refused as NO_ISSUER_CONFIG")
        void shouldRefuseUnknownIssuer() {
            Map<String, Object> claims = validClaims();
            claims.put(ClaimName.ISSUER.getName(), "https://unknown.example.com");
            TokenValidator validator = validator();

            assertRefused(EventType.NO_ISSUER_CONFIG, () -> validator.verifySignature(signed(claims, null)));
            assertNotNull(validator.verifySignature(signed(validClaims(), null)), "positive control");
        }

        @Test
        @DisplayName("A token of a disabled issuer is refused as NO_ISSUER_CONFIG")
        void shouldRefuseDisabledIssuer() {
            IssuerConfig disabled = IssuerConfig.builder().enabled(false).issuerIdentifier(OTHER_ISSUER).build();
            TokenValidator validator = register(TokenValidator.builder()
                    .issuerConfig(issuerConfig())
                    .issuerConfig(disabled)
                    .build());
            Map<String, Object> claims = validClaims();
            claims.put(ClaimName.ISSUER.getName(), OTHER_ISSUER);

            assertRefused(EventType.NO_ISSUER_CONFIG, () -> validator.verifySignature(signed(claims, null)));
            assertNotNull(validator.verifySignature(signed(validClaims(), null)), "positive control: the enabled issuer");
        }

        @Test
        @DisplayName("A token of an issuer whose loader is not OK is refused as NO_ISSUER_CONFIG")
        void shouldRefuseIssuerWithFailedLoader() {
            StatusControlledLoader loader = new StatusControlledLoader(LoaderStatus.ERROR);
            TokenValidator validator = validator(loader);
            String token = signed(validClaims(), null);

            assertRefused(EventType.NO_ISSUER_CONFIG, () -> validator.verifySignature(token));

            loader.setStatus(LoaderStatus.OK);
            assertNotNull(validator.verifySignature(token), "positive control: the same issuer once its loader recovered");
        }

        @Test
        @DisplayName("A token of an issuer whose loader left OK is refused as NO_ISSUER_CONFIG")
        void shouldRefuseIssuerWhoseLoaderLeftOk() {
            StatusControlledLoader loader = new StatusControlledLoader(LoaderStatus.OK);
            TokenValidator validator = validator(loader);
            String token = signed(validClaims(), null);
            assertNotNull(validator.verifySignature(token), "positive control: accepted while the loader is OK");

            loader.setStatus(LoaderStatus.ERROR);

            assertRefused(EventType.NO_ISSUER_CONFIG, () -> validator.verifySignature(token));
        }
    }

    @Nested
    @DisplayName("Header posture")
    class HeaderPosture {

        @Test
        @DisplayName("A token without kid is refused as MISSING_CLAIM")
        void shouldRefuseMissingKeyId() {
            String token = Jwts.builder()
                    .claims(validClaims())
                    .signWith(InMemoryKeyMaterialHandler.getDefaultPrivateKey(), Jwts.SIG.RS256)
                    .compact();
            TokenValidator validator = validator();

            assertRefused(EventType.MISSING_CLAIM, () -> validator.verifySignature(token));
            assertNotNull(validator.verifySignature(signed(validClaims(), null)), "positive control: with kid");
        }

        @Test
        @DisplayName("The issuer's expected token type is not applied, while the verdict paths apply it")
        void shouldSkipExpectedTokenType() {
            TokenValidator validator = register(TokenValidator.builder()
                    .issuerConfig(IssuerConfig.builder()
                            .issuerIdentifier(ISSUER)
                            .expectedAudience(AUDIENCE)
                            .expectedTokenType("at+jwt")
                            .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                            .build())
                    .build());
            String logoutToken = signed(validClaims(), "logout+jwt");

            SignatureVerifiedToken verified = validator.verifySignature(logoutToken);

            assertEquals(Optional.of("logout+jwt"), verified.getTypHeader());
            AccessTokenRequest request = AccessTokenRequest.of(logoutToken);
            assertRefused(EventType.TOKEN_TYPE_MISMATCH, () -> validator.createAccessToken(request));
        }
    }

    @Nested
    @DisplayName("Signature")
    class SignatureChecks {

        @Test
        @DisplayName("A token whose payload was changed after signing is refused as SIGNATURE_VALIDATION_FAILED")
        void shouldRefuseTamperedPayload() {
            String original = signed(validClaims(), null);
            Map<String, Object> changed = validClaims();
            changed.put(ClaimName.SUBJECT.getName(), "someone-else");
            String[] parts = original.split("\\.");
            String forgedPayload = signed(changed, null).split("\\.")[1];
            String tampered = parts[0] + "." + forgedPayload + "." + parts[2];
            TokenValidator validator = validator();

            assertRefused(EventType.SIGNATURE_VALIDATION_FAILED, () -> validator.verifySignature(tampered));
            assertNotNull(validator.verifySignature(original), "positive control: the untouched token");
        }

        @Test
        @DisplayName("A token naming a key the issuer does not publish is refused as KEY_NOT_FOUND")
        void shouldRefuseUnknownKeyId() {
            String token = Jwts.builder()
                    .header().keyId("unknown-key").and()
                    .claims(validClaims())
                    .signWith(InMemoryKeyMaterialHandler.getDefaultPrivateKey(), Jwts.SIG.RS256)
                    .compact();
            TokenValidator validator = validator();

            assertRefused(EventType.KEY_NOT_FOUND, () -> validator.verifySignature(token));
            assertNotNull(validator.verifySignature(signed(validClaims(), null)), "positive control: known kid");
        }
    }

    @Nested
    @DisplayName("No claim verdict")
    class NoClaimVerdict {

        @Test
        @DisplayName("An expired token is returned, while createIdToken refuses it as TOKEN_EXPIRED")
        void shouldAcceptExpiredToken() {
            Map<String, Object> claims = validClaims();
            claims.put(ClaimName.EXPIRATION.getName(), Instant.now().minusSeconds(3600).getEpochSecond());
            claims.put(ClaimName.ISSUED_AT.getName(), Instant.now().minusSeconds(7200).getEpochSecond());
            assertReturnedButRefusedByIdToken(signed(claims, null), EventType.TOKEN_EXPIRED);
        }

        @Test
        @DisplayName("A token for another audience is returned, while createIdToken refuses it as AUDIENCE_MISMATCH")
        void shouldAcceptWrongAudience() {
            Map<String, Object> claims = validClaims();
            claims.put(ClaimName.AUDIENCE.getName(), "https://other-service.example.com");
            assertReturnedButRefusedByIdToken(signed(claims, null), EventType.AUDIENCE_MISMATCH);
        }

        @Test
        @DisplayName("A token without sub is returned, while createIdToken refuses it as MISSING_CLAIM")
        void shouldAcceptMissingSubject() {
            Map<String, Object> claims = validClaims();
            claims.remove(ClaimName.SUBJECT.getName());
            assertReturnedButRefusedByIdToken(signed(claims, null), EventType.MISSING_CLAIM);
        }

        @Test
        @DisplayName("A token without azp is returned with its remaining claims")
        void shouldAcceptMissingAuthorizedParty() {
            Map<String, Object> claims = validClaims();
            claims.remove(ClaimName.AUTHORIZED_PARTY.getName());

            SignatureVerifiedToken verified = validator().verifySignature(signed(claims, null));

            assertFalse(verified.getClaims().containsKey(ClaimName.AUTHORIZED_PARTY.getName()));
            assertEquals("test-subject", verified.getClaims().get(ClaimName.SUBJECT.getName()).getOriginalString());
        }

        @Test
        @DisplayName("The result is not a TokenContent and carries the mapped claims and the raw token")
        void shouldReturnSeparateType() {
            String token = signed(validClaims(), null);

            SignatureVerifiedToken verified = validator().verifySignature(token);

            assertFalse(TokenContent.class.isAssignableFrom(verified.getClass()),
                    "a signature-only result must not be usable as a validated token");
            assertEquals(token, verified.getRawToken());
            assertEquals(ISSUER, verified.getClaims().get(ClaimName.ISSUER.getName()).getOriginalString());
            assertThrows(UnsupportedOperationException.class, () -> verified.getClaims().clear(),
                    "the claims are unmodifiable");
        }

        private void assertReturnedButRefusedByIdToken(String token, EventType idTokenRefusal) {
            TokenValidator validator = validator();

            assertNotNull(validator.verifySignature(token), "the signature-only entry returns the token");
            IdTokenRequest request = IdTokenRequest.of(token);
            assertRefused(idTokenRefusal, () -> validator.createIdToken(request));
        }
    }

    @Nested
    @DisplayName("typ header")
    class TypHeader {

        @Test
        @DisplayName("A present typ is returned verbatim")
        void shouldReturnPresentTyp() {
            assertEquals(Optional.of("logout+jwt"),
                    validator().verifySignature(signed(validClaims(), "logout+jwt")).getTypHeader());
        }

        @Test
        @DisplayName("An absent typ is returned as empty")
        void shouldReturnAbsentTyp() {
            assertEquals(Optional.empty(), validator().verifySignature(signed(validClaims(), null)).getTypHeader());
        }

        @Test
        @DisplayName("A mixed-case typ is returned unchanged, not normalised")
        void shouldReturnMixedCaseTypVerbatim() {
            assertEquals(Optional.of("Application/Logout+JWT"),
                    validator().verifySignature(signed(validClaims(), "Application/Logout+JWT")).getTypHeader());
        }
    }

    @Nested
    @DisplayName("JWE")
    class Jwe {

        @Test
        @DisplayName("A JWE is decrypted when JWE is configured and its inner token is verified")
        void shouldVerifyJweWithConfig() {
            KeyPair encryptionKeys = JweTestTokenFactory.generateRsaKeyPair();
            String jwe = JweTestTokenFactory.encryptAsJwe(signed(validClaims(), "logout+jwt"),
                    encryptionKeys.getPublic(), "RSA-OAEP", "A256GCM", null);
            TokenValidator validator = register(TokenValidator.builder()
                    .issuerConfig(issuerConfig())
                    .jweDecryptionConfig(JweDecryptionConfig.builder()
                            .defaultDecryptionKey(encryptionKeys.getPrivate()).build())
                    .build());

            SignatureVerifiedToken verified = validator.verifySignature(jwe);

            assertEquals(ISSUER, verified.getClaims().get(ClaimName.ISSUER.getName()).getOriginalString());
            assertEquals(Optional.of("logout+jwt"), verified.getTypHeader(), "typ of the inner token");
            assertEquals(jwe, verified.getRawToken(), "the raw token is the token as presented");
        }

        @Test
        @DisplayName("A JWE is refused as JWE_DECRYPTION_NOT_CONFIGURED when JWE is not configured")
        void shouldRefuseJweWithoutConfig() {
            KeyPair encryptionKeys = JweTestTokenFactory.generateRsaKeyPair();
            String jwe = JweTestTokenFactory.encryptAsJwe(signed(validClaims(), null),
                    encryptionKeys.getPublic(), "RSA-OAEP", "A256GCM", null);
            TokenValidator validator = validator();

            assertRefused(EventType.JWE_DECRYPTION_NOT_CONFIGURED, () -> validator.verifySignature(jwe));
            assertNotNull(validator.verifySignature(signed(validClaims(), null)), "positive control: a plain JWS");
        }
    }

    @Nested
    @DisplayName("Event accounting and cache")
    class Accounting {

        @Test
        @DisplayName("A success counts exactly one SIGNATURE_ONLY_VERIFIED event and nothing else")
        void shouldCountExactlyOneSuccessEvent() {
            TokenValidator validator = validator();
            String token = signed(validClaims(), null);
            Map<EventType, Long> before = validator.getSecurityEventCounter().getCounters();

            validator.verifySignature(token);

            assertEquals(Map.of(EventType.SIGNATURE_ONLY_VERIFIED, 1L),
                    delta(before, validator.getSecurityEventCounter().getCounters()));
        }

        @Test
        @DisplayName("A refusal counts exactly the event of the refusing stage and no success event")
        void shouldCountExactlyOneRefusalEvent() {
            TokenValidator validator = validator();
            Map<String, Object> claims = validClaims();
            claims.put(ClaimName.ISSUER.getName(), "https://unknown.example.com");
            String token = signed(claims, null);
            Map<EventType, Long> before = validator.getSecurityEventCounter().getCounters();

            assertRefused(EventType.NO_ISSUER_CONFIG, () -> validator.verifySignature(token));

            assertEquals(Map.of(EventType.NO_ISSUER_CONFIG, 1L),
                    delta(before, validator.getSecurityEventCounter().getCounters()));
        }

        @Test
        @DisplayName("The access-token cache is neither filled nor read by the signature-only entry")
        void shouldNotTouchAccessTokenCache() {
            TokenValidator validator = validator();
            String token = signed(validClaims(), null);
            SecurityEventCounter counter = validator.getSecurityEventCounter();
            AccessTokenRequest request = AccessTokenRequest.of(token);

            validator.verifySignature(token);
            validator.createAccessToken(request);
            assertEquals(0, counter.getCount(EventType.ACCESS_TOKEN_CACHE_HIT),
                    "verifySignature must not have stored the token in the cache");

            validator.verifySignature(token);
            assertEquals(0, counter.getCount(EventType.ACCESS_TOKEN_CACHE_HIT),
                    "verifySignature must not read the cache");

            validator.createAccessToken(request);
            assertEquals(1, counter.getCount(EventType.ACCESS_TOKEN_CACHE_HIT),
                    "positive control: the access-token path does use the cache");
        }
    }

    // === Fixtures ===

    /**
     * @return mutable claims of a token that passes every check of the default issuer configuration
     */
    static Map<String, Object> validClaims() {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(ClaimName.ISSUER.getName(), ISSUER);
        claims.put(ClaimName.SUBJECT.getName(), "test-subject");
        claims.put(ClaimName.AUDIENCE.getName(), AUDIENCE);
        claims.put(ClaimName.AUTHORIZED_PARTY.getName(), "test-client");
        claims.put(ClaimName.ISSUED_AT.getName(), now.getEpochSecond());
        claims.put(ClaimName.EXPIRATION.getName(), now.plusSeconds(3600).getEpochSecond());
        claims.put("scope", "openid");
        return claims;
    }

    /**
     * Signs the claims with the default RS256 test key under the default key id.
     *
     * @param claims the payload claims
     * @param typ    the {@code typ} header value, {@code null} for none
     * @return the compact JWS
     */
    static String signed(Map<String, Object> claims, @Nullable String typ) {
        var header = Jwts.builder().header().keyId(KEY_ID);
        if (typ != null) {
            header.type(typ);
        }
        return header.and()
                .claims(claims)
                .signWith(InMemoryKeyMaterialHandler.getDefaultPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    private static IssuerConfig issuerConfig() {
        return IssuerConfig.builder()
                .issuerIdentifier(ISSUER)
                .expectedAudience(AUDIENCE)
                .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                .build();
    }

    private TokenValidator validator() {
        return register(TokenValidator.builder().issuerConfig(issuerConfig()).build());
    }

    private TokenValidator validator(ParserConfig parserConfig) {
        return register(TokenValidator.builder().parserConfig(parserConfig).issuerConfig(issuerConfig()).build());
    }

    private TokenValidator validator(JwksLoader loader) {
        return register(TokenValidator.builder()
                .issuerConfig(IssuerConfig.builder()
                        .issuerIdentifier(ISSUER)
                        .expectedAudience(AUDIENCE)
                        .jwksLoader(loader)
                        .build())
                .build());
    }

    private TokenValidator register(TokenValidator validator) {
        validators.add(validator);
        return validator;
    }

    private static void assertRefused(EventType expected, Executable call) {
        TokenValidationException refused = assertThrows(TokenValidationException.class, call);
        assertEquals(expected, refused.getEventType());
    }

    private static Map<EventType, Long> delta(Map<EventType, Long> before, Map<EventType, Long> after) {
        Map<EventType, Long> delta = new EnumMap<>(EventType.class);
        after.forEach((type, count) -> {
            long change = count - before.getOrDefault(type, 0L);
            if (change != 0) {
                delta.put(type, change);
            }
        });
        return delta;
    }

    /**
     * A loader serving the default test key whose status can be changed by the test.
     */
    static final class StatusControlledLoader implements JwksLoader {

        private final JwksLoader keys = InMemoryJWKSFactory.createDefaultJwksLoader(new SecurityEventCounter());
        private final AtomicReference<LoaderStatus> status;

        StatusControlledLoader(LoaderStatus initialStatus) {
            this.status = new AtomicReference<>(initialStatus);
        }

        void setStatus(LoaderStatus newStatus) {
            status.set(newStatus);
        }

        @Override
        public LoaderStatus getLoaderStatus() {
            return status.get();
        }

        @Override
        public Optional<KeyInfo> getKeyInfo(String kid) {
            return keys.getKeyInfo(kid);
        }

        @Override
        public Set<String> getKeyIds() {
            return keys.getKeyIds();
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
            keys.initJWKSLoader(securityEventCounter).join();
            return CompletableFuture.completedFuture(status.get());
        }
    }
}
