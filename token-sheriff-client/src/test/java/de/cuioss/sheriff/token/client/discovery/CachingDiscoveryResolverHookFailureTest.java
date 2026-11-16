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
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A throwing {@code afterClaim} hook aborts the refresh like any other abort: the exception reaches
 * the caller that claimed the slot, no state is written, the claim is released, and a caller that
 * joined the claim re-evaluates instead of waiting forever. The delegate is a scripted
 * {@link DiscoveryResolver} subclass and time is driven by an injected {@link InstantSource}; no case
 * sleeps, and every wait is bounded.
 */
@EnableTestLogger
@DisplayName("CachingDiscoveryResolver releases the refresh claim when the afterClaim hook throws")
class CachingDiscoveryResolverHookFailureTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration GRACE = Duration.ofMinutes(10);
    private static final String ISSUER = "https://issuer.example";
    private static final String TOKEN_ENDPOINT = ISSUER + "/token";
    private static final long AWAIT_SECONDS = 10;

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final InstantSource clock = now::get;
    private final CountingDelegate delegate = new CountingDelegate();

    /** Runs once, on the next claim of the refresh slot; every later claim runs no hook. */
    private final AtomicReference<@Nullable Runnable> onNextClaim = new AtomicReference<>();
    private final CachingDiscoveryResolver resolver = new CachingDiscoveryResolver(delegate, TTL, GRACE,
            Duration.ZERO, Duration.ZERO, clock, () -> {
                Runnable hook = onNextClaim.getAndSet(null);
                if (hook != null) {
                    hook.run();
                }
            });

    @Test
    @DisplayName("Should propagate an AssertionError from the hook and release the claim for the next caller")
    void shouldReleaseClaimWhenHookThrowsError() {
        var failure = new AssertionError("hook failed");
        onNextClaim.set(() -> {
            throw failure;
        });

        var thrown = assertThrows(AssertionError.class, resolver::resolve);
        var afterAbort = resolver.status();
        var next = assertTimeoutPreemptively(Duration.ofSeconds(AWAIT_SECONDS), resolver::resolve,
                "the next caller must claim a fresh refresh instead of waiting on the aborted claim");

        assertAll("claim released after an Error from the hook",
                () -> assertSame(failure, thrown, "the hook's error reaches the caller that claimed the slot"),
                () -> assertEquals(DiscoveryCacheStatus.notAttempted(), afterAbort, "no state was written"),
                () -> assertEquals(TOKEN_ENDPOINT, next.tokenEndpoint, "the next caller resolves the document"),
                () -> assertEquals(1, delegate.calls(), "only the next caller's refresh reached the delegate"),
                () -> assertEquals(DiscoveryCacheStatus.State.RESOLVED, resolver.status().state()));
    }

    @Test
    @DisplayName("Should propagate a RuntimeException from the hook without recording a FAILED state")
    void shouldNotRecordHookRuntimeExceptionAsFailure() {
        resolver.resolve();
        advance(TTL);
        var beforeAbort = resolver.status();
        var failure = new IllegalStateException("hook failed");
        onNextClaim.set(() -> {
            throw failure;
        });

        var thrown = assertThrows(IllegalStateException.class, resolver::resolve);
        var afterAbort = resolver.status();
        var next = assertTimeoutPreemptively(Duration.ofSeconds(AWAIT_SECONDS), resolver::resolve,
                "the next caller must claim a fresh refresh instead of waiting on the aborted claim");

        assertAll("hook failure is not a delegate failure",
                () -> assertSame(failure, thrown, "the hook's exception reaches the caller that claimed the slot"),
                () -> assertEquals(beforeAbort, afterAbort, "the status is unchanged"),
                () -> assertEquals(DiscoveryCacheStatus.State.RESOLVED, afterAbort.state(), "no FAILED state"),
                () -> assertNull(afterAbort.lastFailureCause(), "no failure cause was recorded"),
                () -> assertEquals(TOKEN_ENDPOINT, next.tokenEndpoint, "the next caller refreshes"),
                () -> assertEquals(2, delegate.calls(), "the aborted claim never reached the delegate"),
                () -> assertEquals(START.plus(TTL), resolver.status().lastSuccess()));
    }

    @Test
    @DisplayName("Should let a caller that joined the claim re-evaluate when the hook throws")
    void shouldLetJoinerRefreshAfterHookThrows() throws Exception {
        var failure = new IllegalStateException("hook failed");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        onNextClaim.set(() -> {
            entered.countDown();
            await(release);
            throw failure;
        });

        var winner = start(resolver::resolve);
        assertTrue(entered.await(AWAIT_SECONDS, TimeUnit.SECONDS), "the winner must claim the slot");
        var joiner = start(resolver::resolve);
        awaitJoined(joiner.thread());
        release.countDown();

        var winnerOutcome = winner.outcome();
        var joinerOutcome = joiner.outcome();
        assertAll("joiner re-evaluates after the aborted claim",
                () -> assertSame(failure, winnerOutcome.failure(), "the winner receives the hook's exception"),
                () -> assertNull(joinerOutcome.failure(), "the joiner does not receive the abort"),
                () -> assertNotNull(joinerOutcome.result(), "the joiner is answered by its own refresh"),
                () -> assertEquals(TOKEN_ENDPOINT, joinerOutcome.result().tokenEndpoint),
                () -> assertEquals(1, delegate.calls(), "only the joiner's own refresh reached the delegate"),
                () -> assertEquals(DiscoveryCacheStatus.State.RESOLVED, resolver.status().state()));
    }

    @Test
    @DisplayName("Control: should hand a joined caller the winner's refresh when the hook returns normally")
    void shouldShareRefreshWhenHookReturnsNormally() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        onNextClaim.set(() -> {
            entered.countDown();
            await(release);
        });

        var winner = start(resolver::resolve);
        assertTrue(entered.await(AWAIT_SECONDS, TimeUnit.SECONDS), "the winner must claim the slot");
        var joiner = start(resolver::resolve);
        awaitJoined(joiner.thread());
        release.countDown();

        var winnerOutcome = winner.outcome();
        var joinerOutcome = joiner.outcome();
        assertAll("single shared refresh",
                () -> assertNull(winnerOutcome.failure(), "the winner's refresh succeeds"),
                () -> assertNull(joinerOutcome.failure(), "the joiner's wait succeeds"),
                () -> assertNotNull(winnerOutcome.result()),
                () -> assertNotNull(joinerOutcome.result()),
                () -> assertEquals(TOKEN_ENDPOINT, winnerOutcome.result().tokenEndpoint),
                () -> assertEquals(TOKEN_ENDPOINT, joinerOutcome.result().tokenEndpoint),
                () -> assertEquals(1, delegate.calls(), "the joiner shared the winner's refresh"),
                () -> assertEquals(DiscoveryCacheStatus.State.RESOLVED, resolver.status().state()));
    }

    private void advance(Duration duration) {
        now.set(now.get().plus(duration));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(AWAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("the test never released the hook");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while holding the hook", e);
        }
    }

    /**
     * Waits until the thread parks inside {@code CachingDiscoveryResolver.join}, the wait on the
     * in-flight refresh.
     */
    private static void awaitJoined(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (!isJoined(thread)) {
            if (System.nanoTime() > deadline) {
                fail("the joining caller never waited for the in-flight refresh, state " + thread.getState());
            }
            Thread.onSpinWait();
        }
    }

    private static boolean isJoined(Thread thread) {
        if (thread.getState() != Thread.State.WAITING) {
            return false;
        }
        for (StackTraceElement frame : thread.getStackTrace()) {
            if (CachingDiscoveryResolver.class.getName().equals(frame.getClassName())
                    && "join".equals(frame.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    private static Caller start(Callable<ProviderMetadata> call) {
        var task = new FutureTask<>(() -> {
            try {
                return new Outcome(call.call(), null);
            }
            // cui-rewrite:disable InvalidExceptionUsageRecipe
            catch (RuntimeException e) {
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
    private record Outcome(@Nullable ProviderMetadata result, @Nullable RuntimeException failure) {
    }

    /** A delegate that always resolves the same good document and counts its calls. */
    private static final class CountingDelegate extends DiscoveryResolver {

        private final AtomicInteger calls = new AtomicInteger();

        CountingDelegate() {
            super(ClientConfiguration.builder()
                    .issuer(ISSUER)
                    .clientId("client")
                    .clientSecret("secret")
                    .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                    .build());
        }

        int calls() {
            return calls.get();
        }

        @Override
        public ProviderMetadata resolve() {
            calls.incrementAndGet();
            var metadata = new ProviderMetadata();
            metadata.issuer = ISSUER;
            metadata.tokenEndpoint = TOKEN_ENDPOINT;
            return metadata;
        }
    }
}
