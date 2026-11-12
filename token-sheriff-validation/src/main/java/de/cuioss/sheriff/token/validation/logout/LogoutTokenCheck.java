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

/**
 * The check a logout token failed.
 * <p>
 * A refused {@link LogoutTokenValidationResult} names exactly one of these values, the first check that
 * failed in the order {@link LogoutTokenValidator} runs them. The set is bounded, so a caller can log or
 * meter the value without carrying any text taken from the token.
 *
 * @since 1.0
 */
public enum LogoutTokenCheck {

    /**
     * The signature-only entry refused the token: an empty or oversized token, an unknown or unavailable
     * issuer, a refused header, an invalid signature, or a claim it could not map. The precise reason was
     * already counted as a security event by {@code TokenValidator#verifySignature(String)}.
     * <p>
     * A claim-mapping failure of {@code iat} or {@code exp} is the exception: it is refused as
     * {@link #ISSUED_AT} or {@link #EXPIRATION}. A {@code sub} present as a JSON number, boolean, array or
     * object is refused here, as {@code SIGNATURE}, not as {@link #SUBJECT_OR_SESSION}: the signature-only
     * entry maps {@code sub} strictly as a string and refuses any other JSON type before the logout checks run.
     */
    SIGNATURE,

    /** A {@code typ} header is present and is not {@code logout+jwt} or {@code application/logout+jwt}. */
    TYPE,

    /** {@code iss} is not the expected issuer. */
    ISSUER,

    /** {@code aud} is absent or does not contain the expected audience. */
    AUDIENCE,

    /** {@code iat} is absent, not a number, or outside the freshness window around now. */
    ISSUED_AT,

    /** {@code exp} is absent, not a number, or not after now; an {@code exp} exactly now is refused. */
    EXPIRATION,

    /**
     * {@code events} is absent, not a JSON object, or lacks the back-channel logout member as a JSON
     * object.
     */
    EVENTS,

    /** {@code nonce} is present, a JSON {@code null} included; a logout token must not carry one. */
    NONCE,

    /**
     * Neither {@code sub} nor {@code sid} is present as a non-blank string, or one is present as anything else,
     * a JSON {@code null} included. A {@code sub} present as a non-string JSON value other than {@code null}
     * never reaches this check: the signature-only entry already refuses it, as {@link #SIGNATURE}.
     */
    SUBJECT_OR_SESSION,

    /** {@code jti} is absent or not a non-blank string. */
    JWT_ID,

    /** The {@code jti} was already admitted and is still retained by the replay guard. */
    REPLAYED,

    /**
     * The replay guard holds as many live entries as its capacity allows; the token is refused rather than
     * a live entry evicted.
     */
    REPLAY_GUARD_FULL
}
