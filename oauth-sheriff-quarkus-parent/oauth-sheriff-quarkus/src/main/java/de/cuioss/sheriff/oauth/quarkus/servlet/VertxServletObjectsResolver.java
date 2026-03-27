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
import de.cuioss.tools.logging.CuiLogger;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;

import static de.cuioss.sheriff.oauth.quarkus.OAuthSheriffQuarkusLogMessages.ERROR;

/**
 * Vertx-based implementation for resolving servlet objects within Quarkus JAX-RS request contexts.
 *
 * <p>This implementation uses Vertx {@link HttpServerRequest} to access HTTP context and provides
 * a comprehensive compatibility layer that creates an HttpServletRequest-like interface from Vertx data.
 * Methods throw {@link IllegalStateException} when no Vertx context is available, which is
 * the usual case outside of active REST requests.</p>
 *
 * <p><strong>Usage:</strong> This resolver should only be used within active Quarkus JAX-RS request contexts.
 * Outside of REST requests, CDI will throw {@link jakarta.enterprise.inject.IllegalProductException} 
 * because the underlying {@code @RequestScoped} HttpServerRequest producer cannot provide a valid instance.</p>
 *
 * <p><strong>CDI Usage:</strong></p>
 * <pre>{@code
 * @Inject
 * @ServletObjectsResolver
 * HttpServletRequestResolver resolver;
 * }</pre>
 *
 * <p><strong>Quarkus Context:</strong> This implementation works with Quarkus's Vertx-based HTTP layer
 * and provides access to HTTP request data through the native Vertx APIs.</p>
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@ApplicationScoped
@ServletObjectsResolver
public class VertxServletObjectsResolver implements HttpServletRequestResolver {

    private static final CuiLogger LOGGER = new CuiLogger(VertxServletObjectsResolver.class);

    private final Instance<HttpServerRequest> vertxRequestInstance;

    @Inject
    public VertxServletObjectsResolver(Instance<HttpServerRequest> vertxRequestInstance) {
        this.vertxRequestInstance = vertxRequestInstance;
    }

    /**
     * Resolves the HttpServletRequest from the current Vertx context.
     *
     * <p>This implementation creates a comprehensive HttpServletRequest adapter from the Vertx HttpServerRequest.
     * The adapter provides access to headers, request parameters, and other HTTP request information.</p>
     *
     * @return HttpServletRequest adapter from Vertx context
     * @throws jakarta.enterprise.inject.IllegalProductException if not in an active request context 
     *                               (CDI wraps underlying exceptions when @RequestScoped producer fails)
     * @throws IllegalStateException if CDI context is available but HttpServerRequest is null
     */
   
    @Override
    public HttpServletRequest resolveHttpServletRequest() throws IllegalStateException {
        LOGGER.debug("Attempting to resolve HttpServletRequest from Vertx context");

        if (vertxRequestInstance.isUnsatisfied()) {
            LOGGER.error(ERROR.VERTX_REQUEST_CONTEXT_UNAVAILABLE);
            throw new IllegalStateException("Vertx HttpServerRequest bean is not available in CDI context");
        }

        HttpServerRequest vertxRequest = vertxRequestInstance.get();

        if (vertxRequest == null) {
            LOGGER.error(ERROR.VERTX_REQUEST_CONTEXT_UNAVAILABLE);
            throw new IllegalStateException("Vertx HttpServerRequest is null - no active request context available");
        }

        LOGGER.debug("Successfully resolved Vertx HttpServerRequest: %s", vertxRequest.getClass().getName());
        return new VertxHttpServletRequestAdapter(vertxRequest);
    }
}