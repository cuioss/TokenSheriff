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
/**
 * <h2>HTTP-based JWKS Loading</h2>
 *
 * <p>This package loads JSON Web Key Sets (JWKS) from HTTP endpoints, either from a direct JWKS URL
 * or through OpenID Connect well-known discovery, and keeps them current with a scheduled
 * background refresh.</p>
 *
 * <h3>Key Components</h3>
 * <ul>
 *   <li>{@link de.cuioss.sheriff.token.validation.jwks.http.HttpJwksLoader} - the
 *       {@link de.cuioss.sheriff.token.validation.jwks.JwksLoader} for HTTP-based JWKS sources</li>
 *   <li>{@link de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig} - its configuration:
 *       JWKS URL or well-known URL, issuer identifier, refresh interval, TLS settings and egress policy</li>
 *   <li>The cui-http adapters the loader composes: {@code de.cuioss.http.client.adapter.ETagAwareHttpAdapter}
 *       for ETag / HTTP 304 "Not Modified" handling, wrapped by
 *       {@code de.cuioss.http.client.adapter.ResilientHttpAdapter} for retry</li>
 *   <li>{@link de.cuioss.sheriff.token.commons.transport.HttpWellKnownResolver} - resolves the
 *       {@code jwks_uri} and the advertised issuer from the discovery document</li>
 *   <li>{@link de.cuioss.sheriff.token.commons.transport.EgressPolicy} - the SSRF egress guard applied
 *       to every JWKS fetch</li>
 * </ul>
 *
 * <h3>Component Interactions</h3>
 * <ol>
 *   <li><strong>Construction:</strong> {@code HttpJwksLoader} is created from an
 *       {@code HttpJwksLoaderConfig}; the constructor performs no I/O.</li>
 *   <li><strong>Initialization:</strong>
 *       {@link de.cuioss.sheriff.token.validation.jwks.JwksLoader#initJWKSLoader(de.cuioss.sheriff.token.commons.events.SecurityEventCounter)}
 *       supplies the security event counter and starts the asynchronous initial load. In well-known
 *       mode the loader first resolves the discovery document and compares its advertised issuer with
 *       the configured issuer identifier; the {@code jwks_uri} it advertises must pass the egress guard.</li>
 *   <li><strong>Key retrieval:</strong>
 *       {@link de.cuioss.sheriff.token.validation.jwks.JwksLoader#getKeyInfo(String)} only reads the
 *       installed key set; it never triggers loading.</li>
 *   <li><strong>Background refresh:</strong> when enabled, a scheduled task re-fetches the JWKS through
 *       the ETag-aware adapter, so an unchanged key set costs an HTTP 304 instead of a download. The
 *       egress guard is re-checked on every refresh.</li>
 *   <li><strong>Key rotation grace period:</strong> a key set replaced by a refresh is retained as a
 *       retired key set for a grace period, so tokens signed with a just-rotated key still validate.</li>
 *   <li><strong>Failure handling:</strong> a failed refresh keeps the installed keys; the full status
 *       contract is documented on {@link de.cuioss.sheriff.token.validation.jwks.http.HttpJwksLoader}.</li>
 * </ol>
 *
 * <h3>Usage Example</h3>
 * {@snippet :
 * // Create configuration (a direct jwksUrl requires the issuer identifier)
 * HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
 *     .jwksUrl("https://auth.example.com/.well-known/jwks.json")
 *     .issuerIdentifier("https://auth.example.com")
 *     .refreshIntervalSeconds(60)
 *     .build();
 *
 * // Create the loader and start loading the keys
 * JwksLoader loader = JwksLoaderFactory.createHttpLoader(config);
 * LoaderStatus status = loader.initJWKSLoader(securityEventCounter).join();
 *
 * // Get key by ID once the initial load completed
 * Optional<KeyInfo> keyInfo = loader.getKeyInfo("kid123");
 * }
 *
 * <p>Implements requirements:</p>
 * <ul>
 *   <li><a href="../../../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-4.1">VALIDATION-4.1: JWKS Endpoint Support</a></li>
 *   <li><a href="../../../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-8.3">VALIDATION-8.3: Secure Communication</a></li>
 *   <li><a href="../../../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-8.5">VALIDATION-8.5: Cryptographic Agility</a></li>
 * </ul>
 * <p>
 * For more detailed specifications, see the
 * <a href="https://github.com/cuioss/TokenSheriff/tree/main/doc/validation/architecture.adoc#_jwksloader">Technical Components Specification - JwksLoader</a>
 *
 * @author Oliver Wolff
 */
package de.cuioss.sheriff.token.validation.jwks.http;
