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
package de.cuioss.sheriff.token.client.quarkus.deployment;

import de.cuioss.sheriff.token.client.discovery.CachingDiscoveryResolver;
import de.cuioss.sheriff.token.client.discovery.DiscoveryCacheStatus;
import de.cuioss.sheriff.token.client.quarkus.config.ClientRuntimeConfig;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Matched start control of {@link DiscoveryCacheInjectionDeploymentTest}: the same bean that injects
 * {@link CachingDiscoveryResolver} directly starts once the discovery cache is enabled with a TTL, so
 * the failure of the disabled case is caused by the disabled cache and not by the injection itself.
 */
@Tag("quarkus-boot")
@EnableTestLogger
@DisplayName("Injecting CachingDiscoveryResolver starts when the discovery cache is enabled")
class DiscoveryCacheEnabledInjectionDeploymentTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(DiscoveryCacheInjectionDeploymentTest.DirectInjectingBean.class))
            .overrideConfigKey(ClientRuntimeConfig.ISSUER, "https://issuer.example.com/realms/demo")
            .overrideConfigKey(ClientRuntimeConfig.CLIENT_ID, "demo-client")
            .overrideConfigKey(ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED, "true")
            .overrideConfigKey(ClientRuntimeConfig.DISCOVERY_CACHE_TTL, "PT5M");

    @Inject
    DiscoveryCacheInjectionDeploymentTest.DirectInjectingBean injectingBean;

    @Test
    @DisplayName("Should start and serve the injected caching resolver")
    void shouldStartAndServeCachingResolver() {
        CachingDiscoveryResolver resolver = injectingBean.resolver();

        DiscoveryCacheStatus status = resolver.status();

        assertAll("started application with an enabled cache",
                () -> assertInstanceOf(CachingDiscoveryResolver.class, resolver, "the caching resolver is injected"),
                () -> assertEquals(DiscoveryCacheStatus.State.NOT_ATTEMPTED, status.state(),
                        "the start performs no resolution, so the cache reports NOT_ATTEMPTED"));
    }
}
