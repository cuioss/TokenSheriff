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
package de.cuioss.benchmarking.common.base;

import de.cuioss.benchmarking.common.http.HttpClientFactory;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Abstract base class for all benchmarks in the Token-Sheriff project.
 * Provides common functionality and fields to reduce code duplication
 * across different benchmark implementations.
 *
 * <p>This class provides:
 * <ul>
 *   <li>Logging infrastructure</li>
 *   <li>HTTP client management</li>
 *   <li>Common configuration fields</li>
 *   <li>Utility methods for HTTP operations</li>
 *   <li>Metrics export hooks</li>
 * </ul>
 *
 * @author Oliver Wolff
 */
public abstract class AbstractBenchmarkBase {

    static {
        // Set the logging manager to prevent JBoss LogManager errors in forked JVMs
        System.setProperty("java.util.logging.manager", "java.util.logging.LogManager");
    }

    private static final CuiLogger LOGGER = new CuiLogger(AbstractBenchmarkBase.class);

    protected String serviceUrl;
    protected String benchmarkResultsDir;
    protected HttpClient httpClient;


    /**
     * Base setup method that initializes common resources.
     * Subclasses should call this from their @Setup method.
     *
     * <p>This method:
     * <ul>
     *   <li>Initializes the benchmark results directory</li>
     *   <li>Performs additional setup via template method</li>
     *   <li>Creates the HTTP client after serviceUrl is set</li>
     *   <li>Performs post-initialization setup after HTTP client is ready</li>
     * </ul>
     */
    protected void setupBase() {
        // Initialize benchmark results directory
        benchmarkResultsDir = System.getProperty("benchmark.results.dir", "target/benchmark-results");

        // Call template method for subclass-specific setup (sets serviceUrl)
        performAdditionalSetup();

        // Initialize HttpClient AFTER serviceUrl has been set
        initializeHttpClient();

        LOGGER.debug("Base benchmark setup completed");
    }

    /**
     * Initialize the HTTP client. This is called after performAdditionalSetup()
     * to allow serviceUrl to be set first for URL-based client caching.
     * Subclasses can override this to customize HTTP client initialization.
     */
    protected void initializeHttpClient() {
        httpClient = HttpClientFactory.getInsecureClient();
        LOGGER.debug("Using shared Java HttpClient");
    }

    /**
     * Template method for subclasses to perform additional setup.
     * Override this method to add specific initialization logic.
     */
    protected abstract void performAdditionalSetup();

    /**
     * Teardown method called after benchmark execution.
     * Subclasses should call this from their @TearDown method
     * or override to add additional cleanup logic.
     */
    protected void tearDown() {
        LOGGER.debug("Benchmark teardown initiated");

        // Call template method for subclass-specific teardown
        performAdditionalTeardown();

        LOGGER.debug("Benchmark teardown completed");
    }

    /**
     * Template method for subclasses to perform additional teardown.
     * Override this method to add specific cleanup logic.
     */
    protected void performAdditionalTeardown() {
        // Default implementation does nothing
    }

    /**
     * Creates a basic HTTP request builder with common headers.
     *
     * @param url the full URL to send the request to
     * @return configured request builder
     */
    protected HttpRequest.Builder createBaseRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30));
    }

    /**
     * Creates a basic HTTP request builder with common headers using a base URL and path.
     *
     * @param baseUrl the base URL
     * @param path the path to append to the base URL
     * @return configured request builder
     */
    protected HttpRequest.Builder createBaseRequest(String baseUrl, String path) {
        return createBaseRequest(baseUrl + path);
    }

    /**
     * Sends an HTTP request and returns the response.
     *
     * @param request the request to send
     * @return the HTTP response
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the operation is interrupted
     */
    protected HttpResponse<String> sendRequest(HttpRequest request) throws IOException, InterruptedException {
        if (httpClient == null) {
            throw new IllegalStateException("HTTP client not initialized. Ensure setupBase() was called.");
        }

        // Send request using Java HttpClient
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Utility method to handle common error scenarios in benchmarks.
     *
     * @param response the response to check
     * @param expectedStatus the expected HTTP status code
     * @throws IllegalStateException if the response status doesn't match expected
     */
    protected void validateResponse(HttpResponse<String> response, int expectedStatus) {
        if (response.statusCode() != expectedStatus) {
            throw new IllegalStateException("Expected status %d but got %d. Response: %s".formatted(
                    expectedStatus, response.statusCode(), response.body()));
        }
    }

    /**
     * Export metrics at the end of benchmark execution.
     * Subclasses should override this method to implement specific metrics export logic.
     */
    public abstract void exportBenchmarkMetrics();
}