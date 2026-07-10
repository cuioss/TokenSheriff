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
import de.cuioss.tools.logging.CuiLogger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.Config;

/**
 * CDI producer that exposes the framework-agnostic {@code token-sheriff-client} engine to Quarkus
 * ({@code CLIENT-21}). The framework binding lives entirely here, outside the engine: the pure-Java
 * flow classes carry no CDI, MicroProfile, or JAX-RS coupling and are assembled from the resolved
 * {@link ClientConfiguration} by the {@code @Produces} methods below.
 * <p>
 * All produced beans are {@link ApplicationScoped} and resolved lazily. The core
 * {@link #clientConfiguration()} producer only demands the configuration when a consumer actually
 * injects an engine bean, so an application that has this extension on the classpath but does not use
 * the client never fails at startup — {@link ClientConfigMapper#isConfigured(Config)} guards the
 * required-property check.
 * <p>
 * The engine entry points that need only the configuration are produced directly
 * ({@link TokenEndpointClient}, {@link DiscoveryResolver}, {@link UserInfoClient},
 * {@link RevocationClient}). Server-side token lifecycle is wired with the default
 * {@link InMemoryTokenStore}; a relying party that needs a shared, persistent store replaces the
 * {@link TokenStore} bean with its own implementation.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@ApplicationScoped
public class TokenSheriffClientProducer {

    private static final CuiLogger LOGGER = new CuiLogger(TokenSheriffClientProducer.class);

    private final Config config;

    /**
     * @param config the MicroProfile config carrying the {@link ClientRuntimeConfig} properties; must
     *               not be {@code null}
     */
    public TokenSheriffClientProducer(Config config) {
        this.config = config;
    }

    /**
     * Produces the resolved, immutable client configuration from the {@link ClientRuntimeConfig}
     * properties.
     *
     * @return the resolved client configuration
     * @throws IllegalStateException when the extension is on the classpath but not configured
     */
    @Produces
    @ApplicationScoped
    public ClientConfiguration clientConfiguration() {
        if (!ClientConfigMapper.isConfigured(config)) {
            throw new IllegalStateException("The Token-Sheriff client extension is on the classpath but is not"
                    + " configured. Set '" + ClientRuntimeConfig.ISSUER + "' and '" + ClientRuntimeConfig.CLIENT_ID
                    + "' to enable it.");
        }
        ClientConfiguration clientConfiguration = ClientConfigMapper.map(config);
        LOGGER.debug("Produced ClientConfiguration for issuer '%s'", clientConfiguration.getIssuer());
        return clientConfiguration;
    }

    /**
     * @param clientConfiguration the resolved client configuration
     * @return the token-endpoint transport for the token / refresh / client-credentials exchanges
     */
    @Produces
    @ApplicationScoped
    public TokenEndpointClient tokenEndpointClient(ClientConfiguration clientConfiguration) {
        return new TokenEndpointClient(clientConfiguration);
    }

    /**
     * @param clientConfiguration the resolved client configuration
     * @return the discovery resolver for the issuer's {@code .well-known/openid-configuration}
     */
    @Produces
    @ApplicationScoped
    public DiscoveryResolver discoveryResolver(ClientConfiguration clientConfiguration) {
        return new DiscoveryResolver(clientConfiguration);
    }

    /**
     * @param clientConfiguration the resolved client configuration
     * @return the userinfo client for the OIDC userinfo endpoint
     */
    @Produces
    @ApplicationScoped
    public UserInfoClient userInfoClient(ClientConfiguration clientConfiguration) {
        return new UserInfoClient(clientConfiguration);
    }

    /**
     * @param clientConfiguration the resolved client configuration
     * @return the revocation client for the RFC 7009 revocation endpoint
     */
    @Produces
    @ApplicationScoped
    public RevocationClient revocationClient(ClientConfiguration clientConfiguration) {
        return new RevocationClient(clientConfiguration);
    }

    /**
     * @return the default in-memory server-side token store; replaceable by the relying party
     */
    @Produces
    @ApplicationScoped
    public TokenStore tokenStore() {
        return new InMemoryTokenStore();
    }

    /**
     * @return the proactive-refresh scheduler with its default refresh-lead window
     */
    @Produces
    @ApplicationScoped
    public RefreshScheduler refreshScheduler() {
        return new RefreshScheduler();
    }

    /**
     * @param tokenStore       the server-side token store
     * @param refreshScheduler the proactive-refresh policy
     * @return the token lifecycle manager orchestrating store / refresh / revoke-and-clear
     */
    @Produces
    @ApplicationScoped
    public TokenLifecycleManager tokenLifecycleManager(TokenStore tokenStore, RefreshScheduler refreshScheduler) {
        return new TokenLifecycleManager(tokenStore, refreshScheduler);
    }
}
