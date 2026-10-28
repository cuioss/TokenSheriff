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
package de.cuioss.sheriff.token.client.token;

import de.cuioss.sheriff.token.commons.error.ClientProtocolException;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rotation and reuse-detection contract of the {@link RefreshTokenFamily} primitive, exercised in
 * isolation from any transport, store or lifecycle wiring.
 * <p>
 * A family advances its current token across a legitimate rotation, fails closed and revokes itself
 * when a superseded token is replayed against it, and rejects malformed or non-rotating inputs at
 * construction and on rotation. Because the primitive is what the wired path delegates its reuse
 * decision to, pinning it here keeps the decision testable without standing up a token endpoint.
 * <p>
 * The wired end-to-end contract built on top of this primitive — RFC 7009 revoke-on-reuse, the
 * single-flight collapse, and the OIDC Core §12.2 refreshed-ID-token consistency check — lives in
 * {@link RefreshAdversarialTest}, which drives the assembled lifecycle manager against a mock token
 * endpoint using the shared {@link RefreshTestSupport} fixture.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("RefreshTokenFamily rotation and reuse primitive")
class RotationReuseDetectionTest {

    @Test
    @DisplayName("Should advance the current token across a successful family rotation")
    void shouldAdvanceOnRotation() {
        String initial = Generators.letterStrings(20, 40).next();
        String next = Generators.letterStrings(20, 40).next();
        var family = new RefreshTokenFamily(initial);

        family.rotate(initial, next);

        assertAll("after rotation",
                () -> assertFalse(family.isRevoked(), "a valid rotation must not revoke the family"),
                () -> assertEquals(next, family.currentToken(), "the rotated token must become current"));
    }

    @Test
    @DisplayName("Should revoke the family and fail closed when a superseded token is replayed against it")
    void shouldRevokeFamilyOnReuse() {
        String initial = Generators.letterStrings(20, 40).next();
        String next = Generators.letterStrings(20, 40).next();
        String attackerNext = Generators.letterStrings(20, 40).next();
        var family = new RefreshTokenFamily(initial);
        family.rotate(initial, next);

        assertThrows(ClientProtocolException.class, () -> family.rotate(initial, attackerNext),
                "replaying the superseded token must fail closed");
        assertAll("post-reuse state",
                () -> assertTrue(family.isRevoked(), "reuse must revoke the family"),
                () -> assertThrows(ClientProtocolException.class, family::currentToken,
                        "a revoked family must not expose a current token"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Refresh token reuse detected");
    }

    @Test
    @DisplayName("Should raise the revoked-family type on reuse, naming the successor the replay was issued")
    void shouldRaiseRevokedFamilyTypeOnReuse() {
        String initial = Generators.letterStrings(20, 40).next();
        String next = Generators.letterStrings(20, 40).next();
        String replaySuccessor = Generators.letterStrings(20, 40).next();
        var family = new RefreshTokenFamily(initial);
        family.rotate(initial, next);

        var thrown = assertThrows(RefreshTokenFamilyRevokedException.class,
                () -> family.rotate(initial, replaySuccessor));

        assertAll("the reuse refusal records that the server already rotated",
                () -> assertInstanceOf(ClientProtocolException.class, thrown,
                        "existing catch (ClientProtocolException) blocks must still catch it"),
                () -> assertTrue(thrown.isRaisedOnRotation()),
                () -> assertEquals(Optional.of(replaySuccessor), thrown.getRotatedRefreshToken(),
                        "the successor the AS issued is the live credential to revoke"),
                () -> assertFalse(thrown.getMessage().contains(replaySuccessor),
                        "the message must not carry token material"),
                () -> assertTrue(family.isRevoked()));
    }

    @Test
    @DisplayName("Should raise the revoked-family type on a rotation against an already-revoked family")
    void shouldRaiseRevokedFamilyTypeOnRotationAfterRevocation() {
        String initial = Generators.letterStrings(20, 40).next();
        String next = Generators.letterStrings(20, 40).next();
        String laterSuccessor = Generators.letterStrings(20, 40).next();
        var family = new RefreshTokenFamily(initial);
        family.rotate(initial, next);
        assertThrows(RefreshTokenFamilyRevokedException.class,
                () -> family.rotate(initial, Generators.letterStrings(20, 40).next()));

        // The current token is presented here, so only the revoked state — not reuse — can refuse it.
        var thrown = assertThrows(RefreshTokenFamilyRevokedException.class,
                () -> family.rotate(next, laterSuccessor));

        assertAll("a revoked family refuses even its current token, still naming the successor",
                () -> assertTrue(thrown.isRaisedOnRotation()),
                () -> assertEquals(Optional.of(laterSuccessor), thrown.getRotatedRefreshToken()),
                () -> assertEquals("refresh token family is revoked", thrown.getMessage()));
    }

    @Test
    @DisplayName("Should raise the revoked-family type from currentToken with no successor to revoke")
    void shouldRaiseRevokedFamilyTypeFromCurrentToken() {
        String initial = Generators.letterStrings(20, 40).next();
        String next = Generators.letterStrings(20, 40).next();
        var family = new RefreshTokenFamily(initial);
        family.rotate(initial, next);
        assertThrows(RefreshTokenFamilyRevokedException.class,
                () -> family.rotate(initial, Generators.letterStrings(20, 40).next()));

        var thrown = assertThrows(RefreshTokenFamilyRevokedException.class, family::currentToken);

        assertAll("no exchange took place, so nothing was redeemed",
                () -> assertFalse(thrown.isRaisedOnRotation()),
                () -> assertEquals(Optional.empty(), thrown.getRotatedRefreshToken()));
    }

    @Test
    @DisplayName("Should keep the rotation marker but drop the successor across serialization")
    void shouldNotSerializeTheSuccessor() throws Exception {
        String successor = Generators.letterStrings(20, 40).next();
        var refusal = new RefreshTokenFamilyRevokedException("refresh token family is revoked", successor);

        byte[] bytes = serialize(refusal);
        var roundTripped = (RefreshTokenFamilyRevokedException) deserialize(bytes);

        assertAll("the successor is a usable credential and must not ride the throwable",
                () -> assertFalse(new String(bytes, StandardCharsets.ISO_8859_1).contains(successor),
                        "the serialized refusal must not contain the rotated refresh token"),
                () -> assertTrue(roundTripped.isRaisedOnRotation(),
                        "a deserialized rotation refusal must still read as post-redemption"),
                () -> assertEquals(Optional.empty(), roundTripped.getRotatedRefreshToken()));
    }

    @Test
    @DisplayName("Should reject a null successor on the rotation refusal")
    void shouldRejectNullSuccessor() {
        assertThrows(NullPointerException.class,
                () -> new RefreshTokenFamilyRevokedException("refresh token family is revoked", null));
    }

    @Test
    @DisplayName("Should reject invalid tokens and non-rotating successors on the family primitive")
    void shouldRejectInvalidTokens() {
        var family = new RefreshTokenFamily(Generators.letterStrings(20, 40).next());
        var presented = Generators.letterStrings(20, 40).next();
        var freshFamily = new RefreshTokenFamily(presented);
        var rotationSuccessor = Generators.letterStrings(20, 40).next();
        assertAll("token validation",
                () -> assertThrows(NullPointerException.class, () -> new RefreshTokenFamily(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new RefreshTokenFamily("  ")),
                () -> assertThrows(NullPointerException.class,
                        () -> family.rotate(null, rotationSuccessor)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> freshFamily.rotate(presented, presented)));
    }

    /**
     * The superseded-token history and the reseed contract: a family remembers the tokens it superseded
     * (bounded, oldest dropped first), and a successor built for a new grant carries that memory forward
     * so a replay of any token the predecessor knew is still reuse.
     */
    @Nested
    @DisplayName("Superseded history and reseeding")
    class HistoryAndReseed {

        /** The fixed history bound documented on {@link RefreshTokenFamily}. */
        private static final int HISTORY_BOUND = 16;

        @Test
        @DisplayName("Should report the current and every superseded token after a rotation chain")
        void shouldTrackCurrentAndSupersededAcrossRotationChain() {
            List<String> tokens = distinctTokens(3);
            String unknown = Generators.letterStrings(20, 40).next() + "-unknown";
            var family = new RefreshTokenFamily(tokens.get(0));

            family.rotate(tokens.get(0), tokens.get(1));
            family.rotate(tokens.get(1), tokens.get(2));

            assertAll("family state after two rotations",
                    () -> assertTrue(family.isCurrent(tokens.get(2)), "the last rotated token is current"),
                    () -> assertFalse(family.isCurrent(tokens.get(0)), "a superseded token is not current"),
                    () -> assertTrue(family.isSuperseded(tokens.get(0)), "the initial token is superseded"),
                    () -> assertTrue(family.isSuperseded(tokens.get(1)), "the middle token is superseded"),
                    () -> assertFalse(family.isSuperseded(tokens.get(2)), "the current token is not superseded"),
                    () -> assertFalse(family.isSuperseded(unknown), "an unknown token is not superseded"),
                    () -> assertFalse(family.isCurrent(unknown), "an unknown token is not current"));
        }

        @Test
        @DisplayName("Should reject null tokens on the current and superseded queries")
        void shouldRejectNullOnQueries() {
            var family = new RefreshTokenFamily(Generators.letterStrings(20, 40).next());

            assertAll("null queries",
                    () -> assertThrows(NullPointerException.class, () -> family.isCurrent(null)),
                    () -> assertThrows(NullPointerException.class, () -> family.isSuperseded(null)));
        }

        @Test
        @DisplayName("Should carry the old current token and its history into the reseeded successor")
        void shouldCarryHistoryIntoReseededSuccessor() {
            List<String> tokens = distinctTokens(4);
            String newGrant = tokens.get(2);
            String successorRotation = tokens.get(3);
            var family = new RefreshTokenFamily(tokens.get(0));
            family.rotate(tokens.get(0), tokens.get(1));

            var successor = family.reseededWith(newGrant);

            assertAll("successor state",
                    () -> assertTrue(successor.isCurrent(newGrant), "the new grant is the successor's current"),
                    () -> assertTrue(successor.isSuperseded(tokens.get(1)),
                            "the predecessor's current token is superseded in the successor"),
                    () -> assertTrue(successor.isSuperseded(tokens.get(0)),
                            "the predecessor's history is carried forward"),
                    () -> assertFalse(successor.isRevoked(), "the successor starts non-revoked"),
                    () -> assertTrue(family.isCurrent(tokens.get(1)), "the predecessor is left untouched"),
                    () -> assertFalse(family.isSuperseded(newGrant),
                            "the predecessor does not learn the successor's token"));

            successor.rotate(newGrant, successorRotation);

            assertAll("successor rotates from the new grant",
                    () -> assertEquals(successorRotation, successor.currentToken()),
                    () -> assertFalse(successor.isRevoked()));
        }

        @Test
        @DisplayName("Should treat a replay of a predecessor-family token as reuse on the successor")
        void shouldDetectPredecessorReplayOnSuccessor() {
            List<String> tokens = distinctTokens(4);
            var family = new RefreshTokenFamily(tokens.get(0));
            family.rotate(tokens.get(0), tokens.get(1));
            var successor = family.reseededWith(tokens.get(2));

            var thrown = assertThrows(RefreshTokenFamilyRevokedException.class,
                    () -> successor.rotate(tokens.get(0), tokens.get(3)),
                    "a token the predecessor superseded must be reuse on the successor");

            assertAll("the successor fails closed",
                    () -> assertTrue(thrown.isRaisedOnRotation()),
                    () -> assertTrue(successor.isRevoked()),
                    () -> assertFalse(family.isRevoked(), "revoking the successor leaves the predecessor alone"));
        }

        @Test
        @DisplayName("Should refuse to reseed with a token the family already knows or a blank token")
        void shouldRefuseReseedWithKnownToken() {
            List<String> tokens = distinctTokens(2);
            var family = new RefreshTokenFamily(tokens.get(0));
            family.rotate(tokens.get(0), tokens.get(1));

            assertAll("a known token is never a new grant",
                    () -> assertThrows(IllegalArgumentException.class, () -> family.reseededWith(tokens.get(1)),
                            "the current token must not reseed"),
                    () -> assertThrows(IllegalArgumentException.class, () -> family.reseededWith(tokens.get(0)),
                            "a superseded token must not reseed"),
                    () -> assertThrows(IllegalArgumentException.class, () -> family.reseededWith("  ")),
                    () -> assertThrows(NullPointerException.class, () -> family.reseededWith(null)),
                    () -> assertTrue(family.isCurrent(tokens.get(1)), "a refused reseed leaves the family intact"));
        }

        @Test
        @DisplayName("Should keep exactly the newest sixteen superseded tokens, dropping the oldest first")
        void shouldBoundHistoryDroppingOldestFirst() {
            List<String> tokens = distinctTokens(HISTORY_BOUND + 2);
            var family = new RefreshTokenFamily(tokens.get(0));
            for (int i = 0; i < HISTORY_BOUND; i++) {
                family.rotate(tokens.get(i), tokens.get(i + 1));
            }
            assertTrue(family.isSuperseded(tokens.get(0)),
                    "at exactly the bound every superseded token is still remembered");

            family.rotate(tokens.get(HISTORY_BOUND), tokens.get(HISTORY_BOUND + 1));

            assertAll("one past the bound",
                    () -> assertFalse(family.isSuperseded(tokens.get(0)), "the oldest digest is dropped first"),
                    () -> assertTrue(family.isSuperseded(tokens.get(1)), "the next-oldest digest is kept"),
                    () -> assertTrue(family.isSuperseded(tokens.get(HISTORY_BOUND)),
                            "the newest superseded digest is kept"),
                    () -> assertTrue(family.isCurrent(tokens.get(HISTORY_BOUND + 1))));
        }

        @Test
        @DisplayName("Should bound the reseeded successor's history, dropping the predecessor's oldest token")
        void shouldBoundReseededHistory() {
            List<String> tokens = distinctTokens(HISTORY_BOUND + 2);
            var family = new RefreshTokenFamily(tokens.get(0));
            for (int i = 0; i < HISTORY_BOUND; i++) {
                family.rotate(tokens.get(i), tokens.get(i + 1));
            }

            var successor = family.reseededWith(tokens.get(HISTORY_BOUND + 1));

            assertAll("the successor's inherited history is bounded",
                    () -> assertFalse(successor.isSuperseded(tokens.get(0)),
                            "the predecessor's oldest token ages out when its current token is appended"),
                    () -> assertTrue(successor.isSuperseded(tokens.get(1))),
                    () -> assertTrue(successor.isSuperseded(tokens.get(HISTORY_BOUND)),
                            "the predecessor's current token is remembered"),
                    () -> assertTrue(family.isSuperseded(tokens.get(0)),
                            "the predecessor's own history is unchanged"));
        }

        @Test
        @DisplayName("Should let a revoked family's successor accept the new grant")
        void shouldReseedRevokedFamily() {
            List<String> tokens = distinctTokens(5);
            var family = new RefreshTokenFamily(tokens.get(0));
            family.rotate(tokens.get(0), tokens.get(1));
            assertThrows(RefreshTokenFamilyRevokedException.class, () -> family.rotate(tokens.get(0), tokens.get(2)));
            assertTrue(family.isCurrent(tokens.get(1)),
                    "a revoked family still reports the token it was revoked on as current");

            var successor = family.reseededWith(tokens.get(3));
            successor.rotate(tokens.get(3), tokens.get(4));

            assertAll("a fresh login recovers the session",
                    () -> assertTrue(family.isRevoked(), "the predecessor stays revoked"),
                    () -> assertFalse(successor.isRevoked()),
                    () -> assertEquals(tokens.get(4), successor.currentToken()),
                    () -> assertTrue(successor.isSuperseded(tokens.get(1))));
        }

        /**
         * Distinct generated tokens: one generated stem with an index suffix, so no two collide however
         * the generator draws.
         */
        private List<String> distinctTokens(int count) {
            String stem = Generators.letterStrings(20, 40).next();
            return IntStream.range(0, count).mapToObj(i -> stem + "-" + i).toList();
        }
    }

    private static byte[] serialize(Object value) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }
}
