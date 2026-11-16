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
import de.cuioss.sheriff.token.client.quarkus.ClientExceptionMapper;
import de.cuioss.sheriff.token.client.quarkus.TokenSheriffClientProducer;
import de.cuioss.sheriff.token.client.quarkus.TokenSheriffClientRecorder;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.resteasy.common.spi.ResteasyJaxrsProviderBuildItem;
import org.jboss.jandex.DotName;

/**
 * Build-time processor for the Token-Sheriff client Quarkus extension.
 * <p>
 * At augmentation it
 * </p>
 * <ul>
 *   <li>registers the {@code token-sheriff-client} feature,</li>
 *   <li>contributes the {@link TokenSheriffClientProducer} to the CDI bean archive so the
 *       framework-agnostic client engine is exposed as
 *       {@link jakarta.enterprise.context.ApplicationScoped} beans, and</li>
 *   <li>registers the {@link ClientExceptionMapper} as a JAX-RS provider (RESTEasy Classic only),
 *       and</li>
 *   <li>records a runtime-init check that refuses the start when an injection point asks for
 *       {@link CachingDiscoveryResolver} while the discovery cache is not enabled.</li>
 * </ul>
 * <p>
 * The extension runtime jar is not part of the application's bean-discovery archive by default, so
 * both the producer and the mapper are registered explicitly here. The mapper is the RFC 9457 edge
 * that maps the engine's typed exceptions to {@code application/problem+json} ({@code CLIENT-20}); the
 * producer is the framework binding that {@code CLIENT-21} keeps outside the pure-Java engine.
 * </p>
 *
 * @since 1.0
 */
public class TokenSheriffClientProcessor {

    /**
     * The feature name for the Token-Sheriff client extension.
     */
    private static final String FEATURE = "token-sheriff-client";

    /**
     * The type an injection point must ask for to require an enabled discovery cache.
     */
    private static final DotName CACHING_DISCOVERY_RESOLVER = DotName.createSimple(CachingDiscoveryResolver.class);

    /**
     * Logger for build-time processing.
     */
    private static final CuiLogger LOGGER = new CuiLogger(TokenSheriffClientProcessor.class);

    /**
     * LogRecord for feature registration.
     */
    private static final LogRecord TOKEN_SHERIFF_CLIENT_FEATURE_REGISTERED = LogRecordModel.builder()
            .template("Token-Sheriff client feature registered")
            .prefix("TokenSheriffClient_Q_D")
            .identifier(1)
            .build();

    /**
     * Register the Token-Sheriff client feature.
     *
     * @return a {@link FeatureBuildItem} for the {@code token-sheriff-client} feature
     */
    @BuildStep
    public FeatureBuildItem feature() {
        LOGGER.info(TOKEN_SHERIFF_CLIENT_FEATURE_REGISTERED);
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Register the client engine's CDI producer as an additional bean. The extension's runtime jar is
     * not part of the application's bean-discovery archive, so the {@link TokenSheriffClientProducer}
     * — and the engine beans it produces — must be contributed explicitly during augmentation.
     *
     * @return the {@link AdditionalBeanBuildItem} carrying the producer bean class
     */
    @BuildStep
    public AdditionalBeanBuildItem additionalBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(TokenSheriffClientProducer.class)
                .build();
    }

    /**
     * Register the {@link ClientExceptionMapper} as a JAX-RS provider. Its {@code @Provider}
     * annotation alone does not register it, because the extension's runtime jar is not part of the
     * application's index.
     * <p>
     * <strong>RESTEasy Classic only:</strong> {@link ResteasyJaxrsProviderBuildItem} is consumed by
     * RESTEasy Classic. Applications running on Quarkus REST (RESTEasy Reactive) do not pick up this
     * registration.
     * </p>
     *
     * @return a {@link ResteasyJaxrsProviderBuildItem} for the {@link ClientExceptionMapper}
     */
    @BuildStep
    public ResteasyJaxrsProviderBuildItem registerClientExceptionMapper() {
        return new ResteasyJaxrsProviderBuildItem(ClientExceptionMapper.class.getName());
    }

    /**
     * Refuses the start of an application that injects {@link CachingDiscoveryResolver} while the
     * discovery cache is not enabled, instead of failing on the bean's first use.
     * <p>
     * The step reads the final injection points from ArC and selects every point whose required type
     * is {@link CachingDiscoveryResolver}: a direct injection, or an {@code Instance},
     * {@code Provider} or {@code InjectableInstance} parameterised with it, whose required type ArC
     * reports as the looked-up type. The match is on the type name only, so the producer's own
     * injection points, which ask for {@code DiscoveryResolver}, never match. When at least one point
     * matches, a runtime-init check is recorded, because whether the cache is enabled is runtime
     * configuration; when none matches, nothing is recorded and the application gains no runtime
     * work. A dynamic lookup the build cannot see ({@code CDI.current().select(...)},
     * {@code Arc.container().instance(...)}) is not covered: with ArC's default removal of unused beans
     * it finds no bean, and where the bean is retained the producer's first-use guard refuses it.
     *
     * @param validationPhase the ArC validation phase, carrying the final injection points
     * @param recorder        the recorder of the runtime-init check
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void requireDiscoveryCacheForInjection(ValidationPhaseBuildItem validationPhase,
            TokenSheriffClientRecorder recorder) {
        boolean injected = validationPhase.getContext().getInjectionPoints().stream()
                .anyMatch(injectionPoint -> CACHING_DISCOVERY_RESOLVER.equals(injectionPoint.getRequiredType().name()));
        if (injected) {
            recorder.requireDiscoveryCacheEnabled();
        }
    }
}
