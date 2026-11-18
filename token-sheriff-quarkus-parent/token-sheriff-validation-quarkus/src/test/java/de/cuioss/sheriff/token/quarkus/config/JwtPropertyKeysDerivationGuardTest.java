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
package de.cuioss.sheriff.token.quarkus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the fail-closed guard of {@link JwtPropertyKeys#declaredKeysUnder(Class, String, Set, int)}: an empty
 * or short derivation and a {@code public static final String} field that reads {@code null} are refused
 * with an {@link IllegalStateException}, each next to a positive control that derives normally.
 */
@DisplayName("JwtPropertyKeys: fail-closed key derivation")
class JwtPropertyKeysDerivationGuardTest {

    private static final String FIXTURE_BASE = "fixture.base.";

    /**
     * Declares no key under {@link #FIXTURE_BASE}: only the base itself and a key outside it.
     */
    static final class NoKeyFixture {

        public static final String BASE = FIXTURE_BASE;
        public static final String OUTSIDE = "fixture.other.key";

        private NoKeyFixture() {
        }
    }

    /**
     * Declares exactly two keys under {@link #FIXTURE_BASE}.
     */
    static final class TwoKeyFixture {

        public static final String BASE = FIXTURE_BASE;
        public static final String SECOND = FIXTURE_BASE + "second";
        public static final String FIRST = FIXTURE_BASE + "first";

        private TwoKeyFixture() {
        }
    }

    /**
     * Derives its list in its own static initializer and declares a non-constant key after it, so that key
     * still reads {@code null} while the list is derived.
     */
    static final class LateNonConstantFixture {

        public static final String BASE = FIXTURE_BASE;
        public static final String FIRST = FIXTURE_BASE + "first";
        public static final List<String> DERIVED = JwtPropertyKeys.declaredKeysUnder(
                LateNonConstantFixture.class, FIXTURE_BASE, Set.of(), 1);
        @SuppressWarnings("unused") // read reflectively: not a compile-time constant, declared after DERIVED
        public static final String LATE = String.valueOf(FIXTURE_BASE + "late");

        private LateNonConstantFixture() {
        }
    }

    /**
     * Same shape as {@link LateNonConstantFixture}, but the non-constant key is declared before the list,
     * so it is initialized when the list is derived.
     */
    static final class EarlyNonConstantFixture {

        public static final String BASE = FIXTURE_BASE;
        public static final String FIRST = FIXTURE_BASE + "first";
        public static final String EARLY = String.valueOf(FIXTURE_BASE + "early");
        public static final List<String> DERIVED = JwtPropertyKeys.declaredKeysUnder(
                EarlyNonConstantFixture.class, FIXTURE_BASE, Set.of(), 1);

        private EarlyNonConstantFixture() {
        }
    }

    @Nested
    @DisplayName("minimum number of keys")
    class MinimumKeys {

        @Test
        @DisplayName("should refuse an empty derivation, naming the class and the base")
        void shouldRefuseEmptyDerivation() {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> JwtPropertyKeys.declaredKeysUnder(NoKeyFixture.class, FIXTURE_BASE, Set.of(), 1));

            assertAll("refusal message",
                    () -> assertTrue(exception.getMessage().contains(NoKeyFixture.class.getName()),
                            "names the declaring class: " + exception.getMessage()),
                    () -> assertTrue(exception.getMessage().contains(FIXTURE_BASE),
                            "names the base: " + exception.getMessage()),
                    () -> assertFalse(exception.getMessage().contains(NoKeyFixture.OUTSIDE),
                            "names no value: " + exception.getMessage()));
        }

        @Test
        @DisplayName("should refuse an empty derivation through the overload without an explicit minimum")
        void shouldRefuseEmptyDerivationWithDefaultMinimum() {
            assertThrows(IllegalStateException.class,
                    () -> JwtPropertyKeys.declaredKeysUnder(NoKeyFixture.class, FIXTURE_BASE, Set.of()));
        }

        @Test
        @DisplayName("should refuse a derivation shorter than the expected minimum, naming no value")
        void shouldRefuseShortDerivation() {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> JwtPropertyKeys.declaredKeysUnder(TwoKeyFixture.class, FIXTURE_BASE, Set.of(), 3));

            assertAll("refusal message",
                    () -> assertTrue(exception.getMessage().contains(TwoKeyFixture.class.getName()),
                            "names the declaring class: " + exception.getMessage()),
                    () -> assertTrue(exception.getMessage().contains(FIXTURE_BASE),
                            "names the base: " + exception.getMessage()),
                    () -> assertFalse(exception.getMessage().contains(TwoKeyFixture.FIRST),
                            "names no derived value: " + exception.getMessage()),
                    () -> assertFalse(exception.getMessage().contains(TwoKeyFixture.SECOND),
                            "names no derived value: " + exception.getMessage()));
        }

        @Test
        @DisplayName("should refuse a derivation that falls short only because of an excluded key")
        void shouldRefuseDerivationShortAfterExclusion() {
            Set<String> excluded = Set.of(TwoKeyFixture.FIRST);

            assertThrows(IllegalStateException.class,
                    () -> JwtPropertyKeys.declaredKeysUnder(TwoKeyFixture.class, FIXTURE_BASE, excluded, 2));
        }

        @Test
        @DisplayName("positive control: a derivation that meets the minimum returns the keys, sorted")
        void shouldDeriveWhenMinimumIsMet() {
            List<String> derived = JwtPropertyKeys.declaredKeysUnder(TwoKeyFixture.class, FIXTURE_BASE, Set.of(),
                    2);

            assertEquals(List.of(TwoKeyFixture.FIRST, TwoKeyFixture.SECOND), derived,
                    "both keys are derived in natural String order ('first' before 'second')");
        }

        @Test
        @DisplayName("positive control: the production list meets its minimum of four")
        void shouldDeriveProductionList() {
            assertEquals(
                    JwtPropertyKeys.ISSUERS.DPOP_SUB_PROPERTIES, List.of(
                            JwtPropertyKeys.ISSUERS.DPOP_NONCE_CACHE_SIZE,
                            JwtPropertyKeys.ISSUERS.DPOP_NONCE_CACHE_TTL_SECONDS,
                            JwtPropertyKeys.ISSUERS.DPOP_PROOF_MAX_AGE_SECONDS,
                            JwtPropertyKeys.ISSUERS.DPOP_REQUIRED),
                    "the four dpop sub-properties are derived in natural String order");
        }
    }

    @Nested
    @DisplayName("fields that read null")
    class NullFields {

        @Test
        @DisplayName("should refuse a non-constant key declared after the derived list, naming the field")
        void shouldRefuseLateNonConstantField() {
            ExceptionInInitializerError error = assertThrows(ExceptionInInitializerError.class,
                    () -> assertNotNull(LateNonConstantFixture.DERIVED));

            IllegalStateException cause = assertInstanceOf(IllegalStateException.class, error.getCause(),
                    "the class initialization fails with the refusal");
            assertAll("refusal message",
                    () -> assertTrue(cause.getMessage().contains("LATE"),
                            "names the field: " + cause.getMessage()),
                    () -> assertTrue(cause.getMessage().contains("not a compile-time constant"),
                            "states the reason: " + cause.getMessage()),
                    () -> assertFalse(cause.getMessage().contains(FIXTURE_BASE),
                            "names no value: " + cause.getMessage()));
        }

        @Test
        @DisplayName("positive control: a non-constant key declared before the derived list is derived")
        void shouldDeriveEarlyNonConstantField() {
            assertEquals(
                    EarlyNonConstantFixture.DERIVED, List.of(EarlyNonConstantFixture.EARLY, EarlyNonConstantFixture.FIRST),
                    "the early non-constant key is initialized and derived ('early' before 'first')");
        }
    }
}
