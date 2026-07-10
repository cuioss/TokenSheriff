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

import de.cuioss.sheriff.token.client.auth.ClientSecretBasicAuth;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.validation.test.dispatcher.ParDispatcher;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("ParClient pushed-authorization-request transport (RFC 9126)")
class ParClientTest {

    @Getter
    private final ParDispatcher moduleDispatcher = new ParDispatcher();

    @BeforeEach
    void resetDispatcher() {
        moduleDispatcher.setCallCounter(0);
        moduleDispatcher.returnDefault();
    }

    private static ClientConfiguration config() {
        return ClientConfiguration.builder()
                .issuer("https://" + Generators.letterStrings(3, 10).next() + ".example.com")
                .clientId(Generators.nonBlankStrings().next())
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(true)
                .build();
    }

    private static Map<String, String> authorizationParameters() {
        return new HashMap<>(Map.of(
                "response_type", "code",
                "redirect_uri", "https://rp.example.com/callback",
                "scope", "openid",
                "state", Generators.nonBlankStrings().next(),
                "nonce", Generators.nonBlankStrings().next(),
                "code_challenge", Generators.nonBlankStrings().next(),
                "code_challenge_method", "S256"));
    }

    private static String parEndpoint(URIBuilder uriBuilder) {
        return uriBuilder.addPathSegments("oidc", "par").buildAsString();
    }

    private ParClient parClient() {
        return new ParClient(config());
    }

    @Test
    @DisplayName("Should push the request and return only the opaque request_uri")
    void shouldReturnRequestUri(URIBuilder uriBuilder) {
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());

        ParResponse response = parClient().pushAuthorizationRequest(
                parEndpoint(uriBuilder), authorizationParameters(), auth);

        assertAll("PAR response",
                () -> assertEquals("urn:ietf:params:oauth:request_uri:mock-par-reference",
                        response.getRequestUri().orElseThrow(), "the AS-issued request_uri is returned"),
                () -> assertEquals(90, response.getExpiresIn(), "the request_uri lifetime is captured"));
    }

    @Test
    @DisplayName("Should push the raw authorization parameters over the back channel, never the front channel (T-PARAM-INTEGRITY)")
    void shouldPushParametersOverBackChannel(URIBuilder uriBuilder, MockWebServer server) throws Exception {
        var params = authorizationParameters();
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());

        ParResponse response = parClient().pushAuthorizationRequest(parEndpoint(uriBuilder), params, auth);

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().utf8();
        assertAll("back-channel push integrity",
                () -> assertTrue(body.contains("response_type=code"), "the response_type is pushed on the back channel"),
                () -> assertTrue(body.contains("state="), "the state is pushed on the back channel"),
                () -> assertTrue(body.contains("code_challenge="), "the PKCE challenge is pushed on the back channel"),
                () -> assertTrue(body.contains("code_challenge_method=S256"), "the PKCE method is pushed on the back channel"),
                () -> assertFalse(response.getRequestUri().orElseThrow().contains("state="),
                        "the returned request_uri never carries the raw authorization parameters"));
    }

    @Test
    @DisplayName("Should authenticate the back-channel push with the client authentication strategy")
    void shouldDecorateWithClientAuth(URIBuilder uriBuilder, MockWebServer server) throws Exception {
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());

        parClient().pushAuthorizationRequest(parEndpoint(uriBuilder), authorizationParameters(), auth);

        RecordedRequest request = server.takeRequest();
        String authorization = request.getHeaders().get("Authorization");
        assertTrue(authorization != null && authorization.startsWith("Basic "),
                "client_secret_basic must send an HTTP Basic Authorization header on the PAR request");
    }

    @Test
    @DisplayName("Should surface a TransportException on an RFC 9126 error response")
    void shouldRejectOAuthError(URIBuilder uriBuilder) {
        moduleDispatcher.returnOAuthError();
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());
        var endpoint = parEndpoint(uriBuilder);
        var client = parClient();
        var params = authorizationParameters();

        assertThrows(TransportException.class,
                () -> client.pushAuthorizationRequest(endpoint, params, auth));
    }

    @Test
    @DisplayName("Should surface a TransportException on a server error")
    void shouldRejectServerError(URIBuilder uriBuilder) {
        moduleDispatcher.returnError();
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());
        var endpoint = parEndpoint(uriBuilder);
        var client = parClient();
        var params = authorizationParameters();

        assertThrows(TransportException.class,
                () -> client.pushAuthorizationRequest(endpoint, params, auth));
    }

    @Test
    @DisplayName("Should reject an oversized response body (DoS guard)")
    void shouldRejectOversizedBody(URIBuilder uriBuilder) {
        moduleDispatcher.returnOversizedBody();
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());
        var endpoint = parEndpoint(uriBuilder);
        var client = parClient();
        var params = authorizationParameters();

        assertThrows(TransportException.class,
                () -> client.pushAuthorizationRequest(endpoint, params, auth));
    }
}
