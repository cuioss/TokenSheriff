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

/**
 * Drives an {@link HttpResponse.BodyHandler} against synthetic streamed bytes so the bounded
 * body-handler behaviour ({@link BoundedBodyHandler}) can be asserted without a live network:
 * the body is fed in small chunks to prove the ceiling is enforced <em>during</em> streaming, and
 * cancellation of the subscription is recorded as the observable proof that an over-limit body is
 * rejected before it is fully buffered. {@link #subscribe(HttpResponse.BodyHandler, Long)} hands the
 * caller every signal — chunk by chunk, an error, completion — for the edges the fixed driver cannot
 * express.
 */
final class BoundedBodyHandlerTestSupport {

    private BoundedBodyHandlerTestSupport() {
    }

    /**
     * Outcome of driving a body handler.
     *
     * @param body      the assembled body String, or {@code null} when the body future failed
     * @param failure   the failure that completed the body future, or {@code null} on success
     * @param cancelled whether the subscription was cancelled (streaming-abort proof)
     */
    record DriveResult(String body, Throwable failure, boolean cancelled) {

        boolean succeeded() {
            return failure == null;
        }
    }

    /**
     * Feeds {@code body} to the handler in 256-byte chunks. When {@code advertisedContentLength} is
     * non-null it is presented as the response {@code Content-Length} header (exercising the
     * pre-check path); a {@code null} value presents no such header (exercising the streaming-cap
     * path against a body that under-declares or omits its length).
     */
    static DriveResult drive(HttpResponse.BodyHandler<?> handler, byte[] body, Long advertisedContentLength) {
        HttpResponse.ResponseInfo info = responseInfo(advertisedContentLength);
        HttpResponse.BodySubscriber<?> subscriber = handler.apply(info);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        subscriber.onSubscribe(new RecordingSubscription(cancelled));

        int chunkSize = 256;
        for (int offset = 0; offset < body.length; offset += chunkSize) {
            int length = Math.min(chunkSize, body.length - offset);
            subscriber.onNext(List.of(ByteBuffer.wrap(body, offset, length).asReadOnlyBuffer()));
        }
        subscriber.onComplete();

        try {
            Object result = subscriber.getBody().toCompletableFuture().join();
            return new DriveResult((String) result, null, cancelled.get());
        } catch (CompletionException e) {
            return new DriveResult(null, e.getCause() != null ? e.getCause() : e, cancelled.get());
        }
    }

    /**
     * Builds a minimal {@link HttpResponse.ResponseInfo} for a 200 response, optionally advertising
     * {@code Content-Length}. Package-visible so other tests in this package (e.g. tests asserting a
     * bounded body handler produces a subscriber for a successful response) can reuse it instead of
     * duplicating the same anonymous implementation.
     *
     * @param advertisedContentLength the {@code Content-Length} to advertise, or {@code null} for none
     */
    static HttpResponse.ResponseInfo responseInfo(Long advertisedContentLength) {
        return responseInfoWithHeaders(advertisedContentLength == null
                ? Map.of()
                : Map.of("Content-Length", List.of(Long.toString(advertisedContentLength))));
    }

    /**
     * Builds a minimal {@link HttpResponse.ResponseInfo} for a 200 response carrying exactly
     * {@code headerMap}, so a test can present a header value no {@code long} can express.
     *
     * @param headerMap the response headers
     */
    static HttpResponse.ResponseInfo responseInfoWithHeaders(Map<String, List<String>> headerMap) {
        HttpHeaders headers = HttpHeaders.of(headerMap, (name, value) -> true);
        return new HttpResponse.ResponseInfo() {
            @Override
            public int statusCode() {
                return 200;
            }

            @Override
            public HttpHeaders headers() {
                return headers;
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    /**
     * Applies {@code handler} to a response optionally advertising {@code advertisedContentLength} and
     * subscribes it; the caller then sends each signal through the returned {@link ManualDrive}.
     */
    static <T> ManualDrive<T> subscribe(HttpResponse.BodyHandler<T> handler, Long advertisedContentLength) {
        return subscribeTo(handler, responseInfo(advertisedContentLength));
    }

    /**
     * Applies {@code handler} to {@code info} and subscribes it; the caller then sends each signal
     * through the returned {@link ManualDrive}.
     */
    static <T> ManualDrive<T> subscribeTo(HttpResponse.BodyHandler<T> handler, HttpResponse.ResponseInfo info) {
        HttpResponse.BodySubscriber<T> subscriber = handler.apply(info);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean requested = new AtomicBoolean(false);
        subscriber.onSubscribe(new RecordingSubscription(cancelled, requested));
        return new ManualDrive<>(subscriber, cancelled, requested);
    }

    /**
     * A subscribed body subscriber driven signal by signal. Each fed chunk is its own
     * {@code onNext} call and its buffer is returned, so a test can read how many of its bytes the
     * subscriber consumed.
     */
    static final class ManualDrive<T> {

        private final HttpResponse.BodySubscriber<T> subscriber;
        private final AtomicBoolean cancelled;
        private final AtomicBoolean requested;

        private ManualDrive(HttpResponse.BodySubscriber<T> subscriber, AtomicBoolean cancelled,
                AtomicBoolean requested) {
            this.subscriber = subscriber;
            this.cancelled = cancelled;
            this.requested = requested;
        }

        ByteBuffer feed(byte[] chunk) {
            ByteBuffer buffer = ByteBuffer.wrap(chunk);
            subscriber.onNext(List.of(buffer));
            return buffer;
        }

        void feedAll(byte[] body, int chunkSize) {
            for (int offset = 0; offset < body.length; offset += chunkSize) {
                feed(Arrays.copyOfRange(body, offset, Math.min(offset + chunkSize, body.length)));
            }
        }

        void complete() {
            subscriber.onComplete();
        }

        void error(Throwable throwable) {
            subscriber.onError(throwable);
        }

        void resubscribe(Flow.Subscription second) {
            subscriber.onSubscribe(second);
        }

        boolean cancelled() {
            return cancelled.get();
        }

        boolean requested() {
            return requested.get();
        }

        CompletableFuture<T> body() {
            return subscriber.getBody().toCompletableFuture();
        }

        /**
         * @return the failure the body future completed with
         * @throws AssertionError when the body future is not done or did not fail
         */
        Throwable failure() {
            CompletableFuture<T> future = body();
            if (!future.isCompletedExceptionally()) {
                throw new AssertionError("Expected a failed body future, but it was " + future);
            }
            try {
                future.join();
                throw new AssertionError("A failed body future must not join");
            } catch (CompletionException e) {
                return e.getCause();
            }
        }
    }

    static final class RecordingSubscription implements Flow.Subscription {

        private final AtomicBoolean cancelled;
        private final AtomicBoolean requested;

        RecordingSubscription(AtomicBoolean cancelled) {
            this(cancelled, new AtomicBoolean(false));
        }

        RecordingSubscription(AtomicBoolean cancelled, AtomicBoolean requested) {
            this.cancelled = cancelled;
            this.requested = requested;
        }

        @Override
        public void request(long n) {
            // No back-pressure modelling needed: the driver feeds all chunks synchronously.
            requested.set(true);
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }
    }
}
