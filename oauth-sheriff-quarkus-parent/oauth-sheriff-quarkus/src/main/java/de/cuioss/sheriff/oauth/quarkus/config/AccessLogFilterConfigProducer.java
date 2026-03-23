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
package de.cuioss.sheriff.oauth.quarkus.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;

/**
 * CDI producer for {@link AccessLogFilterConfigResolver}.
 * <p>
 * This producer creates the configuration resolver instance and injects
 * it into dependent beans like {@link de.cuioss.sheriff.oauth.quarkus.logging.CustomAccessLogFilter}.
 * </p>
 *
 * @since 1.0
 */
@ApplicationScoped
public class AccessLogFilterConfigProducer {

    private final Config config;

    /**
     * Creates a new AccessLogFilterConfigProducer with the specified configuration.
     *
     * @param config the configuration instance to use for property resolution
     */
    @Inject
    public AccessLogFilterConfigProducer(Config config) {
        this.config = config;
    }

    /**
     * Produces the access log filter config resolver.
     *
     * @return The AccessLogFilterConfigResolver instance
     */
    @Produces
    @ApplicationScoped
    public AccessLogFilterConfigResolver produceAccessLogFilterConfigResolver() {
        return new AccessLogFilterConfigResolver(config);
    }
}