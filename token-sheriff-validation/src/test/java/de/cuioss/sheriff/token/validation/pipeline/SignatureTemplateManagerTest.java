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

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.security.SignatureAlgorithmPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link SignatureTemplateManager}.
 * <p>
 * {@link #getSignatureInstanceSupportedAlgorithms(String)} is the single suite covering every
 * algorithm the manager pre-configures, asserting the exact JCA signature-algorithm name each one
 * resolves to. It carries the coverage the per-family suites used to give: those asserted that a
 * name merely contained {@code RSA} or {@code ECDSA}, which every wrong name in the same family
 * also satisfies, so the family they proved was never the algorithm under test.
 *
 * @author Oliver Wolff
 */
class SignatureTemplateManagerTest {

    /**
     * The JCA signature algorithm each JWA algorithm must resolve to, written out literally.
     * <p>
     * The manager composes these names from the {@code JwsAlgorithm} catalog, so an expectation
     * derived from that catalog would agree with any composition it produces. Only a literal name
     * can catch a rename, a widened family match, or a PS entry silently resolving to PKCS#1 v1.5.
     */
    private static final Map<String, String> EXPECTED_JCA_NAMES = Map.ofEntries(
            Map.entry("ES512", "SHA512withECDSA"),
            Map.entry("ES384", "SHA384withECDSA"),
            Map.entry("ES256", "SHA256withECDSA"),
            Map.entry("EdDSA", "EdDSA"),
            Map.entry("PS512", "RSASSA-PSS"),
            Map.entry("PS384", "RSASSA-PSS"),
            Map.entry("PS256", "RSASSA-PSS"),
            Map.entry("RS512", "SHA512withRSA"),
            Map.entry("RS384", "SHA384withRSA"),
            Map.entry("RS256", "SHA256withRSA"));

    private SignatureTemplateManager manager;

    @BeforeEach
    void setUp() {
        manager = new SignatureTemplateManager(new SignatureAlgorithmPreferences());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ES512", "ES384", "ES256", "EdDSA", "PS512", "PS384", "PS256",
            "RS512", "RS384", "RS256"})
    void getSignatureInstanceSupportedAlgorithms(String algorithm) {
        Signature signature = manager.getSignatureInstance(algorithm);

        assertNotNull(signature, "Signature should not be null for algorithm: " + algorithm);
        assertEquals(EXPECTED_JCA_NAMES.get(algorithm), signature.getAlgorithm(),
                "JCA signature algorithm resolved for " + algorithm);
    }

    @Test
    void ps256ResolvesToRsassaPssAndNeverToPkcs1() {
        Signature signature = manager.getSignatureInstance("PS256");

        assertAll("PS256 is RSASSA-PSS, not PKCS#1 v1.5",
                () -> assertEquals("RSASSA-PSS", signature.getAlgorithm(),
                        "PS256 must resolve to RSASSA-PSS"),
                () -> assertFalse(signature.getAlgorithm().matches("SHA\\d+withRSA"),
                        "PS256 must not resolve to a PKCS#1 v1.5 name — a SHA*withRSA instance would "
                                + "produce a signature scheme the PS256 header does not describe, and a "
                                + "'contains RSA' assertion accepts both"));
    }

    @Test
    void getSignatureInstanceUnsupportedAlgorithm() {
        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> manager.getSignatureInstance("UNSUPPORTED"));

        assertEquals(SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM, exception.getEventType(),
                "getSignatureInstance runs per request (JWS and DPoP proof verification), so the"
                        + " refusal must be the declared type, not an unchecked exception");
        assertTrue(exception.getMessage().contains("UNSUPPORTED"),
                "Exception message should mention the rejected algorithm");
    }

    @Test
    void templateCaching() {
        Signature signature1 = manager.getSignatureInstance("ES256");
        Signature signature2 = manager.getSignatureInstance("ES256");
        assertNotSame(signature1, signature2, "Different Signature instances should be returned");
        assertEquals(signature1.getAlgorithm(), signature2.getAlgorithm(),
                "Same algorithm should be used for both instances");
    }

    @Test
    @SuppressWarnings("java:S1612") // Cannot use method reference due to ambiguous get() methods
    void concurrentAccess() {
        int numberOfThreads = 10;
        int operationsPerThread = 20;
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    assertDoesNotThrow(() -> {
                        Signature signature = manager.getSignatureInstance("ES256");
                        assertNotNull(signature);
                        assertEquals("SHA256withECDSA", signature.getAlgorithm());
                    }, "Exception during concurrent access: ");
                }
            });
            futures.add(future);
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        assertDoesNotThrow(() -> allFutures.get(), "Concurrent access should not throw exceptions");
    }

    @Test
    void unsupportedAlgorithmException() {
        String invalidAlgorithm = "INVALID_ALG";

        SignatureTemplateManager.UnsupportedAlgorithmException exception =
                assertThrows(SignatureTemplateManager.UnsupportedAlgorithmException.class, () -> {
                    throw new SignatureTemplateManager.UnsupportedAlgorithmException(
                            "Test exception for: " + invalidAlgorithm,
                            new NoSuchAlgorithmException("Mock exception"));
                });

        assertNotNull(exception.getCause(), "Exception should have a cause");
        assertTrue(exception.getMessage().contains("Test exception for: INVALID_ALG"),
                "Exception message should contain algorithm name");
    }
}