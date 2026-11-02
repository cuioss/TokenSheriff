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

import de.cuioss.sheriff.token.validation.util.EcdsaSignatureFormatConverter;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.util.Arrays;

/**
 * Shared utility for cryptographic signature verification, used by both
 * {@code TokenSignatureValidator} (JWT access/ID tokens) and {@code DpopProofValidator} (DPoP proofs).
 * <p>
 * Centralizes the ECDSA detection, the ECDSA component-range check and the
 * IEEE P1363 → ASN.1/DER format conversion logic so that both verification paths apply
 * exactly the same rules.
 * <p>
 * The component-range check (defense "H3") rejects an ECDSA signature whose {@code r} or
 * {@code s} component is not strictly between zero and the curve order {@code n}. This keeps
 * the "psychic signature" defense (CVE-2022-21449) in our own code instead of relying solely
 * on the JDK provider to reject a degenerate {@code (0, 0)} signature.
 * <p>
 * The utility is stateless: it neither logs nor counts. Callers translate the thrown
 * exceptions into their own security events.
 *
 * @since 1.0
 */
public final class SignatureVerificationUtil {

    private SignatureVerificationUtil() {
        // utility class
    }

    /**
     * Checks if the algorithm is an ECDSA algorithm that requires signature format conversion
     * from IEEE P1363 (used by JWS/JWT) to ASN.1/DER (used by JCA).
     *
     * @param algorithm the JWT signature algorithm (e.g., "ES256", "RS256")
     * @return true if the algorithm is ECDSA (ES256, ES384, ES512), false otherwise
     */
    public static boolean isEcdsaAlgorithm(String algorithm) {
        return "ES256".equals(algorithm) || "ES384".equals(algorithm) || "ES512".equals(algorithm);
    }

    /**
     * Verifies a signature using the provided public key and algorithm.
     * <p>
     * For ECDSA algorithms the raw JWS {@code r || s} signature is first range-checked
     * ({@code 0 < r,s < n}) and then converted from IEEE P1363 to ASN.1/DER. The range check
     * runs before the verifier is initialized with the key, so a malformed ECDSA signature
     * raises its {@link SignatureException} before any key-related check of the platform verifier.
     *
     * @param signatureTemplateManager the manager providing pre-configured Signature instances
     * @param publicKey                the public key for verification
     * @param algorithm                the JWT algorithm name (e.g., "ES256", "RS256")
     * @param dataBytes                the data that was signed (header.payload as UTF-8 bytes)
     * @param signatureBytes           the raw signature bytes (Base64url-decoded)
     * @return true if the signature is valid, false otherwise
     * @throws InvalidKeyException                                    if the key is invalid for this algorithm,
     *                                                                including a non-EC key for an ECDSA algorithm
     * @throws SignatureException                                     if the signature verification process fails,
     *                                                                or an ECDSA signature is null or malformed,
     *                                                                its key lacks domain parameters, or its
     *                                                                {@code r} or {@code s} component is out of range
     * @throws SignatureTemplateManager.UnsupportedAlgorithmException if the algorithm is not supported
     */
    public static boolean verifySignature(SignatureTemplateManager signatureTemplateManager,
            PublicKey publicKey, String algorithm, byte[] dataBytes, byte[] signatureBytes)
            throws InvalidKeyException, SignatureException {
        Signature verifier = signatureTemplateManager.getSignatureInstance(algorithm);
        boolean ecdsa = isEcdsaAlgorithm(algorithm);
        if (ecdsa) {
            checkEcdsaComponentRange(signatureBytes, publicKey);
        }
        verifier.initVerify(publicKey);
        verifier.update(dataBytes);

        // Convert range-checked ECDSA signatures from IEEE P1363 to ASN.1/DER format
        byte[] verificationSignature = signatureBytes;
        if (ecdsa) {
            verificationSignature = EcdsaSignatureFormatConverter.toJCACompatibleSignature(signatureBytes, algorithm);
        }

        return verifier.verify(verificationSignature);
    }

    /**
     * Rejects an ECDSA signature whose {@code r} or {@code s} component is out of range (H3).
     * <p>
     * A JWS ECDSA signature is the fixed-width concatenation {@code r || s} (RFC 7515). A valid
     * signature requires {@code 0 < r < n} and {@code 0 < s < n}, where {@code n} is the curve
     * order. The all-zero {@code (0, 0)} signature at the heart of the "psychic signature" attack
     * (CVE-2022-21449) violates this and is rejected here before it reaches the platform verifier.
     *
     * @param signatureBytes the raw JWS ECDSA signature ({@code r || s})
     * @param publicKey      the public key, which must be an EC key carrying the curve order {@code n}
     * @throws InvalidKeyException if the key is not an EC public key
     * @throws SignatureException  if the signature is null, empty or of odd length, the key lacks domain
     *                             parameters, or a component is zero, negative, or not less than {@code n}
     */
    private static void checkEcdsaComponentRange(byte[] signatureBytes, PublicKey publicKey)
            throws InvalidKeyException, SignatureException {
        if (signatureBytes == null) {
            throw new SignatureException("Signature cannot be null");
        }
        if (!(publicKey instanceof ECPublicKey ecPublicKey)) {
            throw new InvalidKeyException("ECDSA signature requires an EC public key");
        }
        if (signatureBytes.length == 0 || signatureBytes.length % 2 != 0) {
            throw new SignatureException("Malformed ECDSA signature: expected an even-length r||s encoding");
        }
        ECParameterSpec params = ecPublicKey.getParams();
        if (params == null) {
            throw new SignatureException("Invalid EC public key: missing domain parameters");
        }
        int componentLength = signatureBytes.length / 2;
        BigInteger r = new BigInteger(1, Arrays.copyOfRange(signatureBytes, 0, componentLength));
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(signatureBytes, componentLength, signatureBytes.length));
        BigInteger n = params.getOrder();
        if (r.signum() <= 0 || s.signum() <= 0 || r.compareTo(n) >= 0 || s.compareTo(n) >= 0) {
            throw new SignatureException("ECDSA signature component out of range: r and s must satisfy 0 < r,s < n");
        }
    }
}
