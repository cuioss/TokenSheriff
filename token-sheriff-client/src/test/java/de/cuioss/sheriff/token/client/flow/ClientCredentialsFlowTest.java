/*
 * Copyright © 2022 CUI-OpenSource-Software (info@cuioss.de)
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

import de.cuioss.sheriff.token.client.auth.ClientAuthentication;
import de.cuioss.sheriff.token.client.auth.ClientSecretBasicAuth;
import de.cuioss.sheriff.token.client.auth.PrivateKeyJwtAuth;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.ProviderMetadata;
import de.cuioss.sheriff.token.client.token.TokenValidationBridge;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("ClientCredentialsFlow retrieve-then-validate loop")
class ClientCredentialsFlowTest {

    @Getter
    private final TokenEndpointDispatcher moduleDispatcher = new TokenEndpointDispatcher();

    private TestTokenHolder holder;
    private TokenValidationBridge bridge;

    @BeforeEach
    void setUp() {
        holder = TestTokenGenerators.accessTokens().next();
        TokenValidator validator = TokenValidator.builder().issuerConfig(holder.getIssuerConfig()).build();
        bridge = new TokenValidationBridge(validator);
        moduleDispatcher.reset();
    }

    private static ClientConfiguration config() {
        return ClientConfiguration.builder()
                .issuer("https://" + Generators.letterStrings(3, 10).next() + ".example.com")
                .clientId(Generators.nonBlankStrings().next())
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("api")
                .allowInsecureHttp(true)
                .build();
    }

    private static ProviderMetadata metadata(URIBuilder uriBuilder) {
        var metadata = new ProviderMetadata();
        metadata.tokenEndpoint = uriBuilder.addPathSegment("token").buildAsString();
        return metadata;
    }

    private ClientCredentialsFlow flow(ClientConfiguration config, ClientAuthentication clientAuthentication) {
        return new ClientCredentialsFlow(config, new TokenEndpointClient(config), bridge, clientAuthentication);
    }

    private static ClientSecretBasicAuth basicAuth(ClientConfiguration config) {
        return new ClientSecretBasicAuth(config.getClientId(), config.getClientSecret());
    }

    private static String successBody(String accessToken) {
        return "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"Bearer\",\"expires_in\":300}";
    }

    @Test
    @DisplayName("Should obtain, validate, and return the access token with client_secret_basic auth")
    void shouldObtainAndValidateToken(URIBuilder uriBuilder, MockWebServer server) throws Exception {
        moduleDispatcher.success(successBody(holder.getRawToken()));
        var config = config();

        AccessTokenContent content = flow(config, basicAuth(config)).obtainToken(metadata(uriBuilder));

        assertNotNull(content, "validated access token content should be returned");
        RecordedRequest request = server.takeRequest();
        String authorization = request.getHeaders().get("Authorization");
        assertNotNull(authorization, "Authorization header present");
        assertTrue(authorization.startsWith("Basic "),
                "client_secret_basic must send an HTTP Basic Authorization header");
    }

    @Test
    @DisplayName("Should acquire a service-to-service token with private_key_jwt when the strategy is injected")
    void shouldAcquireWithPrivateKeyJwt(URIBuilder uriBuilder, MockWebServer server) throws Exception {
        moduleDispatcher.success(successBody(holder.getRawToken()));
        var config = config();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        var privateKeyJwt = new PrivateKeyJwtAuth(config.getClientId(),
                metadata(uriBuilder).getTokenEndpoint().orElseThrow(), keyPair.getPrivate(),
                Generators.letterStrings(3, 8).next(), "RS256");

        AccessTokenContent content = flow(config, privateKeyJwt).obtainToken(metadata(uriBuilder));

        assertNotNull(content, "key-based client_credentials acquisition must return a validated token");
        RecordedRequest request = server.takeRequest();
        String body = request.getBody() == null ? "" : request.getBody().utf8();
        assertAll("private_key_jwt wire request",
                () -> assertTrue(body.contains("grant_type=client_credentials"),
                        "grant_type must remain client_credentials"),
                () -> assertTrue(body.contains("client_assertion="),
                        "the private_key_jwt assertion must be sent — the flow is no longer capped at a shared secret"),
                () -> assertNull(request.getHeaders().get("Authorization"),
                        "private_key_jwt authenticates via a form assertion, not an Authorization header"));
    }

    @Test
    @DisplayName("Should delegate Basic encoding to ClientSecretBasicAuth rather than duplicate it")
    void shouldDelegateBasicEncodingToStrategy(URIBuilder uriBuilder, MockWebServer server) throws Exception {
        moduleDispatcher.success(successBody(holder.getRawToken()));
        var config = config();
        var basicAuth = basicAuth(config);
        Map<String, String> expectedHeaders = new HashMap<>();
        basicAuth.decorate(new HashMap<>(), expectedHeaders);

        flow(config, basicAuth).obtainToken(metadata(uriBuilder));

        RecordedRequest request = server.takeRequest();
        assertEquals(expectedHeaders.get("Authorization"), request.getHeaders().get("Authorization"),
                "the flow must delegate Basic encoding to ClientSecretBasicAuth, not reimplement it");
    }

    @Test
    @DisplayName("Should reject a null client authentication strategy")
    void shouldRejectNullClientAuthentication() {
        var config = config();
        var endpointClient = new TokenEndpointClient(config);

        assertThrows(NullPointerException.class,
                () -> new ClientCredentialsFlow(config, endpointClient, bridge, null),
                "a null client authentication strategy must be rejected");
    }

    @Test
    @DisplayName("Should surface a TransportException on a token-endpoint error response")
    void shouldRejectErrorResponse(URIBuilder uriBuilder) {
        moduleDispatcher.error();
        var config = config();
        var flow = flow(config, basicAuth(config));
        var metadata = metadata(uriBuilder);

        assertThrows(TransportException.class, () -> flow.obtainToken(metadata));
    }

    @Test
    @DisplayName("Should reject a retrieved token that fails pipeline validation")
    void shouldRejectTamperedToken(URIBuilder uriBuilder) {
        holder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("https://attacker.example.com"));
        moduleDispatcher.success(successBody(holder.getRawToken()));
        var config = config();
        var flow = flow(config, basicAuth(config));
        var metadata = metadata(uriBuilder);

        assertThrows(TokenValidationException.class, () -> flow.obtainToken(metadata));
    }

    /**
     * Minimal token-endpoint dispatcher serving a configurable success or error response.
     */
    static final class TokenEndpointDispatcher implements ModuleDispatcherElement {

        private static final int HTTP_OK = 200;
        private static final int HTTP_BAD_REQUEST = 400;

        private int status = HTTP_OK;
        private String body = "";

        void reset() {
            this.status = HTTP_OK;
            this.body = "";
        }

        void success(String responseBody) {
            this.status = HTTP_OK;
            this.body = responseBody;
        }

        void error() {
            this.status = HTTP_BAD_REQUEST;
            this.body = "{\"error\":\"invalid_client\"}";
        }

        @Override
        public String getBaseUrl() {
            return "/token";
        }

        @Override
        public Set<HttpMethodMapper> supportedMethods() {
            return Set.of(HttpMethodMapper.POST);
        }

        @Override
        public Optional<MockResponse> handlePost(RecordedRequest request) {
            return Optional.of(new MockResponse(status, Headers.of("Content-Type", "application/json"), body));
        }
    }
}
