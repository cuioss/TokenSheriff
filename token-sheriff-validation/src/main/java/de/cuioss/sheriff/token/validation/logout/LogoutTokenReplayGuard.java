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

import de.cuioss.sheriff.token.validation.util.Sha256Util;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/**
 * A bounded, fail-closed store of the {@code jti} values of admitted logout tokens.
 * <p>
 * Each admitted {@code jti} is kept, as its SHA-256 digest only, until the instant the caller names. A
 * {@code jti} still retained is refused as {@link Admission#REPLAYED}. When the guard holds as many live
 * entries as its capacity, a new {@code jti} is refused as {@link Admission#FULL}: a live entry is never
 * evicted to make room, because forgetting it early would let its token be replayed. Entries whose
 * retention has passed are purged on every admission, which is how capacity is regained.
 * <p>
 * The guard reads no clock of its own. Each admission names the instant it is decided at, and the purge is
 * judged against exactly that instant. The guard is owned by one {@link LogoutTokenValidator}, which builds it
 * from the capacity it is configured with and passes, on every admission, the same instant it judged the
 * token's {@code iat} against. That single-instant rule is what keeps purge and freshness consistent: the
 * validator accepts an {@code iat} while now is at most {@code iat} plus its freshness window and retains the
 * {@code jti} until exactly that instant, so an entry is purged only at an instant at which its token is already
 * refused as stale. Two separate clock reads could straddle that edge - the first accepting the token as fresh,
 * the second purging its entry - and admit a replay; one instant cannot. The rule holds across threads too,
 * because the validator reads the clock, runs the claim checks and calls {@code admit} as one critical section:
 * the instants of concurrent validations are read in the order their admissions are decided, so a later
 * instant never purges an entry before an earlier one has been decided against it. No caller can construct a
 * guard or hand one to a validator.
 * <p>
 * This class is thread-safe. An admission is a check-then-act on shared state - purge, look up the digest,
 * test the capacity, insert - so all four steps run as one critical section under a single private monitor:
 * no two threads can both admit the same {@code jti} or both take the last free entry. The guard starts no
 * thread and holds no static state.
 *
 * @since 1.0
 */
final class LogoutTokenReplayGuard {

    /**
     * The outcome of {@link #admit(String, Instant, Instant)}.
     */
    enum Admission {

        /** The {@code jti} was not retained and is now stored. */
        ADMITTED,

        /** The {@code jti} is still retained from an earlier admission; nothing was stored. */
        REPLAYED,

        /** The guard is at capacity with live entries; nothing was stored and nothing was evicted. */
        FULL
    }

    private final int capacity;
    private final Object lock = new Object();

    /** Hex-encoded SHA-256 digest of each admitted {@code jti}, mapped to the instant it is retained until. */
    private final Map<String, Instant> retainedUntil = new HashMap<>();

    /**
     * Creates an empty guard for the validator that owns it.
     *
     * @param capacity the maximum number of live entries, greater than zero
     * @throws IllegalArgumentException if {@code capacity} is not greater than zero
     */
    LogoutTokenReplayGuard(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be greater than 0, but was: " + capacity);
        }
        this.capacity = capacity;
    }

    /**
     * Admits a {@code jti} unless it is still retained or the guard is full.
     * <p>
     * An entry is retained up to and including {@code retainUntil}; it is purged by an admission decided at an
     * instant past it. The guard reads no clock: {@code now} is the instant the caller decided the token's
     * freshness at, so purge and freshness are judged on one and the same instant.
     *
     * @param jti         the {@code jti} of the logout token, stored as its SHA-256 digest only
     * @param retainUntil the instant up to which the {@code jti} is refused as a replay
     * @param now         the instant this admission is decided at, which decides whether a retained entry has
     *                    expired; the same instant the caller judged the token's freshness against
     * @return whether the {@code jti} was admitted, refused as a replay, or refused because the guard is full
     */
    Admission admit(String jti, Instant retainUntil, Instant now) {
        Objects.requireNonNull(jti, "jti must not be null");
        Objects.requireNonNull(retainUntil, "retainUntil must not be null");
        Objects.requireNonNull(now, "now must not be null");
        String digest = digest(jti);
        synchronized (lock) {
            retainedUntil.values().removeIf(until -> until.isBefore(now));
            if (retainedUntil.containsKey(digest)) {
                return Admission.REPLAYED;
            }
            if (retainedUntil.size() >= capacity) {
                return Admission.FULL;
            }
            retainedUntil.put(digest, retainUntil);
            return Admission.ADMITTED;
        }
    }

    private static String digest(String jti) {
        return HexFormat.of().formatHex(Sha256Util.digest(jti.getBytes(StandardCharsets.UTF_8)));
    }
}
