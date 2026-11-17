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

import de.cuioss.sheriff.token.commons.error.ErrorTextSanitizer;
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
 * <p>
 * The issuer identifier in that WARN passes through {@link ErrorTextSanitizer} as well. A hostile identifier
 * carrying CR/LF, a control character, a bidirectional control and a non-ASCII letter is reported exactly in
 * its sanitiser rendering, so the test fails when the sanitiser call on the identifier is removed; a benign
 * identifier is reported verbatim.
 */
@EnableTestLogger
@DisplayName("JwksEndpointHealthCheck loader-check failures")
class JwksEndpointHealthCheckIssuerStatusFailureTest {

    private static final String BENIGN_ISSUER = "failing-issuer";
    private static final String HOSTILE_ISSUER = "hostile-issuer\r\nFORGED-ISSUER-LINE\u0007‮issuer-ü";
    /** The raw line break the hostile identifier would inject when written unsanitised. */
    private static final String HOSTILE_ISSUER_RAW_BREAK = "\r\nFORGED-ISSUER-LINE";
    /** {@link ErrorTextSanitizer#sanitize(String)} of {@link #HOSTILE_ISSUER}: escaped controls, letter kept. */
    private static final String SANITISED_HOSTILE_ISSUER =
            "hostile-issuer\\r\\nFORGED-ISSUER-LINE\\u0007\\u202eissuer-ü";
    private static final String RAW_MESSAGE = "loader broke\r\nFORGED-LOG-LINE";
    private static final String ESCAPED_MESSAGE = "loader broke\\r\\nFORGED-LOG-LINE";

    @Nested
    @DisplayName("External validator issuer status view")
    class ExternalIssuerStatusView {

        @Test
        @DisplayName("An IllegalStateException from the view reports DOWN and logs only the sanitised message")
        void shouldSanitiseIllegalStateException() {
            assertSanitisedFailure(this::checkExternal, BENIGN_ISSUER, new IllegalStateException(RAW_MESSAGE));
        }

        @Test
        @DisplayName("An IllegalArgumentException from the view reports DOWN and logs only the sanitised message")
        void shouldSanitiseIllegalArgumentException() {
            assertSanitisedFailure(this::checkExternal, BENIGN_ISSUER, new IllegalArgumentException(RAW_MESSAGE));
        }

        @Test
        @DisplayName("A hostile issuer identifier is logged only in its sanitiser rendering")
        void shouldSanitiseHostileIssuerIdentifier() {
            assertSanitisedFailure(this::checkExternal, HOSTILE_ISSUER, new IllegalStateException(RAW_MESSAGE));
            assertHostileIssuerSanitised();
        }

        @Test
        @DisplayName("control: a benign issuer identifier is logged verbatim")
        void shouldReportBenignIssuerIdentifierVerbatim() {
            assertSanitisedFailure(this::checkExternal, BENIGN_ISSUER, new IllegalStateException(RAW_MESSAGE));
            assertBenignIssuerVerbatim();
        }

        @Test
        @DisplayName("positive control: a view that does not throw reports UP and logs no WARN")
        void shouldReportHealthyViewWithoutWarning() {
            assertHealthyWithoutWarning(checkExternal(new SwitchableJwksLoader(BENIGN_ISSUER, null)));
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
            assertSanitisedFailure(this::checkConfigured, BENIGN_ISSUER, new IllegalStateException(RAW_MESSAGE));
        }

        @Test
        @DisplayName("An IllegalArgumentException from the loader reports DOWN and logs only the sanitised message")
        void shouldSanitiseIllegalArgumentException() {
            assertSanitisedFailure(this::checkConfigured, BENIGN_ISSUER,
                    new IllegalArgumentException(RAW_MESSAGE));
        }

        @Test
        @DisplayName("A hostile issuer identifier is logged only in its sanitiser rendering")
        void shouldSanitiseHostileIssuerIdentifier() {
            assertSanitisedFailure(this::checkConfigured, HOSTILE_ISSUER, new IllegalStateException(RAW_MESSAGE));
            assertHostileIssuerSanitised();
        }

        @Test
        @DisplayName("control: a benign issuer identifier is logged verbatim")
        void shouldReportBenignIssuerIdentifierVerbatim() {
            assertSanitisedFailure(this::checkConfigured, BENIGN_ISSUER, new IllegalStateException(RAW_MESSAGE));
            assertBenignIssuerVerbatim();
        }

        @Test
        @DisplayName("positive control: a loader that does not throw reports UP and logs no WARN")
        void shouldReportHealthyLoaderWithoutWarning() {
            assertHealthyWithoutWarning(checkConfigured(new SwitchableJwksLoader(BENIGN_ISSUER, null)));
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
     * Runs the health check over a loader for {@code issuer} that starts throwing {@code failure} once its
     * issuer is set up, and asserts the ERROR outcome and the sanitised, exception-free WARN.
     */
    private static void assertSanitisedFailure(Function<SwitchableJwksLoader, HealthCheckResponse> check,
            String issuer, RuntimeException failure) {
        HealthCheckResponse response = check.apply(new SwitchableJwksLoader(issuer, failure));

        Map<String, Object> data = response.getData().orElseThrow();
        assertAll("loader-check failure",
                () -> assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus()),
                () -> assertEquals("DOWN", data.get("issuer.0.status")),
                () -> assertEquals("none", data.get("issuer.0.jwksType"), "the catch reports no JWKS type"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, ESCAPED_MESSAGE);
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RAW_MESSAGE);
        var warnings = TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(TestLogLevel.WARN,
                ESCAPED_MESSAGE);
        assertFalse(warnings.isEmpty(), "the sanitised WARN must be recorded");
        assertTrue(warnings.stream().allMatch(warning -> warning.getThrown() == null),
                "the WARN must not attach the exception, whose stack trace prints the raw message");
    }

    /**
     * Asserts that the WARN names the hostile issuer exactly in its {@link ErrorTextSanitizer} rendering and
     * that no WARN carries the raw line break the identifier holds.
     */
    private static void assertHostileIssuerSanitised() {
        assertEquals(SANITISED_HOSTILE_ISSUER, ErrorTextSanitizer.sanitize(HOSTILE_ISSUER),
                "fixture: the expected rendering is the sanitiser's");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, SANITISED_HOSTILE_ISSUER);
        assertTrue(TestLoggerFactory.getTestHandler()
                        .resolveLogMessagesContaining(TestLogLevel.WARN, HOSTILE_ISSUER_RAW_BREAK).isEmpty(),
                "no WARN may carry the raw issuer identifier");
    }

    /**
     * Asserts that a benign issuer identifier, which the sanitiser leaves unchanged, is reported verbatim.
     */
    private static void assertBenignIssuerVerbatim() {
        assertEquals(BENIGN_ISSUER, ErrorTextSanitizer.sanitize(BENIGN_ISSUER),
                "fixture: the sanitiser leaves a benign identifier unchanged");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, BENIGN_ISSUER);
    }

    private static void assertHealthyWithoutWarning(HealthCheckResponse response) {
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals("memory", response.getData().orElseThrow().get("issuer.0.jwksType"));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, "FORGED-LOG-LINE");
    }

    private static IssuerConfig issuerConfig(SwitchableJwksLoader loader) {
        return IssuerConfig.builder()
                .issuerIdentifier(loader.issuer)
                .jwksLoader(loader)
                .audienceValidationDisabled(true)
                .build();
    }

    /**
     * An in-memory loader for one issuer, reporting {@link LoaderStatus#OK} until {@link #startFailing()},
     * after which reading its status throws the given exception, as both health-check catches read the status
     * first. Without a failure it keeps reporting {@link LoaderStatus#OK}.
     */
    private static final class SwitchableJwksLoader implements JwksLoader {

        private final String issuer;
        private final @Nullable RuntimeException failure;
        private volatile boolean failing;

        SwitchableJwksLoader(String issuer, @Nullable RuntimeException failure) {
            this.issuer = issuer;
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
