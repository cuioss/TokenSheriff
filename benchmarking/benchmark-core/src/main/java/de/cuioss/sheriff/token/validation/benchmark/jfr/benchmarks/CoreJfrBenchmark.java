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
package de.cuioss.sheriff.token.validation.benchmark.jfr.benchmarks;

import de.cuioss.sheriff.token.validation.benchmark.base.AbstractJfrBenchmark;
import de.cuioss.sheriff.token.validation.benchmark.delegates.CoreValidationDelegate;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Core JFR-instrumented benchmark for JWT validation latency and throughput.
 *
 * <p>The benchmark state uses {@link Scope#Benchmark}: all JMH worker threads share one
 * {@code TokenValidator} instance, mirroring the singleton validator used in production,
 * and record their operations into the one shared JFR instrumentation.
 *
 * @author Oliver Wolff
 */
@State(Scope.Benchmark)
@SuppressWarnings("java:S112")
public class CoreJfrBenchmark extends AbstractJfrBenchmark {

    private static final String OPERATION_TYPE_VALIDATION = "validation";

    private CoreValidationDelegate coreValidationDelegate;

    @Setup(Level.Trial)
    public void setup() {
        // Use base class setup
        setupJfrBase();

        // Initialize delegates
        coreValidationDelegate = new CoreValidationDelegate(tokenValidator, tokenRepository);
    }

    // ========== Core Validation Benchmarks ==========

    /**
     * Measures the mean latency per validation call with JFR instrumentation using the full token spectrum.
     * Runs with the configured {@code jmh.threads} against the shared validator.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureAverageTimeWithJfr() {
        return performValidationWithJfr("measureAverageTimeWithJfr");
    }

    /**
     * Measures the aggregate validation throughput of all worker threads against the one shared
     * validator with JFR instrumentation using the full token spectrum.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public AccessTokenContent measureThroughputWithJfr() {
        return performValidationWithJfr("measureThroughputWithJfr");
    }

    /**
     * Common method to perform a full-spectrum validation with JFR instrumentation.
     *
     * <p>The token is selected once and that same token is both described in the JFR metadata and
     * validated, so payload size and issuer always match the operation being recorded.
     *
     * @param operationName the name of the operation for JFR recording
     * @return the validation result
     */
    private AccessTokenContent performValidationWithJfr(String operationName) {
        String token = coreValidationDelegate.nextFullSpectrumToken();

        try (var recorder = jfrInstrumentation.recordOperation(operationName, OPERATION_TYPE_VALIDATION)) {
            recorder.withPayloadSize(token.length())
                    .withMetadata("issuer", tokenRepository.getTokenIssuer(token).orElse(null));

            AccessTokenContent result = coreValidationDelegate.validateWithFullSpectrum(token);
            recorder.withSuccess(true);
            return result;
        }
    }
}