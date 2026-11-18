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
import de.cuioss.sheriff.token.client.discovery.CachingDiscoveryResolverSupport.Outcome;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static de.cuioss.sheriff.token.client.discovery.CachingDiscoveryResolverSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A throwing {@code afterClaim} hook aborts the refresh like any other abort: the exception reaches
 * the caller that claimed the slot, no state is written, the claim is released, and a caller that
 * joined the claim re-evaluates instead of waiting forever. A hook that publishes a newer entry
 * aborts the claim as a superseded read, and a joined caller is never answered from that entry
 * unless it is still fresh. The delegate is a scripted
 * {@link DiscoveryResolver} subclass and time is driven by an injected {@link InstantSource}; no case
 * sleeps, and every wait is bounded.
 */
@EnableTestLogger
@DisplayName("CachingDiscoveryResolver releases the refresh claim when the afterClaim hook throws")
class CachingDiscoveryResolverHookFailureTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration GRACE = Duration.ofMinutes(10);
    private static final String TOKEN_ENDPOINT = ISSUER + "/token";
    private static final String COMPETING_TOKEN_ENDPOINT = ISSUER + "/competing-token";

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
        var held = new Hold();
        onNextClaim.set(() -> {
            held.enterAndWait();
            throw failure;
        });

        var winner = start(resolver::resolve);
        held.awaitEntered();
        var joiner = start(resolver::resolve);
        awaitJoined(joiner.thread());
        held.release();

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
        var held = new Hold();
        onNextClaim.set(held::enterAndWait);

        var winner = start(resolver::resolve);
        held.awaitEntered();
        var joiner = start(resolver::resolve);
        awaitJoined(joiner.thread());
        held.release();

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

    /**
     * Pins the superseded read: once a joiner is parked on the claim, the hook publishes an already
     * expired entry between the winner's read and its re-read, so the winner aborts its claim. The
     * joined caller must re-evaluate and be
     * answered by a refresh, never by the expired entry the superseded claim found. Before the fix the
     * claim was completed with that entry and the joiner returned its competing token endpoint.
     */
    @Test
    @DisplayName("Should not answer a joined caller from the expired entry a superseded claim found")
    void shouldNotAnswerJoinerFromExpiredSupersedingEntry() throws Exception {
        var competitor = new CachingDiscoveryResolver(new CountingDelegate(COMPETING_TOKEN_ENDPOINT), TTL, GRACE,
                clock);
        competitor.resolve();
        resolver.resolve();
        advance(TTL);
        int callsBefore = delegate.calls();

        var outcomes = runSupersededClaim(competitor);

        assertAll("joiner re-evaluates instead of serving the expired entry",
                () -> assertNull(outcomes[0].failure(), "the winner does not fail"),
                () -> assertNull(outcomes[1].failure(), "the joiner does not fail"),
                () -> assertEquals(TOKEN_ENDPOINT, outcomes[0].result().tokenEndpoint,
                        "the winner is answered by the refresh"),
                () -> assertEquals(TOKEN_ENDPOINT, outcomes[1].result().tokenEndpoint,
                        "the joiner is answered by the refresh, not by the expired competing entry"),
                () -> assertEquals(1, delegate.calls() - callsBefore, "exactly one refresh reached the delegate"),
                () -> assertEquals(DiscoveryCacheStatus.State.RESOLVED, resolver.status().state()),
                () -> assertEquals(START.plus(TTL), resolver.status().lastSuccess(),
                        "the refresh ran at the advanced clock"));
    }

    @Test
    @DisplayName("Control: should answer a joined caller from a fresh entry a superseded claim found, without a refresh")
    void shouldAnswerJoinerFromFreshSupersedingEntryWithoutRefresh() throws Exception {
        resolver.resolve();
        advance(TTL);
        var competitor = new CachingDiscoveryResolver(new CountingDelegate(COMPETING_TOKEN_ENDPOINT), TTL, GRACE,
                clock);
        competitor.resolve();
        int callsBefore = delegate.calls();

        var outcomes = runSupersededClaim(competitor);

        assertAll("a fresh superseding entry is served without a refresh",
                () -> assertNull(outcomes[0].failure(), "the winner does not fail"),
                () -> assertNull(outcomes[1].failure(), "the joiner does not fail"),
                () -> assertEquals(COMPETING_TOKEN_ENDPOINT, outcomes[0].result().tokenEndpoint,
                        "the winner is answered from the fresh competing entry"),
                () -> assertEquals(COMPETING_TOKEN_ENDPOINT, outcomes[1].result().tokenEndpoint,
                        "the joiner is answered from the fresh competing entry"),
                () -> assertEquals(0, delegate.calls() - callsBefore, "no refresh reached the delegate"),
                () -> assertEquals(START.plus(TTL), resolver.status().lastSuccess(),
                        "lastSuccess is the competing entry's resolution"));
    }

    /**
     * Runs a winner whose claim hook holds the claim until a joiner, which read the same stale entry,
     * is parked on it, and then publishes the competitor's entry before the winner re-reads the state.
     *
     * @return the winner's outcome at index 0 and the joiner's at index 1
     */
    private Outcome[] runSupersededClaim(CachingDiscoveryResolver competitor) throws Exception {
        var held = new Hold();
        onNextClaim.set(() -> {
            held.enterAndWait();
            stateOf(resolver).set(stateOf(competitor).get());
        });

        var winner = start(resolver::resolve);
        held.awaitEntered();
        var joiner = start(resolver::resolve);
        awaitJoined(joiner.thread());
        held.release();

        return new Outcome[]{winner.outcome(), joiner.outcome()};
    }

    /**
     * Reads the cache's private state holder, so a hook can publish another cache's entry into it.
     */
    @SuppressWarnings("unchecked")
    private static AtomicReference<Object> stateOf(CachingDiscoveryResolver cache) {
        try {
            Field field = CachingDiscoveryResolver.class.getDeclaredField("state");
            field.setAccessible(true);
            return (AtomicReference<Object>) field.get(cache);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("CachingDiscoveryResolver must hold its entry in a field named 'state'", e);
        }
    }

    private void advance(Duration duration) {
        now.set(now.get().plus(duration));
    }

    /** A delegate that always resolves the same good document and counts its calls. */
    private static final class CountingDelegate extends DiscoveryResolver {

        private final AtomicInteger calls = new AtomicInteger();
        private final String tokenEndpoint;

        CountingDelegate() {
            this(TOKEN_ENDPOINT);
        }

        /** A delegate whose document names {@code tokenEndpoint}, so its entries are recognisable. */
        CountingDelegate(String tokenEndpoint) {
            super(configuration());
            this.tokenEndpoint = tokenEndpoint;
        }

        int calls() {
            return calls.get();
        }

        @Override
        public ProviderMetadata resolve() {
            calls.incrementAndGet();
            return document(tokenEndpoint, null);
        }
    }
}
