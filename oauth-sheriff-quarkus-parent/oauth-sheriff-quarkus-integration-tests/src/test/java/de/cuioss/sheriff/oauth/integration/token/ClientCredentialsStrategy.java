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
 * Used for Zitadel where the service account (machine user) authenticates
 * directly via {@code grant_type=client_credentials}. The {@code client_id}
 * is the machine user's username, the {@code client_secret} is generated on
 * the user (not on an OIDC app). The machine user must have
 * {@code ACCESS_TOKEN_TYPE_JWT} so the returned token is a signed JWT.
 */
public class ClientCredentialsStrategy implements TokenAcquisitionStrategy {

    private final String serviceUserName;
    private final String serviceUserSecret;
    private final String hostHeader;

    /**
     * @param serviceUserName   machine user's username (used as client_id)
     * @param serviceUserSecret machine user's generated secret
     * @param hostHeader        Zitadel requires Host header matching EXTERNALDOMAIN
     */
    public ClientCredentialsStrategy(String serviceUserName, String serviceUserSecret,
                                     String hostHeader) {
        this.serviceUserName = serviceUserName;
        this.serviceUserSecret = serviceUserSecret;
        this.hostHeader = hostHeader;
    }

    @Override
    public TokenResponse acquireToken(String baseUrl, String tokenEndpoint,
                                      String clientId, String clientSecret, String scopes) {
        Response tokenResponse = given()
                .baseUri(baseUrl)
                .header("Host", hostHeader)
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", serviceUserName)
                .formParam("client_secret", serviceUserSecret)
                .formParam("scope", scopes)
                .when()
                .post(tokenEndpoint);

        assertEquals(200, tokenResponse.statusCode(),
                "Client credentials failed for '%s'. Response: %s"
                        .formatted(serviceUserName, tokenResponse.body().asString()));

        Map<String, Object> tokenData = tokenResponse.jsonPath().getMap("");
        return new TokenResponse(
                (String) tokenData.get("access_token"),
                (String) tokenData.get("id_token"),
                (String) tokenData.get("refresh_token"));
    }
}
