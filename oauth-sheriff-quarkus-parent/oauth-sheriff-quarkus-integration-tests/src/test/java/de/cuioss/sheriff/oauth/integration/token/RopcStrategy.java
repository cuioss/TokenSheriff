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
package de.cuioss.sheriff.oauth.integration.token;

import de.cuioss.sheriff.oauth.integration.TestRealm.TokenResponse;
import io.restassured.response.Response;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Token acquisition via Resource Owner Password Credentials (ROPC) grant.
 * Used by providers that support {@code grant_type=password} (Keycloak, Dex).
 * <p>
 * Note: ROPC is deprecated in OAuth 2.1 and not supported by modern IDPs
 * like Zitadel or Ory Hydra. Use {@link TokenExchangeStrategy} for those.
 */
public class RopcStrategy implements TokenAcquisitionStrategy {

    private final String username;
    private final String password;

    public RopcStrategy(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /** Returns the ROPC username — needed by DPoP token acquisition. */
    public String getUsername() {
        return username;
    }

    /** Returns the ROPC password — needed by DPoP token acquisition. */
    public String getPassword() {
        return password;
    }

    @Override
    public TokenResponse acquireToken(String baseUrl, String tokenEndpoint,
                                      String clientId, String clientSecret, String scopes) {
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
                "ROPC token request failed for user '%s'. Response: %s"
                        .formatted(username, tokenResponse.body().asString()));

        Map<String, Object> tokenData = tokenResponse.jsonPath().getMap("");
        return new TokenResponse(
                (String) tokenData.get("access_token"),
                (String) tokenData.get("id_token"),
                (String) tokenData.get("refresh_token"));
    }
}
