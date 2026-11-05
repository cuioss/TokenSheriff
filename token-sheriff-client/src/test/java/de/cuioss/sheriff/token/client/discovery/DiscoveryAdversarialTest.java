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
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fail-closed adversarial cases for {@link DiscoveryResolver}: every rejected condition surfaces a
 * {@link TransportException} rather than a partially-trusted result. The refusal of a non-TLS issuer
 * before any network call is asserted by {@link DiscoveryResolverTest}. The transport and document
 * hardening cases (gap 8) cover a cleartext endpoint inside an HTTPS document, a non-JSON or absent
 * {@code Content-Type}, and a {@code 3xx} that is never followed, each with a positive control.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("DiscoveryResolver fail-closed adversarial cases")
class DiscoveryAdversarialTest {

    /**
     * A TLS issuer on the reserved {@code .invalid} TLD: it is never resolved, because discovery is
     * routed to the cleartext mock server through {@code backChannelOrigin} — the same pattern as
     * {@code BackChannelOriginRoutingTest}, so no TLS fixture is needed.
     */
    private static final String HTTPS_ISSUER = "https://idp.invalid";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String JSON = "application/json";

    @Getter
    private final ScriptedDiscoveryDispatcher moduleDispatcher = new ScriptedDiscoveryDispatcher();

    @BeforeEach
    void resetDispatcher() {
        moduleDispatcher.setCallCounter(0);
        moduleDispatcher.returnDefault();
        moduleDispatcher.clearScript();
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

    @ParameterizedTest(name = "http {0}")
    @ValueSource(strings = {"authorization_endpoint", "token_endpoint", "userinfo_endpoint", "jwks_uri",
            "end_session_endpoint", "revocation_endpoint", "introspection_endpoint",
            "pushed_authorization_request_endpoint"})
    @DisplayName("Should refuse an https issuer's document that advertises a cleartext endpoint")
    void shouldRefuseHttpEndpointFromHttpsIssuer(String endpointField, URIBuilder uriBuilder) {
        moduleDispatcher.script(new MockResponse(200, Headers.of(CONTENT_TYPE, JSON),
                discoveryDocument(endpointField)));
        var resolver = new DiscoveryResolver(httpsIssuerConfig(uriBuilder));

        var thrown = assertThrows(TransportException.class, resolver::resolve,
                "allowInsecureHttp must not relax a scheme downgrade inside an HTTPS document");
        assertTrue(thrown.getMessage().contains(endpointField),
                "the refusal must name the endpoint label: " + thrown.getMessage());
    }

    @Test
    @DisplayName("Should resolve an https issuer's document whose endpoints are all https")
    void shouldResolveAllHttpsEndpoints(URIBuilder uriBuilder) {
        moduleDispatcher.script(new MockResponse(200, Headers.of(CONTENT_TYPE, JSON), discoveryDocument(null)));

        ProviderMetadata metadata = new DiscoveryResolver(httpsIssuerConfig(uriBuilder)).resolve();

        assertEquals(HTTPS_ISSUER + "/token_endpoint", metadata.getTokenEndpoint().orElseThrow());
    }

    @ParameterizedTest(name = "Content-Type: {0}")
    @ValueSource(strings = {"text/html", "text/plain", "application/jrd+json"})
    @DisplayName("Should refuse a discovery response whose Content-Type is not application/json")
    void shouldRefuseNonJsonContentType(String contentType, URIBuilder uriBuilder) {
        moduleDispatcher.script(new MockResponse(200, Headers.of(CONTENT_TYPE, contentType),
                discoveryDocument(null)));
        var resolver = new DiscoveryResolver(httpsIssuerConfig(uriBuilder));

        var thrown = assertThrows(TransportException.class, resolver::resolve);
        assertTrue(thrown.getMessage().contains("Content-Type"),
                "the refusal must name the Content-Type: " + thrown.getMessage());
    }

    @Test
    @DisplayName("Should refuse a discovery response without a Content-Type header")
    void shouldRefuseAbsentContentType(URIBuilder uriBuilder) {
        moduleDispatcher.script(new MockResponse(200, Headers.of(), discoveryDocument(null)));
        var resolver = new DiscoveryResolver(httpsIssuerConfig(uriBuilder));

        var thrown = assertThrows(TransportException.class, resolver::resolve);
        assertTrue(thrown.getMessage().contains("Content-Type"),
                "the refusal must name the Content-Type: " + thrown.getMessage());
    }

    @Test
    @DisplayName("Should accept application/json with a charset parameter")
    void shouldAcceptJsonWithCharset(URIBuilder uriBuilder) {
        moduleDispatcher.script(new MockResponse(200, Headers.of(CONTENT_TYPE, "application/json; charset=UTF-8"),
                discoveryDocument(null)));

        ProviderMetadata metadata = new DiscoveryResolver(httpsIssuerConfig(uriBuilder)).resolve();

        assertEquals(HTTPS_ISSUER, metadata.getIssuer().orElseThrow());
    }

    @Test
    @DisplayName("Should not follow a 3xx from the discovery endpoint and refuse it")
    void shouldNotFollowRedirect(URIBuilder uriBuilder) {
        // Pinned as observed: the redirect is surfaced as an unexpected status and its target is never
        // contacted. If the target were fetched, the dispatcher would answer a second request.
        String location = uriBuilder.buildAsString().replaceAll("/$", "") + WellKnownDispatcher.LOCAL_PATH
                + "?moved=true";
        moduleDispatcher.script(new MockResponse(302, Headers.of("Location", location), ""));
        var resolver = new DiscoveryResolver(httpsIssuerConfig(uriBuilder));

        var thrown = assertThrows(TransportException.class, resolver::resolve);
        assertTrue(thrown.getMessage().contains("unexpected HTTP status 302"),
                "the redirect must surface as an unexpected status: " + thrown.getMessage());
        assertEquals(1, moduleDispatcher.getCallCounter(), "the redirect target must never be contacted");
    }

    private static ClientConfiguration httpsIssuerConfig(URIBuilder uriBuilder) {
        URI server = URI.create(uriBuilder.buildAsString());
        return ClientConfiguration.builder()
                .issuer(HTTPS_ISSUER)
                .clientId(Generators.nonBlankStrings().next())
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .backChannelOrigin(server.getScheme() + "://" + server.getHost() + ":" + server.getPort())
                .allowInsecureHttp(true)
                .build();
    }

    /**
     * Builds a discovery document for {@link #HTTPS_ISSUER} advertising every endpoint field over
     * {@code https}, except {@code cleartextField}, which is advertised over {@code http}.
     *
     * @param cleartextField the endpoint field to downgrade, or {@code null} for an all-https document
     */
    private static String discoveryDocument(@Nullable String cleartextField) {
        var json = new StringBuilder("{\"issuer\":\"").append(HTTPS_ISSUER).append('"');
        for (String field : List.of("authorization_endpoint", "token_endpoint", "userinfo_endpoint", "jwks_uri",
                "end_session_endpoint", "revocation_endpoint", "introspection_endpoint",
                "pushed_authorization_request_endpoint")) {
            String origin = field.equals(cleartextField) ? "http://idp.invalid" : HTTPS_ISSUER;
            json.append(",\"").append(field).append("\":\"").append(origin).append('/').append(field).append('"');
        }
        return json.append(",\"code_challenge_methods_supported\":[\"S256\"]}").toString();
    }

    /**
     * The shared well-known dispatcher, extended by a scripted response that, when set, answers every
     * discovery request instead of the dispatcher's strategy. Every answered request is counted.
     */
    static final class ScriptedDiscoveryDispatcher extends WellKnownDispatcher {

        private volatile @Nullable MockResponse scripted;

        void script(MockResponse response) {
            this.scripted = response;
        }

        void clearScript() {
            this.scripted = null;
        }

        @Override
        public Optional<MockResponse> handleGet(RecordedRequest request) {
            MockResponse response = scripted;
            if (response == null) {
                return super.handleGet(request);
            }
            setCallCounter(getCallCounter() + 1);
            return Optional.of(response);
        }
    }
}
