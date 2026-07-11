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
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.validation.test.dispatcher.WellKnownDispatcher;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import lombok.Getter;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private static ClientConfiguration secureConfigFor(String issuer) {
        return ClientConfiguration.builder()
                .issuer(issuer)
                .clientId(Generators.nonBlankStrings().next())
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(false)
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
    @DisplayName("Should accept a spec-compliant issuer configured with a trailing slash (M10)")
    void shouldAcceptTrailingSlashIssuer(URIBuilder uriBuilder) {
        // A configured issuer with a trailing slash (e.g. .../realms/x/) has its slash stripped when
        // the .well-known URL is built, so a spec-compliant AS echoes the stripped issuer (OIDC
        // Discovery §4.3). Discovery must normalize both sides symmetrically rather than fail every
        // such realm with an issuer mismatch (M10).
        var base = uriBuilder.buildAsString();
        var issuerWithSlash = base.endsWith("/") ? base : base + "/";

        var metadata = new DiscoveryResolver(configFor(issuerWithSlash)).resolve();

        assertAll("trailing-slash issuer accepted",
                () -> assertTrue(metadata.getIssuer().isPresent(), "issuer resolved"),
                () -> assertTrue(metadata.getTokenEndpoint().isPresent(), "token endpoint"));
        assertEquals(1, moduleDispatcher.getCallCounter(), "well-known endpoint should be called once");
    }

    @Test
    @DisplayName("Should warn but not fail when the AS does not advertise PKCE S256")
    void shouldWarnWhenS256NotAdvertised(URIBuilder uriBuilder) {
        var metadata = new DiscoveryResolver(configFor(uriBuilder.buildAsString())).resolve();

        assertFalse(metadata.supportsS256(), "default discovery document advertises no code_challenge_methods");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "does not advertise PKCE 'S256'");
    }

    @Test
    @DisplayName("Should refuse a non-TLS (http) issuer as a TransportException (SSRF/scheme control, M9)")
    void shouldRefuseNonTlsIssuerAsTransportException(URIBuilder uriBuilder) {
        // The mock server is cleartext http://; with allowInsecureHttp=false the discovery-advertised
        // channel must be refused before any credential-bearing fetch, and the failure MUST be the
        // declared TransportException — never a raw IllegalArgumentException.
        var resolver = new DiscoveryResolver(secureConfigFor(uriBuilder.buildAsString()));

        assertThrows(TransportException.class, resolver::resolve,
                "a non-TLS advertised discovery endpoint must be refused as a TransportException");
        assertEquals(0, moduleDispatcher.getCallCounter(),
                "the non-TLS endpoint must be refused before any network call is made");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Rejecting non-TLS issuer");
    }
}

/**
 * Verifies that the discovery fetch enforces the response size ceiling <em>during</em> the read (M7):
 * an oversized well-known document is rejected as a {@link TransportException} rather than being fully
 * buffered into memory before the cap is checked.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("DiscoveryResolver enforces the response size cap during read")
class DiscoveryResolverSizeCapTest {

    @Getter
    private final OversizedWellKnownDispatcher moduleDispatcher = new OversizedWellKnownDispatcher();

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
    @DisplayName("Should reject an oversized discovery document as a TransportException")
    void shouldRejectOversizedDiscoveryDocument(URIBuilder uriBuilder) {
        var resolver = new DiscoveryResolver(configFor(uriBuilder.buildAsString()));

        assertThrows(TransportException.class, resolver::resolve,
                "a discovery document exceeding the payload ceiling must be refused, not buffered whole");
    }

    /**
     * Serves a syntactically-valid well-known document padded well beyond the 8&nbsp;KiB payload
     * ceiling, so the bounded body handler must trip during the read.
     */
    static final class OversizedWellKnownDispatcher implements ModuleDispatcherElement {

        @Override
        public String getBaseUrl() {
            return WellKnownDispatcher.LOCAL_PATH;
        }

        @Override
        public Set<HttpMethodMapper> supportedMethods() {
            return Set.of(HttpMethodMapper.GET);
        }

        @Override
        public Optional<MockResponse> handleGet(RecordedRequest request) {
            String issuer = request.getUrl().toString();
            issuer = issuer.substring(0, issuer.indexOf(WellKnownDispatcher.LOCAL_PATH));
            String padding = "x".repeat(9 * 1024);
            String body = "{\n"
                    + "  \"issuer\": \"" + issuer + "\",\n"
                    + "  \"token_endpoint\": \"" + issuer + "/token\",\n"
                    + "  \"jwks_uri\": \"" + issuer + "/jwks\",\n"
                    + "  \"padding\": \"" + padding + "\"\n"
                    + "}";
            return Optional.of(new MockResponse(200, Headers.of("Content-Type", "application/json"), body));
        }
    }
}
