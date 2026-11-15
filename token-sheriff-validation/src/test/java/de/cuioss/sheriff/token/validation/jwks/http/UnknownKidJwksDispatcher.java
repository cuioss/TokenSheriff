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
package de.cuioss.sheriff.token.validation.jwks.http;

import de.cuioss.sheriff.token.validation.test.InMemoryJWKSFactory;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import lombok.NonNull;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counting mock JWKS endpoint for the unknown-kid refresh tests.
 * <p>
 * Counts every request on the server side, so a test asserts how many fetches actually reached the
 * endpoint rather than what the loader believes it did. The served key set can be switched between the
 * default key ({@link InMemoryJWKSFactory#DEFAULT_KEY_ID}) and a rotated key ({@link #ROTATED_KEY_ID}),
 * and failure responses can be injected: an HTTP 500, a delayed response that runs into the read
 * timeout, an oversized body, malformed JSON and an empty key set. An optional gate holds a response
 * back until the test releases it, and signals when a request has arrived.
 */
class UnknownKidJwksDispatcher implements ModuleDispatcherElement {

    static final String LOCAL_PATH = "/unknown-kid/jwks.json";
    static final String ROTATED_KEY_ID = "alternative-key-id";

    /** Longest time a gated response waits for its release. */
    private static final int GATE_TIMEOUT_SECONDS = 10;

    /** Size of the oversized body, well above any JWKS a parser accepts. */
    private static final int OVERSIZED_BODY_LENGTH = 4 * 1024 * 1024;

    enum Mode {
        DEFAULT_KEY, ROTATED_KEY, SERVER_ERROR, SLOW, OVERSIZED, MALFORMED, EMPTY_KEY_SET
    }

    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile Mode mode = Mode.DEFAULT_KEY;
    private volatile int slowResponseSeconds = 3;
    private volatile CountDownLatch requestArrived;
    private volatile CountDownLatch responseGate;

    int getRequestCount() {
        return requestCount.get();
    }

    void serve(Mode newMode) {
        this.mode = newMode;
    }

    void setSlowResponseSeconds(int seconds) {
        this.slowResponseSeconds = seconds;
    }

    /**
     * Holds the next responses back until {@code gate} is released, counting {@code arrived} down when a
     * request has reached the endpoint.
     */
    void holdResponses(CountDownLatch arrived, CountDownLatch gate) {
        this.requestArrived = arrived;
        this.responseGate = gate;
    }

    @Override
    public Optional<MockResponse> handleGet(@NonNull RecordedRequest request) {
        requestCount.incrementAndGet();
        CountDownLatch arrived = requestArrived;
        if (arrived != null) {
            arrived.countDown();
        }
        awaitGate();
        Headers json = Headers.of("Content-Type", "application/json");
        return Optional.of(switch (mode) {
            case DEFAULT_KEY -> new MockResponse(200, json, InMemoryJWKSFactory.createDefaultJwks());
            case ROTATED_KEY -> new MockResponse(200, json,
                    InMemoryKeyMaterialHandler.createJwks(InMemoryKeyMaterialHandler.Algorithm.RS384, ROTATED_KEY_ID));
            case SERVER_ERROR -> new MockResponse(500, Headers.of(), "");
            case SLOW -> new MockResponse.Builder()
                    .code(200)
                    .headers(json)
                    .body(InMemoryJWKSFactory.createDefaultJwks())
                    .headersDelay(slowResponseSeconds, TimeUnit.SECONDS)
                    .build();
            case OVERSIZED -> new MockResponse(200, json,
                    "{\"keys\":[],\"padding\":\"" + "x".repeat(OVERSIZED_BODY_LENGTH) + "\"}");
            case MALFORMED -> new MockResponse(200, json, InMemoryJWKSFactory.createInvalidJson());
            case EMPTY_KEY_SET -> new MockResponse(200, json, InMemoryJWKSFactory.createEmptyJwks());
        });
    }

    private void awaitGate() {
        CountDownLatch gate = responseGate;
        if (gate == null) {
            return;
        }
        try {
            if (!gate.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The response gate was not released in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the response gate", e);
        }
    }

    @Override
    public String getBaseUrl() {
        return LOCAL_PATH;
    }

    @Override
    public @NonNull Set<HttpMethodMapper> supportedMethods() {
        return Set.of(HttpMethodMapper.GET);
    }
}
