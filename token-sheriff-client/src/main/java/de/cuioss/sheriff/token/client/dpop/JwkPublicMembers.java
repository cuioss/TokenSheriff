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
package de.cuioss.sheriff.token.client.dpop;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EdECPoint;
import java.security.spec.EllipticCurve;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the public JSON Web Key members of a public key.
 * <p>
 * {@link #of(PublicKey)} returns exactly the members RFC 7638 hashes for a JWK thumbprint, so the
 * same map can be serialized into a JWK and hashed into a {@code jkt} without the two ever
 * disagreeing. The supported key types and their member sets are:
 * <table border="1">
 *   <caption>Supported public keys and their JWK members</caption>
 *   <tr><th>Public key</th><th>Members</th><th>Encoding</th></tr>
 *   <tr><td>RSA</td><td>{@code kty}, {@code e}, {@code n}</td>
 *       <td>unsigned big-endian base64url, no leading zero byte (RFC 7518 §6.3.1)</td></tr>
 *   <tr><td>EC, curve P-256</td><td>{@code kty}, {@code crv}, {@code x}, {@code y}</td>
 *       <td>big-endian base64url left-padded to 32 bytes (RFC 7518 §6.2.1)</td></tr>
 *   <tr><td>OKP, curve Ed25519</td><td>{@code kty}, {@code crv}, {@code x}</td>
 *       <td>the RFC 8032 32-byte public-key encoding, base64url (RFC 8037 §2)</td></tr>
 * </table>
 * <p>
 * An EC key is admitted only when its domain parameters are those of P-256 in full and its point
 * lies on that curve. A refused key raises an {@link IllegalArgumentException} with a fixed message
 * that carries no key material. The class is stateless and safe for concurrent use.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7517">RFC 7517 - JSON Web Key (JWK)</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7518">RFC 7518 - JSON Web Algorithms (JWA)</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8037">RFC 8037 - CFRG Elliptic Curve Keys in JOSE</a>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JwkPublicMembers {

    /** Refusal text for an EC key whose domain parameters are not curve P-256's. */
    static final String CURVE_NOT_P256 = "EC public key must use curve P-256 (secp256r1)";

    /** Refusal text for an EC key whose point does not lie on curve P-256. */
    static final String POINT_NOT_ON_CURVE = "EC public key point is not on curve P-256";

    /** Refusal text for an OKP key on a curve other than Ed25519. */
    static final String OKP_CURVE_NOT_ED25519 = "OKP public key must use curve Ed25519";

    /** Refusal text for a key that is neither RSA, EC nor OKP. */
    static final String UNSUPPORTED_KEY_TYPE = "public key must be an RSA, EC P-256 or OKP Ed25519 key";

    /** Refusal text for a fixed-width member whose value exceeds its width. */
    static final String MEMBER_TOO_WIDE = "JWK member exceeds its fixed width";

    private static final String KTY_RSA = "RSA";
    private static final String KTY_EC = "EC";
    private static final String KTY_OKP = "OKP";
    private static final String CRV_P256 = "P-256";
    private static final String CRV_ED25519 = "Ed25519";

    /** JCA standard name of curve P-256. */
    private static final String CURVE_SECP256R1 = "secp256r1";

    /**
     * Curve P-256's domain parameters, resolved once from the JCA provider. Every EC key is compared
     * against these in full — a field-size check alone would admit any other 256-bit curve
     * (secp256k1, brainpoolP256r1), whose key would then be published claiming {@code "crv":"P-256"}:
     * a curve-confusion defect.
     */
    private static final ECParameterSpec P256_PARAMS = resolveP256Params();

    /** Fixed width of a P-256 affine coordinate per RFC 7518 §6.2.1.2. */
    private static final int P256_COORDINATE_BYTES = 32;

    /** Fixed width of an Ed25519 public key per RFC 8032. */
    private static final int ED25519_KEY_BYTES = 32;

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    /**
     * Builds the public JWK members of a public key.
     *
     * @param publicKey the public key; must not be {@code null}
     * @return the unmodifiable member map — {@code kty} plus the members listed on the type
     * @throws IllegalArgumentException if the key is not an RSA, EC P-256 or OKP Ed25519 key, or an
     *         EC key's point does not lie on curve P-256
     */
    public static Map<String, Object> of(PublicKey publicKey) {
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        if (publicKey instanceof RSAPublicKey rsaKey) {
            return Map.of(
                    "kty", KTY_RSA,
                    "e", base64UrlUnsigned(rsaKey.getPublicExponent()),
                    "n", base64UrlUnsigned(rsaKey.getModulus()));
        }
        if (publicKey instanceof ECPublicKey ecKey) {
            requireP256Curve(ecKey.getParams());
            ECPoint point = ecKey.getW();
            requireOnP256(point);
            return Map.of(
                    "kty", KTY_EC,
                    "crv", CRV_P256,
                    // RFC 7518 §6.2.1.2: coordinates are left-padded to the full coordinate width,
                    // so base64UrlUnsigned (which strips rather than pads) must not be used here.
                    "x", base64UrlFixedWidth(point.getAffineX(), P256_COORDINATE_BYTES),
                    "y", base64UrlFixedWidth(point.getAffineY(), P256_COORDINATE_BYTES));
        }
        if (publicKey instanceof EdECPublicKey edKey) {
            if (!CRV_ED25519.equals(edKey.getParams().getName())) {
                throw new IllegalArgumentException(OKP_CURVE_NOT_ED25519);
            }
            return Map.of(
                    "kty", KTY_OKP,
                    "crv", CRV_ED25519,
                    "x", encodeEd25519Point(edKey.getPoint()));
        }
        throw new IllegalArgumentException(UNSUPPORTED_KEY_TYPE);
    }

    /**
     * Resolves curve P-256's domain parameters from the JCA provider.
     *
     * @return the {@code secp256r1} domain parameters
     * @throws IllegalStateException if the provider does not offer curve P-256
     */
    private static ECParameterSpec resolveP256Params() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance(KTY_EC);
            parameters.init(new ECGenParameterSpec(CURVE_SECP256R1));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JCA provider does not offer curve P-256 (secp256r1)", e);
        }
    }

    /**
     * Fails when an EC key is not on curve P-256. The comparison covers the full domain parameters —
     * curve, generator, order and cofactor — because matching only the field size would accept a
     * different 256-bit curve and publish it as {@code "crv":"P-256"}.
     * <p>
     * {@link ECParameterSpec} does not define {@code equals}, so its components are compared
     * individually; {@link EllipticCurve} and {@link ECPoint} do define value equality.
     *
     * @param params the key's domain parameters
     * @throws IllegalArgumentException if the parameters are not curve P-256's
     */
    private static void requireP256Curve(ECParameterSpec params) {
        boolean onP256 = P256_PARAMS.getCurve().equals(params.getCurve())
                && P256_PARAMS.getGenerator().equals(params.getGenerator())
                && P256_PARAMS.getOrder().equals(params.getOrder())
                && P256_PARAMS.getCofactor() == params.getCofactor();
        if (!onP256) {
            throw new IllegalArgumentException(CURVE_NOT_P256);
        }
    }

    /**
     * Fails when a point does not lie on curve P-256: both affine coordinates must be in
     * {@code [0, p)} and satisfy {@code y^2 = x^3 + ax + b (mod p)}. The point at infinity is refused.
     *
     * @param point the key's public point
     * @throws IllegalArgumentException if the point is not on curve P-256
     */
    private static void requireOnP256(ECPoint point) {
        if (ECPoint.POINT_INFINITY.equals(point)) {
            throw new IllegalArgumentException(POINT_NOT_ON_CURVE);
        }
        EllipticCurve curve = P256_PARAMS.getCurve();
        BigInteger p = ((ECFieldFp) curve.getField()).getP();
        BigInteger x = point.getAffineX();
        BigInteger y = point.getAffineY();
        if (!inField(x, p) || !inField(y, p)) {
            throw new IllegalArgumentException(POINT_NOT_ON_CURVE);
        }
        BigInteger left = y.multiply(y).mod(p);
        BigInteger right = x.pow(3).add(curve.getA().multiply(x)).add(curve.getB()).mod(p);
        if (!left.equals(right)) {
            throw new IllegalArgumentException(POINT_NOT_ON_CURVE);
        }
    }

    private static boolean inField(BigInteger value, BigInteger p) {
        return value.signum() >= 0 && value.compareTo(p) < 0;
    }

    /**
     * Encodes an RSA member as an unsigned big-endian base64url value, stripping the sign byte
     * {@link BigInteger#toByteArray()} prepends. Suitable for {@code e} and {@code n}, which are
     * variable-width; it MUST NOT be used for EC coordinates, which are fixed-width and require
     * left-padding — see {@link #base64UrlFixedWidth(BigInteger, int)}.
     */
    private static String base64UrlUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return BASE64_URL.encodeToString(bytes);
    }

    /**
     * Encodes a fixed-width member as a big-endian base64url value left-padded with zeros to exactly
     * {@code width} bytes, per RFC 7518 §6.2.1.2.
     *
     * @param value the non-negative value to encode
     * @param width the fixed member width in bytes
     * @return the base64url encoding of the zero-padded big-endian value
     */
    private static String base64UrlFixedWidth(BigInteger value, int width) {
        return BASE64_URL.encodeToString(toFixedWidth(value, width));
    }

    /**
     * Renders a non-negative value as a big-endian byte array of exactly {@code width} bytes.
     *
     * @param value the non-negative value to render
     * @param width the target width in bytes
     * @return the zero-padded big-endian representation
     * @throws IllegalArgumentException if the value does not fit into {@code width} bytes
     */
    private static byte[] toFixedWidth(BigInteger value, int width) {
        byte[] magnitude = value.toByteArray();
        int offset = 0;
        while (offset < magnitude.length - 1 && magnitude[offset] == 0) {
            offset++;
        }
        int significantLength = magnitude.length - offset;
        if (significantLength > width) {
            throw new IllegalArgumentException(MEMBER_TOO_WIDE);
        }
        byte[] fixedWidth = new byte[width];
        System.arraycopy(magnitude, offset, fixedWidth, width - significantLength, significantLength);
        return fixedWidth;
    }

    /**
     * Encodes an Ed25519 public point as the RFC 8032 32-byte {@code x} member: the point's
     * {@code y} coordinate big-endian left-padded to 32 bytes, byte-reversed to little-endian, with
     * the most significant bit of the final byte set when {@code x} is odd.
     * <p>
     * This is the exact inverse of {@code JwkKeyHandler.parseOkpKey}, the reference implementation
     * for the encoding.
     */
    private static String encodeEd25519Point(EdECPoint point) {
        byte[] encoded = toFixedWidth(point.getY(), ED25519_KEY_BYTES);
        reverseInPlace(encoded);
        if (point.isXOdd()) {
            encoded[encoded.length - 1] |= (byte) 0x80;
        }
        return BASE64_URL.encodeToString(encoded);
    }

    /**
     * Reverses a byte array in place (big-endian to little-endian conversion).
     */
    private static void reverseInPlace(byte[] array) {
        for (int i = 0, j = array.length - 1; i < j; i++, j--) {
            byte tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
    }
}
