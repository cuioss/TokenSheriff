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
package de.cuioss.sheriff.token.client.flow;

import de.cuioss.sheriff.token.client.auth.ClientSecretBasicAuth;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.ProviderMetadata;
import de.cuioss.sheriff.token.client.token.IdTokenValidationBridge;
import de.cuioss.sheriff.token.client.token.TokenValidationBridge;
import de.cuioss.sheriff.token.commons.error.ClientProtocolException;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static de.cuioss.sheriff.token.client.flow.AuthorizationRequestBuilderParametersTest.wireParameters;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link AuthorizationCodeFlow#authorizePushed} against a MockWebServer PAR endpoint
 * (RFC 9126): the front-channel redirect carries only {@code client_id} and the issued
 * {@code request_uri}, the pushed body is exactly the validated parameter set of the returned flow
 * context, and every refusal happens before any request reaches the authorization server.
 * <p>
 * The tests are deliberately not grouped into {@code @Nested} classes: {@code @EnableMockWebServer}
 * resolves the dispatcher through {@link #getModuleDispatcher()} on the test instance, which a nested
 * instance does not expose.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("AuthorizationCodeFlow pushed authorization request (RFC 9126)")
class AuthorizationCodeFlowPushedTest {

    private static final String ISSUER = "https://issuer.example.com";
    private static final String REDIRECT_URI = "https://rp.example.com/callback";
    private static final String AUTH_ENDPOINT = "https://issuer.example.com/authorize";
    private static final String REQUEST_URI = "urn:ietf:params:oauth:request_uri:pushed-reference";

    @Getter
    private final PushedRequestDispatcher moduleDispatcher = new PushedRequestDispatcher();

    private TokenValidationBridge accessBridge;
    private IdTokenValidationBridge idBridge;

    @BeforeEach
    void setUp() {
        TokenValidator validator = TokenValidator.builder()
                .issuerConfig(TestTokenGenerators.accessTokens().next().getIssuerConfig()).build();
        accessBridge = new TokenValidationBridge(validator);
        idBridge = new IdTokenValidationBridge(validator);
        moduleDispatcher.reset();
    }

    private static ClientConfiguration config(boolean allowInsecureHttp) {
        return ClientConfiguration.builder()
                .issuer(ISSUER)
                .clientId(Generators.letterStrings(4, 16).next())
                .clientSecret(Generators.letterStrings(8, 24).next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .scope("profile")
                .redirectUri(REDIRECT_URI)
                .allowInsecureHttp(allowInsecureHttp)
                .allowLoopbackEgress(true)
                .build();
    }

    private static ClientConfiguration config() {
        return config(true);
    }

    private static ProviderMetadata metadata(URIBuilder uriBuilder, String authorizationEndpoint, boolean s256) {
        var metadata = new ProviderMetadata();
        metadata.issuer = ISSUER;
        metadata.authorizationEndpoint = authorizationEndpoint;
        metadata.pushedAuthorizationRequestEndpoint = uriBuilder.addPathSegments("oidc", "par").buildAsString();
        metadata.codeChallengeMethodsSupported = List.of(s256 ? "S256" : "plain");
        return metadata;
    }

    private static ProviderMetadata metadata(URIBuilder uriBuilder) {
        return metadata(uriBuilder, AUTH_ENDPOINT, true);
    }

    private AuthorizationCodeFlow flow(ClientConfiguration configuration) {
        return new AuthorizationCodeFlow(configuration, new TokenEndpointClient(configuration), accessBridge,
                idBridge);
    }

    private static ClientSecretBasicAuth auth(ClientConfiguration configuration) {
        return new ClientSecretBasicAuth(configuration.getClientId(), configuration.getClientSecret());
    }

    /** Decodes an {@code application/x-www-form-urlencoded} body into its parameters. */
    private static Map<String, String> formParameters(String body) {
        Map<String, String> parameters = new HashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            parameters.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return parameters;
    }

    @Test
    @DisplayName("Should redirect with exactly client_id and request_uri")
    void shouldRedirectWithReferenceOnly(URIBuilder uriBuilder) {
        var configuration = config();
        var options = AuthorizationRequestOptions.builder()
                .prompt(Prompt.LOGIN).responseMode(ResponseMode.QUERY).build();

        var redirect = flow(configuration).authorizePushed(metadata(uriBuilder), auth(configuration), options);

        assertAll("front-channel redirect",
                () -> assertTrue(redirect.authorizationUrl().startsWith(AUTH_ENDPOINT + "?"),
                        "the redirect targets the advertised authorization endpoint"),
                () -> assertEquals(List.of(Map.entry("client_id", configuration.getClientId()),
                                Map.entry("request_uri", REQUEST_URI)),
                        wireParameters(redirect.authorizationUrl()),
                        "no state, nonce, PKCE, scope, response_mode or prompt reaches the front channel"));
    }

    @Test
    @DisplayName("Should push the state, nonce and PKCE challenge of the returned flow context")
    void shouldPushReturnedContext(URIBuilder uriBuilder, MockWebServer server) throws Exception {
        var configuration = config();

        var redirect = flow(configuration).authorizePushed(metadata(uriBuilder), auth(configuration),
                AuthorizationRequestOptions.DEFAULTS);

        RecordedRequest request = server.takeRequest();
        Map<String, String> pushed = formParameters(request.getBody().utf8());
        FlowContext context = redirect.context();
        assertAll("pushed context",
                () -> assertEquals(context.state(), pushed.get("state"), "the returned state was pushed"),
                () -> assertEquals(context.nonce(), pushed.get("nonce"), "the returned nonce was pushed"),
                () -> assertEquals(context.pkceChallenge().codeChallenge(), pushed.get("code_challenge"),
                        "the returned PKCE challenge was pushed"),
                () -> assertEquals(1, moduleDispatcher.getCallCount(), "exactly one push was sent"));
    }

    @Test
    @DisplayName("Should push exactly the parameters the builder returns for the same context and options")
    void shouldPushBuilderParameters(URIBuilder uriBuilder, MockWebServer server) throws Exception {
        var configuration = config();
        var metadata = metadata(uriBuilder);
        var clientAuthentication = auth(configuration);
        var options = AuthorizationRequestOptions.builder()
                .scopes(List.of("openid", "email")).prompt(Prompt.CONSENT).build();

        var redirect = flow(configuration).authorizePushed(metadata, clientAuthentication, options);

        RecordedRequest request = server.takeRequest();
        Map<String, String> pushed = formParameters(request.getBody().utf8());
        Map<String, String> decorationForm = new HashMap<>();
        Map<String, String> decorationHeaders = new HashMap<>();
        clientAuthentication.decorate(decorationForm, decorationHeaders);
        pushed.keySet().removeAll(decorationForm.keySet());

        Map<String, String> expected = new AuthorizationRequestBuilder().parameters(configuration, metadata,
                redirect.context(), options);
        assertAll("pushed body",
                () -> assertEquals(expected, pushed,
                        "the body minus the client-authentication decoration is the builder's parameter set"),
                () -> assertEquals(decorationHeaders.get("Authorization"), request.getHeaders().get("Authorization"),
                        "the push is authenticated with the client authentication"));
    }

    @Test
    @DisplayName("Should refuse metadata without a PAR endpoint before any request")
    void shouldRefuseWithoutParEndpoint(MockWebServer server) {
        var configuration = config();
        var metadata = new ProviderMetadata();
        metadata.issuer = ISSUER;
        metadata.authorizationEndpoint = AUTH_ENDPOINT;
        metadata.codeChallengeMethodsSupported = List.of("S256");
        var flow = flow(configuration);
        var clientAuthentication = auth(configuration);

        var thrown = assertThrows(ClientProtocolException.class,
                () -> flow.authorizePushed(metadata, clientAuthentication, AuthorizationRequestOptions.DEFAULTS));

        assertAll("no PAR endpoint",
                () -> assertEquals("provider metadata is missing the pushed authorization request endpoint",
                        thrown.getMessage(), "the refusal names the missing endpoint"),
                () -> assertEquals(0, server.getRequestCount(), "the server records zero requests"));
    }

    @Test
    @DisplayName("Should refuse a PAR response without request_uri with TransportException")
    void shouldRefuseResponseWithoutRequestUri(URIBuilder uriBuilder) {
        moduleDispatcher.returnWithoutRequestUri();
        var configuration = config();
        var metadata = metadata(uriBuilder);
        var flow = flow(configuration);
        var clientAuthentication = auth(configuration);

        var thrown = assertThrows(TransportException.class,
                () -> flow.authorizePushed(metadata, clientAuthentication, AuthorizationRequestOptions.DEFAULTS));

        assertAll("missing request_uri",
                () -> assertEquals("PAR endpoint response is missing the request_uri", thrown.getMessage(),
                        "the refusal names the missing request_uri"),
                () -> assertEquals(1, moduleDispatcher.getCallCount(), "the push reached the server once"));
    }

    @Test
    @DisplayName("Should refuse a non-TLS authorization endpoint before any push")
    void shouldRefuseNonTlsAuthorizationEndpoint(URIBuilder uriBuilder, MockWebServer server) {
        var configuration = config(false);
        var metadata = metadata(uriBuilder, "http://issuer.example.com/authorize", true);
        var flow = flow(configuration);
        var clientAuthentication = auth(configuration);

        assertThrows(ClientProtocolException.class,
                () -> flow.authorizePushed(metadata, clientAuthentication, AuthorizationRequestOptions.DEFAULTS),
                "a cleartext authorization endpoint is refused");
        assertEquals(0, server.getRequestCount(), "nothing was pushed");
    }

    @Test
    @DisplayName("Should refuse an authorization server without PKCE S256 before any push")
    void shouldRefuseMissingS256(URIBuilder uriBuilder, MockWebServer server) {
        var configuration = config();
        var metadata = metadata(uriBuilder, AUTH_ENDPOINT, false);
        var flow = flow(configuration);
        var clientAuthentication = auth(configuration);

        assertThrows(ClientProtocolException.class,
                () -> flow.authorizePushed(metadata, clientAuthentication, AuthorizationRequestOptions.DEFAULTS),
                "an AS without S256 is refused");
        assertEquals(0, server.getRequestCount(), "nothing was pushed");
    }

    @Test
    @DisplayName("Should reject null arguments before any push")
    void shouldRejectNullArguments(URIBuilder uriBuilder) {
        var configuration = config();
        var metadata = metadata(uriBuilder);
        var flow = flow(configuration);
        var clientAuthentication = auth(configuration);
        var options = AuthorizationRequestOptions.DEFAULTS;

        assertAll("null guards",
                () -> assertThrows(NullPointerException.class,
                        () -> flow.authorizePushed(null, clientAuthentication, options)),
                () -> assertThrows(NullPointerException.class,
                        () -> flow.authorizePushed(metadata, null, options)),
                () -> assertThrows(NullPointerException.class,
                        () -> flow.authorizePushed(metadata, clientAuthentication, null)),
                () -> assertEquals(0, moduleDispatcher.getCallCount(), "nothing was pushed"));
    }

    /**
     * PAR endpoint double: answers an RFC 9126 §2.2 success carrying a fixed {@code request_uri}, or a
     * success body that omits it, and counts the pushes it received.
     */
    static final class PushedRequestDispatcher implements ModuleDispatcherElement {

        private static final String SUCCESS_BODY = "{\"request_uri\":\"" + REQUEST_URI + "\",\"expires_in\":90}";
        private static final String NO_REQUEST_URI_BODY = "{\"expires_in\":90}";

        private boolean omitRequestUri;

        @Getter
        private int callCount;

        void reset() {
            omitRequestUri = false;
            callCount = 0;
        }

        void returnWithoutRequestUri() {
            omitRequestUri = true;
        }

        @Override
        public String getBaseUrl() {
            return "/oidc/par";
        }

        @Override
        public Set<HttpMethodMapper> supportedMethods() {
            return Set.of(HttpMethodMapper.POST);
        }

        @Override
        public Optional<MockResponse> handlePost(RecordedRequest request) {
            callCount++;
            return Optional.of(new MockResponse(201, Headers.of("Content-Type", "application/json"),
                    omitRequestUri ? NO_REQUEST_URI_BODY : SUCCESS_BODY));
        }
    }
}
