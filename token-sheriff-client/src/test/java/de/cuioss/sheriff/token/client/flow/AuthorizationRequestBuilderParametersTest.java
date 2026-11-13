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
import de.cuioss.sheriff.token.commons.error.ClientProtocolException;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies {@link AuthorizationRequestBuilder#parameters} against the URL that
 * {@link AuthorizationRequestBuilder#build} emits: same names, same values, same order, same
 * refusals, and a default URL that stays byte-identical to the request the builder has always sent.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("AuthorizationRequestBuilder ordered parameter map")
class AuthorizationRequestBuilderParametersTest {

    private static final String ISSUER = "https://issuer.example.com";
    private static final String REDIRECT_URI = "https://rp.example.com/callback";
    private static final String AUTH_ENDPOINT = "https://issuer.example.com/authorize";
    private static final String ACR = "urn:mace:incommon:iap:silver";
    private static final int MAX_AGE = 300;

    private final AuthorizationRequestBuilder builder = new AuthorizationRequestBuilder();

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

    private static ProviderMetadata metadata(@Nullable String authorizationEndpoint, boolean s256) {
        var metadata = new ProviderMetadata();
        metadata.issuer = ISSUER;
        if (authorizationEndpoint != null) {
            metadata.authorizationEndpoint = authorizationEndpoint;
        }
        metadata.codeChallengeMethodsSupported = List.of(s256 ? "S256" : "plain");
        return metadata;
    }

    private static ProviderMetadata metadata() {
        return metadata(AUTH_ENDPOINT, true);
    }

    /**
     * Decodes the query of a built URL into its parameters, in wire order, keeping duplicates.
     */
    static List<Map.Entry<String, String>> wireParameters(String url) {
        List<Map.Entry<String, String>> pairs = new ArrayList<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            int eq = pair.indexOf('=');
            pairs.add(Map.entry(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8)));
        }
        return pairs;
    }

    static long occurrences(String url, String name) {
        return wireParameters(url).stream().filter(entry -> entry.getKey().equals(name)).count();
    }

    static Stream<Arguments> fixtures() {
        return Stream.of(
                Arguments.of("default", null, null, AuthorizationRequestOptions.DEFAULTS),
                Arguments.of("query", null, null,
                        AuthorizationRequestOptions.builder().responseMode(ResponseMode.QUERY).build()),
                Arguments.of("prompt", null, null,
                        AuthorizationRequestOptions.builder().prompt(Prompt.LOGIN).build()),
                Arguments.of("scope override", null, null,
                        AuthorizationRequestOptions.builder().scopes(List.of("openid", "email", "offline_access"))
                                .build()),
                Arguments.of("acr_values", ACR, null, AuthorizationRequestOptions.DEFAULTS),
                Arguments.of("max_age", null, MAX_AGE, AuthorizationRequestOptions.DEFAULTS),
                Arguments.of("every option", ACR, MAX_AGE, AuthorizationRequestOptions.builder()
                        .scopes(List.of("openid")).responseMode(ResponseMode.QUERY).prompt(Prompt.CONSENT).build()));
    }

    @Nested
    @DisplayName("Map mirrors the built URL")
    class MirrorsBuild {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.cuioss.sheriff.token.client.flow.AuthorizationRequestBuilderParametersTest#fixtures")
        @DisplayName("Should hold exactly the names and values build emits, in the same order")
        void shouldMatchBuiltUrl(String fixture, @Nullable String acrValues, @Nullable Integer maxAge,
                AuthorizationRequestOptions options) {
            var configuration = config();
            var context = FlowContext.create(REDIRECT_URI, acrValues, maxAge);

            Map<String, String> parameters = builder.parameters(configuration, metadata(), context, options);
            String url = builder.build(configuration, metadata(), context, options);

            assertEquals(wireParameters(url), List.copyOf(parameters.entrySet()),
                    "the map and the URL carry the same parameters in the same order for fixture " + fixture);
        }

        @Test
        @DisplayName("Should emit the same URL from both build overloads when the options are the defaults")
        void shouldMatchDefaultOverload() {
            var configuration = config();
            var context = FlowContext.create(REDIRECT_URI, ACR, MAX_AGE);

            assertEquals(builder.build(configuration, metadata(), context),
                    builder.build(configuration, metadata(), context, AuthorizationRequestOptions.DEFAULTS),
                    "DEFAULTS reproduces the URL of the three-argument build");
        }
    }

    @Nested
    @DisplayName("Byte-identical default request")
    class Golden {

        @Test
        @DisplayName("Should keep the default URL byte-identical to the request emitted before the refactor")
        void shouldKeepDefaultUrlByteIdentical() {
            var context = FlowContext.create(REDIRECT_URI);
            String expected = AUTH_ENDPOINT
                    + "?scope=openid+profile"
                    + "&response_type=code"
                    + "&redirect_uri=https%3A%2F%2Frp.example.com%2Fcallback"
                    + "&state=" + context.state()
                    + "&code_challenge_method=S256"
                    + "&nonce=" + context.nonce()
                    + "&client_id=client-1"
                    + "&code_challenge=" + context.pkceChallenge().codeChallenge()
                    + "&response_mode=form_post";

            assertEquals(expected, builder.build(config("client-1"), metadata(), context),
                    "the default request keeps its pre-refactor parameter order and encoding");
        }

        @Test
        @DisplayName("Should keep the step-up URL byte-identical to the request emitted before the refactor")
        void shouldKeepStepUpUrlByteIdentical() {
            var context = FlowContext.create(REDIRECT_URI, ACR, MAX_AGE);
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

            assertEquals(expected, builder.build(config("client-1"), metadata(), context),
                    "max_age leads and acr_values follows scope, as in the pre-refactor request");
        }
    }

    @Nested
    @DisplayName("Optional parameters")
    class OptionalParameters {

        @Test
        @DisplayName("Should omit every absent optional parameter from the map")
        void shouldOmitAbsentOptionalParameters() {
            var configuration = ClientConfiguration.builder()
                    .issuer(ISSUER)
                    .clientId(Generators.letterStrings(4, 16).next())
                    .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                    .redirectUri(REDIRECT_URI)
                    .build();
            var context = FlowContext.create(REDIRECT_URI);

            Map<String, String> parameters = builder.parameters(configuration, metadata(), context,
                    AuthorizationRequestOptions.DEFAULTS);

            assertAll("absent optional parameters",
                    () -> assertFalse(parameters.containsKey("scope"), "no scope without configured scopes"),
                    () -> assertFalse(parameters.containsKey("prompt"), "no prompt without the option"),
                    () -> assertFalse(parameters.containsKey("acr_values"), "no acr_values without step-up"),
                    () -> assertFalse(parameters.containsKey("max_age"), "no max_age without step-up"),
                    () -> assertEquals(List.of("response_type", "redirect_uri", "state", "code_challenge_method",
                                    "nonce", "client_id", "code_challenge", "response_mode"),
                            List.copyOf(parameters.keySet()), "only the mandatory parameters remain"));
        }

        @Test
        @DisplayName("Should send the prompt exactly once")
        void shouldSendPromptExactlyOnce() {
            var options = AuthorizationRequestOptions.builder().prompt(Prompt.SELECT_ACCOUNT).build();
            var context = FlowContext.create(REDIRECT_URI);

            String url = builder.build(config(), metadata(), context, options);
            Map<String, String> parameters = builder.parameters(config(), metadata(), context, options);

            assertAll("single prompt",
                    () -> assertEquals(1, occurrences(url, "prompt"), "the URL carries one prompt parameter"),
                    () -> assertEquals("select_account", parameters.get("prompt"), "the map carries the wire value"));
        }

        @Test
        @DisplayName("Should request response_mode=query when the query option is chosen")
        void shouldRequestQueryResponseMode() {
            var options = AuthorizationRequestOptions.builder().responseMode(ResponseMode.QUERY).build();
            var context = FlowContext.create(REDIRECT_URI);

            String url = builder.build(config(), metadata(), context, options);

            assertAll("query response mode",
                    () -> assertEquals(1, occurrences(url, "response_mode"), "one response_mode parameter"),
                    () -> assertEquals("query", builder.parameters(config(), metadata(), context, options)
                            .get("response_mode"), "the map requests query"),
                    () -> assertFalse(url.contains("form_post"), "form_post is not requested as well"));
        }

        @Test
        @DisplayName("Should request the overriding scopes instead of the configured scopes")
        void shouldRequestOverridingScopes() {
            var options = AuthorizationRequestOptions.builder().scopes(List.of("openid", "email")).build();

            Map<String, String> parameters = builder.parameters(config(), metadata(),
                    FlowContext.create(REDIRECT_URI), options);

            assertEquals("openid email", parameters.get("scope"), "the override replaces openid profile");
        }
    }

    @Nested
    @DisplayName("Unmodifiable map")
    class Unmodifiable {

        @Test
        @DisplayName("Should refuse put, remove and clear")
        void shouldRefuseMutation() {
            Map<String, String> parameters = builder.parameters(config(), metadata(),
                    FlowContext.create(REDIRECT_URI), AuthorizationRequestOptions.DEFAULTS);

            assertAll("unmodifiable",
                    () -> assertThrows(UnsupportedOperationException.class,
                            () -> parameters.put("prompt", "none"), "put is refused"),
                    () -> assertThrows(UnsupportedOperationException.class,
                            () -> parameters.remove("state"), "remove is refused"),
                    () -> assertThrows(UnsupportedOperationException.class, parameters::clear, "clear is refused"));
        }
    }

    static Stream<Arguments> invalidMetadata() {
        return Stream.of(
                Arguments.of("missing endpoint", metadata(null, true)),
                Arguments.of("non-TLS endpoint", metadata("http://issuer.example.com/authorize", true)),
                Arguments.of("missing S256", metadata(AUTH_ENDPOINT, false)));
    }

    @Nested
    @DisplayName("Same refusals as build")
    class Refusals {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.cuioss.sheriff.token.client.flow.AuthorizationRequestBuilderParametersTest#invalidMetadata")
        @DisplayName("Should raise the same exception type and message from parameters as from build")
        void shouldRefuseLikeBuild(String fixture, ProviderMetadata metadata) {
            var configuration = config();
            var context = FlowContext.create(REDIRECT_URI);
            var options = AuthorizationRequestOptions.DEFAULTS;

            var fromBuild = assertThrows(ClientProtocolException.class,
                    () -> builder.build(configuration, metadata, context, options), fixture + " refused by build");
            var fromParameters = assertThrows(ClientProtocolException.class,
                    () -> builder.parameters(configuration, metadata, context, options),
                    fixture + " refused by parameters");

            assertEquals(fromBuild.getMessage(), fromParameters.getMessage(),
                    "parameters reports the " + fixture + " refusal with the message build uses");
        }

        @Test
        @DisplayName("Should reject null arguments")
        void shouldRejectNullArguments() {
            var configuration = config();
            var metadata = metadata();
            var context = FlowContext.create(REDIRECT_URI);
            var options = AuthorizationRequestOptions.DEFAULTS;

            assertAll("null guards",
                    () -> assertThrows(NullPointerException.class,
                            () -> builder.parameters(null, metadata, context, options)),
                    () -> assertThrows(NullPointerException.class,
                            () -> builder.parameters(configuration, null, context, options)),
                    () -> assertThrows(NullPointerException.class,
                            () -> builder.parameters(configuration, metadata, null, options)),
                    () -> assertThrows(NullPointerException.class,
                            () -> builder.parameters(configuration, metadata, context, null)),
                    () -> assertThrows(NullPointerException.class,
                            () -> builder.build(configuration, metadata, context, null)));
        }
    }
}
