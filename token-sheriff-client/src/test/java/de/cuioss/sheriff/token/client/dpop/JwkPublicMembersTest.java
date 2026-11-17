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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Serial;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JwkPublicMembers}: the member set per key type, the fixed-width and unsigned
 * encodings, and the key-material-free refusals of off-curve, wrong-curve and unsupported keys.
 */
@DisplayName("JwkPublicMembers JWK member sets and refusals")
class JwkPublicMembersTest {

    /** A P-256 key whose affine {@code x} coordinate is 247 bits long, so it needs left-padding. */
    private static final String EC_P256_PADDED_X_X509 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEAFCrxWGiWPnK6GVejRh639ltV5DySo+6eBJObher"
            + "D9RR1Ikah9SehSqYnWMoxRpwcijyApUfO7jki+m+x52vMw==";

    private static final int P256_COORDINATE_BYTES = 32;
    private static final int RSA_2048_MODULUS_BYTES = 256;

    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private static PublicKey rsaKey;
    private static ECPublicKey p256Key;
    private static PublicKey ed25519Key;

    @BeforeAll
    static void generateKeys() throws GeneralSecurityException {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        rsaKey = rsa.generateKeyPair().getPublic();

        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(new ECGenParameterSpec("secp256r1"));
        p256Key = (ECPublicKey) ec.generateKeyPair().getPublic();

        ed25519Key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("memberSets")
    @DisplayName("Should publish exactly the RFC 7638 member set of each supported key type")
    void shouldPublishMemberSetPerKeyType(String keyType, PublicKey publicKey, Set<String> expectedMembers,
            Map<String, String> expectedFixedMembers) {
        Map<String, Object> members = JwkPublicMembers.of(publicKey);

        assertEquals(expectedMembers, members.keySet(), "the %s member set".formatted(keyType));
        expectedFixedMembers.forEach((name, value) -> assertEquals(value, members.get(name),
                "the %s member %s".formatted(keyType, name)));
    }

    @Test
    @DisplayName("Should left-pad an EC coordinate that has a leading zero byte to 32 bytes")
    void shouldLeftPadEcCoordinate() throws Exception {
        var paddedKey = (ECPublicKey) KeyFactory.getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(EC_P256_PADDED_X_X509)));

        byte[] x = BASE64_URL_DECODER.decode((String) JwkPublicMembers.of(paddedKey).get("x"));

        assertAll("padded x coordinate",
                () -> assertEquals(P256_COORDINATE_BYTES, x.length, "x is always 32 bytes wide"),
                () -> assertEquals(0, x[0], "the leading zero byte is kept as padding"),
                () -> assertEquals(paddedKey.getW().getAffineX(), new BigInteger(1, x),
                        "the padded bytes still encode the coordinate"));
    }

    @Test
    @DisplayName("Should strip the sign byte of an RSA modulus")
    void shouldStripRsaModulusSignByte() {
        BigInteger modulus = ((RSAPublicKey) rsaKey).getModulus();

        byte[] n = BASE64_URL_DECODER.decode((String) JwkPublicMembers.of(rsaKey).get("n"));

        assertAll("unsigned modulus",
                () -> assertEquals(RSA_2048_MODULUS_BYTES + 1, modulus.toByteArray().length,
                        "the fixture's two's-complement form carries a sign byte"),
                () -> assertEquals(RSA_2048_MODULUS_BYTES, n.length, "n carries no sign byte"),
                () -> assertNotEquals(0, n[0], "n has no leading zero byte"),
                () -> assertEquals(modulus, new BigInteger(1, n), "n still encodes the modulus"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedKeys")
    @DisplayName("Should refuse an unsupported key with a fixed message free of key material")
    void shouldRefuseWithFixedMessage(String description, PublicKey publicKey, String expectedMessage,
            List<String> forbiddenFragments) {
        var thrown = assertThrows(IllegalArgumentException.class, () -> JwkPublicMembers.of(publicKey),
                "%s must be refused".formatted(description));

        assertEquals(expectedMessage, thrown.getMessage(), "the refusal text is the fixed constant");
        forbiddenFragments.forEach(fragment -> assertFalse(thrown.getMessage().contains(fragment),
                "the refusal must not render key material or the field size"));
    }

    @Test
    @DisplayName("Should return an unmodifiable member map")
    void shouldReturnUnmodifiableMap() {
        Map<String, Object> members = JwkPublicMembers.of(p256Key);

        assertThrows(UnsupportedOperationException.class, () -> members.put("kid", "injected"),
                "a caller must not be able to alter the published members");
    }

    @Test
    @DisplayName("Should reject a null public key")
    void shouldRejectNullKey() {
        assertThrows(NullPointerException.class, () -> JwkPublicMembers.of(null));
    }

    static Stream<Arguments> memberSets() {
        return Stream.of(
                Arguments.of("RSA", rsaKey, Set.of("kty", "e", "n"), Map.of("kty", "RSA", "e", "AQAB")),
                Arguments.of("EC", p256Key, Set.of("kty", "crv", "x", "y"), Map.of("kty", "EC", "crv", "P-256")),
                Arguments.of("OKP", ed25519Key, Set.of("kty", "crv", "x"), Map.of("kty", "OKP", "crv", "Ed25519")));
    }

    static Stream<Arguments> refusedKeys() throws GeneralSecurityException {
        ECParameterSpec p256 = p256Key.getParams();
        ECPoint generator = p256.getGenerator();
        BigInteger prime = ((ECFieldFp) p256.getCurve().getField()).getP();
        ECPoint offCurve = new ECPoint(generator.getAffineX(), generator.getAffineY().add(BigInteger.ONE));
        ECPoint outOfField = new ECPoint(generator.getAffineX().add(prime), generator.getAffineY());

        KeyPairGenerator p384 = KeyPairGenerator.getInstance("EC");
        p384.initialize(new ECGenParameterSpec("secp384r1"));
        var p384Key = (ECPublicKey) p384.generateKeyPair().getPublic();

        KeyPairGenerator dsa = KeyPairGenerator.getInstance("DSA");
        dsa.initialize(2048);

        return Stream.of(
                Arguments.of("an off-curve P-256 point", new FixedEcPublicKey(offCurve, p256),
                        JwkPublicMembers.POINT_NOT_ON_CURVE, renderings(offCurve)),
                Arguments.of("a P-256 coordinate outside the field", new FixedEcPublicKey(outOfField, p256),
                        JwkPublicMembers.POINT_NOT_ON_CURVE, renderings(outOfField)),
                Arguments.of("the point at infinity", new FixedEcPublicKey(ECPoint.POINT_INFINITY, p256),
                        JwkPublicMembers.POINT_NOT_ON_CURVE, List.of()),
                Arguments.of("a secp256k1 key", new FixedEcPublicKey(Secp256k1.GENERATOR, Secp256k1.PARAMS),
                        JwkPublicMembers.CURVE_NOT_P256, withFieldSize(renderings(Secp256k1.GENERATOR))),
                Arguments.of("a P-384 key", p384Key,
                        JwkPublicMembers.CURVE_NOT_P256, withFieldSize(renderings(p384Key.getW()))),
                Arguments.of("an Ed448 key", KeyPairGenerator.getInstance("Ed448").generateKeyPair().getPublic(),
                        JwkPublicMembers.OKP_CURVE_NOT_ED25519, List.of("Ed448")),
                Arguments.of("a DSA key", dsa.generateKeyPair().getPublic(),
                        JwkPublicMembers.UNSUPPORTED_KEY_TYPE, List.of("DSA")));
    }

    /** Decimal, hexadecimal and base64url renderings of both coordinates of a point. */
    private static List<String> renderings(ECPoint point) {
        List<String> fragments = new ArrayList<>();
        for (BigInteger coordinate : List.of(point.getAffineX(), point.getAffineY())) {
            fragments.add(coordinate.toString());
            fragments.add(coordinate.toString(16));
            fragments.add(Base64.getUrlEncoder().withoutPadding().encodeToString(coordinate.toByteArray()));
        }
        return fragments;
    }

    /** Adds the field-size rendering a curve refusal once carried, so its return is caught. */
    private static List<String> withFieldSize(List<String> fragments) {
        List<String> extended = new ArrayList<>(fragments);
        extended.add("-bit");
        return extended;
    }

    /** The published domain parameters of secp256k1 (SEC 2 §2.4.1), a 256-bit curve that is not P-256. */
    private static final class Secp256k1 {

        private static final BigInteger PRIME =
                new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16);
        private static final BigInteger ORDER =
                new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
        private static final ECPoint GENERATOR = new ECPoint(
                new BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16),
                new BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16));
        private static final ECParameterSpec PARAMS = new ECParameterSpec(
                new EllipticCurve(new ECFieldFp(PRIME), BigInteger.ZERO, BigInteger.valueOf(7)),
                GENERATOR, ORDER, 1);

        private Secp256k1() {
        }
    }

    /**
     * An EC public key assembled from an arbitrary point and domain parameters. The JDK refuses to
     * build such keys through {@code KeyFactory} when the point is invalid, so the off-curve cases are
     * hand-built; {@link JwkPublicMembers} reads nothing but the point and the parameters.
     */
    private record FixedEcPublicKey(ECPoint w, ECParameterSpec params) implements ECPublicKey {

        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public ECPoint getW() {
            return w;
        }

        @Override
        public ECParameterSpec getParams() {
            return params;
        }

        @Override
        public String getAlgorithm() {
            return "EC";
        }

        @Override
        public String getFormat() {
            return "X.509";
        }

        @Override
        public byte[] getEncoded() {
            return new byte[0];
        }
    }
}
