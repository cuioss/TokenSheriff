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

import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.benchmark.LibraryMetricsExporter;
import de.cuioss.sheriff.token.validation.benchmark.MockTokenRepository;
import de.cuioss.sheriff.token.validation.metrics.TokenValidatorMonitorConfig;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.BenchmarkParams;

import java.io.IOException;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.ERROR;

/**
 * Abstract base class for all JWT library benchmarks.
 * Provides token repository and validator setup for library-specific benchmarks.
 *
 * <p>The concrete benchmarks share their state across all JMH worker threads ({@code Scope.Benchmark}),
 * so all threads validate against the one {@link TokenValidator} and record into its one performance
 * monitor. The trial teardown exports that monitor once per benchmark method.
 *
 * @author Oliver Wolff
 */
public abstract class AbstractBenchmark {

    static {
        // Set the logging manager to prevent JBoss LogManager errors in forked JVMs
        System.setProperty("java.util.logging.manager", "java.util.logging.LogManager");
    }

    private static final CuiLogger LOGGER = new CuiLogger(AbstractBenchmark.class);
    protected MockTokenRepository tokenRepository;
    protected TokenValidator tokenValidator;

    /**
     * Setup method for benchmark initialization.
     * Initializes token repository and validator with default configuration.
     * Subclasses should call this from their @Setup method.
     */
    protected void setupBase() {
        LOGGER.debug("Setting up JWT library benchmark");

        // Initialize token repository with cache size configured for 10% of tokens
        MockTokenRepository.Config config = MockTokenRepository.Config.builder()
                .cacheSize(60) // 10% of default 600 tokens
                .build();
        tokenRepository = new MockTokenRepository(config);

        // Create token validator with cache configuration
        tokenValidator = tokenRepository.createTokenValidator(
                TokenValidatorMonitorConfig.builder()
                        .measurementTypes(TokenValidatorMonitorConfig.ALL_MEASUREMENT_TYPES)
                        .windowSize(10000)
                        .build());

        LOGGER.debug("JWT library benchmark setup completed");
    }

    /**
     * Exports the step metrics of the shared validator's monitor for the benchmark method of this trial.
     *
     * <p>The benchmark state uses {@code Scope.Benchmark}, so this teardown runs once per trial over the
     * single monitor shared by all worker threads: the exported metrics cover every thread. The metrics are
     * keyed per benchmark method (see {@link #metricsKey(String)}), so the trials of different methods of
     * the same class do not overwrite each other.
     *
     * @param params the JMH parameters of the finished trial, injected by JMH
     */
    @TearDown(Level.Trial)
    public void exportBenchmarkMetrics(BenchmarkParams params) {
        if (tokenValidator != null) {
            try {
                LibraryMetricsExporter.exportMetrics(metricsKey(params.getBenchmark()),
                        tokenValidator.getPerformanceMonitor());
            } catch (IOException e) {
                LOGGER.error(e, ERROR.EXPORT_FAILED);
            }
        }
    }

    /**
     * Derives the metrics key of a benchmark method from its fully qualified JMH benchmark name.
     *
     * @param fullyQualifiedBenchmark the JMH benchmark name, for example
     *                                {@code de.cuioss.sheriff.token.validation.benchmark.standard.SimpleCoreValidationBenchmark.measureThroughput}
     * @return the last two dot-separated segments, for example
     * {@code SimpleCoreValidationBenchmark.measureThroughput}; the name itself when it has fewer segments
     */
    static String metricsKey(String fullyQualifiedBenchmark) {
        int methodSeparator = fullyQualifiedBenchmark.lastIndexOf('.');
        if (methodSeparator <= 0) {
            return fullyQualifiedBenchmark;
        }
        int classSeparator = fullyQualifiedBenchmark.lastIndexOf('.', methodSeparator - 1);
        return fullyQualifiedBenchmark.substring(classSeparator + 1);
    }
}