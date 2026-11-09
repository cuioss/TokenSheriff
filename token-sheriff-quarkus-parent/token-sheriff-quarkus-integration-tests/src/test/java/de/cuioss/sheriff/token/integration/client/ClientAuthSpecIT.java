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
package de.cuioss.sheriff.token.integration.client;

import de.cuioss.sheriff.token.client.auth.ClientSecretBasicAuth;
import de.cuioss.sheriff.token.client.auth.ClientSecretPostAuth;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.flow.ClientAuthenticationFailedException;
import de.cuioss.sheriff.token.client.flow.TokenEndpointClient;
import de.cuioss.sheriff.token.integration.BaseIntegrationTest;
import de.cuioss.tools.logging.CuiLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Increment-3 client-authentication spec against the real Keycloak container ({@code CLIENT-4}).
 * <p>
 * Exercises the production shared-secret client-authentication strategies
 * ({@link ClientSecretBasicAuth} and {@link ClientSecretPostAuth}) end-to-end against the
 * integration realm's token endpoint, confirming Keycloak accepts each wire form and issues a token.
 * Two negatives pin the refusal side:
 * <ul>
 *   <li>a wrong secret presented through {@link ClientSecretBasicAuth} is sent through the production
 *       {@link TokenEndpointClient}, which must report it as {@link ClientAuthenticationFailedException}
 *       naming HTTP {@code 401} and issue no token. Keycloak answers this case with {@code 401} and
 *       {@code unauthorized_client}; the client library normalizes that answer into the RFC 6749 §5.2
 *       client-authentication refusal ({@code invalid_client}), so the spec asserts the typed refusal
 *       rather than the raw code;</li>
 *   <li>a raw request carrying no client authentication at all is refused with status {@code 400} or
 *       {@code 401}, an RFC 6749 §5.2 {@code error} member that is exactly {@code invalid_client}, and no
 *       token.</li>
 * </ul>
 * The container uses a self-signed certificate: the raw requests run against a trust-all TLS context
 * (integration-test scope only), mirroring {@link BaseIntegrationTest}'s relaxed HTTPS posture, while
 * the production client validates the chain against the generated test truststore.
 */
@DisplayName("Client authentication against real Keycloak")
class ClientAuthSpecIT extends BaseIntegrationTest {

    private static final CuiLogger LOGGER = new CuiLogger(ClientAuthSpecIT.class);

    private static final String INTEGRATION_ISSUER = KeycloakUrlSupport.EXTERNAL_BASE + "/realms/integration";
    private static final String TOKEN_ENDPOINT = INTEGRATION_ISSUER + "/protocol/openid-connect/token";
    private static final String CLIENT_ID = "integration-client";
    private static final String CLIENT_SECRET = "integration-secret";
    private static final String WRONG_CLIENT_SECRET = "not-the-integration-secret";

    /** The RFC 6749 §5.2 {@code error} member naming {@code invalid_client} — the member, not a substring. */
    private static final Pattern INVALID_CLIENT_ERROR = Pattern.compile("\"error\"\\s*:\\s*\"invalid_client\"");

    @Test
    @DisplayName("Should authenticate with client_secret_basic (Authorization header) and issue a token")
    void shouldAuthenticateWithClientSecretBasic() throws Exception {
        var auth = new ClientSecretBasicAuth(CLIENT_ID, CLIENT_SECRET);
        Map<String, String> form = new HashMap<>();
        Map<String, String> headers = new HashMap<>();
        form.put("grant_type", "client_credentials");
        auth.decorate(form, headers);

        HttpResponse<String> response = send(form, headers);
        LOGGER.debug("client_secret_basic token response: %s", response.body());

        assertAll("client_secret_basic token response",
                () -> assertEquals(200, response.statusCode(), "Keycloak must accept client_secret_basic"),
                () -> assertTrue(response.body().contains("access_token"), "response carries an access_token"));
    }

    @Test
    @DisplayName("Should authenticate with client_secret_post (form body) and issue a token")
    void shouldAuthenticateWithClientSecretPost() throws Exception {
        var auth = new ClientSecretPostAuth(CLIENT_ID, CLIENT_SECRET);
        Map<String, String> form = new HashMap<>();
        Map<String, String> headers = new HashMap<>();
        form.put("grant_type", "client_credentials");
        auth.decorate(form, headers);

        HttpResponse<String> response = send(form, headers);
        LOGGER.debug("client_secret_post token response: %s", response.body());

        assertAll("client_secret_post token response",
                () -> assertEquals(200, response.statusCode(), "Keycloak must accept client_secret_post"),
                () -> assertTrue(response.body().contains("access_token"), "response carries an access_token"));
    }

    @Test
    @DisplayName("Should surface a wrong client_secret_basic secret as a typed client-authentication refusal")
    void shouldRejectWrongClientSecret() {
        var auth = new ClientSecretBasicAuth(CLIENT_ID, WRONG_CLIENT_SECRET);
        Map<String, String> form = new HashMap<>();
        Map<String, String> headers = new HashMap<>();
        form.put("grant_type", "client_credentials");
        auth.decorate(form, headers);
        var tokenEndpoint = new TokenEndpointClient(wrongSecretConfiguration());

        var refused = assertThrows(ClientAuthenticationFailedException.class,
                () -> tokenEndpoint.requestToken(TOKEN_ENDPOINT, form, headers),
                "the production token-endpoint client must report a wrong secret as a failed client "
                        + "authentication (RFC 6749 §5.2 invalid_client), never issue a token");
        LOGGER.debug("wrong-secret refusal: %s", refused.getMessage());

        assertTrue(refused.getMessage().contains("HTTP 401"),
                "Keycloak refuses a wrong client secret with HTTP 401, got: " + refused.getMessage());
    }

    @Test
    @DisplayName("Should refuse a token request carrying no client authentication as invalid_client")
    void shouldRejectMissingClientAuthentication() throws Exception {
        Map<String, String> form = new HashMap<>();
        form.put("grant_type", "client_credentials");

        HttpResponse<String> response = send(form, Map.of());
        LOGGER.debug("unauthenticated token response: status=%s, body=%s", response.statusCode(), response.body());

        assertInvalidClient(response, "a request without client authentication");
    }

    /**
     * Asserts the RFC 6749 §5.2 client-authentication refusal: status {@code 400} or {@code 401}, an
     * {@code error} member that is exactly {@code invalid_client}, and no issued token.
     */
    private static void assertInvalidClient(HttpResponse<String> response, String scenario) {
        assertAll("Keycloak must refuse " + scenario,
                () -> assertTrue(Set.of(400, 401).contains(response.statusCode()),
                        "status must be 400 or 401, got " + response.statusCode()),
                () -> assertTrue(INVALID_CLIENT_ERROR.matcher(response.body()).find(),
                        "the error member must be invalid_client, got " + response.body()),
                () -> assertFalse(response.body().contains("access_token"),
                        "the refusal must not carry an access_token"));
    }

    /**
     * @return the {@code integration} realm configuration the wrong-secret request runs under, carrying
     *         the chain-validating trust material; the presented credential itself comes from the
     *         decorating {@link ClientSecretBasicAuth}, so the configured secret is the wrong one too
     */
    private static ClientConfiguration wrongSecretConfiguration() {
        return ClientConfiguration.builder()
                .issuer(INTEGRATION_ISSUER)
                .clientId(CLIENT_ID)
                .clientSecret(WRONG_CLIENT_SECRET)
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .sslContext(RefreshEngineSupport.chainValidatingSslContext())
                // The host-mapped Keycloak is reached on localhost.
                .allowLoopbackEgress(true)
                .build();
    }

    private static HttpResponse<String> send(Map<String, String> form, Map<String, String> headers) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .sslContext(trustAllContext())
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_ENDPOINT))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form)));
        headers.forEach(builder::header);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String encodeForm(Map<String, String> form) {
        StringJoiner joiner = new StringJoiner("&");
        form.forEach((key, value) -> joiner.add(URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        return joiner.toString();
    }

    private static SSLContext trustAllContext() throws Exception {
        TrustManager[] trustAll = {new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // integration-test trust-all
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // integration-test trust-all
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustAll, new SecureRandom());
        return context;
    }
}
