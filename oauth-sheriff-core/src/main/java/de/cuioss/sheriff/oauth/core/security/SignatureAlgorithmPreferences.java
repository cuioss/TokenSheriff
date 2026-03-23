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

import de.cuioss.sheriff.oauth.core.JWTValidationLogMessages;
import de.cuioss.tools.logging.CuiLogger;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for managing algorithm preferences for JWT signature validation.
 * <p>
 * This class provides methods to get preferred algorithms and check if an algorithm is supported
 * during JWT signature verification at runtime. It implements cryptographic agility by allowing 
 * configuration of preferred algorithms and supporting algorithm migration.
 * <p>
 * For more details on the security aspects, see the
 * <a href="https://github.com/cuioss/OAuthSheriff/tree/main/doc/security/security-reference.adoc">Security Specification</a>
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public class SignatureAlgorithmPreferences {

    private static final CuiLogger LOGGER = new CuiLogger(SignatureAlgorithmPreferences.class);

    /**
     * List of supported signature algorithms in order of preference (most preferred first).
     */
    @Getter
    private final List<String> preferredAlgorithms;

    /**
     * List of explicitly rejected algorithms for security reasons.
     */
    private static final List<String> REJECTED_ALGORITHMS = List.of("HS256", "HS384", "HS512", "none");

    /**
     * Default constructor that initializes the preferred algorithms list with default values.
     */
    public SignatureAlgorithmPreferences() {
        this.preferredAlgorithms = getDefaultPreferredAlgorithms();
    }

    /**
     * Constructor that allows specifying custom preferred algorithms.
     *
     * @param preferredAlgorithms the list of preferred algorithms in order of preference
     */
    public SignatureAlgorithmPreferences(List<String> preferredAlgorithms) {
        this.preferredAlgorithms = Collections.unmodifiableList(preferredAlgorithms);
    }

    /**
     * Gets the default list of preferred signature algorithms in order of preference.
     *
     * @return the default list of preferred signature algorithms
     */
    public static List<String> getDefaultPreferredAlgorithms() {
        LOGGER.debug("Getting default preferred signature algorithms");

        // Order algorithms by preference (most secure first)
        return List.of("ES512", "ES384", "ES256", "EdDSA", "PS512", "PS384", "PS256", "RS512", "RS384", "RS256");
    }

    /**
     * Checks if an algorithm is supported for JWT signature verification.
     *
     * @param algorithm the algorithm to check
     * @return true if the algorithm is supported, false otherwise
     */
    public boolean isSupported(String algorithm) {
        if (algorithm == null || algorithm.isEmpty()) {
            return false;
        }

        // Check if the algorithm is explicitly rejected
        if (REJECTED_ALGORITHMS.contains(algorithm)) {
            LOGGER.warn(JWTValidationLogMessages.WARN.ALGORITHM_REJECTED, algorithm);
            return false;
        }

        // Check if the algorithm is in the preferred list
        return preferredAlgorithms.contains(algorithm);
    }

    /**
     * Gets the most preferred algorithm from a list of available algorithms.
     *
     * @param availableAlgorithms the list of available algorithms
     * @return an Optional containing the most preferred algorithm if available, empty otherwise
     */
    public Optional<String> getMostPreferredAlgorithm(List<String> availableAlgorithms) {
        if (availableAlgorithms == null || availableAlgorithms.isEmpty()) {
            return Optional.empty();
        }

        // Find the first algorithm in the preferred list that is also in the available list
        return preferredAlgorithms.stream()
                .filter(availableAlgorithms::contains)
                .findFirst();
    }
}