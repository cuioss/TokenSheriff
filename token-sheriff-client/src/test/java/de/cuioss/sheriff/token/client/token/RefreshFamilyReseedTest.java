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

import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.ProviderMetadata;
import de.cuioss.sheriff.token.client.flow.RefreshFlow;
import de.cuioss.sheriff.token.client.flow.TokenEndpointClient;
import de.cuioss.sheriff.token.client.internal.ClientLogMessages;
import de.cuioss.sheriff.token.client.lifecycle.InMemoryTokenStore;
import de.cuioss.sheriff.token.client.lifecycle.RefreshScheduler;
import de.cuioss.sheriff.token.client.lifecycle.StoredToken;
import de.cuioss.sheriff.token.client.lifecycle.TokenLifecycleManager;
import de.cuioss.sheriff.token.client.lifecycle.TokenStore;
import de.cuioss.sheriff.token.commons.error.ClientProtocolException;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.test.dispatcher.TokenDispatcher;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wired contract that the rotation family follows every legitimate grant while still revoking a real
 * replay, driven through {@link TokenLifecycleManager#refresh} over the real {@link RefreshFlow} against
 * a mock token endpoint.
 * <p>
 * A re-login reseeds the family, an externally applied refresh advances it, and a refresh racing a
 * re-login yields to the newer grant — none of them raises a false reuse. Each of those is paired with a
 * negative control showing that a token the family superseded is still reuse, so removing the reseed or
 * advance guard turns a test red. A refused refresh — {@code invalid_grant}, an inconsistent refreshed ID
 * token on a rotated exchange, or a detected reuse — fails the session closed, but spares a re-login grant
 * stored while that exchange was in flight; each refusal path is paired with the same path and no re-login,
 * which must still clear the session. The wired path also maps an absurd {@code expires_in} to a bundle
 * with no expiry instead of overflowing.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("Wired refresh: the rotation family follows re-grants without weakening reuse detection")
class RefreshFamilyReseedTest extends RefreshTestSupport {

    /** An RFC 6749 §5.2 error body declaring the presented refresh token invalid. */
    private static final String INVALID_GRANT = "{\"error\":\"invalid_grant\"}";

    private ClientConfiguration config;
    private TokenLifecycleManager manager;
    private RecordingRevocationClient revocationClient;
    private String session;

    @BeforeEach
    void setUp() {
        initRefreshFixture();
        config = config();
        manager = manager();
        revocationClient = new RecordingRevocationClient(config);
        session = Generators.letterStrings(10, 20).next();
    }

    @Test
    @DisplayName("Should refresh a re-login grant with no reuse and no revocation")
    void shouldRefreshAfterReLogin(URIBuilder uriBuilder) {
        ProviderMetadata metadata = metadata(uriBuilder);
        String rt1 = token();
        manager.store(session, bearerBundle(rt1, null));
        refreshTo(metadata, token());
        String reLoginGrant = token();
        String successor = token();

        manager.store(session, bearerBundle(reLoginGrant, null));
        StoredToken refreshed = refreshTo(metadata, successor);

        assertAll("the re-login grant is a new family member, not a replay",
                () -> assertEquals(successor, refreshed.refreshToken()),
                () -> assertFalse(revocationClient.revokedAny(), "a legitimate refresh revokes nothing"),
                () -> assertEquals(Optional.of(successor), manager.get(session).map(StoredToken::refreshToken)));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RefreshTokenFamily.class);
    }

    @Test
    @DisplayName("Should treat the pre-re-login token as reuse after the family was reseeded")
    void shouldDetectPreReLoginReplay(URIBuilder uriBuilder) {
        ProviderMetadata metadata = metadata(uriBuilder);
        String rt1 = token();
        String rt2 = token();
        manager.store(session, bearerBundle(rt1, null));
        refreshTo(metadata, rt2);
        manager.store(session, bearerBundle(token(), null));

        manager.store(session, bearerBundle(rt2, null));
        String replaySuccessor = token();

        assertReuseRevokedAndCleared(metadata, rt2, replaySuccessor);
    }

    @Test
    @DisplayName("Should refresh the token an external applyRefresh advanced the family to")
    void shouldRefreshAfterExternalApplyRefresh(URIBuilder uriBuilder) {
        ProviderMetadata metadata = metadata(uriBuilder);
        String a = token();
        String a2 = token();
        String a3 = token();
        manager.store(session, bearerBundle(a, null));
        manager.applyRefresh(session, token(), a2, null, null, null);

        StoredToken refreshed = refreshTo(metadata, a3);

        assertAll("the externally applied rotation advanced the family",
                () -> assertEquals(a3, refreshed.refreshToken()),
                () -> assertFalse(revocationClient.revokedAny()));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RefreshTokenFamily.class);
    }

    @Test
    @DisplayName("Should treat the token an external applyRefresh superseded as reuse")
    void shouldDetectReplayOfExternallySupersededToken(URIBuilder uriBuilder) {
        ProviderMetadata metadata = metadata(uriBuilder);
        String a = token();
        manager.store(session, bearerBundle(a, null));
        manager.applyRefresh(session, token(), token(), null, null, null);

        manager.store(session, bearerBundle(a, null));

        assertReuseRevokedAndCleared(metadata, a, token());
    }

    @Test
    @DisplayName("Should treat a stale re-store of a superseded token as reuse rather than a new grant")
    void shouldDetectStaleReStore(URIBuilder uriBuilder) {
        ProviderMetadata metadata = metadata(uriBuilder);
        String rt1 = token();
        manager.store(session, bearerBundle(rt1, null));
        refreshTo(metadata, token());

        manager.store(session, bearerBundle(rt1, null));

        assertReuseRevokedAndCleared(metadata, rt1, token());
    }

    @Test
    @DisplayName("Should yield an in-flight refresh to a re-login and revoke the orphaned successor")
    void shouldYieldInFlightRefreshToReLogin(URIBuilder uriBuilder) throws Exception {
        ProviderMetadata metadata = metadata(uriBuilder);
        String rt1 = token();
        String orphanedSuccessor = token();
        String reLoginGrant = token();
        manager.store(session, bearerBundle(rt1, null));
        var exchangeStarted = new CountDownLatch(1);
        var releaseEndpoint = new CountDownLatch(1);
        getModuleDispatcher().blockUntil(releaseEndpoint)
                .respondWith(TokenDispatcher.tokenResponse(accessHolder.getRawToken(), orphanedSuccessor, null, 300));
        RefreshFlow signallingFlow = new RefreshFlow(config, new TokenEndpointClient(config), accessBridge,
                clientAuth(config)) {
            @Override
            public RotationResult refresh(ProviderMetadata providerMetadata, String refreshToken) {
                exchangeStarted.countDown();
                return super.refresh(providerMetadata, refreshToken);
            }
        };
        var clientAuth = clientAuth(config);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        Optional<StoredToken> result;
        try {
            Future<Optional<StoredToken>> inFlight = pool.submit(() -> manager.refresh(session, metadata,
                    signallingFlow, revocationClient, idBridge, clientAuth));
            assertTrue(exchangeStarted.await(10, TimeUnit.SECONDS), "the refresh must reach the exchange");
            manager.store(session, bearerBundle(reLoginGrant, null));
            releaseEndpoint.countDown();
            result = inFlight.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        getModuleDispatcher().blockUntil(null);

        assertAll("the in-flight refresh yields to the newer grant",
                () -> assertEquals(Optional.of(reLoginGrant), result.map(StoredToken::refreshToken),
                        "the newer grant is returned unchanged"),
                () -> assertEquals(Optional.of(reLoginGrant), manager.get(session).map(StoredToken::refreshToken),
                        "the newer grant is still the stored bundle"),
                () -> assertTrue(revocationClient.revoked(orphanedSuccessor),
                        "the successor nothing holds any more is revoked best-effort"),
                () -> assertFalse(revocationClient.revoked(reLoginGrant)));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RefreshTokenFamily.class);

        String nextSuccessor = token();
        assertEquals(nextSuccessor, refreshTo(metadata, nextSuccessor).refreshToken(),
                "the reseeded family still accepts the newer grant's own refresh");
    }

    @Test
    @DisplayName("Should not read a re-login landing between the token read and the family capture as reuse")
    void shouldNotReadReLoginBetweenReadAndCaptureAsReuse(URIBuilder uriBuilder) {
        ProviderMetadata metadata = metadata(uriBuilder);
        var racingStore = new RacingTokenStore();
        var racingManager = new TokenLifecycleManager(racingStore, new RefreshScheduler());
        String rt1 = token();
        String reLoginGrant = token();
        String successor = token();
        racingManager.store(session, bearerBundle(rt1, null));
        racingStore.afterNextRetrieve(() -> racingManager.store(session, bearerBundle(reLoginGrant, null)));
        getModuleDispatcher().respondWith(TokenDispatcher.tokenResponse(accessHolder.getRawToken(), successor,
                null, 300));

        Optional<StoredToken> refreshed = racingManager.refresh(session, metadata, refreshFlow(config),
                revocationClient, idBridge, clientAuth(config));

        assertAll("the refresh re-reads the newer grant instead of presenting the stale token",
                () -> assertEquals(Optional.of(successor), refreshed.map(StoredToken::refreshToken),
                        "the newer grant is refreshed, not read as a replay of the token read before it"),
                () -> assertFalse(revocationClient.revokedAny(), "nothing is revoked"),
                () -> assertEquals(Optional.of(successor), racingManager.get(session).map(StoredToken::refreshToken),
                        "the session survives with the refreshed grant"));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RefreshTokenFamily.class);
    }

    @Test
    @DisplayName("Should adopt a grant that is stored but not yet reseeded instead of reading it as reuse")
    void shouldAdoptGrantStoredBeforeFamilyReseed(URIBuilder uriBuilder) {
        ProviderMetadata metadata = metadata(uriBuilder);
        var racingStore = new RacingTokenStore();
        var racingManager = new TokenLifecycleManager(racingStore, new RefreshScheduler());
        String rt1 = token();
        String grant = token();
        String successor = token();
        racingManager.store(session, bearerBundle(rt1, null));
        // store() writes the token store first and reseeds the family second; this is the state a refresh
        // sees when it reads between the two statements: the new grant held, its family not reseeded yet.
        racingStore.store(session, bearerBundle(grant, null));
        getModuleDispatcher().respondWith(TokenDispatcher.tokenResponse(accessHolder.getRawToken(), successor,
                null, 300));

        Optional<StoredToken> refreshed = racingManager.refresh(session, metadata, refreshFlow(config),
                revocationClient, idBridge, clientAuth(config));

        assertAll("the half-applied grant is adopted as a new grant, not replayed",
                () -> assertEquals(Optional.of(successor), refreshed.map(StoredToken::refreshToken)),
                () -> assertFalse(revocationClient.revokedAny(), "nothing is revoked"),
                () -> assertEquals(Optional.of(successor), racingManager.get(session).map(StoredToken::refreshToken),
                        "the session survives with the refreshed grant"));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RefreshTokenFamily.class);
    }

    @Test
    @DisplayName("Should keep the token slot and the family slot paired when two grants are stored concurrently")
    void shouldPairSlotsUnderConcurrentStore(URIBuilder uriBuilder) throws Exception {
        ProviderMetadata metadata = metadata(uriBuilder);
        var racingStore = new RacingTokenStore();
        var racingManager = new TokenLifecycleManager(racingStore, new RefreshScheduler());
        String firstGrant = token();
        String secondGrant = token();
        String successor = token();
        var secondStoreReturned = new CountDownLatch(1);
        var secondStore = new AtomicReference<Future<?>>();
        var secondStoreInsideGap = new AtomicBoolean();

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // The hook runs on the first store's thread, after its token write and before its family
            // update, and only then starts the second store. This is a timing-bounded guard: the hook
            // waits at most a bounded time for the second store. Without the grant lock the second store
            // normally completes inside the gap and the test turns red; with it, it blocks until the first
            // store is done and the bounded wait simply times out. The bound can only weaken the red
            // check (a second store slower than the bound leaves a broken lock undetected), never make
            // the green check flaky.
            racingStore.afterNextStore(() -> {
                secondStore.set(pool.submit(() -> {
                    racingManager.store(session, bearerBundle(secondGrant, null));
                    secondStoreReturned.countDown();
                }));
                secondStoreInsideGap.set(awaitBounded(secondStoreReturned));
            });
            racingManager.store(session, bearerBundle(firstGrant, null));
            secondStore.get().get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        getModuleDispatcher().respondWith(TokenDispatcher.tokenResponse(accessHolder.getRawToken(), successor,
                null, 300));

        Optional<StoredToken> refreshed = racingManager.refresh(session, metadata, refreshFlow(config),
                revocationClient, idBridge, clientAuth(config));

        assertAll("the surviving grant refreshes with its own family (second store inside the gap: "
                + secondStoreInsideGap.get() + ")",
                () -> assertEquals(Optional.of(successor), refreshed.map(StoredToken::refreshToken),
                        "the stored grant is refreshed, not read as a replay"),
                () -> assertFalse(revocationClient.revokedAny(), "nothing is revoked"),
                () -> assertEquals(Optional.of(successor), racingManager.get(session).map(StoredToken::refreshToken),
                        "the session survives with the refreshed grant"));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RefreshTokenFamily.class);
    }

    @Test
    @DisplayName("Should adopt a stored-but-not-yet-reseeded grant when an external applyRefresh rotates from it")
    void shouldAdoptGrantStoredBeforeFamilyReseedOnExternalApplyRefresh() {
        var racingStore = new RacingTokenStore();
        var racingManager = new TokenLifecycleManager(racingStore, new RefreshScheduler());
        String rt1 = token();
        String grant = token();
        String rotated = token();
        racingManager.store(session, bearerBundle(rt1, null));
        // store() writes the token store first and reseeds the family second; an external applyRefresh
        // running between the two sees the new grant held while its family is not reseeded yet.
        racingStore.store(session, bearerBundle(grant, null));

        Optional<StoredToken> applied = racingManager.applyRefresh(session, token(), rotated, null, null, null);

        assertAll("the half-applied grant is adopted, then rotated, not read as a replay",
                () -> assertEquals(Optional.of(rotated), applied.map(StoredToken::refreshToken)),
                () -> assertEquals(Optional.of(rotated), racingManager.get(session).map(StoredToken::refreshToken),
                        "the session survives with the rotated grant"),
                () -> assertFalse(revocationClient.revokedAny(), "nothing is revoked"));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RefreshTokenFamily.class);
    }

    @Test
    @DisplayName("Should treat a stale superseded token as reuse when an external applyRefresh rotates from it")
    void shouldRefuseExternalApplyRefreshRotatingFromSupersededToken() {
        String rt1 = token();
        String rt2 = token();
        manager.store(session, bearerBundle(rt1, null));
        manager.applyRefresh(session, token(), rt2, null, null, null);
        // A stale re-store puts the superseded rt1 back; the family keeps remembering it as superseded.
        manager.store(session, bearerBundle(rt1, null));

        String replaySuccessor = token();
        String replayAccessToken = token();
        var reuse = assertThrows(RefreshTokenFamilyRevokedException.class,
                () -> manager.applyRefresh(session, replayAccessToken, replaySuccessor, null, null, null));

        assertAll("the adoption path never launders a superseded token into a fresh grant",
                () -> assertEquals(Optional.of(replaySuccessor), reuse.getRotatedRefreshToken(),
                        "the refusal carries the successor as a live revocation target"),
                () -> assertTrue(manager.get(session).isEmpty(), "the session is cleared fail-closed"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Refresh token reuse detected");
    }

    @Test
    @DisplayName("Should keep a grant stored while an external applyRefresh tears the session down on reuse")
    void shouldKeepGrantStoredDuringApplyRefreshReuseTeardown() throws Exception {
        var racingStore = new RacingTokenStore();
        var racingManager = new TokenLifecycleManager(racingStore, new RefreshScheduler());
        String rt1 = token();
        String rt2 = token();
        String newGrant = token();
        String replaySuccessor = token();
        String nextSuccessor = token();
        racingManager.store(session, bearerBundle(rt1, null));
        racingManager.applyRefresh(session, token(), rt2, null, null, null);
        // A stale re-store puts the superseded rt1 back, so the next applyRefresh rotating from it is reuse.
        racingManager.store(session, bearerBundle(rt1, null));
        var concurrentStoreReturned = new CountDownLatch(1);
        var concurrentStore = new AtomicReference<Future<?>>();
        var concurrentStoreInsideTeardown = new AtomicBoolean();

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // The hook runs on the applyRefresh thread after the refused update has returned and before
            // the reuse teardown clears the session, and only then starts a store() of a new grant. This
            // is a timing-bounded guard: without the grant lock around the teardown the store normally
            // completes inside the window and the teardown wipes the new grant; with it, the store blocks
            // until the teardown is done and the bounded wait simply times out. The bound can only weaken
            // the red check, never make the green check flaky.
            racingStore.afterNextUpdate(() -> {
                concurrentStore.set(pool.submit(() -> {
                    racingManager.store(session, bearerBundle(newGrant, null));
                    concurrentStoreReturned.countDown();
                }));
                concurrentStoreInsideTeardown.set(awaitBounded(concurrentStoreReturned));
            });
            String replayAccessToken = token();
            assertThrows(RefreshTokenFamilyRevokedException.class,
                    () -> racingManager.applyRefresh(session, replayAccessToken, replaySuccessor, null, null, null));
            concurrentStore.get().get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(Optional.of(newGrant), racingManager.get(session).map(StoredToken::refreshToken),
                "the grant stored during the teardown survives it (store inside the teardown window: "
                        + concurrentStoreInsideTeardown.get() + ")");
        Optional<StoredToken> advanced = racingManager.applyRefresh(session, token(), nextSuccessor, null, null,
                null);
        assertEquals(Optional.of(nextSuccessor), advanced.map(StoredToken::refreshToken),
                "the surviving grant has its own family, so rotating from it is not reuse");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Refresh token reuse detected");
    }

    @Test
    @DisplayName("Should keep a re-login grant when the server rejects the refused refresh token as invalid_grant")
    void shouldKeepReLoginGrantWhenCredentialIsRejected(URIBuilder uriBuilder) {
        ProviderMetadata metadata = metadata(uriBuilder);
        arrangeCredentialRejection();

        assertReLoginSurvivesRefusal(metadata, TransportException.class);
        assertFalse(revocationClient.revokedAny(), "a rejected credential is never revoked");
    }

    @Test
    @DisplayName("Should clear the session on invalid_grant when no re-login landed during the exchange")
    void shouldClearSessionOnCredentialRejectionWithoutReLogin(URIBuilder uriBuilder) {
        ProviderMetadata metadata = metadata(uriBuilder);
        arrangeCredentialRejection();

        assertRefusalClearsSession(metadata, TransportException.class);
    }

    @Test
    @DisplayName("Should keep a re-login grant when the rotated exchange carries an inconsistent refreshed ID token")
    void shouldKeepReLoginGrantWhenRefreshedIdTokenIsInconsistent(URIBuilder uriBuilder) {
        ProviderMetadata metadata = metadata(uriBuilder);
        String rotated = arrangeInconsistentIdToken();

        assertReLoginSurvivesRefusal(metadata, IllegalStateException.class);
        assertTrue(revocationClient.revoked(rotated), "the AS-issued successor is still revoked (RFC 7009)");
    }

    @Test
    @DisplayName("Should clear the session on an inconsistent refreshed ID token when no re-login landed")
    void shouldClearSessionOnInconsistentIdTokenWithoutReLogin(URIBuilder uriBuilder) {
        ProviderMetadata metadata = metadata(uriBuilder);
        arrangeInconsistentIdToken();

        assertRefusalClearsSession(metadata, IllegalStateException.class);
    }

    @Test
    @DisplayName("Should keep a re-login grant when the refused exchange presented a superseded token")
    void shouldKeepReLoginGrantWhenPresentedTokenIsReuse(URIBuilder uriBuilder) {
        ProviderMetadata metadata = metadata(uriBuilder);
        ReuseFixture reuse = arrangeReuse(metadata);

        assertReLoginSurvivesRefusal(metadata, ClientProtocolException.class);
        assertAll("the reuse verdict and its revocations are unchanged",
                () -> assertTrue(revocationClient.revoked(reuse.replayed()), "the replayed token is revoked"),
                () -> assertTrue(revocationClient.revoked(reuse.successor()), "the replay's successor is revoked"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Refresh token reuse detected");
    }

    @Test
    @DisplayName("Should clear the session on reuse when no re-login landed during the exchange")
    void shouldClearSessionOnReuseWithoutReLogin(URIBuilder uriBuilder) {
        ProviderMetadata metadata = metadata(uriBuilder);
        arrangeReuse(metadata);

        assertRefusalClearsSession(metadata, ClientProtocolException.class);
    }

    @Test
    @DisplayName("Should keep a grant stored concurrently while a refused exchange tears the session down")
    void shouldKeepGrantStoredDuringRefusedExchangeTeardown(URIBuilder uriBuilder) throws Exception {
        ProviderMetadata metadata = metadata(uriBuilder);
        var racingStore = new RacingTokenStore();
        var racingManager = new TokenLifecycleManager(racingStore, new RefreshScheduler());
        String newGrant = token();
        racingManager.store(session, bearerBundle(token(), null));
        getModuleDispatcher().respondWith(400, INVALID_GRANT);
        var concurrentStoreReturned = new CountDownLatch(1);
        var concurrentStore = new AtomicReference<Future<?>>();
        var concurrentStoreInsideTeardown = new AtomicBoolean();
        var clientAuth = clientAuth(config);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // Armed during the exchange, so the next retrieve is the teardown's compare. The hook runs on
            // the refreshing thread after the compare has read the refused token and before the clear, and
            // only then starts a store() of a new grant. This is a timing-bounded guard: without the grant
            // lock around the compare and the clear the store normally completes inside the window and the
            // clear wipes the new grant; with it, the store blocks until the teardown is done and the
            // bounded wait simply times out. The bound can only weaken the red check, never make the green
            // check flaky.
            RefreshFlow flow = flowRunningDuringExchange(() -> racingStore.afterNextRetrieve(() -> {
                concurrentStore.set(pool.submit(() -> {
                    racingManager.store(session, bearerBundle(newGrant, null));
                    concurrentStoreReturned.countDown();
                }));
                concurrentStoreInsideTeardown.set(awaitBounded(concurrentStoreReturned));
            }));
            assertThrows(TransportException.class,
                    () -> racingManager.refresh(session, metadata, flow, revocationClient, idBridge, clientAuth));
            assertNotNull(concurrentStore.get(), "the teardown reads the store before it clears");
            concurrentStore.get().get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(Optional.of(newGrant), racingManager.get(session).map(StoredToken::refreshToken),
                "the grant stored during the teardown survives it (store inside the teardown window: "
                        + concurrentStoreInsideTeardown.get() + ")");
    }

    @Test
    @DisplayName("Should clear the session when the store cannot be read during a refused exchange's teardown")
    void shouldClearSessionWhenStoreReadFailsDuringRefusalTeardown(URIBuilder uriBuilder) {
        ProviderMetadata metadata = metadata(uriBuilder);
        var racingStore = new RacingTokenStore();
        var racingManager = new TokenLifecycleManager(racingStore, new RefreshScheduler());
        String reLoginGrant = token();
        racingManager.store(session, bearerBundle(token(), null));
        getModuleDispatcher().respondWith(400, INVALID_GRANT);
        // A newer grant IS stored, but the teardown's read of it fails: an unreadable store is no evidence
        // the refused credential has gone, so the clear must run regardless.
        RefreshFlow flow = flowRunningDuringExchange(() -> {
            racingManager.store(session, bearerBundle(reLoginGrant, null));
            racingStore.failNextRetrieve();
        });
        var clientAuth = clientAuth(config);

        assertThrows(TransportException.class,
                () -> racingManager.refresh(session, metadata, flow, revocationClient, idBridge, clientAuth),
                "the original refusal still reaches the caller, not the store failure");

        assertAll("an unreadable store fails the session closed",
                () -> assertFalse(racingStore.retrieveFailurePending(), "the teardown read the store and it failed"),
                () -> assertTrue(racingManager.get(session).isEmpty(), "the newer grant is cleared with it"));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, TokenLifecycleManager.class);
    }

    @Test
    @DisplayName("Should store a bundle with no expiry when the refresh carries an absurd expires_in")
    void shouldStoreNoExpiryForAbsurdExpiresIn(URIBuilder uriBuilder) {
        ProviderMetadata metadata = metadata(uriBuilder);
        manager.store(session, bearerBundle(token(), null));

        StoredToken absurd = refreshTo(metadata, token(), Long.MAX_VALUE);
        StoredToken plausible = refreshTo(metadata, token(), 300);

        assertAll("expires_in is plausibility-capped on the wired path",
                () -> assertNull(absurd.expiresAt(), "an absurd lifetime is stored as no known expiry"),
                () -> assertNotNull(plausible.expiresAt(), "a plausible lifetime is stored as an expiry"));
    }

    private void assertReuseRevokedAndCleared(ProviderMetadata metadata, String replayed, String replaySuccessor) {
        getModuleDispatcher().respondWith(TokenDispatcher.tokenResponse(accessHolder.getRawToken(),
                replaySuccessor, null, 300));
        var flow = refreshFlow(config);
        var clientAuth = clientAuth(config);

        assertThrows(ClientProtocolException.class,
                () -> manager.refresh(session, metadata, flow, revocationClient, idBridge, clientAuth));

        assertAll("a superseded token is still reuse",
                () -> assertTrue(revocationClient.revoked(replayed), "the replayed token is revoked"),
                () -> assertTrue(revocationClient.revoked(replaySuccessor), "the replay's successor is revoked"),
                () -> assertTrue(manager.get(session).isEmpty(), "the session is cleared fail-closed"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Refresh token reuse detected");
    }

    /** Stores a grant and makes the token endpoint reject its refresh as {@code invalid_grant}. */
    private void arrangeCredentialRejection() {
        manager.store(session, bearerBundle(token(), null));
        getModuleDispatcher().respondWith(400, INVALID_GRANT);
    }

    /**
     * Stores a grant and makes the token endpoint rotate it while returning an ID token whose {@code sub}
     * differs from the refreshed access token's, so the §12.2 check refuses after rotation.
     *
     * @return the rotated refresh token the endpoint issues
     */
    private String arrangeInconsistentIdToken() {
        String subject = Generators.letterStrings(10, 15).next();
        accessHolder.withClaim(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString(subject));
        idHolder.withClaim(ClaimName.SUBJECT.getName(),
                ClaimValue.forPlainString(subject + Generators.letterStrings(3, 5).next()));
        manager.store(session, bearerBundle(token(), token()));
        String rotated = token();
        getModuleDispatcher().respondWith(TokenDispatcher.tokenResponse(accessHolder.getRawToken(), rotated,
                idHolder.getRawToken(), 300));
        return rotated;
    }

    /**
     * Refreshes once and re-stores the superseded token, so the next refresh presents a replay, and makes
     * the token endpoint issue a successor for that replay.
     */
    private ReuseFixture arrangeReuse(ProviderMetadata metadata) {
        String replayed = token();
        manager.store(session, bearerBundle(replayed, null));
        refreshTo(metadata, token());
        manager.store(session, bearerBundle(replayed, null));
        String successor = token();
        getModuleDispatcher().respondWith(TokenDispatcher.tokenResponse(accessHolder.getRawToken(), successor,
                null, 300));
        return new ReuseFixture(replayed, successor);
    }

    /** The replayed token of {@link #arrangeReuse} and the successor the endpoint issues for it. */
    private record ReuseFixture(String replayed, String successor) {
    }

    /**
     * Runs the arranged refused refresh with a re-login stored during the exchange — after the held token
     * was captured, before the teardown — and asserts the refusal propagates, the re-login grant survives
     * with a family that still refreshes it, and the kept grant is logged.
     */
    private void assertReLoginSurvivesRefusal(ProviderMetadata metadata, Class<? extends Throwable> refusal) {
        String reLoginGrant = token();
        RefreshFlow flow = flowRunningDuringExchange(() -> manager.store(session, bearerBundle(reLoginGrant, null)));
        var clientAuth = clientAuth(config);

        assertThrows(refusal, () -> manager.refresh(session, metadata, flow, revocationClient, idBridge, clientAuth));

        assertAll("the refusal ends the refused exchange, not the newer grant",
                () -> assertEquals(Optional.of(reLoginGrant), manager.get(session).map(StoredToken::refreshToken),
                        "the re-login grant stored during the exchange is still the stored bundle"),
                () -> assertFalse(revocationClient.revoked(reLoginGrant), "the newer grant is never revoked"));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                ClientLogMessages.INFO.REFUSED_REFRESH_KEPT_NEWER_GRANT.resolveIdentifierString());
        String successor = token();
        assertEquals(successor, refreshTo(metadata, successor).refreshToken(),
                "the kept grant's family is intact, so its own refresh is not reuse");
    }

    /** Runs the arranged refused refresh with no re-login and asserts the session is cleared fail-closed. */
    private void assertRefusalClearsSession(ProviderMetadata metadata, Class<? extends Throwable> refusal) {
        var flow = refreshFlow(config);
        var clientAuth = clientAuth(config);

        assertThrows(refusal, () -> manager.refresh(session, metadata, flow, revocationClient, idBridge, clientAuth));

        assertTrue(manager.get(session).isEmpty(), "with no newer grant the refused session is cleared fail-closed");
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, TokenLifecycleManager.class);
    }

    /** The real refresh flow, running {@code duringExchange} on the refreshing thread before the exchange. */
    private RefreshFlow flowRunningDuringExchange(Runnable duringExchange) {
        return new RefreshFlow(config, new TokenEndpointClient(config), accessBridge, clientAuth(config)) {
            @Override
            public RotationResult refresh(ProviderMetadata providerMetadata, String refreshToken) {
                duringExchange.run();
                return super.refresh(providerMetadata, refreshToken);
            }
        };
    }

    private StoredToken refreshTo(ProviderMetadata metadata, String successor) {
        return refreshTo(metadata, successor, 300);
    }

    private StoredToken refreshTo(ProviderMetadata metadata, String successor, long expiresIn) {
        getModuleDispatcher().respondWith(TokenDispatcher.tokenResponse(accessHolder.getRawToken(), successor,
                null, expiresIn));
        return manager.refresh(session, metadata, refreshFlow(config), revocationClient, idBridge,
                clientAuth(config)).orElseThrow();
    }

    private static String token() {
        return Generators.letterStrings(20, 40).next();
    }

    /**
     * Waits a short, bounded time for {@code latch}. The bound can only weaken the red check (the crossing
     * is not provoked when the second store is slower than the bound), never make the green flaky.
     *
     * @return whether the latch fired inside the bound
     */
    private static boolean awaitBounded(CountDownLatch latch) {
        try {
            return latch.await(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * An in-memory store that runs one registered action right after the next {@code retrieve} has taken
     * its snapshot and before it returns — the point at which a re-login lands between a refresh reading
     * the held token and capturing its family — or right after the next {@code store} write has returned,
     * the point between the manager's token write and its family update — or right after the next
     * {@code update} has returned or thrown, the point between a refused applyRefresh transform and its
     * fail-closed teardown. Each action fires once, on the calling thread, so the interleaving point is
     * deterministic; only the concurrent-writer tests add a bounded wait on top of it. It can also fail the
     * next {@code retrieve} outright, standing in for a store that cannot be read.
     */
    private static final class RacingTokenStore implements TokenStore {

        private final InMemoryTokenStore delegate = new InMemoryTokenStore();
        private final AtomicReference<Runnable> afterNextRetrieve = new AtomicReference<>();
        private final AtomicReference<Runnable> afterNextStore = new AtomicReference<>();
        private final AtomicReference<Runnable> afterNextUpdate = new AtomicReference<>();
        private final AtomicBoolean failNextRetrieve = new AtomicBoolean();

        void afterNextRetrieve(Runnable action) {
            afterNextRetrieve.set(action);
        }

        void failNextRetrieve() {
            failNextRetrieve.set(true);
        }

        boolean retrieveFailurePending() {
            return failNextRetrieve.get();
        }

        void afterNextStore(Runnable action) {
            afterNextStore.set(action);
        }

        void afterNextUpdate(Runnable action) {
            afterNextUpdate.set(action);
        }

        @Override
        public void store(String sessionId, StoredToken token) {
            delegate.store(sessionId, token);
            Runnable action = afterNextStore.getAndSet(null);
            if (action != null) {
                action.run();
            }
        }

        @Override
        public Optional<StoredToken> retrieve(String sessionId) {
            if (failNextRetrieve.getAndSet(false)) {
                throw new IllegalStateException("simulated unreadable token store");
            }
            Optional<StoredToken> snapshot = delegate.retrieve(sessionId);
            Runnable action = afterNextRetrieve.getAndSet(null);
            if (action != null) {
                action.run();
            }
            return snapshot;
        }

        @Override
        public Optional<StoredToken> remove(String sessionId) {
            return delegate.remove(sessionId);
        }

        @Override
        public Optional<StoredToken> update(String sessionId, UnaryOperator<StoredToken> updater) {
            try {
                return delegate.update(sessionId, updater);
            } finally {
                Runnable action = afterNextUpdate.getAndSet(null);
                if (action != null) {
                    action.run();
                }
            }
        }
    }
}
