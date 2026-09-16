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
package de.cuioss.sheriff.token.validation.util;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link Sha256Util}, the module's single translation point for an unavailable digest algorithm.
 * <p>
 * The translation matters beyond the digest itself: every caller sits on the per-request validation
 * path (access-token cache keying, DPoP {@code ath} and thumbprint hashing, ECDH-ES key derivation),
 * and an unchecked failure there would escape {@code TokenValidator}'s declared contract — which is
 * what lets a burned refresh token be classified as pre-redemption on the client side.
 */
@DisplayName("Sha256Util: the digest, and the declared refusal when the algorithm is missing")
class Sha256UtilTest {

    /** RFC 6234's SHA-256 of "abc", written out literally rather than recomputed with the code under test. */
    private static final String SHA256_OF_ABC =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    @DisplayName("digests to the published SHA-256 value")
    void shouldComputeThePublishedDigest() {
        byte[] digest = Sha256Util.digest("abc".getBytes(StandardCharsets.US_ASCII));

        assertEquals(SHA256_OF_ABC, HexFormat.of().formatHex(digest),
                "Sha256Util must produce the SHA-256 value RFC 6234 publishes for \"abc\"");
    }

    @Test
    @DisplayName("hands out a fresh digest each time, so incremental callers cannot share state")
    void shouldReturnAFreshDigestPerCall() {
        MessageDigest first = Sha256Util.newDigest();
        MessageDigest second = Sha256Util.newDigest();

        assertNotNull(first);
        assertEquals("SHA-256", first.getAlgorithm());
        assertNotSame(first, second,
                "ConcatKdf digests incrementally across derivation rounds, so a shared instance would"
                        + " let one caller's buffered input leak into another's");
    }

    @Test
    @DisplayName("refuses a missing algorithm with the declared type, not an unchecked exception")
    void shouldRefuseAMissingAlgorithmWithTheDeclaredType() {
        // SHA-256 is mandated by the Java Security Standard Algorithm Names specification, so the
        // broken-JRE branch cannot be reached through newDigest(). Asking for an algorithm no JRE
        // provides drives the same translation on the real code path instead of taking it on trust.
        TokenValidationException refusal = assertThrows(TokenValidationException.class,
                () -> Sha256Util.newDigest("SHA-256-THAT-NO-JRE-PROVIDES"));

        assertEquals(SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM, refusal.getEventType());
        assertEquals("SHA-256-THAT-NO-JRE-PROVIDES algorithm not available", refusal.getMessage());
        assertNotNull(refusal.getCause(), "the original NoSuchAlgorithmException must be preserved as cause");
    }
}
