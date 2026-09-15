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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

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
