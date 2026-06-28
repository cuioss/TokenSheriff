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
package de.cuioss.sheriff.token.quarkus.health;

import de.cuioss.sheriff.token.validation.IssuerConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

import java.util.List;

/**
 * Health check for JWT validation configuration.
 * <p>
 * This class implements the SmallRye Health check interface to provide
 * liveness status for the JWT validation component. It only needs access
 * to the issuer configurations to verify they are properly loaded.
 * </p>
 *
 * @since 1.0
 */
@ApplicationScoped
@Liveness
public class TokenValidatorHealthCheck implements HealthCheck {

    private static final String HEALTHCHECK_NAME = "jwt-validator";
    private static final String ERROR_NO_ISSUER_CONFIGS = "No issuer configurations found";
    private static final String ERROR = "error";

    private final List<IssuerConfig> issuerConfigs;

    @Inject
    public TokenValidatorHealthCheck(List<IssuerConfig> issuerConfigs) {
        this.issuerConfigs = issuerConfigs;
    }

    @Override
    public HealthCheckResponse call() {
        if (issuerConfigs.isEmpty()) {
            return createErrorResponse(ERROR_NO_ISSUER_CONFIGS);
        }

        return HealthCheckResponse.named(HEALTHCHECK_NAME)
                .up()
                .withData("issuerCount", issuerConfigs.size())
                .build();
    }

    /**
     * Creates an error response with the given error message.
     *
     * @param errorMessage the error message
     * @return the health check response
     */
    private HealthCheckResponse createErrorResponse(String errorMessage) {
        return HealthCheckResponse.named(HEALTHCHECK_NAME)
                .down()
                .withData(ERROR, errorMessage)
                .build();
    }
}
