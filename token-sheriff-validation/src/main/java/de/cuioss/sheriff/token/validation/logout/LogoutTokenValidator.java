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
import de.cuioss.sheriff.token.validation.domain.token.SignatureVerifiedToken;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Validates a logout token of OpenID Connect Back-Channel Logout 1.0.
 * <p>
 * {@link #validate(String)} verifies the signature through {@link TokenValidator#verifySignature(String)}
 * and then checks the native JSON claims of the token in a fixed order, returning the first check that
 * fails as a {@link LogoutTokenCheck}:
 * <ol>
 *   <li>{@link LogoutTokenCheck#SIGNATURE} - the signature-only entry refused the token</li>
 *   <li>{@link LogoutTokenCheck#TYPE} - a present {@code typ} header is neither {@code logout+jwt} nor
 *   {@code application/logout+jwt}, compared case-insensitively; an absent {@code typ} is accepted</li>
 *   <li>{@link LogoutTokenCheck#ISSUER} - {@code iss} is not the expected issuer</li>
 *   <li>{@link LogoutTokenCheck#AUDIENCE} - {@code aud}, a string or an array, lacks the expected audience</li>
 *   <li>{@link LogoutTokenCheck#ISSUED_AT} - {@code iat} is absent, not a number, or more than the freshness
 *   window before or after now; exactly at either edge is accepted</li>
 *   <li>{@link LogoutTokenCheck#EXPIRATION} - {@code exp} is absent, not a number, or before now; exactly now is
 *   accepted</li>
 *   <li>{@link LogoutTokenCheck#EVENTS} - {@code events} is not a JSON object whose
 *   {@value #BACKCHANNEL_LOGOUT_EVENT} member is a JSON object; the claim is read as JSON, never scanned as
 *   text</li>
 *   <li>{@link LogoutTokenCheck#NONCE} - {@code nonce} is present</li>
 *   <li>{@link LogoutTokenCheck#SUBJECT_OR_SESSION} - neither {@code sub} nor {@code sid} is a non-blank string,
 *   or one of them is present as anything else</li>
 *   <li>{@link LogoutTokenCheck#JWT_ID} - {@code jti} is absent or not a non-blank string</li>
 *   <li>{@link LogoutTokenCheck#REPLAYED} / {@link LogoutTokenCheck#REPLAY_GUARD_FULL} - the replay guard refused
 *   the {@code jti}</li>
 * </ol>
 * The replay guard is consulted last, so a token refused by any other check never takes guard capacity. An
 * admitted {@code jti} is retained until {@code iat} plus the freshness window; after that the token is refused
 * by the {@code iat} check anyway.
 * <p>
 * The validator does not log and returns no text taken from the token, so the caller decides how a refusal
 * is logged or metered. Receiving the logout request over HTTP and ending the named session stay with the
 * consuming application.
 * <p>
 * This class is immutable and thread-safe.
 *
 * @since 1.0
 * @see <a href="https://openid.net/specs/openid-connect-backchannel-1_0.html#Validation">Back-Channel Logout 1.0
 * section 2.6</a>
 */
public final class LogoutTokenValidator {

    /**
     * The member of the {@code events} claim that declares a JWT to be a logout token.
     */
    public static final String BACKCHANNEL_LOGOUT_EVENT = "http://schemas.openid.net/event/backchannel-logout";

    private static final String LOGOUT_TYPE = "logout+jwt";
    private static final String LOGOUT_MEDIA_TYPE = "application/" + LOGOUT_TYPE;

    private final TokenValidator tokenValidator;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final Duration freshnessWindow;
    private final LogoutTokenReplayGuard replayGuard;
    private final Clock clock;

    /**
     * Creates a validator.
     *
     * @param tokenValidator   the validator whose signature-only entry verifies the token
     * @param expectedIssuer   the issuer the {@code iss} claim must equal
     * @param expectedAudience the audience the {@code aud} claim must contain
     * @param freshnessWindow  how far {@code iat} may lie before or after now, positive
     * @param replayGuard      the guard that refuses a {@code jti} seen before; it must be built with the same
     *                         time source as {@code clock} (see {@link LogoutTokenReplayGuard#LogoutTokenReplayGuard(int, Clock)})
     * @param clock            the clock that decides {@code iat} freshness and {@code exp}
     * @throws IllegalArgumentException if {@code freshnessWindow} is zero or negative, or an identifier is blank
     */
    @Builder
    private LogoutTokenValidator(TokenValidator tokenValidator, String expectedIssuer, String expectedAudience,
            Duration freshnessWindow, LogoutTokenReplayGuard replayGuard, Clock clock) {
        this.tokenValidator = Objects.requireNonNull(tokenValidator, "tokenValidator must not be null");
        this.expectedIssuer = Objects.requireNonNull(expectedIssuer, "expectedIssuer must not be null");
        this.expectedAudience = Objects.requireNonNull(expectedAudience, "expectedAudience must not be null");
        if (expectedIssuer.isBlank() || expectedAudience.isBlank()) {
            throw new IllegalArgumentException("expectedIssuer and expectedAudience must not be blank");
        }
        this.freshnessWindow = Objects.requireNonNull(freshnessWindow, "freshnessWindow must not be null");
        if (freshnessWindow.isZero() || freshnessWindow.isNegative()) {
            throw new IllegalArgumentException("freshnessWindow must be positive, but was: " + freshnessWindow);
        }
        this.replayGuard = Objects.requireNonNull(replayGuard, "replayGuard must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Validates one raw logout token.
     *
     * @param rawToken the logout token as received, may be {@code null} or blank (refused as
     *                 {@link LogoutTokenCheck#SIGNATURE})
     * @return the accepted claims, or the one check the token failed; never {@code null}
     */
    public LogoutTokenValidationResult validate(@Nullable String rawToken) {
        SignatureVerifiedToken verified;
        try {
            verified = tokenValidator.verifySignature(rawToken);
        } catch (TokenValidationException e) {
            return LogoutTokenValidationResult.refused(LogoutTokenCheck.SIGNATURE);
        }
        if (!isLogoutType(verified.getTypHeader().orElse(null))) {
            return LogoutTokenValidationResult.refused(LogoutTokenCheck.TYPE);
        }
        Map<String, Object> claims = verified.getRawClaims();
        if (!expectedIssuer.equals(claims.get("iss"))) {
            return LogoutTokenValidationResult.refused(LogoutTokenCheck.ISSUER);
        }
        if (!containsAudience(claims.get("aud"))) {
            return LogoutTokenValidationResult.refused(LogoutTokenCheck.AUDIENCE);
        }
        Instant now = clock.instant();
        Optional<Instant> issuedAt = numericDate(claims.get("iat"));
        if (issuedAt.isEmpty() || issuedAt.get().isBefore(now.minus(freshnessWindow))
                || issuedAt.get().isAfter(now.plus(freshnessWindow))) {
            return LogoutTokenValidationResult.refused(LogoutTokenCheck.ISSUED_AT);
        }
        Optional<Instant> expiration = numericDate(claims.get("exp"));
        if (expiration.isEmpty() || expiration.get().isBefore(now)) {
            return LogoutTokenValidationResult.refused(LogoutTokenCheck.EXPIRATION);
        }
        if (!(claims.get("events") instanceof Map<?, ?> events && events.get(BACKCHANNEL_LOGOUT_EVENT) instanceof Map)) {
            return LogoutTokenValidationResult.refused(LogoutTokenCheck.EVENTS);
        }
        if (claims.containsKey("nonce")) {
            return LogoutTokenValidationResult.refused(LogoutTokenCheck.NONCE);
        }
        Object subject = claims.get("sub");
        Object sessionId = claims.get("sid");
        if (!isAbsentOrNonBlankString(subject) || !isAbsentOrNonBlankString(sessionId)
                || (subject == null && sessionId == null)) {
            return LogoutTokenValidationResult.refused(LogoutTokenCheck.SUBJECT_OR_SESSION);
        }
        if (!(claims.get("jti") instanceof String jwtId) || jwtId.isBlank()) {
            return LogoutTokenValidationResult.refused(LogoutTokenCheck.JWT_ID);
        }
        return switch (replayGuard.admit(jwtId, issuedAt.get().plus(freshnessWindow))) {
            case ADMITTED -> LogoutTokenValidationResult.accepted(expectedIssuer, (String) subject,
                    (String) sessionId, jwtId);
            case REPLAYED -> LogoutTokenValidationResult.refused(LogoutTokenCheck.REPLAYED);
            case FULL -> LogoutTokenValidationResult.refused(LogoutTokenCheck.REPLAY_GUARD_FULL);
        };
    }

    private static boolean isLogoutType(@Nullable String typHeader) {
        if (typHeader == null) {
            return true;
        }
        String typ = typHeader.toLowerCase(Locale.ROOT);
        return LOGOUT_TYPE.equals(typ) || LOGOUT_MEDIA_TYPE.equals(typ);
    }

    private boolean containsAudience(@Nullable Object audience) {
        if (audience instanceof String single) {
            return expectedAudience.equals(single);
        }
        return audience instanceof List<?> many && many.contains(expectedAudience);
    }

    private static boolean isAbsentOrNonBlankString(@Nullable Object value) {
        return value == null || value instanceof String text && !text.isBlank();
    }

    /**
     * Reads a JSON number as a NumericDate in seconds since the epoch; a fraction is rounded down.
     *
     * @param value the claim value
     * @return the instant, empty when the value is not a number or not a representable instant
     */
    private static Optional<Instant> numericDate(@Nullable Object value) {
        if (!(value instanceof Number number)) {
            return Optional.empty();
        }
        try {
            long seconds = new BigDecimal(number.toString()).setScale(0, RoundingMode.FLOOR).longValueExact();
            return Optional.of(Instant.ofEpochSecond(seconds));
        } catch (NumberFormatException | ArithmeticException | DateTimeException e) {
            return Optional.empty();
        }
    }
}
