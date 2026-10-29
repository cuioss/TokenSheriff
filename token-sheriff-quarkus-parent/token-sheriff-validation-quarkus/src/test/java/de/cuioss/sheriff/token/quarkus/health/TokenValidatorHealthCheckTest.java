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

import de.cuioss.sheriff.token.quarkus.config.JwtTestProfile;
import de.cuioss.sheriff.token.quarkus.observability.ObservedValidatorResolver;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.easymock.EasyMock.createNiceMock;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@Tag("quarkus-boot")
@TestProfile(JwtTestProfile.class)
@EnableTestLogger
@DisplayName("TokenValidatorHealthCheck")
class TokenValidatorHealthCheckTest {

    @Inject
    @Liveness
    TokenValidatorHealthCheck healthCheck;


    @Nested
    @DisplayName("Health Check Response")
    class HealthCheckResponseTests {

        @Test
        @DisplayName("should return UP status with valid configuration")
        void healthCheckShouldReturnUpStatus() {
            HealthCheckResponse response = healthCheck.call();
            assertNotNull(response, "HealthCheckResponse should not be null");
            assertEquals(HealthCheckResponse.Status.UP, response.getStatus(),
                    "Health check status should be UP with valid configuration");
        }

        @Test
        @DisplayName("should have correct name")
        void healthCheckName() {
            HealthCheckResponse response = healthCheck.call();
            assertEquals("jwt-validator", response.getName(),
                    "Health check should have correct name");
        }

        @Test
        @DisplayName("should include correct data for UP status")
        void healthCheckDataForUpStatus() {
            HealthCheckResponse response = healthCheck.call();

            // Verify response has data
            assertTrue(response.getData().isPresent(),
                    "Health check data should be present for UP status");

            Map<String, Object> data = response.getData().get();

            // UP status should have issuer count
            assertTrue(data.containsKey("issuerCount"),
                    "Health check data should contain issuerCount when UP");

            Object issuerCountValue = data.get("issuerCount");
            assertNotNull(issuerCountValue, "issuerCount should not be null");

            assertInstanceOf(Number.class, issuerCountValue,
                    "issuerCount should be a Number, but was: " + issuerCountValue.getClass().getSimpleName());

            int issuerCount = ((Number) issuerCountValue).intValue();
            assertTrue(issuerCount > 0,
                    "issuerCount should be greater than 0 when UP, but was: " + issuerCount);

            assertEquals("configured", data.get("status"),
                    "status should report the property-configured outcome");
        }

        @Test
        @DisplayName("should maintain response structure consistency between calls")
        void shouldTestHealthCheckResponseStructure() {
            HealthCheckResponse response1 = healthCheck.call();
            HealthCheckResponse response2 = healthCheck.call();

            assertEquals(response1.getName(), response2.getName(),
                    "Health check name should be consistent");
            assertEquals("jwt-validator", response1.getName(),
                    "Health check should have correct name");

            assertTrue(response1.getData().isPresent(), "First response should have data");
            assertTrue(response2.getData().isPresent(), "Second response should have data");

            assertEquals(HealthCheckResponse.Status.UP, response1.getStatus(),
                    "First response status should be UP with valid configuration");
            assertEquals(HealthCheckResponse.Status.UP, response2.getStatus(),
                    "Second response status should be UP with valid configuration");

            assertEquals(response1.getStatus(), response2.getStatus(),
                    "Response status should be consistent between calls");
        }
    }

    @Nested
    @DisplayName("Resolution Outcome Matrix")
    class ResolutionOutcomeMatrix {

        @Test
        @DisplayName("should report UP with the external-validator marker when a foreign validator is observed")
        void shouldReportExternalValidator() {
            TokenValidatorHealthCheck check = new TokenValidatorHealthCheck(new ObservedValidatorResolver(
                    ObservedValidatorResolver.Outcome.EXTERNAL_VALIDATOR,
                    createNiceMock(TokenValidator.class), List.of(), null));

            HealthCheckResponse response = check.call();

            Map<String, Object> data = response.getData().orElseThrow();
            assertAll("external validator liveness",
                    () -> assertEquals(HealthCheckResponse.Status.UP, response.getStatus(),
                            "Liveness must stay UP when an external validator is in use"),
                    () -> assertEquals("observing external validator", data.get("status"),
                            "status must name the external-validator outcome"),
                    () -> assertEquals(0, ((Number) data.get("issuerCount")).intValue(),
                            "A foreign validator exposes no issuer configurations"));
        }

        @Test
        @DisplayName("should report UP with the not-configured marker when nothing is observable")
        void shouldReportNotConfigured() {
            TokenValidatorHealthCheck check = new TokenValidatorHealthCheck(new ObservedValidatorResolver(
                    ObservedValidatorResolver.Outcome.NOT_CONFIGURED, null, List.of(), null));

            HealthCheckResponse response = check.call();

            Map<String, Object> data = response.getData().orElseThrow();
            assertAll("unconfigured liveness",
                    () -> assertEquals(HealthCheckResponse.Status.UP, response.getStatus(),
                            "An unconfigured optional extension is not a dead application"),
                    () -> assertEquals("not configured", data.get("status"),
                            "status must name the not-configured outcome"),
                    () -> assertEquals(0, ((Number) data.get("issuerCount")).intValue(),
                            "No issuer configuration is observable"));
        }

        @Test
        @DisplayName("should report UP with the resolution-failed marker when the resolver throws")
        void shouldReportResolutionFailed() {
            TokenValidatorHealthCheck check = new TokenValidatorHealthCheck(new FailingResolver());

            HealthCheckResponse response = assertDoesNotThrow(check::call,
                    "A failing resolver must not make the liveness probe throw");

            Map<String, Object> data = response.getData().orElseThrow();
            assertAll("resolution-failed liveness",
                    () -> assertEquals(HealthCheckResponse.Status.UP, response.getStatus(),
                            "Liveness reflects process health only and must stay UP"),
                    () -> assertEquals(TokenValidatorHealthCheck.STATUS_RESOLUTION_FAILED, data.get("status"),
                            "status must name the resolution-failed outcome"),
                    () -> assertEquals(0, ((Number) data.get("issuerCount")).intValue(),
                            "No issuer configuration is observable when resolution fails"),
                    () -> assertTrue(data.values().stream()
                                    .noneMatch(value -> String.valueOf(value).contains(FailingResolver.FAILURE_MESSAGE)),
                            "The exception message must not leak into the health payload"));
        }

        @Test
        @DisplayName("should report UP with the resolution-failed marker when only the outcome lookup throws")
        void shouldReportResolutionFailedWhenOutcomeThrows() {
            TokenValidatorHealthCheck check = new TokenValidatorHealthCheck(new OutcomeFailingResolver());

            HealthCheckResponse response = assertDoesNotThrow(check::call,
                    "A failing outcome lookup must not make the liveness probe throw");

            Map<String, Object> data = response.getData().orElseThrow();
            assertAll("outcome-failure liveness",
                    () -> assertEquals(HealthCheckResponse.Status.UP, response.getStatus(),
                            "Liveness reflects process health only and must stay UP"),
                    () -> assertEquals(TokenValidatorHealthCheck.STATUS_RESOLUTION_FAILED, data.get("status"),
                            "status must name the resolution-failed outcome"),
                    () -> assertEquals(0, ((Number) data.get("issuerCount")).intValue(),
                            "The issuer count read before the failure must be discarded"),
                    () -> assertTrue(data.values().stream()
                                    .noneMatch(value -> String.valueOf(value).contains(FailingResolver.FAILURE_MESSAGE)),
                            "The exception message must not leak into the health payload"));
        }
    }

    /**
     * Resolver whose issuer configurations resolve normally but whose outcome lookup fails, so the
     * failure surfaces only after a non-zero issuer count has already been read.
     */
    private static final class OutcomeFailingResolver extends ObservedValidatorResolver {

        /** Two placeholder entries: only the size is read by the health check. */
        private static final List<IssuerConfig> TWO_ISSUERS = Collections.nCopies(2, null);

        OutcomeFailingResolver() {
            super(Outcome.NOT_CONFIGURED, null, List.of(), null);
        }

        @Override
        public Outcome outcome() {
            throw new IllegalStateException(FailingResolver.FAILURE_MESSAGE);
        }

        @Override
        public List<IssuerConfig> observedIssuerConfigs() {
            return TWO_ISSUERS;
        }
    }

    /**
     * Resolver whose observation accessors fail, standing in for a CDI or configuration failure while
     * resolving the observed validator.
     */
    private static final class FailingResolver extends ObservedValidatorResolver {

        static final String FAILURE_MESSAGE = "secret-resolution-detail";

        FailingResolver() {
            super(Outcome.NOT_CONFIGURED, null, List.of(), null);
        }

        @Override
        public Outcome outcome() {
            throw new IllegalStateException(FAILURE_MESSAGE);
        }

        @Override
        public List<IssuerConfig> observedIssuerConfigs() {
            throw new IllegalStateException(FAILURE_MESSAGE);
        }
    }
}
