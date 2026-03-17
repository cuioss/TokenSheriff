/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.oauth.integration;

import de.cuioss.sheriff.oauth.integration.TestRealm.Capability;
import de.cuioss.tools.logging.CuiLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Shared provider sources for parameterized integration tests.
 * <p>
 * Adding a new IDP = one line in {@link #allProviders()} + declare its
 * {@link Capability capabilities} in the factory method.
 * <p>
 * Maven profiles control IDP availability via system properties:
 * <ul>
 *     <li>{@code integration-tests} profile: no {@code dex.enabled} → only Keycloak</li>
 *     <li>{@code multi-idp-tests} profile: {@code dex.enabled=true} → Keycloak + Dex</li>
 * </ul>
 */
public final class TestProviders {

    private static final CuiLogger LOGGER = new CuiLogger(TestProviders.class);

    private TestProviders() {
        // utility class
    }

    /**
     * All available OIDC providers. Includes Dex when {@code -Ddex.enabled=true}
     * is set via Maven failsafe system properties.
     * <p>
     * If Dex is enabled but unreachable, {@code obtainValidToken()} will fail loudly
     * (no silent skip, no {@code @EnabledIf}).
     */
    public static Stream<TestRealm> allProviders() {
        var providers = new ArrayList<TestRealm>();
        providers.add(TestRealm.createIntegrationRealm());
        providers.add(TestRealm.createBenchmarkRealm());
        if (Boolean.getBoolean("dex.enabled")) {
            providers.add(TestRealm.createDexProvider());
        }
        return providers.stream();
    }

    /**
     * Returns providers that support <em>all</em> of the given capabilities.
     * Providers missing any required capability are skipped with an INFO log.
     *
     * @param required capabilities the test needs
     * @return stream of matching providers (may be empty)
     */
    public static Stream<TestRealm> withCapabilities(Capability... required) {
        Set<Capability> requiredSet = Set.of(required);
        List<TestRealm> all = allProviders().toList();
        List<TestRealm> matching = new ArrayList<>();

        for (TestRealm realm : all) {
            if (realm.hasAllCapabilities(required)) {
                matching.add(realm);
            } else {
                LOGGER.info("Skipping %s — missing capabilities: %s (has: %s)",
                        realm, requiredSet, realm.getCapabilities());
            }
        }
        return matching.stream();
    }

    /**
     * Providers that support bearer auth with roles, groups, and custom scopes.
     * Convenience wrapper for the most common Keycloak-specific capability set.
     */
    public static Stream<TestRealm> bearerAuthProviders() {
        return withCapabilities(Capability.ROLES, Capability.GROUPS, Capability.CUSTOM_SCOPES);
    }

    /**
     * Providers that support the {@code offline_access} scope for refresh tokens.
     */
    public static Stream<TestRealm> offlineAccessProviders() {
        return withCapabilities(Capability.OFFLINE_ACCESS);
    }

    /**
     * Providers whose access tokens are JWTs (not opaque).
     * Required for access token validation tests — opaque tokens (e.g. Dex)
     * cannot be validated as JWTs.
     */
    public static Stream<TestRealm> jwtAccessTokenProviders() {
        return withCapabilities(Capability.JWT_ACCESS_TOKENS);
    }
}
