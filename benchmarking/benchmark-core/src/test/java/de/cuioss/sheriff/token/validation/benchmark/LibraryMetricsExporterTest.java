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
package de.cuioss.sheriff.token.validation.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.cuioss.sheriff.token.validation.metrics.MeasurementType;
import de.cuioss.sheriff.token.validation.metrics.TokenValidatorMonitor;
import de.cuioss.sheriff.token.validation.metrics.TokenValidatorMonitorConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class LibraryMetricsExporterTest {

    private static final Path METRICS_FILE = Path.of("target/benchmark-results/jwt-validation-metrics.json");

    private final Gson gson = new GsonBuilder().create();

    @Test
    void shouldExportMetricsToJsonFile() throws Exception {
        // Given
        TokenValidatorMonitor monitor = TokenValidatorMonitorConfig.builder()
                .measurementType(MeasurementType.TOKEN_PARSING)
                .measurementType(MeasurementType.HEADER_VALIDATION)
                .build()
                .createMonitor();

        monitor.recordMeasurement(MeasurementType.TOKEN_PARSING, TimeUnit.MILLISECONDS.toNanos(10));
        monitor.recordMeasurement(MeasurementType.HEADER_VALIDATION, TimeUnit.MILLISECONDS.toNanos(5));

        // When
        LibraryMetricsExporter.exportMetrics("test-benchmark", monitor);

        // Then - metrics are exported to hardcoded target/benchmark-results
        Path resultsDir = Path.of("target/benchmark-results");
        Path[] jsonFiles;
        try (var files = Files.list(resultsDir)) {
            jsonFiles = files.filter(p -> p.toString().endsWith(".json")).toArray(Path[]::new);
        }

        assertEquals(1, jsonFiles.length);
        assertEquals("jwt-validation-metrics.json", jsonFiles[0].getFileName().toString());

        Map<String, Object> allMetrics = readAllMetrics();

        assertFalse(allMetrics.isEmpty());

        Map<String, Object> benchmarkMetrics = benchmarkEntry(allMetrics, "test-benchmark");

        // Verify JSON structure for the benchmark
        assertNotNull(benchmarkMetrics.get("timestamp"));
        assertNotNull(benchmarkMetrics.get("steps"));

        Map<String, Map<String, Object>> steps = steps(benchmarkMetrics);
        assertTrue(steps.containsKey("token_parsing"));
        assertTrue(steps.containsKey("header_validation"));

        // Verify metrics exist
        Map<String, Object> parseMetrics = steps.get("token_parsing");
        assertNotNull(parseMetrics.get("sample_count"));
        assertNotNull(parseMetrics.get("p50_us"));
        assertNotNull(parseMetrics.get("p95_us"));
        assertNotNull(parseMetrics.get("p99_us"));
    }

    @Test
    void shouldFormatNumbersCorrectly() throws Exception {
        // Given
        TokenValidatorMonitor monitor = TokenValidatorMonitorConfig.builder()
                .measurementType(MeasurementType.TOKEN_PARSING)
                .measurementType(MeasurementType.SIGNATURE_VALIDATION)
                .build()
                .createMonitor();

        // Simulate measurements with values that would result in < 10 and >= 10 microseconds
        // Small measurement < 10 microseconds (e.g., 7.6)
        monitor.recordMeasurement(MeasurementType.TOKEN_PARSING, 7600); // 7.6 microseconds

        // Large measurement >= 10 microseconds (e.g., 13)
        monitor.recordMeasurement(MeasurementType.SIGNATURE_VALIDATION, 13000); // 13 microseconds

        // When
        LibraryMetricsExporter.exportMetrics("test-benchmark", monitor);

        // Then - metrics are exported to hardcoded target/benchmark-results
        String jsonContent = Files.readString(METRICS_FILE);

        // Verify that values < 10 have one decimal place
        assertTrue(jsonContent.contains("7.6"), "Values < 10 should have one decimal place");

        // Verify that values >= 10 are integers without decimal point
        assertTrue(jsonContent.contains("\"p50_us\": 13"), "Values >= 10 should be integers without decimal");
    }

    @Test
    void shouldExportMeasurementsOfAllThreadsRecordedIntoOneSharedMonitor() throws Exception {
        int threadCount = 4;
        int measurementsPerThread = 50;
        TokenValidatorMonitor sharedMonitor = TokenValidatorMonitorConfig.builder()
                .windowSize(10000)
                .measurementType(MeasurementType.TOKEN_PARSING)
                .build()
                .createMonitor();
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> workers = new ArrayList<>();
        try {
            for (int thread = 0; thread < threadCount; thread++) {
                workers.add(executor.submit(() -> {
                    startGate.await();
                    for (int i = 0; i < measurementsPerThread; i++) {
                        sharedMonitor.recordMeasurement(MeasurementType.TOKEN_PARSING,
                                TimeUnit.MICROSECONDS.toNanos(20));
                    }
                    return null;
                }));
            }
            startGate.countDown();
            for (Future<?> worker : workers) {
                worker.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        LibraryMetricsExporter.exportMetrics("SharedStateBenchmark.measureAggregation", sharedMonitor);

        Map<String, Map<String, Object>> steps =
                steps(benchmarkEntry(readAllMetrics(), "SharedStateBenchmark.measureAggregation"));
        assertEquals((long) threadCount * measurementsPerThread,
                ((Number) steps.get("token_parsing").get("sample_count")).longValue(),
                "Exported sample count must cover the measurements of every thread");
    }

    @Test
    void shouldKeepTheMetricsOfEachBenchmarkMethodSeparate() throws Exception {
        TokenValidatorMonitor throughputMonitor = TokenValidatorMonitorConfig.builder()
                .measurementType(MeasurementType.TOKEN_PARSING)
                .build()
                .createMonitor();
        throughputMonitor.recordMeasurement(MeasurementType.TOKEN_PARSING, TimeUnit.MICROSECONDS.toNanos(20));
        TokenValidatorMonitor averageTimeMonitor = TokenValidatorMonitorConfig.builder()
                .measurementType(MeasurementType.SIGNATURE_VALIDATION)
                .build()
                .createMonitor();
        averageTimeMonitor.recordMeasurement(MeasurementType.SIGNATURE_VALIDATION, TimeUnit.MICROSECONDS.toNanos(30));

        LibraryMetricsExporter.exportMetrics("KeyIsolationBenchmark.measureThroughput", throughputMonitor);
        LibraryMetricsExporter.exportMetrics("KeyIsolationBenchmark.measureAverageTime", averageTimeMonitor);

        Map<String, Object> allMetrics = readAllMetrics();
        Map<String, Map<String, Object>> throughputSteps =
                steps(benchmarkEntry(allMetrics, "KeyIsolationBenchmark.measureThroughput"));
        Map<String, Map<String, Object>> averageTimeSteps =
                steps(benchmarkEntry(allMetrics, "KeyIsolationBenchmark.measureAverageTime"));
        assertAll("Each benchmark method keeps its own step data",
                () -> assertTrue(throughputSteps.containsKey("token_parsing")),
                () -> assertFalse(throughputSteps.containsKey("signature_validation")),
                () -> assertTrue(averageTimeSteps.containsKey("signature_validation")),
                () -> assertFalse(averageTimeSteps.containsKey("token_parsing")));
    }

    private Map<String, Object> readAllMetrics() throws IOException {
        TypeToken<Map<String, Object>> typeToken = new TypeToken<>() {
        };
        return gson.fromJson(Files.readString(METRICS_FILE), typeToken.getType());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> benchmarkEntry(Map<String, Object> allMetrics, String key) {
        Object entry = allMetrics.get(key);
        assertNotNull(entry, "Metrics file must contain an entry for " + key);
        return (Map<String, Object>) entry;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> steps(Map<String, Object> benchmarkMetrics) {
        return (Map<String, Map<String, Object>>) benchmarkMetrics.get("steps");
    }
}
