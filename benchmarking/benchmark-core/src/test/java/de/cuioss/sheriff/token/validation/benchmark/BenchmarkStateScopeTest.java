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

import de.cuioss.sheriff.token.validation.benchmark.base.AbstractBenchmark;
import de.cuioss.sheriff.token.validation.benchmark.standard.SimpleCoreValidationBenchmark;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the concurrency model of the JMH benchmarks: every benchmark state is shared by all
 * worker threads ({@link Scope#Benchmark}), so the threads exercise one {@code TokenValidator}
 * the way the production singleton is exercised.
 * <p>
 * The benchmark classes are discovered from the compiled benchmark sources rather than listed by hand,
 * so a newly added benchmark class is pinned without editing this test.
 */
@DisplayName("Benchmark state scope")
class BenchmarkStateScopeTest {

    private static final String BENCHMARK_PACKAGE = "de.cuioss.sheriff.token.validation.benchmark";

    @ParameterizedTest(name = "{0} shares one state across all threads")
    @MethodSource("benchmarkClasses")
    @DisplayName("Should declare @State(Scope.Benchmark)")
    void shouldDeclareBenchmarkScopedState(Class<?> benchmarkClass) {
        State state = benchmarkClass.getAnnotation(State.class);

        assertNotNull(state, benchmarkClass.getSimpleName() + " must declare @State");
        assertEquals(Scope.Benchmark, state.value(),
                benchmarkClass.getSimpleName() + " must share its state across all worker threads");
    }

    @Test
    @DisplayName("Should discover the benchmark classes from the compiled sources")
    void shouldDiscoverBenchmarkClasses() {
        List<Class<?>> discovered = benchmarkClasses().toList();

        assertAll("Discovered benchmark classes",
                () -> assertFalse(discovered.isEmpty(), "The scan must find at least one benchmark class"),
                () -> assertTrue(discovered.contains(SimpleCoreValidationBenchmark.class),
                        "The scan must find the known SimpleCoreValidationBenchmark"));
    }

    /**
     * Discovers every concrete class of the benchmark packages that declares at least one
     * {@link Benchmark} method, excluding the classes JMH generates.
     *
     * @return the discovered benchmark classes, sorted by name
     */
    static Stream<Class<?>> benchmarkClasses() {
        Path classesRoot = classesRoot();
        Path packageRoot = classesRoot.resolve(BENCHMARK_PACKAGE.replace('.', '/'));
        try (Stream<Path> files = Files.walk(packageRoot)) {
            List<Class<?>> classes = files
                    .map(path -> classesRoot.relativize(path).toString().replace('\\', '/'))
                    .filter(name -> name.endsWith(".class"))
                    .filter(name -> !name.contains("$") && !name.contains("jmh_generated"))
                    .<Class<?>>map(name -> loadClass(name.substring(0, name.length() - ".class".length()).replace('/', '.')))
                    .filter(BenchmarkStateScopeTest::isBenchmarkClass)
                    .sorted(Comparator.comparing(Class::getName))
                    .toList();
            return classes.stream();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to scan " + packageRoot, e);
        }
    }

    private static boolean isBenchmarkClass(Class<?> type) {
        return !Modifier.isAbstract(type.getModifiers())
                && Arrays.stream(type.getMethods()).anyMatch(method -> method.isAnnotationPresent(Benchmark.class));
    }

    private static Path classesRoot() {
        try {
            return Path.of(AbstractBenchmark.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to locate the compiled benchmark classes", e);
        }
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className, false, BenchmarkStateScopeTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unable to load " + className, e);
        }
    }
}
