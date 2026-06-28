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
package de.cuioss.sheriff.token.validation.domain.context;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Request object for access token validation, carrying the token string, HTTP headers,
 * and optionally the HTTP request URI and method for DPoP htu/htm validation (RFC 9449).
 * <p>
 * This record is immutable and thread-safe. The HTTP headers map is defensively copied
 * at construction time.
 * <p>
 * Usage:
 * <pre>
 * // Without HTTP headers (test/simple scenarios)
 * AccessTokenRequest request = AccessTokenRequest.of(tokenString);
 *
 * // With HTTP headers (production scenarios with DPoP support)
 * AccessTokenRequest request = AccessTokenRequest.of(tokenString, httpHeaders);
 *
 * // With full HTTP context (DPoP htu/htm validation)
 * AccessTokenRequest request = new AccessTokenRequest(tokenString, httpHeaders, requestUri, requestMethod);
 * </pre>
 *
 * @param tokenString the raw token string, must not be null
 * @param httpHeaders the HTTP headers from the original request, defensively copied
 * @param requestUri the full HTTP request URI (scheme + host + path), nullable
 * @param requestMethod the HTTP method (GET, POST, etc.), nullable
 * @since 1.0
 */
public record AccessTokenRequest(String tokenString, Map<String, List<String>> httpHeaders,
@Nullable
String requestUri, @Nullable
        String requestMethod)
        implements TokenValidationRequest {

    /**
     * Compact constructor that validates inputs and creates defensive copies.
     *
     * @param tokenString the raw token string, must not be null
     * @param httpHeaders the HTTP headers, must not be null (may be empty)
     * @param requestUri the full HTTP request URI, may be null
     * @param requestMethod the HTTP method, may be null
     */
    public AccessTokenRequest {
        Objects.requireNonNull(tokenString, "tokenString must not be null");
        Objects.requireNonNull(httpHeaders, "httpHeaders must not be null");
        httpHeaders = Map.copyOf(httpHeaders);
    }

    /**
     * Convenience factory for creating a request without HTTP headers.
     * <p>
     * Suitable for test scenarios or callers that do not have access to HTTP context.
     *
     * @param tokenString the raw token string, must not be null
     * @return a new AccessTokenRequest with an empty header map and no URI/method
     */
    public static AccessTokenRequest of(String tokenString) {
        return new AccessTokenRequest(tokenString, Map.of(), null, null);
    }

    /**
     * Convenience factory for creating a request with HTTP headers but no URI/method.
     * <p>
     * Suitable for callers that have HTTP headers but do not need DPoP htu/htm validation.
     *
     * @param tokenString the raw token string, must not be null
     * @param httpHeaders the HTTP headers, must not be null
     * @return a new AccessTokenRequest with the specified headers and no URI/method
     */
    public static AccessTokenRequest of(String tokenString, Map<String, List<String>> httpHeaders) {
        return new AccessTokenRequest(tokenString, httpHeaders, null, null);
    }
}
