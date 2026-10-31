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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.BenchmarkParams;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AbstractBenchmark metrics export")
class AbstractBenchmarkTest {

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
}
