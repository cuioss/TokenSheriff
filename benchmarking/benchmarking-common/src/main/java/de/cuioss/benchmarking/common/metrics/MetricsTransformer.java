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

import de.cuioss.benchmarking.common.report.MetricConversionUtil;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transforms raw Prometheus metrics into structured Quarkus runtime metrics.
 * This class is responsible for all metric restructuring, renaming, and organization.
 *
 */
public class MetricsTransformer {

    private static final String AREA_HEAP = "area=\"heap\"";

    /**
     * Transforms raw Prometheus metrics into the structured Quarkus runtime metrics format.
     *
     * @param allMetrics Raw metrics from Prometheus endpoint
     * @return Structured metrics ready for JSON export
     */
    public Map<String, Object> transformToQuarkusRuntimeMetrics(Map<String, Double> allMetrics) {
        Map<String, Object> runtimeMetrics = new LinkedHashMap<>();

        // Add timestamp
        runtimeMetrics.put("timestamp", Instant.now().toString());

        // Transform and add the four main sections
        runtimeMetrics.put("system", createSystemMetrics(allMetrics));
        runtimeMetrics.put("http_server_requests", createHttpServerRequestsMetrics(allMetrics));
        runtimeMetrics.put("sheriff_token_validation_success_operations_total", createJwtValidationSuccessMetrics(allMetrics));
        runtimeMetrics.put("sheriff_token_validation_errors", createJwtValidationErrorsMetrics(allMetrics));

        return runtimeMetrics;
    }

    private Map<String, Object> createSystemMetrics(Map<String, Double> allMetrics) {
        Map<String, Object> systemMetrics = new LinkedHashMap<>();

        processCpuAndThreadMetrics(allMetrics, systemMetrics);
        processMemoryMetrics(allMetrics, systemMetrics);

        return systemMetrics;
    }

    private void processCpuAndThreadMetrics(Map<String, Double> allMetrics, Map<String, Object> systemMetrics) {
        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();

            if (metricName.startsWith("system_cpu_count")) {
                systemMetrics.put("cpu_cores_available", value.intValue());
            } else if (metricName.startsWith("system_load_average_1m")) {
                systemMetrics.put("cpu_load_average", formatNumber(value));
            } else if (metricName.startsWith("jdk_threads_peak_threads")) {
                systemMetrics.put("threads_peak", value.intValue());
            } else if (metricName.startsWith("process_cpu_usage") && value > 0) {
                systemMetrics.put("quarkus_cpu_usage_percent", formatNumber(value * 100));
            } else if (metricName.startsWith("system_cpu_usage") && value > 0) {
                systemMetrics.put("system_cpu_usage_percent", formatNumber(value * 100));
            } else if (metricName.startsWith("jvm_gc_overhead_percent") && value > 0) {
                systemMetrics.put("gc_overhead_percent", formatNumber(value * 100));
            }
        }
    }

    private void processMemoryMetrics(Map<String, Double> allMetrics, Map<String, Object> systemMetrics) {
        long totalHeapUsed = 0;
        long totalHeapCommitted = 0;
        long totalHeapMax = 0;
        long totalNonHeapUsed = 0;

        // Collect memory values
        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();

            if (metricName.startsWith("jvm_memory_used_bytes")) {
                if (metricName.contains(AREA_HEAP)) {
                    totalHeapUsed += value.longValue();
                } else if (metricName.contains("area=\"nonheap\"")) {
                    totalNonHeapUsed += value.longValue();
                }
            } else if (metricName.startsWith("jvm_memory_committed_bytes") && metricName.contains(AREA_HEAP)) {
                totalHeapCommitted += value.longValue();
            } else if (metricName.startsWith("jvm_memory_max_bytes") && metricName.contains(AREA_HEAP)) {
                long maxValue = value.longValue();
                if (maxValue > 0) {
                    totalHeapMax += maxValue;
                }
            }
        }

        // Add memory metrics
        addMemoryMetrics(systemMetrics, totalHeapUsed, totalHeapCommitted, totalHeapMax, totalNonHeapUsed);
    }

    private void addMemoryMetrics(Map<String, Object> systemMetrics, long totalHeapUsed,
            long totalHeapCommitted, long totalHeapMax, long totalNonHeapUsed) {
        if (totalHeapUsed > 0) {
            systemMetrics.put("memory_heap_used_mb", totalHeapUsed / (1024 * 1024));
        }
        if (totalNonHeapUsed > 0) {
            systemMetrics.put("memory_nonheap_used_mb", totalNonHeapUsed / (1024 * 1024));
        }
        if (totalHeapCommitted > 0 && totalHeapCommitted != totalHeapUsed) {
            long diff = Math.abs(totalHeapCommitted - totalHeapUsed);
            if (diff > totalHeapUsed * 0.1) {
                systemMetrics.put("memory_heap_committed_mb", totalHeapCommitted / (1024 * 1024));
            }
        }
        if (totalHeapMax > 0) {
            systemMetrics.put("memory_heap_max_mb", totalHeapMax / (1024 * 1024));
        }
        if (totalHeapUsed > 0 && totalNonHeapUsed > 0) {
            long totalMemoryUsed = totalHeapUsed + totalNonHeapUsed;
            systemMetrics.put("memory_total_used_mb", totalMemoryUsed / (1024 * 1024));
        }
    }

    private Map<String, Object> createHttpServerRequestsMetrics(Map<String, Double> allMetrics) {
        Map<String, Object> httpMetrics = new LinkedHashMap<>();

        double sumSeconds = 0;
        double maxSeconds = 0;

        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();

            if (metricName.startsWith("http_server_requests_seconds")) {
                if (metricName.contains("_count")) {
                    long count = value.longValue();
                    httpMetrics.put("total_requests", count);
                } else if (metricName.contains("_sum")) {
                    sumSeconds = value;
                } else if (metricName.contains("_max")) {
                    maxSeconds = value;
                    // Format the value for display
                    if (maxSeconds > 0) {
                        httpMetrics.put("max_duration_seconds", formatNumber(maxSeconds));
                    }
                }
            }
        }

        // Only include the sum if it exists - format for display
        if (sumSeconds > 0) {
            httpMetrics.put("total_duration_seconds", formatNumber(sumSeconds));
        }

        return httpMetrics;
    }

    private Map<String, Object> createJwtValidationSuccessMetrics(Map<String, Double> allMetrics) {
        Map<String, Object> successMetrics = new LinkedHashMap<>();

        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();

            if (metricName.startsWith("sheriff_token_validation_success_operations_total")) {
                String eventType = extractEventType(metricName);
                if (eventType != null && value > 0) {
                    successMetrics.put(eventType, value.longValue());
                }
            }
        }

        return successMetrics;
    }

    private Map<String, Object> createJwtValidationErrorsMetrics(Map<String, Double> allMetrics) {
        Map<String, Object> errorsMap = new LinkedHashMap<>();

        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();

            if (metricName.startsWith("sheriff_token_validation_errors_total")) {
                String category = extractCategory(metricName);
                String eventType = extractEventType(metricName);

                if (category != null && eventType != null) {
                    String key = category + "_" + eventType;
                    Map<String, Object> errorEntry = new LinkedHashMap<>();
                    errorEntry.put("category", category);
                    errorEntry.put("event_type", eventType);
                    errorEntry.put("count", value.longValue());
                    errorsMap.put(key, errorEntry);
                }
            }
        }

        return errorsMap;
    }

    private String extractCategory(String metricName) {
        int catStart = metricName.indexOf("category=\"");
        if (catStart != -1) {
            catStart += "category=\"".length();
            int catEnd = metricName.indexOf("\"", catStart);
            if (catEnd != -1) {
                return metricName.substring(catStart, catEnd);
            }
        }
        return null;
    }

    private String extractEventType(String metricName) {
        int typeStart = metricName.indexOf("event_type=\"");
        if (typeStart != -1) {
            typeStart += "event_type=\"".length();
            int typeEnd = metricName.indexOf("\"", typeStart);
            if (typeEnd != -1) {
                return metricName.substring(typeStart, typeEnd);
            }
        }
        return null;
    }

    /**
     * Formats numeric values for consistent display using MetricConversionUtil rules.
     *
     * @param value the value to format
     * @return formatted value as Object (String or Number)
     */
    private Object formatNumber(double value) {
        // Use MetricConversionUtil for consistent formatting
        String formatted = MetricConversionUtil.formatForDisplay(value);
        // Try to parse back to appropriate type
        try {
            if (formatted.contains(".")) {
                return Double.parseDouble(formatted);
            } else {
                return Long.parseLong(formatted);
            }
        } catch (NumberFormatException e) {
            // Fallback to string if parsing fails
            return formatted;
        }
    }
}