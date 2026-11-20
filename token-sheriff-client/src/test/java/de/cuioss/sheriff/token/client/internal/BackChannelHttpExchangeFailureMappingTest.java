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
package de.cuioss.sheriff.token.client.internal;

import de.cuioss.sheriff.token.commons.error.TransportException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins how {@link BackChannelHttp#exchange} maps a failure of the {@link java.net.http.HttpClient#sendAsync}
 * future that is not one of the shapes {@link BackChannelHttpExchangeTest} holds as positive controls (a
 * response, the malformed-framing forms, an {@link IOException} and an {@link Error} cause, a directly
 * cancelled future):
 * <ul>
 *   <li>a wrapped {@link CancellationException} becomes an {@link IOException} with the fixed text of a
 *       cancelled exchange;</li>
 *   <li>every other cause, a {@link RuntimeException} or a checked cause that is no {@link IOException},
 *       becomes an {@link IOException} with the fixed text of a failed exchange and no chained cause, so no
 *       failure message — which can carry remote content — reaches the caller;</li>
 *   <li>a nested {@link ExecutionException} is unwrapped like a {@link CompletionException}, so a
 *       cancellation, a malformed {@code Content-Length} or an {@link IOException} below it keeps its own
 *       mapping;</li>
 *   <li>the unwrap is bounded by {@link BackChannelHttp#MAX_CAUSE_DEPTH}: a wrapper chain deeper than the
 *       bound ends as the fixed failed-exchange text.</li>
 * </ul>
 * The future is completed by the test through {@link StubHttpClient}: no JDK path that completes it with any
 * of these shapes is known, so the mappings are defensive and the stub is what can drive them.
 */
@DisplayName("BackChannelHttp exchange failure mapping")
class BackChannelHttpExchangeFailureMappingTest {

    private static final TransportException MALFORMED = new TransportException("malformed response");
    private static final Supplier<TransportException> MALFORMED_SUPPLIER = () -> MALFORMED;

    /** Stands for remote content a failure message could carry; it must not reach the caller. */
    private static final String REMOTE_CONTENT = "remote-content-from-the-response";

    private static HttpResponse<String> exchange(CompletableFuture<HttpResponse<String>> future)
            throws IOException, InterruptedException {
        return BackChannelHttp.exchange(new StubHttpClient(future), StubHttpClient.REQUEST,
                HttpResponse.BodyHandlers.ofString(), MALFORMED_SUPPLIER);
    }

    private static CompletableFuture<HttpResponse<String>> failedWith(Throwable cause) {
        CompletableFuture<HttpResponse<String>> future = new CompletableFuture<>();
        future.completeExceptionally(cause);
        return future;
    }

    /**
     * Wraps {@code innermost} in {@code depth} {@link ExecutionException}s. {@link ExecutionException} is used
     * rather than {@link CompletionException} because {@link CompletableFuture#get()} strips one outer
     * {@link CompletionException} itself, which would shift the depth the mapping sees by one.
     */
    private static Throwable wrappedIn(int depth, Throwable innermost) {
        Throwable failure = innermost;
        for (int level = 0; level < depth; level++) {
            failure = new ExecutionException(failure);
        }
        return failure;
    }

    static Stream<Arguments> fixedFailures() {
        return Stream.of(
                Arguments.of("an IllegalStateException", new IllegalStateException(REMOTE_CONTENT)),
                Arguments.of("an IllegalArgumentException with a cause that is no NumberFormatException",
                        new IllegalArgumentException(REMOTE_CONTENT, new IOException(REMOTE_CONTENT))),
                Arguments.of("an IllegalArgumentException without a cause",
                        new IllegalArgumentException(REMOTE_CONTENT)),
                Arguments.of("an IllegalStateException wrapped in a CompletionException",
                        new CompletionException(new IllegalStateException(REMOTE_CONTENT))),
                Arguments.of("an IllegalArgumentException wrapped in nested CompletionExceptions",
                        new CompletionException(new CompletionException(new IllegalArgumentException(REMOTE_CONTENT)))),
                Arguments.of("a CompletionException without a cause", new CompletionException(REMOTE_CONTENT, null)),
                Arguments.of("a checked cause that is no IOException", new TimeoutException(REMOTE_CONTENT)),
                Arguments.of("a checked cause below a nested ExecutionException",
                        new ExecutionException(REMOTE_CONTENT, new TimeoutException(REMOTE_CONTENT))),
                Arguments.of("an IOException below a wrapper chain deeper than the unwrap bound",
                        wrappedIn(BackChannelHttp.MAX_CAUSE_DEPTH + 1, new IOException(REMOTE_CONTENT))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixedFailures")
    @DisplayName("should map a checked, runtime or over-deep failure to an IOException with a fixed text and no cause")
    void shouldMapFailureToFixedIOException(String description, Throwable failure) {
        CompletableFuture<HttpResponse<String>> future = failedWith(failure);

        IOException thrown = assertThrows(IOException.class, () -> exchange(future));

        assertAll(description,
                () -> assertEquals(BackChannelHttp.EXCHANGE_FAILED, thrown.getMessage(), "The text is fixed"),
                () -> assertNull(thrown.getCause(), "The failure is not chained"));
    }

    static Stream<Arguments> wrappedCancellations() {
        return Stream.of(
                Arguments.of("a CancellationException wrapped in a CompletionException",
                        new CompletionException(new CancellationException(REMOTE_CONTENT))),
                Arguments.of("a CancellationException wrapped in nested CompletionExceptions",
                        new CompletionException(new CompletionException(new CancellationException(REMOTE_CONTENT)))),
                Arguments.of("a CancellationException below a nested ExecutionException",
                        new ExecutionException(new CancellationException(REMOTE_CONTENT))),
                Arguments.of("a CancellationException below CompletionException(ExecutionException(...))",
                        new CompletionException(new ExecutionException(new CancellationException(REMOTE_CONTENT)))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wrappedCancellations")
    @DisplayName("should map a future failed with a wrapped cancellation to the IOException of a cancelled exchange")
    void shouldMapWrappedCancellationToFixedIOException(String description, Throwable failure) {
        CompletableFuture<HttpResponse<String>> future = failedWith(failure);

        IOException thrown = assertThrows(IOException.class, () -> exchange(future));

        assertAll(description,
                () -> assertEquals(BackChannelHttp.EXCHANGE_CANCELLED, thrown.getMessage(), "The text is fixed"),
                () -> assertNull(thrown.getCause(), "The cancellation is not chained"));
    }

    static Stream<Arguments> nestedMalformedFraming() {
        return Stream.of(
                Arguments.of("an IllegalArgumentException caused by a NumberFormatException below an ExecutionException",
                        new ExecutionException(new IllegalArgumentException(REMOTE_CONTENT,
                                new NumberFormatException(REMOTE_CONTENT)))),
                Arguments.of("the same form below CompletionException(ExecutionException(...))",
                        new CompletionException(new ExecutionException(new IllegalArgumentException(REMOTE_CONTENT,
                                new NumberFormatException(REMOTE_CONTENT))))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nestedMalformedFraming")
    @DisplayName("should replace a malformed-framing failure below a nested ExecutionException by the supplied exception")
    void shouldReplaceNestedMalformedFramingBySuppliedException(String description, Throwable failure) {
        CompletableFuture<HttpResponse<String>> future = failedWith(failure);

        TransportException thrown = assertThrows(TransportException.class, () -> exchange(future));

        assertAll(description,
                () -> assertSame(MALFORMED, thrown, "The supplied exception replaces the JDK's"),
                () -> assertNull(thrown.getCause(), "The JDK's exception is not chained"));
    }

    @Test
    @DisplayName("should rethrow an IOException below a nested ExecutionException as the client reported it")
    void shouldRethrowIoExceptionBelowNestedExecutionException() {
        IOException cause = new IOException("connection reset");
        CompletableFuture<HttpResponse<String>> future = failedWith(new ExecutionException(cause));

        IOException thrown = assertThrows(IOException.class, () -> exchange(future));

        assertSame(cause, thrown, "The IOException below the wrapper reaches the caller, not a fixed text");
    }

    @Test
    @DisplayName("positive control: an IOException below a wrapper chain as deep as the unwrap bound is unwrapped")
    void shouldUnwrapChainAtTheBound() {
        IOException cause = new IOException("connection reset");
        CompletableFuture<HttpResponse<String>> future =
                failedWith(wrappedIn(BackChannelHttp.MAX_CAUSE_DEPTH, cause));

        IOException thrown = assertThrows(IOException.class, () -> exchange(future));

        assertSame(cause, thrown, "A chain within the bound reaches its innermost cause");
    }
}
