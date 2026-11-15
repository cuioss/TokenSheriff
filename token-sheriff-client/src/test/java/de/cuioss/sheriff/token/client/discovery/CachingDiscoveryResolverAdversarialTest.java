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
package de.cuioss.sheriff.token.client.discovery;

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.validation.test.dispatcher.WellKnownDispatcher;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import lombok.Getter;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adversarial cases for {@link CachingDiscoveryResolver}: a refresh that returns a hostile document
 * is refused by the unchanged {@link DiscoveryResolver} checks and never replaces the cached
 * document, and a refresh stampede after TTL expiry reaches the authorization server exactly once.
 * The mock authorization server speaks TLS, as in {@code DiscoveryAdversarialTest}.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer(useHttps = true)
@DisplayName("CachingDiscoveryResolver adversarial refreshes")
class CachingDiscoveryResolverAdversarialTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration GRACE = Duration.ofMinutes(10);
    private static final int MAX_DOCUMENT_SIZE = 4 * 1024;
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String JSON = "application/json";
    private static final int STAMPEDE_THREADS = 16;

    @Getter
    private final RefreshDispatcher moduleDispatcher = new RefreshDispatcher();

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final InstantSource clock = now::get;

    /** A hostile document served on refresh, each refused by a different {@link DiscoveryResolver} check. */
    enum Attack {
        /** The document names another issuer (mix-up). */
        ISSUER_CHANGED(issuer -> json(document("https://attacker.example", issuer, null, 0))),
        /** An endpoint is downgraded to cleartext inside an https document. */
        ENDPOINT_DOWNGRADED(issuer -> json(document(issuer, issuer, "token_endpoint", 0))),
        /** The document exceeds the configured size bound. */
        OVERSIZED(issuer -> json(document(issuer, issuer, null, 2 * MAX_DOCUMENT_SIZE))),
        /** The document is served with a non-JSON media type. */
        WRONG_MEDIA_TYPE(issuer -> new MockResponse(200, Headers.of(CONTENT_TYPE, "text/html"),
                document(issuer, issuer, null, 0)));

        private final Function<String, MockResponse> response;

        Attack(Function<String, MockResponse> response) {
            this.response = response;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Attack.class)
    @DisplayName("Should refuse a hostile refreshed document and keep serving the cached one within grace")
    void shouldRefuseHostileRefresh(Attack attack, URIBuilder uriBuilder, SSLContext sslContext) {
        var resolver = resolver(uriBuilder, sslContext);
        var good = resolver.resolve();

        advance(TTL);
        moduleDispatcher.serve(attack.response::apply);
        var servedAfterRefusal = resolver.resolve();
        advance(GRACE.minusSeconds(1));
        var servedLater = resolver.resolve();

        assertAll(attack + " refused",
                () -> assertEquals(3, moduleDispatcher.requests(), "the hostile document was fetched twice"),
                () -> assertEquals(good.getIssuer(), servedAfterRefusal.getIssuer(), "issuer unchanged"),
                () -> assertEquals(good.getTokenEndpoint(), servedAfterRefusal.getTokenEndpoint(),
                        "token endpoint unchanged"),
                () -> assertEquals(good.getTokenEndpoint(), servedLater.getTokenEndpoint(),
                        "the cached document was never replaced"),
                () -> assertEquals(DiscoveryCacheStatus.State.FAILED, resolver.status().state()),
                () -> assertEquals(TransportException.class.getName(), resolver.status().lastFailureCause()),
                () -> assertEquals(START, resolver.status().lastSuccess(), "lastSuccess is the good document"));
    }

    @Test
    @DisplayName("Should reach the authorization server exactly once for a refresh stampede")
    void shouldRefreshOnceUnderStampede(URIBuilder uriBuilder, SSLContext sslContext) throws Exception {
        var resolver = resolver(uriBuilder, sslContext);
        var good = resolver.resolve();
        advance(TTL);

        var release = new CountDownLatch(1);
        moduleDispatcher.holdUntil(release);
        var start = new CountDownLatch(1);
        var entered = new CountDownLatch(STAMPEDE_THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(STAMPEDE_THREADS);
        List<ProviderMetadata> results = new ArrayList<>();
        try {
            List<Future<ProviderMetadata>> futures = new ArrayList<>();
            for (int i = 0; i < STAMPEDE_THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    entered.countDown();
                    return resolver.resolve();
                }));
            }
            start.countDown();
            assertTrue(entered.await(10, TimeUnit.SECONDS), "every caller must start");
            assertTrue(moduleDispatcher.awaitHeldRequest(), "the refresh must reach the server");
            release.countDown();
            for (Future<ProviderMetadata> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
        } finally {
            release.countDown();
            pool.shutdownNow();
        }

        Set<ProviderMetadata> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        distinct.addAll(results);
        assertAll("single-flight refresh",
                () -> assertEquals(2, moduleDispatcher.requests(), "exactly one upstream refresh"),
                () -> assertEquals(STAMPEDE_THREADS, results.size()),
                () -> assertTrue(results.stream().allMatch(m -> m.getTokenEndpoint().equals(good.getTokenEndpoint())),
                        "every caller receives equal content"),
                () -> assertEquals(STAMPEDE_THREADS, distinct.size(), "every caller receives its own copy"),
                () -> assertEquals(DiscoveryCacheStatus.State.RESOLVED, resolver.status().state()),
                () -> assertEquals(START.plus(TTL), resolver.status().lastSuccess()));
    }

    private CachingDiscoveryResolver resolver(URIBuilder uriBuilder, SSLContext sslContext) {
        var configuration = ClientConfiguration.builder()
                .issuer(issuer(uriBuilder))
                .clientId(Generators.nonBlankStrings().next())
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .sslContext(sslContext)
                .allowLoopbackEgress(true)
                .discoveryDocumentMaxSize(MAX_DOCUMENT_SIZE)
                .build();
        return new CachingDiscoveryResolver(new DiscoveryResolver(configuration), TTL, GRACE, clock);
    }

    private void advance(Duration duration) {
        now.set(now.get().plus(duration));
    }

    private static String issuer(URIBuilder uriBuilder) {
        String origin = uriBuilder.buildAsString();
        return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
    }

    private static MockResponse json(String body) {
        return new MockResponse(200, Headers.of(CONTENT_TYPE, JSON), body);
    }

    /**
     * Builds a discovery document whose endpoints are rooted at {@code origin}.
     *
     * @param issuerField    the value of the document's {@code issuer} member
     * @param origin         the https origin every endpoint is rooted at
     * @param cleartextField the endpoint to advertise over {@code http}, or {@code null} for none
     * @param paddingBytes   approximate number of bytes to pad the document with, through a real
     *                       list-valued member
     */
    static String document(String issuerField, String origin, @Nullable String cleartextField, int paddingBytes) {
        var json = new StringBuilder("{\"issuer\":\"").append(issuerField).append('"');
        for (String field : List.of("authorization_endpoint", "token_endpoint", "userinfo_endpoint", "jwks_uri")) {
            String root = field.equals(cleartextField) ? "http://idp.invalid" : origin;
            json.append(",\"").append(field).append("\":\"").append(root).append('/').append(field).append('"');
        }
        if (paddingBytes > 0) {
            String entry = "\"" + "x".repeat(64) + "\"";
            json.append(",\"token_endpoint_auth_methods_supported\":[")
                    .append(String.join(",", Collections.nCopies(paddingBytes / 67 + 1, entry)))
                    .append(']');
        }
        return json.append(",\"code_challenge_methods_supported\":[\"S256\"]}").toString();
    }

    /**
     * Serves a good document until a hostile one is scripted, counts every request thread-safely and,
     * on demand, holds a response until the test releases it.
     */
    static final class RefreshDispatcher implements ModuleDispatcherElement {

        private final AtomicInteger requestCount = new AtomicInteger();
        private final CountDownLatch heldRequest = new CountDownLatch(1);
        private volatile @Nullable Function<String, MockResponse> scripted;
        private volatile @Nullable CountDownLatch release;

        void serve(Function<String, MockResponse> response) {
            this.scripted = response;
        }

        void holdUntil(CountDownLatch latch) {
            this.release = latch;
        }

        boolean awaitHeldRequest() throws InterruptedException {
            return heldRequest.await(10, TimeUnit.SECONDS);
        }

        int requests() {
            return requestCount.get();
        }

        @Override
        public String getBaseUrl() {
            return WellKnownDispatcher.LOCAL_PATH;
        }

        @Override
        public Set<HttpMethodMapper> supportedMethods() {
            return Set.of(HttpMethodMapper.GET);
        }

        @Override
        public Optional<MockResponse> handleGet(RecordedRequest request) {
            requestCount.incrementAndGet();
            String url = request.getUrl().toString();
            String issuer = url.substring(0, url.indexOf(WellKnownDispatcher.LOCAL_PATH));
            CountDownLatch latch = release;
            if (latch != null) {
                heldRequest.countDown();
                try {
                    latch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            var response = scripted;
            if (response != null) {
                return Optional.of(response.apply(issuer));
            }
            return Optional.of(json(document(issuer, issuer, null, 0)));
        }
    }
}
