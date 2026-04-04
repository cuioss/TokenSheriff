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
package de.cuioss.benchmarking.common.util;

import org.apache.commons.io.output.TeeOutputStream;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

/**
 * Centralized logging configuration for JMH benchmarks.
 * Sets up logging to write to both console and a timestamped file in benchmark-results.
 *
 * <p>This class provides:
 * <ul>
 *   <li>Dual output to console and file via TeeOutputStream</li>
 *   <li>Capture of all System.out and System.err output</li>
 *   <li>Timestamped log files in benchmark results directory</li>
 *   <li>Configurable java.util.logging levels</li>
 * </ul>
 *
 */
@SuppressWarnings("java:S106") // System.out and System.err usage is intentional for logging setup
public final class BenchmarkLoggingSetup {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String ROOT_LOGGER_NAME = "";
    private static final String DE_PACKAGE = "de";
    private static final String DE_CUIOSS_PACKAGE = "de.cuioss";
    private static final String DE_CUIOSS_BENCHMARKING_PACKAGE = "de.cuioss.benchmarking";
    private static final String JMH_PACKAGE = "org.openjdk.jmh";

    // Keep references to original streams
    private static final PrintStream ORIGINAL_OUT = System.out;
    private static final PrintStream ORIGINAL_ERR = System.err;

    // Keep reference to file output stream for proper cleanup
    private static FileOutputStream currentFileOut = null;

    private static final System.Logger BOOTSTRAP_LOGGER = System.getLogger(BenchmarkLoggingSetup.class.getName());

    private BenchmarkLoggingSetup() {
        // Utility class
    }

    /**
     * Configures logging to write to both console and a timestamped file in benchmark-results.
     * Also redirects System.out and System.err to capture all console output.
     *
     * @param benchmarkResultsDir the directory where benchmark results are stored
     */
    public static void configureLogging(String benchmarkResultsDir) {
        // Prepare paths and directories
        Path resultsPath = Path.of(benchmarkResultsDir);
        try {
            Files.createDirectories(resultsPath);
        } catch (IOException e) {
            BOOTSTRAP_LOGGER.log(System.Logger.Level.WARNING, "Failed to create benchmark results directory: " + e.getMessage());
            BOOTSTRAP_LOGGER.log(System.Logger.Level.WARNING, "Continuing with console-only logging");
            return;
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logFileName = "benchmark-run_%s.log".formatted(timestamp);
        Path logFile = resultsPath.resolve(logFileName);

        try {
            // Close any previous file output stream
            closeCurrentFileOut();

            // Create new file output stream - kept open for the duration of the benchmark
            currentFileOut = new FileOutputStream(logFile.toFile(), true);

            TeeOutputStream teeOut = new TeeOutputStream(ORIGINAL_OUT, currentFileOut);
            PrintStream newOut = new PrintStream(teeOut, true); // auto-flush enabled

            TeeOutputStream teeErr = new TeeOutputStream(ORIGINAL_ERR, currentFileOut);
            PrintStream newErr = new PrintStream(teeErr, true); // auto-flush enabled

            // Redirect System.out and System.err
            System.setOut(newOut);
            System.setErr(newErr);

            // Configure java.util.logging
            configureJavaUtilLogging(resultsPath, timestamp);

            // Log configuration success (after JUL is configured, System.Logger routes through it)
            BOOTSTRAP_LOGGER.log(System.Logger.Level.INFO, "Benchmark logging configured - writing to: " + logFile);
            BOOTSTRAP_LOGGER.log(System.Logger.Level.INFO, "All console output (System.out/err and JMH) will be captured to both console and file");

        } catch (IOException e) {
            BOOTSTRAP_LOGGER.log(System.Logger.Level.WARNING, "Failed to configure file logging: " + e.getMessage());
            BOOTSTRAP_LOGGER.log(System.Logger.Level.WARNING, "Continuing with console-only logging");
            closeCurrentFileOut();
        }
    }

    private static void closeCurrentFileOut() {
        if (currentFileOut != null) {
            try {
                currentFileOut.close();
            } catch (IOException e) {
                // Ignore close errors
            } finally {
                currentFileOut = null;
            }
        }
    }


    private static void configureJavaUtilLogging(Path resultsPath, String timestamp) throws IOException {
        Logger rootLogger = Logger.getLogger(ROOT_LOGGER_NAME);

        Handler[] handlers = rootLogger.getHandlers();
        for (Handler handler : handlers) {
            rootLogger.removeHandler(handler);
        }

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        consoleHandler.setFormatter(new SimpleFormatter());
        rootLogger.addHandler(consoleHandler);

        String logFileName = "benchmark-jul_%s.log".formatted(timestamp);
        Path julLogFile = resultsPath.resolve(logFileName);
        FileHandler fileHandler = new FileHandler(julLogFile.toString(), true);
        fileHandler.setLevel(Level.ALL);
        fileHandler.setFormatter(new SimpleFormatter());
        rootLogger.addHandler(fileHandler);

        rootLogger.setLevel(Level.INFO);

        // Configure de.cuioss packages
        configurePackageLogging(DE_PACKAGE, Level.INFO);
        configurePackageLogging(DE_CUIOSS_PACKAGE, Level.INFO);
        configurePackageLogging(DE_CUIOSS_BENCHMARKING_PACKAGE, Level.INFO);

        // Disable verbose JMH internal logging
        configurePackageLogging(JMH_PACKAGE, Level.WARNING);
    }

    private static void configurePackageLogging(String packageName, Level level) {
        Logger.getLogger(packageName).setLevel(level);
    }

}