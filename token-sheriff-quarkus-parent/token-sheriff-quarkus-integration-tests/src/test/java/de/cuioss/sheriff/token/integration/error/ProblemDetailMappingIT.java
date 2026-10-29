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
package de.cuioss.sheriff.token.integration.error;

import de.cuioss.sheriff.token.integration.BaseIntegrationTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static de.cuioss.sheriff.token.integration.TestConstants.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves end to end that an unhandled {@code TokenValidationException} is rendered as an
 * RFC 9457 {@code application/problem+json} document by the extension-registered
 * {@code ProblemDetailExceptionMapper}, and that the document does not leak the exception message.
 */
@DisplayName("Problem Detail Mapping")
class ProblemDetailMappingIT extends BaseIntegrationTest {

    private static final String UNHANDLED_PATH = "/jwt/validate-unhandled";
    private static final String EXPLICIT_PATH = "/jwt/validate-explicit";
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String MALFORMED_TOKEN = "not-a-jwt";
    private static final String EXPLICIT_MESSAGE_PREFIX = "Token validation failed: ";

    @Test
    @DisplayName("Unhandled TokenValidationException is mapped to 401 application/problem+json without leaking the message")
    void shouldMapUnhandledTokenValidationExceptionToProblemJson() {
        String exceptionMessage = exceptionMessageFor(MALFORMED_TOKEN);

        Response response = given()
                .contentType(CONTENT_TYPE_JSON)
                .body(Map.of(TOKEN_FIELD_NAME, MALFORMED_TOKEN))
                .when()
                .post(UNHANDLED_PATH);

        response.then()
                .statusCode(401)
                .contentType(startsWith(PROBLEM_JSON))
                .body("type", endsWith("token-invalid"))
                .body("status", equalTo(401));

        String body = response.asString();
        assertFalse(body.contains(exceptionMessage),
                "the problem document must not contain the exception message: " + exceptionMessage);
    }

    /**
     * Reads the validator's exception message for the given token through the endpoint that
     * catches the exception and echoes its message, so the leak assertion compares against the
     * real message rather than a guessed one.
     */
    private static String exceptionMessageFor(String token) {
        String message = given()
                .contentType(CONTENT_TYPE_JSON)
                .body(Map.of(TOKEN_FIELD_NAME, token))
                .when()
                .post(EXPLICIT_PATH)
                .then()
                .statusCode(401)
                .extract()
                .path(MESSAGE);
        assertTrue(message != null && message.startsWith(EXPLICIT_MESSAGE_PREFIX) && message.length() > EXPLICIT_MESSAGE_PREFIX.length(),
                "the explicit endpoint must echo the exception message, got: " + message);
        return message.substring(EXPLICIT_MESSAGE_PREFIX.length());
    }
}
