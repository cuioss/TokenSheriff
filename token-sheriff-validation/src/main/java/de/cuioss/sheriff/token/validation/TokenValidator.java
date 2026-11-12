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
package de.cuioss.sheriff.token.validation;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.cache.AccessTokenCacheConfig;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.domain.context.IdTokenRequest;
import de.cuioss.sheriff.token.validation.domain.context.RefreshTokenRequest;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import de.cuioss.sheriff.token.validation.domain.token.IdTokenContent;
import de.cuioss.sheriff.token.validation.domain.token.SignatureVerifiedToken;
import de.cuioss.sheriff.token.validation.domain.token.TokenContent;
import de.cuioss.sheriff.token.validation.domain.token.UnvalidatedRefreshToken;
import de.cuioss.sheriff.token.validation.dpop.DpopProofValidator;
import de.cuioss.sheriff.token.validation.dpop.DpopReplayProtection;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig;
import de.cuioss.sheriff.token.validation.jwks.JwksLoader;
import de.cuioss.sheriff.token.validation.metrics.*;
import de.cuioss.sheriff.token.validation.pipeline.*;
import de.cuioss.sheriff.token.validation.pipeline.validator.TokenClaimValidator;
import de.cuioss.sheriff.token.validation.pipeline.validator.TokenHeaderValidator;
import de.cuioss.sheriff.token.validation.pipeline.validator.TokenSignatureValidator;
import de.cuioss.sheriff.token.validation.pipeline.validator.TokenStringValidator;
import de.cuioss.sheriff.token.validation.pipeline.validator.TokenValidationRule;
import de.cuioss.tools.logging.CuiLogger;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main entry point for creating and validating JWT tokens.
 * <p>
 * This class provides methods for creating different types of tokens from
 * JWT strings, handling the validation and parsing process.
 * <p>
 * The validator uses a pipeline approach to validate tokens:
 * <ol>
 *   <li>Basic format validation</li>
 *   <li>Issuer validation</li>
 *   <li>Header validation</li>
 *   <li>Signature validation</li>
 *   <li>Token building</li>
 *   <li>Claim validation</li>
 * </ol>
 * <p>
 * {@link #verifySignature(String)} is a separate entry that stops after the signature: it returns a
 * {@link SignatureVerifiedToken} carrying the mapped claims and the verbatim {@code typ} header with
 * <strong>no claim verdict</strong>. Its result is not an authentication decision and is not a
 * {@code TokenContent}.
 * <p>
 * {@link #getRawClaims(TokenContent)} returns the payload of a validated token as a native JSON map by
 * decoding its raw token again with this validator's parser and bounds.
 * <p>
 * This class is thread-safe after construction.
 * All validation methods can be called concurrently from multiple threads.
 * <p>
 * <strong>Lifecycle:</strong> the validator takes ownership of the JWKS loaders of the enabled issuer
 * configurations passed to it. {@link #close()} closes them, stopping their background refresh, so an
 * {@link IssuerConfig} (or a pre-built JWKS loader set on it) must not be shared with a validator that
 * outlives this one.
 * <p>
 * <strong>Design Note:</strong> This class intentionally implements the facade/factory pattern,
 * orchestrating the creation and wiring of all JWT validation components. The higher dependency
 * count is a natural consequence of this design pattern and is acceptable for a public API
 * entry point that encapsulates significant complexity.
 * <p>
 * Usage example:
 * {@snippet :
 * // Configure HTTP-based JWKS loading (a direct jwksUrl requires the issuer identifier)
 * HttpJwksLoaderConfig httpConfig = HttpJwksLoaderConfig.builder()
 *     .jwksUrl("https://example.com/.well-known/jwks.json")
 *     .issuerIdentifier("https://example.com")
 *     .refreshIntervalSeconds(60)
 *     .build();
 *
 * // Create an issuer configuration
 * IssuerConfig issuerConfig = IssuerConfig.builder()
 *     .issuerIdentifier("https://example.com")
 *     .expectedAudience("my-client")
 *     .httpJwksLoaderConfig(httpConfig)
 *     .build(); // Validation happens automatically
 *
 * // Create the token validator with custom metrics and cache configuration
 * TokenValidatorMonitorConfig metricsConfig = TokenValidatorMonitorConfig.builder()
 *     .windowSize(200)
 *     .measurementType(MeasurementType.SIGNATURE_VALIDATION)
 *     .measurementType(MeasurementType.COMPLETE_VALIDATION)
 *     .build();
 *
 * // Configure access token caching
 * AccessTokenCacheConfig cacheConfig = AccessTokenCacheConfig.builder()
 *     .maxSize(500)  // Set to 0 to disable caching
 *     .evictionIntervalSeconds(300L)
 *     .build();
 *
 * // The validator creates a SecurityEventCounter internally and passes it to all components
 * TokenValidator tokenValidator = TokenValidator.builder()
 *     .parserConfig(ParserConfig.builder().build())
 *     .issuerConfig(issuerConfig)
 *     .monitorConfig(metricsConfig)  // Optional: null means no types monitored
 *     .cacheConfig(cacheConfig)      // Optional: null means default caching enabled
 *     .build();
 *
 * // Parse an access token
 * AccessTokenContent accessToken = tokenValidator.createAccessToken(AccessTokenRequest.of(tokenString));
 *
 * // Parse an ID token
 * IdTokenContent idToken = tokenValidator.createIdToken(IdTokenRequest.of(tokenString));
 *
 * // Parse a refresh token
 * UnvalidatedRefreshToken refreshToken = tokenValidator.createRefreshToken(RefreshTokenRequest.of(tokenString));
 *
 * // Access the security event counter for monitoring
 * SecurityEventCounter securityEventCounter = tokenValidator.getSecurityEventCounter();
 *
 * // Access the performance monitor for detailed pipeline metrics
 * TokenValidatorMonitor performanceMonitor = tokenValidator.getPerformanceMonitor();
 * Optional<StripedRingBufferStatistics> metrics =
 *         performanceMonitor.getValidationMetrics(MeasurementType.SIGNATURE_VALIDATION);
 * Optional<Duration> p50SignatureTime = metrics.map(StripedRingBufferStatistics::p50);
 * }
 * <p>
 * Implements requirements:
 * <ul>
 *   <li><a href="../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-1">VALIDATION-1: Token Parsing and Validation</a></li>
 *   <li><a href="../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-2">VALIDATION-2: Token Representation</a></li>
 *   <li><a href="../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-3">VALIDATION-3: Multi-Issuer Support</a></li>
 * </ul>
 * <p>
 * For more detailed specifications, see the
 * <a href="https://github.com/cuioss/TokenSheriff/tree/main/doc/validation/architecture.adoc#_tokenvalidator">Technical Components Specification</a>
 *
 * @since 1.0
 */
@SuppressWarnings({"JavadocLinkAsPlainText", "java:S6539"}) // java:S6539: Intentional facade pattern - high coupling is by design
public class TokenValidator implements Closeable {

    private static final CuiLogger LOGGER = new CuiLogger(TokenValidator.class);

    /**
     * Counter for security events that occur during token processing.
     * This counter is thread-safe and can be accessed from outside to monitor security events.
     */
    @Getter
    private final SecurityEventCounter securityEventCounter;

    /**
     * Monitor for performance metrics of JWT validation pipeline steps.
     * This monitor is thread-safe and provides detailed timing measurements for each validation step.
     */
    @Getter
    private final TokenValidatorMonitor performanceMonitor;

    /**
     * Validator for pre-pipeline token string validation (null, blank, size checks).
     * This validator runs before any pipeline processing to fail fast on invalid inputs.
     */
    private final TokenStringValidator tokenStringValidator;

    /**
     * Pipeline for validating access tokens with caching support.
     * Handles full validation including early cache checks before expensive operations.
     */
    private final AccessTokenValidationPipeline accessTokenPipeline;

    /**
     * Pipeline for validating ID tokens without caching.
     * Handles full validation for ID tokens used during authentication flows.
     */
    private final IdTokenValidationPipeline idTokenPipeline;

    /**
     * Pipeline for validating refresh tokens with minimal validation.
     * Handles opaque or lightly validated refresh tokens.
     */
    private final RefreshTokenValidationPipeline refreshTokenPipeline;

    /**
     * The parser shared by the pipelines, bound by this validator's {@link ParserConfig} and JWE
     * decryption configuration. Kept to re-derive the native claims of a validated token in
     * {@link #getRawClaims(TokenContent)}.
     */
    private final NonValidatingJwtParser jwtParser;

    /**
     * Shared DPoP replay protection instance, or null if no issuer has DPoP enabled.
     * Stored for lifecycle management ({@link #close()}).
     */
    @Nullable
    private final DpopReplayProtection dpopReplayProtection;

    /**
     * The JWKS loaders of the enabled issuer configurations. The validator owns them: {@link #close()}
     * closes every loader that is {@link AutoCloseable}.
     */
    private final List<JwksLoader> jwksLoaders;

    /**
     * Private constructor used by builder.
     */
    @Builder
    private TokenValidator(
            @Nullable ParserConfig parserConfig,
            @Singular List<IssuerConfig> issuerConfigs,
            @Singular List<TokenValidationRule> tokenValidationRules,
            @Nullable TokenValidatorMonitorConfig monitorConfig,
            @Nullable AccessTokenCacheConfig cacheConfig,
            @Nullable JweDecryptionConfig jweDecryptionConfig) {

        if (issuerConfigs.isEmpty()) {
            throw new IllegalArgumentException("At least one issuer configuration must be provided");
        }
        if (issuerConfigs.contains(null)) {
            throw new IllegalArgumentException("Issuer configurations must not contain null elements");
        }

        // Disabled configs skip build-time validation and have no JwksLoader — they must not
        // participate in validator construction. IssuerConfigCache performs the same filtering
        // (and logs the skipped configs).
        List<IssuerConfig> enabledIssuerConfigs = issuerConfigs.stream()
                .filter(IssuerConfig::isEnabled)
                .toList();
        if (enabledIssuerConfigs.isEmpty()) {
            throw new IllegalArgumentException("At least one enabled issuer configuration must be provided");
        }
        this.jwksLoaders = enabledIssuerConfigs.stream()
                .map(IssuerConfig::getJwksLoader)
                .toList();

        // Use default ParserConfig if not provided
        if (parserConfig == null) {
            parserConfig = ParserConfig.builder().build();
        }

        LOGGER.debug("Initialize token validator with %s and %s issuer configurations", parserConfig, issuerConfigs.size());

        // Always create new instances internally
        this.securityEventCounter = new SecurityEventCounter();

        // Create monitor based on configuration
        if (monitorConfig != null) {
            this.performanceMonitor = monitorConfig.createMonitor();
        } else {
            // Default: disabled monitoring
            this.performanceMonitor = TokenValidatorMonitorConfig.disabled().createMonitor();
        }

        // The parser is shared by the pipelines and kept for getRawClaims(TokenContent).
        // The JWE config hands the parser the effective JWE size limit, the same one TokenStringValidator
        // applies below, so the access, ID and refresh pipelines (which share this parser) accept a JWE up
        // to maxEncryptedTokenSize while a JWS and the inner JWS stay bound by ParserConfig.maxTokenSize.
        this.jwtParser = NonValidatingJwtParser.builder()
                .config(parserConfig)
                .securityEventCounter(this.securityEventCounter)
                .jweDecryptionConfig(jweDecryptionConfig)
                .build();

        // Let the IssuerConfigCache handle all issuer config processing
        IssuerConfigCache issuerConfigCache = new IssuerConfigCache(issuerConfigs, this.securityEventCounter);

        // Initialize immutable map of TokenSignatureValidator instances for each issuer
        // This eliminates the performance bottleneck of creating new instances on every validation
        Map<String, TokenSignatureValidator> signatureValidatorsMap = new HashMap<>();
        Map<String, SignatureTemplateManager> signatureTemplateManagers = new HashMap<>();
        Map<String, TokenBuilder> tokenBuildersMap = new HashMap<>();
        Map<String, TokenClaimValidator> claimValidatorsMap = new HashMap<>();
        Map<String, TokenHeaderValidator> headerValidatorsMap = new HashMap<>();

        for (IssuerConfig issuerConfig : enabledIssuerConfigs) {
            String issuerIdentifier = issuerConfig.getIssuerIdentifier();

            // Create one shared SignatureTemplateManager per issuer (reused by signature + DPoP validators)
            SignatureTemplateManager signatureTemplateManager = new SignatureTemplateManager(issuerConfig.getAlgorithmPreferences());
            signatureTemplateManagers.put(issuerIdentifier, signatureTemplateManager);

            // Initialize signature validator
            TokenSignatureValidator signatureValidator = new TokenSignatureValidator(
                    issuerConfig.getJwksLoader(),
                    this.securityEventCounter,
                    signatureTemplateManager
            );
            signatureValidatorsMap.put(issuerIdentifier, signatureValidator);
            LOGGER.debug("Pre-created TokenSignatureValidator for issuer: %s", issuerIdentifier);

            // Initialize token builder
            TokenBuilder tokenBuilder = new TokenBuilder(issuerConfig, this.securityEventCounter);
            tokenBuildersMap.put(issuerIdentifier, tokenBuilder);
            LOGGER.debug("Pre-created TokenBuilder for issuer: %s", issuerIdentifier);

            // Initialize claim validator
            TokenClaimValidator claimValidator = new TokenClaimValidator(issuerConfig, this.securityEventCounter);
            claimValidatorsMap.put(issuerIdentifier, claimValidator);
            LOGGER.debug("Pre-created TokenClaimValidator for issuer: %s", issuerIdentifier);

            // Initialize header validator
            TokenHeaderValidator headerValidator = new TokenHeaderValidator(issuerConfig, this.securityEventCounter);
            headerValidatorsMap.put(issuerIdentifier, headerValidator);
            LOGGER.debug("Pre-created TokenHeaderValidator for issuer: %s", issuerIdentifier);
        }

        Map<String, TokenSignatureValidator> signatureValidators = Map.copyOf(signatureValidatorsMap);
        Map<String, TokenBuilder> tokenBuilders = Map.copyOf(tokenBuildersMap);
        Map<String, TokenClaimValidator> claimValidators = Map.copyOf(claimValidatorsMap);
        Map<String, TokenHeaderValidator> headerValidators = Map.copyOf(headerValidatorsMap);

        // Initialize DPoP validators for issuers that have DPoP configured
        // Shared replay protection across all issuers (jti must be globally unique per RFC 9449)
        this.dpopReplayProtection = createDpopReplayProtection(enabledIssuerConfigs);
        Map<String, DpopProofValidator> dpopValidators = createDpopValidators(enabledIssuerConfigs,
                this.securityEventCounter, this.dpopReplayProtection, signatureTemplateManagers, parserConfig);

        // Use default cache config if not provided
        if (cacheConfig == null) {
            cacheConfig = AccessTokenCacheConfig.defaultConfig();
        }

        // Construct TokenStringValidator for pre-pipeline validation
        // When JWE is configured, a 5-part compact JWE is bound by maxEncryptedTokenSize and every other
        // token by the larger of the two limits (the parser applies the exact bound)
        this.tokenStringValidator = new TokenStringValidator(
                parserConfig, this.securityEventCounter, jweDecryptionConfig);
        LOGGER.debug("TokenStringValidator initialized");

        // Construct RefreshTokenValidationPipeline (minimal validation, no cache, no metrics, no security events)
        // Uses jwtParser.decodeOpaqueToken() internally to avoid false-positive events for opaque tokens
        this.refreshTokenPipeline = new RefreshTokenValidationPipeline(jwtParser);
        LOGGER.debug("RefreshTokenValidationPipeline initialized");

        // Construct IdTokenValidationPipeline (full validation, no cache, no metrics)
        this.idTokenPipeline = new IdTokenValidationPipeline(
                jwtParser,
                issuerConfigCache,
                signatureValidators,
                tokenBuilders,
                claimValidators,
                headerValidators,
                this.securityEventCounter);
        LOGGER.debug("IdTokenValidationPipeline initialized");

        // Construct AccessTokenValidationPipeline (full validation, with cache and metrics)
        // Pipeline creates its own AccessTokenCache from config
        this.accessTokenPipeline = new AccessTokenValidationPipeline(
                jwtParser,
                issuerConfigCache,
                signatureValidators,
                tokenBuilders,
                claimValidators,
                headerValidators,
                dpopValidators,
                tokenValidationRules,
                cacheConfig,
                this.securityEventCounter,
                this.performanceMonitor);
        LOGGER.debug("AccessTokenValidationPipeline initialized with cache maxSize=%s, evictionInterval=%ss",
                cacheConfig.getMaxSize(), cacheConfig.getEvictionIntervalSeconds());

        LOGGER.info(JWTValidationLogMessages.INFO.TOKEN_FACTORY_INITIALIZED,
                "%s enabled issuer(s): %s".formatted(enabledIssuerConfigs.size(),
                        enabledIssuerConfigs.stream().map(IssuerConfig::getIssuerIdentifier).toList()));

        if (jweDecryptionConfig != null) {
            LOGGER.info(JWTValidationLogMessages.INFO.JWE_DECRYPTION_ENABLED, jweDecryptionConfig.getKeyCount());
        }
    }

    /**
     * Creates the DPoP replay protection shared by all DPoP-enabled issuers. It is sized with the
     * maximum nonce-cache TTL and the maximum nonce-cache size across those issuers, for consistent
     * behavior.
     *
     * @param enabledIssuerConfigs the enabled issuer configurations
     * @return the shared replay protection, or {@code null} if no issuer has DPoP enabled
     */
    private static @Nullable DpopReplayProtection createDpopReplayProtection(List<IssuerConfig> enabledIssuerConfigs) {
        long maxNonceCacheTtl = 0;
        int maxNonceCacheSize = 0;
        for (IssuerConfig issuerConfig : enabledIssuerConfigs) {
            if (issuerConfig.getDpopConfig() != null) {
                maxNonceCacheTtl = Math.max(maxNonceCacheTtl, issuerConfig.getDpopConfig().getNonceCacheTtlSeconds());
                maxNonceCacheSize = Math.max(maxNonceCacheSize, issuerConfig.getDpopConfig().getNonceCacheSize());
            }
        }
        return maxNonceCacheSize > 0
                ? new DpopReplayProtection(maxNonceCacheTtl, maxNonceCacheSize)
                : null;
    }

    /**
     * Creates one {@link DpopProofValidator} per DPoP-enabled issuer.
     *
     * @param enabledIssuerConfigs      the enabled issuer configurations
     * @param securityEventCounter      the counter for security events
     * @param dpopReplayProtection      the shared replay protection, {@code null} if no issuer has DPoP enabled
     * @param signatureTemplateManagers the per-issuer signature template managers
     * @param parserConfig              the parser configuration
     * @return an immutable map of issuer identifier to DPoP proof validator
     */
    private static Map<String, DpopProofValidator> createDpopValidators(List<IssuerConfig> enabledIssuerConfigs,
            SecurityEventCounter securityEventCounter, @Nullable DpopReplayProtection dpopReplayProtection,
            Map<String, SignatureTemplateManager> signatureTemplateManagers, ParserConfig parserConfig) {
        Map<String, DpopProofValidator> dpopValidatorsMap = new HashMap<>();
        for (IssuerConfig issuerConfig : enabledIssuerConfigs) {
            if (issuerConfig.getDpopConfig() != null) {
                DpopProofValidator dpopValidator = new DpopProofValidator(
                        issuerConfig, securityEventCounter, dpopReplayProtection,
                        signatureTemplateManagers.get(issuerConfig.getIssuerIdentifier()),
                        parserConfig, parserConfig.getDslJson());
                dpopValidatorsMap.put(issuerConfig.getIssuerIdentifier(), dpopValidator);
                LOGGER.debug("Pre-created DpopProofValidator for issuer: %s", issuerConfig.getIssuerIdentifier());
            }
        }
        return Map.copyOf(dpopValidatorsMap);
    }


    /**
     * Creates an access token from the given request.
     *
     * @param request the access token validation request containing the token string and HTTP headers
     * @return The parsed access token
     * @throws TokenValidationException if the token is invalid
     */
    public AccessTokenContent createAccessToken(AccessTokenRequest request) {
        LOGGER.debug("Creating access token");

        // Record complete validation time
        MetricsTicker completeTicker = MetricsTickerFactory.createStartedTicker(MeasurementType.COMPLETE_VALIDATION, performanceMonitor);
        try {
            // Pre-pipeline validation (null, blank, size)
            tokenStringValidator.validate(request.tokenString());

            // Delegate to access token pipeline (handles caching, metrics, full validation)
            AccessTokenContent result = accessTokenPipeline.validate(request);

            LOGGER.debug("Successfully created access token");
            securityEventCounter.increment(SecurityEventCounter.EventType.ACCESS_TOKEN_CREATED);

            return result;
        } finally {
            completeTicker.stopAndRecord();
        }
    }

    /**
     * Creates an ID token from the given request.
     *
     * @param request the ID token validation request containing the token string and HTTP headers
     * @return The parsed ID token
     * @throws TokenValidationException if the token is invalid
     */
    public IdTokenContent createIdToken(IdTokenRequest request) {
        LOGGER.debug("Creating ID token");

        // Pre-pipeline validation (null, blank, size)
        tokenStringValidator.validate(request.tokenString());

        // Delegate to ID token pipeline (handles full validation, no caching, no metrics)
        IdTokenContent validatedToken = idTokenPipeline.validate(request);

        LOGGER.debug("Successfully created ID-Token");
        securityEventCounter.increment(SecurityEventCounter.EventType.ID_TOKEN_CREATED);

        return validatedToken;
    }

    /**
     * Creates a refresh token from the given request.
     *
     * @param request the refresh token validation request containing the token string and HTTP headers
     * @return The parsed refresh token
     * @throws TokenValidationException if the token is invalid
     */
    public UnvalidatedRefreshToken createRefreshToken(RefreshTokenRequest request) {
        LOGGER.debug("Creating refresh token");

        // Pre-pipeline validation (null, blank, size)
        tokenStringValidator.validate(request.tokenString());

        // Delegate to refresh token pipeline (handles minimal validation, no caching, no metrics)
        UnvalidatedRefreshToken refreshToken = refreshTokenPipeline.validate(request);

        LOGGER.debug("Successfully created Refresh-Token");
        securityEventCounter.increment(SecurityEventCounter.EventType.REFRESH_TOKEN_CREATED);

        return refreshToken;
    }

    /**
     * Verifies the signature of a token without reaching any claim verdict.
     * <p>
     * <strong>WARNING:</strong> the result is not an authentication decision. The token string limits,
     * issuer resolution (the issuer must be enabled and its JWKS loader healthy), the header checks for
     * algorithm, key ID and embedded JWK, and the signature are verified, and the claims are mapped.
     * No claim is validated: an expired token, a token issued for another audience or a token without
     * {@code sub} or {@code azp} is returned as long as its signature is valid. The issuer's expected
     * token type is not checked; the {@code typ} header is returned verbatim for the caller to judge.
     * A JWE is decrypted when JWE decryption is configured.
     * <p>
     * The access-token cache is neither read nor written, and no performance measurement is recorded.
     * A success counts {@link SecurityEventCounter.EventType#SIGNATURE_ONLY_VERIFIED} once; a refusal
     * counts the event of the stage that refused the token.
     *
     * @param rawToken the raw token string
     * @return the signature-verified token carrying the unvalidated claims and the verbatim {@code typ}
     * @throws TokenValidationException if the token string, the issuer, the header or the signature is
     *                                  refused, or a claim cannot be mapped
     */
    public SignatureVerifiedToken verifySignature(String rawToken) {
        LOGGER.debug("Verifying token signature");

        // Pre-pipeline validation (null, blank, size)
        tokenStringValidator.validate(rawToken);

        // Delegate to the ID token pipeline's signature-only sibling (no claim validation, no cache, no metrics)
        SignatureVerifiedToken verifiedToken = idTokenPipeline.verifySignature(rawToken);

        LOGGER.debug("Successfully verified token signature");
        securityEventCounter.increment(SecurityEventCounter.EventType.SIGNATURE_ONLY_VERIFIED);

        return verifiedToken;
    }

    /**
     * Returns the payload of a validated token as a native JSON map.
     * <p>
     * The token's raw token is decoded again with this validator's own parser, so the
     * {@link ParserConfig} size, depth, array and string bounds apply, and a JWE is decrypted with this
     * validator's JWE configuration to read the inner payload. A payload the parser refuses is refused as
     * a whole, never partially returned. The token content itself is unchanged and nothing is cached: each
     * call decodes, and for a JWE decrypts, again.
     * <p>
     * JSON objects are unmodifiable maps in member order, JSON arrays unmodifiable lists in element order,
     * and numbers, booleans and strings keep their JSON type; a {@code null} nested inside an object or
     * array is kept, a top-level {@code null} member is omitted.
     *
     * @param token a token validated by this validator
     * @return the deep-immutable native claims of the token, never {@code null}
     * @throws TokenValidationException if the raw token cannot be decoded within this validator's bounds
     */
    public Map<String, Object> getRawClaims(TokenContent token) {
        return jwtParser.decode(token.getRawToken()).body().toNativeClaims();
    }

    /**
     * Shuts down internal resources (cache eviction executor, DPoP replay protection) and closes the
     * JWKS loaders of the enabled issuer configurations.
     * <p>
     * Every JWKS loader that is {@link AutoCloseable} is closed, which stops its background refresh
     * and releases an executor it owns. Ownership of those loaders is transferred to this validator:
     * an {@link IssuerConfig} (or a pre-built JWKS loader set on it) must not be shared with a
     * validator that outlives this one. An {@link Exception} closing one loader is logged and neither
     * stops the remaining loaders nor the shutdown of the other resources. A loader whose close is
     * interrupted does not stop the remaining loaders either; the calling thread's interrupt flag is
     * restored. An {@link Error} thrown by the close of one loader is not contained: it leaves the loop
     * over the loaders, so the loaders after it are not closed, and it is handled as the failure of the
     * JWKS release step described below.
     * <p>
     * The release runs in three steps: the access token pipeline shutdown, the DPoP replay protection
     * close and the JWKS loader close. A failure of one step does not prevent the steps after it: each
     * step runs even when an earlier step failed with any {@link Throwable}, an {@link Error} included.
     * This guarantee holds between the three steps only, not between the loaders inside the JWKS step.
     * Such a failure (for example a {@link SecurityException} from an executor shutdown) is still
     * propagated to the caller, unwrapped, once the remaining steps ran; when more than one step fails,
     * the failure of the last failing step is propagated and the failure of each earlier failing step
     * is attached to it as suppressed, so none is lost, provided the propagated throwable has
     * suppression enabled: {@link Throwable#addSuppressed(Throwable)} records nothing on a throwable
     * created with suppression disabled.
     * <p>
     * This should be called when the TokenValidator is no longer needed.
     */
    @Override
    public void close() {
        releaseInOrder(accessTokenPipeline::shutdown, this::closeDpopReplayProtection, this::closeJwksLoaders);
    }

    /**
     * Runs the release steps in order, each one even when an earlier one failed with any
     * {@link Throwable}, an {@link Error} included. Once every step ran, the failure of the last
     * failing step is propagated unwrapped and the failure of every earlier failing step is attached
     * to it as suppressed with {@link Throwable#addSuppressed(Throwable)}, so no failure is lost —
     * provided the propagated throwable has suppression enabled; on one created with suppression
     * disabled the earlier failures are not recorded. An earlier failure that is the same instance as
     * the propagated one is not attached to itself.
     * <p>
     * The guarantee covers the steps as units. What happens inside a step is up to the step: for
     * {@code close()} the JWKS step contains an {@link Exception} from one loader but not an
     * {@link Error}, which ends that step early and leaves the remaining loaders unclosed.
     *
     * @param steps the release steps, in the order they run
     */
    static void releaseInOrder(Runnable... steps) {
        Throwable propagated = null;
        List<Throwable> earlier = new ArrayList<>();
        for (Runnable step : steps) {
            try {
                step.run();
            }
            // cui-rewrite:disable InvalidExceptionUsageRecipe
            // Intentional catch-all: a release step may fail with any unchecked exception or Error, and
            // one failing step must neither stop the remaining steps nor lose its failure. The failure
            // is rethrown once every step ran.
            catch (Throwable e) { // NOSONAR java:S1181 - every release step must run after any Throwable, an Error included
                if (propagated != null) {
                    earlier.add(propagated);
                }
                propagated = e;
            }
        }
        if (propagated != null) {
            for (Throwable failure : earlier) {
                if (failure != propagated) {
                    propagated.addSuppressed(failure);
                }
            }
            throwUnchecked(propagated);
        }
    }

    /**
     * Rethrows a failure caught from a {@link Runnable} release step as it is, without wrapping it and
     * without making the caller declare a checked exception. Such a failure is an unchecked exception
     * or an {@link Error}, as {@link Runnable#run()} declares no checked exception.
     *
     * @param failure the failure to rethrow
     * @param <T>     the type the compiler sees thrown, inferred as an unchecked exception at the call site
     * @throws T always, the given failure itself
     */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUnchecked(Throwable failure) throws T {
        throw (T) failure;
    }

    private void closeDpopReplayProtection() {
        if (dpopReplayProtection != null) {
            dpopReplayProtection.close();
        }
    }

    private void closeJwksLoaders() {
        for (JwksLoader loader : jwksLoaders) {
            if (loader instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (InterruptedException e) {
                    // Restore the interrupt flag for the caller, but keep closing the remaining loaders.
                    Thread.currentThread().interrupt();
                    LOGGER.debug(e, "Interrupted while closing JWKS loader %s", loader.getClass().getSimpleName());
                }
                // cui-rewrite:disable InvalidExceptionUsageRecipe
                // Intentional catch-all: AutoCloseable.close() declares Exception, and one failing
                // loader must not keep the others running. Only the class name is logged: a custom
                // loader's accessors may themselves throw and must not escape this block.
                catch (Exception e) {
                    LOGGER.debug(e, "Failed to close JWKS loader %s", loader.getClass().getSimpleName());
                }
            }
        }
    }
}
