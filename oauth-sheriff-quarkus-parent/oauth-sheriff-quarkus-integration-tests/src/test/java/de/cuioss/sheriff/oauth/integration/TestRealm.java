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
package de.cuioss.sheriff.oauth.integration;

import io.restassured.response.Response;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Helper class for managing test realms in integration tests.
 * Supports multiple OIDC providers (Keycloak, Dex) with configurable base URLs
 * and token endpoint paths.
 */
public class TestRealm {

    private static final String KEYCLOAK_BASE_URL = "https://localhost:1443";
    private static final String KEYCLOAK_MANAGEMENT_URL = "https://localhost:1090";

    private static final String TOKEN_ENDPOINT_TEMPLATE = "/realms/%s/protocol/openid-connect/token";
    private static final String CERTS_ENDPOINT_TEMPLATE = "/realms/%s/protocol/openid-connect/certs";
    private static final String WELL_KNOWN_ENDPOINT_TEMPLATE = "/realms/%s/.well-known/openid-configuration";

    // Dex provider constants
    private static final String DEX_BASE_URL = "https://localhost:2556";
    private static final String DEX_WELL_KNOWN_PATH = "/dex/.well-known/openid-configuration";
    private static final String DEX_TOKEN_PATH = "/dex/token";

    // Integration realm constants
    private static final String INTEGRATION_REALM_ID = "integration";
    private static final String INTEGRATION_CLIENT_ID = "integration-client";
    private static final String INTEGRATION_CLIENT_SECRET = "integration-secret";
    private static final String INTEGRATION_USERNAME = "integration-user";
    private static final String INTEGRATION_PASSWORD = "integration-password";

    // DPoP client constants (same realm, different client with dpop.bound.access.tokens=true)
    private static final String DPOP_CLIENT_ID = "dpop-client";
    private static final String DPOP_CLIENT_SECRET = "dpop-secret";

    // JWE client constants (same realm, client with id_token_encrypted_response_alg/enc)
    private static final String JWE_CLIENT_ID = "jwe-client";
    private static final String JWE_CLIENT_SECRET = "jwe-secret";

    // Benchmark realm constants
    private static final String BENCHMARK_REALM_ID = "benchmark";
    private static final String BENCHMARK_CLIENT_ID = "benchmark-client";
    private static final String BENCHMARK_CLIENT_SECRET = "benchmark-secret";
    private static final String BENCHMARK_USERNAME = "benchmark-user";
    private static final String BENCHMARK_PASSWORD = "benchmark-password";

    private final String realmIdentifier;
    private final String clientId;
    private final String clientSecret;
    private final String username;
    private final String password;
    private final String baseUrl;
    private final String tokenEndpoint;
    private final String providerName;

    /**
     * Creates a new TestRealm instance for Keycloak (backward compatible).
     *
     * @param realmIdentifier the realm identifier (e.g., "integration", "benchmark")
     * @param clientId the client ID for authentication
     * @param clientSecret the client secret for authentication
     * @param username the username for authentication
     * @param password the password for authentication
     */
    public TestRealm(String realmIdentifier, String clientId, String clientSecret, String username, String password) {
        this(realmIdentifier, clientId, clientSecret, username, password,
                KEYCLOAK_BASE_URL, TOKEN_ENDPOINT_TEMPLATE.formatted(realmIdentifier), "Keycloak");
    }

    /**
     * Creates a new TestRealm instance with custom provider configuration.
     *
     * @param realmIdentifier the realm/provider identifier for display purposes
     * @param clientId the client ID for authentication
     * @param clientSecret the client secret for authentication
     * @param username the username for authentication
     * @param password the password for authentication
     * @param baseUrl the provider base URL (e.g., "https://localhost:2556")
     * @param tokenEndpoint the token endpoint path (e.g., "/dex/token")
     * @param providerName the provider name for display in test output
     */
    public TestRealm(String realmIdentifier, String clientId, String clientSecret, String username, String password,
                     String baseUrl, String tokenEndpoint, String providerName) {
        this.realmIdentifier = realmIdentifier;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.username = username;
        this.password = password;
        this.baseUrl = baseUrl;
        this.tokenEndpoint = tokenEndpoint;
        this.providerName = providerName;
    }

    /**
     * Returns the provider name for display in test output.
     *
     * @return the provider name (e.g., "Keycloak", "Dex")
     */
    public String getProviderName() {
        return providerName;
    }

    /**
     * Factory method to create a TestRealm instance for integration tests.
     *
     * @return TestRealm configured for integration realm
     */
    public static TestRealm createIntegrationRealm() {
        return new TestRealm(
                INTEGRATION_REALM_ID,
                INTEGRATION_CLIENT_ID,
                INTEGRATION_CLIENT_SECRET,
                INTEGRATION_USERNAME,
                INTEGRATION_PASSWORD
        );
    }

    /**
     * Factory method to create a TestRealm instance for DPoP testing.
     * Uses the dpop-client in the integration realm which has {@code dpop.bound.access.tokens=true}.
     *
     * @return TestRealm configured for DPoP testing
     */
    public static TestRealm createDpopRealm() {
        return new TestRealm(
                INTEGRATION_REALM_ID,
                DPOP_CLIENT_ID,
                DPOP_CLIENT_SECRET,
                INTEGRATION_USERNAME,
                INTEGRATION_PASSWORD
        );
    }

    /**
     * Factory method to create a TestRealm instance for JWE testing.
     * Uses the jwe-client in the integration realm which has encrypted ID token response configured.
     *
     * @return TestRealm configured for JWE testing
     */
    public static TestRealm createJweRealm() {
        return new TestRealm(
                INTEGRATION_REALM_ID,
                JWE_CLIENT_ID,
                JWE_CLIENT_SECRET,
                INTEGRATION_USERNAME,
                INTEGRATION_PASSWORD
        );
    }

    /**
     * Factory method to create a TestRealm instance for benchmark tests.
     *
     * @return TestRealm configured for benchmark realm
     */
    public static TestRealm createBenchmarkRealm() {
        return new TestRealm(
                BENCHMARK_REALM_ID,
                BENCHMARK_CLIENT_ID,
                BENCHMARK_CLIENT_SECRET,
                BENCHMARK_USERNAME,
                BENCHMARK_PASSWORD
        );
    }

    /**
     * Factory method to create a TestRealm instance for Dex OIDC provider testing.
     * Dex is a lightweight, OpenID Certified provider used for multi-IDP validation.
     *
     * @return TestRealm configured for Dex provider
     */
    public static TestRealm createDexProvider() {
        return new TestRealm(
                "dex",
                "dex-client",
                "dex-secret",
                "dex-user@example.com",
                "dex-password",
                DEX_BASE_URL,
                DEX_TOKEN_PATH,
                "Dex"
        );
    }

    /**
     * Obtains a valid token from the realm.
     * Similar to JwtValidationIntegrationIT#obtainValidTokenFromIntegrationRealm
     *
     * @return TokenResponse containing access, ID, and refresh tokens
     */
    public TokenResponse obtainValidToken() {
        return obtainValidTokenWithScopes("openid profile email");
    }

    /**
     * Obtains a valid token from the realm with specific scopes.
     * This method allows requesting tokens with custom scopes for testing different scenarios.
     *
     * @param scopes the scopes to request (e.g., "openid profile email read")
     * @return TokenResponse containing access, ID, and refresh tokens
     */
    public TokenResponse obtainValidTokenWithScopes(String scopes) {
        Response tokenResponse = given()
                .baseUri(baseUrl)
                .contentType("application/x-www-form-urlencoded")
                .formParam("client_id", clientId)
                .formParam("client_secret", clientSecret)
                .formParam("username", username)
                .formParam("password", password)
                .formParam("grant_type", "password")
                .formParam("scope", scopes)
                .when()
                .post(tokenEndpoint);

        assertEquals(200, tokenResponse.statusCode(),
                "Should be able to obtain tokens from " + providerName + "/" + realmIdentifier + " with scopes: " + scopes + ". Response: " + tokenResponse.body().asString());

        Map<String, Object> tokenData = tokenResponse.jsonPath().getMap("");

        String accessToken = (String) tokenData.get("access_token");
        String idToken = (String) tokenData.get("id_token");
        String refreshToken = (String) tokenData.get("refresh_token");

        // Validate tokens (refresh token may be null for providers that don't issue it
        // without offline_access scope, e.g., Dex)
        validateToken(accessToken, "Access token");
        validateToken(idToken, "ID token");
        if (refreshToken != null) {
            validateToken(refreshToken, "Refresh token");
        }

        return new TokenResponse(accessToken, idToken, refreshToken);
    }

    /**
     * Obtains a valid token with all required scopes for bearer token tests.
     * This includes the "read" scope which is required by the BearerToken annotations.
     *
     * @return TokenResponse containing access, ID, and refresh tokens with all required scopes
     */
    public TokenResponse obtainValidTokenWithAllScopes() {
        return obtainValidTokenWithScopes("openid profile email read");
    }

    /**
     * Obtains a DPoP-bound token from Keycloak by sending a DPoP proof header
     * with the token request. The returned access token will contain a {@code cnf.jkt} claim.
     *
     * @param dpopHelper the DPoP proof helper (provides the key pair and proof generation)
     * @return TokenResponse containing DPoP-bound access, ID, and refresh tokens
     */
    public TokenResponse obtainDpopBoundToken(DpopProofHelper dpopHelper) {
        String tokenUrl = baseUrl + tokenEndpoint;
        String dpopProof = dpopHelper.createTokenEndpointProof(tokenUrl);

        Response tokenResponse = given()
                .baseUri(baseUrl)
                .contentType("application/x-www-form-urlencoded")
                .header("DPoP", dpopProof)
                .formParam("client_id", clientId)
                .formParam("client_secret", clientSecret)
                .formParam("username", username)
                .formParam("password", password)
                .formParam("grant_type", "password")
                .formParam("scope", "openid profile email")
                .when()
                .post(tokenEndpoint);

        assertEquals(200, tokenResponse.statusCode(),
                "Should be able to obtain DPoP-bound tokens from " + providerName + "/" + realmIdentifier
                        + ". Response: " + tokenResponse.body().asString());

        Map<String, Object> tokenData = tokenResponse.jsonPath().getMap("");

        String accessToken = (String) tokenData.get("access_token");
        String idToken = (String) tokenData.get("id_token");
        String refreshToken = (String) tokenData.get("refresh_token");

        validateToken(accessToken, "DPoP-bound access token");
        validateToken(idToken, "ID token");
        validateToken(refreshToken, "Refresh token");

        return new TokenResponse(accessToken, idToken, refreshToken);
    }

    /**
     * Checks if the well-known endpoint is healthy/available.
     * For Keycloak, uses the realm-specific well-known URL.
     * For other providers (e.g., Dex), uses the provider-specific well-known path.
     *
     * @return true if the endpoint is healthy, false otherwise
     */
    public boolean isWellKnownEndpointHealthy() {
        String wellKnownPath = "Keycloak".equals(providerName)
                ? WELL_KNOWN_ENDPOINT_TEMPLATE.formatted(realmIdentifier)
                : DEX_WELL_KNOWN_PATH;

        try {
            Response response = given()
                    .baseUri(baseUrl)
                    .when()
                    .get(wellKnownPath);

            return response.statusCode() == 200 &&
                    response.body().asString().contains("\"issuer\"") &&
                    response.body().asString().contains("\"jwks_uri\"");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if the JWKS endpoint is accessible.
     * For Keycloak, uses the realm-specific certs URL.
     * For other providers (e.g., Dex), discovers the JWKS URI from the well-known endpoint.
     *
     * @return true if the JWKS endpoint is accessible, false otherwise
     */
    public boolean isJwksEndpointHealthy() {
        try {
            if ("Keycloak".equals(providerName)) {
                Response response = given()
                        .baseUri(baseUrl)
                        .when()
                        .get(CERTS_ENDPOINT_TEMPLATE.formatted(realmIdentifier));

                return response.statusCode() == 200 && response.body().asString().contains("\"keys\"");
            }
            // For non-Keycloak providers, discover JWKS URI from well-known endpoint
            Response wellKnownResponse = given()
                    .baseUri(baseUrl)
                    .when()
                    .get(DEX_WELL_KNOWN_PATH);

            if (wellKnownResponse.statusCode() != 200) {
                return false;
            }
            String jwksUri = wellKnownResponse.jsonPath().getString("jwks_uri");
            if (jwksUri == null) {
                return false;
            }
            Response jwksResponse = given().when().get(jwksUri);
            return jwksResponse.statusCode() == 200 && jwksResponse.body().asString().contains("\"keys\"");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if the provider is healthy and accessible.
     * For Keycloak, checks the management endpoint.
     * For other providers, checks the well-known endpoint.
     *
     * @return true if the provider is healthy, false otherwise
     */
    public boolean isKeycloakHealthy() {
        if ("Keycloak".equals(providerName)) {
            try {
                Response response = given()
                        .baseUri(KEYCLOAK_MANAGEMENT_URL)
                        .when()
                        .get("/health/ready");
                return response.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        }
        // For non-Keycloak providers, use well-known endpoint as health check
        return isWellKnownEndpointHealthy();
    }

    /**
     * Checks if this provider is reachable. Used for conditional test execution.
     *
     * @return true if the provider is reachable, false otherwise
     */
    public boolean isProviderAvailable() {
        return isWellKnownEndpointHealthy();
    }

    private void validateToken(String token, String tokenType) {
        assertNotNull(token, tokenType + " should not be null");
        assertFalse(token.isEmpty(), tokenType + " should not be empty");
    }

    /**
     * Response object containing the different token types.
     */
    public record TokenResponse(String accessToken, String idToken, String refreshToken) {
    }
}
