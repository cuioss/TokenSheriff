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

import de.cuioss.tools.logging.CuiLogger;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base test class for core JWT validation tests that are provider-agnostic.
 * Tests in this class work with any OIDC provider (Keycloak, Dex, etc.) and cover:
 * <ul>
 *     <li>Provider health and token acquisition</li>
 *     <li>Access token validation via Authorization header</li>
 *     <li>ID token validation via request body</li>
 *     <li>Refresh token validation (when provider supports it)</li>
 *     <li>Multiple consecutive requests</li>
 *     <li>Invalid token rejection</li>
 *     <li>Security event metrics verification</li>
 * </ul>
 * <p>
 * Keycloak-specific tests (bearer token producers, interceptors with roles/groups/scopes)
 * are in {@link AbstractJwtValidationEndpointTest}.
 */
@DisplayName("Core JWT Validation Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractCoreJwtValidationTest extends BaseIntegrationTest {

    private static final CuiLogger LOGGER = new CuiLogger(AbstractCoreJwtValidationTest.class);

    public static final String AUTHORIZATION = "Authorization";
    static final String CONTENT_TYPE_JSON = "application/json";
    static final String BEARER_PREFIX = "Bearer ";
    static final String JWT_VALIDATE_PATH = "/jwt/validate";
    static final String JWT_VALIDATE_ID_TOKEN_PATH = "/jwt/validate/id-token";
    static final String JWT_VALIDATE_REFRESH_TOKEN_PATH = "/jwt/validate/refresh-token";
    static final String ACCESS_TOKEN_VALID_MESSAGE = "Access token is valid";
    static final String TOKEN_FIELD_NAME = "token";
    public static final String VALID = "valid";
    public static final String MESSAGE = "message";
    public static final String REFRESH_TOKEN_IS_VALID = "Refresh token is valid";

    /**
     * Returns the TestRealm instance to use for testing.
     *
     * @return TestRealm instance for testing
     */
    protected abstract TestRealm getTestRealm();

    @Test
    @Order(1)
    @DisplayName("Verify provider health and token acquisition")
    void providerHealthAndTokenAcquisition() {
        assertTrue(getTestRealm().isKeycloakHealthy(),
                getTestRealm().getProviderName() + " should be healthy and accessible");

        TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();
        assertNotNull(tokenResponse.accessToken(), "Access token should not be null");
        assertNotNull(tokenResponse.idToken(), "ID token should not be null");
        // Refresh token may be null for some providers without offline_access scope
    }

    @Nested
    @DisplayName("Positive Tests - Valid Token Validation")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PositiveTests {

        @Test
        @Order(2)
        @DisplayName("Validate access token via Authorization header")
        void validateAccessTokenEndpointPositive() {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();
            String validAccessToken = tokenResponse.accessToken();

            given()
                    .contentType(CONTENT_TYPE_JSON)
                    .header(AUTHORIZATION, BEARER_PREFIX + validAccessToken)
                    .when()
                    .post(JWT_VALIDATE_PATH)
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo(ACCESS_TOKEN_VALID_MESSAGE));
        }

        @Test
        @Order(3)
        @DisplayName("Validate ID token via request body")
        void validateIdTokenEndpointPositive() {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();
            String validIdToken = tokenResponse.idToken();

            given()
                    .contentType(CONTENT_TYPE_JSON)
                    .body(Map.of(TOKEN_FIELD_NAME, validIdToken))
                    .when()
                    .post(JWT_VALIDATE_ID_TOKEN_PATH)
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo("ID token is valid"));
        }

        @Test
        @Order(4)
        @DisplayName("Validate refresh token via request body")
        void validateRefreshTokenEndpointPositive() {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();
            String validRefreshToken = tokenResponse.refreshToken();

            // Skip if provider doesn't issue refresh tokens without offline_access
            if (validRefreshToken == null) {
                return;
            }

            given()
                    .contentType(CONTENT_TYPE_JSON)
                    .body(Map.of(TOKEN_FIELD_NAME, validRefreshToken))
                    .when()
                    .post(JWT_VALIDATE_REFRESH_TOKEN_PATH)
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo(REFRESH_TOKEN_IS_VALID));
        }

        @Test
        @Order(14)
        @DisplayName("Validate access token with multiple consecutive requests")
        void validateAccessTokenEndpointMultipleRequests() {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();
            String validAccessToken = tokenResponse.accessToken();

            for (int i = 0; i < 3; i++) {
                given()
                        .contentType(CONTENT_TYPE_JSON)
                        .header(AUTHORIZATION, BEARER_PREFIX + validAccessToken)
                        .when()
                        .post(JWT_VALIDATE_PATH)
                        .then()
                        .statusCode(200)
                        .body(VALID, equalTo(true))
                        .body(MESSAGE, equalTo(ACCESS_TOKEN_VALID_MESSAGE));
            }
        }
    }

    @Test
    @Order(15)
    @DisplayName("Reject invalid token")
    void rejectInvalidToken() {
        given()
                .contentType(CONTENT_TYPE_JSON)
                .header(AUTHORIZATION, BEARER_PREFIX + "invalid.token.here")
                .when()
                .post(JWT_VALIDATE_PATH)
                .then()
                .statusCode(200)
                .body(VALID, equalTo(false));
    }

    @Test
    @Order(99)
    @DisplayName("Verify SecurityEventCounter metrics have sensible bounds after all tests")
    void verifySecurityEventCounterMetrics() {
        LOGGER.debug("Verifying SecurityEventCounter metrics bounds after all integration tests");

        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> {
                    String response = given()
                            .when()
                            .get("/q/metrics")
                            .then()
                            .statusCode(200)
                            .extract()
                            .body()
                            .asString();
                    return response.contains("sheriff_oauth_validation");
                });

        String metricsResponse = given()
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .asString();

        LOGGER.debug("Raw metrics response length: %s", metricsResponse.length());

        String[] lines = metricsResponse.split("\n");
        for (String line : lines) {
            if (line.contains("sheriff_oauth_validation")) {
                LOGGER.debug("Found JWT validation metric: %s", line);
            }
        }

        assertTrue(metricsResponse.contains("sheriff_oauth_validation_errors_total"),
                "Should contain error metrics");

        boolean hasSuccessMetrics = metricsResponse.contains("sheriff_oauth_validation_success_total");
        LOGGER.debug("Success metrics present: %s", hasSuccessMetrics);

        Map<String, Double> parsedMetrics = parseMetricsResponse(metricsResponse);

        if (hasSuccessMetrics) {
            double accessTokensCreated = getMetricValue(parsedMetrics, "sheriff_oauth_validation_success_total", "ACCESS_TOKEN_CREATED");
            assertTrue(accessTokensCreated >= 10,
                    "Should have created at least 10 access tokens during integration tests, got: " + accessTokensCreated);
            assertTrue(accessTokensCreated <= 10000,
                    "Access token creation count seems unreasonably high: " + accessTokensCreated);

            double accessTokenCacheHits = getMetricValue(parsedMetrics, "sheriff_oauth_validation_success_total", "ACCESS_TOKEN_CACHE_HIT");
            assertTrue(accessTokenCacheHits >= 0,
                    "Cache hits should be non-negative: " + accessTokenCacheHits);

            double totalSuccess = accessTokensCreated + accessTokenCacheHits
                    + getMetricValue(parsedMetrics, "sheriff_oauth_validation_success_total", "ID_TOKEN_CREATED")
                    + getMetricValue(parsedMetrics, "sheriff_oauth_validation_success_total", "REFRESH_TOKEN_CREATED");
            assertTrue(totalSuccess >= 10,
                    "Total successful operations should be at least 10: " + totalSuccess);

            LOGGER.debug("""
                    SecurityEventCounter metrics validation passed - ACCESS_TOKEN_CREATED: %s, \
                    ACCESS_TOKEN_CACHE_HIT: %s, Total Success: %s""",
                    accessTokensCreated, accessTokenCacheHits, totalSuccess);
        } else {
            LOGGER.debug("Success metrics not found - this indicates SecurityEventCounter success events are not being published");
            assertFalse(parsedMetrics.isEmpty(), "Should have some metrics available");
        }
    }

    private Map<String, Double> parseMetricsResponse(String metricsResponse) {
        Map<String, Double> metrics = new HashMap<>();
        String[] lines = metricsResponse.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#") || line.isEmpty()) {
                continue;
            }

            int spaceIndex = line.lastIndexOf(' ');
            if (spaceIndex > 0) {
                String metricPart = line.substring(0, spaceIndex);
                String valuePart = line.substring(spaceIndex + 1);

                try {
                    double value = Double.parseDouble(valuePart);
                    metrics.put(metricPart, value);
                } catch (NumberFormatException e) {
                    // Ignore invalid metrics
                }
            }
        }

        return metrics;
    }

    private double getMetricValue(Map<String, Double> metrics, String metricPrefix, String eventType) {
        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            String metricName = entry.getKey();
            if (metricName.startsWith(metricPrefix) &&
                    metricName.contains("event_type=\"" + eventType + "\"")) {
                return entry.getValue();
            }
        }
        return 0.0;
    }
}
