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
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.Unremovable;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Matched start control of {@link DiscoveryCacheInjectionDeploymentTest}: an application that keeps
 * the discovery cache disabled and has no {@link CachingDiscoveryResolver} injection point starts,
 * so the deployment check refuses only an application that asks for the cache. No discovery cache
 * property is set.
 */
@Tag("quarkus-boot")
@EnableTestLogger
@DisplayName("An application without a CachingDiscoveryResolver injection starts with the cache disabled")
class DiscoveryCacheDisabledNoInjectionDeploymentTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(UnrelatedBean.class));

    @Inject
    UnrelatedBean unrelatedBean;

    @Test
    @DisplayName("Should start and inject the application's own beans")
    void shouldStartWithoutCachingResolverInjection() {
        assertInstanceOf(UnrelatedBean.class, ClientProxy.unwrap(unrelatedBean),
                "the application started with the cache disabled and its own bean is injected");
    }

    /**
     * Application bean that does not use the discovery cache.
     */
    @ApplicationScoped
    @Unremovable
    public static class UnrelatedBean {
    }
}
