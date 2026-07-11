/*
 * Copyright © 2022 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.token.client.lifecycle;

import de.cuioss.sheriff.token.client.dpop.ConstraintBinding;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The server-side token bundle held for a single session ({@code CLIENT-18} / {@code CLIENT-19}).
 * <p>
 * A stored token keeps the retrieved token material off the browser: the access token, the optional
 * refresh token and ID token, the optional sender-constraint {@link ConstraintBinding} (from the
 * DPoP/mTLS increment), and the access-token expiry. Carrying the {@code ConstraintBinding} here is
 * what lets the sender-constraint survive storage and proactive refresh.
 * <p>
 * <strong>Refresh is proof-driven, not metadata-only.</strong> {@link #refreshed} does <em>not</em>
 * blindly copy the stored binding forward — that would let a sender-constrained session silently
 * degrade to a plain bearer token if the authorization server returned one on refresh. Instead the
 * caller passes the {@code cnf} binding actually confirmed on the <em>refreshed</em> token, and a
 * previously sender-constrained bundle refuses the refresh unless that binding still confirms the same
 * key ({@code CLIENT-18}). A downgrade to bearer, or a re-binding to a different key, fails closed.
 * <p>
 * The value object is immutable; a refresh produces a new {@code StoredToken} rather than mutating
 * shared state, so concurrent flows over the same session never observe a half-updated bundle.
 *
 * @param accessToken       the raw access token; never {@code null} or blank
 * @param refreshToken      the raw refresh token, or {@code null} when none was issued
 * @param idToken           the raw OpenID Connect ID token, or {@code null} when none was issued
 * @param constraintBinding the sender-constraint binding, or {@code null} for a bearer token
 * @param expiresAt         the access-token expiry instant, or {@code null} when unknown
 * @since 1.0
 * @author Oliver Wolff
 */
public record StoredToken(String accessToken, @Nullable
        String refreshToken, @Nullable
        String idToken,
@Nullable
ConstraintBinding constraintBinding, @Nullable
        Instant expiresAt) {

    /**
     * @param accessToken       the raw access token; must not be {@code null} or blank
     * @param refreshToken      the raw refresh token, or {@code null}
     * @param idToken           the raw ID token, or {@code null}
     * @param constraintBinding the sender-constraint binding, or {@code null}
     * @param expiresAt         the access-token expiry, or {@code null}
     */
    public StoredToken {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        if (accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
    }

    /**
     * @return the sender-constraint binding, if the token is sender-constrained
     */
    public Optional<ConstraintBinding> binding() {
        return Optional.ofNullable(constraintBinding);
    }

    /**
     * @param now the reference instant
     * @return whether the access token has expired at {@code now} (never expired when the expiry is
     *         unknown)
     */
    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    /**
     * Produces the successor bundle after a refresh, carrying the ID token forward and recording the
     * sender-constraint the <em>refreshed</em> token was actually issued under.
     * <p>
     * When this bundle was sender-constrained, the refresh is refused unless {@code refreshedBinding}
     * still confirms the same key: a {@code null} {@code refreshedBinding} (the AS issued a plain
     * bearer token) or a binding to a different key is a downgrade and fails closed with an
     * {@link IllegalStateException}, rather than the previous behaviour of copying the stale binding
     * onto a token that no longer carries it. When this bundle was an unconstrained bearer token, the
     * successor simply records whatever binding the refreshed token carries.
     *
     * @param newAccessToken   the refreshed access token; must not be {@code null} or blank
     * @param newRefreshToken  the refreshed refresh token, or {@code null} to keep the current one
     * @param newExpiresAt     the refreshed access-token expiry, or {@code null} when unknown
     * @param refreshedBinding the {@code cnf} sender-constraint confirmed on the refreshed token, or
     *                         {@code null} when the refreshed token is a plain bearer token
     * @return the refreshed bundle recording the verified binding and carrying the ID token forward
     * @throws IllegalStateException if this bundle was sender-constrained but {@code refreshedBinding}
     *         no longer confirms the same key (a downgrade or re-binding)
     */
    public StoredToken refreshed(String newAccessToken, @Nullable String newRefreshToken,
            @Nullable Instant newExpiresAt, @Nullable ConstraintBinding refreshedBinding) {
        if (constraintBinding != null && !constraintBinding.equals(refreshedBinding)) {
            throw new IllegalStateException(
                    "refreshed token is no longer bound to the stored sender-constraint key; "
                            + "refusing to preserve a stale cnf binding on a downgraded token");
        }
        return new StoredToken(newAccessToken,
                newRefreshToken != null ? newRefreshToken : refreshToken,
                idToken, refreshedBinding, newExpiresAt);
    }
}
