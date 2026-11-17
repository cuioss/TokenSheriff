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
package de.cuioss.sheriff.token.client.auth;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The key types and minimum key strength {@link PemKeyPairLoader} admits.
 * <p>
 * The caller decides which key types its use admits — for example a caller that hands the pair to
 * {@link PrivateKeyJwtAuth}, which does not offer {@code EdDSA}, narrows the policy to
 * {@link KeyType#RSA} and {@link KeyType#EC_P256}. {@link #defaults()} returns the TokenSheriff
 * defaults: every supported key type, with an RSA modulus of at least
 * {@value #MINIMUM_RSA_MODULUS_BITS} bits.
 * <p>
 * Instances are immutable and safe for concurrent use.
 *
 * @param allowedKeyTypes       the admitted key types; must not be {@code null}, empty or contain
 *                              {@code null}
 * @param minimumRsaModulusBits the minimum RSA modulus length in bits; at least
 *                              {@value #MINIMUM_RSA_MODULUS_BITS}
 * @since 1.0
 * @author Oliver Wolff
 */
public record KeyPairPolicy(Set<KeyType> allowedKeyTypes, int minimumRsaModulusBits) {

    /** The lowest RSA modulus length in bits a policy may admit. */
    public static final int MINIMUM_RSA_MODULUS_BITS = 2048;

    /** Refusal text for a policy that admits no key type. */
    static final String NO_KEY_TYPES = "key pair policy must admit at least one key type";

    /** Refusal text for a policy whose RSA minimum is below {@value #MINIMUM_RSA_MODULUS_BITS} bits. */
    static final String RSA_MINIMUM_TOO_LOW = "key pair policy RSA modulus minimum must be at least 2048 bits";

    /**
     * The key types a {@link KeyPairPolicy} can admit.
     */
    public enum KeyType {
        /** An RSA key pair, subject to {@link KeyPairPolicy#minimumRsaModulusBits()}. */
        RSA,
        /** An EC key pair on curve P-256 (secp256r1). */
        EC_P256,
        /** An EdDSA key pair on curve Ed25519. */
        ED25519
    }

    /**
     * Validates the policy and copies the admitted key types immutably.
     *
     * @throws IllegalArgumentException if no key type is admitted or the RSA minimum is below
     *         {@value #MINIMUM_RSA_MODULUS_BITS} bits
     */
    public KeyPairPolicy {
        Objects.requireNonNull(allowedKeyTypes, "allowedKeyTypes must not be null");
        if (allowedKeyTypes.isEmpty()) {
            throw new IllegalArgumentException(NO_KEY_TYPES);
        }
        if (minimumRsaModulusBits < MINIMUM_RSA_MODULUS_BITS) {
            throw new IllegalArgumentException(RSA_MINIMUM_TOO_LOW);
        }
        allowedKeyTypes = Set.copyOf(allowedKeyTypes);
    }

    /**
     * @return the TokenSheriff defaults: RSA, EC P-256 and Ed25519 admitted, RSA modulus of at least
     *         {@value #MINIMUM_RSA_MODULUS_BITS} bits
     */
    public static KeyPairPolicy defaults() {
        return new KeyPairPolicy(EnumSet.allOf(KeyType.class), MINIMUM_RSA_MODULUS_BITS);
    }

    /**
     * @param keyType the key type to check
     * @return {@code true} if the policy admits the key type
     */
    public boolean allows(KeyType keyType) {
        return allowedKeyTypes.contains(keyType);
    }
}
