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
package de.cuioss.sheriff.token.quarkus.producer;

import de.cuioss.sheriff.token.quarkus.config.IssuerConfigResolver;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.tools.logging.CuiLogger;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;

/**
 * Forces the property-configured {@link TokenValidator} to be built while the application starts.
 * <p>
 * The {@link TokenValidator} produced by {@link TokenValidatorProducer} is an
 * {@link ApplicationScoped} bean and therefore lazy: without this initializer an invalid
 * {@code sheriff.token.issuers.*} configuration would only surface on the first request that
 * validates a token, after the application already reported itself as started.
 * </p>
 * <p>
 * <strong>Gate:</strong> on {@link StartupEvent} this bean evaluates
 * {@link IssuerConfigResolver#hasEnabledIssuers(Config)} — the same pure configuration probe that
 * decides {@code PROPERTY_CONFIGURED} in the observability resolver, so startup and observability
 * agree on what "configured" means.
 * </p>
 * <ul>
 *   <li>No enabled issuer &rarr; the validator handle is not touched at all. An application that does
 *       not configure the extension, or that supplies its own {@code TokenValidator}, keeps booting.</li>
 *   <li>At least one enabled issuer &rarr; the extension's default-qualified validator is resolved and
 *       its client proxy is forced to instantiate the underlying bean.</li>
 * </ul>
 * <p>
 * <strong>Fail-boot contract:</strong> no exception is caught. Any failure while resolving the issuer
 * configuration or while building the validator propagates out of the startup observer and aborts the
 * application start.
 * </p>
 *
 * @since 1.0
 */
@ApplicationScoped
public class TokenValidatorStartupInitializer {

    private static final CuiLogger LOGGER = new CuiLogger(TokenValidatorStartupInitializer.class);

    private final Config config;
    private final Instance<TokenValidator> tokenValidator;

    /**
     * CDI constructor.
     *
     * @param config         the MicroProfile configuration used for the enabled-issuer probe
     * @param tokenValidator lazy handle to the default-qualified {@link TokenValidator}; it is only
     *                       resolved when the probe reports at least one enabled issuer
     */
    @Inject
    public TokenValidatorStartupInitializer(Config config, Instance<TokenValidator> tokenValidator) {
        this.config = config;
        this.tokenValidator = tokenValidator;
    }

    /**
     * Builds the property-configured validator at startup when at least one issuer is enabled.
     * <p>
     * Calling {@link TokenValidator#getSecurityEventCounter()} on the resolved client proxy forces the
     * container to create the producer and run its initialization; any failure there propagates and
     * fails the application start.
     * </p>
     *
     * @param event the Quarkus startup event
     */
    void onStartup(@Observes StartupEvent event) {
        if (!IssuerConfigResolver.hasEnabledIssuers(config)) {
            LOGGER.debug("No enabled issuer configured, leaving the TokenValidator uninitialized at startup");
            return;
        }
        LOGGER.debug("Enabled issuer configured, forcing TokenValidator initialization at startup");
        tokenValidator.get().getSecurityEventCounter();
    }
}
