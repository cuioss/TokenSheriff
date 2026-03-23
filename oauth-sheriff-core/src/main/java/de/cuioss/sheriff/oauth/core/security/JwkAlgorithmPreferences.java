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
package de.cuioss.sheriff.oauth.core.security;

import de.cuioss.tools.logging.CuiLogger;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Utility class for managing algorithm preferences for JWK (JSON Web Key) parsing.
 * <p>
 * This class validates the 'alg' field in JWK objects during JWKS parsing to ensure
 * only keys with known/supported algorithms are loaded into the key store.
 * This is structural validation, not runtime signature verification preferences.
 * <p>
 * For more details on the security aspects, see the
 * <a href="https://github.com/cuioss/OAuthSheriff/tree/main/doc/security/security-reference.adoc">Security Specification</a>
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public class JwkAlgorithmPreferences {

    private static final CuiLogger LOGGER = new CuiLogger(JwkAlgorithmPreferences.class);

    /**
     * List of supported JWK algorithms for parsing.
     */
    @Getter
    private final List<String> supportedAlgorithms;

    /**
     * Default constructor that initializes the supported algorithms list with default values.
     */
    public JwkAlgorithmPreferences() {
        this.supportedAlgorithms = getDefaultSupportedAlgorithms();
    }

    /**
     * Constructor that allows specifying custom supported algorithms.
     *
     * @param supportedAlgorithms the list of supported algorithms for JWK parsing
     */
    public JwkAlgorithmPreferences(List<String> supportedAlgorithms) {
        this.supportedAlgorithms = Collections.unmodifiableList(supportedAlgorithms);
    }

    /**
     * Gets the default list of supported algorithms for JWK parsing.
     *
     * @return the default list of supported JWK algorithms
     */
    public static List<String> getDefaultSupportedAlgorithms() {
        LOGGER.debug("Getting default supported JWK algorithms");

        // Algorithms that can be parsed and loaded from JWK objects
        // Includes both signature algorithms and JWE key management algorithms
        return List.of("RS256", "RS384", "RS512", "ES256", "ES384", "ES512", "EdDSA", "PS256", "PS384", "PS512",
                "RSA-OAEP", "RSA-OAEP-256", "ECDH-ES");
    }

    /**
     * Checks if an algorithm is supported for JWK parsing.
     *
     * @param algorithm the algorithm to check
     * @return true if the algorithm is supported for JWK parsing, false otherwise
     */
    public boolean isSupported(String algorithm) {
        if (algorithm == null || algorithm.isEmpty()) {
            return false;
        }

        return supportedAlgorithms.contains(algorithm);
    }
}