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

import de.cuioss.sheriff.token.client.dpop.ConstraintBinding;
import de.cuioss.sheriff.token.client.token.RefreshTokenFamilyRevokedException;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the base-token compare-and-set of {@link TokenLifecycleManager#applyRefresh}: every apply names the
 * refresh token its result was derived from, and a result whose base token the store no longer holds — a
 * newer grant was stored after it was derived — writes nothing, advances no family, and returns the newer
 * bundle unchanged. A matching base still writes, advances the family, and keeps every refusal of the
 * apply (binding, subject, reuse) in force.
 * <p>
 * Whether a family was advanced is observed through the public API: rotating from a token the family
 * superseded is refused as reuse, rotating from its current token is not.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("TokenLifecycleManager.applyRefresh is a compare-and-set against its base refresh token")
class TokenLifecycleManagerApplyRefreshTest {

    private TokenLifecycleManager manager;
    private String session;

    @BeforeEach
    void setUp() {
        manager = new TokenLifecycleManager(new InMemoryTokenStore(), new RefreshScheduler());
        session = token();
    }

    @Nested
    @DisplayName("A base token the store no longer holds")
    class StaleBase {

        @Test
        @DisplayName("Should return the newer grant unchanged and keep it stored when a late result derived from an older grant is applied")
        void shouldKeepNewerGrantOnLateExternalResult() {
            String grantA = token();
            String successorOfA = token();
            StoredToken grantB = bundle(token(), null);
            manager.store(session, bundle(grantA, null));
            manager.store(session, grantB);

            Optional<StoredToken> applied = manager.applyRefresh(session, grantA, token(), successorOfA, null, null,
                    null);

            assertAll("the late result derived from grant A does not overwrite grant B",
                    () -> assertEquals(Optional.of(grantB), applied, "the newer bundle is returned unchanged"),
                    () -> assertEquals(Optional.of(grantB), manager.get(session), "the newer bundle stays stored"));
        }

        @Test
        @DisplayName("Should not advance the newer grant's family when a late result derived from an older grant is applied")
        void shouldNotAdvanceNewerFamilyOnLateExternalResult() {
            String grantA = token();
            String grantB = token();
            String successorOfB = token();
            manager.store(session, bundle(grantA, null));
            manager.store(session, bundle(grantB, null));
            manager.applyRefresh(session, grantA, token(), token(), null, null, null);

            Optional<StoredToken> advanced = manager.applyRefresh(session, grantB, token(), successorOfB, null, null,
                    null);

            assertEquals(Optional.of(successorOfB), advanced.map(StoredToken::refreshToken),
                    "grant B is still the family's current token, so rotating from it is not reuse");
        }

        @Test
        @DisplayName("Should honour the base in the overload that carries a refreshed ID token")
        void shouldHonourBaseWithIdToken() {
            String grantA = token();
            StoredToken grantB = bundle(token(), token());
            manager.store(session, bundle(grantA, token()));
            manager.store(session, grantB);

            Optional<StoredToken> applied = manager.applyRefresh(session, grantA, token(), token(), null, null, null,
                    token());

            assertAll("the late result and its ID token are discarded",
                    () -> assertEquals(Optional.of(grantB), applied),
                    () -> assertEquals(Optional.of(grantB), manager.get(session)));
        }

        @Test
        @DisplayName("Should compare the base before the binding check, so a stale result is discarded, not refused")
        void shouldCompareBaseBeforeBindingCheck() {
            String grantA = token();
            StoredToken boundGrantB = new StoredToken(token(), token(), null,
                    ConstraintBinding.dpop(Generators.letterStrings(30, 43).next()), null, null);
            manager.store(session, bundle(grantA, null));
            manager.store(session, boundGrantB);

            Optional<StoredToken> applied = manager.applyRefresh(session, grantA, token(), token(), null, null, null);

            assertEquals(Optional.of(boundGrantB), applied,
                    "a result derived from grant A is not checked against grant B's binding at all");
        }
    }

    @Nested
    @DisplayName("A base token the store still holds")
    class MatchingBase {

        @Test
        @DisplayName("Should write the refreshed bundle and advance the family")
        void shouldWriteAndAdvanceFamily() {
            String grantA = token();
            String successorOfA = token();
            String accessToken = token();
            manager.store(session, bundle(grantA, null));

            Optional<StoredToken> applied = manager.applyRefresh(session, grantA, accessToken, successorOfA, null,
                    null, null);
            manager.store(session, bundle(grantA, null));
            String replayAccessToken = token();
            String replaySuccessor = token();

            assertAll("the matching apply writes and advances",
                    () -> assertEquals(Optional.of(successorOfA), applied.map(StoredToken::refreshToken)),
                    () -> assertEquals(Optional.of(accessToken), applied.map(StoredToken::accessToken)),
                    () -> assertThrows(RefreshTokenFamilyRevokedException.class,
                            () -> manager.applyRefresh(session, grantA, replayAccessToken, replaySuccessor, null,
                                    null, null),
                            "grant A was superseded by the advance, so rotating from it again is reuse"),
                    () -> assertTrue(manager.get(session).isEmpty(), "the reuse refusal clears the session fail-closed"));
        }

        @Test
        @DisplayName("Should write the refreshed ID token through the overload that carries it")
        void shouldWriteIdTokenWhenBaseMatches() {
            String grantA = token();
            String refreshedIdToken = token();
            manager.store(session, bundle(grantA, token()));

            Optional<StoredToken> applied = manager.applyRefresh(session, grantA, token(), token(), null, null, null,
                    refreshedIdToken);

            assertEquals(Optional.of(refreshedIdToken), applied.map(StoredToken::idToken));
        }

        @Test
        @DisplayName("Should match a null base against a bundle that holds no refresh token")
        void shouldMatchNullBaseWithoutRefreshToken() {
            String accessToken = token();
            manager.store(session, bundle(null, null));

            Optional<StoredToken> applied = manager.applyRefresh(session, null, accessToken, null, null, null, null);

            assertEquals(Optional.of(accessToken), applied.map(StoredToken::accessToken));
        }

        @Test
        @DisplayName("Should still refuse a refresh that drops the stored sender-constraint")
        void shouldRefuseBindingDowngrade() {
            String grantA = token();
            StoredToken bound = new StoredToken(token(), grantA, null,
                    ConstraintBinding.mtls(Generators.letterStrings(30, 43).next()), null, null);
            manager.store(session, bound);
            String accessToken = token();
            String successor = token();

            assertThrows(IllegalStateException.class,
                    () -> manager.applyRefresh(session, grantA, accessToken, successor, null, null, null));
            assertEquals(Optional.of(bound), manager.get(session), "the refused apply writes nothing");
        }

        @Test
        @DisplayName("Should still refuse a refresh naming a different subject")
        void shouldRefuseSubjectChange() {
            String grantA = token();
            StoredToken bound = new StoredToken(token(), grantA, null, null, null, "alice");
            manager.store(session, bound);
            String accessToken = token();
            String successor = token();

            assertThrows(IllegalStateException.class,
                    () -> manager.applyRefresh(session, grantA, accessToken, successor, null, null, "mallory"));
            assertEquals(Optional.of(bound), manager.get(session), "the refused apply writes nothing");
        }
    }

    private static StoredToken bundle(String refreshToken, String idToken) {
        return new StoredToken(token(), refreshToken, idToken, null, null, null);
    }

    private static String token() {
        return Generators.letterStrings(20, 40).next();
    }
}
