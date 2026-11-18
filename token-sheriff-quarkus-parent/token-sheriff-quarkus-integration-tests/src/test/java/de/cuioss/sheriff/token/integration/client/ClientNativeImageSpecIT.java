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
package de.cuioss.sheriff.token.integration.client;

import de.cuioss.sheriff.token.integration.BaseIntegrationTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Native-image contract of the client engine ({@code CLIENT-26}), asserted from the refusing side.
 * <p>
 * The {@code /client-native} probes of {@code ClientEngineNativeEndpoint} drive the production
 * {@code TokenEndpointClient} inside the native executable, where the reflection registration of the
 * generated DslJson converters and the run-time initialization shipped with {@code token-sheriff-client}
 * actually matter. A missing registration does not fail loudly there: the engine reads an empty
 * response and the refusal it should have raised turns into a different outcome. Each test therefore
 * pins the exact refusal the engine must reach:
 * <ul>
 *   <li>a Keycloak {@code invalid_grant} answer is classified as a rejected credential;</li>
 *   <li>a token whose {@code cnf.jkt} names another key is refused as a jkt mismatch;</li>
 *   <li>the matching-key control is accepted, so the mismatch refusal comes from the binding check
 *       and not from a response that could not be parsed.</li>
 * </ul>
 * The two input guards of the probe endpoint are pinned as well: an unknown DPoP-binding mode and a
 * stub-token request whose thumbprint is not base64url are each answered with {@code 400}, so a guard
 * that stopped refusing would turn a probe into an unvalidated request instead of failing here.
 */
@DisplayName("Client engine refusals inside the native executable")
class ClientNativeImageSpecIT extends BaseIntegrationTest {

    private static final String CREDENTIAL_REJECTION_PATH = "/client-native/credential-rejection";
    private static final String DPOP_BINDING_PATH = "/client-native/dpop-binding";
    private static final String STUB_TOKEN_PATH = "/client-native/stub-token";
    private static final String MODE = "mode";
    private static final String OUTCOME = "outcome";
    private static final String ERROR = "error";
    private static final String PROBE_JKT_PARAMETER = "probe_jkt";

    @Test
    @DisplayName("Keycloak invalid_grant answer is classified as a rejected credential")
    void shouldClassifyInvalidGrantAsCredentialRejection() {
        Response response = given()
                .when()
                .get(CREDENTIAL_REJECTION_PATH);

        assertOutcome(response, "credential_rejected");
    }

    @Test
    @DisplayName("Token bound to another key is refused as a cnf.jkt mismatch")
    void shouldRefuseTokenBoundToAnotherKey() {
        Response response = given()
                .queryParam(MODE, "other")
                .when()
                .get(DPOP_BINDING_PATH);

        assertOutcome(response, "jkt_mismatch");
    }

    @Test
    @DisplayName("Token bound to the proof key is accepted")
    void shouldAcceptTokenBoundToProofKey() {
        Response response = given()
                .queryParam(MODE, "match")
                .when()
                .get(DPOP_BINDING_PATH);

        assertOutcome(response, "accepted");
    }

    @Test
    @DisplayName("An unknown DPoP-binding mode is refused with 400")
    void shouldRefuseUnknownDpopBindingMode() {
        Response response = given()
                .queryParam(MODE, "unknown")
                .when()
                .get(DPOP_BINDING_PATH);

        assertAll("Unknown mode refusal",
                () -> assertEquals(400, response.statusCode(), "An unknown mode must be refused"),
                () -> assertEquals("mode must be 'match' or 'other'", response.jsonPath().getString(ERROR),
                        "The refusal must name the accepted modes"));
    }

    @Test
    @DisplayName("A stub-token request whose thumbprint is not base64url is refused with 400")
    void shouldRefuseStubTokenWithNonBase64UrlThumbprint() {
        Response response = given()
                .contentType(ContentType.URLENC)
                .formParam(PROBE_JKT_PARAMETER, "not\"base64url")
                .when()
                .post(STUB_TOKEN_PATH);

        assertAll("Non-base64url thumbprint refusal",
                () -> assertEquals(400, response.statusCode(), "A thumbprint outside base64url must be refused"),
                () -> assertEquals("invalid_request", response.jsonPath().getString(ERROR),
                        "The refusal must carry the invalid_request error"));
    }

    private static void assertOutcome(Response response, String expectedOutcome) {
        assertAll("Probe outcome",
                () -> assertEquals(200, response.statusCode(), "The probe must answer 200"),
                () -> assertEquals(expectedOutcome, response.jsonPath().getString(OUTCOME),
                        "The probe must report the expected outcome"));
    }
}
