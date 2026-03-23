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
package de.cuioss.sheriff.oauth.quarkus.deployment;

import de.cuioss.sheriff.oauth.core.IssuerConfig;
import de.cuioss.sheriff.oauth.core.IssuerConfigCache;
import de.cuioss.sheriff.oauth.core.ParserConfig;
import de.cuioss.sheriff.oauth.core.TokenValidator;
import de.cuioss.sheriff.oauth.core.domain.claim.ClaimValue;
import de.cuioss.sheriff.oauth.core.domain.claim.mapper.IdentityMapper;
import de.cuioss.sheriff.oauth.core.domain.claim.mapper.ScopeMapper;
import de.cuioss.sheriff.oauth.core.domain.token.AccessTokenContent;
import de.cuioss.sheriff.oauth.core.jwks.http.HttpJwksLoader;
import de.cuioss.sheriff.oauth.core.jwks.http.HttpJwksLoaderConfig;
import de.cuioss.sheriff.oauth.core.security.SecurityEventCounter;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OAuthSheriffProcessor} build step methods.
 */
@EnableTestLogger
class OAuthSheriffProcessorBuildStepTest {

    private final OAuthSheriffProcessor processor = new OAuthSheriffProcessor();

    @Test
    void shouldCreateFeatureBuildItem() {
        // Act
        FeatureBuildItem featureItem = processor.feature();

        // Assert
        assertNotNull(featureItem);
        assertEquals("oauth-sheriff", featureItem.getName());
    }

    @Test
    void shouldRegisterJwtValidationConstructorClassesForReflection() {
        // Act
        ReflectiveClassBuildItem reflectiveItem = processor.registerJwtValidationConstructorClassesForReflection();

        // Assert
        assertNotNull(reflectiveItem);
        assertTrue(reflectiveItem.getClassNames().contains(TokenValidator.class.getName()));
        assertTrue(reflectiveItem.getClassNames().contains(IssuerConfigCache.class.getName()));
        assertTrue(reflectiveItem.getClassNames().contains(SecurityEventCounter.class.getName()));
        assertFalse(reflectiveItem.getClassNames().contains(IssuerConfig.class.getName()));
        assertFalse(reflectiveItem.getClassNames().contains(ParserConfig.class.getName()));
    }

    @Test
    void shouldRegisterJwtConfigurationClassesForReflection() {
        // Act
        ReflectiveClassBuildItem reflectiveItem = processor.registerJwtConfigurationClassesForReflection();

        // Assert
        assertNotNull(reflectiveItem);
        assertTrue(reflectiveItem.getClassNames().contains(IssuerConfig.class.getName()));
        assertTrue(reflectiveItem.getClassNames().contains(ParserConfig.class.getName()));
        assertTrue(reflectiveItem.getClassNames().contains(HttpJwksLoaderConfig.class.getName()));
        // These classes are in the constructor group
        assertFalse(reflectiveItem.getClassNames().contains(TokenValidator.class.getName()));
    }

    @Test
    void shouldRegisterJwtTokenContentClassesForReflection() {
        // Act
        ReflectiveClassBuildItem reflectiveItem = processor.registerJwtTokenContentClassesForReflection();

        // Assert
        assertNotNull(reflectiveItem);
        assertTrue(reflectiveItem.getClassNames().contains(AccessTokenContent.class.getName()));
        assertTrue(reflectiveItem.getClassNames().contains(ClaimValue.class.getName()));
        // Claim mappers are in separate group
        assertFalse(reflectiveItem.getClassNames().contains(IdentityMapper.class.getName()));
    }

    @Test
    void shouldRegisterJwtClaimMapperClassesForReflection() {
        // Act
        ReflectiveClassBuildItem reflectiveItem = processor.registerJwtClaimMapperClassesForReflection();

        // Assert
        assertNotNull(reflectiveItem);
        assertTrue(reflectiveItem.getClassNames().contains(IdentityMapper.class.getName()));
        assertTrue(reflectiveItem.getClassNames().contains(ScopeMapper.class.getName()));
        // Token content classes are in separate group
        assertFalse(reflectiveItem.getClassNames().contains(AccessTokenContent.class.getName()));
    }

    // REMOVED: registerBearerTokenClassesForReflection test
    // This follows the standard: application-level classes use annotations,
    // infrastructure/library classes use deployment processor

    @Test
    void shouldRegisterRuntimeInitializedClasses() {
        // Act
        RuntimeInitializedClassBuildItem runtimeItem = processor.runtimeInitializedClasses();

        // Assert
        assertNotNull(runtimeItem);
        assertEquals(HttpJwksLoader.class.getName(), runtimeItem.getClassName());
    }

    @Test
    void shouldCreateAdditionalBeans() {
        // Act
        AdditionalBeanBuildItem beanItem = processor.additionalBeans();

        // Assert
        assertNotNull(beanItem);
    }

    @Test
    void shouldCreateDevUICard() {
        // Act
        CardPageBuildItem cardItem = processor.createJwtDevUICard();

        // Assert
        assertNotNull(cardItem);
        assertFalse(cardItem.getPages().isEmpty());
        assertEquals(2, cardItem.getPages().size());
    }

    @Test
    void shouldCreateDevUIJsonRPCService() {
        // Act
        JsonRPCProvidersBuildItem jsonRpcItem = processor.createJwtDevUIJsonRPCService();

        // Assert
        assertNotNull(jsonRpcItem);
    }

    @Test
    void shouldRegisterUnremovableBeans() {
        // Arrange
        List<UnremovableBeanBuildItem> unremovableBeans = new ArrayList<>();
        BuildProducer<UnremovableBeanBuildItem> producer = unremovableBeans::add;

        // Act
        processor.registerUnremovableBeans(producer);

        // Assert - We have 3 core unremovable beans: TokenValidator, JwtMetricsCollector, MeterRegistry
        assertEquals(1, unremovableBeans.size());
    }
}
