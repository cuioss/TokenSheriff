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
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.TestLoggerFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import lombok.NonNull;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.easymock.EasyMock.createNiceMock;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the two loader-check catches of {@link JwksEndpointHealthCheck}, the one over a configured issuer and
 * the one over an external validator's issuer status view: for each exception type the catch handles, the
 * issuer is reported {@code DOWN} with JWKS type {@code none}, and the WARN carries the exception message
 * only in its escaped form and attaches no exception, whose stack trace would print the message raw
 * (ADR-0022, CWE-117).
 */
@EnableTestLogger
@DisplayName("JwksEndpointHealthCheck loader-check failures")
class JwksEndpointHealthCheckIssuerStatusFailureTest {

    private static final String ISSUER = "failing-issuer";
    private static final String RAW_MESSAGE = "loader broke\r\nFORGED-LOG-LINE";
    private static final String ESCAPED_MESSAGE = "loader broke\\r\\nFORGED-LOG-LINE";

    @Nested
    @DisplayName("External validator issuer status view")
    class ExternalIssuerStatusView {

        @Test
        @DisplayName("An IllegalStateException from the view reports DOWN and logs only the sanitised message")
        void shouldSanitiseIllegalStateException() {
            assertSanitisedFailure(this::checkExternal, new IllegalStateException(RAW_MESSAGE));
        }

        @Test
        @DisplayName("An IllegalArgumentException from the view reports DOWN and logs only the sanitised message")
        void shouldSanitiseIllegalArgumentException() {
            assertSanitisedFailure(this::checkExternal, new IllegalArgumentException(RAW_MESSAGE));
        }

        @Test
        @DisplayName("positive control: a view that does not throw reports UP and logs no WARN")
        void shouldReportHealthyViewWithoutWarning() {
            assertHealthyWithoutWarning(checkExternal(new SwitchableJwksLoader(null)));
        }

        private HealthCheckResponse checkExternal(SwitchableJwksLoader loader) {
            try (TokenValidator external = TokenValidator.builder().issuerConfig(issuerConfig(loader)).build()) {
                JwksEndpointHealthCheck healthCheck = new JwksEndpointHealthCheck(new ObservedValidatorResolver(
                        ObservedValidatorResolver.Outcome.EXTERNAL_VALIDATOR, external, List.of(), null, true), 30);
                loader.startFailing();
                return healthCheck.call();
            }
        }
    }

    @Nested
    @DisplayName("Configured issuer")
    class ConfiguredIssuer {

        @Test
        @DisplayName("An IllegalStateException from the loader reports DOWN and logs only the sanitised message")
        void shouldSanitiseIllegalStateException() {
            assertSanitisedFailure(this::checkConfigured, new IllegalStateException(RAW_MESSAGE));
        }

        @Test
        @DisplayName("An IllegalArgumentException from the loader reports DOWN and logs only the sanitised message")
        void shouldSanitiseIllegalArgumentException() {
            assertSanitisedFailure(this::checkConfigured, new IllegalArgumentException(RAW_MESSAGE));
        }

        @Test
        @DisplayName("positive control: a loader that does not throw reports UP and logs no WARN")
        void shouldReportHealthyLoaderWithoutWarning() {
            assertHealthyWithoutWarning(checkConfigured(new SwitchableJwksLoader(null)));
        }

        private HealthCheckResponse checkConfigured(SwitchableJwksLoader loader) {
            IssuerConfig issuerConfig = issuerConfig(loader);
            JwksEndpointHealthCheck healthCheck = new JwksEndpointHealthCheck(new ObservedValidatorResolver(
                    ObservedValidatorResolver.Outcome.PROPERTY_CONFIGURED, createNiceMock(TokenValidator.class),
                    List.of(issuerConfig), null), 30);
            loader.startFailing();
            return healthCheck.call();
        }
    }

    // === Fixtures ===

    /**
     * Runs the health check over a loader that starts throwing {@code failure} once its issuer is set up, and
     * asserts the ERROR outcome and the sanitised, exception-free WARN.
     */
    private static void assertSanitisedFailure(Function<SwitchableJwksLoader, HealthCheckResponse> check,
            RuntimeException failure) {
        HealthCheckResponse response = check.apply(new SwitchableJwksLoader(failure));

        Map<String, Object> data = response.getData().orElseThrow();
        assertAll("loader-check failure",
                () -> assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus()),
                () -> assertEquals("DOWN", data.get("issuer.0.status")),
                () -> assertEquals("none", data.get("issuer.0.jwksType"), "the catch reports no JWKS type"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, ESCAPED_MESSAGE);
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, ISSUER);
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RAW_MESSAGE);
        var warnings = TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(TestLogLevel.WARN,
                ESCAPED_MESSAGE);
        assertFalse(warnings.isEmpty(), "the sanitised WARN must be recorded");
        assertTrue(warnings.stream().allMatch(warning -> warning.getThrown() == null),
                "the WARN must not attach the exception, whose stack trace prints the raw message");
    }

    private static void assertHealthyWithoutWarning(HealthCheckResponse response) {
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals("memory", response.getData().orElseThrow().get("issuer.0.jwksType"));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, "FORGED-LOG-LINE");
    }

    private static IssuerConfig issuerConfig(JwksLoader loader) {
        return IssuerConfig.builder()
                .issuerIdentifier(ISSUER)
                .jwksLoader(loader)
                .audienceValidationDisabled(true)
                .build();
    }

    /**
     * An in-memory loader reporting {@link LoaderStatus#OK} until {@link #startFailing()}, after which reading
     * its status throws the given exception, as both health-check catches read the status first. Without a
     * failure it keeps reporting {@link LoaderStatus#OK}.
     */
    private static final class SwitchableJwksLoader implements JwksLoader {

        private final @Nullable RuntimeException failure;
        private volatile boolean failing;

        SwitchableJwksLoader(@Nullable RuntimeException failure) {
            this.failure = failure;
        }

        void startFailing() {
            failing = true;
        }

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
            return CompletableFuture.completedFuture(LoaderStatus.OK);
        }

        @Override
        public LoaderStatus getLoaderStatus() {
            RuntimeException toThrow = failure;
            if (failing && toThrow != null) {
                throw toThrow;
            }
            return LoaderStatus.OK;
        }
    }
}
