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
package de.cuioss.sheriff.token.client.quarkus;

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.DiscoveryResolver;
import de.cuioss.sheriff.token.client.flow.TokenEndpointClient;
import de.cuioss.sheriff.token.client.lifecycle.InMemoryTokenStore;
import de.cuioss.sheriff.token.client.lifecycle.RefreshScheduler;
import de.cuioss.sheriff.token.client.lifecycle.RevocationClient;
import de.cuioss.sheriff.token.client.lifecycle.TokenLifecycleManager;
import de.cuioss.sheriff.token.client.lifecycle.TokenStore;
import de.cuioss.sheriff.token.client.quarkus.config.ClientConfigMapper;
import de.cuioss.sheriff.token.client.quarkus.config.ClientRuntimeConfig;
import de.cuioss.sheriff.token.client.token.UserInfoClient;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableTestLogger
@DisplayName("TokenSheriffClientProducer + ClientConfigMapper wiring")
class TokenSheriffClientProducerTest {

    private static final String ISSUER = "https://issuer.example.com/realms/demo";
    private static final String CLIENT_ID = "demo-client";
    private static final String CLIENT_SECRET = "demo-secret";
    private static final String REDIRECT_URI = "https://app.example.com/callback";

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

        @Test
        @DisplayName("Should map every configured property onto the engine ClientConfiguration")
        void shouldMapEveryConfiguredProperty() {
            Map<String, String> properties = new HashMap<>(Map.of(
                    ClientRuntimeConfig.ISSUER, ISSUER,
                    ClientRuntimeConfig.CLIENT_ID, CLIENT_ID,
                    ClientRuntimeConfig.CLIENT_SECRET, CLIENT_SECRET,
                    ClientRuntimeConfig.AUTH_METHOD, ClientAuthMethod.CLIENT_SECRET_POST.getMetadataValue(),
                    ClientRuntimeConfig.SCOPES, "openid,profile,email",
                    ClientRuntimeConfig.REDIRECT_URI, REDIRECT_URI,
                    ClientRuntimeConfig.ALLOW_INSECURE_HTTP, "true"));

            ClientConfiguration configuration = new TokenSheriffClientProducer(configOf(properties)).clientConfiguration();

            assertAll("mapped client configuration",
                    () -> assertEquals(ISSUER, configuration.getIssuer(), "issuer maps through"),
                    () -> assertEquals(CLIENT_ID, configuration.getClientId(), "clientId maps through"),
                    () -> assertEquals(CLIENT_SECRET, configuration.getClientSecret(), "clientSecret maps through"),
                    () -> assertEquals(ClientAuthMethod.CLIENT_SECRET_POST, configuration.getAuthMethod(),
                            "authMethod resolves from the metadata value"),
                    () -> assertEquals(List.of("openid", "profile", "email"), configuration.getScopes(),
                            "comma-separated scopes map to an ordered list"),
                    () -> assertEquals(REDIRECT_URI, configuration.getRedirectUri(), "redirectUri maps through"),
                    () -> assertTrue(configuration.isAllowInsecureHttp(), "allowInsecureHttp maps through"));
        }

        @Test
        @DisplayName("Should apply defaults when only the required properties are configured")
        void shouldApplyDefaults() {
            ClientConfiguration configuration =
                    new TokenSheriffClientProducer(configOf(minimalClientProperties())).clientConfiguration();

            assertAll("defaulted client configuration",
                    () -> assertEquals(ClientAuthMethod.CLIENT_SECRET_BASIC, configuration.getAuthMethod(),
                            "authMethod defaults to client_secret_basic"),
                    () -> assertFalse(configuration.isAllowInsecureHttp(), "allowInsecureHttp defaults to false"),
                    () -> assertTrue(configuration.getScopes().isEmpty(), "scopes default to empty"),
                    () -> assertNull(configuration.getClientSecret(), "clientSecret is absent by default"),
                    () -> assertNull(configuration.getRedirectUri(), "redirectUri is absent by default"));
        }

        @Test
        @DisplayName("Should fail loud when the client extension is present but not configured")
        void shouldFailWhenUnconfigured() {
            TokenSheriffClientProducer producer = new TokenSheriffClientProducer(configOf(Map.of()));

            IllegalStateException failure =
                    assertThrows(IllegalStateException.class, producer::clientConfiguration,
                            "an unconfigured client must not silently produce a configuration");
            assertTrue(failure.getMessage().contains(ClientRuntimeConfig.ISSUER),
                    "the failure names the missing issuer property");
        }
    }

    @Nested
    @DisplayName("Engine bean production")
    class EngineBeanProduction {

        private final TokenSheriffClientProducer producer =
                new TokenSheriffClientProducer(configOf(minimalClientProperties()));

        @Test
        @DisplayName("Should assemble each configuration-only engine entry point")
        void shouldProduceConfigurationOnlyEntryPoints() {
            ClientConfiguration configuration = producer.clientConfiguration();

            assertAll("configuration-only engine beans",
                    () -> assertNotNull(producer.tokenEndpointClient(configuration), "token-endpoint client"),
                    () -> assertNotNull(producer.discoveryResolver(configuration), "discovery resolver"),
                    () -> assertNotNull(producer.userInfoClient(configuration), "userinfo client"),
                    () -> assertNotNull(producer.revocationClient(configuration), "revocation client"),
                    () -> assertInstanceOf(TokenEndpointClient.class, producer.tokenEndpointClient(configuration),
                            "token-endpoint client type"),
                    () -> assertInstanceOf(DiscoveryResolver.class, producer.discoveryResolver(configuration),
                            "discovery resolver type"),
                    () -> assertInstanceOf(UserInfoClient.class, producer.userInfoClient(configuration),
                            "userinfo client type"),
                    () -> assertInstanceOf(RevocationClient.class, producer.revocationClient(configuration),
                            "revocation client type"));
        }

        @Test
        @DisplayName("Should wire the server-side token lifecycle over the default in-memory store")
        void shouldWireTokenLifecycle() {
            TokenStore tokenStore = producer.tokenStore();
            RefreshScheduler refreshScheduler = producer.refreshScheduler();
            TokenLifecycleManager lifecycleManager = producer.tokenLifecycleManager(tokenStore, refreshScheduler);

            assertAll("token lifecycle beans",
                    () -> assertInstanceOf(InMemoryTokenStore.class, tokenStore, "default store is in-memory"),
                    () -> assertNotNull(refreshScheduler, "refresh scheduler"),
                    () -> assertNotNull(lifecycleManager, "lifecycle manager"),
                    () -> assertNotSame(producer.tokenStore(), tokenStore,
                            "each store production is an independent instance"));
        }
    }

    @Nested
    @DisplayName("ClientConfigMapper contract")
    class ClientConfigMapperContract {

        @Test
        @DisplayName("Should report configured only when both issuer and clientId are present")
        void shouldReportConfigured() {
            assertAll("isConfigured predicate",
                    () -> assertTrue(ClientConfigMapper.isConfigured(configOf(minimalClientProperties())),
                            "issuer + clientId => configured"),
                    () -> assertFalse(ClientConfigMapper.isConfigured(
                            configOf(Map.of(ClientRuntimeConfig.ISSUER, ISSUER))), "issuer alone => not configured"),
                    () -> assertFalse(ClientConfigMapper.isConfigured(configOf(Map.of())), "empty => not configured"));
        }

        @Test
        @DisplayName("Should reject an unknown auth-method value")
        void shouldRejectUnknownAuthMethod() {
            Map<String, String> properties = minimalClientProperties();
            properties.put(ClientRuntimeConfig.AUTH_METHOD, "not_a_real_method");
            Config config = configOf(properties);

            IllegalArgumentException failure =
                    assertThrows(IllegalArgumentException.class, () -> ClientConfigMapper.map(config),
                            "an unrecognised auth-method must be rejected");
            assertTrue(failure.getMessage().contains(ClientRuntimeConfig.AUTH_METHOD),
                    "the failure names the auth-method property");
        }

        @Test
        @DisplayName("Should throw when a required property is missing during mapping")
        void shouldThrowOnMissingRequiredProperty() {
            Config config = configOf(Map.of(ClientRuntimeConfig.ISSUER, ISSUER));

            assertThrows(IllegalStateException.class, () -> ClientConfigMapper.map(config),
                    "mapping without a clientId must fail loud");
        }
    }
}
