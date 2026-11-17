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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden regression test pinning the RFC 7638 {@code jkt} thumbprints {@link DpopProofGenerator}
 * derives for fixed proof keys.
 * <p>
 * The expected values were captured from the generator before its JWK member construction moved
 * into a shared utility; any change to the published member set or its encoding changes a
 * thumbprint and fails this test. The keys are hard-coded X.509 {@code SubjectPublicKeyInfo}
 * encodings so the assertion is reproducible. The EC key is chosen so its {@code x} coordinate has
 * a leading zero byte, which exercises the RFC 7518 §6.2.1.2 left-padding.
 * <p>
 * The {@code jkt} depends on the public key alone, so each generator is built with a public-only
 * key pair; no proof is signed.
 */
@DisplayName("DpopProofGenerator golden jkt thumbprints")
class DpopProofGeneratorJktGoldenTest {

    private static final String RSA_2048_X509 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2SKE+fw9Y5PMWr7WZkEBg4v41u8eFkt+"
            + "syH/3Kpufir8aSNT4JjFBqflqF1bpg0abFLGJYGVxO1UM+vtivDJ5/ZS2bYjp9cejKdhciLcz5xLN+dHmj+Vu0IZ9NjOKW8/fQqN"
            + "EVsj2NFQYNo7Uvm/SiS8isj9ROaMSOqsouvj4MGDCI4U+HzO68QsBWH/4HMuwp57k9rPEw7HhQGNgSe5WUHcy3PX276L8JkKVK8R"
            + "LzhBYNf5HNK29NN8KpaO1G8BMPfKxMDaV2glBNGwwuY8YIC1dc5k7NtqC02BfsxINzRYpJWqm3MLAGdsmDxQPr35KjPE0ThFvskg"
            + "XvZKtEjKvwIDAQAB";

    /** A P-256 key whose affine {@code x} coordinate is 247 bits long, so it needs left-padding. */
    private static final String EC_P256_PADDED_X_X509 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEAFCrxWGiWPnK6GVejRh639ltV5DySo+6eBJObher"
            + "D9RR1Ikah9SehSqYnWMoxRpwcijyApUfO7jki+m+x52vMw==";

    private static final String ED25519_X509 = "MCowBQYDK2VwAyEAWo8pUJ2jn5Iw081JApJL4NBEduV1TYpn3PaYiW5MCBQ=";

    private static final String RSA_JKT = "o6nDrI1Nqj_qJGgic2_0df9yOWI-A_uNJhml0NJz2zw";
    private static final String EC_JKT = "Kr0220D6LZHqbqz1khKmugThggKz9H9IqVhjVcby-pU";
    private static final String ED25519_JKT = "kiEwjm4CWKM0tSQYp7Xz2l1P7upDZNbGdHx7vG2MBFc";

    /** Bit length below which a P-256 coordinate's 32-byte big-endian form starts with a zero byte. */
    private static final int PADDED_COORDINATE_MAX_BITS = 248;

    @ParameterizedTest(name = "{0} with {2}")
    @MethodSource("goldenThumbprints")
    @DisplayName("Should derive the captured jkt for every supported algorithm of each key type")
    void shouldDeriveGoldenJkt(String keyAlgorithm, String x509, String jwsAlgorithm, String expectedJkt)
            throws Exception {
        KeyPair publicOnly = new KeyPair(decodePublicKey(keyAlgorithm, x509), null);

        var generator = new DpopProofGenerator(publicOnly, jwsAlgorithm);

        assertEquals(expectedJkt, generator.jkt(),
                "the jkt for a fixed %s key and %s must stay the captured golden value".formatted(
                        keyAlgorithm, jwsAlgorithm));
    }

    @Test
    @DisplayName("Should use an EC fixture whose x coordinate needs left-padding")
    void shouldUseEcFixtureNeedingLeftPadding() throws Exception {
        var ecKey = (ECPublicKey) decodePublicKey("EC", EC_P256_PADDED_X_X509);

        assertTrue(ecKey.getW().getAffineX().bitLength() < PADDED_COORDINATE_MAX_BITS,
                "the golden EC key must keep a leading zero byte in x, or it no longer pins the padding");
    }

    static Stream<Arguments> goldenThumbprints() {
        return Stream.of(
                Arguments.of("RSA", RSA_2048_X509, "RS256", RSA_JKT),
                Arguments.of("RSA", RSA_2048_X509, "RS384", RSA_JKT),
                Arguments.of("RSA", RSA_2048_X509, "RS512", RSA_JKT),
                Arguments.of("RSA", RSA_2048_X509, "PS256", RSA_JKT),
                Arguments.of("EC", EC_P256_PADDED_X_X509, "ES256", EC_JKT),
                Arguments.of("Ed25519", ED25519_X509, "EdDSA", ED25519_JKT));
    }

    private static PublicKey decodePublicKey(String keyAlgorithm, String x509) throws GeneralSecurityException {
        return KeyFactory.getInstance(keyAlgorithm)
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(x509)));
    }
}
