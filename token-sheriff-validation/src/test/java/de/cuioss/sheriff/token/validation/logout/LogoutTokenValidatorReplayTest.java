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
package de.cuioss.sheriff.token.validation.logout;

import de.cuioss.sheriff.token.validation.TokenValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static de.cuioss.sheriff.token.validation.logout.LogoutTokenValidatorSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the replay guard of {@link LogoutTokenValidator} (the {@link LogoutTokenCheck#REPLAYED} and
 * {@link LogoutTokenCheck#REPLAY_GUARD_FULL} checks), its reading of fractional NumericDate claims against the
 * validator's clock, and the construction constraints on the freshness window and the guard capacity. The
 * remaining checks of OpenID Connect Back-Channel Logout 1.0 section 2.6 are covered by
 * {@link LogoutTokenValidatorTest}.
 */
@DisplayName("LogoutTokenValidator replay guard, clock and construction")
class LogoutTokenValidatorReplayTest {

    private TokenValidator tokenValidator;
    private LogoutTokenValidator underTest;

    @BeforeEach
    void setUp() {
        tokenValidator = newTokenValidator();
        underTest = validator(100, CLOCK);
    }

    @AfterEach
    void closeValidator() {
        tokenValidator.close();
    }

    /**
     * RFC 7519 section 2 permits a non-integer NumericDate. The clock sits half a second past
     * {@link LogoutTokenValidatorSupport#NOW}, so every edge lies inside a second, and each fraction is exact in
     * binary so the JSON number carries it unchanged. A validator that rounded the claim to whole seconds would
     * decide the cases marked below the other way.
     */
    @Nested
    @DisplayName("Fractional NumericDate")
    class FractionalNumericDate {

        private LogoutTokenValidator onMidSecondClock;

        @BeforeEach
        void setUpMidSecondClock() {
            onMidSecondClock = validator(100, Clock.fixed(NOW.plusMillis(500), ZoneOffset.UTC));
        }

        @Test
        @DisplayName("exp a fraction of a second after a clock inside the same second is accepted")
        void shouldAcceptExpirationFractionallyInFuture() {
            // Rounded down, exp would be NOW, which is before the clock, and the token would be refused
            assertTrue(onMidSecondClock.validate(withMembers("exp", NOW, ".75", "jti-exp-future")).isAccepted());
        }

        @Test
        @DisplayName("exp a fraction of a second before the clock is refused as EXPIRATION")
        void shouldRefuseExpirationFractionallyInPast() {
            assertRefused(LogoutTokenCheck.EXPIRATION,
                    onMidSecondClock.validate(withMembers("exp", NOW, ".25", "jti-exp-past")));
        }

        @Test
        @DisplayName("iat a fraction of a second inside the earliest edge is accepted, just outside is refused")
        void shouldHonourFractionalEarliestEdge() {
            // The earliest edge is NOW - WINDOW + 0.5; rounded down, the inside case would fall before it
            assertTrue(onMidSecondClock.validate(withMembers("iat", NOW.minus(WINDOW), ".75", "jti-early-inside"))
                    .isAccepted());
            assertRefused(LogoutTokenCheck.ISSUED_AT,
                    onMidSecondClock.validate(withMembers("iat", NOW.minus(WINDOW), ".25", "jti-early-outside")));
        }

        @Test
        @DisplayName("iat a fraction of a second inside the latest edge is accepted, just outside is refused")
        void shouldHonourFractionalLatestEdge() {
            // The latest edge is NOW + WINDOW + 0.5; rounded down, the outside case would fall on NOW + WINDOW
            assertTrue(onMidSecondClock.validate(withMembers("iat", NOW.plus(WINDOW), ".25", "jti-late-inside"))
                    .isAccepted());
            assertRefused(LogoutTokenCheck.ISSUED_AT,
                    onMidSecondClock.validate(withMembers("iat", NOW.plus(WINDOW), ".75", "jti-late-outside")));
        }

        @Test
        @DisplayName("A fractional iat retains the jti until iat plus the window, exactly")
        void shouldRetainJtiUntilFractionalIssuedAtPlusWindow() {
            MutableClock clock = new MutableClock(NOW);
            LogoutTokenValidator onMutableClock = validator(10, clock);
            Map<String, String> members = validJsonMembers();
            members.put("iat", NOW.getEpochSecond() + ".75");
            members.put("exp", String.valueOf(NOW.plus(WINDOW).plusSeconds(60).getEpochSecond()));
            String token = signedJson(members);
            assertTrue(onMutableClock.validate(token).isAccepted(), "first presentation");

            // At NOW + WINDOW + 0.5 the token is still fresh, so the entry must still be retained
            clock.set(NOW.plus(WINDOW).plusMillis(500));

            assertRefused(LogoutTokenCheck.REPLAYED, onMutableClock.validate(token));
        }

        /**
         * @return a signed logout token whose {@code claim} is {@code base} in epoch seconds followed by the
         * literal {@code fraction}, and whose {@code jti} is {@code jti}
         */
        private String withMembers(String claim, Instant base, String fraction, String jti) {
            Map<String, String> members = validJsonMembers();
            members.put(claim, base.getEpochSecond() + fraction);
            members.put("jti", quoted(jti));
            return signedJson(members);
        }
    }

    @Nested
    @DisplayName("REPLAYED and REPLAY_GUARD_FULL")
    class Replay {

        @Test
        @DisplayName("A jti replayed inside its retention window is refused as REPLAYED")
        void shouldRefuseReplay() {
            String token = signed(validClaims(), "logout+jwt");

            assertTrue(underTest.validate(token).isAccepted(), "first presentation");
            assertRefused(LogoutTokenCheck.REPLAYED, underTest.validate(token));
        }

        @Test
        @DisplayName("The guard retains a jti on the validator's clock for the whole freshness window of its iat")
        void shouldRetainJtiOnValidatorClock() {
            MutableClock clock = new MutableClock(NOW);
            LogoutTokenValidator onMutableClock = validator(10, clock);
            Map<String, Object> claims = validClaims();
            claims.put("exp", NOW.plus(WINDOW).plusSeconds(60).getEpochSecond());
            String token = signed(claims, "logout+jwt");
            assertTrue(onMutableClock.validate(token).isAccepted(), "first presentation");

            clock.set(NOW.plus(WINDOW));
            assertRefused(LogoutTokenCheck.REPLAYED, onMutableClock.validate(token));

            clock.set(NOW.plus(WINDOW).plusSeconds(1));
            assertRefused(LogoutTokenCheck.ISSUED_AT, onMutableClock.validate(token));
        }

        @Test
        @DisplayName("A replay at the iat + window edge is refused although a second clock read would be past it")
        void shouldRefuseReplayAtEdgeWhenClockReadsStraddle() {
            Instant edge = NOW.plus(WINDOW);
            Instant pastEdge = edge.plusSeconds(1);
            Map<String, Object> claims = validClaims();
            claims.put("exp", pastEdge.plusSeconds(60).getEpochSecond());
            String token = signed(claims, "logout+jwt");
            Map<String, Object> otherJti = new LinkedHashMap<>(claims);
            otherJti.put("jti", "jti-straddle-control");
            // Every validation after the first sees the edge on its first clock read and an instant past the edge
            // on any further read: a validator that read the clock twice would purge the entry and admit the replay
            StraddlingClock clock = new StraddlingClock(NOW, edge, pastEdge);
            LogoutTokenValidator onStraddlingClock = validator(10, clock);
            assertTrue(onStraddlingClock.validate(token).isAccepted(), "first presentation");

            clock.straddleNextValidation();
            assertRefused(LogoutTokenCheck.REPLAYED, onStraddlingClock.validate(token));
            assertEquals(1, clock.readsOfLastValidation(), "the validator reads its clock once per validation");

            clock.straddleNextValidation();
            assertTrue(onStraddlingClock.validate(signed(otherJti, "logout+jwt")).isAccepted(),
                    "positive control: a new jti on the same straddling instants passes freshness at the edge");
        }

        @Test
        @DisplayName("A replay inside the freshness window is refused as REPLAYED on a straddling clock")
        void shouldRefuseReplayInsideWindowOnStraddlingClock() {
            Instant inside = NOW.plusSeconds(60);
            StraddlingClock clock = new StraddlingClock(NOW, inside, NOW.plus(WINDOW).plusSeconds(1));
            LogoutTokenValidator onStraddlingClock = validator(10, clock);
            String token = signed(validClaims(), "logout+jwt");
            assertTrue(onStraddlingClock.validate(token).isAccepted(), "first presentation");

            clock.straddleNextValidation();

            assertRefused(LogoutTokenCheck.REPLAYED, onStraddlingClock.validate(token));
        }

        @Test
        @DisplayName("A later validation on another thread cannot purge the entry an earlier reading is still deciding against")
        void shouldNotLetLaterReadingPurgeEntryOfEarlierConcurrentValidation() throws Exception {
            Instant edge = NOW.plus(WINDOW);
            Instant pastEdge = edge.plusSeconds(1);
            Map<String, Object> replayed = validClaims();
            replayed.put("exp", pastEdge.plusSeconds(60).getEpochSecond());
            String replayedToken = signed(replayed, "logout+jwt");
            // A fresh token at pastEdge: its admission purges every entry retained only until the edge
            Map<String, Object> later = validClaims();
            later.put("jti", "jti-later-reading");
            later.put("iat", pastEdge.getEpochSecond());
            later.put("exp", pastEdge.plusSeconds(60).getEpochSecond());
            String laterToken = signed(later, "logout+jwt");

            ConcurrentReadingClock clock = new ConcurrentReadingClock(NOW, edge, pastEdge);
            LogoutTokenValidator onClock = validator(10, clock);
            assertTrue(onClock.validate(replayedToken).isAccepted(), "first presentation");
            clock.arm();

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                // Thread A reads the edge instant, where the replayed token is still fresh, and then stays inside
                // its decision until the later validation has finished or the wait runs out
                Future<LogoutTokenValidationResult> earlier = executor.submit(() -> {
                    clock.markCurrentThreadAsEarlierReading();
                    return onClock.validate(replayedToken);
                });
                assertTrue(clock.awaitEarlierReadingDone(), "the earlier validation read its clock");

                LogoutTokenValidationResult laterResult = onClock.validate(laterToken);
                clock.laterValidationFinished();

                assertTrue(laterResult.isAccepted(), "the later token is fresh at its own instant");
                assertRefused(LogoutTokenCheck.REPLAYED, earlier.get(10, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @DisplayName("A new jti is refused as REPLAY_GUARD_FULL when the guard is at capacity")
        void shouldRefuseWhenGuardFull() {
            LogoutTokenValidator tight = validator(1, CLOCK);
            Map<String, Object> second = validClaims();
            second.put("jti", "jti-2");

            assertTrue(tight.validate(signed(validClaims(), "logout+jwt")).isAccepted());
            assertRefused(LogoutTokenCheck.REPLAY_GUARD_FULL, tight.validate(signed(second, "logout+jwt")));
        }

        @Test
        @DisplayName("A token refused by an earlier check takes no guard capacity")
        void shouldNotConsumeCapacityOnRefusal() {
            LogoutTokenValidator tight = validator(1, CLOCK);
            Map<String, Object> withNonce = validClaims();
            withNonce.put("nonce", "n");

            assertRefused(LogoutTokenCheck.NONCE, tight.validate(signed(withNonce, "logout+jwt")));
            assertTrue(tight.validate(signed(validClaims(), "logout+jwt")).isAccepted(),
                    "the single slot is still free");
        }
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("A zero or negative freshness window is rejected")
        void shouldRejectNonPositiveWindow() {
            var zero = LogoutTokenValidator.builder().tokenValidator(tokenValidator).expectedIssuer(ISSUER)
                    .expectedAudience(AUDIENCE).freshnessWindow(Duration.ZERO).replayGuardCapacity(1).clock(CLOCK);
            var negative = LogoutTokenValidator.builder().tokenValidator(tokenValidator).expectedIssuer(ISSUER)
                    .expectedAudience(AUDIENCE).freshnessWindow(Duration.ofSeconds(-1)).replayGuardCapacity(1)
                    .clock(CLOCK);

            assertThrows(IllegalArgumentException.class, zero::build);
            assertThrows(IllegalArgumentException.class, negative::build);
        }

        @Test
        @DisplayName("A replay guard capacity of zero, negative or unset is rejected")
        void shouldRejectNonPositiveCapacity() {
            var zero = LogoutTokenValidator.builder().tokenValidator(tokenValidator).expectedIssuer(ISSUER)
                    .expectedAudience(AUDIENCE).freshnessWindow(WINDOW).replayGuardCapacity(0).clock(CLOCK);
            var negative = LogoutTokenValidator.builder().tokenValidator(tokenValidator).expectedIssuer(ISSUER)
                    .expectedAudience(AUDIENCE).freshnessWindow(WINDOW).replayGuardCapacity(-1).clock(CLOCK);
            var unset = LogoutTokenValidator.builder().tokenValidator(tokenValidator).expectedIssuer(ISSUER)
                    .expectedAudience(AUDIENCE).freshnessWindow(WINDOW).clock(CLOCK);

            assertThrows(IllegalArgumentException.class, zero::build);
            assertThrows(IllegalArgumentException.class, negative::build);
            assertThrows(IllegalArgumentException.class, unset::build);
            assertNotNull(validator(1, CLOCK), "positive control: capacity 1");
        }

        @Test
        @DisplayName("A blank issuer or audience is rejected")
        void shouldRejectBlankIdentifiers() {
            var blankIssuer = LogoutTokenValidator.builder().tokenValidator(tokenValidator).expectedIssuer(" ")
                    .expectedAudience(AUDIENCE).freshnessWindow(WINDOW).replayGuardCapacity(1).clock(CLOCK);
            var blankAudience = LogoutTokenValidator.builder().tokenValidator(tokenValidator).expectedIssuer(ISSUER)
                    .expectedAudience("").freshnessWindow(WINDOW).replayGuardCapacity(1).clock(CLOCK);

            assertThrows(IllegalArgumentException.class, blankIssuer::build);
            assertThrows(IllegalArgumentException.class, blankAudience::build);
        }

        @Test
        @DisplayName("A missing collaborator is rejected")
        void shouldRejectNullCollaborator() {
            var noClock = LogoutTokenValidator.builder().tokenValidator(tokenValidator).expectedIssuer(ISSUER)
                    .expectedAudience(AUDIENCE).freshnessWindow(WINDOW).replayGuardCapacity(1);
            var noValidator = LogoutTokenValidator.builder().expectedIssuer(ISSUER).expectedAudience(AUDIENCE)
                    .freshnessWindow(WINDOW).replayGuardCapacity(1).clock(CLOCK);

            assertThrows(NullPointerException.class, noClock::build);
            assertThrows(NullPointerException.class, noValidator::build);
        }
    }

    // === Fixtures ===

    private LogoutTokenValidator validator(int replayGuardCapacity, Clock clock) {
        return logoutValidator(tokenValidator, replayGuardCapacity, clock);
    }

    /**
     * A clock that answers {@code initial} until it is armed; once armed by {@link #straddleNextValidation()},
     * its first read answers {@code firstRead} and every further read {@code laterRead}, so two reads within one
     * validation straddle whatever edge lies between the two instants.
     */
    private static final class StraddlingClock extends Clock {

        private final Instant initial;
        private final Instant firstRead;
        private final Instant laterRead;
        private final AtomicInteger reads = new AtomicInteger();
        private volatile boolean armed;

        StraddlingClock(Instant initial, Instant firstRead, Instant laterRead) {
            this.initial = initial;
            this.firstRead = firstRead;
            this.laterRead = laterRead;
        }

        void straddleNextValidation() {
            reads.set(0);
            armed = true;
        }

        int readsOfLastValidation() {
            return reads.get();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            int read = reads.incrementAndGet();
            if (!armed) {
                return initial;
            }
            return read == 1 ? firstRead : laterRead;
        }
    }

    /**
     * A clock for two validations racing on two threads. Until armed it answers {@code initial}. Once armed, the
     * thread marked as the earlier reading reads {@code earlier}, announces that it has read, and then holds its
     * decision open until {@link #laterValidationFinished()} or a bounded wait; every other thread reads
     * {@code later}. A validator that does not order clock read and admission lets the later reading finish
     * first and purge the entry; one that does makes the later validation wait for the earlier decision.
     */
    private static final class ConcurrentReadingClock extends Clock {

        private final Instant initial;
        private final Instant earlier;
        private final Instant later;
        private final CountDownLatch earlierRead = new CountDownLatch(1);
        private final CountDownLatch laterFinished = new CountDownLatch(1);
        private final ThreadLocal<Boolean> earlierReading = ThreadLocal.withInitial(() -> Boolean.FALSE);
        private volatile boolean armed;

        ConcurrentReadingClock(Instant initial, Instant earlier, Instant later) {
            this.initial = initial;
            this.earlier = earlier;
            this.later = later;
        }

        void arm() {
            armed = true;
        }

        void markCurrentThreadAsEarlierReading() {
            earlierReading.set(Boolean.TRUE);
        }

        boolean awaitEarlierReadingDone() throws InterruptedException {
            return earlierRead.await(10, TimeUnit.SECONDS);
        }

        void laterValidationFinished() {
            laterFinished.countDown();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            if (!armed) {
                return initial;
            }
            if (!Boolean.TRUE.equals(earlierReading.get())) {
                return later;
            }
            earlierRead.countDown();
            try {
                // A validator that orders read and admission keeps the later validation waiting, so this wait
                // runs out; one that does not lets the later validation finish and release it at once
                laterFinished.await(1500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return earlier;
        }
    }

    /**
     * A clock whose instant the test sets.
     */
    private static final class MutableClock extends Clock {

        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant newInstant) {
            this.instant = newInstant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
