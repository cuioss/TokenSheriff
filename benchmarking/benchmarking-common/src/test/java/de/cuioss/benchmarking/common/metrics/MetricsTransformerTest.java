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
package de.cuioss.benchmarking.common.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetricsTransformerTest {

    private MetricsTransformer transformer;
    private Map<String, Double> testMetrics;

    @BeforeEach
    void setUp() {
        transformer = new MetricsTransformer();
        testMetrics = new HashMap<>();
    }

    @Test
    void shouldTransformEmptyMetrics() {
        Map<String, Object> result = transformer.transformToQuarkusRuntimeMetrics(new HashMap<>());

        assertNotNull(result);
        assertTrue(result.containsKey("timestamp"));
        assertTrue(result.containsKey("system"));
        assertTrue(result.containsKey("sheriff_token_validation_success_operations_total"));
        assertTrue(result.containsKey("sheriff_token_validation_errors"));

        // All sections should exist but be empty
        assertTrue(((Map<?, ?>) result.get("system")).isEmpty());
        assertTrue(((Map<?, ?>) result.get("sheriff_token_validation_success_operations_total")).isEmpty());
        assertTrue(((Map<?, ?>) result.get("sheriff_token_validation_errors")).isEmpty());
    }

    @Test
    void shouldTransformSystemMetrics() {
        testMetrics.put("system_cpu_count", 4.0);
        testMetrics.put("system_load_average_1m", 2.5);
        testMetrics.put("jdk_threads_peak_threads", 86.0);
        testMetrics.put("process_cpu_usage", 0.25);  // 25%
        testMetrics.put("system_cpu_usage", 0.50);   // 50%
        testMetrics.put("jvm_memory_used_bytes{area=\"heap\"}", 100_000_000.0);
        testMetrics.put("jvm_memory_used_bytes{area=\"nonheap\"}", 50_000_000.0);

        Map<String, Object> result = transformer.transformToQuarkusRuntimeMetrics(testMetrics);
        Map<String, Object> system = (Map<String, Object>) result.get("system");

        assertEquals(4, system.get("cpu_cores_available"));
        assertEquals(2.5, system.get("cpu_load_average"));
        assertEquals(86, system.get("threads_peak"));
        assertEquals(25L, system.get("quarkus_cpu_usage_percent"));
        assertEquals(50L, system.get("system_cpu_usage_percent"));
        assertEquals(95L, system.get("memory_heap_used_mb"));  // ~95MB
        assertEquals(47L, system.get("memory_nonheap_used_mb"));  // ~47MB
    }

    @Test
    void shouldTransformJwtSuccessMetrics() {
        testMetrics.put("sheriff_token_validation_success_operations_total{event_type=\"ACCESS_TOKEN_CREATED\"}", 500.0);
        testMetrics.put("sheriff_token_validation_success_operations_total{event_type=\"ID_TOKEN_CREATED\"}", 300.0);

        Map<String, Object> result = transformer.transformToQuarkusRuntimeMetrics(testMetrics);
        Map<String, Object> successOps = (Map<String, Object>) result.get("sheriff_token_validation_success_operations_total");

        assertEquals(500L, successOps.get("ACCESS_TOKEN_CREATED"));
        assertEquals(300L, successOps.get("ID_TOKEN_CREATED"));
    }

    @Test
    void shouldTransformJwtErrorMetrics() {
        testMetrics.put("sheriff_token_validation_errors_total{category=\"INVALID_SIGNATURE\",event_type=\"KEY_NOT_FOUND\"}", 10.0);
        testMetrics.put("sheriff_token_validation_errors_total{category=\"SEMANTIC_ISSUES\",event_type=\"TOKEN_EXPIRED\"}", 5.0);

        Map<String, Object> result = transformer.transformToQuarkusRuntimeMetrics(testMetrics);
        Map<String, Object> errors = (Map<String, Object>) result.get("sheriff_token_validation_errors");

        Map<String, Object> sigError = (Map<String, Object>) errors.get("INVALID_SIGNATURE_KEY_NOT_FOUND");
        assertNotNull(sigError);
        assertEquals("INVALID_SIGNATURE", sigError.get("category"));
        assertEquals("KEY_NOT_FOUND", sigError.get("event_type"));
        assertEquals(10L, sigError.get("count"));

        Map<String, Object> semError = (Map<String, Object>) errors.get("SEMANTIC_ISSUES_TOKEN_EXPIRED");
        assertNotNull(semError);
        assertEquals("SEMANTIC_ISSUES", semError.get("category"));
        assertEquals("TOKEN_EXPIRED", semError.get("event_type"));
        assertEquals(5L, semError.get("count"));
    }

    @Test
    void shouldIgnoreZeroValues() {
        testMetrics.put("sheriff_token_validation_success_operations_total{event_type=\"ACCESS_TOKEN_CREATED\"}", 0.0);
        testMetrics.put("jvm_memory_used_bytes{area=\"heap\"}", 0.0);

        Map<String, Object> result = transformer.transformToQuarkusRuntimeMetrics(testMetrics);

        Map<String, Object> successOps = (Map<String, Object>) result.get("sheriff_token_validation_success_operations_total");
        assertFalse(successOps.containsKey("ACCESS_TOKEN_CREATED"), "Should not include zero-value success operations");

        Map<String, Object> system = (Map<String, Object>) result.get("system");
        assertFalse(system.containsKey("memory_heap_used_mb"), "Should not include zero memory values");
    }

    @Test
    void shouldHandleCompleteRealWorldMetrics() {
        // Simulate real-world metrics
        testMetrics.put("system_cpu_count", 4.0);
        testMetrics.put("process_cpu_usage", 0.025);
        testMetrics.put("system_cpu_usage", 0.022);
        testMetrics.put("jdk_threads_peak_threads", 86.0);
        testMetrics.put("jvm_memory_used_bytes{area=\"heap\",id=\"G1 Eden Space\"}", 30_000_000.0);
        testMetrics.put("jvm_memory_used_bytes{area=\"heap\",id=\"G1 Old Gen\"}", 20_000_000.0);
        testMetrics.put("sheriff_token_validation_success_operations_total{event_type=\"ACCESS_TOKEN_CREATED\",result=\"success\"}", 3584040.0);
        testMetrics.put("sheriff_token_validation_errors_total{category=\"INVALID_SIGNATURE\",event_type=\"KEY_NOT_FOUND\"}", 279132.0);

        Map<String, Object> result = transformer.transformToQuarkusRuntimeMetrics(testMetrics);

        assertNotNull(result);
        assertNotNull(result.get("timestamp"));

        // Verify system metrics
        Map<String, Object> system = (Map<String, Object>) result.get("system");
        assertEquals(4, system.get("cpu_cores_available"));
        assertEquals(2.5, system.get("quarkus_cpu_usage_percent"));
        assertEquals(2.2, system.get("system_cpu_usage_percent"));
        assertEquals(86, system.get("threads_peak"));
        assertEquals(47L, system.get("memory_heap_used_mb"));  // ~47MB total heap

        // Verify JWT success metrics
        Map<String, Object> successOps = (Map<String, Object>) result.get("sheriff_token_validation_success_operations_total");
        assertEquals(3584040L, successOps.get("ACCESS_TOKEN_CREATED"));

        // Verify JWT error metrics
        Map<String, Object> errors = (Map<String, Object>) result.get("sheriff_token_validation_errors");
        Map<String, Object> keyNotFoundError = (Map<String, Object>) errors.get("INVALID_SIGNATURE_KEY_NOT_FOUND");
        assertEquals(279132L, keyNotFoundError.get("count"));
    }
}