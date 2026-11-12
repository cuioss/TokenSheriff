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

import de.cuioss.http.security.database.AttackTestCase;
import de.cuioss.http.security.database.OWASPTop10AttackDatabase;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.ProviderMetadata;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static de.cuioss.sheriff.token.client.flow.AuthorizationRequestBuilderParametersTest.wireParameters;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Drives parameter-injection payloads through every caller-supplied value of an authorization
 * request — {@link ResponseMode#of}, {@link Prompt#of} and the scope override of
 * {@link AuthorizationRequestOptions} — and asserts that no payload can add, duplicate or split a
 * request parameter: a payload is either refused before it reaches a URL or, for a scope value carrying
 * only {@code &}, {@code =} or {@code #}, percent-encoded so every parameter name appears exactly once.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("Authorization request fail-closed against parameter injection")
class AuthorizationRequestInjectionAttackDatabaseTest {

    private static final String REDIRECT_URI = "https://rp.example.com/callback";
    private static final String AUTH_ENDPOINT = "https://issuer.example.com/authorize";
    private static final List<String> REQUEST_PARAMETERS = List.of("scope", "response_type", "redirect_uri",
            "state", "code_challenge_method", "nonce", "client_id", "code_challenge", "response_mode");

    private final AuthorizationRequestBuilder builder = new AuthorizationRequestBuilder();

    /**
     * Payloads that carry whitespace ({@code CR}, {@code LF}, tab, space): refused by every entry point.
     */
    static Stream<String> whitespacePayloads() {
        return Stream.of(
                "openid\r\nX: y",
                "openid\r\nresponse_mode: query",
                "login\nprompt=consent",
                "form_post\r",
                "openid scope=admin",
                "openid\tadmin",
                "query &response_mode=form_post",
                "none\u000Bprompt=login");
    }

    /**
     * Payloads that carry only the delimiters {@code &}, {@code =} and {@code #}: refused by the
     * closed-set values, percent-encoded as a scope value.
     */
    static Stream<String> delimiterPayloads() {
        return Stream.of(
                "form_post&response_mode=query",
                "none&prompt=login",
                "openid&scope=admin",
                "openid&client_id=attacker",
                "openid&redirect_uri=https://evil.example/cb",
                "openid&state=forged&nonce=forged",
                "query#fragment",
                "openid#&code_challenge_method=plain",
                "form_post=query",
                "scope=openid",
                "&",
                "=",
                "#",
                "&&==##");
    }

    static Stream<String> allPayloads() {
        return Stream.concat(whitespacePayloads(), delimiterPayloads());
    }

    private static ClientConfiguration config() {
        return ClientConfiguration.builder()
                .issuer("https://issuer.example.com")
                .clientId(Generators.letterStrings(4, 16).next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .redirectUri(REDIRECT_URI)
                .build();
    }

    private static ProviderMetadata metadata() {
        var metadata = new ProviderMetadata();
        metadata.issuer = "https://issuer.example.com";
        metadata.authorizationEndpoint = AUTH_ENDPOINT;
        metadata.codeChallengeMethodsSupported = List.of("S256");
        return metadata;
    }

    private static Map<String, Long> nameCounts(String url) {
        return wireParameters(url).stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.counting()));
    }

    @ParameterizedTest
    @MethodSource("allPayloads")
    @DisplayName("Should refuse an injection payload as a response_mode value")
    void refusesPayloadAsResponseMode(String payload) {
        assertThrows(IllegalArgumentException.class, () -> ResponseMode.of(payload),
                "response_mode never accepts an injection payload");
    }

    @ParameterizedTest
    @MethodSource("allPayloads")
    @DisplayName("Should refuse an injection payload as a prompt value")
    void refusesPayloadAsPrompt(String payload) {
        assertThrows(IllegalArgumentException.class, () -> Prompt.of(payload),
                "prompt never accepts an injection payload");
    }

    @ParameterizedTest
    @ArgumentsSource(OWASPTop10AttackDatabase.ArgumentsProvider.class)
    @DisplayName("Should refuse an OWASP Top 10 attack payload as a response_mode or prompt value")
    void refusesOwaspPayloadAsClosedSetValue(AttackTestCase testCase) {
        String payload = testCase.attackString();

        assertAll("closed-set values refuse an OWASP payload",
                () -> assertThrows(IllegalArgumentException.class, () -> ResponseMode.of(payload)),
                () -> assertThrows(IllegalArgumentException.class, () -> Prompt.of(payload)));
    }

    @ParameterizedTest
    @MethodSource("whitespacePayloads")
    @DisplayName("Should refuse a scope override carrying whitespace before it reaches a URL")
    void refusesWhitespaceScopePayload(String payload) {
        var optionsBuilder = AuthorizationRequestOptions.builder().scopes(List.of("openid", payload));

        assertThrows(IllegalArgumentException.class, optionsBuilder::build,
                "a scope entry carrying whitespace would split into further tokens or header lines");
    }

    @ParameterizedTest
    @MethodSource("delimiterPayloads")
    @DisplayName("Should percent-encode a delimiter scope payload so every parameter appears exactly once")
    void encodesDelimiterScopePayload(String payload) {
        var options = AuthorizationRequestOptions.builder().scopes(List.of("openid", payload)).build();
        var context = FlowContext.create(REDIRECT_URI);

        String url = builder.build(config(), metadata(), context, options);
        Map<String, Long> counts = nameCounts(url);

        assertAll("payload stays inside the scope value",
                () -> assertEquals(REQUEST_PARAMETERS.stream().collect(Collectors.toMap(n -> n, n -> 1L)), counts,
                        "every request parameter appears exactly once and no parameter is added"),
                () -> assertEquals("openid " + payload, builder.parameters(config(), metadata(), context, options)
                        .get("scope"), "the map carries the payload verbatim as scope data"),
                () -> assertEquals("openid " + payload, wireParameters(url).getFirst().getValue(),
                        "the URL decodes back to the payload verbatim"),
                () -> assertFalse(url.contains("#"), "no fragment delimiter reaches the URL raw"),
                () -> assertEquals("form_post", wireParameters(url).getLast().getValue(),
                        "the response_mode is the one the options requested"));
    }

    @Test
    @DisplayName("Should encode an oversize scope list completely into exactly one scope parameter")
    void encodesOversizeScopeListCompletely() {
        List<String> scopes = IntStream.range(0, 5_000).mapToObj(i -> "scope" + i).toList();
        var options = AuthorizationRequestOptions.builder().scopes(scopes).build();

        String url = builder.build(config(), metadata(), FlowContext.create(REDIRECT_URI), options);
        String scopeValue = wireParameters(url).stream().filter(entry -> "scope".equals(entry.getKey()))
                .map(Map.Entry::getValue).collect(Collectors.joining());

        assertAll("oversize scope list",
                () -> assertEquals(1L, nameCounts(url).get("scope"), "one scope parameter"),
                () -> assertEquals(String.join(" ", scopes), scopeValue, "every scope is encoded, none truncated"),
                () -> assertEquals(5_000, scopeValue.split(" ").length, "all 5000 scope-tokens are carried"));
    }
}
