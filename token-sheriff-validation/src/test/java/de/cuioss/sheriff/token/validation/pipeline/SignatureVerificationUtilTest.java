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

import de.cuioss.sheriff.token.validation.security.SignatureAlgorithmPreferences;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler.Algorithm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SignatureVerificationUtil}, in particular the ECDSA component-range check (H3)
 * that rejects an {@code r} or {@code s} outside {@code (0, n)} before the platform verifier runs.
 */
@DisplayName("SignatureVerificationUtil")
class SignatureVerificationUtilTest {

    private static final String RANGE_MESSAGE = "ECDSA signature component out of range";
    private static final byte[] DATA = "header.payload".getBytes(StandardCharsets.UTF_8);

    private final SignatureTemplateManager templateManager =
            new SignatureTemplateManager(new SignatureAlgorithmPreferences());

    @Nested
    @DisplayName("ECDSA component-range check")
    class EcdsaRangeCheck {

        @ParameterizedTest(name = "{0}: all-zero r || s is rejected")
        @EnumSource(value = Algorithm.class, names = {"ES256", "ES384", "ES512"})
        @DisplayName("Should reject an all-zero ECDSA signature with SignatureException")
        void shouldRejectAllZeroSignature(Algorithm algorithm) {
            PublicKey publicKey = InMemoryKeyMaterialHandler.getDefaultPublicKey(algorithm);
            byte[] zeroSignature = new byte[2 * componentLength(algorithm)];

            var ex = assertThrows(SignatureException.class, () -> verify(publicKey, algorithm, zeroSignature));
            assertTrue(ex.getMessage().contains(RANGE_MESSAGE), "Message must name the range check: " + ex.getMessage());
        }

        @ParameterizedTest(name = "{0}: r == n is rejected")
        @EnumSource(value = Algorithm.class, names = {"ES256", "ES384", "ES512"})
        @DisplayName("Should reject a signature whose r component equals the curve order")
        void shouldRejectREqualToOrder(Algorithm algorithm) {
            ECPublicKey publicKey = (ECPublicKey) InMemoryKeyMaterialHandler.getDefaultPublicKey(algorithm);
            BigInteger n = publicKey.getParams().getOrder();
            byte[] signature = concat(algorithm, n, BigInteger.ONE);

            var ex = assertThrows(SignatureException.class, () -> verify(publicKey, algorithm, signature));
            assertTrue(ex.getMessage().contains(RANGE_MESSAGE), "Message must name the range check: " + ex.getMessage());
        }

        @ParameterizedTest(name = "{0}: s == n is rejected")
        @EnumSource(value = Algorithm.class, names = {"ES256", "ES384", "ES512"})
        @DisplayName("Should reject a signature whose s component equals the curve order")
        void shouldRejectSEqualToOrder(Algorithm algorithm) {
            ECPublicKey publicKey = (ECPublicKey) InMemoryKeyMaterialHandler.getDefaultPublicKey(algorithm);
            BigInteger n = publicKey.getParams().getOrder();
            byte[] signature = concat(algorithm, BigInteger.ONE, n);

            var ex = assertThrows(SignatureException.class, () -> verify(publicKey, algorithm, signature));
            assertTrue(ex.getMessage().contains(RANGE_MESSAGE), "Message must name the range check: " + ex.getMessage());
        }

        @Test
        @DisplayName("Should reject an odd-length ECDSA signature")
        void shouldRejectOddLengthSignature() {
            PublicKey publicKey = InMemoryKeyMaterialHandler.getDefaultPublicKey(Algorithm.ES256);
            byte[] signature = new byte[63];
            signature[0] = 1;

            var ex = assertThrows(SignatureException.class, () -> verify(publicKey, Algorithm.ES256, signature));
            assertTrue(ex.getMessage().contains("even-length"), "Message must name the malformed encoding: " + ex.getMessage());
        }

        @Test
        @DisplayName("Should reject an empty ECDSA signature")
        void shouldRejectEmptySignature() {
            PublicKey publicKey = InMemoryKeyMaterialHandler.getDefaultPublicKey(Algorithm.ES256);
            byte[] signature = new byte[0];

            var ex = assertThrows(SignatureException.class, () -> verify(publicKey, Algorithm.ES256, signature));
            assertTrue(ex.getMessage().contains("even-length"), "Message must name the malformed encoding: " + ex.getMessage());
        }

        @Test
        @DisplayName("Should reject a null ECDSA signature with SignatureException")
        void shouldRejectNullSignature() {
            PublicKey publicKey = InMemoryKeyMaterialHandler.getDefaultPublicKey(Algorithm.ES256);

            var ex = assertThrows(SignatureException.class, () -> verify(publicKey, Algorithm.ES256, null));
            assertEquals("Signature cannot be null", ex.getMessage(), "Message must name the null signature");
        }

        @Test
        @DisplayName("Should reject a non-EC key for an ECDSA algorithm with InvalidKeyException")
        void shouldRejectNonEcKey() {
            PublicKey rsaKey = InMemoryKeyMaterialHandler.getDefaultPublicKey(Algorithm.RS256);
            byte[] signature = new byte[64];

            assertThrows(InvalidKeyException.class, () -> verify(rsaKey, Algorithm.ES256, signature));
        }

        @ParameterizedTest(name = "{0}: genuine signature verifies")
        @EnumSource(value = Algorithm.class, names = {"ES256", "ES384", "ES512"})
        @DisplayName("Should verify a genuine ECDSA signature as valid")
        void shouldVerifyGenuineSignature(Algorithm algorithm) throws Exception {
            PublicKey publicKey = InMemoryKeyMaterialHandler.getDefaultPublicKey(algorithm);
            byte[] signature = sign(InMemoryKeyMaterialHandler.getDefaultPrivateKey(algorithm), p1363JcaName(algorithm));

            assertTrue(verify(publicKey, algorithm, signature), "A genuine " + algorithm + " signature must verify");
        }
    }

    @Nested
    @DisplayName("Non-ECDSA algorithms")
    class NonEcdsa {

        @Test
        @DisplayName("Should not range-check an RS256 signature")
        void shouldNotRangeCheckRsaSignature() throws Exception {
            RSAPublicKey publicKey = (RSAPublicKey) InMemoryKeyMaterialHandler.getDefaultPublicKey(Algorithm.RS256);
            byte[] zeroSignature = new byte[(publicKey.getModulus().bitLength() + 7) / 8];

            assertFalse(verify(publicKey, Algorithm.RS256, zeroSignature),
                    "An all-zero RS256 signature is rejected by the verifier, not by the ECDSA range check");
        }

        @Test
        @DisplayName("Should verify a genuine RS256 signature as valid")
        void shouldVerifyGenuineRsaSignature() throws Exception {
            PublicKey publicKey = InMemoryKeyMaterialHandler.getDefaultPublicKey(Algorithm.RS256);
            byte[] signature = sign(InMemoryKeyMaterialHandler.getDefaultPrivateKey(Algorithm.RS256), "SHA256withRSA");

            assertTrue(verify(publicKey, Algorithm.RS256, signature), "A genuine RS256 signature must verify");
        }
    }

    private boolean verify(PublicKey publicKey, Algorithm algorithm, byte[] signature)
            throws InvalidKeyException, SignatureException {
        return SignatureVerificationUtil.verifySignature(templateManager, publicKey, algorithm.name(), DATA, signature);
    }

    private static byte[] sign(PrivateKey privateKey, String jcaName) throws GeneralSecurityException {
        Signature signer = Signature.getInstance(jcaName);
        signer.initSign(privateKey);
        signer.update(DATA);
        return signer.sign();
    }

    private static String p1363JcaName(Algorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> "SHA256withECDSAinP1363Format";
            case ES384 -> "SHA384withECDSAinP1363Format";
            case ES512 -> "SHA512withECDSAinP1363Format";
            default -> throw new IllegalArgumentException("Not an ECDSA algorithm: " + algorithm);
        };
    }

    private static int componentLength(Algorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> 32;
            case ES384 -> 48;
            case ES512 -> 66;
            default -> throw new IllegalArgumentException("Not an ECDSA algorithm: " + algorithm);
        };
    }

    private static byte[] concat(Algorithm algorithm, BigInteger r, BigInteger s) {
        int length = componentLength(algorithm);
        byte[] result = new byte[2 * length];
        copyUnsigned(r, result, 0, length);
        copyUnsigned(s, result, length, length);
        return result;
    }

    private static void copyUnsigned(BigInteger value, byte[] target, int offset, int length) {
        byte[] bytes = value.toByteArray();
        int start = bytes.length > length ? bytes.length - length : 0;
        int copyLength = bytes.length - start;
        System.arraycopy(bytes, start, target, offset + length - copyLength, copyLength);
    }
}
