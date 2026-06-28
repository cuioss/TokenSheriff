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
/**
 * Provides security-related functionality for JWT Token handling.
 * <p>
 * This package contains classes that implement security best practices for JWT Token
 * processing, including algorithm preferences, secure SSL context provision, and security
 * event monitoring.
 * <p>
 * Key components:
 * <ul>
 *   <li>{@link de.cuioss.sheriff.oauth.core.security.SignatureAlgorithmPreferences} - Manages algorithm preferences for JWT signature validation</li>
 *   <li>{@link de.cuioss.sheriff.oauth.core.security.JwkAlgorithmPreferences} - Manages algorithm preferences for JWK parsing</li>
 *   <li>{@link de.cuioss.sheriff.oauth.core.security.SecurityEventCounter} - Tracks security-relevant events for monitoring</li>
 * </ul>
 * <p>
 * The classes in this package implement security best practices, including:
 * <ul>
 *   <li>Cryptographic agility - supporting multiple algorithms with preference ordering</li>
 *   <li>Secure defaults - using strong algorithms by default</li>
 *   <li>Explicit rejection of insecure algorithms</li>
 *   <li>Security event monitoring and metrics</li>
 * </ul>
 * <p>
 * These security features are used throughout the JWT Token handling framework to ensure
 * secure token validation and JWKS retrieval.
 * <p>
 * This package implements the following requirements:
 * <ul>
 *   <li><a href="../../../../../../../../../../doc/Requirements.adoc#VALIDATION-8.1">VALIDATION-8.1: Token Size Limits</a></li>
 *   <li><a href="../../../../../../../../../../doc/Requirements.adoc#VALIDATION-8.2">VALIDATION-8.2: Safe Parsing</a></li>
 *   <li><a href="../../../../../../../../../../doc/Requirements.adoc#VALIDATION-8.3">VALIDATION-8.3: Secure Communication</a></li>
 *   <li><a href="../../../../../../../../../../doc/Requirements.adoc#VALIDATION-8.4">VALIDATION-8.4: Claims Validation</a></li>
 *   <li><a href="../../../../../../../../../../doc/Requirements.adoc#VALIDATION-8.5">VALIDATION-8.5: Cryptographic Agility</a></li>
 * </ul>
 * <p>
 * For more details on the security aspects, see the
 * <a href="https://github.com/cuioss/OAuthSheriff/tree/main/doc/security/security-reference.adoc">Security Specification</a>
 *
 * @author Oliver Wolff
 * @see de.cuioss.sheriff.oauth.core.pipeline.validator.TokenSignatureValidator
 * @see de.cuioss.sheriff.oauth.core.jwks.http.HttpJwksLoader
 */
package de.cuioss.sheriff.oauth.core.security;
