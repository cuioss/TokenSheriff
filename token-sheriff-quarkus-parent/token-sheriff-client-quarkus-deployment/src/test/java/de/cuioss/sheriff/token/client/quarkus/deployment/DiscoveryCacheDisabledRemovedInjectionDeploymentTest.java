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
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins what happens to an application whose only {@link CachingDiscoveryResolver} injection point
 * belongs to a bean that ArC removes as unused, while the discovery cache is disabled. No discovery
 * cache property is set and the bean is not {@code @Unremovable}, so ArC's default removal of unused
 * beans drops it before the validation phase: the deployment check of
 * {@code TokenSheriffClientProcessor#requireDiscoveryCacheForInjection} never sees its injection
 * point and the application starts.
 * <p>
 * The matched negative control is
 * {@link DiscoveryCacheInjectionDeploymentTest.DirectInjectionWhileDisabled}: the same direct
 * injection on a bean that differs only by {@code @Unremovable}, whose retained injection point fails
 * the start. A failing start and a started application cannot share one
 * {@link QuarkusExtensionTest}, and a started application resolves the test instance as a bean, which
 * a nested class cannot be, so the control lives in that class.
 */
@Tag("quarkus-boot")
@EnableTestLogger
@DisplayName("A removed CachingDiscoveryResolver injection point does not fail the start with the cache disabled")
class DiscoveryCacheDisabledRemovedInjectionDeploymentTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(RemovedInjectingBean.class));

    @Test
    @DisplayName("Should start and hold no bean for the removed injecting class")
    void shouldStartWithRemovedInjectionPoint() {
        assertFalse(Arc.container().instance(RemovedInjectingBean.class).isAvailable(),
                "ArC removed the unused injecting bean, so its injection point never reached the deployment check");
    }

    /**
     * Application bean that injects the caching resolver directly and is used by nothing, so ArC
     * removes it.
     */
    @ApplicationScoped
    public static class RemovedInjectingBean {

        @Inject
        CachingDiscoveryResolver resolver;
    }
}
