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
import de.cuioss.sheriff.token.client.internal.BackChannelHttp;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import lombok.Getter;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the {@link ParClient} call site sends through {@link BackChannelHttp#send}: a PAR response whose
 * {@code Content-Length} is not a number reaches the caller only as the fixed
 * {@link BackChannelHttp#MALFORMED_RESPONSE} failure, never with the raw header value. The positive control
 * serves the same response with a numeric {@code Content-Length}.
 */
@EnableMockWebServer
@DisplayName("ParClient malformed response framing")
class ParClientMalformedResponseTest {

    private static final String MALFORMED_CONTENT_LENGTH = "12raw34remote56";
    private static final String SUCCESS_BODY =
            "{\"request_uri\":\"urn:ietf:params:oauth:request_uri:mock-par-reference\",\"expires_in\":90}";

    @Getter
    private final RawContentLengthDispatcher moduleDispatcher = new RawContentLengthDispatcher();

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

    private static Map<String, String> authorizationParameters() {
        return new HashMap<>(Map.of(
                "response_type", "code",
                "redirect_uri", "https://rp.example.com/callback",
                "scope", "openid",
                "state", "state-value",
                "code_challenge", "challenge-value",
                "code_challenge_method", "S256"));
    }

    private static String parEndpoint(URIBuilder uriBuilder) {
        return uriBuilder.addPathSegments("oidc", "par").buildAsString();
    }

    @Test
    @DisplayName("Should report a Content-Length that is not a number as the fixed malformed-response failure")
    void shouldReportMalformedContentLengthAsFixedFailure(URIBuilder uriBuilder) {
        moduleDispatcher.setContentLength(MALFORMED_CONTENT_LENGTH);
        var client = new ParClient(config());
        var endpoint = parEndpoint(uriBuilder);
        var params = authorizationParameters();
        var auth = new ClientSecretBasicAuth("test-client", "test-secret");

        TransportException thrown = assertThrows(TransportException.class,
                () -> client.pushAuthorizationRequest(endpoint, params, auth));

        assertAll("fixed malformed-response failure",
                () -> assertEquals("PAR request failed: " + BackChannelHttp.MALFORMED_RESPONSE, thrown.getMessage(),
                        "the PAR client reports the fixed text"),
                () -> assertFalse(thrown.getMessage().contains(MALFORMED_CONTENT_LENGTH),
                        "the raw header value must not reach the caller"),
                () -> assertNull(thrown.getCause(), "the JDK exception carrying the raw value is not chained"));
    }

    @Test
    @DisplayName("positive control: the same response with a numeric Content-Length is accepted")
    void shouldAcceptNumericContentLength(URIBuilder uriBuilder) {
        moduleDispatcher.setContentLength(Integer.toString(SUCCESS_BODY.length()));

        ParResponse response = new ParClient(config()).pushAuthorizationRequest(parEndpoint(uriBuilder),
                authorizationParameters(), new ClientSecretBasicAuth("test-client", "test-secret"));

        assertEquals(Optional.of("urn:ietf:params:oauth:request_uri:mock-par-reference"), response.getRequestUri(),
                "a numeric Content-Length yields the pushed request_uri");
    }

    /**
     * Serves a 201 PAR success whose {@code Content-Length} value is written to the wire verbatim.
     */
    static final class RawContentLengthDispatcher implements ModuleDispatcherElement {

        private String contentLength = Integer.toString(SUCCESS_BODY.length());

        void setContentLength(String contentLength) {
            this.contentLength = contentLength;
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
            return Optional.of(new MockResponse.Builder().code(201)
                    .addHeader("Content-Type", "application/json")
                    .body(SUCCESS_BODY)
                    .removeHeader("Content-Length")
                    .addHeaderLenient("Content-Length", contentLength)
                    .build());
        }
    }
}
