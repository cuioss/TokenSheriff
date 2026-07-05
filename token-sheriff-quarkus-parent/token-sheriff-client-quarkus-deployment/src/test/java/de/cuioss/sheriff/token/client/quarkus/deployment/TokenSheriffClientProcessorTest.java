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
package de.cuioss.sheriff.token.client.quarkus.deployment;

import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link TokenSheriffClientProcessor}.
 */
@EnableTestLogger
class TokenSheriffClientProcessorTest {

    private final TokenSheriffClientProcessor processor = new TokenSheriffClientProcessor();

    @Test
    void shouldRegisterTokenSheriffClientFeature() {
        FeatureBuildItem featureItem = processor.feature();

        assertNotNull(featureItem);
        assertEquals("token-sheriff-client", featureItem.getName());
    }
}
