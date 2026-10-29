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
package de.cuioss.sheriff.token.quarkus.health;

import de.cuioss.sheriff.token.quarkus.observability.ObservedValidatorResolver;
import de.cuioss.tools.logging.CuiLogger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Health check for JWT validation configuration.
 * <p>
 * This class implements the SmallRye Health check interface to provide
 * liveness status for the JWT validation component. It consults
 * {@link ObservedValidatorResolver} to report on the validator actually in use, which may be an
 * externally-produced one rather than the extension's own.
 * </p>
 * <p>
 * Liveness reflects process health only and is therefore always {@code UP} — an unconfigured
 * optional extension is not a dead application, and neither is one whose observed validator cannot
 * be resolved. The {@code status} data key carries the real state:
 * </p>
 * <ul>
 *   <li>{@code configured} — the extension's property-configured validator is in use</li>
 *   <li>{@code observing external validator} — an application-supplied validator is observed</li>
 *   <li>{@code not configured} — no validator is configured</li>
 *   <li>{@code resolution failed} — resolving the observed validator threw; the check still
 *       reports {@code UP} with an {@code issuerCount} of {@code 0}, and no exception detail is put
 *       into the payload</li>
 * </ul>
 * <p>
 * Configuration errors are not reported here: they surface at boot, where the property-configured
 * validator is built eagerly. JWKS reachability is reported on readiness by
 * {@link JwksEndpointHealthCheck}.
 * </p>
 *
 * @since 1.0
 */
@ApplicationScoped
@Liveness
public class TokenValidatorHealthCheck implements HealthCheck {

    private static final CuiLogger LOGGER = new CuiLogger(TokenValidatorHealthCheck.class);

    private static final String HEALTHCHECK_NAME = "jwt-validator";
    static final String STATUS_EXTERNAL_VALIDATOR = "observing external validator";
    static final String STATUS_NOT_CONFIGURED = "not configured";
    static final String STATUS_CONFIGURED = "configured";
    static final String STATUS_RESOLUTION_FAILED = "resolution failed";

    private final ObservedValidatorResolver resolver;

    @Inject
    public TokenValidatorHealthCheck(ObservedValidatorResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public HealthCheckResponse call() {
        int issuerCount;
        String status;
        try {
            issuerCount = resolver.observedIssuerConfigs().size();
            status = statusFor(resolver.outcome());
        }
        // cui-rewrite:disable InvalidExceptionUsageRecipe
        // Intentional catch-all: a liveness probe must never throw, and the resolver's failure
        // sources (CDI resolution, configuration access) are an open set of unchecked exceptions.
        // The failure is reported through a fixed status marker only; its message stays out of the
        // health payload (T-LEAK) and is logged at DEBUG.
        catch (RuntimeException e) {
            LOGGER.debug(e, "Failed to resolve the observed TokenValidator for the liveness check");
            issuerCount = 0;
            status = STATUS_RESOLUTION_FAILED;
        }
        return HealthCheckResponse.named(HEALTHCHECK_NAME)
                .up()
                .withData("issuerCount", issuerCount)
                .withData("status", status)
                .build();
    }

    private static String statusFor(ObservedValidatorResolver.Outcome outcome) {
        return switch (outcome) {
            case PROPERTY_CONFIGURED -> STATUS_CONFIGURED;
            case EXTERNAL_VALIDATOR -> STATUS_EXTERNAL_VALIDATOR;
            case NOT_CONFIGURED -> STATUS_NOT_CONFIGURED;
        };
    }
}
