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
package de.cuioss.sheriff.token.validation.pipeline;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.IssuerConfigCache;
import de.cuioss.sheriff.token.validation.JWTValidationLogMessages;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.domain.context.IdTokenRequest;
import de.cuioss.sheriff.token.validation.domain.context.ValidationContext;
import de.cuioss.sheriff.token.validation.domain.token.IdTokenContent;
import de.cuioss.sheriff.token.validation.domain.token.SignatureVerifiedToken;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.pipeline.validator.TokenClaimValidator;
import de.cuioss.sheriff.token.validation.pipeline.validator.TokenHeaderValidator;
import de.cuioss.sheriff.token.validation.pipeline.validator.TokenSignatureValidator;
import de.cuioss.tools.logging.CuiLogger;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Pipeline for validating ID tokens.
 * <p>
 * ID tokens require full validation but are typically validated only once during
 * login flows, so they are not cached. This pipeline performs complete cryptographic
 * validation and claims checking.
 * <p>
 * <strong>Validation Steps:</strong>
 * <ol>
 *   <li>Parse token into header and payload</li>
 *   <li>Extract and validate issuer claim</li>
 *   <li>Resolve issuer configuration</li>
 *   <li>Validate JWT header</li>
 *   <li>Validate JWT signature (cryptographic verification)</li>
 *   <li>Build typed IdTokenContent object</li>
 *   <li>Validate token claims (expiration, audience, etc.)</li>
 * </ol>
 * <p>
 * {@link #verifySignature(String)} runs steps 1 to 6 for a signature-only verification: the header
 * check skips the issuer's expected token type, claims are mapped without building an
 * {@code IdTokenContent}, and step 7 does not run.
 * <p>
 * <strong>Note:</strong> TokenStringValidator has already validated that the token
 * is non-null, non-blank, and within size limits before this pipeline is called.
 * <p>
 * <strong>No Metrics Instrumentation:</strong> ID token validation uses
 * {@code NoOpMetricsTicker} and does not record performance metrics.
 * <p>
 * <strong>No Caching:</strong> ID tokens are not cached.
 * <p>
 * This class is thread-safe after construction. All validators are pre-created
 * and cached in immutable maps for optimal performance.
 *
 * @apiNote This class is internal to Token-Sheriff and not part of the public API.
 * @since 1.0
 * @author Oliver Wolff
 */
public class IdTokenValidationPipeline {

    private static final CuiLogger LOGGER = new CuiLogger(IdTokenValidationPipeline.class);

    private final NonValidatingJwtParser jwtParser;
    private final IssuerConfigCache issuerConfigCache;
    private final Map<String, TokenSignatureValidator> signatureValidators;
    private final Map<String, TokenBuilder> tokenBuilders;
    private final Map<String, TokenClaimValidator> claimValidators;
    private final Map<String, TokenHeaderValidator> headerValidators;
    private final SecurityEventCounter securityEventCounter;

    /**
     * Creates a new IdTokenValidationPipeline.
     *
     * @param jwtParser the JWT parser for decoding tokens
     * @param issuerConfigCache the resolver for issuer configurations
     * @param signatureValidators pre-created signature validators keyed by issuer
     * @param tokenBuilders pre-created token builders keyed by issuer
     * @param claimValidators pre-created claim validators keyed by issuer
     * @param headerValidators pre-created header validators keyed by issuer
     * @param securityEventCounter the security event counter for tracking operations
     */
    public IdTokenValidationPipeline(NonValidatingJwtParser jwtParser,
            IssuerConfigCache issuerConfigCache,
            Map<String, TokenSignatureValidator> signatureValidators,
            Map<String, TokenBuilder> tokenBuilders,
            Map<String, TokenClaimValidator> claimValidators,
            Map<String, TokenHeaderValidator> headerValidators,
            SecurityEventCounter securityEventCounter) {
        this.jwtParser = jwtParser;
        this.issuerConfigCache = issuerConfigCache;
        this.signatureValidators = signatureValidators;
        this.tokenBuilders = tokenBuilders;
        this.claimValidators = claimValidators;
        this.headerValidators = headerValidators;
        this.securityEventCounter = securityEventCounter;
    }

    /**
     * Validates and returns an ID token.
     * <p>
     * Performs complete validation including parsing, signature verification,
     * and claims validation. All validation steps must succeed for the token
     * to be considered valid.
     *
     * @param request the ID token validation request (token string guaranteed non-null, non-blank, within size limits)
     * @return the validated ID token content
     * @throws TokenValidationException if any validation step fails
     */
    public IdTokenContent validate(IdTokenRequest request) {
        LOGGER.debug("Validating ID token");

        // TokenStringValidator has already checked: null, blank, size

        // 1-5. Parse, extract issuer, resolve issuer config, validate header and signature
        VerifiedJwt verified = parseAndVerify(request.tokenString(),
                (headerValidator, decodedJwt) -> headerValidator.validate(decodedJwt, request));
        DecodedJwt decodedJwt = verified.decodedJwt();
        IssuerConfig issuerConfig = verified.issuerConfig();

        // 6. Build token
        TokenBuilder tokenBuilder = ValidatorLookup.getOrThrow(tokenBuilders, issuerConfig.getIssuerIdentifier(), "token builder");
        IdTokenContent token = tokenBuilder.createIdToken(decodedJwt)
                .orElseThrow(() -> {
                    LOGGER.debug("ID token building failed");
                    return new TokenValidationException(
                            SecurityEventCounter.EventType.MISSING_CLAIM,
                            "Failed to build ID token from decoded JWT"
                    );
                });

        // 7. Validate claims
        // Create ValidationContext with cached current time to eliminate synchronous OffsetDateTime.now() calls
        // Use per-issuer clock skew and max token age from IssuerConfig
        ValidationContext context = new ValidationContext(
                issuerConfig.getClockSkewSeconds(),
                issuerConfig.getMaxTokenAgeSeconds());
        IdTokenContent validatedToken = (IdTokenContent) ValidatorLookup.getOrThrow(claimValidators, issuerConfig.getIssuerIdentifier(), "claim validator")
                .validate(token, context);

        LOGGER.debug("Successfully validated ID token");

        return validatedToken;
    }

    /**
     * Verifies the signature of a token and maps its claims, without reaching any claim verdict.
     * <p>
     * Runs steps 1 to 6 of {@link #validate(IdTokenRequest)} — parse (including JWE decryption),
     * issuer extraction, issuer resolution, header, signature and claim mapping — with two
     * differences: the header check skips the issuer's {@code expectedTokenType}, and claims are
     * mapped without building an {@link IdTokenContent}. Step 7, claim validation, does not run, so
     * an expired token, a token for another audience or a token without {@code sub} is returned as
     * long as its signature is valid. No security event is counted for the success; a refusal
     * counts the event of the stage that refused it.
     *
     * @param tokenString the token string (guaranteed non-null, non-blank, within size limits)
     * @return the signature-verified token carrying the unvalidated claims and the verbatim
     *         {@code typ} header
     * @throws TokenValidationException if parsing, issuer resolution, the header check, the
     *                                  signature check or claim mapping fails
     */
    public SignatureVerifiedToken verifySignature(String tokenString) {
        LOGGER.debug("Verifying token signature without claim validation");

        VerifiedJwt verified = parseAndVerify(tokenString,
                (headerValidator, decodedJwt) -> headerValidator.validateWithoutTokenType(decodedJwt));
        DecodedJwt decodedJwt = verified.decodedJwt();

        Map<String, ClaimValue> claims = ValidatorLookup.getOrThrow(tokenBuilders,
                verified.issuerConfig().getIssuerIdentifier(), "token builder").mapClaims(decodedJwt);

        LOGGER.debug("Successfully verified token signature");
        return new SignatureVerifiedToken(claims, decodedJwt.header().getTyp().orElse(null), decodedJwt.rawToken());
    }

    /**
     * Runs the steps shared by {@link #validate(IdTokenRequest)} and {@link #verifySignature(String)}:
     * parse, issuer extraction, issuer resolution, header check and signature check.
     *
     * @param tokenString the token string
     * @param headerCheck the header check to apply with the issuer's header validator
     * @return the decoded token and the resolved issuer configuration
     * @throws TokenValidationException if any of the steps fails
     */
    private VerifiedJwt parseAndVerify(String tokenString, BiConsumer<TokenHeaderValidator, DecodedJwt> headerCheck) {
        // 1. Parse token
        DecodedJwt decodedJwt = jwtParser.decode(tokenString);

        // 2. Extract issuer
        String issuerString = decodedJwt.getIssuer().orElseThrow(() -> {
            LOGGER.warn(JWTValidationLogMessages.WARN.MISSING_CLAIM, "iss");
            securityEventCounter.increment(SecurityEventCounter.EventType.MISSING_CLAIM);
            return new TokenValidationException(
                    SecurityEventCounter.EventType.MISSING_CLAIM,
                    "Missing required issuer (iss) claim in token"
            );
        });

        // 3. Resolve issuer config
        IssuerConfig issuerConfig = issuerConfigCache.resolveConfig(issuerString);

        // 4. Validate header
        headerCheck.accept(
                ValidatorLookup.getOrThrow(headerValidators, issuerConfig.getIssuerIdentifier(), "header validator"),
                decodedJwt);

        // 5. Validate signature
        ValidatorLookup.getOrThrow(signatureValidators, issuerConfig.getIssuerIdentifier(), "signature validator")
                .validateSignature(decodedJwt);

        return new VerifiedJwt(decodedJwt, issuerConfig);
    }

    /**
     * A decoded token whose header and signature passed, with the issuer configuration it resolved to.
     *
     * @param decodedJwt   the decoded token
     * @param issuerConfig the resolved issuer configuration
     */
    private record VerifiedJwt(DecodedJwt decodedJwt, IssuerConfig issuerConfig) {
    }
}
