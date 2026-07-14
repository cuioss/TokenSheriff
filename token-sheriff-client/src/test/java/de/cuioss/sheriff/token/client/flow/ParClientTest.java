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
                "state", opaqueParameterValue(),
                "nonce", opaqueParameterValue(),
                "code_challenge", opaqueParameterValue(),
                "code_challenge_method", "S256"));
    }

    /**
     * Generates a distinctive, sufficiently long opaque value for the security-sensitive
     * authorization parameters (state, nonce, PKCE challenge). A minimum length of 24 random
     * letters guarantees the value cannot coincidentally occur as a substring of the fixed
     * mock AS-issued opaque {@code request_uri}, which would otherwise make the no-leak
     * assertions in {@link #shouldReturnRequestUri} flaky (a short {@code nonBlankStrings}
     * value could match by chance).
     */
    private static String opaqueParameterValue() {
        return Generators.letterStrings(24, 40).next();
    }

    private static String parEndpoint(URIBuilder uriBuilder) {
        return uriBuilder.addPathSegments("oidc", "par").buildAsString();
    }

    private ParClient parClient() {
        return new ParClient(config());
    }

    @Test
    @DisplayName("Should push the request and return an opaque request_uri that leaks none of the pushed parameters (TEST-9)")
    void shouldReturnRequestUri(URIBuilder uriBuilder) {
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());
        var params = authorizationParameters();

        ParResponse response = parClient().pushAuthorizationRequest(
                parEndpoint(uriBuilder), params, auth);

        String requestUri = response.getRequestUri().orElseThrow();
        assertAll("PAR response is an opaque reference",
                () -> assertFalse(requestUri.isBlank(),
                        "the AS-issued request_uri must be present and non-blank"),
                () -> assertFalse(requestUri.contains(params.get("state")),
                        "the opaque request_uri must not leak the pushed state"),
                () -> assertFalse(requestUri.contains(params.get("nonce")),
                        "the opaque request_uri must not leak the pushed nonce"),
                () -> assertFalse(requestUri.contains(params.get("code_challenge")),
                        "the opaque request_uri must not leak the pushed PKCE challenge"),
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
