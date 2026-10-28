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

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.EgressPolicy;
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.validation.test.InMemoryJWKSFactory;
import de.cuioss.sheriff.token.validation.test.dispatcher.JwksResolveDispatcher;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import mockwebserver3.MockWebServer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link HttpJwksLoader} re-validates the SSRF egress guard on <em>every</em> background
 * refresh, not only once at initial adapter construction (C1 follow-up, finding 9edd1b).
 * <p>
 * The reused, cached {@link de.cuioss.http.client.adapter.HttpAdapter} means a DNS answer that rebinds
 * the JWKS host into an internal/metadata range after the first fetch would otherwise be trusted. These
 * tests drive the re-check deterministically with an injected {@link EgressPolicy.HostResolver} whose
 * answer flips from a public to a blocked address between the initial load and the refresh, so no real
 * DNS or timing is involved.
 * <p>
 * It also pins that a resolution rejected by the egress guard (or by the issuer check) stores no
 * endpoint, and that the URI the egress guard re-checks is always the URI of the adapter that fetches.
 */
@EnableTestLogger
@EnableMockWebServer
@DisplayName("HttpJwksLoader per-refresh SSRF egress re-validation")
class HttpJwksLoaderEgressRefreshTest {

    private static final String TEST_KID = InMemoryJWKSFactory.DEFAULT_KEY_ID;
    private static final String PUBLIC_IP = "93.184.216.34";
    private static final String METADATA_IP = "169.254.169.254";
    private static final String BLOCKED_HOST = "blocked.example.com";
    private static final String CURRENT_JWKS_PATH = "/jwks-current.json";

    @Getter
    private final JwksResolveDispatcher moduleDispatcher = new JwksResolveDispatcher();

    private SecurityEventCounter securityEventCounter;

    @BeforeEach
    void setUp() {
        moduleDispatcher.setCallCounter(0);
        securityEventCounter = new SecurityEventCounter();
    }

    /**
     * A rebinding resolver: it answers {@link #PUBLIC_IP} until {@code rebound} is flipped, then
     * answers {@link #METADATA_IP}. The actual HTTP fetch still targets the real MockWebServer
     * endpoint — this resolver only feeds the egress gate, so flipping it models a DNS rebind of the
     * fetch host without moving the server.
     */
    private static EgressPolicy reboundPolicy(AtomicBoolean rebound) {
        EgressPolicy.HostResolver resolver = host -> {
            String ip = rebound.get() ? METADATA_IP : PUBLIC_IP;
            return new InetAddress[]{InetAddress.getByName(ip)};
        };
        return EgressPolicy.builder().resolver(resolver).build();
    }

    @Test
    @DisplayName("Should block the refresh fetch when the host rebinds into a metadata address after init")
    void shouldBlockRefreshWhenHostRebinds(URIBuilder uriBuilder) {
        moduleDispatcher.returnDefault();
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowInsecureHttp(true)
                .jwksUrl(jwksEndpoint)
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(0)
                .build();

        AtomicBoolean rebound = new AtomicBoolean(false);
        HttpJwksLoader loader = new HttpJwksLoader(config, reboundPolicy(rebound));
        loader.initJWKSLoader(securityEventCounter).join();

        assertTrue(loader.getKeyInfo(TEST_KID).isPresent(), "Initial load should succeed while the host is public");
        assertEquals(1, moduleDispatcher.getCallCounter(), "Initial load fetches exactly once");

        rebound.set(true);
        loader.performBackgroundRefresh();

        assertEquals(1, moduleDispatcher.getCallCounter(),
                "A rebound host must be rejected by the per-refresh egress re-check before any fetch");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "SSRF egress blocked");

        loader.close();
    }

    @Test
    @DisplayName("Should fetch on refresh when the host still resolves to a permitted address")
    void shouldFetchRefreshWhenHostStillPermitted(URIBuilder uriBuilder) {
        moduleDispatcher.returnDefault();
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowInsecureHttp(true)
                .jwksUrl(jwksEndpoint)
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(0)
                .build();

        AtomicBoolean rebound = new AtomicBoolean(false);
        HttpJwksLoader loader = new HttpJwksLoader(config, reboundPolicy(rebound));
        loader.initJWKSLoader(securityEventCounter).join();

        assertTrue(loader.getKeyInfo(TEST_KID).isPresent(), "Initial load should succeed");
        assertEquals(1, moduleDispatcher.getCallCounter(), "Initial load fetches exactly once");

        loader.performBackgroundRefresh();

        assertEquals(2, moduleDispatcher.getCallCounter(),
                "A still-permitted host must let the refresh proceed to a second fetch");

        loader.close();
    }

    @Test
    @DisplayName("An egress-rejected resolution stores no endpoint and fetches nothing")
    void egressRejectedResolutionStoresNoEndpoint(URIBuilder uriBuilder) {
        moduleDispatcher.returnDefault();
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowInsecureHttp(true)
                .jwksUrl(uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString())
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(0)
                .build();

        try (HttpJwksLoader loader = new HttpJwksLoader(config, reboundPolicy(new AtomicBoolean(true)))) {
            LoaderStatus status = loader.initJWKSLoader(securityEventCounter).join();

            assertAll("a URI rejected by the egress guard is never stored",
                    () -> assertEquals(LoaderStatus.ERROR, status),
                    () -> assertEquals(Optional.empty(), loader.getJwksFetchUri(), "no endpoint may be stored"),
                    () -> assertEquals(0, moduleDispatcher.getCallCounter(), "nothing may be fetched"),
                    () -> assertEquals(Optional.of("test-issuer"), loader.getIssuerIdentifier()));
        }
    }

    @Test
    @DisplayName("An issuer-mismatch rejection stores no endpoint and fetches nothing")
    void issuerMismatchStoresNoEndpoint() throws Exception {
        DiscoveryGenerationDispatcher dispatcher = new DiscoveryGenerationDispatcher(List.of(CURRENT_JWKS_PATH));
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(dispatcher);
            server.start();
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                    .wellKnownUrl(server.url(DiscoveryGenerationDispatcher.WELL_KNOWN_PATH).toString())
                    .issuerIdentifier("https://other-issuer.example.com")
                    .refreshIntervalSeconds(0)
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                LoaderStatus status = loader.initJWKSLoader(securityEventCounter).join();

                assertAll("a rejected discovery is never stored",
                        () -> assertEquals(LoaderStatus.ERROR, status),
                        () -> assertEquals(Optional.empty(), loader.getJwksFetchUri(), "no endpoint may be stored"),
                        () -> assertEquals(0, dispatcher.jwksFetches(CURRENT_JWKS_PATH), "nothing may be fetched"));
            }
        }
    }

    @Test
    @DisplayName("A stale egress-rejected resolution does not pair its URI with the current adapter")
    void staleRejectedResolutionKeepsEndpointConsistent() throws Exception {
        DiscoveryGenerationDispatcher dispatcher = new DiscoveryGenerationDispatcher(
                List.of("http://" + BLOCKED_HOST + "/jwks-stale.json", CURRENT_JWKS_PATH));
        CountDownLatch firstDiscoveryGate = new CountDownLatch(1);
        dispatcher.holdFirstDiscovery(firstDiscoveryGate);

        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(dispatcher);
            server.start();
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                    .wellKnownUrl(server.url(DiscoveryGenerationDispatcher.WELL_KNOWN_PATH).toString())
                    .refreshIntervalSeconds(0)
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config, blockedHostPolicy())) {
                CompletableFuture<LoaderStatus> stale = loader.initJWKSLoader(securityEventCounter);
                Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> dispatcher.discoveryRequests() >= 1);
                loader.close();
                assertEquals(LoaderStatus.OK, loader.initJWKSLoader(securityEventCounter).get(10, TimeUnit.SECONDS));

                firstDiscoveryGate.countDown();
                stale.get(10, TimeUnit.SECONDS);
                loader.performBackgroundRefresh();

                assertAll("the egress check and the fetch use one endpoint of the current generation",
                        () -> assertEquals(Optional.of(CURRENT_JWKS_PATH), loader.getJwksFetchUri().map(URI::getPath)),
                        () -> assertEquals(2, dispatcher.jwksFetches(CURRENT_JWKS_PATH),
                                "the refresh passes the egress check of the current endpoint and fetches it"),
                        () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()));
            }
        } finally {
            firstDiscoveryGate.countDown();
        }
    }

    /**
     * A policy whose resolver answers {@link #METADATA_IP} for {@link #BLOCKED_HOST} and
     * {@link #PUBLIC_IP} for every other host, so only URIs on the blocked host are rejected.
     */
    private static EgressPolicy blockedHostPolicy() {
        EgressPolicy.HostResolver resolver = host -> new InetAddress[]{
                InetAddress.getByName(BLOCKED_HOST.equals(host) ? METADATA_IP : PUBLIC_IP)};
        return EgressPolicy.builder().resolver(resolver).build();
    }
}
