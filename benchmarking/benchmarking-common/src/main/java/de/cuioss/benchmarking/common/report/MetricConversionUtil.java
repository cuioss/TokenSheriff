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
package de.cuioss.benchmarking.common.report;

import java.util.Locale;

import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Metrics.Conversions;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Metrics.Units;

/**
 * Central utility for converting benchmark metrics between different units.
 * This is the SINGLE source of truth for all metric conversions.
 */
public final class MetricConversionUtil {

    private MetricConversionUtil() {
        // Utility class
    }

    /**
     * Converts a benchmark score to operations per second.
     * 
     * @param score the raw score value
     * @param unit the unit string from JMH
     * @return throughput in operations per second
     */
    public static double convertToOpsPerSecond(double score, String unit) {
        // Handle throughput units directly
        // IMPORTANT: Check more specific units first to avoid partial matches!
        if (unit.contains(Units.OPS_PER_NS)) {
            return score * Conversions.NANOS_TO_SECONDS;
        } else if (unit.contains(Units.OPS_PER_US)) {
            return score * Conversions.MICROS_TO_SECONDS;
        } else if (unit.contains(Units.OPS_PER_MS)) {
            return score * Conversions.MILLIS_TO_SECONDS;
        } else if (unit.contains(Units.OPS_PER_SEC) || unit.contains(Units.OPS_PER_SEC_ALT)) {
            return score;
        } else if (unit.contains(Units.NS_PER_OP)) {
            return Conversions.NANOS_TO_SECONDS / score;
        } else if (unit.contains(Units.US_PER_OP)) {
            return Conversions.MICROS_TO_SECONDS / score;
        } else if (unit.contains(Units.MS_PER_OP)) {
            return Conversions.MILLIS_TO_SECONDS / score;
        } else if (unit.contains(Units.SEC_PER_OP)) {
            return 1.0 / score;
        }
        return score;
    }

    /**
     * Converts a benchmark score to milliseconds per operation.
     * This is the CENTRALIZED method for all latency conversions.
     * 
     * @param score the raw score value
     * @param unit the unit string from JMH
     * @return latency in milliseconds per operation
     */
    public static double convertToMillisecondsPerOp(double score, String unit) {
        // Handle latency units (time per operation)
        // IMPORTANT: Check more specific units first to avoid partial matches!
        // "us/op" contains "s/op", so must check "us/op" before "s/op"
        // "ns/op" contains "s/op", so must check "ns/op" before "s/op"
        if (unit.contains(Units.NS_PER_OP)) {
            return score / Conversions.NANOS_TO_MILLIS; // Convert nanoseconds to milliseconds
        } else if (unit.contains(Units.US_PER_OP)) {
            return score / Conversions.MICROS_TO_MILLIS; // Convert microseconds to milliseconds
        } else if (unit.contains(Units.MS_PER_OP)) {
            return score; // Already in ms/op
        } else if (unit.contains(Units.SEC_PER_OP)) {
            return score * Conversions.MILLIS_TO_SECONDS; // Convert seconds to milliseconds
        } else if (unit.contains(Units.OPS_PER_NS)) {
            return 1.0 / (score * Conversions.NANOS_TO_MILLIS); // ops/ns -> ns/op -> ms/op
        } else if (unit.contains(Units.OPS_PER_US)) {
            return 1.0 / (score * Conversions.MICROS_TO_MILLIS); // ops/us -> us/op -> ms/op
        } else if (unit.contains(Units.OPS_PER_MS)) {
            return 1.0 / score; // ops/ms -> ms/op
        } else if (unit.contains(Units.OPS_PER_SEC) || unit.contains(Units.OPS_PER_SEC_ALT)) {
            return Conversions.MILLIS_TO_SECONDS / score; // ops/s -> s/op -> ms/op
        }
        // Unknown unit, return 0 to filter out in calculations
        return 0;
    }

    /**
     * Central method for formatting numeric values for display.
     * Rules:
     * - Values less than 2: 2 fraction digits
     * - Values less than 10: 1 fraction digit
     * - Values greater than or equal to 10: No fraction digits
     * 
     * @param value the numeric value to format
     * @return formatted string representation
     */
    public static String formatForDisplay(double value) {
        if (value < 2) {
            return String.format(Locale.US, "%.2f", value);
        } else if (value < 10) {
            return String.format(Locale.US, "%.1f", value);
        } else {
            return String.format(Locale.US, "%d", Math.round(value));
        }
    }

    /**
     * Formats throughput value with appropriate units.
     * 
     * @param value throughput in ops/s
     * @return formatted string with units
     */
    public static String formatThroughput(double value) {
        if (value >= 1_000_000) {
            return formatForDisplay(value / 1_000_000) + Units.M_OPS_S;
        } else if (value >= 1000) {
            return formatForDisplay(value / 1000) + Units.K_OPS_S;
        } else {
            return formatForDisplay(value) + Units.SPACE_OPS_S;
        }
    }

    /**
     * Formats latency value with appropriate units.
     * 
     * @param ms latency in milliseconds
     * @return formatted string with units
     */
    public static String formatLatency(double ms) {
        if (ms >= 1000) {
            return formatForDisplay(ms / 1000) + Units.SUFFIX_S;
        } else {
            return formatForDisplay(ms) + Units.SUFFIX_MS;
        }
    }
}