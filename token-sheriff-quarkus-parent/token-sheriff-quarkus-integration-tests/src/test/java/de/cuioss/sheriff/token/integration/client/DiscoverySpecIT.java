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
package de.cuioss.sheriff.token.integration.client;

import de.cuioss.sheriff.token.client.discovery.CachingDiscoveryResolver;
import de.cuioss.sheriff.token.client.discovery.DiscoveryCacheStatus;
import de.cuioss.sheriff.token.client.discovery.DiscoveryResolver;
import de.cuioss.sheriff.token.client.discovery.ProviderMetadata;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.integration.BaseIntegrationTest;
import de.cuioss.tools.logging.CuiLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Increment-1 discovery spec against the real Keycloak container ({@code CLIENT-14}).
 * <p>
 * Verifies that the integration realm serves a well-formed OpenID Connect discovery document with
 * the endpoints and PKCE {@code S256} capability the client engine consumes. The container uses a
 * self-signed certificate, so the request runs against a trust-all TLS context (integration-test
 * scope only), mirroring {@link BaseIntegrationTest}'s relaxed HTTPS posture.
 * <p>
 * A second case resolves the {@code client-engine} realm through the production engine
 * {@link DiscoveryResolver} wrapped in a {@link CachingDiscoveryResolver}, over the chain-validating
 * trust material {@link RefreshEngineSupport} assembles: the realm is fetched once, the resolutions
 * within the TTL return equal, independent copies that a caller cannot use to change the cached
 * document, and the cache reports itself resolved.
 */
@DisplayName("OIDC discovery against real Keycloak")
class DiscoverySpecIT extends BaseIntegrationTest {

    private static final CuiLogger LOGGER = new CuiLogger(DiscoverySpecIT.class);

    // The discovery document is fetched over the host-mapped public base, which is also the static
    // KC_HOSTNAME Keycloak advertises the issuer under.
    private static final String WELL_KNOWN =
            KeycloakUrlSupport.EXTERNAL_BASE + "/realms/integration/.well-known/openid-configuration";
    private static final String EXPECTED_ISSUER = KeycloakUrlSupport.EXTERNAL_BASE + "/realms/integration";

    // The client of the client-engine realm RefreshEngineSupport runs its own discovery round trip under;
    // discovery itself is unauthenticated.
    private static final String CACHED_DISCOVERY_CLIENT_ID = "integration-client";
    private static final String CACHED_DISCOVERY_CLIENT_SECRET = "integration-secret";

    @Test
    @DisplayName("Should serve a discovery document with the expected endpoints and S256 support")
    void shouldServeDiscoveryDocument() throws Exception {
        var response = fetch(WELL_KNOWN);

        assertEquals(200, response.statusCode(), "discovery document should be served");
        String body = response.body();
        LOGGER.debug("Resolved discovery document: %s", body);

        assertAll("discovery document contents",
                () -> assertTrue(body.contains("\"issuer\""), "issuer present"),
                () -> assertTrue(body.contains(EXPECTED_ISSUER),
                        "issuer names the static public hostname (" + EXPECTED_ISSUER + ")"),
                () -> assertTrue(body.contains("\"token_endpoint\""), "token_endpoint present"),
                () -> assertTrue(body.contains("\"authorization_endpoint\""), "authorization_endpoint present"),
                () -> assertTrue(body.contains("\"jwks_uri\""), "jwks_uri present"),
                () -> assertTrue(body.contains("S256"), "PKCE S256 advertised (CLIENT-2 groundwork)"));
    }

    @Test
    @DisplayName("Should serve a cached engine-level resolution within the TTL as independent, equal copies")
    void shouldServeCachedEngineResolution() {
        // The integration-test module does not wire the Quarkus client extension, so the production
        // engine resolver is assembled exactly as RefreshEngineSupport does and wrapped in the cache.
        Instant now = Instant.now();
        AtomicInteger upstreamResolutions = new AtomicInteger();
        DiscoveryResolver countingDelegate = new DiscoveryResolver(RefreshEngineSupport.clientConfiguration(
                CACHED_DISCOVERY_CLIENT_ID, CACHED_DISCOVERY_CLIENT_SECRET)) {
            @Override
            public ProviderMetadata resolve() {
                upstreamResolutions.incrementAndGet();
                return super.resolve();
            }
        };
        CachingDiscoveryResolver resolver = new CachingDiscoveryResolver(countingDelegate,
                Duration.ofMinutes(5), Duration.ZERO, InstantSource.fixed(now));

        ProviderMetadata first = resolver.resolve();
        ProviderMetadata second = resolver.resolve();
        String servedTokenEndpoint = first.tokenEndpoint;
        first.tokenEndpoint = "https://mutated.invalid/token";
        ProviderMetadata third = resolver.resolve();
        DiscoveryCacheStatus status = resolver.status();

        assertAll("cached engine-level resolution",
                () -> assertEquals(1, upstreamResolutions.get(),
                        "the realm is fetched once; the later resolutions within the TTL are served from the cache"),
                () -> assertNotSame(first, second, "each resolution hands out a defensive copy"),
                () -> assertEquals(servedTokenEndpoint, third.tokenEndpoint,
                        "a change to a handed-out copy does not reach the cached document"),
                () -> assertEquals(RefreshEngineSupport.ISSUER, first.issuer,
                        "the resolved issuer is the realm's advertised identity"),
                () -> assertEquals(first.issuer, second.issuer, "issuer is content-equal"),
                () -> assertEquals(first.authorizationEndpoint, second.authorizationEndpoint,
                        "authorization endpoint is content-equal"),
                () -> assertEquals(servedTokenEndpoint, second.tokenEndpoint, "token endpoint is content-equal"),
                () -> assertEquals(first.jwksUri, second.jwksUri, "jwks_uri is content-equal"),
                () -> assertEquals(first.revocationEndpoint, second.revocationEndpoint,
                        "revocation endpoint is content-equal"),
                () -> assertEquals(first.codeChallengeMethodsSupported, second.codeChallengeMethodsSupported,
                        "advertised PKCE methods are content-equal"),
                () -> assertEquals(DiscoveryCacheStatus.State.RESOLVED, status.state(),
                        "the cache reports the real-realm resolution as RESOLVED"),
                () -> assertEquals(now, status.lastSuccess(),
                        "the single resolution is stamped with the injected clock's instant"),
                () -> assertNull(status.lastFailureCause(), "no resolution attempt failed"),
                () -> assertEquals(LoaderStatus.OK, resolver.getLoaderStatus(),
                        "a resolved cache reports its loader status as OK"));
    }

    private static HttpResponse<String> fetch(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .sslContext(trustAllContext())
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static SSLContext trustAllContext() throws Exception {
        TrustManager[] trustAll = {new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // integration-test trust-all
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // integration-test trust-all
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustAll, new SecureRandom());
        return context;
    }
}
