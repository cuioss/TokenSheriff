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
package de.cuioss.sheriff.token.client.token;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reuse-detection / revoke-on-reuse tests for {@link RefreshTokenFamily} ({@code CLIENT-5}) —
 * deliverable 4.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("RefreshTokenFamily revoke-on-reuse")
class RotationReuseDetectionTest {

    @Test
    @DisplayName("Should start with the initial token active and not revoked")
    void shouldStartActive() {
        String initial = Generators.letterStrings(20, 40).next();

        var family = new RefreshTokenFamily(initial);

        assertAll("initial state",
                () -> assertFalse(family.isRevoked(), "a fresh family must not be revoked"),
                () -> assertEquals(initial, family.currentToken(), "the initial token must be current"));
    }

    @Test
    @DisplayName("Should advance the current token across a successful rotation")
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
    @DisplayName("Should revoke the family and fail closed when a superseded token is replayed")
    void shouldRevokeOnReuse() {
        String initial = Generators.letterStrings(20, 40).next();
        String next = Generators.letterStrings(20, 40).next();
        String attackerNext = Generators.letterStrings(20, 40).next();
        var family = new RefreshTokenFamily(initial);
        family.rotate(initial, next);

        assertThrows(IllegalStateException.class, () -> family.rotate(initial, attackerNext),
                "replaying the superseded token must fail closed");
        String furtherSuccessor = Generators.letterStrings(20, 40).next();
        assertAll("post-reuse state",
                () -> assertTrue(family.isRevoked(), "reuse must revoke the family"),
                () -> assertThrows(IllegalStateException.class, family::currentToken,
                        "a revoked family must not expose a current token"),
                () -> assertThrows(IllegalStateException.class,
                        () -> family.rotate(next, furtherSuccessor),
                        "a revoked family must reject all further rotations"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Refresh token reuse detected");
    }

    @Test
    @DisplayName("Should revoke the family when an unknown token is presented")
    void shouldRevokeOnUnknownToken() {
        String initial = Generators.letterStrings(20, 40).next();
        String unknown = Generators.letterStrings(20, 40).next();
        String next = Generators.letterStrings(20, 40).next();
        var family = new RefreshTokenFamily(initial);

        assertThrows(IllegalStateException.class, () -> family.rotate(unknown, next),
                "presenting a token that is not current must fail closed");
        assertTrue(family.isRevoked(), "an unknown token must revoke the family");
    }

    @Test
    @DisplayName("Should reject a rotation whose successor equals the presented token")
    void shouldRejectNonRotatingSuccessor() {
        String initial = Generators.letterStrings(20, 40).next();
        var family = new RefreshTokenFamily(initial);

        assertThrows(IllegalArgumentException.class, () -> family.rotate(initial, initial),
                "the successor must differ from the presented token");
    }

    @Test
    @DisplayName("Should reject null or blank tokens")
    void shouldRejectInvalidTokens() {
        var familyForNullRotation = new RefreshTokenFamily(Generators.letterStrings(20, 40).next());
        var successor = Generators.letterStrings(20, 40).next();
        var familyForBlankSuccessor = new RefreshTokenFamily(Generators.letterStrings(20, 40).next());
        var presented = Generators.letterStrings(20, 40).next();
        assertAll("token validation",
                () -> assertThrows(NullPointerException.class, () -> new RefreshTokenFamily(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new RefreshTokenFamily("  ")),
                () -> assertThrows(NullPointerException.class,
                        () -> familyForNullRotation.rotate(null, successor)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> familyForBlankSuccessor.rotate(presented, "  ")));
    }
}
