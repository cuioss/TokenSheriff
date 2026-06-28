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
package de.cuioss.sheriff.token.quarkus.deployment;

import de.cuioss.sheriff.token.quarkus.config.AccessLogFilterConfigResolver;
import de.cuioss.sheriff.token.quarkus.config.ParserConfigResolver;
import de.cuioss.sheriff.token.quarkus.interceptor.BearerTokenInterceptor;
import de.cuioss.sheriff.token.quarkus.logging.CustomAccessLogFilter;
import de.cuioss.sheriff.token.quarkus.mapper.ClaimMapperRegistry;
import de.cuioss.sheriff.token.quarkus.mapper.DiscoverableClaimMapper;
import de.cuioss.sheriff.token.quarkus.mapper.keycloak.KeycloakGroupsMapperBean;
import de.cuioss.sheriff.token.quarkus.mapper.keycloak.KeycloakRolesMapperBean;
import de.cuioss.sheriff.token.quarkus.metrics.JwtMetricsCollector;
import de.cuioss.sheriff.token.quarkus.producer.BearerTokenProducer;
import de.cuioss.sheriff.token.quarkus.producer.JsonWebTokenAdapter;
import de.cuioss.sheriff.token.quarkus.producer.TokenValidatorProducer;
import de.cuioss.sheriff.token.quarkus.runtime.TokenSheriffDevUIRuntimeService;
import de.cuioss.sheriff.token.quarkus.servlet.VertxServletObjectsResolver;
import de.cuioss.sheriff.token.quarkus.validation.DiscoverableTokenValidationRule;
import de.cuioss.sheriff.token.quarkus.validation.TokenValidationRuleRegistry;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.IssuerConfigCache;
import de.cuioss.sheriff.token.validation.ParserConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValueType;
import de.cuioss.sheriff.token.validation.domain.claim.mapper.*;
import de.cuioss.sheriff.token.validation.domain.token.*;
import de.cuioss.sheriff.token.validation.jwe.JweAlgorithmPreferences;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptor;
import de.cuioss.sheriff.token.validation.jwks.http.HttpJwksLoader;
import de.cuioss.sheriff.token.validation.jwks.http.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.validation.jwks.key.JWKSKeyLoader;
import de.cuioss.sheriff.token.validation.jwks.key.KeyInfo;
import de.cuioss.sheriff.token.validation.jwks.parser.JwksParser;
import de.cuioss.sheriff.token.validation.pipeline.DecodedJwt;
import de.cuioss.sheriff.token.validation.pipeline.NonValidatingJwtParser;
import de.cuioss.sheriff.token.validation.pipeline.TokenBuilder;
import de.cuioss.sheriff.token.validation.pipeline.validator.TokenClaimValidator;
import de.cuioss.sheriff.token.validation.pipeline.validator.TokenHeaderValidator;
import de.cuioss.sheriff.token.validation.pipeline.validator.TokenSignatureValidator;
import de.cuioss.sheriff.token.validation.security.JwkAlgorithmPreferences;
import de.cuioss.sheriff.token.validation.security.SecurityEventCounter;
import de.cuioss.sheriff.token.validation.security.SignatureAlgorithmPreferences;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.resteasy.common.spi.ResteasyJaxrsProviderBuildItem;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.jandex.DotName;

/**
 * Processor for the Token-Sheriff Quarkus extension.
 * <p>
 * This class handles the build-time processing for the extension, including
 * registering the feature, setting up reflection configuration, and providing
 * DevUI integration.
 * </p>
 */
public class TokenSheriffProcessor {

    /**
     * The feature name for the Token-Sheriff extension.
     */
    private static final String FEATURE = "token-sheriff";

    /**
     * Logger for build-time processing.
     */
    private static final CuiLogger LOGGER = new CuiLogger(TokenSheriffProcessor.class);

    /**
     * LogRecord for feature registration.
     */
    private static final LogRecord TOKEN_SHERIFF_FEATURE_REGISTERED = LogRecordModel.builder()
            .template("Token-Sheriff feature registered")
            .prefix("TokenSheriff_Q_D")
            .identifier(1)
            .build();

    /**
     * Register the Token-Sheriff feature.
     *
     * @return A {@link FeatureBuildItem} for the Token-Sheriff feature
     */
    @BuildStep
    public FeatureBuildItem feature() {
        LOGGER.info(TOKEN_SHERIFF_FEATURE_REGISTERED);
        return new FeatureBuildItem(FEATURE);
    }


    /**
     * Register JWT validation classes that need methods and constructors for reflection.
     * These are core library classes instantiated directly and called via methods.
     *
     * @return A {@link ReflectiveClassBuildItem} for the JWT validation classes with constructors
     */
    @BuildStep
    public ReflectiveClassBuildItem registerJwtValidationConstructorClassesForReflection() {
        return ReflectiveClassBuildItem.builder(
                // Classes that need methods + constructors for instantiation
                TokenValidator.class,        // Public constructors, API methods
                IssuerConfigCache.class,  // Package constructor, internal methods
                SecurityEventCounter.class,  // Default constructor, counter methods
                MeterRegistry.class)         // Micrometer registry for metrics
                .methods(true)    // Methods needed for API calls and getters
                .fields(false)    // No direct field access needed
                .constructors(true) // Constructors needed for instantiation
                .build();
    }

    /**
     * Register JWT configuration classes that only need methods for reflection.
     * These classes are created via builder pattern and accessed via getters.
     *
     * @return A {@link ReflectiveClassBuildItem} for the JWT configuration classes
     */
    @BuildStep
    public ReflectiveClassBuildItem registerJwtConfigurationClassesForReflection() {
        return ReflectiveClassBuildItem.builder(
                // Configuration classes created via builder pattern
                IssuerConfig.class,         // Builder pattern, getter methods
                ParserConfig.class,         // Builder pattern, configuration getters
                HttpJwksLoaderConfig.class) // Builder pattern, configuration getters
                .methods(true)    // Methods needed for getter access
                .fields(false)    // No direct field access needed
                .constructors(false) // Created via builder, no constructor reflection needed
                .build();
    }

    /**
     * Register JWT validation pipeline classes for reflection.
     * These are the performance-critical classes in the validation pipeline.
     *
     * @return A {@link ReflectiveClassBuildItem} for JWT validation pipeline classes
     */
    @BuildStep
    public ReflectiveClassBuildItem registerJwtPipelineClassesForReflection() {
        return ReflectiveClassBuildItem.builder(
                // Critical validation pipeline classes
                NonValidatingJwtParser.class,
                TokenSignatureValidator.class,
                TokenHeaderValidator.class,
                TokenClaimValidator.class,
                TokenBuilder.class,
                DecodedJwt.class,
                // JWKS loading classes
                HttpJwksLoader.class,
                JWKSKeyLoader.class,
                KeyInfo.class,
                JwksParser.class,
                // Security and algorithm classes
                SignatureAlgorithmPreferences.class,
                JwkAlgorithmPreferences.class,
                // JWE decryption classes
                JweDecryptor.class,
                JweDecryptionConfig.class,
                JweAlgorithmPreferences.class)
                .methods(false)  // Methods not needed - these are instantiated and called directly
                .fields(false)   // Fields not needed - no direct field access
                .constructors(true) // Only constructors needed for instantiation
                .build();
    }

    /**
     * Register JWT token content classes for reflection.
     * These classes need full reflection for getter/setter access and field binding.
     *
     * @return A {@link ReflectiveClassBuildItem} for JWT token content classes
     */
    @BuildStep
    public ReflectiveClassBuildItem registerJwtTokenContentClassesForReflection() {
        return ReflectiveClassBuildItem.builder(
                // Token content classes - need full reflection for getter/setter access
                AccessTokenContent.class,
                IdTokenContent.class,
                UnvalidatedRefreshToken.class,
                TokenContent.class,
                BaseTokenContent.class,
                MinimalTokenContent.class,
                // MicroProfile JWT interface and adapter for CDI injection
                JsonWebToken.class,
                JsonWebTokenAdapter.class,
                // Claim handling classes - need full reflection for enum handling
                ClaimValue.class,
                ClaimName.class,
                ClaimValueType.class)
                .methods(true)  // Methods needed for getters/setters on token content
                .fields(true)   // Fields needed for direct field access in token content
                .constructors(true) // Constructors needed for instantiation
                .build();
    }

    /**
     * Register JWT claim mapper classes for reflection.
     * These classes only need constructors for instantiation - no method/field access.
     *
     * @return A {@link ReflectiveClassBuildItem} for JWT claim mapper classes
     */
    @BuildStep
    public ReflectiveClassBuildItem registerJwtClaimMapperClassesForReflection() {
        return ReflectiveClassBuildItem.builder(
                // Claim mappers - only need constructors for instantiation
                IdentityMapper.class,
                JsonCollectionMapper.class,
                KeycloakDefaultGroupsMapper.class,
                KeycloakDefaultRolesMapper.class,
                OffsetDateTimeMapper.class,
                ScopeMapper.class,
                StringSplitterMapper.class,
                // CDI-discoverable claim mapper interface
                DiscoverableClaimMapper.class)
                .methods(false)  // Methods not needed - mappers are called via interface
                .fields(false)   // Fields not needed - no field access
                .constructors(true) // Only constructors needed for instantiation
                .build();
    }

    /**
     * Register DSL-JSON service providers for native image.
     * This ensures DSL-JSON converters can be found via service loader at runtime.
     */
    @BuildStep
    public void registerDslJsonServiceProviders(BuildProducer<ServiceProviderBuildItem> serviceProvider) {
        // Register all DSL-JSON configurations from classpath
        serviceProvider.produce(ServiceProviderBuildItem.allProvidersFromClassPath("com.dslplatform.json.Configuration"));

        // Explicitly register our generated converters as service providers
        serviceProvider.produce(new ServiceProviderBuildItem("com.dslplatform.json.Configuration",
                "de.cuioss.sheriff.token.validation.json._WellKnownConfiguration_DslJsonConverter",
                "de.cuioss.sheriff.token.validation.json._Jwks_DslJsonConverter",
                "de.cuioss.sheriff.token.validation.json._JwkKey_DslJsonConverter",
                "de.cuioss.sheriff.token.validation.json._JwtHeader_DslJsonConverter"));
    }


    /**
     * Register classes that need to be initialized at runtime.
     *
     * @return A {@link RuntimeInitializedClassBuildItem} for classes that need runtime initialization
     */
    @BuildStep
    public RuntimeInitializedClassBuildItem runtimeInitializedClasses() {
        return new RuntimeInitializedClassBuildItem(HttpJwksLoader.class.getName());
    }

    /**
     * Register native image resources that JWT validation needs access to.
     * This ensures configuration files and other resources are included in the native image.
     */
    @BuildStep
    public void registerJwtValidationResources(BuildProducer<NativeImageResourceBuildItem> resourceProducer) {
        // Include any JWT validation configuration files that might exist
        resourceProducer.produce(new NativeImageResourceBuildItem("META-INF/services/de.cuioss.sheriff.token.validation.jwks.JwksLoader"));
        resourceProducer.produce(new NativeImageResourceBuildItem("META-INF/services/de.cuioss.sheriff.token.validation.domain.claim.mapper.ClaimMapper"));
    }


    /**
     * Register additional CDI beans for JWT validation.
     *
     * @return A {@link AdditionalBeanBuildItem} for CDI beans that need explicit registration
     */
    @BuildStep
    public AdditionalBeanBuildItem additionalBeans() {
        return AdditionalBeanBuildItem.builder()
                // Explicitly register the CDI producer classes to ensure they're discovered
                .addBeanClasses(
                        TokenValidatorProducer.class,
                        BearerTokenProducer.class,
                        ParserConfigResolver.class,
                        VertxServletObjectsResolver.class,
                        JwtMetricsCollector.class,
                        // CDI-based claim mapper infrastructure
                        ClaimMapperRegistry.class,
                        KeycloakRolesMapperBean.class,
                        KeycloakGroupsMapperBean.class,
                        // CDI-based token validation rule infrastructure
                        TokenValidationRuleRegistry.class
                )
                // Register additional configuration producers using class references
                .addBeanClass(AccessLogFilterConfigResolver.class)
                .addBeanClass(CustomAccessLogFilter.class)
                // Register interceptor infrastructure
                .addBeanClass(BearerTokenInterceptor.class)
                .setUnremovable()
                .build();
    }

    /**
     * Register the CustomAccessLogFilter as a JAX-RS provider.
     * This is required for Quarkus extensions to properly register JAX-RS providers.
     *
     * @return A {@link ResteasyJaxrsProviderBuildItem} for the CustomAccessLogFilter
     */
    @BuildStep
    public ResteasyJaxrsProviderBuildItem registerCustomAccessLogFilter() {
        return new ResteasyJaxrsProviderBuildItem(CustomAccessLogFilter.class.getName());
    }

    /**
     * Register core JWT validation beans as unremovable to ensure they're available for injection.
     * This is critical for native image compilation where CDI discovery can be limited.
     *
     * @param unremovableBeans producer for unremovable bean build items
     */
    @BuildStep
    public void registerUnremovableBeans(BuildProducer<UnremovableBeanBuildItem> unremovableBeans) {
        // Ensure core library beans are never removed from the CDI container
        unremovableBeans.produce(UnremovableBeanBuildItem.beanTypes(
                DotName.createSimple(TokenValidator.class.getName()),
                DotName.createSimple(JwtMetricsCollector.class.getName()),
                DotName.createSimple(MeterRegistry.class.getName()),
                // Ensure user-provided DiscoverableClaimMapper implementations are discovered
                DotName.createSimple(DiscoverableClaimMapper.class.getName()),
                // Ensure user-provided DiscoverableTokenValidationRule implementations are discovered
                DotName.createSimple(DiscoverableTokenValidationRule.class.getName())
        ));
    }


    /**
     * Create DevUI card page for JWT validation monitoring and debugging.
     *
     * @return A {@link CardPageBuildItem} for the JWT DevUI card
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    public CardPageBuildItem createJwtDevUICard() {
        CardPageBuildItem cardPageBuildItem = new CardPageBuildItem();

        // Status & Config page (merged view of validation status, JWKS endpoints, and configuration)
        cardPageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:shield-halved")
                .title("Status & Config")
                .componentLink("qwc-jwt-status-config.js"));

        // Token Debugging Tools page
        cardPageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:bug")
                .title("Token Debugger")
                .componentLink("qwc-jwt-debugger.js"));

        return cardPageBuildItem;
    }

    /**
     * Register JSON-RPC providers for DevUI runtime data access.
     *
     * @return A {@link JsonRPCProvidersBuildItem} for JWT DevUI JSON-RPC methods
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    public JsonRPCProvidersBuildItem createJwtDevUIJsonRPCService() {
        return new JsonRPCProvidersBuildItem("TokenSheriffDevUI", TokenSheriffDevUIRuntimeService.class);
    }

}
