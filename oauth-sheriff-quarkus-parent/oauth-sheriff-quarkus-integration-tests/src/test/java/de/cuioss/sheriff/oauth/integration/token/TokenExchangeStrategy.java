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
 * Token acquisition via OAuth 2.0 Client Credentials grant.
 * <p>
 * Used for IDPs where the service account (machine user) directly holds
 * credentials and can obtain JWT access tokens via {@code grant_type=client_credentials}.
 * In Zitadel, the {@code client_id} is the service account's user ID and
 * the {@code client_secret} is generated on the user (not on an OIDC app).
 * <p>
 * The service account must have {@code ACCESS_TOKEN_TYPE_JWT} configured
 * so the returned access token is a signed JWT, not an opaque token.
 */
public class TokenExchangeStrategy implements TokenAcquisitionStrategy {

    private final String serviceUserId;
    private final String serviceUserSecret;

    /**
     * @param serviceUserId     machine user's ID (used as client_id)
     * @param serviceUserSecret machine user's generated secret (used as client_secret)
     */
    public TokenExchangeStrategy(String serviceUserId, String serviceUserSecret) {
        this.serviceUserId = serviceUserId;
        this.serviceUserSecret = serviceUserSecret;
    }

    @Override
    public TokenResponse acquireToken(String baseUrl, String tokenEndpoint,
                                      String clientId, String clientSecret, String scopes) {
        // client_credentials with service account credentials (not the OIDC app)
        // Host header must match Zitadel's EXTERNALDOMAIN for instance routing
        Response tokenResponse = given()
                .baseUri(baseUrl)
                .header("Host", "zitadel:3080")
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", serviceUserId)
                .formParam("client_secret", serviceUserSecret)
                .formParam("scope", scopes)
                .when()
                .post(tokenEndpoint);

        assertEquals(200, tokenResponse.statusCode(),
                "Client credentials request failed for service user '%s'. Response: %s"
                        .formatted(serviceUserId, tokenResponse.body().asString()));

        Map<String, Object> tokenData = tokenResponse.jsonPath().getMap("");
        return new TokenResponse(
                (String) tokenData.get("access_token"),
                (String) tokenData.get("id_token"),
                (String) tokenData.get("refresh_token"));
    }
}
