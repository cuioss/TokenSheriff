/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.benchmarking.common.repository;

import com.google.gson.JsonObject;
import de.cuioss.benchmarking.common.http.HttpClientFactory;
import de.cuioss.benchmarking.common.token.TokenProvider;
import de.cuioss.benchmarking.common.util.JsonSerializationHelper;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.ERROR.FAILED_FETCH_TOKEN;
import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.WARN.TOKEN_POOL_EMPTY;

/**
 * Keycloak-based token repository for fetching real JWT tokens from a Keycloak server.
 * <p>
 * This implementation fetches actual tokens from a Keycloak authentication server,
 * making it suitable for integration benchmarks that need to test against real
 * authentication infrastructure.
 * </p>
 * <p>
 * Features:
 * <ul>
 *   <li>Fetches tokens from Keycloak using password grant</li>
 *   <li>Maintains a pool of tokens for rotation</li>
 *   <li>Supports SSL verification configuration</li>
 *   <li>Provides round-robin token distribution</li>
 * </ul>
 *
 * @author Oliver Wolff
 */
public class KeycloakTokenRepository implements TokenProvider {

    private static final CuiLogger LOGGER = new CuiLogger(KeycloakTokenRepository.class);
    private static final int HTTP_OK = 200;
    private static final int REQUEST_TIMEOUT_SECONDS = 10;

    private final TokenRepositoryConfig config;
    private final List<TokenInfo> tokenPool;
    private final AtomicInteger tokenIndex;
    private final HttpClient httpClient;

    /**
     * Creates a new KeycloakTokenRepository with the given configuration.
     *
     * @param config the configuration for connecting to Keycloak
     */
    public KeycloakTokenRepository(TokenRepositoryConfig config) {
        this.config = config;
        this.tokenPool = new ArrayList<>(config.getTokenPoolSize());
        this.tokenIndex = new AtomicInteger(0);

        this.httpClient = HttpClientFactory.getClient(config.isVerifySsl());

        // Initialize token pool
        initializeTokenPool();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Gets the next token from the pool using round-robin rotation.
     * This ensures even distribution and simulates cache miss scenarios.
     * If the pool is empty, fetches a single token directly from Keycloak.
     * </p>
     */
    @Override
    public String getNextToken() {
        if (tokenPool.isEmpty()) {
            LOGGER.warn(TOKEN_POOL_EMPTY);
            return fetchSingleToken();
        }

        int index = tokenIndex.getAndIncrement() % tokenPool.size();
        return tokenPool.get(index).accessToken();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the current size of the token pool.
     * </p>
     */
    @Override
    public int getTokenPoolSize() {
        return tokenPool.size();
    }

    private void initializeTokenPool() {
        LOGGER.debug("Initializing token pool with %s tokens", config.getTokenPoolSize());

        for (int i = 0; i < config.getTokenPoolSize(); i++) {
            String token = fetchSingleToken();
            tokenPool.add(new TokenInfo(token));
        }

        LOGGER.debug("Token pool initialized with %s tokens", tokenPool.size());
    }

    private String fetchSingleToken() {
        String tokenEndpoint = "%s/realms/%s/protocol/openid-connect/token".formatted(
                config.getKeycloakBaseUrl(), config.getRealm());

        try {
            // Build form data as URL encoded string
            String formData = "grant_type=" + URLEncoder.encode("password", StandardCharsets.UTF_8) +
                    "&client_id=" + URLEncoder.encode(config.getClientId(), StandardCharsets.UTF_8) +
                    "&client_secret=" + URLEncoder.encode(config.getClientSecret(), StandardCharsets.UTF_8) +
                    "&username=" + URLEncoder.encode(config.getUsername(), StandardCharsets.UTF_8) +
                    "&password=" + URLEncoder.encode(config.getPassword(), StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == HTTP_OK) {
                return extractAccessToken(response);
            } else {
                handleTokenFetchError(response);
                throw new TokenFetchException("Unexpected error - handleTokenFetchError should have thrown");
            }
        } catch (IOException e) {
            throw new TokenFetchException("Error fetching token from Keycloak", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TokenFetchException("Token fetch interrupted", e);
        }
    }


    /**
     * Internal class to hold token information.
     */
    private record TokenInfo(String accessToken) {

    }

    private String extractAccessToken(HttpResponse<String> response) {
        String responseBody = response.body();
        if (responseBody == null || responseBody.isEmpty()) {
            throw new TokenFetchException("Empty response body from token endpoint");
        }

        JsonObject jsonResponse = JsonSerializationHelper.fromJson(responseBody, JsonObject.class);
        if (jsonResponse == null || !jsonResponse.has("access_token")) {
            throw new TokenFetchException("No access_token field in response");
        }

        String token = jsonResponse.get("access_token").getAsString();
        if (token == null || token.isEmpty()) {
            throw new TokenFetchException("Access token is null or empty");
        }

        return token;
    }

    private void handleTokenFetchError(HttpResponse<String> response) {
        String errorBody = response.body() != null ? response.body() : "<no body>";

        LOGGER.error(FAILED_FETCH_TOKEN, response.statusCode(), errorBody);

        throw new TokenFetchException(
                "Failed to fetch token from Keycloak. Status: %d, Body: %s".formatted(
                        response.statusCode(), errorBody)
        );
    }

    /**
     * Custom exception for token fetch errors.
     */
    public static class TokenFetchException extends RuntimeException {
        public TokenFetchException(String message) {
            super(message);
        }

        public TokenFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Refreshes the token pool by fetching new tokens from Keycloak.
     * This replaces all existing tokens in the pool with fresh ones.
     * </p>
     *
     * @throws TokenFetchException if unable to fetch new tokens from Keycloak
     */
    @Override
    public void refreshTokens() {
        LOGGER.debug("Refreshing token pool with %s tokens", config.getTokenPoolSize());

        tokenPool.clear();

        for (int i = 0; i < config.getTokenPoolSize(); i++) {
            String token = fetchSingleToken();
            tokenPool.add(new TokenInfo(token));
        }

        // Reset the index to start from the beginning
        tokenIndex.set(0);

        LOGGER.debug("Token pool refreshed with %s tokens", tokenPool.size());
    }
}