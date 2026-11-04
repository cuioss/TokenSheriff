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

import de.cuioss.sheriff.token.client.flow.StepUpChallengeParser.StepUpChallenge;
import de.cuioss.sheriff.token.commons.error.ClientProtocolException;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.domain.token.IdTokenContent;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the {@link StepUpResultVerifier} contract on a fixed clock: the {@code auth_time} freshness
 * boundary sits exactly at {@code max_age}, an opt-in clock-skew allowance widens it by exactly the
 * allowance, and the default construction keeps the exact boundary. The {@code acr} checks and the
 * {@code auth_time} parse failures are asserted alongside, since they share the same entry point.
 */
@EnableGeneratorController
@DisplayName("StepUpResultVerifier: injectable clock, opt-in skew allowance, acr and auth_time checks")
class StepUpResultVerifierTest {

    private static final String ACR = "urn:mace:incommon:iap:silver";
    private static final String CLAIM_ACR = "acr";
    private static final String CLAIM_AUTH_TIME = "auth_time";

    private Instant now;
    private int maxAge;

    @BeforeEach
    void setUp() {
        now = Instant.ofEpochSecond(1_700_000_000L + Generators.integers(0, 100_000_000).next());
        maxAge = Generators.integers(60, 3600).next();
    }

    private StepUpResultVerifier verifierWithSkew(Duration skew) {
        return new StepUpResultVerifier(InstantSource.fixed(now), skew);
    }

    private static IdTokenContent idToken(String acr, String authTime) {
        TestTokenHolder holder = TestTokenGenerators.idTokens().next();
        holder.withClaim(CLAIM_ACR, ClaimValue.forPlainString(acr));
        holder.withClaim(CLAIM_AUTH_TIME, ClaimValue.forPlainString(authTime));
        return holder.asIdTokenContent();
    }

    private IdTokenContent authenticatedSecondsAgo(long ageSeconds) {
        return idToken(ACR, Long.toString(now.getEpochSecond() - ageSeconds));
    }

    private static IdTokenContent idTokenWithout(String claimName, String authTime) {
        TestTokenHolder holder = TestTokenGenerators.idTokens().next();
        holder.withClaim(CLAIM_ACR, ClaimValue.forPlainString(ACR));
        holder.withClaim(CLAIM_AUTH_TIME, ClaimValue.forPlainString(authTime));
        holder.withoutClaim(claimName);
        return holder.asIdTokenContent();
    }

    @Nested
    @DisplayName("max_age boundary")
    class MaxAgeBoundary {

        @Test
        @DisplayName("Should accept an auth_time exactly max_age old when no skew is allowed")
        void shouldAcceptExactlyMaxAgeWithoutSkew() {
            var verifier = verifierWithSkew(Duration.ZERO);
            var challenge = new StepUpChallenge(null, maxAge);
            var idToken = authenticatedSecondsAgo(maxAge);

            assertDoesNotThrow(() -> verifier.verify(challenge, idToken),
                    "an authentication exactly max_age old is still fresh");
        }

        @Test
        @DisplayName("Should reject an auth_time one second older than max_age when no skew is allowed")
        void shouldRejectOneSecondPastMaxAgeWithoutSkew() {
            var verifier = verifierWithSkew(Duration.ZERO);
            var challenge = new StepUpChallenge(null, maxAge);
            var idToken = authenticatedSecondsAgo(maxAge + 1L);

            assertThrows(ClientProtocolException.class, () -> verifier.verify(challenge, idToken),
                    "an authentication older than max_age is stale");
        }

        @Test
        @DisplayName("Should accept an auth_time max_age + skew old when the skew is allowed")
        void shouldAcceptMaxAgePlusSkew() {
            int skew = Generators.integers(1, 600).next();
            var verifier = verifierWithSkew(Duration.ofSeconds(skew));
            var challenge = new StepUpChallenge(null, maxAge);
            var idToken = authenticatedSecondsAgo((long) maxAge + skew);

            assertDoesNotThrow(() -> verifier.verify(challenge, idToken),
                    "the opted-in skew widens the max_age boundary by exactly the allowance");
        }

        @Test
        @DisplayName("Should reject an auth_time one second older than max_age + skew")
        void shouldRejectOneSecondPastMaxAgePlusSkew() {
            int skew = Generators.integers(1, 600).next();
            var verifier = verifierWithSkew(Duration.ofSeconds(skew));
            var challenge = new StepUpChallenge(null, maxAge);
            var idToken = authenticatedSecondsAgo((long) maxAge + skew + 1L);

            assertThrows(ClientProtocolException.class, () -> verifier.verify(challenge, idToken),
                    "the skew widens the boundary by the allowance and no further");
        }

        @Test
        @DisplayName("Should apply zero skew on the default construction path")
        void shouldApplyZeroSkewByDefault() {
            var challenge = new StepUpChallenge(null, maxAge);
            var idToken = idToken(ACR, Long.toString(Instant.now().getEpochSecond() - maxAge - 5L));
            var defaultVerifier = new StepUpResultVerifier();
            var skewedVerifier = new StepUpResultVerifier(InstantSource.system(), Duration.ofSeconds(60));

            assertThrows(ClientProtocolException.class, () -> defaultVerifier.verify(challenge, idToken),
                    "the default verifier keeps the exact max_age boundary");
            assertDoesNotThrow(() -> skewedVerifier.verify(challenge, idToken),
                    "the same token passes only once a caller opts into a skew allowance");
        }

        @Test
        @DisplayName("Should reject an auth_time so far in the past that the age computation would overflow")
        void shouldRejectOverflowingAge() {
            var verifier = verifierWithSkew(Duration.ofSeconds(Generators.integers(0, 600).next()));
            var challenge = new StepUpChallenge(null, maxAge);
            var idToken = idToken(ACR, Long.toString(Long.MIN_VALUE));

            assertThrows(ClientProtocolException.class, () -> verifier.verify(challenge, idToken),
                    "an age that overflows saturates to the maximum and stays stale under any finite limit");
        }

        @Test
        @DisplayName("Should accept a future-dated auth_time as fresh: the check bounds staleness only")
        void shouldAcceptFutureDatedAuthTime() {
            var verifier = verifierWithSkew(Duration.ZERO);
            var challenge = new StepUpChallenge(null, maxAge);
            var nearFuture = authenticatedSecondsAgo(-Generators.integers(1, 100_000).next());
            var farFuture = idToken(ACR, Long.toString(Long.MAX_VALUE));

            assertDoesNotThrow(() -> verifier.verify(challenge, nearFuture),
                    "an auth_time ahead of the clock yields a negative age, which never exceeds max_age");
            assertDoesNotThrow(() -> verifier.verify(challenge, farFuture),
                    "even a maximal future auth_time yields a negative age and is accepted as fresh");
        }

        @Test
        @DisplayName("Should not overflow the limit when the skew allowance is at its maximum")
        void shouldSaturateMaximumSkew() {
            var verifier = verifierWithSkew(Duration.ofSeconds(Long.MAX_VALUE));
            var challenge = new StepUpChallenge(null, maxAge);
            var idToken = authenticatedSecondsAgo((long) maxAge + Generators.integers(1, 100_000).next());

            assertDoesNotThrow(() -> verifier.verify(challenge, idToken),
                    "max_age plus a maximal skew saturates instead of wrapping to a negative limit");
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("Should reject a null clock")
        void shouldRejectNullClock() {
            var skew = Duration.ZERO;

            assertThrows(NullPointerException.class, () -> new StepUpResultVerifier(null, skew));
        }

        @Test
        @DisplayName("Should reject a null skew allowance")
        void shouldRejectNullSkew() {
            var clock = InstantSource.system();

            assertThrows(NullPointerException.class, () -> new StepUpResultVerifier(clock, null));
        }

        @Test
        @DisplayName("Should reject a negative skew allowance")
        void shouldRejectNegativeSkew() {
            var clock = InstantSource.system();
            var negative = Duration.ofSeconds(-Generators.integers(1, 600).next());

            assertThrows(IllegalArgumentException.class, () -> new StepUpResultVerifier(clock, negative),
                    "a negative allowance would narrow max_age below what the challenge asked for");
        }
    }

    @Nested
    @DisplayName("acr and auth_time claims")
    class Claims {

        @Test
        @DisplayName("Should accept an acr among the required values")
        void shouldAcceptRequiredAcr() {
            var verifier = verifierWithSkew(Duration.ZERO);
            var challenge = new StepUpChallenge("urn:other " + ACR, null);
            var idToken = authenticatedSecondsAgo(0);

            assertDoesNotThrow(() -> verifier.verify(challenge, idToken));
        }

        @Test
        @DisplayName("Should reject an ID token without an acr claim")
        void shouldRejectMissingAcr() {
            var verifier = verifierWithSkew(Duration.ZERO);
            var challenge = new StepUpChallenge(ACR, null);
            var idToken = idTokenWithout(CLAIM_ACR, Long.toString(now.getEpochSecond()));

            assertThrows(ClientProtocolException.class, () -> verifier.verify(challenge, idToken));
        }

        @Test
        @DisplayName("Should reject an acr that is not among the required values")
        void shouldRejectAcrNotInSet() {
            var verifier = verifierWithSkew(Duration.ZERO);
            var challenge = new StepUpChallenge(ACR, null);
            var idToken = idToken("urn:mace:incommon:iap:bronze", Long.toString(now.getEpochSecond()));

            assertThrows(ClientProtocolException.class, () -> verifier.verify(challenge, idToken));
        }

        @Test
        @DisplayName("Should reject an acr_values constraint that names no value")
        void shouldRejectEmptyAcrConstraint() {
            var verifier = verifierWithSkew(Duration.ZERO);
            var challenge = new StepUpChallenge("   ", null);
            var idToken = authenticatedSecondsAgo(0);

            assertThrows(ClientProtocolException.class, () -> verifier.verify(challenge, idToken),
                    "a constraint that cannot be evaluated fails closed");
        }

        @Test
        @DisplayName("Should reject an ID token without an auth_time claim")
        void shouldRejectMissingAuthTime() {
            var verifier = verifierWithSkew(Duration.ofSeconds(Generators.integers(1, 600).next()));
            var challenge = new StepUpChallenge(null, maxAge);
            var idToken = idTokenWithout(CLAIM_AUTH_TIME, Long.toString(now.getEpochSecond()));

            assertThrows(ClientProtocolException.class, () -> verifier.verify(challenge, idToken),
                    "a skew allowance never substitutes for a missing auth_time");
        }

        @Test
        @DisplayName("Should reject a non-numeric auth_time")
        void shouldRejectNonNumericAuthTime() {
            var verifier = verifierWithSkew(Duration.ofSeconds(Generators.integers(1, 600).next()));
            var challenge = new StepUpChallenge(null, maxAge);
            var idToken = idToken(ACR, Generators.letterStrings(5, 10).next());

            assertThrows(ClientProtocolException.class, () -> verifier.verify(challenge, idToken));
        }
    }
}
