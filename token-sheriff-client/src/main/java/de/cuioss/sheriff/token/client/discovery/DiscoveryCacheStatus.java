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
package de.cuioss.sheriff.token.client.discovery;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable snapshot of a {@link CachingDiscoveryResolver}'s cache, as reported by
 * {@link CachingDiscoveryResolver#status()}.
 * <p>
 * The snapshot is read from memory only; producing it never contacts the authorization server.
 * Following ADR-0014, the optional components are typed {@code @Nullable} and carry {@code null}
 * when absent, while {@link #state()} is required and rejected when {@code null}.
 *
 * @param state            the cache state; never {@code null}
 * @param lastSuccess      the instant of the last successful resolution, or {@code null} when the
 *                         discovery document has never been resolved
 * @param lastFailureCause the fully qualified class name of the exception that failed the most
 *                         recent resolution attempt, or {@code null} when the most recent attempt
 *                         succeeded or none was made; it is a class name only and never carries an
 *                         exception message or a response body
 * @since 1.0
 */
public record DiscoveryCacheStatus(State state, @Nullable Instant lastSuccess, @Nullable String lastFailureCause) {

    /**
     * The state of the discovery cache.
     */
    public enum State {

        /** No resolution has been attempted yet. */
        NOT_ATTEMPTED,

        /** The most recent resolution attempt succeeded. */
        RESOLVED,

        /**
         * The most recent resolution attempt failed; a previously resolved document may still be
         * served within its grace period.
         */
        FAILED
    }

    /**
     * Validates the required component.
     *
     * @throws NullPointerException if {@code state} is {@code null}
     */
    public DiscoveryCacheStatus {
        Objects.requireNonNull(state, "state must not be null");
    }

    /**
     * Creates the status of a cache on which no resolution has been attempted yet.
     *
     * @return a status in {@link State#NOT_ATTEMPTED} with neither a success instant nor a failure cause
     */
    public static DiscoveryCacheStatus notAttempted() {
        return new DiscoveryCacheStatus(State.NOT_ATTEMPTED, null, null);
    }
}
