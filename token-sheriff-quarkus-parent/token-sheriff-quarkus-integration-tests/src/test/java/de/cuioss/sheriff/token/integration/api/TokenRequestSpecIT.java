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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static de.cuioss.sheriff.token.integration.TestConstants.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * TokenRequest integration spec — tests that a token padded with whitespace is trimmed and reaches
 * validation instead of being treated as empty. Deserialization of a plain token value is covered by
 * {@code ApiValidationSpecIT}.
 */
@DisplayName("TokenRequest Spec")
class TokenRequestSpecIT extends BaseIntegrationTest {

    @Test
    @DisplayName("TokenRequest.isEmpty() should work correctly with token trimming")
    void tokenRequestIsEmptyWithTokenTrimming() {
        given()
                .contentType(CONTENT_TYPE_JSON)
                .body(Map.of(TOKEN_FIELD_NAME, "  valid.token.value  "))
                .when()
                .post("/jwt/validate-explicit")
                .then()
                .statusCode(401)
                .body(VALID, equalTo(false))
                .body(MESSAGE, containsString("Token validation failed"));
    }
}
