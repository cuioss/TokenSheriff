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
package de.cuioss.sheriff.token.commons.transport;

import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.handler.SecureSSLContextProvider;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the transport settings of {@link HttpJwksLoaderConfig}: the connect and read timeouts with their
 * explicit defaults, hostname verification on the direct-JWKS and the well-known branch, and how the TLS
 * settings are fitted to a cleartext endpoint and carried to an advertised {@code jwks_uri}. The endpoint,
 * egress and parser settings are covered by {@link HttpJwksLoaderConfigTest}, the opt-in loader settings
 * and the issuer copy by {@link HttpJwksLoaderConfigUnknownKidTest}.
 */
@EnableTestLogger
@DisplayName("Tests HttpJwksLoaderConfig transport settings")
@SuppressWarnings("java:S5778")
// owolff: Suppressing because for a builder this is not a problem
class HttpJwksLoaderConfigTransportTest {

    private static final String VALID_URL = "https://example.com/.well-known/jwks.json";
    private static final String WELL_KNOWN_URL = "https://example.com/.well-known/openid-configuration";
    private static final String INSECURE_JWKS_URL = "http://example.com/.well-known/jwks.json";
    private static final int REFRESH_INTERVAL = 60;

    @Test
    @DisplayName("Should apply explicit JWKS transport timeout defaults consistent with discovery (M8)")
    void shouldApplyExplicitTransportTimeoutDefaults() {

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(REFRESH_INTERVAL)
                .build();

        HttpHandler handler = config.getHttpHandler();
        assertAll("Explicit JWKS transport timeout defaults (M8)",
                () -> assertEquals(2, handler.getConnectionTimeoutSeconds(),
                        "Default JWKS connect timeout must be 2s to match the discovery transport, not cui-http's laxer 10s default"),
                () -> assertEquals(3, handler.getReadTimeoutSeconds(),
                        "Default JWKS read timeout must be 3s to match the discovery transport, not cui-http's laxer 10s default"));
    }

    @Test
    @DisplayName("Should set connect timeout seconds")
    void shouldSetConnectTimeoutSeconds() {

        int connectTimeout = 30;
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(REFRESH_INTERVAL)
                .connectTimeoutSeconds(connectTimeout)
                .build();
        assertEquals(connectTimeout, config.getHttpHandler().getConnectionTimeoutSeconds(),
                "Explicit connect timeout must override the default and be carried by the HttpHandler");
    }

    @Test
    @DisplayName("Should set read timeout seconds")
    void shouldSetReadTimeoutSeconds() {

        int readTimeout = 60;
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(REFRESH_INTERVAL)
                .readTimeoutSeconds(readTimeout)
                .build();
        assertEquals(readTimeout, config.getHttpHandler().getReadTimeoutSeconds(),
                "Explicit read timeout must override the default and be carried by the HttpHandler");
    }

    @Test
    @DisplayName("Should set both connect and read timeout seconds")
    void shouldSetBothTimeouts() {

        int connectTimeout = 30;
        int readTimeout = 60;
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(REFRESH_INTERVAL)
                .connectTimeoutSeconds(connectTimeout)
                .readTimeoutSeconds(readTimeout)
                .build();
        HttpHandler handler = config.getHttpHandler();
        assertAll("Explicit connect and read timeouts",
                () -> assertEquals(connectTimeout, handler.getConnectionTimeoutSeconds(),
                        "Explicit connect timeout must be carried by the HttpHandler"),
                () -> assertEquals(readTimeout, handler.getReadTimeoutSeconds(),
                        "Explicit read timeout must be carried by the HttpHandler"));
    }

    @Test
    @DisplayName("Should throw exception for zero connect timeout seconds")
    void shouldThrowExceptionForZeroConnectTimeoutSeconds() {

        assertThrows(IllegalArgumentException.class, () -> HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(REFRESH_INTERVAL)
                .connectTimeoutSeconds(0)
                .build());
    }

    @Test
    @DisplayName("Should throw exception for negative connect timeout seconds")
    void shouldThrowExceptionForNegativeConnectTimeoutSeconds() {

        int negativeConnectTimeout = -1;
        assertThrows(IllegalArgumentException.class, () -> HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(REFRESH_INTERVAL)
                .connectTimeoutSeconds(negativeConnectTimeout)
                .build());
    }

    @Test
    @DisplayName("Should throw exception for zero read timeout seconds")
    void shouldThrowExceptionForZeroReadTimeoutSeconds() {

        assertThrows(IllegalArgumentException.class, () -> HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(REFRESH_INTERVAL)
                .readTimeoutSeconds(0)
                .build());
    }

    @Test
    @DisplayName("Should throw exception for negative read timeout seconds")
    void shouldThrowExceptionForNegativeReadTimeoutSeconds() {

        int negativeReadTimeout = -1;
        assertThrows(IllegalArgumentException.class, () -> HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(REFRESH_INTERVAL)
                .readTimeoutSeconds(negativeReadTimeout)
                .build());
    }

    @Test
    @DisplayName("Should enable hostname verification by default on the direct-JWKS branch")
    void shouldEnableHostnameVerificationByDefaultOnDirectBranch() {
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer")
                .build();

        assertTrue(config.getHttpHandler().isVerifyHostname(),
                "hostname verification must stay enabled when the knob is never called");
    }

    @Test
    @DisplayName("Should enable hostname verification by default on the well-known branch")
    void shouldEnableHostnameVerificationByDefaultOnWellKnownBranch() {
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .wellKnownUrl(WELL_KNOWN_URL)
                .issuerIdentifier("test-issuer")
                .build();

        assertTrue(config.getHttpHandler().isVerifyHostname(),
                "the discovery transport must default to hostname verification enabled");
    }

    @Test
    @DisplayName("Should forward verifyHostname(false) to the handler on the direct-JWKS branch")
    void shouldForwardExplicitVerifyHostnameOnDirectBranch() {
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer")
                .verifyHostname(false)
                .build();

        assertFalse(config.getHttpHandler().isVerifyHostname(),
                "an explicit verifyHostname(false) must reach the built JWKS handler");
    }

    @Test
    @DisplayName("Should forward verifyHostname(false) to the handler on the well-known branch")
    void shouldForwardExplicitVerifyHostnameOnWellKnownBranch() {
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .wellKnownUrl(WELL_KNOWN_URL)
                .issuerIdentifier("test-issuer")
                .verifyHostname(false)
                .build();

        assertFalse(config.getHttpHandler().isVerifyHostname(),
                "the knob must not be inert for well-known-configured issuers");
    }

    @Test
    @DisplayName("Should reject verifyHostname(false) combined with sslContext(...) naming the conflict")
    void shouldRejectVerifyHostnameFalseWithSslContext() throws Exception {
        var builder = HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer")
                .verifyHostname(false)
                .sslContext(SSLContext.getDefault());

        var exception = assertThrows(IllegalArgumentException.class, builder::build,
                "the incompatible trust-material combination must be rejected at build()");

        assertTrue(exception.getMessage().contains("verifyHostname(false) cannot be combined with sslContext(...)"),
                "the guard's own message must name the conflict, but was: " + exception.getMessage());
        assertFalse(exception.getMessage().contains("Invalid URL or HttpHandler configuration"),
                "a trust-material conflict must not be reported as a malformed-URL problem");
        assertNull(exception.getCause(),
                "the conflict must be raised first-class, not rewrapped around a nested cause");
    }

    @Test
    @DisplayName("Should build a cleartext direct-JWKS handler despite TLS-only settings")
    void shouldBuildCleartextDirectHandlerDespiteTlsSettings() throws Exception {
        var relaxed = HttpJwksLoaderConfig.builder()
                .jwksUrl(INSECURE_JWKS_URL)
                .issuerIdentifier("test-issuer")
                .allowInsecureHttp(true)
                .verifyHostname(false);
        var pinned = HttpJwksLoaderConfig.builder()
                .jwksUrl(INSECURE_JWKS_URL)
                .issuerIdentifier("test-issuer")
                .allowInsecureHttp(true)
                .sslContext(SSLContext.getDefault())
                .tlsVersions(new SecureSSLContextProvider());

        assertAll("cui-http refuses TLS-only settings on http://, so they must not reach a cleartext handler",
                () -> assertDoesNotThrow(relaxed::build),
                () -> assertDoesNotThrow(pinned::build));
    }

    @Test
    @DisplayName("Should build a cleartext discovery handler when hostname verification is relaxed")
    void shouldBuildCleartextWellKnownHandlerWithRelaxedHostnameVerification() {
        var builder = HttpJwksLoaderConfig.builder()
                .wellKnownUrl("http://example.com/.well-known/openid-configuration")
                .issuerIdentifier("test-issuer")
                .allowInsecureHttp(true)
                .verifyHostname(false);

        assertDoesNotThrow(builder::build);
    }

    @Test
    @DisplayName("Should fit the TLS settings to the scheme of an advertised JWKS URL")
    void shouldFitTlsSettingsToAdvertisedJwksScheme() {
        HttpJwksLoaderConfig tlsDiscovery = HttpJwksLoaderConfig.builder()
                .wellKnownUrl(WELL_KNOWN_URL)
                .issuerIdentifier("test-issuer")
                .allowInsecureHttp(true)
                .verifyHostname(false)
                .build();
        HttpJwksLoaderConfig cleartextDiscovery = HttpJwksLoaderConfig.builder()
                .wellKnownUrl("http://example.com/.well-known/openid-configuration")
                .issuerIdentifier("test-issuer")
                .allowInsecureHttp(true)
                .verifyHostname(false)
                .build();

        assertAll("the advertised jwks_uri may differ in scheme from the discovery endpoint",
                () -> assertDoesNotThrow(() -> tlsDiscovery.getHttpHandler(INSECURE_JWKS_URL),
                        "a cleartext jwks_uri must not inherit verifyHostname(false) from a TLS discovery handler"),
                () -> assertFalse(tlsDiscovery.getHttpHandler(VALID_URL).isVerifyHostname()),
                () -> assertFalse(cleartextDiscovery.getHttpHandler(VALID_URL).isVerifyHostname(),
                        "a TLS jwks_uri must keep the relaxation although the cleartext discovery handler carries none"));
    }

    @Test
    @DisplayName("Should carry caller-supplied TLS settings to the discovery handler and an advertised TLS JWKS URL")
    void shouldCarryTlsSettingsThroughWellKnownDiscovery() throws Exception {
        SSLContext configured = SSLContext.getInstance("TLS");
        configured.init(null, null, null);
        HttpJwksLoaderConfig tlsDiscovery = HttpJwksLoaderConfig.builder()
                .wellKnownUrl(WELL_KNOWN_URL)
                .issuerIdentifier("test-issuer")
                .allowInsecureHttp(true)
                .sslContext(configured)
                .tlsVersions(new SecureSSLContextProvider())
                .build();
        HttpJwksLoaderConfig cleartextDiscovery = HttpJwksLoaderConfig.builder()
                .wellKnownUrl("http://example.com/.well-known/openid-configuration")
                .issuerIdentifier("test-issuer")
                .allowInsecureHttp(true)
                .sslContext(configured)
                .build();

        assertAll("the configured trust material must not silently fall back to the JVM default trust",
                () -> assertSame(configured, tlsDiscovery.getHttpHandler().getSslContext(),
                        "the discovery fetch must use the configured SSL context"),
                () -> assertSame(configured, tlsDiscovery.getHttpHandler(VALID_URL).getSslContext(),
                        "an advertised TLS jwks_uri must use the configured SSL context"),
                () -> assertSame(configured, cleartextDiscovery.getHttpHandler(VALID_URL).getSslContext(),
                        "a TLS jwks_uri behind cleartext discovery must still use the configured SSL context"),
                () -> assertDoesNotThrow(() -> tlsDiscovery.getHttpHandler(INSECURE_JWKS_URL),
                        "a cleartext jwks_uri must not inherit the TLS-only settings"));
    }
}
