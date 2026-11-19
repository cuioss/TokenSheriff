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
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.commons.error.TransportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that {@link BackChannelHttp#send} maps an {@link IllegalArgumentException} to the malformed-response
 * failure only when it fails the exchange: a request the JDK HTTP client refuses before sending is thrown
 * synchronously by {@link HttpClient#sendAsync}, a request-side fault that reaches the caller as the
 * {@link IllegalArgumentException} the client threw.
 * <p>
 * The refused request is hand-built: it carries the restricted {@code Connection} header, which
 * {@link HttpRequest.Builder} refuses to set, so only a custom {@link HttpRequest} can hand it to the client.
 * The client refuses it while preparing the exchange, before any byte is written. The positive control sends a
 * valid request to a loopback server whose {@code Content-Length} is not a number and sees the fixed
 * {@link BackChannelHttp#MALFORMED_RESPONSE} failure.
 * <p>
 * A calling thread that is already interrupted sends nothing: the call throws {@link InterruptedException}
 * before the request is handed to the client, so neither the client nor the loopback server sees it. The
 * positive control sends the same request from a thread that is not interrupted and receives the response.
 */
@DisplayName("BackChannelHttp request refusal")
class BackChannelHttpRequestRefusalTest {

    private static final String FAILURE_CONTEXT = "test endpoint request failed";
    private static final String RESTRICTED_HEADER = "Connection";

    private RawHttpServer server;

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.close();
        }
    }

    private static ClientConfiguration config() {
        return ClientConfiguration.builder()
                .issuer("https://idp.example.com/realms/demo")
                .clientId("test-client")
                .clientSecret("test-secret")
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(true)
                .allowLoopbackEgress(true)
                .build();
    }

    /**
     * Starts a loopback server that reads the request head and answers with a response whose
     * {@code Content-Length} is not a number, and returns its URL.
     */
    private String serveMalformedResponse(String statusLine) throws IOException {
        return serve(statusLine, "12raw34");
    }

    /**
     * Starts a loopback server that counts every request it reads and answers with {@code statusLine}, the
     * {@code {}} body and the given {@code Content-Length} value, and returns its URL.
     */
    private String serve(String statusLine, String contentLength) throws IOException {
        server = RawHttpServer.answering("HTTP/1.1 " + statusLine + "\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + contentLength + "\r\n"
                + "Connection: close\r\n"
                + "\r\n"
                + "{}");
        return server.url();
    }

    @Test
    @DisplayName("negative control: the request builder refuses the restricted header the hand-built request carries")
    void shouldBeRefusedByTheRequestBuilder() {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1/endpoint"));

        assertThrows(IllegalArgumentException.class, () -> builder.header(RESTRICTED_HEADER, "close"),
                "The builder cannot produce the request the client refuses");
    }

    @Test
    @DisplayName("should let a request the client refuses before sending reach the caller as IllegalArgumentException")
    void shouldLetRequestRefusalReachCallerAsIllegalArgumentException() {
        BackChannelHttp backChannel = new BackChannelHttp(config(), 1024, BackChannelHttp.FIXED_PARSER_CONFIG_ORIGIN);
        HttpHandler handler = backChannel.validatedHandler("http://127.0.0.1:1/endpoint", FAILURE_CONTEXT);
        HttpClient client = backChannel.sharedClient(handler);
        HttpRequest request = new RestrictedHeaderRequest(handler.requestBuilder().GET().build());

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> backChannel.send(client, request, FAILURE_CONTEXT));

        assertAll("Request-side refusal",
                () -> assertEquals(IllegalArgumentException.class, failure.getClass(),
                        "The JDK's refusal is not wrapped into a transport failure"),
                () -> assertFalse(String.valueOf(failure.getMessage()).contains(BackChannelHttp.MALFORMED_RESPONSE),
                        "A request-side fault is not reported as a malformed response"));
    }

    @Test
    @DisplayName("positive control: a malformed Content-Length response maps to the fixed transport failure")
    void shouldMapMalformedResponseToFixedFailure() throws Exception {
        assertMalformedResponseMapsToFixedFailure("200 OK");
    }

    @Test
    @DisplayName("should map a 204 whose Content-Length the client refuses before any body handler runs to the fixed failure")
    void shouldMapMalformedNoContentResponseToFixedFailure() throws Exception {
        assertMalformedResponseMapsToFixedFailure("204 No Content");
    }

    private void assertMalformedResponseMapsToFixedFailure(String statusLine) throws IOException {
        String url = serveMalformedResponse(statusLine);
        BackChannelHttp backChannel = new BackChannelHttp(config(), 1024, BackChannelHttp.FIXED_PARSER_CONFIG_ORIGIN);
        HttpHandler handler = backChannel.validatedHandler(url, FAILURE_CONTEXT);
        HttpClient client = backChannel.sharedClient(handler);
        HttpRequest request = handler.requestBuilder().GET().build();

        TransportException failure = assertThrows(TransportException.class,
                () -> backChannel.send(client, request, FAILURE_CONTEXT));

        assertAll("Malformed response after " + statusLine,
                () -> assertEquals(FAILURE_CONTEXT + ": " + BackChannelHttp.MALFORMED_RESPONSE, failure.getMessage(),
                        "A response the client refuses as malformed is the fixed malformed-response failure"),
                () -> assertFalse(failure.getMessage().contains("12raw34"), "The raw header value is not echoed"),
                () -> assertNull(failure.getCause(), "The JDK exception carrying the raw value is not chained"));
    }

    @Test
    @DisplayName("should send nothing when the calling thread is already interrupted")
    void shouldSendNothingWhenAlreadyInterrupted() throws Exception {
        String url = serve("200 OK", "2");
        BackChannelHttp backChannel = new BackChannelHttp(config(), 1024, BackChannelHttp.FIXED_PARSER_CONFIG_ORIGIN);
        HttpHandler handler = backChannel.validatedHandler(url, FAILURE_CONTEXT);
        RecordingClient client = new RecordingClient(backChannel.sharedClient(handler));
        HttpRequest request = handler.requestBuilder().GET().build();

        boolean interruptStatusAfterCall;
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> backChannel.send(client, request, FAILURE_CONTEXT),
                    "An interrupted caller gets the InterruptedException the wait would raise");
            interruptStatusAfterCall = Thread.currentThread().isInterrupted();
        } finally {
            Thread.interrupted();
        }

        assertAll("Already-interrupted caller",
                () -> assertEquals(0, client.dispatched.get(), "The request is never handed to the client"),
                () -> assertEquals(0, server.receivedRequests(), "No request reaches the server"),
                () -> assertFalse(interruptStatusAfterCall,
                        "The interrupt status is cleared with the exception, as HttpClient.send does"));
    }

    @Test
    @DisplayName("positive control: the same call from a thread that is not interrupted is sent and answered")
    void shouldSendWhenNotInterrupted() throws Exception {
        String url = serve("200 OK", "2");
        BackChannelHttp backChannel = new BackChannelHttp(config(), 1024, BackChannelHttp.FIXED_PARSER_CONFIG_ORIGIN);
        HttpHandler handler = backChannel.validatedHandler(url, FAILURE_CONTEXT);
        RecordingClient client = new RecordingClient(backChannel.sharedClient(handler));
        HttpRequest request = handler.requestBuilder().GET().build();

        HttpResponse<String> response = backChannel.send(client, request, FAILURE_CONTEXT);

        assertAll("Caller that is not interrupted",
                () -> assertEquals(200, response.statusCode(), "The server's status reaches the caller"),
                () -> assertEquals("{}", response.body(), "The server's body reaches the caller"),
                () -> assertEquals(1, client.dispatched.get(), "The request is handed to the client once"),
                () -> assertEquals(1, server.receivedRequests(), "The request reaches the server once"));
    }

    /**
     * A client that delegates to the shared client and counts every request handed to it, so a test can tell a
     * request that was never dispatched from one that was dispatched and then cancelled.
     */
    private static final class RecordingClient extends HttpClient {

        private final HttpClient delegate;
        private final AtomicInteger dispatched = new AtomicInteger();

        private RecordingClient(HttpClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return delegate.cookieHandler();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return delegate.connectTimeout();
        }

        @Override
        public Redirect followRedirects() {
            return delegate.followRedirects();
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return delegate.proxy();
        }

        @Override
        public SSLContext sslContext() {
            return delegate.sslContext();
        }

        @Override
        public SSLParameters sslParameters() {
            return delegate.sslParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return delegate.authenticator();
        }

        @Override
        public Version version() {
            return delegate.version();
        }

        @Override
        public Optional<Executor> executor() {
            return delegate.executor();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            dispatched.incrementAndGet();
            return delegate.send(request, responseBodyHandler);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            dispatched.incrementAndGet();
            return delegate.sendAsync(request, responseBodyHandler);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            dispatched.incrementAndGet();
            return delegate.sendAsync(request, responseBodyHandler, pushPromiseHandler);
        }
    }

    /**
     * A request that delegates to a builder-made request and adds the restricted {@code Connection} header,
     * which {@link HttpRequest.Builder} refuses to set.
     */
    private static final class RestrictedHeaderRequest extends HttpRequest {

        private final HttpRequest delegate;

        private RestrictedHeaderRequest(HttpRequest delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<BodyPublisher> bodyPublisher() {
            return delegate.bodyPublisher();
        }

        @Override
        public String method() {
            return delegate.method();
        }

        @Override
        public Optional<Duration> timeout() {
            return delegate.timeout();
        }

        @Override
        public boolean expectContinue() {
            return delegate.expectContinue();
        }

        @Override
        public URI uri() {
            return delegate.uri();
        }

        @Override
        public Optional<HttpClient.Version> version() {
            return delegate.version();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(RESTRICTED_HEADER, List.of("close")), (name, value) -> true);
        }
    }
}
