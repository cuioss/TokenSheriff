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
package de.cuioss.sheriff.token.validation.test.dispatcher;

import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;

import java.util.Optional;
import java.util.Set;

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MockWebServer dispatcher for the OAuth 2.0 token endpoint (RFC 6749).
 * <p>
 * Serves grant responses in {@link ResponseStrategy#DEFAULT} mode and supports the
 * adversarial modes required by the commons threat catalog: RFC 6749 §5.2 error responses,
 * malformed bodies, and oversized bodies (DoS).
 * <p>
 * Follows the same configuration ({@code returnDefault()} / {@code returnError()} / …) and
 * verification ({@link #assertCallsAnswered(int)}) pattern as
 * {@link WellKnownDispatcher} and {@link JwksResolveDispatcher}.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749">RFC 6749 — OAuth 2.0</a>
 */
@SuppressWarnings("UnusedReturnValue")
public class TokenDispatcher implements ModuleDispatcherElement {

    /**
     * "/oidc/token"
     */
    public static final String LOCAL_PATH = "/oidc/token";

    @Getter
    @Setter
    private int callCounter = 0;
    private ResponseStrategy responseStrategy = ResponseStrategy.DEFAULT;

    public TokenDispatcher() {
        // No initialization needed
    }

    /**
     * Return a valid token grant response (default).
     *
     * @return this instance for method chaining
     */
    public TokenDispatcher returnDefault() {
        this.responseStrategy = ResponseStrategy.DEFAULT;
        return this;
    }

    /**
     * Return an RFC 6749 §5.2 OAuth error response (HTTP 400).
     *
     * @return this instance for method chaining
     */
    public TokenDispatcher returnOAuthError() {
        this.responseStrategy = ResponseStrategy.OAUTH_ERROR;
        return this;
    }

    /**
     * Return an HTTP 500 server error.
     *
     * @return this instance for method chaining
     */
    public TokenDispatcher returnError() {
        this.responseStrategy = ResponseStrategy.SERVER_ERROR;
        return this;
    }

    /**
     * Adversarial: return a malformed (non-JSON) body with a 200 status.
     *
     * @return this instance for method chaining
     */
    public TokenDispatcher returnMalformedBody() {
        this.responseStrategy = ResponseStrategy.MALFORMED_BODY;
        return this;
    }

    /**
     * Adversarial: return an oversized body to exercise response-size limits (DoS).
     *
     * @return this instance for method chaining
     */
    public TokenDispatcher returnOversizedBody() {
        this.responseStrategy = ResponseStrategy.OVERSIZED_BODY;
        return this;
    }

    @Override
    public Optional<MockResponse> handlePost(@NonNull RecordedRequest request) {
        callCounter++;
        Headers json = Headers.of("Content-Type", "application/json");
        return switch (responseStrategy) {
            case OAUTH_ERROR -> Optional.of(new MockResponse(SC_BAD_REQUEST, json,
                    "{\"error\":\"invalid_grant\",\"error_description\":\"The provided grant is invalid.\"}"));
            case SERVER_ERROR -> Optional.of(new MockResponse(SC_INTERNAL_SERVER_ERROR, Headers.of(), ""));
            case MALFORMED_BODY -> Optional.of(new MockResponse(SC_OK, json, "{ not json"));
            case OVERSIZED_BODY -> Optional.of(new MockResponse(SC_OK, json, AdversarialResponses.oversizedJson()));
            default -> Optional.of(new MockResponse(SC_OK, json, defaultTokenResponse()));
        };
    }

    private String defaultTokenResponse() {
        return "{\n" +
                "  \"access_token\": \"mock-access-token\",\n" +
                "  \"token_type\": \"Bearer\",\n" +
                "  \"expires_in\": 3600,\n" +
                "  \"refresh_token\": \"mock-refresh-token\",\n" +
                "  \"id_token\": \"mock-id-token\",\n" +
                "  \"scope\": \"openid profile email\"\n" +
                "}";
    }

    @Override
    public String getBaseUrl() {
        return LOCAL_PATH;
    }

    @Override
    public @NonNull Set<HttpMethodMapper> supportedMethods() {
        return Set.of(HttpMethodMapper.POST);
    }

    /**
     * Verifies whether this endpoint was called the given times.
     *
     * @param expected count of calls
     */
    public void assertCallsAnswered(int expected) {
        assertEquals(expected, callCounter);
    }

    /**
     * Response strategies for the token endpoint.
     */
    public enum ResponseStrategy {
        /** Valid token grant response. */
        DEFAULT,
        /** RFC 6749 §5.2 OAuth error response (HTTP 400). */
        OAUTH_ERROR,
        /** HTTP 500 server error. */
        SERVER_ERROR,
        /** Adversarial: malformed (non-JSON) body. */
        MALFORMED_BODY,
        /** Adversarial: oversized body (DoS). */
        OVERSIZED_BODY
    }
}
