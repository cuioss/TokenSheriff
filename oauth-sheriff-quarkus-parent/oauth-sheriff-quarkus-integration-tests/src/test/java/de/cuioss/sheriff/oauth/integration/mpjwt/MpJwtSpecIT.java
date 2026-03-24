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
package de.cuioss.sheriff.oauth.integration.mpjwt;

import de.cuioss.sheriff.oauth.integration.BaseIntegrationTest;
import de.cuioss.sheriff.oauth.integration.TestRealm;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MicroProfile JWT specification features:
 * <ul>
 *   <li>{@code @Inject JsonWebToken} and {@code @Inject Principal}</li>
 *   <li>{@code @RolesAllowed}, {@code @DenyAll}, {@code @PermitAll}</li>
 * </ul>
 */
@DisplayName("MP-JWT Spec")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MpJwtSpecIT extends BaseIntegrationTest {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    // === JsonWebToken injection tests ===

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("de.cuioss.sheriff.oauth.integration.TestProviders#rolesProviders")
    @Order(1)
    @DisplayName("@Inject JsonWebToken — returns valid principal name with token")
    void jsonWebTokenPrincipalName(TestRealm realm) {
        var tokenResponse = realm.obtainValidToken();

        var response = given()
                .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                .when()
                .get("/jwt/mp-jwt/principal")
                .then()
                .statusCode(200)
                .extract()
                .response();

        String principalName = response.jsonPath().getString("principalName");
        assertNotNull(principalName, "principalName should not be null");
        assertFalse(principalName.isBlank(), "principalName should not be blank");
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("de.cuioss.sheriff.oauth.integration.TestProviders#groupsProviders")
    @Order(2)
    @DisplayName("@Inject JsonWebToken — groups match token groups")
    void jsonWebTokenGroups(TestRealm realm) {
        var tokenResponse = realm.obtainValidToken();

        var response = given()
                .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                .when()
                .get("/jwt/mp-jwt/principal")
                .then()
                .statusCode(200)
                .extract()
                .response();

        var groups = response.jsonPath().getList("groups");
        assertNotNull(groups, "groups should not be null");
        assertFalse(groups.isEmpty(), "groups should not be empty for providers with GROUPS capability");
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("de.cuioss.sheriff.oauth.integration.TestProviders#rolesProviders")
    @Order(3)
    @DisplayName("@Inject JsonWebToken — class is AccessTokenContent (not EmptyJsonWebToken)")
    void jsonWebTokenClassIsAccessTokenContent(TestRealm realm) {
        var tokenResponse = realm.obtainValidToken();

        var response = given()
                .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                .when()
                .get("/jwt/mp-jwt/principal")
                .then()
                .statusCode(200)
                .extract()
                .response();

        String principalClass = response.jsonPath().getString("principalClass");
        assertEquals("AccessTokenContent", principalClass,
                "JsonWebToken should be AccessTokenContent when a valid token is present");
    }

    // === @RolesAllowed tests ===

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("de.cuioss.sheriff.oauth.integration.TestProviders#groupsProviders")
    @Order(10)
    @DisplayName("@RolesAllowed(\"test-group\") — grants access when token has matching group")
    void rolesAllowedGrantsAccess(TestRealm realm) {
        var tokenResponse = realm.obtainValidToken();

        given()
                .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                .when()
                .get("/jwt/roles/test-group")
                .then()
                .statusCode(200)
                .body("access", equalTo("granted"))
                .body("role", equalTo("test-group"));
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("de.cuioss.sheriff.oauth.integration.TestProviders#rolesProviders")
    @Order(11)
    @DisplayName("@RolesAllowed(\"admin\") — returns 403 when token lacks admin group")
    void rolesAllowedDeniesAccess(TestRealm realm) {
        var tokenResponse = realm.obtainValidToken();

        given()
                .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                .when()
                .get("/jwt/roles/admin")
                .then()
                .statusCode(403);
    }

    // === @DenyAll tests ===

    @Test
    @Order(20)
    @DisplayName("@DenyAll — always returns 403 even with valid token")
    void denyAllWithToken() {
        var realm = TestRealm.createIntegrationRealm();
        var tokenResponse = realm.obtainValidToken();

        given()
                .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                .when()
                .get("/jwt/roles/deny-all")
                .then()
                .statusCode(403);
    }

    @Test
    @Order(21)
    @DisplayName("@DenyAll — returns 403 without token")
    void denyAllWithoutToken() {
        given()
                .when()
                .get("/jwt/roles/deny-all")
                .then()
                .statusCode(403);
    }

    // === @PermitAll tests ===

    @Test
    @Order(30)
    @DisplayName("@PermitAll — allows access without token")
    void permitAllWithoutToken() {
        given()
                .when()
                .get("/jwt/roles/permit-all")
                .then()
                .statusCode(200)
                .body("access", equalTo("granted"));
    }

    @Test
    @Order(31)
    @DisplayName("@PermitAll — allows access with token")
    void permitAllWithToken() {
        var realm = TestRealm.createIntegrationRealm();
        var tokenResponse = realm.obtainValidToken();

        given()
                .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                .when()
                .get("/jwt/roles/permit-all")
                .then()
                .statusCode(200)
                .body("access", equalTo("granted"));
    }

    // === @RolesAllowed without token ===

    @Test
    @Order(40)
    @DisplayName("@RolesAllowed — returns 401 when no token is present")
    void rolesAllowedNoToken() {
        given()
                .when()
                .get("/jwt/roles/user")
                .then()
                .statusCode(401);
    }
}
