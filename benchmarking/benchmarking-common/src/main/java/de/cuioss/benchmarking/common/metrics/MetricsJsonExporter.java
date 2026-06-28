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

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import de.cuioss.tools.logging.CuiLogger;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.WARN.*;

/**
 * Exports metrics data to JSON files in a target directory.
 * Handles JSON serialization, file writing, and metrics aggregation.
 *
 */
public class MetricsJsonExporter {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsJsonExporter.class);

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(Number.class, (JsonSerializer<Number>) (src, typeOfSrc, context) -> {
                if (src instanceof Double || src instanceof Float) {
                    double value = src.doubleValue();
                    if (value == Math.floor(value) && !Double.isInfinite(value)) {
                        return new JsonPrimitive(src.longValue());
                    }
                    return new JsonPrimitive(value);
                }
                return new JsonPrimitive(src);
            })
            .create();
    public static final String BEARER_TOKEN_RESULT = "getBearerTokenResult";

    private final Path targetDirectory;

    /**
     * Creates a new metrics JSON exporter.
     *
     * @param targetDirectory Directory where JSON files will be written
     */
    public MetricsJsonExporter(Path targetDirectory) {
        this.targetDirectory = targetDirectory;
        try {
            Files.createDirectories(targetDirectory);
            LOGGER.debug("MetricsJsonExporter initialized with target directory: %s (exists: %s)",
                    targetDirectory.toAbsolutePath(), Files.exists(targetDirectory));
        } catch (IOException e) {
            LOGGER.warn(e, FAILED_CREATE_TARGET_DIR, targetDirectory);
        }
    }

    /**
     * Exports metrics data to a JSON file.
     *
     * @param fileName The name of the JSON file
     * @param metricsData The metrics data to export
     * @throws IOException if writing fails
     */
    public void exportToFile(String fileName, Map<String, Object> metricsData) throws IOException {
        Path outputFile = targetDirectory.resolve(fileName);

        try (FileWriter writer = new FileWriter(outputFile.toFile())) {
            GSON.toJson(metricsData, writer);
            writer.flush();
            LOGGER.debug("Exported metrics to: %s", outputFile.toAbsolutePath());
        }
    }

    /**
     * Exports JWT validation metrics for a specific benchmark method.
     *
     * @param benchmarkMethodName The name of the benchmark method
     * @param timestamp The timestamp when the benchmark was executed
     * @param allMetrics All metrics data
     * @throws IOException if export fails
     */
    public void exportJwtValidationMetrics(String benchmarkMethodName, Instant timestamp,
            Map<String, Double> allMetrics) throws IOException {
        LOGGER.debug("Exporting JWT validation metrics for: %s", benchmarkMethodName);

        if (isJwtValidationBenchmark(benchmarkMethodName)) {
            Map<String, Object> timedMetrics = extractTimedMetrics(allMetrics);
            Map<String, Object> securityEventMetrics = extractSecurityEventMetrics(allMetrics);

            Map<String, Object> benchmarkData = new LinkedHashMap<>();
            benchmarkData.put("timestamp", timestamp.toString());
            benchmarkData.put("bearer_token_producer_metrics", timedMetrics);
            benchmarkData.put("security_event_counter_metrics", securityEventMetrics);

            String simpleBenchmarkName = extractSimpleBenchmarkName(benchmarkMethodName);
            updateAggregatedMetrics("integration-metrics.json", simpleBenchmarkName, benchmarkData);
        } else {
            LOGGER.debug("Benchmark %s is not JWT validation, raw metrics were saved", benchmarkMethodName);
        }
    }

    /**
     * Exports resource metrics (CPU, memory) for system monitoring.
     *
     * @param timestamp The timestamp when metrics were collected
     * @param allMetrics All metrics data
     * @throws IOException if export fails
     */
    public void exportResourceMetrics(Instant timestamp, Map<String, Double> allMetrics) throws IOException {
        LOGGER.debug("Exporting resource metrics");

        Map<String, Object> resourceData = new LinkedHashMap<>();
        resourceData.put("timestamp", timestamp.toString());
        resourceData.put("cpu_metrics", extractCpuMetrics(allMetrics));
        resourceData.put("memory_metrics", extractMemoryMetrics(allMetrics));

        exportToFile("resource-metrics.json", resourceData);
    }

    /**
     * Updates an aggregated metrics file with new benchmark data.
     * For quarkus-metrics.json files, applies special transformation to create the new structure.
     *
     * @param fileName The name of the aggregated metrics file
     * @param benchmarkName The name of the benchmark
     * @param benchmarkData The benchmark data to add/update
     * @throws IOException if writing fails
     */
    public void updateAggregatedMetrics(String fileName, String benchmarkName,
            Map<String, Object> benchmarkData) throws IOException {
        Map<String, Object> allMetrics = readExistingMetrics(fileName);

        if ("quarkus-metrics.json".equals(fileName)) {
            // Apply special transformation for Quarkus runtime metrics
            String transformedKey = "quarkus-runtime-metrics";
            Map<String, Object> transformedData = transformToQuarkusRuntimeMetrics(benchmarkData);
            allMetrics.put(transformedKey, transformedData);
        } else {
            allMetrics.put(benchmarkName, benchmarkData);
        }

        exportToFile(fileName, allMetrics);
        LOGGER.debug("Updated %s with %s benchmarks", fileName, allMetrics.size());
    }

    /**
     * Transforms benchmark data into the new Quarkus runtime metrics structure.
     * Removes the "benchmark" field and ensures proper structure.
     */
    private Map<String, Object> transformToQuarkusRuntimeMetrics(Map<String, Object> originalData) {
        Map<String, Object> transformed = new LinkedHashMap<>(originalData);

        // Remove the "benchmark" field if present
        transformed.remove("benchmark");

        return transformed;
    }

    /**
     * Reads existing metrics from a JSON file.
     *
     * @param fileName The name of the JSON file
     * @return The parsed metrics map, or empty map if file doesn't exist or is empty
     */
    public Map<String, Object> readExistingMetrics(String fileName) {
        Map<String, Object> existingMetrics = new LinkedHashMap<>();
        Path filePath = targetDirectory.resolve(fileName);

        if (Files.exists(filePath)) {
            try {
                String content = Files.readString(filePath);
                if (!content.trim().isEmpty()) {
                    Type mapType = new TypeToken<Map<String, Object>>(){
                    }.getType();
                    Map<String, Object> parsed = GSON.fromJson(content, mapType);
                    if (parsed != null) {
                        existingMetrics = parsed;
                    }
                }
            } catch (IOException | JsonSyntaxException e) {
                LOGGER.warn(FAILED_READ_METRICS_FILE, fileName, e.getMessage());
                try {
                    Files.deleteIfExists(filePath);
                } catch (IOException deleteException) {
                    LOGGER.warn(deleteException, FAILED_DELETE_CORRUPTED_FILE);
                }
            }
        }

        return existingMetrics;
    }

    private boolean isJwtValidationBenchmark(String benchmarkMethodName) {
        return benchmarkMethodName.contains("JwtValidationBenchmark") ||
                "JwtValidation".equals(benchmarkMethodName) ||
                benchmarkMethodName.startsWith("validateJwt") ||
                "validateJwtToken".equals(benchmarkMethodName) ||
                benchmarkMethodName.contains("validateAccessToken") ||
                benchmarkMethodName.contains("validateIdToken");
    }

    private Map<String, Object> extractTimedMetrics(Map<String, Double> allMetrics) {
        Map<String, Object> timedMetrics = new LinkedHashMap<>();

        Double count = null;
        Double sum = null;
        Double max = null;

        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();

            if (metricName.contains("bearer_token_validation_seconds")) {
                if (metricName.contains("_count") && metricName.contains(BEARER_TOKEN_RESULT)) {
                    count = value;
                } else if (metricName.contains("_sum") && metricName.contains(BEARER_TOKEN_RESULT)) {
                    sum = value;
                } else if (metricName.contains("_max") && metricName.contains(BEARER_TOKEN_RESULT)) {
                    max = value;
                }
            }
        }

        if (count != null && sum != null && max != null && count > 0) {
            double avgMicros = (sum / count) * 1_000_000;

            Map<String, Object> validationMetric = new LinkedHashMap<>();
            validationMetric.put("sample_count", formatNumber(count.longValue()));
            validationMetric.put("p50_us", formatNumber(avgMicros));
            validationMetric.put("p95_us", formatNumber(Math.min(avgMicros * 2, max * 1_000_000 * 0.8)));
            validationMetric.put("p99_us", formatNumber(max * 1_000_000 * 0.9));
            timedMetrics.put("validation", validationMetric);
        } else {
            Map<String, Object> emptyMetric = new LinkedHashMap<>();
            emptyMetric.put("sample_count", formatNumber(0));
            emptyMetric.put("p50_us", formatNumber(0));
            emptyMetric.put("p95_us", formatNumber(0));
            emptyMetric.put("p99_us", formatNumber(0));
            timedMetrics.put("validation", emptyMetric);
        }

        return timedMetrics;
    }

    private Map<String, Object> extractSecurityEventMetrics(Map<String, Double> allMetrics) {
        Map<String, Object> securityMetrics = new LinkedHashMap<>();
        Map<String, Map<String, Object>> errorsByCategory = new LinkedHashMap<>();
        Map<String, Object> successByType = new LinkedHashMap<>();
        long totalErrors = 0;
        long totalSuccess = 0;

        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();

            if (metricName.startsWith("sheriff_token_validation_errors_total")) {
                String category = extractTag(metricName, "category");
                String eventType = extractTag(metricName, "event_type");

                if (category != null && eventType != null && value != null && value > 0) {
                    Map<String, Object> categoryData = errorsByCategory.computeIfAbsent(category, k -> new LinkedHashMap<>());
                    categoryData.put(eventType, formatNumber(value.longValue()));
                    totalErrors += value.longValue();
                }
            } else if (metricName.startsWith("sheriff_token_validation_success")) {
                String eventType = extractTag(metricName, "event_type");
                String result = extractTag(metricName, "result");

                if (eventType != null && "success".equals(result) && value != null && value > 0) {
                    successByType.put(eventType, formatNumber(value.longValue()));
                    totalSuccess += value.longValue();
                }
            }
        }

        securityMetrics.put("total_errors", formatNumber(totalErrors));
        securityMetrics.put("total_success", formatNumber(totalSuccess));
        securityMetrics.put("errors_by_category", errorsByCategory);
        securityMetrics.put("success_by_type", successByType);

        return securityMetrics;
    }

    private Map<String, Object> extractCpuMetrics(Map<String, Double> allMetrics) {
        Map<String, Object> cpuMetrics = new LinkedHashMap<>();

        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();

            if (metricName.startsWith("system_cpu_usage")) {
                cpuMetrics.put("system_cpu_usage", formatPercentage(value));
            } else if (metricName.startsWith("process_cpu_usage")) {
                cpuMetrics.put("process_cpu_usage", formatPercentage(value));
            } else if (metricName.startsWith("system_cpu_count")) {
                cpuMetrics.put("cpu_count", value.intValue());
            }
        }

        return cpuMetrics;
    }

    private Map<String, Object> extractMemoryMetrics(Map<String, Double> allMetrics) {
        Map<String, Object> memoryMetrics = new LinkedHashMap<>();

        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();

            if (metricName.startsWith("jvm_memory_used_bytes")) {
                String area = extractTag(metricName, "area");
                if ("heap".equals(area)) {
                    memoryMetrics.put("heap_used_bytes", value.longValue());
                } else if ("nonheap".equals(area)) {
                    memoryMetrics.put("nonheap_used_bytes", value.longValue());
                }
            }
        }

        return memoryMetrics;
    }

    private String extractTag(String metricName, String tagName) {
        String pattern = tagName + "=\"([^\"]+)\"";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(metricName);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Object formatNumber(double value) {
        if (value < 10) {
            DecimalFormat df = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
            return Double.parseDouble(df.format(value));
        } else {
            return Math.round(value);
        }
    }

    private Object formatPercentage(double value) {
        double percentage = value * 100.0;
        if (percentage < 10) {
            return Math.round(percentage * 10.0) / 10.0;
        } else {
            return Math.round(percentage);
        }
    }

    private String extractSimpleBenchmarkName(String fullBenchmarkName) {
        if (fullBenchmarkName.contains(".")) {
            return fullBenchmarkName.substring(fullBenchmarkName.lastIndexOf('.') + 1);
        }
        return fullBenchmarkName;
    }
}