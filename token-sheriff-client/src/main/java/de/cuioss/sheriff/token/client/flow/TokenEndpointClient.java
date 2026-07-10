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

import com.dslplatform.json.DslJson;
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.handler.HttpStatusFamily;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.internal.FormEncoder;
import de.cuioss.sheriff.token.client.token.TokenResponse;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Thin transport wrapper for the OAuth 2.0 back-channel token endpoint.
 * <p>
 * The client composes the commons-blessed cui-http {@link HttpHandler} (design fork 1) to perform
 * the authenticated, form-encoded {@code POST} to the token endpoint over TLS, then normalizes the
 * JSON response into a {@link TokenResponse}. It adds no transport hardening of its own — TLS/SSRF
 * posture is inherited from {@code HttpHandler}. Client authentication is decorated onto the request
 * by the caller (headers and/or form parameters); this class is auth-agnostic transport only.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public class TokenEndpointClient {

    private static final CuiLogger LOGGER = new CuiLogger(TokenEndpointClient.class);

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String FORM_URLENCODED = "application/x-www-form-urlencoded";
    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int READ_TIMEOUT_SECONDS = 10;

    private final ClientConfiguration configuration;
    private final DslJson<Object> dslJson;
    private final int maxContentSize;

    /**
     * @param configuration the client configuration carrying the TLS policy; must not be {@code null}
     */
    public TokenEndpointClient(ClientConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        ParserConfig parserConfig = ParserConfig.builder().build();
        this.dslJson = parserConfig.getDslJson();
        this.maxContentSize = parserConfig.getMaxPayloadSize();
    }

    /**
     * Performs a form-encoded {@code POST} to the token endpoint and normalizes the response.
     *
     * @param tokenEndpoint    the absolute token endpoint URL (from discovery); must be TLS unless
     *                         {@link ClientConfiguration#isAllowInsecureHttp()} is set
     * @param formParameters   the form-encoded request body parameters (e.g. {@code grant_type})
     * @param requestHeaders   additional request headers (e.g. an {@code Authorization} header)
     * @return the normalized token response
     * @throws TransportException if the request fails, the response is not successful, or the body
     *         cannot be parsed
     */
    public TokenResponse requestToken(String tokenEndpoint,
            Map<String, String> formParameters,
            Map<String, String> requestHeaders) {
        Objects.requireNonNull(tokenEndpoint, "tokenEndpoint must not be null");
        Objects.requireNonNull(formParameters, "formParameters must not be null");
        Objects.requireNonNull(requestHeaders, "requestHeaders must not be null");

        HttpHandler handler = HttpHandler.builder()
                .url(tokenEndpoint)
                .connectionTimeoutSeconds(CONNECT_TIMEOUT_SECONDS)
                .readTimeoutSeconds(READ_TIMEOUT_SECONDS)
                .allowInsecureHttp(configuration.isAllowInsecureHttp())
                .build();

        HttpRequest.Builder requestBuilder = handler.requestBuilder()
                .header(CONTENT_TYPE, FORM_URLENCODED)
                .POST(HttpRequest.BodyPublishers.ofString(FormEncoder.encode(formParameters)));
        requestHeaders.forEach(requestBuilder::header);

        HttpClient client = handler.createHttpClient();
        try {
            HttpResponse<String> response = client.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (!HttpStatusFamily.isSuccess(response.statusCode())) {
                throw new TransportException(
                        "Token endpoint returned unexpected HTTP status " + response.statusCode());
            }
            return parse(response.body());
        } catch (IOException e) {
            LOGGER.debug(e, "Token endpoint request failed: %s", e.getMessage());
            throw new TransportException("Token endpoint request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException("Token endpoint request interrupted", e);
        }
    }

    private TokenResponse parse(String body) {
        if (body == null || body.isBlank()) {
            throw new TransportException("Empty token endpoint response");
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxContentSize) {
            throw new TransportException("Token endpoint response exceeds maximum allowed size");
        }
        try {
            TokenResponse tokenResponse = dslJson.deserialize(TokenResponse.class, bytes, bytes.length);
            if (tokenResponse == null || tokenResponse.accessToken == null) {
                throw new TransportException("Token endpoint response is missing the access_token");
            }
            return tokenResponse;
        } catch (IOException e) {
            LOGGER.debug(e, "Failed to parse token endpoint response: %s", e.getMessage());
            throw new TransportException("Failed to parse token endpoint response: " + e.getMessage(), e);
        }
    }

}
