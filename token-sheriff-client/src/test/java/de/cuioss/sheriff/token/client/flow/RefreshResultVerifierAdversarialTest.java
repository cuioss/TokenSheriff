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
package de.cuioss.sheriff.token.client.flow;

import de.cuioss.sheriff.token.client.token.IdTokenValidationBridge;
import de.cuioss.sheriff.token.client.token.RotationResult;
import de.cuioss.sheriff.token.client.token.RotationResult.ScopeDelta;
import de.cuioss.sheriff.token.client.token.TokenValidationBridge;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.test.JwtTokenTamperingUtil;
import de.cuioss.sheriff.token.validation.test.JwtTokenTamperingUtil.TamperingStrategy;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Adversarial refreshed ID tokens against {@link RefreshResultVerifier}: forged, tampered, unsigned,
 * downgraded, expired and mis-addressed tokens an attacker in the token response could hand back.
 * <p>
 * Each one must be refused by the pipeline step — before any §12.2 comparison could be fooled by a
 * claim the attacker chose — and classified {@link RefreshFailureClassification.Kind#REDEEMED} with
 * the exchange's redemption, rotated or not, so the caller quarantines a burned session instead of
 * keeping it. The bound-subject step is attacked separately with an access token that simply drops
 * its {@code sub}.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("RefreshResultVerifier refuses adversarial refresh results after redemption")
class RefreshResultVerifierAdversarialTest {

    private TestTokenHolder accessHolder;
    private TestTokenHolder idHolder;
    private TokenValidationBridge accessBridge;
    private IdTokenValidationBridge idBridge;

    /** A forged refreshed ID token, built from the legitimate ID-token holder. */
    enum Attack {

        /** The payload's {@code sub} is swapped for another user's while the original signature is kept. */
        SUBJECT_SUBSTITUTION((holder, subject) -> substituteClaim(holder.getRawToken(), "sub", subject,
                subject + "-victim")),

        /** The payload's {@code iss} is swapped while the original signature is kept. */
        ISSUER_SUBSTITUTION((holder, subject) -> substituteClaim(holder.getRawToken(), "iss",
                holder.getIssuer(), "https://attacker.example.com")),

        /** The header announces {@code alg=none}. */
        ALG_NONE((holder, subject) -> JwtTokenTamperingUtil.applyTamperingStrategy(holder.getRawToken(),
                TamperingStrategy.ALGORITHM_NONE)),

        /** The signature segment is stripped. */
        UNSIGNED((holder, subject) -> JwtTokenTamperingUtil.applyTamperingStrategy(holder.getRawToken(),
                TamperingStrategy.REMOVE_SIGNATURE)),

        /** Correctly signed, but its {@code exp} lies an hour in the past. */
        EXPIRED((holder, subject) -> {
            OffsetDateTime past = OffsetDateTime.now().minusHours(1);
            holder.withClaim(ClaimName.EXPIRATION.getName(),
                    ClaimValue.forDateTime(String.valueOf(past.toEpochSecond()), past));
            return holder.getRawToken();
        }),

        /** Correctly signed, but addressed to an audience this client is not. */
        WRONG_AUDIENCE((holder, subject) -> {
            holder.withClaim(ClaimName.AUDIENCE.getName(), ClaimValue.forPlainString("foreign-audience"));
            return holder.getRawToken();
        });

        private final BiFunction<TestTokenHolder, String, String> forge;

        Attack(BiFunction<TestTokenHolder, String, String> forge) {
            this.forge = forge;
        }

        String forge(TestTokenHolder holder, String subject) {
            return forge.apply(holder, subject);
        }
    }

    @BeforeEach
    void setUp() {
        accessHolder = TestTokenGenerators.accessTokens().next();
        idHolder = TestTokenGenerators.idTokens().next();
        TokenValidator validator = TokenValidator.builder().issuerConfig(accessHolder.getIssuerConfig()).build();
        accessBridge = new TokenValidationBridge(validator);
        idBridge = new IdTokenValidationBridge(validator);
    }

    static Stream<Arguments> attacksOverRotation() {
        return Arrays.stream(Attack.values())
                .flatMap(attack -> Stream.of(Arguments.of(attack, true), Arguments.of(attack, false)));
    }

    @ParameterizedTest(name = "{0}, rotated={1}")
    @MethodSource("attacksOverRotation")
    @DisplayName("Should refuse a forged refreshed ID token through the pipeline and classify it REDEEMED")
    void shouldRefuseForgedIdTokenThroughThePipeline(Attack attack, boolean rotated) {
        String subject = Generators.letterStrings(10, 15).next();
        accessHolder.withClaim(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString(subject));
        idHolder.withClaim(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString(subject));
        String legitimate = idHolder.getRawToken();
        String forged = attack.forge(idHolder, subject);
        assertNotEquals(legitimate, forged, "the attack must actually alter the ID token");
        RotationResult rotation = rotation(rotated, forged);

        var thrown = assertThrows(RedeemedValidationRefusalException.class,
                () -> RefreshResultVerifier.verify(rotation, idBridge, subject),
                attack + " must be refused by the pipeline step, never reach the §12.2 comparison");

        assertInstanceOf(TokenValidationException.class, thrown.getCause(),
                "the pipeline refusal is carried as the cause");
        assertClassifiedRedeemed(thrown, rotation);
    }

    @ParameterizedTest(name = "rotated={0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("Should refuse an access token that drops its subject while a subject is bound")
    void shouldRefuseSubjectlessAccessTokenWhileBound(boolean rotated) {
        String boundSubject = Generators.letterStrings(10, 15).next();
        accessHolder.withoutClaim(ClaimName.SUBJECT.getName());
        // The default issuer configuration treats 'sub' as mandatory, so the subject-less access token
        // is validated by an issuer that opts out — the attack only reaches the verifier that way.
        IssuerConfig subjectOptional = IssuerConfig.builder()
                .issuerIdentifier(accessHolder.getIssuer())
                .jwksContent(InMemoryKeyMaterialHandler.createJwks(accessHolder.getSigningAlgorithm(),
                        accessHolder.getKeyId()))
                .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                .expectedClientId(TestTokenHolder.TEST_CLIENT_ID)
                .claimSubOptional(true)
                .build();
        accessBridge = new TokenValidationBridge(TokenValidator.builder().issuerConfig(subjectOptional).build());
        RotationResult rotation = rotation(rotated, null);

        var thrown = assertThrows(RedeemedIdentityRefusalException.class,
                () -> RefreshResultVerifier.verify(rotation, idBridge, boundSubject),
                "a refreshed access token that names nobody cannot keep a session bound to somebody");

        assertInstanceOf(IllegalStateException.class, thrown.getCause(),
                "the binding refusal is carried as the cause");
        assertClassifiedRedeemed(thrown, rotation);
    }

    private RotationResult rotation(boolean rotated, String rawIdToken) {
        return new RotationResult(accessBridge.validateAccessToken(accessHolder.getRawToken()),
                Generators.letterStrings(20, 40).next(), rawIdToken, 300, rotated, null, ScopeDelta.UNDECLARED);
    }

    private static void assertClassifiedRedeemed(Throwable thrown, RotationResult rotation) {
        RefreshRedemption expected = rotation.rotated()
                ? RefreshRedemption.rotated(rotation.refreshToken())
                : RefreshRedemption.notRotated();
        RefreshFailureClassification classification = RefreshFlow.classify(thrown);

        assertAll("the adversarial refusal is post-redemption",
                () -> assertEquals(RefreshFailureClassification.Kind.REDEEMED, classification.kind(),
                        "the authorization server already answered, so the refusal is REDEEMED"),
                () -> assertEquals(expected, classification.redemption(),
                        "the refusal carries the redemption of the exchange it was raised over"));
    }

    /**
     * Replaces one string claim in the payload while keeping header and signature byte for byte, the
     * shape of an attacker who edits a token he cannot re-sign.
     */
    private static String substituteClaim(String rawToken, String claim, String oldValue, String newValue) {
        String[] parts = rawToken.split("\\.", -1);
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String target = "\"" + claim + "\":\"" + oldValue + "\"";
        if (!payload.contains(target)) {
            throw new IllegalStateException("fixture payload does not carry " + target);
        }
        String tampered = payload.replace(target, "\"" + claim + "\":\"" + newValue + "\"");
        parts[1] = Base64.getUrlEncoder().withoutPadding().encodeToString(tampered.getBytes(StandardCharsets.UTF_8));
        return String.join(".", parts);
    }
}
