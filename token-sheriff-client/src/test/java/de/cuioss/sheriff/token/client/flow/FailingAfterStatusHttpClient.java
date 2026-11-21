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
package de.cuioss.sheriff.token.client.flow;

import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Test fixture: an {@link HttpClient} whose {@code sendAsync} first lets the request's body handler observe a
 * response with a given status — so a caller that records the status line in its body handler, as
 * {@link TokenEndpointClient} does, records it — and then returns a future failed with a given cause. With no
 * status given the body handler is never invoked, as for a failure before the response headers arrived.
 * <p>
 * It opens no socket and starts no thread: the body handler runs on the calling thread and the returned
 * future is already completed.
 */
final class FailingAfterStatusHttpClient extends HttpClient {

    private final @Nullable Integer status;
    private final Throwable cause;

    /**
     * @param status the status the body handler observes, or {@code null} for no status line
     * @param cause  the cause the returned future fails with
     */
    FailingAfterStatusHttpClient(@Nullable Integer status, Throwable cause) {
        this.status = status;
        this.cause = cause;
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return Optional.empty();
    }

    @Override
    public Redirect followRedirects() {
        return Redirect.NEVER;
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
        throw new UnsupportedOperationException("not used by the token endpoint client");
    }

    @Override
    public SSLParameters sslParameters() {
        return new SSLParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return Optional.empty();
    }

    @Override
    public Version version() {
        return Version.HTTP_1_1;
    }

    @Override
    public Optional<Executor> executor() {
        return Optional.empty();
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        throw new UnsupportedOperationException("the exchange waits on sendAsync");
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler) {
        if (status != null) {
            responseBodyHandler.apply(new StatusOnly(status));
        }
        CompletableFuture<HttpResponse<T>> failed = new CompletableFuture<>();
        failed.completeExceptionally(cause);
        return failed;
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        return sendAsync(request, responseBodyHandler);
    }

    /** The status line of a response with no headers. */
    private record StatusOnly(int statusCode) implements HttpResponse.ResponseInfo {

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }
    }
}
