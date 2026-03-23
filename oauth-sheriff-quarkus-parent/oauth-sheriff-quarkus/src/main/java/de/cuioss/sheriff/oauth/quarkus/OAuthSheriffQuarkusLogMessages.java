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
package de.cuioss.sheriff.oauth.quarkus;

import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;
import lombok.experimental.UtilityClass;

/**
 * Log messages for the OAuth Sheriff Quarkus runtime module.
 * <p>
 * This class provides structured logging constants for runtime JWT validation functionality,
 * following the CUI logging standards with unique identifiers for each message.
 * </p>
 * <p>
 * Message ID ranges:
 * <ul>
 *   <li>001-099: INFO level messages</li>
 *   <li>100-199: WARN level messages</li>
 *   <li>200-299: ERROR level messages</li>
 * </ul>
 *
 * @since 1.0
 * @see de.cuioss.tools.logging.LogRecord
 */
@UtilityClass
public final class OAuthSheriffQuarkusLogMessages {

    /**
     * The prefix for all log messages in this module.
     */
    public static final String PREFIX = "OAuthSheriff_Q";

    /**
     * INFO level log messages (001-099).
     */
    @UtilityClass
    public static final class INFO {

        // Configuration Messages (001-010)

        public static final LogRecord RESOLVING_ISSUER_CONFIGURATIONS = LogRecordModel.builder()
                .template("Resolving issuer configurations from properties")
                .prefix(PREFIX)
                .identifier(1)
                .build();

        public static final LogRecord RESOLVED_ISSUER_CONFIGURATION = LogRecordModel.builder()
                .template("Resolved issuer configuration: %s")
                .prefix(PREFIX)
                .identifier(2)
                .build();

        public static final LogRecord RESOLVED_ENABLED_ISSUER_CONFIGURATIONS = LogRecordModel.builder()
                .template("Resolved %s enabled issuer configurations")
                .prefix(PREFIX)
                .identifier(3)
                .build();

        public static final LogRecord RESOLVED_PARSER_CONFIG = LogRecordModel.builder()
                .template("Resolved ParserConfig: maxTokenSize=%s bytes, maxPayloadSize=%s bytes, maxStringLength=%s, maxBufferSize=%s")
                .prefix(PREFIX)
                .identifier(4)
                .build();

        // Validation Messages (011-020)

        public static final LogRecord INITIALIZING_JWT_VALIDATION_COMPONENTS = LogRecordModel.builder()
                .template("Initializing JWT validation components from configuration")
                .prefix(PREFIX)
                .identifier(11)
                .build();

        public static final LogRecord JWT_VALIDATION_COMPONENTS_INITIALIZED = LogRecordModel.builder()
                .template("JWT validation components initialized successfully with %s issuers")
                .prefix(PREFIX)
                .identifier(12)
                .build();

        public static final LogRecord RESOLVING_ACCESS_LOG_FILTER_CONFIG = LogRecordModel.builder()
                .template("Resolving access log filter configuration from properties")
                .prefix(PREFIX)
                .identifier(13)
                .build();

        // Claim Mapper Registry Messages (014-015)

        public static final LogRecord CLAIM_MAPPER_REGISTRY_INITIALIZED = LogRecordModel.builder()
                .template("Claim mapper registry initialized with %s custom mapper(s): %s")
                .prefix(PREFIX)
                .identifier(14)
                .build();

        public static final LogRecord NO_CUSTOM_CLAIM_MAPPERS_DISCOVERED = LogRecordModel.builder()
                .template("No custom claim mappers discovered")
                .prefix(PREFIX)
                .identifier(15)
                .build();

        // Metrics Messages (021-030)

        public static final LogRecord INITIALIZING_JWT_METRICS_COLLECTOR = LogRecordModel.builder()
                .template("Initializing JwtMetricsCollector")
                .prefix(PREFIX)
                .identifier(21)
                .build();

        public static final LogRecord JWT_METRICS_COLLECTOR_INITIALIZED = LogRecordModel.builder()
                .template("JwtMetricsCollector initialized with %s event types")
                .prefix(PREFIX)
                .identifier(22)
                .build();


        // Cache Messages (041-050)

        public static final LogRecord RESOLVING_ACCESS_TOKEN_CACHE_CONFIG = LogRecordModel.builder()
                .template("Resolving access token cache configuration from properties")
                .prefix(PREFIX)
                .identifier(41)
                .build();

        public static final LogRecord ACCESS_TOKEN_CACHE_DISABLED = LogRecordModel.builder()
                .template("Access token cache disabled (maxSize=0)")
                .prefix(PREFIX)
                .identifier(42)
                .build();

        public static final LogRecord ACCESS_TOKEN_CACHE_CONFIGURED = LogRecordModel.builder()
                .template("Access token cache configured: maxSize=%s, evictionIntervalSeconds=%s")
                .prefix(PREFIX)
                .identifier(43)
                .build();

        // Metrics Clear Messages (051-060)

        public static final LogRecord CLEARING_JWT_METRICS = LogRecordModel.builder()
                .template("Clearing all JWT metrics")
                .prefix(PREFIX)
                .identifier(51)
                .build();

        public static final LogRecord JWT_METRICS_CLEARED = LogRecordModel.builder()
                .template("JWT metrics cleared successfully")
                .prefix(PREFIX)
                .identifier(52)
                .build();

        // JWKS Startup Messages (061-070)

        public static final LogRecord JWKS_STARTUP_SERVICE_INITIALIZED = LogRecordModel.builder()
                .template("JWKS startup service activated for background key loading")
                .prefix(PREFIX)
                .identifier(60)
                .build();

        public static final LogRecord STARTING_ASYNCHRONOUS_JWKS_INITIALIZATION = LogRecordModel.builder()
                .template("Starting asynchronous JWKS initialization for %s issuer(s)")
                .prefix(PREFIX)
                .identifier(61)
                .build();

        public static final LogRecord NO_ISSUER_CONFIGURATIONS_FOUND = LogRecordModel.builder()
                .template("No issuer configurations found - skipping JWKS initialization")
                .prefix(PREFIX)
                .identifier(62)
                .build();

        public static final LogRecord BACKGROUND_JWKS_INITIALIZATION_COMPLETED = LogRecordModel.builder()
                .template("Background JWKS initialization completed successfully")
                .prefix(PREFIX)
                .identifier(63)
                .build();

        public static final LogRecord BACKGROUND_JWKS_LOADING_COMPLETED_FOR_ISSUER = LogRecordModel.builder()
                .template("Background JWKS loading completed for issuer: %s with status: %s")
                .prefix(PREFIX)
                .identifier(64)
                .build();

        // JWE Configuration Messages (031-040)

        public static final LogRecord JWE_DECRYPTION_CONFIG_RESOLVING = LogRecordModel.builder()
                .template("Resolving JWE decryption configuration from properties")
                .prefix(PREFIX)
                .identifier(31)
                .build();

        public static final LogRecord JWE_DECRYPTION_CONFIG_RESOLVED = LogRecordModel.builder()
                .template("JWE decryption configuration resolved with %s key(s)")
                .prefix(PREFIX)
                .identifier(32)
                .build();

        public static final LogRecord JWE_CONFIG_CHECK = LogRecordModel.builder()
                .template("JWE config check: singleKeyPath=%s, keystorePath=%s, multiKeys=%s")
                .prefix(PREFIX)
                .identifier(33)
                .build();

        public static final LogRecord JWE_DECRYPTION_NOT_CONFIGURED = LogRecordModel.builder()
                .template("No JWE decryption configuration found - JWE support disabled")
                .prefix(PREFIX)
                .identifier(34)
                .build();

        // Access Log Filter Messages (065-070)

        public static final LogRecord CUSTOM_ACCESS_LOG_FILTER_INITIALIZED = LogRecordModel.builder()
                .template("CustomAccessLogFilter initialized: %s")
                .prefix(PREFIX)
                .identifier(65)
                .build();

        public static final LogRecord ACCESS_LOG_ENTRY = LogRecordModel.builder()
                .template("%s")
                .prefix(PREFIX)
                .identifier(66)
                .build();
    }

    /**
     * WARN level log messages (100-199).
     */
    @UtilityClass
    public static final class WARN {

        // Health Check Warnings (100-110)

        public static final LogRecord ERROR_CHECKING_JWKS_LOADER = LogRecordModel.builder()
                .template("Error checking JWKS loader for issuer %s: %s")
                .prefix(PREFIX)
                .identifier(100)
                .build();

        // Bearer Token Warnings (121-130)

        public static final LogRecord BEARER_TOKEN_REQUIREMENTS_NOT_MET_DETAILED = LogRecordModel.builder()
                .template("Bearer token does not meet requirements. Missing scopes: %s, Missing roles: %s, Missing groups: %s")
                .prefix(PREFIX)
                .identifier(128)
                .build();

        // JWKS Startup Warnings (131-140)

        public static final LogRecord BACKGROUND_JWKS_LOADING_FAILED_FOR_ISSUER = LogRecordModel.builder()
                .template("Background JWKS loading failed for issuer %s: %s")
                .prefix(PREFIX)
                .identifier(131)
                .build();

        // Metrics Warnings (133-140)

        public static final LogRecord NO_MICROMETER_COUNTER_FOUND = LogRecordModel.builder()
                .template("No Micrometer counter found for event type %s, delta %s lost")
                .prefix(PREFIX)
                .identifier(133)
                .build();

        public static final LogRecord JWKS_LOADING_RETRY_WARNING = LogRecordModel.builder()
                .template("JWKS loading failed for issuer %s: %s - will retry via background refresh")
                .prefix(PREFIX)
                .identifier(134)
                .build();

        public static final LogRecord BACKGROUND_JWKS_ISSUES_WARNING = LogRecordModel.builder()
                .template("Background JWKS initialization encountered issues: %s - on-demand loading will handle this")
                .prefix(PREFIX)
                .identifier(135)
                .build();

        // JWE Warnings (141-145)

        public static final LogRecord JWE_KEYSTORE_MISSING_ALIAS = LogRecordModel.builder()
                .template("JWE keystore configured at %s but no key-alias specified — skipping keystore key loading")
                .prefix(PREFIX)
                .identifier(141)
                .build();

        public static final LogRecord JWE_KEY_LOADING_FAILED = LogRecordModel.builder()
                .template("Failed to load JWE decryption keys, JWE support disabled: %s")
                .prefix(PREFIX)
                .identifier(142)
                .build();

        // Interceptor Warnings (136-145)

        public static final LogRecord BEARER_TOKEN_ANNOTATION_NOT_FOUND = LogRecordModel.builder()
                .template("@BearerAuth annotation not found on method or class: %s")
                .prefix(PREFIX)
                .identifier(136)
                .build();
    }

    /**
     * ERROR level log messages (200-299).
     */
    @UtilityClass
    public static final class ERROR {

        // Infrastructure Errors (200-210)

        public static final LogRecord VERTX_REQUEST_CONTEXT_UNAVAILABLE = LogRecordModel.builder()
                .template("Vertx HttpServerRequest context is unavailable - no active request context found")
                .prefix(PREFIX)
                .identifier(201)
                .build();
    }
}