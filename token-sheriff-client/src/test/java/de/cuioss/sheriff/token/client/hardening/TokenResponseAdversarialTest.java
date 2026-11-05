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
package de.cuioss.sheriff.token.client.hardening;

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.flow.RedeemedResponseException;
import de.cuioss.sheriff.token.client.flow.TokenEndpointClient;
import de.cuioss.sheriff.token.client.token.TokenResponse;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valid-JSON adversarial token-endpoint responses (TC-7). Every body below is well-formed JSON served
 * with HTTP 200 and {@code application/json}, so neither the status check nor the JSON syntax rejects
 * it: the response <em>shape</em> is what is hostile. Each case pins the exact typed failure
 * {@link TokenEndpointClient} raises. Because every body arrives after a success status, the grant is
 * already redeemed, and every refusal is a {@link RedeemedResponseException}.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("TokenEndpointClient against valid-JSON hostile token responses (TC-7)")
class TokenResponseAdversarialTest {

    private static final String VALID_PREFIX = "{\"access_token\":\"token\",\"token_type\":\"Bearer\"";

    @Getter
    private final TokenBodyDispatcher moduleDispatcher = new TokenBodyDispatcher();

    @Test
    @DisplayName("Should refuse a response without access_token")
    void shouldRefuseMissingAccessToken(URIBuilder uriBuilder) {
        assertRedeemedFailure("{\"token_type\":\"Bearer\",\"expires_in\":300}", uriBuilder);
    }

    @ParameterizedTest(name = "expires_in = {0}")
    @ValueSource(strings = {"\"soon\"", "300.5", "true", "{\"seconds\":300}", "[300]", "99999999999999999999"})
    @DisplayName("Should refuse a wrong-typed or out-of-range expires_in")
    void shouldRefuseWrongTypedExpiresIn(String expiresIn, URIBuilder uriBuilder) {
        assertRedeemedFailure(VALID_PREFIX + ",\"expires_in\":" + expiresIn + "}", uriBuilder);
    }

    @Test
    @DisplayName("Should accept a quoted numeric expires_in as the observed lenient behaviour")
    void shouldAcceptQuotedNumericExpiresIn(URIBuilder uriBuilder) {
        // Pinned as observed: the JSON library reads a quoted integer into the long field. This
        // lenient acceptance is a documented risk of this hardening pass, not an endorsed contract.
        TokenResponse response = request(VALID_PREFIX + ",\"expires_in\":\"300\"}", uriBuilder);

        assertEquals(300L, response.getExpiresIn(), "a quoted integer is read as its numeric value");
    }

    @Test
    @DisplayName("Should refuse a response that repeats access_token with different values")
    void shouldRefuseDuplicateAccessToken(URIBuilder uriBuilder) {
        RedeemedResponseException thrown = assertRedeemedFailure(
                "{\"access_token\":\"first\",\"token_type\":\"Bearer\",\"access_token\":\"second\"}", uriBuilder);

        assertTrue(thrown.getMessage().contains("'access_token'"),
                "the refusal must name the repeated member: " + thrown.getMessage());
    }

    @Test
    @DisplayName("Should refuse a response that repeats token_type as Bearer and DPoP")
    void shouldRefuseDuplicateTokenType(URIBuilder uriBuilder) {
        RedeemedResponseException thrown = assertRedeemedFailure(
                "{\"access_token\":\"token\",\"token_type\":\"Bearer\",\"token_type\":\"DPoP\"}", uriBuilder);

        assertTrue(thrown.getMessage().contains("'token_type'"),
                "the refusal must name the repeated member: " + thrown.getMessage());
    }

    @Test
    @DisplayName("Should refuse an access_token longer than the parser string bound")
    void shouldRefuseOversizeAccessToken(URIBuilder uriBuilder) {
        String oversizeToken = "a".repeat(ParserConfig.DEFAULT_MAX_STRING_LENGTH + 1);

        assertRedeemedFailure("{\"access_token\":\"" + oversizeToken + "\",\"token_type\":\"Bearer\"}", uriBuilder);
    }

    @Test
    @DisplayName("Should refuse a body larger than the maximum payload size")
    void shouldRefuseOversizeBody(URIBuilder uriBuilder) {
        String padding = "p".repeat(ParserConfig.DEFAULT_MAX_PAYLOAD_SIZE);

        assertRedeemedFailure(VALID_PREFIX + ",\"padding\":\"" + padding + "\"}", uriBuilder);
    }

    @ParameterizedTest(name = "token_type = \"{0}\"")
    @ValueSource(strings = {"mac", "", "bearerx"})
    @DisplayName("Should refuse an unknown token_type")
    void shouldRefuseUnknownTokenType(String tokenType, URIBuilder uriBuilder) {
        assertRedeemedFailure("{\"access_token\":\"token\",\"token_type\":\"" + tokenType + "\"}", uriBuilder);
    }

    private RedeemedResponseException assertRedeemedFailure(String body, URIBuilder uriBuilder) {
        moduleDispatcher.serve(body);
        var client = new TokenEndpointClient(config());
        String tokenEndpoint = uriBuilder.addPathSegment("token").buildAsString();
        Map<String, String> formParameters = Map.of("grant_type", "client_credentials");
        Map<String, String> requestHeaders = Map.of();

        return assertThrows(RedeemedResponseException.class,
                () -> client.requestToken(tokenEndpoint, formParameters, requestHeaders),
                "a hostile token response must be refused after the grant was redeemed");
    }

    private TokenResponse request(String body, URIBuilder uriBuilder) {
        moduleDispatcher.serve(body);
        var client = new TokenEndpointClient(config());
        String tokenEndpoint = uriBuilder.addPathSegment("token").buildAsString();
        return client.requestToken(tokenEndpoint, Map.of("grant_type", "client_credentials"), Map.of());
    }

    private static ClientConfiguration config() {
        return ClientConfiguration.builder()
                .issuer("https://" + Generators.letterStrings(3, 10).next() + ".example.com")
                .clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(true)
                .build();
    }

    /**
     * Serves a configurable body at the token endpoint with HTTP 200 and {@code application/json}, so
     * the response shape — not the status or the media type — is what must be refused.
     */
    static final class TokenBodyDispatcher implements ModuleDispatcherElement {

        private volatile String body = "";

        void serve(String responseBody) {
            this.body = responseBody;
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
            return Optional.of(new MockResponse(200, Headers.of("Content-Type", "application/json"), body));
        }
    }
}
