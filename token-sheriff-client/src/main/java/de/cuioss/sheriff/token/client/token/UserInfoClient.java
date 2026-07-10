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
package de.cuioss.sheriff.token.client.token;

import com.dslplatform.json.DslJson;
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.handler.HttpStatusFamily;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Fetches the OpenID Connect userinfo document over the authenticated back channel and normalizes it
 * ({@code CLIENT-16}).
 * <p>
 * The client presents the validated access token as an RFC 6750 {@code Bearer} credential to the
 * userinfo endpoint over TLS and normalizes the JSON response into a {@link UserInfoResponse}. The
 * response is transport-authenticated only; its identity is never trusted until
 * {@link SubBindingValidator} binds the {@code sub} back to the validated ID token. The transport
 * reuses the commons-blessed cui-http {@link HttpHandler}; it adds no transport hardening of its own.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#UserInfo">OIDC Core §5.3</a>
 */
public class UserInfoClient {

    private static final CuiLogger LOGGER = new CuiLogger(UserInfoClient.class);

    private static final String AUTHORIZATION = "Authorization";
    private static final String ACCEPT = "Accept";
    private static final String APPLICATION_JSON = "application/json";
    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int READ_TIMEOUT_SECONDS = 10;

    private final ClientConfiguration configuration;
    private final DslJson<Object> dslJson;
    private final int maxContentSize;

    /**
     * @param configuration the client configuration carrying the TLS policy; must not be {@code null}
     */
    public UserInfoClient(ClientConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        ParserConfig parserConfig = ParserConfig.builder().build();
        this.dslJson = parserConfig.getDslJson();
        this.maxContentSize = parserConfig.getMaxPayloadSize();
    }

    /**
     * Fetches the userinfo document for the given access token.
     *
     * @param userInfoEndpoint the absolute userinfo endpoint URL (from discovery); must be TLS unless
     *                         {@link ClientConfiguration#isAllowInsecureHttp()} is set
     * @param accessToken      the validated access token to present as a Bearer credential; must not
     *                         be {@code null}
     * @return the normalized userinfo response
     * @throws TransportException if the request fails, the response is not successful, the body cannot
     *         be parsed, or the response omits the {@code sub}
     */
    public UserInfoResponse fetchUserInfo(String userInfoEndpoint, String accessToken) {
        Objects.requireNonNull(userInfoEndpoint, "userInfoEndpoint must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");

        HttpHandler handler = HttpHandler.builder()
                .url(userInfoEndpoint)
                .connectionTimeoutSeconds(CONNECT_TIMEOUT_SECONDS)
                .readTimeoutSeconds(READ_TIMEOUT_SECONDS)
                .allowInsecureHttp(configuration.isAllowInsecureHttp())
                .build();

        HttpRequest request = handler.requestBuilder()
                .header(AUTHORIZATION, "Bearer " + accessToken)
                .header(ACCEPT, APPLICATION_JSON)
                .GET()
                .build();

        HttpClient client = handler.createHttpClient();
        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (!HttpStatusFamily.isSuccess(response.statusCode())) {
                throw new TransportException(
                        "userinfo endpoint returned unexpected HTTP status " + response.statusCode());
            }
            return parse(response.body());
        } catch (IOException e) {
            LOGGER.debug(e, "userinfo request failed: %s", e.getMessage());
            throw new TransportException("userinfo request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException("userinfo request interrupted", e);
        }
    }

    private UserInfoResponse parse(String body) {
        if (body == null || body.isBlank()) {
            throw new TransportException("Empty userinfo endpoint response");
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxContentSize) {
            throw new TransportException("userinfo endpoint response exceeds maximum allowed size");
        }
        try {
            UserInfoResponse userInfo = dslJson.deserialize(UserInfoResponse.class, bytes, bytes.length);
            if (userInfo == null || userInfo.sub == null || userInfo.sub.isBlank()) {
                throw new TransportException("userinfo endpoint response is missing the 'sub' claim");
            }
            return userInfo;
        } catch (IOException e) {
            LOGGER.debug(e, "Failed to parse userinfo endpoint response: %s", e.getMessage());
            throw new TransportException("Failed to parse userinfo endpoint response: " + e.getMessage(), e);
        }
    }
}
