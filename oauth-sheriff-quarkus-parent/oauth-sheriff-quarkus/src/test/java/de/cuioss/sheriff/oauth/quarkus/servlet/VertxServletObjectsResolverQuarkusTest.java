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

import de.cuioss.sheriff.oauth.quarkus.annotation.ServletObjectsResolver;
import de.cuioss.sheriff.oauth.quarkus.config.JwtTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.util.*;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quarkus integration tests for {@link VertxServletObjectsResolver} and {@link VertxHttpServletRequestAdapter}.
 *
 * <p>These tests verify the functionality of the VertxHttpServletRequestAdapter in a real Quarkus environment
 * with actual HTTP requests. This complements the unit tests in VertxHttpServletRequestAdapterUnsupportedOperationsTest
 * which focus on the UnsupportedOperationException cases.</p>
 *
 * @author Oliver Wolff
 */
@QuarkusTest
@TestProfile(JwtTestProfile.class)
@DisplayName("VertxServletObjectsResolver and VertxHttpServletRequestAdapter Integration Tests")
class VertxServletObjectsResolverQuarkusTest {

    // Note: Tests for accessing @RequestScoped HttpServerRequest outside of request context
    // are not reliable because Quarkus test context behavior varies. The important test
    // is the REST endpoint test below which verifies the resolver works during actual HTTP requests.

    @Test
    @DisplayName("Should resolve HttpServletRequest during active REST request")
    void shouldResolveHttpServletRequestDuringActiveRestRequest() {
        // Make HTTP request to test endpoint
        String response = given()
                .header("Authorization", "Bearer vertx-test-token")
                .header("X-Test-Header", "test-value")
                .when()
                .get("/test/vertx-resolver")
                .then()
                .statusCode(200)
                .contentType("text/plain")
                .extract()
                .asString();

        // Verify that the resolver worked during the request
        assertTrue(response.contains("HttpServletRequest available: true"),
                "HttpServletRequest should be available during active REST request");
        assertTrue(response.contains("HeaderMap available: true"),
                "HeaderMap should be available during active REST request");
        assertTrue(response.contains("HeaderCount: "),
                "Header count should be reported");
        assertTrue(response.contains("AuthHeader: Bearer vertx-test-token"),
                "Authorization header should be correctly retrieved");
    }

    @Test
    @DisplayName("Should correctly handle request parameters")
    void shouldHandleRequestParameters() {
        // Make HTTP request with query parameters
        String response = given()
                .queryParam("param1", "value1")
                .queryParam("param2", "value2")
                .queryParam("param2", "value3") // Multiple values for same parameter
                .when()
                .get("/test/parameters")
                .then()
                .statusCode(200)
                .contentType("text/plain")
                .extract()
                .asString();

        // Verify parameter handling
        assertTrue(response.contains("param1=value1"),
                "Single parameter should be correctly retrieved");
        assertTrue(response.contains("param2=[value2, value3]"),
                "Multiple values for same parameter should be correctly retrieved");
        assertTrue(response.contains("parameterNames: param1,param2"),
                "Parameter names should be correctly enumerated");
    }

    @Test
    @DisplayName("Should correctly handle request URI and URL")
    void shouldHandleRequestUriAndUrl() {
        // Make HTTP request to test URI/URL handling
        String response = given()
                .when()
                .get("/test/uri-url/segment1/segment2")
                .then()
                .statusCode(200)
                .contentType("text/plain")
                .extract()
                .asString();

        // Verify URI/URL handling
        assertTrue(response.contains("URI: /test/uri-url/segment1/segment2"),
                "Request URI should be correctly retrieved");
        assertTrue(response.contains("URL: http"),
                "Request URL should be correctly retrieved and contain scheme");
        assertTrue(response.contains("ServletPath: /test/uri-url/segment1/segment2"),
                "Servlet path should be correctly retrieved");
        assertTrue(response.contains("Method: GET"),
                "Request method should be correctly retrieved");
        assertTrue(response.contains("Protocol: HTTP"),
                "Protocol should be correctly retrieved");
    }

    @Test
    @DisplayName("Should correctly handle cookies")
    void shouldHandleCookies() {
        // Make HTTP request with cookies
        String response = given()
                .cookie("testCookie1", "cookieValue1")
                .cookie("testCookie2", "cookieValue2")
                .when()
                .get("/test/cookies")
                .then()
                .statusCode(200)
                .contentType("text/plain")
                .extract()
                .asString();

        // Verify cookie handling
        assertTrue(response.contains("Cookie count: 2"),
                "Cookie count should be correctly reported");
        assertTrue(response.contains("testCookie1=cookieValue1"),
                "First cookie should be correctly retrieved");
        assertTrue(response.contains("testCookie2=cookieValue2"),
                "Second cookie should be correctly retrieved");
    }

    @Test
    @DisplayName("Should correctly handle request attributes")
    void shouldHandleRequestAttributes() {
        // Make HTTP request to test attributes
        String response = given()
                .when()
                .get("/test/attributes")
                .then()
                .statusCode(200)
                .contentType("text/plain")
                .extract()
                .asString();

        // Verify attribute handling
        assertTrue(response.contains("Attribute1: value1"),
                "First attribute should be correctly set and retrieved");
        assertTrue(response.contains("Attribute2: value2"),
                "Second attribute should be correctly set and retrieved");
        assertTrue(response.contains("Attribute names: attr1,attr2"),
                "Attribute names should be correctly enumerated");
    }

    @Test
    @DisplayName("Should correctly handle content-related methods")
    void shouldHandleContentRelatedMethods() {
        // Make HTTP request with content-related headers
        String response = given()
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Content-Length", "256")
                .when()
                .get("/test/content-info")
                .then()
                .statusCode(200)
                .contentType("text/plain")
                .extract()
                .asString();

        // Verify content-related methods
        assertTrue(response.contains("CharacterEncoding: UTF-8"),
                "Character encoding should be correctly extracted from Content-Type header");
        assertTrue(response.contains("ContentType: application/json; charset=UTF-8"),
                "Content type should be correctly retrieved");
        assertTrue(response.contains("ContentLength: 256"),
                "Content length should be correctly retrieved as int");
        assertTrue(response.contains("ContentLengthLong: 256"),
                "Content length should be correctly retrieved as long");
        assertTrue(response.contains("SetCharacterEncoding: ISO-8859-1"),
                "Setting character encoding should be validated");
    }

    /**
     * Test endpoint that uses the Vertx servlet resolver during HTTP request.
     * This is a proper JAX-RS resource with @RequestScoped to ensure it's in the right context.
     */
    @Path("/test")
    @RequestScoped
    public static class TestEndpoint {

        @Inject
        @ServletObjectsResolver
        HttpServletRequestResolver resolver;

        @GET
        @Path("/vertx-resolver")
        @Produces(MediaType.TEXT_PLAIN)
        public Response testVertxResolver() {
            // Test HttpServletRequest resolution
            String result;
            try {
                HttpServletRequest httpServletRequest = resolver.resolveHttpServletRequest();

                // Test header map resolution
                Map<String, List<String>> headerMap = resolver.resolveHeaderMap();

                // Test specific header access from HttpServletRequest
                String authHeader = httpServletRequest.getHeader("Authorization");

                // Return simple text response with debug info
                result = "HttpServletRequest available: true, HeaderMap available: true, HeaderCount: %d, AuthHeader: %s".formatted(
                        headerMap.size(), authHeader != null ? authHeader : "null");
            } catch (IllegalStateException e) {
                // Return simple text response with debug info
                result = "HttpServletRequest available: false, HeaderMap available: false, HeaderCount: 0";
            }

            return Response.ok(result).build();
        }

        @GET
        @Path("/parameters")
        @Produces(MediaType.TEXT_PLAIN)
        public Response testParameters() {
            try {
                HttpServletRequest request = resolver.resolveHttpServletRequest();

                StringBuilder result = new StringBuilder();

                // Test single parameter
                result.append("param1=").append(request.getParameter("param1")).append("\n");

                // Test multiple values for same parameter
                String[] param2Values = request.getParameterValues("param2");
                result.append("param2=").append(Arrays.toString(param2Values)).append("\n");

                // Test parameter names enumeration
                Enumeration<String> paramNames = request.getParameterNames();
                List<String> namesList = Collections.list(paramNames);
                result.append("parameterNames: ").append(String.join(",", namesList)).append("\n");

                // Test parameter map
                Map<String, String[]> paramMap = request.getParameterMap();
                result.append("parameterMap size: ").append(paramMap.size()).append("\n");

                return Response.ok(result.toString()).build();
            } catch (IllegalStateException e) {
                return Response.serverError().entity("Error: " + e.getMessage()).build();
            }
        }

        @GET
        @Path("/uri-url/{segment1}/{segment2}")
        @Produces(MediaType.TEXT_PLAIN)
        public Response testUriAndUrl(@PathParam("segment1") String segment1, @PathParam("segment2") String segment2) {
            try {
                HttpServletRequest request = resolver.resolveHttpServletRequest();

                StringBuilder result = new StringBuilder();

                // Test URI
                result.append("URI: ").append(request.getRequestURI()).append("\n");

                // Test URL
                result.append("URL: ").append(request.getRequestURL()).append("\n");

                // Test servlet path
                result.append("ServletPath: ").append(request.getServletPath()).append("\n");

                // Test method
                result.append("Method: ").append(request.getMethod()).append("\n");

                // Test protocol
                result.append("Protocol: ").append(request.getProtocol()).append("\n");

                // Test scheme
                result.append("Scheme: ").append(request.getScheme()).append("\n");

                // Test server name and port
                result.append("ServerName: ").append(request.getServerName()).append("\n");
                result.append("ServerPort: ").append(request.getServerPort()).append("\n");

                return Response.ok(result.toString()).build();
            } catch (IllegalStateException e) {
                return Response.serverError().entity("Error: " + e.getMessage()).build();
            }
        }

        @GET
        @Path("/cookies")
        @Produces(MediaType.TEXT_PLAIN)
        public Response testCookies() {
            try {
                HttpServletRequest request = resolver.resolveHttpServletRequest();

                StringBuilder result = new StringBuilder();

                // Test cookies
                Cookie[] cookies = request.getCookies();
                result.append("Cookie count: ").append(cookies != null ? cookies.length : 0).append("\n");

                if (cookies != null) {
                    for (Cookie cookie : cookies) {
                        result.append(cookie.getName()).append("=").append(cookie.getValue()).append("\n");
                    }
                }

                return Response.ok(result.toString()).build();
            } catch (IllegalStateException e) {
                return Response.serverError().entity("Error: " + e.getMessage()).build();
            }
        }

        @GET
        @Path("/attributes")
        @Produces(MediaType.TEXT_PLAIN)
        public Response testAttributes() {
            try {
                HttpServletRequest request = resolver.resolveHttpServletRequest();

                // Set attributes
                request.setAttribute("attr1", "value1");
                request.setAttribute("attr2", "value2");

                StringBuilder result = new StringBuilder();

                // Test attribute retrieval
                result.append("Attribute1: ").append(request.getAttribute("attr1")).append("\n");
                result.append("Attribute2: ").append(request.getAttribute("attr2")).append("\n");

                // Test attribute names enumeration
                Enumeration<String> attrNames = request.getAttributeNames();
                List<String> namesList = Collections.list(attrNames);
                // Sort the names to ensure consistent order for testing
                Collections.sort(namesList);
                result.append("Attribute names: ").append(String.join(",", namesList)).append("\n");

                return Response.ok(result.toString()).build();
            } catch (IllegalStateException e) {
                return Response.serverError().entity("Error: " + e.getMessage()).build();
            }
        }

        @GET
        @Path("/content-info")
        @Produces(MediaType.TEXT_PLAIN)
        public Response testContentInfo() {
            try {
                HttpServletRequest request = resolver.resolveHttpServletRequest();
                StringBuilder result = new StringBuilder();

                // Test getContentType
                String contentType = request.getContentType();
                result.append("ContentType: ").append(contentType).append("\n");

                // Test getCharacterEncoding
                String characterEncoding = request.getCharacterEncoding();
                result.append("CharacterEncoding: ").append(characterEncoding).append("\n");

                // Test getContentLength
                int contentLength = request.getContentLength();
                result.append("ContentLength: ").append(contentLength).append("\n");

                // Test getContentLengthLong
                long contentLengthLong = request.getContentLengthLong();
                result.append("ContentLengthLong: ").append(contentLengthLong).append("\n");

                // Test setCharacterEncoding
                try {
                    request.setCharacterEncoding("ISO-8859-1");
                    result.append("SetCharacterEncoding: ISO-8859-1\n");
                } catch (UnsupportedEncodingException e) {
                    result.append("SetCharacterEncoding Error: ").append(e.getMessage()).append("\n");
                }

                return Response.ok(result.toString()).build();
            } catch (IllegalStateException e) {
                return Response.serverError().entity("Error: " + e.getMessage()).build();
            }
        }
    }
}
