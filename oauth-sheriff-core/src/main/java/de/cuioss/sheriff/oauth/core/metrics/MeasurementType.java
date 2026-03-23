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
package de.cuioss.sheriff.oauth.core.metrics;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum defining all measurement types for JWT validation pipeline steps.
 * <p>
 * Each measurement type represents a specific phase of the JWT validation process
 * that can be independently monitored and analyzed for performance optimization.
 * <p>
 * The enum constants are ordered by their execution sequence in the validation pipeline (ordinals 0-14).
 * This natural ordering makes it easy to identify pipeline flow and bottlenecks by ordinal value.
 * <p>
 * <strong>Pipeline Execution Order (Access Token):</strong>
 * <ol>
 *   <li>COMPLETE_VALIDATION (0) - Wraps entire validation</li>
 *   <li>TOKEN_FORMAT_CHECK (1) - Pre-pipeline string validation</li>
 *   <li>TOKEN_PARSING (2) - Decode Base64URL and parse JSON</li>
 *   <li>ISSUER_EXTRACTION (3) - Extract issuer claim</li>
 *   <li>CACHE_LOOKUP (4) - Check cache before expensive operations</li>
 *   <li>ISSUER_CONFIG_RESOLUTION (5) - Resolve issuer configuration</li>
 *   <li>HEADER_VALIDATION (6) - Validate JWT header</li>
 *   <li>SIGNATURE_VALIDATION (7) - Cryptographic verification (most expensive)</li>
 *   <li>TOKEN_BUILDING (8) - Build typed token object</li>
 *   <li>CLAIMS_VALIDATION (9) - Validate token claims</li>
 *   <li>CACHE_STORE (10) - Store validated token in cache</li>
 * </ol>
 * <p>
 * <strong>Cross-Cutting Concerns:</strong>
 * <ul>
 *   <li>JWKS_OPERATIONS (11) - Key loading (happens within signature validation)</li>
 *   <li>RETRY_ATTEMPT (12) - Individual retry attempts</li>
 *   <li>RETRY_COMPLETE (13) - Complete retry cycle with delays</li>
 *   <li>RETRY_DELAY (14) - Exponential backoff delay timing</li>
 * </ul>
 * <p>
 * <strong>Pipeline Usage:</strong>
 * <ul>
 *   <li><strong>AccessTokenValidationPipeline</strong>: Uses ordinals 0-10 for complete instrumentation</li>
 *   <li><strong>IdTokenValidationPipeline</strong>: No metrics (uses NoOpMetricsTicker)</li>
 *   <li><strong>RefreshTokenValidationPipeline</strong>: No metrics (uses NoOpMetricsTicker)</li>
 * </ul>
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@RequiredArgsConstructor
@Getter
public enum MeasurementType {
    /**
     * 0. Complete token validation from start to finish.
     * <p>
     * Includes all pipeline steps, error handling, and represents the total
     * time taken for a JWT validation request from the perspective of the caller.
     * This is the most important metric for end-to-end performance analysis.
     */
    COMPLETE_VALIDATION("0. Complete JWT validation"),

    /**
     * 1. Token format validation (pre-pipeline).
     * <p>
     * Measures time to validate basic token format including empty/blank checks
     * and size limits before any parsing occurs. Performed by TokenStringValidator.
     * This is typically very fast but helps identify the overhead of initial validation checks.
     */
    TOKEN_FORMAT_CHECK("1. Token format validation"),

    /**
     * 2. JWT token parsing and structure validation.
     * <p>
     * Measures time to decode Base64URL segments, parse JSON structures,
     * and validate basic JWT format. This step typically has minimal overhead
     * but can indicate issues with malformed tokens or JSON parsing performance.
     */
    TOKEN_PARSING("2. JWT token parsing"),

    /**
     * 3. Issuer extraction from decoded JWT.
     * <p>
     * Measures time to extract and validate the presence of the issuer (iss) claim
     * from the decoded JWT. This includes claim lookup and validation that the
     * issuer is present.
     */
    ISSUER_EXTRACTION("3. Issuer extraction"),

    /**
     * 4. Cache lookup operation (access tokens only).
     * <p>
     * Measures time to look up a token in the cache, including key generation
     * and hash computation. This happens BEFORE expensive signature validation
     * to maximize cache performance (addresses issue #131).
     * Only used by AccessTokenValidationPipeline.
     */
    CACHE_LOOKUP("4. Cache lookup operation"),

    /**
     * 5. Issuer configuration resolution.
     * <p>
     * Measures time to look up the appropriate issuer configuration based on the
     * issuer claim. This includes configuration cache lookups and health checks.
     * High values may indicate configuration lookup inefficiencies or cache misses.
     */
    ISSUER_CONFIG_RESOLUTION("5. Issuer config resolution"),

    /**
     * 6. JWT header validation.
     * <p>
     * Measures time to validate header claims and structure, including
     * algorithm verification, key ID extraction, and header claim validation.
     * Performance issues here may indicate problems with header processing logic.
     */
    HEADER_VALIDATION("6. JWT header validation"),

    /**
     * 7. JWT signature verification (most expensive).
     * <p>
     * Measures time for cryptographic signature validation using RSA/ECDSA algorithms.
     * This is typically the most expensive operation in JWT validation, often consuming
     * 90%+ of total validation time. Performance optimization efforts should focus here.
     * <p>
     * This is why cache lookup happens at ordinal 4 (before this step) rather than after.
     */
    SIGNATURE_VALIDATION("7. JWT signature validation"),

    /**
     * 8. Token object building.
     * <p>
     * Measures time to construct the typed token objects (AccessTokenContent,
     * IdTokenContent) from the validated JWT claims. This includes claim extraction,
     * type conversion, and object instantiation.
     */
    TOKEN_BUILDING("8. Token building"),

    /**
     * 9. JWT claims validation.
     * <p>
     * Measures time to validate token claims including expiration (exp),
     * not-before (nbf), audience (aud), issuer (iss), and other standard/custom claims.
     * This step is typically fast but can indicate issues with claim processing logic.
     */
    CLAIMS_VALIDATION("9. JWT claims validation"),

    /**
     * 10. Cache store operation (access tokens only).
     * <p>
     * Measures time to store a validated token in the cache, including
     * serialization and LRU management. This metric helps identify caching overhead.
     * Only used by AccessTokenValidationPipeline.
     */
    CACHE_STORE("10. Cache store operation"),

    /**
     * 11. JWKS key retrieval and processing operations (cross-cutting).
     * <p>
     * Measures time to fetch JWKS keys from remote endpoints, parse key material,
     * and perform key caching operations. This happens within SIGNATURE_VALIDATION
     * when keys need to be loaded. High values here may indicate network
     * latency issues, JWKS endpoint problems, or key processing inefficiencies.
     */
    JWKS_OPERATIONS("11. JWKS key operations"),

    /**
     * 12. HTTP retry operation - single attempt (cross-cutting).
     * <p>
     * Measures time for individual retry attempts within the retry strategy.
     * This includes the actual operation execution time but excludes retry delays.
     * Useful for analyzing per-attempt performance patterns.
     */
    RETRY_ATTEMPT("12. HTTP retry single attempt"),

    /**
     * 13. HTTP retry operation - complete with all attempts (cross-cutting).
     * <p>
     * Measures total time for retry operations including all attempts, delays,
     * and eventual success or failure. This represents the complete retry cycle
     * from the caller's perspective and includes exponential backoff delays.
     */
    RETRY_COMPLETE("13. HTTP retry complete operation"),

    /**
     * 14. HTTP retry delay time (cross-cutting).
     * <p>
     * Measures actual delay time between retry attempts as calculated by the
     * exponential backoff algorithm including jitter. This helps analyze
     * retry timing patterns and backoff effectiveness.
     */
    RETRY_DELAY("14. HTTP retry delay time"),

    /**
     * 15. JWE decryption operation (cross-cutting).
     * <p>
     * Measures time to decrypt a JWE token including key resolution,
     * CEK decryption, content decryption, and optional decompression.
     * This step occurs within TOKEN_PARSING when a 5-part JWE token is detected.
     */
    JWE_DECRYPTION("15. JWE decryption");

    /**
     * Human-readable description of this measurement type for logging and monitoring.
     * Includes ordinal number prefix to indicate pipeline execution order.
     */
    private final String description;
}
