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

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO.ITERATION_WINDOWS_PARSED;
import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.WARN.FAILED_PARSE_TIMESTAMP_LINE;
import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.WARN.TIMESTAMP_FILE_NOT_EXIST;

/**
 * Parses JMH iteration timestamp files to extract precise timing windows
 * for Prometheus metrics collection.
 * <p>
 * Supports JSONL format (one JSON object per line) from TimestampProfiler.
 */
public class IterationTimestampParser {

    private static final CuiLogger LOGGER = new CuiLogger(IterationTimestampParser.class);

    /**
     * Represents a time window for a benchmark iteration
     */
    public record IterationWindow(
    String benchmarkName,
    String forkId,
    String iterationType,
    int iterationNumber,
    Instant startTime,
    Instant endTime,
    long durationMs
    ) {
        public boolean isMeasurement() {
            return "MEASUREMENT".equalsIgnoreCase(iterationType);
        }

        public boolean isWarmup() {
            return "WARMUP".equalsIgnoreCase(iterationType);
        }
    }

    /**
     * Parses JSONL timestamp file (one JSON object per line)
     */
    public static List<IterationWindow> parseJsonlFile(Path timestampFile) throws IOException {
        if (!Files.exists(timestampFile)) {
            LOGGER.warn(TIMESTAMP_FILE_NOT_EXIST, timestampFile);
            return List.of();
        }

        List<IterationWindow> windows = new ArrayList<>();

        try (var lines = Files.lines(timestampFile)) {
            lines.forEach(line -> {
                if (line.trim().isEmpty()) return;

                try {
                    JsonObject node = JsonParser.parseString(line).getAsJsonObject();
                    windows.add(parseIterationNode(node));
                } catch (JsonParseException | IllegalStateException e) {
                    // JsonParseException: malformed JSON
                    // IllegalStateException: not a JSON object (getAsJsonObject on wrong type)
                    LOGGER.warn(e, FAILED_PARSE_TIMESTAMP_LINE, line);
                }
            });
        }

        LOGGER.info(ITERATION_WINDOWS_PARSED, windows.size(), timestampFile);
        return windows;
    }

    /**
     * Parses a single iteration node
     */
    private static IterationWindow parseIterationNode(JsonObject node) {
        return parseIterationNode(node, node.get("benchmark").getAsString());
    }

    /**
     * Parses a single iteration node with explicit benchmark name
     */
    private static IterationWindow parseIterationNode(JsonObject node, String benchmarkName) {
        return new IterationWindow(
                benchmarkName,
                node.get("fork").getAsString(),
                node.get("type").getAsString(),
                node.get("iteration").getAsInt(),
                Instant.parse(node.get("start").getAsString()),
                Instant.parse(node.get("end").getAsString()),
                node.get("duration_ms").getAsLong()
        );
    }

}