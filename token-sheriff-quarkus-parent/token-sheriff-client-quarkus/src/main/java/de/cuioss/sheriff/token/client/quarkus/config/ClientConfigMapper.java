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
package de.cuioss.sheriff.token.client.quarkus.config;

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.CachingDiscoveryResolver;
import de.cuioss.sheriff.token.client.discovery.DiscoveryResolver;
import org.eclipse.microprofile.config.Config;

import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Maps MicroProfile {@link Config} values under the {@link ClientRuntimeConfig} keys to the
 * framework-agnostic engine {@link ClientConfiguration} ({@code CLIENT-21}).
 * <p>
 * Values are read through {@code Config.getOptionalValue}, so the mapping never fails eagerly at
 * container startup: an application that has the client extension on the classpath but does not
 * configure it simply reports {@link #isConfigured(Config) not configured}. The mapping enforces the
 * required fields only when {@link #map(Config)} is actually invoked (that is, when a bean the engine
 * exposes is used).
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public final class ClientConfigMapper {

    private static final String DEFAULT_AUTH_METHOD = ClientAuthMethod.CLIENT_SECRET_BASIC.getMetadataValue();

    private ClientConfigMapper() {
        // utility class — never instantiated
    }

    /**
     * Reports whether enough configuration is present to build a {@link ClientConfiguration}. A client
     * is configured only when both {@link ClientRuntimeConfig#ISSUER} and
     * {@link ClientRuntimeConfig#CLIENT_ID} are set.
     *
     * @param config the MicroProfile config to inspect; must not be {@code null}
     * @return {@code true} when the client is configured
     */
    public static boolean isConfigured(Config config) {
        return config.getOptionalValue(ClientRuntimeConfig.ISSUER, String.class).isPresent()
                && config.getOptionalValue(ClientRuntimeConfig.CLIENT_ID, String.class).isPresent();
    }

    /**
     * Builds the engine {@link ClientConfiguration} from the configured MicroProfile values.
     *
     * @param config the MicroProfile config to read; must not be {@code null}
     * @return the mapped, immutable client configuration
     * @throws IllegalStateException    when a required key ({@link ClientRuntimeConfig#ISSUER} or
     *                                  {@link ClientRuntimeConfig#CLIENT_ID}) is missing
     * @throws IllegalArgumentException when {@link ClientRuntimeConfig#AUTH_METHOD} names an
     *                                  unrecognised authentication method, or when
     *                                  {@link ClientRuntimeConfig#DISCOVERY_DOCUMENT_MAX_SIZE} is not
     *                                  positive, or when {@link ClientRuntimeConfig#BACK_CHANNEL_ORIGIN}
     *                                  is not a bare absolute {@code http}/{@code https} origin, or when
     *                                  {@link ClientRuntimeConfig#ALLOWED_EGRESS_HOSTS} holds a blank
     *                                  entry (the engine {@link ClientConfiguration} rejects both at build
     *                                  time)
     */
    public static ClientConfiguration map(Config config) {
        String issuer = requiredValue(config, ClientRuntimeConfig.ISSUER);
        String clientId = requiredValue(config, ClientRuntimeConfig.CLIENT_ID);

        ClientConfiguration.ClientConfigurationBuilder builder = ClientConfiguration.builder()
                .issuer(issuer)
                .clientId(clientId)
                .authMethod(resolveAuthMethod(config))
                .allowInsecureHttp(config.getOptionalValue(ClientRuntimeConfig.ALLOW_INSECURE_HTTP, Boolean.class)
                        .orElse(Boolean.FALSE))
                // Secure-by-default: absent property means hostname verification stays ON, mirroring
                // ClientConfiguration's verifyHostname default. Note the polarity differs from the
                // allow-insecure-http entry above, whose secure default is FALSE.
                .verifyHostname(config.getOptionalValue(ClientRuntimeConfig.VERIFY_HOSTNAME, Boolean.class)
                        .orElse(Boolean.TRUE))
                .strictScopeReconciliation(
                        config.getOptionalValue(ClientRuntimeConfig.STRICT_SCOPE_RECONCILIATION, Boolean.class)
                                .orElse(Boolean.FALSE))
                // Secure-by-default: absent means the host-address egress policy keeps refusing loopback.
                .allowLoopbackEgress(config.getOptionalValue(ClientRuntimeConfig.ALLOW_LOOPBACK_EGRESS, Boolean.class)
                        .orElse(Boolean.FALSE));

        config.getOptionalValue(ClientRuntimeConfig.DISCOVERY_DOCUMENT_MAX_SIZE, Integer.class)
                .ifPresent(builder::discoveryDocumentMaxSize);
        config.getOptionalValue(ClientRuntimeConfig.CLIENT_SECRET, String.class).ifPresent(builder::clientSecret);
        config.getOptionalValue(ClientRuntimeConfig.REDIRECT_URI, String.class).ifPresent(builder::redirectUri);
        config.getOptionalValue(ClientRuntimeConfig.BACK_CHANNEL_ORIGIN, String.class)
                .ifPresent(builder::backChannelOrigin);
        resolveScopes(config).forEach(builder::scope);
        config.getOptionalValues(ClientRuntimeConfig.ALLOWED_EGRESS_HOSTS, String.class)
                .orElse(List.of())
                .forEach(builder::allowedEgressHost);

        return builder.build();
    }

    /**
     * The discovery cache properties that are meaningful only while
     * {@link ClientRuntimeConfig#DISCOVERY_CACHE_ENABLED} is {@code true}.
     */
    private static final List<String> DISCOVERY_CACHE_SETTINGS = List.of(
            ClientRuntimeConfig.DISCOVERY_CACHE_TTL,
            ClientRuntimeConfig.DISCOVERY_CACHE_GRACE_PERIOD,
            ClientRuntimeConfig.DISCOVERY_CACHE_MIN_REFRESH_INTERVAL,
            ClientRuntimeConfig.DISCOVERY_CACHE_JITTER);

    /**
     * Maps the parameter name that {@link CachingDiscoveryResolver#checkSettings} puts first in each
     * refusal to the property that carries the value, so a refusal names the setting to fix. The
     * rules themselves live only in {@code checkSettings}.
     */
    private static final Map<String, String> DISCOVERY_CACHE_PARAMETER_PROPERTIES = Map.of(
            "ttl", ClientRuntimeConfig.DISCOVERY_CACHE_TTL,
            "gracePeriod", ClientRuntimeConfig.DISCOVERY_CACHE_GRACE_PERIOD,
            "minRefreshInterval", ClientRuntimeConfig.DISCOVERY_CACHE_MIN_REFRESH_INTERVAL,
            "jitter", ClientRuntimeConfig.DISCOVERY_CACHE_JITTER);

    /** Used for a refusal whose parameter name is not in the table; names the property family. */
    private static final String DISCOVERY_CACHE_PROPERTIES = ClientRuntimeConfig.DISCOVERY_CACHE_PREFIX + ".*";

    /**
     * The settings of an enabled discovery cache, in the shape of the
     * {@link CachingDiscoveryResolver} constructor arguments.
     *
     * @param ttl                how long a resolved document is served without a refresh
     * @param gracePeriod        how long past its time to live a document is still served while
     *                           refreshes fail
     * @param minRefreshInterval the minimum time between a failed refresh and the next attempt
     * @param jitter             the upper bound of the random shortening of each freshness window
     */
    public record DiscoveryCacheSettings(Duration ttl, Duration gracePeriod, Duration minRefreshInterval,
    Duration jitter) {

        /**
         * Validates that every component is present.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public DiscoveryCacheSettings {
            Objects.requireNonNull(ttl, "ttl must not be null");
            Objects.requireNonNull(gracePeriod, "gracePeriod must not be null");
            Objects.requireNonNull(minRefreshInterval, "minRefreshInterval must not be null");
            Objects.requireNonNull(jitter, "jitter must not be null");
        }

        /**
         * Creates the caching resolver these settings describe.
         *
         * @param delegate the resolver that fetches and validates the discovery document
         * @param clock    the source of the current instant
         * @return the caching resolver
         * @throws IllegalArgumentException if a duration is out of range (see
         *                                  {@link CachingDiscoveryResolver})
         */
        public CachingDiscoveryResolver create(DiscoveryResolver delegate, InstantSource clock) {
            return new CachingDiscoveryResolver(delegate, ttl, gracePeriod, minRefreshInterval, jitter, clock);
        }
    }

    /**
     * Rejects an inconsistent or invalid discovery cache configuration (ADR-0026). A
     * {@code sheriff.client.discovery-cache.*} property other than
     * {@link ClientRuntimeConfig#DISCOVERY_CACHE_ENABLED} that is set while the cache is not enabled
     * would otherwise be silently ignored, so it is refused first, whatever its value. An enabled cache
     * needs a {@link ClientRuntimeConfig#DISCOVERY_CACHE_TTL}; each of its four durations must then
     * parse as a duration, and together they must satisfy
     * {@link CachingDiscoveryResolver#checkSettings}, the single holder of the value rules (a positive
     * TTL, no negative duration, at most 365 days for the TTL, the grace period and the minimum
     * refresh interval, and a jitter smaller than the TTL). A configuration without any discovery cache
     * property passes.
     *
     * @param config the MicroProfile config to inspect; must not be {@code null}
     * @throws IllegalStateException naming the offending property and the fix, when a cache property
     *                               is set while the cache is not enabled, when the cache is enabled
     *                               without a TTL, when a value does not parse as a duration, or when
     *                               a value breaks a rule of {@code checkSettings}
     */
    public static void validateDiscoveryCache(Config config) {
        if (!isDiscoveryCacheEnabled(config)) {
            for (String key : DISCOVERY_CACHE_SETTINGS) {
                if (config.getOptionalValue(key, String.class).isPresent()) {
                    throw new IllegalStateException("Token-Sheriff client property '" + key + "' is set but '"
                            + ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED + "' is not true, so it would be ignored."
                            + " Set '" + ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED
                            + "=true' to use the discovery cache, or remove '" + key + "'.");
                }
            }
            return;
        }
        if (config.getOptionalValue(ClientRuntimeConfig.DISCOVERY_CACHE_TTL, String.class).isEmpty()) {
            throw new IllegalStateException("Token-Sheriff client property '" + ClientRuntimeConfig.DISCOVERY_CACHE_TTL
                    + "' is required when '" + ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED + "' is true."
                    + " Set it to how long a resolved discovery document may be reused (for example PT5M),"
                    + " or disable the discovery cache.");
        }
        Duration ttl = parsedDuration(config, ClientRuntimeConfig.DISCOVERY_CACHE_TTL);
        Duration gracePeriod = parsedDuration(config, ClientRuntimeConfig.DISCOVERY_CACHE_GRACE_PERIOD);
        Duration minRefreshInterval = parsedDuration(config, ClientRuntimeConfig.DISCOVERY_CACHE_MIN_REFRESH_INTERVAL);
        Duration jitter = parsedDuration(config, ClientRuntimeConfig.DISCOVERY_CACHE_JITTER);
        try {
            CachingDiscoveryResolver.checkSettings(ttl, gracePeriod, minRefreshInterval, jitter);
        } catch (IllegalArgumentException e) {
            String refusal = e.getMessage();
            String parameter = refusal.substring(0, refusal.indexOf(' '));
            String property = DISCOVERY_CACHE_PARAMETER_PROPERTIES.getOrDefault(parameter, DISCOVERY_CACHE_PROPERTIES);
            throw new IllegalStateException("Token-Sheriff client property '" + property + "' is invalid: "
                    + refusal + ". Change '" + property + "' to a value the rule allows, or disable the"
                    + " discovery cache.", e);
        }
    }

    /**
     * Requires an enabled discovery cache, for an application that uses {@link CachingDiscoveryResolver}.
     * The deployment check that refuses the start when an injection point asks for the bean and the
     * first-use guard of the producer both call this method, so both fail with the same message. Only
     * the configuration is read; no client configuration is needed.
     *
     * @param config the MicroProfile config to inspect; must not be {@code null}
     * @throws IllegalStateException naming {@link ClientRuntimeConfig#DISCOVERY_CACHE_ENABLED} and
     *                               {@link ClientRuntimeConfig#DISCOVERY_CACHE_TTL}, when the cache is not
     *                               enabled; or as {@link #validateDiscoveryCache(Config)}, when the
     *                               configuration is inconsistent or invalid
     */
    public static void requireDiscoveryCacheEnabled(Config config) {
        if (mapDiscoveryCache(config).isEmpty()) {
            throw new IllegalStateException("The Token-Sheriff discovery cache is not enabled."
                    + " Set '" + ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED + "=true' and '"
                    + ClientRuntimeConfig.DISCOVERY_CACHE_TTL + "' to inject a CachingDiscoveryResolver.");
        }
    }

    /**
     * Maps the discovery cache properties, after {@link #validateDiscoveryCache(Config) validating}
     * them, so the returned settings always satisfy {@link CachingDiscoveryResolver#checkSettings}.
     *
     * @param config the MicroProfile config to read; must not be {@code null}
     * @return the settings of the enabled cache, or {@link Optional#empty()} when the cache is not
     *         enabled
     * @throws IllegalStateException when the configuration is inconsistent or invalid (see
     *                               {@link #validateDiscoveryCache(Config)})
     */
    public static Optional<DiscoveryCacheSettings> mapDiscoveryCache(Config config) {
        validateDiscoveryCache(config);
        if (!isDiscoveryCacheEnabled(config)) {
            return Optional.empty();
        }
        return Optional.of(new DiscoveryCacheSettings(
                config.getValue(ClientRuntimeConfig.DISCOVERY_CACHE_TTL, Duration.class),
                optionalDuration(config, ClientRuntimeConfig.DISCOVERY_CACHE_GRACE_PERIOD),
                optionalDuration(config, ClientRuntimeConfig.DISCOVERY_CACHE_MIN_REFRESH_INTERVAL),
                optionalDuration(config, ClientRuntimeConfig.DISCOVERY_CACHE_JITTER)));
    }

    private static boolean isDiscoveryCacheEnabled(Config config) {
        return config.getOptionalValue(ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED, Boolean.class)
                .orElse(Boolean.FALSE);
    }

    private static Duration optionalDuration(Config config, String key) {
        return config.getOptionalValue(key, Duration.class).orElse(Duration.ZERO);
    }

    private static Duration parsedDuration(Config config, String key) {
        try {
            return optionalDuration(config, key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Token-Sheriff client property '" + key
                    + "' is not a valid duration. Set it to an ISO-8601 duration such as PT5M.", e);
        }
    }

    private static String requiredValue(Config config, String key) {
        return config.getOptionalValue(key, String.class)
                .orElseThrow(() -> new IllegalStateException(
                        "Required Token-Sheriff client property '" + key + "' is not configured"));
    }

    private static ClientAuthMethod resolveAuthMethod(Config config) {
        String value = config.getOptionalValue(ClientRuntimeConfig.AUTH_METHOD, String.class)
                .orElse(DEFAULT_AUTH_METHOD);
        return ClientAuthMethod.fromMetadataValue(value)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown '" + ClientRuntimeConfig.AUTH_METHOD + "' value '" + value
                                + "'; expected an RFC 8414 token_endpoint_auth_methods value"));
    }

    private static List<String> resolveScopes(Config config) {
        return config.getOptionalValues(ClientRuntimeConfig.SCOPES, String.class).orElse(List.of());
    }
}
