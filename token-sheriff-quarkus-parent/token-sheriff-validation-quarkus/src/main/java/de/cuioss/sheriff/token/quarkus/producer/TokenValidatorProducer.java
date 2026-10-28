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
package de.cuioss.sheriff.token.quarkus.producer;

import de.cuioss.http.client.adapter.RetryConfig;
import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.quarkus.config.AccessTokenCacheConfigResolver;
import de.cuioss.sheriff.token.quarkus.config.IssuerConfigResolver;
import de.cuioss.sheriff.token.quarkus.config.ParserConfigResolver;
import de.cuioss.sheriff.token.quarkus.config.RetryStrategyConfigResolver;
import de.cuioss.sheriff.token.quarkus.jwe.JweDecryptionConfigResolver;
import de.cuioss.sheriff.token.quarkus.mapper.ClaimMapperRegistry;
import de.cuioss.sheriff.token.quarkus.validation.TokenValidationRuleRegistry;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.cache.AccessTokenCacheConfig;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig;
import de.cuioss.tools.logging.CuiLogger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.Config;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static de.cuioss.sheriff.token.quarkus.TokenSheriffQuarkusLogMessages.INFO;

/**
 * CDI producer for JWT validation related instances.
 * <p>
 * This producer creates and manages JWT validation components from
 * configuration properties. Components are initialized during startup
 * via {@link PostConstruct} and exposed through field-based producers. When the
 * producer is destroyed ({@link PreDestroy}), the {@link TokenValidator} it created is
 * closed, which also stops the background refresh of its JWKS loaders.
 * </p>
 * <p>
 * Configuration resolution is delegated to dedicated resolver classes for
 * better separation of concerns, while validation is handled by the underlying
 * builders to avoid logic duplication.
 * </p>
 * <p>
 * Produced components:
 * <ul>
 *   <li>{@link TokenValidator} - Main JWT validation component (includes SecurityEventCounter)</li>
 *   <li>{@link List}&lt;{@link IssuerConfig}&gt; - Resolved issuer configurations</li>
 *   <li>{@link SecurityEventCounter} - Security event counter for monitoring</li>
 * </ul>
 *
 * @since 1.0
 */
@ApplicationScoped
public class TokenValidatorProducer {

    private static final CuiLogger LOGGER = new CuiLogger(TokenValidatorProducer.class);

    private final Config config;
    private final ClaimMapperRegistry claimMapperRegistry;
    private final TokenValidationRuleRegistry validationRuleRegistry;

    @Produces
    @ApplicationScoped
    TokenValidator tokenValidator;

    @Produces
    @ApplicationScoped
    List<IssuerConfig> issuerConfigs;

    @Produces
    @ApplicationScoped
    ParserConfig parserConfig;

    @Produces
    @ApplicationScoped
    SecurityEventCounter securityEventCounter;

    /**
     * Set once {@link #destroy()} closed {@link #tokenValidator}, so it is closed at most once.
     */
    private final AtomicBoolean validatorClosed = new AtomicBoolean();

    @SuppressWarnings("java:S2637") // False positive: fields are initialized in @PostConstruct
    public TokenValidatorProducer(Config config, ClaimMapperRegistry claimMapperRegistry,
            TokenValidationRuleRegistry validationRuleRegistry) {
        this.config = config;
        this.claimMapperRegistry = claimMapperRegistry;
        this.validationRuleRegistry = validationRuleRegistry;
    }

    /**
     * Initializes all JWT validation components from configuration.
     * <p>
     * This method is called during CDI container startup and creates all
     * the validation components that will be exposed through the field producers.
     * </p>
     */
    @PostConstruct
    void init() {
        LOGGER.info(INFO.INITIALIZING_JWT_VALIDATION_COMPONENTS);

        // Create RetryConfig from configuration (used locally, not produced as CDI bean)
        RetryStrategyConfigResolver retryResolver = new RetryStrategyConfigResolver(config);
        RetryConfig retryConfig = retryResolver.resolveRetryConfig();

        // Resolve ParserConfig FIRST — must be created on the caller's thread (correct classloader)
        // before any async JWKS loading that may run on ForkJoinPool threads
        ParserConfigResolver parserConfigResolver = new ParserConfigResolver(config);
        parserConfig = parserConfigResolver.resolveParserConfig();

        // Resolve issuer configurations using the dedicated resolver WITH parserConfig
        // CDI-discovered claim mappers are applied globally via ClaimMapperRegistry
        IssuerConfigResolver issuerConfigResolver = new IssuerConfigResolver(config, retryConfig, claimMapperRegistry, parserConfig);
        issuerConfigs = issuerConfigResolver.resolveIssuerConfigs();

        // Resolve cache config using the dedicated resolver
        AccessTokenCacheConfigResolver cacheConfigResolver = new AccessTokenCacheConfigResolver(config);
        AccessTokenCacheConfig cacheConfig = cacheConfigResolver.resolveCacheConfig();

        // Resolve optional JWE decryption configuration
        JweDecryptionConfigResolver jweResolver = new JweDecryptionConfigResolver(config);
        JweDecryptionConfig jweDecryptionConfig = jweResolver.resolveJweDecryptionConfig();

        // Create TokenValidator using builder pattern - it handles internal initialization
        TokenValidator.TokenValidatorBuilder builder = TokenValidator.builder()
                .parserConfig(parserConfig)
                .cacheConfig(cacheConfig);

        if (jweDecryptionConfig != null) {
            builder.jweDecryptionConfig(jweDecryptionConfig);
        }

        // Add each issuer config to the builder
        for (IssuerConfig issuerConfig : issuerConfigs) {
            builder.issuerConfig(issuerConfig);
        }

        // Add custom validation rules from CDI-discovered DiscoverableTokenValidationRule beans
        if (validationRuleRegistry != null) {
            builder.tokenValidationRules(validationRuleRegistry.getValidationRules());
        }

        tokenValidator = builder.build();

        // Extract the SecurityEventCounter from the built TokenValidator so the CDI-produced
        // bean is the same instance that actually counts events during validation
        this.securityEventCounter = tokenValidator.getSecurityEventCounter();

        LOGGER.info(INFO.JWT_VALIDATION_COMPONENTS_INITIALIZED, issuerConfigs.size());
    }

    /**
     * Closes the {@link TokenValidator} this producer created when the producer is destroyed, for
     * example when the CDI application context shuts down or is restarted in dev mode.
     * <p>
     * {@link TokenValidator#close()} releases the validator's internal resources and closes the JWKS
     * loaders it owns, stopping their background refresh. The validator is closed at most once, nothing
     * happens when {@link #init()} did not create one, and a failure while closing is logged instead of
     * being propagated into the container shutdown.
     */
    @PreDestroy
    void destroy() {
        TokenValidator validator = tokenValidator;
        if (validator == null || !validatorClosed.compareAndSet(false, true)) {
            return;
        }
        try {
            validator.close();
        }
        // cui-rewrite:disable InvalidExceptionUsageRecipe
        // Intentional catch-all: a failure while releasing the validator must not abort the
        // container shutdown that triggered this callback. TokenValidator.close() already isolates
        // failures of the (open-set) JwksLoader closeables itself; the remaining unchecked failure
        // sources are its pipeline and replay-protection resources. This site does NOT fall under
        // the AGENTS.md SPI carve-out for InvalidExceptionUsageRecipe: the suppression is a
        // recorded, accepted deviation (review advisory F3).
        catch (RuntimeException e) {
            LOGGER.debug(e, "Failed to close the TokenValidator on producer destruction");
        }
    }
}
