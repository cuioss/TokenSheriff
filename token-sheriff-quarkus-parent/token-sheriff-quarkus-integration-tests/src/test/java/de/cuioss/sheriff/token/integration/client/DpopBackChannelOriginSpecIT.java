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
package de.cuioss.sheriff.token.integration.client;

import de.cuioss.sheriff.token.client.auth.ClientSecretBasicAuth;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.DiscoveryResolver;
import de.cuioss.sheriff.token.client.discovery.ProviderMetadata;
import de.cuioss.sheriff.token.client.dpop.DpopProofGenerator;
import de.cuioss.sheriff.token.client.dpop.SenderConstraint;
import de.cuioss.sheriff.token.client.flow.AuthorizationCodeFlow;
import de.cuioss.sheriff.token.client.flow.CallbackParameters;
import de.cuioss.sheriff.token.client.flow.IssValidator;
import de.cuioss.sheriff.token.client.flow.RefreshFlow;
import de.cuioss.sheriff.token.client.flow.TokenEndpointClient;
import de.cuioss.sheriff.token.client.token.IdTokenValidationBridge;
import de.cuioss.sheriff.token.client.token.RotationResult;
import de.cuioss.sheriff.token.client.token.TokenValidationBridge;
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.integration.BaseIntegrationTest;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DPoP-constrained {@code authorization_code} + refresh spec over a mapped back channel, against the real
 * Keycloak container ({@code CLIENT-11}).
 * <p>
 * <strong>Scenario.</strong> Keycloak runs with the static public hostname
 * {@link KeycloakUrlSupport#EXTERNAL_BASE}, so its issuer and every advertised endpoint name
 * {@code https://localhost:1443}. The client is configured with that public issuer and with
 * {@code backChannelOrigin=}{@value #BACK_CHANNEL_ORIGIN}: a distinct origin that reaches the same
 * container through the same port mapping. Every back-channel call — discovery, the code exchange and
 * the refresh — is therefore dialled at {@code 127.0.0.1:1443}, while the browser leg stays on the
 * public front channel. {@code 127.0.0.1} is an IP subject alternative name of the test certificate
 * ({@code generate-certificates.sh}), so hostname verification stays on and trust comes from the
 * generated {@code localhost-truststore.p12} through
 * {@link RefreshEngineSupport#chainValidatingSslContext()} — no trust-all context is involved.
 * <p>
 * <strong>Contract under test.</strong> The {@code dpop-client} realm client requires DPoP-bound access
 * tokens. The spec redeems the authorization code and then refreshes, both through the production
 * engine with a {@link SenderConstraint#dpop(DpopProofGenerator) DPoP sender-constraint}, and asserts
 * that both legs succeed and that both access tokens are bound ({@code cnf.jkt}) to the proof key.
 * Before the {@code htu} change of the DPoP back-channel-origin plan, this spec failed with
 * {@code invalid_dpop_proof} / {@code DPoP HTTP URL mismatch}: the proof named the advertised public
 * token endpoint while the request was sent to the back-channel origin (see ADR-0015).
 */
@DisplayName("DPoP authorization_code + refresh through a mapped back-channel origin against real Keycloak")
class DpopBackChannelOriginSpecIT extends BaseIntegrationTest {

    private static final CuiLogger LOGGER = new CuiLogger(DpopBackChannelOriginSpecIT.class);

    /** Public issuer identity: the realm's issuer under Keycloak's static hostname. */
    private static final String ISSUER = KeycloakUrlSupport.EXTERNAL_BASE + "/realms/integration";

    /** A distinct origin that reaches the same Keycloak container; listed in the certificate SAN. */
    private static final String BACK_CHANNEL_ORIGIN = "https://127.0.0.1:1443";

    private static final String CLIENT_ID = "dpop-client";
    private static final String CLIENT_SECRET = "dpop-secret";
    private static final String REDIRECT_URI = "https://localhost/callback";
    private static final String USERNAME = "integration-user";
    private static final String PASSWORD = "integration-password";

    /** First {@code action} attribute of the Keycloak login page — the login-actions endpoint. */
    private static final Pattern FORM_ACTION = Pattern.compile("action=\"([^\"]+)\"");

    /** Extracts the {@code cnf.jkt} confirmation value from a decoded JWT claims set. */
    private static final Pattern JKT_CLAIM = Pattern.compile("\"jkt\"\\s*:\\s*\"([^\"]+)\"");

    private ClientConfiguration configuration;
    private DpopProofGenerator proofGenerator;
    private SenderConstraint dpopConstraint;

    @BeforeEach
    void assembleEngine() {
        configuration = ClientConfiguration.builder()
                .issuer(ISSUER)
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .redirectUri(REDIRECT_URI)
                .backChannelOrigin(BACK_CHANNEL_ORIGIN)
                .sslContext(RefreshEngineSupport.chainValidatingSslContext())
                .build();
        KeyPair proofKey = RefreshEngineSupport.generateRsaKeyPair();
        proofGenerator = RefreshEngineSupport.dpopProofGenerator(proofKey);
        dpopConstraint = SenderConstraint.dpop(proofGenerator);
    }

    @Test
    @DisplayName("Should redeem the code and refresh with DPoP-bound tokens when the back channel is mapped")
    void shouldBindBothTokensThroughMappedBackChannel() throws Exception {
        ProviderMetadata metadata = new DiscoveryResolver(configuration).resolve();
        assertEquals(ISSUER, metadata.getIssuer().orElseThrow(),
                "discovery dialled through the back-channel origin must still report the public issuer");
        TokenValidator validator = tokenValidator(metadata.jwksUri);
        TokenValidationBridge accessBridge = new TokenValidationBridge(validator);
        IdTokenValidationBridge idBridge = new IdTokenValidationBridge(validator);
        ClientSecretBasicAuth clientAuthentication = new ClientSecretBasicAuth(CLIENT_ID, CLIENT_SECRET);

        AuthorizationCodeFlow codeFlow = new AuthorizationCodeFlow(configuration,
                new TokenEndpointClient(configuration), accessBridge, idBridge, new IssValidator(), dpopConstraint);
        AuthorizationCodeFlow.AuthorizationRedirect redirect = codeFlow.authorize(metadata);
        CallbackParameters callback = CallbackParameters.parse(login(redirect.authorizationUrl()));

        AuthorizationCodeFlow.AuthenticationResult authentication = assertDoesNotThrow(
                () -> codeFlow.exchange(metadata, redirect.context(), callback, clientAuthentication),
                "Keycloak must answer the DPoP-bound code exchange on the mapped back channel with HTTP 200");
        LOGGER.debug("DPoP code exchange through %s succeeded", BACK_CHANNEL_ORIGIN);
        String refreshToken = authentication.refreshToken();
        assertNotNull(refreshToken, "the authorization_code exchange must issue a refresh token");

        RefreshFlow refreshFlow = new RefreshFlow(configuration, new TokenEndpointClient(configuration),
                accessBridge, clientAuthentication, dpopConstraint);
        RotationResult rotation = assertDoesNotThrow(() -> refreshFlow.refresh(metadata, refreshToken),
                "Keycloak must answer the DPoP-bound refresh on the mapped back channel with HTTP 200");

        assertAll("both access tokens are bound to the proof key",
                () -> assertEquals(proofGenerator.jkt(), jktOf(authentication.accessToken().getRawToken()),
                        "the code-exchange access token must carry cnf.jkt of the proof key"),
                () -> assertEquals(proofGenerator.jkt(), jktOf(rotation.accessToken().getRawToken()),
                        "the refreshed access token must carry cnf.jkt of the proof key"));
    }

    /**
     * Completes the browser leg of the flow on the public front channel: fetches the login page, posts
     * the integration user's credentials to the login-actions URL Keycloak emitted, and reduces the
     * {@code form_post} auto-submit document to the callback query string.
     *
     * @param authorizationUrl the authorization request URL the production engine built
     * @return the URL-encoded callback query ({@code code}, {@code state}, {@code iss})
     */
    private static String login(String authorizationUrl) throws Exception {
        HttpClient browser = HttpClient.newBuilder()
                .sslContext(RefreshEngineSupport.chainValidatingSslContext())
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest authRequest = HttpRequest.newBuilder().uri(URI.create(authorizationUrl))
                .header("Accept", "text/html").GET().build();
        HttpResponse<String> loginPage = browser.send(authRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginPage.statusCode(), "Keycloak must serve the login form for the authorization request");

        Matcher matcher = FORM_ACTION.matcher(loginPage.body());
        assertTrue(matcher.find(), "the Keycloak login page must expose a form action URL");
        String actionUrl = matcher.group(1).replace("&amp;", "&");

        String form = param("username", USERNAME) + "&" + param("password", PASSWORD);
        HttpRequest loginRequest = HttpRequest.newBuilder().uri(URI.create(actionUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> loginResult = browser.send(loginRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginResult.statusCode(), "a successful login must return the form_post auto-submit document");

        FormPostSupport.FormPost formPost = FormPostSupport.parse(loginResult.body());
        assertTrue(formPost.action().startsWith(REDIRECT_URI),
                "the form_post target must be the exact registered redirect_uri");
        return formPost.query();
    }

    /**
     * @param jwksUri the JWKS endpoint the realm's discovery document advertises
     * @return a validator for the {@code integration} realm, fetching its JWKS over the chain-validated
     *         loopback endpoint; audience validation is off because the realm's tokens name the resource
     *         server rather than the acquiring client
     */
    private static TokenValidator tokenValidator(String jwksUri) {
        HttpJwksLoaderConfig jwksConfig = HttpJwksLoaderConfig.builder()
                .jwksUrl(jwksUri)
                .issuerIdentifier(ISSUER)
                .sslContext(RefreshEngineSupport.chainValidatingSslContext())
                .allowLoopbackEgress(true)
                .build();
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier(ISSUER)
                .audienceValidationDisabled(true)
                .claimSubOptional(false)
                .httpJwksLoaderConfig(jwksConfig)
                .build();
        return TokenValidator.builder().issuerConfig(issuerConfig).build();
    }

    /**
     * @param jwt the raw, signed access token
     * @return the {@code cnf.jkt} the token confirms, or {@code null} when it carries none
     */
    private static @Nullable String jktOf(String jwt) {
        String[] segments = jwt.split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8);
        Matcher matcher = JKT_CLAIM.matcher(claims);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String param(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
