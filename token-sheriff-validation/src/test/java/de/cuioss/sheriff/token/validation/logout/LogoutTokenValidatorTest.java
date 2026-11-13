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

import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import io.jsonwebtoken.Jwts;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link LogoutTokenValidator}: one test per check of OpenID Connect Back-Channel Logout 1.0 section 2.6,
 * each paired with the accepted positive control, and every refusal asserting the identity of the
 * {@link LogoutTokenCheck} that failed.
 */
@DisplayName("LogoutTokenValidator")
class LogoutTokenValidatorTest {

    private static final String ISSUER = "https://logout-issuer.example.com";
    private static final String OTHER_ISSUER = "https://other-logout-issuer.example.com";
    private static final String AUDIENCE = "logout-client";
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private TokenValidator tokenValidator;
    private LogoutTokenValidator underTest;

    @BeforeEach
    void setUp() {
        tokenValidator = TokenValidator.builder()
                .issuerConfig(issuerConfig(ISSUER))
                .issuerConfig(issuerConfig(OTHER_ISSUER))
                .build();
        underTest = validator(100, CLOCK);
    }

    @AfterEach
    void closeValidator() {
        tokenValidator.close();
    }

    @Nested
    @DisplayName("Positive control")
    class PositiveControl {

        @Test
        @DisplayName("A valid logout token is accepted and carries iss, sub, sid and jti")
        void shouldAcceptValidLogoutToken() {
            LogoutTokenValidationResult result = underTest.validate(signed(validClaims(), "logout+jwt"));

            assertTrue(result.isAccepted(), result::toString);
            assertEquals(Optional.empty(), result.getFailedCheck());
            assertEquals(Optional.of(ISSUER), result.getIssuer());
            assertEquals(Optional.of("subject-1"), result.getSubject());
            assertEquals(Optional.of("session-1"), result.getSessionId());
            assertEquals(Optional.of("jti-1"), result.getJwtId());
            assertEquals("LogoutTokenValidationResult(accepted)", result.toString());
        }
    }

    @Nested
    @DisplayName("SIGNATURE")
    class Signature {

        @Test
        @DisplayName("A token whose payload was changed after signing is refused as SIGNATURE")
        void shouldRefuseTamperedSignature() {
            String original = signed(validClaims(), "logout+jwt");
            Map<String, Object> changed = validClaims();
            changed.put("sub", "someone-else");
            String[] parts = original.split("\\.");
            String tampered = parts[0] + "." + signed(changed, "logout+jwt").split("\\.")[1] + "." + parts[2];

            assertRefused(LogoutTokenCheck.SIGNATURE, underTest.validate(tampered));
            assertTrue(underTest.validate(original).isAccepted(), "positive control: the untouched token");
        }

        @Test
        @DisplayName("A null or blank token is refused as SIGNATURE")
        void shouldRefuseEmptyToken() {
            assertRefused(LogoutTokenCheck.SIGNATURE, underTest.validate(null));
            assertRefused(LogoutTokenCheck.SIGNATURE, underTest.validate("  "));
        }

        @Test
        @DisplayName("A refused result names only the failed check, never a claim")
        void shouldExposeNoClaimOnRefusal() {
            LogoutTokenValidationResult result = underTest.validate("not-a-token");

            assertRefused(LogoutTokenCheck.SIGNATURE, result);
            assertEquals(Optional.empty(), result.getIssuer());
            assertEquals(Optional.empty(), result.getSubject());
            assertEquals(Optional.empty(), result.getSessionId());
            assertEquals(Optional.empty(), result.getJwtId());
            assertEquals("LogoutTokenValidationResult(refused=SIGNATURE)", result.toString());
        }
    }

    @Nested
    @DisplayName("TYPE")
    class Type {

        @ParameterizedTest(name = "typ \"{0}\" is accepted")
        @ValueSource(strings = {"logout+jwt", "application/logout+jwt", "Logout+JWT", "Application/Logout+Jwt"})
        @DisplayName("logout+jwt and application/logout+jwt are accepted in any case")
        void shouldAcceptLogoutTypes(String typ) {
            assertTrue(underTest.validate(signed(validClaims(), typ)).isAccepted());
        }

        @Test
        @DisplayName("An absent typ is accepted")
        void shouldAcceptAbsentTyp() {
            assertTrue(underTest.validate(signed(validClaims(), null)).isAccepted());
        }

        @ParameterizedTest(name = "typ \"{0}\" is refused as TYPE")
        @ValueSource(strings = {"at+jwt", "JWT"})
        @DisplayName("A typ other than the logout types is refused as TYPE")
        void shouldRefuseOtherTypes(String typ) {
            assertRefused(LogoutTokenCheck.TYPE, underTest.validate(signed(validClaims(), typ)));
        }
    }

    @Nested
    @DisplayName("ISSUER")
    class Issuer {

        @Test
        @DisplayName("A token of another trusted issuer is refused as ISSUER")
        void shouldRefuseWrongIssuer() {
            Map<String, Object> claims = validClaims();
            claims.put("iss", OTHER_ISSUER);

            assertRefused(LogoutTokenCheck.ISSUER, underTest.validate(signed(claims, "logout+jwt")));
        }
    }

    @Nested
    @DisplayName("AUDIENCE")
    class Audience {

        @Test
        @DisplayName("An array audience containing the expected audience is accepted")
        void shouldAcceptArrayAudience() {
            Map<String, Object> claims = validClaims();
            claims.put("aud", List.of("someone-else", AUDIENCE));

            assertTrue(underTest.validate(signed(claims, "logout+jwt")).isAccepted());
        }

        @Test
        @DisplayName("A string audience other than the expected one is refused as AUDIENCE")
        void shouldRefuseWrongStringAudience() {
            Map<String, Object> claims = validClaims();
            claims.put("aud", "someone-else");

            assertRefused(LogoutTokenCheck.AUDIENCE, underTest.validate(signed(claims, "logout+jwt")));
        }

        @Test
        @DisplayName("An array audience lacking the expected audience is refused as AUDIENCE")
        void shouldRefuseWrongArrayAudience() {
            Map<String, Object> claims = validClaims();
            claims.put("aud", List.of("someone-else", "another-one"));

            assertRefused(LogoutTokenCheck.AUDIENCE, underTest.validate(signed(claims, "logout+jwt")));
        }

        @Test
        @DisplayName("An absent audience is refused as AUDIENCE")
        void shouldRefuseAbsentAudience() {
            Map<String, Object> claims = validClaims();
            claims.remove("aud");

            assertRefused(LogoutTokenCheck.AUDIENCE, underTest.validate(signed(claims, "logout+jwt")));
        }
    }

    @Nested
    @DisplayName("ISSUED_AT")
    class IssuedAt {

        @Test
        @DisplayName("iat exactly at the earliest edge of the window is accepted, one second earlier is refused")
        void shouldHonourEarliestEdge() {
            assertTrue(underTest.validate(withIssuedAt(NOW.minus(WINDOW), "jti-early-edge")).isAccepted());
            assertRefused(LogoutTokenCheck.ISSUED_AT,
                    underTest.validate(withIssuedAt(NOW.minus(WINDOW).minusSeconds(1), "jti-early-beyond")));
        }

        @Test
        @DisplayName("iat exactly at the latest edge of the window is accepted, one second later is refused")
        void shouldHonourLatestEdge() {
            assertTrue(underTest.validate(withIssuedAt(NOW.plus(WINDOW), "jti-late-edge")).isAccepted());
            assertRefused(LogoutTokenCheck.ISSUED_AT,
                    underTest.validate(withIssuedAt(NOW.plus(WINDOW).plusSeconds(1), "jti-late-beyond")));
        }

        @Test
        @DisplayName("A token without iat is refused as ISSUED_AT")
        void shouldRefuseMissingIssuedAt() {
            Map<String, Object> claims = validClaims();
            claims.remove("iat");

            assertRefused(LogoutTokenCheck.ISSUED_AT, underTest.validate(signed(claims, "logout+jwt")));
        }

        @Test
        @DisplayName("A token whose iat is a string is refused as ISSUED_AT")
        void shouldRefuseNonNumericIssuedAt() {
            Map<String, String> members = validJsonMembers();
            members.put("iat", quoted(String.valueOf(NOW.getEpochSecond())));

            assertRefused(LogoutTokenCheck.ISSUED_AT, underTest.validate(signedJson(members)));
            assertTrue(underTest.validate(signedJson(validJsonMembers())).isAccepted(),
                    "positive control: the same payload with a numeric iat");
        }

        private String withIssuedAt(Instant issuedAt, String jti) {
            Map<String, Object> claims = validClaims();
            claims.put("iat", issuedAt.getEpochSecond());
            claims.put("jti", jti);
            return signed(claims, "logout+jwt");
        }
    }

    @Nested
    @DisplayName("EXPIRATION")
    class Expiration {

        @Test
        @DisplayName("exp exactly now is refused (RFC 7519 section 4.1.4), one second after now is accepted")
        void shouldHonourExpirationEdge() {
            assertRefused(LogoutTokenCheck.EXPIRATION, underTest.validate(withExpiration(NOW, "jti-exp-now")));
            assertRefused(LogoutTokenCheck.EXPIRATION,
                    underTest.validate(withExpiration(NOW.minusSeconds(1), "jti-exp-past")));
            assertTrue(underTest.validate(withExpiration(NOW.plusSeconds(1), "jti-exp-next")).isAccepted(),
                    "positive control: exp one second after now");
        }

        @Test
        @DisplayName("A token without exp is refused as EXPIRATION")
        void shouldRefuseMissingExpiration() {
            Map<String, Object> claims = validClaims();
            claims.remove("exp");

            assertRefused(LogoutTokenCheck.EXPIRATION, underTest.validate(signed(claims, "logout+jwt")));
        }

        @Test
        @DisplayName("A token whose exp is a string is refused as EXPIRATION")
        void shouldRefuseNonNumericExpiration() {
            Map<String, String> members = validJsonMembers();
            members.put("exp", quoted("tomorrow"));

            assertRefused(LogoutTokenCheck.EXPIRATION, underTest.validate(signedJson(members)));
            assertTrue(underTest.validate(signedJson(validJsonMembers())).isAccepted(),
                    "positive control: the same payload with a numeric exp");
        }

        private String withExpiration(Instant expiration, String jti) {
            Map<String, Object> claims = validClaims();
            claims.put("exp", expiration.getEpochSecond());
            claims.put("jti", jti);
            return signed(claims, "logout+jwt");
        }
    }

    /**
     * RFC 7519 section 2 permits a non-integer NumericDate. The clock sits half a second past {@link #NOW}, so
     * every edge lies inside a second, and each fraction is exact in binary so the JSON number carries it
     * unchanged. A validator that rounded the claim to whole seconds would decide the cases marked below the
     * other way.
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
    @DisplayName("EVENTS")
    class Events {

        @Test
        @DisplayName("events as a string naming the logout event is refused as EVENTS")
        void shouldRefuseEventsAsString() {
            assertRefused(LogoutTokenCheck.EVENTS, withEvents(LogoutTokenValidator.BACKCHANNEL_LOGOUT_EVENT));
        }

        @Test
        @DisplayName("events as an array naming the logout event is refused as EVENTS")
        void shouldRefuseEventsAsArray() {
            assertRefused(LogoutTokenCheck.EVENTS, withEvents(List.of(LogoutTokenValidator.BACKCHANNEL_LOGOUT_EVENT)));
        }

        @Test
        @DisplayName("events as an object lacking the logout event member is refused as EVENTS")
        void shouldRefuseEventsLackingMember() {
            assertRefused(LogoutTokenCheck.EVENTS,
                    withEvents(Map.of("http://schemas.openid.net/event/other", Map.of())));
        }

        @Test
        @DisplayName("events as a string merely containing the event URI is refused as EVENTS")
        void shouldRefuseEventsAsTextContainingUri() {
            assertRefused(LogoutTokenCheck.EVENTS,
                    withEvents("{\"" + LogoutTokenValidator.BACKCHANNEL_LOGOUT_EVENT + "\":{}}"));
        }

        @Test
        @DisplayName("A logout event member that is not a JSON object is refused as EVENTS")
        void shouldRefuseNonObjectMember() {
            assertRefused(LogoutTokenCheck.EVENTS,
                    withEvents(Map.of(LogoutTokenValidator.BACKCHANNEL_LOGOUT_EVENT, "present")));
        }

        @Test
        @DisplayName("An ID token, which carries no events, is refused as EVENTS")
        void shouldRefuseIdToken() {
            Map<String, Object> idToken = new LinkedHashMap<>();
            idToken.put("iss", ISSUER);
            idToken.put("sub", "subject-1");
            idToken.put("aud", AUDIENCE);
            idToken.put("azp", AUDIENCE);
            idToken.put("iat", NOW.getEpochSecond());
            idToken.put("exp", NOW.plusSeconds(300).getEpochSecond());
            idToken.put("sid", "session-1");
            idToken.put("jti", "id-token-jti");

            assertRefused(LogoutTokenCheck.EVENTS, underTest.validate(signed(idToken, null)));
        }

        private LogoutTokenValidationResult withEvents(Object events) {
            Map<String, Object> claims = validClaims();
            claims.put("events", events);
            return underTest.validate(signed(claims, "logout+jwt"));
        }
    }

    @Nested
    @DisplayName("NONCE")
    class Nonce {

        @Test
        @DisplayName("A token carrying nonce is refused as NONCE")
        void shouldRefuseNonce() {
            Map<String, Object> claims = validClaims();
            claims.put("nonce", "n-0S6_WzA2Mj");

            assertRefused(LogoutTokenCheck.NONCE, underTest.validate(signed(claims, "logout+jwt")));
        }

        @Test
        @DisplayName("A token carrying nonce as JSON null is refused as NONCE")
        void shouldRefuseNullNonce() {
            Map<String, String> members = validJsonMembers();
            members.put("nonce", "null");

            assertRefused(LogoutTokenCheck.NONCE, underTest.validate(signedJson(members)));
            assertTrue(underTest.validate(signedJson(validJsonMembers())).isAccepted(),
                    "positive control: the same payload without nonce");
        }
    }

    @Nested
    @DisplayName("SUBJECT_OR_SESSION")
    class SubjectOrSession {

        @Test
        @DisplayName("A token with sid only is accepted")
        void shouldAcceptSessionOnly() {
            Map<String, Object> claims = validClaims();
            claims.remove("sub");

            LogoutTokenValidationResult result = underTest.validate(signed(claims, "logout+jwt"));

            assertTrue(result.isAccepted(), result::toString);
            assertEquals(Optional.empty(), result.getSubject());
            assertEquals(Optional.of("session-1"), result.getSessionId());
        }

        @Test
        @DisplayName("A token with sub only is accepted")
        void shouldAcceptSubjectOnly() {
            Map<String, Object> claims = validClaims();
            claims.remove("sid");

            LogoutTokenValidationResult result = underTest.validate(signed(claims, "logout+jwt"));

            assertTrue(result.isAccepted(), result::toString);
            assertEquals(Optional.of("subject-1"), result.getSubject());
            assertEquals(Optional.empty(), result.getSessionId());
        }

        @Test
        @DisplayName("A token with neither sub nor sid is refused as SUBJECT_OR_SESSION")
        void shouldRefuseNeither() {
            Map<String, Object> claims = validClaims();
            claims.remove("sub");
            claims.remove("sid");

            assertRefused(LogoutTokenCheck.SUBJECT_OR_SESSION, underTest.validate(signed(claims, "logout+jwt")));
        }

        @Test
        @DisplayName("A sid that is not a string is refused as SUBJECT_OR_SESSION")
        void shouldRefuseNonStringSession() {
            Map<String, Object> claims = validClaims();
            claims.put("sid", 42);

            assertRefused(LogoutTokenCheck.SUBJECT_OR_SESSION, underTest.validate(signed(claims, "logout+jwt")));
        }

        @ParameterizedTest(name = "sub {0} is refused as SIGNATURE")
        @ValueSource(strings = {"42", "true", "[\"subject-1\"]", "{\"id\":\"subject-1\"}"})
        @DisplayName("A sub that is a JSON number, boolean, array or object is refused as SIGNATURE")
        void shouldRefuseNonStringSubjectAsSignature(String subJson) {
            Map<String, String> members = validJsonMembers();
            members.put("sub", subJson);

            // The signature-only entry maps sub strictly as a string and refuses any other JSON type before the
            // logout checks run, so the refusal is SIGNATURE (documented on LogoutTokenCheck#SIGNATURE)
            assertRefused(LogoutTokenCheck.SIGNATURE, underTest.validate(signedJson(members)));
            assertTrue(underTest.validate(signedJson(validJsonMembers())).isAccepted(),
                    "positive control: the same payload with a string sub");
        }

        @Test
        @DisplayName("A sub given as JSON null beside a valid sid is refused as SUBJECT_OR_SESSION")
        void shouldRefuseNullSubject() {
            Map<String, String> members = validJsonMembers();
            members.put("sub", "null");

            assertRefused(LogoutTokenCheck.SUBJECT_OR_SESSION, underTest.validate(signedJson(members)));
            members.remove("sub");
            assertTrue(underTest.validate(signedJson(members)).isAccepted(),
                    "positive control: the same payload with sub omitted");
        }
    }

    @Nested
    @DisplayName("JWT_ID")
    class JwtId {

        @Test
        @DisplayName("A token without jti is refused as JWT_ID")
        void shouldRefuseMissingJti() {
            Map<String, Object> claims = validClaims();
            claims.remove("jti");

            assertRefused(LogoutTokenCheck.JWT_ID, underTest.validate(signed(claims, "logout+jwt")));
        }

        @Test
        @DisplayName("A token with a blank jti is refused as JWT_ID")
        void shouldRefuseBlankJti() {
            Map<String, Object> claims = validClaims();
            claims.put("jti", "   ");

            assertRefused(LogoutTokenCheck.JWT_ID, underTest.validate(signed(claims, "logout+jwt")));
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
        return LogoutTokenValidator.builder()
                .tokenValidator(tokenValidator)
                .expectedIssuer(ISSUER)
                .expectedAudience(AUDIENCE)
                .freshnessWindow(WINDOW)
                .replayGuardCapacity(replayGuardCapacity)
                .clock(clock)
                .build();
    }

    /**
     * @return mutable claims of a logout token that passes every check
     */
    private static Map<String, Object> validClaims() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", ISSUER);
        claims.put("sub", "subject-1");
        claims.put("sid", "session-1");
        claims.put("aud", AUDIENCE);
        claims.put("iat", NOW.getEpochSecond());
        claims.put("exp", NOW.plusSeconds(120).getEpochSecond());
        claims.put("jti", "jti-1");
        claims.put("events", Map.of(LogoutTokenValidator.BACKCHANNEL_LOGOUT_EVENT, Map.of()));
        return claims;
    }

    private static String signed(Map<String, Object> claims, @Nullable String typ) {
        var header = Jwts.builder().header().keyId(InMemoryKeyMaterialHandler.DEFAULT_KEY_ID);
        if (typ != null) {
            header.type(typ);
        }
        return header.and()
                .claims(claims)
                .signWith(InMemoryKeyMaterialHandler.getDefaultPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * @return mutable members of a logout token that passes every check, each value as literal JSON text
     */
    private static Map<String, String> validJsonMembers() {
        Map<String, String> members = new LinkedHashMap<>();
        members.put("iss", quoted(ISSUER));
        members.put("sub", quoted("subject-1"));
        members.put("sid", quoted("session-1"));
        members.put("aud", quoted(AUDIENCE));
        members.put("iat", String.valueOf(NOW.getEpochSecond()));
        members.put("exp", String.valueOf(NOW.plusSeconds(120).getEpochSecond()));
        members.put("jti", quoted("jti-1"));
        members.put("events", "{" + quoted(LogoutTokenValidator.BACKCHANNEL_LOGOUT_EVENT) + ":{}}");
        return members;
    }

    /**
     * Signs a payload written as literal JSON with the default RS256 test key and {@code typ: logout+jwt},
     * so a JSON {@code null} or a string-typed date reaches the parser exactly as written.
     *
     * @param members the payload members, each value as literal JSON text
     * @return the compact JWS
     */
    private static String signedJson(Map<String, String> members) {
        String header = "{\"alg\":\"RS256\",\"kid\":" + quoted(InMemoryKeyMaterialHandler.DEFAULT_KEY_ID)
                + ",\"typ\":\"logout+jwt\"}";
        String payload = members.entrySet().stream()
                .map(member -> quoted(member.getKey()) + ":" + member.getValue())
                .collect(Collectors.joining(",", "{", "}"));
        String signingInput = base64Url(header) + "." + base64Url(payload);
        try {
            java.security.Signature signer = java.security.Signature.getInstance("SHA256withRSA");
            signer.initSign(InMemoryKeyMaterialHandler.getDefaultPrivateKey());
            signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign the test token", e);
        }
    }

    private static String quoted(String text) {
        return "\"" + text + "\"";
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static IssuerConfig issuerConfig(String issuer) {
        return IssuerConfig.builder()
                .issuerIdentifier(issuer)
                .expectedAudience(AUDIENCE)
                .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                .build();
    }

    private static void assertRefused(LogoutTokenCheck expected, LogoutTokenValidationResult result) {
        assertFalse(result.isAccepted(), () -> "expected refusal as " + expected + " but was " + result);
        assertEquals(Optional.of(expected), result.getFailedCheck());
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
