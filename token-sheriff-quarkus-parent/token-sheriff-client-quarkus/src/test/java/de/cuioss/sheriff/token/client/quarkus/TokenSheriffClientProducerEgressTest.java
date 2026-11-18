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
import de.cuioss.sheriff.token.client.quarkus.config.ClientConfigMapper;
import de.cuioss.sheriff.token.client.quarkus.config.ClientRuntimeConfig;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests how {@link TokenSheriffClientProducer} and {@link ClientConfigMapper} carry the egress settings
 * ({@link ClientRuntimeConfig#ALLOW_LOOPBACK_EGRESS}, {@link ClientRuntimeConfig#ALLOWED_EGRESS_HOSTS})
 * onto the engine {@link ClientConfiguration}: the secure default, the opt-ins, and the refusal of an
 * element that is not a bare host — a port-carrying one, and one with surrounding whitespace, since the
 * client mapping does not trim. The other producer wiring is covered by
 * {@link TokenSheriffClientProducerTest}.
 */
@EnableTestLogger
@DisplayName("TokenSheriffClientProducer + ClientConfigMapper egress settings")
class TokenSheriffClientProducerEgressTest {

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

    @Nested
    @DisplayName("ClientConfiguration production")
    class ClientConfigurationProduction {

        // The two egress-policy keys are matched pairs as well: omitting them must leave the
        // host-address egress policy at its secure default, and setting them must reach the engine.

        @Test
        @DisplayName("Should keep the egress policy secure when the egress properties are absent")
        void shouldDefaultEgressPolicyToSecure() {
            ClientConfiguration configuration =
                    new TokenSheriffClientProducer(configOf(minimalClientProperties())).clientConfiguration();

            assertAll("secure egress defaults",
                    () -> assertFalse(configuration.isAllowLoopbackEgress(),
                            "omitting allow-loopback-egress must keep loopback refused"),
                    () -> assertTrue(configuration.getAllowedEgressHosts().isEmpty(),
                            "omitting allowed-egress-hosts must leave the allow-list empty"));
        }

        @Test
        @DisplayName("Should map allow-loopback-egress onto the engine configuration when enabled")
        void shouldMapAllowLoopbackEgress() {
            Map<String, String> properties = minimalClientProperties();
            properties.put(ClientRuntimeConfig.ALLOW_LOOPBACK_EGRESS, "true");

            ClientConfiguration configuration =
                    new TokenSheriffClientProducer(configOf(properties)).clientConfiguration();

            assertTrue(configuration.isAllowLoopbackEgress(),
                    "sheriff.client.allow-loopback-egress=true must reach the engine configuration");
        }

        @Test
        @DisplayName("Should map allowed-egress-hosts onto the engine configuration as an ordered list")
        void shouldMapAllowedEgressHosts() {
            Map<String, String> properties = minimalClientProperties();
            properties.put(ClientRuntimeConfig.ALLOWED_EGRESS_HOSTS, "keycloak,idp.internal");

            ClientConfiguration configuration =
                    new TokenSheriffClientProducer(configOf(properties)).clientConfiguration();

            assertEquals(List.of("keycloak", "idp.internal"), configuration.getAllowedEgressHosts(),
                    "sheriff.client.allowed-egress-hosts must reach the engine configuration");
        }
    }

    @Nested
    @DisplayName("ClientConfigMapper contract")
    class ClientConfigMapperContract {

        @Test
        @DisplayName("Should reject a port-carrying allowed-egress-hosts element without echoing it")
        void shouldRejectPortCarryingAllowedEgressHost() {
            Map<String, String> properties = minimalClientProperties();
            properties.put(ClientRuntimeConfig.ALLOWED_EGRESS_HOSTS, "idp.internal,keycloak:8080");
            Config config = configOf(properties);

            IllegalArgumentException failure =
                    assertThrows(IllegalArgumentException.class, () -> ClientConfigMapper.map(config),
                            "an allow-list element carrying a port matches no contacted host and must fail the mapping");
            assertAll("the failure names the allow-list and the entry index, never the entry",
                    () -> assertEquals(
                            "allowedEgressHosts entry 1 is refused: egress allow-list host must be a bare host without a port",
                            failure.getMessage(),
                            "the failure is the field, the zero-based refused index and the validator's fixed text"),
                    () -> assertFalse(failure.getMessage().contains("keycloak"),
                            "the refused element must not be echoed, but was: " + failure.getMessage()));
        }

        @Test
        @DisplayName("Should pass allowed-egress-hosts elements on untrimmed, so a padded element fails the mapping")
        void shouldNotTrimAllowedEgressHostElements() {
            Map<String, String> padded = minimalClientProperties();
            padded.put(ClientRuntimeConfig.ALLOWED_EGRESS_HOSTS, "keycloak, dex");
            Map<String, String> unpadded = minimalClientProperties();
            unpadded.put(ClientRuntimeConfig.ALLOWED_EGRESS_HOSTS, "keycloak,dex");
            Config paddedConfig = configOf(padded);

            IllegalArgumentException failure =
                    assertThrows(IllegalArgumentException.class, () -> ClientConfigMapper.map(paddedConfig),
                            "the client mapping does not trim, so a whitespace-padded element must fail it");
            assertAll("the padded element is refused at its index; the unpadded list is the positive control",
                    () -> assertEquals(
                            "allowedEgressHosts entry 1 is refused: egress allow-list host must not contain whitespace",
                            failure.getMessage(),
                            "the failure is the field, the padded element's zero-based index and the validator's fixed text"),
                    () -> assertFalse(failure.getMessage().contains("dex"),
                            "the refused element must not be echoed, but was: " + failure.getMessage()),
                    () -> assertEquals(List.of("keycloak", "dex"),
                            ClientConfigMapper.map(configOf(unpadded)).getAllowedEgressHosts(),
                            "the same hosts without padding map through"));
        }
    }
}
