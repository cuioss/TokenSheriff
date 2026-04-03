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

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import org.easymock.EasyMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests specifically for the UnsupportedOperationException cases in {@link VertxHttpServletRequestAdapter}.
 *
 * <p>This test class verifies that methods which cannot be implemented
 * in the Vertx environment properly throw UnsupportedOperationException with
 * descriptive messages.</p>
 *
 * @author Oliver Wolff
 */
@DisplayName("VertxHttpServletRequestAdapter Unsupported Operations Tests")
class VertxHttpServletRequestAdapterUnsupportedOperationsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "getAuthType", "getRemoteUser", "isUserInRole", "getUserPrincipal",
            "getRequestedSessionId", "getSession", "getSessionWithBoolean", "changeSessionId",
            "isRequestedSessionIdValid", "isRequestedSessionIdFromCookie",
            "isRequestedSessionIdFromURL", "authenticate", "login", "logout",
            "getParts", "getPart", "upgrade", "getInputStream", "getReader",
            "getRequestDispatcher", "getServletContext", "startAsync", "startAsyncWithParams",
            "getAsyncContext", "getServletConnection", "getPathTranslated"
    })
    @DisplayName("Should throw UnsupportedOperationException for unsupported methods")
    @SuppressWarnings("java:S5961")
    void shouldThrowUnsupportedOperationExceptionForUnsupportedMethods(String methodName) {
        // Create a mock HttpServerRequest
        HttpServerRequest mockRequest = EasyMock.createMock(HttpServerRequest.class);

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
        VertxHttpServletRequestAdapter adapter = new VertxHttpServletRequestAdapter(mockRequest);

        UnsupportedOperationException exception = null;

        switch (methodName) {
            case "getAuthType":
                exception = assertThrows(UnsupportedOperationException.class, adapter::getAuthType);
                break;
            case "getRemoteUser":
                exception = assertThrows(UnsupportedOperationException.class, adapter::getRemoteUser);
                break;
            case "isUserInRole":
                exception = assertThrows(UnsupportedOperationException.class, () -> adapter.isUserInRole("admin"));
                break;
            case "getUserPrincipal":
                exception = assertThrows(UnsupportedOperationException.class, adapter::getUserPrincipal);
                break;
            case "getRequestedSessionId":
                exception = assertThrows(UnsupportedOperationException.class, adapter::getRequestedSessionId);
                break;
            case "getSession":
                exception = assertThrows(UnsupportedOperationException.class, adapter::getSession);
                break;
            case "getSessionWithBoolean":
                exception = assertThrows(UnsupportedOperationException.class, () -> adapter.getSession(true));
                break;
            case "changeSessionId":
                exception = assertThrows(UnsupportedOperationException.class, adapter::changeSessionId);
                break;
            case "isRequestedSessionIdValid":
                exception = assertThrows(UnsupportedOperationException.class, adapter::isRequestedSessionIdValid);
                break;
            case "isRequestedSessionIdFromCookie":
                exception = assertThrows(UnsupportedOperationException.class, adapter::isRequestedSessionIdFromCookie);
                break;
            case "isRequestedSessionIdFromURL":
                exception = assertThrows(UnsupportedOperationException.class, adapter::isRequestedSessionIdFromURL);
                break;
            case "authenticate":
                exception = assertThrows(UnsupportedOperationException.class, () -> adapter.authenticate(null));
                break;
            case "login":
                exception = assertThrows(UnsupportedOperationException.class, () -> adapter.login("user", "pass"));
                break;
            case "logout":
                exception = assertThrows(UnsupportedOperationException.class, adapter::logout);
                break;
            case "getParts":
                exception = assertThrows(UnsupportedOperationException.class, adapter::getParts);
                break;
            case "getPart":
                exception = assertThrows(UnsupportedOperationException.class, () -> adapter.getPart("file"));
                break;
            case "upgrade":
                exception = assertThrows(UnsupportedOperationException.class, () -> adapter.upgrade(null));
                break;
            case "getInputStream":
                exception = assertThrows(UnsupportedOperationException.class, adapter::getInputStream);
                break;
            case "getReader":
                exception = assertThrows(UnsupportedOperationException.class, adapter::getReader);
                break;
            case "getRequestDispatcher":
                exception = assertThrows(UnsupportedOperationException.class, () -> adapter.getRequestDispatcher("/path"));
                break;
            case "getServletContext":
                exception = assertThrows(UnsupportedOperationException.class, adapter::getServletContext);
                break;
            case "startAsync":
                exception = assertThrows(UnsupportedOperationException.class, adapter::startAsync);
                break;
            case "startAsyncWithParams":
                exception = assertThrows(UnsupportedOperationException.class, () -> adapter.startAsync(null, null));
                break;
            case "getAsyncContext":
                exception = assertThrows(UnsupportedOperationException.class, adapter::getAsyncContext);
                break;
            case "getServletConnection":
                exception = assertThrows(UnsupportedOperationException.class, adapter::getServletConnection);
                break;
            case "getPathTranslated":
                exception = assertThrows(UnsupportedOperationException.class, adapter::getPathTranslated);
                break;
            default:
                fail("Unknown method: " + methodName);
        }

        assertNotNull(exception, "Exception should not be null for method: " + methodName);
        assertNotNull(exception.getMessage(), "Exception message should not be null for method: " + methodName);
        assertFalse(exception.getMessage().isEmpty(), "Exception message should not be empty for method: " + methodName);

        // Verify that the exception message contains useful information
        String message = exception.getMessage().toLowerCase();
        assertTrue(message.contains("not") && (message.contains("support") || message.contains("available")),
                "Exception message should indicate that the operation is not supported or not available");
    }


    @Test
    @DisplayName("getContextPath should return empty string")
    void getContextPathShouldReturnEmptyString() {
        // Create a mock HttpServerRequest
        HttpServerRequest mockRequest = EasyMock.createMock(HttpServerRequest.class);

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
        VertxHttpServletRequestAdapter adapter = new VertxHttpServletRequestAdapter(mockRequest);

        String contextPath = adapter.getContextPath();

        assertEquals("", contextPath, "Context path should be an empty string");

        EasyMock.verify(mockRequest);
    }
}
