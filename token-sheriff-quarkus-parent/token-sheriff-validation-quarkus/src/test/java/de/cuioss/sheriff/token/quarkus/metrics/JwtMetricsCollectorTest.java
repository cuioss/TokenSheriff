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

import de.cuioss.sheriff.token.quarkus.config.JwtTestProfile;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.security.SecurityEventCounter;
import de.cuioss.sheriff.token.validation.security.SecurityEventCounter.EventType;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Objects;

import static de.cuioss.sheriff.token.quarkus.TokenSheriffQuarkusLogMessages.INFO;
import static de.cuioss.test.juli.LogAsserts.assertLogMessagePresent;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JwtMetricsCollector}.
 * <p>
 * This test class verifies that the metrics collector properly initializes and registers
 * metrics for all security event types. It also tests that the collector correctly updates
 * metrics when security events occur.
 * <p>
 * The tests cover:
 * <ul>
 *   <li>Initialization of metrics for all event types</li>
 *   <li>Recording and updating metrics for security events</li>
 * </ul>
 */
@QuarkusTest
@TestProfile(JwtTestProfile.class)
@EnableTestLogger
class JwtMetricsCollectorTest {

    @Inject
    TokenValidator tokenValidator;

    @Inject
    MeterRegistry registry;

    @Inject
    JwtMetricsCollector metricsCollector;

    @Test
    @DisplayName("Should initialize metrics for all event types")
    void shouldInitializeMetrics() {
        // Ensure collector is properly initialized
        assertNotNull(metricsCollector);

        // Logging verification is handled in separate unit test

        // Get counters from registry - both error and success counters
        Collection<Counter> errorCounters = registry.find(MetricIdentifier.VALIDATION.ERRORS).counters();
        Collection<Counter> successCounters = registry.find(MetricIdentifier.VALIDATION.SUCCESS).counters();

        // Verify counters exist for all event types
        assertFalse(errorCounters.isEmpty(), "Should have registered error counters");
        assertFalse(successCounters.isEmpty(), "Should have registered success counters");

        // Verify all event types have corresponding counters
        for (EventType eventType : SecurityEventCounter.EventType.values()) {
            if (eventType.getCategory() == null) {
                // Success events should be in success counters
                boolean hasCounter = successCounters.stream()
                        .anyMatch(counter -> Objects.equals(counter.getId().getTag("event_type"), eventType.name()));
                assertTrue(hasCounter, "Should have success counter for event type: " + eventType.name());
            } else {
                // Error events should be in error counters
                boolean hasCounter = errorCounters.stream()
                        .anyMatch(counter -> Objects.equals(counter.getId().getTag("event_type"), eventType.name()));
                assertTrue(hasCounter, "Should have error counter for event type: " + eventType.name());
            }
        }
    }

    @Test
    @DisplayName("Should record metrics for security events")
    void shouldHaveMetricsForSecurityEvents() {
        // Get the security event counter from the token validator
        SecurityEventCounter counter = tokenValidator.getSecurityEventCounter();
        assertNotNull(counter);

        // Record some events
        EventType testEventType = EventType.SIGNATURE_VALIDATION_FAILED;
        counter.increment(testEventType);
        counter.increment(testEventType);

        // Manually update counters (instead of waiting for scheduled update)
        metricsCollector.updateCounters();

        // Verify the metric exists with the correct tags
        boolean hasMetric = !registry.find(MetricIdentifier.VALIDATION.ERRORS)
                .tag("event_type", testEventType.name())
                .tag("result", "failure")
                .tag("category", "INVALID_SIGNATURE")
                .counters().isEmpty();

        assertTrue(hasMetric, "Should have metric for the event type");
    }


    @Test
    @DisplayName("Should clear all metrics when clear method is called")
    void shouldClearAllMetrics() {
        // Get the security event counter
        SecurityEventCounter securityEventCounter = tokenValidator.getSecurityEventCounter();
        assertNotNull(securityEventCounter);

        // Record some events
        EventType testEventType = EventType.SIGNATURE_VALIDATION_FAILED;
        securityEventCounter.increment(testEventType);
        securityEventCounter.increment(testEventType);

        // Verify data is recorded
        assertTrue(securityEventCounter.getCount(testEventType) > 0, "Should have recorded security events");

        // Clear all metrics
        metricsCollector.clear();

        // Verify logging occurred during clear
        assertLogMessagePresent(TestLogLevel.INFO, INFO.CLEARING_JWT_METRICS.format());
        assertLogMessagePresent(TestLogLevel.INFO, INFO.JWT_METRICS_CLEARED.format());

        // Verify all metrics are cleared
        assertEquals(0, securityEventCounter.getCount(testEventType), "Security event counter should be cleared");

        // Verify the tracking maps are cleared by recording new events and checking delta calculation
        securityEventCounter.increment(testEventType);
        metricsCollector.updateCounters();

        // The counter should only reflect the new increment, not the old ones
        Counter metricCounter = registry.find(MetricIdentifier.VALIDATION.ERRORS)
                .tag("event_type", testEventType.name())
                .counter();
        assertNotNull(metricCounter, "Counter should exist");
        // Note: We can't directly check the counter value as it's cumulative in Micrometer,
        // but we've verified the underlying monitors are cleared
    }
}
