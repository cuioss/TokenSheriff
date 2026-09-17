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

import javax.net.ssl.SSLContext;
import java.net.URI;

/**
 * Keeps the TLS-only transport settings off cleartext {@code http://} endpoints.
 * <p>
 * cui-http refuses {@code sslContext(...)}, {@code tlsVersions(...)} and {@code verifyHostname(false)} on
 * an {@code http://} URI, because an http handler establishes no TLS connection. One issuer configuration
 * fans out to several endpoints — the discovery document and the {@code jwks_uri} it advertises — which
 * may differ in scheme when cleartext is permitted. The TLS settings are therefore applied per endpoint:
 * kept for a TLS endpoint, dropped for a cleartext one, instead of failing the whole configuration.
 *
 * @since 1.0
 */
final class CleartextEndpoints {

    private CleartextEndpoints() {
    }

    /**
     * The configured TLS settings of one transport configuration, retained so they can be applied to every
     * TLS endpoint it serves — also to one derived from a handler that carries none, such as the handler of a
     * cleartext discovery endpoint.
     *
     * @param verifyHostname the configured hostname-verification posture
     * @param sslContext     the caller-supplied trust material, or {@code null} for the cui-http default
     * @param tlsVersions    the caller-supplied TLS-version policy, or {@code null} for the cui-http default
     */
    record TlsSettings(boolean verifyHostname, SSLContext sslContext, SecureSSLContextProvider tlsVersions) {
    }

    /**
     * @param endpoint the configured or advertised endpoint URL, may be {@code null}
     * @return {@code true} if the endpoint uses the cleartext {@code http} scheme. A URL without a scheme
     *         (which cui-http upgrades to https) or a malformed one (which the handler builder rejects on its
     *         own) is not cleartext.
     */
    static boolean isCleartext(String endpoint) {
        if (endpoint == null) {
            return false;
        }
        try {
            return "http".equalsIgnoreCase(URI.create(endpoint.strip()).getScheme());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Fits a builder's TLS settings to {@code endpoint}: for a TLS endpoint the configured settings are applied
     * (a {@code null} SSL context or TLS-version policy leaves whatever the builder already carries); for a
     * cleartext endpoint the TLS-only settings are reset so cui-http accepts the handler.
     *
     * @param builder  the handler builder already pointing at {@code endpoint}
     * @param endpoint the endpoint URL the builder targets
     * @param settings the configured TLS settings
     * @return the given builder
     */
    static HttpHandler.HttpHandlerBuilder applyTlsSettings(HttpHandler.HttpHandlerBuilder builder, String endpoint,
            TlsSettings settings) {
        if (isCleartext(endpoint)) {
            return builder.sslContext(null).tlsVersions(null).verifyHostname(true);
        }
        builder.verifyHostname(settings.verifyHostname());
        if (settings.sslContext() != null) {
            builder.sslContext(settings.sslContext());
        }
        if (settings.tlsVersions() != null) {
            builder.tlsVersions(settings.tlsVersions());
        }
        return builder;
    }
}
