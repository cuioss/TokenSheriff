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
import de.cuioss.sheriff.token.validation.exception.ClaimMappingException;
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
 *   <li>{@link LogoutTokenCheck#EXPIRATION} - {@code exp} is absent, not a number, or not after now; an
 *   {@code exp} exactly now is refused, as RFC 7519 section 4.1.4 requires the current time to be before it</li>
 *   <li>{@link LogoutTokenCheck#EVENTS} - {@code events} is not a JSON object whose
 *   {@value #BACKCHANNEL_LOGOUT_EVENT} member is a JSON object; the claim is read as JSON, never scanned as
 *   text</li>
 *   <li>{@link LogoutTokenCheck#NONCE} - {@code nonce} is present, whatever its value, a JSON {@code null}
 *   included</li>
 *   <li>{@link LogoutTokenCheck#SUBJECT_OR_SESSION} - neither {@code sub} nor {@code sid} is a non-blank string,
 *   or one of them is present as anything else, a JSON {@code null} included</li>
 *   <li>{@link LogoutTokenCheck#JWT_ID} - {@code jti} is absent or not a non-blank string</li>
 *   <li>{@link LogoutTokenCheck#REPLAYED} / {@link LogoutTokenCheck#REPLAY_GUARD_FULL} - the replay guard refused
 *   the {@code jti}</li>
 * </ol>
 * A present {@code iat} or {@code exp} whose value is not a number is already refused when the signature-only
 * entry maps the claims, with a {@link ClaimMappingException}. The refusal keeps the identity of the claim's
 * check, {@link LogoutTokenCheck#ISSUED_AT} or {@link LogoutTokenCheck#EXPIRATION}, but is reached before the
 * other claim checks run; a mapping failure of any other claim is refused as {@link LogoutTokenCheck#SIGNATURE}.
 * That includes a {@code sub} present as a JSON number, boolean, array or object: the signature-only entry maps
 * {@code sub} strictly as a string, so such a token is refused as {@link LogoutTokenCheck#SIGNATURE}, not as
 * {@link LogoutTokenCheck#SUBJECT_OR_SESSION}. A {@code sub} present as a JSON {@code null}, and a {@code sid}
 * of any non-string type, reach the {@link LogoutTokenCheck#SUBJECT_OR_SESSION} check.
 * The replay guard is consulted last, so a token refused by any other check never takes guard capacity. An
 * admitted {@code jti} is retained until {@code iat} plus the freshness window; after that the token is refused
 * by the {@code iat} check anyway. Each validation reads the clock exactly once, judges {@code iat} and
 * {@code exp} against that instant, and hands the same instant to the replay guard, which reads no clock of its
 * own and decides its purge on it. Freshness and retention are therefore judged on one instant: an entry is
 * purged only at an instant at which its token is already refused by the {@code iat} check. Two clock readings
 * could straddle the {@code iat} plus window edge and admit a replay; one reading cannot. The validator builds
 * its replay guard itself, from the capacity it is given; a guard is never shared between validators.
 * <p>
 * The validator does not log and returns no text taken from the token, so the caller decides how a refusal
 * is logged or metered. Receiving the logout request over HTTP and ending the named session stay with the
 * consuming application.
 * <p>
 * This class is thread-safe. Its only mutable state is the replay guard it owns, whose admissions are atomic.
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
     * Orders the clock read, the claim checks and the replay admission of every validation. Without it, thread A
     * could read an instant at which a token is still fresh, thread B could then read a later instant and purge
     * that token's {@code jti} entry, and A would admit the replay afterwards. With it, the instants of
     * concurrent validations are read in the order their admissions are decided, so a later instant can only
     * purge an entry whose token every earlier decision has already settled.
     */
    private final Object decisionLock = new Object();

    /**
     * Creates a validator.
     *
     * @param tokenValidator   the validator whose signature-only entry verifies the token
     * @param expectedIssuer   the issuer the {@code iss} claim must equal
     * @param expectedAudience the audience the {@code aud} claim must contain
     * @param freshnessWindow     how far {@code iat} may lie before or after now, positive
     * @param replayGuardCapacity the maximum number of live {@code jti} entries of the replay guard this validator
     *                            builds, greater than zero; size it above the number of logout tokens the issuer
     *                            can send within twice the freshness window
     * @param clock               the clock read once per validation; that one instant decides {@code iat}
     *                            freshness, {@code exp}, and the purge of the replay guard
     * @throws IllegalArgumentException if {@code freshnessWindow} is zero or negative, {@code replayGuardCapacity}
     *                                  is not greater than zero, or an identifier is blank
     */
    @Builder
    private LogoutTokenValidator(TokenValidator tokenValidator, String expectedIssuer, String expectedAudience,
            Duration freshnessWindow, int replayGuardCapacity, Clock clock) {
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
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.replayGuard = new LogoutTokenReplayGuard(replayGuardCapacity);
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
            // A null token is handed on as the empty string, which the signature-only entry refuses and
            // counts exactly as it counts a null one
            verified = tokenValidator.verifySignature(Objects.requireNonNullElse(rawToken, ""));
        } catch (ClaimMappingException e) {
            return LogoutTokenValidationResult.refused(checkOfUnmappableClaim(e.getClaimName()));
        } catch (TokenValidationException e) {
            return LogoutTokenValidationResult.refused(LogoutTokenCheck.SIGNATURE);
        }
        Map<String, Object> claims = verified.getRawClaims();
        // Clock read, claim checks and admission are one critical section. The clock is read exactly once, and
        // the replay guard decides its purge on that same instant, so freshness and retention cannot be judged
        // on two readings that straddle the iat + window edge. Holding the lock across the three steps orders the
        // instants of concurrent validations with their admissions: a later reading cannot purge an entry
        // before an earlier reading has decided against it. The section does no I/O and runs no signature work,
        // which happened above.
        synchronized (decisionLock) {
            Instant now = clock.instant();
            Optional<LogoutTokenCheck> failedCheck = firstFailedCheck(verified, claims, now);
            if (failedCheck.isPresent()) {
                return LogoutTokenValidationResult.refused(failedCheck.get());
            }
            // Every claim read below passed its check above: iat is a number, jti a non-blank string, and
            // sub and sid are each absent or a non-blank string
            Instant retainUntil = numericDate(claims.get("iat")).orElseThrow().plus(freshnessWindow);
            String jwtId = (String) claims.get("jti");
            return switch (replayGuard.admit(jwtId, retainUntil, now)) {
                case ADMITTED -> LogoutTokenValidationResult.accepted(expectedIssuer,
                        (String) claims.get("sub"), (String) claims.get("sid"), jwtId);
                case REPLAYED -> LogoutTokenValidationResult.refused(LogoutTokenCheck.REPLAYED);
                case FULL -> LogoutTokenValidationResult.refused(LogoutTokenCheck.REPLAY_GUARD_FULL);
            };
        }
    }

    /**
     * Runs the claim checks from {@link LogoutTokenCheck#TYPE} to {@link LogoutTokenCheck#JWT_ID} in the
     * documented order.
     *
     * @param verified the signature-verified token
     * @param claims   its native claims
     * @param now      the instant {@code iat} and {@code exp} are judged against
     * @return the first check that fails, empty when every check passes
     */
    private Optional<LogoutTokenCheck> firstFailedCheck(SignatureVerifiedToken verified, Map<String, Object> claims,
            Instant now) {
        if (!isLogoutType(verified.getTypHeader().orElse(null))) {
            return Optional.of(LogoutTokenCheck.TYPE);
        }
        if (!expectedIssuer.equals(claims.get("iss"))) {
            return Optional.of(LogoutTokenCheck.ISSUER);
        }
        if (!containsAudience(claims.get("aud"))) {
            return Optional.of(LogoutTokenCheck.AUDIENCE);
        }
        if (!isFresh(claims.get("iat"), now)) {
            return Optional.of(LogoutTokenCheck.ISSUED_AT);
        }
        if (!isUnexpired(claims.get("exp"), now)) {
            return Optional.of(LogoutTokenCheck.EXPIRATION);
        }
        if (!hasLogoutEvent(claims.get("events"))) {
            return Optional.of(LogoutTokenCheck.EVENTS);
        }
        if (isPresent(verified, "nonce")) {
            return Optional.of(LogoutTokenCheck.NONCE);
        }
        if (!hasSubjectOrSession(verified)) {
            return Optional.of(LogoutTokenCheck.SUBJECT_OR_SESSION);
        }
        if (!(claims.get("jti") instanceof String jwtId) || jwtId.isBlank()) {
            return Optional.of(LogoutTokenCheck.JWT_ID);
        }
        return Optional.empty();
    }

    /**
     * Attributes a claim the signature-only entry could not map to the check that owns the claim.
     *
     * @param claimName the name of the claim whose value could not be mapped
     * @return {@link LogoutTokenCheck#ISSUED_AT} for {@code iat}, {@link LogoutTokenCheck#EXPIRATION} for
     * {@code exp}, and {@link LogoutTokenCheck#SIGNATURE} for any other claim
     */
    private static LogoutTokenCheck checkOfUnmappableClaim(String claimName) {
        return switch (claimName) {
            case "iat" -> LogoutTokenCheck.ISSUED_AT;
            case "exp" -> LogoutTokenCheck.EXPIRATION;
            default -> LogoutTokenCheck.SIGNATURE;
        };
    }

    private boolean isFresh(@Nullable Object issuedAtClaim, Instant now) {
        return numericDate(issuedAtClaim)
                .filter(issuedAt -> !issuedAt.isBefore(now.minus(freshnessWindow))
                        && !issuedAt.isAfter(now.plus(freshnessWindow)))
                .isPresent();
    }

    private static boolean isUnexpired(@Nullable Object expirationClaim, Instant now) {
        return numericDate(expirationClaim).filter(expiration -> expiration.isAfter(now)).isPresent();
    }

    private static boolean hasLogoutEvent(@Nullable Object eventsClaim) {
        return eventsClaim instanceof Map<?, ?> events && events.get(BACKCHANNEL_LOGOUT_EVENT) instanceof Map;
    }

    /**
     * A claim is present when the payload carries the member, whatever its value: the native claims omit a
     * top-level JSON {@code null} member, which {@link SignatureVerifiedToken#getNullClaimNames()} names.
     */
    private static boolean isPresent(SignatureVerifiedToken verified, String claimName) {
        return verified.getRawClaims().containsKey(claimName) || verified.getNullClaimNames().contains(claimName);
    }

    /**
     * At least one of {@code sub} and {@code sid} is a non-blank string, and neither is present as anything
     * else, a JSON {@code null} included.
     */
    private static boolean hasSubjectOrSession(SignatureVerifiedToken verified) {
        return isAbsentOrNonBlankString(verified, "sub") && isAbsentOrNonBlankString(verified, "sid")
                && (isPresent(verified, "sub") || isPresent(verified, "sid"));
    }

    private static boolean isAbsentOrNonBlankString(SignatureVerifiedToken verified, String claimName) {
        return !isPresent(verified, claimName)
                || verified.getRawClaims().get(claimName) instanceof String text && !text.isBlank();
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
