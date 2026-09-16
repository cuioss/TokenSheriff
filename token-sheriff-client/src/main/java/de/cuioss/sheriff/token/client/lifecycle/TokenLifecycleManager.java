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
package de.cuioss.sheriff.token.client.lifecycle;

import de.cuioss.sheriff.token.client.auth.ClientAuthentication;
import de.cuioss.sheriff.token.client.discovery.ProviderMetadata;
import de.cuioss.sheriff.token.client.dpop.ConstraintBinding;
import de.cuioss.sheriff.token.client.flow.RefreshFailureClassification;
import de.cuioss.sheriff.token.client.flow.RefreshFlow;
import de.cuioss.sheriff.token.client.flow.RefreshRedemption;
import de.cuioss.sheriff.token.client.internal.ClientLogMessages;
import de.cuioss.sheriff.token.client.token.IdTokenValidationBridge;
import de.cuioss.sheriff.token.client.token.RefreshTokenFamily;
import de.cuioss.sheriff.token.client.token.RotationResult;
import de.cuioss.sheriff.token.commons.error.ClientProtocolException;
import de.cuioss.sheriff.token.commons.error.TokenSheriffException;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import de.cuioss.sheriff.token.validation.domain.token.IdTokenContent;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Orchestrates the server-side token lifecycle over a {@link TokenStore}: store, retrieve, proactive
 * refresh, and revoke-and-clear on logout ({@code CLIENT-17} / {@code CLIENT-18} / {@code CLIENT-19}).
 * <p>
 * Refresh is sender-constraint aware but proof-driven, not metadata-only: {@link #applyRefresh} takes
 * the {@code cnf} binding actually confirmed on the refreshed token and verifies it against the stored
 * {@link StoredToken#binding() ConstraintBinding} (via {@link StoredToken#refreshed}), so a
 * proactively-refreshed token that came back as a plain bearer — or bound to a different key — is
 * rejected as a downgrade rather than silently keeping the stale binding ({@code CLIENT-18}).
 * <p>
 * <strong>Refresh is identity-bound as well as constraint-bound.</strong> Alongside that downgrade
 * refusal, {@link #applyRefresh} carries the principal the refreshed access token names and
 * {@link StoredToken#refreshed} refuses the refresh when a session already bound to a subject is handed
 * a different one — so a refresh rotates credentials but never re-points a live session at another
 * user. Both checks run inside the same atomic transform, so neither opens a check-then-act window.
 * When the transform refuses a redemption the authorization server already <em>rotated</em>,
 * {@link #doRefresh} fails the session closed: the presented token has been burned at the AS, so
 * there is nothing left to keep using. The AS-issued successor is revoked (RFC 7009), and the store
 * entry and the session's {@link de.cuioss.sheriff.token.client.token.RefreshTokenFamily} are cleared,
 * forcing re-authentication. Restoring the presented token instead would leave the client holding a
 * credential the AS has already invalidated, turning a local refusal into a remote failure on the very
 * next refresh. A refusal on a <em>non-rotated</em> redemption needs none of this: the presented token
 * is still valid at the AS and the family was never advanced, so the untouched store entry stays usable.
 * <p>
 * <strong>The same fail-closed rule covers refusals raised inside the exchange itself.</strong>
 * {@link RefreshFlow#refresh} can refuse after the authorization server has already answered — the
 * freshly issued access token fails client-side validation, the granted scope is broader than
 * requested under the opt-in strict posture, or the successful response cannot be parsed at all — and
 * every one of those reaches {@link #refresh} before any {@link RotationResult} exists, so rotation
 * cannot be read off a result. The discriminator is instead
 * {@link RefreshFlow#classify(Throwable)}, which sorts every refusal into one of three dispositions
 * this class dispatches on with no default arm:
 * <ul>
 *   <li>{@link RefreshFailureClassification.Kind#REDEEMED} — the server consumed the grant. The
 *       carried {@link de.cuioss.sheriff.token.client.flow.RefreshRedemption} says whether the
 *       presented token was burned; when it was, the session is quarantined and the known successor
 *       revoked. An unparseable success response is the one redeemed case where rotation is not
 *       computable at all; it is presumed rotated and cleared, without inventing a successor to
 *       revoke.</li>
 *   <li>{@link RefreshFailureClassification.Kind#CREDENTIAL_REJECTED} — the server declared the
 *       presented refresh token invalid (RFC 6749 §5.2 {@code invalid_grant}) without redeeming it.
 *       The session is cleared, but no revocation is attempted: there is no successor, and the
 *       presented token is exactly what the server just refused.</li>
 *   <li>{@link RefreshFailureClassification.Kind#PRE_REDEMPTION} — the request never reached
 *       redemption (a connection failure, a DNS failure, a {@code 5xx}), so the presented token is
 *       still valid and the session is deliberately left intact rather than destroyed over a transient
 *       fault.</li>
 * </ul>
 * <p>
 * <strong>Logout is fail-closed with no stale-read window.</strong> {@link #revokeAndClear} performs a
 * single atomic take-and-clear via {@link TokenStore#remove(String)}: after it returns, the session's
 * tokens are already gone from the store, so any concurrent {@link #get} sees nothing and a stale
 * token can no longer be used through the store. The returned bundle is handed back so the relying
 * party revokes it at the authorization server (RFC 7009, via
 * {@link RevocationClient}) — the store clear (client-side fail-closed) and the AS revocation
 * (server-side invalidation) together defend {@code T-LOGOUT} / {@code T-REFRESH-THEFT}.
 * <p>
 * <strong>Refresh is reuse-detecting, single-flight, and ID-token-consistent.</strong>
 * {@link #refresh} drives the whole rotation for a session: it presents the stored refresh token
 * through {@link RefreshFlow}, routes the rotation through the session's
 * {@link RefreshTokenFamily}, and on a detected reuse of a superseded token revokes the family at the
 * authorization server (RFC 7009) and clears the store fail-closed ({@code CLIENT-5}). A
 * per-session single-flight collapses a concurrent proactive refresh onto one in-flight rotation, so
 * a benign race never redeems the same token twice and self-classifies as reuse. A refreshed ID token
 * is verified for OIDC Core §12.2 {@code iss}/{@code sub} consistency against the refreshed access
 * token and carried forward, rather than silently preserving the pre-refresh ID token.
 * <p>
 * The session&rarr;family mapping is held in-memory (matching the default {@link InMemoryTokenStore}
 * posture); a relying party that runs a persistent, shared {@link TokenStore} across instances would
 * pair it with a persistent family store — that SPI is not yet provided.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7009">RFC 7009 - OAuth 2.0 Token Revocation</a>
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#RefreshTokenResponse">OIDC Core §12.2</a>
 */
public class TokenLifecycleManager {

    private static final CuiLogger LOGGER = new CuiLogger(TokenLifecycleManager.class);

    private static final String REFRESH_TOKEN_TYPE_HINT = "refresh_token";

    /**
     * Upper bound on the number of tracked rotation families. A session that expires without an
     * explicit logout (e.g. {@link TokenStore} eviction or timeout) never reaches
     * {@link #revokeAndClear}, so its family entry is not removed on the logout path; the bounded
     * LRU below evicts the least-recently-used family once this cap is exceeded, so {@link #families}
     * cannot grow without limit. The cap is deliberately generous (well above any realistic
     * concurrent-session count for an in-memory posture) so eviction only ever reclaims families
     * whose sessions are long dead.
     */
    private static final int MAX_TRACKED_FAMILIES = 10_000;

    private final TokenStore tokenStore;
    private final RefreshScheduler refreshScheduler;

    /**
     * Per-session refresh-token rotation families. Seeded on {@link #store} when a refresh token is
     * present and removed on {@link #revokeAndClear}, so a superseded-token replay against a
     * still-live session is detected as reuse.
     * <p>
     * Backed by an access-ordered, size-capped LRU (a {@link LinkedHashMap} in {@code accessOrder}
     * mode wrapped for thread-safety) rather than an unbounded map: a session that expires without an
     * explicit logout never removes its family via {@link #revokeAndClear}, so an unbounded map would
     * leak those entries forever. Capping at {@link #MAX_TRACKED_FAMILIES} evicts the least-recently
     * used family — one whose session has been idle longest — without touching the reuse-detection or
     * single-flight semantics, which continue to key off the per-session entry while it is live.
     */
    private final Map<String, RefreshTokenFamily> families = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Serial
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<String, RefreshTokenFamily> eldest) {
                    return size() > MAX_TRACKED_FAMILIES;
                }
            });

    /**
     * Per-session in-flight rotations. The first caller to start a refresh for a session installs its
     * future here; a concurrent caller shares that single result instead of redeeming the same token
     * again (the {@code H6} single-flight spanning redeem + apply).
     */
    private final ConcurrentMap<String, CompletableFuture<Optional<StoredToken>>> inFlight =
            new ConcurrentHashMap<>();

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
     * <p>
     * When the bundle carries a refresh token and no rotation family yet tracks the session, a family
     * is seeded with that token so a later replay of a superseded token is detected as reuse. An
     * existing family is left intact — re-storing an <em>older</em> bundle for a live session does not
     * roll the family back, so the stale refresh token is still recognised as superseded.
     *
     * @param sessionId the opaque session identifier; must not be {@code null} or blank
     * @param token     the token bundle; must not be {@code null}
     */
    public void store(String sessionId, StoredToken token) {
        tokenStore.store(sessionId, token);
        String refreshToken = token.refreshToken();
        if (refreshToken != null && !refreshToken.isBlank()) {
            families.computeIfAbsent(sessionId, key -> new RefreshTokenFamily(refreshToken));
        }
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
     * Applies refreshed token material to a stored session, carrying the ID token forward and
     * verifying the refreshed token's sender-constraint and principal against the stored ones
     * ({@code CLIENT-18}). Does nothing and returns empty when no bundle is held. When the stored
     * bundle was sender-constrained but {@code refreshedBinding} no longer confirms the same key, or
     * when the stored bundle is bound to a subject that {@code refreshedSubject} does not match, the
     * transform throws {@link IllegalStateException} rather than writing a mismatched bundle.
     *
     * @param sessionId        the opaque session identifier; must not be {@code null} or blank
     * @param newAccessToken   the refreshed access token; must not be {@code null} or blank
     * @param newRefreshToken  the refreshed refresh token, or {@code null} to keep the current one
     * @param newExpiresAt     the refreshed access-token expiry, or {@code null} when unknown
     * @param refreshedBinding the {@code cnf} binding confirmed on the refreshed token, or {@code null}
     *                         when the refreshed token is a plain bearer token
     * @param refreshedSubject the principal the refreshed access token names, or {@code null} when it
     *                         carries no subject
     * @return the updated stored bundle, or {@link Optional#empty()} when no bundle was held
     */
    public Optional<StoredToken> applyRefresh(String sessionId, String newAccessToken,
            @Nullable String newRefreshToken, @Nullable Instant newExpiresAt,
            @Nullable ConstraintBinding refreshedBinding, @Nullable String refreshedSubject) {
        return applyRefresh(sessionId, newAccessToken, newRefreshToken, newExpiresAt, refreshedBinding,
                refreshedSubject, null);
    }

    /**
     * Applies refreshed token material to a stored session, carrying the supplied refreshed ID token
     * (OIDC Core §12.2) and verifying the refreshed token's sender-constraint and principal against the
     * stored ones ({@code CLIENT-18}). Does nothing and returns empty when no bundle is held.
     *
     * @param sessionId        the opaque session identifier; must not be {@code null} or blank
     * @param newAccessToken   the refreshed access token; must not be {@code null} or blank
     * @param newRefreshToken  the refreshed refresh token, or {@code null} to keep the current one
     * @param newExpiresAt     the refreshed access-token expiry, or {@code null} when unknown
     * @param refreshedBinding the {@code cnf} binding confirmed on the refreshed token, or {@code null}
     *                         when the refreshed token is a plain bearer token
     * @param refreshedSubject the principal the refreshed access token names, or {@code null} when it
     *                         carries no subject
     * @param newIdToken       the refreshed, consistency-checked ID token, or {@code null} to keep the
     *                         current one when the AS omitted it on the refresh
     * @return the updated stored bundle, or {@link Optional#empty()} when no bundle was held
     */
    public Optional<StoredToken> applyRefresh(String sessionId, String newAccessToken,
            @Nullable String newRefreshToken, @Nullable Instant newExpiresAt,
            @Nullable ConstraintBinding refreshedBinding, @Nullable String refreshedSubject,
            @Nullable String newIdToken) {
        // Atomic retrieve-transform-store: a concurrent revokeAndClear (logout) can no longer slip
        // between the read and the write and resurrect a just-revoked session, so a refresh applied
        // after logout is a no-op rather than a stale-token write (CLIENT-22). The subject binding is
        // checked inside the same transform for the same reason: comparing here, before the update,
        // would reopen the check-then-act window the atomic update exists to close.
        return tokenStore.update(sessionId,
                current -> current.refreshed(newAccessToken, newRefreshToken, newExpiresAt, refreshedBinding,
                        refreshedSubject, newIdToken));
    }

    /**
     * Drives a full, reuse-detecting, single-flight refresh for a session ({@code CLIENT-5} /
     * {@code CLIENT-22}, OIDC Core §12.2).
     * <p>
     * The stored refresh token is presented through {@code refreshFlow}; when the AS rotates it, the
     * rotation is routed through the session's {@link RefreshTokenFamily}. A presented token the
     * family knows to be superseded is a reuse: the family is revoked at the authorization server via
     * {@code revocationClient} (RFC 7009), the store is cleared fail-closed, and the reuse is
     * re-thrown. A concurrent proactive refresh for the same session shares the single in-flight
     * rotation rather than redeeming the token twice, so a benign race never self-classifies as reuse.
     * A refreshed ID token is verified for §12.2 {@code iss}/{@code sub} consistency against the
     * refreshed access token before it is carried forward.
     * <p>
     * This coordinator refreshes plain-bearer sessions; it applies the refreshed material with no
     * {@code cnf} binding, so a sender-constrained stored bundle fails closed (a downgrade rather than
     * a silent bearer refresh) — sender-constrained refresh is applied through
     * {@link #applyRefresh(String, String, String, Instant, ConstraintBinding, String, String)} with
     * the confirmed binding.
     * <p>
     * The refreshed principal is read from the validated refreshed access token and handed to the
     * transform, so a session already bound to a subject refuses a refresh naming a different one.
     *
     * @param sessionId               the opaque session identifier; must not be {@code null} or blank
     * @param metadata                the resolved provider metadata (token + revocation endpoints);
     *                                must not be {@code null}
     * @param refreshFlow             the refresh-token exchange; must not be {@code null}
     * @param revocationClient        the RFC 7009 revocation client used on detected reuse; must not be
     *                                {@code null}
     * @param idTokenValidationBridge the ID-token validation bridge for the §12.2 consistency check;
     *                                must not be {@code null}
     * @param clientAuthentication    the client authentication to present on revocation; must not be
     *                                {@code null}
     * @return the refreshed stored bundle, or {@link Optional#empty()} when no refreshable bundle is
     *         held
     * @throws de.cuioss.sheriff.token.commons.error.ClientProtocolException if refresh-token reuse is
     *                               detected (the family is revoked and the store cleared first), or
     *                               if the granted scope is broader than requested under the opt-in
     *                               strict posture
     * @throws de.cuioss.sheriff.token.commons.error.TokenSheriffException if the exchange itself is
     *                               refused — the refreshed access token fails validation, the strict
     *                               scope posture refuses the grant, or the token request fails. When
     *                               the authorization server had already redeemed the presented token
     *                               the session is quarantined first; when it had not, the stored
     *                               bundle is left untouched because that token is still usable
     * @throws IllegalStateException if the refreshed ID token is inconsistent with the refreshed
     *                               access token, or if the refreshed access token names a principal
     *                               other than the one the session is bound to. When the authorization
     *                               server had already rotated the refresh token, the session is
     *                               quarantined first — the rotated token is revoked (RFC 7009) and the
     *                               store entry and rotation family are cleared — so the caller must
     *                               re-authenticate rather than retry
     */
    public Optional<StoredToken> refresh(String sessionId, ProviderMetadata metadata,
            RefreshFlow refreshFlow, RevocationClient revocationClient,
            IdTokenValidationBridge idTokenValidationBridge, ClientAuthentication clientAuthentication) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(refreshFlow, "refreshFlow must not be null");
        Objects.requireNonNull(revocationClient, "revocationClient must not be null");
        Objects.requireNonNull(idTokenValidationBridge, "idTokenValidationBridge must not be null");
        Objects.requireNonNull(clientAuthentication, "clientAuthentication must not be null");

        CompletableFuture<Optional<StoredToken>> mine = new CompletableFuture<>();
        CompletableFuture<Optional<StoredToken>> alreadyInFlight = inFlight.putIfAbsent(sessionId, mine);
        if (alreadyInFlight != null) {
            onSingleFlightJoin(sessionId);
            return awaitInFlight(alreadyInFlight);
        }
        try {
            Optional<StoredToken> refreshed = doRefresh(sessionId, metadata, refreshFlow, revocationClient,
                    idTokenValidationBridge, clientAuthentication);
            mine.complete(refreshed);
            return refreshed;
        } catch (TokenSheriffException | IllegalStateException failure) {
            // Narrowed against the throwers doRefresh declares: TokenSheriffException and
            // IllegalStateException. ClientProtocolException — the third declared thrower — is a
            // subclass of TokenSheriffException and is therefore already covered; naming it as a
            // separate multi-catch alternative would not compile.
            mine.completeExceptionally(failure);
            throw failure;
        } finally {
            // Defensive completion, and the reason the catch below may be narrowed at all: a
            // throwable outside the narrowed set would otherwise leave mine uncompleted while a
            // joining caller is already blocked on it in awaitInFlight, hanging that caller
            // indefinitely. CompletableFuture completion is idempotent, so this is a no-op on the
            // normal path and on the narrowed-catch path — it only fires for an escape the catch
            // does not name.
            mine.completeExceptionally(new IllegalStateException(
                    "refresh terminated without completing the single-flight future"));
            inFlight.remove(sessionId, mine);
        }
    }

    /**
     * Observation point reached by a caller that has just <em>joined</em> an in-flight rotation — its
     * {@code putIfAbsent} returned the leading caller's future — and is about to block on that future.
     * The default implementation does nothing.
     * <p>
     * <strong>This hook is deliberately not dead code, and must not be removed as such.</strong> It
     * exists so the single-flight invariant ("a concurrent caller joins the one rotation rather than
     * redeeming the refresh token a second time") is <em>testable</em> without a timing assumption. The
     * join path touches no collaborator a test can decorate: it reads the map and waits. Every signal
     * available from outside — the leading caller reaching the token endpoint, the leading caller
     * reaching the store — is a proxy that fires strictly <em>before</em> the join and therefore leaves
     * a window in which the leading caller can finish and release its entry, letting the joining caller
     * start a second redemption and turning the test flaky. Announcing the join itself, at the only
     * point where "this caller has joined" is a fact rather than a prediction, closes that window with
     * a real happens-before edge: the joining caller already holds the leading caller's future when the
     * signal fires, so the rotation it joins cannot be missed no matter how the two threads are
     * scheduled afterwards.
     * <p>
     * It carries no production behaviour and no production subclass overrides it. Implementations must
     * not throw and must not block indefinitely — this runs on the joining caller's thread, inside the
     * public {@link #refresh} call.
     *
     * @param sessionId the session whose in-flight rotation was joined; never {@code null}
     */
    protected void onSingleFlightJoin(String sessionId) {
        // No-op by default: see the Javadoc above for why this observation point exists.
    }

    private Optional<StoredToken> doRefresh(String sessionId, ProviderMetadata metadata,
            RefreshFlow refreshFlow, RevocationClient revocationClient,
            IdTokenValidationBridge idTokenValidationBridge, ClientAuthentication clientAuthentication) {
        Optional<StoredToken> held = tokenStore.retrieve(sessionId);
        if (held.isEmpty()) {
            return Optional.empty();
        }
        String presentedRefreshToken = held.get().refreshToken();
        if (presentedRefreshToken == null || presentedRefreshToken.isBlank()) {
            return Optional.empty();
        }

        RotationResult rotation;
        try {
            rotation = refreshFlow.refresh(metadata, presentedRefreshToken);
        } catch (TokenSheriffException refusedExchange) {
            handleRefusedExchange(refusedExchange, sessionId, metadata, revocationClient,
                    clientAuthentication);
            // The rethrow stays at the call site: doRefresh's callers rely on the refusal propagating
            // out of the exchange unchanged, and the helper only decides the session disposition.
            throw refusedExchange;
        }

        // OIDC Core §12.2 consistency is checked BEFORE the family is advanced, so a refusal here can
        // never leave a half-applied rotation behind. What the refusal must do next depends on whether
        // the AS rotated: when it did not, the presented refresh token is still valid, so the refusal
        // propagates with the family and the store left untouched. When it did, the AS has already
        // burned the presented token, so keeping it would leave the session holding a dead credential —
        // the same defect the post-transform quarantine below closes. The session is therefore
        // quarantined fail-closed on the rotated successor. The family has not been advanced at this
        // point, and quarantineRejectedRotation does not assume it was: it revokes the rotated token and
        // clears both the store entry and the family unconditionally.
        String refreshedIdToken;
        try {
            refreshedIdToken = verifiedRefreshedIdToken(rotation, idTokenValidationBridge);
        } catch (IllegalStateException inconsistent) {
            if (rotation.rotated()) {
                quarantineRejectedRotation(sessionId, metadata, rotation.refreshToken(), revocationClient,
                        clientAuthentication);
            }
            throw inconsistent;
        }

        if (rotation.rotated()) {
            RefreshTokenFamily family = families.computeIfAbsent(sessionId,
                    key -> new RefreshTokenFamily(presentedRefreshToken));
            try {
                family.rotate(presentedRefreshToken, rotation.refreshToken());
            } catch (ClientProtocolException reuse) {
                // The catch stays on the supertype: RefreshTokenFamilyRevokedException is a
                // ClientProtocolException, so this keeps holding, and nothing else rotate can raise is
                // one. The successor is read from the rotation this exchange actually produced, not from
                // the refusal, so the revoked credential is one the AS demonstrably issued here.
                revokeReusedFamily(sessionId, metadata, presentedRefreshToken, rotation.refreshToken(),
                        revocationClient, clientAuthentication);
                throw reuse;
            }
        }

        Instant refreshedExpiry = rotation.accessTokenExpiresInSeconds() > 0
                ? Instant.now().plusSeconds(rotation.accessTokenExpiresInSeconds())
                : null;

        // The principal the refreshed access token names, handed to the transform so a session already
        // bound to a subject refuses a refresh that names a different one. It is read from the same
        // validated access token the §12.2 consistency check above uses.
        String refreshedSubject = rotation.accessToken().getSubject().orElse(null);

        // When the store write below is refused — the identity or sender-constraint binding check
        // inside the atomic transform rejects this refresh — the store keeps the pre-refresh token
        // untouched. If the AS rotated, that pre-refresh token has nonetheless been burned server-side,
        // so the session is quarantined fail-closed rather than left holding a dead credential. If the
        // AS did not rotate, the presented token is still valid and the untouched store entry needs no
        // further action.
        try {
            return applyRefresh(sessionId, rotation.accessToken().getRawToken(), rotation.refreshToken(),
                    refreshedExpiry, null, refreshedSubject, refreshedIdToken);
        } catch (IllegalStateException rejected) {
            if (rotation.rotated()) {
                quarantineRejectedRotation(sessionId, metadata, rotation.refreshToken(), revocationClient,
                        clientAuthentication);
            }
            throw rejected;
        }
    }

    /**
     * Decides what a refusal raised <em>inside</em> the token exchange does to the session.
     * <p>
     * A refusal raised INSIDE the exchange reaches the caller before any {@code RotationResult} exists,
     * so rotation cannot be read off a result here. {@link RefreshFlow#classify} is the single place the
     * three situations are told apart, and the {@code default} arm below is a real runtime guard rather
     * than dead defensive code. An earlier revision of this comment claimed the compiler enforced
     * exhaustiveness because the arms use arrow labels; that is wrong. Under JLS SE21 §14.11.1 an enum
     * is a <em>legacy</em> selector type, and a switch STATEMENT is enhanced — and so exhaustiveness-checked
     * — only when it carries a pattern label, a {@code null} label, or a non-legacy selector. Arrow labels
     * govern fall-through between arms, not exhaustiveness. Without the {@code default} arm a fourth
     * {@code Kind} would therefore compile, take no lifecycle action, and fall through to the caller's
     * rethrow — silently leaving the session in whatever state it was already in, which is the failure
     * mode this dispatch exists to close.
     * <p>
     * This method decides the disposition only; it never rethrows. The refusal is rethrown by the caller
     * so the control-flow contract {@code doRefresh}'s callers rely on stays unchanged.
     *
     * @param refusedExchange      the refusal the exchange raised; never {@code null}
     * @param sessionId            the session whose disposition is being decided; never {@code null}
     * @param metadata             the provider metadata used for any revocation call; never {@code null}
     * @param revocationClient     the client used for best-effort revocation; never {@code null}
     * @param clientAuthentication the authentication presented on a revocation call; never {@code null}
     */
    private void handleRefusedExchange(TokenSheriffException refusedExchange, String sessionId,
            ProviderMetadata metadata, RevocationClient revocationClient,
            ClientAuthentication clientAuthentication) {
        RefreshFailureClassification classification = RefreshFlow.classify(refusedExchange);
        switch (classification.kind()) {
            case REDEEMED -> {
                // The server consumed the grant. Only a redemption that BURNED the presented token
                // quarantines: a redeemed-but-not-rotated exchange (RFC 6749 §6 permits omitting a
                // new refresh token) leaves the presented token usable.
                RefreshRedemption redemption = Objects.requireNonNull(classification.redemption(),
                        "a REDEEMED classification carries the redemption it was raised under");
                if (redemption.presentedTokenBurned()) {
                    quarantineRedeemedRefresh(sessionId, metadata, redemption, revocationClient,
                            clientAuthentication);
                }
            }
            // The server never redeemed anything, so there is no successor — but it declared the
            // presented token invalid, so keeping the session would leave the caller holding a dead
            // credential no retry revives.
            case CREDENTIAL_REJECTED -> quarantineRejectedCredential(sessionId, metadata,
                    revocationClient, clientAuthentication);
            // The request never reached the point where the server processed it (connection
            // failure, DNS failure, 5xx), so the presented refresh token is still valid. Clearing
            // the session here would destroy a working one over a transient fault.
            case PRE_REDEMPTION -> LOGGER.debug(
                    "Refresh refused before the authorization server processed the grant; "
                            + "leaving the session intact");
            // Unreachable while Kind has exactly the three constants above, and deliberately kept:
            // this switch is NOT compiler-exhaustive (see the Javadoc), so a fourth Kind would
            // otherwise take no disposition at all. Failing loud beats silently preserving a session
            // whose credential state nothing decided.
            default -> throw new IllegalStateException(
                    "unhandled refresh failure classification: " + classification.kind());
        }
    }

    /**
     * Fails a session closed after the rotation family refused the redemption as a replay.
     * <p>
     * <strong>Both credentials are revoked, in this order.</strong> The replayed token is named first:
     * it is the credential the authorization server's own reuse detection keys on, so on a server that
     * revokes family-wide (OAuth 2.0 Security BCP §4.14.2) naming it is what kills the whole family. The
     * successor is named second because a server that does <em>not</em> revoke family-wide has just
     * issued it as a live credential on the very exchange this client is refusing — revoking only the
     * replayed token would leave that successor valid until it expired, which is the disposition
     * ADR-0005 and ADR-0010 assign to a refusal raised after redemption. Each revocation is independent
     * and best-effort, so a failure of the first does not stop the second.
     */
    private void revokeReusedFamily(String sessionId, ProviderMetadata metadata, String reusedToken,
            @Nullable String rotatedSuccessor, RevocationClient revocationClient,
            ClientAuthentication clientAuthentication) {
        LOGGER.warn(ClientLogMessages.WARN.REFRESH_REUSE_REVOCATION, maskSessionId(sessionId));
        List<String> revocationTargets = rotatedSuccessor == null
                ? List.of(reusedToken)
                : List.of(reusedToken, rotatedSuccessor);
        revokeAndClearFailClosed(sessionId, metadata, revocationTargets, revocationClient,
                clientAuthentication);
    }

    /**
     * Fails a session closed after the authorization server rotated the refresh token but the client
     * then refused the redemption — either because the refreshed ID token is inconsistent with the
     * refreshed access token (OIDC Core §12.2) or because the atomic store transform rejected an
     * identity or sender-constraint binding mismatch.
     * <p>
     * By the time either refusal fires, the AS has already issued {@code rotatedToken} and invalidated
     * the token this client presented, so there is no usable credential left to keep: restoring the
     * presented token would only hand the caller one the AS has burned. The refusal is a security event
     * on a token the AS just minted, so the rotated token is revoked at the AS (RFC 7009, best-effort)
     * and both the store entry and the rotation family are dropped, forcing re-authentication.
     */
    private void quarantineRejectedRotation(String sessionId, ProviderMetadata metadata, String rotatedToken,
            RevocationClient revocationClient, ClientAuthentication clientAuthentication) {
        LOGGER.warn(ClientLogMessages.WARN.REFRESH_IDENTITY_REJECTED_QUARANTINE, maskSessionId(sessionId));
        revokeAndClearFailClosed(sessionId, metadata, revocationTargets(rotatedToken), revocationClient,
                clientAuthentication);
    }

    /**
     * Fails a session closed after the authorization server redeemed the presented refresh token but
     * {@link RefreshFlow#refresh} then refused the exchange — the freshly issued access token failed
     * client-side validation, the granted scope was broader than requested under the opt-in strict
     * posture, or the successful response could not be parsed at all.
     * <p>
     * The disposition follows what is actually known about rotation, and never invents a token to
     * revoke. When the authorization server issued a successor, that successor is revoked (RFC 7009)
     * and the session cleared, exactly as the post-transform quarantine does. When the response was
     * unparseable, no successor exists on this side and rotation is not computable even in principle:
     * the presented token is presumed burned, so the store entry and rotation family are cleared, but
     * no revocation is attempted because there is no token to name in it.
     */
    private void quarantineRedeemedRefresh(String sessionId, ProviderMetadata metadata,
            RefreshRedemption redemption, RevocationClient revocationClient,
            ClientAuthentication clientAuthentication) {
        String rotatedToken = redemption.rotatedRefreshToken();
        if (rotatedToken == null) {
            LOGGER.warn(ClientLogMessages.WARN.REFRESH_REDEMPTION_UNVERIFIABLE_QUARANTINE,
                    maskSessionId(sessionId));
        } else {
            LOGGER.warn(ClientLogMessages.WARN.REFRESH_IDENTITY_REJECTED_QUARANTINE, maskSessionId(sessionId));
        }
        revokeAndClearFailClosed(sessionId, metadata, revocationTargets(rotatedToken), revocationClient,
                clientAuthentication);
    }

    /**
     * Fails a session closed after the authorization server refused the presented refresh token as
     * invalid (RFC 6749 §5.2 {@code invalid_grant} on a client-error status).
     * <p>
     * Nothing was redeemed on this path and nothing was rotated, so <strong>no revocation is
     * attempted</strong>: there is no successor to name in one, and the presented token is precisely
     * the credential the server has just told us it no longer honours. What remains is the client-side
     * fail-closed clear — the store entry and the rotation family are dropped, forcing
     * re-authentication — because a session left holding a credential the server has declared dead is a
     * session that fails on its very next refresh with no way to recover.
     */
    private void quarantineRejectedCredential(String sessionId, ProviderMetadata metadata,
            RevocationClient revocationClient, ClientAuthentication clientAuthentication) {
        LOGGER.warn(ClientLogMessages.WARN.REFRESH_CREDENTIAL_REJECTED_QUARANTINE, maskSessionId(sessionId));
        revokeAndClearFailClosed(sessionId, metadata, List.of(), revocationClient, clientAuthentication);
    }

    /**
     * @return the revocation targets for a single, possibly unknown credential: the token when one is
     *         known, and an empty list when it is {@code null}, which means there is nothing to name in
     *         a revocation
     */
    private static List<String> revocationTargets(@Nullable String token) {
        return token == null ? List.of() : List.of(token);
    }

    /**
     * Revokes every named credential at the authorization server (best-effort, in the given order) and
     * clears the session's store entry and rotation family, so the client-side fail-closed clear happens
     * whether or not the AS revocations succeed. An empty list means no revocable credential is known —
     * the clear still happens, and nothing is attempted.
     * <p>
     * Each revocation is attempted independently: a {@link TransportException} from one does not stop
     * the next, because the targets are separate credentials and skipping the rest would leave a live
     * one behind over an unrelated failure.
     */
    private void revokeAndClearFailClosed(String sessionId, ProviderMetadata metadata,
            List<String> revocationTargets, RevocationClient revocationClient,
            ClientAuthentication clientAuthentication) {
        try {
            if (!revocationTargets.isEmpty()) {
                metadata.getRevocationEndpoint().ifPresent(endpoint -> {
                    for (String token : revocationTargets) {
                        try {
                            revocationClient.revoke(endpoint, token, REFRESH_TOKEN_TYPE_HINT, clientAuthentication);
                        } catch (TransportException revocationFailure) {
                            // Revocation is best-effort: a failed AS revocation must not stop the
                            // client-side fail-closed store clear below, must not stop the remaining
                            // targets, and must not mask the original refusal to the caller.
                            LOGGER.debug(revocationFailure, "RFC 7009 revocation on fail-closed clear failed: %s",
                                    revocationFailure.getMessage());
                        }
                    }
                });
            }
        } finally {
            // The clear is the fail-closed guarantee itself, so it must not be reachable only on the
            // paths the catch above names. RevocationClient is a non-final collaborator whose revoke
            // is overridable: an implementation may throw an unchecked exception that the narrowed
            // TransportException catch does not cover, which would otherwise escape this method with
            // the session and its rotation family still stored — a retained credential the caller was
            // told had been destroyed. Running the clear in a finally keeps the guarantee unconditional
            // while leaving propagation unchanged: TransportException stays caught and best-effort,
            // anything else still reaches the caller, only now after the clear has run.
            tokenStore.remove(sessionId);
            families.remove(sessionId);
        }
    }

    /**
     * Verifies a refreshed ID token against the refreshed access token (OIDC Core §12.2) and returns
     * the ID token to carry forward, or {@code null} when the AS omitted one on the refresh.
     */
    @Nullable
    private static String verifiedRefreshedIdToken(RotationResult rotation,
            IdTokenValidationBridge idTokenValidationBridge) {
        String rawIdToken = rotation.idToken();
        if (rawIdToken == null || rawIdToken.isBlank()) {
            return null;
        }
        IdTokenContent refreshedIdToken = idTokenValidationBridge.validateRefreshedIdToken(rawIdToken);
        if (!consistentWithAccessToken(rotation.accessToken(), refreshedIdToken)) {
            LOGGER.warn(ClientLogMessages.WARN.REFRESHED_ID_TOKEN_INCONSISTENT);
            throw new IllegalStateException(
                    "refreshed ID token is inconsistent with the refreshed access token (OIDC Core §12.2)");
        }
        return rawIdToken;
    }

    private static boolean consistentWithAccessToken(AccessTokenContent accessToken, IdTokenContent idToken) {
        if (!accessToken.getIssuer().equals(idToken.getIssuer())) {
            return false;
        }
        // 'sub' is mandatory on an OIDC ID token but optional on an access token: cross-check it only
        // when the access token actually carries a subject (§12.2 "the access token's sub").
        Optional<String> accessSubject = accessToken.getSubject();
        return accessSubject.isEmpty() || accessSubject.equals(idToken.getSubject());
    }

    private static Optional<StoredToken> awaitInFlight(
            CompletableFuture<Optional<StoredToken>> inFlightRotation) {
        try {
            return inFlightRotation.join();
        } catch (CompletionException completion) {
            if (completion.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw completion;
        }
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
        families.remove(sessionId);
        if (removed.isPresent()) {
            LOGGER.info(ClientLogMessages.INFO.LOGOUT_TOKENS_CLEARED, maskSessionId(sessionId));
        }
        return removed;
    }

    /**
     * Masks a session identifier for logging: session identification values must never appear in
     * logs unmasked, so this logs a stable, non-reversible correlation hash instead of the raw
     * identifier.
     *
     * @param sessionId the raw session identifier; must not be {@code null}
     * @return the first 8 hex characters of the SHA-256 digest of {@code sessionId}
     */
    private static String maskSessionId(String sessionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            return "********";
        }
    }
}
