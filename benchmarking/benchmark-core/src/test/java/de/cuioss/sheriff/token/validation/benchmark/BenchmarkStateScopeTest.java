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

import de.cuioss.sheriff.token.validation.benchmark.jfr.benchmarks.CoreJfrBenchmark;
import de.cuioss.sheriff.token.validation.benchmark.jfr.benchmarks.ErrorJfrBenchmark;
import de.cuioss.sheriff.token.validation.benchmark.jfr.benchmarks.MixedJfrBenchmark;
import de.cuioss.sheriff.token.validation.benchmark.standard.SimpleCoreValidationBenchmark;
import de.cuioss.sheriff.token.validation.benchmark.standard.SimpleErrorLoadBenchmark;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the concurrency model of the JMH benchmarks: every benchmark state is shared by all
 * worker threads ({@link Scope#Benchmark}), so the threads exercise one {@code TokenValidator}
 * the way the production singleton is exercised.
 */
@DisplayName("Benchmark state scope")
class BenchmarkStateScopeTest {

    @ParameterizedTest(name = "{0} shares one state across all threads")
    @ValueSource(classes = {
            SimpleCoreValidationBenchmark.class,
            SimpleErrorLoadBenchmark.class,
            CoreJfrBenchmark.class,
            ErrorJfrBenchmark.class,
            MixedJfrBenchmark.class
    })
    @DisplayName("Should declare @State(Scope.Benchmark)")
    void shouldDeclareBenchmarkScopedState(Class<?> benchmarkClass) {
        State state = benchmarkClass.getAnnotation(State.class);

        assertNotNull(state, benchmarkClass.getSimpleName() + " must declare @State");
        assertEquals(Scope.Benchmark, state.value(),
                benchmarkClass.getSimpleName() + " must share its state across all worker threads");
    }
}
