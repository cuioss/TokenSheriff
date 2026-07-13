/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.token.commons.transport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Factory for {@link java.net.http.HttpResponse.BodyHandler BodyHandler}s that enforce a hard byte
 * ceiling on the response body <em>while it streams</em>, before the full body is ever materialized.
 * <p>
 * The IdP-advertised discovery and JWKS endpoints are attacker-influenceable: a hostile or
 * slow-drip multi-gigabyte body would exhaust the heap if the whole body were buffered before any
 * size check. {@link #ofBoundedString(Charset, long)} closes that vector with two layers, both
 * fail-closed:
 * <ol>
 *   <li><strong>Content-Length pre-check</strong> — a response that advertises a
 *       {@code Content-Length} larger than the ceiling is rejected without reading its body.</li>
 *   <li><strong>Capped streaming read</strong> — bytes are counted as they arrive; the moment the
 *       running total would exceed the ceiling the subscription is cancelled and the body future
 *       fails, so no more than {@code maxBytes} are ever held in memory (the body that lied about
 *       its {@code Content-Length}, or advertised none, is caught here).</li>
 * </ol>
 * An over-limit body therefore fails closed with an {@link IOException}, never an {@code OutOfMemoryError}.
 *
 * @since 1.0
 */
final class BoundedBodyHandlers {

    private BoundedBodyHandlers() {
    }

    /**
     * Returns a body handler that decodes the response body to a {@link String} using {@code charset},
     * rejecting any body that exceeds {@code maxBytes} during streaming.
     *
     * @param charset  the charset used to decode the accumulated bytes
     * @param maxBytes the inclusive byte ceiling; a body strictly larger than this is rejected
     * @return a bounded {@code BodyHandler<String>}
     */
    static java.net.http.HttpResponse.BodyHandler<String> ofBoundedString(Charset charset, long maxBytes) {
        return responseInfo -> {
            OptionalLong advertised = responseInfo.headers().firstValueAsLong("Content-Length");
            if (advertised.isPresent() && advertised.getAsLong() > maxBytes) {
                return rejectingSubscriber(maxBytes);
            }
            return new BoundedStringSubscriber(charset, maxBytes);
        };
    }

    /**
     * A subscriber that discards the incoming body (so the connection is not left dangling) and fails
     * the body future — used for the {@code Content-Length} pre-check rejection path.
     */
    private static java.net.http.HttpResponse.BodySubscriber<String> rejectingSubscriber(long maxBytes) {
        return java.net.http.HttpResponse.BodySubscribers.mapping(
                java.net.http.HttpResponse.BodySubscribers.discarding(),
                ignored -> {
                    throw new UncheckedIOException(new IOException(overLimitMessage(maxBytes)));
                });
    }

    private static String overLimitMessage(long maxBytes) {
        return "Response body exceeds maximum allowed size of " + maxBytes + " bytes";
    }

    /**
     * Accumulates the streamed body up to {@code maxBytes}, failing the body future the instant the
     * running byte total would exceed the ceiling.
     */
    private static final class BoundedStringSubscriber
            implements java.net.http.HttpResponse.BodySubscriber<String> {

        private final Charset charset;
        private final long maxBytes;
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private final List<byte[]> chunks = new ArrayList<>();
        private long total;
        private Flow.Subscription subscription;

        BoundedStringSubscriber(Charset charset, long maxBytes) {
            this.charset = charset;
            this.maxBytes = maxBytes;
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
        public void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) {
                return;
            }
            for (ByteBuffer buffer : buffers) {
                int remaining = buffer.remaining();
                total += remaining;
                if (total > maxBytes) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException(overLimitMessage(maxBytes)));
                    return;
                }
                byte[] chunk = new byte[remaining];
                buffer.get(chunk);
                chunks.add(chunk);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            if (result.isDone()) {
                return;
            }
            byte[] all = new byte[(int) total];
            int position = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, all, position, chunk.length);
                position += chunk.length;
            }
            result.complete(new String(all, charset));
        }
    }
}
