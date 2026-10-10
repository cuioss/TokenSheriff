/*
 * Copyright © 2025-present CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.token.integration.api;

import de.cuioss.sheriff.token.integration.BaseIntegrationTest;
import de.cuioss.sheriff.token.integration.TestRealm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static de.cuioss.sheriff.token.integration.TestConstants.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * TokenRequest integration spec — tests how {@code /jwt/validate-explicit} treats a token value that
 * is padded with whitespace.
 * <p>
 * {@link #paddedValidTokenIsTrimmedBeforeValidation()} proves the trimming: a valid access token is
 * accepted with and without padding. An endpoint that passed the padded value on untrimmed would
 * reject it. {@link #paddedNonTokenReachesValidation()} proves only that a padded value is not
 * treated as empty; its {@code 401} is the same with and without trimming. Deserialization of a
 * plain token value is covered by {@code ApiValidationSpecIT}.
 */
@DisplayName("TokenRequest Spec")
class TokenRequestSpecIT extends BaseIntegrationTest {

    private static final String VALIDATE_EXPLICIT_PATH = "/jwt/validate-explicit";
    private static final String ACCESS_TOKEN_VALID_MESSAGE = "Access token is valid";
    private static final String SUBJECT = "data.subject";

    @Test
    @DisplayName("A valid access token padded with whitespace is trimmed and accepted")
    void paddedValidTokenIsTrimmedBeforeValidation() {
        String accessToken = TestRealm.createIntegrationRealm().obtainValidToken().accessToken();

        String subject = given()
                .contentType(CONTENT_TYPE_JSON)
                .body(Map.of(TOKEN_FIELD_NAME, accessToken))
                .when()
                .post(VALIDATE_EXPLICIT_PATH)
                .then()
                .statusCode(200)
                .body(VALID, equalTo(true))
                .body(MESSAGE, equalTo(ACCESS_TOKEN_VALID_MESSAGE))
                .extract()
                .path(SUBJECT);

        given()
                .contentType(CONTENT_TYPE_JSON)
                .body(Map.of(TOKEN_FIELD_NAME, "  " + accessToken + " \t"))
                .when()
                .post(VALIDATE_EXPLICIT_PATH)
                .then()
                .statusCode(200)
                .body(VALID, equalTo(true))
                .body(MESSAGE, equalTo(ACCESS_TOKEN_VALID_MESSAGE))
                .body(SUBJECT, equalTo(subject));
    }

    @Test
    @DisplayName("A padded value that is no token is not treated as empty and reaches validation")
    void paddedNonTokenReachesValidation() {
        given()
                .contentType(CONTENT_TYPE_JSON)
                .body(Map.of(TOKEN_FIELD_NAME, "  valid.token.value  "))
                .when()
                .post(VALIDATE_EXPLICIT_PATH)
                .then()
                .statusCode(401)
                .body(VALID, equalTo(false))
                .body(MESSAGE, containsString("Token validation failed"));
    }
}
