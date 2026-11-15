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

import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.CachingDiscoveryResolver;
import de.cuioss.sheriff.token.client.discovery.DiscoveryCacheStatus;
import de.cuioss.sheriff.token.client.discovery.DiscoveryResolver;
import de.cuioss.sheriff.token.client.quarkus.config.ClientConfigMapper;
import de.cuioss.sheriff.token.client.quarkus.config.ClientRuntimeConfig;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.config.SmallRyeConfigBuilder;
import jakarta.enterprise.inject.Typed;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the opt-in discovery cache wiring of {@link TokenSheriffClientProducer} and
 * {@link ClientConfigMapper}: the cache is off unless {@code sheriff.client.discovery-cache.enabled}
 * is {@code true}, the plain {@link DiscoveryResolver} bean is unaffected by it, and every
 * inconsistent cache configuration fails startup with an {@link IllegalStateException} naming the
 * offending property (ADR-0026).
 */
@EnableTestLogger
@DisplayName("TokenSheriffClientProducer discovery cache wiring")
class TokenSheriffClientDiscoveryCacheTest {

    private static final String ISSUER = "https://issuer.example.com/realms/demo";
    private static final String CLIENT_ID = "demo-client";

    private static Config configOf(Map<String, String> properties) {
        return new SmallRyeConfigBuilder().withDefaultValues(properties).build();
    }

    private static Map<String, String> minimalClientProperties() {
        return new HashMap<>(Map.of(
                ClientRuntimeConfig.ISSUER, ISSUER,
                ClientRuntimeConfig.CLIENT_ID, CLIENT_ID));
    }

    private static Map<String, String> enabledCacheProperties() {
        Map<String, String> properties = minimalClientProperties();
        properties.put(ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED, "true");
        properties.put(ClientRuntimeConfig.DISCOVERY_CACHE_TTL, "PT5M");
        return properties;
    }

    private static void startUp(TokenSheriffClientProducer producer) {
        producer.validateDiscoveryCacheOnStartup(new StartupEvent());
    }

    @Nested
    @DisplayName("Cache disabled")
    class CacheDisabled {

        @Test
        @DisplayName("Should start and keep the plain DiscoveryResolver bean when no cache property is set")
        void shouldKeepPlainResolverByDefault() {
            Config config = configOf(minimalClientProperties());
            TokenSheriffClientProducer producer = new TokenSheriffClientProducer(config);
            ClientConfiguration configuration = producer.clientConfiguration();

            assertDoesNotThrow(() -> startUp(producer),
                    "an application without any discovery cache property must start unaffected");
            DiscoveryResolver resolver = producer.discoveryResolver(configuration);

            assertAll("cache disabled by default",
                    () -> assertEquals(DiscoveryResolver.class, resolver.getClass(),
                            "the DiscoveryResolver bean stays the plain, uncached resolver"),
                    () -> assertEquals(Optional.empty(), ClientConfigMapper.mapDiscoveryCache(config),
                            "no cache settings are mapped while the cache is not enabled"));
        }

        @Test
        @DisplayName("Should treat an explicit enabled=false like an absent property")
        void shouldTreatExplicitFalseAsDisabled() {
            Map<String, String> properties = minimalClientProperties();
            properties.put(ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED, "false");
            Config config = configOf(properties);

            assertDoesNotThrow(() -> startUp(new TokenSheriffClientProducer(config)),
                    "enabled=false alone is not an orphan and must not fail startup");
            assertEquals(Optional.empty(), ClientConfigMapper.mapDiscoveryCache(config),
                    "enabled=false maps no cache settings");
        }

        @Test
        @DisplayName("Should fail naming the enabled property when the caching resolver is injected while disabled")
        void shouldFailInjectionWhileDisabled() {
            TokenSheriffClientProducer producer = new TokenSheriffClientProducer(configOf(minimalClientProperties()));
            DiscoveryResolver resolver = producer.discoveryResolver(producer.clientConfiguration());

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> producer.cachingDiscoveryResolver(resolver),
                    "a disabled cache must never silently produce a caching resolver");
            assertTrue(failure.getMessage().contains(ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED),
                    "the failure names the enabled property: " + failure.getMessage());
        }
    }

    @Nested
    @DisplayName("Cache enabled")
    class CacheEnabled {

        @Test
        @DisplayName("Should produce a CachingDiscoveryResolver reporting NOT_ATTEMPTED when enabled with a TTL")
        void shouldProduceCachingResolver() {
            TokenSheriffClientProducer producer = new TokenSheriffClientProducer(configOf(enabledCacheProperties()));
            DiscoveryResolver resolver = producer.discoveryResolver(producer.clientConfiguration());

            assertDoesNotThrow(() -> startUp(producer), "a consistent enabled cache must start");
            CachingDiscoveryResolver caching = producer.cachingDiscoveryResolver(resolver);
            DiscoveryCacheStatus status = caching.status();

            assertAll("freshly produced caching resolver",
                    () -> assertNotNull(caching, "the caching resolver is produced"),
                    () -> assertEquals(DiscoveryCacheStatus.State.NOT_ATTEMPTED, status.state(),
                            "production performs no resolution, so the cache reports NOT_ATTEMPTED"),
                    () -> assertNull(status.lastSuccess(), "nothing has been resolved yet"),
                    () -> assertNull(status.lastFailureCause(), "nothing has failed yet"),
                    () -> assertEquals(DiscoveryResolver.class, producer.discoveryResolver(
                                    producer.clientConfiguration()).getClass(),
                            "the plain DiscoveryResolver bean is unchanged when the cache is enabled"));
        }

        @Test
        @DisplayName("Should map every configured duration and default the optional ones to zero")
        void shouldMapDurations() {
            Map<String, String> full = enabledCacheProperties();
            full.put(ClientRuntimeConfig.DISCOVERY_CACHE_GRACE_PERIOD, "PT10M");
            full.put(ClientRuntimeConfig.DISCOVERY_CACHE_MIN_REFRESH_INTERVAL, "PT30S");
            full.put(ClientRuntimeConfig.DISCOVERY_CACHE_JITTER, "PT15S");

            ClientConfigMapper.DiscoveryCacheSettings mapped =
                    ClientConfigMapper.mapDiscoveryCache(configOf(full)).orElseThrow();
            ClientConfigMapper.DiscoveryCacheSettings defaulted =
                    ClientConfigMapper.mapDiscoveryCache(configOf(enabledCacheProperties())).orElseThrow();

            assertAll("mapped discovery cache settings",
                    () -> assertEquals(Duration.ofMinutes(5), mapped.ttl(), "ttl maps through"),
                    () -> assertEquals(Duration.ofMinutes(10), mapped.gracePeriod(), "grace-period maps through"),
                    () -> assertEquals(Duration.ofSeconds(30), mapped.minRefreshInterval(),
                            "min-refresh-interval maps through"),
                    () -> assertEquals(Duration.ofSeconds(15), mapped.jitter(), "jitter maps through"),
                    () -> assertEquals(Duration.ZERO, defaulted.gracePeriod(),
                            "grace-period defaults to zero, failing closed at TTL expiry"),
                    () -> assertEquals(Duration.ZERO, defaulted.minRefreshInterval(),
                            "min-refresh-interval defaults to zero"),
                    () -> assertEquals(Duration.ZERO, defaulted.jitter(), "jitter defaults to zero"));
        }

        @Test
        @DisplayName("Should fail startup naming the ttl property when the cache is enabled without a TTL")
        void shouldFailWhenEnabledWithoutTtl() {
            Map<String, String> properties = minimalClientProperties();
            properties.put(ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED, "true");
            TokenSheriffClientProducer producer = new TokenSheriffClientProducer(configOf(properties));

            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> startUp(producer),
                    "an enabled cache without a TTL has no usable time to live");
            assertTrue(failure.getMessage().contains(ClientRuntimeConfig.DISCOVERY_CACHE_TTL),
                    "the failure names the ttl property: " + failure.getMessage());
        }

        @Test
        @DisplayName("Should type the caching resolver bean to its own class only")
        void shouldTypeCachingResolverBeanToItsOwnClass() throws Exception {
            Typed typed = TokenSheriffClientProducer.class
                    .getMethod("cachingDiscoveryResolver", DiscoveryResolver.class).getAnnotation(Typed.class);

            assertNotNull(typed, "the producer must restrict the bean types");
            assertArrayEquals(new Class<?>[]{CachingDiscoveryResolver.class}, typed.value(),
                    "the bean must not also satisfy a LoadingStatusProvider injection point");
        }
    }

    @Nested
    @DisplayName("Orphaned cache properties")
    class OrphanedProperties {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                ClientRuntimeConfig.DISCOVERY_CACHE_TTL,
                ClientRuntimeConfig.DISCOVERY_CACHE_GRACE_PERIOD,
                ClientRuntimeConfig.DISCOVERY_CACHE_MIN_REFRESH_INTERVAL,
                ClientRuntimeConfig.DISCOVERY_CACHE_JITTER})
        @DisplayName("Should fail startup naming a cache property set without enabled=true")
        void shouldFailStartupOnOrphanedProperty(String orphan) {
            Map<String, String> properties = minimalClientProperties();
            properties.put(orphan, "PT1M");
            TokenSheriffClientProducer producer = new TokenSheriffClientProducer(configOf(properties));

            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> startUp(producer),
                    "an orphaned cache property must fail startup, never be silently ignored");
            assertAll("orphan failure message",
                    () -> assertTrue(failure.getMessage().contains("'" + orphan + "'"),
                            "the failure names the orphaned property: " + failure.getMessage()),
                    () -> assertTrue(failure.getMessage().contains(ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED),
                            "the failure names the fix: " + failure.getMessage()));
        }

        @Test
        @DisplayName("Should fail startup on an orphan even when enabled is explicitly false")
        void shouldFailStartupOnOrphanWithExplicitFalse() {
            Map<String, String> properties = minimalClientProperties();
            properties.put(ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED, "false");
            properties.put(ClientRuntimeConfig.DISCOVERY_CACHE_TTL, "PT5M");
            TokenSheriffClientProducer producer = new TokenSheriffClientProducer(configOf(properties));

            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> startUp(producer),
                    "enabled=false does not legitimise a cache property");
            assertTrue(failure.getMessage().contains(ClientRuntimeConfig.DISCOVERY_CACHE_TTL),
                    "the failure names the orphaned ttl property: " + failure.getMessage());
        }

        @Test
        @DisplayName("Should validate without requiring the client itself to be configured")
        void shouldValidateWithoutClientConfiguration() {
            TokenSheriffClientProducer unconfigured = new TokenSheriffClientProducer(configOf(Map.of()));
            TokenSheriffClientProducer orphanOnly = new TokenSheriffClientProducer(
                    configOf(Map.of(ClientRuntimeConfig.DISCOVERY_CACHE_JITTER, "PT1S")));

            assertAll("startup check reads the cache configuration only",
                    () -> assertDoesNotThrow(() -> startUp(unconfigured),
                            "an unconfigured client extension must still start"),
                    () -> assertThrows(IllegalStateException.class, () -> startUp(orphanOnly),
                            "an orphan fails startup even when no client bean is ever created"));
        }
    }
}
