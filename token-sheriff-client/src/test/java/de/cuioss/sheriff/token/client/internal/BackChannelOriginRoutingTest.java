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
import de.cuioss.sheriff.token.client.discovery.ProviderMetadata;
import de.cuioss.sheriff.token.client.dpop.DpopProofGenerator;
import de.cuioss.sheriff.token.client.dpop.SenderConstraint;
import de.cuioss.sheriff.token.client.flow.AuthorizationRequestBuilder;
import de.cuioss.sheriff.token.client.flow.FlowContext;
import de.cuioss.sheriff.token.client.flow.ParClient;
import de.cuioss.sheriff.token.client.flow.TokenEndpointClient;
import de.cuioss.sheriff.token.client.lifecycle.RevocationClient;
import de.cuioss.sheriff.token.client.logout.EndSessionFlow;
import de.cuioss.sheriff.token.client.logout.PostLogoutRedirectValidator;
import de.cuioss.sheriff.token.client.token.UserInfoClient;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end routing coverage for {@code ClientConfiguration.backChannelOrigin} over a live mock
 * authorization server.
 * <p>
 * The issuer and every advertised endpoint live on {@value #PUBLIC_ORIGIN}, an origin under the
 * reserved {@code .invalid} TLD that can never be resolved, while the back-channel origin points at
 * the mock server. A request recorded by the mock server is therefore proof that the call was
 * retargeted; the recorded path proves the advertised path was kept. The assertions on the resolved
 * metadata and the front-channel URLs pin the other half of the contract: the public identity is never
 * rewritten. A DPoP proof is the exception by design: its {@code htu} names the target URI of the request
 * it travels with (RFC 9449 §4.2), so on a mapped call it carries the back-channel origin with the
 * advertised path.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("Back-channel origin routing against a mock authorization server")
class BackChannelOriginRoutingTest {

    private static final String PUBLIC_ORIGIN = "https://idp.invalid";
    private static final String REALM_PATH = "/realms/demo";
    private static final String ISSUER = PUBLIC_ORIGIN + REALM_PATH;
    private static final String WELL_KNOWN_PATH = REALM_PATH + "/.well-known/openid-configuration";
    private static final String TOKEN_PATH = REALM_PATH + "/protocol/openid-connect/token";
    private static final String PAR_PATH = REALM_PATH + "/protocol/openid-connect/ext/par/request";
    private static final String USERINFO_PATH = REALM_PATH + "/protocol/openid-connect/userinfo";
    private static final String REVOCATION_PATH = REALM_PATH + "/protocol/openid-connect/revoke";
    private static final String AUTHORIZATION_PATH = REALM_PATH + "/protocol/openid-connect/auth";
    private static final String END_SESSION_PATH = REALM_PATH + "/protocol/openid-connect/logout";
    private static final String REDIRECT_URI = "https://rp.example.com/callback";
    private static final String POST_LOGOUT_REDIRECT = "https://rp.example.com/postlogout";
    private static final String HTTP_ISSUER = "http://idp.invalid" + REALM_PATH;
    private static final String UNREACHABLE_HTTPS_ORIGIN = "https://127.0.0.1:1";
    private static final String NON_TLS_ISSUER_REFUSAL = "Rejecting non-TLS issuer";

    @Getter
    private final RealmDispatcher moduleDispatcher = new RealmDispatcher();

    private ClientConfiguration configuration;
    private String backChannelOrigin;

    @BeforeEach
    void setUp(URIBuilder uriBuilder) {
        moduleDispatcher.reset();
        URI server = URI.create(uriBuilder.buildAsString());
        backChannelOrigin = server.getScheme() + "://" + server.getHost() + ":" + server.getPort();
        configuration = ClientConfiguration.builder()
                .issuer(ISSUER)
                .clientId(Generators.letterStrings(8, 16).next())
                .clientSecret(Generators.letterStrings(16, 32).next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .redirectUri(REDIRECT_URI)
                .backChannelOrigin(backChannelOrigin)
                .allowInsecureHttp(true)
                .build();
    }

    @Test
    @DisplayName("Discovery reaches the back-channel origin at the advertised path and keeps the public metadata")
    void shouldRouteDiscoveryAndKeepPublicMetadata() {
        ProviderMetadata metadata = new DiscoveryResolver(configuration).resolve();

        assertAll("discovery retargeted, identity untouched",
                () -> assertEquals(List.of(WELL_KNOWN_PATH), moduleDispatcher.paths()),
                () -> assertEquals(ISSUER, metadata.getIssuer().orElseThrow(),
                        "the discovery-document issuer still matches the configured public issuer"),
                () -> assertEquals(publicUrl(TOKEN_PATH), metadata.getTokenEndpoint().orElseThrow(),
                        "the resolved metadata keeps the published public endpoint"),
                () -> assertEquals(publicUrl(USERINFO_PATH), metadata.getUserinfoEndpoint().orElseThrow()),
                () -> assertEquals(publicUrl(AUTHORIZATION_PATH),
                        metadata.getAuthorizationEndpoint().orElseThrow()));
    }

    @Test
    @DisplayName("Discovery still rejects a document whose issuer differs from the configured issuer")
    void shouldStillRejectIssuerMismatch() {
        moduleDispatcher.advertiseIssuer("https://other.invalid" + REALM_PATH);
        var resolver = new DiscoveryResolver(configuration);

        assertThrows(TransportException.class, resolver::resolve,
                "the back-channel origin must not relax the OpenID Connect Discovery §4.3 issuer check");
        assertEquals(List.of(WELL_KNOWN_PATH), moduleDispatcher.paths(),
                "the document was fetched from the back-channel origin and then refused");
    }

    @Test
    @DisplayName("An http issuer is refused at discovery without allowInsecureHttp, even with an https back-channel origin")
    void shouldRefuseHttpIssuerDespiteHttpsBackChannelOrigin() {
        var resolver = new DiscoveryResolver(httpIssuerWithHttpsBackChannel(false));

        var thrown = assertThrows(TransportException.class, resolver::resolve,
                "the issuer-scheme check applies to the configured issuer, not the back-channel origin");
        assertTrue(thrown.getMessage().contains(NON_TLS_ISSUER_REFUSAL),
                "discovery must fail on the non-TLS issuer check, but the message was: " + thrown.getMessage());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, NON_TLS_ISSUER_REFUSAL);
    }

    @Test
    @DisplayName("An http issuer with allowInsecureHttp and an https back-channel origin passes the issuer-scheme check")
    void shouldPassIssuerSchemeCheckForHttpIssuerWhenInsecureHttpAllowed() {
        // Same setup as the refusal case, with allowInsecureHttp=true: discovery now goes past the
        // issuer-scheme check and fails only when the unreachable back-channel origin refuses the
        // connection.
        var resolver = new DiscoveryResolver(httpIssuerWithHttpsBackChannel(true));

        var thrown = assertThrows(TransportException.class, resolver::resolve,
                "the unreachable back-channel origin must fail the discovery request");
        assertFalse(thrown.getMessage().contains(NON_TLS_ISSUER_REFUSAL),
                "with allowInsecureHttp the issuer-scheme check must pass, but the message was: "
                        + thrown.getMessage());
    }

    @Test
    @DisplayName("The token request reaches the back-channel origin at the advertised path")
    void shouldRouteTokenRequest() {
        var client = new TokenEndpointClient(configuration);

        var response = client.requestToken(publicUrl(TOKEN_PATH),
                Map.of("grant_type", "client_credentials"), Map.of());

        assertAll("token request retargeted",
                () -> assertNotNull(response),
                () -> assertEquals(List.of(TOKEN_PATH), moduleDispatcher.paths()));
    }

    @Test
    @DisplayName("A DPoP proof sent to the rewritten token endpoint names the back-channel URL actually contacted as htu")
    void shouldUseBackChannelUrlAsDpopHtuForTokenRequest() throws Exception {
        var client = new TokenEndpointClient(configuration);

        client.requestToken(publicUrl(TOKEN_PATH), Map.of("grant_type", "client_credentials"), Map.of(),
                dpopConstraint());

        assertProofNamesBackChannelUrl(TOKEN_PATH);
    }

    @Test
    @DisplayName("Both DPoP proofs of a use_dpop_nonce retry on a rewritten token endpoint name the back-channel URL as htu")
    void shouldUseBackChannelUrlAsDpopHtuForNonceRetry() throws Exception {
        var nonce = Generators.letterStrings(16, 32).next();
        moduleDispatcher.challengeNextTokenRequestWithNonce(nonce);
        var client = new TokenEndpointClient(configuration);

        var response = client.requestToken(publicUrl(TOKEN_PATH), Map.of("grant_type", "client_credentials"),
                Map.of(), dpopConstraint());

        assertNotNull(response, "the retried request must succeed");
        assertEquals(List.of(TOKEN_PATH, TOKEN_PATH), moduleDispatcher.paths(),
                "the first attempt and the nonce retry must both reach the back-channel origin");
        String firstPayload = proofPayload(moduleDispatcher.requests().getFirst());
        String retryPayload = proofPayload(moduleDispatcher.requests().get(1));
        String expectedHtu = "\"htu\":\"" + backChannelOrigin + TOKEN_PATH + "\"";
        assertAll("both proofs name the URL actually contacted",
                () -> assertTrue(firstPayload.contains(expectedHtu),
                        "the first proof's htu must name the back-channel URL, but the payload was: " + firstPayload),
                () -> assertFalse(firstPayload.contains(PUBLIC_ORIGIN),
                        "the first proof's htu must not name the advertised public URL, but the payload was: "
                                + firstPayload),
                () -> assertTrue(retryPayload.contains(expectedHtu),
                        "the retry proof's htu must name the back-channel URL, but the payload was: " + retryPayload),
                () -> assertFalse(retryPayload.contains(PUBLIC_ORIGIN),
                        "the retry proof's htu must not name the advertised public URL, but the payload was: "
                                + retryPayload),
                () -> assertTrue(retryPayload.contains("\"nonce\":\"" + nonce + "\""),
                        "the retry proof must echo the challenged nonce, but the payload was: " + retryPayload));
    }

    @Test
    @DisplayName("A DPoP proof sent to the rewritten userinfo endpoint names the back-channel URL actually contacted as htu")
    void shouldUseBackChannelUrlAsDpopHtuForUserInfoRequest() throws Exception {
        var client = new UserInfoClient(configuration);

        var userInfo = client.fetchUserInfo(publicUrl(USERINFO_PATH), Generators.letterStrings(20, 40).next(),
                dpopConstraint());

        assertEquals("alice", userInfo.getSub().orElseThrow());
        assertProofNamesBackChannelUrl(USERINFO_PATH);
    }

    @Test
    @DisplayName("The pushed authorization request reaches the back-channel origin at the advertised path")
    void shouldRoutePushedAuthorizationRequest() {
        var client = new ParClient(configuration);

        var response = client.pushAuthorizationRequest(publicUrl(PAR_PATH),
                Map.of("response_type", "code", "redirect_uri", REDIRECT_URI), clientAuthentication());

        assertAll("PAR retargeted",
                () -> assertTrue(response.getRequestUri().isPresent()),
                () -> assertEquals(List.of(PAR_PATH), moduleDispatcher.paths()));
    }

    @Test
    @DisplayName("The userinfo request reaches the back-channel origin at the advertised path")
    void shouldRouteUserInfoRequest() {
        var client = new UserInfoClient(configuration);

        var userInfo = client.fetchUserInfo(publicUrl(USERINFO_PATH), Generators.letterStrings(20, 40).next());

        assertAll("userinfo retargeted",
                () -> assertEquals("alice", userInfo.getSub().orElseThrow()),
                () -> assertEquals(List.of(USERINFO_PATH), moduleDispatcher.paths()));
    }

    @Test
    @DisplayName("The revocation request reaches the back-channel origin at the advertised path")
    void shouldRouteRevocationRequest() {
        var client = new RevocationClient(configuration);
        var endpoint = publicUrl(REVOCATION_PATH);
        var token = Generators.letterStrings(20, 40).next();
        var authentication = clientAuthentication();

        assertDoesNotThrow(() -> client.revoke(endpoint, token, "refresh_token", authentication));

        assertEquals(List.of(REVOCATION_PATH), moduleDispatcher.paths());
    }

    @Test
    @DisplayName("An endpoint advertised on a foreign origin is contacted where advertised, not retargeted")
    void shouldNotRetargetForeignOriginEndpoint() {
        // Port 1 on loopback refuses the connection at once, so the call fails fast if — and only if —
        // it is sent where it was advertised. A retargeted call would reach the mock server instead.
        var client = new TokenEndpointClient(configuration);
        var foreignEndpoint = "http://127.0.0.1:1" + TOKEN_PATH;
        Map<String, String> form = Map.of("grant_type", "client_credentials");
        Map<String, String> headers = Map.of();

        assertThrows(TransportException.class, () -> client.requestToken(foreignEndpoint, form, headers),
                "the foreign endpoint must be contacted as advertised");
        assertTrue(moduleDispatcher.paths().isEmpty(),
                "a foreign-origin endpoint must never be sent to the back-channel origin");
    }

    @Test
    @DisplayName("The authorization-request and end-session URLs keep the public origin")
    void shouldKeepFrontChannelOnPublicOrigin() {
        ProviderMetadata metadata = new DiscoveryResolver(configuration).resolve();
        var context = FlowContext.create(REDIRECT_URI);
        var endSession = new EndSessionFlow(new PostLogoutRedirectValidator(Set.of(POST_LOGOUT_REDIRECT)));

        String authorizationUrl = new AuthorizationRequestBuilder().build(configuration, metadata, context);
        String logoutUrl = endSession.buildLogoutRedirect(metadata.getEndSessionEndpoint().orElseThrow(),
                Generators.letterStrings(30, 60).next(), POST_LOGOUT_REDIRECT, context.state());

        assertAll("the browser is sent to the public origin, never the back-channel origin",
                () -> assertTrue(authorizationUrl.startsWith(publicUrl(AUTHORIZATION_PATH) + "?"),
                        "authorization request URL was: " + authorizationUrl),
                () -> assertTrue(logoutUrl.startsWith(publicUrl(END_SESSION_PATH) + "?"),
                        "end-session URL was: " + logoutUrl));
    }

    private static String publicUrl(String path) {
        return PUBLIC_ORIGIN + path;
    }

    private static SenderConstraint dpopConstraint() throws Exception {
        var keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        return SenderConstraint.dpop(new DpopProofGenerator(keyPairGenerator.generateKeyPair(), "ES256"));
    }

    /**
     * Asserts that the single recorded request arrived at {@code path} and carried a DPoP proof whose
     * {@code htu} is the back-channel origin plus {@code path} — the URL actually contacted — and never
     * the advertised public URL (RFC 9449 §4.2).
     */
    private void assertProofNamesBackChannelUrl(String path) {
        assertEquals(List.of(path), moduleDispatcher.paths(), "the request must reach the back-channel origin");
        String payload = proofPayload(moduleDispatcher.requests().getFirst());
        assertAll("htu names the URL actually contacted",
                () -> assertTrue(payload.contains("\"htu\":\"" + backChannelOrigin + path + "\""),
                        "htu must name the back-channel URL actually contacted, but the payload was: " + payload),
                () -> assertFalse(payload.contains(PUBLIC_ORIGIN),
                        "htu must not name the advertised public URL, but the payload was: " + payload));
    }

    /**
     * @return the decoded JSON payload of the DPoP proof the recorded request carried
     */
    private static String proofPayload(RecordedRequest request) {
        String proof = request.getHeaders().get("DPoP");
        assertNotNull(proof, "the DPoP proof must be sent with the request");
        return new String(Base64.getUrlDecoder().decode(proof.split("\\.")[1]), StandardCharsets.UTF_8);
    }

    /**
     * An {@code http://} issuer whose back channel is an {@code https://} origin on loopback port 1,
     * which refuses the connection at once — a discovery that gets past the issuer-scheme check fails
     * fast on the connection attempt instead.
     */
    private static ClientConfiguration httpIssuerWithHttpsBackChannel(boolean allowInsecureHttp) {
        return ClientConfiguration.builder()
                .issuer(HTTP_ISSUER)
                .clientId(Generators.letterStrings(8, 16).next())
                .clientSecret(Generators.letterStrings(16, 32).next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .backChannelOrigin(UNREACHABLE_HTTPS_ORIGIN)
                .allowInsecureHttp(allowInsecureHttp)
                .build();
    }

    private ClientSecretBasicAuth clientAuthentication() {
        return new ClientSecretBasicAuth(configuration.getClientId(), configuration.getClientSecret());
    }

    /**
     * Serves a whole realm under {@value #REALM_PATH}: the discovery document (advertising every
     * endpoint on the public origin) and minimal successful responses for the token, PAR, userinfo and
     * revocation endpoints. Every request is recorded so a test can assert where — and how often — the
     * back channel actually arrived.
     */
    static final class RealmDispatcher implements ModuleDispatcherElement {

        private static final String CONTENT_TYPE = "Content-Type";
        private static final String APPLICATION_JSON = "application/json";

        private final List<RecordedRequest> recorded = new CopyOnWriteArrayList<>();
        private volatile String advertisedIssuer = ISSUER;
        private final AtomicReference<String> pendingNonceChallenge = new AtomicReference<>();

        void reset() {
            recorded.clear();
            advertisedIssuer = ISSUER;
            pendingNonceChallenge.set(null);
        }

        void advertiseIssuer(String issuer) {
            advertisedIssuer = issuer;
        }

        /**
         * Answers the next token request — and only that one — with an RFC 9449 §8 {@code use_dpop_nonce}
         * challenge carrying {@code nonce} in the {@code DPoP-Nonce} header.
         */
        void challengeNextTokenRequestWithNonce(String nonce) {
            pendingNonceChallenge.set(nonce);
        }

        List<RecordedRequest> requests() {
            return List.copyOf(recorded);
        }

        List<String> paths() {
            return recorded.stream().map(request -> request.getUrl().encodedPath()).toList();
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
            recorded.add(request);
            return switch (request.getUrl().encodedPath()) {
                case WELL_KNOWN_PATH -> json(discoveryDocument());
                case USERINFO_PATH -> json("{\"sub\":\"alice\"}");
                default -> Optional.of(new MockResponse(404, Headers.of(), ""));
            };
        }

        @Override
        public Optional<MockResponse> handlePost(RecordedRequest request) {
            recorded.add(request);
            String nonce = TOKEN_PATH.equals(request.getUrl().encodedPath())
                    ? pendingNonceChallenge.getAndSet(null) : null;
            if (nonce != null) {
                return Optional.of(new MockResponse(400,
                        Headers.of(CONTENT_TYPE, APPLICATION_JSON, "DPoP-Nonce", nonce),
                        "{\"error\":\"use_dpop_nonce\"}"));
            }
            return switch (request.getUrl().encodedPath()) {
                case TOKEN_PATH -> json("{\"access_token\":\"opaque-access-token\",\"token_type\":\"Bearer\","
                        + "\"expires_in\":300}");
                case PAR_PATH -> json("{\"request_uri\":\"urn:ietf:params:oauth:request_uri:routed\","
                        + "\"expires_in\":90}");
                case REVOCATION_PATH -> Optional.of(new MockResponse(200, Headers.of(), ""));
                default -> Optional.of(new MockResponse(404, Headers.of(), ""));
            };
        }

        private String discoveryDocument() {
            return "{"
                    + "\"issuer\":\"" + advertisedIssuer + "\","
                    + "\"authorization_endpoint\":\"" + publicUrl(AUTHORIZATION_PATH) + "\","
                    + "\"token_endpoint\":\"" + publicUrl(TOKEN_PATH) + "\","
                    + "\"pushed_authorization_request_endpoint\":\"" + publicUrl(PAR_PATH) + "\","
                    + "\"userinfo_endpoint\":\"" + publicUrl(USERINFO_PATH) + "\","
                    + "\"revocation_endpoint\":\"" + publicUrl(REVOCATION_PATH) + "\","
                    + "\"end_session_endpoint\":\"" + publicUrl(END_SESSION_PATH) + "\","
                    + "\"jwks_uri\":\"" + publicUrl(REALM_PATH + "/protocol/openid-connect/certs") + "\","
                    + "\"code_challenge_methods_supported\":[\"S256\"]"
                    + "}";
        }

        private static Optional<MockResponse> json(String body) {
            return Optional.of(new MockResponse(200, Headers.of(CONTENT_TYPE, APPLICATION_JSON), body));
        }
    }
}
