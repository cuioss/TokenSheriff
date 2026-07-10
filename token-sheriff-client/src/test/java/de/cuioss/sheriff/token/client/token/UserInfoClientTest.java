/*
 * Copyright © 2022 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.token.client.token;

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.validation.test.dispatcher.UserInfoDispatcher;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("UserInfoClient userinfo fetch (OIDC Core §5.3)")
class UserInfoClientTest {

    @Getter
    private final UserInfoDispatcher moduleDispatcher = new UserInfoDispatcher();

    @BeforeEach
    void resetDispatcher() {
        moduleDispatcher.setCallCounter(0);
        moduleDispatcher.returnDefault();
    }

    private static ClientConfiguration config() {
        return ClientConfiguration.builder()
                .issuer("https://" + Generators.letterStrings(3, 10).next() + ".example.com")
                .clientId(Generators.nonBlankStrings().next())
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(true)
                .build();
    }

    private static String userInfoEndpoint(URIBuilder uriBuilder) {
        return uriBuilder.addPathSegments("oidc", "userinfo").buildAsString();
    }

    private UserInfoClient userInfoClient() {
        return new UserInfoClient(config());
    }

    @Test
    @DisplayName("Should fetch and normalize the userinfo document")
    void shouldFetchUserInfo(URIBuilder uriBuilder) {
        UserInfoResponse userInfo = userInfoClient().fetchUserInfo(
                userInfoEndpoint(uriBuilder), Generators.nonBlankStrings().next());

        assertAll("userinfo response",
                () -> assertEquals(UserInfoDispatcher.DEFAULT_SUBJECT, userInfo.getSub().orElseThrow(),
                        "the sub is captured"),
                () -> assertEquals("test@example.com", userInfo.getEmail().orElseThrow(), "the email is captured"),
                () -> assertTrue(userInfo.isEmailVerified(), "the email_verified flag is captured"));
    }

    @Test
    @DisplayName("Should present the access token as an RFC 6750 Bearer credential")
    void shouldPresentBearerToken(URIBuilder uriBuilder, MockWebServer server) throws Exception {
        String accessToken = Generators.nonBlankStrings().next();

        userInfoClient().fetchUserInfo(userInfoEndpoint(uriBuilder), accessToken);

        RecordedRequest request = server.takeRequest();
        assertEquals("Bearer " + accessToken, request.getHeaders().get("Authorization"),
                "the access token is presented as a Bearer credential");
    }

    @Test
    @DisplayName("Should surface a TransportException on an invalid_token (401) response")
    void shouldRejectUnauthorized(URIBuilder uriBuilder) {
        moduleDispatcher.returnError();
        var client = userInfoClient();
        var endpoint = userInfoEndpoint(uriBuilder);
        var token = Generators.nonBlankStrings().next();

        assertThrows(TransportException.class, () -> client.fetchUserInfo(endpoint, token));
    }

    @Test
    @DisplayName("Should reject an oversized response body (DoS guard)")
    void shouldRejectOversizedBody(URIBuilder uriBuilder) {
        moduleDispatcher.returnOversizedBody();
        var client = userInfoClient();
        var endpoint = userInfoEndpoint(uriBuilder);
        var token = Generators.nonBlankStrings().next();

        assertThrows(TransportException.class, () -> client.fetchUserInfo(endpoint, token));
    }

    @Test
    @DisplayName("Should return the (unbound) sub even when it does not match — binding is a separate step")
    void shouldReturnMismatchedSubForBindingStep(URIBuilder uriBuilder) {
        moduleDispatcher.returnSubMismatch();

        UserInfoResponse userInfo = userInfoClient().fetchUserInfo(
                userInfoEndpoint(uriBuilder), Generators.nonBlankStrings().next());

        assertEquals("other-subject", userInfo.getSub().orElseThrow(),
                "the transport returns the raw sub; SubBindingValidator is what rejects the mismatch");
    }
}
