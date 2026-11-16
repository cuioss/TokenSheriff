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

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.internal.ClientLogMessages;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.Serial;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression cases for the refresh path of {@link CachingDiscoveryResolver}: a refreshed document
 * reaches the next caller only after the refresh, callers that join a failed refresh receive the
 * documented answer, an aborted refresh releases the slot so the joined callers re-evaluate, and the
 * recovery record carries a pinned identifier. The delegate is a scripted {@link DiscoveryResolver}
 * subclass and time is driven by an injected {@link InstantSource}; no case sleeps.
 */
@EnableTestLogger
@DisplayName("CachingDiscoveryResolver refresh regressions")
class CachingDiscoveryResolverRefreshRegressionTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration GRACE = Duration.ofMinutes(10);
    private static final String ISSUER = "https://issuer.example";
    private static final String OLD_TOKEN_ENDPOINT = ISSUER + "/token";
    private static final String OLD_USERINFO_ENDPOINT = ISSUER + "/userinfo";
    private static final String NEW_TOKEN_ENDPOINT = ISSUER + "/v2/token";
    private static final String NEW_USERINFO_ENDPOINT = ISSUER + "/v2/userinfo";
    private static final long AWAIT_SECONDS = 10;

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final InstantSource clock = now::get;
    private final ScriptedDelegate delegate = new ScriptedDelegate();

    @Nested
    @DisplayName("Refreshed document")
    class RefreshedDocument {

        @Test
        @DisplayName("Should serve the old endpoints before the TTL, without a refresh")
        void shouldServeOldEndpointsBeforeTtl() {
            delegate.then(ScriptedDelegate::oldDocument).then(ScriptedDelegate::newDocument);
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
            delegate.then(ScriptedDelegate::oldDocument).then(ScriptedDelegate::newDocument);
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

            var winner = start(resolver::resolve);
            held.awaitEntered();
            var joiner = start(resolver::resolve);
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
            delegate.then(ScriptedDelegate::oldDocument).then(() -> {
                held.enterAndWait();
                throw new TransportException("refresh failed");
            });
            var resolver = resolver();
            resolver.resolve();
            advance(TTL);

            var winner = start(resolver::resolve);
            held.awaitEntered();
            var joiner = start(resolver::resolve);
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
            }).then(ScriptedDelegate::oldDocument);
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
            }).then(ScriptedDelegate::newDocument);
            var resolver = resolver();

            var winner = start(resolver::resolve);
            held.awaitEntered();
            var joiner = start(resolver::resolve);
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
            delegate.then(ScriptedDelegate::oldDocument)
                    .then(() -> {
                        throw new TransportException("refresh failed");
                    })
                    .then(ScriptedDelegate::newDocument);
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

    /** Waits until the thread parks, which a joined caller does inside its wait for the refresh. */
    private static void awaitParked(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (thread.getState() != Thread.State.WAITING) {
            if (System.nanoTime() > deadline) {
                fail("the joining caller never waited for the in-flight refresh, state " + thread.getState());
            }
            Thread.onSpinWait();
        }
    }

    private static Caller start(Callable<ProviderMetadata> call) {
        var task = new FutureTask<>(() -> {
            try {
                return new Outcome(call.call(), null);
            }
            // cui-rewrite:disable InvalidExceptionUsageRecipe
            catch (RuntimeException | Error e) {
                // The outcome under test is whatever the resolver throws, Error included.
                return new Outcome(null, e);
            }
        });
        var thread = new Thread(task, "discovery-caller");
        thread.setDaemon(true);
        thread.start();
        return new Caller(thread, task);
    }

    /** A caller running {@link CachingDiscoveryResolver#resolve()} on its own thread. */
    private record Caller(Thread thread, FutureTask<Outcome> task) {

        Outcome outcome() throws Exception {
            return task.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    /** What one caller got back. */
    private record Outcome(@Nullable ProviderMetadata result, @Nullable Throwable failure) {
    }

    /** Holds the delegate inside its call until the test releases it. */
    private static final class Hold {

        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        void enterAndWait() {
            entered.countDown();
            try {
                if (!release.await(AWAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new AssertionError("the test never released the delegate");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while holding the delegate", e);
            }
        }

        void awaitEntered() throws InterruptedException {
            assertTrue(entered.await(AWAIT_SECONDS, TimeUnit.SECONDS), "the winner must reach the delegate");
        }

        void release() {
            release.countDown();
        }
    }

    /** An {@link Error} thrown by the delegate, distinct from anything the cache throws itself. */
    private static final class ScriptedError extends Error {

        @Serial
        private static final long serialVersionUID = 1L;

        ScriptedError() {
            super("scripted delegate error");
        }
    }

    /** A delegate that answers each call with the next scripted step and counts the calls. */
    private static final class ScriptedDelegate extends DiscoveryResolver {

        private final Queue<Supplier<ProviderMetadata>> script = new ConcurrentLinkedQueue<>();
        private final AtomicInteger calls = new AtomicInteger();

        ScriptedDelegate() {
            super(ClientConfiguration.builder()
                    .issuer(ISSUER)
                    .clientId("client")
                    .clientSecret("secret")
                    .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                    .build());
        }

        ScriptedDelegate then(Supplier<ProviderMetadata> step) {
            script.add(step);
            return this;
        }

        int calls() {
            return calls.get();
        }

        @Override
        public ProviderMetadata resolve() {
            calls.incrementAndGet();
            Supplier<ProviderMetadata> step = script.poll();
            if (step == null) {
                throw new AssertionError("unscripted delegate call");
            }
            return step.get();
        }

        static ProviderMetadata oldDocument() {
            return document(OLD_TOKEN_ENDPOINT, OLD_USERINFO_ENDPOINT);
        }

        static ProviderMetadata newDocument() {
            return document(NEW_TOKEN_ENDPOINT, NEW_USERINFO_ENDPOINT);
        }

        private static ProviderMetadata document(String tokenEndpoint, String userinfoEndpoint) {
            var metadata = new ProviderMetadata();
            metadata.issuer = ISSUER;
            metadata.tokenEndpoint = tokenEndpoint;
            metadata.userinfoEndpoint = userinfoEndpoint;
            return metadata;
        }
    }
}
