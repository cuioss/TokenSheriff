/*
 * Copyright © 2022 CUI-OpenSource-Software (info@cuioss.de)
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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mechanics + fail-closed unit tests for {@link DpopProofGenerator} ({@code CLIENT-11}, RFC 9449) —
 * deliverable 8.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("DpopProofGenerator DPoP proof + replay defence")
class DpopProofGeneratorTest {

    private static final String HTM = "POST";
    private static final String HTU = "https://as.example.com/realms/demo/protocol/openid-connect/token";

    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @Test
    @DisplayName("Should build a signed dpop+jwt proof carrying the htm/htu/jti/iat claims and embedded JWK")
    void shouldBuildSignedProof() {
        var proofGenerator = new DpopProofGenerator(keyPair, "RS256");

        String proof = proofGenerator.generateProof(HTM, HTU);

        String[] segments = proof.split("\\.");
        assertEquals(3, segments.length, "a DPoP proof is a three-segment compact JWT");
        String header = decode(segments[0]);
        String payload = decode(segments[1]);
        assertAll("proof structure",
                () -> assertTrue(header.contains("\"typ\":\"dpop+jwt\""), "header carries the dpop+jwt typ"),
                () -> assertTrue(header.contains("\"alg\":\"RS256\""), "header carries the signing algorithm"),
                () -> assertTrue(header.contains("\"jwk\":"), "header embeds the proof public JWK"),
                () -> assertTrue(payload.contains("\"htm\":\"" + HTM + "\""), "payload binds the HTTP method"),
                () -> assertTrue(payload.contains("\"htu\":\"" + HTU + "\""), "payload binds the target URI"),
                () -> assertTrue(payload.contains("\"jti\":"), "payload carries a proof identifier"),
                () -> assertTrue(payload.contains("\"iat\":"), "payload carries the issue time"),
                () -> assertTrue(signatureVerifies(segments), "the proof is signed by the proof key"));
    }

    @Test
    @DisplayName("Should mint a distinct jti on every proof so no two proofs are replayable copies")
    void shouldMintDistinctJtiPerProof() {
        var proofGenerator = new DpopProofGenerator(keyPair, "RS256");
        Set<String> seenJtis = new HashSet<>();

        for (int i = 0; i < 25; i++) {
            String payload = decode(proofGenerator.generateProof(HTM, HTU).split("\\.")[1]);
            assertTrue(seenJtis.add(extractJti(payload)), "each proof must carry a fresh, previously-unseen jti");
        }
    }

    @Test
    @DisplayName("Should fail closed and log when the jti source repeats an identifier (replay defence)")
    void shouldRejectJtiReuse() {
        String fixedJti = "static-jti-value";
        var proofGenerator = new DpopProofGenerator(keyPair, "RS256", () -> fixedJti);

        String first = proofGenerator.generateProof(HTM, HTU);
        assertNotEquals(null, first, "the first proof with a given jti is emitted");
        assertThrows(IllegalStateException.class, () -> proofGenerator.generateProof(HTM, HTU),
                "re-emitting a proof with the same jti would be replayable and must fail closed");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "DPoP proof 'jti' reuse detected");
    }

    @Test
    @DisplayName("Should expose a stable RFC 7638 jkt thumbprint tied to the proof key")
    void shouldExposeStableJkt() {
        var first = new DpopProofGenerator(keyPair, "RS256");
        var second = new DpopProofGenerator(keyPair, "RS256");

        assertAll("jkt",
                () -> assertTrue(first.jkt() != null && !first.jkt().isBlank(), "jkt is present"),
                () -> assertEquals(first.jkt(), second.jkt(),
                        "the same proof key yields the same RFC 7638 thumbprint"),
                () -> assertTrue(first.jkt().matches("[A-Za-z0-9_-]+"), "jkt is base64url without padding"));
    }

    @Test
    @DisplayName("Should reject a non-RSA key pair and an unsupported signing algorithm")
    void shouldRejectInvalidConfiguration() throws Exception {
        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(256);
        KeyPair ecKeyPair = ec.generateKeyPair();

        assertAll("configuration guards",
                () -> assertThrows(IllegalArgumentException.class, () -> new DpopProofGenerator(ecKeyPair, "RS256"),
                        "a non-RSA proof key is rejected"),
                () -> assertThrows(IllegalArgumentException.class, () -> new DpopProofGenerator(keyPair, "HS256"),
                        "an unsupported signing algorithm is rejected"));
    }

    @Test
    @DisplayName("Should reject a blank htm or htu")
    void shouldRejectBlankParameters() {
        var proofGenerator = new DpopProofGenerator(keyPair, "RS256");

        assertAll("parameter guards",
                () -> assertThrows(IllegalArgumentException.class, () -> proofGenerator.generateProof("  ", HTU)),
                () -> assertThrows(IllegalArgumentException.class, () -> proofGenerator.generateProof(HTM, "  ")));
    }

    private boolean signatureVerifies(String[] segments) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(keyPair.getPublic());
            verifier.update((segments[0] + "." + segments[1]).getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getUrlDecoder().decode(segments[2]));
        } /*~~(TODO: Catch specific not Exception. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe)~~>*/catch (Exception e) {
            return false;
        }
    }

    private static String extractJti(String payload) {
        int start = payload.indexOf("\"jti\":\"") + "\"jti\":\"".length();
        int end = payload.indexOf('"', start);
        return payload.substring(start, end);
    }

    private static String decode(String segment) {
        return new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8);
    }
}
