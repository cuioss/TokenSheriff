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
package de.cuioss.sheriff.token.validation.util;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Small utility wrapping {@link MessageDigest} SHA-256 digest computation.
 * <p>
 * SHA-256 is required to be present by the Java Security Standard Algorithm Names
 * specification, so the checked {@link NoSuchAlgorithmException} can only occur on a broken JRE.
 * It is translated to {@link TokenValidationException} rather than an unchecked exception because
 * every caller is on the per-request validation path (access-token cache keying, DPoP proof
 * thumbprint and {@code ath} hashing, ECDH-ES key derivation), where an undeclared unchecked failure
 * would escape {@code TokenValidator}'s documented contract.
 * <p>
 * This class is the module's <strong>single</strong> translation point for that condition: callers
 * that need an incremental digest take {@link #newDigest()} rather than calling
 * {@link MessageDigest#getInstance(String)} and repeating the translation.
 *
 * @since 1.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Sha256Util {

    private static final String SHA_256 = "SHA-256";

    /**
     * Computes the SHA-256 digest of the given input.
     *
     * @param input the bytes to digest, must not be {@code null}
     * @return the 32-byte SHA-256 digest
     * @throws TokenValidationException if the SHA-256 algorithm is not available (broken JRE)
     */
    public static byte[] digest(byte[] input) {
        return newDigest().digest(input);
    }

    /**
     * Returns a fresh SHA-256 {@link MessageDigest} for callers that digest incrementally.
     *
     * @return a new SHA-256 message digest
     * @throws TokenValidationException if the SHA-256 algorithm is not available (broken JRE)
     */
    public static MessageDigest newDigest() {
        return newDigest(SHA_256);
    }

    /**
     * The algorithm-parameterised seam behind {@link #newDigest()}.
     * <p>
     * It exists so the broken-JRE translation can be exercised by asking for an algorithm the JRE
     * really does not have. Without it the {@code catch} would be unreachable from any test — and an
     * untested translation on the request path is exactly the kind of guard this module cannot take
     * on trust, because it is the one that decides whether a refresh failure is declared or not.
     *
     * @param algorithm the digest algorithm to instantiate
     * @return a new message digest for {@code algorithm}
     * @throws TokenValidationException if the algorithm is not available
     */
    static MessageDigest newDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM,
                    "%s algorithm not available".formatted(algorithm), e);
        }
    }
}
