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
package de.cuioss.sheriff.token.client.flow;

import de.cuioss.sheriff.token.client.auth.ClientSecretBasicAuth;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.ProviderMetadata;
import de.cuioss.sheriff.token.client.token.IdTokenValidationBridge;
import de.cuioss.sheriff.token.client.token.TokenValidationBridge;
import de.cuioss.sheriff.token.commons.error.ClientProtocolException;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import de.cuioss.sheriff.token.validation.domain.token.IdTokenContent;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import lombok.Getter;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("AuthorizationCodeFlow authorize + validated exchange")
class AuthorizationCodeFlowTest {

    private static final String REDIRECT_URI = "https://rp.example.com/callback";
    private static final String AUTH_ENDPOINT = "https://issuer.example.com/authorize";
    private static final String AT_HASH_CLAIM = "at_hash";
    private static final Pattern JWS_ALG_PATTERN = Pattern.compile("\"alg\"\\s*:\\s*\"([^\"]+)\"");

    @Getter
    private final TokenEndpointDispatcher moduleDispatcher = new TokenEndpointDispatcher();

    private TestTokenHolder accessHolder;
    private TestTokenHolder idHolder;
    private TokenValidationBridge accessBridge;
    private IdTokenValidationBridge idBridge;

    @BeforeEach
    void setUp() {
        accessHolder = TestTokenGenerators.accessTokens().next();
        idHolder = TestTokenGenerators.idTokens().next();
        TokenValidator validator = TokenValidator.builder().issuerConfig(accessHolder.getIssuerConfig()).build();
        accessBridge = new TokenValidationBridge(validator);
        idBridge = new IdTokenValidationBridge(validator);
        moduleDispatcher.reset();
    }

    private static ClientConfiguration config() {
        return ClientConfiguration.builder()
                .issuer("https://issuer.example.com")
                .clientId(Generators.nonBlankStrings().next())
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .redirectUri(REDIRECT_URI)
                .allowInsecureHttp(true)
                .build();
    }

    private static ProviderMetadata metadataWithTokenEndpoint(URIBuilder uriBuilder) {
        var metadata = new ProviderMetadata();
        metadata.issuer = "https://issuer.example.com";
        metadata.tokenEndpoint = uriBuilder.addPathSegment("token").buildAsString();
        return metadata;
    }

    private AuthorizationCodeFlow flow(ClientConfiguration config) {
        return new AuthorizationCodeFlow(config, new TokenEndpointClient(config), accessBridge, idBridge);
    }

    private ClientSecretBasicAuth auth(ClientConfiguration config) {
        return new ClientSecretBasicAuth(config.getClientId(), config.getClientSecret());
    }

    @Test
    @DisplayName("authorize() should mint a redirect carrying the authorization URL and a persistable flow context")
    void shouldMintRedirect() {
        var metadata = new ProviderMetadata();
        metadata.authorizationEndpoint = AUTH_ENDPOINT;
        metadata.codeChallengeMethodsSupported = List.of("S256");

        AuthorizationCodeFlow.AuthorizationRedirect redirect = flow(config()).authorize(metadata);

        assertAll("authorization redirect",
                () -> assertNotNull(redirect.context(), "a flow context must be minted for the callback"),
                () -> assertTrue(redirect.authorizationUrl().startsWith(AUTH_ENDPOINT + "?"),
                        "the URL must target the advertised authorization endpoint"),
                () -> assertTrue(redirect.authorizationUrl().contains("state=" + redirect.context().state()),
                        "the front-channel state must bind the minted context"));
    }

    @Test
    @DisplayName("authorize() should reject null metadata")
    void shouldRejectNullMetadata() {
        var flow = flow(config());
        assertThrows(NullPointerException.class, () -> flow.authorize(null));
    }

    @Test
    @DisplayName("exchange() should redeem the code and return the validated, nonce-bound access and ID tokens")
    void shouldRedeemAndValidate(URIBuilder uriBuilder, MockWebServer server) throws Exception {
        var config = config();
        var context = FlowContext.create(REDIRECT_URI);
        idHolder.withClaim("nonce", ClaimValue.forPlainString(context.nonce()));
        moduleDispatcher.success(accessHolder.getRawToken(), idHolder.getRawToken());
        String code = Generators.letterStrings(20, 40).next();
        var callback = new CallbackParameters(code, context.state(), null, null, null);

        AuthorizationCodeFlow.AuthenticationResult result =
                flow(config).exchange(metadataWithTokenEndpoint(uriBuilder), context, callback, auth(config));

        RecordedRequest request = server.takeRequest();
        String body = request.getBody() == null ? "" : request.getBody().utf8();
        assertAll("validated exchange",
                () -> assertNotNull(result.accessToken(), "a validated access token must be returned"),
                () -> assertNotNull(result.idToken(), "a validated ID token must be returned"),
                () -> assertTrue(body.contains("grant_type=authorization_code"),
                        "the token request must use the authorization_code grant"),
                () -> assertTrue(body.contains("code=" + code), "the redeemed code must be sent"),
                () -> assertTrue(body.contains("code_verifier="),
                        "the secret PKCE verifier must be redeemed at the token endpoint"),
                () -> assertNotNull(request.getHeaders().get("Authorization"),
                        "client authentication must decorate the token request"));
    }

    @Test
    @DisplayName("exchange() should reject an ID token whose nonce does not match the flow context")
    void shouldRejectNonceMismatch(URIBuilder uriBuilder) {
        var config = config();
        var context = FlowContext.create(REDIRECT_URI);
        idHolder.withClaim("nonce", ClaimValue.forPlainString(Generators.letterStrings(20, 40).next()));
        moduleDispatcher.success(accessHolder.getRawToken(), idHolder.getRawToken());
        var metadata = metadataWithTokenEndpoint(uriBuilder);
        var callback = new CallbackParameters(Generators.letterStrings(20, 40).next(), context.state(), null, null, null);
        var flow = flow(config);
        var clientAuth = auth(config);

        assertThrows(ClientProtocolException.class,
                () -> flow.exchange(metadata, context, callback, clientAuth));
    }

    @Test
    @DisplayName("exchange() should reject a token response that carries no ID token")
    void shouldRejectMissingIdToken(URIBuilder uriBuilder) {
        var config = config();
        var context = FlowContext.create(REDIRECT_URI);
        moduleDispatcher.success(accessHolder.getRawToken(), null);
        var metadata = metadataWithTokenEndpoint(uriBuilder);
        var callback = new CallbackParameters(Generators.letterStrings(20, 40).next(), context.state(), null, null, null);
        var flow = flow(config);
        var clientAuth = auth(config);

        var thrown = assertThrows(IllegalStateException.class,
                () -> flow.exchange(metadata, context, callback, clientAuth));
        assertTrue(thrown.getMessage().contains("ID token"), "the failure must name the missing ID token");
    }

    @Test
    @DisplayName("exchange() should reject a callback whose state does not match the flow context")
    void shouldRejectStateMismatch(URIBuilder uriBuilder) {
        var config = config();
        var context = FlowContext.create(REDIRECT_URI);
        var metadata = metadataWithTokenEndpoint(uriBuilder);
        var callback = new CallbackParameters(Generators.letterStrings(20, 40).next(),
                Generators.letterStrings(20, 40).next(), null, null, null);
        var flow = flow(config);
        var clientAuth = auth(config);

        assertThrows(ClientProtocolException.class,
                () -> flow.exchange(metadata, context, callback, clientAuth));
    }

    @Test
    @DisplayName("exchange() should reject metadata that advertises no token endpoint")
    void shouldRejectMissingTokenEndpoint(URIBuilder uriBuilder) {
        var config = config();
        var context = FlowContext.create(REDIRECT_URI);
        idHolder.withClaim("nonce", ClaimValue.forPlainString(context.nonce()));
        moduleDispatcher.success(accessHolder.getRawToken(), idHolder.getRawToken());
        var metadata = new ProviderMetadata();
        var callback = new CallbackParameters(Generators.letterStrings(20, 40).next(), context.state(), null, null, null);
        var flow = flow(config);
        var clientAuth = auth(config);

        assertThrows(IllegalStateException.class,
                () -> flow.exchange(metadata, context, callback, clientAuth));
    }

    @Test
    @DisplayName("exchange() should surface a TransportException on a token-endpoint error response")
    void shouldSurfaceTransportException(URIBuilder uriBuilder) {
        var config = config();
        var context = FlowContext.create(REDIRECT_URI);
        moduleDispatcher.error();
        var metadata = metadataWithTokenEndpoint(uriBuilder);
        var callback = new CallbackParameters(Generators.letterStrings(20, 40).next(), context.state(), null, null, null);
        var flow = flow(config);
        var clientAuth = auth(config);

        assertThrows(TransportException.class,
                () -> flow.exchange(metadata, context, callback, clientAuth));
    }

    @Test
    @DisplayName("exchange() should reject an access token that fails pipeline validation")
    void shouldRejectTamperedAccessToken(URIBuilder uriBuilder) {
        var config = config();
        var context = FlowContext.create(REDIRECT_URI);
        accessHolder.withClaim(ClaimName.ISSUER.getName(),
                ClaimValue.forPlainString("https://attacker.example.com"));
        idHolder.withClaim("nonce", ClaimValue.forPlainString(context.nonce()));
        moduleDispatcher.success(accessHolder.getRawToken(), idHolder.getRawToken());
        var metadata = metadataWithTokenEndpoint(uriBuilder);
        var callback = new CallbackParameters(Generators.letterStrings(20, 40).next(), context.state(), null, null, null);
        var flow = flow(config);
        var clientAuth = auth(config);

        assertThrows(TokenValidationException.class,
                () -> flow.exchange(metadata, context, callback, clientAuth));
    }

    @Test
    @DisplayName("exchange() should reject an ID token whose at_hash does not bind the returned access token (M4)")
    void shouldRejectMismatchedAtHash(URIBuilder uriBuilder) {
        var config = config();
        var context = FlowContext.create(REDIRECT_URI);
        String otherAccessToken = Generators.letterStrings(20, 40).next();
        idHolder.withClaim("nonce", ClaimValue.forPlainString(context.nonce()));
        idHolder.withClaim(AT_HASH_CLAIM, ClaimValue.forPlainString(atHash(idHolder.getRawToken(), otherAccessToken)));
        moduleDispatcher.success(accessHolder.getRawToken(), idHolder.getRawToken());
        var metadata = metadataWithTokenEndpoint(uriBuilder);
        var callback = new CallbackParameters(Generators.letterStrings(20, 40).next(), context.state(), null, null, null);
        var flow = flow(config);
        var clientAuth = auth(config);

        assertThrows(ClientProtocolException.class,
                () -> flow.exchange(metadata, context, callback, clientAuth),
                "the token exchange must reject an ID token whose at_hash does not bind the access token"
                        + " returned in the same response");
    }

    /**
     * Computes the OIDC {@code at_hash} for {@code accessToken} using the digest the raw ID token's JWS
     * {@code alg} selects — mirroring the binding the production seam asserts (OIDC Core §3.1.3.6).
     */
    private static String atHash(String rawIdToken, String accessToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(shaAlgorithm(rawIdToken));
            byte[] hash = digest.digest(accessToken.getBytes(StandardCharsets.US_ASCII));
            byte[] leftHalf = Arrays.copyOf(hash, hash.length / 2);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(leftHalf);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String shaAlgorithm(String rawIdToken) {
        String alg = jwsAlgorithm(rawIdToken);
        if (alg.endsWith("384")) {
            return "SHA-384";
        }
        if (alg.endsWith("512")) {
            return "SHA-512";
        }
        return "SHA-256";
    }

    private static String jwsAlgorithm(String rawIdToken) {
        int dot = rawIdToken.indexOf('.');
        if (dot <= 0) {
            return "";
        }
        String header = new String(Base64.getUrlDecoder().decode(rawIdToken.substring(0, dot)),
                StandardCharsets.UTF_8);
        Matcher matcher = JWS_ALG_PATTERN.matcher(header);
        return matcher.find() ? matcher.group(1) : "";
    }

    @Test
    @DisplayName("exchange() should reject null arguments")
    void shouldRejectNullArguments(URIBuilder uriBuilder) {
        var config = config();
        var context = FlowContext.create(REDIRECT_URI);
        var metadata = metadataWithTokenEndpoint(uriBuilder);
        var callback = new CallbackParameters(Generators.letterStrings(20, 40).next(), context.state(), null, null, null);
        var flow = flow(config);
        var clientAuth = auth(config);

        assertAll("null-guards",
                () -> assertThrows(NullPointerException.class,
                        () -> flow.exchange(null, context, callback, clientAuth)),
                () -> assertThrows(NullPointerException.class,
                        () -> flow.exchange(metadata, null, callback, clientAuth)),
                () -> assertThrows(NullPointerException.class,
                        () -> flow.exchange(metadata, context, null, clientAuth)),
                () -> assertThrows(NullPointerException.class,
                        () -> flow.exchange(metadata, context, callback, null)));
    }

    @Test
    @DisplayName("AuthorizationRedirect should preserve its URL and context and reject nulls")
    void authorizationRedirectContract() {
        var context = FlowContext.create(REDIRECT_URI);
        var redirect = new AuthorizationCodeFlow.AuthorizationRedirect(AUTH_ENDPOINT, context);

        assertAll("redirect record",
                () -> assertEquals(AUTH_ENDPOINT, redirect.authorizationUrl(), "the URL must be preserved"),
                () -> assertSame(context, redirect.context(), "the context must be preserved"),
                () -> assertThrows(NullPointerException.class,
                        () -> new AuthorizationCodeFlow.AuthorizationRedirect(null, context)),
                () -> assertThrows(NullPointerException.class,
                        () -> new AuthorizationCodeFlow.AuthorizationRedirect(AUTH_ENDPOINT, null)));
    }

    @Test
    @DisplayName("AuthenticationResult should preserve its tokens and reject nulls")
    void authenticationResultContract() {
        AccessTokenContent access = accessHolder.asAccessTokenContent();
        IdTokenContent id = idHolder.asIdTokenContent();
        var result = new AuthorizationCodeFlow.AuthenticationResult(access, id);

        assertAll("authentication result record",
                () -> assertSame(access, result.accessToken(), "the access token must be preserved"),
                () -> assertSame(id, result.idToken(), "the ID token must be preserved"),
                () -> assertThrows(NullPointerException.class,
                        () -> new AuthorizationCodeFlow.AuthenticationResult(null, id)),
                () -> assertThrows(NullPointerException.class,
                        () -> new AuthorizationCodeFlow.AuthenticationResult(access, null)));
    }

    /**
     * Token-endpoint dispatcher serving a configurable authorization_code response (with optional ID
     * token) or an error.
     */
    static final class TokenEndpointDispatcher implements ModuleDispatcherElement {

        private static final int HTTP_OK = 200;
        private static final int HTTP_BAD_REQUEST = 400;

        private int status = HTTP_OK;
        private String body = "";

        void reset() {
            this.status = HTTP_OK;
            this.body = "";
        }

        void success(String accessToken, String idToken) {
            this.status = HTTP_OK;
            StringBuilder json = new StringBuilder("{\"access_token\":\"").append(accessToken)
                    .append("\",\"token_type\":\"Bearer\",\"expires_in\":300");
            if (idToken != null) {
                json.append(",\"id_token\":\"").append(idToken).append('"');
            }
            json.append('}');
            this.body = json.toString();
        }

        void error() {
            this.status = HTTP_BAD_REQUEST;
            this.body = "{\"error\":\"invalid_grant\"}";
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
            return Optional.of(new MockResponse(status, Headers.of("Content-Type", "application/json"), body));
        }
    }
}
