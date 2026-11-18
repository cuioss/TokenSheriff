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
package de.cuioss.sheriff.token.client.token;

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.internal.BackChannelHttp;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import lombok.Getter;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the {@link UserInfoClient} call site sends through {@link BackChannelHttp#send}: a userinfo
 * response whose {@code Content-Length} is not a number reaches the caller only as the fixed
 * {@link BackChannelHttp#MALFORMED_RESPONSE} failure, never with the raw header value. The positive control
 * serves the same response with a numeric {@code Content-Length}.
 */
@EnableMockWebServer
@DisplayName("UserInfoClient malformed response framing")
class UserInfoClientMalformedResponseTest {

    private static final String MALFORMED_CONTENT_LENGTH = "12raw34remote56";
    private static final String SUBJECT = "user-subject";
    private static final String SUCCESS_BODY = "{\"sub\":\"" + SUBJECT + "\"}";
    private static final String ACCESS_TOKEN = "access-token-value";

    @Getter
    private final RawContentLengthDispatcher moduleDispatcher = new RawContentLengthDispatcher();

    private static ClientConfiguration config() {
        return ClientConfiguration.builder()
                .issuer("https://idp.example.com/realms/demo")
                .clientId("test-client")
                .clientSecret("test-secret")
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(true)
                .allowLoopbackEgress(true)
                .build();
    }

    private static String userInfoEndpoint(URIBuilder uriBuilder) {
        return uriBuilder.addPathSegments("oidc", "userinfo").buildAsString();
    }

    @Test
    @DisplayName("Should report a Content-Length that is not a number as the fixed malformed-response failure")
    void shouldReportMalformedContentLengthAsFixedFailure(URIBuilder uriBuilder) {
        moduleDispatcher.setContentLength(MALFORMED_CONTENT_LENGTH);
        var client = new UserInfoClient(config());
        var endpoint = userInfoEndpoint(uriBuilder);

        TransportException thrown = assertThrows(TransportException.class,
                () -> client.fetchUserInfo(endpoint, ACCESS_TOKEN));

        assertAll("fixed malformed-response failure",
                () -> assertEquals("userinfo request failed: " + BackChannelHttp.MALFORMED_RESPONSE,
                        thrown.getMessage(), "the userinfo client reports the fixed text"),
                () -> assertFalse(thrown.getMessage().contains(MALFORMED_CONTENT_LENGTH),
                        "the raw header value must not reach the caller"),
                () -> assertNull(thrown.getCause(), "the JDK exception carrying the raw value is not chained"));
    }

    @Test
    @DisplayName("positive control: the same response with a numeric Content-Length is accepted")
    void shouldAcceptNumericContentLength(URIBuilder uriBuilder) {
        moduleDispatcher.setContentLength(Integer.toString(SUCCESS_BODY.length()));

        UserInfoResponse userInfo = new UserInfoClient(config()).fetchUserInfo(userInfoEndpoint(uriBuilder),
                ACCESS_TOKEN);

        assertEquals(Optional.of(SUBJECT), userInfo.getSub(), "a numeric Content-Length yields the userinfo sub");
    }

    /**
     * Serves a userinfo success whose {@code Content-Length} value is written to the wire verbatim.
     */
    static final class RawContentLengthDispatcher implements ModuleDispatcherElement {

        private String contentLength = Integer.toString(SUCCESS_BODY.length());

        void setContentLength(String contentLength) {
            this.contentLength = contentLength;
        }

        @Override
        public String getBaseUrl() {
            return "/oidc/userinfo";
        }

        @Override
        public Set<HttpMethodMapper> supportedMethods() {
            return Set.of(HttpMethodMapper.GET);
        }

        @Override
        public Optional<MockResponse> handleGet(RecordedRequest request) {
            return Optional.of(new MockResponse.Builder().code(200)
                    .addHeader("Content-Type", "application/json")
                    .body(SUCCESS_BODY)
                    .removeHeader("Content-Length")
                    .addHeaderLenient("Content-Length", contentLength)
                    .build());
        }
    }
}
