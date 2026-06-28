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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for MetricsJsonExporter
 */
class MetricsJsonExporterTest {

    @TempDir
    Path tempDir;

    private Path targetDir;
    private MetricsJsonExporter exporter;
    private Gson gson = new Gson();

    @BeforeEach
    void setUp() {
        targetDir = tempDir.resolve("target");
        exporter = new MetricsJsonExporter(targetDir);
    }

    @Test
    void shouldCreateTargetDirectoryOnInitialization() {
        Path newTargetDir = tempDir.resolve("new-target");
        assertFalse(Files.exists(newTargetDir), "Directory should not exist initially");

        new MetricsJsonExporter(newTargetDir);

        assertTrue(Files.exists(newTargetDir), "Directory should be created during initialization");
    }

    @Test
    void shouldExportToFileSuccessfully() throws Exception {
        Map<String, Object> testData = new HashMap<>();
        testData.put("test_key", "test_value");
        testData.put("number", 42);

        exporter.exportToFile("test-export.json", testData);

        Path exportedFile = targetDir.resolve("test-export.json");
        assertTrue(Files.exists(exportedFile), "File should be exported");

        String content = Files.readString(exportedFile);
        assertFalse(content.isEmpty(), "File content should not be empty");

        Type mapType = new TypeToken<Map<String, Object>>(){
        }.getType();
        Map<String, Object> parsedData = gson.fromJson(content, mapType);
        assertEquals("test_value", parsedData.get("test_key"));
        assertEquals(42.0, parsedData.get("number"));
    }

    @Test
    void shouldExportJwtValidationMetricsForValidBenchmark() throws Exception {
        Map<String, Double> metricsData = createJwtValidationMetrics();
        Instant timestamp = Instant.now();

        exporter.exportJwtValidationMetrics("JwtValidationBenchmark.validateToken", timestamp, metricsData);

        Path integrationFile = targetDir.resolve("integration-metrics.json");
        assertTrue(Files.exists(integrationFile), "Integration metrics file should be created");

        String content = Files.readString(integrationFile);
        Type mapType = new TypeToken<Map<String, Object>>(){
        }.getType();
        Map<String, Object> parsedData = gson.fromJson(content, mapType);

        assertTrue(parsedData.containsKey("validateToken"), "Should contain benchmark data");

        @SuppressWarnings("unchecked") Map<String, Object> benchmarkData = (Map<String, Object>) parsedData.get("validateToken");
        assertTrue(benchmarkData.containsKey("timestamp"), "Should contain timestamp");
        assertTrue(benchmarkData.containsKey("bearer_token_producer_metrics"), "Should contain bearer token metrics");
        assertTrue(benchmarkData.containsKey("security_event_counter_metrics"), "Should contain security event metrics");
    }

    @Test
    void shouldNotExportForNonJwtValidationBenchmark() throws Exception {
        Map<String, Double> metricsData = createJwtValidationMetrics();
        Instant timestamp = Instant.now();

        exporter.exportJwtValidationMetrics("SomeOtherBenchmark.someMethod", timestamp, metricsData);

        Path integrationFile = targetDir.resolve("integration-metrics.json");
        assertFalse(Files.exists(integrationFile), "Integration metrics file should not be created for non-JWT benchmark");
    }

    @Test
    void shouldExportResourceMetrics() throws Exception {
        Map<String, Double> resourceMetrics = createResourceMetrics();
        Instant timestamp = Instant.now();

        exporter.exportResourceMetrics(timestamp, resourceMetrics);

        Path resourceFile = targetDir.resolve("resource-metrics.json");
        assertTrue(Files.exists(resourceFile), "Resource metrics file should be created");

        String content = Files.readString(resourceFile);
        Type mapType = new TypeToken<Map<String, Object>>(){
        }.getType();
        Map<String, Object> parsedData = gson.fromJson(content, mapType);

        assertTrue(parsedData.containsKey("timestamp"), "Should contain timestamp");
        assertTrue(parsedData.containsKey("cpu_metrics"), "Should contain CPU metrics");
        assertTrue(parsedData.containsKey("memory_metrics"), "Should contain memory metrics");

        @SuppressWarnings("unchecked") Map<String, Object> cpuMetrics = (Map<String, Object>) parsedData.get("cpu_metrics");
        assertTrue(cpuMetrics.containsKey("system_cpu_usage"), "Should contain system CPU usage");
        assertTrue(cpuMetrics.containsKey("process_cpu_usage"), "Should contain process CPU usage");
        assertTrue(cpuMetrics.containsKey("cpu_count"), "Should contain CPU count");

        @SuppressWarnings("unchecked") Map<String, Object> memoryMetrics = (Map<String, Object>) parsedData.get("memory_metrics");
        assertTrue(memoryMetrics.containsKey("heap_used_bytes"), "Should contain heap memory");
        assertTrue(memoryMetrics.containsKey("nonheap_used_bytes"), "Should contain non-heap memory");
    }

    @Test
    void shouldUpdateAggregatedMetrics() throws Exception {
        Map<String, Object> benchmarkData1 = new HashMap<>();
        benchmarkData1.put("timestamp", "2025-01-01T10:00:00Z");
        benchmarkData1.put("sample_count", 1000);

        Map<String, Object> benchmarkData2 = new HashMap<>();
        benchmarkData2.put("timestamp", "2025-01-01T11:00:00Z");
        benchmarkData2.put("sample_count", 2000);

        exporter.updateAggregatedMetrics("aggregated.json", "benchmark1", benchmarkData1);
        exporter.updateAggregatedMetrics("aggregated.json", "benchmark2", benchmarkData2);

        Path aggregatedFile = targetDir.resolve("aggregated.json");
        assertTrue(Files.exists(aggregatedFile), "Aggregated metrics file should exist");

        String content = Files.readString(aggregatedFile);
        Type mapType = new TypeToken<Map<String, Object>>(){
        }.getType();
        Map<String, Object> parsedData = gson.fromJson(content, mapType);

        assertTrue(parsedData.containsKey("benchmark1"), "Should contain first benchmark");
        assertTrue(parsedData.containsKey("benchmark2"), "Should contain second benchmark");

        @SuppressWarnings("unchecked") Map<String, Object> benchmark1Data = (Map<String, Object>) parsedData.get("benchmark1");
        assertEquals(1000.0, benchmark1Data.get("sample_count"));

        @SuppressWarnings("unchecked") Map<String, Object> benchmark2Data = (Map<String, Object>) parsedData.get("benchmark2");
        assertEquals(2000.0, benchmark2Data.get("sample_count"));
    }

    @Test
    void shouldReadExistingMetrics() throws Exception {
        Map<String, Object> originalData = new HashMap<>();
        originalData.put("existing_key", "existing_value");
        originalData.put("number", 123);

        exporter.exportToFile("existing.json", originalData);

        Map<String, Object> readData = exporter.readExistingMetrics("existing.json");

        assertEquals(originalData.size(), readData.size());
        assertEquals("existing_value", readData.get("existing_key"));
        assertEquals(123.0, readData.get("number"));
    }

    @Test
    void shouldReturnEmptyMapForNonExistentFile() {
        Map<String, Object> readData = exporter.readExistingMetrics("non-existent.json");

        assertTrue(readData.isEmpty(), "Should return empty map for non-existent file");
    }

    @Test
    void shouldReturnEmptyMapForEmptyFile() throws Exception {
        Path emptyFile = targetDir.resolve("empty.json");
        Files.createFile(emptyFile);

        Map<String, Object> readData = exporter.readExistingMetrics("empty.json");

        assertTrue(readData.isEmpty(), "Should return empty map for empty file");
    }

    @Test
    void shouldHandleCorruptedJsonFile() throws Exception {
        Path corruptedFile = targetDir.resolve("corrupted.json");
        Files.write(corruptedFile, "{ invalid json content".getBytes());

        Map<String, Object> readData = exporter.readExistingMetrics("corrupted.json");

        assertTrue(readData.isEmpty(), "Should return empty map for corrupted JSON");
        assertFalse(Files.exists(corruptedFile), "Corrupted file should be deleted");
    }

    @Test
    void shouldRecognizeJwtValidationBenchmarks() throws Exception {
        Map<String, Double> metricsData = createJwtValidationMetrics();
        Instant timestamp = Instant.now();

        String[] validBenchmarkNames = {
                "JwtValidationBenchmark.validateAccessToken",
                "JwtValidation",
                "validateJwtToken",
                "validateAccessToken",
                "validateIdToken"
        };

        for (String benchmarkName : validBenchmarkNames) {
            exporter.exportJwtValidationMetrics(benchmarkName, timestamp, metricsData);
        }

        Path integrationFile = targetDir.resolve("integration-metrics.json");
        assertTrue(Files.exists(integrationFile), "Integration metrics should be created for JWT validation benchmarks");

        String content = Files.readString(integrationFile);
        Type mapType = new TypeToken<Map<String, Object>>(){
        }.getType();
        Map<String, Object> parsedData = gson.fromJson(content, mapType);

        assertEquals(4, parsedData.size(), "Should contain 4 unique benchmarks (validateAccessToken appears twice)");
    }

    @Test
    void shouldExtractTimedMetricsCorrectly() throws Exception {
        Map<String, Double> metricsData = new HashMap<>();
        metricsData.put("sheriff_oauth_bearer_token_validation_seconds_count{class=\"de.cuioss.sheriff.token.quarkus.producer.BearerTokenProducer\",exception=\"none\",method=\"getBearerTokenResult\"}", 1000.0);
        metricsData.put("sheriff_oauth_bearer_token_validation_seconds_sum{class=\"de.cuioss.sheriff.token.quarkus.producer.BearerTokenProducer\",exception=\"none\",method=\"getBearerTokenResult\"}", 2.5);
        metricsData.put("sheriff_oauth_bearer_token_validation_seconds_max{class=\"de.cuioss.sheriff.token.quarkus.producer.BearerTokenProducer\",exception=\"none\",method=\"getBearerTokenResult\"}", 0.010);

        Instant timestamp = Instant.now();
        exporter.exportJwtValidationMetrics("JwtValidationBenchmark.test", timestamp, metricsData);

        Path integrationFile = targetDir.resolve("integration-metrics.json");
        String content = Files.readString(integrationFile);
        Type mapType = new TypeToken<Map<String, Object>>(){
        }.getType();
        Map<String, Object> parsedData = gson.fromJson(content, mapType);

        @SuppressWarnings("unchecked") Map<String, Object> benchmarkData = (Map<String, Object>) parsedData.get("test");
        @SuppressWarnings("unchecked") Map<String, Object> timedMetrics = (Map<String, Object>) benchmarkData.get("bearer_token_producer_metrics");
        @SuppressWarnings("unchecked") Map<String, Object> validationMetric = (Map<String, Object>) timedMetrics.get("validation");

        assertEquals(1000.0, validationMetric.get("sample_count"));
        assertTrue(validationMetric.containsKey("p50_us"), "Should contain p50 percentile");
        assertTrue(validationMetric.containsKey("p95_us"), "Should contain p95 percentile");
        assertTrue(validationMetric.containsKey("p99_us"), "Should contain p99 percentile");
    }

    @Test
    void shouldExtractSecurityEventMetricsCorrectly() throws Exception {
        Map<String, Double> metricsData = new HashMap<>();
        metricsData.put("sheriff_oauth_validation_errors_total{category=\"INVALID_STRUCTURE\",event_type=\"TOKEN_EMPTY\",result=\"failure\"}", 5.0);
        metricsData.put("sheriff_oauth_validation_errors_total{category=\"SEMANTIC_ISSUES\",event_type=\"TOKEN_EXPIRED\",result=\"failure\"}", 10.0);
        metricsData.put("sheriff_oauth_validation_success_operations_total{event_type=\"ACCESS_TOKEN_CREATED\",result=\"success\"}", 1000.0);

        Instant timestamp = Instant.now();
        exporter.exportJwtValidationMetrics("JwtValidationBenchmark.test", timestamp, metricsData);

        Path integrationFile = targetDir.resolve("integration-metrics.json");
        String content = Files.readString(integrationFile);
        Type mapType = new TypeToken<Map<String, Object>>(){
        }.getType();
        Map<String, Object> parsedData = gson.fromJson(content, mapType);

        @SuppressWarnings("unchecked") Map<String, Object> benchmarkData = (Map<String, Object>) parsedData.get("test");
        @SuppressWarnings("unchecked") Map<String, Object> securityMetrics = (Map<String, Object>) benchmarkData.get("security_event_counter_metrics");

        assertEquals(15.0, securityMetrics.get("total_errors"));
        assertEquals(1000.0, securityMetrics.get("total_success"));

        assertTrue(securityMetrics.containsKey("errors_by_category"), "Should contain errors by category");
        assertTrue(securityMetrics.containsKey("success_by_type"), "Should contain success by type");
    }

    @Test
    void shouldFormatNumbersCorrectly() throws Exception {
        Map<String, Object> testData = new HashMap<>();
        testData.put("small_number", 5.67);
        testData.put("large_number", 123.456);
        testData.put("integer_value", 100.0);

        exporter.exportToFile("format-test.json", testData);

        Path formatFile = targetDir.resolve("format-test.json");
        String content = Files.readString(formatFile);
        Type mapType = new TypeToken<Map<String, Object>>(){
        }.getType();
        Map<String, Object> parsedData = gson.fromJson(content, mapType);

        assertEquals(5.67, parsedData.get("small_number"));
        assertEquals(123.456, parsedData.get("large_number"));
        assertEquals(100.0, ((Number) parsedData.get("integer_value")).doubleValue());
    }

    @Test
    void shouldExtractCpuMetricsCorrectly() throws Exception {
        Map<String, Double> resourceMetrics = new HashMap<>();
        resourceMetrics.put("system_cpu_usage", 0.25);
        resourceMetrics.put("process_cpu_usage", 0.15);
        resourceMetrics.put("system_cpu_count", 8.0);

        exporter.exportResourceMetrics(Instant.now(), resourceMetrics);

        Path resourceFile = targetDir.resolve("resource-metrics.json");
        String content = Files.readString(resourceFile);
        Type mapType = new TypeToken<Map<String, Object>>(){
        }.getType();
        Map<String, Object> parsedData = gson.fromJson(content, mapType);

        @SuppressWarnings("unchecked") Map<String, Object> cpuMetrics = (Map<String, Object>) parsedData.get("cpu_metrics");

        assertEquals(25.0, cpuMetrics.get("system_cpu_usage"));
        assertEquals(15.0, cpuMetrics.get("process_cpu_usage"));
        assertEquals(8.0, ((Number) cpuMetrics.get("cpu_count")).doubleValue());
    }

    @Test
    void shouldExtractMemoryMetricsCorrectly() throws Exception {
        Map<String, Double> resourceMetrics = new HashMap<>();
        resourceMetrics.put("jvm_memory_used_bytes{area=\"heap\",id=\"eden space\"}", 100000.0);
        resourceMetrics.put("jvm_memory_used_bytes{area=\"nonheap\",id=\"metaspace\"}", 50000.0);

        exporter.exportResourceMetrics(Instant.now(), resourceMetrics);

        Path resourceFile = targetDir.resolve("resource-metrics.json");
        String content = Files.readString(resourceFile);
        Type mapType = new TypeToken<Map<String, Object>>(){
        }.getType();
        Map<String, Object> parsedData = gson.fromJson(content, mapType);

        @SuppressWarnings("unchecked") Map<String, Object> memoryMetrics = (Map<String, Object>) parsedData.get("memory_metrics");

        assertEquals(100000.0, ((Number) memoryMetrics.get("heap_used_bytes")).doubleValue());
        assertEquals(50000.0, ((Number) memoryMetrics.get("nonheap_used_bytes")).doubleValue());
    }

    @Test
    void shouldHandleEmptyTimedMetrics() throws Exception {
        Map<String, Double> metricsData = new HashMap<>();

        Instant timestamp = Instant.now();
        exporter.exportJwtValidationMetrics("JwtValidationBenchmark.test", timestamp, metricsData);

        Path integrationFile = targetDir.resolve("integration-metrics.json");
        String content = Files.readString(integrationFile);
        Type mapType = new TypeToken<Map<String, Object>>(){
        }.getType();
        Map<String, Object> parsedData = gson.fromJson(content, mapType);

        @SuppressWarnings("unchecked") Map<String, Object> benchmarkData = (Map<String, Object>) parsedData.get("test");
        @SuppressWarnings("unchecked") Map<String, Object> timedMetrics = (Map<String, Object>) benchmarkData.get("bearer_token_producer_metrics");
        @SuppressWarnings("unchecked") Map<String, Object> validationMetric = (Map<String, Object>) timedMetrics.get("validation");

        assertEquals(0.0, validationMetric.get("sample_count"));
        assertEquals(0.0, validationMetric.get("p50_us"));
        assertEquals(0.0, validationMetric.get("p95_us"));
        assertEquals(0.0, validationMetric.get("p99_us"));
    }

    @Test
    void shouldExtractSimpleBenchmarkNameCorrectly() throws Exception {
        Map<String, Double> metricsData = createJwtValidationMetrics();
        Instant timestamp = Instant.now();

        exporter.exportJwtValidationMetrics("com.example.package.JwtValidationBenchmark.validateAccessToken", timestamp, metricsData);

        Path integrationFile = targetDir.resolve("integration-metrics.json");
        String content = Files.readString(integrationFile);
        Type mapType = new TypeToken<Map<String, Object>>(){
        }.getType();
        Map<String, Object> parsedData = gson.fromJson(content, mapType);

        assertTrue(parsedData.containsKey("validateAccessToken"), "Should extract simple name without package");
        assertFalse(parsedData.containsKey("com.example.package.JwtValidationBenchmark.validateAccessToken"), "Should not contain full package name");
    }

    @Test
    void shouldHandleNullValues() throws Exception {
        Map<String, Object> testData = new HashMap<>();
        testData.put("null_value", null);
        testData.put("valid_value", "test");

        exporter.exportToFile("null-test.json", testData);

        Path nullFile = targetDir.resolve("null-test.json");
        String content = Files.readString(nullFile);
        Type mapType = new TypeToken<Map<String, Object>>(){
        }.getType();
        Map<String, Object> parsedData = gson.fromJson(content, mapType);

        assertTrue(parsedData.containsKey("null_value"), "Should contain null key");
        assertNull(parsedData.get("null_value"), "Null value should be preserved");
        assertEquals("test", parsedData.get("valid_value"));
    }

    @SuppressWarnings("java:S5961")
    @Test
    void shouldCreateQuarkusRuntimeMetricsStructure() throws Exception {
        // Load real metrics data from test resources
        Map<String, Double> realMetrics = loadRealMetricsFromTestResources();

        // Create the expected structured data using real metrics
        Map<String, Object> quarkusData = createQuarkusRuntimeMetricsFromRealData(realMetrics);

        exporter.updateAggregatedMetrics("quarkus-metrics.json", "WrkBenchmark", quarkusData);

        Path quarkusFile = targetDir.resolve("quarkus-metrics.json");
        assertTrue(Files.exists(quarkusFile), "Quarkus metrics file should exist");

        String content = Files.readString(quarkusFile);
        Type mapType = new TypeToken<Map<String, Object>>(){
        }.getType();
        Map<String, Object> parsedData = gson.fromJson(content, mapType);

        // Should use "quarkus-runtime-metrics" as key instead of "WrkBenchmark"
        assertTrue(parsedData.containsKey("quarkus-runtime-metrics"), "Should contain quarkus-runtime-metrics key");
        assertFalse(parsedData.containsKey("WrkBenchmark"), "Should not contain WrkBenchmark key");

        @SuppressWarnings("unchecked") Map<String, Object> runtimeData = (Map<String, Object>) parsedData.get("quarkus-runtime-metrics");

        // Should not contain "benchmark" field
        assertFalse(runtimeData.containsKey("benchmark"), "Should not contain benchmark field");

        // Should contain timestamp
        assertTrue(runtimeData.containsKey("timestamp"), "Should contain timestamp");

        // Should contain three main nodes
        assertTrue(runtimeData.containsKey("system"), "Should contain system node");
        assertTrue(runtimeData.containsKey("sheriff_oauth_validation_success_operations_total"), "Should contain JWT success operations");
        assertTrue(runtimeData.containsKey("sheriff_oauth_validation_errors"), "Should contain JWT validation errors");

        // Verify system node structure with new naming conventions
        @SuppressWarnings("unchecked") Map<String, Object> systemMetrics = (Map<String, Object>) runtimeData.get("system");
        assertTrue(systemMetrics.containsKey("quarkus_cpu_usage_percent"), "Should contain Quarkus CPU usage");
        assertTrue(systemMetrics.containsKey("system_cpu_usage_percent"), "Should contain system CPU usage");
        assertTrue(systemMetrics.containsKey("threads_peak"), "Should contain peak threads");
        assertTrue(systemMetrics.containsKey("memory_heap_used_mb"), "Should contain memory metrics");
        assertTrue(systemMetrics.containsKey("cpu_cores_available"), "Should contain CPU cores");

        // Verify values are reasonable (not hardcoded since we're using real data)
        Number cpuUsage = (Number) systemMetrics.get("quarkus_cpu_usage_percent");
        assertNotNull(cpuUsage, "Quarkus CPU usage should not be null");
        assertTrue(cpuUsage.doubleValue() >= 0 && cpuUsage.doubleValue() <= 100,
                "CPU usage should be between 0 and 100 percent");

        Number cores = (Number) systemMetrics.get("cpu_cores_available");
        assertNotNull(cores, "CPU cores should not be null");
        assertEquals(4, cores.intValue(), "Should have 4 CPU cores from real metrics");

        // Verify JWT success operations structure with real values
        @SuppressWarnings("unchecked") Map<String, Object> successOps = (Map<String, Object>) runtimeData.get("sheriff_oauth_validation_success_operations_total");
        assertTrue(successOps.containsKey("ACCESS_TOKEN_CREATED"), "Should contain ACCESS_TOKEN_CREATED");
        // Verify the value is positive (not hardcoded since we're using real data that can vary)
        Number tokenCount = (Number) successOps.get("ACCESS_TOKEN_CREATED");
        assertNotNull(tokenCount, "Token count should not be null");
        assertTrue(tokenCount.longValue() > 0, "Should have created at least one token");

        // Verify JWT validation errors structure
        @SuppressWarnings("unchecked") List<Map<String, Object>> errors = (List<Map<String, Object>>) runtimeData.get("sheriff_oauth_validation_errors");
        assertFalse(errors.isEmpty(), "Should contain error entries");

        // Verify errors are sorted by category and event_type
        Map<String, Object> firstError = errors.getFirst();
        assertTrue(firstError.containsKey("category"), "Error should contain category");
        assertTrue(firstError.containsKey("event_type"), "Error should contain event_type");
        assertTrue(firstError.containsKey("count"), "Error should contain count");

        // Verify specific error ordering (should start with INVALID_SIGNATURE category)
        assertEquals("INVALID_SIGNATURE", firstError.get("category"));
    }

    private Map<String, Double> createJwtValidationMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("sheriff_oauth_bearer_token_validation_seconds_count{class=\"de.cuioss.sheriff.token.quarkus.producer.BearerTokenProducer\",exception=\"none\",method=\"getBearerTokenResult\"}", 5000.0);
        metrics.put("sheriff_oauth_bearer_token_validation_seconds_sum{class=\"de.cuioss.sheriff.token.quarkus.producer.BearerTokenProducer\",exception=\"none\",method=\"getBearerTokenResult\"}", 1.5);
        metrics.put("sheriff_oauth_bearer_token_validation_seconds_max{class=\"de.cuioss.sheriff.token.quarkus.producer.BearerTokenProducer\",exception=\"none\",method=\"getBearerTokenResult\"}", 0.005);
        metrics.put("sheriff_oauth_validation_errors_total{category=\"INVALID_STRUCTURE\",event_type=\"TOKEN_EMPTY\",result=\"failure\"}", 0.0);
        metrics.put("sheriff_oauth_validation_success_operations_total{event_type=\"ACCESS_TOKEN_CREATED\",result=\"success\"}", 5000.0);
        return metrics;
    }

    private Map<String, Double> createResourceMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("system_cpu_usage", 0.35);
        metrics.put("process_cpu_usage", 0.25);
        metrics.put("system_cpu_count", 4.0);
        metrics.put("jvm_memory_used_bytes{area=\"heap\",id=\"eden space\"}", 200000.0);
        metrics.put("jvm_memory_used_bytes{area=\"nonheap\",id=\"metaspace\"}", 100000.0);
        return metrics;
    }

    private Map<String, Double> loadRealMetricsFromTestResources() throws IOException {
        // Load the real metrics file from test resources
        // Use the real WRK benchmark metrics file
        try (var inputStream = getClass().getResourceAsStream("/metrics/wrk-benchmark-metrics.txt")) {
            assertNotNull(inputStream, "Test metrics file should exist in resources");

            Map<String, Double> metrics = new HashMap<>();
            String content = new String(inputStream.readAllBytes());

            // Parse Prometheus format metrics
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue; // Skip comments and empty lines
                }

                // Parse metric line: metric_name{labels} value
                int spaceIndex = line.lastIndexOf(' ');
                if (spaceIndex > 0) {
                    String metricKey = line.substring(0, spaceIndex);
                    String valueStr = line.substring(spaceIndex + 1);
                    try {
                        double value = Double.parseDouble(valueStr);
                        metrics.put(metricKey, value);
                    } catch (NumberFormatException e) {
                        // Skip non-numeric values
                    }
                }
            }

            assertTrue(metrics.size() > 50, "Should load many metrics from real data");
            return metrics;
        }
    }

    private Map<String, Object> createQuarkusRuntimeMetricsFromRealData(Map<String, Double> realMetrics) {
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", "2025-09-24T15:06:02.082992Z");

        // System metrics node with NEW naming conventions to match MetricsOrchestrator
        Map<String, Object> systemMetrics = new HashMap<>();

        // Convert CPU to percentages with new names
        double processCpu = realMetrics.getOrDefault("process_cpu_usage", 0.0);
        if (processCpu > 0.0001) {
            systemMetrics.put("quarkus_cpu_usage_percent", processCpu * 100);
        }

        double systemCpu = realMetrics.getOrDefault("system_cpu_usage", 0.0);
        if (systemCpu > 0.0001) {
            systemMetrics.put("system_cpu_usage_percent", systemCpu * 100);
        }
        systemMetrics.put("threads_peak", realMetrics.getOrDefault("jvm_threads_peak_threads", 0.0).intValue());
        systemMetrics.put("cpu_cores_available", 4);  // From real data

        // Calculate memory in MB to match MetricsOrchestrator
        double heapUsed = realMetrics.entrySet().stream()
                .filter(e -> e.getKey().startsWith("jvm_memory_used_bytes") && e.getKey().contains("area=\"heap\""))
                .mapToDouble(Map.Entry::getValue)
                .sum();
        if (heapUsed > 0) {
            systemMetrics.put("memory_heap_used_mb", (long) (heapUsed / (1024 * 1024)));
        }

        data.put("system", systemMetrics);

        // JWT validation success operations - extract from real data
        Map<String, Object> successOps = new HashMap<>();
        realMetrics.entrySet().stream()
                .filter(e -> e.getKey().startsWith("sheriff_oauth_validation_success_operations_total"))
                .forEach(e -> {
                    String eventType = extractEventType(e.getKey());
                    if (eventType != null) {
                        successOps.put(eventType, e.getValue());
                    }
                });
        data.put("sheriff_oauth_validation_success_operations_total", successOps);

        // JWT validation errors - extract from real data and structure as array
        List<Map<String, Object>> errors = new ArrayList<>();
        realMetrics.entrySet().stream()
                .filter(e -> e.getKey().startsWith("sheriff_oauth_validation_errors_total"))
                .sorted(Map.Entry.comparingByKey()) // Sort by metric name for consistent ordering
                .forEach(e -> {
                    String category = extractCategory(e.getKey());
                    String eventType = extractEventType(e.getKey());
                    if (category != null && eventType != null) {
                        errors.add(createErrorEntry(category, eventType, e.getValue()));
                    }
                });

        // Sort errors by category, then by event_type
        errors.sort((e1, e2) -> {
            int categoryCompare = ((String) e1.get("category")).compareTo((String) e2.get("category"));
            if (categoryCompare != 0) return categoryCompare;
            return ((String) e1.get("event_type")).compareTo((String) e2.get("event_type"));
        });

        data.put("sheriff_oauth_validation_errors", errors);
        return data;
    }

    private String extractEventType(String metricKey) {
        // Extract event_type from metric labels like: event_type="ACCESS_TOKEN_CREATED"
        int eventTypeStart = metricKey.indexOf("event_type=\"");
        if (eventTypeStart == -1) return null;
        eventTypeStart += "event_type=\"".length();
        int eventTypeEnd = metricKey.indexOf("\"", eventTypeStart);
        if (eventTypeEnd == -1) return null;
        return metricKey.substring(eventTypeStart, eventTypeEnd);
    }

    private String extractCategory(String metricKey) {
        // Extract category from metric labels like: category="INVALID_STRUCTURE"
        int categoryStart = metricKey.indexOf("category=\"");
        if (categoryStart == -1) return null;
        categoryStart += "category=\"".length();
        int categoryEnd = metricKey.indexOf("\"", categoryStart);
        if (categoryEnd == -1) return null;
        return metricKey.substring(categoryStart, categoryEnd);
    }

    private Map<String, Object> createErrorEntry(String category, String eventType, Double count) {
        Map<String, Object> error = new HashMap<>();
        error.put("category", category);
        error.put("event_type", eventType);
        error.put("count", count);
        return error;
    }
}