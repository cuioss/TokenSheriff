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
package de.cuioss.sheriff.token.client.lifecycle;

import de.cuioss.sheriff.token.client.auth.ClientAuthentication;
import de.cuioss.sheriff.token.client.auth.ClientSecretBasicAuth;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.ProviderMetadata;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import lombok.Getter;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("RevocationClient RFC 7009 authenticated revocation")
class RevocationClientTest {

    @Getter
    private final RevocationDispatcher moduleDispatcher = new RevocationDispatcher();

    @BeforeEach
    void setUp() {
        moduleDispatcher.reset();
    }

    private static ClientConfiguration config() {
        return ClientConfiguration.builder()
                .issuer("https://" + Generators.letterStrings(3, 10).next() + ".example.com")
                .clientId(Generators.letterStrings(5, 12).next())
                .clientSecret(Generators.letterStrings(8, 20).next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(true)
                .allowLoopbackEgress(true)
                .build();
    }

    private static String endpoint(URIBuilder uriBuilder) {
        return uriBuilder.addPathSegment("revoke").buildAsString();
    }

    private static ClientSecretBasicAuth auth(ClientConfiguration config) {
        return new ClientSecretBasicAuth(config.getClientId(), config.getClientSecret());
    }

    private static ProviderMetadata metadataWithRevocationEndpoint(@Nullable String revocationEndpoint) {
        var metadata = new ProviderMetadata();
        metadata.revocationEndpoint = revocationEndpoint;
        return metadata;
    }

    @Test
    @DisplayName("Should send an authenticated revocation carrying the token and type hint")
    void shouldSendAuthenticatedRevocation(URIBuilder uriBuilder, MockWebServer server) throws Exception {
        moduleDispatcher.status(200);
        var config = config();
        String token = Generators.letterStrings(20, 40).next();

        new RevocationClient(config).revoke(endpoint(uriBuilder), token, "refresh_token", auth(config));

        RecordedRequest request = server.takeRequest();
        String body = request.getBody() == null ? "" : request.getBody().utf8();
        assertAll("revocation request",
                () -> assertTrue(body.contains("token=" + token), "the token to revoke must be sent"),
                () -> assertTrue(body.contains("token_type_hint=refresh_token"),
                        "the RFC 7009 token_type_hint must be sent when provided"),
                () -> assertNotNull(request.getHeaders().get("Authorization"),
                        "client authentication must decorate the revocation request"));
    }

    @Test
    @DisplayName("Should omit the token_type_hint when it is null")
    void shouldOmitTypeHintWhenNull(URIBuilder uriBuilder, MockWebServer server) throws Exception {
        moduleDispatcher.status(200);
        var config = config();
        String token = Generators.letterStrings(20, 40).next();

        new RevocationClient(config).revoke(endpoint(uriBuilder), token, null, auth(config));

        RecordedRequest request = server.takeRequest();
        String body = request.getBody() == null ? "" : request.getBody().utf8();
        assertFalse(body.contains("token_type_hint"), "an absent token_type_hint must not be sent");
    }

    @Test
    @DisplayName("Should treat any 2xx as success per RFC 7009 §2.2")
    void shouldTreatNoContentAsSuccess(URIBuilder uriBuilder) {
        moduleDispatcher.status(204);
        var config = config();
        var revocationEndpoint = endpoint(uriBuilder);
        var clientAuth = auth(config);
        String token = Generators.letterStrings(20, 40).next();

        assertDoesNotThrow(() -> new RevocationClient(config).revoke(revocationEndpoint, token, null, clientAuth));
    }

    @Test
    @DisplayName("Should surface a TransportException on a non-success status")
    void shouldSurfaceTransportExceptionOnError(URIBuilder uriBuilder) {
        moduleDispatcher.status(400);
        var config = config();
        var client = new RevocationClient(config);
        var revocationEndpoint = endpoint(uriBuilder);
        var clientAuth = auth(config);
        String token = Generators.letterStrings(20, 40).next();

        assertThrows(TransportException.class,
                () -> client.revoke(revocationEndpoint, token, "access_token", clientAuth));
    }

    @Test
    @DisplayName("Should reject null construction arguments")
    void shouldRejectNullConfiguration() {
        assertThrows(NullPointerException.class, () -> new RevocationClient(null));
    }

    @Test
    @DisplayName("Should reject null and blank revocation arguments")
    void shouldRejectInvalidArguments(URIBuilder uriBuilder) {
        var config = config();
        var client = new RevocationClient(config);
        var revocationEndpoint = endpoint(uriBuilder);
        var clientAuth = auth(config);
        String token = Generators.letterStrings(20, 40).next();

        assertAll("argument validation",
                () -> assertThrows(NullPointerException.class,
                        () -> client.revoke(null, token, null, clientAuth)),
                () -> assertThrows(NullPointerException.class,
                        () -> client.revoke(revocationEndpoint, null, null, clientAuth)),
                () -> assertThrows(NullPointerException.class,
                        () -> client.revoke(revocationEndpoint, token, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> client.revoke(revocationEndpoint, "   ", null, clientAuth)));
    }

    @Test
    @DisplayName("revokeIfSupported should send one revocation and return true when an endpoint is advertised")
    void revokeIfSupportedShouldRevokeWhenEndpointAdvertised(URIBuilder uriBuilder, MockWebServer server)
            throws Exception {
        moduleDispatcher.status(200);
        var config = config();
        var metadata = metadataWithRevocationEndpoint(endpoint(uriBuilder));
        String token = Generators.letterStrings(20, 40).next();

        boolean revoked = new RevocationClient(config).revokeIfSupported(metadata, token, "refresh_token",
                auth(config));

        assertTrue(revoked, "an advertised revocation endpoint must be used and reported");
        assertEquals(1, server.getRequestCount(), "exactly one revocation request must be sent");
        RecordedRequest request = server.takeRequest();
        String body = request.getBody() == null ? "" : request.getBody().utf8();
        assertAll("delegated revocation request",
                () -> assertTrue(body.contains("token=" + token), "the token to revoke must be sent"),
                () -> assertTrue(body.contains("token_type_hint=refresh_token"),
                        "the RFC 7009 token_type_hint must be sent when provided"));
    }

    @Test
    @DisplayName("revokeIfSupported should send nothing and return false when no endpoint is advertised")
    void revokeIfSupportedShouldSkipWhenNoEndpointAdvertised(MockWebServer server) {
        var config = config();
        var metadata = metadataWithRevocationEndpoint(null);
        String token = Generators.letterStrings(20, 40).next();

        boolean revoked = new RevocationClient(config).revokeIfSupported(metadata, token, null, auth(config));

        assertFalse(revoked, "an absent revocation endpoint must be reported as not revoked");
        assertEquals(0, server.getRequestCount(), "no request may be sent without a revocation endpoint");
    }

    @Test
    @DisplayName("revokeIfSupported should propagate a TransportException from a failing endpoint")
    void revokeIfSupportedShouldPropagateTransportException(URIBuilder uriBuilder) {
        moduleDispatcher.status(400);
        var config = config();
        var client = new RevocationClient(config);
        var metadata = metadataWithRevocationEndpoint(endpoint(uriBuilder));
        var clientAuth = auth(config);
        String token = Generators.letterStrings(20, 40).next();

        assertThrows(TransportException.class,
                () -> client.revokeIfSupported(metadata, token, "access_token", clientAuth));
    }

    @Test
    @DisplayName("revokeIfSupported should delegate to an overridden revoke (ADR-0006)")
    void revokeIfSupportedShouldDelegateToOverriddenRevoke(MockWebServer server) {
        var config = config();
        List<String> recorded = new ArrayList<>();
        var client = new RevocationClient(config) {
            @Override
            public void revoke(String revocationEndpoint, String token, @Nullable String tokenTypeHint,
                    ClientAuthentication clientAuthentication) {
                recorded.add(revocationEndpoint + "|" + token + "|" + tokenTypeHint);
            }
        };
        String revocationEndpoint = "https://" + Generators.letterStrings(3, 10).next() + ".example.com/revoke";
        var metadata = metadataWithRevocationEndpoint(revocationEndpoint);
        String token = Generators.letterStrings(20, 40).next();

        boolean revoked = client.revokeIfSupported(metadata, token, "refresh_token", auth(config));

        assertTrue(revoked, "the delegated call must be reported as a revocation");
        assertEquals(List.of(revocationEndpoint + "|" + token + "|refresh_token"), recorded,
                "the overridden revoke must observe exactly the delegated call");
        assertEquals(0, server.getRequestCount(), "the overriding subclass owns the transport");
    }

    @Test
    @DisplayName("revokeIfSupported should reject invalid arguments whatever the metadata advertises")
    void revokeIfSupportedShouldRejectInvalidArguments(MockWebServer server) {
        var config = config();
        var client = new RevocationClient(config);
        var withoutEndpoint = metadataWithRevocationEndpoint(null);
        var clientAuth = auth(config);
        String token = Generators.letterStrings(20, 40).next();

        assertAll("argument validation",
                () -> assertThrows(NullPointerException.class,
                        () -> client.revokeIfSupported(null, token, null, clientAuth)),
                () -> assertThrows(NullPointerException.class,
                        () -> client.revokeIfSupported(withoutEndpoint, null, null, clientAuth)),
                () -> assertThrows(NullPointerException.class,
                        () -> client.revokeIfSupported(withoutEndpoint, token, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> client.revokeIfSupported(withoutEndpoint, "   ", null, clientAuth)));
        assertEquals(0, server.getRequestCount(), "a rejected call must send nothing");
    }

    /**
     * Revocation-endpoint dispatcher serving a configurable HTTP status.
     */
    static final class RevocationDispatcher implements ModuleDispatcherElement {

        private static final int HTTP_OK = 200;

        private int status = HTTP_OK;

        void reset() {
            this.status = HTTP_OK;
        }

        void status(int status) {
            this.status = status;
        }

        @Override
        public String getBaseUrl() {
            return "/revoke";
        }

        @Override
        public Set<HttpMethodMapper> supportedMethods() {
            return Set.of(HttpMethodMapper.POST);
        }

        @Override
        public Optional<MockResponse> handlePost(RecordedRequest request) {
            return Optional.of(new MockResponse(status, Headers.of("Content-Type", "application/json"), ""));
        }
    }
}
