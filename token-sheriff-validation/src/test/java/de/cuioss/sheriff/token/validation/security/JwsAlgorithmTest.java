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
package de.cuioss.sheriff.token.validation.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.spec.PSSParameterSpec;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JwsAlgorithm}, the single definition of the accepted JWS signing algorithms.
 * <p>
 * Every expectation here is written out literally. The catalog is what other classes derive from —
 * {@code SignatureAlgorithmPreferences} takes its default list of supported algorithms from the declared order and
 * {@code SignatureTemplateManager} builds its signature templates from the per-constant metadata —
 * so an expectation read back out of the catalog would agree with any change to it and pin nothing.
 *
 * @author Oliver Wolff
 */
@DisplayName("Tests the JWS signing-algorithm catalog")
class JwsAlgorithmTest {

    /**
     * One expected catalog entry. {@code digest}, {@code jcaSignatureAlgorithm} and the PSS members
     * are {@code null} / {@code 0} where the entry is expected to carry none — those absences are
     * assertions in their own right, not gaps.
     */
    private record ExpectedAlgorithm(String jwaName, String keyType, String digest,
    String jcaSignatureAlgorithm, String pssDigest, int pssSaltLengthBytes) {
    }

    /**
     * The catalog as it must be declared, in the order it must declare it in.
     */
    private static final List<ExpectedAlgorithm> EXPECTED_ALGORITHMS = List.of(
            new ExpectedAlgorithm("ES512", "EC", "SHA-512", null, null, 0),
            new ExpectedAlgorithm("ES384", "EC", "SHA-384", null, null, 0),
            new ExpectedAlgorithm("ES256", "EC", "SHA-256", null, null, 0),
            new ExpectedAlgorithm("EdDSA", "OKP", null, "EdDSA", null, 0),
            new ExpectedAlgorithm("PS512", "RSA", "SHA-512", "RSASSA-PSS", "SHA-512", 64),
            new ExpectedAlgorithm("PS384", "RSA", "SHA-384", "RSASSA-PSS", "SHA-384", 48),
            new ExpectedAlgorithm("PS256", "RSA", "SHA-256", "RSASSA-PSS", "SHA-256", 32),
            new ExpectedAlgorithm("RS512", "RSA", "SHA-512", "SHA512withRSA", null, 0),
            new ExpectedAlgorithm("RS384", "RSA", "SHA-384", "SHA384withRSA", null, 0),
            new ExpectedAlgorithm("RS256", "RSA", "SHA-256", "SHA256withRSA", null, 0));

    static List<ExpectedAlgorithm> expectedAlgorithms() {
        return EXPECTED_ALGORITHMS;
    }

    @Test
    @DisplayName("Declare exactly the expected algorithm names")
    void shouldDeclareExactlyTheExpectedAlgorithmNames() {
        Set<String> declared = new LinkedHashSet<>(declaredJwaNames());

        Set<String> expected = new LinkedHashSet<>(expectedJwaNames());
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(declared);
        Set<String> unexpected = new LinkedHashSet<>(declared);
        unexpected.removeAll(expected);

        assertAll("the catalog declares exactly the expected algorithms",
                () -> assertTrue(missing.isEmpty(), "Expected algorithms absent from the catalog: " + missing),
                () -> assertTrue(unexpected.isEmpty(), "Algorithms in the catalog but not expected: " + unexpected));
    }

    @Test
    @DisplayName("Declare the algorithms in the documented order")
    void shouldDeclareTheAlgorithmsInTheDocumentedOrder() {
        assertEquals(expectedJwaNames(), declaredJwaNames(),
                "Declaration order is the order SignatureAlgorithmPreferences publishes as its default list");
    }

    @ParameterizedTest
    @DisplayName("Carry the expected metadata per algorithm")
    @MethodSource("expectedAlgorithms")
    void shouldCarryTheExpectedMetadata(ExpectedAlgorithm expected) {
        JwsAlgorithm algorithm = JwsAlgorithm.fromJwaName(expected.jwaName())
                .orElseThrow(() -> new AssertionError("Catalog declares no entry named " + expected.jwaName()));

        assertAll("metadata of " + expected.jwaName(),
                () -> assertEquals(expected.keyType(), algorithm.getKeyType(),
                        "JWK key type of " + expected.jwaName()),
                () -> assertEquals(Optional.ofNullable(expected.digest()), algorithm.getDigest(),
                        "Digest of " + expected.jwaName()),
                () -> assertEquals(Optional.ofNullable(expected.jcaSignatureAlgorithm()),
                        algorithm.getJcaSignatureAlgorithm(),
                        "JCA signature-algorithm name of " + expected.jwaName()),
                () -> assertEquals(expected.pssSaltLengthBytes() > 0, algorithm.getPssParameters().isPresent(),
                        "PSS parameters are carried by the PS family only, and " + expected.jwaName()
                                + " is expected to " + (expected.pssSaltLengthBytes() > 0 ? "carry" : "carry no")
                                + " parameters"));
    }

    @ParameterizedTest
    @DisplayName("Carry the expected PSS parameters for the PS family")
    @MethodSource("expectedAlgorithms")
    void shouldCarryTheExpectedPssParameters(ExpectedAlgorithm expected) {
        Optional<PSSParameterSpec> parameters = JwsAlgorithm.fromJwaName(expected.jwaName())
                .orElseThrow(() -> new AssertionError("Catalog declares no entry named " + expected.jwaName()))
                .getPssParameters();

        assertEquals(expected.pssDigest(), parameters.map(PSSParameterSpec::getDigestAlgorithm).orElse(null),
                "PSS digest of " + expected.jwaName());
        assertEquals(expected.pssSaltLengthBytes(), parameters.map(PSSParameterSpec::getSaltLength).orElse(0).intValue(),
                "PSS salt length of " + expected.jwaName()
                        + " — a wrong salt length produces signatures a conformant verifier rejects");
    }

    @ParameterizedTest
    @DisplayName("Return an empty optional for a name the catalog does not declare")
    @ValueSource(strings = {"HS256", "none", "es256", "ES256 ", "RSA-OAEP", "EDDSA"})
    void shouldReturnEmptyForAnUnknownName(String jwaName) {
        assertTrue(JwsAlgorithm.fromJwaName(jwaName).isEmpty(),
                "An algorithm this library does not accept must not resolve: " + jwaName);
    }

    @Test
    @DisplayName("Return an empty optional for a null name")
    void shouldReturnEmptyForANullName() {
        assertTrue(JwsAlgorithm.fromJwaName(null).isEmpty(), "A null name must not resolve to an algorithm");
    }

    private static List<String> expectedJwaNames() {
        return EXPECTED_ALGORITHMS.stream().map(ExpectedAlgorithm::jwaName).toList();
    }

    private static List<String> declaredJwaNames() {
        return Arrays.stream(JwsAlgorithm.values()).map(JwsAlgorithm::getJwaName).toList();
    }
}
