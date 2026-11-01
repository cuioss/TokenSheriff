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
package de.cuioss.sheriff.token.validation.benchmark.standard;

import de.cuioss.sheriff.token.validation.benchmark.base.AbstractBenchmark;
import de.cuioss.sheriff.token.validation.benchmark.delegates.CoreValidationDelegate;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Core JWT validation benchmark measuring latency and throughput of the token validator.
 *
 * <p>The benchmark state uses {@link Scope#Benchmark}: all JMH worker threads share one
 * {@code TokenValidator} instance, mirroring the singleton validator used in production.
 * Running with the configured {@code jmh.threads} therefore measures the validator under
 * genuine concurrent access rather than one private validator per thread.
 *
 * @author Oliver Wolff
 */
@State(Scope.Benchmark)
@SuppressWarnings("java:S112")
public class SimpleCoreValidationBenchmark extends AbstractBenchmark {

    private CoreValidationDelegate validationDelegate;


    @Setup(Level.Trial)
    public void setup() {
        // Use base class setup with our benchmark names
        setupBase();

        // Initialize validation delegate
        validationDelegate = new CoreValidationDelegate(tokenValidator, tokenRepository);
    }

    /**
     * Measures the mean latency per validation call using the full token spectrum.
     * The benchmark runs with the configured {@code jmh.threads} against the shared validator,
     * rotating through all 600 tokens to exercise cache effectiveness, and reports the average
     * time of a single call. Lower values indicate better performance.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureAverageTime() {
        return validationDelegate.validateWithFullSpectrum();
    }

    /**
     * Measures the aggregate validation throughput of all worker threads against the one
     * shared validator using the full token spectrum, rotating through all 600 tokens to
     * exercise cache effectiveness. Higher values indicate better throughput.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public AccessTokenContent measureThroughput() {
        return validationDelegate.validateWithFullSpectrum();
    }

}