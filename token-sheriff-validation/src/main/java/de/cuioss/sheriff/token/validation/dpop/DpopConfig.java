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
package de.cuioss.sheriff.token.validation.dpop;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Configuration for DPoP (Demonstrating Proof of Possession) validation per RFC 9449.
 * <p>
 * This class is immutable after construction and thread-safe.
 * A {@code DpopConfig} instance is only created when DPoP validation is enabled
 * for a given issuer.
 * </p>
 * <p>
 * Configuration options:
 * <ul>
 *   <li>{@code required} - When {@code true}, tokens without {@code cnf.jkt} are rejected.
 *       When {@code false} (default), tokens without {@code cnf.jkt} pass normally (bearer mode).</li>
 *   <li>{@code proofMaxAgeSeconds} - Maximum age of a DPoP proof JWT's {@code iat} claim (default: 300s).</li>
 *   <li>{@code nonceCacheSize} - Soft cap on the number of jti entries for replay protection
 *       (default: 10000). It is not a maximum: a jti still inside its TTL window is never evicted,
 *       so the cache can exceed the cap by at most the number of jtis that arrive within one TTL
 *       window (arrival rate x TTL); those entries are reclaimed once their window has closed.</li>
 *   <li>{@code nonceCacheTtlSeconds} - TTL for jti replay entries (default: 360s). A value at or
 *       below zero, or above 86400, is rejected by {@code build()} with an
 *       {@link IllegalArgumentException}. A positive value below {@code proofMaxAgeSeconds + 60} is
 *       raised to that floor. The floor adds the fixed {@link #DEFAULT_CLOCK_SKEW_SECONDS} (60), not
 *       the issuer's own clock skew, so a jti stays cached for as long as its proof can remain
 *       acceptable only for an issuer whose clock skew is at most 60 seconds, and then only to
 *       whole-second granularity: a proof's age is counted in whole seconds while the entry expires at
 *       millisecond resolution, so at the full 60-second skew a jti first accepted at the earliest
 *       instant its proof is acceptable can lapse up to just under one second before that proof stops
 *       being acceptable.</li>
 * </ul>
 *
 * @since 1.0
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9449">RFC 9449 - OAuth 2.0 Demonstrating Proof of Possession (DPoP)</a>
 */
@Getter
@EqualsAndHashCode
@ToString
public class DpopConfig {

    /** Default maximum age in seconds for DPoP proof {@code iat} claim. */
    public static final long DEFAULT_PROOF_MAX_AGE_SECONDS = 300;

    /** Default soft cap on the number of jti entries in the replay cache. */
    public static final int DEFAULT_NONCE_CACHE_SIZE = 10_000;

    /**
     * Default TTL in seconds for jti replay cache entries. Set to
     * {@code DEFAULT_PROOF_MAX_AGE_SECONDS + DEFAULT_CLOCK_SKEW_SECONDS} so the default configuration
     * already satisfies the replay-window invariant (M3) without derivation, for an issuer clock skew of
     * at most {@code DEFAULT_CLOCK_SKEW_SECONDS} and up to the sub-second whole-second edge described on
     * the class.
     */
    public static final long DEFAULT_NONCE_CACHE_TTL_SECONDS = 360;

    /**
     * Clock-skew allowance in seconds used to size the replay window (M3). Mirrors the default DPoP
     * proof {@code iat} freshness tolerance ({@code IssuerConfig.DEFAULT_CLOCK_SKEW_SECONDS}): a proof
     * is acceptable while its age lies in {@code [-clockSkew, proofMaxAge]}, so its jti must stay
     * cached for at least {@code proofMaxAge + clockSkew} to prevent replay across that window, up to the
     * sub-second whole-second edge at its end described on the class.
     */
    public static final long DEFAULT_CLOCK_SKEW_SECONDS = 60;

    /**
     * Whether DPoP validation is required for this issuer.
     * <p>
     * When {@code true}, tokens without a {@code cnf.jkt} claim are rejected.
     * When {@code false} (default), tokens without {@code cnf.jkt} pass normally (bearer mode),
     * but tokens that do contain {@code cnf.jkt} must present a valid DPoP proof.
     * </p>
     */
    boolean required;

    /**
     * Maximum acceptable age in seconds for a DPoP proof JWT's {@code iat} claim.
     * Proofs older than this value are rejected as stale.
     */
    long proofMaxAgeSeconds;

    /**
     * Soft cap on the number of entries in the jti replay protection cache.
     * <p>
     * This is not a hard maximum. A jti still inside its TTL window is never evicted, because
     * forgetting it early would permit a replay, so the cache can exceed this value by at most the
     * number of jtis that arrive within one TTL window. Those entries are reclaimed by the periodic
     * expiry sweep or by an over-cap insert once their window has closed.
     * </p>
     */
    int nonceCacheSize;

    /**
     * Time-to-live in seconds for jti entries in the replay cache.
     * After this period, a previously seen jti can be reused.
     */
    long nonceCacheTtlSeconds;

    private DpopConfig(boolean required, long proofMaxAgeSeconds,
            int nonceCacheSize, long nonceCacheTtlSeconds) {
        this.required = required;
        this.proofMaxAgeSeconds = proofMaxAgeSeconds;
        this.nonceCacheSize = nonceCacheSize;
        this.nonceCacheTtlSeconds = nonceCacheTtlSeconds;
    }

    /**
     * Creates a new builder for DpopConfig.
     *
     * @return a new DpopConfigBuilder instance
     */
    public static DpopConfigBuilder builder() {
        return new DpopConfigBuilder();
    }

    /**
     * Builder for {@link DpopConfig} with sensible defaults.
     */
    public static class DpopConfigBuilder {
        private boolean required = false;
        private long proofMaxAgeSeconds = DEFAULT_PROOF_MAX_AGE_SECONDS;
        private int nonceCacheSize = DEFAULT_NONCE_CACHE_SIZE;
        private long nonceCacheTtlSeconds = DEFAULT_NONCE_CACHE_TTL_SECONDS;

        /**
         * Sets whether DPoP validation is required.
         *
         * @param required {@code true} to require DPoP for all tokens
         * @return this builder instance
         */
        public DpopConfigBuilder required(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Sets the maximum age for DPoP proof {@code iat} claims.
         *
         * @param proofMaxAgeSeconds maximum age in seconds
         * @return this builder instance
         */
        public DpopConfigBuilder proofMaxAgeSeconds(long proofMaxAgeSeconds) {
            this.proofMaxAgeSeconds = proofMaxAgeSeconds;
            return this;
        }

        /**
         * Sets the soft cap of the jti replay cache.
         *
         * @param nonceCacheSize soft cap on the number of entries; in-window entries are never evicted
         * @return this builder instance
         */
        public DpopConfigBuilder nonceCacheSize(int nonceCacheSize) {
            this.nonceCacheSize = nonceCacheSize;
            return this;
        }

        /**
         * Sets the TTL for jti replay cache entries.
         *
         * @param nonceCacheTtlSeconds TTL in seconds
         * @return this builder instance
         */
        public DpopConfigBuilder nonceCacheTtlSeconds(long nonceCacheTtlSeconds) {
            this.nonceCacheTtlSeconds = nonceCacheTtlSeconds;
            return this;
        }

        /**
         * Builds the DpopConfig instance.
         *
         * @return a validated DpopConfig
         * @throws IllegalArgumentException if configuration values are invalid
         */
        public DpopConfig build() {
            if (proofMaxAgeSeconds <= 0) {
                throw new IllegalArgumentException("proofMaxAgeSeconds must be positive, got: " + proofMaxAgeSeconds);
            }
            if (proofMaxAgeSeconds > 3600) {
                throw new IllegalArgumentException("proofMaxAgeSeconds must not exceed 3600 (1 hour), got: " + proofMaxAgeSeconds);
            }
            if (nonceCacheSize <= 0) {
                throw new IllegalArgumentException("nonceCacheSize must be positive, got: " + nonceCacheSize);
            }
            if (nonceCacheTtlSeconds <= 0) {
                throw new IllegalArgumentException("nonceCacheTtlSeconds must be positive, got: " + nonceCacheTtlSeconds);
            }
            if (nonceCacheTtlSeconds > 86400) {
                throw new IllegalArgumentException("nonceCacheTtlSeconds must not exceed 86400 (24 hours), got: " + nonceCacheTtlSeconds);
            }
            // Enforce the replay-window invariant (M3): the jti replay cache must live at least as long
            // as a proof can remain acceptable — the freshness window is proofMaxAge + clock skew. If the
            // configured TTL is shorter, a jti could expire while the same proof is still fresh, opening a
            // replay window. Derive the effective TTL up to that minimum so the window is closed for an
            // issuer clock skew of at most DEFAULT_CLOCK_SKEW_SECONDS, to within a residual of less than
            // one second at its end: a proof's age counts whole seconds, the cache entry expires in
            // milliseconds.
            long minReplayTtlSeconds = proofMaxAgeSeconds + DEFAULT_CLOCK_SKEW_SECONDS;
            long effectiveNonceCacheTtlSeconds = Math.max(nonceCacheTtlSeconds, minReplayTtlSeconds);
            return new DpopConfig(required, proofMaxAgeSeconds, nonceCacheSize, effectiveNonceCacheTtlSeconds);
        }
    }
}
