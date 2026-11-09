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
package de.cuioss.sheriff.token.client.internal;

import de.cuioss.sheriff.token.client.auth.ClientSecretBasicAuth;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.DiscoveryResolver;
import de.cuioss.sheriff.token.client.flow.ParClient;
import de.cuioss.sheriff.token.client.flow.TokenEndpointClient;
import de.cuioss.sheriff.token.client.lifecycle.RevocationClient;
import de.cuioss.sheriff.token.client.token.UserInfoClient;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.commons.transport.EgressPolicy;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the host-address egress policy every discovery and back-channel call applies to the URL it
 * actually contacts.
 * <p>
 * The mock authorization server is reachable on loopback. Each test injects an
 * {@link EgressPolicy.HostResolver} through {@link ClientConfiguration#getEgressHostResolver()} that
 * maps the mock server's host onto a disallowed address — the cloud-metadata address
 * {@value #METADATA_ADDRESS} or the site-local address {@value #SITE_LOCAL_ADDRESS}. The HTTP client
 * itself still resolves the real host, so without the policy the request would reach the server: an
 * empty request log is the proof that the call was refused <em>before</em> anything was sent.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("Host-address egress policy on the client back channel")
class BackChannelEgressPolicyTest {

    private static final String METADATA_ADDRESS = "169.254.169.254";
    private static final String SITE_LOCAL_ADDRESS = "10.0.0.1";
    private static final String SSRF_REFUSAL = "SSRF egress blocked";
    private static final String QUERY_SECRET = "client_secret=hunter2";
    private static final String PUBLIC_ISSUER = "https://idp.invalid/realms/demo";
    private static final String REALM_PATH = "/realms/demo";
    private static final String WELL_KNOWN_PATH = REALM_PATH + "/.well-known/openid-configuration";
    private static final String TOKEN_PATH = REALM_PATH + "/protocol/openid-connect/token";
    private static final String PAR_PATH = REALM_PATH + "/protocol/openid-connect/ext/par/request";
    private static final String USERINFO_PATH = REALM_PATH + "/protocol/openid-connect/userinfo";
    private static final String REVOCATION_PATH = REALM_PATH + "/protocol/openid-connect/revoke";
    private static final Map<String, String> CLIENT_CREDENTIALS = Map.of("grant_type", "client_credentials");

    @Getter
    private final RecordingDispatcher moduleDispatcher = new RecordingDispatcher();

    private String serverOrigin;
    private String serverHost;

    @BeforeEach
    void setUp(URIBuilder uriBuilder) {
        moduleDispatcher.reset();
        URI server = URI.create(uriBuilder.buildAsString());
        serverHost = server.getHost();
        serverOrigin = server.getScheme() + "://" + server.getHost() + ":" + server.getPort();
    }

    @Test
    @DisplayName("A token endpoint resolving to the cloud-metadata address is refused before the request is sent")
    void shouldRefuseTokenEndpointOnMetadataAddress() {
        var client = new TokenEndpointClient(mappedConfiguration(METADATA_ADDRESS));

        assertRefusedWithoutContact(() -> client.requestToken(serverUrl(TOKEN_PATH), CLIENT_CREDENTIALS, Map.of()));
    }

    @Test
    @DisplayName("A PAR endpoint resolving to a site-local address is refused before the request is sent")
    void shouldRefuseParEndpointOnSiteLocalAddress() {
        var configuration = mappedConfiguration(SITE_LOCAL_ADDRESS);
        var client = new ParClient(configuration);
        var authentication = authentication(configuration);

        assertRefusedWithoutContact(() -> client.pushAuthorizationRequest(serverUrl(PAR_PATH),
                Map.of("response_type", "code"), authentication));
    }

    @Test
    @DisplayName("A userinfo endpoint resolving to the cloud-metadata address is refused before the request is sent")
    void shouldRefuseUserInfoEndpointOnMetadataAddress() {
        var client = new UserInfoClient(mappedConfiguration(METADATA_ADDRESS));
        var accessToken = Generators.letterStrings(20, 40).next();

        assertRefusedWithoutContact(() -> client.fetchUserInfo(serverUrl(USERINFO_PATH), accessToken));
    }

    @Test
    @DisplayName("A revocation endpoint resolving to a site-local address is refused before the request is sent")
    void shouldRefuseRevocationEndpointOnSiteLocalAddress() {
        var configuration = mappedConfiguration(SITE_LOCAL_ADDRESS);
        var client = new RevocationClient(configuration);
        var token = Generators.letterStrings(20, 40).next();
        var authentication = authentication(configuration);

        assertRefusedWithoutContact(() -> client.revoke(serverUrl(REVOCATION_PATH), token, "refresh_token",
                authentication));
    }

    @Test
    @DisplayName("A discovery document on a host resolving to the cloud-metadata address is refused before it is fetched")
    void shouldRefuseDiscoveryOnMetadataAddress() {
        var resolver = new DiscoveryResolver(baseBuilder(serverUrl(REALM_PATH))
                .allowLoopbackEgress(true)
                .egressHostResolver(mappingServerHostTo(METADATA_ADDRESS))
                .build());

        assertRefusedWithoutContact(resolver::resolve);
    }

    @Test
    @DisplayName("A back-channel origin on a site-local address is refused unless its host is allow-listed")
    void shouldRefuseSiteLocalBackChannelOriginUnlessAllowed() {
        var refused = new TokenEndpointClient(baseBuilder(PUBLIC_ISSUER)
                .backChannelOrigin(serverOrigin)
                .egressHostResolver(mappingServerHostTo(SITE_LOCAL_ADDRESS))
                .build());
        var advertised = "https://idp.invalid" + TOKEN_PATH;

        assertRefusedWithoutContact(() -> refused.requestToken(advertised, CLIENT_CREDENTIALS, Map.of()));

        var allowed = new TokenEndpointClient(baseBuilder(PUBLIC_ISSUER)
                .backChannelOrigin(serverOrigin)
                .egressHostResolver(mappingServerHostTo(SITE_LOCAL_ADDRESS))
                .allowedEgressHost(serverHost)
                .build());

        assertDoesNotThrow(() -> allowed.requestToken(advertised, CLIENT_CREDENTIALS, Map.of()));
        assertEquals(List.of(TOKEN_PATH), moduleDispatcher.paths(),
                "an allow-listed back-channel origin must be reached at the advertised path");
    }

    @Test
    @DisplayName("A loopback endpoint is refused by default and admitted only by the loopback opt-in")
    void shouldAdmitLoopbackOnlyWithOptIn() {
        var secureDefault = new TokenEndpointClient(baseBuilder(serverUrl(REALM_PATH)).build());

        assertRefusedWithoutContact(() -> secureDefault.requestToken(serverUrl(TOKEN_PATH), CLIENT_CREDENTIALS,
                Map.of()));

        var optedIn = new TokenEndpointClient(baseBuilder(serverUrl(REALM_PATH)).allowLoopbackEgress(true).build());

        assertDoesNotThrow(() -> optedIn.requestToken(serverUrl(TOKEN_PATH), CLIENT_CREDENTIALS, Map.of()));
        assertEquals(List.of(TOKEN_PATH), moduleDispatcher.paths(), "the loopback opt-in must admit the loopback server");
    }

    @Test
    @DisplayName("The loopback opt-in does not admit a link-local address")
    void shouldNotLetLoopbackOptInAdmitLinkLocal() {
        // mappedConfiguration sets the loopback opt-in; the metadata address is link-local, not loopback.
        // The PAR client is used here so the opt-in is shown to hold on a second back-channel endpoint kind.
        var configuration = mappedConfiguration(METADATA_ADDRESS);
        var client = new ParClient(configuration);
        var authentication = authentication(configuration);

        assertRefusedWithoutContact(() -> client.pushAuthorizationRequest(serverUrl(PAR_PATH),
                Map.of("response_type", "code"), authentication));
    }

    @Test
    @DisplayName("The allowed-host opt-in admits only the hosts it names")
    void shouldAdmitOnlyNamedAllowedHosts() {
        var client = new TokenEndpointClient(baseBuilder(serverUrl(REALM_PATH))
                .egressHostResolver(mappingServerHostTo(SITE_LOCAL_ADDRESS))
                .allowedEgressHost("other-host.invalid")
                .build());

        assertRefusedWithoutContact(() -> client.requestToken(serverUrl(TOKEN_PATH), CLIENT_CREDENTIALS, Map.of()));
    }

    @Test
    @DisplayName("A refusal carries no part of the contacted URL, neither in its message nor in its cause chain")
    void shouldNotEchoTheContactedUrlInTheRefusal() {
        var client = new TokenEndpointClient(mappedConfiguration(METADATA_ADDRESS));
        String endpointWithSecrets = serverUrl(TOKEN_PATH) + "?" + QUERY_SECRET;

        var thrown = assertThrows(TransportException.class,
                () -> client.requestToken(endpointWithSecrets, CLIENT_CREDENTIALS, Map.of()));

        assertTrue(causeChainMentions(thrown, SSRF_REFUSAL), "the refusal must name its reason");
        assertFalse(causeChainMentions(thrown, "hunter2"), "the query secret must not be echoed");
        assertFalse(causeChainMentions(thrown, TOKEN_PATH), "the URL path must not be echoed");
        assertFalse(causeChainMentions(thrown, serverHost), "the contacted host must not be echoed");
    }

    private void assertRefusedWithoutContact(Executable call) {
        var thrown = assertThrows(TransportException.class, call,
                "the host-address egress policy must refuse the contacted host");
        assertTrue(causeChainMentions(thrown, SSRF_REFUSAL),
                "the refusal must be the SSRF-blocked branch, but the message was: " + thrown.getMessage());
        assertTrue(moduleDispatcher.paths().isEmpty(),
                "a refused call must never reach the server, but it received: " + moduleDispatcher.paths());
    }

    private static boolean causeChainMentions(Throwable thrown, String fragment) {
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A configuration for an issuer on the mock server, with the loopback opt-in set so that only the
     * injected mapping to {@code address} can make the policy refuse.
     */
    private ClientConfiguration mappedConfiguration(String address) {
        return baseBuilder(serverUrl(REALM_PATH))
                .allowLoopbackEgress(true)
                .egressHostResolver(mappingServerHostTo(address))
                .build();
    }

    private static ClientConfiguration.ClientConfigurationBuilder baseBuilder(String issuer) {
        return ClientConfiguration.builder()
                .issuer(issuer)
                .clientId(Generators.letterStrings(8, 16).next())
                .clientSecret(Generators.letterStrings(16, 32).next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .allowInsecureHttp(true);
    }

    /**
     * Resolves the mock server's host to {@code address} and every other host as the JDK would. The
     * address literal is parsed, never looked up.
     */
    private EgressPolicy.HostResolver mappingServerHostTo(String address) {
        String mappedHost = serverHost;
        return host -> mappedHost.equalsIgnoreCase(host)
                ? new InetAddress[]{InetAddress.getByName(address)}
                : resolveDefault(host);
    }

    private static InetAddress[] resolveDefault(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }

    private static ClientSecretBasicAuth authentication(ClientConfiguration configuration) {
        return new ClientSecretBasicAuth(configuration.getClientId(), configuration.getClientSecret());
    }

    private String serverUrl(String path) {
        return serverOrigin + path;
    }

    /**
     * Records every request and answers each endpoint with a minimal successful response, so a test can
     * tell a refused call (nothing recorded) from one that was sent.
     */
    static final class RecordingDispatcher implements ModuleDispatcherElement {

        private final List<String> recordedPaths = new CopyOnWriteArrayList<>();

        void reset() {
            recordedPaths.clear();
        }

        List<String> paths() {
            return List.copyOf(recordedPaths);
        }

        @Override
        public String getBaseUrl() {
            return REALM_PATH;
        }

        @Override
        public Set<HttpMethodMapper> supportedMethods() {
            return Set.of(HttpMethodMapper.GET, HttpMethodMapper.POST);
        }

        @Override
        public Optional<MockResponse> handleGet(RecordedRequest request) {
            String path = request.getUrl().encodedPath();
            recordedPaths.add(path);
            return switch (path) {
                case WELL_KNOWN_PATH -> json("{\"issuer\":\"unused\"}");
                case USERINFO_PATH -> json("{\"sub\":\"alice\"}");
                default -> Optional.of(new MockResponse(404, Headers.of(), ""));
            };
        }

        @Override
        public Optional<MockResponse> handlePost(RecordedRequest request) {
            String path = request.getUrl().encodedPath();
            recordedPaths.add(path);
            return switch (path) {
                case TOKEN_PATH -> json("{\"access_token\":\"opaque-access-token\",\"token_type\":\"Bearer\","
                        + "\"expires_in\":300}");
                case PAR_PATH -> json("{\"request_uri\":\"urn:ietf:params:oauth:request_uri:egress\","
                        + "\"expires_in\":90}");
                case REVOCATION_PATH -> Optional.of(new MockResponse(200, Headers.of(), ""));
                default -> Optional.of(new MockResponse(404, Headers.of(), ""));
            };
        }

        private static Optional<MockResponse> json(String body) {
            return Optional.of(new MockResponse(200, Headers.of("Content-Type", "application/json"), body));
        }
    }
}
