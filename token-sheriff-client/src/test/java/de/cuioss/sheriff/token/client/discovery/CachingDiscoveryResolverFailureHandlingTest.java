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
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How {@link CachingDiscoveryResolver} treats a delegate failure that is not a plain transport
 * failure: any other runtime exception is recorded as a failed refresh, and a refresh that fails
 * while the calling thread is interrupted is aborted without writing any state. The delegate is a
 * scripted {@link DiscoveryResolver} subclass and time is driven by an injected
 * {@link InstantSource}; no case sleeps.
 */
@EnableTestLogger
@DisplayName("CachingDiscoveryResolver records every delegate failure and aborts an interrupted refresh")
class CachingDiscoveryResolverFailureHandlingTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration GRACE = Duration.ofMinutes(10);
    private static final Duration INTERVAL = Duration.ofMinutes(1);
    private static final String ISSUER = "https://issuer.example";
    private static final String GOOD_TOKEN_ENDPOINT = ISSUER + "/token";
    private static final String SECRET_DETAIL = "detail-that-must-not-reach-the-status";
    private static final long AWAIT_SECONDS = 10;

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final InstantSource clock = now::get;
    private final ScriptedDelegate delegate = new ScriptedDelegate();

    @AfterEach
    void clearInterruptFlag() {
        // A case that fails before it consumes the flag must not leak it into the next case.
        Thread.interrupted();
    }

    static Stream<Arguments> unexpectedFailures() {
        return Stream.of(
                Arguments.of(new IllegalStateException(SECRET_DETAIL)),
                Arguments.of(new UnsupportedOperationException(SECRET_DETAIL)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unexpectedFailures")
    @DisplayName("Should record an unexpected runtime exception as FAILED with its class name only")
    void shouldRecordUnexpectedExceptionAsFailure(RuntimeException failure) {
        delegate.then(() -> {
            throw failure;
        });
        var resolver = resolver(Duration.ZERO);

        var thrown = assertThrows(RuntimeException.class, resolver::resolve);

        var status = resolver.status();
        assertAll("unexpected failure recorded",
                () -> assertSame(failure, thrown, "nothing resolved: the recorded exception itself is rethrown"),
                () -> assertEquals(DiscoveryCacheStatus.State.FAILED, status.state()),
                () -> assertEquals(failure.getClass().getName(), status.lastFailureCause()),
                () -> assertFalse(status.lastFailureCause().contains(SECRET_DETAIL), "no message text"),
                () -> assertNull(status.lastSuccess()),
                () -> assertEquals(LoaderStatus.ERROR, resolver.getLoaderStatus(), "nothing servable"));
    }

    @Test
    @DisplayName("Should apply the retry backoff after an unexpected runtime exception")
    void shouldApplyBackoffAfterUnexpectedException() {
        var failure = new IllegalStateException(SECRET_DETAIL);
        delegate.then(() -> {
            throw failure;
        }).then(ScriptedDelegate::good);
        var resolver = resolver(INTERVAL);

        assertThrows(IllegalStateException.class, resolver::resolve);
        advance(INTERVAL.minusSeconds(1));
        var withinInterval = assertThrows(IllegalStateException.class, resolver::resolve);
        assertEquals(1, delegate.calls(), "no second delegate call inside the minimum refresh interval");

        advance(Duration.ofSeconds(1));
        var recovered = resolver.resolve();

        assertAll("backoff applied",
                () -> assertSame(failure, withinInterval, "the stored instance is rethrown unchanged"),
                () -> assertEquals(2, delegate.calls(), "the interval elapsed, so it refreshes again"),
                () -> assertEquals(GOOD_TOKEN_ENDPOINT, recovered.tokenEndpoint),
                () -> assertEquals(DiscoveryCacheStatus.State.RESOLVED, resolver.status().state()));
    }

    @Test
    @DisplayName("Should serve the stale copy within grace and fail closed after it on an unexpected exception")
    void shouldApplyStalePolicyToUnexpectedException() {
        var failure = new IllegalStateException(SECRET_DETAIL);
        delegate.then(ScriptedDelegate::good).then(() -> {
            throw failure;
        });
        // An interval longer than the grace period, so the call past grace answers from the record.
        var resolver = resolver(GRACE.plus(INTERVAL));
        resolver.resolve();
        advance(TTL);

        var stale = resolver.resolve();
        var withinGrace = resolver.getLoaderStatus();
        advance(GRACE);
        var expired = assertThrows(TransportException.class, resolver::resolve);

        assertAll("stale policy",
                () -> assertEquals(GOOD_TOKEN_ENDPOINT, stale.tokenEndpoint, "the stale copy is served"),
                () -> assertEquals(LoaderStatus.UNDEFINED, withinGrace, "servable within grace"),
                () -> assertEquals(IllegalStateException.class.getName(), resolver.status().lastFailureCause()),
                () -> assertTrue(expired.getMessage().contains("expired"), expired.getMessage()),
                () -> assertTrue(expired.getMessage().contains(IllegalStateException.class.getName()),
                        expired.getMessage()),
                () -> assertSame(failure, expired.getCause(), "the recorded exception is the cause"),
                () -> assertEquals(LoaderStatus.ERROR, resolver.getLoaderStatus()),
                () -> assertEquals(2, delegate.calls()));
    }

    @Test
    @DisplayName("Should hand the winner and a joined caller the same recorded exception")
    void shouldHandJoinerTheRecordedException() throws Exception {
        var failure = new IllegalStateException(SECRET_DETAIL);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        delegate.then(() -> {
            entered.countDown();
            await(release);
            throw failure;
        });
        var resolver = resolver(Duration.ZERO);

        var winner = start(resolver::resolve);
        assertTrue(entered.await(AWAIT_SECONDS, TimeUnit.SECONDS), "the winner must reach the delegate");
        var joiner = start(resolver::resolve);
        awaitParked(joiner.thread());
        release.countDown();

        var winnerOutcome = winner.outcome();
        var joinerOutcome = joiner.outcome();
        assertAll("joined failure",
                () -> assertSame(failure, winnerOutcome.failure(), "the winner receives the recorded exception"),
                () -> assertSame(failure, joinerOutcome.failure(), "the joiner receives the same instance"),
                () -> assertEquals(1, delegate.calls(), "the joiner shared the winner's refresh"),
                () -> assertEquals(DiscoveryCacheStatus.State.FAILED, resolver.status().state()));
    }

    @Test
    @DisplayName("Should abort an interrupted first refresh without writing state or a backoff")
    void shouldAbortInterruptedFirstRefresh() {
        delegate.then(ScriptedDelegate::interrupted).then(ScriptedDelegate::good);
        var resolver = resolver(INTERVAL);

        var thrown = assertThrows(TransportException.class, resolver::resolve);
        boolean interruptKept = Thread.interrupted();
        var afterAbort = resolver.status();
        var loaderAfterAbort = resolver.getLoaderStatus();

        var next = resolver.resolve();

        assertAll("aborted first refresh",
                () -> assertTrue(interruptKept, "the caller's interrupt flag is kept"),
                () -> assertInstanceOf(InterruptedException.class, thrown.getCause(), "the delegate's exception"),
                () -> assertEquals(DiscoveryCacheStatus.notAttempted(), afterAbort, "no state was written"),
                () -> assertEquals(LoaderStatus.UNDEFINED, loaderAfterAbort),
                () -> assertEquals(GOOD_TOKEN_ENDPOINT, next.tokenEndpoint, "the next call refreshes"),
                () -> assertEquals(2, delegate.calls(), "no backoff held the next call back"));
    }

    @Test
    @DisplayName("Should leave a stale cache untouched when its refresh is interrupted")
    void shouldAbortInterruptedRefreshOfStaleCache() {
        delegate.then(ScriptedDelegate::good).then(ScriptedDelegate::interrupted).then(ScriptedDelegate::good);
        var resolver = resolver(INTERVAL);
        resolver.resolve();
        advance(TTL);
        var beforeAbort = resolver.status();

        assertThrows(TransportException.class, resolver::resolve);
        boolean interruptKept = Thread.interrupted();
        var afterAbort = resolver.status();
        resolver.resolve();

        assertAll("aborted stale refresh",
                () -> assertTrue(interruptKept, "the caller's interrupt flag is kept"),
                () -> assertEquals(beforeAbort, afterAbort, "the status is unchanged"),
                () -> assertEquals(DiscoveryCacheStatus.State.RESOLVED, afterAbort.state()),
                () -> assertNull(afterAbort.lastFailureCause(), "the interrupt is not a provider failure"),
                () -> assertEquals(3, delegate.calls(), "the next call refreshes again without a backoff"),
                () -> assertEquals(START.plus(TTL), resolver.status().lastSuccess()));
    }

    @Test
    @DisplayName("Should let a joined caller run its own refresh after the winner's refresh was interrupted")
    void shouldLetJoinerRefreshAfterInterruptedWinner() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        delegate.then(() -> {
            entered.countDown();
            await(release);
            return ScriptedDelegate.interrupted();
        }).then(ScriptedDelegate::good);
        var resolver = resolver(INTERVAL);

        var winner = start(resolver::resolve);
        assertTrue(entered.await(AWAIT_SECONDS, TimeUnit.SECONDS), "the winner must reach the delegate");
        var joiner = start(resolver::resolve);
        awaitParked(joiner.thread());
        release.countDown();

        var winnerOutcome = winner.outcome();
        var joinerOutcome = joiner.outcome();
        assertAll("joiner re-evaluates",
                () -> assertInstanceOf(TransportException.class, winnerOutcome.failure(),
                        "the winner receives the delegate's exception"),
                () -> assertTrue(winnerOutcome.interrupted(), "the winner keeps its interrupt flag"),
                () -> assertNull(joinerOutcome.failure(), "the joiner does not receive the abort"),
                () -> assertNotNull(joinerOutcome.result(), "the joiner resolves through its own refresh"),
                () -> assertEquals(GOOD_TOKEN_ENDPOINT, joinerOutcome.result().tokenEndpoint),
                () -> assertFalse(joinerOutcome.interrupted(), "the joiner was never interrupted"),
                () -> assertEquals(2, delegate.calls(), "the joiner claimed a fresh refresh"),
                () -> assertEquals(DiscoveryCacheStatus.State.RESOLVED, resolver.status().state()));
    }

    @Test
    @DisplayName("Should still record a TransportException as FAILED when the caller is not interrupted")
    void shouldRecordNonInterruptedTransportException() {
        var failure = new TransportException("request failed", new InterruptedException());
        delegate.then(() -> {
            throw failure;
        });
        var resolver = resolver(INTERVAL);

        var thrown = assertThrows(TransportException.class, resolver::resolve);
        boolean interrupted = Thread.currentThread().isInterrupted();
        advance(INTERVAL.minusSeconds(1));
        assertThrows(TransportException.class, resolver::resolve);

        var status = resolver.status();
        assertAll("negative control",
                () -> assertFalse(interrupted, "the flag is not set by the cache"),
                () -> assertSame(failure, thrown),
                () -> assertEquals(DiscoveryCacheStatus.State.FAILED, status.state()),
                () -> assertEquals(TransportException.class.getName(), status.lastFailureCause()),
                () -> assertEquals(1, delegate.calls(), "the backoff of a recorded failure applies"));
    }

    private CachingDiscoveryResolver resolver(Duration minRefreshInterval) {
        return new CachingDiscoveryResolver(delegate, TTL, GRACE, minRefreshInterval, Duration.ZERO, clock);
    }

    private void advance(Duration duration) {
        now.set(now.get().plus(duration));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(AWAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("the test never released the delegate");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while holding the delegate", e);
        }
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
                return new Outcome(call.call(), null, Thread.currentThread().isInterrupted());
            }
            /*TODO: Catch specific not RuntimeException. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe*/
            catch (RuntimeException e) {
                return new Outcome(null, e, Thread.currentThread().isInterrupted());
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

    /** What one caller got back, and whether its interrupt flag was set when it got it. */
    private record Outcome(@Nullable ProviderMetadata result, @Nullable RuntimeException failure,
    boolean interrupted) {
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

        static ProviderMetadata good() {
            var metadata = new ProviderMetadata();
            metadata.issuer = ISSUER;
            metadata.tokenEndpoint = GOOD_TOKEN_ENDPOINT;
            return metadata;
        }

        /** Mirrors {@code DiscoveryResolver.fetch}: restore the flag, then report a transport failure. */
        static ProviderMetadata interrupted() {
            Thread.currentThread().interrupt();
            throw new TransportException("request interrupted", new InterruptedException());
        }
    }
}
