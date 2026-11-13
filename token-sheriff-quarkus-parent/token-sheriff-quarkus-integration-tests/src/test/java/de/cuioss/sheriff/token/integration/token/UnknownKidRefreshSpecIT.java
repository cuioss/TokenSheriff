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
package de.cuioss.sheriff.token.integration.token;

import de.cuioss.sheriff.token.integration.BaseIntegrationTest;
import de.cuioss.sheriff.token.integration.TestRealm;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.cuioss.sheriff.token.integration.TestConstants.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unknown-{@code kid} refresh spec for the opted-in {@code integration} issuer (ADR-0030).
 * <p>
 * The running native application configures the issuer with
 * {@code jwks.http.unknown-kid-refresh-window-seconds=5} and {@code jwks.http.initial-load-retry=true}.
 * A burst of Keycloak tokens of the integration realm whose header {@code kid} is replaced by invented
 * values must be refused on every request, and must neither block the request path nor degrade the
 * issuer: a genuine token of the same realm is accepted before and after the burst, and the readiness
 * check keeps reporting the JWKS endpoints {@code UP}.
 * <p>
 * Each genuine token is freshly acquired, so its acceptance is a real key lookup and not a hit of the
 * access-token cache.
 */
@DisplayName("Unknown-kid Refresh Spec")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UnknownKidRefreshSpecIT extends BaseIntegrationTest {

    private static final int BURST_SIZE = 50;
    private static final Pattern KID_PATTERN = Pattern.compile("\"kid\"\\s*:\\s*\"[^\"]*\"");
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final TestRealm realm = TestRealm.createIntegrationRealm();

    @Test
    @Order(1)
    @DisplayName("should accept a genuine token of the opted-in issuer before the burst")
    void shouldAcceptGenuineTokenBeforeBurst() {
        assertAccepted(realm.obtainValidToken().accessToken());
    }

    @Test
    @Order(2)
    @DisplayName("should refuse every token of a burst carrying invented kid values")
    void shouldRefuseEveryTokenWithInventedKid() {
        String genuineToken = realm.obtainValidToken().accessToken();

        for (int i = 0; i < BURST_SIZE; i++) {
            String forgedToken = withKid(genuineToken, "invented-kid-" + i);

            given()
                    .contentType(CONTENT_TYPE_JSON)
                    .header(AUTHORIZATION, BEARER_PREFIX + forgedToken)
                    .when()
                    .post(JWT_VALIDATE_PATH)
                    .then()
                    .statusCode(401)
                    .body(VALID, equalTo(false));
        }
    }

    @Test
    @Order(3)
    @DisplayName("should accept a genuine token of the opted-in issuer after the burst")
    void shouldAcceptGenuineTokenAfterBurst() {
        assertAccepted(realm.obtainValidToken().accessToken());
    }

    @Test
    @Order(4)
    @DisplayName("should keep reporting the JWKS endpoints UP after the burst")
    void shouldKeepReadinessUpAfterBurst() {
        given()
                .when()
                .get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    private static void assertAccepted(String accessToken) {
        given()
                .contentType(CONTENT_TYPE_JSON)
                .header(AUTHORIZATION, BEARER_PREFIX + accessToken)
                .when()
                .post(JWT_VALIDATE_PATH)
                .then()
                .statusCode(200)
                .body(VALID, equalTo(true));
    }

    /**
     * Replaces the {@code kid} of the token header, keeping payload and signature unchanged.
     *
     * @param token the compact JWS
     * @param kid   the {@code kid} to write into the header
     * @return the token with the replaced header
     */
    private static String withKid(String token, String kid) {
        int headerEnd = token.indexOf('.');
        String header = new String(URL_DECODER.decode(token.substring(0, headerEnd)), StandardCharsets.UTF_8);
        Matcher matcher = KID_PATTERN.matcher(header);
        assertTrue(matcher.find(), "The genuine token header must carry a kid: " + header);

        String replacedHeader = matcher.replaceFirst(Matcher.quoteReplacement("\"kid\":\"" + kid + "\""));
        assertNotEquals(header, replacedHeader, "The kid must actually have been replaced");

        return URL_ENCODER.encodeToString(replacedHeader.getBytes(StandardCharsets.UTF_8))
                + token.substring(headerEnd);
    }
}
