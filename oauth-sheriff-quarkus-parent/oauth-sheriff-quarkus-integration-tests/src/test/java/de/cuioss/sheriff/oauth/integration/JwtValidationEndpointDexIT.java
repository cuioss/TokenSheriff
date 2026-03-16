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
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JWT validation using Dex as the OIDC provider.
 * These tests verify that OAuthSheriff correctly validates tokens issued by Dex,
 * ensuring multi-IDP compatibility beyond Keycloak.
 * <p>
 * Tests are skipped when Dex is not running (requires {@code COMPOSE_PROFILES=multi-idp}).
 * <p>
 * Dex does not support Keycloak-specific features like roles, groups, or custom scopes,
 * so bearer token producer/interceptor tests remain Keycloak-only.
 */
@DisplayName("JWT Validation Endpoint Tests - Dex Provider")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIf("isDexAvailable")
class JwtValidationEndpointDexIT extends BaseIntegrationTest {

    private static final CuiLogger LOGGER = new CuiLogger(JwtValidationEndpointDexIT.class);
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JWT_VALIDATE_PATH = "/jwt/validate";
    private static final String JWT_VALIDATE_ID_TOKEN_PATH = "/jwt/validate/id-token";
    private static final String TOKEN_FIELD_NAME = "token";

    private final TestRealm dexProvider = TestRealm.createDexProvider();

    static boolean isDexAvailable() {
        try {
            return TestRealm.createDexProvider().isProviderAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @Order(1)
    @DisplayName("Verify Dex health and token acquisition")
    void dexHealthAndTokenAcquisition() {
        assertTrue(dexProvider.isWellKnownEndpointHealthy(),
                "Dex well-known endpoint should be healthy");
        assertTrue(dexProvider.isJwksEndpointHealthy(),
                "Dex JWKS endpoint should be healthy");

        TestRealm.TokenResponse tokenResponse = dexProvider.obtainValidToken();
        assertNotNull(tokenResponse.accessToken(), "Dex access token should not be null");
        assertNotNull(tokenResponse.idToken(), "Dex ID token should not be null");
        // Dex may or may not return refresh tokens depending on configuration
        LOGGER.info("Successfully acquired tokens from Dex provider");
    }

    @Test
    @Order(2)
    @DisplayName("Validate Dex access token via Authorization header")
    void validateDexAccessToken() {
        TestRealm.TokenResponse tokenResponse = dexProvider.obtainValidToken();

        given()
                .contentType(CONTENT_TYPE_JSON)
                .header("Authorization", BEARER_PREFIX + tokenResponse.accessToken())
                .when()
                .post(JWT_VALIDATE_PATH)
                .then()
                .statusCode(200)
                .body("valid", equalTo(true))
                .body("message", equalTo("Access token is valid"));
    }

    @Test
    @Order(3)
    @DisplayName("Validate Dex ID token via request body")
    void validateDexIdToken() {
        TestRealm.TokenResponse tokenResponse = dexProvider.obtainValidToken();

        given()
                .contentType(CONTENT_TYPE_JSON)
                .body(Map.of(TOKEN_FIELD_NAME, tokenResponse.idToken()))
                .when()
                .post(JWT_VALIDATE_ID_TOKEN_PATH)
                .then()
                .statusCode(200)
                .body("valid", equalTo(true))
                .body("message", equalTo("ID token is valid"));
    }

    @Test
    @Order(4)
    @DisplayName("Validate multiple consecutive Dex token requests")
    void validateMultipleConsecutiveDexRequests() {
        TestRealm.TokenResponse tokenResponse = dexProvider.obtainValidToken();

        for (int i = 0; i < 3; i++) {
            given()
                    .contentType(CONTENT_TYPE_JSON)
                    .header("Authorization", BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .post(JWT_VALIDATE_PATH)
                    .then()
                    .statusCode(200)
                    .body("valid", equalTo(true));
        }
    }

    @Test
    @Order(5)
    @DisplayName("Validate Dex refresh token via request body (requires offline_access scope)")
    void validateDexRefreshToken() {
        // Dex issues refresh tokens when offline_access scope is requested
        TestRealm.TokenResponse tokenResponse = dexProvider.obtainValidTokenWithScopes("openid profile email offline_access");
        assertNotNull(tokenResponse.refreshToken(), "Dex should issue refresh token with offline_access scope");

        given()
                .contentType(CONTENT_TYPE_JSON)
                .body(Map.of(TOKEN_FIELD_NAME, tokenResponse.refreshToken()))
                .when()
                .post("/jwt/validate/refresh-token")
                .then()
                .statusCode(200)
                .body("valid", equalTo(true))
                .body("message", equalTo("Refresh token is valid"));
    }

    @Test
    @Order(6)
    @DisplayName("Verify JWKS resolution from Dex well-known endpoint")
    void verifyDexJwksResolution() {
        // Verify the full OIDC discovery chain works: well-known -> jwks_uri -> keys
        assertTrue(dexProvider.isWellKnownEndpointHealthy(),
                "Dex well-known endpoint should return valid OIDC configuration");
        assertTrue(dexProvider.isJwksEndpointHealthy(),
                "Dex JWKS endpoint (discovered via well-known) should return signing keys");
    }

    @Test
    @Order(10)
    @DisplayName("Reject invalid token claiming Dex issuer")
    void rejectInvalidDexToken() {
        given()
                .contentType(CONTENT_TYPE_JSON)
                .header("Authorization", BEARER_PREFIX + "invalid.dex.token")
                .when()
                .post(JWT_VALIDATE_PATH)
                .then()
                .statusCode(200)
                .body("valid", equalTo(false));
    }
}
