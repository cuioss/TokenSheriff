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
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;

/**
 * MockWebServer dispatcher for loader-generation tests: each well-known discovery request advertises
 * the next {@code jwks_uri} of a fixed sequence, the first discovery response can be held back until a
 * latch is released, and every JWKS path is served the default key set while its fetches are counted
 * per path.
 * <p>
 * This lets a test let one initialization resolve a different {@code jwks_uri} than a later one, and
 * hold the first resolution in flight across a {@code close()} and a re-initialization of the loader.
 */
final class DiscoveryGenerationDispatcher extends Dispatcher {

    /** Path of the discovery document. */
    static final String WELL_KNOWN_PATH = "/.well-known/openid-configuration";

    private static final int GATE_TIMEOUT_SECONDS = 10;

    private final List<String> advertisedJwksUris;
    private final AtomicInteger discoveryRequests = new AtomicInteger();
    private final Map<String, AtomicInteger> jwksFetches = new ConcurrentHashMap<>();
    private volatile @Nullable CountDownLatch firstDiscoveryGate;

    /**
     * @param advertisedJwksUris the {@code jwks_uri} advertised by the first, second, ... discovery
     *                           request; the last entry is repeated for every later request. An entry
     *                           starting with {@code /} is resolved against the server, any other entry
     *                           is advertised verbatim.
     */
    DiscoveryGenerationDispatcher(List<String> advertisedJwksUris) {
        this.advertisedJwksUris = List.copyOf(advertisedJwksUris);
    }

    /**
     * Holds the response to the first discovery request until {@code gate} is released.
     *
     * @param gate the latch releasing the first discovery response
     */
    void holdFirstDiscovery(CountDownLatch gate) {
        this.firstDiscoveryGate = gate;
    }

    /**
     * @return the number of discovery requests received so far
     */
    int discoveryRequests() {
        return discoveryRequests.get();
    }

    /**
     * @param path the JWKS path, for example {@code /jwks-a.json}
     * @return the number of fetches of that path received so far
     */
    int jwksFetches(String path) {
        AtomicInteger counter = jwksFetches.get(path);
        return counter == null ? 0 : counter.get();
    }

    @Override
    public MockResponse dispatch(RecordedRequest request) {
        HttpUrl url = request.getUrl();
        String path = url.encodedPath();
        if (WELL_KNOWN_PATH.equals(path)) {
            return discovery(url);
        }
        if (path.startsWith("/jwks")) {
            jwksFetches.computeIfAbsent(path, key -> new AtomicInteger()).incrementAndGet();
            return new MockResponse(SC_OK, Headers.of("Content-Type", "application/json"),
                    InMemoryJWKSFactory.createDefaultJwks());
        }
        return new MockResponse(SC_NOT_FOUND, Headers.of(), "");
    }

    private MockResponse discovery(HttpUrl url) {
        int index = discoveryRequests.getAndIncrement();
        String baseUrl = url.newBuilder().encodedPath("/").build().toString();
        String issuer = baseUrl.substring(0, baseUrl.length() - 1);
        String advertised = advertisedJwksUris.get(Math.min(index, advertisedJwksUris.size() - 1));
        String jwksUri = advertised.startsWith("/") ? issuer + advertised : advertised;
        if (index == 0) {
            awaitFirstDiscoveryGate();
        }
        return new MockResponse(SC_OK, Headers.of("Content-Type", "application/json"), """
                {
                  "issuer": "%s",
                  "jwks_uri": "%s"
                }
                """.formatted(issuer, jwksUri));
    }

    private void awaitFirstDiscoveryGate() {
        CountDownLatch gate = firstDiscoveryGate;
        if (gate == null) {
            return;
        }
        try {
            if (!gate.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The first discovery gate was not released within "
                        + GATE_TIMEOUT_SECONDS + " seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the first discovery gate", e);
        }
    }
}
