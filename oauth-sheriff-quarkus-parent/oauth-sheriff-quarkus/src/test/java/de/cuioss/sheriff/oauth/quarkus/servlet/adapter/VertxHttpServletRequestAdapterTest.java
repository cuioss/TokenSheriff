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
package de.cuioss.sheriff.oauth.quarkus.servlet.adapter;

import de.cuioss.sheriff.oauth.quarkus.servlet.VertxHttpServletRequestAdapter;
import io.vertx.core.MultiMap;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import jakarta.servlet.DispatcherType;
import org.easymock.EasyMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VertxHttpServletRequestAdapter}.
 *
 * <p>This test class focuses on the basic functionality that can be tested without
 * complex mocking. The VertxHttpServletRequestAdapter is thoroughly tested through
 * integration tests in the Quarkus test environment where real Vertx objects are available.</p>
 *
 * <p>These unit tests verify:</p>
 * <ul>
 *   <li>Constructor null-safety</li>
 *   <li>Thread-safe attribute management</li>
 *   <li>UnsupportedOperationException behavior for unsupported servlet methods</li>
 * </ul>
 *
 * @author Oliver Wolff
 */
@DisplayName("VertxHttpServletRequestAdapter Tests")
class VertxHttpServletRequestAdapterTest {

    private HttpServerRequest mockRequest;
    private VertxHttpServletRequestAdapter adapter;

    @BeforeEach
    void setUp() {
        mockRequest = EasyMock.createMock(HttpServerRequest.class);
        // Set up default expectations for methods that are called in the constructor
        EasyMock.expect(mockRequest.version()).andReturn(HttpVersion.HTTP_1_1).anyTimes();
        EasyMock.expect(mockRequest.method()).andReturn(HttpMethod.GET).anyTimes();
        EasyMock.expect(mockRequest.scheme()).andReturn("http").anyTimes();
        EasyMock.expect(mockRequest.uri()).andReturn("/test").anyTimes();
        EasyMock.expect(mockRequest.path()).andReturn("/test").anyTimes();
        EasyMock.expect(mockRequest.query()).andReturn(null).anyTimes();
        EasyMock.expect(mockRequest.absoluteURI()).andReturn("http://localhost/test").anyTimes();
        EasyMock.expect(mockRequest.headers()).andReturn(MultiMap.caseInsensitiveMultiMap()).anyTimes();
        EasyMock.expect(mockRequest.params()).andReturn(MultiMap.caseInsensitiveMultiMap()).anyTimes();
        EasyMock.expect(mockRequest.cookies()).andReturn(Collections.emptySet()).anyTimes();

        EasyMock.replay(mockRequest);
        adapter = new VertxHttpServletRequestAdapter(mockRequest);
        EasyMock.reset(mockRequest);
    }

    @Test
    @DisplayName("Should support HTTP header name normalization for RFC compliance")
    void shouldSupportHttpHeaderNameNormalizationForRfcCompliance() {
        // RFC 7230 §3.2 (HTTP/1.1) and RFC 9113 §8.1.2 (HTTP/2) both require
        // header field names to be treated as case-insensitive. The adapter
        // delegates to Vertx's MultiMap.caseInsensitiveMultiMap(), so a header
        // stored as "Content-Type" must also be retrievable by "content-type" or
        // "CONTENT-TYPE".
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("Content-Type", "application/json");
        headers.add("Authorization", "Bearer token");

        EasyMock.expect(mockRequest.headers()).andReturn(headers).anyTimes();
        EasyMock.replay(mockRequest);

        // Canonical casing
        assertEquals("application/json", adapter.getHeader("Content-Type"));
        // Lowercase – RFC-mandated case-insensitivity
        assertEquals("application/json", adapter.getHeader("content-type"));
        // Uppercase
        assertEquals("application/json", adapter.getHeader("CONTENT-TYPE"));
        // Mixed case on a second header
        assertEquals("Bearer token", adapter.getHeader("AUTHORIZATION"));

        EasyMock.verify(mockRequest);
    }

    @Nested
    @DisplayName("HTTP Header Operations")
    class HttpHeaderTests {

        @Test
        @DisplayName("getHeader should return header value")
        void getHeaderShouldReturnHeaderValue() {
            // Setup
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add("Content-Type", "application/json");
            headers.add("Authorization", "Bearer token123");

            EasyMock.expect(mockRequest.headers()).andReturn(headers).anyTimes();
            EasyMock.expect(mockRequest.getHeader("Content-Type")).andReturn("application/json").anyTimes();
            EasyMock.expect(mockRequest.getHeader("Authorization")).andReturn("Bearer token123").anyTimes();
            EasyMock.expect(mockRequest.getHeader("NonExistentHeader")).andReturn(null).anyTimes();
            EasyMock.replay(mockRequest);

            // Test
            assertEquals("application/json", adapter.getHeader("Content-Type"));
            assertEquals("Bearer token123", adapter.getHeader("Authorization"));
            assertNull(adapter.getHeader("NonExistentHeader"));

            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("getHeaders should return enumeration of header values")
        void getHeadersShouldReturnEnumerationOfHeaderValues() {
            // Setup
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add("Accept", "application/json");
            headers.add("Accept", "text/plain");

            EasyMock.expect(mockRequest.headers()).andReturn(headers).anyTimes();
            EasyMock.replay(mockRequest);

            // Test
            Enumeration<String> acceptHeaders = adapter.getHeaders("Accept");
            Set<String> headerValues = new HashSet<>();
            while (acceptHeaders.hasMoreElements()) {
                headerValues.add(acceptHeaders.nextElement());
            }

            assertEquals(2, headerValues.size());
            assertTrue(headerValues.contains("application/json"));
            assertTrue(headerValues.contains("text/plain"));

            // Test non-existent header
            Enumeration<String> nonExistentHeaders = adapter.getHeaders("NonExistentHeader");
            assertFalse(nonExistentHeaders.hasMoreElements());

            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("getHeaderNames should return all header names")
        void getHeaderNamesShouldReturnAllHeaderNames() {
            // Setup
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add("Content-Type", "application/json");
            headers.add("Authorization", "Bearer token123");
            headers.add("Accept", "application/json");

            EasyMock.expect(mockRequest.headers()).andReturn(headers).anyTimes();
            EasyMock.replay(mockRequest);

            // Test
            Enumeration<String> headerNames = adapter.getHeaderNames();
            Set<String> names = new HashSet<>();
            while (headerNames.hasMoreElements()) {
                names.add(headerNames.nextElement());
            }

            assertTrue(names.contains("Content-Type"));
            assertTrue(names.contains("Authorization"));
            assertTrue(names.contains("Accept"));
            assertEquals(3, names.size());

            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("getIntHeader should parse integer headers")
        void getIntHeaderShouldParseIntegerHeaders() {
            // Setup
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add("Content-Length", "1024");
            headers.add("Invalid-Int", "not-an-int");

            EasyMock.expect(mockRequest.headers()).andReturn(headers).anyTimes();
            EasyMock.replay(mockRequest);

            // Test
            assertEquals(1024, adapter.getIntHeader("Content-Length"));
            assertEquals(-1, adapter.getIntHeader("NonExistentHeader"));

            // Test invalid integer format
            assertEquals(-1, adapter.getIntHeader("Invalid-Int"));

            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("getDateHeader should parse date headers")
        void getDateHeaderShouldParseDateHeaders() {
            // Setup
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add("If-Modified-Since", "1609459200000");
            headers.add("Invalid-Date", "not-a-date");

            EasyMock.expect(mockRequest.headers()).andReturn(headers).anyTimes();
            EasyMock.replay(mockRequest);

            // Test
            assertEquals(1609459200000L, adapter.getDateHeader("If-Modified-Since"));
            assertEquals(-1, adapter.getDateHeader("NonExistentHeader"));

            // Test invalid date format
            assertEquals(-1, adapter.getDateHeader("Invalid-Date"));

            EasyMock.verify(mockRequest);
        }
    }

    @Nested
    @DisplayName("Cookie Operations")
    class CookieTests {

        @Test
        @DisplayName("getCookies should return array of cookies")
        @SuppressWarnings("java:S5961")
        void getCookiesShouldReturnArrayOfCookies() {
            // Setup
            Cookie sessionCookie = EasyMock.createMock(Cookie.class);
            EasyMock.expect(sessionCookie.getName()).andReturn("sessionId").anyTimes();
            EasyMock.expect(sessionCookie.getValue()).andReturn("abc123").anyTimes();
            EasyMock.expect(sessionCookie.getDomain()).andReturn("example.com").anyTimes();
            EasyMock.expect(sessionCookie.getPath()).andReturn("/").anyTimes();
            EasyMock.expect(sessionCookie.getMaxAge()).andReturn(3600L).anyTimes();
            EasyMock.expect(sessionCookie.isSecure()).andReturn(true).anyTimes();
            EasyMock.expect(sessionCookie.isHttpOnly()).andReturn(true).anyTimes();
            EasyMock.expect(sessionCookie.getSameSite()).andReturn(null).anyTimes();
            EasyMock.replay(sessionCookie);

            Cookie preferenceCookie = EasyMock.createMock(Cookie.class);
            EasyMock.expect(preferenceCookie.getName()).andReturn("preference").anyTimes();
            EasyMock.expect(preferenceCookie.getValue()).andReturn("dark-mode").anyTimes();
            EasyMock.expect(preferenceCookie.getDomain()).andReturn(null).anyTimes();
            EasyMock.expect(preferenceCookie.getPath()).andReturn(null).anyTimes();
            EasyMock.expect(preferenceCookie.getMaxAge()).andReturn(-1L).anyTimes();
            EasyMock.expect(preferenceCookie.isSecure()).andReturn(false).anyTimes();
            EasyMock.expect(preferenceCookie.isHttpOnly()).andReturn(false).anyTimes();
            EasyMock.expect(preferenceCookie.getSameSite()).andReturn(null).anyTimes();
            EasyMock.replay(preferenceCookie);

            Set<Cookie> cookies = new HashSet<>();
            cookies.add(sessionCookie);
            cookies.add(preferenceCookie);

            EasyMock.expect(mockRequest.cookies()).andReturn(cookies).anyTimes();
            EasyMock.replay(mockRequest);

            // Test
            jakarta.servlet.http.Cookie[] servletCookies = adapter.getCookies();
            assertEquals(2, servletCookies.length);

            // Find cookies by name
            jakarta.servlet.http.Cookie sessionServletCookie = null;
            jakarta.servlet.http.Cookie preferenceServletCookie = null;

            for (jakarta.servlet.http.Cookie cookie : servletCookies) {
                if ("sessionId".equals(cookie.getName())) {
                    sessionServletCookie = cookie;
                } else if ("preference".equals(cookie.getName())) {
                    preferenceServletCookie = cookie;
                }
            }

            // Verify sessionId cookie
            assertNotNull(sessionServletCookie);
            assertEquals("abc123", sessionServletCookie.getValue());
            assertEquals("example.com", sessionServletCookie.getDomain());
            assertEquals("/", sessionServletCookie.getPath());
            assertEquals(3600, sessionServletCookie.getMaxAge());
            assertTrue(sessionServletCookie.getSecure());
            assertTrue(sessionServletCookie.isHttpOnly());

            // Verify preference cookie
            assertNotNull(preferenceServletCookie);
            assertEquals("dark-mode", preferenceServletCookie.getValue());

            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("getCookies should return empty array when no cookies")
        void getCookiesShouldReturnEmptyArrayWhenNoCookies() {
            // Setup
            EasyMock.expect(mockRequest.cookies()).andReturn(Collections.emptySet()).anyTimes();
            EasyMock.replay(mockRequest);

            // Test with default empty cookie set
            jakarta.servlet.http.Cookie[] cookies = adapter.getCookies();
            assertNotNull(cookies);
            assertEquals(0, cookies.length);

            EasyMock.verify(mockRequest);
        }
    }

    @Nested
    @DisplayName("HTTP Method and URL Operations")
    class HttpMethodAndUrlTests {

        @Test
        @DisplayName("getMethod should return HTTP method")
        void getMethodShouldReturnHttpMethod() {
            // Setup
            EasyMock.expect(mockRequest.method()).andReturn(HttpMethod.GET).times(1);
            EasyMock.expect(mockRequest.method()).andReturn(HttpMethod.POST).times(1);
            EasyMock.replay(mockRequest);

            assertEquals("GET", adapter.getMethod());
            assertEquals("POST", adapter.getMethod());

            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("getQueryString should return query string")
        void getQueryStringShouldReturnQueryString() {
            // Setup
            EasyMock.expect(mockRequest.query()).andReturn("param1=value1&param2=value2").anyTimes();
            EasyMock.replay(mockRequest);

            assertEquals("param1=value1&param2=value2", adapter.getQueryString());

            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("getRequestURI should return request URI")
        void getRequestURIShouldReturnRequestURI() {
            // Setup
            EasyMock.expect(mockRequest.uri()).andReturn("/api/users/123").anyTimes();
            EasyMock.replay(mockRequest);

            assertEquals("/api/users/123", adapter.getRequestURI());

            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("getRequestURL should return request URL")
        void getRequestURLShouldReturnRequestURL() {
            // Setup
            EasyMock.expect(mockRequest.absoluteURI()).andReturn("https://example.com/api/users/123").anyTimes();
            EasyMock.replay(mockRequest);

            assertEquals("https://example.com/api/users/123", adapter.getRequestURL().toString());

            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("getServletPath should return servlet path")
        void getServletPathShouldReturnServletPath() {
            // Setup
            EasyMock.expect(mockRequest.path()).andReturn("/api/users/123").anyTimes();
            EasyMock.replay(mockRequest);

            assertEquals("/api/users/123", adapter.getServletPath());

            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("getProtocol should return HTTP version")
        void getProtocolShouldReturnHttpVersion() {
            // Setup
            EasyMock.expect(mockRequest.version()).andReturn(HttpVersion.HTTP_1_1).times(1);
            EasyMock.expect(mockRequest.version()).andReturn(HttpVersion.HTTP_2).times(1);
            EasyMock.replay(mockRequest);

            assertEquals("HTTP/1.1", adapter.getProtocol());
            assertEquals("HTTP/2", adapter.getProtocol());

            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("getScheme should return request scheme")
        void getSchemeShouldReturnRequestScheme() {
            // Setup
            EasyMock.expect(mockRequest.scheme()).andReturn("https").anyTimes();
            EasyMock.replay(mockRequest);

            assertEquals("https", adapter.getScheme());

            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("isSecure should return true for HTTPS")
        void isSecureShouldReturnTrueForHttps() {
            // Setup
            EasyMock.expect(mockRequest.scheme()).andReturn("https").times(1);
            EasyMock.expect(mockRequest.scheme()).andReturn("http").times(1);
            EasyMock.replay(mockRequest);

            assertTrue(adapter.isSecure());
            assertFalse(adapter.isSecure());

            EasyMock.verify(mockRequest);
        }
    }

    @Nested
    @DisplayName("Request Attributes")
    class RequestAttributeTests {

        @Test
        @DisplayName("getAttribute and setAttribute should manage attributes")
        void getAndSetAttributeShouldManageAttributes() {
            // Test setting and getting attributes
            adapter.setAttribute("attr1", "value1");
            adapter.setAttribute("attr2", 123);

            assertEquals("value1", adapter.getAttribute("attr1"));
            assertEquals(123, adapter.getAttribute("attr2"));
            assertNull(adapter.getAttribute("nonExistentAttr"));

            // Test removing attribute by setting to null
            adapter.setAttribute("attr1", null);
            assertNull(adapter.getAttribute("attr1"));

            // Test explicit removal
            adapter.removeAttribute("attr2");
            assertNull(adapter.getAttribute("attr2"));
        }

        @Test
        @DisplayName("getAttributeNames should return all attribute names")
        void getAttributeNamesShouldReturnAllAttributeNames() {
            // Setup
            adapter.setAttribute("attr1", "value1");
            adapter.setAttribute("attr2", 123);

            // Test
            Enumeration<String> attrNames = adapter.getAttributeNames();
            Set<String> names = new HashSet<>();
            while (attrNames.hasMoreElements()) {
                names.add(attrNames.nextElement());
            }

            assertTrue(names.contains("attr1"));
            assertTrue(names.contains("attr2"));
            assertEquals(2, names.size());
        }
    }

    @Nested
    @DisplayName("Character Encoding and Content")
    class CharacterEncodingAndContentTests {

        @Test
        @DisplayName("getCharacterEncoding should extract charset from Content-Type")
        void getCharacterEncodingShouldExtractCharsetFromContentType() {
            // Setup - Test 1: charset=UTF-8
            MultiMap headers1 = MultiMap.caseInsensitiveMultiMap();
            headers1.add("Content-Type", "application/json; charset=UTF-8");

            // Test 2: charset=ISO-8859-1
            MultiMap headers2 = MultiMap.caseInsensitiveMultiMap();
            headers2.add("Content-Type", "text/html; charset=ISO-8859-1");

            // Test 3: no charset
            MultiMap headers3 = MultiMap.caseInsensitiveMultiMap();
            headers3.add("Content-Type", "application/json");

            // Test 4: no Content-Type header
            MultiMap headers4 = MultiMap.caseInsensitiveMultiMap();

            EasyMock.expect(mockRequest.headers())
                    .andReturn(headers1).times(1)
                    .andReturn(headers2).times(1)
                    .andReturn(headers3).times(1)
                    .andReturn(headers4).times(1);
            EasyMock.replay(mockRequest);

            // Test
            assertEquals("UTF-8", adapter.getCharacterEncoding());

            // Test with different charset
            assertEquals("ISO-8859-1", adapter.getCharacterEncoding());

            // Test with no charset in Content-Type
            assertEquals("UTF-8", adapter.getCharacterEncoding()); // Default is UTF-8

            // Test with no Content-Type header
            assertEquals("UTF-8", adapter.getCharacterEncoding()); // Default is UTF-8
            
            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("setCharacterEncoding should validate charset")
        void setCharacterEncodingShouldValidateCharset() throws Exception {
            // Test valid charset
            adapter.setCharacterEncoding("ISO-8859-1");
            adapter.setCharacterEncoding("UTF-16");

            // Test invalid charset
            assertThrows(UnsupportedEncodingException.class, () -> adapter.setCharacterEncoding("INVALID-CHARSET"));

            // Test null charset (should not throw)
            adapter.setCharacterEncoding(null);
        }

        @Test
        @DisplayName("getContentLength should return Content-Length header as int")
        void getContentLengthShouldReturnContentLengthHeaderAsInt() {
            // Setup
            MultiMap headers1 = MultiMap.caseInsensitiveMultiMap();
            headers1.add("Content-Length", "1024");

            MultiMap headers2 = MultiMap.caseInsensitiveMultiMap();
            headers2.add("Content-Length", "not-a-number");

            MultiMap headers3 = MultiMap.caseInsensitiveMultiMap();

            EasyMock.expect(mockRequest.headers())
                    .andReturn(headers1).times(1)
                    .andReturn(headers2).times(1)
                    .andReturn(headers3).times(1);
            EasyMock.replay(mockRequest);

            // Test
            assertEquals(1024, adapter.getContentLength());

            // Test with invalid Content-Length
            assertEquals(-1, adapter.getContentLength());

            // Test with no Content-Length header
            assertEquals(-1, adapter.getContentLength());

            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("getContentType should return Content-Type header")
        void getContentTypeShouldReturnContentTypeHeader() {
            // Setup
            MultiMap headers1 = MultiMap.caseInsensitiveMultiMap();
            headers1.add("Content-Type", "application/json; charset=UTF-8");

            MultiMap headers2 = MultiMap.caseInsensitiveMultiMap();

            EasyMock.expect(mockRequest.headers())
                    .andReturn(headers1).times(1)
                    .andReturn(headers2).times(1);
            EasyMock.replay(mockRequest);

            // Test
            assertEquals("application/json; charset=UTF-8", adapter.getContentType());

            // Test with no Content-Type header
            assertNull(adapter.getContentType());

            EasyMock.verify(mockRequest);
        }
    }

    @Nested
    @DisplayName("Request Parameters")
    class RequestParameterTests {

        @Test
        @DisplayName("getParameter should return parameter value")
        void getParameterShouldReturnParameterValue() {
            // Setup
            EasyMock.expect(mockRequest.query()).andReturn("param1=value1&param2=value2&param3=value%203").anyTimes();
            EasyMock.expect(mockRequest.params()).andReturn(MultiMap.caseInsensitiveMultiMap()).anyTimes();
            EasyMock.replay(mockRequest);

            // Test
            assertEquals("value1", adapter.getParameter("param1"));
            assertEquals("value2", adapter.getParameter("param2"));
            assertEquals("value 3", adapter.getParameter("param3"));
            assertNull(adapter.getParameter("nonExistentParam"));

            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("getParameterNames should return all parameter names")
        void getParameterNamesShouldReturnAllParameterNames() {
            // Setup
            EasyMock.expect(mockRequest.query()).andReturn("param1=value1&param2=value2&param2=value3").anyTimes();
            EasyMock.expect(mockRequest.params()).andReturn(MultiMap.caseInsensitiveMultiMap()).anyTimes();
            EasyMock.replay(mockRequest);

            // Test
            Enumeration<String> paramNames = adapter.getParameterNames();
            Set<String> names = new HashSet<>();
            while (paramNames.hasMoreElements()) {
                names.add(paramNames.nextElement());
            }

            assertTrue(names.contains("param1"));
            assertTrue(names.contains("param2"));
            assertEquals(2, names.size());

            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("getParameterValues should return all values for a parameter")
        void getParameterValuesShouldReturnAllValuesForParameter() {
            // Setup
            EasyMock.expect(mockRequest.query()).andReturn("param1=value1&param2=value2&param2=value3").anyTimes();
            EasyMock.expect(mockRequest.params()).andReturn(MultiMap.caseInsensitiveMultiMap()).anyTimes();
            EasyMock.replay(mockRequest);

            // Test
            String[] param1Values = adapter.getParameterValues("param1");
            assertNotNull(param1Values);
            assertEquals(1, param1Values.length);
            assertEquals("value1", param1Values[0]);

            String[] param2Values = adapter.getParameterValues("param2");
            assertNotNull(param2Values);
            assertEquals(2, param2Values.length);
            assertTrue(Arrays.asList(param2Values).contains("value2"));
            assertTrue(Arrays.asList(param2Values).contains("value3"));

            // Test non-existent parameter
            assertNull(adapter.getParameterValues("nonExistentParam"));

            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("getParameterMap should return map of all parameters")
        void getParameterMapShouldReturnMapOfAllParameters() {
            // Setup
            EasyMock.expect(mockRequest.query()).andReturn("param1=value1&param2=value2&param2=value3&param3=").anyTimes();
            EasyMock.expect(mockRequest.params()).andReturn(MultiMap.caseInsensitiveMultiMap()).anyTimes();
            EasyMock.replay(mockRequest);

            // Test
            Map<String, String[]> paramMap = adapter.getParameterMap();

            // Check param1
            assertTrue(paramMap.containsKey("param1"));
            assertEquals(1, paramMap.get("param1").length);
            assertEquals("value1", paramMap.get("param1")[0]);

            // Check param2
            assertTrue(paramMap.containsKey("param2"));
            assertEquals(2, paramMap.get("param2").length);
            List<String> param2Values = Arrays.asList(paramMap.get("param2"));
            assertTrue(param2Values.contains("value2"));
            assertTrue(param2Values.contains("value3"));

            // Check param3 (empty value)
            assertTrue(paramMap.containsKey("param3"));
            assertEquals(1, paramMap.get("param3").length);
            assertEquals("", paramMap.get("param3")[0]);

            EasyMock.verify(mockRequest);
        }
    }

    @Nested
    @DisplayName("Request Lifecycle Operations")
    class RequestLifecycleTests {

        @Test
        @DisplayName("getDispatcherType should return REQUEST")
        void getDispatcherTypeShouldReturnRequest() {
            assertEquals(DispatcherType.REQUEST, adapter.getDispatcherType());
        }

        @Test
        @DisplayName("getRequestId should generate unique ID")
        void getRequestIdShouldGenerateUniqueId() {
            String requestId = adapter.getRequestId();
            assertNotNull(requestId);
            assertTrue(requestId.startsWith("vertx-req-"));

            // Test that IDs are consistent for the same adapter instance
            String requestId2 = adapter.getRequestId();
            assertEquals(requestId, requestId2);
        }

        @Test
        @DisplayName("getProtocolRequestId should return same as requestId")
        void getProtocolRequestIdShouldReturnSameAsRequestId() {
            String requestId = adapter.getRequestId();
            String protocolRequestId = adapter.getProtocolRequestId();

            assertEquals(requestId, protocolRequestId);
        }

        @Test
        @DisplayName("isAsyncStarted should return false")
        void isAsyncStartedShouldReturnFalse() {
            assertFalse(adapter.isAsyncStarted());
        }

        @Test
        @DisplayName("isAsyncSupported should return false")
        void isAsyncSupportedShouldReturnFalse() {
            assertFalse(adapter.isAsyncSupported());
        }
    }

    @Nested
    @DisplayName("Locale Operations")
    class LocaleTests {

        @Test
        @DisplayName("getLocale should parse Accept-Language header")
        void getLocaleShouldParseAcceptLanguageHeader() {
            // Setup
            MultiMap headers1 = MultiMap.caseInsensitiveMultiMap();
            headers1.add("Accept-Language", "en-US,en;q=0.9,fr;q=0.8");

            MultiMap headers2 = MultiMap.caseInsensitiveMultiMap();
            headers2.add("Accept-Language", "fr-FR;q=0.9,en-US;q=0.8");

            MultiMap headers3 = MultiMap.caseInsensitiveMultiMap();

            // getLocale calls getLocales which uses headers().get("Accept-Language")
            EasyMock.expect(mockRequest.headers())
                    .andReturn(headers1).times(1)
                    .andReturn(headers2).times(1)
                    .andReturn(headers3).times(1);
            EasyMock.replay(mockRequest);

            // Test
            Locale locale = adapter.getLocale();
            assertEquals("en-US", locale.toLanguageTag());

            // Test with quality value
            locale = adapter.getLocale();
            assertEquals("fr-FR", locale.toLanguageTag());

            // Test with no Accept-Language header
            locale = adapter.getLocale();
            assertEquals(Locale.getDefault(), locale);

            EasyMock.verify(mockRequest);
        }

        @Test
        @DisplayName("getLocales should parse all Accept-Language values")
        void getLocalesShouldParseAllAcceptLanguageValues() {
            // Setup
            MultiMap headers1 = MultiMap.caseInsensitiveMultiMap();
            headers1.add("Accept-Language", "en-US,en;q=0.9,fr;q=0.8,de;q=0.7");

            MultiMap headers2 = MultiMap.caseInsensitiveMultiMap();

            EasyMock.expect(mockRequest.headers())
                    .andReturn(headers1).times(1)
                    .andReturn(headers2).times(1);
            EasyMock.replay(mockRequest);

            // Test
            Enumeration<Locale> locales = adapter.getLocales();
            List<Locale> localeList = Collections.list(locales);

            assertEquals(4, localeList.size());
            assertEquals("en-US", localeList.getFirst().toLanguageTag());
            assertEquals("en", localeList.get(1).toLanguageTag());
            assertEquals("fr", localeList.get(2).toLanguageTag());
            assertEquals("de", localeList.get(3).toLanguageTag());

            // Test with no Accept-Language header
            locales = adapter.getLocales();
            localeList = Collections.list(locales);

            assertEquals(1, localeList.size());
            assertEquals(Locale.getDefault(), localeList.getFirst());

            EasyMock.verify(mockRequest);
        }
    }
}