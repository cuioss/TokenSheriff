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

import de.cuioss.sheriff.token.client.auth.KeyPairPolicy.KeyType;
import de.cuioss.sheriff.token.client.dpop.DpopProofGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Happy-path and policy tests for {@link PemKeyPairLoader} and {@link KeyPairPolicy}.
 * <p>
 * Every PEM file is generated at test time from freshly generated key pairs and written into a
 * {@link TempDir}; no key material is committed. The refusal cases live in
 * {@link PemKeyPairLoaderAdversarialTest}, which reuses the PEM helpers declared here.
 */
@DisplayName("PemKeyPairLoader happy paths and key pair policy")
class PemKeyPairLoaderTest {

    static final String LF = "\n";
    static final String CRLF = "\r\n";
    static final String PRIVATE_KEY = "PRIVATE KEY";
    static final String PUBLIC_KEY = "PUBLIC KEY";

    private static final String TOKEN_ENDPOINT = "https://as.example.com/token";

    private static KeyPair rsa2048;
    private static KeyPair rsa3072;
    private static KeyPair ecP256;
    private static KeyPair ed25519;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void generateKeyPairs() throws GeneralSecurityException {
        rsa2048 = rsaKeyPair(2048);
        rsa3072 = rsaKeyPair(3072);
        ecP256 = ecKeyPair("secp256r1");
        ed25519 = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    static Stream<Arguments> supportedKeyPairs() {
        return Stream.of(
                Arguments.of("RSA 2048", rsa2048),
                Arguments.of("EC P-256", ecP256),
                Arguments.of("Ed25519", ed25519));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("supportedKeyPairs")
    @DisplayName("Should load each supported key type with the private block first")
    void shouldLoadPrivateKeyFirst(String keyType, KeyPair expected) throws Exception {
        Path file = write(tempDir, pemFile(expected, LF));

        assertSameKeyPair(expected, PemKeyPairLoader.load(file), keyType);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("supportedKeyPairs")
    @DisplayName("Should load each supported key type with the public block first")
    void shouldLoadPublicKeyFirst(String keyType, KeyPair expected) throws Exception {
        String content = pem(PUBLIC_KEY, expected.getPublic().getEncoded(), LF)
                + pem(PRIVATE_KEY, expected.getPrivate().getEncoded(), LF);
        Path file = write(tempDir, content);

        assertSameKeyPair(expected, PemKeyPairLoader.load(file), keyType);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("supportedKeyPairs")
    @DisplayName("Should load each supported key type with CRLF line endings")
    void shouldLoadCrlfLineEndings(String keyType, KeyPair expected) throws Exception {
        Path file = write(tempDir, pemFile(expected, CRLF));

        assertSameKeyPair(expected, PemKeyPairLoader.load(file), keyType);
    }

    @Test
    @DisplayName("Should accept blank lines before, between and after the blocks")
    void shouldAcceptBlankLinesAroundBlocks() throws Exception {
        String content = LF + pem(PRIVATE_KEY, ecP256.getPrivate().getEncoded(), LF) + LF + LF
                + pem(PUBLIC_KEY, ecP256.getPublic().getEncoded(), LF) + LF;
        Path file = write(tempDir, content);

        assertSameKeyPair(ecP256, PemKeyPairLoader.load(file), "EC P-256");
    }

    static Stream<Arguments> dpopKeyPairs() {
        return Stream.of(
                Arguments.of("RS256", rsa2048),
                Arguments.of("ES256", ecP256),
                Arguments.of("EdDSA", ed25519));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dpopKeyPairs")
    @DisplayName("Should return a pair DpopProofGenerator accepts and signs with")
    void shouldReturnPairAcceptedByDpopProofGenerator(String algorithm, KeyPair source) throws Exception {
        KeyPair loaded = PemKeyPairLoader.load(write(tempDir, pemFile(source, LF)));

        var generator = new DpopProofGenerator(loaded, algorithm);

        assertEquals(3, generator.generateProof("POST", TOKEN_ENDPOINT).split("\\.").length,
                "the loaded %s pair must produce a three-part DPoP proof".formatted(algorithm));
    }

    static Stream<Arguments> privateKeyJwtKeyPairs() {
        return Stream.of(
                Arguments.of("RS256", rsa2048),
                Arguments.of("ES256", ecP256));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("privateKeyJwtKeyPairs")
    @DisplayName("Should return a pair PrivateKeyJwtAuth accepts and signs with")
    void shouldReturnPairAcceptedByPrivateKeyJwtAuth(String algorithm, KeyPair source) throws Exception {
        KeyPairPolicy jwtPolicy = new KeyPairPolicy(EnumSet.of(KeyType.RSA, KeyType.EC_P256),
                KeyPairPolicy.MINIMUM_RSA_MODULUS_BITS);
        KeyPair loaded = PemKeyPairLoader.load(write(tempDir, pemFile(source, LF)), jwtPolicy);
        var auth = new PrivateKeyJwtAuth("client", TOKEN_ENDPOINT, loaded.getPrivate(), "kid-1", algorithm);
        Map<String, String> form = new HashMap<>();

        auth.decorate(form, new HashMap<>());

        assertEquals(3, form.get("client_assertion").split("\\.").length,
                "the loaded %s pair must produce a three-part client assertion".formatted(algorithm));
    }

    @Test
    @DisplayName("Should refuse an Ed25519 key under a policy that excludes Ed25519")
    void shouldRefuseEd25519WhenExcluded() throws Exception {
        KeyPairPolicy policy = new KeyPairPolicy(EnumSet.of(KeyType.RSA, KeyType.EC_P256),
                KeyPairPolicy.MINIMUM_RSA_MODULUS_BITS);
        Path file = write(tempDir, pemFile(ed25519, LF));

        var thrown = assertThrows(IllegalArgumentException.class, () -> PemKeyPairLoader.load(file, policy));

        assertEquals(PemKeyPairLoader.KEY_TYPE_NOT_ALLOWED, thrown.getMessage(),
                "an excluded key type must be refused with the fixed key-type text");
    }

    @Test
    @DisplayName("Should refuse an RSA 2048 key under a policy requiring 3072 bits")
    void shouldRefuseRsaBelowRaisedMinimum() throws Exception {
        KeyPairPolicy policy = new KeyPairPolicy(EnumSet.allOf(KeyType.class), 3072);
        Path file = write(tempDir, pemFile(rsa2048, LF));

        var thrown = assertThrows(IllegalArgumentException.class, () -> PemKeyPairLoader.load(file, policy));

        assertEquals(PemKeyPairLoader.RSA_MODULUS_TOO_SHORT, thrown.getMessage(),
                "an RSA key below the raised minimum must be refused with the fixed text");
    }

    @Test
    @DisplayName("Should load an RSA 3072 key under a policy requiring 3072 bits")
    void shouldLoadRsaAtRaisedMinimum() throws Exception {
        KeyPairPolicy policy = new KeyPairPolicy(EnumSet.of(KeyType.RSA), 3072);
        Path file = write(tempDir, pemFile(rsa3072, LF));

        assertSameKeyPair(rsa3072, PemKeyPairLoader.load(file, policy), "RSA 3072");
    }

    @Test
    @DisplayName("Should admit every key type with a 2048-bit RSA minimum by default")
    void shouldProvideDefaults() {
        KeyPairPolicy defaults = KeyPairPolicy.defaults();

        assertAll("default policy",
                () -> assertEquals(EnumSet.allOf(KeyType.class), defaults.allowedKeyTypes(),
                        "the defaults must admit every key type"),
                () -> assertEquals(KeyPairPolicy.MINIMUM_RSA_MODULUS_BITS, defaults.minimumRsaModulusBits(),
                        "the defaults must require the 2048-bit RSA floor"),
                () -> assertTrue(defaults.allows(KeyType.ED25519), "the defaults must admit Ed25519"));
    }

    @Test
    @DisplayName("Should copy the admitted key types immutably")
    void shouldCopyKeyTypesImmutably() {
        Set<KeyType> keyTypes = EnumSet.of(KeyType.RSA);
        KeyPairPolicy policy = new KeyPairPolicy(keyTypes, KeyPairPolicy.MINIMUM_RSA_MODULUS_BITS);

        keyTypes.add(KeyType.ED25519);

        assertAll("immutable copy",
                () -> assertFalse(policy.allows(KeyType.ED25519),
                        "a later change to the caller's set must not widen the policy"),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> policy.allowedKeyTypes().add(KeyType.EC_P256),
                        "the admitted key types must not be modifiable"));
    }

    @Test
    @DisplayName("Should refuse a policy without key types or below the RSA floor")
    void shouldRefuseInvalidPolicy() {
        Set<KeyType> none = EnumSet.noneOf(KeyType.class);
        Set<KeyType> rsaOnly = EnumSet.of(KeyType.RSA);
        Set<KeyType> withNull = new HashSet<>();
        withNull.add(null);

        assertAll("policy refusals",
                () -> assertEquals(KeyPairPolicy.NO_KEY_TYPES,
                        assertThrows(IllegalArgumentException.class,
                                () -> new KeyPairPolicy(none, KeyPairPolicy.MINIMUM_RSA_MODULUS_BITS)).getMessage(),
                        "an empty key-type set must be refused with the fixed text"),
                () -> assertEquals(KeyPairPolicy.RSA_MINIMUM_TOO_LOW,
                        assertThrows(IllegalArgumentException.class,
                                () -> new KeyPairPolicy(rsaOnly, 2047)).getMessage(),
                        "an RSA minimum below 2048 bits must be refused with the fixed text"),
                () -> assertThrows(NullPointerException.class,
                        () -> new KeyPairPolicy(null, KeyPairPolicy.MINIMUM_RSA_MODULUS_BITS),
                        "a null key-type set must be refused"),
                () -> assertThrows(NullPointerException.class,
                        () -> new KeyPairPolicy(withNull, KeyPairPolicy.MINIMUM_RSA_MODULUS_BITS),
                        "a key-type set containing null must be refused"));
    }

    @Test
    @DisplayName("Should refuse null arguments")
    void shouldRefuseNullArguments() throws Exception {
        Path file = write(tempDir, pemFile(ecP256, LF));
        KeyPairPolicy defaults = KeyPairPolicy.defaults();

        assertAll("null rejection",
                () -> assertThrows(NullPointerException.class, () -> PemKeyPairLoader.load(null)),
                () -> assertThrows(NullPointerException.class, () -> PemKeyPairLoader.load(null, defaults)),
                () -> assertThrows(NullPointerException.class, () -> PemKeyPairLoader.load(file, null)));
    }

    private static void assertSameKeyPair(KeyPair expected, KeyPair actual, String keyType) {
        assertAll(keyType,
                () -> assertArrayEquals(expected.getPublic().getEncoded(), actual.getPublic().getEncoded(),
                        "the loaded public key must equal the written %s public key".formatted(keyType)),
                () -> assertArrayEquals(expected.getPrivate().getEncoded(), actual.getPrivate().getEncoded(),
                        "the loaded private key must equal the written %s private key".formatted(keyType)));
    }

    /**
     * Renders one PEM block with 64-character body lines and the given line ending.
     */
    static String pem(String label, byte[] der, String lineEnding) {
        String body = Base64.getMimeEncoder(64, lineEnding.getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + label + "-----" + lineEnding + body + lineEnding
                + "-----END " + label + "-----" + lineEnding;
    }

    /**
     * Renders the private-key block followed by the public-key block of a key pair.
     */
    static String pemFile(KeyPair keyPair, String lineEnding) {
        return pem(PRIVATE_KEY, keyPair.getPrivate().getEncoded(), lineEnding)
                + pem(PUBLIC_KEY, keyPair.getPublic().getEncoded(), lineEnding);
    }

    static Path write(Path directory, String content) throws IOException {
        return write(directory, content.getBytes(StandardCharsets.US_ASCII));
    }

    static Path write(Path directory, byte[] content) throws IOException {
        Path file = Files.createTempFile(directory, "key-", ".pem");
        Files.write(file, content);
        return file;
    }

    static KeyPair rsaKeyPair(int bits) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    static KeyPair ecKeyPair(String curve) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(curve));
        return generator.generateKeyPair();
    }
}
