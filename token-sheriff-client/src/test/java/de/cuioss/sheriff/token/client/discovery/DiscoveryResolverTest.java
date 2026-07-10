/*
 * Copyright © 2022 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.token.client.discovery;

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.validation.test.dispatcher.WellKnownDispatcher;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("DiscoveryResolver against a mocked well-known endpoint")
class DiscoveryResolverTest {

    @Getter
    private final WellKnownDispatcher moduleDispatcher = new WellKnownDispatcher();

    @BeforeEach
    void resetDispatcher() {
        moduleDispatcher.setCallCounter(0);
        moduleDispatcher.returnDefault();
    }

    private static ClientConfiguration configFor(String issuer) {
        return ClientConfiguration.builder()
                .issuer(issuer)
                .clientId(Generators.nonBlankStrings().next())
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(true)
                .build();
    }

    @Test
    @DisplayName("Should resolve the advertised endpoints from the discovery document")
    void shouldResolveEndpoints(URIBuilder uriBuilder) {
        var issuer = uriBuilder.buildAsString();

        var metadata = new DiscoveryResolver(configFor(issuer)).resolve();

        assertAll("resolved metadata",
                () -> assertEquals(issuer, metadata.getIssuer().orElseThrow()),
                () -> assertTrue(metadata.getTokenEndpoint().isPresent(), "token endpoint"),
                () -> assertTrue(metadata.getAuthorizationEndpoint().isPresent(), "authorization endpoint"),
                () -> assertTrue(metadata.getUserinfoEndpoint().isPresent(), "userinfo endpoint"),
                () -> assertTrue(metadata.getJwksUri().isPresent(), "jwks uri"));
        assertEquals(1, moduleDispatcher.getCallCounter(), "well-known endpoint should be called once");
    }

    @Test
    @DisplayName("Should warn but not fail when the AS does not advertise PKCE S256")
    void shouldWarnWhenS256NotAdvertised(URIBuilder uriBuilder) {
        var metadata = new DiscoveryResolver(configFor(uriBuilder.buildAsString())).resolve();

        assertFalse(metadata.supportsS256(), "default discovery document advertises no code_challenge_methods");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "does not advertise PKCE 'S256'");
    }
}
