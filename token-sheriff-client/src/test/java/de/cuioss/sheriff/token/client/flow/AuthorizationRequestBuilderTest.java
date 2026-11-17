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
import de.cuioss.sheriff.token.commons.error.ClientProtocolException;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@EnableGeneratorController
@DisplayName("AuthorizationRequestBuilder authorization_code + PKCE S256 request")
class AuthorizationRequestBuilderTest {

    private static final String REDIRECT_URI = "https://rp.example.com/callback";
    private static final String AUTH_ENDPOINT = "https://issuer.example.com/authorize";
    private static final String RESPONSE_MODE_FORM_POST = "form_post";

    private final AuthorizationRequestBuilder builder = new AuthorizationRequestBuilder();

    private static ClientConfiguration config() {
        return config(Generators.nonBlankStrings().next());
    }

    private static ClientConfiguration config(String clientId) {
        return ClientConfiguration.builder()
                .issuer("https://issuer.example.com")
                .clientId(clientId)
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .scope("profile")
                .redirectUri(REDIRECT_URI)
                .build();
    }

    private static ProviderMetadata metadataWithS256(String authorizationEndpoint) {
        var metadata = new ProviderMetadata();
        metadata.issuer = "https://issuer.example.com";
        metadata.authorizationEndpoint = authorizationEndpoint;
        metadata.codeChallengeMethodsSupported = List.of("S256");
        return metadata;
    }

    private static Map<String, String> queryParams(String url) {
        Map<String, String> params = new HashMap<>();
        String query = URI.create(url).getRawQuery();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("Should carry every mandatory authorization_code + PKCE parameter")
        void shouldCarryMandatoryParameters() {
            var configuration = config();
            var context = FlowContext.create(REDIRECT_URI);

            String url = builder.build(configuration, metadataWithS256(AUTH_ENDPOINT), context);
            Map<String, String> params = queryParams(url);

            assertAll("authorization request parameters",
                    () -> assertTrue(url.startsWith(AUTH_ENDPOINT + "?"), "URL starts at the authorization endpoint"),
                    () -> assertEquals("code", params.get("response_type"), "response_type is 'code'"),
                    () -> assertEquals(configuration.getClientId(), params.get("client_id"), "client_id is carried"),
                    () -> assertEquals(REDIRECT_URI, params.get("redirect_uri"), "exact redirect_uri is carried"),
                    () -> assertEquals("openid profile", params.get("scope"), "space-delimited scopes are carried"),
                    () -> assertEquals(context.state(), params.get("state"), "state is carried"),
                    () -> assertEquals(context.nonce(), params.get("nonce"), "nonce is carried"),
                    () -> assertEquals(context.pkceChallenge().codeChallenge(), params.get("code_challenge"),
                            "PKCE code_challenge is carried"),
                    () -> assertEquals("S256", params.get("code_challenge_method"),
                            "PKCE method is always S256"));
        }

        @Test
        @DisplayName("Should request response_mode=form_post so the response is not leaked in the redirect URL")
        void shouldRequestFormPostResponseMode() {
            var context = FlowContext.create(REDIRECT_URI);

            String url = builder.build(config(), metadataWithS256(AUTH_ENDPOINT), context);

            assertEquals(RESPONSE_MODE_FORM_POST, queryParams(url).get("response_mode"),
                    "response_mode=form_post keeps code/state/iss out of the redirect URL (T-URL-LEAK)");
        }

        @Test
        @DisplayName("Should never leak the secret PKCE code_verifier into the front-channel URL")
        void shouldNotLeakVerifier() {
            var context = FlowContext.create(REDIRECT_URI);

            String url = builder.build(config(), metadataWithS256(AUTH_ENDPOINT), context);

            assertFalse(url.contains(context.pkceChallenge().codeVerifier()),
                    "the code_verifier is a secret and must never appear on the authorization request");
        }

        @Test
        @DisplayName("Should append acr_values when the flow context requests step-up")
        void shouldAppendAcrValues() {
            String acr = "urn:mace:incommon:iap:silver";
            var context = FlowContext.create(REDIRECT_URI, acr);

            String url = builder.build(config(), metadataWithS256(AUTH_ENDPOINT), context);

            assertEquals(acr, queryParams(url).get("acr_values"), "requested acr_values are appended");
        }

        @Test
        @DisplayName("Should omit acr_values when step-up is not requested")
        void shouldOmitAcrValues() {
            var context = FlowContext.create(REDIRECT_URI);

            String url = builder.build(config(), metadataWithS256(AUTH_ENDPOINT), context);

            assertFalse(queryParams(url).containsKey("acr_values"), "acr_values is absent without step-up");
        }

        @Test
        @DisplayName("Should omit scope when no scopes are configured")
        void shouldOmitScopeWhenEmpty() {
            var configuration = ClientConfiguration.builder()
                    .issuer("https://issuer.example.com")
                    .clientId(Generators.nonBlankStrings().next())
                    .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                    .redirectUri(REDIRECT_URI)
                    .build();
            var context = FlowContext.create(REDIRECT_URI);

            String url = builder.build(configuration, metadataWithS256(AUTH_ENDPOINT), context);

            assertFalse(queryParams(url).containsKey("scope"), "scope is omitted when no scopes are configured");
        }

        @Test
        @DisplayName("Should join with '&' when the authorization endpoint already carries a query string")
        void shouldJoinWithAmpersandWhenEndpointHasQuery() {
            var context = FlowContext.create(REDIRECT_URI);

            String url = builder.build(config(), metadataWithS256(AUTH_ENDPOINT + "?ui_locales=de"), context);

            assertAll("query joining",
                    () -> assertTrue(url.startsWith(AUTH_ENDPOINT + "?ui_locales=de&"),
                            "existing query is preserved and joined with '&'"),
                    () -> assertEquals("de", queryParams(url).get("ui_locales"), "the pre-existing parameter survives"));
        }
    }

    @Nested
    @DisplayName("Endpoint fragment (C-4)")
    class EndpointFragment {

        @Test
        @DisplayName("Should insert every parameter before the endpoint fragment and keep the fragment unchanged")
        void shouldInsertParametersBeforeFragment() {
            var context = FlowContext.create(REDIRECT_URI);

            String url = builder.build(config(), metadataWithS256(AUTH_ENDPOINT + "#frag"), context);
            Map<String, String> params = queryParams(url);

            assertAll("fragment-aware query",
                    () -> assertTrue(url.startsWith(AUTH_ENDPOINT + "?"), "the query starts after the path"),
                    () -> assertEquals("#frag", url.substring(url.indexOf('#')), "the fragment is kept at the end"),
                    () -> assertEquals(context.state(), params.get("state"), "state reaches the query"),
                    () -> assertEquals(context.nonce(), params.get("nonce"), "nonce reaches the query"),
                    () -> assertEquals(context.pkceChallenge().codeChallenge(), params.get("code_challenge"),
                            "the PKCE challenge reaches the query"),
                    () -> assertEquals("code", params.get("response_type"), "response_type reaches the query"));
        }

        @Test
        @DisplayName("Should join an existing query with '&' before the endpoint fragment")
        void shouldJoinExistingQueryBeforeFragment() {
            var context = FlowContext.create(REDIRECT_URI);

            String url = builder.build(config(), metadataWithS256(AUTH_ENDPOINT + "?a=b#frag"), context);

            assertAll("existing query and fragment",
                    () -> assertTrue(url.startsWith(AUTH_ENDPOINT + "?a=b&"), "joins the existing query with '&'"),
                    () -> assertEquals("#frag", url.substring(url.indexOf('#')), "the fragment is kept at the end"),
                    () -> assertEquals("b", queryParams(url).get("a"), "the pre-existing parameter survives"),
                    () -> assertEquals(context.state(), queryParams(url).get("state"), "state reaches the query"));
        }

        @Test
        @DisplayName("Should not treat a '?' inside the endpoint fragment as the query separator")
        void shouldIgnoreQuestionMarkInsideFragment() {
            var context = FlowContext.create(REDIRECT_URI);

            String url = builder.build(config(), metadataWithS256(AUTH_ENDPOINT + "#frag?x=y"), context);

            assertAll("question mark inside the fragment",
                    () -> assertTrue(url.startsWith(AUTH_ENDPOINT + "?"), "the query is opened with '?'"),
                    () -> assertEquals("#frag?x=y", url.substring(url.indexOf('#')),
                            "the fragment is kept unchanged at the end"),
                    () -> assertEquals(context.state(), queryParams(url).get("state"), "state reaches the query"),
                    () -> assertFalse(queryParams(url).containsKey("x"), "the fragment content is not a parameter"));
        }

        @Test
        @DisplayName("Should keep the URL for an endpoint without a fragment unchanged")
        void shouldKeepUrlWithoutFragmentUnchanged() {
            var context = FlowContext.create(REDIRECT_URI);

            String url = builder.build(config(), metadataWithS256(AUTH_ENDPOINT), context);

            assertAll("no fragment",
                    () -> assertTrue(url.startsWith(AUTH_ENDPOINT + "?"), "the query directly follows the endpoint"),
                    () -> assertFalse(url.contains("#"), "no fragment is introduced"));
        }
    }

    @Nested
    @DisplayName("Fail-closed")
    class FailClosed {

        @Test
        @DisplayName("Should reject a metadata document without an authorization endpoint")
        void shouldRejectMissingAuthorizationEndpoint() {
            var metadata = new ProviderMetadata();
            metadata.codeChallengeMethodsSupported = List.of("S256");
            var context = FlowContext.create(REDIRECT_URI);
            var configuration = config();

            assertThrows(ClientProtocolException.class,
                    () -> builder.build(configuration, metadata, context));
        }

        @Test
        @DisplayName("Should reject null arguments")
        void shouldRejectNullArguments() {
            var metadata = metadataWithS256(AUTH_ENDPOINT);
            var context = FlowContext.create(REDIRECT_URI);
            var configuration = config();

            assertAll("null-guards",
                    () -> assertThrows(NullPointerException.class,
                            () -> builder.build(null, metadata, context)),
                    () -> assertThrows(NullPointerException.class,
                            () -> builder.build(configuration, null, context)),
                    () -> assertThrows(NullPointerException.class,
                            () -> builder.build(configuration, metadata, null)));
        }
    }

    @Nested
    @DisplayName("Endpoint-scheme enforcement (L5)")
    class SchemeEnforcement {

        private static final String HTTP_ENDPOINT = "http://issuer.example.com/authorize";

        private static ClientConfiguration config(boolean allowInsecureHttp) {
            return ClientConfiguration.builder()
                    .issuer("https://issuer.example.com")
                    .clientId(Generators.nonBlankStrings().next())
                    .clientSecret(Generators.nonBlankStrings().next())
                    .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                    .scope("openid")
                    .redirectUri(REDIRECT_URI)
                    .allowInsecureHttp(allowInsecureHttp)
                    .build();
        }

        @Test
        @DisplayName("Should always accept an https endpoint, whether or not insecure http is allowed")
        void shouldAcceptHttpsRegardlessOfInsecureFlag() {
            var context = FlowContext.create(REDIRECT_URI);

            assertAll("https always allowed",
                    () -> assertTrue(builder.build(config(false), metadataWithS256(AUTH_ENDPOINT), context)
                            .startsWith(AUTH_ENDPOINT + "?"), "https accepted when insecure http is not allowed"),
                    () -> assertTrue(builder.build(config(true), metadataWithS256(AUTH_ENDPOINT), context)
                            .startsWith(AUTH_ENDPOINT + "?"), "https accepted when insecure http is allowed"));
        }

        @Test
        @DisplayName("Should accept a cleartext http endpoint only when insecure http is explicitly allowed")
        void shouldAcceptHttpOnlyWhenAllowed() {
            var context = FlowContext.create(REDIRECT_URI);

            String url = builder.build(config(true), metadataWithS256(HTTP_ENDPOINT), context);

            assertTrue(url.startsWith(HTTP_ENDPOINT + "?"),
                    "a cleartext http endpoint is permitted for a local test setup when allowInsecureHttp is set");
        }

        @Test
        @DisplayName("Should reject a cleartext http endpoint when insecure http is not allowed")
        void shouldRejectHttpWhenNotAllowed() {
            var configuration = config(false);
            var metadata = metadataWithS256(HTTP_ENDPOINT);
            var context = FlowContext.create(REDIRECT_URI);

            assertThrows(ClientProtocolException.class,
                    () -> builder.build(configuration, metadata, context),
                    "a non-TLS endpoint must be refused unless insecure http is explicitly allowed");
        }

        @Test
        @DisplayName("Should reject a non-http(s) scheme even when insecure http is allowed")
        void shouldRejectForeignSchemeEvenWhenInsecureAllowed() {
            var configuration = config(true);
            var context = FlowContext.create(REDIRECT_URI);
            var ftp = metadataWithS256("ftp://issuer.example.com/authorize");
            var file = metadataWithS256("file:///etc/passwd");

            assertAll("only literal http is tolerated under allowInsecureHttp",
                    () -> assertThrows(ClientProtocolException.class,
                            () -> builder.build(configuration, ftp, context),
                            "an ftp endpoint is never a valid authorization endpoint"),
                    () -> assertThrows(ClientProtocolException.class,
                            () -> builder.build(configuration, file, context),
                            "a file endpoint is never a valid authorization endpoint"));
        }

        @Test
        @DisplayName("Should reject an endpoint with no scheme (relative URL) even when insecure http is allowed")
        void shouldRejectSchemelessEndpoint() {
            var configuration = config(true);
            var metadata = metadataWithS256("/authorize");
            var context = FlowContext.create(REDIRECT_URI);

            assertThrows(ClientProtocolException.class,
                    () -> builder.build(configuration, metadata, context),
                    "a schemeless (relative) authorization endpoint must be rejected");
        }
    }

    @Nested
    @DisplayName("Routing of the three-argument build (ADR-0006: one overridable entry point)")
    class Routing {

        private static ClientConfiguration fixedConfig() {
            return config("client-1");
        }

        private static AuthorizationCodeFlow flow(ClientConfiguration configuration,
                AuthorizationRequestBuilder requestBuilder) {
            TokenValidator validator = TokenValidator.builder()
                    .issuerConfig(TestTokenGenerators.accessTokens().next().getIssuerConfig()).build();
            return new AuthorizationCodeFlow(configuration, new TokenEndpointClient(configuration),
                    new TokenValidationBridge(validator), new IdTokenValidationBridge(validator), new IssValidator(),
                    requestBuilder, new CallbackHandler(), null);
        }

        @Test
        @DisplayName("Should emit the pinned default URL from the three-argument build, equal to the DEFAULTS call")
        void shouldEmitPinnedDefaultUrl() {
            var configuration = fixedConfig();
            var metadata = metadataWithS256(AUTH_ENDPOINT);
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
                    + "&response_mode=" + RESPONSE_MODE_FORM_POST;

            String threeArgument = builder.build(configuration, metadata, context);

            assertAll("the three-argument build delegates without changing the default request",
                    () -> assertEquals(expected, threeArgument, "the default URL keeps every byte"),
                    () -> assertEquals(builder.build(configuration, metadata, context,
                                    AuthorizationRequestOptions.DEFAULTS), threeArgument,
                            "the three-argument build equals the four-argument build with DEFAULTS"));
        }

        @Test
        @DisplayName("Should reach a four-argument override through the three-argument build, with DEFAULTS")
        void shouldReachFourArgumentOverrideThroughThreeArgumentBuild() {
            var overriding = new FourArgumentMarkerBuilder();
            var context = FlowContext.create(REDIRECT_URI);

            String url = overriding.build(fixedConfig(), metadataWithS256(AUTH_ENDPOINT), context);

            assertAll("the four-argument override governs the three-argument callers",
                    () -> assertEquals(FourArgumentMarkerBuilder.MARKER_URL, url, "the override supplied the URL"),
                    () -> assertEquals(1, overriding.calls.get(), "the override ran once"),
                    () -> assertSame(AuthorizationRequestOptions.DEFAULTS, overriding.lastOptions,
                            "the three-argument build passes DEFAULTS"));
        }

        @Test
        @DisplayName("Should reach a four-argument override through AuthorizationCodeFlow.authorize(metadata)")
        void shouldReachFourArgumentOverrideThroughAuthorize() {
            var overriding = new FourArgumentMarkerBuilder();
            var configuration = fixedConfig();

            var redirect = flow(configuration, overriding).authorize(metadataWithS256(AUTH_ENDPOINT));

            assertAll("authorize(metadata) runs the four-argument override",
                    () -> assertEquals(FourArgumentMarkerBuilder.MARKER_URL, redirect.authorizationUrl(),
                            "the override supplied the authorization URL"),
                    () -> assertEquals(1, overriding.calls.get(), "the override ran once"),
                    () -> assertSame(AuthorizationRequestOptions.DEFAULTS, overriding.lastOptions,
                            "authorize(metadata) requests the defaults"));
        }

        @Test
        @DisplayName("Should leave the four-argument result alone when a subclass overrides only the three-argument build")
        void shouldNotRouteFourArgumentBuildThroughThreeArgumentOverride() {
            var overriding = new ThreeArgumentMarkerBuilder();
            var configuration = fixedConfig();
            var metadata = metadataWithS256(AUTH_ENDPOINT);
            var context = FlowContext.create(REDIRECT_URI);

            String fourArgument = overriding.build(configuration, metadata, context,
                    AuthorizationRequestOptions.DEFAULTS);

            assertAll("negative control: delegation runs from three to four arguments only",
                    () -> assertEquals(builder.build(configuration, metadata, context,
                                    AuthorizationRequestOptions.DEFAULTS), fourArgument,
                            "the four-argument build is the unmodified one"),
                    () -> assertEquals(0, overriding.calls.get(), "the three-argument override never ran"),
                    () -> assertEquals(ThreeArgumentMarkerBuilder.MARKER_URL,
                            overriding.build(configuration, metadata, context),
                            "the three-argument override still answers its own callers"));
        }
    }

    /** Test double that replaces the four-argument {@code build} and records the options it received. */
    static final class FourArgumentMarkerBuilder extends AuthorizationRequestBuilder {

        static final String MARKER_URL = "https://double.example.com/authorize?marker=four-argument";
        final AtomicInteger calls = new AtomicInteger();
        AuthorizationRequestOptions lastOptions;

        @Override
        public String build(ClientConfiguration configuration, ProviderMetadata metadata, FlowContext context,
                AuthorizationRequestOptions options) {
            calls.incrementAndGet();
            lastOptions = options;
            return MARKER_URL;
        }
    }

    /** Test double that replaces only the three-argument {@code build}. */
    static final class ThreeArgumentMarkerBuilder extends AuthorizationRequestBuilder {

        static final String MARKER_URL = "https://double.example.com/authorize?marker=three-argument";
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public String build(ClientConfiguration configuration, ProviderMetadata metadata, FlowContext context) {
            calls.incrementAndGet();
            return MARKER_URL;
        }
    }
}
