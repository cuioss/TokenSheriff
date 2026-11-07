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
import de.cuioss.sheriff.token.client.internal.ClientLogMessages;
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

import javax.net.ssl.SSLContext;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fail-closed adversarial cases for {@link DiscoveryResolver}: every rejected condition surfaces a
 * {@link TransportException} rather than a partially-trusted result. The refusal of a non-TLS issuer
 * before any network call is asserted by {@link DiscoveryResolverTest}. The transport and document
 * hardening cases (gap 8) cover a cleartext endpoint inside an HTTPS document, a non-JSON, absent or
 * conflicting {@code Content-Type}, and a {@code 3xx} that is never followed, each with a positive
 * control.
 * <p>
 * The mock authorization server speaks TLS ({@code useHttps = true}), as the engine's outbound HTTP
 * does: the configured issuer is the mock server's own {@code https} origin, and the client trusts the
 * mock's certificate through the injected {@link SSLContext}, handed to it as the per-client trust
 * material ({@link ClientConfiguration#getSslContext()}). No case reaches the mock over plain HTTP.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer(useHttps = true)
@DisplayName("DiscoveryResolver fail-closed adversarial cases")
class DiscoveryAdversarialTest {

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String JSON = "application/json";
    private static final String CONTENT_TYPE_REFUSAL = "discovery response Content-Type is not application/json";

    /** A cleartext origin advertised inside an otherwise all-https document; it is never contacted. */
    private static final String CLEARTEXT_ORIGIN = "http://idp.invalid";

    @Getter
    private final ScriptedDiscoveryDispatcher moduleDispatcher = new ScriptedDiscoveryDispatcher();

    @BeforeEach
    void resetDispatcher() {
        moduleDispatcher.setCallCounter(0);
        moduleDispatcher.returnDefault();
        moduleDispatcher.clearScript();
    }

    @Test
    @DisplayName("Should reject an HTTP error response from the discovery endpoint")
    void shouldRejectServerError(URIBuilder uriBuilder, SSLContext sslContext) {
        moduleDispatcher.returnError();
        var resolver = new DiscoveryResolver(tlsConfig(uriBuilder, sslContext, false));

        assertThrows(TransportException.class, resolver::resolve);
    }

    @Test
    @DisplayName("Should reject an unparseable discovery document")
    void shouldRejectInvalidJson(URIBuilder uriBuilder, SSLContext sslContext) {
        moduleDispatcher.returnInvalidJson();
        var resolver = new DiscoveryResolver(tlsConfig(uriBuilder, sslContext, false));

        assertThrows(TransportException.class, resolver::resolve);
    }

    @Test
    @DisplayName("Should reject a document whose issuer does not match the configured issuer")
    void shouldRejectIssuerMismatch(URIBuilder uriBuilder, SSLContext sslContext) {
        moduleDispatcher.returnInvalidIssuer();
        var resolver = new DiscoveryResolver(tlsConfig(uriBuilder, sslContext, false));

        assertThrows(TransportException.class, resolver::resolve);
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "does not match configured issuer");
    }

    @ParameterizedTest(name = "http {0}")
    @ValueSource(strings = {"authorization_endpoint", "token_endpoint", "userinfo_endpoint", "jwks_uri",
            "end_session_endpoint", "revocation_endpoint", "introspection_endpoint",
            "pushed_authorization_request_endpoint"})
    @DisplayName("Should refuse an https issuer's document that advertises a cleartext endpoint")
    void shouldRefuseHttpEndpointFromHttpsIssuer(String endpointField, URIBuilder uriBuilder,
            SSLContext sslContext) {
        String issuer = issuer(uriBuilder);
        moduleDispatcher.script(new MockResponse(200, Headers.of(CONTENT_TYPE, JSON),
                discoveryDocument(issuer, endpointField)));
        // allowInsecureHttp is set on purpose: the case proves the flag does not relax the refusal.
        var resolver = new DiscoveryResolver(tlsConfig(uriBuilder, sslContext, true));

        var thrown = assertThrows(TransportException.class, resolver::resolve,
                "allowInsecureHttp must not relax a scheme downgrade inside an HTTPS document");
        assertEquals(discoveryFailure(issuer, "https issuer advertises a non-https " + endpointField),
                thrown.getMessage(), "the refusal must name the endpoint label");
    }

    @Test
    @DisplayName("Should resolve an https issuer's document whose endpoints are all https")
    void shouldResolveAllHttpsEndpoints(URIBuilder uriBuilder, SSLContext sslContext) {
        String issuer = issuer(uriBuilder);
        moduleDispatcher.script(new MockResponse(200, Headers.of(CONTENT_TYPE, JSON),
                discoveryDocument(issuer, null)));

        ProviderMetadata metadata = new DiscoveryResolver(tlsConfig(uriBuilder, sslContext, false)).resolve();

        assertEquals(issuer + "/token_endpoint", metadata.getTokenEndpoint().orElseThrow());
    }

    @ParameterizedTest(name = "Content-Type: {0}")
    @ValueSource(strings = {"text/html", "text/plain", "application/jrd+json"})
    @DisplayName("Should refuse a discovery response whose Content-Type is not application/json")
    void shouldRefuseNonJsonContentType(String contentType, URIBuilder uriBuilder, SSLContext sslContext) {
        String issuer = issuer(uriBuilder);
        moduleDispatcher.script(new MockResponse(200, Headers.of(CONTENT_TYPE, contentType),
                discoveryDocument(issuer, null)));
        var resolver = new DiscoveryResolver(tlsConfig(uriBuilder, sslContext, false));

        var thrown = assertThrows(TransportException.class, resolver::resolve);
        assertEquals(discoveryFailure(issuer, CONTENT_TYPE_REFUSAL), thrown.getMessage(),
                "the refusal must name the Content-Type");
    }

    @Test
    @DisplayName("Should refuse a discovery response without a Content-Type header")
    void shouldRefuseAbsentContentType(URIBuilder uriBuilder, SSLContext sslContext) {
        String issuer = issuer(uriBuilder);
        moduleDispatcher.script(new MockResponse(200, Headers.of(), discoveryDocument(issuer, null)));
        var resolver = new DiscoveryResolver(tlsConfig(uriBuilder, sslContext, false));

        var thrown = assertThrows(TransportException.class, resolver::resolve);
        assertEquals(discoveryFailure(issuer, CONTENT_TYPE_REFUSAL), thrown.getMessage(),
                "the refusal must name the Content-Type");
    }

    @ParameterizedTest(name = "Content-Type lines: application/json first = {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("Should refuse a discovery response carrying a second, conflicting Content-Type line")
    void shouldRefuseConflictingContentTypeLines(boolean jsonFirst, URIBuilder uriBuilder, SSLContext sslContext) {
        String issuer = issuer(uriBuilder);
        Headers headers = jsonFirst
                ? Headers.of(CONTENT_TYPE, JSON, CONTENT_TYPE, "text/html")
                : Headers.of(CONTENT_TYPE, "text/html", CONTENT_TYPE, JSON);
        moduleDispatcher.script(new MockResponse(200, headers, discoveryDocument(issuer, null)));
        var resolver = new DiscoveryResolver(tlsConfig(uriBuilder, sslContext, false));

        var thrown = assertThrows(TransportException.class, resolver::resolve,
                "a conflicting Content-Type line must be refused whatever its position");
        assertEquals(discoveryFailure(issuer, CONTENT_TYPE_REFUSAL), thrown.getMessage(),
                "the refusal must name the Content-Type");
    }

    @Test
    @DisplayName("Should accept a discovery response repeating application/json on two Content-Type lines")
    void shouldAcceptRepeatedJsonContentTypeLines(URIBuilder uriBuilder, SSLContext sslContext) {
        String issuer = issuer(uriBuilder);
        moduleDispatcher.script(new MockResponse(200,
                Headers.of(CONTENT_TYPE, JSON, CONTENT_TYPE, "application/json; charset=UTF-8"),
                discoveryDocument(issuer, null)));

        ProviderMetadata metadata = new DiscoveryResolver(tlsConfig(uriBuilder, sslContext, false)).resolve();

        assertEquals(issuer, metadata.getIssuer().orElseThrow());
    }

    @Test
    @DisplayName("Should accept application/json with a charset parameter")
    void shouldAcceptJsonWithCharset(URIBuilder uriBuilder, SSLContext sslContext) {
        String issuer = issuer(uriBuilder);
        moduleDispatcher.script(new MockResponse(200, Headers.of(CONTENT_TYPE, "application/json; charset=UTF-8"),
                discoveryDocument(issuer, null)));

        ProviderMetadata metadata = new DiscoveryResolver(tlsConfig(uriBuilder, sslContext, false)).resolve();

        assertEquals(issuer, metadata.getIssuer().orElseThrow());
    }

    @Test
    @DisplayName("Should not follow a 3xx from the discovery endpoint and refuse it")
    void shouldNotFollowRedirect(URIBuilder uriBuilder, SSLContext sslContext) {
        // Pinned as observed: the redirect is surfaced as an unexpected status and its target is never
        // contacted. If the target were fetched, the dispatcher would answer a second request.
        String issuer = issuer(uriBuilder);
        String location = issuer + WellKnownDispatcher.LOCAL_PATH + "?moved=true";
        moduleDispatcher.script(new MockResponse(302, Headers.of("Location", location), ""));
        var resolver = new DiscoveryResolver(tlsConfig(uriBuilder, sslContext, false));

        var thrown = assertThrows(TransportException.class, resolver::resolve);
        assertEquals(discoveryFailure(issuer, "unexpected HTTP status 302"), thrown.getMessage(),
                "the redirect must surface as an unexpected status");
        assertEquals(1, moduleDispatcher.getCallCounter(), "the redirect target must never be contacted");
    }

    /**
     * The mock server's own {@code https} origin, used as the configured issuer so discovery is fetched
     * from the TLS mock directly.
     */
    private static String issuer(URIBuilder uriBuilder) {
        String origin = uriBuilder.buildAsString();
        String issuer = origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
        assertTrue(issuer.startsWith("https://"), "the mock authorization server must be reached over TLS: " + issuer);
        return issuer;
    }

    /**
     * A client configuration for the mock server's {@code https} issuer that trusts the mock's
     * certificate through the injected {@link SSLContext} and permits loopback egress, since the mock
     * listens on loopback.
     *
     * @param allowInsecureHttp whether cleartext is permitted; only the scheme-downgrade case sets it, to
     *                          prove the flag does not relax that refusal
     */
    private static ClientConfiguration tlsConfig(URIBuilder uriBuilder, SSLContext sslContext,
            boolean allowInsecureHttp) {
        return ClientConfiguration.builder()
                .issuer(issuer(uriBuilder))
                .clientId(Generators.nonBlankStrings().next())
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .sslContext(sslContext)
                .allowInsecureHttp(allowInsecureHttp)
                .allowLoopbackEgress(true)
                .build();
    }

    /**
     * The exact refusal message {@link DiscoveryResolver} raises for {@code issuer} with the given
     * detail.
     */
    private static String discoveryFailure(String issuer, String detail) {
        return ClientLogMessages.ERROR.DISCOVERY_FAILED.format(issuer, detail);
    }

    /**
     * Builds a discovery document for {@code issuer} advertising every endpoint field over {@code https},
     * except {@code cleartextField}, which is advertised over {@code http}.
     *
     * @param issuer         the {@code https} issuer the document names and roots its endpoints at
     * @param cleartextField the endpoint field to downgrade, or {@code null} for an all-https document
     */
    private static String discoveryDocument(String issuer, @Nullable String cleartextField) {
        var json = new StringBuilder("{\"issuer\":\"").append(issuer).append('"');
        for (String field : List.of("authorization_endpoint", "token_endpoint", "userinfo_endpoint", "jwks_uri",
                "end_session_endpoint", "revocation_endpoint", "introspection_endpoint",
                "pushed_authorization_request_endpoint")) {
            String origin = field.equals(cleartextField) ? CLEARTEXT_ORIGIN : issuer;
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
