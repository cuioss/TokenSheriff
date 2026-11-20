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

import de.cuioss.sheriff.token.client.discovery.CachingDiscoveryResolverSupport.Hold;
import de.cuioss.sheriff.token.client.discovery.CachingDiscoveryResolverSupport.ScriptedDelegate;
import de.cuioss.sheriff.token.client.internal.ClientLogMessages;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.Serial;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicReference;

import static de.cuioss.sheriff.token.client.discovery.CachingDiscoveryResolverSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Cases for the refresh path of {@link CachingDiscoveryResolver}: a refreshed document
 * reaches the next caller only after the refresh, callers that join a failed refresh receive the
 * documented answer, an aborted refresh releases the slot so the joined callers re-evaluate, and the
 * recovery record carries a pinned identifier. The delegate is a scripted {@link DiscoveryResolver}
 * subclass and time is driven by an injected {@link InstantSource}; no case sleeps.
 */
@EnableTestLogger
@DisplayName("CachingDiscoveryResolver refresh path")
class CachingDiscoveryResolverRefreshPathTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration GRACE = Duration.ofMinutes(10);
    private static final String OLD_TOKEN_ENDPOINT = ISSUER + "/token";
    private static final String OLD_USERINFO_ENDPOINT = ISSUER + "/userinfo";
    private static final String NEW_TOKEN_ENDPOINT = ISSUER + "/v2/token";
    private static final String NEW_USERINFO_ENDPOINT = ISSUER + "/v2/userinfo";

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final InstantSource clock = now::get;
    private final ScriptedDelegate delegate = new ScriptedDelegate();

    @Nested
    @DisplayName("Refreshed document")
    class RefreshedDocument {

        @Test
        @DisplayName("Should serve the old endpoints before the TTL, without a refresh")
        void shouldServeOldEndpointsBeforeTtl() {
            delegate.then(Documents::oldDocument).then(Documents::newDocument);
            var resolver = resolver();
            resolver.resolve();

            advance(TTL.minusSeconds(1));
            var beforeTtl = resolver.resolve();

            assertAll("negative control: still fresh",
                    () -> assertEquals(OLD_TOKEN_ENDPOINT, beforeTtl.tokenEndpoint),
                    () -> assertEquals(OLD_USERINFO_ENDPOINT, beforeTtl.userinfoEndpoint),
                    () -> assertEquals(1, delegate.calls(), "no refresh inside the TTL"));
        }

        @Test
        @DisplayName("Should hand the refreshed endpoints to the caller after the refresh and to every later caller")
        void shouldServeRefreshedEndpointsAfterRefresh() {
            delegate.then(Documents::oldDocument).then(Documents::newDocument);
            var resolver = resolver();
            var initial = resolver.resolve();

            advance(TTL);
            var refreshed = resolver.resolve();
            advance(Duration.ofSeconds(1));
            var next = resolver.resolve();

            assertAll("refreshed document",
                    () -> assertEquals(OLD_TOKEN_ENDPOINT, initial.tokenEndpoint, "the first document"),
                    () -> assertEquals(NEW_TOKEN_ENDPOINT, refreshed.tokenEndpoint,
                            "the refreshing caller receives the new token endpoint"),
                    () -> assertEquals(NEW_USERINFO_ENDPOINT, refreshed.userinfoEndpoint,
                            "the refreshing caller receives the new userinfo endpoint"),
                    () -> assertEquals(NEW_TOKEN_ENDPOINT, next.tokenEndpoint, "the next caller as well"),
                    () -> assertEquals(NEW_USERINFO_ENDPOINT, next.userinfoEndpoint, "the next caller as well"),
                    () -> assertEquals(2, delegate.calls(), "one refresh, the next call is served from the cache"),
                    () -> assertEquals(START.plus(TTL), resolver.status().lastSuccess()));
        }
    }

    @Nested
    @DisplayName("Joiners of a failed refresh")
    class JoinersOfFailedRefresh {

        @Test
        @DisplayName("Should hand a joiner the transport failure when nothing was ever resolved")
        void shouldHandJoinerTransportFailureWithNothingResolved() throws Exception {
            var failure = new TransportException("discovery failed");
            var held = new Hold();
            delegate.then(() -> {
                held.enterAndWait();
                throw failure;
            });
            var resolver = resolver();

            var winner = startCatchingErrors(resolver::resolve);
            held.awaitEntered();
            var joiner = startCatchingErrors(resolver::resolve);
            awaitParked(joiner.thread());
            held.release();

            var winnerOutcome = winner.outcome();
            var joinerOutcome = joiner.outcome();
            assertAll("joined failure, nothing resolved",
                    () -> assertSame(failure, winnerOutcome.failure(), "the winner receives the documented exception"),
                    () -> assertSame(failure, joinerOutcome.failure(), "the joiner receives the same instance"),
                    () -> assertEquals(1, delegate.calls(), "the joiner shared the winner's refresh"),
                    () -> assertEquals(DiscoveryCacheStatus.State.FAILED, resolver.status().state()),
                    () -> assertEquals(LoaderStatus.ERROR, resolver.getLoaderStatus(), "nothing servable"));
        }

        @Test
        @DisplayName("Should hand a joiner the stale copy within grace when the refresh fails")
        void shouldHandJoinerStaleCopyWithinGrace() throws Exception {
            var held = new Hold();
            delegate.then(Documents::oldDocument).then(() -> {
                held.enterAndWait();
                throw new TransportException("refresh failed");
            });
            var resolver = resolver();
            resolver.resolve();
            advance(TTL);

            var winner = startCatchingErrors(resolver::resolve);
            held.awaitEntered();
            var joiner = startCatchingErrors(resolver::resolve);
            awaitParked(joiner.thread());
            held.release();

            var winnerOutcome = winner.outcome();
            var joinerOutcome = joiner.outcome();
            assertAll("joined failure within grace",
                    () -> assertNull(winnerOutcome.failure(), "the winner is served the stale copy"),
                    () -> assertNull(joinerOutcome.failure(), "the joiner is served the stale copy"),
                    () -> assertEquals(OLD_TOKEN_ENDPOINT, winnerOutcome.result().tokenEndpoint),
                    () -> assertEquals(OLD_TOKEN_ENDPOINT, joinerOutcome.result().tokenEndpoint),
                    () -> assertNotSame(winnerOutcome.result(), joinerOutcome.result(), "each gets its own copy"),
                    () -> assertEquals(2, delegate.calls(), "the joiner shared the winner's refresh"),
                    () -> assertEquals(DiscoveryCacheStatus.State.FAILED, resolver.status().state()),
                    () -> assertEquals(START, resolver.status().lastSuccess(), "lastSuccess is kept"),
                    () -> assertEquals(LoaderStatus.UNDEFINED, resolver.getLoaderStatus(), "servable within grace"));
        }
    }

    @Nested
    @DisplayName("Aborted refresh")
    class AbortedRefresh {

        @Test
        @DisplayName("Should release the slot after an Error, so the next call refreshes without a recorded failure")
        void shouldReleaseSlotAfterError() {
            var error = new ScriptedError();
            delegate.then(() -> {
                throw error;
            }).then(Documents::oldDocument);
            var resolver = resolver();

            var thrown = assertThrows(ScriptedError.class, resolver::resolve);
            var afterAbort = resolver.status();
            var next = resolver.resolve();

            assertAll("aborted by an Error",
                    () -> assertSame(error, thrown, "the Error reaches the winner unchanged"),
                    () -> assertEquals(DiscoveryCacheStatus.notAttempted(), afterAbort, "no state was written"),
                    () -> assertEquals(OLD_TOKEN_ENDPOINT, next.tokenEndpoint, "the slot was released"),
                    () -> assertEquals(2, delegate.calls()));
        }

        @Test
        @DisplayName("Should let a joiner re-evaluate and run its own refresh after the winner's Error")
        void shouldLetJoinerRefreshAfterWinnerError() throws Exception {
            var error = new ScriptedError();
            var held = new Hold();
            delegate.then(() -> {
                held.enterAndWait();
                throw error;
            }).then(Documents::newDocument);
            var resolver = resolver();

            var winner = startCatchingErrors(resolver::resolve);
            held.awaitEntered();
            var joiner = startCatchingErrors(resolver::resolve);
            awaitParked(joiner.thread());
            held.release();

            var winnerOutcome = winner.outcome();
            var joinerOutcome = joiner.outcome();
            assertAll("joiner re-evaluates after an aborted refresh",
                    () -> assertSame(error, winnerOutcome.failure(), "the winner receives the Error"),
                    () -> assertNull(joinerOutcome.failure(), "the joiner never sees the abort"),
                    () -> assertEquals(NEW_TOKEN_ENDPOINT, joinerOutcome.result().tokenEndpoint,
                            "the joiner resolves through its own refresh"),
                    () -> assertEquals(2, delegate.calls(), "the joiner claimed a fresh refresh"),
                    () -> assertEquals(DiscoveryCacheStatus.State.RESOLVED, resolver.status().state()),
                    () -> assertNull(resolver.status().lastFailureCause(), "the Error is not a provider failure"));
        }
    }

    @Nested
    @DisplayName("Recovery record identifier")
    class RecoveryIdentifier {

        @Test
        @DisplayName("Should emit the recovery record under the identifier TokenSheriffClient-4")
        void shouldEmitPinnedRecoveryIdentifier() {
            delegate.then(Documents::oldDocument)
                    .then(() -> {
                        throw new TransportException("refresh failed");
                    })
                    .then(Documents::newDocument);
            var resolver = resolver();
            resolver.resolve();
            advance(TTL);
            resolver.resolve();
            advance(Duration.ofSeconds(1));

            resolver.resolve();

            assertEquals("TokenSheriffClient-4",
                    ClientLogMessages.INFO.DISCOVERY_CACHE_RECOVERED.resolveIdentifierString(),
                    "the documented identifier of the recovery record");
            LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO, "TokenSheriffClient-4");
        }
    }

    private CachingDiscoveryResolver resolver() {
        return new CachingDiscoveryResolver(delegate, TTL, GRACE, Duration.ZERO, Duration.ZERO, clock);
    }

    private void advance(Duration duration) {
        now.set(now.get().plus(duration));
    }

    /** An {@link Error} thrown by the delegate, distinct from anything the cache throws itself. */
    private static final class ScriptedError extends Error {

        @Serial
        private static final long serialVersionUID = 1L;

        ScriptedError() {
            super("scripted delegate error");
        }
    }

    /** The two documents the scripted delegate serves: the one before and the one after a refresh. */
    private static final class Documents {

        private Documents() {
        }

        static ProviderMetadata oldDocument() {
            return document(OLD_TOKEN_ENDPOINT, OLD_USERINFO_ENDPOINT);
        }

        static ProviderMetadata newDocument() {
            return document(NEW_TOKEN_ENDPOINT, NEW_USERINFO_ENDPOINT);
        }
    }
}
