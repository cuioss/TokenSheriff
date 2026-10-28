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

import de.cuioss.sheriff.token.client.token.RefreshTokenFamilyRevokedException;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bounded-growth guard for {@link TokenLifecycleManager#store} family tracking.
 * <p>
 * A session that expires without an explicit logout never reaches
 * {@link TokenLifecycleManager#revokeAndClear}, so its rotation-family entry is not removed on the
 * logout path. The manager therefore backs the family map with a size-capped, access-ordered LRU. These
 * tests build the manager with a small cap through its package-private constructor and observe the bound
 * behaviourally: a session whose family is still tracked refuses a replayed superseded refresh token as
 * reuse, while a session whose family was evicted re-creates it from the held token and accepts the
 * replay — the accepted residual risk the class documents.
 */
@EnableTestLogger
@DisplayName("TokenLifecycleManager bounds the refresh-token family map")
class TokenLifecycleManagerFamilyEvictionTest {

    private static final int SMALL_CAP = 2;

    @Test
    @DisplayName("Should evict the least-recently-used family once the cap is exceeded")
    void shouldBoundFamilyMapGrowth() {
        var manager = new TokenLifecycleManager(new InMemoryTokenStore(), new RefreshScheduler(), SMALL_CAP);
        seedRotatedSession(manager, "session-0");
        seedRotatedSession(manager, "session-1");
        // Re-storing session-0's current bundle touches its family, so session-1 becomes the eldest.
        manager.store("session-0", bundle("session-0", 1));
        seedRotatedSession(manager, "session-2");

        assertAll("only the least-recently-used family was evicted",
                () -> assertThrows(RefreshTokenFamilyRevokedException.class,
                        () -> replaySuperseded(manager, "session-0"),
                        "the recently-used family is retained, so the replay is reuse"),
                () -> assertEquals(Optional.of("refresh-session-1-replayed"),
                        replaySuperseded(manager, "session-1").map(StoredToken::refreshToken),
                        "the eldest family was evicted, so the replay is not recognised"),
                () -> assertThrows(RefreshTokenFamilyRevokedException.class,
                        () -> replaySuperseded(manager, "session-2"),
                        "the most-recently-stored family is retained"));
    }

    /**
     * Pins the accepted post-eviction residual risk documented on {@link TokenLifecycleManager}: once a
     * session's family has been evicted, a replay of a refresh token superseded before the eviction
     * re-creates the family from the held token and raises no client-side reuse. Only the authorization
     * server's own reuse detection remains for that session.
     */
    @Test
    @DisplayName("Should accept a superseded-token replay after eviction as the documented residual risk")
    void shouldAcceptReplayAfterEvictionAsResidualRisk() {
        var manager = new TokenLifecycleManager(new InMemoryTokenStore(), new RefreshScheduler(), SMALL_CAP);
        seedRotatedSession(manager, "victim");
        for (int i = 0; i < SMALL_CAP; i++) {
            seedRotatedSession(manager, "other-" + i);
        }

        Optional<StoredToken> replayed = replaySuperseded(manager, "victim");

        assertAll("the evicted family is re-created and no reuse is raised",
                () -> assertTrue(replayed.isPresent(), "the session survives the replay"),
                () -> assertEquals("refresh-victim-replayed", replayed.orElseThrow().refreshToken()));
    }

    @Test
    @DisplayName("Should detect the same replay as reuse while the family is still tracked")
    void shouldDetectReplayWhileFamilyTracked() {
        var manager = new TokenLifecycleManager(new InMemoryTokenStore(), new RefreshScheduler(), SMALL_CAP);
        seedRotatedSession(manager, "victim");

        assertThrows(RefreshTokenFamilyRevokedException.class, () -> replaySuperseded(manager, "victim"));
        assertTrue(manager.get("victim").isEmpty(), "the reuse refusal clears the session fail-closed");
    }

    @Test
    @DisplayName("Should reject a non-positive family cap")
    void shouldRejectNonPositiveCap() {
        var store = new InMemoryTokenStore();
        var scheduler = new RefreshScheduler();

        assertThrows(IllegalArgumentException.class, () -> new TokenLifecycleManager(store, scheduler, 0));
    }

    /** Stores generation 0 of the session's refresh token and rotates it to generation 1. */
    private static void seedRotatedSession(TokenLifecycleManager manager, String sessionId) {
        manager.store(sessionId, bundle(sessionId, 0));
        manager.applyRefresh(sessionId, "access-" + sessionId + "-1", refreshToken(sessionId, 1), null, null, null);
    }

    /**
     * Re-stores the superseded generation-0 bundle and applies a refresh from it, the shape of a stolen
     * superseded token being redeemed.
     */
    private static Optional<StoredToken> replaySuperseded(TokenLifecycleManager manager, String sessionId) {
        manager.store(sessionId, bundle(sessionId, 0));
        return manager.applyRefresh(sessionId, "access-" + sessionId + "-replayed",
                "refresh-" + sessionId + "-replayed", null, null, null);
    }

    private static StoredToken bundle(String sessionId, int generation) {
        return new StoredToken("access-" + sessionId + "-" + generation, refreshToken(sessionId, generation),
                null, null, null, null);
    }

    private static String refreshToken(String sessionId, int generation) {
        return "refresh-" + sessionId + "-" + generation;
    }
}
