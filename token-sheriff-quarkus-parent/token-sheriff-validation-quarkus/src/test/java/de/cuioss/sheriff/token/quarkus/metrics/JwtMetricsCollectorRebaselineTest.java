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
package de.cuioss.sheriff.token.quarkus.metrics;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.metrics.MetricIdentifier;
import de.cuioss.sheriff.token.quarkus.observability.ObservedValidatorResolver;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plain unit tests for the delta/re-baseline logic of {@link JwtMetricsCollector},
 * independent of the Quarkus container: pre-init events are exported, positive
 * deltas increment Micrometer counters, and an external reset re-baselines
 * instead of silently under-reporting.
 */
@EnableTestLogger
class JwtMetricsCollectorRebaselineTest {

    private static final SecurityEventCounter.EventType EVENT =
            SecurityEventCounter.EventType.ACCESS_TOKEN_CREATED;

    /**
     * Pins a resolver to a validator exposing the supplied security event counter.
     */
    private static ObservedValidatorResolver observing(SecurityEventCounter counter) {
        TokenValidator validator = createNiceMock(TokenValidator.class);
        expect(validator.getSecurityEventCounter()).andStubReturn(counter);
        replay(validator);
        return new ObservedValidatorResolver(ObservedValidatorResolver.Outcome.PROPERTY_CONFIGURED,
                validator, List.of(), null);
    }

    private double counterValue(SimpleMeterRegistry registry) {
        return counterValue(registry, EVENT);
    }

    /**
     * Sums the exported counters of one event type. Success events (no category) are exported
     * under the success meter, every other event under the error meter.
     */
    private double counterValue(SimpleMeterRegistry registry, SecurityEventCounter.EventType eventType) {
        String meterName = eventType.getCategory() == null
                ? MetricIdentifier.VALIDATION.SUCCESS
                : MetricIdentifier.VALIDATION.ERRORS;
        return registry.find(meterName)
                .tag("event_type", eventType.name())
                .counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(names = {"ACCESS_TOKEN_CREATED", "SIGNATURE_VALIDATION_FAILED"})
    @DisplayName("Events counted before initialization are exported by the initial update")
    void exportsPreInitializationEvents(SecurityEventCounter.EventType eventType) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SecurityEventCounter securityEventCounter = new SecurityEventCounter();
        securityEventCounter.increment(eventType);
        securityEventCounter.increment(eventType);

        JwtMetricsCollector collector = new JwtMetricsCollector(registry, observing(securityEventCounter));
        collector.initialize();

        assertEquals(2.0, counterValue(registry, eventType),
                "pre-init events must not be dropped from the baseline");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(names = {"ACCESS_TOKEN_CREATED", "SIGNATURE_VALIDATION_FAILED"})
    @DisplayName("External counter reset re-baselines instead of under-reporting subsequent events")
    void rebaselinesAfterExternalReset(SecurityEventCounter.EventType eventType) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SecurityEventCounter securityEventCounter = new SecurityEventCounter();
        JwtMetricsCollector collector = new JwtMetricsCollector(registry, observing(securityEventCounter));
        collector.initialize();

        // Normal export: positive delta
        securityEventCounter.increment(eventType);
        securityEventCounter.increment(eventType);
        securityEventCounter.increment(eventType);
        collector.updateCounters();
        assertEquals(3.0, counterValue(registry, eventType));

        // External reset: delta goes negative — must re-baseline, not decrement or ignore
        securityEventCounter.reset();
        assertEquals(0, securityEventCounter.getCount(eventType), "the security event counter must be reset");
        collector.updateCounters();
        assertEquals(3.0, counterValue(registry, eventType), "reset must not change exported totals");

        // Events after the reset are exported against the fresh baseline
        securityEventCounter.increment(eventType);
        collector.updateCounters();
        assertEquals(4.0, counterValue(registry, eventType),
                "post-reset events must be exported against the re-baselined count");
    }

    @Test
    @DisplayName("Update without new events leaves exported totals unchanged")
    void updateWithoutNewEventsIsNoop() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SecurityEventCounter securityEventCounter = new SecurityEventCounter();
        JwtMetricsCollector collector = new JwtMetricsCollector(registry, observing(securityEventCounter));
        collector.initialize();

        securityEventCounter.increment(EVENT);
        collector.updateCounters();
        assertEquals(1.0, counterValue(registry));

        // Zero delta: neither increment nor re-baseline must happen
        collector.updateCounters();
        assertEquals(1.0, counterValue(registry), "unchanged counts must not be re-exported");
    }

    @Test
    @DisplayName("Missing Micrometer counter is reported as WARN and the delta is consumed")
    void warnsWhenNoMicrometerCounterRegistered() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SecurityEventCounter securityEventCounter = new SecurityEventCounter();
        JwtMetricsCollector collector = new JwtMetricsCollector(registry, observing(securityEventCounter));

        // initialize() is deliberately not called: no Micrometer counters are registered
        securityEventCounter.increment(EVENT);
        collector.updateCounters();

        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "No Micrometer counter found");

        // The baseline still advances, so late registration must not re-export the lost delta
        collector.initialize();
        assertEquals(0.0, counterValue(registry),
                "delta reported while no counter existed must not be exported retroactively");
    }
}
