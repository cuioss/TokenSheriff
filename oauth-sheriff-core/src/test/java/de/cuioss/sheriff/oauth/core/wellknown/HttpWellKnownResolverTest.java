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
package de.cuioss.sheriff.oauth.core.wellknown;

import de.cuioss.http.client.adapter.RetryConfig;
import de.cuioss.sheriff.oauth.core.json.WellKnownResult;
import de.cuioss.sheriff.oauth.core.security.SecurityEventCounter;
import de.cuioss.sheriff.oauth.core.test.dispatcher.WellKnownDispatcher;
import de.cuioss.sheriff.oauth.core.util.LoaderStatus;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for {@link HttpWellKnownResolver} with MockWebServer integration.
 * <p>
 * These tests verify the resolver works correctly with real HTTP operations,
 * including timing variants, error scenarios, and caching behavior.
 *
 * @author Oliver Wolff
 */
@EnableTestLogger
@DisplayName("HttpWellKnownResolver MockWebServer Tests")
@EnableMockWebServer
class HttpWellKnownResolverTest {

    @Getter
    private final WellKnownDispatcher moduleDispatcher = new WellKnownDispatcher();

    private HttpWellKnownResolver resolver;

    @BeforeEach
    void setUp() {
        moduleDispatcher.setCallCounter(0);
    }

    @Test
    @DisplayName("Should successfully resolve JWKS URI from well-known endpoint")
    void shouldSuccessfullyResolveJwksUri(URIBuilder uriBuilder) {
        // Setup well-known dispatcher to return valid response
        moduleDispatcher.returnDefault();

        String baseUrl = uriBuilder.buildAsString();
        String wellKnownUrl = uriBuilder.addPathSegment(".well-known")
                .addPathSegment("openid-configuration").buildAsString();
        String expectedJwksUri = baseUrl + "/oidc/jwks.json";

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(wellKnownUrl)
                .retryConfig(RetryConfig.builder().maxAttempts(1).build())
                .build();

        resolver = config.createResolver(new SecurityEventCounter());

        // Test JWKS URI resolution
        Optional<String> jwksUri = resolver.getJwksUri();
        assertTrue(jwksUri.isPresent(), "JWKS URI should be discovered");
        assertEquals(expectedJwksUri, jwksUri.get(), "JWKS URI should match expected URL");

        // Verify health status is OK after successful resolution
        assertEquals(LoaderStatus.OK, resolver.getLoaderStatus());

        // Verify well-known endpoint was called once
        assertEquals(1, moduleDispatcher.getCallCounter(), "Well-known endpoint should be called once");
    }

    @Test
    @DisplayName("Should successfully resolve issuer from well-known endpoint")
    void shouldSuccessfullyResolveIssuer(URIBuilder uriBuilder) {
        // Setup well-known dispatcher to return valid response
        moduleDispatcher.returnDefault();

        String baseUrl = uriBuilder.buildAsString();
        String wellKnownUrl = uriBuilder.addPathSegment(".well-known")
                .addPathSegment("openid-configuration").buildAsString();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(wellKnownUrl)
                .retryConfig(RetryConfig.builder().maxAttempts(1).build())
                .build();

        resolver = config.createResolver(new SecurityEventCounter());

        // Test issuer resolution
        Optional<String> issuer = resolver.getIssuer();
        assertTrue(issuer.isPresent(), "Issuer should be discovered");
        assertEquals(baseUrl, issuer.get(), "Issuer should match base URL");

        // Verify health status is OK
        assertEquals(LoaderStatus.OK, resolver.getLoaderStatus());
    }

    @Test
    @DisplayName("Should resolve multiple endpoints correctly")
    void shouldResolveMultipleEndpoints(URIBuilder uriBuilder) {
        // Setup well-known dispatcher to return valid response
        moduleDispatcher.returnDefault();

        String baseUrl = uriBuilder.buildAsString();
        String wellKnownUrl = uriBuilder.addPathSegment(".well-known")
                .addPathSegment("openid-configuration").buildAsString();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(wellKnownUrl)
                .retryConfig(RetryConfig.builder().maxAttempts(1).build())
                .build();

        resolver = config.createResolver(new SecurityEventCounter());

        // Test multiple endpoint resolutions
        Optional<String> issuer = resolver.getIssuer();
        Optional<String> jwksUri = resolver.getJwksUri();
        Optional<WellKnownResult> wellKnownResult = resolver.getWellKnownResult();

        // Core endpoints should be available
        assertTrue(issuer.isPresent(), "Issuer should be available");
        assertTrue(jwksUri.isPresent(), "JWKS URI should be available");
        assertTrue(wellKnownResult.isPresent(), "WellKnownResult should be available");

        // Verify endpoint URLs
        assertEquals(baseUrl, issuer.get());
        assertEquals(baseUrl + "/oidc/jwks.json", jwksUri.get());

        // Verify additional endpoints via WellKnownResult
        var result = wellKnownResult.get();
        assertEquals(baseUrl + "/protocol/openid-connect/auth", result.authorizationEndpoint());
        assertEquals(baseUrl + "/protocol/openid-connect/token", result.tokenEndpoint());
        assertEquals(baseUrl + "/protocol/openid-connect/userinfo", result.userinfoEndpoint());

        // Should only call the well-known endpoint once due to caching
        assertEquals(1, moduleDispatcher.getCallCounter(), "Well-known endpoint should be called once (cached)");
    }

    @Test
    @DisplayName("Should handle error responses gracefully")
    void shouldHandleErrorResponsesGracefully(URIBuilder uriBuilder) {
        // Setup dispatcher to return error
        moduleDispatcher.returnError();

        String wellKnownUrl = uriBuilder.addPathSegment(".well-known")
                .addPathSegment("openid-configuration").buildAsString();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(wellKnownUrl)
                .retryConfig(RetryConfig.builder().maxAttempts(1).build())
                .build();

        resolver = config.createResolver(new SecurityEventCounter());

        // All endpoint resolutions should return empty
        assertFalse(resolver.getJwksUri().isPresent(), "JWKS URI should not be available on error");
        assertFalse(resolver.getIssuer().isPresent(), "Issuer should not be available on error");
        assertFalse(resolver.getWellKnownResult().isPresent(), "WellKnownResult should not be available on error");

        // Health status should reflect error
        assertEquals(LoaderStatus.ERROR, resolver.getLoaderStatus());

        // Well-known endpoint should be called
        assertTrue(moduleDispatcher.getCallCounter() >= 1, "Well-known endpoint should be called at least once");
    }

    @Test
    @DisplayName("Should handle missing JWKS URI gracefully")
    void shouldHandleMissingJwksUriGracefully(URIBuilder uriBuilder) {
        // Setup dispatcher with response missing JWKS URI
        moduleDispatcher.returnMissingJwksUri();

        String wellKnownUrl = uriBuilder.addPathSegment(".well-known")
                .addPathSegment("openid-configuration").buildAsString();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(wellKnownUrl)
                .retryConfig(RetryConfig.builder().maxAttempts(1).build())
                .build();

        resolver = config.createResolver(new SecurityEventCounter());

        // JWKS URI should not be available
        assertFalse(resolver.getJwksUri().isPresent(), "JWKS URI should not be available when missing from response");

        // No endpoints should be available since JWKS URI is a required field
        assertFalse(resolver.getIssuer().isPresent(), "Issuer should not be available when JWKS URI is missing (invalid config)");

        // Health status should be ERROR since JWKS URI is required for JWT validation
        assertEquals(LoaderStatus.ERROR, resolver.getLoaderStatus());
    }

    @Test
    @DisplayName("Should provide access to complete WellKnownResult")
    void shouldProvideAccessToCompleteWellKnownResult(URIBuilder uriBuilder) {
        // Setup well-known dispatcher to return valid response
        moduleDispatcher.returnDefault();

        String wellKnownUrl = uriBuilder.addPathSegment(".well-known")
                .addPathSegment("openid-configuration").buildAsString();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(wellKnownUrl)
                .retryConfig(RetryConfig.builder().maxAttempts(1).build())
                .build();

        resolver = config.createResolver(new SecurityEventCounter());

        // Test complete result access
        Optional<WellKnownResult> result = resolver.getWellKnownResult();
        assertTrue(result.isPresent(), "WellKnownResult should be available");

        var wellKnown = result.get();
        assertNotNull(wellKnown.getIssuer(), "Issuer should be present in result");
        assertNotNull(wellKnown.getJwksUri(), "JWKS URI should be present in result");
        assertNotNull(wellKnown.authorizationEndpoint(), "Authorization endpoint should be present in result");
    }
}