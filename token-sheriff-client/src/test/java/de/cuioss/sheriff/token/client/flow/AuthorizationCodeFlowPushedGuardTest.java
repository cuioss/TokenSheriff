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
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the invariant guard of {@link AuthorizationCodeFlow#authorizePushed}: the default
 * {@link AuthorizationRequestBuilder#parameters(ClientConfiguration, ProviderMetadata, FlowContext,
 * AuthorizationRequestOptions)} refuses metadata without an authorization endpoint, so the flow can rely
 * on the endpoint being present afterwards. A subclass whose {@code parameters(...)} skips that check
 * breaks the invariant; the flow then refuses with an {@link IllegalStateException} naming the broken
 * invariant, before any request reaches the PAR endpoint.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("AuthorizationCodeFlow pushed authorization request: authorization-endpoint invariant guard")
class AuthorizationCodeFlowPushedGuardTest {

    private static final String ISSUER = "https://issuer.example.com";
    private static final String REDIRECT_URI = "https://rp.example.com/callback";

    @Getter
    private final AuthorizationCodeFlowPushedTest.PushedRequestDispatcher moduleDispatcher =
            new AuthorizationCodeFlowPushedTest.PushedRequestDispatcher();

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

    private static ClientConfiguration config() {
        return ClientConfiguration.builder()
                .issuer(ISSUER)
                .clientId(Generators.letterStrings(4, 16).next())
                .clientSecret(Generators.letterStrings(8, 24).next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .redirectUri(REDIRECT_URI)
                .allowInsecureHttp(true)
                .allowLoopbackEgress(true)
                .build();
    }

    /** Metadata advertising a PAR endpoint and S256, but no authorization endpoint. */
    private static ProviderMetadata metadataWithoutAuthorizationEndpoint(URIBuilder uriBuilder) {
        var metadata = new ProviderMetadata();
        metadata.issuer = ISSUER;
        metadata.pushedAuthorizationRequestEndpoint = uriBuilder.addPathSegments("oidc", "par").buildAsString();
        metadata.codeChallengeMethodsSupported = List.of("S256");
        return metadata;
    }

    private AuthorizationCodeFlow flow(ClientConfiguration configuration, AuthorizationRequestBuilder builder) {
        return new AuthorizationCodeFlow(configuration, new TokenEndpointClient(configuration), accessBridge,
                idBridge, new IssValidator(), builder, new CallbackHandler(), null);
    }

    private static ClientSecretBasicAuth auth(ClientConfiguration configuration) {
        return new ClientSecretBasicAuth(configuration.getClientId(), configuration.getClientSecret());
    }

    @Test
    @DisplayName("Should refuse with IllegalStateException when an override of parameters(...) skips the endpoint check")
    void shouldRefuseBrokenInvariantBeforeAnyRequest(URIBuilder uriBuilder, MockWebServer server) {
        var configuration = config();
        var metadata = metadataWithoutAuthorizationEndpoint(uriBuilder);
        var flow = flow(configuration, new UncheckedParametersBuilder());
        var clientAuthentication = auth(configuration);

        var thrown = assertThrows(IllegalStateException.class,
                () -> flow.authorizePushed(metadata, clientAuthentication, AuthorizationRequestOptions.DEFAULTS));

        assertAll("broken invariant",
                () -> assertEquals("parameters(...) returned without verifying the authorization endpoint",
                        thrown.getMessage(), "the refusal names the broken invariant"),
                () -> assertFalse(ClientProtocolException.class.isInstance(thrown),
                        "a broken invariant is not a protocol refusal"),
                () -> assertEquals(0, moduleDispatcher.getCallCount(), "nothing was pushed"),
                () -> assertEquals(0, server.getRequestCount(), "the server records zero requests"));
    }

    @Test
    @DisplayName("Control: the unmodified builder refuses the same metadata with ClientProtocolException")
    void shouldRefuseWithProtocolExceptionThroughDefaultBuilder(URIBuilder uriBuilder, MockWebServer server) {
        var configuration = config();
        var metadata = metadataWithoutAuthorizationEndpoint(uriBuilder);
        var flow = flow(configuration, new AuthorizationRequestBuilder());
        var clientAuthentication = auth(configuration);

        var thrown = assertThrows(ClientProtocolException.class,
                () -> flow.authorizePushed(metadata, clientAuthentication, AuthorizationRequestOptions.DEFAULTS));

        assertAll("protocol refusal",
                () -> assertEquals("provider metadata is missing the authorization endpoint", thrown.getMessage(),
                        "the builder names the missing authorization endpoint"),
                () -> assertFalse(PushedAuthorizationEndpointMissingException.class.isInstance(thrown),
                        "a PAR endpoint is advertised, so the refusal is not the missing-PAR type"),
                () -> assertEquals(0, moduleDispatcher.getCallCount(), "nothing was pushed"),
                () -> assertEquals(0, server.getRequestCount(), "the server records zero requests"));
    }

    /** A builder whose {@code parameters(...)} skips every endpoint check. */
    private static final class UncheckedParametersBuilder extends AuthorizationRequestBuilder {

        @Override
        public Map<String, String> parameters(ClientConfiguration configuration, ProviderMetadata metadata,
                FlowContext context, AuthorizationRequestOptions options) {
            return Map.of("response_type", "code", "client_id", configuration.getClientId());
        }
    }
}
