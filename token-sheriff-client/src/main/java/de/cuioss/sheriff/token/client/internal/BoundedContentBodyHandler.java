/*
 * Copyright © 2022 CUI-OpenSource-Software (info@cuioss.de)
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * A {@link HttpResponse.BodyHandler} that streams a response body into a {@link String} while
 * enforcing a hard byte ceiling <em>during</em> the read, rather than buffering the whole body and
 * checking its size afterwards.
 * <p>
 * The default {@link HttpResponse.BodyHandlers#ofString()} fully materializes the response body in
 * memory before the caller can inspect its length, so a size cap applied to the returned string
 * defends nothing against a hostile or misbehaving authorization server (M7): the out-of-memory has
 * already happened. This handler subscribes to the response body as a stream of {@link ByteBuffer}s,
 * accumulates them into a bounded buffer, and completes the body exceptionally with an
 * {@link IOException} the instant the running total would exceed {@code maxBytes} — cancelling the
 * subscription so no further bytes are read. Back-channel clients translate that {@link IOException}
 * into the declared {@code TransportException} on their normal {@code IOException} catch path.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public final class BoundedContentBodyHandler implements HttpResponse.BodyHandler<String> {

    private final int maxBytes;
    private final Charset charset;

    private BoundedContentBodyHandler(int maxBytes, Charset charset) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must not be negative");
        }
        this.maxBytes = maxBytes;
        this.charset = Objects.requireNonNull(charset, "charset must not be null");
    }

    /**
     * @param maxBytes the maximum number of body bytes to read before failing; must not be negative
     * @return a UTF-8 bounded body handler capped at {@code maxBytes}
     */
    public static BoundedContentBodyHandler of(int maxBytes) {
        return new BoundedContentBodyHandler(maxBytes, StandardCharsets.UTF_8);
    }

    @Override
    public HttpResponse.BodySubscriber<String> apply(HttpResponse.ResponseInfo responseInfo) {
        return new BoundedStringSubscriber(maxBytes, charset);
    }

    /**
     * Accumulates the streamed body up to {@code maxBytes}, failing fast when the running total would
     * exceed the ceiling.
     */
    private static final class BoundedStringSubscriber implements HttpResponse.BodySubscriber<String> {

        private final int maxBytes;
        private final Charset charset;
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        private long total;

        BoundedStringSubscriber(int maxBytes, Charset charset) {
            this.maxBytes = maxBytes;
            this.charset = charset;
        }

        @Override
        public CompletionStage<String> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer item : items) {
                int remaining = item.remaining();
                total += remaining;
                if (total > maxBytes) {
                    subscription.cancel();
                    buffer.reset();
                    result.completeExceptionally(new IOException(
                            "response body exceeds the maximum allowed size of " + maxBytes + " bytes"));
                    return;
                }
                byte[] chunk = new byte[remaining];
                item.get(chunk);
                buffer.writeBytes(chunk);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            if (!result.isDone()) {
                result.complete(buffer.toString(charset));
            }
        }
    }
}
