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
 * what lets the sender-constraint survive storage and proactive refresh — {@link #refreshed} copies
 * it forward so a refreshed token stays bound to the same key ({@code CLIENT-18}).
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
     * Produces the successor bundle after a refresh, preserving the sender-constraint binding and the
     * ID token so the refreshed token stays bound to the same key ({@code CLIENT-18}).
     *
     * @param newAccessToken  the refreshed access token; must not be {@code null} or blank
     * @param newRefreshToken the refreshed refresh token, or {@code null} to keep the current one
     * @param newExpiresAt    the refreshed access-token expiry, or {@code null} when unknown
     * @return the refreshed bundle carrying forward the binding and ID token
     */
    public StoredToken refreshed(String newAccessToken, @Nullable String newRefreshToken,
            @Nullable Instant newExpiresAt) {
        return new StoredToken(newAccessToken,
                newRefreshToken != null ? newRefreshToken : refreshToken,
                idToken, constraintBinding, newExpiresAt);
    }
}
