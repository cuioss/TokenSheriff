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
package de.cuioss.sheriff.oauth.quarkus.servlet;

import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;

/**
 * Mock implementation of {@link HttpServletRequestResolver} for testing purposes.
 * 
 * <p>This mock uses {@link HttpServletRequestMock} internally and exposes header manipulation
 * methods for convenient test setup. It allows full control over the HTTP request context
 * during testing.</p>
 * 
 * <p>The mock can be configured to either return a mock HttpServletRequest or to simulate
 * the absence of a request context by throwing IllegalStateException.</p>
 * 
 * <p>Usage example:</p>
 * <pre>{@code
 * HttpServletRequestResolverMock mock = new HttpServletRequestResolverMock();
 * mock.setBearerToken("mytoken123")
 *     .setHeader("X-Custom-Header", "value1")
 *     .addHeader("X-Custom-Header", "value2")
 *     .setMethod("POST")
 *     .setRequestURI("/api/test");
 * 
 * // Use in tests
 * HttpServletRequest request = mock.resolveHttpServletRequest();
 * Map<String, List<String>> headers = mock.resolveHeaderMap();
 * 
 * // Simulate absence of request context
 * mock.setRequestContextAvailable(false);
 * assertThrows(IllegalStateException.class, () -> mock.resolveHttpServletRequest());
 * }</pre>
 *
 * @author Oliver Wolff
 */
public class HttpServletRequestResolverMock implements HttpServletRequestResolver {

    private final HttpServletRequestMock httpServletRequestMock;
    private boolean requestContextAvailable = true;

    /**
     * Creates a new HttpServletRequestResolverMock with default configuration.
     * The mock will return a valid HttpServletRequest unless explicitly configured otherwise.
     */
    public HttpServletRequestResolverMock() {
        this.httpServletRequestMock = new HttpServletRequestMock();
    }

    /**
     * Creates a new HttpServletRequestResolverMock with the specified HttpServletRequestMock.
     * 
     * @param httpServletRequestMock the HttpServletRequestMock to use
     */
    public HttpServletRequestResolverMock(HttpServletRequestMock httpServletRequestMock) {
        this.httpServletRequestMock = httpServletRequestMock;
    }

    @NonNull
    @Override
    public HttpServletRequest resolveHttpServletRequest() throws IllegalStateException {
        if (!requestContextAvailable) {
            throw new IllegalStateException("Request context not available - mock configured to simulate absent context");
        }
        return httpServletRequestMock;
    }

    // Configuration methods

    /**
     * Controls whether the mock should simulate an available request context.
     * 
     * @param available true to simulate available context, false to simulate absent context
     * @return this mock for method chaining
     */
    public HttpServletRequestResolverMock setRequestContextAvailable(boolean available) {
        this.requestContextAvailable = available;
        return this;
    }

    /**
     * Gets the current request context availability setting.
     * 
     * @return true if request context is simulated as available, false otherwise
     */
    public boolean isRequestContextAvailable() {
        return requestContextAvailable;
    }

    /**
     * Gets the underlying HttpServletRequestMock for direct manipulation.
     * 
     * @return the HttpServletRequestMock instance
     */
    public HttpServletRequestMock getHttpServletRequestMock() {
        return httpServletRequestMock;
    }

    // Header manipulation methods - delegates to HttpServletRequestMock

    /**
     * Sets a header value, replacing any existing values for the same header name.
     * 
     * @param name the header name
     * @param value the header value
     * @return this mock for method chaining
     */
    public HttpServletRequestResolverMock setHeader(String name, String value) {
        httpServletRequestMock.setHeader(name, value);
        return this;
    }

    /**
     * Adds a header value to the existing values for the given header name.
     * 
     * @param name the header name
     * @param value the header value to add
     * @return this mock for method chaining
     */
    public HttpServletRequestResolverMock addHeader(String name, String value) {
        httpServletRequestMock.addHeader(name, value);
        return this;
    }

    /**
     * Removes all values for the given header name.
     * 
     * @param name the header name to remove
     * @return this mock for method chaining
     */
    public HttpServletRequestResolverMock removeHeader(String name) {
        httpServletRequestMock.removeHeader(name);
        return this;
    }

    /**
     * Clears all headers.
     * 
     * @return this mock for method chaining
     */
    public HttpServletRequestResolverMock clearHeaders() {
        httpServletRequestMock.clearHeaders();
        return this;
    }

    /**
     * Sets the Authorization header with a Bearer token.
     * 
     * @param token the bearer token (without "Bearer " prefix)
     * @return this mock for method chaining
     */
    public HttpServletRequestResolverMock setBearerToken(String token) {
        httpServletRequestMock.setBearerToken(token);
        return this;
    }

    // Request property setters - delegates to HttpServletRequestMock

    /**
     * Sets the HTTP method.
     * 
     * @param method the HTTP method (GET, POST, etc.)
     * @return this mock for method chaining
     */
    public HttpServletRequestResolverMock setMethod(String method) {
        httpServletRequestMock.setMethod(method);
        return this;
    }

    /**
     * Sets the request URI.
     * 
     * @param requestURI the request URI
     * @return this mock for method chaining
     */
    public HttpServletRequestResolverMock setRequestURI(String requestURI) {
        httpServletRequestMock.setRequestURI(requestURI);
        return this;
    }

    /**
     * Sets the context path.
     * 
     * @param contextPath the context path
     * @return this mock for method chaining
     */
    public HttpServletRequestResolverMock setContextPath(String contextPath) {
        httpServletRequestMock.setContextPath(contextPath);
        return this;
    }

    // Convenience methods for testing

    /**
     * Creates a mock configured for a typical JWT bearer token scenario.
     * 
     * @param token the JWT token (without "Bearer " prefix)
     * @return a configured mock with Authorization header set
     */
    public static HttpServletRequestResolverMock withBearerToken(String token) {
        return new HttpServletRequestResolverMock()
                .setBearerToken(token)
                .setMethod("GET")
                .setRequestURI("/api/protected");
    }

    /**
     * Creates a mock configured for a scenario without request context.
     * 
     * @return a configured mock that throws IllegalStateException
     */
    public static HttpServletRequestResolverMock withoutRequestContext() {
        return new HttpServletRequestResolverMock()
                .setRequestContextAvailable(false);
    }

    /**
     * Creates a mock configured for a typical REST API request.
     * 
     * @param method the HTTP method
     * @param requestURI the request URI
     * @return a configured mock with basic request properties set
     */
    public static HttpServletRequestResolverMock withRequest(String method, String requestURI) {
        return new HttpServletRequestResolverMock()
                .setMethod(method)
                .setRequestURI(requestURI)
                .setHeader("Content-Type", "application/json")
                .setHeader("Accept", "application/json");
    }

    /**
     * Resets the mock to its initial state with default configuration.
     * 
     * @return this mock for method chaining
     */
    public HttpServletRequestResolverMock reset() {
        httpServletRequestMock.clearHeaders();
        httpServletRequestMock.setMethod("GET");
        httpServletRequestMock.setRequestURI("/");
        httpServletRequestMock.setContextPath("");
        this.requestContextAvailable = true;
        return this;
    }
}