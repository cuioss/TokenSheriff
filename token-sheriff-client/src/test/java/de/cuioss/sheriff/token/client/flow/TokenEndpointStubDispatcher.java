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

import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serves the token endpoint in one of three shapes: a syntactically malformed 200 body whose parse
 * error echoes an embedded {@code CR}/{@code LF} (the default, driving the M8 case); an RFC 9449
 * §8 {@code use_dpop_nonce} challenge — a 400 carrying a {@code DPoP-Nonce} header — served on
 * <em>every</em> call, so a client that retried more than once is caught by the call counter; or a
 * configured 200 success body, driving the issuance-time sender-constraint binding cases.
 */
final class TokenEndpointStubDispatcher implements ModuleDispatcherElement {

    // The unparseable numeric expires_in embeds a raw CR/LF the AS controls; DSL-JSON's parse error
    // echoes that fragment verbatim, so it exercises the sanitizer on the parse-error path.
    private static final String MALFORMED_BODY =
            "{\"access_token\":\"a\",\"token_type\":\"Bearer\",\"expires_in\":9\r\nINJECTED9}";

    private static final String NONCE_CHALLENGE_BODY = "{\"error\":\"use_dpop_nonce\"}";

    private static final int HTTP_BAD_REQUEST = 400;

    private final AtomicInteger callCounter = new AtomicInteger();

    /** The {@code DPoP-Nonce} value to challenge with, or {@code null} to serve the malformed body. */
    private volatile String challengeNonce;

    /** The 200 success body to serve, or {@code null} to serve the malformed body. */
    private volatile String successBody;

    /** The error body to serve (without a nonce), or {@code null} for the other shapes. */
    private volatile String errorBody;

    /** The status the {@link #errorBody} is served with. */
    private volatile int errorStatus = HTTP_BAD_REQUEST;

    /**
     * The {@code Content-Length} value written verbatim on a {@code {}} body, or {@code null} for the other
     * shapes.
     */
    private volatile String rawContentLength;

    /** The status the {@link #rawContentLength} response is served with. */
    private volatile int rawContentLengthStatus;

    private static final List<String> DEFAULT_CONTENT_TYPES = List.of("application/json");

    /** The {@code Content-Type} lines the success and error bodies are served with. */
    private volatile List<String> contentTypes = DEFAULT_CONTENT_TYPES;

    /** Serve {@code body} as a 400 error response without a {@code DPoP-Nonce} on every call. */
    void failWith(String body) {
        failWith(HTTP_BAD_REQUEST, body);
    }

    /** Serve {@code body} with the non-success {@code status} and no {@code DPoP-Nonce} on every call. */
    void failWith(int status, String body) {
        this.errorStatus = status;
        this.errorBody = body;
    }

    /** Serve {@code status} with a {@code Content-Length} of {@code value}, written verbatim, on every call. */
    void respondWithRawContentLength(int status, String value) {
        this.rawContentLengthStatus = status;
        this.rawContentLength = value;
    }

    /** Serve a {@code use_dpop_nonce} challenge carrying {@code nonce} on every call. */
    void challengeWithNonce(String nonce) {
        this.challengeNonce = nonce;
    }

    /** Serve {@code body} as a 200 success response on every call. */
    void respondWith(String body) {
        this.successBody = body;
    }

    /** @return how many times the endpoint was called, i.e. the attempt count including retries */
    int getCallCounter() {
        return callCounter.get();
    }

    /**
     * Serve the success and error bodies with exactly these {@code Content-Type} lines; no value
     * serves no {@code Content-Type} at all.
     */
    void withContentTypes(String... values) {
        this.contentTypes = List.of(values);
    }

    private Headers contentTypeHeaders() {
        Headers.Builder builder = new Headers.Builder();
        contentTypes.forEach(value -> builder.add("Content-Type", value));
        return builder.build();
    }

    void reset() {
        this.challengeNonce = null;
        this.successBody = null;
        this.errorBody = null;
        this.errorStatus = HTTP_BAD_REQUEST;
        this.rawContentLength = null;
        this.contentTypes = DEFAULT_CONTENT_TYPES;
        this.callCounter.set(0);
    }

    @Override
    public String getBaseUrl() {
        return "/token";
    }

    @Override
    public Set<HttpMethodMapper> supportedMethods() {
        return Set.of(HttpMethodMapper.POST);
    }

    @Override
    public Optional<MockResponse> handlePost(RecordedRequest request) {
        callCounter.incrementAndGet();
        String contentLength = this.rawContentLength;
        if (contentLength != null) {
            return Optional.of(new MockResponse.Builder().code(rawContentLengthStatus)
                    .headers(contentTypeHeaders())
                    .body("{}")
                    .removeHeader("Content-Length")
                    .addHeaderLenient("Content-Length", contentLength)
                    .build());
        }
        String error = this.errorBody;
        if (error != null) {
            return Optional.of(new MockResponse(errorStatus, contentTypeHeaders(), error));
        }
        String nonce = this.challengeNonce;
        if (nonce == null) {
            String body = this.successBody;
            return Optional.of(new MockResponse(200, contentTypeHeaders(),
                    body == null ? MALFORMED_BODY : body));
        }
        return Optional.of(new MockResponse(400,
                Headers.of("Content-Type", "application/json", "DPoP-Nonce", nonce),
                NONCE_CHALLENGE_BODY));
    }
}
