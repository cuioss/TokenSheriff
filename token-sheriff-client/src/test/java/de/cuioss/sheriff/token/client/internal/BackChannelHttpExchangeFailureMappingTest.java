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
import java.net.ProtocolException;
import java.net.http.HttpResponse;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins how {@link BackChannelHttp#exchange} maps a response-side runtime failure of the
 * {@link java.net.http.HttpClient#sendAsync} future: a wrapped {@link CancellationException} becomes an
 * {@link IOException} with the fixed text of a cancelled exchange, and every other {@link RuntimeException}
 * cause becomes an {@link IOException} with the fixed text of a failed exchange and no chained cause, so every
 * endpoint client classifies it through its {@link IOException} handling and no runtime message — which can
 * carry remote content — reaches the caller.
 * <p>
 * The future is completed by the test through {@link StubHttpClient}: no JDK path that completes it with
 * either shape is known, so the mapping is defensive and the stub is what can drive it. The positive
 * controls show that the shapes the mapping must leave alone — a response, the malformed-framing forms, an
 * {@link IOException} and an {@link Error} cause, a directly cancelled future — keep their own handling.
 */
@DisplayName("BackChannelHttp exchange failure mapping")
class BackChannelHttpExchangeFailureMappingTest {

    private static final TransportException MALFORMED = new TransportException("malformed response");
    private static final Supplier<TransportException> MALFORMED_SUPPLIER = () -> MALFORMED;

    /** Stands for remote content a runtime message could carry; it must not reach the caller. */
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

    static Stream<Arguments> runtimeFailures() {
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
                Arguments.of("a CompletionException without a cause", new CompletionException(REMOTE_CONTENT, null)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("runtimeFailures")
    @DisplayName("should map a response-side runtime failure to an IOException with a fixed text and no cause")
    void shouldMapRuntimeFailureToFixedIOException(String description, RuntimeException failure) {
        CompletableFuture<HttpResponse<String>> future = failedWith(failure);

        IOException thrown = assertThrows(IOException.class, () -> exchange(future));

        assertAll(description,
                () -> assertEquals(BackChannelHttp.EXCHANGE_FAILED, thrown.getMessage(), "The text is fixed"),
                () -> assertNull(thrown.getCause(), "The runtime failure is not chained"));
    }

    static Stream<Arguments> wrappedCancellations() {
        return Stream.of(
                Arguments.of("a CancellationException wrapped in a CompletionException",
                        new CompletionException(new CancellationException(REMOTE_CONTENT))),
                Arguments.of("a CancellationException wrapped in nested CompletionExceptions",
                        new CompletionException(new CompletionException(new CancellationException(REMOTE_CONTENT)))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wrappedCancellations")
    @DisplayName("should map a future failed with a wrapped cancellation to the IOException of a cancelled exchange")
    void shouldMapWrappedCancellationToFixedIOException(String description, CompletionException failure) {
        CompletableFuture<HttpResponse<String>> future = failedWith(failure);

        IOException thrown = assertThrows(IOException.class, () -> exchange(future));

        assertAll(description,
                () -> assertEquals(BackChannelHttp.EXCHANGE_CANCELLED, thrown.getMessage(), "The text is fixed"),
                () -> assertNull(thrown.getCause(), "The cancellation is not chained"));
    }

    @Test
    @DisplayName("positive control: a directly cancelled future gives the IOException of a cancelled exchange")
    void shouldMapDirectCancellationToFixedIOException() {
        CompletableFuture<HttpResponse<String>> future = new CompletableFuture<>();
        future.cancel(true);

        IOException thrown = assertThrows(IOException.class, () -> exchange(future));

        assertAll("Direct cancellation",
                () -> assertEquals(BackChannelHttp.EXCHANGE_CANCELLED, thrown.getMessage(), "The text is fixed"),
                () -> assertInstanceOf(CancellationException.class, thrown.getCause(),
                        "The direct cancellation keeps its JDK cause"));
    }

    @Test
    @DisplayName("positive control: a completed future returns its response")
    void shouldReturnResponseOfCompletedFuture() throws Exception {
        HttpResponse<String> response = new StubHttpClient.StubResponse();

        assertSame(response, exchange(CompletableFuture.completedFuture(response)),
                "The response the client produced reaches the caller");
    }

    static Stream<Arguments> malformedFramingForms() {
        ProtocolException parser = new ProtocolException("invalid Content-Length");
        parser.initCause(new NumberFormatException(REMOTE_CONTENT));
        ProtocolException wrapped = new ProtocolException("invalid Content-Length");
        wrapped.initCause(parser);
        return Stream.of(
                Arguments.of("the NumberFormatException the future fails with",
                        new NumberFormatException(REMOTE_CONTENT)),
                Arguments.of("an IllegalArgumentException caused by a NumberFormatException",
                        new IllegalArgumentException(REMOTE_CONTENT, new NumberFormatException(REMOTE_CONTENT))),
                Arguments.of("an IllegalArgumentException caused by a NumberFormatException, wrapped",
                        new CompletionException(new IllegalArgumentException(REMOTE_CONTENT,
                                new NumberFormatException(REMOTE_CONTENT)))),
                Arguments.of("the ProtocolException chain of JDK 27", wrapped));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedFramingForms")
    @DisplayName("positive control: a malformed-framing failure is replaced by the supplied exception")
    void shouldReplaceMalformedFramingBySuppliedException(String description, Throwable failure) {
        CompletableFuture<HttpResponse<String>> future = failedWith(failure);

        TransportException thrown = assertThrows(TransportException.class, () -> exchange(future));

        assertAll(description,
                () -> assertSame(MALFORMED, thrown, "The supplied exception replaces the JDK's"),
                () -> assertNull(thrown.getCause(), "The JDK's exception is not chained"));
    }

    static Stream<Arguments> failuresRethrownAsIs() {
        return Stream.of(
                Arguments.of("an IOException", new IOException("connection reset")),
                Arguments.of("an Error", new Error("client error")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failuresRethrownAsIs")
    @DisplayName("positive control: an IOException or Error cause is rethrown as the client reported it")
    void shouldRethrowIoExceptionAndErrorAsIs(String description, Throwable failure) {
        CompletableFuture<HttpResponse<String>> future = failedWith(failure);

        Throwable thrown = assertThrows(Throwable.class, () -> exchange(future));

        assertSame(failure, thrown, description + " reaches the caller as the client reported it");
    }
}
