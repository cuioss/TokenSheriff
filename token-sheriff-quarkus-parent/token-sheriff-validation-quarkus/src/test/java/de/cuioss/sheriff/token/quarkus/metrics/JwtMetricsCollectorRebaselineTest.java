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
package de.cuioss.sheriff.token.quarkus.metrics;

import de.cuioss.sheriff.token.validation.security.SecurityEventCounter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plain unit tests for the delta/re-baseline logic of {@link JwtMetricsCollector},
 * independent of the Quarkus container: pre-init events are exported, positive
 * deltas increment Micrometer counters, and an external reset re-baselines
 * instead of silently under-reporting.
 */
class JwtMetricsCollectorRebaselineTest {

    private static final SecurityEventCounter.EventType EVENT =
            SecurityEventCounter.EventType.ACCESS_TOKEN_CREATED;

    private double counterValue(SimpleMeterRegistry registry) {
        return registry.find(MetricIdentifier.VALIDATION.SUCCESS)
                .tag("event_type", EVENT.name())
                .counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    @Test
    @DisplayName("Events counted before initialization are exported by the initial update")
    void exportsPreInitializationEvents() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SecurityEventCounter securityEventCounter = new SecurityEventCounter();
        securityEventCounter.increment(EVENT);
        securityEventCounter.increment(EVENT);

        JwtMetricsCollector collector = new JwtMetricsCollector(registry, securityEventCounter);
        collector.initialize();

        assertEquals(2.0, counterValue(registry),
                "pre-init events must not be dropped from the baseline");
    }

    @Test
    @DisplayName("External counter reset re-baselines instead of under-reporting subsequent events")
    void rebaselinesAfterExternalReset() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SecurityEventCounter securityEventCounter = new SecurityEventCounter();
        JwtMetricsCollector collector = new JwtMetricsCollector(registry, securityEventCounter);
        collector.initialize();

        // Normal export: positive delta
        securityEventCounter.increment(EVENT);
        securityEventCounter.increment(EVENT);
        securityEventCounter.increment(EVENT);
        collector.updateCounters();
        assertEquals(3.0, counterValue(registry));

        // External reset: delta goes negative — must re-baseline, not decrement or ignore
        securityEventCounter.reset();
        collector.updateCounters();
        assertEquals(3.0, counterValue(registry), "reset must not change exported totals");

        // Events after the reset are exported against the fresh baseline
        securityEventCounter.increment(EVENT);
        collector.updateCounters();
        assertEquals(4.0, counterValue(registry),
                "post-reset events must be exported against the re-baselined count");
    }
}
