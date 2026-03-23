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
package de.cuioss.sheriff.oauth.core.wellknown;

import com.dslplatform.json.DslJson;
import de.cuioss.http.client.ContentType;
import de.cuioss.http.client.converter.HttpResponseConverter;
import de.cuioss.sheriff.oauth.core.JWTValidationLogMessages;
import de.cuioss.sheriff.oauth.core.exception.TokenValidationException;
import de.cuioss.sheriff.oauth.core.json.WellKnownResult;
import de.cuioss.sheriff.oauth.core.security.SecurityEventCounter;
import de.cuioss.sheriff.oauth.core.security.SecurityEventCounter.EventType;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Direct HTTP content converter for WellKnownResult using DSL-JSON mapping.
 * <p>
 * This converter directly maps HTTP response bodies to type-safe {@link WellKnownResult}
 * records, eliminating intermediate JsonObject representations and providing optimal performance
 * for ResilientHttpHandler integration.
 * <p>
 * Path: HttpResponse.BodyHandler → DSL-JSON → WellKnownResult
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public class WellKnownConfigurationConverter implements HttpResponseConverter<WellKnownResult> {

    private static final CuiLogger LOGGER = new CuiLogger(WellKnownConfigurationConverter.class);

    private final DslJson<Object> dslJson;
    private final SecurityEventCounter securityEventCounter;
    private final int maxContentSize;

    /**
     * Creates a WellKnownResult content converter with the specified DSL-JSON instance.
     *
     * @param dslJson the DSL-JSON instance containing JSON security settings
     */
    public WellKnownConfigurationConverter(DslJson<Object> dslJson) {
        this(dslJson, new SecurityEventCounter(), 8 * 1024); // Default 8KB limit
    }

    /**
     * Creates a WellKnownResult content converter with full configuration.
     *
     * @param dslJson the DSL-JSON instance containing JSON security settings
     * @param securityEventCounter the security event counter for tracking violations
     * @param maxContentSize maximum allowed JSON content size in bytes
     */
    public WellKnownConfigurationConverter(DslJson<Object> dslJson,
            SecurityEventCounter securityEventCounter,
            int maxContentSize) {
        this.dslJson = dslJson;
        this.securityEventCounter = securityEventCounter;
        this.maxContentSize = maxContentSize;
    }

    @Override
    public Optional<WellKnownResult> convert(Object rawContent) {
        if (!(rawContent instanceof String stringContent)) {
            LOGGER.error(JWTValidationLogMessages.ERROR.JSON_PARSE_FAILED, "Expected String content, got: %s",
                    rawContent != null ? rawContent.getClass().getSimpleName() : "null");
            return Optional.empty();
        }

        if (stringContent.trim().isEmpty()) {
            LOGGER.error(JWTValidationLogMessages.ERROR.JSON_PARSE_FAILED, "Empty well-known response");
            return Optional.empty();
        }

        // Check content size limit
        byte[] contentBytes = stringContent.getBytes(StandardCharsets.UTF_8);
        if (contentBytes.length > maxContentSize) {
            LOGGER.warn(JWTValidationLogMessages.WARN.JWKS_JSON_PARSE_FAILED, "Well-known response size exceeds maximum allowed size");
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            throw new TokenValidationException(
                    EventType.JWKS_JSON_PARSE_FAILED,
                    JWTValidationLogMessages.WARN.JWKS_JSON_PARSE_FAILED.format("Well-known response size exceeds maximum allowed size")
            );
        }

        try {
            // TRUE MAPPER APPROACH: JSON → DSL-JSON → WellKnownResult (NO intermediates!)
            WellKnownResult config = dslJson.deserialize(WellKnownResult.class, contentBytes, contentBytes.length);

            if (config == null) {
                LOGGER.error(JWTValidationLogMessages.ERROR.JSON_PARSE_FAILED, "Failed to deserialize well-known configuration");
                return Optional.empty();
            }

            // Validate required fields
            if (config.issuer() == null || config.issuer().trim().isEmpty()) {
                LOGGER.error(JWTValidationLogMessages.ERROR.JSON_PARSE_FAILED, "Missing required field: issuer");
                return Optional.empty();
            }
            if (config.jwksUri() == null || config.jwksUri().trim().isEmpty()) {
                LOGGER.error(JWTValidationLogMessages.ERROR.JSON_PARSE_FAILED, "Missing required field: jwks_uri");
                return Optional.empty();
            }

            return Optional.of(config);

        } catch (IOException e) {
            // Check if this is a security limit violation
            String errorMessage = e.getMessage();
            if (isSecurityLimitViolation(errorMessage)) {
                LOGGER.warn(JWTValidationLogMessages.WARN.JWKS_JSON_PARSE_FAILED, errorMessage);
                securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
                throw new TokenValidationException(
                        EventType.JWKS_JSON_PARSE_FAILED,
                        JWTValidationLogMessages.WARN.JWKS_JSON_PARSE_FAILED.format(errorMessage)
                );
            }

            // Regular parsing errors
            LOGGER.error(e, JWTValidationLogMessages.ERROR.JSON_PARSE_FAILED, e.getMessage());
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            // Invalid well-known configuration (missing required fields)
            LOGGER.error(e, JWTValidationLogMessages.ERROR.JSON_PARSE_FAILED, "Invalid well-known configuration: %s", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public HttpResponse.BodyHandler<?> getBodyHandler() {
        return HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
    }

    @Override
    public ContentType contentType() {
        return ContentType.APPLICATION_JSON;
    }

    /**
     * Determines if an IOException is caused by a DSL-JSON security limit violation.
     */
    private boolean isSecurityLimitViolation(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }
        return errorMessage.contains("buffer") ||
                errorMessage.contains("limit") ||
                errorMessage.contains("too large") ||
                errorMessage.contains("exceeded");
    }
}