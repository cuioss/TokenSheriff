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
package de.cuioss.sheriff.token.validation.benchmark.base;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.cuioss.sheriff.token.validation.benchmark.delegates.CoreValidationDelegate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.runner.IterationType;
import org.openjdk.jmh.runner.WorkloadParams;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AbstractBenchmark metrics export")
class AbstractBenchmarkTest {

    private static final Path METRICS_FILE = Path.of("target/benchmark-results/jwt-validation-metrics.json");

    private static final String BENCHMARK_CLASS =
            "de.cuioss.sheriff.token.validation.benchmark.standard.SimpleCoreValidationBenchmark";

    @Test
    @DisplayName("Should key the metrics by benchmark class and method")
    void shouldKeyMetricsByClassAndMethod() {
        String key = AbstractBenchmark.metricsKey(BENCHMARK_CLASS + ".measureThroughput");

        assertEquals("SimpleCoreValidationBenchmark.measureThroughput", key);
    }

    @Test
    @DisplayName("Should give two methods of the same class distinct keys")
    void shouldGiveDistinctKeysToMethodsOfOneClass() {
        String throughputKey = AbstractBenchmark.metricsKey(BENCHMARK_CLASS + ".measureThroughput");
        String averageTimeKey = AbstractBenchmark.metricsKey(BENCHMARK_CLASS + ".measureAverageTime");

        assertNotEquals(throughputKey, averageTimeKey);
    }

    @Test
    @DisplayName("Should return a name without a method segment unchanged")
    void shouldReturnUnqualifiedNameUnchanged() {
        assertEquals("SimpleCoreValidationBenchmark", AbstractBenchmark.metricsKey("SimpleCoreValidationBenchmark"));
    }

    @Test
    @DisplayName("Should export at trial teardown with the JMH benchmark parameters")
    void shouldExportAtTrialTeardownWithBenchmarkParams() throws Exception {
        Method export = AbstractBenchmark.class.getMethod("exportBenchmarkMetrics", BenchmarkParams.class);

        TearDown tearDown = export.getAnnotation(TearDown.class);
        assertNotNull(tearDown, "exportBenchmarkMetrics must be a JMH teardown fixture");
        assertEquals(Level.Trial, tearDown.value());
    }

    @Test
    @DisplayName("Should export the shared validator metrics under the per-method key")
    void shouldExportSharedValidatorMetricsUnderPerMethodKey() throws IOException {
        AbstractBenchmark benchmark = new AbstractBenchmark() {
        };
        benchmark.setupBase();
        new CoreValidationDelegate(benchmark.tokenValidator, benchmark.tokenRepository).validateWithFullSpectrum();

        benchmark.exportBenchmarkMetrics(benchmarkParams(BENCHMARK_CLASS + ".measureThroughput"));

        Map<String, Object> entry = metricsEntry("SimpleCoreValidationBenchmark.measureThroughput");
        assertAll("Exported entry of the benchmark method",
                () -> assertNotNull(entry.get("timestamp"), "Entry must carry a timestamp"),
                () -> assertInstanceOf(Map.class, entry.get("steps"), "Entry must carry the step metrics"),
                () -> assertFalse(((Map<?, ?>) entry.get("steps")).isEmpty(),
                        "The validated token must have produced step metrics"));
    }

    private static BenchmarkParams benchmarkParams(String benchmark) {
        IterationParams iteration = new IterationParams(IterationType.MEASUREMENT, 1, TimeValue.seconds(1), 1);
        return new BenchmarkParams(benchmark, benchmark, false, 1, new int[]{1}, List.of(), 1, 0,
                iteration, iteration, Mode.Throughput, new WorkloadParams(), TimeUnit.SECONDS, 1,
                "java", List.of(), "21", "vm", "1", "1", TimeValue.minutes(1));
    }

    /**
     * Reads only the entry of the given key, because every export of the module shares the metrics file.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> metricsEntry(String key) throws IOException {
        TypeToken<Map<String, Object>> typeToken = new TypeToken<>() {
        };
        Map<String, Object> allMetrics = new Gson().fromJson(Files.readString(METRICS_FILE), typeToken.getType());
        Object entry = allMetrics.get(key);
        assertNotNull(entry, "Metrics file must contain an entry for " + key);
        return (Map<String, Object>) entry;
    }
}
