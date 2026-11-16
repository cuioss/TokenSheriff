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
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The contract of {@link RefreshResultVerifier}: the three ordered steps (ID-token validation, OIDC Core
 * §12.2 consistency, subject binding), the raw ID token it hands back, and the redemption every refusal
 * carries.
 * <p>
 * Every refusal is pinned twice — once over a rotated result and once over a non-rotated one — because
 * the redemption it carries is the only thing a caller decides the session's fate from: a rotated
 * refusal must name the successor, a non-rotated one must say the presented token survived.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("RefreshResultVerifier verifies a refresh result before it is stored")
class RefreshResultVerifierTest {

    private static final String INCONSISTENT_LOG_ID = "TokenSheriffClient-109";

    private TestTokenHolder accessHolder;
    private TestTokenHolder idHolder;
    private TokenValidationBridge accessBridge;
    private IdTokenValidationBridge idBridge;

    @BeforeEach
    void setUp() {
        accessHolder = TestTokenGenerators.accessTokens().next();
        idHolder = TestTokenGenerators.idTokens().next();
        TokenValidator validator = TokenValidator.builder().issuerConfig(accessHolder.getIssuerConfig()).build();
        accessBridge = new TokenValidationBridge(validator);
        idBridge = new IdTokenValidationBridge(validator);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    @DisplayName("Should return null and skip validation when the exchange issued no ID token")
    void shouldReturnNullWhenNoIdTokenIssued(String rawIdToken) {
        RotationResult rotation = rotation(true, rawIdToken);

        assertNull(RefreshResultVerifier.verify(rotation, idBridge, null),
                "an absent or blank ID token is not validated and yields null, so the stored one is kept");
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RefreshResultVerifier.class);
    }

    @Test
    @DisplayName("Should return the raw ID token when it validates and is consistent with the access token")
    void shouldReturnRawIdTokenWhenConsistent() {
        String subject = withSameSubject();
        RotationResult rotation = rotation(true, idHolder.getRawToken());

        assertEquals(idHolder.getRawToken(), RefreshResultVerifier.verify(rotation, idBridge, subject),
                "a verified ID token is returned verbatim for the caller to carry forward");
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RefreshResultVerifier.class);
    }

    @Test
    @DisplayName("Should pass §12.2 when the access token carries no subject and nothing is bound")
    void shouldPassConsistencyWhenAccessTokenHasNoSubject() {
        RotationResult rotation = subjectlessRotation(false, idHolder.getRawToken());

        assertEquals(idHolder.getRawToken(), RefreshResultVerifier.verify(rotation, idBridge, null),
                "'sub' is optional on an access token, so its absence is not a §12.2 inconsistency");
    }

    @Test
    @DisplayName("Should skip the binding step when no subject is bound")
    void shouldSkipBindingWhenBoundSubjectIsNull() {
        accessHolder.withClaim(ClaimName.SUBJECT.getName(),
                ClaimValue.forPlainString(Generators.letterStrings(10, 15).next()));
        RotationResult rotation = rotation(true, null);

        assertNull(RefreshResultVerifier.verify(rotation, idBridge, null),
                "a null bound subject accepts any refreshed subject, because nothing is bound to compare");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Should carry a pipeline refusal as RedeemedValidationRefusalException with the original as cause")
    void shouldCarryPipelineRefusal(boolean rotated) {
        idHolder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("https://attacker.example.com"));
        String rawIdToken = idHolder.getRawToken();
        TokenValidationException original = assertThrows(TokenValidationException.class,
                () -> idBridge.validateRefreshedIdToken(rawIdToken),
                "the fixture's ID token must be one the validation pipeline itself rejects");
        RotationResult rotation = rotation(rotated, rawIdToken);

        var thrown = assertThrows(RedeemedValidationRefusalException.class,
                () -> RefreshResultVerifier.verify(rotation, idBridge, null));

        assertAll("the pipeline refusal is carried, not replaced",
                () -> assertInstanceOf(TokenValidationException.class, thrown.getCause(),
                        "the original refusal is preserved as the cause"),
                () -> assertEquals(original.getMessage(), thrown.getMessage(),
                        "the carrier keeps the original message"),
                () -> assertEquals(original.getEventType(), thrown.getEventType(),
                        "the carrier keeps the original event type"));
        assertClassifiedRedeemed(thrown, rotation);
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RefreshResultVerifier.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Should refuse a refreshed ID token whose issuer differs from the access token (§12.2)")
    void shouldRefuseIssuerMismatch(boolean rotated) {
        withSameSubject();
        idHolder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("https://sibling-issuer.example.com"));
        // Both issuers are registered, so the ID token passes the pipeline on its own merits and the
        // refusal can only come from the §12.2 'iss' cross-check.
        var crossIssuerBridge = new IdTokenValidationBridge(TokenValidator.builder()
                .issuerConfig(accessHolder.getIssuerConfig())
                .issuerConfig(idHolder.getIssuerConfig())
                .build());
        RotationResult rotation = rotation(rotated, idHolder.getRawToken());

        var thrown = assertThrows(RedeemedIdentityRefusalException.class,
                () -> RefreshResultVerifier.verify(rotation, crossIssuerBridge, null));

        assertInconsistencyRefusal(thrown);
        assertClassifiedRedeemed(thrown, rotation);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Should refuse a refreshed ID token whose subject differs from the access token (§12.2)")
    void shouldRefuseSubjectMismatch(boolean rotated) {
        String subject = Generators.letterStrings(10, 15).next();
        accessHolder.withClaim(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString(subject));
        idHolder.withClaim(ClaimName.SUBJECT.getName(),
                ClaimValue.forPlainString(subject + Generators.letterStrings(3, 5).next()));
        RotationResult rotation = rotation(rotated, idHolder.getRawToken());

        var thrown = assertThrows(RedeemedIdentityRefusalException.class,
                () -> RefreshResultVerifier.verify(rotation, idBridge, null));

        assertInconsistencyRefusal(thrown);
        assertClassifiedRedeemed(thrown, rotation);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Should refuse a refreshed access token that names another subject than the bound one")
    void shouldRefuseBoundSubjectMismatch(boolean rotated) {
        String boundSubject = Generators.letterStrings(10, 15).next();
        accessHolder.withClaim(ClaimName.SUBJECT.getName(),
                ClaimValue.forPlainString(boundSubject + Generators.letterStrings(3, 5).next()));
        RotationResult rotation = rotation(rotated, null);

        var thrown = assertThrows(RedeemedIdentityRefusalException.class,
                () -> RefreshResultVerifier.verify(rotation, idBridge, boundSubject));

        assertBindingRefusal(thrown);
        assertClassifiedRedeemed(thrown, rotation);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Should refuse a refreshed access token without a subject while a subject is bound")
    void shouldRefuseBoundSubjectAbsentOnAccessToken(boolean rotated) {
        RotationResult rotation = subjectlessRotation(rotated, null);
        String boundSubject = Generators.letterStrings(10, 15).next();

        var thrown = assertThrows(RedeemedIdentityRefusalException.class,
                () -> RefreshResultVerifier.verify(rotation, idBridge, boundSubject));

        assertBindingRefusal(thrown);
        assertClassifiedRedeemed(thrown, rotation);
    }

    @Test
    @DisplayName("Should accept a refreshed access token naming exactly the bound subject")
    void shouldAcceptMatchingBoundSubject() {
        String subject = withSameSubject();
        RotationResult rotation = rotation(false, idHolder.getRawToken());

        assertEquals(idHolder.getRawToken(), RefreshResultVerifier.verify(rotation, idBridge, subject),
                "the matched control of the binding refusals: the same subject passes all three steps");
    }

    @Test
    @DisplayName("Should reject null arguments")
    void shouldRejectNullArguments() {
        RotationResult rotation = rotation(false, null);

        assertAll("null-guards",
                () -> assertThrows(NullPointerException.class,
                        () -> RefreshResultVerifier.verify(null, idBridge, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> RefreshResultVerifier.verify(rotation, null, null)));
    }

    /** Gives the access token and the ID token the same generated subject and returns it. */
    private String withSameSubject() {
        String subject = Generators.letterStrings(10, 15).next();
        accessHolder.withClaim(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString(subject));
        idHolder.withClaim(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString(subject));
        return subject;
    }

    /** A refresh result over the validated access token, rotated to a fresh successor or not. */
    private RotationResult rotation(boolean rotated, @Nullable String rawIdToken) {
        return rotation(accessBridge, rotated, rawIdToken);
    }

    /**
     * A refresh result whose access token carries no {@code sub}. The default issuer configuration
     * treats {@code sub} as mandatory, so the access token is validated by an issuer that opts out.
     */
    private RotationResult subjectlessRotation(boolean rotated, @Nullable String rawIdToken) {
        accessHolder.withoutClaim(ClaimName.SUBJECT.getName());
        IssuerConfig subjectOptional = IssuerConfig.builder()
                .issuerIdentifier(accessHolder.getIssuer())
                .jwksContent(InMemoryKeyMaterialHandler.createJwks(accessHolder.getSigningAlgorithm(),
                        accessHolder.getKeyId()))
                .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                .expectedClientId(TestTokenHolder.TEST_CLIENT_ID)
                .claimSubOptional(true)
                .build();
        var subjectOptionalBridge = new TokenValidationBridge(
                TokenValidator.builder().issuerConfig(subjectOptional).build());
        return rotation(subjectOptionalBridge, rotated, rawIdToken);
    }

    private RotationResult rotation(TokenValidationBridge bridge, boolean rotated, @Nullable String rawIdToken) {
        return new RotationResult(bridge.validateAccessToken(accessHolder.getRawToken()),
                Generators.letterStrings(20, 40).next(), rawIdToken, 300, rotated, null, ScopeDelta.UNDECLARED);
    }

    private static void assertClassifiedRedeemed(Throwable thrown, RotationResult rotation) {
        RefreshRedemption expected = rotation.rotated()
                ? RefreshRedemption.rotated(rotation.refreshToken())
                : RefreshRedemption.notRotated();
        RefreshFailureClassification classification = RefreshFlow.classify(thrown);

        assertAll("the refusal is post-redemption and carries the exchange's redemption",
                () -> assertEquals(RefreshFailureClassification.Kind.REDEEMED, classification.kind(),
                        "every verifier refusal is raised after the authorization server answered"),
                () -> assertEquals(expected, classification.redemption(),
                        "a rotated result names its successor; a non-rotated one says the token survived"));
    }

    private static void assertInconsistencyRefusal(RedeemedIdentityRefusalException thrown) {
        assertAll("the §12.2 refusal keeps today's message on an IllegalStateException cause",
                () -> assertInstanceOf(IllegalStateException.class, thrown.getCause(),
                        "the carried refusal is the cause"),
                () -> assertSame(thrown.getCause().getMessage(), thrown.getMessage(),
                        "the carrier keeps the refusal's message"),
                () -> assertEquals(
                        "refreshed ID token is inconsistent with the refreshed access token (OIDC Core §12.2)",
                        thrown.getMessage(), "the message is unchanged for existing callers"));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN, INCONSISTENT_LOG_ID);
    }

    private static void assertBindingRefusal(RedeemedIdentityRefusalException thrown) {
        assertAll("the binding refusal is its own refusal, never the §12.2 one",
                () -> assertInstanceOf(IllegalStateException.class, thrown.getCause(),
                        "the carried refusal is the cause"),
                () -> assertTrue(thrown.getMessage().contains("bound"),
                        "the message names the subject binding"));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RefreshResultVerifier.class);
    }
}
