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

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the bounded reads of {@link BackChannelHttp#bodyHandler()}, which delegates to the commons
 * {@code BoundedBodyHandler} (ADR-0031): the back channel gains the {@code Content-Length} pre-check, keeps
 * the streaming ceiling, and every failure names the fixed bound origin. The handler is driven with a
 * synthetic {@link HttpResponse.ResponseInfo} and chunks; no socket is opened.
 */
@DisplayName("BackChannelHttp bounded body reads")
class BackChannelHttpBoundedBodyTest {

    private static final int MAX_CONTENT_SIZE = 64;

    private static final String EXPECTED_MESSAGE = "Response body exceeds maximum allowed size of 64 bytes ("
            + BackChannelHttp.FIXED_PARSER_CONFIG_ORIGIN + ")";

    private final HttpResponse.BodyHandler<String> handler = new BackChannelHttp(ClientConfiguration.builder()
            .issuer("https://idp.example.com/realms/demo")
            .clientId("test-client")
            .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
            .build(), MAX_CONTENT_SIZE, BackChannelHttp.FIXED_PARSER_CONFIG_ORIGIN).bodyHandler();

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean requested = new AtomicBoolean(false);

    private HttpResponse.BodySubscriber<String> subscribe(Long advertisedContentLength) {
        Map<String, List<String>> headers = advertisedContentLength == null
                ? Map.of()
                : Map.of("Content-Length", List.of(Long.toString(advertisedContentLength)));
        HttpResponse.BodySubscriber<String> subscriber = handler.apply(new HttpResponse.ResponseInfo() {
            @Override
            public int statusCode() {
                return 200;
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(headers, (name, value) -> true);
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        });
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                requested.set(true);
            }

            @Override
            public void cancel() {
                cancelled.set(true);
            }
        });
        return subscriber;
    }

    private static void feed(HttpResponse.BodySubscriber<String> subscriber, int length, int chunkSize) {
        byte[] body = new byte[length];
        Arrays.fill(body, (byte) 'x');
        for (int offset = 0; offset < length; offset += chunkSize) {
            subscriber.onNext(List.of(ByteBuffer.wrap(body, offset, Math.min(chunkSize, length - offset))));
        }
    }

    private static Throwable failureOf(HttpResponse.BodySubscriber<String> subscriber) {
        CompletableFuture<String> body = subscriber.getBody().toCompletableFuture();
        CompletionException exception = assertThrows(CompletionException.class, body::join,
                "The body future must fail");
        return exception.getCause();
    }

    @Test
    @DisplayName("should fail a response advertising a Content-Length above the ceiling before reading it")
    void shouldFailOverAdvertisedResponseBeforeReading() {
        HttpResponse.BodySubscriber<String> subscriber = subscribe(MAX_CONTENT_SIZE + 1L);

        Throwable failure = failureOf(subscriber);
        assertAll("Pre-check",
                () -> assertTrue(cancelled.get(), "The subscription is cancelled before any byte flows"),
                () -> assertFalse(requested.get(), "No byte is requested"),
                () -> assertInstanceOf(IOException.class, failure, "The failure reaches the IOException path"),
                () -> assertEquals(EXPECTED_MESSAGE, failure.getMessage(), "The failure names the fixed origin"));
    }

    @Test
    @DisplayName("should fail a body without Content-Length while it streams past the ceiling")
    void shouldFailOverLimitBodyWhileStreaming() {
        HttpResponse.BodySubscriber<String> subscriber = subscribe(null);
        feed(subscriber, MAX_CONTENT_SIZE + 1, 16);
        subscriber.onComplete();

        Throwable failure = failureOf(subscriber);
        assertAll("Streaming ceiling",
                () -> assertTrue(requested.get(), "A body without Content-Length is read"),
                () -> assertTrue(cancelled.get(), "The crossing chunk cancels the read"),
                () -> assertInstanceOf(IOException.class, failure, "The failure reaches the IOException path"),
                () -> assertEquals(EXPECTED_MESSAGE, failure.getMessage(), "The failure names the fixed origin"));
    }

    @Test
    @DisplayName("should return an at-limit body unchanged")
    void shouldReturnAtLimitBodyUnchanged() {
        HttpResponse.BodySubscriber<String> subscriber = subscribe((long) MAX_CONTENT_SIZE);
        feed(subscriber, MAX_CONTENT_SIZE, 16);
        subscriber.onComplete();

        assertAll("At-limit body",
                () -> assertEquals("x".repeat(MAX_CONTENT_SIZE), subscriber.getBody().toCompletableFuture().join(),
                        "The body is returned unchanged"),
                () -> assertFalse(cancelled.get(), "An at-limit body is read to its end"));
    }
}
