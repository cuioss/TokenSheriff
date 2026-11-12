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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

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
 */
@DisplayName("Client engine refusals inside the native executable")
class ClientNativeImageSpecIT extends BaseIntegrationTest {

    private static final String CREDENTIAL_REJECTION_PATH = "/client-native/credential-rejection";
    private static final String DPOP_BINDING_PATH = "/client-native/dpop-binding";
    private static final String MODE = "mode";
    private static final String OUTCOME = "outcome";

    @Test
    @DisplayName("Keycloak invalid_grant answer is classified as a rejected credential")
    void shouldClassifyInvalidGrantAsCredentialRejection() {
        given()
                .when()
                .get(CREDENTIAL_REJECTION_PATH)
                .then()
                .statusCode(200)
                .body(OUTCOME, equalTo("credential_rejected"));
    }

    @Test
    @DisplayName("Token bound to another key is refused as a cnf.jkt mismatch")
    void shouldRefuseTokenBoundToAnotherKey() {
        given()
                .queryParam(MODE, "other")
                .when()
                .get(DPOP_BINDING_PATH)
                .then()
                .statusCode(200)
                .body(OUTCOME, equalTo("jkt_mismatch"));
    }

    @Test
    @DisplayName("Token bound to the proof key is accepted")
    void shouldAcceptTokenBoundToProofKey() {
        given()
                .queryParam(MODE, "match")
                .when()
                .get(DPOP_BINDING_PATH)
                .then()
                .statusCode(200)
                .body(OUTCOME, equalTo("accepted"));
    }
}
