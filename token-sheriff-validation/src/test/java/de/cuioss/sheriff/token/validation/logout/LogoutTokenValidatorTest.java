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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.cuioss.sheriff.token.validation.logout.LogoutTokenValidatorSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link LogoutTokenValidator}: one test per check of OpenID Connect Back-Channel Logout 1.0 section 2.6,
 * each paired with the accepted positive control, and every refusal asserting the identity of the
 * {@link LogoutTokenCheck} that failed. The replay guard, fractional NumericDate handling and the construction
 * constraints are covered by {@link LogoutTokenValidatorReplayTest}.
 */
@DisplayName("LogoutTokenValidator")
class LogoutTokenValidatorTest {

    private TokenValidator tokenValidator;
    private LogoutTokenValidator underTest;

    @BeforeEach
    void setUp() {
        tokenValidator = newTokenValidator();
        underTest = logoutValidator(tokenValidator, 100, CLOCK);
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
}
