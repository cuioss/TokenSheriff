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
package de.cuioss.sheriff.token.client.token;

import de.cuioss.sheriff.token.commons.error.ClientProtocolException;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Objects;
import java.util.Optional;

/**
 * Signals that a {@link RefreshTokenFamily} refused a request because the family is revoked: either
 * this very call detected reuse of a token that is not the family's current one, which revokes the
 * family, or the family had already been revoked by an earlier reuse.
 * <p>
 * <strong>This is a session-ending signal, never a transient one.</strong> Reuse is the signature of
 * a stolen-token replay (OAuth 2.0 Security BCP §4.14.2), and a revoked family fails closed on every
 * later call. A caller that receives it owes the session a fail-closed end: revoke at the
 * authorization server (RFC 7009, best-effort), then clear both its stored token bundle and the
 * family, and require re-authentication. A caller that composes
 * {@link de.cuioss.sheriff.token.client.flow.RefreshFlow#refresh} with {@link RefreshTokenFamily} and
 * routes the failure through {@link de.cuioss.sheriff.token.client.flow.RefreshFlow#classify(Throwable)}
 * gets a session-ending classification for it, never the session-preserving pre-redemption one.
 * <p>
 * The type records <em>where</em> the refusal was raised, because that decides what the
 * authorization server has already done:
 * <ul>
 *   <li>{@link RefreshTokenFamily#rotate(String, String) rotate} — the caller only ever feeds a
 *       rotation into the family after the authorization server answered and issued the successor,
 *       so by the time the family refuses, the presented token has been redeemed server-side and the
 *       successor is a live credential. {@link #isRaisedOnRotation()} answers {@code true} and
 *       {@link #getRotatedRefreshToken()} names that successor as the revocation target.</li>
 *   <li>{@link RefreshTokenFamily#currentToken() currentToken} — no exchange took place, nothing was
 *       redeemed and no successor exists. {@link #isRaisedOnRotation()} answers {@code false} and
 *       there is no token to revoke.</li>
 * </ul>
 * <p>
 * It extends {@link ClientProtocolException}, so every existing
 * {@code catch (ClientProtocolException)} and every documented {@code @throws ClientProtocolException}
 * contract keeps holding unchanged. It lives beside the family rather than in the flow package so the
 * {@code token} package keeps no dependency on {@code flow}, which already depends on {@code token}.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics">OAuth 2.0 Security BCP</a>
 */
// java:S110 — the inheritance depth is inherited wholesale from the library's existing exception
// taxonomy (ClientProtocolException and its ancestors); this type adds exactly one level to it, exactly
// as RedeemedScopeRefusalException does. The subtype relation is load-bearing for source compatibility:
// it is what lets the family signal get its own type without touching a single existing catch block,
// assertThrows or documented @throws.
@SuppressWarnings("java:S110")
public class RefreshTokenFamilyRevokedException extends ClientProtocolException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Whether the refusal was raised by {@link RefreshTokenFamily#rotate(String, String)}, i.e. after the
     * authorization server had already redeemed the presented token. Deliberately <em>not</em> transient:
     * it carries no credential material, and keeping it is what lets a deserialized instance still read
     * as post-redemption.
     */
    private final boolean raisedOnRotation;

    /**
     * Deliberately {@code transient}, for the reason given on
     * {@code RedeemedValidationRefusalException}: the successor is live credential material and must not
     * ride an exception across a serialization boundary. A deserialized instance therefore still reports
     * {@link #isRaisedOnRotation()} but names no successor.
     */
    @Nullable
    private final transient String rotatedRefreshToken;

    /**
     * Creates the refusal raised by {@link RefreshTokenFamily#rotate(String, String)}.
     *
     * @param message             the caller-safe detail message; must not interpolate token material
     * @param rotatedRefreshToken the successor the authorization server issued for the refused rotation;
     *                            must not be {@code null}
     */
    public RefreshTokenFamilyRevokedException(String message, String rotatedRefreshToken) {
        super(message);
        this.raisedOnRotation = true;
        this.rotatedRefreshToken = Objects.requireNonNull(rotatedRefreshToken,
                "rotatedRefreshToken must not be null");
    }

    /**
     * Creates the refusal raised outside a rotation, by {@link RefreshTokenFamily#currentToken()}.
     *
     * @param message the caller-safe detail message; must not interpolate token material
     */
    public RefreshTokenFamilyRevokedException(String message) {
        super(message);
        this.raisedOnRotation = false;
        this.rotatedRefreshToken = null;
    }

    /**
     * @return {@code true} when the refusal was raised by {@link RefreshTokenFamily#rotate(String, String)},
     *         after the authorization server had already redeemed the presented token; {@code false} when
     *         it was raised by {@link RefreshTokenFamily#currentToken()}, where nothing was redeemed
     */
    public boolean isRaisedOnRotation() {
        return raisedOnRotation;
    }

    /**
     * @return the successor the authorization server issued for the refused rotation — the credential to
     *         revoke — or empty when the refusal was not raised on rotation, or on a deserialized instance
     *         whose successor was deliberately not serialized
     */
    public Optional<String> getRotatedRefreshToken() {
        return Optional.ofNullable(rotatedRefreshToken);
    }
}
