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

import de.cuioss.sheriff.token.client.discovery.CachingDiscoveryResolver;
import de.cuioss.sheriff.token.client.quarkus.config.ClientConfigMapper;
import de.cuioss.sheriff.token.client.quarkus.config.ClientRuntimeConfig;
import io.quarkus.runtime.annotations.Recorder;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Runtime-init checks of the Token-Sheriff client extension.
 * <p>
 * {@link #requireDiscoveryCacheEnabled()} refuses the start of an application that injects
 * {@link CachingDiscoveryResolver} while the discovery cache is not enabled. The check runs at
 * runtime init rather than at build time because {@link ClientRuntimeConfig#DISCOVERY_CACHE_ENABLED}
 * is runtime configuration, and it runs before the application serves. The deployment processor
 * records it only when the build found an injection point that asks for
 * {@link CachingDiscoveryResolver}, so an application that does not use the cache gains no runtime
 * work. The recorder holds no state and creates no bean.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@Recorder
public class TokenSheriffClientRecorder {

    /**
     * Refuses the start when the discovery cache is not enabled, reading the runtime configuration
     * only.
     *
     * @throws IllegalStateException naming {@link ClientRuntimeConfig#DISCOVERY_CACHE_ENABLED}, when the
     *                               cache is not enabled, or when the discovery cache configuration is
     *                               inconsistent or invalid (see
     *                               {@link ClientConfigMapper#requireDiscoveryCacheEnabled})
     */
    public void requireDiscoveryCacheEnabled() {
        ClientConfigMapper.requireDiscoveryCacheEnabled(ConfigProvider.getConfig());
    }
}
