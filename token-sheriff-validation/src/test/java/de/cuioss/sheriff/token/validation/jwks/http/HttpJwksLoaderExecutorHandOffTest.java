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
package de.cuioss.sheriff.token.validation.jwks.http;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.validation.JWTValidationLogMessages;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.Serial;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The hand-off of the opt-in unknown-kid fetch of {@link HttpJwksLoader} to its refresh executor, and the
 * ordering of the in-flight guard and the window claim it depends on.
 * <p>
 * A miss inside a claimed window returns before it touches the guard. Otherwise the guard is claimed before
 * the window, a lost window releases it, and every hand-off outcome other than an accepted task releases it
 * in the same method, an {@link Error} from {@code execute} included, which still reaches the caller; a
 * failed hand-off keeps the window claimed, so it burns it. A caller-supplied executor must run or reject every
 * task it accepts; the last case pins what happens when it does not, as the documented limit of that
 * requirement. The executor used here runs, throws from or silently drops {@code execute} calls on
 * demand; its scheduled tasks and {@code submit} calls are unaffected, so the periodic refresh and the
 * drain are never scripted.
 */
@EnableTestLogger
@EnableMockWebServer
@DisplayName("HttpJwksLoader unknown-kid executor hand-off")
class HttpJwksLoaderExecutorHandOffTest {

    private static final String ROTATED_KID = UnknownKidJwksDispatcher.ROTATED_KEY_ID;
    private static final Duration WINDOW = Duration.ofSeconds(30);

    @Getter
    private final UnknownKidJwksDispatcher moduleDispatcher = new UnknownKidJwksDispatcher();

    private final AtomicLong clock = new AtomicLong(1_000L);
    private ScriptedExecutor executor;

    @BeforeEach
    void setUp() {
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.DEFAULT_KEY);
        executor = new ScriptedExecutor();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private HttpJwksLoader initializedLoader(URIBuilder uriBuilder) {
        return initializedLoader(uriBuilder, clock::get);
    }

    private HttpJwksLoader initializedLoader(URIBuilder uriBuilder, LongSupplier monotonicClock) {
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .allowLoopbackEgress(true)
                .allowInsecureHttp(true)
                .jwksUrl(uriBuilder.addPathSegment(UnknownKidJwksDispatcher.LOCAL_PATH).buildAsString())
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(3600)
                .scheduledExecutorService(executor)
                .unknownKidRefreshWindow(WINDOW)
                .build();
        HttpJwksLoader loader = new HttpJwksLoader(config, config.getEgressPolicy(), monotonicClock);
        assertEquals(LoaderStatus.OK, loader.initJWKSLoader(new SecurityEventCounter()).join(),
                "the initial load must install the default key set");
        return loader;
    }

    private void drainExecutor() throws Exception {
        executor.submit(() -> {
        }).get(30, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("An executor failing with a non-rejection RuntimeException releases the guard and throws nothing on the caller")
    void shouldReleaseGuardWhenHandOffFails(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(uriBuilder);
        executor.behaviour = ScriptedExecutor.Behaviour.THROW;

        assertDoesNotThrow(() -> assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty()),
                "the failed hand-off surfaces nothing on the calling thread");
        int afterFailure = moduleDispatcher.getRequestCount();
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO,
                JWTValidationLogMessages.INFO.UNKNOWN_KID_REFRESH_SCHEDULED.resolveIdentifierString());
        loader.performBackgroundRefresh();
        int afterPeriodic = moduleDispatcher.getRequestCount();

        executor.behaviour = ScriptedExecutor.Behaviour.RUN;
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.ROTATED_KEY);
        clock.addAndGet(WINDOW.toNanos());
        assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty());
        drainExecutor();

        assertAll("the guard was released by the failed hand-off",
                () -> assertEquals(1, afterFailure, "the failed hand-off fetched nothing"),
                () -> assertEquals(2, afterPeriodic, "a later periodic cycle fetches"),
                () -> assertEquals(3, moduleDispatcher.getRequestCount(), "a later claim fetches"),
                () -> assertTrue(loader.getKeyInfo(ROTATED_KID).isPresent(), "the later fetch installed the key"));
        loader.close();
    }

    @Test
    @DisplayName("An Error thrown by the executor's execute reaches the caller and releases the guard")
    void shouldReleaseGuardAndPropagateWhenHandOffThrowsError(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(uriBuilder);
        executor.behaviour = ScriptedExecutor.Behaviour.ERROR;

        ScriptedHandOffError thrown = assertThrows(ScriptedHandOffError.class,
                () -> loader.getKeyInfo(ROTATED_KID), "the Error of the hand-off reaches the caller");
        int afterError = moduleDispatcher.getRequestCount();
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO,
                JWTValidationLogMessages.INFO.UNKNOWN_KID_REFRESH_SCHEDULED.resolveIdentifierString());
        loader.performBackgroundRefresh();
        int afterPeriodic = moduleDispatcher.getRequestCount();

        executor.behaviour = ScriptedExecutor.Behaviour.RUN;
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.ROTATED_KEY);
        clock.addAndGet(WINDOW.toNanos());
        assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty());
        drainExecutor();

        assertAll("the guard was released by the hand-off that threw an Error",
                () -> assertEquals(ScriptedHandOffError.MESSAGE, thrown.getMessage(), "the executor's own Error"),
                () -> assertEquals(1, afterError, "the hand-off that threw fetched nothing"),
                () -> assertEquals(2, afterPeriodic, "the next periodic cycle fetches"),
                () -> assertEquals(3, moduleDispatcher.getRequestCount(), "a miss in the next window schedules a fetch"),
                () -> assertTrue(loader.getKeyInfo(ROTATED_KID).isPresent(), "the later fetch installed the key"));
        loader.close();
    }

    @Test
    @DisplayName("A miss while the guard is held leaves the window unclaimed; a miss with a free guard claims it")
    void shouldNotBurnWindowWhileGuardIsHeld(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(uriBuilder);
        CountDownLatch arrived = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        moduleDispatcher.holdResponses(arrived, release);
        CompletableFuture<Void> periodic = CompletableFuture.runAsync(loader::performBackgroundRefresh);
        assertTrue(arrived.await(10, TimeUnit.SECONDS), "the periodic fetch holds the guard");

        assertTrue(loader.getKeyInfo("invented-while-held").isEmpty());
        release.countDown();
        periodic.get(30, TimeUnit.SECONDS);
        drainExecutor();
        int afterHeldMiss = moduleDispatcher.getRequestCount();

        assertTrue(loader.getKeyInfo("invented-with-free-guard").isEmpty());
        drainExecutor();
        int afterFreeMiss = moduleDispatcher.getRequestCount();

        assertTrue(loader.getKeyInfo("invented-within-claimed-window").isEmpty());
        drainExecutor();

        assertAll("the window is claimed only by a miss that also holds the guard",
                () -> assertEquals(2, afterHeldMiss, "the miss while held scheduled no fetch"),
                () -> assertEquals(3, afterFreeMiss, "the held guard left the window unclaimed, so the same clock fetches"),
                () -> assertEquals(3, moduleDispatcher.getRequestCount(),
                        "positive control: the miss with a free guard consumed the window"),
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()));
        loader.close();
    }

    @Test
    @DisplayName("Documented limit: an executor that silently discards an accepted task leaves the guard held")
    void shouldKeepGuardHeldWhenExecutorDiscardsAcceptedTask(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(uriBuilder);
        executor.behaviour = ScriptedExecutor.Behaviour.DISCARD;

        assertTrue(loader.getKeyInfo("invented-discarded").isEmpty());
        executor.behaviour = ScriptedExecutor.Behaviour.RUN;
        clock.addAndGet(WINDOW.toNanos());
        assertTrue(loader.getKeyInfo("invented-after-window").isEmpty());
        drainExecutor();
        int afterLaterMiss = moduleDispatcher.getRequestCount();
        loader.performBackgroundRefresh();

        assertAll("a run-or-reject violation by the executor is not recovered by the loader",
                () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                        JWTValidationLogMessages.INFO.UNKNOWN_KID_REFRESH_SCHEDULED.resolveIdentifierString()),
                () -> assertEquals(1, afterLaterMiss, "no unknown-kid fetch is scheduled while the guard is held"),
                () -> assertEquals(1, moduleDispatcher.getRequestCount(), "the periodic cycle skips its fetch"),
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus(), "the installed keys stay usable"));
        loader.close();
    }

    @Test
    @DisplayName("A miss inside a claimed window leaves the guard free; the claim path still holds it")
    void shouldLeaveGuardFreeForMissInsideClaimedWindow(URIBuilder uriBuilder) throws Exception {
        GatedClock gatedClock = new GatedClock();
        HttpJwksLoader loader = initializedLoader(uriBuilder, gatedClock);
        assertTrue(loader.getKeyInfo("invented-claiming").isEmpty());
        drainExecutor();
        int afterClaim = moduleDispatcher.getRequestCount();

        GatedRead insideWindowMiss = gatedClock.gateNextReadOf(
                () -> assertTrue(loader.getKeyInfo("invented-inside-window").isEmpty()));
        assertTrue(gatedClock.arrived.await(10, TimeUnit.SECONDS), "the miss inside the window reads the clock");
        loader.performBackgroundRefresh();
        int afterPeriodicDuringMiss = moduleDispatcher.getRequestCount();
        gatedClock.release.countDown();
        insideWindowMiss.thread().join(TimeUnit.SECONDS.toMillis(30));
        assertFalse(insideWindowMiss.thread().isAlive(), "the miss inside the window returned");
        insideWindowMiss.rethrowFailure();
        drainExecutor();
        int afterInsideWindowMiss = moduleDispatcher.getRequestCount();

        clock.addAndGet(WINDOW.toNanos());
        CountDownLatch arrived = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        moduleDispatcher.holdResponses(arrived, release);
        assertTrue(loader.getKeyInfo("invented-after-window").isEmpty());
        assertTrue(arrived.await(10, TimeUnit.SECONDS), "the claimed fetch reaches the endpoint");
        loader.performBackgroundRefresh();
        int afterPeriodicDuringClaimedFetch = moduleDispatcher.getRequestCount();
        release.countDown();
        drainExecutor();

        assertAll("only a miss that claims the window holds the guard",
                () -> assertEquals(2, afterClaim, "the first miss claimed the window and fetched"),
                () -> assertEquals(3, afterPeriodicDuringMiss,
                        "a periodic cycle started while the miss inside the window runs still fetches"),
                () -> assertEquals(3, afterInsideWindowMiss, "the miss inside the window fetched nothing"),
                () -> assertEquals(4, afterPeriodicDuringClaimedFetch,
                        "negative control: a periodic cycle skips its fetch while a claimed fetch holds the guard"),
                () -> assertEquals(4, moduleDispatcher.getRequestCount()),
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()));
        loader.close();
    }

    @Test
    @DisplayName("A rejected hand-off burns its window: a second miss in it fetches nothing, a miss after it fetches")
    void shouldBurnWindowOnRejectedHandOff(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(uriBuilder);
        executor.behaviour = ScriptedExecutor.Behaviour.REJECT;

        assertDoesNotThrow(() -> assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty()),
                "the rejected hand-off surfaces nothing on the calling thread");
        executor.behaviour = ScriptedExecutor.Behaviour.RUN;
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.ROTATED_KEY);
        assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty());
        drainExecutor();
        int afterSecondMiss = moduleDispatcher.getRequestCount();

        clock.addAndGet(WINDOW.toNanos());
        assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty());
        drainExecutor();

        assertAll("the rejected hand-off consumed its window",
                () -> assertEquals(1, afterSecondMiss, "a second miss in the burnt window fetched nothing"),
                () -> assertEquals(2, moduleDispatcher.getRequestCount(), "a miss in the next window fetches"),
                () -> assertTrue(loader.getKeyInfo(ROTATED_KID).isPresent(), "the next window's fetch installed the key"));
        loader.close();
    }

    /**
     * One action run on its own thread by {@link GatedClock#gateNextReadOf(Runnable)}. The action runs
     * inside {@link #outcome}, which records anything it throws - its own assertion, or the release timeout
     * of the gated clock read - so the test thread can rethrow it instead of losing it with the thread.
     *
     * @param thread  the thread running the action, for a bounded {@code join} and an {@code isAlive} check
     * @param outcome the action's result, holding any {@link Throwable} it threw
     */
    private record GatedRead(Thread thread, FutureTask<Void> outcome) {

        /**
         * Rethrows on the calling thread whatever the finished action threw, keeping its own message.
         * Call only after {@link #thread()} has terminated.
         */
        void rethrowFailure() throws InterruptedException {
            try {
                outcome.get(1, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                throw new AssertionError("the gated worker failed: " + cause.getMessage(), cause);
            } catch (TimeoutException e) {
                throw new AssertionError("the gated worker left no outcome", e);
            }
        }
    }

    /**
     * The test clock, read through {@link #clock}, that can hold back the next read made by one chosen
     * thread until {@link #release} is counted down, counting {@link #arrived} down when that read starts.
     * Reads by every other thread, and every later read, return at once.
     */
    private final class GatedClock implements LongSupplier {

        private final CountDownLatch arrived = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile Thread gatedThread;

        GatedRead gateNextReadOf(Runnable action) {
            FutureTask<Void> outcome = new FutureTask<>(action, null);
            Thread thread = new Thread(outcome, "gated-clock-reader");
            gatedThread = thread;
            thread.start();
            return new GatedRead(thread, outcome);
        }

        @Override
        public long getAsLong() {
            if (Thread.currentThread() == gatedThread) {
                gatedThread = null;
                arrived.countDown();
                try {
                    assertTrue(release.await(30, TimeUnit.SECONDS), "the gated clock read was not released");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while the clock read was gated", e);
                }
            }
            return clock.get();
        }
    }

    /**
     * The {@link Error} the scripted executor throws from {@code execute}; a type of its own, so the test
     * cannot mistake another error for it.
     */
    private static final class ScriptedHandOffError extends Error {

        @Serial
        private static final long serialVersionUID = 1L;
        static final String MESSAGE = "scripted hand-off error";

        ScriptedHandOffError() {
            super(MESSAGE);
        }
    }

    /**
     * A single-thread scheduled executor whose {@code execute} runs, throws or silently drops the task, as
     * scripted. {@link ScheduledThreadPoolExecutor} implements {@code submit} and the scheduling methods
     * without {@code execute}, so only the unknown-kid hand-off is affected.
     */
    private static final class ScriptedExecutor extends ScheduledThreadPoolExecutor {

        enum Behaviour {
            RUN, THROW, ERROR, REJECT, DISCARD
        }

        private volatile Behaviour behaviour = Behaviour.RUN;

        ScriptedExecutor() {
            super(1);
        }

        @Override
        public void execute(Runnable command) {
            switch (behaviour) {
                case THROW -> throw new IllegalStateException("scripted hand-off failure");
                case ERROR -> throw new ScriptedHandOffError();
                case REJECT -> throw new RejectedExecutionException("scripted hand-off rejection");
                case DISCARD -> {
                    // accepted and silently dropped: the run-or-reject requirement is violated
                }
                case RUN -> super.execute(command);
            }
        }
    }
}
