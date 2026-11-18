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
import org.jspecify.annotations.Nullable;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The shared harness of the {@link CachingDiscoveryResolver} unit tests that drive concurrent
 * callers: a caller-thread runner, the bounded waits for a caller parked on an in-flight refresh, a
 * scripted {@link DiscoveryResolver} delegate and a {@link Hold} that keeps a call inside the cache
 * until the test releases it. No helper sleeps, and every wait is bounded by {@link #AWAIT_SECONDS}.
 */
final class CachingDiscoveryResolverSupport {

    private CachingDiscoveryResolverSupport() {
    }

    /** The issuer every scripted document names. */
    static final String ISSUER = "https://issuer.example";

    /** The bound of every wait of the harness, in seconds. */
    static final long AWAIT_SECONDS = 10;

    /**
     * Runs {@link CachingDiscoveryResolver#resolve()}, or any other call, on its own thread and hands
     * a runtime exception it throws back to the test as the outcome.
     */
    static Caller start(Callable<ProviderMetadata> call) {
        return start(call, false);
    }

    /**
     * Like {@link #start(Callable)}, but an {@link Error} the call throws is handed back as the outcome
     * too, for a test whose outcome under test is an {@code Error} the delegate throws.
     */
    static Caller startCatchingErrors(Callable<ProviderMetadata> call) {
        return start(call, true);
    }

    private static Caller start(Callable<ProviderMetadata> call, boolean catchErrors) {
        var task = new FutureTask<>(() -> {
            try {
                return new Outcome(call.call(), null, Thread.currentThread().isInterrupted());
            }
            // cui-rewrite:disable InvalidExceptionUsageRecipe
            catch (RuntimeException | Error e) {
                // A sanctioned test boundary (AGENTS.md): the caller thread funnels whatever the call
                // throws into the test's own report, because the failure under test is not known in
                // advance. An Error is reported only for the runner that asks for it; otherwise it
                // propagates and fails the task as an unexpected failure.
                if (e instanceof Error error && !catchErrors) {
                    throw error;
                }
                return new Outcome(null, e, Thread.currentThread().isInterrupted());
            }
        });
        var thread = new Thread(task, "discovery-caller");
        thread.setDaemon(true);
        thread.start();
        return new Caller(thread, task);
    }

    /** Waits until the thread parks, which a joined caller does inside its wait for the refresh. */
    static void awaitParked(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (thread.getState() != Thread.State.WAITING) {
            if (System.nanoTime() > deadline) {
                fail("the joining caller never waited for the in-flight refresh, state " + thread.getState());
            }
            Thread.onSpinWait();
        }
    }

    /**
     * Waits until the thread parks inside {@code CachingDiscoveryResolver.join}, the wait on the
     * in-flight refresh, and not in any other wait.
     */
    static void awaitJoined(Thread thread) {
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

    /** A document of {@link #ISSUER} naming the given endpoints. */
    static ProviderMetadata document(String tokenEndpoint, @Nullable String userinfoEndpoint) {
        var metadata = new ProviderMetadata();
        metadata.issuer = ISSUER;
        metadata.tokenEndpoint = tokenEndpoint;
        metadata.userinfoEndpoint = userinfoEndpoint;
        return metadata;
    }

    /** A client configuration of {@link #ISSUER}, the configuration every test delegate is built on. */
    static ClientConfiguration configuration() {
        return ClientConfiguration.builder()
                .issuer(ISSUER)
                .clientId("client")
                .clientSecret("secret")
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .build();
    }

    /** A caller running a call on its own thread. */
    record Caller(Thread thread, FutureTask<Outcome> task) {

        Outcome outcome() throws Exception {
            return task.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    /** What one caller got back, and whether its interrupt flag was set when it got it. */
    record Outcome(@Nullable ProviderMetadata result, @Nullable Throwable failure, boolean interrupted) {
    }

    /** Holds a call inside the cache until the test releases it. */
    static final class Hold {

        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        void enterAndWait() {
            entered.countDown();
            try {
                if (!release.await(AWAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new AssertionError("the test never released the held call");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while holding the call", e);
            }
        }

        void awaitEntered() throws InterruptedException {
            assertTrue(entered.await(AWAIT_SECONDS, TimeUnit.SECONDS), "the held call must be reached");
        }

        void release() {
            release.countDown();
        }
    }

    /** A delegate that answers each call with the next scripted step and counts the calls. */
    static final class ScriptedDelegate extends DiscoveryResolver {

        private final Queue<Supplier<ProviderMetadata>> script = new ConcurrentLinkedQueue<>();
        private final AtomicInteger calls = new AtomicInteger();

        ScriptedDelegate() {
            super(configuration());
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
    }
}
