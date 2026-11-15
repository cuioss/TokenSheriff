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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The hand-off of the opt-in unknown-kid fetch of {@link HttpJwksLoader} to its refresh executor, and the
 * ordering of the in-flight guard and the window claim it depends on.
 * <p>
 * The guard is claimed before the window, a lost window releases it, and every hand-off outcome other than
 * an accepted task releases it in the same method. A caller-supplied executor must run or reject every
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
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .allowLoopbackEgress(true)
                .allowInsecureHttp(true)
                .jwksUrl(uriBuilder.addPathSegment(UnknownKidJwksDispatcher.LOCAL_PATH).buildAsString())
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(3600)
                .scheduledExecutorService(executor)
                .unknownKidRefreshWindow(WINDOW)
                .build();
        HttpJwksLoader loader = new HttpJwksLoader(config, config.getEgressPolicy(), clock::get);
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

    /**
     * A single-thread scheduled executor whose {@code execute} runs, throws or silently drops the task, as
     * scripted. {@link ScheduledThreadPoolExecutor} implements {@code submit} and the scheduling methods
     * without {@code execute}, so only the unknown-kid hand-off is affected.
     */
    private static final class ScriptedExecutor extends ScheduledThreadPoolExecutor {

        enum Behaviour {
            RUN, THROW, DISCARD
        }

        private volatile Behaviour behaviour = Behaviour.RUN;

        ScriptedExecutor() {
            super(1);
        }

        @Override
        public void execute(Runnable command) {
            switch (behaviour) {
                case THROW -> throw new IllegalStateException("scripted hand-off failure");
                case DISCARD -> {
                    // accepted and silently dropped: the run-or-reject requirement is violated
                }
                case RUN -> super.execute(command);
            }
        }
    }
}
