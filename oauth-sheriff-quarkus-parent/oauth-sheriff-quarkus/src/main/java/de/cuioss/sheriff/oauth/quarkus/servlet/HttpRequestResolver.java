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

import java.util.*;

/**
 * Interface for resolving HTTP servlet objects within request contexts.
 *
 * <p>The primary method is {@link #resolveHttpServletRequest()} which returns the
 * current HttpServletRequest. If no request context is available, implementations
 * must throw {@link IllegalStateException}.</p>
 *
 * <p>The {@link #resolveHeaderMap()} method provides a default implementation that
 * extracts headers from the resolved HttpServletRequest.</p>
 *
 * <p><strong>Context Dependency:</strong> When not in an appropriate request context (e.g., outside REST requests),
 * the CDI system will throw {@link jakarta.enterprise.inject.IllegalProductException} because the underlying
 * {@code @RequestScoped} HttpServerRequest producer cannot provide a valid instance. This is wrapped behavior -
 * implementations may throw {@link IllegalStateException} which gets wrapped by CDI.</p>
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public interface HttpServletRequestResolver {

    /**
     * Resolves the HttpServletRequest from the current request context.
     *
     * <p>This is the primary method that implementations must provide.</p>
     *
     * @return HttpServletRequest from the current context
     * @throws jakarta.enterprise.inject.IllegalProductException if not in an active request context 
     *                               (CDI wraps underlying exceptions when @RequestScoped producer fails)
     * @throws IllegalStateException if the infrastructure is not available to resolve the request
     */
   
    HttpServletRequest resolveHttpServletRequest() throws IllegalStateException;

    /**
     * Resolves HTTP headers from the current request context as a Map with normalized header names.
     *
     * <p>This default implementation extracts headers from the HttpServletRequest
     * resolved by {@link #resolveHttpServletRequest()}. Header field names are normalized
     * to lowercase for RFC compliance and consistent processing.</p>
     *
     * <p><strong>Header Name Normalization:</strong> According to RFC 7230 Section 3.2 (HTTP/1.1),
     * header field names are case-insensitive. RFC 9113 Section 8.1.2 (HTTP/2) requires header
     * field names to be converted to lowercase prior to encoding. This implementation normalizes
     * all header names to lowercase using {@link java.util.Locale#ROOT} to ensure consistent,
     * locale-independent processing regardless of the underlying HTTP protocol version.</p>
     *
     * <p><strong>Protocol Compatibility:</strong> This normalization approach supports both
     * HTTP/1.1 and HTTP/2 protocols, eliminating the need for case-insensitive header lookups
     * in consuming code while maintaining full RFC compliance.</p>
     *
     * @return Map of HTTP headers with lowercase header names from the current context
     * @throws jakarta.enterprise.inject.IllegalProductException if not in an active request context 
     *                               (CDI wraps underlying exceptions when @RequestScoped producer fails)
     * @throws IllegalStateException if the infrastructure is not available to resolve headers
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7230#section-3.2">RFC 7230 Section 3.2: Header Fields</a>
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc9113#section-8.1.2">RFC 9113 Section 8.1.2: HTTP Header Fields</a>
     */
   
    default Map<String, List<String>> resolveHeaderMap() throws IllegalStateException {
        return createHeaderMapFromRequest(resolveHttpServletRequest());
    }

    /**
     * Creates a header map from the given HttpServletRequest with normalized header names.
     *
     * <p>This helper method is used by the default implementation of {@link #resolveHeaderMap()}.
     * Header field names are normalized to lowercase for RFC compliance and consistent processing.</p>
     *
     * <p><strong>Implementation Notes:</strong> Header names are converted to lowercase using
     * {@link java.util.Locale#ROOT} to ensure locale-independent normalization. This approach
     * follows RFC 9113 requirements for HTTP/2 while maintaining compatibility with HTTP/1.1.</p>
     *
     * @param request the HttpServletRequest to extract headers from (must not be null)
     * @return Map of HTTP headers with normalized lowercase header names
     * @throws IllegalStateException if headers cannot be extracted from the request
     */
   
    private Map<String, List<String>> createHeaderMapFromRequest(HttpServletRequest request)
            throws IllegalStateException {

        Map<String, List<String>> headerMap = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                List<String> headerValues = new ArrayList<>();
                Enumeration<String> values = request.getHeaders(headerName);
                while (values.hasMoreElements()) {
                    headerValues.add(values.nextElement());
                }
                // Normalize header name to lowercase per RFC 9113 (HTTP/2) and RFC 7230 (HTTP/1.1)
                // Use ROOT locale to ensure consistent, locale-independent conversion
                headerMap.put(headerName.toLowerCase(Locale.ROOT), headerValues);
            }
        }

        return headerMap;
    }

    /**
     * Resolves the full HTTP request URI (scheme + host + path) from the current context.
     * Used for DPoP htu claim validation per RFC 9449.
     *
     * @return the request URI as a string, or null if not available
     */
    default String resolveRequestUri() {
        HttpServletRequest request = resolveHttpServletRequest();
        return request.getRequestURL().toString();
    }

    /**
     * Resolves the HTTP method from the current context.
     * Used for DPoP htm claim validation per RFC 9449.
     *
     * @return the HTTP method (GET, POST, etc.), or null if not available
     */
    default String resolveRequestMethod() {
        HttpServletRequest request = resolveHttpServletRequest();
        return request.getMethod();
    }
}