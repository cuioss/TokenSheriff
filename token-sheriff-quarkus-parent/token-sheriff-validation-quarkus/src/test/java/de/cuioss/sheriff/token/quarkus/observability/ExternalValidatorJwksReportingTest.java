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
package de.cuioss.sheriff.token.quarkus.observability;

import de.cuioss.sheriff.token.quarkus.config.JwtPropertyKeys;
import de.cuioss.sheriff.token.quarkus.health.JwksEndpointHealthCheck;
import de.cuioss.sheriff.token.quarkus.observability.ForeignValidatorObservabilityTest.ForeignValidatorProducer;
import de.cuioss.sheriff.token.quarkus.observability.ForeignValidatorObservabilityTest.GatewayValidator;
import de.cuioss.sheriff.token.validation.IssuerStatusView;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Container-level test of the opt-in external-validator reporting of the JWKS readiness probe: the
 * {@code sheriff.token.issuers.*} namespace is empty, the application supplies its own
 * differently-qualified {@link TokenValidator}, and
 * {@value JwtPropertyKeys.HEALTH.JWKS#REPORT_EXTERNAL_VALIDATOR} is set.
 * <p>
 * It mirrors {@link ForeignValidatorObservabilityTest}, which pins the default behaviour — readiness
 * {@code UP} with {@code checkedEndpoints=0} — and reuses its foreign validator producer. Here the
 * container's own readiness check must report every enabled issuer of the foreign validator through
 * its read-only issuer status views.
 */
@QuarkusTest
@Tag("quarkus-boot")
@TestProfile(ExternalValidatorJwksReportingTest.ReportingProfile.class)
@EnableTestLogger
@DisplayName("Opt-in JWKS readiness reporting for a foreign TokenValidator")
class ExternalValidatorJwksReportingTest {

    /**
     * Supplies no {@code sheriff.token.issuers.*} property, enables the foreign producer and sets the
     * reporting opt-in.
     */
    public static class ReportingProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(JwtPropertyKeys.HEALTH.JWKS.REPORT_EXTERNAL_VALIDATOR, "true");
        }

        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(ForeignValidatorProducer.class);
        }
    }

    @Inject
    ObservedValidatorResolver resolver;

    @Inject
    @GatewayValidator
    TokenValidator foreignValidator;

    @Inject
    @Readiness
    JwksEndpointHealthCheck readinessCheck;

    @Test
    @DisplayName("the resolver reports the foreign validator's issuer statuses")
    void resolverReportsForeignIssuerStatuses() {
        List<IssuerStatusView> expected = foreignValidator.getIssuerStatuses();

        assertAll("opted-in resolution",
                () -> assertEquals(ObservedValidatorResolver.Outcome.EXTERNAL_VALIDATOR, resolver.outcome(),
                        "An empty namespace plus one foreign validator must resolve to EXTERNAL_VALIDATOR"),
                () -> assertFalse(expected.isEmpty(), "the foreign validator has an enabled issuer"),
                () -> assertEquals(expected, resolver.observedIssuerStatuses(),
                        "the opt-in must report the foreign validator's own issuer status views"),
                () -> assertTrue(resolver.observedIssuerConfigs().isEmpty(),
                        "A foreign validator still exposes no issuer configurations"));
    }

    @Test
    @DisplayName("readiness reports every issuer of the foreign validator")
    void readinessReportsForeignIssuers() {
        IssuerStatusView issuer = foreignValidator.getIssuerStatuses().getFirst();

        HealthCheckResponse response = readinessCheck.call();

        Map<String, Object> data = response.getData().orElseThrow();
        assertAll("opted-in readiness",
                () -> assertEquals(HealthCheckResponse.Status.UP, response.getStatus(),
                        "A foreign validator with a ready issuer is UP"),
                () -> assertEquals(foreignValidator.getIssuerStatuses().size(),
                        ((Number) data.get("checkedEndpoints")).intValue(),
                        "every enabled issuer of the foreign validator must be checked"),
                () -> assertEquals("READY", data.get("readiness")),
                () -> assertEquals("COMPLETE", data.get("loading")),
                () -> assertEquals(issuer.issuerIdentifier(), data.get("issuer.0.url"),
                        "the issuer identifier is disclosed by default"),
                () -> assertEquals(issuer.jwksType().toString(), data.get("issuer.0.jwksType")),
                () -> assertEquals("UP", data.get("issuer.0.status")));
    }
}
