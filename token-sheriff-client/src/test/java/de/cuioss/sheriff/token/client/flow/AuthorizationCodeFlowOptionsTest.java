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
import de.cuioss.sheriff.token.client.discovery.ProviderMetadata;
import de.cuioss.sheriff.token.client.token.IdTokenValidationBridge;
import de.cuioss.sheriff.token.client.token.TokenValidationBridge;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static de.cuioss.sheriff.token.client.flow.AuthorizationRequestBuilderParametersTest.wireParameters;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the per-call {@link AuthorizationRequestOptions} entry point of {@link AuthorizationCodeFlow}:
 * {@link AuthorizationRequestOptions#DEFAULTS} reproduces the request of
 * {@link AuthorizationCodeFlow#authorize(ProviderMetadata)}, a scope override applies to one call only and
 * leaves the shared configuration untouched, concurrent calls do not interfere, and a subclassed
 * {@link AuthorizationRequestBuilder} overriding the three-argument {@code build} is still dispatched by
 * the option-less entry point.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("AuthorizationCodeFlow per-call authorization-request options")
class AuthorizationCodeFlowOptionsTest {

    private static final String ISSUER = "https://issuer.example.com";
    private static final String REDIRECT_URI = "https://rp.example.com/callback";
    private static final String AUTH_ENDPOINT = "https://issuer.example.com/authorize";
    private static final String PARAM_SCOPE = "scope";
    private static final String CONFIGURED_SCOPES = "openid profile";
    private static final long LATCH_TIMEOUT_SECONDS = 10;

    private TokenValidationBridge accessBridge;
    private IdTokenValidationBridge idBridge;

    @BeforeEach
    void setUp() {
        TokenValidator validator = TokenValidator.builder()
                .issuerConfig(TestTokenGenerators.accessTokens().next().getIssuerConfig()).build();
        accessBridge = new TokenValidationBridge(validator);
        idBridge = new IdTokenValidationBridge(validator);
    }

    private static ClientConfiguration config(String clientId) {
        return ClientConfiguration.builder()
                .issuer(ISSUER)
                .clientId(clientId)
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .scope("profile")
                .redirectUri(REDIRECT_URI)
                .build();
    }

    private static ClientConfiguration config() {
        return config(Generators.letterStrings(4, 16).next());
    }

    private static ProviderMetadata metadata() {
        var metadata = new ProviderMetadata();
        metadata.issuer = ISSUER;
        metadata.authorizationEndpoint = AUTH_ENDPOINT;
        metadata.codeChallengeMethodsSupported = List.of("S256");
        return metadata;
    }

    private AuthorizationCodeFlow flow(ClientConfiguration configuration) {
        return new AuthorizationCodeFlow(configuration, new TokenEndpointClient(configuration), accessBridge,
                idBridge);
    }

    private AuthorizationCodeFlow flow(ClientConfiguration configuration, AuthorizationRequestBuilder builder) {
        return new AuthorizationCodeFlow(configuration, new TokenEndpointClient(configuration), accessBridge,
                idBridge, new IssValidator(), builder, new CallbackHandler(), null);
    }

    /**
     * Replaces the per-context random values of a built URL with fixed placeholders, so two requests
     * minted with different contexts can be compared parameter by parameter.
     */
    static List<Map.Entry<String, String>> normalized(String url, FlowContext context) {
        return wireParameters(url).stream()
                .map(entry -> Map.entry(entry.getKey(), placeholder(entry.getValue(), context)))
                .toList();
    }

    private static String placeholder(String value, FlowContext context) {
        if (value.equals(context.state())) {
            return "{state}";
        }
        if (value.equals(context.nonce())) {
            return "{nonce}";
        }
        if (value.equals(context.pkceChallenge().codeChallenge())) {
            return "{code_challenge}";
        }
        return value;
    }

    private static String scopeOf(String url) {
        return wireParameters(url).stream()
                .filter(entry -> PARAM_SCOPE.equals(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("Default options")
    class Defaults {

        @Test
        @DisplayName("Should send the same parameter set with DEFAULTS as without options")
        void shouldMatchOptionLessRequest() {
            var flow = flow(config());

            var plain = flow.authorize(metadata());
            var withDefaults = flow.authorize(metadata(), AuthorizationRequestOptions.DEFAULTS);

            assertEquals(normalized(plain.authorizationUrl(), plain.context()),
                    normalized(withDefaults.authorizationUrl(), withDefaults.context()),
                    "DEFAULTS reproduces the names, values and order of the option-less request");
        }

        @Test
        @DisplayName("Should keep the default flow URL byte-identical to the request emitted before the refactor")
        void shouldKeepDefaultFlowUrlByteIdentical() {
            var flow = flow(config("client-1"));

            var plain = flow.authorize(metadata());
            var withDefaults = flow.authorize(metadata(), AuthorizationRequestOptions.DEFAULTS);

            assertAll("golden default URL",
                    () -> assertEquals(golden(plain.context()), plain.authorizationUrl(),
                            "authorize(metadata) keeps its pre-refactor parameter order and encoding"),
                    () -> assertEquals(golden(withDefaults.context()), withDefaults.authorizationUrl(),
                            "authorize(metadata, DEFAULTS) emits the same byte sequence"));
        }

        private static String golden(FlowContext context) {
            return AUTH_ENDPOINT
                    + "?scope=openid+profile"
                    + "&response_type=code"
                    + "&redirect_uri=https%3A%2F%2Frp.example.com%2Fcallback"
                    + "&state=" + context.state()
                    + "&code_challenge_method=S256"
                    + "&nonce=" + context.nonce()
                    + "&client_id=client-1"
                    + "&code_challenge=" + context.pkceChallenge().codeChallenge()
                    + "&response_mode=form_post";
        }
    }

    @Nested
    @DisplayName("Scope override")
    class ScopeOverride {

        @Test
        @DisplayName("Should replace the configured scopes for one call without mutating the configuration")
        void shouldOverrideForOneCallOnly() {
            var configuration = config();
            var flow = flow(configuration);
            var options = AuthorizationRequestOptions.builder().scopes(List.of("openid", "email")).build();

            var overridden = flow.authorize(metadata(), options);
            var following = flow.authorize(metadata());

            assertAll("per-call scope override",
                    () -> assertEquals("openid email", scopeOf(overridden.authorizationUrl()),
                            "the override replaces the configured scopes for that call"),
                    () -> assertEquals(List.of("openid", "profile"), configuration.getScopes(),
                            "the shared configuration keeps its scopes"),
                    () -> assertEquals(CONFIGURED_SCOPES, scopeOf(following.authorizationUrl()),
                            "a following option-less call sends the configured scopes again"));
        }

        @Test
        @DisplayName("Should keep two concurrent calls with different scope overrides apart")
        void shouldNotInterfereAcrossConcurrentCalls() throws Exception {
            var bothInFlight = new CountDownLatch(2);
            var builder = new RendezvousBuilder(bothInFlight);
            var flow = flow(config(), builder);
            var first = AuthorizationRequestOptions.builder().scopes(List.of("openid", "email")).build();
            var second = AuthorizationRequestOptions.builder().scopes(List.of("openid", "phone")).build();

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<AuthorizationCodeFlow.AuthorizationRedirect> firstCall =
                        executor.submit(() -> flow.authorize(metadata(), first));
                Future<AuthorizationCodeFlow.AuthorizationRedirect> secondCall =
                        executor.submit(() -> flow.authorize(metadata(), second));

                var firstRedirect = firstCall.get(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                var secondRedirect = secondCall.get(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                assertAll("concurrent scope overrides",
                        () -> assertEquals(2, builder.rendezvousCount(),
                                "both calls were inside the builder at the same time"),
                        () -> assertEquals("openid email", scopeOf(firstRedirect.authorizationUrl()),
                                "the first call keeps its own scopes"),
                        () -> assertEquals("openid phone", scopeOf(secondRedirect.authorizationUrl()),
                                "the second call keeps its own scopes"),
                        () -> assertNotEquals(firstRedirect.context().state(), secondRedirect.context().state(),
                                "each call mints its own flow context"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("Opt-in parameters")
    class OptIns {

        @Test
        @DisplayName("Should carry prompt and response_mode=query to the URL")
        void shouldCarryPromptAndQuery() {
            var options = AuthorizationRequestOptions.builder()
                    .prompt(Prompt.LOGIN).responseMode(ResponseMode.QUERY).build();

            var redirect = flow(config()).authorize(metadata(), options);

            List<Map.Entry<String, String>> parameters = wireParameters(redirect.authorizationUrl());
            assertAll("opt-in parameters",
                    () -> assertTrue(parameters.contains(Map.entry("prompt", "login")), "prompt reaches the URL"),
                    () -> assertTrue(parameters.contains(Map.entry("response_mode", "query")),
                            "response_mode=query reaches the URL"),
                    () -> assertTrue(parameters.stream().noneMatch(entry -> "form_post".equals(entry.getValue())),
                            "form_post is not requested as well"));
        }

        @Test
        @DisplayName("Should reject null options and null metadata")
        void shouldRejectNullArguments() {
            var flow = flow(config());
            var metadata = metadata();

            assertAll("null guards",
                    () -> assertEquals("options must not be null", assertThrows(NullPointerException.class,
                            () -> flow.authorize(metadata, null)).getMessage()),
                    () -> assertEquals("metadata must not be null", assertThrows(NullPointerException.class,
                            () -> flow.authorize(null, AuthorizationRequestOptions.DEFAULTS)).getMessage()));
        }

        @Test
        @DisplayName("Should refuse a client without redirect URI with the message of the option-less call")
        void shouldRefuseMissingRedirectUri() {
            var configuration = ClientConfiguration.builder()
                    .issuer(ISSUER)
                    .clientId(Generators.letterStrings(4, 16).next())
                    .clientSecret(Generators.nonBlankStrings().next())
                    .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                    .scope("openid")
                    .build();
            var flow = flow(configuration);
            var metadata = metadata();

            var plain = assertThrows(IllegalArgumentException.class, () -> flow.authorize(metadata));
            var withOptions = assertThrows(IllegalArgumentException.class,
                    () -> flow.authorize(metadata, AuthorizationRequestOptions.DEFAULTS));

            assertEquals(plain.getMessage(), withOptions.getMessage(),
                    "the options overload refuses with the option-less message");
        }
    }

    @Nested
    @DisplayName("Overridable builder")
    class Overridability {

        @Test
        @DisplayName("Should still dispatch a subclass overriding the three-argument build from authorize(metadata)")
        void shouldDispatchOverriddenThreeArgumentBuild() {
            var builder = new MarkerBuilder();

            var redirect = flow(config(), builder).authorize(metadata());

            assertAll("three-argument override",
                    () -> assertEquals(MarkerBuilder.MARKER_URL, redirect.authorizationUrl(),
                            "the overriding build supplied the URL"),
                    () -> assertEquals(1, builder.threeArgumentCalls.get(), "the override ran once"),
                    () -> assertEquals(REDIRECT_URI, builder.lastContextRedirectUri,
                            "the override received the minted flow context"));
        }
    }

    /**
     * Test double that replaces the three-argument {@code build}, as an application subclass written
     * against the pre-options API does.
     */
    static final class MarkerBuilder extends AuthorizationRequestBuilder {

        static final String MARKER_URL = "https://double.example.com/authorize?marker=three-argument";

        final AtomicInteger threeArgumentCalls = new AtomicInteger();
        String lastContextRedirectUri;

        @Override
        public String build(ClientConfiguration configuration, ProviderMetadata metadata, FlowContext context) {
            threeArgumentCalls.incrementAndGet();
            lastContextRedirectUri = context.redirectUri();
            return MARKER_URL;
        }
    }

    /**
     * Test double that holds every options call until two calls are inside it at once, so the
     * concurrency test observes two genuinely overlapping requests without any sleep.
     */
    static final class RendezvousBuilder extends AuthorizationRequestBuilder {

        private final CountDownLatch bothInFlight;
        private final AtomicInteger rendezvous = new AtomicInteger();

        RendezvousBuilder(CountDownLatch bothInFlight) {
            this.bothInFlight = bothInFlight;
        }

        @Override
        public String build(ClientConfiguration configuration, ProviderMetadata metadata, FlowContext context,
                AuthorizationRequestOptions options) {
            bothInFlight.countDown();
            try {
                if (bothInFlight.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    rendezvous.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for the concurrent call", e);
            }
            return super.build(configuration, metadata, context, options);
        }

        int rendezvousCount() {
            return rendezvous.get();
        }
    }
}
