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
package de.cuioss.sheriff.token.client.flow;

import de.cuioss.sheriff.token.client.auth.ClientSecretBasicAuth;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.validation.test.dispatcher.ParDispatcher;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import lombok.Getter;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("ParClient pushed-authorization-request transport (RFC 9126)")
class ParClientTest {

    @Getter
    private final ParTestDispatcher moduleDispatcher = new ParTestDispatcher();

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
                .allowLoopbackEgress(true)
                .build();
    }

    private static Map<String, String> authorizationParameters() {
        return new HashMap<>(Map.of(
                "response_type", "code",
                "redirect_uri", "https://rp.example.com/callback",
                "scope", "openid",
                "state", opaqueParameterValue(),
                "nonce", opaqueParameterValue(),
                "code_challenge", opaqueParameterValue(),
                "code_challenge_method", "S256"));
    }

    /**
     * Generates a distinctive opaque value for the security-sensitive authorization parameters
     * (state, nonce, PKCE challenge) that is <em>provably</em> never a substring of the fixed mock
     * AS-issued opaque {@code request_uri} ({@code urn:ietf:params:oauth:request_uri:mock-par-reference}).
     * <p>
     * Root cause of the historical JDK 25 flake: a letters-only generated value shares the
     * request_uri's alphabet, so the no-leak substring assertions in {@link #shouldReturnRequestUri}
     * relied on the random value being improbably — but not provably — distinct, which a
     * cui-test-generator value collision under a given seed could defeat. The fixed request_uri
     * contains no digits, so appending a generated digit run puts every pushed value in an alphabet
     * disjoint from the request_uri: the value can never occur as a substring of it, making the
     * no-leak assertions deterministic under any generator seed on JDK 25.
     */
    private static String opaqueParameterValue() {
        return Generators.letterStrings(24, 40).next() + Generators.integers(100000, 999999).next();
    }

    private static String parEndpoint(URIBuilder uriBuilder) {
        return uriBuilder.addPathSegments("oidc", "par").buildAsString();
    }

    private ParClient parClient() {
        return new ParClient(config());
    }

    @Test
    @DisplayName("Should push the request and return an opaque request_uri that leaks none of the pushed parameters (TEST-9)")
    void shouldReturnRequestUri(URIBuilder uriBuilder) {
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());
        var params = authorizationParameters();

        ParResponse response = parClient().pushAuthorizationRequest(
                parEndpoint(uriBuilder), params, auth);

        String requestUri = response.getRequestUri().orElseThrow();
        assertAll("PAR response is an opaque reference",
                () -> assertFalse(requestUri.isBlank(),
                        "the AS-issued request_uri must be present and non-blank"),
                () -> assertFalse(requestUri.contains(params.get("state")),
                        "the opaque request_uri must not leak the pushed state"),
                () -> assertFalse(requestUri.contains(params.get("nonce")),
                        "the opaque request_uri must not leak the pushed nonce"),
                () -> assertFalse(requestUri.contains(params.get("code_challenge")),
                        "the opaque request_uri must not leak the pushed PKCE challenge"),
                () -> assertEquals(90, response.getExpiresIn(), "the request_uri lifetime is captured"));
    }

    @Test
    @DisplayName("Should push the raw authorization parameters over the back channel, never the front channel (T-PARAM-INTEGRITY)")
    void shouldPushParametersOverBackChannel(URIBuilder uriBuilder, MockWebServer server) throws Exception {
        var params = authorizationParameters();
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());

        ParResponse response = parClient().pushAuthorizationRequest(parEndpoint(uriBuilder), params, auth);

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().utf8();
        assertAll("back-channel push integrity",
                () -> assertTrue(body.contains("response_type=code"), "the response_type is pushed on the back channel"),
                () -> assertTrue(body.contains("state="), "the state is pushed on the back channel"),
                () -> assertTrue(body.contains("code_challenge="), "the PKCE challenge is pushed on the back channel"),
                () -> assertTrue(body.contains("code_challenge_method=S256"), "the PKCE method is pushed on the back channel"),
                () -> assertFalse(response.getRequestUri().orElseThrow().contains("state="),
                        "the returned request_uri never carries the raw authorization parameters"));
    }

    @Test
    @DisplayName("Should authenticate the back-channel push with the client authentication strategy")
    void shouldDecorateWithClientAuth(URIBuilder uriBuilder, MockWebServer server) throws Exception {
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());

        parClient().pushAuthorizationRequest(parEndpoint(uriBuilder), authorizationParameters(), auth);

        RecordedRequest request = server.takeRequest();
        String authorization = request.getHeaders().get("Authorization");
        assertTrue(authorization != null && authorization.startsWith("Basic "),
                "client_secret_basic must send an HTTP Basic Authorization header on the PAR request");
    }

    @Test
    @DisplayName("Should surface a TransportException on an RFC 9126 error response")
    void shouldRejectOAuthError(URIBuilder uriBuilder) {
        moduleDispatcher.returnOAuthError();
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());
        var endpoint = parEndpoint(uriBuilder);
        var client = parClient();
        var params = authorizationParameters();

        assertThrows(TransportException.class,
                () -> client.pushAuthorizationRequest(endpoint, params, auth));
    }

    @Test
    @DisplayName("Should surface a TransportException on a server error")
    void shouldRejectServerError(URIBuilder uriBuilder) {
        moduleDispatcher.returnError();
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());
        var endpoint = parEndpoint(uriBuilder);
        var client = parClient();
        var params = authorizationParameters();

        assertThrows(TransportException.class,
                () -> client.pushAuthorizationRequest(endpoint, params, auth));
    }

    @Test
    @DisplayName("Should reject an oversized response body (DoS guard)")
    void shouldRejectOversizedBody(URIBuilder uriBuilder) {
        moduleDispatcher.returnOversizedBody();
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());
        var endpoint = parEndpoint(uriBuilder);
        var client = parClient();
        var params = authorizationParameters();

        assertThrows(TransportException.class,
                () -> client.pushAuthorizationRequest(endpoint, params, auth));
    }

    @Test
    @DisplayName("Should sanitize an AS-controlled parse-error fragment carrying CR/LF (M8)")
    void shouldSanitizeParseErrorFragment(URIBuilder uriBuilder) {
        // The PAR response body is AS-controlled; a malformed value with an embedded CR/LF must not
        // forge a log line — the parse-error fragment must be routed through LogSanitizer before it is
        // exposed on the exception message or the log appender.
        moduleDispatcher.returnMalformedJson("{\"request_uri\":\"urn:x\",\"expires_in\":9\r\nINJECTED9}");
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());
        var endpoint = parEndpoint(uriBuilder);
        var client = parClient();
        var params = authorizationParameters();

        TransportException thrown = assertThrows(TransportException.class,
                () -> client.pushAuthorizationRequest(endpoint, params, auth),
                "a malformed PAR response must surface as a TransportException");

        String message = thrown.getMessage();
        assertAll("sanitized parse-error fragment",
                () -> assertFalse(message.indexOf('\r') >= 0,
                        "a raw CR from the AS-controlled body must not reach the exception message"),
                () -> assertFalse(message.indexOf('\n') >= 0,
                        "a raw LF from the AS-controlled body must not reach the exception message"),
                () -> assertTrue(message.contains("\\r") && message.contains("\\n"),
                        "the injected CR/LF must survive in escaped form, proving the fragment was carried and sanitized"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"request_uri\":\"urn:x\\",
            "{\"request_uri\":\"\\u12"
    })
    @DisplayName("Should reject a response body that ends inside a string escape as a TransportException")
    void shouldRejectTruncatedEscape(String truncatedBody, URIBuilder uriBuilder) {
        moduleDispatcher.returnMalformedJson(truncatedBody);
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());
        var endpoint = parEndpoint(uriBuilder);
        var client = parClient();
        var params = authorizationParameters();

        TransportException thrown = assertThrows(TransportException.class,
                () -> client.pushAuthorizationRequest(endpoint, params, auth),
                "a truncated escape must be the declared TransportException, not an unchecked exception");

        assertAll("declared parse failure",
                () -> assertTrue(thrown.getMessage().startsWith("Failed to parse PAR endpoint response"),
                        thrown.getMessage()),
                () -> assertInstanceOf(ArrayIndexOutOfBoundsException.class, thrown.getCause(),
                        "the truncated-escape parse failure must be carried as the cause"));
    }

    @ParameterizedTest(name = "expires_in = {0}")
    @ValueSource(strings = {"\"90\"", "\"0\""})
    @DisplayName("Should refuse a quoted numeric expires_in as a malformed PAR response (RFC 9126 §2.2)")
    void shouldRefuseQuotedNumericExpiresIn(String expiresIn, URIBuilder uriBuilder) {
        moduleDispatcher.returnMalformedJson(
                "{\"request_uri\":\"urn:ietf:params:oauth:request_uri:x\",\"expires_in\":" + expiresIn + "}");
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());
        var endpoint = parEndpoint(uriBuilder);
        var client = parClient();
        var params = authorizationParameters();

        TransportException thrown = assertThrows(TransportException.class,
                () -> client.pushAuthorizationRequest(endpoint, params, auth),
                "a quoted expires_in must not be read as a number");

        assertTrue(thrown.getMessage().startsWith("Failed to parse PAR endpoint response"),
                "the refusal must take the existing parse-failure path, but was: " + thrown.getMessage());
    }

    @ParameterizedTest(name = "HTTP 201 Content-Type [{0}] -> TransportException")
    @CsvSource(delimiter = '|', value = {
            "''",
            "text/plain",
            "application/jrd+json",
            "application/json,text/html"})
    @DisplayName("Should refuse a 201 whose Content-Type is not application/json before parsing its body")
    void shouldRefuseResponseWithoutJsonContentType(String contentTypes, URIBuilder uriBuilder) {
        moduleDispatcher.returnSuccessWithContentTypes(
                contentTypes.isEmpty() ? new String[0] : contentTypes.split(","));
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());
        var endpoint = parEndpoint(uriBuilder);
        var client = parClient();
        var params = authorizationParameters();

        TransportException thrown = assertThrows(TransportException.class,
                () -> client.pushAuthorizationRequest(endpoint, params, auth),
                "a PAR response not declared as JSON must not be parsed");

        assertTrue(thrown.getMessage().contains("Content-Type is not application/json"),
                "the refusal must name the media type, but was: " + thrown.getMessage());
    }

    @Test
    @DisplayName("Should accept a 201 whose application/json Content-Type carries a charset")
    void shouldAcceptJsonContentTypeWithCharset(URIBuilder uriBuilder) {
        moduleDispatcher.returnSuccessWithContentTypes("Application/JSON; charset=UTF-8");
        var auth = new ClientSecretBasicAuth(Generators.nonBlankStrings().next(), Generators.nonBlankStrings().next());

        ParResponse response = parClient().pushAuthorizationRequest(parEndpoint(uriBuilder),
                authorizationParameters(), auth);

        assertTrue(response.getRequestUri().isPresent(), "parameters on application/json are allowed");
    }

    /**
     * Wraps the shared {@link ParDispatcher} strategies and adds a malformed-JSON strategy so the M8
     * parse-error sanitization path can be exercised with an AS-controlled CR/LF payload.
     */
    static final class ParTestDispatcher implements ModuleDispatcherElement {

        private static final String APPLICATION_JSON = "application/json";

        private static final String SUCCESS_BODY =
                "{\"request_uri\":\"urn:ietf:params:oauth:request_uri:mock-par-reference\",\"expires_in\":90}";

        private final ParDispatcher delegate = new ParDispatcher();
        private boolean malformed;
        private String malformedBody = "";
        private List<String> successContentTypes;

        ParTestDispatcher returnDefault() {
            malformed = false;
            successContentTypes = null;
            delegate.returnDefault();
            return this;
        }

        /**
         * Serve a 201 success body with exactly these {@code Content-Type} lines; no value serves no
         * {@code Content-Type} at all.
         */
        ParTestDispatcher returnSuccessWithContentTypes(String... values) {
            malformed = false;
            successContentTypes = List.of(values);
            return this;
        }

        ParTestDispatcher returnOAuthError() {
            malformed = false;
            delegate.returnOAuthError();
            return this;
        }

        ParTestDispatcher returnError() {
            malformed = false;
            delegate.returnError();
            return this;
        }

        ParTestDispatcher returnOversizedBody() {
            malformed = false;
            delegate.returnOversizedBody();
            return this;
        }

        ParTestDispatcher returnMalformedJson(String body) {
            malformed = true;
            malformedBody = body;
            return this;
        }

        void setCallCounter(int callCounter) {
            delegate.setCallCounter(callCounter);
        }

        @Override
        public String getBaseUrl() {
            return delegate.getBaseUrl();
        }

        @Override
        public Set<HttpMethodMapper> supportedMethods() {
            return delegate.supportedMethods();
        }

        @Override
        public Optional<MockResponse> handlePost(RecordedRequest request) {
            if (malformed) {
                return Optional.of(new MockResponse(200, Headers.of("Content-Type", APPLICATION_JSON), malformedBody));
            }
            List<String> contentTypes = successContentTypes;
            if (contentTypes != null) {
                Headers.Builder headers = new Headers.Builder();
                contentTypes.forEach(value -> headers.add("Content-Type", value));
                return Optional.of(new MockResponse(201, headers.build(), SUCCESS_BODY));
            }
            return delegate.handlePost(request);
        }
    }
}
