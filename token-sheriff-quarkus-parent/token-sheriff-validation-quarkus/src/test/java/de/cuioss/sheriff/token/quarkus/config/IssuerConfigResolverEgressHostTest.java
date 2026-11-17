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
package de.cuioss.sheriff.token.quarkus.config;

import de.cuioss.http.client.adapter.RetryConfig;
import de.cuioss.sheriff.token.quarkus.test.TestConfig;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.cuioss.test.juli.LogAsserts.assertLogMessagePresentContaining;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests how {@link IssuerConfigResolver} forwards the
 * {@link JwtPropertyKeys.ISSUERS#ALLOWED_EGRESS_HOSTS} CSV to the JWKS loader configuration: a
 * port-carrying element fails startup naming the property but never the element, blank elements stay
 * dropped, and bare hosts — including a bare IPv6 literal — pass.
 */
@DisplayName("IssuerConfigResolver: allowed-egress-hosts bare-host rule")
@EnableTestLogger(debug = IssuerConfigResolver.class)
class IssuerConfigResolverEgressHostTest {

    private static final String TEST_ISSUER = "test";
    private static final String ISSUER_IDENTIFIER = "https://keycloak:8443/realms/test";
    private static final String JWKS_URL = ISSUER_IDENTIFIER + "/protocol/openid-connect/certs";
    private static final String PROPERTY = JwtPropertyKeys.ISSUERS.ALLOWED_EGRESS_HOSTS.formatted(TEST_ISSUER);

    private static IssuerConfigResolver resolverWithAllowList(String allowedEgressHosts) {
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), ISSUER_IDENTIFIER,
                JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), JWKS_URL,
                PROPERTY, allowedEgressHosts,
                JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true"
        ));
        return new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);
    }

    private static void createLoaderConfig(IssuerConfigResolver resolver) {
        resolver.createHttpJwksLoaderConfig(TEST_ISSUER, JWKS_URL, null, Optional.of(ISSUER_IDENTIFIER));
    }

    @ParameterizedTest(name = "refuses element \"{0}\"")
    @ValueSource(strings = {"keycloak:8080", "[::1]:443"})
    @DisplayName("Should fail startup on a port-carrying element, naming the property but not the element")
    void shouldFailOnPortCarryingElement(String element) {
        var resolver = resolverWithAllowList("dex, " + element);

        var exception = assertThrows(IllegalStateException.class, () -> createLoaderConfig(resolver),
                "a port-carrying allow-list element must fail startup instead of never matching");

        var messages = new ArrayList<String>();
        for (Throwable current = exception; current != null; current = current.getCause()) {
            messages.add(String.valueOf(current.getMessage()));
        }
        assertAll("the failure names the property and never the element",
                () -> assertTrue(exception.getMessage().contains(PROPERTY),
                        "the message must name the property, but was: " + exception.getMessage()),
                () -> assertTrue(messages.stream().noneMatch(message -> message.contains(element)),
                        "the element must not appear in the message or its cause chain: " + messages),
                () -> assertInstanceOf(IllegalArgumentException.class, exception.getCause(),
                        "the builder's fixed-text refusal is chained as the cause"));
    }

    @Test
    @DisplayName("Should fail the whole issuer resolution on a port-carrying element")
    void shouldFailIssuerResolutionOnPortCarryingElement() {
        var resolver = resolverWithAllowList("keycloak:8080");

        var exception = assertThrows(IllegalStateException.class, resolver::resolveIssuerConfigs,
                "startup resolution must refuse the misconfigured allow-list");

        assertTrue(exception.getMessage().contains(PROPERTY),
                "the message must name the property, but was: " + exception.getMessage());
    }

    @Test
    @DisplayName("Should keep dropping blank CSV elements instead of refusing them")
    void shouldDropBlankElements() {
        var resolver = resolverWithAllowList("keycloak, , ,dex,");

        assertDoesNotThrow(() -> createLoaderConfig(resolver),
                "blank CSV elements are dropped by the parser and never reach the builder");
        assertLogMessagePresentContaining(TestLogLevel.DEBUG,
                "Allow-listed egress hosts for " + TEST_ISSUER + ": " + List.of("keycloak", "dex"));
    }

    @ParameterizedTest(name = "accepts \"{0}\"")
    @ValueSource(strings = {"keycloak", "keycloak, dex, zitadel", "10.0.0.5", "::1", "[2001:db8::1]"})
    @DisplayName("Should accept bare hosts, IPv4 literals and bare or bracketed IPv6 literals")
    void shouldAcceptBareHosts(String allowedEgressHosts) {
        var resolver = resolverWithAllowList(allowedEgressHosts);

        assertDoesNotThrow(() -> createLoaderConfig(resolver),
                "a bare host is a valid allow-list element");
        assertLogMessagePresentContaining(TestLogLevel.DEBUG, "Allow-listed egress hosts for " + TEST_ISSUER);
    }
}
