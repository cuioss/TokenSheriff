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
package de.cuioss.sheriff.token.commons.transport;

import de.cuioss.http.client.adapter.RetryConfig;
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.handler.SecureSSLContextProvider;
import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import javax.net.ssl.SSLContext;
import java.net.URI;

/**
 * Configuration for well-known endpoint discovery.
 * <p>
 * This class encapsulates all configuration parameters needed to create a
 * WellKnownConfig for OIDC endpoint discovery.
 * It uses an internal {@link HttpHandler} built with sensible defaults while allowing
 * customization of timeouts, SSL context, and parser configuration.
 * <p>
 * The configuration supports both String URLs and URI objects for the well-known endpoint,
 * and provides comprehensive SSL/TLS configuration options through the HttpHandler builder pattern.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@ToString
@EqualsAndHashCode
public class WellKnownConfig {

    /**
     * Default connect timeout in seconds for well-known endpoint requests.
     */
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 2;

    /**
     * Default read timeout in seconds for well-known endpoint requests.
     */
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 3;

    /**
     * The HTTP handler for well-known endpoint requests.
     */
    @Getter
    private final HttpHandler httpHandler;

    /**
     * The retry configuration for HTTP operations.
     */
    @Getter
    private final RetryConfig retryConfig;

    /**
     * Parser configuration for JSON processing.
     */
    @Getter
    private final ParserConfig parserConfig;

    private WellKnownConfig(HttpHandler httpHandler, RetryConfig retryConfig, ParserConfig parserConfig) {
        this.httpHandler = httpHandler;
        this.retryConfig = retryConfig;
        this.parserConfig = parserConfig;
    }

    /**
     * Creates a new HttpWellKnownResolver using this configuration.
     *
     * @param securityEventCounter the shared security event counter
     * @return a configured HttpWellKnownResolver instance
     */
    public HttpWellKnownResolver createResolver(SecurityEventCounter securityEventCounter) {
        return new HttpWellKnownResolver(this, securityEventCounter);
    }

    /**
     * Creates a new builder for WellKnownConfig.
     *
     * @return a new WellKnownConfigBuilder instance
     */
    public static WellKnownConfigBuilder builder() {
        return new WellKnownConfigBuilder();
    }

    /**
     * Builder for creating WellKnownConfig instances using HttpHandler builder pattern.
     */
    public static class WellKnownConfigBuilder {
        private final HttpHandler.HttpHandlerBuilder httpHandlerBuilder;
        private RetryConfig retryConfig = RetryConfig.defaults();
        private ParserConfig parserConfig;

        /**
         * Constructor initializing the HttpHandlerBuilder with sensible defaults.
         */
        public WellKnownConfigBuilder() {
            // Permit cleartext HTTP endpoints for well-known discovery. Since cui-http 2.0.0,
            // HttpHandler.build() refuses plaintext HTTP unless allowInsecureHttp(true) is set;
            // opt in here to preserve the prior allow-but-warn behavior (the insecure-HTTP warning
            // is emitted by HttpJwksLoaderConfig.build()).
            this.httpHandlerBuilder = HttpHandler.builder()
                    .allowInsecureHttp(true)
                    .connectionTimeoutSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS)
                    .readTimeoutSeconds(DEFAULT_READ_TIMEOUT_SECONDS);
        }

        /**
         * Sets the well-known endpoint URL as a string.
         *
         * @param wellKnownUrl the well-known endpoint URL string. Must not be null.
         * @return this builder instance
         * @throws IllegalArgumentException if the URL is invalid
         */
        public WellKnownConfigBuilder wellKnownUrl(String wellKnownUrl) {
            httpHandlerBuilder.url(wellKnownUrl);
            return this;
        }

        /**
         * Sets the well-known endpoint URI.
         *
         * @param wellKnownUri the well-known endpoint URI. Must not be null.
         * @return this builder instance
         */
        public WellKnownConfigBuilder wellKnownUri(URI wellKnownUri) {
            httpHandlerBuilder.uri(wellKnownUri);
            return this;
        }

        /**
         * Sets the connection timeout in seconds.
         *
         * @param connectTimeoutSeconds the connection timeout in seconds. Must be positive.
         * @return this builder instance
         * @throws IllegalArgumentException if connectTimeoutSeconds is not positive
         */
        public WellKnownConfigBuilder connectTimeoutSeconds(int connectTimeoutSeconds) {
            httpHandlerBuilder.connectionTimeoutSeconds(connectTimeoutSeconds);
            return this;
        }

        /**
         * Sets the read timeout in seconds.
         *
         * @param readTimeoutSeconds the read timeout in seconds. Must be positive.
         * @return this builder instance
         * @throws IllegalArgumentException if readTimeoutSeconds is not positive
         */
        public WellKnownConfigBuilder readTimeoutSeconds(int readTimeoutSeconds) {
            httpHandlerBuilder.readTimeoutSeconds(readTimeoutSeconds);
            return this;
        }

        /**
         * Sets the SSL context for HTTPS connections.
         *
         * @param sslContext the SSL context to use
         * @return this builder instance
         */
        public WellKnownConfigBuilder sslContext(SSLContext sslContext) {
            httpHandlerBuilder.sslContext(sslContext);
            return this;
        }

        /**
         * Sets the TLS versions configuration.
         *
         * @param tlsVersions the TLS versions configuration
         * @return this builder instance
         */
        public WellKnownConfigBuilder tlsVersions(SecureSSLContextProvider tlsVersions) {
            httpHandlerBuilder.tlsVersions(tlsVersions);
            return this;
        }

        /**
         * Sets the retry configuration for HTTP operations.
         * Defaults to RetryConfig.defaults() if not explicitly set.
         *
         * @param retryConfig the retry configuration to use for HTTP requests
         * @return this builder instance
         */
        public WellKnownConfigBuilder retryConfig(RetryConfig retryConfig) {
            this.retryConfig = retryConfig;
            return this;
        }

        /**
         * Sets the parser configuration for JSON processing.
         *
         * @param parserConfig the parser configuration
         * @return this builder instance
         */
        public WellKnownConfigBuilder parserConfig(ParserConfig parserConfig) {
            this.parserConfig = parserConfig;
            return this;
        }

        /**
         * Builds a new WellKnownConfig instance.
         *
         * @return a new WellKnownConfig instance
         * @throws IllegalStateException if no well-known URI was configured
         * @throws IllegalArgumentException if the HTTP handler configuration is invalid
         */
        public WellKnownConfig build() {
            try {
                HttpHandler httpHandler = httpHandlerBuilder.build();
                ParserConfig finalParserConfig = parserConfig != null ? parserConfig : ParserConfig.builder().build();
                return new WellKnownConfig(httpHandler, retryConfig, finalParserConfig);
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new IllegalArgumentException("Invalid well-known endpoint configuration", e);
            }
        }
    }
}