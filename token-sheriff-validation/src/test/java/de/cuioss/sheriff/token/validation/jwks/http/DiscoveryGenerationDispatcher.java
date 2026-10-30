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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static jakarta.servlet.http.HttpServletResponse.*;

/**
 * MockWebServer dispatcher for loader-generation tests: each well-known discovery request advertises
 * the next {@code jwks_uri} of a fixed sequence, the first discovery response can be held back until a
 * latch is released, and every JWKS path is served the default key set (unless
 * {@link #serveJwks(String, String)} configures its own) while its fetches are counted per path.
 * <p>
 * This lets a test let one initialization resolve a different {@code jwks_uri} than a later one, and
 * hold the first resolution in flight across a {@code close()} and a re-initialization of the loader.
 * <p>
 * Further controls serve the re-discovery tests: a JWKS path can be marked as failing, so its
 * fetches are answered with HTTP 500 (and still counted) until it is restored; a JWKS path can serve its
 * own key set instead of the default one; the discovery document can advertise an override issuer from a
 * given discovery request onward; and a one-shot action can be run while a given discovery request or the
 * next fetch of a given JWKS path is being answered, before its response is sent, so a test can close the
 * loader while one of its requests is in flight.
 */
final class DiscoveryGenerationDispatcher extends Dispatcher {

    /** Path of the discovery document. */
    static final String WELL_KNOWN_PATH = "/.well-known/openid-configuration";

    private static final int GATE_TIMEOUT_SECONDS = 10;

    private final List<String> advertisedJwksUris;
    private final AtomicInteger discoveryRequests = new AtomicInteger();
    private final Map<String, AtomicInteger> jwksFetches = new ConcurrentHashMap<>();
    private final Set<String> failingJwksPaths = ConcurrentHashMap.newKeySet();
    private final Map<String, String> servedJwks = new ConcurrentHashMap<>();
    private final Map<Integer, Runnable> discoveryActions = new ConcurrentHashMap<>();
    private final Map<String, Runnable> jwksFetchActions = new ConcurrentHashMap<>();
    private volatile @Nullable CountDownLatch firstDiscoveryGate;
    private volatile @Nullable IssuerOverride issuerOverride;

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
     * Answers every following fetch of the given JWKS path with HTTP 500 until
     * {@link #restoreJwks(String)} is called. Failed fetches are still counted.
     *
     * @param path the JWKS path, for example {@code /jwks-a.json}
     */
    void failJwks(String path) {
        failingJwksPaths.add(path);
    }

    /**
     * Serves {@code jwks} instead of the default key set for every following successful fetch of the
     * given JWKS path.
     *
     * @param path the JWKS path, for example {@code /jwks-b.json}
     * @param jwks the JWKS document to serve
     */
    void serveJwks(String path, String jwks) {
        servedJwks.put(path, jwks);
    }

    /**
     * Runs {@code action} once, while the discovery request with the given zero-based index is being
     * answered and before its response is sent.
     *
     * @param discoveryIndex the zero-based index of the discovery request
     * @param action         the action to run
     */
    void onDiscovery(int discoveryIndex, Runnable action) {
        discoveryActions.put(discoveryIndex, action);
    }

    /**
     * Runs {@code action} once, while the next fetch of the given JWKS path is being answered and before
     * its response is sent.
     *
     * @param path   the JWKS path, for example {@code /jwks-a.json}
     * @param action the action to run
     */
    void onNextJwksFetch(String path, Runnable action) {
        jwksFetchActions.put(path, action);
    }

    /**
     * Serves the default key set again for a JWKS path marked by {@link #failJwks(String)}.
     *
     * @param path the JWKS path, for example {@code /jwks-a.json}
     */
    void restoreJwks(String path) {
        failingJwksPaths.remove(path);
    }

    /**
     * Advertises {@code issuer} instead of the server's own issuer in the discovery responses from the
     * discovery request with the given zero-based index onward; earlier requests keep the server issuer.
     *
     * @param fromDiscoveryIndex the zero-based index of the first discovery request advertising
     *                           {@code issuer}
     * @param issuer             the issuer to advertise
     */
    void advertiseIssuerFrom(int fromDiscoveryIndex, String issuer) {
        this.issuerOverride = new IssuerOverride(fromDiscoveryIndex, issuer);
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
            runOnce(jwksFetchActions.remove(path));
            if (failingJwksPaths.contains(path)) {
                return new MockResponse(SC_INTERNAL_SERVER_ERROR, Headers.of(), "");
            }
            return new MockResponse(SC_OK, Headers.of("Content-Type", "application/json"),
                    servedJwks.getOrDefault(path, InMemoryJWKSFactory.createDefaultJwks()));
        }
        return new MockResponse(SC_NOT_FOUND, Headers.of(), "");
    }

    private MockResponse discovery(HttpUrl url) {
        int index = discoveryRequests.getAndIncrement();
        String baseUrl = url.newBuilder().encodedPath("/").build().toString();
        String serverIssuer = baseUrl.substring(0, baseUrl.length() - 1);
        String advertised = advertisedJwksUris.get(Math.min(index, advertisedJwksUris.size() - 1));
        String jwksUri = advertised.startsWith("/") ? serverIssuer + advertised : advertised;
        IssuerOverride override = issuerOverride;
        String issuer = override != null && index >= override.fromDiscoveryIndex() ? override.issuer() : serverIssuer;
        if (index == 0) {
            awaitFirstDiscoveryGate();
        }
        runOnce(discoveryActions.remove(index));
        return new MockResponse(SC_OK, Headers.of("Content-Type", "application/json"), """
                {
                  "issuer": "%s",
                  "jwks_uri": "%s"
                }
                """.formatted(issuer, jwksUri));
    }

    private static void runOnce(@Nullable Runnable action) {
        if (action != null) {
            action.run();
        }
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

    /**
     * An issuer advertised from a given discovery request onward.
     *
     * @param fromDiscoveryIndex the zero-based index of the first discovery request advertising it
     * @param issuer             the advertised issuer
     */
    private record IssuerOverride(int fromDiscoveryIndex, String issuer) {
    }
}
