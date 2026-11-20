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

import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.sheriff.token.client.auth.ClientSecretBasicAuth;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.DiscoveryResolver;
import de.cuioss.sheriff.token.client.flow.ParClient;
import de.cuioss.sheriff.token.client.flow.TokenEndpointClient;
import de.cuioss.sheriff.token.client.lifecycle.RevocationClient;
import de.cuioss.sheriff.token.client.token.UserInfoClient;
import de.cuioss.sheriff.token.commons.error.TransportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ProtocolException;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests every branch of {@link BackChannelHttp#exchange}: the wait on the {@link HttpClient#sendAsync} future
 * and the mapping of the way that future ends.
 * <p>
 * The branch tests drive {@code exchange} through {@link StubHttpClient}, whose {@code sendAsync} returns a
 * future the test prepared, so each outcome — a response, a failure cause, a cancellation, an incomplete future
 * — is reached without a socket. The mapping of a response-side runtime failure and of a wrapped cancellation
 * is pinned by {@link BackChannelHttpExchangeFailureMappingTest}. The request-timeout tests use a loopback server socket that accepts the
 * connection in its backlog and never sends response headers, so the wait of every endpoint client for the
 * headers ends only through the request's timeout. They cover the headers phase only; whether the request
 * timeout also bounds the body read that follows the headers depends on the JDK and is observed by
 * {@link BackChannelHttpBodyPhaseTest}.
 */
@DisplayName("BackChannelHttp exchange")
class BackChannelHttpExchangeTest {

    private static final HttpRequest REQUEST = StubHttpClient.REQUEST;
    private static final TransportException MALFORMED = new TransportException("malformed response");
    private static final Supplier<TransportException> MALFORMED_SUPPLIER = () -> MALFORMED;
    private static final int READ_TIMEOUT_SECONDS = 1;

    private ServerSocket silentServer;

    @AfterEach
    void tearDown() throws IOException {
        if (silentServer != null) {
            silentServer.close();
        }
    }

    private static HttpResponse<String> exchange(CompletableFuture<HttpResponse<String>> future)
            throws IOException, InterruptedException {
        return BackChannelHttp.exchange(new StubHttpClient(future), REQUEST, HttpResponse.BodyHandlers.ofString(),
                MALFORMED_SUPPLIER);
    }

    private static CompletableFuture<HttpResponse<String>> failedWith(Throwable cause) {
        CompletableFuture<HttpResponse<String>> future = new CompletableFuture<>();
        future.completeExceptionally(cause);
        return future;
    }

    @Test
    @DisplayName("positive control: a completed future returns its response")
    void shouldReturnResponseOfCompletedFuture() throws Exception {
        HttpResponse<String> response = new StubHttpClient.StubResponse();

        assertSame(response, exchange(CompletableFuture.completedFuture(response)),
                "The response the client produced reaches the caller");
    }

    static Stream<Arguments> failuresRethrownAsIs() {
        return Stream.of(
                Arguments.of("an IOException", new IOException("connection reset")),
                Arguments.of("an Error", new Error("client error")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failuresRethrownAsIs")
    @DisplayName("should rethrow a failure of the future as the client reported it")
    void shouldRethrowFailureAsIs(String description, Throwable cause) {
        CompletableFuture<HttpResponse<String>> future = failedWith(cause);

        Throwable thrown = assertThrows(Throwable.class, () -> exchange(future));

        assertAll(description,
                () -> assertSame(cause, thrown, "The failure reaches the caller as the client reported it"),
                () -> assertSame(cause.getCause(), thrown.getCause(), "Its cause is intact"));
    }

    @Test
    @DisplayName("should unwrap nested CompletionExceptions before mapping the cause")
    void shouldUnwrapNestedCompletionExceptions() {
        IOException cause = new IOException("connection reset");
        CompletableFuture<HttpResponse<String>> future =
                failedWith(new CompletionException(new CompletionException(cause)));

        IOException thrown = assertThrows(IOException.class, () -> exchange(future));

        assertSame(cause, thrown, "The innermost cause reaches the caller, not a CompletionException");
    }

    @Test
    @DisplayName("should wrap a checked cause that is not an IOException in an IOException with a fixed text")
    void shouldWrapCheckedCauseInIOException() {
        TimeoutException cause = new TimeoutException("checked failure");
        CompletableFuture<HttpResponse<String>> future = failedWith(cause);

        IOException thrown = assertThrows(IOException.class, () -> exchange(future));

        assertAll("Wrapped checked cause",
                () -> assertEquals(BackChannelHttp.EXCHANGE_FAILED, thrown.getMessage(), "The text is fixed"),
                () -> assertSame(cause, thrown.getCause(), "The checked cause is chained"));
    }

    static Stream<Arguments> malformedFramingForms() {
        ProtocolException parser = new ProtocolException("invalid Content-Length");
        parser.initCause(new NumberFormatException("12raw34"));
        ProtocolException wrapped = new ProtocolException("invalid Content-Length");
        wrapped.initCause(parser);
        return Stream.of(
                Arguments.of("the NumberFormatException the future fails with", new NumberFormatException("12raw34")),
                Arguments.of("an IllegalArgumentException caused by a NumberFormatException",
                        new IllegalArgumentException("12raw34", new NumberFormatException("12raw34"))),
                Arguments.of("the ProtocolException chain of JDK 27", wrapped));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedFramingForms")
    @DisplayName("should replace a malformed-framing failure by the supplied exception")
    void shouldReplaceMalformedFramingFailure(String description, Throwable cause) {
        CompletableFuture<HttpResponse<String>> future = failedWith(cause);

        TransportException thrown = assertThrows(TransportException.class, () -> exchange(future));

        assertAll(description,
                () -> assertSame(MALFORMED, thrown, "The supplied exception replaces the JDK's"),
                () -> assertNull(thrown.getCause(), "The JDK's exception is not chained"));
    }

    @Test
    @DisplayName("should map a cancelled wait to an IOException with a fixed text")
    void shouldMapCancellationToIOException() {
        CompletableFuture<HttpResponse<String>> future = new CompletableFuture<>();
        future.cancel(true);

        IOException thrown = assertThrows(IOException.class, () -> exchange(future));

        assertEquals(BackChannelHttp.EXCHANGE_CANCELLED, thrown.getMessage(),
                "A cancelled exchange is an IOException, which every endpoint client already handles");
    }

    @Test
    @DisplayName("should cancel the exchange and rethrow when interrupted while waiting")
    void shouldCancelExchangeWhenInterruptedWhileWaiting() throws Exception {
        CompletableFuture<HttpResponse<String>> future = new CompletableFuture<>();
        CompletableFuture<Throwable> outcome = new CompletableFuture<>();
        Thread waiter = new Thread(() -> {
            try {
                exchange(future);
                outcome.complete(null);
            } catch (Exception | Error e) {
                outcome.complete(e);
            }
        }, "exchange-waiter");
        waiter.start();
        // The waiter is parked inside get() once it registered on the future and is in WAITING.
        while (future.getNumberOfDependents() == 0 || waiter.getState() != Thread.State.WAITING) {
            assertTrue(waiter.isAlive(), "The waiter waits on the incomplete future");
            Thread.onSpinWait();
        }

        waiter.interrupt();
        Throwable thrown = outcome.get(30, TimeUnit.SECONDS);
        waiter.join(TimeUnit.SECONDS.toMillis(30));

        assertAll("Interrupted wait",
                () -> assertInstanceOf(InterruptedException.class, thrown, "The interrupt reaches the caller"),
                () -> assertTrue(future.isCancelled(), "The exchange is cancelled"));
    }

    @Test
    @DisplayName("positive control: a future completed while waiting returns its response and is not cancelled")
    void shouldReturnResponseCompletedWhileWaiting() throws Exception {
        CompletableFuture<HttpResponse<String>> future = new CompletableFuture<>();
        CompletableFuture<Object> outcome = new CompletableFuture<>();
        Thread waiter = new Thread(() -> {
            try {
                outcome.complete(exchange(future));
            } catch (Exception | Error e) {
                outcome.complete(e);
            }
        }, "exchange-waiter");
        waiter.start();
        while (future.getNumberOfDependents() == 0 || waiter.getState() != Thread.State.WAITING) {
            assertTrue(waiter.isAlive(), "The waiter waits on the incomplete future");
            Thread.onSpinWait();
        }
        HttpResponse<String> response = new StubHttpClient.StubResponse();

        future.complete(response);
        Object result = outcome.get(30, TimeUnit.SECONDS);
        waiter.join(TimeUnit.SECONDS.toMillis(30));

        assertAll("Completed wait",
                () -> assertSame(response, result, "The response reaches the caller"),
                () -> assertFalse(future.isCancelled(), "The exchange is not cancelled"));
    }

    // --- request timeout: the bound of the wait for the response headers -------------------------------

    private String silentEndpoint(String path) throws IOException {
        silentServer = new ServerSocket(0, 50, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
        return "http://127.0.0.1:" + silentServer.getLocalPort() + path;
    }

    private static ClientConfiguration config(String issuer) {
        return ClientConfiguration.builder()
                .issuer(issuer)
                .clientId("test-client")
                .clientSecret("test-secret")
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(true)
                .allowLoopbackEgress(true)
                .readTimeoutSeconds(READ_TIMEOUT_SECONDS)
                .build();
    }

    @Test
    @DisplayName("should build every back-channel request with the configured read timeout as its timeout")
    void shouldCarryReadTimeoutOnRequest() throws Exception {
        String endpoint = silentEndpoint("/endpoint");
        BackChannelHttp backChannel = new BackChannelHttp(config(endpoint), 1024,
                BackChannelHttp.FIXED_PARSER_CONFIG_ORIGIN);
        HttpHandler handler = backChannel.validatedHandler(endpoint, "test endpoint request failed");

        HttpRequest request = handler.requestBuilder().GET().build();

        assertEquals(Optional.of(Duration.ofSeconds(READ_TIMEOUT_SECONDS)), request.timeout(),
                "The request timeout is the configured read timeout");
    }

    /** One call of an endpoint client against {@code endpoint}, configured by {@code configuration}. */
    @FunctionalInterface
    interface CallSite {
        void call(ClientConfiguration configuration, String endpoint);
    }

    static Stream<Arguments> callSites() {
        return Stream.of(
                Arguments.of("DiscoveryResolver", (CallSite) (configuration, endpoint) ->
                        new DiscoveryResolver(configuration).resolve()),
                Arguments.of("ParClient", (CallSite) (configuration, endpoint) ->
                        new ParClient(configuration).pushAuthorizationRequest(endpoint,
                                Map.of("response_type", "code"), basicAuth(configuration))),
                Arguments.of("RevocationClient", (CallSite) (configuration, endpoint) ->
                        new RevocationClient(configuration).revoke(endpoint, "token-value", null,
                                basicAuth(configuration))),
                Arguments.of("UserInfoClient", (CallSite) (configuration, endpoint) ->
                        new UserInfoClient(configuration).fetchUserInfo(endpoint, "access-token-value")),
                Arguments.of("TokenEndpointClient", (CallSite) (configuration, endpoint) ->
                        new TokenEndpointClient(configuration).requestToken(endpoint,
                                Map.of("grant_type", "client_credentials"), Map.of())));
    }

    private static ClientSecretBasicAuth basicAuth(ClientConfiguration configuration) {
        return new ClientSecretBasicAuth(configuration.getClientId(), configuration.getClientSecret());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callSites")
    @DisplayName("should end the wait for the response headers of every endpoint client through the request timeout")
    void shouldBoundWaitByRequestTimeout(String clientName, CallSite callSite) throws Exception {
        String endpoint = silentEndpoint("/endpoint");
        ClientConfiguration configuration = config(endpoint);
        long started = System.nanoTime();

        TransportException failure = assertThrows(TransportException.class,
                () -> callSite.call(configuration, endpoint));
        Duration waited = Duration.ofNanos(System.nanoTime() - started);

        assertAll(clientName,
                () -> assertTrue(hasCause(failure, HttpTimeoutException.class),
                        "The wait ends with the request timeout, not with an answer"),
                () -> assertFalse(waited.compareTo(Duration.ofSeconds(READ_TIMEOUT_SECONDS)) < 0,
                        "The wait lasts at least the configured read timeout"));
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}
