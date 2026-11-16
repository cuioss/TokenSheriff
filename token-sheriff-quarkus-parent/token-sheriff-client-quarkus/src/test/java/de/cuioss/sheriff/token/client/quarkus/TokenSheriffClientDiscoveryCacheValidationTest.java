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
package de.cuioss.sheriff.token.client.quarkus;

import de.cuioss.sheriff.token.client.discovery.CachingDiscoveryResolver;
import de.cuioss.sheriff.token.client.quarkus.config.ClientConfigMapper;
import de.cuioss.sheriff.token.client.quarkus.config.ClientRuntimeConfig;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the value checks of the discovery cache startup validation (ADR-0026): every invalid
 * {@code sheriff.client.discovery-cache.*} value fails
 * {@link TokenSheriffClientProducer#validateDiscoveryCacheOnStartup(StartupEvent)} with an
 * {@link IllegalStateException} naming the property that carries it, the same value on a disabled
 * cache fails as an orphan, and values at the bound pass. It also pins
 * {@link ClientConfigMapper#requireDiscoveryCacheEnabled(Config)}, the check shared by the deployment
 * injection check and the producer's first-use guard.
 * <p>
 * The rules come from {@code CachingDiscoveryResolver.checkSettings}: a positive TTL, no negative
 * duration, at most 365 days for the TTL, the grace period and the minimum refresh interval, and a
 * jitter smaller than the TTL. The value of every row is chosen to break exactly one of them.
 */
@EnableTestLogger
@DisplayName("TokenSheriffClientProducer discovery cache value validation")
class TokenSheriffClientDiscoveryCacheValidationTest {

    /** The TTL of the enabled configuration a non-TTL row is set on. */
    private static final String VALID_TTL = "PT5M";

    /** The upper bound of the TTL, the grace period and the minimum refresh interval. */
    private static final String AT_BOUND = "P365D";

    /** One day past the bound. */
    private static final String ABOVE_BOUND = "P366D";

    private static final String UNPARSEABLE = "not-a-duration";

    private static final String NEGATIVE = "-PT1M";

    private static Config configOf(Map<String, String> properties) {
        return new SmallRyeConfigBuilder().withDefaultValues(properties).build();
    }

    /**
     * An enabled cache with a valid TTL, carrying {@code value} under {@code property}; for the TTL
     * row the value replaces the valid TTL.
     */
    private static Map<String, String> enabledWith(String property, String value) {
        Map<String, String> properties = new HashMap<>();
        properties.put(ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED, "true");
        properties.put(ClientRuntimeConfig.DISCOVERY_CACHE_TTL, VALID_TTL);
        properties.put(property, value);
        return properties;
    }

    private static void startUp(Config config) {
        new TokenSheriffClientProducer(config).validateDiscoveryCacheOnStartup(new StartupEvent());
    }

    /**
     * One row per invalid value: the property, the value and the rule it breaks.
     */
    static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of(ClientRuntimeConfig.DISCOVERY_CACHE_TTL, "PT0S", "zero ttl"),
                Arguments.of(ClientRuntimeConfig.DISCOVERY_CACHE_TTL, NEGATIVE, "negative ttl"),
                Arguments.of(ClientRuntimeConfig.DISCOVERY_CACHE_TTL, UNPARSEABLE, "unparseable ttl"),
                Arguments.of(ClientRuntimeConfig.DISCOVERY_CACHE_TTL, ABOVE_BOUND, "ttl above 365 days"),
                Arguments.of(ClientRuntimeConfig.DISCOVERY_CACHE_GRACE_PERIOD, NEGATIVE, "negative grace period"),
                Arguments.of(ClientRuntimeConfig.DISCOVERY_CACHE_GRACE_PERIOD, UNPARSEABLE,
                        "unparseable grace period"),
                Arguments.of(ClientRuntimeConfig.DISCOVERY_CACHE_GRACE_PERIOD, ABOVE_BOUND,
                        "grace period above 365 days"),
                Arguments.of(ClientRuntimeConfig.DISCOVERY_CACHE_MIN_REFRESH_INTERVAL, NEGATIVE,
                        "negative min refresh interval"),
                Arguments.of(ClientRuntimeConfig.DISCOVERY_CACHE_MIN_REFRESH_INTERVAL, UNPARSEABLE,
                        "unparseable min refresh interval"),
                Arguments.of(ClientRuntimeConfig.DISCOVERY_CACHE_MIN_REFRESH_INTERVAL, ABOVE_BOUND,
                        "min refresh interval above 365 days"),
                Arguments.of(ClientRuntimeConfig.DISCOVERY_CACHE_JITTER, "-PT1S", "negative jitter"),
                Arguments.of(ClientRuntimeConfig.DISCOVERY_CACHE_JITTER, UNPARSEABLE, "unparseable jitter"),
                Arguments.of(ClientRuntimeConfig.DISCOVERY_CACHE_JITTER, VALID_TTL, "jitter equal to ttl"),
                Arguments.of(ClientRuntimeConfig.DISCOVERY_CACHE_JITTER, "PT6M", "jitter above ttl"));
    }

    @Nested
    @DisplayName("Invalid values")
    class InvalidValues {

        @ParameterizedTest(name = "{2}: {0}={1}")
        @MethodSource("de.cuioss.sheriff.token.client.quarkus.TokenSheriffClientDiscoveryCacheValidationTest#invalidValues")
        @DisplayName("Should fail startup naming the property when an enabled cache carries an invalid value")
        void shouldFailStartupNamingInvalidProperty(String property, String value, String rule) {
            Config config = configOf(enabledWith(property, value));

            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> startUp(config),
                    "an enabled cache with a " + rule + " must fail the start, not the first resolution");
            assertTrue(failure.getMessage().contains("'" + property + "'"),
                    "the failure names the property carrying the " + rule + ": " + failure.getMessage());
        }

        @ParameterizedTest(name = "{2}: {0}={1}")
        @MethodSource("de.cuioss.sheriff.token.client.quarkus.TokenSheriffClientDiscoveryCacheValidationTest#invalidValues")
        @DisplayName("Should fail startup as an orphan when a disabled cache carries the same invalid value")
        void shouldFailStartupAsOrphanWhenDisabled(String property, String value, String rule) {
            Config config = configOf(Map.of(property, value));

            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> startUp(config),
                    "a disabled cache with a " + rule + " must fail as an orphan");
            assertAll("orphan failure message",
                    () -> assertTrue(failure.getMessage().contains("'" + property + "' is set but"),
                            "the orphan check, not the value check, refuses it: " + failure.getMessage()),
                    () -> assertTrue(failure.getMessage().contains(ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED),
                            "the failure names the fix: " + failure.getMessage()));
        }

        @Test
        @DisplayName("Should keep the duration parse failure as the cause of an unparseable value")
        void shouldKeepParseFailureAsCause() {
            Config config = configOf(enabledWith(ClientRuntimeConfig.DISCOVERY_CACHE_GRACE_PERIOD, UNPARSEABLE));

            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> startUp(config),
                    "an unparseable grace period must fail the start");
            assertAll("unparseable value failure",
                    () -> assertTrue(failure.getMessage().contains("is not a valid duration"),
                            "the failure says the value is not a duration: " + failure.getMessage()),
                    () -> assertInstanceOf(IllegalArgumentException.class, failure.getCause(),
                            "the config conversion failure is kept as the cause"));
        }

        @Test
        @DisplayName("Should keep the rule refusal as the cause of a value that breaks a rule")
        void shouldKeepRuleRefusalAsCause() {
            Config config = configOf(enabledWith(ClientRuntimeConfig.DISCOVERY_CACHE_MIN_REFRESH_INTERVAL,
                    ABOVE_BOUND));

            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> startUp(config),
                    "a minimum refresh interval above the bound must fail the start");
            assertAll("rule refusal failure",
                    () -> assertInstanceOf(IllegalArgumentException.class, failure.getCause(),
                            "the checkSettings refusal is kept as the cause"),
                    () -> assertTrue(failure.getMessage().contains("minRefreshInterval must be at most 365 days"),
                            "the failure carries the rule that was broken: " + failure.getMessage()));
        }
    }

    /** The property that carries a setting, as the configuration documents it. */
    private static String expectedProperty(CachingDiscoveryResolver.Setting setting) {
        return switch (setting) {
            case TTL -> ClientRuntimeConfig.DISCOVERY_CACHE_TTL;
            case GRACE_PERIOD -> ClientRuntimeConfig.DISCOVERY_CACHE_GRACE_PERIOD;
            case MIN_REFRESH_INTERVAL -> ClientRuntimeConfig.DISCOVERY_CACHE_MIN_REFRESH_INTERVAL;
            case JITTER -> ClientRuntimeConfig.DISCOVERY_CACHE_JITTER;
        };
    }

    /** A value that breaks exactly one rule of the setting on an otherwise valid enabled cache. */
    private static String invalidValueOf(CachingDiscoveryResolver.Setting setting) {
        return switch (setting) {
            case TTL -> "PT0S";
            case GRACE_PERIOD -> NEGATIVE;
            case MIN_REFRESH_INTERVAL -> ABOVE_BOUND;
            case JITTER -> VALID_TTL;
        };
    }

    @Nested
    @DisplayName("Setting mapping")
    class SettingMapping {

        @ParameterizedTest(name = "{0}")
        @EnumSource(CachingDiscoveryResolver.Setting.class)
        @DisplayName("Should name exactly the property of the setting that checkSettings refused")
        void shouldNameExactlyTheRefusedSettingsProperty(CachingDiscoveryResolver.Setting setting) {
            String property = expectedProperty(setting);
            Config config = configOf(enabledWith(property, invalidValueOf(setting)));

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> ClientConfigMapper.validateDiscoveryCache(config),
                    "an invalid " + setting + " must fail the validation");
            CachingDiscoveryResolver.InvalidSettingException refusal = assertInstanceOf(
                    CachingDiscoveryResolver.InvalidSettingException.class, failure.getCause(),
                    "the checkSettings refusal is kept as the cause");
            assertAll(setting + " refusal",
                    () -> assertEquals(setting, refusal.setting(), "checkSettings names the offending setting"),
                    () -> assertTrue(failure.getMessage().contains("'" + property + "'"),
                            "the failure names " + property + ": " + failure.getMessage()),
                    () -> assertTrue(Arrays.stream(CachingDiscoveryResolver.Setting.values())
                                    .filter(other -> other != setting)
                                    .map(TokenSheriffClientDiscoveryCacheValidationTest::expectedProperty)
                                    .noneMatch(other -> failure.getMessage().contains("'" + other + "'")),
                            "the failure names no other cache property: " + failure.getMessage()));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(CachingDiscoveryResolver.Setting.class)
        @DisplayName("Should refuse the property of every setting as an orphan while the cache is disabled")
        void shouldRefuseEverySettingsPropertyAsOrphan(CachingDiscoveryResolver.Setting setting) {
            String property = expectedProperty(setting);
            Config config = configOf(Map.of(property, "PT1M"));

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> ClientConfigMapper.validateDiscoveryCache(config),
                    property + " on a disabled cache must be refused as an orphan");
            assertTrue(failure.getMessage().contains("'" + property + "' is set but"),
                    "the orphan check names " + property + ": " + failure.getMessage());
        }

        @Test
        @DisplayName("Should cover exactly the four discovery cache duration properties in the orphan check")
        void shouldCoverExactlyTheDurationPropertiesInOrphanCheck() {
            Set<String> refused = new HashSet<>();
            for (CachingDiscoveryResolver.Setting setting : CachingDiscoveryResolver.Setting.values()) {
                String property = expectedProperty(setting);
                Config config = configOf(Map.of(property, "PT1M"));
                IllegalStateException failure = assertThrows(IllegalStateException.class,
                        () -> ClientConfigMapper.validateDiscoveryCache(config));
                if (failure.getMessage().contains("'" + property + "' is set but")) {
                    refused.add(property);
                }
            }

            assertEquals(Set.of(ClientRuntimeConfig.DISCOVERY_CACHE_TTL,
                            ClientRuntimeConfig.DISCOVERY_CACHE_GRACE_PERIOD,
                            ClientRuntimeConfig.DISCOVERY_CACHE_MIN_REFRESH_INTERVAL,
                            ClientRuntimeConfig.DISCOVERY_CACHE_JITTER), refused,
                    "the orphan check covers exactly the four duration properties of ClientRuntimeConfig");
        }
    }

    @Nested
    @DisplayName("Valid values")
    class ValidValues {

        @Test
        @DisplayName("Should start when the ttl, grace period and min refresh interval are exactly at the bound")
        void shouldStartAtTheBound() {
            Map<String, String> properties = enabledWith(ClientRuntimeConfig.DISCOVERY_CACHE_TTL, AT_BOUND);
            properties.put(ClientRuntimeConfig.DISCOVERY_CACHE_GRACE_PERIOD, AT_BOUND);
            properties.put(ClientRuntimeConfig.DISCOVERY_CACHE_MIN_REFRESH_INTERVAL, AT_BOUND);
            properties.put(ClientRuntimeConfig.DISCOVERY_CACHE_JITTER, "P364D");
            Config config = configOf(properties);

            assertDoesNotThrow(() -> startUp(config), "365 days is the inclusive upper bound");
            ClientConfigMapper.DiscoveryCacheSettings settings =
                    ClientConfigMapper.mapDiscoveryCache(config).orElseThrow();
            assertAll("settings at the bound",
                    () -> assertEquals(Duration.ofDays(365), settings.ttl(), "the ttl at the bound maps through"),
                    () -> assertEquals(Duration.ofDays(365), settings.gracePeriod(),
                            "the grace period at the bound maps through"),
                    () -> assertEquals(Duration.ofDays(365), settings.minRefreshInterval(),
                            "the min refresh interval at the bound maps through"),
                    () -> assertEquals(Duration.ofDays(364), settings.jitter(),
                            "a jitter just below the ttl maps through"));
        }

        @Test
        @DisplayName("Should start with a fully valid configuration of all four durations")
        void shouldStartWithFullyValidConfiguration() {
            Map<String, String> properties = enabledWith(ClientRuntimeConfig.DISCOVERY_CACHE_GRACE_PERIOD, "PT10M");
            properties.put(ClientRuntimeConfig.DISCOVERY_CACHE_MIN_REFRESH_INTERVAL, "PT30S");
            properties.put(ClientRuntimeConfig.DISCOVERY_CACHE_JITTER, "PT15S");
            Config config = configOf(properties);

            assertDoesNotThrow(() -> startUp(config), "a valid configuration must start");
            assertDoesNotThrow(() -> ClientConfigMapper.mapDiscoveryCache(config).orElseThrow(),
                    "a valid enabled configuration maps its settings");
        }
    }

    @Nested
    @DisplayName("requireDiscoveryCacheEnabled")
    class RequireDiscoveryCacheEnabled {

        @Test
        @DisplayName("Should fail naming the enabled property when no cache property is set")
        void shouldFailWithoutAnyCacheProperty() {
            Config config = configOf(Map.of());

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> ClientConfigMapper.requireDiscoveryCacheEnabled(config),
                    "a cache that was never enabled cannot be required");
            assertTrue(failure.getMessage().contains(ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED),
                    "the failure names the enabled property: " + failure.getMessage());
        }

        @Test
        @DisplayName("Should fail naming the enabled property when the cache is explicitly disabled")
        void shouldFailWhenExplicitlyDisabled() {
            Config config = configOf(Map.of(ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED, "false"));

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> ClientConfigMapper.requireDiscoveryCacheEnabled(config),
                    "enabled=false cannot satisfy a required cache");
            assertTrue(failure.getMessage().contains(ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED),
                    "the failure names the enabled property: " + failure.getMessage());
        }

        @Test
        @DisplayName("Should pass for an enabled cache with a TTL and no client configuration")
        void shouldPassForEnabledCacheWithoutClientConfiguration() {
            Config config = configOf(Map.of(
                    ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED, "true",
                    ClientRuntimeConfig.DISCOVERY_CACHE_TTL, VALID_TTL));

            assertDoesNotThrow(() -> ClientConfigMapper.requireDiscoveryCacheEnabled(config),
                    "no issuer or client id is needed to require the cache");
        }
    }
}
