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

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for JWT validation using Dex as the OIDC provider.
 * Extends {@link AbstractCoreJwtValidationTest} to reuse all core validation tests
 * (access token, ID token, refresh token, multiple requests, metrics).
 * <p>
 * Adds Dex-specific tests for JWKS resolution and refresh tokens with offline_access scope.
 * <p>
 * Behavior depends on the active Maven profile:
 * <ul>
 *   <li>{@code -Pmulti-idp-tests}: Dex is required — tests <b>fail</b> if Dex is unreachable</li>
 *   <li>{@code -Pintegration-tests}: Dex is optional — tests are <b>skipped</b> if Dex is not running</li>
 * </ul>
 * <p>
 * Dex does not support Keycloak-specific features like roles, groups, or custom scopes,
 * so bearer token producer/interceptor tests in {@link AbstractJwtValidationEndpointTest}
 * are not inherited.
 */
@DisplayName("JWT Validation Endpoint Tests - Dex Provider")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIf("isDexExpectedOrAvailable")
class JwtValidationEndpointDexIT extends AbstractCoreJwtValidationTest {

    @Override
    protected TestRealm getTestRealm() {
        return TestRealm.createDexProvider();
    }

    /**
     * Determines whether Dex tests should run.
     * <ul>
     *   <li>When {@code multi.idp.required=true} (set by multi-idp-tests profile):
     *       always returns {@code true} so tests run and fail loudly if Dex is unreachable</li>
     *   <li>Otherwise: returns {@code true} only if Dex is actually reachable (graceful skip)</li>
     * </ul>
     */
    static boolean isDexExpectedOrAvailable() {
        if (Boolean.getBoolean("multi.idp.required")) {
            return true; // Dex must be running — let tests fail if it's not
        }
        try {
            return TestRealm.createDexProvider().isProviderAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @Order(5)
    @DisplayName("Validate Dex refresh token with offline_access scope")
    void validateDexRefreshTokenWithOfflineAccess() {
        // Dex issues refresh tokens when offline_access scope is requested
        TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidTokenWithScopes("openid profile email offline_access");
        assertNotNull(tokenResponse.refreshToken(), "Dex should issue refresh token with offline_access scope");

        given()
                .contentType(CONTENT_TYPE_JSON)
                .body(Map.of(TOKEN_FIELD_NAME, tokenResponse.refreshToken()))
                .when()
                .post(JWT_VALIDATE_REFRESH_TOKEN_PATH)
                .then()
                .statusCode(200)
                .body(VALID, equalTo(true))
                .body(MESSAGE, equalTo(REFRESH_TOKEN_IS_VALID));
    }

    @Test
    @Order(6)
    @DisplayName("Verify JWKS resolution from Dex well-known endpoint")
    void verifyDexJwksResolution() {
        assertTrue(getTestRealm().isWellKnownEndpointHealthy(),
                "Dex well-known endpoint should return valid OIDC configuration");
        assertTrue(getTestRealm().isJwksEndpointHealthy(),
                "Dex JWKS endpoint (discovered via well-known) should return signing keys");
    }
}
