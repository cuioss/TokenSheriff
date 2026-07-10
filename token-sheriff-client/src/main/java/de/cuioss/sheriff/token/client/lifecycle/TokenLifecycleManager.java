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

import de.cuioss.sheriff.token.client.internal.ClientLogMessages;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Orchestrates the server-side token lifecycle over a {@link TokenStore}: store, retrieve, proactive
 * refresh, and revoke-and-clear on logout ({@code CLIENT-17} / {@code CLIENT-18} / {@code CLIENT-19}).
 * <p>
 * Refresh preserves the sender-constraint: {@link #applyRefresh} carries the stored
 * {@link StoredToken#binding() ConstraintBinding} forward so a proactively-refreshed token stays
 * bound to the same key ({@code CLIENT-18}).
 * <p>
 * <strong>Logout is fail-closed with no stale-read window.</strong> {@link #revokeAndClear} performs a
 * single atomic take-and-clear via {@link TokenStore#remove(String)}: after it returns, the session's
 * tokens are already gone from the store, so any concurrent {@link #get} sees nothing and a stale
 * token can no longer be used through the store. The returned bundle is handed back so the relying
 * party revokes it at the authorization server (RFC 7009, via
 * {@link RevocationClient}) — the store clear (client-side fail-closed) and the AS revocation
 * (server-side invalidation) together defend {@code T-LOGOUT} / {@code T-REFRESH-THEFT}.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public class TokenLifecycleManager {

    private static final CuiLogger LOGGER = new CuiLogger(TokenLifecycleManager.class);

    private final TokenStore tokenStore;
    private final RefreshScheduler refreshScheduler;

    /**
     * @param tokenStore       the backing token store; must not be {@code null}
     * @param refreshScheduler the proactive-refresh policy; must not be {@code null}
     */
    public TokenLifecycleManager(TokenStore tokenStore, RefreshScheduler refreshScheduler) {
        this.tokenStore = Objects.requireNonNull(tokenStore, "tokenStore must not be null");
        this.refreshScheduler = Objects.requireNonNull(refreshScheduler, "refreshScheduler must not be null");
    }

    /**
     * Stores (or replaces) the token bundle for a session.
     *
     * @param sessionId the opaque session identifier; must not be {@code null} or blank
     * @param token     the token bundle; must not be {@code null}
     */
    public void store(String sessionId, StoredToken token) {
        tokenStore.store(sessionId, token);
    }

    /**
     * @param sessionId the opaque session identifier; must not be {@code null} or blank
     * @return the stored bundle, or {@link Optional#empty()} when none is held
     */
    public Optional<StoredToken> get(String sessionId) {
        return tokenStore.retrieve(sessionId);
    }

    /**
     * @param sessionId the opaque session identifier; must not be {@code null} or blank
     * @param now       the reference instant; must not be {@code null}
     * @return whether the session's access token is inside its proactive-refresh window (false when no
     *         token is held or the expiry is unknown)
     */
    public boolean needsRefresh(String sessionId, Instant now) {
        return get(sessionId).map(token -> refreshScheduler.needsRefresh(token, now)).orElse(false);
    }

    /**
     * Applies refreshed token material to a stored session, preserving the sender-constraint and ID
     * token ({@code CLIENT-18}). Does nothing and returns empty when no bundle is held.
     *
     * @param sessionId       the opaque session identifier; must not be {@code null} or blank
     * @param newAccessToken  the refreshed access token; must not be {@code null} or blank
     * @param newRefreshToken the refreshed refresh token, or {@code null} to keep the current one
     * @param newExpiresAt    the refreshed access-token expiry, or {@code null} when unknown
     * @return the updated stored bundle, or {@link Optional#empty()} when no bundle was held
     */
    public Optional<StoredToken> applyRefresh(String sessionId, String newAccessToken,
            @Nullable String newRefreshToken, @Nullable Instant newExpiresAt) {
        Optional<StoredToken> current = tokenStore.retrieve(sessionId);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        StoredToken refreshed = current.get().refreshed(newAccessToken, newRefreshToken, newExpiresAt);
        tokenStore.store(sessionId, refreshed);
        return Optional.of(refreshed);
    }

    /**
     * Atomically clears the session's held tokens (fail-closed) and returns them so the relying party
     * can revoke them at the authorization server. After this call {@link #get(String)} for the same
     * session returns empty.
     *
     * @param sessionId the opaque session identifier; must not be {@code null} or blank
     * @return the cleared bundle to revoke at the AS, or {@link Optional#empty()} when none was held
     */
    public Optional<StoredToken> revokeAndClear(String sessionId) {
        Optional<StoredToken> removed = tokenStore.remove(sessionId);
        if (removed.isPresent()) {
            LOGGER.info(ClientLogMessages.INFO.LOGOUT_TOKENS_REVOKED, sessionId);
        }
        return removed;
    }
}
