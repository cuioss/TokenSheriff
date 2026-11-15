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

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.JwksType;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.quarkus.observability.ObservedValidatorResolver;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.jwks.JwksLoader;
import de.cuioss.sheriff.token.validation.jwks.key.KeyInfo;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import lombok.NonNull;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.easymock.EasyMock.createNiceMock;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link JwksEndpointHealthCheck} for the outcomes without configured issuers: an external validator,
 * with and without the reporting opt-in, and nothing configured at all. Every check is built directly over an
 * {@link ObservedValidatorResolver}, so no Quarkus application is booted.
 * <p>
 * Split from {@code JwksEndpointHealthCheckTest}, which keeps the configured-issuer tests.
 */
@EnableTestLogger
@DisplayName("JwksEndpointHealthCheck with an external or no validator")
class JwksEndpointHealthCheckExternalValidatorTest {

    @Test
    @DisplayName("Health check should report UP with zero endpoints when an external validator is observed")
    void healthCheckExternalValidator() {
        JwksEndpointHealthCheck externalHealthCheck = new JwksEndpointHealthCheck(
                new ObservedValidatorResolver(ObservedValidatorResolver.Outcome.EXTERNAL_VALIDATOR,
                        createNiceMock(TokenValidator.class), List.of(), null),
                30);

        HealthCheckResponse response = externalHealthCheck.call();

        Map<String, Object> data = response.getData().orElseThrow();
        assertAll("external validator readiness",
                () -> assertEquals(HealthCheckResponse.Status.UP, response.getStatus(),
                        "Readiness must stay UP when an external validator is in use"),
                () -> assertEquals(0, ((Number) data.get("checkedEndpoints")).intValue(),
                        "A foreign validator exposes no JWKS endpoints to probe"),
                () -> assertEquals("EXTERNAL_VALIDATOR", data.get("readiness"),
                        "readiness must name the external-validator outcome"),
                () -> assertFalse(data.containsKey("issuer.0.url"),
                        "No per-issuer data may be emitted without observable issuers"));
    }

    @Test
    @DisplayName("Health check should report UP with zero endpoints when nothing is configured")
    void healthCheckNotConfigured() {
        JwksEndpointHealthCheck unconfiguredHealthCheck = new JwksEndpointHealthCheck(
                new ObservedValidatorResolver(ObservedValidatorResolver.Outcome.NOT_CONFIGURED,
                        null, List.of(), null),
                30);

        HealthCheckResponse response = unconfiguredHealthCheck.call();

        Map<String, Object> data = response.getData().orElseThrow();
        assertAll("unconfigured readiness",
                () -> assertEquals(HealthCheckResponse.Status.UP, response.getStatus(),
                        "An unconfigured optional extension must not hold the application out of the load balancer"),
                () -> assertEquals(0, ((Number) data.get("checkedEndpoints")).intValue(),
                        "Nothing is observable, so nothing is probed"),
                () -> assertEquals("NOT_CONFIGURED", data.get("readiness"),
                        "readiness must name the not-configured outcome"));
    }

    @Test
    @DisplayName("Opted-in external validator with ready issuers reports UP with per-issuer data")
    void optedInExternalValidatorUp() {
        try (TokenValidator external = externalValidator(
                     new IssuerSpec("ext-issuer-a", LoaderStatus.OK), new IssuerSpec("ext-issuer-b", LoaderStatus.OK))) {
            HealthCheckResponse response = new JwksEndpointHealthCheck(reportingExternal(external), 30).call();

            Map<String, Object> data = response.getData().orElseThrow();
            assertAll("opted-in external readiness, all ready",
                    () -> assertEquals(HealthCheckResponse.Status.UP, response.getStatus()),
                    () -> assertEquals(2, ((Number) data.get("checkedEndpoints")).intValue()),
                    () -> assertEquals("READY", data.get("readiness")),
                    () -> assertEquals("COMPLETE", data.get("loading")),
                    () -> assertEquals("ext-issuer-a", data.get("issuer.0.url")),
                    () -> assertEquals("memory", data.get("issuer.0.jwksType")),
                    () -> assertEquals("UP", data.get("issuer.0.status")),
                    () -> assertEquals("ext-issuer-b", data.get("issuer.1.url")),
                    () -> assertEquals("UP", data.get("issuer.1.status")));
        }
    }

    @Test
    @DisplayName("Opted-in external validator with a failed issuer reports DOWN")
    void optedInExternalValidatorDown() {
        try (TokenValidator external = externalValidator(
                     new IssuerSpec("ext-healthy", LoaderStatus.OK), new IssuerSpec("ext-failed", LoaderStatus.ERROR))) {
            HealthCheckResponse response = new JwksEndpointHealthCheck(reportingExternal(external), 30).call();

            Map<String, Object> data = response.getData().orElseThrow();
            assertAll("opted-in external readiness, one failed",
                    () -> assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus()),
                    () -> assertEquals(2, ((Number) data.get("checkedEndpoints")).intValue()),
                    () -> assertEquals("NOT_READY", data.get("readiness")),
                    () -> assertEquals("COMPLETE", data.get("loading")),
                    () -> assertEquals("UP", data.get("issuer.0.status")),
                    () -> assertEquals("ext-failed", data.get("issuer.1.url")),
                    () -> assertEquals("DOWN", data.get("issuer.1.status")));
        }
    }

    @Test
    @DisplayName("Opted-in external validator with a still-loading issuer reports DOWN and IN_PROGRESS")
    void optedInExternalValidatorInProgress() {
        try (TokenValidator external = externalValidator(new IssuerSpec("ext-loading", LoaderStatus.UNDEFINED))) {
            HealthCheckResponse response = new JwksEndpointHealthCheck(reportingExternal(external), 30).call();

            Map<String, Object> data = response.getData().orElseThrow();
            assertAll("opted-in external readiness, still loading",
                    () -> assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus()),
                    () -> assertEquals(1, ((Number) data.get("checkedEndpoints")).intValue()),
                    () -> assertEquals("NOT_READY", data.get("readiness")),
                    () -> assertEquals("IN_PROGRESS", data.get("loading")),
                    () -> assertEquals("DOWN", data.get("issuer.0.status")));
        }
    }

    @Test
    @DisplayName("disclose-issuer=false omits issuer.N.url for an external validator, the default discloses it")
    void nonDisclosingPayloadForExternalValidator() {
        try (TokenValidator external = externalValidator(new IssuerSpec("ext-secret", LoaderStatus.OK))) {
            HealthCheckResponse nonDisclosing =
                    new JwksEndpointHealthCheck(reportingExternal(external), 30, false).call();
            HealthCheckResponse disclosing =
                    new JwksEndpointHealthCheck(reportingExternal(external), 30, true).call();

            Map<String, Object> nonDisclosingData = nonDisclosing.getData().orElseThrow();
            Map<String, Object> disclosingData = disclosing.getData().orElseThrow();
            assertAll("non-disclosing external payload",
                    () -> assertEquals(HealthCheckResponse.Status.UP, nonDisclosing.getStatus()),
                    () -> assertFalse(nonDisclosingData.containsKey("issuer.0.url"),
                            "the external issuer identifier must not be disclosed"),
                    () -> assertFalse(nonDisclosingData.containsValue("ext-secret"),
                            "the external issuer identifier must appear nowhere in the payload"),
                    () -> assertEquals("memory", nonDisclosingData.get("issuer.0.jwksType")),
                    () -> assertEquals("UP", nonDisclosingData.get("issuer.0.status")),
                    () -> assertEquals(1, ((Number) nonDisclosingData.get("checkedEndpoints")).intValue()),
                    () -> assertEquals("READY", nonDisclosingData.get("readiness")),
                    () -> assertEquals("COMPLETE", nonDisclosingData.get("loading")),
                    () -> assertEquals("ext-secret", disclosingData.get("issuer.0.url"),
                            "positive control: the default discloses the external issuer identifier"));
        }
    }

    @Test
    @DisplayName("An external validator without the opt-in, and NOT_CONFIGURED even with it, report zero endpoints")
    void zeroEndpointsWithoutObservableIssuerStatuses() {
        try (TokenValidator external = externalValidator(new IssuerSpec("ext-issuer", LoaderStatus.OK))) {
            HealthCheckResponse notOptedIn = new JwksEndpointHealthCheck(
                    new ObservedValidatorResolver(ObservedValidatorResolver.Outcome.EXTERNAL_VALIDATOR,
                            external, List.of(), null),
                    30, false).call();
            HealthCheckResponse notConfigured = new JwksEndpointHealthCheck(
                    new ObservedValidatorResolver(ObservedValidatorResolver.Outcome.NOT_CONFIGURED,
                            null, List.of(), null, true),
                    30).call();

            assertAll("zero endpoints",
                    () -> assertEquals(HealthCheckResponse.Status.UP, notOptedIn.getStatus()),
                    () -> assertEquals(0, ((Number) notOptedIn.getData().orElseThrow().get("checkedEndpoints")).intValue()),
                    () -> assertEquals("EXTERNAL_VALIDATOR", notOptedIn.getData().orElseThrow().get("readiness")),
                    () -> assertEquals(HealthCheckResponse.Status.UP, notConfigured.getStatus()),
                    () -> assertEquals(0, ((Number) notConfigured.getData().orElseThrow().get("checkedEndpoints")).intValue()),
                    () -> assertEquals("NOT_CONFIGURED", notConfigured.getData().orElseThrow().get("readiness")));
        }
    }

    @Test
    @DisplayName("The opt-in leaves the PROPERTY_CONFIGURED payload unchanged")
    void optInLeavesPropertyConfiguredPayloadUnchanged() {
        IssuerConfig healthy = configuredIssuer("healthy-issuer", LoaderStatus.OK);
        IssuerConfig unhealthy = configuredIssuer("unhealthy-issuer", LoaderStatus.ERROR);

        HealthCheckResponse byDefault = new JwksEndpointHealthCheck(
                new ObservedValidatorResolver(ObservedValidatorResolver.Outcome.PROPERTY_CONFIGURED,
                        createNiceMock(TokenValidator.class), List.of(healthy, unhealthy), null),
                30).call();
        HealthCheckResponse optedIn = new JwksEndpointHealthCheck(
                new ObservedValidatorResolver(ObservedValidatorResolver.Outcome.PROPERTY_CONFIGURED,
                        createNiceMock(TokenValidator.class), List.of(healthy, unhealthy), null, true),
                30).call();

        assertEquals(byDefault.getStatus(), optedIn.getStatus());
        assertEquals(byDefault.getData().orElseThrow(), optedIn.getData().orElseThrow(),
                "the opt-in must not change the payload of configured issuers");
    }

    /**
     * Pins a resolver to the external-validator outcome over the given validator with the reporting opt-in set.
     */
    private static ObservedValidatorResolver reportingExternal(TokenValidator external) {
        return new ObservedValidatorResolver(ObservedValidatorResolver.Outcome.EXTERNAL_VALIDATOR,
                external, List.of(), null, true);
    }

    /**
     * Builds a real validator, standing in for an embedder's own, over in-memory issuers with fixed loader statuses.
     */
    private static TokenValidator externalValidator(IssuerSpec... issuers) {
        TokenValidator.TokenValidatorBuilder builder = TokenValidator.builder();
        for (IssuerSpec issuer : issuers) {
            builder.issuerConfig(configuredIssuer(issuer.identifier(), issuer.status()));
        }
        return builder.build();
    }

    private static IssuerConfig configuredIssuer(String identifier, LoaderStatus status) {
        return IssuerConfig.builder()
                .issuerIdentifier(identifier)
                .jwksLoader(new FixedStatusJwksLoader(status))
                .audienceValidationDisabled(true)
                .build();
    }

    private record IssuerSpec(String identifier, LoaderStatus status) {
    }

    /**
     * In-memory JwksLoader reporting a fixed status.
     */
    private record FixedStatusJwksLoader(LoaderStatus status) implements JwksLoader {

        @Override
        public Optional<KeyInfo> getKeyInfo(String kid) {
            return Optional.empty();
        }

        @Override
        public Set<String> getKeyIds() {
            return Set.of();
        }

        @Override
        public JwksType getJwksType() {
            return JwksType.MEMORY;
        }

        @Override
        public Optional<String> getIssuerIdentifier() {
            return Optional.empty();
        }

        @Override
        public CompletableFuture<LoaderStatus> initJWKSLoader(@NonNull SecurityEventCounter securityEventCounter) {
            return CompletableFuture.completedFuture(status);
        }

        @Override
        public LoaderStatus getLoaderStatus() {
            return status;
        }
    }
}
