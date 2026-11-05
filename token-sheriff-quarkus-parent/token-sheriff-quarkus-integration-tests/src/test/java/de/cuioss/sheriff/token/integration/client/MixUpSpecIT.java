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

import de.cuioss.sheriff.token.client.auth.ClientAuthentication;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.DiscoveryResolver;
import de.cuioss.sheriff.token.client.discovery.ProviderMetadata;
import de.cuioss.sheriff.token.client.dpop.SenderConstraint;
import de.cuioss.sheriff.token.client.flow.AuthorizationCodeFlow;
import de.cuioss.sheriff.token.client.flow.CallbackHandler;
import de.cuioss.sheriff.token.client.flow.CallbackParameters;
import de.cuioss.sheriff.token.client.flow.CredentialRejectedException;
import de.cuioss.sheriff.token.client.flow.FlowContext;
import de.cuioss.sheriff.token.client.flow.IssValidator;
import de.cuioss.sheriff.token.client.flow.TokenEndpointClient;
import de.cuioss.sheriff.token.client.token.TokenResponse;
import de.cuioss.sheriff.token.commons.error.ClientProtocolException;
import de.cuioss.sheriff.token.integration.BaseIntegrationTest;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Increment-6 authorization-server mix-up spec against the real Keycloak container ({@code CLIENT-8}).
 * <p>
 * The RFC 9207 defence is driven through the production wiring, not through a stand-alone validator
 * call: a production {@link AuthorizationCodeFlow} is configured for realm {@code integration} (issuer
 * and endpoints resolved through the production {@link DiscoveryResolver}), and
 * {@link AuthorizationCodeFlow#exchange} runs {@link CallbackHandler#handle} and then
 * {@link IssValidator} against {@link ClientConfiguration#getIssuer()} before it redeems the code. The
 * token endpoint is reached through a counting {@link TokenEndpointClient} subclass, so the spec observes
 * whether the code was ever presented:
 * <ul>
 *     <li>a callback stamped with the {@code iss} of a foreign authorization server (the
 *         {@code benchmark} realm on the same Keycloak) is rejected with a {@link ClientProtocolException}
 *         and the token endpoint is never called;</li>
 *     <li>an honest callback stamped with the {@code integration} realm's own {@code iss} passes the
 *         issuer check and reaches the token endpoint exactly once — the code is not a genuine one, so
 *         Keycloak then refuses the redemption with HTTP {@code 400 invalid_grant}, which surfaces as a
 *         {@link CredentialRejectedException} rather than the mix-up rejection.</li>
 * </ul>
 * Discovery of the foreign realm's issuer runs against a trust-all TLS context (integration-test scope
 * only); the wired flow uses the chain-validating context from {@link RefreshEngineSupport}.
 */
@DisplayName("RFC 9207 mix-up defence against real Keycloak issuers")
class MixUpSpecIT extends BaseIntegrationTest {

    private static final CuiLogger LOGGER = new CuiLogger(MixUpSpecIT.class);

    private static final String INTEGRATION_ISSUER = KeycloakUrlSupport.EXTERNAL_BASE + "/realms/integration";
    private static final String FOREIGN_ISSUER = KeycloakUrlSupport.EXTERNAL_BASE + "/realms/benchmark";
    private static final String CLIENT_ID = "integration-client";
    private static final String CLIENT_SECRET = "integration-secret";
    private static final String REDIRECT_URI = "https://localhost/callback";
    private static final String NON_GENUINE_CODE = "not-a-genuine-authorization-code";

    @Test
    @DisplayName("Should reject a foreign realm's iss before the code ever reaches the token endpoint")
    void shouldRejectMixUpBeforeCodeRedemption() throws Exception {
        String foreignIssuer = discoveredIssuer(FOREIGN_ISSUER);
        assertNotNull(foreignIssuer, "Keycloak must advertise the foreign realm's issuer in its discovery document");

        WiredFlow wired = wiredFlow();
        FlowContext context = FlowContext.create(REDIRECT_URI);
        var mixedUpCallback = new CallbackParameters(NON_GENUINE_CODE, context.state(), null, null, foreignIssuer);

        assertThrows(ClientProtocolException.class,
                () -> wired.flow().exchange(wired.metadata(), context, mixedUpCallback, wired.authentication()),
                "a callback stamped with a foreign realm's iss is rejected (mix-up defence)");
        assertEquals(0, wired.tokenEndpoint().calls(),
                "the mix-up must be refused before the code is presented at the token endpoint");
    }

    @Test
    @DisplayName("Should let an honest iss through to the token endpoint exactly once")
    void shouldPassHonestIssuerToTokenEndpoint() {
        WiredFlow wired = wiredFlow();
        String advertisedIssuer = wired.metadata().issuer;
        LOGGER.debug("Keycloak integration realm issuer: %s", advertisedIssuer);
        // CallbackHandler enforces single use, so the honest control runs on a context of its own.
        FlowContext context = FlowContext.create(REDIRECT_URI);
        var honestCallback = new CallbackParameters(NON_GENUINE_CODE, context.state(), null, null, advertisedIssuer);

        assertThrows(CredentialRejectedException.class,
                () -> wired.flow().exchange(wired.metadata(), context, honestCallback, wired.authentication()),
                "Keycloak must refuse to redeem a code it never issued with invalid_grant, not the mix-up rejection");
        assertEquals(1, wired.tokenEndpoint().calls(),
                "the honest callback must pass the mix-up check and reach the token endpoint exactly once");
    }

    @Test
    @DisplayName("Should confirm the two realms advertise distinct issuer identifiers")
    void shouldHaveDistinctIssuers() throws Exception {
        String integration = discoveredIssuer(INTEGRATION_ISSUER);
        String foreign = discoveredIssuer(FOREIGN_ISSUER);

        assertTrue(integration != null && foreign != null && !integration.equals(foreign),
                "the integration and benchmark realms must present distinct issuer identifiers");
    }

    /**
     * Assembles the production {@code authorization_code} flow for realm {@code integration} exactly as
     * an application does, with a counting token-endpoint transport. The validation bridges are never
     * reached on either path of this spec (the mix-up is refused before redemption, and the honest
     * control's redemption is refused by Keycloak), so the shared client-engine validator suffices.
     */
    private static WiredFlow wiredFlow() {
        ClientConfiguration configuration = ClientConfiguration.builder()
                .issuer(INTEGRATION_ISSUER)
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .redirectUri(REDIRECT_URI)
                .sslContext(RefreshEngineSupport.chainValidatingSslContext())
                .build();
        ProviderMetadata metadata = new DiscoveryResolver(configuration).resolve();
        assertTrue(metadata.supportsAuthorizationResponseIssParameter(),
                "Keycloak must advertise authorization_response_iss_parameter_supported (RFC 9207 precondition)");

        TokenValidator validator = RefreshEngineSupport.tokenValidator();
        CountingTokenEndpointClient tokenEndpoint = new CountingTokenEndpointClient(configuration);
        var flow = new AuthorizationCodeFlow(configuration, tokenEndpoint,
                RefreshEngineSupport.accessTokenBridge(validator), RefreshEngineSupport.idTokenBridge(validator));
        return new WiredFlow(flow, metadata, tokenEndpoint, RefreshEngineSupport.clientAuthentication(configuration));
    }

    private String discoveredIssuer(String issuerBase) throws Exception {
        HttpClient client = HttpClient.newBuilder().sslContext(trustAllContext())
                .connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(issuerBase + "/.well-known/openid-configuration"))
                .header("Accept", "application/json").GET().build();
        String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        return extractIssuer(body);
    }

    private static String extractIssuer(String discoveryJson) {
        int key = discoveryJson.indexOf("\"issuer\"");
        if (key < 0) {
            return null;
        }
        int colon = discoveryJson.indexOf(':', key);
        int firstQuote = discoveryJson.indexOf('"', colon + 1);
        int secondQuote = discoveryJson.indexOf('"', firstQuote + 1);
        return discoveryJson.substring(firstQuote + 1, secondQuote);
    }

    private static SSLContext trustAllContext() throws Exception {
        TrustManager[] trustAll = {new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // integration-test trust-all
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // integration-test trust-all
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustAll, new SecureRandom());
        return context;
    }

    /**
     * The wired flow together with the collaborators the assertions observe.
     */
    private record WiredFlow(AuthorizationCodeFlow flow, ProviderMetadata metadata,
    CountingTokenEndpointClient tokenEndpoint,
    ClientAuthentication authentication) {
    }

    /**
     * Production token-endpoint transport that counts how often a code redemption was attempted. It
     * overrides the four-argument overload, which is the one {@link AuthorizationCodeFlow#exchange}
     * calls, and delegates every attempt to the real transport.
     */
    private static final class CountingTokenEndpointClient extends TokenEndpointClient {

        private final AtomicInteger calls = new AtomicInteger();

        CountingTokenEndpointClient(ClientConfiguration configuration) {
            super(configuration);
        }

        @Override
        public TokenResponse requestToken(String tokenEndpoint, Map<String, String> formParameters,
                Map<String, String> requestHeaders, @Nullable SenderConstraint senderConstraint) {
            calls.incrementAndGet();
            return super.requestToken(tokenEndpoint, formParameters, requestHeaders, senderConstraint);
        }

        int calls() {
            return calls.get();
        }
    }
}
