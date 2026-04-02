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
package de.cuioss.sheriff.oauth.quarkus.logging;

import de.cuioss.sheriff.oauth.quarkus.config.AccessLogFilterConfigResolver;
import de.cuioss.sheriff.oauth.quarkus.config.JwtPropertyKeys;
import de.cuioss.sheriff.oauth.quarkus.test.TestConfig;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests CustomAccessLogFilter functionality.
 * This test focuses on configuration resolution and basic filter behavior.
 */
@EnableTestLogger
class CustomAccessLogFilterTest {

    @Test
    @DisplayName("Should initialize filter with default configuration")
    void shouldInitializeFilterWithDefaultConfig() {
        TestConfig config = new TestConfig(Map.of());
        AccessLogFilterConfigResolver resolver = new AccessLogFilterConfigResolver(config);

        assertDoesNotThrow(() -> new CustomAccessLogFilter(resolver, null));

        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, "CustomAccessLogFilter initialized");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, "enabled=false");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, "enabled=false");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, "statusCodes=400-599");
    }

    @Test
    @DisplayName("Should initialize filter with custom configuration")
    void shouldInitializeFilterWithCustomConfig() {
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.ACCESSLOG.MIN_STATUS_CODE, "500",
                JwtPropertyKeys.ACCESSLOG.MAX_STATUS_CODE, "599",
                JwtPropertyKeys.ACCESSLOG.EXCLUDE_PATHS, "/health/**,/metrics/**"
        ));
        AccessLogFilterConfigResolver resolver = new AccessLogFilterConfigResolver(config);

        assertDoesNotThrow(() -> new CustomAccessLogFilter(resolver, null));

        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, "CustomAccessLogFilter initialized");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, "enabled=false");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, "enabled=false");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, "statusCodes=500-599");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, "/health/**");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, "/metrics/**");
    }

    @Test
    @DisplayName("Should initialize filter with include status codes")
    void shouldInitializeFilterWithIncludeStatusCodes() {
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.ACCESSLOG.INCLUDE_STATUS_CODES, "201,202"
        ));
        AccessLogFilterConfigResolver resolver = new AccessLogFilterConfigResolver(config);

        assertDoesNotThrow(() -> new CustomAccessLogFilter(resolver, null));

        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, "CustomAccessLogFilter initialized");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, "enabled=false");
    }

    @Test
    @DisplayName("Should initialize filter with custom pattern")
    void shouldInitializeFilterWithCustomPattern() {
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.ACCESSLOG.PATTERN, "{method} {path} {status}"
        ));
        AccessLogFilterConfigResolver resolver = new AccessLogFilterConfigResolver(config);

        assertDoesNotThrow(() -> new CustomAccessLogFilter(resolver, null));

        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, "CustomAccessLogFilter initialized");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, "enabled=false");
    }

    @Test
    @DisplayName("Should initialize filter with enabled flag set to true")
    void shouldInitializeFilterWithEnabledFlag() {
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.ACCESSLOG.ENABLED, "true"
        ));
        AccessLogFilterConfigResolver resolver = new AccessLogFilterConfigResolver(config);

        assertDoesNotThrow(() -> new CustomAccessLogFilter(resolver, null));

        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, "CustomAccessLogFilter initialized");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, "enabled=true");
    }
}