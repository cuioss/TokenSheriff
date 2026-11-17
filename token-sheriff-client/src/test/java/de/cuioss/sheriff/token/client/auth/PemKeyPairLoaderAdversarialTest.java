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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static de.cuioss.sheriff.token.client.auth.PemKeyPairLoaderTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial regression tests pinning the refusal contract of {@link PemKeyPairLoader}.
 * <p>
 * Each case writes a hostile PEM file (or a non-file path) into a {@link TempDir}, all key material
 * generated at test time, and asserts that the loader refuses it with exactly the expected fixed
 * text, attaches no cause, and leaks neither the file path nor any fragment of a base64 body.
 */
@DisplayName("PemKeyPairLoader adversarial refusals")
class PemKeyPairLoaderAdversarialTest {

    /** The length of the body fragment checked for absence in a refusal message. */
    private static final int FRAGMENT_LENGTH = 8;

    private static KeyPair rsa2048;
    private static KeyPair otherRsa2048;
    private static KeyPair rsa1024;
    private static KeyPair ecP256;
    private static KeyPair ecP384;
    private static KeyPair ed448;

    @TempDir
    Path tempDir;

    /** Creates the path under test inside the temporary directory. */
    @FunctionalInterface
    interface Fixture {
        Path create(Path directory) throws IOException, GeneralSecurityException;
    }

    @BeforeAll
    static void generateKeyPairs() throws GeneralSecurityException {
        rsa2048 = rsaKeyPair(2048);
        otherRsa2048 = rsaKeyPair(2048);
        rsa1024 = rsaKeyPair(1024);
        ecP256 = ecKeyPair("secp256r1");
        ecP384 = ecKeyPair("secp384r1");
        ed448 = KeyPairGenerator.getInstance("Ed448").generateKeyPair();
    }

    static Stream<Arguments> hostileFiles() {
        return Stream.of(
                // size, file kind and encoding
                refusal("file one byte beyond the bound",
                        dir -> write(dir, filled(PemKeyPairLoader.MAX_FILE_BYTES + 1)),
                        PemKeyPairLoader.TOO_LARGE),
                refusal("multi-megabyte file",
                        dir -> write(dir, filled(4 * 1024 * 1024)),
                        PemKeyPairLoader.TOO_LARGE),
                refusal("directory",
                        dir -> Files.createTempDirectory(dir, "pem-dir-"),
                        PemKeyPairLoader.NOT_A_REGULAR_FILE),
                refusal("missing file",
                        dir -> dir.resolve("missing-key.pem"),
                        PemKeyPairLoader.NOT_A_REGULAR_FILE),
                refusal("empty file",
                        dir -> write(dir, new byte[0]),
                        PemKeyPairLoader.MISSING_BLOCK),
                refusal("non-ASCII byte in a body",
                        dir -> write(dir, withNonAsciiByte(pemFile(ecP256, LF))),
                        PemKeyPairLoader.NOT_ASCII),
                // text outside the blocks
                refusal("text before the blocks",
                        dir -> write(dir, "key material follows" + LF + pemFile(ecP256, LF)),
                        PemKeyPairLoader.TEXT_OUTSIDE_BLOCK),
                refusal("text after the blocks",
                        dir -> write(dir, pemFile(ecP256, LF) + "trailing comment" + LF),
                        PemKeyPairLoader.TEXT_OUTSIDE_BLOCK),
                refusal("text between the blocks",
                        dir -> write(dir, privateBlock(ecP256) + "between" + LF + publicBlock(ecP256)),
                        PemKeyPairLoader.TEXT_OUTSIDE_BLOCK),
                // labels and block structure
                refusal("unknown label",
                        dir -> write(dir, pem("CERTIFICATE", ecP256.getPublic().getEncoded(), LF)
                                + pemFile(ecP256, LF)),
                        PemKeyPairLoader.UNKNOWN_LABEL),
                refusal("repeated PRIVATE KEY block",
                        dir -> write(dir, privateBlock(ecP256) + privateBlock(ecP256) + publicBlock(ecP256)),
                        PemKeyPairLoader.REPEATED_BLOCK),
                refusal("repeated PUBLIC KEY block",
                        dir -> write(dir, privateBlock(ecP256) + publicBlock(ecP256) + publicBlock(ecP256)),
                        PemKeyPairLoader.REPEATED_BLOCK),
                refusal("BEGIN and END labels that differ",
                        dir -> write(dir, privateBlock(ecP256).replace("-----END PRIVATE KEY-----",
                                "-----END PUBLIC KEY-----") + publicBlock(ecP256)),
                        PemKeyPairLoader.LABEL_MISMATCH),
                refusal("unterminated block",
                        dir -> write(dir, publicBlock(ecP256) + privateBlock(ecP256)
                                .replace(LF + "-----END PRIVATE KEY-----" + LF, "")),
                        PemKeyPairLoader.UNTERMINATED_BLOCK),
                refusal("public block only",
                        dir -> write(dir, publicBlock(ecP256)),
                        PemKeyPairLoader.MISSING_BLOCK),
                refusal("PEM injection: a second BEGIN inside a body",
                        dir -> write(dir, injectedBoundary(ecP256) + publicBlock(ecP256)),
                        PemKeyPairLoader.BOUNDARY_INSIDE_BLOCK),
                refusal("invalid base64 body",
                        dir -> write(dir, "-----BEGIN PRIVATE KEY-----" + LF + "MIGH@@not*base64!!" + LF
                                + "-----END PRIVATE KEY-----" + LF + publicBlock(ecP256)),
                        PemKeyPairLoader.INVALID_BASE64),
                // unsupported private-key encodings
                refusal("ENCRYPTED PRIVATE KEY block",
                        dir -> write(dir, relabelledPrivate(ecP256, "ENCRYPTED PRIVATE KEY") + publicBlock(ecP256)),
                        PemKeyPairLoader.UNSUPPORTED_PRIVATE_KEY_ENCODING),
                refusal("PKCS#1 RSA PRIVATE KEY block",
                        dir -> write(dir, relabelledPrivate(rsa2048, "RSA PRIVATE KEY") + publicBlock(rsa2048)),
                        PemKeyPairLoader.UNSUPPORTED_PRIVATE_KEY_ENCODING),
                refusal("SEC1 EC PRIVATE KEY block",
                        dir -> write(dir, relabelledPrivate(ecP256, "EC PRIVATE KEY") + publicBlock(ecP256)),
                        PemKeyPairLoader.UNSUPPORTED_PRIVATE_KEY_ENCODING),
                // key material and policy
                refusal("halves of two different key pairs",
                        dir -> write(dir, privateBlock(rsa2048) + publicBlock(otherRsa2048)),
                        PemKeyPairLoader.KEY_PAIR_MISMATCH),
                refusal("RSA 1024 key",
                        dir -> write(dir, pemFile(rsa1024, LF)),
                        PemKeyPairLoader.RSA_MODULUS_TOO_SHORT),
                refusal("EC P-384 key",
                        dir -> write(dir, pemFile(ecP384, LF)),
                        PemKeyPairLoader.EC_KEY_NOT_P256),
                refusal("Ed448 key",
                        dir -> write(dir, pemFile(ed448, LF)),
                        PemKeyPairLoader.KEY_TYPE_NOT_ALLOWED));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hostileFiles")
    @DisplayName("Should refuse a hostile PEM file with its fixed text and leak neither path nor content")
    void shouldRefuseWithFixedText(String scenario, Fixture fixture, String expectedMessage)
            throws Exception {
        Path file = fixture.create(tempDir);

        var thrown = assertThrows(IllegalArgumentException.class, () -> PemKeyPairLoader.load(file),
                "the %s must be refused".formatted(scenario));

        String message = thrown.getMessage();
        assertAll(scenario,
                () -> assertEquals(expectedMessage, message,
                        "the %s must be refused with its fixed text".formatted(scenario)),
                () -> assertNull(thrown.getCause(),
                        "the refusal of the %s must not attach a cause".formatted(scenario)),
                () -> assertFalse(message.contains(file.toString()),
                        "the refusal must not carry the file path"),
                () -> assertFalse(message.contains(file.getFileName().toString()),
                        "the refusal must not carry the file name"),
                () -> assertBodyFragmentsAbsent(file, message));
    }

    /**
     * Whether the JDK {@code KeyFactory} validates an EC point at decode time differs between JDK
     * builds: when it does, the off-curve key fails to decode and is refused as a key the policy does
     * not admit; when it does not, the decoded key reaches the on-curve check. Both refusals are
     * fixed texts the policy allows, so the test accepts either and pins everything else.
     */
    @Test
    @DisplayName("Should refuse an off-curve EC public half with a fixed text whether or not the JDK validates the point")
    void shouldRefuseOffCurveEcPublicHalf() throws Exception {
        Path onCurve = write(tempDir, pemFile(ecP256, LF));
        Path offCurve = write(tempDir, privateBlock(ecP256) + pem(PUBLIC_KEY, offCurve(ecP256), LF));

        assertNotNull(PemKeyPairLoader.load(onCurve), "the on-curve P-256 key pair is the positive control");
        var thrown = assertThrows(IllegalArgumentException.class, () -> PemKeyPairLoader.load(offCurve),
                "the off-curve public half must be refused");

        String message = thrown.getMessage();
        assertAll("off-curve EC public half",
                () -> assertTrue(Set.of(PemKeyPairLoader.EC_KEY_NOT_P256, PemKeyPairLoader.KEY_TYPE_NOT_ALLOWED)
                                .contains(message),
                        "the refusal must be one of the two fixed texts the policy allows, but was: " + message),
                () -> assertNull(thrown.getCause(), "the refusal must not attach a cause"),
                () -> assertFalse(message.contains(offCurve.toString()), "the refusal must not carry the file path"),
                () -> assertBodyFragmentsAbsent(offCurve, message));
    }

    private static Arguments refusal(String scenario, Fixture fixture, String expectedMessage) {
        return Arguments.of(scenario, fixture, expectedMessage);
    }

    /**
     * Asserts that no body line of the file — every line that is not a boundary line — appears in
     * the message, by checking a leading fragment of each.
     */
    private static void assertBodyFragmentsAbsent(Path file, String message) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > PemKeyPairLoader.MAX_FILE_BYTES) {
            return;
        }
        List<String> fragments = Files.readAllLines(file, StandardCharsets.ISO_8859_1).stream()
                .map(String::strip)
                .filter(line -> !line.startsWith("-----") && line.length() >= FRAGMENT_LENGTH)
                .map(line -> line.substring(0, FRAGMENT_LENGTH))
                .toList();
        for (String fragment : fragments) {
            assertFalse(message.contains(fragment), "the refusal must not carry a fragment of the file content");
        }
    }

    private static String privateBlock(KeyPair keyPair) {
        return pem(PRIVATE_KEY, keyPair.getPrivate().getEncoded(), LF);
    }

    private static String publicBlock(KeyPair keyPair) {
        return pem(PUBLIC_KEY, keyPair.getPublic().getEncoded(), LF);
    }

    private static String relabelledPrivate(KeyPair keyPair, String label) {
        return pem(label, keyPair.getPrivate().getEncoded(), LF);
    }

    /** A private-key block whose body carries a {@code BEGIN PUBLIC KEY} line after its first line. */
    private static String injectedBoundary(KeyPair keyPair) {
        String block = privateBlock(keyPair);
        int secondLine = block.indexOf(LF, block.indexOf(LF) + 1) + 1;
        return block.substring(0, secondLine) + "-----BEGIN PUBLIC KEY-----" + LF + block.substring(secondLine);
    }

    /** The file content with a two-byte UTF-8 sequence inserted at the start of the first body line. */
    private static byte[] withNonAsciiByte(String content) {
        int bodyStart = content.indexOf(LF) + 1;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(content.substring(0, bodyStart).getBytes(StandardCharsets.US_ASCII));
        out.write(0xC3);
        out.write(0xA9);
        out.writeBytes(content.substring(bodyStart).getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    /**
     * The X.509 encoding of a P-256 public key whose {@code y} coordinate is altered in its last
     * byte, so the encoded point no longer lies on the curve.
     */
    private static byte[] offCurve(KeyPair keyPair) {
        byte[] encoded = keyPair.getPublic().getEncoded().clone();
        encoded[encoded.length - 1] ^= 0x01;
        return encoded;
    }

    private static byte[] filled(int size) {
        byte[] content = new byte[size];
        Arrays.fill(content, (byte) 'A');
        return content;
    }
}
