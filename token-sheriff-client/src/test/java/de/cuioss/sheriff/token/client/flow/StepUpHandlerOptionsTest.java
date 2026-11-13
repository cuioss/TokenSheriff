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
import de.cuioss.sheriff.token.client.flow.StepUpChallengeParser.StepUpChallenge;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static de.cuioss.sheriff.token.client.flow.AuthorizationCodeFlowOptionsTest.normalized;
import static de.cuioss.sheriff.token.client.flow.AuthorizationRequestBuilderParametersTest.wireParameters;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the per-call {@link AuthorizationRequestOptions} overload of {@link StepUpHandler#initiate}:
 * {@link AuthorizationRequestOptions#DEFAULTS} reproduces the three-argument step-up request, the
 * options reach the URL while the challenge's {@code acr_values} and {@code max_age} are kept, the
 * three-argument request stays byte-identical, a subclassed {@link AuthorizationRequestBuilder} is
 * dispatched on the path that matches the entry point, and the overload refuses invalid input exactly
 * as the three-argument method does.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("StepUpHandler per-call authorization-request options (RFC 9470)")
class StepUpHandlerOptionsTest {

    private static final String ISSUER = "https://issuer.example.com";
    private static final String REDIRECT_URI = "https://rp.example.com/callback";
    private static final String AUTH_ENDPOINT = "https://issuer.example.com/authorize";
    private static final String ACR = "urn:mace:incommon:iap:silver";
    private static final int MAX_AGE = 300;

    private final StepUpHandler handler = new StepUpHandler();

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

    private static StepUpChallenge challenge() {
        return new StepUpChallenge(ACR, MAX_AGE);
    }

    @Nested
    @DisplayName("Default options")
    class Defaults {

        @Test
        @DisplayName("Should send the same parameter set with DEFAULTS as the three-argument initiate")
        void shouldMatchThreeArgumentRequest() {
            var configuration = config();

            var plain = handler.initiate(configuration, metadata(), challenge());
            var withDefaults = handler.initiate(configuration, metadata(), challenge(),
                    AuthorizationRequestOptions.DEFAULTS);

            List<Map.Entry<String, String>> parameters = wireParameters(withDefaults.authorizationUrl());
            assertAll("default overload",
                    () -> assertEquals(normalized(plain.authorizationUrl(), plain.context()),
                            normalized(withDefaults.authorizationUrl(), withDefaults.context()),
                            "DEFAULTS reproduces the names, values and order of the three-argument request"),
                    () -> assertTrue(parameters.contains(Map.entry("acr_values", ACR)),
                            "the challenge acr_values is carried"),
                    () -> assertTrue(parameters.contains(Map.entry("max_age", Integer.toString(MAX_AGE))),
                            "the challenge max_age is carried"));
        }

        @Test
        @DisplayName("Should keep the three-argument step-up URL byte-identical with its form_post default")
        void shouldKeepThreeArgumentUrlByteIdentical() {
            var request = handler.initiate(config("client-1"), metadata(), challenge());

            FlowContext context = request.context();
            String expected = AUTH_ENDPOINT
                    + "?max_age=300"
                    + "&scope=openid+profile"
                    + "&acr_values=urn%3Amace%3Aincommon%3Aiap%3Asilver"
                    + "&response_type=code"
                    + "&redirect_uri=https%3A%2F%2Frp.example.com%2Fcallback"
                    + "&state=" + context.state()
                    + "&code_challenge_method=S256"
                    + "&nonce=" + context.nonce()
                    + "&client_id=client-1"
                    + "&code_challenge=" + context.pkceChallenge().codeChallenge()
                    + "&response_mode=form_post";

            assertEquals(expected, request.authorizationUrl(),
                    "the three-argument step-up keeps its pre-refactor parameter order and encoding");
        }
    }

    static Stream<Arguments> optionFixtures() {
        Stream<Arguments> fixed = Stream.of(
                Arguments.of("response_mode=query",
                        AuthorizationRequestOptions.builder().responseMode(ResponseMode.QUERY).build(),
                        "response_mode", "query"),
                Arguments.of("scope override",
                        AuthorizationRequestOptions.builder().scopes(List.of("openid", "email")).build(),
                        "scope", "openid email"));
        Stream<Arguments> prompts = Arrays.stream(Prompt.values())
                .map(prompt -> Arguments.of("prompt=" + prompt.wireValue(),
                        AuthorizationRequestOptions.builder().prompt(prompt).build(), "prompt", prompt.wireValue()));
        return Stream.concat(fixed, prompts);
    }

    @Nested
    @DisplayName("Options through the overload")
    class Options {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.cuioss.sheriff.token.client.flow.StepUpHandlerOptionsTest#optionFixtures")
        @DisplayName("Should carry the option to the URL while keeping the challenge acr_values and max_age")
        void shouldCarryOptionAndChallenge(String fixture, AuthorizationRequestOptions options, String name,
                String value) {
            var request = handler.initiate(config(), metadata(), challenge(), options);

            List<Map.Entry<String, String>> parameters = wireParameters(request.authorizationUrl());
            assertAll(fixture,
                    () -> assertTrue(parameters.contains(Map.entry(name, value)),
                            name + "=" + value + " reaches the step-up URL"),
                    () -> assertEquals(1, parameters.stream().filter(entry -> entry.getKey().equals(name)).count(),
                            name + " is sent exactly once"),
                    () -> assertTrue(parameters.contains(Map.entry("acr_values", ACR)),
                            "the challenge acr_values is still carried"),
                    () -> assertTrue(parameters.contains(Map.entry("max_age", Integer.toString(MAX_AGE))),
                            "the challenge max_age is still carried"));
        }
    }

    @Nested
    @DisplayName("Overridable builder")
    class Overridability {

        @Test
        @DisplayName("Should dispatch the three-argument build override from the three-argument initiate only")
        void shouldDispatchThreeArgumentOverride() {
            var builder = new RecordingBuilder();

            var request = new StepUpHandler(builder).initiate(config(), metadata(), challenge());

            assertAll("three-argument path",
                    () -> assertEquals(RecordingBuilder.THREE_ARGUMENT_URL, request.authorizationUrl(),
                            "the overriding three-argument build supplied the URL"),
                    () -> assertEquals(1, builder.threeArgumentCalls.get(), "the three-argument build ran once"),
                    () -> assertEquals(0, builder.optionsCalls.get(), "the options build did not run"));
        }

        @Test
        @DisplayName("Should dispatch the options build from the overload only")
        void shouldDispatchOptionsOverride() {
            var builder = new RecordingBuilder();

            var request = new StepUpHandler(builder).initiate(config(), metadata(), challenge(),
                    AuthorizationRequestOptions.DEFAULTS);

            assertAll("options path",
                    () -> assertEquals(RecordingBuilder.OPTIONS_URL, request.authorizationUrl(),
                            "the overriding options build supplied the URL"),
                    () -> assertEquals(0, builder.threeArgumentCalls.get(), "the three-argument build did not run"),
                    () -> assertEquals(1, builder.optionsCalls.get(), "the options build ran once"));
        }
    }

    @Nested
    @DisplayName("Refusals")
    class Refusals {

        @Test
        @DisplayName("Should refuse null options with NullPointerException")
        void shouldRefuseNullOptions() {
            var configuration = config();
            var metadata = metadata();
            var challenge = challenge();

            var thrown = assertThrows(NullPointerException.class,
                    () -> handler.initiate(configuration, metadata, challenge, null));

            assertEquals("options must not be null", thrown.getMessage(), "the refusal names the options");
        }

        @Test
        @DisplayName("Should refuse a missing redirect URI with the type and message of the three-argument method")
        void shouldRefuseMissingRedirectUriLikeThreeArgument() {
            var configuration = ClientConfiguration.builder()
                    .issuer(ISSUER)
                    .clientId(Generators.letterStrings(4, 16).next())
                    .clientSecret(Generators.nonBlankStrings().next())
                    .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                    .scope("openid")
                    .build();
            var metadata = metadata();
            var challenge = challenge();

            var plain = assertThrows(IllegalArgumentException.class,
                    () -> handler.initiate(configuration, metadata, challenge));
            var withOptions = assertThrows(IllegalArgumentException.class,
                    () -> handler.initiate(configuration, metadata, challenge, AuthorizationRequestOptions.DEFAULTS));

            assertEquals(plain.getMessage(), withOptions.getMessage(),
                    "the overload refuses a missing redirect URI with the three-argument message");
        }

        @Test
        @DisplayName("Should refuse null configuration, metadata and challenge with the three-argument messages")
        void shouldRefuseNullArgumentsLikeThreeArgument() {
            var configuration = config();
            var metadata = metadata();
            var challenge = challenge();
            var options = AuthorizationRequestOptions.DEFAULTS;

            assertAll("null guards",
                    () -> assertEquals(
                            assertThrows(NullPointerException.class,
                                    () -> handler.initiate(null, metadata, challenge)).getMessage(),
                            assertThrows(NullPointerException.class,
                                    () -> handler.initiate(null, metadata, challenge, options)).getMessage(),
                            "null configuration"),
                    () -> assertEquals(
                            assertThrows(NullPointerException.class,
                                    () -> handler.initiate(configuration, null, challenge)).getMessage(),
                            assertThrows(NullPointerException.class,
                                    () -> handler.initiate(configuration, null, challenge, options)).getMessage(),
                            "null metadata"),
                    () -> assertEquals(
                            assertThrows(NullPointerException.class,
                                    () -> handler.initiate(configuration, metadata, null)).getMessage(),
                            assertThrows(NullPointerException.class,
                                    () -> handler.initiate(configuration, metadata, null, options)).getMessage(),
                            "null challenge"));
        }
    }

    /**
     * Test double that overrides both {@code build} variants with distinguishable URLs and counts the
     * calls each receives.
     */
    static final class RecordingBuilder extends AuthorizationRequestBuilder {

        static final String THREE_ARGUMENT_URL = "https://double.example.com/authorize?marker=three-argument";
        static final String OPTIONS_URL = "https://double.example.com/authorize?marker=options";

        final AtomicInteger threeArgumentCalls = new AtomicInteger();
        final AtomicInteger optionsCalls = new AtomicInteger();

        @Override
        public String build(ClientConfiguration configuration, ProviderMetadata metadata, FlowContext context) {
            threeArgumentCalls.incrementAndGet();
            return THREE_ARGUMENT_URL;
        }

        @Override
        public String build(ClientConfiguration configuration, ProviderMetadata metadata, FlowContext context,
                AuthorizationRequestOptions options) {
            optionsCalls.incrementAndGet();
            return OPTIONS_URL;
        }
    }
}
