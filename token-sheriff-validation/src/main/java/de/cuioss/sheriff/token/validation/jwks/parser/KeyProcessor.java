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
package de.cuioss.sheriff.token.validation.jwks.parser;

import de.cuioss.sheriff.token.commons.error.ErrorTextSanitizer;
import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.events.SecurityEventCounter.EventType;
import de.cuioss.sheriff.token.commons.transport.JwkKey;
import de.cuioss.sheriff.token.validation.JWTValidationLogMessages.WARN;
import de.cuioss.sheriff.token.validation.jwks.key.JwkKeyHandler;
import de.cuioss.sheriff.token.validation.jwks.key.KeyInfo;
import de.cuioss.sheriff.token.validation.security.JwkAlgorithmPreferences;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;
import java.util.Optional;

/**
 * Processes individual JWK objects and creates KeyInfo instances.
 * This class is responsible for:
 * <ul>
 *   <li>Processing RSA keys</li>
 *   <li>Processing EC keys</li>
 *   <li>Determining appropriate algorithms</li>
 *   <li>Error handling and logging</li>
 * </ul>
 * @since 1.0
 */
public class KeyProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(KeyProcessor.class);
    private static final String RSA_KEY_TYPE = "RSA";
    private static final String EC_KEY_TYPE = "EC";
    private static final String OKP_KEY_TYPE = "OKP";

    /** Upper bound for the length of a remote JWK value rendered into a log line. */
    private static final int MAX_LOGGED_VALUE_LENGTH = 64;


    private final SecurityEventCounter securityEventCounter;


    private final JwkAlgorithmPreferences jwkAlgorithmPreferences;

    public KeyProcessor(SecurityEventCounter securityEventCounter,
            JwkAlgorithmPreferences jwkAlgorithmPreferences) {
        this.securityEventCounter = securityEventCounter;
        this.jwkAlgorithmPreferences = jwkAlgorithmPreferences;
    }

    /**
     * Process a JWK object and create a KeyInfo with validation.
     *
     * @param jwk the JWK object to process
     * @return an Optional containing the KeyInfo if processing succeeded, empty otherwise
     */
    public Optional<KeyInfo> processKey(JwkKey jwk) {
        // Validate key parameters first
        if (!validateKeyParameters(jwk)) {
            return Optional.empty();
        }

        String kty = jwk.kty();

        if (jwk.getKid().isEmpty()) {
            LOGGER.warn(WARN.JWK_MISSING_KID);
            return Optional.empty();
        }
        String kid = jwk.kid();

        KeyInfo keyInfo = switch (kty) {
            case RSA_KEY_TYPE -> processRsaKey(jwk, kid);
            case EC_KEY_TYPE -> processEcKey(jwk, kid);
            case OKP_KEY_TYPE -> processOkpKey(jwk, kid);
            default -> {
                LOGGER.debug("Unsupported key type: %s for key ID: %s", sanitize(kty), sanitize(kid));
                yield null;
            }
        };

        return Optional.ofNullable(keyInfo);
    }

    /**
     * Neutralises a remote JWK value, or a parser message quoting one, before it is rendered into a
     * log line (CWE-117, ADR-0022): every control character and Unicode line separator becomes
     * {@code '?'} and the value is cut to {@value #MAX_LOGGED_VALUE_LENGTH} characters, the same form
     * {@code JwkKey} and {@code JweDecryptor} use.
     *
     * @param value the remote value, may be {@code null}
     * @return the sanitised value, or {@code "<none>"} for {@code null}
     */
    private static String sanitize(@Nullable String value) {
        if (value == null) {
            return "<none>";
        }
        return ErrorTextSanitizer.sanitize(value, '?', MAX_LOGGED_VALUE_LENGTH, "...");
    }

    /**
     * Validates individual key parameters and algorithms.
     *
     * @param jwkKey the individual key object to validate
     * @return true if the key is valid, false otherwise
     */
    private boolean validateKeyParameters(JwkKey jwkKey) {
        // Validate required key type
        if (jwkKey.kty() == null || jwkKey.kty().trim().isEmpty()) {
            LOGGER.warn(WARN.JWK_MISSING_KTY);
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return false;
        }

        String keyType = jwkKey.kty();

        // Validate key type is supported
        if (!"RSA".equals(keyType) && !"EC".equals(keyType) && !"OKP".equals(keyType)) {
            LOGGER.warn(WARN.JWK_UNSUPPORTED_KEY_TYPE, sanitize(keyType));
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return false;
        }

        // Validate key ID if present (length check)
        if (jwkKey.getKid().isPresent()) {
            String keyId = jwkKey.kid();
            if (keyId.length() > 100) {
                LOGGER.warn(WARN.JWK_KEY_ID_TOO_LONG, keyId.length());
                securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
                return false;
            }
        }

        // Validate algorithm if present
        if (jwkKey.alg() != null) {
            String algorithm = jwkKey.alg();
            if (!jwkAlgorithmPreferences.isSupported(algorithm)) {
                LOGGER.warn(WARN.JWK_INVALID_ALGORITHM, sanitize(algorithm));
                securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
                return false;
            }
        }

        return true;
    }

    /**
     * Process an RSA key and create a KeyInfo object.
     *
     * @param jwk the JWK object
     * @param kid the key ID
     * @return the KeyInfo object or null if processing failed
     */
    private KeyInfo processRsaKey(JwkKey jwk, String kid) {
        // Reject sub-2048-bit RSA keys before parsing (M1), with a clear, typed rejection event.
        Optional<BigInteger> modulus = jwk.getModulusAsBigInteger();
        if (modulus.isPresent() && modulus.get().bitLength() < JwkKeyHandler.MIN_RSA_KEY_SIZE_BITS) {
            LOGGER.warn(WARN.RSA_KEY_PARSE_FAILED, sanitize(kid),
                    "RSA key modulus is %d bits; minimum accepted is %d bits".formatted(
                            modulus.get().bitLength(), JwkKeyHandler.MIN_RSA_KEY_SIZE_BITS));
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return null;
        }
        try {
            var publicKey = JwkKeyHandler.parseRsaKey(jwk);
            // Determine algorithm if not specified
            String alg = jwk.alg();
            if (alg == null) {
                alg = "RS256";
                LOGGER.warn(WARN.JWK_RSA_DEFAULTING_TO_RS256, sanitize(kid));
            }
            LOGGER.debug("Parsed RSA key with ID: %s and algorithm: %s", sanitize(kid), sanitize(alg));
            return new KeyInfo(publicKey, alg, kid);
        } catch (InvalidKeySpecException | IllegalStateException e) {
            // The parser's message can quote the rejected key material, so it is sanitised and the
            // exception object, whose stack trace would print it raw, is not attached (CWE-117)
            LOGGER.warn(WARN.RSA_KEY_PARSE_FAILED, sanitize(kid), sanitize(e.getMessage()));
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return null;
        }
    }

    /**
     * Process an EC key and create a KeyInfo object.
     * <p>
     * The curve binds an EC key to exactly one ES algorithm (P-256 → ES256, P-384 → ES384,
     * P-521 → ES512). A JWK whose {@code alg} disagrees with that curve-derived algorithm is
     * dropped rather than installed under a label it cannot satisfy; an absent {@code alg} is
     * derived from the curve.
     *
     * @param jwk the JWK object
     * @param kid the key ID
     * @return the KeyInfo object or null if processing failed
     */
    private KeyInfo processEcKey(JwkKey jwk, String kid) {
        try {
            var publicKey = JwkKeyHandler.parseEcKey(jwk);
            // parseEcKey has validated the curve, so the curve-derived algorithm is defined
            String curveAlgorithm = JwkKeyHandler.determineEcAlgorithm(jwk.crv());
            String alg = jwk.getAlg().orElse(curveAlgorithm);
            if (!curveAlgorithm.equals(alg)) {
                LOGGER.warn(WARN.EC_KEY_PARSE_FAILED, sanitize(kid),
                        "alg '%s' does not match curve '%s', which requires '%s'".formatted(
                                sanitize(alg), sanitize(jwk.crv()), curveAlgorithm));
                securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
                return null;
            }
            LOGGER.debug("Parsed EC key with ID: %s and algorithm: %s", sanitize(kid), sanitize(alg));
            return new KeyInfo(publicKey, alg, kid);
        } catch (InvalidKeySpecException | IllegalStateException e) {
            // Sanitised message, no exception object: see processRsaKey
            LOGGER.warn(WARN.EC_KEY_PARSE_FAILED, sanitize(kid), sanitize(e.getMessage()));
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return null;
        }
    }

    /**
     * Process an OKP (Octet Key Pair) key and create a KeyInfo object.
     * OKP keys are used for EdDSA signatures (Ed25519/Ed448) as defined in RFC 8037.
     *
     * @param jwk the JWK object
     * @param kid the key ID
     * @return the KeyInfo object or null if processing failed
     */
    private KeyInfo processOkpKey(JwkKey jwk, String kid) {
        try {
            var publicKey = JwkKeyHandler.parseOkpKey(jwk);
            // EdDSA always uses the single algorithm identifier "EdDSA"
            String alg = jwk.alg() != null ? jwk.alg() : JwkKeyHandler.determineOkpAlgorithm();
            LOGGER.debug("Parsed OKP key with ID: %s and algorithm: %s", sanitize(kid), sanitize(alg));
            return new KeyInfo(publicKey, alg, kid);
        } catch (InvalidKeySpecException | IllegalStateException e) {
            // Sanitised message, no exception object: see processRsaKey
            LOGGER.warn(WARN.OKP_KEY_PARSE_FAILED, sanitize(kid), sanitize(e.getMessage()));
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return null;
        }
    }
}