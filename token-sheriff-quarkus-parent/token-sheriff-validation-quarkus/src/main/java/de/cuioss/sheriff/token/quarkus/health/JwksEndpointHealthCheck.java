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

import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.quarkus.config.JwtPropertyKeys;
import de.cuioss.sheriff.token.quarkus.observability.ObservedValidatorResolver;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.IssuerStatusView;
import de.cuioss.sheriff.token.validation.jwks.JwksLoader;
import de.cuioss.tools.logging.CuiLogger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static de.cuioss.sheriff.token.quarkus.TokenSheriffQuarkusLogMessages.WARN;

/**
 * Health check for JWKS endpoint connectivity.
 * <p>
 * This class implements the SmallRye Health check interface to provide
 * readiness status for JWT validation JWKS endpoints. It consults
 * {@link ObservedValidatorResolver} for the issuer configurations of the validator actually in use
 * and checks the health of their JWKS loaders.
 * </p>
 * <p>
 * When no issuer configuration is observable — an externally-produced validator is in use, or
 * nothing is configured at all — the check short-circuits to {@code UP} with
 * {@code checkedEndpoints=0}: an unconfigured optional extension is no reason to hold the whole
 * application out of the load balancer. The {@code readiness} data key carries the real state.
 * </p>
 * <p>
 * With the opt-in {@value JwtPropertyKeys.HEALTH.JWKS#REPORT_EXTERNAL_VALIDATOR}, the issuers of an
 * externally produced validator are reported through its read-only, non-fetching issuer status views,
 * with the same per-issuer keys and the same readiness, loading and {@code DOWN} logic as configured
 * issuers.
 * </p>
 * <p>
 * {@value JwtPropertyKeys.HEALTH.JWKS#DISCLOSE_ISSUER}{@code =false} omits the
 * {@code issuer.N.url} entry of every reported issuer, configured or external, so the payload
 * carries only the per-issuer JWKS type and status, the counts and the readiness verdict.
 * </p>
 *
 * @since 1.0
 */
@ApplicationScoped
@Readiness
public class JwksEndpointHealthCheck implements HealthCheck {

    private static final CuiLogger LOGGER = new CuiLogger(JwksEndpointHealthCheck.class);
    private static final String DEFAULT_CACHE_SECONDS = "30";
    private static final String HEALTHCHECK_NAME = "jwks-endpoints";
    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";

    private static final String DEFAULT_DISCLOSE_ISSUER = "true";

    private final ObservedValidatorResolver resolver;
    private final ConcurrentHashMap<String, CachedResponse> healthCheckCache = new ConcurrentHashMap<>();
    private final long cacheTimeoutMillis;
    private final boolean discloseIssuer;

    /**
     * Creates the health check with the default {@value JwtPropertyKeys.HEALTH.JWKS#DISCLOSE_ISSUER}
     * of {@code true}.
     *
     * @param resolver     the resolver naming the observed validator
     * @param cacheSeconds the cache time-to-live in seconds for health check results
     */
    public JwksEndpointHealthCheck(ObservedValidatorResolver resolver, int cacheSeconds) {
        this(resolver, cacheSeconds, true);
    }

    /**
     * CDI constructor.
     *
     * @param resolver       the resolver naming the observed validator
     * @param cacheSeconds   the cache time-to-live in seconds for health check results
     * @param discloseIssuer whether every reported issuer, configured or external, is reported with
     *                       its identifier as {@code issuer.N.url}
     */
    @Inject
    public JwksEndpointHealthCheck(ObservedValidatorResolver resolver,
            @ConfigProperty(name = JwtPropertyKeys.HEALTH.JWKS.CACHE_SECONDS, defaultValue = DEFAULT_CACHE_SECONDS) int cacheSeconds,
            @ConfigProperty(name = JwtPropertyKeys.HEALTH.JWKS.DISCLOSE_ISSUER, defaultValue = DEFAULT_DISCLOSE_ISSUER) boolean discloseIssuer) {
        this.resolver = resolver;
        this.cacheTimeoutMillis = TimeUnit.SECONDS.toMillis(cacheSeconds);
        this.discloseIssuer = discloseIssuer;
    }

    @Override
    public HealthCheckResponse call() {
        // Use cache to prevent excessive network calls
        CachedResponse cached = healthCheckCache.get(HEALTHCHECK_NAME);
        if (cached != null && !cached.isExpired()) {
            return cached.response;
        }

        HealthCheckResponse response = performHealthCheck();
        healthCheckCache.put(HEALTHCHECK_NAME, new CachedResponse(response, System.currentTimeMillis() + cacheTimeoutMillis));
        return response;
    }

    /**
     * Performs the actual health check without caching.
     *
     * @return the health check response
     */
    private HealthCheckResponse performHealthCheck() {
        var responseBuilder = HealthCheckResponse.named(HEALTHCHECK_NAME).up();

        List<EndpointResult> results;
        List<IssuerConfig> issuerConfigs = resolver.observedIssuerConfigs();
        if (!issuerConfigs.isEmpty()) {
            results = issuerConfigs.stream()
                    .map(issuerConfig -> EndpointResult.fromIssuerConfig(issuerConfig.getIssuerIdentifier(), issuerConfig))
                    .toList();
        } else {
            List<IssuerStatusView> issuerStatuses = resolver.observedIssuerStatuses();
            if (issuerStatuses.isEmpty()) {
                return responseBuilder
                        .withData("checkedEndpoints", 0)
                        .withData("readiness", resolver.outcome().name())
                        .withData("loading", "COMPLETE")
                        .build();
            }
            results = issuerStatuses.stream()
                    .map(EndpointResult::fromIssuerStatus)
                    .toList();
        }

        // Add all endpoint data to response
        for (int i = 0; i < results.size(); i++) {
            results.get(i).addToResponse(responseBuilder, "issuer." + i + ".", discloseIssuer);
        }

        // Calculate overall readiness and loading status
        boolean allReady = results.stream().allMatch(EndpointResult::isHealthy);
        boolean anyLoading = results.stream().anyMatch(r -> r.status() == LoaderStatus.UNDEFINED);

        // Set overall health status with enhanced readiness reporting
        responseBuilder.withData("checkedEndpoints", results.size());

        if (!allReady) {
            responseBuilder.down();
        }

        // Add enhanced readiness reporting
        responseBuilder
                .withData("readiness", allReady ? "READY" : "NOT_READY")
                .withData("loading", anyLoading ? "IN_PROGRESS" : "COMPLETE");

        return responseBuilder.build();
    }

    private record CachedResponse(HealthCheckResponse response, long expiryTime) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    private record EndpointResult(String issuer, String jwksType, LoaderStatus status) {

        /**
         * Creates an EndpointResult from an issuer configuration.
         *
         * @param issuer       the issuer name
         * @param issuerConfig the issuer configuration
         * @return the endpoint result
         */
        static EndpointResult fromIssuerConfig(String issuer, IssuerConfig issuerConfig) {
            try {
                JwksLoader jwksLoader = issuerConfig.getJwksLoader();

                // Handle null loader (issuer disabled or not initialized)
                if (jwksLoader == null) {
                    LoaderStatus status = LoaderStatus.UNDEFINED;
                    LOGGER.debug("JWKS loader status for issuer %s: %s (loader not initialized)", issuer, status);
                    return new EndpointResult(issuer, "none", status);
                }

                LoaderStatus status = jwksLoader.getLoaderStatus();
                LOGGER.debug("JWKS loader status for issuer %s: %s", issuer, status);

                return new EndpointResult(issuer, jwksLoader.getJwksType().toString(), status);
            } catch (IllegalStateException | IllegalArgumentException e) {
                LOGGER.warn(e, WARN.ERROR_CHECKING_JWKS_LOADER, issuer, e.getMessage());
                return new EndpointResult(issuer, "none", LoaderStatus.ERROR);
            }
        }

        /**
         * Creates an EndpointResult from the read-only status view of an external validator's issuer.
         * Reading the view fetches no keys.
         *
         * @param view the issuer status view
         * @return the endpoint result
         */
        static EndpointResult fromIssuerStatus(IssuerStatusView view) {
            String issuer = view.issuerIdentifier();
            try {
                LoaderStatus status = view.loaderStatus();
                LOGGER.debug("JWKS loader status for issuer %s: %s", issuer, status);
                return new EndpointResult(issuer, view.jwksType().toString(), status);
            } catch (IllegalStateException | IllegalArgumentException e) {
                LOGGER.warn(e, WARN.ERROR_CHECKING_JWKS_LOADER, issuer, e.getMessage());
                return new EndpointResult(issuer, "none", LoaderStatus.ERROR);
            }
        }

        /**
         * Adds this endpoint's data to the health check response builder.
         *
         * @param responseBuilder the response builder
         * @param prefix          the prefix for the data keys
         * @param includeUrl      whether the issuer identifier is added as {@code url}
         */
        void addToResponse(HealthCheckResponseBuilder responseBuilder, String prefix, boolean includeUrl) {
            boolean up = status == LoaderStatus.OK;
            if (includeUrl) {
                responseBuilder.withData(prefix + "url", issuer);
            }
            responseBuilder.withData(prefix + "jwksType", jwksType);
            responseBuilder.withData(prefix + "status", up ? STATUS_UP : STATUS_DOWN);
        }

        /**
         * Checks if this endpoint is healthy.
         *
         * @return true if the status is OK, false otherwise
         */
        boolean isHealthy() {
            return status == LoaderStatus.OK;
        }
    }
}
