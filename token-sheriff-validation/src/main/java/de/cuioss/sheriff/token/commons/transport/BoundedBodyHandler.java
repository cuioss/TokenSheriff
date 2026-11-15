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
package de.cuioss.sheriff.token.commons.transport;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * {@link java.net.http.HttpResponse.BodyHandler BodyHandler}s that enforce a hard byte ceiling on a
 * response body <em>while it streams</em>, before the full body is ever materialized.
 * <p>
 * A remote endpoint — an IdP's discovery document or key set, a token endpoint — is
 * attacker-influenceable: a hostile or slow-drip multi-gigabyte body would exhaust the heap if the whole
 * body were buffered before any size check. The handlers count the bytes as they arrive and act the
 * moment the running total would exceed the ceiling, according to an {@link OverLimitPolicy}:
 * <ul>
 *   <li>{@link OverLimitPolicy#FAIL} — fail closed. A response that advertises a {@code Content-Length}
 *       larger than the ceiling is rejected without reading a byte of its body: the subscription is
 *       cancelled in {@code onSubscribe}. A body that crosses the ceiling while streaming (one that lied
 *       about its {@code Content-Length}, or advertised none) cancels the subscription, releases the
 *       buffered bytes and fails the body future. Either way the body future completes exceptionally
 *       with an {@link IOException} reading
 *       {@code Response body exceeds maximum allowed size of N bytes (origin)}.</li>
 *   <li>{@link OverLimitPolicy#TRUNCATE} — keep the prefix. The advertised {@code Content-Length} is not
 *       checked; the body is read up to the ceiling, the subscription is cancelled at the first crossing,
 *       and the result holds exactly the first {@code maxBytes} bytes with
 *       {@link BoundedBody#truncated()} {@code true}. No byte past the ceiling is delivered; at most the
 *       ceiling plus the one in-flight chunk is held while reading.</li>
 * </ul>
 * Chunks that arrive after the first crossing are ignored under both policies. The handlers log
 * nothing; an over-limit body never becomes an {@code OutOfMemoryError}.
 * <p>
 * The ceiling is accepted from {@code 0} to {@link #MAX_CEILING} inclusive, the largest array the result
 * can hold; a negative or larger ceiling is refused with an {@link IllegalArgumentException} when the
 * handler is created. The {@code boundOrigin} names the setting that produced the ceiling and whether it
 * is settable; it is reported verbatim in the rejection message, so a reader of the failure can tell a
 * tunable bound from a fixed one.
 * <p>
 * <strong>Thread safety:</strong> a handler is immutable and stateless, and may be shared and reused for
 * any number of requests. Each call to {@code apply} creates a fresh subscriber that serves exactly one
 * response.
 * <p>
 * Example:
 * {@snippet :
 * HttpResponse.BodyHandler<String> handler = BoundedBodyHandler.ofString(StandardCharsets.UTF_8,
 *         64 * 1024, "ParserConfig.maxHttpResponseSize; settable via ParserConfig.builder()");
 * HttpResponse<String> response = httpClient.send(request, handler); // IOException when over the ceiling
 * }
 *
 * @since 1.0
 */
public final class BoundedBodyHandler {

    /**
     * The largest accepted ceiling in bytes: the largest array the result can hold.
     */
    public static final long MAX_CEILING = Integer.MAX_VALUE - 8L;

    /**
     * What a handler does when the body exceeds the ceiling.
     */
    public enum OverLimitPolicy {

        /**
         * Fail the body future with an {@link IOException}; an over-advertised {@code Content-Length}
         * is refused before any byte is read.
         */
        FAIL,

        /**
         * Complete with the first {@code maxBytes} bytes and {@link BoundedBody#truncated()} {@code true}.
         */
        TRUNCATE
    }

    private BoundedBodyHandler() {
    }

    /**
     * Returns a handler that reads the body into a {@link BoundedBody}, applying {@code policy} when the
     * body exceeds {@code maxBytes}.
     *
     * @param maxBytes    the inclusive byte ceiling, from {@code 0} to {@link #MAX_CEILING}; a body strictly
     *                    larger than this is over the limit
     * @param policy      what to do with an over-limit body
     * @param boundOrigin the provenance of the ceiling — the setting that produced it, and whether it is
     *                    settable; reported verbatim in the rejection message
     * @return a bounded, reusable {@code BodyHandler<BoundedBody>}
     * @throws NullPointerException     if {@code policy} or {@code boundOrigin} is {@code null}
     * @throws IllegalArgumentException if {@code maxBytes} is negative or greater than {@link #MAX_CEILING}
     */
    public static HttpResponse.BodyHandler<BoundedBody> ofBytes(long maxBytes, OverLimitPolicy policy,
            String boundOrigin) {
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(boundOrigin, "boundOrigin must not be null");
        requireCeilingInRange(maxBytes);
        return responseInfo -> subscriberFor(responseInfo, maxBytes, policy, boundOrigin);
    }

    /**
     * Returns a handler that reads the body under the {@link OverLimitPolicy#FAIL} policy and decodes it
     * with {@code charset}.
     *
     * @param charset     the charset used to decode the bytes
     * @param maxBytes    the inclusive byte ceiling, from {@code 0} to {@link #MAX_CEILING}; a body strictly
     *                    larger than this fails with an {@link IOException}
     * @param boundOrigin the provenance of the ceiling — the setting that produced it, and whether it is
     *                    settable; reported verbatim in the rejection message
     * @return a bounded, reusable {@code BodyHandler<String>}
     * @throws NullPointerException     if {@code charset} or {@code boundOrigin} is {@code null}
     * @throws IllegalArgumentException if {@code maxBytes} is negative or greater than {@link #MAX_CEILING}
     */
    public static HttpResponse.BodyHandler<String> ofString(Charset charset, long maxBytes, String boundOrigin) {
        Objects.requireNonNull(charset, "charset must not be null");
        Objects.requireNonNull(boundOrigin, "boundOrigin must not be null");
        requireCeilingInRange(maxBytes);
        return responseInfo -> HttpResponse.BodySubscribers.mapping(
                subscriberFor(responseInfo, maxBytes, OverLimitPolicy.FAIL, boundOrigin),
                body -> new String(body.content, charset));
    }

    private static void requireCeilingInRange(long maxBytes) {
        if (maxBytes < 0 || maxBytes > MAX_CEILING) {
            throw new IllegalArgumentException(
                    "maxBytes must be between 0 and " + MAX_CEILING + " inclusive, but was " + maxBytes);
        }
    }

    private static HttpResponse.BodySubscriber<BoundedBody> subscriberFor(HttpResponse.ResponseInfo responseInfo,
            long maxBytes, OverLimitPolicy policy, String boundOrigin) {
        if (policy == OverLimitPolicy.FAIL) {
            OptionalLong advertised = responseInfo.headers().firstValueAsLong("Content-Length");
            if (advertised.isPresent() && advertised.getAsLong() > maxBytes) {
                return new CancellingRejectSubscriber(maxBytes, boundOrigin);
            }
        }
        return new BoundedSubscriber(maxBytes, policy, boundOrigin);
    }

    private static String overLimitMessage(long maxBytes, String boundOrigin) {
        return "Response body exceeds maximum allowed size of " + maxBytes + " bytes (" + boundOrigin + ")";
    }

    /**
     * The body read by a bounded handler: its bytes and whether bytes past the ceiling were dropped.
     * Immutable; {@link #toString()} never shows the content.
     */
    public static final class BoundedBody {

        private final byte[] content;
        private final boolean truncated;

        private BoundedBody(byte[] content, boolean truncated) {
            this.content = content;
            this.truncated = truncated;
        }

        /**
         * @return a copy of the body bytes; at most the ceiling
         */
        public byte[] bytes() {
            return content.clone();
        }

        /**
         * @return {@code true} only when bytes past the ceiling arrived and were dropped; a body of
         * exactly the ceiling is not truncated
         */
        public boolean truncated() {
            return truncated;
        }

        /**
         * @param charset the charset used to decode the bytes
         * @return the body decoded with {@code charset}
         */
        public String asString(Charset charset) {
            return new String(content, Objects.requireNonNull(charset, "charset must not be null"));
        }

        @Override
        public String toString() {
            return "BoundedBody[length=" + content.length + ", truncated=" + truncated + "]";
        }
    }

    /**
     * Subscriber for the {@code Content-Length} pre-check rejection: cancels the subscription the instant
     * it is offered, so no body byte is streamed, and returns an already-failed body future. Unlike a
     * discarding subscriber, which requests {@link Long#MAX_VALUE} and drains every buffer, this defeats an
     * over-advertised body without reading it.
     */
    private static final class CancellingRejectSubscriber implements HttpResponse.BodySubscriber<BoundedBody> {

        private final CompletableFuture<BoundedBody> result = new CompletableFuture<>();

        CancellingRejectSubscriber(long maxBytes, String boundOrigin) {
            result.completeExceptionally(new IOException(overLimitMessage(maxBytes, boundOrigin)));
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.cancel();
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            // Cancelled in onSubscribe; no body bytes are delivered.
        }

        @Override
        public void onError(Throwable throwable) {
            // The body future already carries the over-limit IOException.
        }

        @Override
        public void onComplete() {
            // The body future already carries the over-limit IOException.
        }

        @Override
        public CompletionStage<BoundedBody> getBody() {
            return result;
        }
    }

    /**
     * Accumulates the streamed body up to the ceiling and applies the policy at the first crossing:
     * cancel the subscription, release or complete with the buffered bytes, ignore every later chunk.
     */
    private static final class BoundedSubscriber implements HttpResponse.BodySubscriber<BoundedBody> {

        private final long maxBytes;
        private final OverLimitPolicy policy;
        private final String boundOrigin;
        private final CompletableFuture<BoundedBody> result = new CompletableFuture<>();
        private final List<byte[]> chunks = new ArrayList<>();
        private long total;
        private Flow.Subscription subscription;

        BoundedSubscriber(long maxBytes, OverLimitPolicy policy, String boundOrigin) {
            this.maxBytes = maxBytes;
            this.policy = policy;
            this.boundOrigin = boundOrigin;
        }

        @Override
        public CompletionStage<BoundedBody> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription offered) {
            if (subscription != null) {
                // Reactive Streams rule 2.5: a second subscription is cancelled, the first one stays.
                offered.cancel();
                return;
            }
            subscription = offered;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) {
                return;
            }
            for (ByteBuffer buffer : buffers) {
                int remaining = buffer.remaining();
                if (total + remaining > maxBytes) {
                    onCrossing(buffer);
                    return;
                }
                chunks.add(read(buffer, remaining));
                total += remaining;
            }
        }

        private void onCrossing(ByteBuffer buffer) {
            subscription.cancel();
            if (policy == OverLimitPolicy.TRUNCATE) {
                chunks.add(read(buffer, (int) (maxBytes - total)));
                total = maxBytes;
                result.complete(new BoundedBody(assemble(), true));
            } else {
                result.completeExceptionally(new IOException(overLimitMessage(maxBytes, boundOrigin)));
            }
            chunks.clear();
        }

        private static byte[] read(ByteBuffer buffer, int length) {
            byte[] chunk = new byte[length];
            buffer.get(chunk);
            return chunk;
        }

        private byte[] assemble() {
            byte[] all = new byte[(int) total];
            int position = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, all, position, chunk.length);
                position += chunk.length;
            }
            return all;
        }

        @Override
        public void onError(Throwable throwable) {
            chunks.clear();
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            if (result.isDone()) {
                return;
            }
            result.complete(new BoundedBody(assemble(), false));
            chunks.clear();
        }
    }
}
