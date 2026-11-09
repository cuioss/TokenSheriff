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
package de.cuioss.sheriff.token.client.flow;

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.dpop.DpopProofGenerator;
import de.cuioss.sheriff.token.client.dpop.SenderConstraint;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the order of the {@code invalid_scope} classification and the RFC 9449 §8 nonce retry in
 * {@link TokenEndpointClient}: an {@code invalid_scope} answer is terminal, so it is classified before
 * the nonce retry and never retried, even when the answer also carries a valid {@code DPoP-Nonce}
 * header.
 * <p>
 * The stub answers the first call with {@code invalid_scope} plus a {@code DPoP-Nonce} and every later
 * call with a plain {@code 503}. A client that retried with the nonce would surface that {@code 503} as
 * a plain {@link de.cuioss.sheriff.token.commons.error.TransportException} — in a refresh, a
 * pre-redemption failure instead of the scope refusal.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("TokenEndpointClient classifies invalid_scope before the DPoP nonce retry")
class TokenEndpointClientScopeNonceTest {

    private static final KeyPair RSA_KEY_PAIR;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            RSA_KEY_PAIR = generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key pair generation failed", e);
        }
    }

    @Getter
    private final ScopeThenServerErrorDispatcher moduleDispatcher = new ScopeThenServerErrorDispatcher();

    @BeforeEach
    void setUp() {
        moduleDispatcher.reset();
    }

    @Test
    @DisplayName("invalid_scope carrying a DPoP-Nonce is a scope refusal and is not retried with the nonce")
    void shouldClassifyInvalidScopeBeforeTheNonceRetry(URIBuilder uriBuilder) {
        var client = new TokenEndpointClient(ClientConfiguration.builder()
                .issuer("https://issuer.example.com")
                .clientId(Generators.nonBlankStrings().next())
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .redirectUri("https://rp.example.com/callback")
                .allowInsecureHttp(true)
                .allowLoopbackEgress(true)
                .build());
        String endpoint = uriBuilder.addPathSegment("token").buildAsString();
        Map<String, String> requestParams = Map.of("grant_type", "refresh_token");
        Map<String, String> requestHeaders = Map.of();
        SenderConstraint constraint = SenderConstraint.dpop(new DpopProofGenerator(RSA_KEY_PAIR, "RS256"));

        InvalidScopeException thrown = assertThrows(InvalidScopeException.class,
                () -> client.requestToken(endpoint, requestParams, requestHeaders, constraint),
                "an invalid_scope answer is terminal, whatever else the response carries");

        assertAll("scope refusal before the nonce retry",
                () -> assertEquals(1, moduleDispatcher.getCallCounter(),
                        "an invalid_scope answer must not be retried with the DPoP nonce"),
                () -> assertEquals(InvalidScopeException.ERROR_CODE, thrown.getErrorCode()));
    }

    /** First call: {@code invalid_scope} with a nonce; every later call: a plain {@code 503}. */
    static final class ScopeThenServerErrorDispatcher implements ModuleDispatcherElement {

        private static final int HTTP_BAD_REQUEST = 400;
        private static final int HTTP_UNAVAILABLE = 503;

        private final AtomicInteger callCounter = new AtomicInteger();

        int getCallCounter() {
            return callCounter.get();
        }

        void reset() {
            callCounter.set(0);
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
            if (callCounter.incrementAndGet() == 1) {
                return Optional.of(new MockResponse(HTTP_BAD_REQUEST,
                        Headers.of("Content-Type", "application/json", "DPoP-Nonce", "server-nonce-1"),
                        "{\"error\":\"invalid_scope\"}"));
            }
            return Optional.of(new MockResponse(HTTP_UNAVAILABLE, Headers.of(), ""));
        }
    }
}
