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
package de.cuioss.sheriff.token.quarkus.deployment;

import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.quarkus.test.QuarkusExtensionTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for verifying the auto-configuration of the Token-Sheriff Quarkus extension.
 * <p>
 * The extension is deployed with {@code application-test.properties}; the raw
 * configuration reads are asserted by {@link TokenSheriffIntegrationTest}.
 */
@Tag("quarkus-boot")
@EnableTestLogger
class TokenSheriffProcessorTest {

    /**
     * The Quarkus test framework.
     */
    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .withConfigurationResource("application-test.properties");

    @Test
    void shouldTestProcessorBasicFunctionality() {
        // Test that processor can be instantiated without issues
        assertDoesNotThrow(TokenSheriffProcessor::new,
                "TokenSheriffProcessor should be instantiable without exceptions");

        TokenSheriffProcessor processor = new TokenSheriffProcessor();
        assertNotNull(processor, "Processor should not be null");
    }

}
