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
package de.cuioss.sheriff.oauth.quarkus.logging;

import jakarta.ws.rs.core.MultivaluedMap;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Utility class for extracting client IP addresses from HTTP headers.
 * Handles the complex logic of determining the original client IP address
 * through various proxy chains, CDN configurations, and load balancers.
 *
 * <p>This class follows 2024 industry standards for IP extraction, supporting
 * headers from major CDN providers (Cloudflare, Akamai, Fastly), load balancers,
 * and proxy configurations.</p>
 *
 * <p>The extraction follows a priority order from most trusted to least trusted
 * headers, with special handling for comma-separated values and RFC 7239 format.</p>
 *
 * <p><strong>Security Warning — Proxy Trust Model:</strong></p>
 * <ul>
 *   <li>The proxy headers inspected by this class (e.g. {@code X-Forwarded-For},
 *       {@code CF-Connecting-IP}) are <strong>only trustworthy when the application
 *       is deployed behind a trusted reverse proxy</strong> that overwrites or
 *       validates these headers before forwarding the request.</li>
 *   <li>In direct-client deployments (no reverse proxy), <strong>all proxy headers
 *       can be spoofed</strong> by the client, making the extracted IP unreliable
 *       for security decisions such as rate limiting or audit logging.</li>
 *   <li>The application should use Quarkus's proxy trust configuration
 *       ({@code quarkus.http.proxy.proxy-address-forwarding},
 *       {@code quarkus.http.proxy.trusted-proxies}) to limit which upstream
 *       addresses are allowed to set forwarding headers.</li>
 * </ul>
 *
 * @since 1.0
 */
@UtilityClass
public class ClientIpExtractor {

    /**
     * Standard proxy headers checked for client IP address extraction, in priority order.
     * Based on 2024 industry standards covering major CDNs, load balancers, and proxy configurations.
     */
    private static final String[] PROXY_HEADERS = {
            "X-Forwarded-For",       // Most common, de-facto standard
            "CF-Connecting-IP",      // Cloudflare (very common CDN)
            "True-Client-IP",        // Cloudflare Enterprise, Akamai CDN
            "X-Real-IP",             // Nginx style, widely used
            "Fastly-Client-IP",      // Fastly CDN
            "X-Client-IP",           // Microsoft style, various proxies
            "X-Originating-IP",      // Some proxy configurations
            "X-Original-Forwarded-For", // Nested proxy scenarios
            "X-Forwarded",           // Less common variant
            "X-Remote-IP",           // Alternative client IP header
            "X-Remote-Addr",         // Remote address variant
            "X-Cluster-Client-IP",   // Cluster environments
            "Remote-Addr"            // Basic proxy header
    };

    /**
     * Extracts the client IP address from HTTP headers.
     * Follows standard proxy header priority and fallback chain.
     *
     * @param headers JAX-RS MultivaluedMap of HTTP headers
     * @return The extracted client IP address, or "unknown" if none found
     */
    public static String extractClientIp(MultivaluedMap<String, String> headers) {
        // Check standard headers first
        for (String headerName : PROXY_HEADERS) {
            String result = extractFromHeader(headers, headerName);
            if (result != null) {
                return result;
            }
        }

        // Special case: RFC 7239 Forwarded header (more complex parsing)
        String result = extractFromForwardedHeader(headers);
        if (result != null) {
            return result;
        }

        return "unknown";
    }

    /**
     * Extracts IP address from a standard proxy header (MultivaluedMap version).
     * Handles comma-separated values by taking the first (original client) IP.
     *
     * @param headers MultivaluedMap of HTTP headers
     * @param headerName Name of the header to check
     * @return The extracted IP address, or null if not found/empty
     */
    private static String extractFromHeader(MultivaluedMap<String, String> headers, String headerName) {
        List<String> headerValues = headers.get(headerName);
        if (headerValues != null && !headerValues.isEmpty()) {
            String headerValue = headerValues.getFirst();
            if (headerValue != null && !headerValue.trim().isEmpty()) {
                // For comma-separated values, take the first (original client)
                return headerValue.split(",")[0].trim();
            }
        }
        return null;
    }

    /**
     * Extracts IP address from RFC 7239 Forwarded header (MultivaluedMap version).
     * Parses the complex "for=IP; proto=http" format.
     *
     * @param headers MultivaluedMap of HTTP headers
     * @return The extracted IP address, or null if not found/parseable
     */
    private static String extractFromForwardedHeader(MultivaluedMap<String, String> headers) {
        List<String> forwardedValues = headers.get("Forwarded");
        if (forwardedValues != null && !forwardedValues.isEmpty()) {
            String standardForwarded = forwardedValues.getFirst();
            if (standardForwarded != null && !standardForwarded.trim().isEmpty()) {
                // Parse "for=IP" from Forwarded header
                String[] parts = standardForwarded.split(";");
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (trimmed.startsWith("for=")) {
                        String forValue = trimmed.substring(4);
                        // Remove quotes and brackets if present, take IP before port
                        return forValue.replaceAll("[\"\\[\\]]", "").split(":")[0];
                    }
                }
            }
        }
        return null;
    }

}