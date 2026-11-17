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
import java.util.Arrays;
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
 *       {@link BoundedBody#truncated()} {@code true}. No byte past the ceiling is delivered.</li>
 * </ul>
 * Chunks that arrive after the first crossing are ignored under both policies. The handlers log
 * nothing; an over-limit body never becomes an {@code OutOfMemoryError}.
 * <p>
 * The bytes read so far are kept in one array that grows by doubling and never beyond the ceiling, so
 * the number of objects a body costs grows with the logarithm of its size, not with the number of
 * buffers it arrives in: a body delivered as many one-byte buffers costs no more than the same bytes in
 * one buffer, and an empty buffer costs nothing. The assembled body never holds more than the ceiling, but
 * during a growth step the old and the new array are both live, so under either policy the memory held
 * while reading peaks below twice the ceiling plus the one in-flight chunk.
 * <p>
 * Under {@link OverLimitPolicy#FAIL} a {@code Content-Length} header that is not a valid number is a
 * normal failure as well, when the handler observes it: the subscription is cancelled without requesting
 * a byte and the body future completes exceptionally with an {@link IOException} reading
 * {@code Response Content-Length header is not a valid number}; the header value is not echoed. That
 * guarantee is the handler's own. Whether the surrounding client reaches the handler, and whether it
 * parses the header itself first, is the client's behaviour: the JDK HTTP/1.1 client (observed on JDK 25)
 * parses {@code Content-Length} itself after the handler returned, so there the caller of
 * {@code HttpClient.send} receives the JDK's own {@link IllegalArgumentException} (cause
 * {@link NumberFormatException}) carrying the raw header value, and the fixed message is not delivered. A
 * caller therefore treats the message of any exception out of an HTTP exchange as remote content and
 * sanitises it before logging.
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
     * Fixed message of the failure for a {@code Content-Length} header that is not a valid number. The
     * header value is remote content and is not part of the message.
     */
    static final String UNPARSABLE_CONTENT_LENGTH = "Response Content-Length header is not a valid number";

    private static final String CONTENT_LENGTH = "Content-Length";

    /**
     * What a handler does when the body exceeds the ceiling.
     *
     * @since 1.0
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
            OptionalLong advertised;
            try {
                advertised = responseInfo.headers().firstValueAsLong(CONTENT_LENGTH);
            } catch (NumberFormatException e) {
                // The header value is remote content and is not echoed into the message.
                return new CancellingRejectSubscriber(new IOException(UNPARSABLE_CONTENT_LENGTH));
            }
            if (advertised.isPresent() && advertised.getAsLong() > maxBytes) {
                return new CancellingRejectSubscriber(new IOException(overLimitMessage(maxBytes, boundOrigin)));
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
     *
     * @since 1.0
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
     * Subscriber for a {@code Content-Length} pre-check rejection (an over-advertised or unparsable
     * length): cancels the subscription the instant it is offered, so no body byte is streamed, and
     * returns an already-failed body future. Unlike a discarding subscriber, which requests
     * {@link Long#MAX_VALUE} and drains every buffer, this defeats the body without reading it.
     */
    private static final class CancellingRejectSubscriber implements HttpResponse.BodySubscriber<BoundedBody> {

        private final CompletableFuture<BoundedBody> result = new CompletableFuture<>();

        CancellingRejectSubscriber(IOException failure) {
            result.completeExceptionally(failure);
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

        private static final byte[] EMPTY = new byte[0];

        /** Capacity of the first allocation, unless the ceiling is smaller. */
        private static final int INITIAL_CAPACITY = 1024;

        private final long maxBytes;
        private final OverLimitPolicy policy;
        private final String boundOrigin;
        private final CompletableFuture<BoundedBody> result = new CompletableFuture<>();

        /**
         * The bytes read so far, in {@code content[0..total)}. Grown by doubling, never beyond
         * {@link #maxBytes}, so the number of arrays allocated grows with the logarithm of the body size
         * and not with the number of buffers it arrives in.
         */
        private byte[] content = EMPTY;
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
                append(buffer, remaining);
            }
        }

        private void onCrossing(ByteBuffer buffer) {
            subscription.cancel();
            if (policy == OverLimitPolicy.TRUNCATE) {
                append(buffer, (int) (maxBytes - total));
                result.complete(new BoundedBody(assemble(), true));
            } else {
                result.completeExceptionally(new IOException(overLimitMessage(maxBytes, boundOrigin)));
            }
            content = EMPTY;
        }

        /**
         * Copies {@code length} bytes of {@code buffer} behind the bytes read so far; the caller guarantees
         * that {@code total + length} does not exceed {@link #maxBytes}. An empty read allocates nothing.
         */
        private void append(ByteBuffer buffer, int length) {
            if (length == 0) {
                return;
            }
            int position = (int) total;
            int required = position + length;
            if (required > content.length) {
                long doubled = Math.max(2L * content.length, INITIAL_CAPACITY);
                content = Arrays.copyOf(content, (int) Math.clamp(doubled, required, maxBytes));
            }
            buffer.get(content, position, length);
            total = required;
        }

        private byte[] assemble() {
            return content.length == total ? content : Arrays.copyOf(content, (int) total);
        }

        @Override
        public void onError(Throwable throwable) {
            content = EMPTY;
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            if (result.isDone()) {
                return;
            }
            result.complete(new BoundedBody(assemble(), false));
            content = EMPTY;
        }
    }
}
