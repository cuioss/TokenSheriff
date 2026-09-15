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

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * What a refused refresh means for the presented refresh token, as
 * {@link RefreshFlow#classify(Throwable)} decides it.
 * <p>
 * The disposition a caller owes a refusal is not derivable from the exception type it caught, because
 * three structurally different situations all surface as "the refresh failed":
 * <ul>
 *   <li>{@link Kind#PRE_REDEMPTION} — the request never reached the point where the authorization
 *       server processed the grant (a connection failure, a DNS failure, an SSRF-blocked target, a
 *       {@code 5xx}, or a {@code 4xx} the server did not attribute to the credential). The presented
 *       token is untouched and still valid: the session must be left alone. Quarantining here would
 *       destroy a working session over a transient network fault.</li>
 *   <li>{@link Kind#CREDENTIAL_REJECTED} — the authorization server looked at the presented refresh
 *       token and declared it invalid (RFC 6749 §5.2 {@code invalid_grant} on a client-error status).
 *       Nothing was redeemed and nothing was rotated, so there is no successor and nothing to revoke —
 *       but the credential is dead and no retry will revive it, so the session must be cleared.</li>
 *   <li>{@link Kind#REDEEMED} — the authorization server answered a success status, and therefore
 *       consumed the grant, before a client-side check refused the result. The presented token may
 *       have been rotated and burned server-side; the carried {@link RefreshRedemption} says what is
 *       known about that, including the {@link RefreshRedemption#rotationUnknown()} state where it is
 *       not computable at all.</li>
 * </ul>
 * A refresh-token family's reuse or already-revoked refusal
 * ({@link de.cuioss.sheriff.token.client.token.RefreshTokenFamilyRevokedException}) is not a fourth
 * situation. It is mapped onto the two session-ending kinds by what the authorization server has
 * already done: {@link Kind#REDEEMED} when the family refused a rotation the server had already
 * performed, with the successor it issued as the revocation target, and {@link Kind#CREDENTIAL_REJECTED}
 * when the family refused outside any exchange, where nothing was redeemed. It never maps to
 * {@link Kind#PRE_REDEMPTION}.
 * <p>
 * The three are mutually exclusive and exhaustive, which is what lets a caller dispatch on
 * {@link #kind()} in a switch with no default arm: a fourth state added later is a compile error at
 * every dispatch site rather than a silent fall-through to the session-preserving default.
 *
 * @param kind       which of the three situations produced the refusal; never {@code null}
 * @param redemption what the authorization server did to the presented refresh token, carried
 *                   <em>only</em> on {@link Kind#REDEEMED}. It is {@code null} on the other two kinds
 *                   because nothing was redeemed there — an absence that means "no redemption
 *                   happened", never "a redemption happened whose state was lost". The canonical
 *                   constructor rejects both disagreeing pairings, so no instance that contradicts its
 *                   own kind is constructible through any entry point
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-5.2">RFC 6749 §5.2 - Error Response</a>
 */
public record RefreshFailureClassification(Kind kind, @Nullable RefreshRedemption redemption) {

    /**
     * The three mutually exclusive situations a refused refresh can be in, ordered from the least to
     * the most that the authorization server did with the presented credential.
     */
    public enum Kind {

        /**
         * The authorization server never processed the grant, so the presented refresh token is
         * untouched and still usable. The session is left intact.
         */
        PRE_REDEMPTION,

        /**
         * The authorization server refused the presented refresh token as invalid without processing
         * the grant. Nothing was rotated, so there is no successor to revoke, but the credential is
         * dead and the session must be cleared. Also the kind of a revoked refresh-token family's
         * refusal raised outside any exchange, which likewise redeemed nothing.
         */
        CREDENTIAL_REJECTED,

        /**
         * The authorization server processed the grant — and therefore redeemed the presented refresh
         * token — before the refusal was raised. The carried {@link RefreshRedemption} says whether a
         * successor is known. Also the kind of a refresh-token family's reuse or already-revoked
         * refusal of a rotation the server had already performed.
         */
        REDEEMED
    }

    /**
     * Rejects the two pairings the type's own contract declares impossible.
     *
     * @throws NullPointerException     when {@code kind} is {@code null}
     * @throws IllegalArgumentException when {@code kind} is {@link Kind#REDEEMED} and no redemption is
     *                                  supplied — a redeemed classification with no state would read
     *                                  downstream as "nothing is known", which is the pre-redemption
     *                                  disposition and the exact inversion this type prevents
     * @throws IllegalArgumentException when a redemption is supplied on a kind other than
     *                                  {@link Kind#REDEEMED} — no redemption happened there, so the
     *                                  state would be fabricated
     */
    public RefreshFailureClassification {
        Objects.requireNonNull(kind, "kind must not be null");
        if (kind == Kind.REDEEMED && redemption == null) {
            throw new IllegalArgumentException("REDEEMED must carry the redemption it was raised under");
        }
        if (kind != Kind.REDEEMED && redemption != null) {
            throw new IllegalArgumentException(
                    "only REDEEMED carries a redemption; " + kind + " redeemed nothing");
        }
    }

    /**
     * @return the classification for a failure raised before the authorization server processed the
     *         grant, leaving the presented refresh token valid
     */
    public static RefreshFailureClassification preRedemption() {
        return new RefreshFailureClassification(Kind.PRE_REDEMPTION, null);
    }

    /**
     * @return the classification for a refresh token the authorization server declared invalid without
     *         redeeming it
     */
    public static RefreshFailureClassification credentialRejected() {
        return new RefreshFailureClassification(Kind.CREDENTIAL_REJECTED, null);
    }

    /**
     * @param redemption what the authorization server did to the presented refresh token; must not be
     *                   {@code null}
     * @return the classification for a refusal raised after the authorization server redeemed the
     *         presented refresh token
     * @throws NullPointerException when {@code redemption} is {@code null}
     */
    public static RefreshFailureClassification redeemed(RefreshRedemption redemption) {
        Objects.requireNonNull(redemption, "redemption must not be null");
        return new RefreshFailureClassification(Kind.REDEEMED, redemption);
    }
}
