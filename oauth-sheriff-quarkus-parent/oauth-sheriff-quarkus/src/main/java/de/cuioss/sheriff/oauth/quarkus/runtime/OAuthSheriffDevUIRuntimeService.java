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
package de.cuioss.sheriff.oauth.quarkus.runtime;

import de.cuioss.sheriff.oauth.core.IssuerConfig;
import de.cuioss.sheriff.oauth.core.ParserConfig;
import de.cuioss.sheriff.oauth.core.TokenValidator;
import de.cuioss.sheriff.oauth.core.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.oauth.core.domain.token.TokenContent;
import de.cuioss.sheriff.oauth.core.exception.TokenValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonException;

import java.util.*;

/**
 * Runtime JSON-RPC service for OAuth Sheriff DevUI.
 * <p>
 * Provides read-only status information and token validation for the DevUI
 * components. All methods return {@link Map} structures serialized as JSON-RPC
 * responses over WebSocket.
 * </p>
 *
 * @since 1.0
 */
@ApplicationScoped
public class OAuthSheriffDevUIRuntimeService {

    // String constants for commonly used literals
    private static final String JWT_VALIDATION_DISABLED = "JWT validation is disabled";
    private static final String MESSAGE = "message";
    private static final String VALID = "valid";
    private static final String ERROR = "error";
    private static final String TOKEN_TYPE = "tokenType";
    private static final String CLAIMS = "claims";
    private static final String ISSUER = "issuer";
    private static final String HEALTH_STATUS = "healthStatus";

    private final TokenValidator tokenValidator;
    private final List<IssuerConfig> issuerConfigs;
    private final ParserConfig parserConfig;

    /**
     * Constructor for dependency injection.
     *
     * @param tokenValidator the token validator
     * @param issuerConfigs  the issuer configurations from TokenValidatorProducer
     * @param parserConfig   the parser configuration from TokenValidatorProducer
     */
    @Inject
    public OAuthSheriffDevUIRuntimeService(TokenValidator tokenValidator, List<IssuerConfig> issuerConfigs,
            ParserConfig parserConfig) {
        this.tokenValidator = tokenValidator;
        this.issuerConfigs = issuerConfigs;
        this.parserConfig = parserConfig;
    }

    /**
     * Get runtime JWT validation status.
     *
     * @return A map containing runtime validation status information
     */
   
    public Map<String, Object> getValidationStatus() {
        Map<String, Object> status = new HashMap<>();

        boolean isEnabled = isJwtEnabled();
        status.put("enabled", isEnabled);
        status.put("validatorPresent", true);
        status.put("status", isEnabled ? "ACTIVE" : "INACTIVE");

        if (isEnabled) {
            status.put("statusMessage", "JWT validation is active and ready");
        } else {
            status.put("statusMessage", JWT_VALIDATION_DISABLED);
        }

        return status;
    }

    /**
     * Get runtime JWKS endpoint status.
     *
     * @return A map containing runtime JWKS status information
     */
   
    public Map<String, Object> getJwksStatus() {
        Map<String, Object> jwksInfo = new HashMap<>();

        boolean hasIssuers = !issuerConfigs.isEmpty();
        jwksInfo.put("status", hasIssuers ? "CONFIGURED" : "NO_ISSUERS");

        // Build issuers array with details for each configured issuer
        List<Map<String, Object>> issuers = new ArrayList<>();
        for (IssuerConfig ic : issuerConfigs) {
            Map<String, Object> issuer = new HashMap<>();
            String identifier = ic.getIssuerIdentifier();
            issuer.put("name", identifier);
            issuer.put("issuerUri", identifier);
            issuer.put("jwksUri", ic.getJwksLoader() != null ? "configured" : "not configured");
            issuer.put("loaderStatus", ic.getLoaderStatus().toString());
            issuer.put("lastRefresh", "N/A");
            issuers.add(issuer);
        }
        jwksInfo.put("issuers", issuers);

        return jwksInfo;
    }

    /**
     * Get runtime configuration information.
     *
     * @return A map containing runtime configuration information
     */
   
    public Map<String, Object> getConfiguration() {
        Map<String, Object> configMap = new HashMap<>();

        boolean isEnabled = isJwtEnabled();
        configMap.put("enabled", isEnabled);
        configMap.put("logLevel", "INFO");

        // Parser configuration section — values from actual runtime config
        Map<String, Object> parser = new HashMap<>();
        parser.put("maxTokenSize", parserConfig.getMaxTokenSize());
        parser.put("maxPayloadSize", parserConfig.getMaxPayloadSize());
        parser.put("maxStringLength", parserConfig.getMaxStringLength());
        // Clock skew is per-issuer; show first issuer's value or default
        int clockSkew = issuerConfigs.isEmpty() ? 60 : issuerConfigs.getFirst().getClockSkewSeconds();
        parser.put("clockSkewSeconds", clockSkew);
        configMap.put("parser", parser);

        // HTTP JWKS loader configuration section — documented defaults
        // (HttpJwksLoaderConfig is not accessible from this service)
        Map<String, Object> httpJwksLoader = new HashMap<>();
        httpJwksLoader.put("connectTimeoutSeconds", "per-issuer (default: 10)");
        httpJwksLoader.put("readTimeoutSeconds", "per-issuer (default: 10)");
        httpJwksLoader.put("sizeLimit", parserConfig.getMaxTokenSize());
        configMap.put("httpJwksLoader", httpJwksLoader);

        // Issuers configuration section
        Map<String, Object> issuersMap = new LinkedHashMap<>();
        for (IssuerConfig ic : issuerConfigs) {
            String identifier = ic.getIssuerIdentifier();
            String name = identifier;
            Map<String, Object> issuerDetail = new HashMap<>();
            issuerDetail.put("issuerUri", identifier);
            issuerDetail.put("jwksUri", ic.getJwksLoader() != null ? "configured" : null);
            issuerDetail.put("audience", ic.getExpectedAudience().isEmpty() ? null
                    : String.join(", ", ic.getExpectedAudience()));
            issuerDetail.put("clockSkewSeconds", ic.getClockSkewSeconds());
            issuerDetail.put("expectedTokenType", ic.getExpectedTokenType());
            issuerDetail.put("claimSubOptional", ic.isClaimSubOptional());
            issuerDetail.put("dpopEnabled", ic.getDpopConfig() != null);
            issuersMap.put(name, issuerDetail);
        }
        configMap.put("issuers", issuersMap);

        return configMap;
    }

    /**
     * Validate a JWT access token using the runtime validator.
     * Only validates access tokens, not ID tokens or refresh tokens.
     *
     * @param token The JWT access token to validate
     * @return A map containing validation result
     */
   
    public Map<String, Object> validateToken(String token) {
        Map<String, Object> result = new HashMap<>();
        // Set default state - token is invalid until proven valid
        result.put(VALID, false);

        if (token == null || token.trim().isEmpty()) {
            result.put(ERROR, "Token is empty or null");
            return result;
        }

        if (!isJwtEnabled()) {
            result.put(ERROR, JWT_VALIDATION_DISABLED);
            return result;
        }

        try {
            // Only validate as access token
            TokenContent tokenContent = tokenValidator.createAccessToken(AccessTokenRequest.of(token.trim()));

            result.put(VALID, true);
            result.put(TOKEN_TYPE, "ACCESS_TOKEN");
            // Convert ClaimValue objects to simple strings for JSON-RPC serialization
            // (ClaimValue contains OffsetDateTime which Jackson cannot serialize by default)
            Map<String, String> simpleClaims = new HashMap<>();
            tokenContent.getClaims().forEach((key, value) -> {
                if (value != null) {
                    simpleClaims.put(key, value.getOriginalString());
                } else {
                    simpleClaims.put(key, null);
                }
            });
            result.put(CLAIMS, simpleClaims);
            result.put(ISSUER, tokenContent.getIssuer());

        } catch (TokenValidationException e) {
            // Token remains invalid (default state)
            result.put(ERROR, e.getMessage());
            result.put("details", "Access token validation failed");
        } catch (JsonException | IllegalArgumentException e) {
            // Handle JSON parsing errors and other token format issues
            result.put(ERROR, "Invalid token format: " + e.getMessage());
            result.put("details", "Token format is invalid");
        }

        return result;
    }

    /**
     * Get runtime health information.
     *
     * @return A map containing runtime health information
     */
   
    public Map<String, Object> getHealthInfo() {
        Map<String, Object> health = new HashMap<>();

        boolean configValid = isJwtEnabled();
        health.put("configurationValid", configValid);

        if (configValid) {
            health.put(MESSAGE, "All JWT components are healthy and operational");
            health.put(HEALTH_STATUS, "UP");
        } else {
            health.put(MESSAGE, "JWT validation is disabled or misconfigured");
            health.put(HEALTH_STATUS, "DOWN");
        }

        return health;
    }

    /**
     * Helper method to determine if JWT validation is enabled.
     * JWT is considered enabled if there are any enabled issuers configured.
     *
     * @return true if JWT validation is enabled, false otherwise
     */
    private boolean isJwtEnabled() {
        return countEnabledIssuers() > 0;
    }

    /**
     * Counts the number of enabled issuers using the resolved issuer configurations
     * from TokenValidatorProducer. This leverages existing functionality and avoids
     * duplication of configuration parsing logic.
     *
     * @return number of enabled issuers
     */
    private int countEnabledIssuers() {
        // IssuerConfigResolver already filters to only enabled issuers
        return issuerConfigs.size();
    }

}
