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
package de.cuioss.sheriff.token.client.hardening;

import de.cuioss.http.security.database.ApacheCVEAttackDatabase;
import de.cuioss.http.security.database.AttackTestCase;
import de.cuioss.http.security.database.ModSecurityCRSAttackDatabase;
import de.cuioss.http.security.database.OWASPTop10AttackDatabase;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.flow.TokenEndpointClient;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import lombok.Getter;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Hardening sweep — drives the full {@code http.security} attack corpora through the token-endpoint
 * <em>fetch</em> parsing path and asserts it fails closed ({@code CLIENT-20}, {@code CLIENT-22}) —
 * deliverable 10.
 * <p>
 * Every adversarial response body — injection, traversal, encoding-evasion, oversized — is rejected
 * by {@link TokenEndpointClient}: it never yields a trusted {@code TokenResponse}, but raises a
 * {@link TransportException} instead. The engine adds no bespoke transport; the response is parsed
 * through the commons DSL-JSON pipeline and treated as untrusted until proven well-formed.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("Fetch-path fail-closed against http.security attack corpora")
class FetchPathAttackDatabaseTest {

    @Getter
    private final AttackBodyDispatcher moduleDispatcher = new AttackBodyDispatcher();

    @ParameterizedTest
    @ArgumentsSource(OWASPTop10AttackDatabase.ArgumentsProvider.class)
    @DisplayName("Should reject an OWASP Top 10 attack payload served as a token response body")
    void rejectsOwaspAttackBodies(AttackTestCase testCase, URIBuilder uriBuilder) {
        assertFetchFailsClosed(testCase.attackString(), uriBuilder);
    }

    @ParameterizedTest
    @ArgumentsSource(ApacheCVEAttackDatabase.ArgumentsProvider.class)
    @DisplayName("Should reject an Apache CVE attack payload served as a token response body")
    void rejectsApacheCveAttackBodies(AttackTestCase testCase, URIBuilder uriBuilder) {
        assertFetchFailsClosed(testCase.attackString(), uriBuilder);
    }

    @ParameterizedTest
    @ArgumentsSource(ModSecurityCRSAttackDatabase.ArgumentsProvider.class)
    @DisplayName("Should reject a ModSecurity CRS attack payload served as a token response body")
    void rejectsModSecurityAttackBodies(AttackTestCase testCase, URIBuilder uriBuilder) {
        assertFetchFailsClosed(testCase.attackString(), uriBuilder);
    }

    private void assertFetchFailsClosed(String attackBody, URIBuilder uriBuilder) {
        moduleDispatcher.serve(attackBody);
        var config = config();
        var client = new TokenEndpointClient(config);
        String tokenEndpoint = uriBuilder.addPathSegment("token").buildAsString();

        assertThrows(TransportException.class,
                () -> client.requestToken(tokenEndpoint, Map.of("grant_type", "client_credentials"), Map.of()),
                "an adversarial token response body must never yield a trusted token");
    }

    private static ClientConfiguration config() {
        return ClientConfiguration.builder()
                .issuer("https://" + Generators.letterStrings(3, 10).next() + ".example.com")
                .clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(true)
                .build();
    }

    /**
     * Serves a configurable body at the token endpoint with HTTP 200, so the parser — not the HTTP
     * status — is what must reject the adversarial payload.
     */
    static final class AttackBodyDispatcher implements ModuleDispatcherElement {

        private String body = "";

        void serve(String responseBody) {
            this.body = responseBody;
        }

        @Override
        public String getBaseUrl() {
            return "/token";
        }

        @Override
        public Set<HttpMethodMapper> supportedMethods() {
            return Set.of(HttpMethodMapper.POST);
        }

        @Override
        public Optional<MockResponse> handlePost(RecordedRequest request) {
            return Optional.of(new MockResponse(200, Headers.of("Content-Type", "application/json"), body));
        }
    }
}
