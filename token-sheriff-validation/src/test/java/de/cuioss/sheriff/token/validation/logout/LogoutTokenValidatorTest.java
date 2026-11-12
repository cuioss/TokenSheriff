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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

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
        underTest = validator(new LogoutTokenReplayGuard(100, CLOCK));
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
        @DisplayName("exp exactly now is accepted, one second in the past is refused")
        void shouldHonourExpirationEdge() {
            assertTrue(underTest.validate(withExpiration(NOW, "jti-exp-now")).isAccepted());
            assertRefused(LogoutTokenCheck.EXPIRATION,
                    underTest.validate(withExpiration(NOW.minusSeconds(1), "jti-exp-past")));
        }

        @Test
        @DisplayName("A token without exp is refused as EXPIRATION")
        void shouldRefuseMissingExpiration() {
            Map<String, Object> claims = validClaims();
            claims.remove("exp");

            assertRefused(LogoutTokenCheck.EXPIRATION, underTest.validate(signed(claims, "logout+jwt")));
        }

        private String withExpiration(Instant expiration, String jti) {
            Map<String, Object> claims = validClaims();
            claims.put("exp", expiration.getEpochSecond());
            claims.put("jti", jti);
            return signed(claims, "logout+jwt");
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
        @DisplayName("A new jti is refused as REPLAY_GUARD_FULL when the guard is at capacity")
        void shouldRefuseWhenGuardFull() {
            LogoutTokenValidator tight = validator(new LogoutTokenReplayGuard(1, CLOCK));
            Map<String, Object> second = validClaims();
            second.put("jti", "jti-2");

            assertTrue(tight.validate(signed(validClaims(), "logout+jwt")).isAccepted());
            assertRefused(LogoutTokenCheck.REPLAY_GUARD_FULL, tight.validate(signed(second, "logout+jwt")));
        }

        @Test
        @DisplayName("A token refused by an earlier check takes no guard capacity")
        void shouldNotConsumeCapacityOnRefusal() {
            LogoutTokenValidator tight = validator(new LogoutTokenReplayGuard(1, CLOCK));
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
            LogoutTokenReplayGuard guard = new LogoutTokenReplayGuard(1, CLOCK);
            var zero = LogoutTokenValidator.builder().tokenValidator(tokenValidator).expectedIssuer(ISSUER)
                    .expectedAudience(AUDIENCE).freshnessWindow(Duration.ZERO).replayGuard(guard).clock(CLOCK);
            var negative = LogoutTokenValidator.builder().tokenValidator(tokenValidator).expectedIssuer(ISSUER)
                    .expectedAudience(AUDIENCE).freshnessWindow(Duration.ofSeconds(-1)).replayGuard(guard)
                    .clock(CLOCK);

            assertThrows(IllegalArgumentException.class, zero::build);
            assertThrows(IllegalArgumentException.class, negative::build);
        }

        @Test
        @DisplayName("A blank issuer or audience is rejected")
        void shouldRejectBlankIdentifiers() {
            LogoutTokenReplayGuard guard = new LogoutTokenReplayGuard(1, CLOCK);
            var blankIssuer = LogoutTokenValidator.builder().tokenValidator(tokenValidator).expectedIssuer(" ")
                    .expectedAudience(AUDIENCE).freshnessWindow(WINDOW).replayGuard(guard).clock(CLOCK);
            var blankAudience = LogoutTokenValidator.builder().tokenValidator(tokenValidator).expectedIssuer(ISSUER)
                    .expectedAudience("").freshnessWindow(WINDOW).replayGuard(guard).clock(CLOCK);

            assertThrows(IllegalArgumentException.class, blankIssuer::build);
            assertThrows(IllegalArgumentException.class, blankAudience::build);
        }

        @Test
        @DisplayName("A missing collaborator is rejected")
        void shouldRejectNullCollaborator() {
            var noClock = LogoutTokenValidator.builder().tokenValidator(tokenValidator).expectedIssuer(ISSUER)
                    .expectedAudience(AUDIENCE).freshnessWindow(WINDOW)
                    .replayGuard(new LogoutTokenReplayGuard(1, CLOCK));
            var noValidator = LogoutTokenValidator.builder().expectedIssuer(ISSUER).expectedAudience(AUDIENCE)
                    .freshnessWindow(WINDOW).replayGuard(new LogoutTokenReplayGuard(1, CLOCK)).clock(CLOCK);

            assertThrows(NullPointerException.class, noClock::build);
            assertThrows(NullPointerException.class, noValidator::build);
        }
    }

    // === Fixtures ===

    private LogoutTokenValidator validator(LogoutTokenReplayGuard guard) {
        return LogoutTokenValidator.builder()
                .tokenValidator(tokenValidator)
                .expectedIssuer(ISSUER)
                .expectedAudience(AUDIENCE)
                .freshnessWindow(WINDOW)
                .replayGuard(guard)
                .clock(CLOCK)
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
}
