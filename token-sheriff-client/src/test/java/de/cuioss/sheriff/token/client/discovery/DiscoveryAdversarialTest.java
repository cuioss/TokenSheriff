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
package de.cuioss.sheriff.token.client.discovery;

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.commons.error.TransportException;
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

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Fail-closed adversarial cases for {@link DiscoveryResolver}: every rejected condition surfaces a
 * {@link TransportException} rather than a partially-trusted result. The refusal of a non-TLS issuer
 * before any network call is asserted by {@link DiscoveryResolverTest}.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("DiscoveryResolver fail-closed adversarial cases")
class DiscoveryAdversarialTest {

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
    @DisplayName("Should reject an HTTP error response from the discovery endpoint")
    void shouldRejectServerError(URIBuilder uriBuilder) {
        moduleDispatcher.returnError();
        var config = configFor(uriBuilder.buildAsString());
        var resolver = new DiscoveryResolver(config);

        assertThrows(TransportException.class, resolver::resolve);
    }

    @Test
    @DisplayName("Should reject an unparseable discovery document")
    void shouldRejectInvalidJson(URIBuilder uriBuilder) {
        moduleDispatcher.returnInvalidJson();
        var config = configFor(uriBuilder.buildAsString());
        var resolver = new DiscoveryResolver(config);

        assertThrows(TransportException.class, resolver::resolve);
    }

    @Test
    @DisplayName("Should reject a document whose issuer does not match the configured issuer")
    void shouldRejectIssuerMismatch(URIBuilder uriBuilder) {
        moduleDispatcher.returnInvalidIssuer();
        var config = configFor(uriBuilder.buildAsString());
        var resolver = new DiscoveryResolver(config);

        assertThrows(TransportException.class, resolver::resolve);
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "does not match configured issuer");
    }
}
