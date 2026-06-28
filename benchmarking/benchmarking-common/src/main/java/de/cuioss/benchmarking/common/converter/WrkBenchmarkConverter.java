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
package de.cuioss.benchmarking.common.converter;

import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.benchmarking.common.model.BenchmarkData;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.ERROR.FAILED_PARSE_WRK_FILE;

/**
 * Converts WRK benchmark output to the central BenchmarkData model
 */
@SuppressWarnings("java:S5852") // ok for test-data
public class WrkBenchmarkConverter implements BenchmarkConverter {

    private static final CuiLogger LOGGER = new CuiLogger(WrkBenchmarkConverter.class);

    // Regex patterns for parsing WRK output
    private static final Pattern REQUESTS_PER_SEC = Pattern.compile("Requests/sec:\\s+([\\d.]+)");
    private static final Pattern LATENCY_STATS = Pattern.compile(
            "Latency\\s+([\\d.]+)(\\w+)\\s+([\\d.]+)(\\w+)\\s+([\\d.]+)(\\w+)\\s+([\\d.]+)%");
    private static final Pattern LATENCY_PERCENTILE = Pattern.compile(
            "\\s+(\\d+(?:\\.\\d+)?)%\\s+([\\d.]+)(\\w+)");

    @Override
    public BenchmarkData convert(Path sourcePath) throws IOException {
        if (sourcePath.toFile().isDirectory()) {
            // Convert all WRK output files in directory
            return convertDirectory(sourcePath);
        } else {
            // Convert single WRK output file
            return convertFile(sourcePath);
        }
    }

    @Override
    public boolean canConvert(Path sourcePath) {
        if (sourcePath.toFile().isDirectory()) {
            return sourcePath.getFileName().toString().contains("wrk");
        }
        String fileName = sourcePath.getFileName().toString();
        return fileName.contains("wrk") && fileName.endsWith(".txt");
    }

    private BenchmarkData convertDirectory(Path dir) throws IOException {
        List<BenchmarkData.Benchmark> benchmarks = new ArrayList<>();

        // Process all .txt files in the directory (should only contain WRK results)
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".txt"))
                    .forEach(file -> {
                        try {
                            LOGGER.debug("Processing WRK result file: " + file.getFileName());
                            BenchmarkData.Benchmark benchmark = parseWrkFile(file);
                            if (benchmark != null) {
                                benchmarks.add(benchmark);
                            }
                        } catch (IOException e) {
                            LOGGER.error(e, FAILED_PARSE_WRK_FILE, file);
                        }
                    });
        }

        return BenchmarkData.builder()
                .metadata(createMetadata())
                .overview(createOverview(benchmarks))
                .benchmarks(benchmarks)
                .build();
    }

    private BenchmarkData convertFile(Path file) throws IOException {
        BenchmarkData.Benchmark benchmark = parseWrkFile(file);
        List<BenchmarkData.Benchmark> benchmarks = benchmark != null ?
                List.of(benchmark) : List.of();

        return BenchmarkData.builder()
                .metadata(createMetadata())
                .overview(createOverview(benchmarks))
                .benchmarks(benchmarks)
                .build();
    }

    private BenchmarkData.Benchmark parseWrkFile(Path file) throws IOException {
        String content = Files.readString(file);
        String name = extractBenchmarkName(file);

        // Parse requests per second (throughput)
        double requestsPerSec = 0;
        Matcher m = REQUESTS_PER_SEC.matcher(content);
        if (m.find()) {
            requestsPerSec = Double.parseDouble(m.group(1));
        }

        // Parse latency statistics
        double latencyAvg = 0;
        double latencyStdev = 0;
        m = LATENCY_STATS.matcher(content);
        if (m.find()) {
            latencyAvg = convertToMs(Double.parseDouble(m.group(1)), m.group(2));
            latencyStdev = convertToMs(Double.parseDouble(m.group(3)), m.group(4));
        }

        // Parse percentiles
        Map<String, Double> percentiles = new LinkedHashMap<>();
        m = LATENCY_PERCENTILE.matcher(content);
        while (m.find()) {
            double percentile = Double.parseDouble(m.group(1));
            double value = convertToMs(Double.parseDouble(m.group(2)), m.group(3));
            percentiles.put(String.valueOf(percentile), value);
        }

        // Ensure standard percentiles exist
        ensureStandardPercentiles(percentiles, latencyAvg, latencyStdev);

        return BenchmarkData.Benchmark.builder()
                .name(name)
                .fullName("wrk." + name)
                .mode("thrpt")
                .rawScore(requestsPerSec)
                .score(formatThroughput(requestsPerSec))
                .scoreUnit("ops/s")
                .throughput(formatThroughput(requestsPerSec))
                .latency(formatLatency(latencyAvg))
                .error(latencyStdev)
                .variabilityCoefficient(latencyAvg > 0 ? (latencyStdev / latencyAvg * 100) : 0)
                .confidenceLow(Math.max(0, latencyAvg - latencyStdev))
                .confidenceHigh(latencyAvg + latencyStdev)
                .percentiles(percentiles)
                .build();
    }

    private String extractBenchmarkName(Path file) {
        String fileName = file.getFileName().toString();

        // First, try to extract from embedded metadata if available
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                if (line.startsWith("benchmark_name: ")) {
                    return line.substring(16).trim();
                }
                // Stop looking after WRK output starts
                if ("=== WRK OUTPUT ===".equals(line)) {
                    break;
                }
            }
        } catch (IOException e) {
            LOGGER.debug("Could not read metadata from file: " + file, e);
        }

        // Fallback to filename-based extraction
        if (fileName.contains("jwt")) {
            return "jwtValidation";
        } else if (fileName.contains("health-live")) {
            return "healthLiveCheck";
        } else if (fileName.contains("health")) {
            return "healthCheck";
        }
        return fileName.replace("-results.txt", "").replace("wrk-", "").replace("-output", "");
    }

    private double convertToMs(double value, String unit) {
        return switch (unit.toLowerCase()) {
            case "us" -> value / 1000.0;
            case "s" -> value * 1000.0;
            case "m" -> value * 60000.0;
            default -> value; // Handles "ms" and any other units as-is
        };
    }

    private void ensureStandardPercentiles(Map<String, Double> percentiles, double avg, double stdev) {
        String[] standard = {"0.0", "50.0", "90.0", "95.0", "99.0", "99.9", "99.99", "100.0"};

        for (String p : standard) {
            if (percentiles.containsKey(p)) {
                continue;
            }
            double percentileValue = Double.parseDouble(p);
            // Simple estimation based on normal distribution
            double estimated = estimatePercentile(percentileValue, avg, stdev);
            percentiles.put(p, estimated);
        }
    }

    private double estimatePercentile(double percentile, double avg, double stdev) {
        if (percentile <= 50) {
            return Math.max(0, avg - stdev * (50 - percentile) / 50);
        } else {
            return avg + stdev * (percentile - 50) / 50 * 2;
        }
    }

    private BenchmarkData.Metadata createMetadata() {
        Instant now = Instant.now();
        return BenchmarkData.Metadata.builder()
                .timestamp(now.toString())
                .displayTimestamp(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
                        .withZone(ZoneOffset.UTC)
                        .format(now))
                .benchmarkType(BenchmarkType.INTEGRATION.getDisplayName())
                .reportVersion("2.0")
                .build();
    }

    private BenchmarkData.Overview createOverview(List<BenchmarkData.Benchmark> benchmarks) {
        // Find JWT benchmark (primary)
        BenchmarkData.Benchmark primary = benchmarks.stream()
                .filter(b -> b.getName().contains("jwt") || b.getName().contains("Validation"))
                .findFirst()
                .orElse(benchmarks.isEmpty() ? null : benchmarks.getFirst());

        if (primary == null) {
            return BenchmarkData.Overview.builder()
                    .throughput("N/A")
                    .latency("N/A")
                    .throughputOpsPerSec(0.0)
                    .latencyMs(0.0)
                    .performanceScore(0)
                    .performanceGrade("F")
                    .performanceGradeClass("grade-f")
                    .build();
        }

        double throughput = primary.getRawScore();
        double latencyMs = primary.getPercentiles().getOrDefault("50.0", 100.0);
        int score = calculatePerformanceScore(throughput, latencyMs);
        String grade = calculatePerformanceGrade(score);

        return BenchmarkData.Overview.builder()
                .throughput(primary.getThroughput())
                .latency(primary.getLatency())
                .throughputOpsPerSec(throughput)  // Store numeric value used for score calculation
                .latencyMs(latencyMs)             // Store numeric value used for score calculation
                .throughputBenchmarkName(primary.getName())
                .latencyBenchmarkName(primary.getName())
                .performanceScore(score)
                .performanceGrade(grade)
                .performanceGradeClass("grade-" + grade.toLowerCase())
                .build();
    }

    private String formatThroughput(double reqPerSec) {
        if (reqPerSec >= 1000) {
            return String.format(Locale.US, "%.1fK ops/s", reqPerSec / 1000);
        }
        return String.format(Locale.US, "%.0f ops/s", reqPerSec);
    }

    private String formatLatency(double ms) {
        if (ms < 1) {
            return String.format(Locale.US, "%.0fμs", ms * 1000);
        }
        return String.format(Locale.US, "%.1fms", ms);
    }

    private int calculatePerformanceScore(double throughput, double latencyMs) {
        // Score based on throughput (0-50 points) and latency (0-50 points)
        int throughputScore = (int) Math.min(50, throughput / 200);
        int latencyScore = (int) Math.max(0, 50 - latencyMs / 2);
        return throughputScore + latencyScore;
    }

    private String calculatePerformanceGrade(int score) {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }
}