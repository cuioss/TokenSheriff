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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base test class for Keycloak-specific JWT validation endpoint tests.
 * Extends {@link AbstractCoreJwtValidationTest} with bearer token producer and interceptor
 * tests that require Keycloak-specific features (roles, groups, scopes).
 * <p>
 * Implementations provide a Keycloak test realm via {@link #getTestRealm()}.
 */
@DisplayName("JWT Validation Endpoint Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractJwtValidationEndpointTest extends AbstractCoreJwtValidationTest {

    private static final CuiLogger LOGGER = new CuiLogger(AbstractJwtValidationEndpointTest.class);

    @Nested
    @DisplayName("Bearer Token Producer Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class BearerTokenProducerTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "/jwt/bearer-token/with-scopes",
                "/jwt/bearer-token/with-roles",
                "/jwt/bearer-token/with-groups",
                "/jwt/bearer-token/with-all"
        })
        @DisplayName("Bearer token endpoint validation with different requirement types")
        void bearerTokenEndpointValidation(String endpoint) {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidTokenWithAllScopes();

            if (endpoint.contains("scopes")) {
                LOGGER.debug("bearerTokenWithScopes:%s", tokenResponse.accessToken());
            }

            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get(endpoint)
                    .then()
                    .statusCode(200);
            // Just verify the endpoint responds - content validation depends on actual token
        }
    }

    @Nested
    @DisplayName("Bearer Token Interceptor Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class BearerTokenInterceptorTests {

        @Test
        @DisplayName("Interceptor validation - basic (no requirements)")
        void interceptorValidationBasic() {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();

            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get("/jwt/interceptor/basic")
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo("Interceptor validation successful (basic)"));
        }

        @Test
        @DisplayName("Interceptor validation - with scopes")
        void interceptorValidationWithScopes() {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidTokenWithAllScopes();

            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get("/jwt/interceptor/with-scopes")
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo("Interceptor validation successful (with scopes)"));
        }

        @Test
        @DisplayName("Interceptor validation - with roles")
        void interceptorValidationWithRoles() {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidTokenWithAllScopes();

            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get("/jwt/interceptor/with-roles")
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo("Interceptor validation successful (with roles)"));
        }

        @Test
        @DisplayName("Interceptor validation - with groups")
        void interceptorValidationWithGroups() {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidTokenWithAllScopes();

            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get("/jwt/interceptor/with-groups")
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo("Interceptor validation successful (with groups)"));
        }

        @Test
        @DisplayName("Interceptor validation - with all requirements")
        void interceptorValidationWithAll() {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidTokenWithAllScopes();

            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get("/jwt/interceptor/with-all")
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo("Interceptor validation successful (with all requirements)"));
        }

        @Test
        @DisplayName("Interceptor validation - missing token returns 401")
        void interceptorValidationMissingToken() {
            given()
                    .when()
                    .get("/jwt/interceptor/basic")
                    .then()
                    .statusCode(401);
        }

        @Test
        @DisplayName("Interceptor validation - invalid token returns 401")
        void interceptorValidationInvalidToken() {
            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + "invalid.token.here")
                    .when()
                    .get("/jwt/interceptor/basic")
                    .then()
                    .statusCode(401);
        }

        @ParameterizedTest
        @ValueSource(strings = {"/jwt/interceptor/with-scopes", "/jwt/interceptor/with-roles", "/jwt/interceptor/with-groups"})
        @DisplayName("Interceptor validation - token with default scopes/roles/groups succeeds")
        void interceptorValidationWithDefaultPermissions(String endpoint) {
            // Note: obtainValidToken() gets a token with default scopes ("read"), roles, and groups ("/test-group")
            // as configured in the Keycloak realm. These match the requirements of the test endpoints.
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();

            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get(endpoint)
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true));
        }

        @Test
        @DisplayName("Interceptor validation with String return type - success")
        void interceptorValidationWithStringReturnSuccess() {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();

            String response = given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get("/jwt/interceptor/string-return")
                    .then()
                    .statusCode(200)
                    .contentType("text/plain")
                    .extract()
                    .asString();

            assertEquals("String return type validation successful", response);
        }

        @Test
        @DisplayName("Interceptor validation with String return type - failure throws WebApplicationException")
        void interceptorValidationWithStringReturnFailure() {
            // This tests the critical ClassCastException fix:
            // When validation fails and method returns String (not Response),
            // the interceptor should throw WebApplicationException, not try to cast Response to String
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();

            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get("/jwt/interceptor/string-return-fail")
                    .then()
                    .statusCode(401); // Constraint violation - missing required scope (scopes return 401)
        }

        @Test
        @DisplayName("Interceptor validation with String return type - missing token")
        void interceptorValidationWithStringReturnMissingToken() {
            // Test that missing token also properly throws WebApplicationException for String return type
            given()
                    .when()
                    .get("/jwt/interceptor/string-return")
                    .then()
                    .statusCode(401); // No token given
        }

        @Test
        @DisplayName("CDI injection validation with @BearerToken - validates token structure")
        void cdiInjectionWithBearerTokenValidation() {
            // This test validates the CDI-based validation pattern where the endpoint
            // manually checks authorization status using Instance<BearerTokenResult>

            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();

            var response = given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get("/jwt/interceptor/with-token-access")
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo("Token access successful"))
                    .extract()
                    .response();

            // Validate response contains expected token data
            var data = response.jsonPath().getMap("data");
            assertNotNull(data, "Response data should not be null");

            // Validate userId is present and not empty
            String userId = (String) data.get("userId");
            assertNotNull(userId, "userId should be present in response");
            assertFalse(userId.isBlank(), "userId should not be blank");

            // Validate scopes contains 'read'
            @SuppressWarnings("unchecked") var scopes = (Collection<String>) data.get("scopes");
            assertNotNull(scopes, "scopes should be present in response");
            assertTrue(scopes.contains("read"), "scopes should contain 'read'");

            // Validate roles and groups are present (may be empty but should be present)
            assertNotNull(data.get("roles"), "roles should be present in response");
            assertNotNull(data.get("groups"), "groups should be present in response");
        }

        @Test
        @DisplayName("CDI injection validation with @BearerToken - missing token returns 401")
        void cdiInjectionWithBearerTokenMissingToken() {
            // Verify that missing token is properly handled by CDI validation
            given()
                    .when()
                    .get("/jwt/interceptor/with-token-access")
                    .then()
                    .statusCode(401); // CDI validation should reject before processing
        }

        @Test
        @DisplayName("CDI injection validation with @BearerToken - invalid token returns 401")
        void cdiInjectionWithBearerTokenInvalidToken() {
            // Verify that invalid token is properly handled by CDI validation
            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + "invalid.jwt.token")
                    .when()
                    .get("/jwt/interceptor/with-token-access")
                    .then()
                    .statusCode(401); // CDI validation should reject invalid tokens
        }
    }
}
