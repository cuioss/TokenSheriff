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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.SSLContext;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CleartextEndpoints keeps TLS-only settings off http:// endpoints")
class CleartextEndpointsTest {

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/jwks", "HTTP://example.com/jwks", " http://example.com/jwks"})
    @DisplayName("Should classify an http scheme as cleartext, case-insensitively")
    void shouldClassifyHttpAsCleartext(String endpoint) {
        assertTrue(CleartextEndpoints.isCleartext(endpoint));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"https://example.com/jwks", "example.com/jwks", "http://exa mple.com/jwks"})
    @DisplayName("Should not classify https, scheme-less, malformed or null endpoints as cleartext")
    void shouldNotClassifyOtherEndpointsAsCleartext(String endpoint) {
        assertFalse(CleartextEndpoints.isCleartext(endpoint));
    }

    @Test
    @DisplayName("Should reset the TLS-only settings for a cleartext endpoint so the handler builds")
    void shouldResetTlsSettingsForCleartextEndpoint() throws Exception {
        String endpoint = "http://example.com/jwks";
        var builder = HttpHandler.builder()
                .url(endpoint)
                .allowInsecureHttp(true)
                .sslContext(SSLContext.getDefault())
                .tlsVersions(new SecureSSLContextProvider());

        var handler = assertDoesNotThrow(
                () -> CleartextEndpoints.applyTlsSettings(builder, endpoint,
                        new CleartextEndpoints.TlsSettings(false, null, null)).build(),
                "cui-http refuses TLS-only settings on http://, so they must be reset");

        assertTrue(handler.isVerifyHostname(), "a cleartext handler keeps the neutral hostname default");
    }

    @Test
    @DisplayName("Should apply the configured TLS settings to a TLS endpoint")
    void shouldApplyTlsSettingsToTlsEndpoint() throws Exception {
        String endpoint = "https://example.com/jwks";

        var configured = SSLContext.getInstance("TLS");
        configured.init(null, null, null);

        var handler = CleartextEndpoints.applyTlsSettings(HttpHandler.builder().url(endpoint), endpoint,
                new CleartextEndpoints.TlsSettings(true, configured, new SecureSSLContextProvider())).build();

        assertAll("the configured TLS settings reach a TLS endpoint",
                () -> assertTrue(handler.isVerifyHostname()),
                () -> assertSame(configured, handler.getSslContext()));
    }
}
