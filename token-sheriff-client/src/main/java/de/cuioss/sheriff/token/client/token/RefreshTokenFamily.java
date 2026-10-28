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

import de.cuioss.sheriff.token.client.internal.ClientLogMessages;
import de.cuioss.sheriff.token.commons.error.ClientProtocolException;
import de.cuioss.tools.logging.CuiLogger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tracks a single refresh-token rotation family and enforces revoke-on-reuse ({@code CLIENT-17}).
 * <p>
 * Each redemption of a refresh token rotates it to a new value (OAuth 2.0 Security BCP): the
 * superseded token is retired and only the current token may be redeemed next. Presenting a
 * superseded token again is the signature of a stolen-token replay — the family is then
 * <em>revoked</em> and every further redemption fails closed, so an attacker who captured an old
 * refresh token cannot ride it into a valid session.
 * <p>
 * <strong>The family remembers what it superseded.</strong> Besides the current token it keeps a
 * bounded, insertion-ordered history of the tokens it has superseded — by rotation and by
 * {@link #reseededWith(String) reseeding} — so a caller can tell a stale re-store of a superseded token
 * ({@link #isSuperseded(String)}) from a genuinely new grant. The history holds SHA-256 digests, never
 * the raw tokens, so it adds no credential material to the heap. It is capped at a fixed bound of
 * sixteen entries and the oldest digest is dropped first. <em>Accepted residual
 * risk:</em> a token aged out of the history is no longer recognised as superseded by this client;
 * replaying it is then caught only by the authorization server's own reuse detection.
 * <p>
 * Reuse semantics do not depend on the history: in {@link #rotate(String, String)} any presented token
 * that is not the current one is reuse, whether or not it is still remembered.
 * <p>
 * The family is thread-safe: concurrent redemptions of the same current token are serialized so
 * that exactly one wins the rotation and every loser is treated as a reuse of the now-superseded
 * token, revoking the family.
 * <p>
 * Every refusal caused by a revoked family is raised as {@link RefreshTokenFamilyRevokedException}, a
 * {@link ClientProtocolException} subtype, and it always ends the session: see
 * {@link #rotate(String, String)} for what a caller owes it.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics">OAuth 2.0 Security BCP §4.13</a>
 */
public class RefreshTokenFamily {

    private static final CuiLogger LOGGER = new CuiLogger(RefreshTokenFamily.class);

    private static final String REVOKED_MESSAGE = "refresh token family is revoked";

    /**
     * Upper bound on the superseded-token history. Sixteen 32-byte digests per family keep the memory
     * cost bounded across every tracked family while still covering every rotation a realistic stale
     * re-store could lag behind.
     */
    private static final int MAX_SUPERSEDED_HISTORY = 16;

    private final ReentrantLock lock = new ReentrantLock();

    /** SHA-256 digests of the superseded tokens, oldest first; guarded by {@link #lock}. */
    private final Deque<byte[]> supersededDigests;

    private String currentToken;
    private boolean revoked;

    /**
     * @param initialRefreshToken the first refresh token issued for this family; must not be
     *                            {@code null} or blank
     */
    public RefreshTokenFamily(String initialRefreshToken) {
        this(requireNonBlank(initialRefreshToken), new ArrayDeque<>());
    }

    private RefreshTokenFamily(String currentToken, Deque<byte[]> supersededDigests) {
        this.currentToken = currentToken;
        this.supersededDigests = supersededDigests;
    }

    /**
     * Atomically redeems the presented refresh token and advances the family to its rotated
     * successor.
     * <p>
     * The presented token must be the current active token. Presenting a superseded token — or any
     * token that is not current — is treated as reuse: the family is revoked and a
     * {@link RefreshTokenFamilyRevokedException} is thrown. Once revoked, every subsequent call fails
     * closed with the same type.
     * <p>
     * <strong>What a caller owes on that refusal.</strong> It is session-ending, never transient: revoke
     * at the authorization server (RFC 7009, best-effort), clear the stored token bundle and this family,
     * and require re-authentication — which is what
     * {@link de.cuioss.sheriff.token.client.lifecycle.TokenLifecycleManager} does. By the time this
     * method is called the authorization server has already redeemed {@code presentedToken} and issued
     * {@code rotatedToken}, so the refusal carries {@code rotatedToken} as a live revocation target
     * ({@link RefreshTokenFamilyRevokedException#getRotatedRefreshToken()}). A caller that classifies
     * failures through {@link de.cuioss.sheriff.token.client.flow.RefreshFlow#classify(Throwable)} may
     * route this refusal through it: it classifies as
     * {@link de.cuioss.sheriff.token.client.flow.RefreshFailureClassification.Kind#REDEEMED} with that
     * successor, never as the session-preserving pre-redemption kind.
     *
     * @param presentedToken the refresh token being redeemed; must not be {@code null} or blank
     * @param rotatedToken   the successor refresh token the AS issued; must not be {@code null} or
     *                       blank, and must differ from {@code presentedToken}
     * @throws RefreshTokenFamilyRevokedException if the family is already revoked, or reuse is detected
     *                                            (which also revokes the family) — a
     *                                            {@link ClientProtocolException} subtype
     * @throws IllegalArgumentException           if {@code rotatedToken} equals {@code presentedToken}
     */
    public void rotate(String presentedToken, String rotatedToken) {
        requireNonBlank(presentedToken);
        requireNonBlank(rotatedToken);
        if (rotatedToken.equals(presentedToken)) {
            throw new IllegalArgumentException("rotatedToken must differ from the presented token");
        }
        lock.lock();
        try {
            assertRedeemable(presentedToken, rotatedToken);
            recordSuperseded(digest(presentedToken));
            currentToken = rotatedToken;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether {@code token} is this family's current token. The answer is about the family's state
     * only: a revoked family still reports the token it was revoked on as current, so a caller can keep
     * the revoked family — and its fail-closed refusal — when that token is presented again.
     *
     * @param token the refresh token to test; must not be {@code null}
     * @return {@code true} if {@code token} is the current token
     */
    public boolean isCurrent(String token) {
        Objects.requireNonNull(token, "token must not be null");
        lock.lock();
        try {
            return constantTimeEquals(digest(currentToken), digest(token));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether {@code token} is one this family has superseded and still remembers. A token aged out of
     * the bounded history (see the class Javadoc) answers {@code false}.
     *
     * @param token the refresh token to test; must not be {@code null}
     * @return {@code true} if {@code token} is in the superseded-token history
     */
    public boolean isSuperseded(String token) {
        Objects.requireNonNull(token, "token must not be null");
        byte[] candidate = digest(token);
        lock.lock();
        try {
            for (byte[] superseded : supersededDigests) {
                if (constantTimeEquals(superseded, candidate)) {
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the successor family for a new grant: its current token is {@code newToken}, and its
     * superseded history is this family's history followed by this family's current token, bounded to
     * the newest entries the history cap allows (see the class Javadoc). A replay of any token this family knew is
     * therefore still recognised by the successor.
     * <p>
     * The successor is a fresh, non-revoked instance; this family is left untouched, so a caller still
     * holding it — an in-flight rotation, for example — keeps a consistent view. A revoked family may be
     * reseeded: the new grant starts a clean family that still remembers the revoked one's tokens.
     *
     * @param newToken the refresh token of the new grant; must not be {@code null} or blank, and must be
     *                 neither the current nor a remembered superseded token
     * @return the successor family; never {@code null}
     * @throws IllegalArgumentException if {@code newToken} is blank, or is already known to this family —
     *                                  a known token is never a new grant, so it can never launder a
     *                                  replay into a fresh family
     */
    public RefreshTokenFamily reseededWith(String newToken) {
        requireNonBlank(newToken);
        lock.lock();
        try {
            // Checked under the (reentrant) lock, so a concurrent rotation cannot slip between the
            // known-token check and the snapshot the successor is built from.
            if (isCurrent(newToken) || isSuperseded(newToken)) {
                throw new IllegalArgumentException("a token already known to the family cannot reseed it");
            }
            Deque<byte[]> successorHistory = new ArrayDeque<>(supersededDigests);
            RefreshTokenFamily successor = new RefreshTokenFamily(newToken, successorHistory);
            successor.recordSuperseded(digest(currentToken));
            return successor;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return whether this family has been revoked (by reuse detection)
     */
    public boolean isRevoked() {
        lock.lock();
        try {
            return revoked;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return the current active refresh token
     * @throws RefreshTokenFamilyRevokedException if the family has been revoked — a
     *                                            {@link ClientProtocolException} subtype. Nothing was
     *                                            redeemed on this call, so it carries no successor, but the
     *                                            session is still over: clear the stored token bundle and
     *                                            this family. Routed through
     *                                            {@link de.cuioss.sheriff.token.client.flow.RefreshFlow#classify(Throwable)}
     *                                            it classifies as
     *                                            {@link de.cuioss.sheriff.token.client.flow.RefreshFailureClassification.Kind#CREDENTIAL_REJECTED}
     */
    public String currentToken() {
        lock.lock();
        try {
            if (revoked) {
                throw new RefreshTokenFamilyRevokedException(REVOKED_MESSAGE);
            }
            return currentToken;
        } finally {
            lock.unlock();
        }
    }

    private void assertRedeemable(String presentedToken, String rotatedToken) {
        if (revoked) {
            throw new RefreshTokenFamilyRevokedException(REVOKED_MESSAGE, rotatedToken);
        }
        if (!currentToken.equals(presentedToken)) {
            revoked = true;
            LOGGER.warn(ClientLogMessages.WARN.REFRESH_TOKEN_REUSE);
            throw new RefreshTokenFamilyRevokedException(
                    "refresh token reuse detected; the refresh token family has been revoked", rotatedToken);
        }
    }

    /**
     * Appends a superseded digest and drops the oldest once the history exceeds its bound. The caller
     * holds {@link #lock}, or owns the instance exclusively before publishing it.
     */
    private void recordSuperseded(byte[] supersededDigest) {
        supersededDigests.addLast(supersededDigest);
        while (supersededDigests.size() > MAX_SUPERSEDED_HISTORY) {
            supersededDigests.removeFirst();
        }
    }

    private static boolean constantTimeEquals(byte[] left, byte[] right) {
        return MessageDigest.isEqual(left, right);
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory on every Java platform (java.security.MessageDigest Javadoc).
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String requireNonBlank(String value) {
        Objects.requireNonNull(value, "refresh token must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("refresh token must not be blank");
        }
        return value;
    }
}
