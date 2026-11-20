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

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.internal.RawHttpServer;
import de.cuioss.sheriff.token.client.token.TokenResponse;
import de.cuioss.sheriff.token.commons.error.TransportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that a token request the HTTP client refuses before sending reaches the caller of
 * {@link TokenEndpointClient#requestToken(String, Map, Map)} as the raw {@link IllegalArgumentException} its
 * {@code @throws} documents — a programming fault of the caller, never a {@link TransportException} — and that
 * nothing is sent.
 * <p>
 * The refused request carries the restricted {@code Connection} header, the request shape of
 * {@code BackChannelHttpRequestRefusalTest}; the JDK refuses to put it on a request. A loopback
 * {@link RawHttpServer} counts every request it receives. The positive control sends the same request with a permitted header and receives
 * the token response.
 */
@DisplayName("TokenEndpointClient request refusal")
class TokenEndpointClientRequestRefusalTest {

    private static final String TOKEN_BODY = "{\"access_token\":\"access-token-value\",\"token_type\":\"Bearer\"}";
    private static final Map<String, String> FORM = Map.of("grant_type", "client_credentials");

    private RawHttpServer server;

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.close();
        }
    }

    private static ClientConfiguration config() {
        return ClientConfiguration.builder()
                .issuer("https://idp.example.com/realms/demo")
                .clientId("test-client")
                .clientSecret("test-secret")
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(true)
                .allowLoopbackEgress(true)
                .build();
    }

    /** Starts a loopback server that counts every request and answers it with a token response. */
    private String serveTokenResponse() throws IOException {
        server = RawHttpServer.answering("HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + TOKEN_BODY.length() + "\r\n"
                + "Connection: close\r\n"
                + "\r\n"
                + TOKEN_BODY);
        return server.url();
    }

    @Test
    @DisplayName("should let a request the client refuses before sending reach the caller as IllegalArgumentException")
    void shouldLetRefusedRequestReachCallerAsIllegalArgumentException() throws Exception {
        String endpoint = serveTokenResponse();
        TokenEndpointClient client = new TokenEndpointClient(config());
        Map<String, String> restrictedHeader = Map.of("Connection", "close");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> client.requestToken(endpoint, FORM, restrictedHeader));

        assertAll("Request-side refusal",
                () -> assertEquals(IllegalArgumentException.class, failure.getClass(),
                        "The refusal is not wrapped into a transport failure"),
                () -> assertEquals(0, server.receivedRequests(), "Nothing reaches the server"));
    }

    @Test
    @DisplayName("positive control: the same request with a permitted header is sent and answered")
    void shouldSendRequestWithPermittedHeader() throws Exception {
        String endpoint = serveTokenResponse();
        TokenEndpointClient client = new TokenEndpointClient(config());
        Map<String, String> permittedHeader = Map.of("X-Request-Id", "request-1");

        TokenResponse response = client.requestToken(endpoint, FORM, permittedHeader);

        assertAll("Permitted request",
                () -> assertEquals("access-token-value", response.accessToken, "The token response is returned"),
                () -> assertEquals(1, server.receivedRequests(), "The request reaches the server once"));
    }
}
