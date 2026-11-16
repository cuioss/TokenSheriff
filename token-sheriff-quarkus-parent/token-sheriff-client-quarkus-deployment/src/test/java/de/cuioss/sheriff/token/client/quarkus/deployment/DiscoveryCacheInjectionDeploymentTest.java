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
import de.cuioss.sheriff.token.client.quarkus.config.ClientRuntimeConfig;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.quarkus.arc.Unremovable;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Container-level test of the deployment check that refuses the start of an application that
 * injects {@link CachingDiscoveryResolver} while the discovery cache is not enabled
 * ({@code TokenSheriffClientProcessor#requireDiscoveryCacheForInjection}).
 * <p>
 * One {@link QuarkusExtensionTest} boots one application, so each failing case is its own nested
 * class with its own extension: a direct injection and an {@link Instance} injection must each fail
 * the deployment with an {@link IllegalStateException} naming
 * {@link ClientRuntimeConfig#DISCOVERY_CACHE_ENABLED}, asserted through the extension's
 * expected-exception hook. The two matched controls that must start live in their own top-level
 * classes, because a started application resolves the test instance as a bean, which a nested
 * class cannot be: {@link DiscoveryCacheEnabledInjectionDeploymentTest} (the same injecting bean with
 * the cache enabled and a TTL) and {@link DiscoveryCacheDisabledNoInjectionDeploymentTest} (the cache
 * disabled and no {@link CachingDiscoveryResolver} injection point).
 * <p>
 * The injecting beans are {@link Unremovable}, so ArC never drops them as unused and the build
 * always sees their injection points.
 */
@Tag("quarkus-boot")
@EnableTestLogger
@DisplayName("Injecting CachingDiscoveryResolver requires an enabled discovery cache")
class DiscoveryCacheInjectionDeploymentTest {

    /**
     * Asserts that {@code thrown}, or an exception in its cause chain, is the
     * {@link IllegalStateException} of {@code ClientConfigMapper.requireDiscoveryCacheEnabled},
     * wherever Quarkus wrapped it.
     *
     * @param thrown the failure of the start or of the first use
     */
    static void assertDisabledCacheFailure(Throwable thrown) {
        assertNotNull(thrown, "CachingDiscoveryResolver must not be usable while the cache is disabled");
        Throwable current = thrown;
        while (current != null) {
            if (current instanceof IllegalStateException && current.getMessage() != null
                    && current.getMessage().contains(ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED)) {
                return;
            }
            current = current.getCause();
        }
        fail("the failure must be an IllegalStateException naming " + ClientRuntimeConfig.DISCOVERY_CACHE_ENABLED
                + ", but the cause chain was: " + thrown);
    }

    @Nested
    @DisplayName("Direct injection while the cache is disabled")
    class DirectInjectionWhileDisabled {

        @RegisterExtension
        static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
                .withApplicationRoot(jar -> jar.addClasses(DirectInjectingBean.class))
                .assertException(DiscoveryCacheInjectionDeploymentTest::assertDisabledCacheFailure);

        @Test
        @DisplayName("Should not start the application")
        void shouldFailDeployment() {
            // Never invoked: QuarkusExtensionTest skips test methods once assertException is set, and the
            // startup failure itself is asserted in assertDisabledCacheFailure.
            fail("the deployment should have failed for a CachingDiscoveryResolver injection while disabled");
        }
    }

    @Nested
    @DisplayName("Instance injection while the cache is disabled")
    class InstanceInjectionWhileDisabled {

        @RegisterExtension
        static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
                .withApplicationRoot(jar -> jar.addClasses(InstanceInjectingBean.class))
                .assertException(DiscoveryCacheInjectionDeploymentTest::assertDisabledCacheFailure);

        @Test
        @DisplayName("Should not start the application")
        void shouldFailDeployment() {
            // Never invoked: QuarkusExtensionTest skips test methods once assertException is set, and the
            // startup failure itself is asserted in assertDisabledCacheFailure.
            fail("the deployment should have failed for an Instance<CachingDiscoveryResolver> injection while disabled");
        }
    }

    /**
     * Application bean that injects the caching resolver directly.
     */
    @ApplicationScoped
    @Unremovable
    public static class DirectInjectingBean {

        @Inject
        CachingDiscoveryResolver resolver;

        CachingDiscoveryResolver resolver() {
            return resolver;
        }
    }

    /**
     * Application bean that looks the caching resolver up through an {@link Instance}.
     */
    @ApplicationScoped
    @Unremovable
    public static class InstanceInjectingBean {

        @Inject
        Instance<CachingDiscoveryResolver> resolver;
    }
}
