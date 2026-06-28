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
package de.cuioss.sheriff.token.validation.wellknown;

import de.cuioss.http.client.adapter.ETagAwareHttpAdapter;
import de.cuioss.http.client.adapter.HttpAdapter;
import de.cuioss.http.client.adapter.ResilientHttpAdapter;
import de.cuioss.http.client.result.HttpResult;
import de.cuioss.sheriff.token.validation.json.WellKnownResult;
import de.cuioss.sheriff.token.validation.security.SecurityEventCounter;
import de.cuioss.sheriff.token.validation.util.LoaderStatus;
import de.cuioss.sheriff.token.validation.util.LoadingStatusProvider;
import de.cuioss.tools.logging.CuiLogger;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP-based implementation for resolving OpenID Connect well-known configuration endpoints.
 * <p>
 * This class provides a thin wrapper around {@link HttpAdapter} for loading and
 * parsing well-known OIDC discovery documents. It handles HTTP operations, caching via ETag,
 * and provides convenient access to discovered endpoints.
 * <p>
 * The resolver loads the well-known configuration once and caches the result for
 * subsequent endpoint lookups. It provides methods to access common OIDC endpoints
 * like JWKS URI, issuer, authorization endpoint, etc.
 * <p>
 * <strong>Thread Safety:</strong> This class uses AtomicReference for lock-free thread-safe caching.
 * The compareAndSet pattern prevents duplicate loads while allowing concurrent access.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public class HttpWellKnownResolver implements LoadingStatusProvider {

    private static final CuiLogger LOGGER = new CuiLogger(HttpWellKnownResolver.class);

    private final HttpAdapter<WellKnownResult> wellKnownAdapter;
    private final AtomicReference<HttpResult<WellKnownResult>> cachedResult = new AtomicReference<>();
    private final AtomicReference<LoaderStatus> status = new AtomicReference<>(LoaderStatus.UNDEFINED);

    /**
     * Creates a new HttpWellKnownResolver with the specified configuration.
     * <p>
     * The resolver uses a composition of adapters:
     * <ul>
     * <li>Base: {@link ETagAwareHttpAdapter} for bandwidth optimization via ETag caching</li>
     * <li>Decorator: {@link ResilientHttpAdapter} for retry behavior</li>
     * </ul>
     *
     * @param config the well-known configuration containing HTTP handler and parser settings
     * @param securityEventCounter the shared security event counter for tracking violations
     */
    public HttpWellKnownResolver(WellKnownConfig config, SecurityEventCounter securityEventCounter) {
        var converter = new WellKnownConfigurationConverter(
                config.getParserConfig().getDslJson(),
                securityEventCounter,
                config.getParserConfig().getMaxPayloadSize());

        // Create base adapter with ETag caching
        HttpAdapter<WellKnownResult> baseAdapter = ETagAwareHttpAdapter.<WellKnownResult>builder()
                .httpHandler(config.getHttpHandler())
                .responseConverter(converter)
                .build();

        // Wrap with retry behavior
        this.wellKnownAdapter = ResilientHttpAdapter.wrap(baseAdapter, config.getRetryConfig());

        LOGGER.debug("Created HttpWellKnownResolver for well-known endpoint discovery");
    }

    /**
     * Ensures the well-known configuration is loaded and cached using thread-safe lazy initialization.
     * <p>
     * Uses AtomicReference.updateAndGet for lock-free thread-safe caching. The atomic update ensures
     * only one thread performs the HTTP load, while racing threads get the cached result.
     *
     * @return Optional containing the WellKnownResult if available and valid, empty otherwise
     */
    private Optional<WellKnownResult> ensureLoaded() {
        HttpResult<WellKnownResult> result = cachedResult.updateAndGet(current -> {
            if (current == null) {
                status.set(LoaderStatus.LOADING);
                HttpResult<WellKnownResult> loaded = wellKnownAdapter.getBlocking();
                status.set(loaded.isSuccess() ? LoaderStatus.OK : LoaderStatus.ERROR);
                return loaded;
            }
            return current;
        });

        return result.isSuccess() ? result.getContent() : Optional.empty();
    }

    /**
     * Gets the JWKS URI from the well-known configuration.
     *
     * @return Optional containing the JWKS URI if available, empty otherwise
     */
    public Optional<String> getJwksUri() {
        return ensureLoaded().flatMap(WellKnownResult::getJwksUri);
    }

    /**
     * Gets the issuer from the well-known configuration.
     *
     * @return Optional containing the issuer if available, empty otherwise
     */
    public Optional<String> getIssuer() {
        return ensureLoaded().flatMap(WellKnownResult::getIssuer);
    }

    /**
     * Gets the complete well-known configuration result.
     *
     * @return Optional containing the WellKnownResult if available, empty otherwise
     */
    Optional<WellKnownResult> getWellKnownResult() {
        return ensureLoaded();
    }

    /**
     * Checks the health status of the well-known resolver.
     * <p>
     * Returns the current loading status tracked through the {@link #status} field.
     *
     * @return the current LoaderStatus indicating health state
     */
    @Override
    public LoaderStatus getLoaderStatus() {
        return status.get();
    }
}