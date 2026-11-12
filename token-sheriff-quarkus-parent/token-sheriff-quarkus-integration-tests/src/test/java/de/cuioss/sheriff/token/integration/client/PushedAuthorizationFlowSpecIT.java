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
import de.cuioss.sheriff.token.client.flow.AuthorizationCodeFlow;
import de.cuioss.sheriff.token.client.flow.AuthorizationCodeFlow.AuthorizationRedirect;
import de.cuioss.sheriff.token.client.flow.AuthorizationRequestOptions;
import de.cuioss.sheriff.token.client.flow.Prompt;
import de.cuioss.sheriff.token.client.flow.TokenEndpointClient;
import de.cuioss.sheriff.token.client.token.IdTokenValidationBridge;
import de.cuioss.sheriff.token.client.token.TokenValidationBridge;
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.integration.BaseIntegrationTest;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.tools.logging.CuiLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * RFC 9126 pushed authorization request driven through the production
 * {@link AuthorizationCodeFlow#authorizePushed} entry point against the real Keycloak container.
 * <p>
 * Capability-gated like {@link ParSpecIT}: the realm's discovery document is resolved through the
 * production {@link DiscoveryResolver}, and the spec skips when no
 * {@code pushed_authorization_request_endpoint} is advertised. When PAR is advertised it asserts that
 * <ol>
 *   <li>the redirect the engine returns carries exactly {@code client_id} and {@code request_uri} —
 *       none of the pushed parameters reach the front channel;</li>
 *   <li>Keycloak accepts that redirect: following it serves the login page, not an error page;</li>
 *   <li>logging the integration user in on that login page completes the authorization: Keycloak
 *       answers with the {@code form_post} document carrying an authorization code;</li>
 *   <li>the {@code request_uri} is single-use (RFC 9126 §4): following the same redirect a second time
 *       no longer serves the login page.</li>
 * </ol>
 * The login step is what makes the single use observable. RFC 9126 §4 lets an authorization server
 * accept a duplicate request "due to a user reloading/refreshing their user agent", and Keycloak enforces
 * one-time use at the point of authorization, not when the authorization endpoint is visited: the
 * authorization-endpoint parser only reads the pushed request from the single-use store, and the entry is
 * removed when the login completes and the authorization response is built. Before that login, a second
 * follow is therefore served the login page again; after it, Keycloak no longer finds the pushed request
 * and answers with an error page.
 * <p>
 * The engine reaches Keycloak over the chain-validating test truststore, as in the other client-engine
 * specs ({@link RefreshEngineSupport#chainValidatingSslContext()}); the browser leg, which follows the
 * redirect and submits the login form, runs against a trust-all TLS context (integration-test scope only),
 * mirroring {@link AuthCodePkceSpecIT}.
 */
@DisplayName("RFC 9126 pushed authorization through AuthorizationCodeFlow against real Keycloak")
class PushedAuthorizationFlowSpecIT extends BaseIntegrationTest {

    private static final CuiLogger LOGGER = new CuiLogger(PushedAuthorizationFlowSpecIT.class);

    private static final String ISSUER = KeycloakUrlSupport.EXTERNAL_BASE + "/realms/integration";
    private static final String CLIENT_ID = "integration-client";
    private static final String CLIENT_SECRET = "integration-secret";
    private static final String REDIRECT_URI = "https://localhost/callback";
    private static final String USERNAME = "integration-user";
    private static final String PASSWORD = "integration-password";

    /** The {@code action} target of the login form Keycloak serves. */
    private static final Pattern FORM_ACTION = Pattern.compile("action=\"([^\"]+)\"");

    /** The RFC 9126 §2.2 URN prefix of a {@code request_uri} the authorization server issues. */
    private static final String REQUEST_URI_PREFIX = "urn:ietf:params:oauth:request_uri:";

    /** The username input of Keycloak's login form — present only when the login page is served. */
    private static final Pattern LOGIN_FORM_USERNAME = Pattern.compile("name=\"username\"");

    /** The bound on same-origin {@code 3xx} hops one browser-like follow takes before it stops. */
    private static final int MAX_SAME_ORIGIN_HOPS = 5;

    @Test
    @DisplayName("Should push the request and return a single-use redirect carrying only client_id and request_uri")
    void shouldAuthorizeThroughPushedRequest() throws Exception {
        ClientConfiguration configuration = clientConfiguration();
        ProviderMetadata metadata = new DiscoveryResolver(configuration).resolve();
        assumeTrue(metadata.getPushedAuthorizationRequestEndpoint().isPresent(),
                "Keycloak realm does not advertise a PAR endpoint — skipping capability-gated PAR spec");

        AuthorizationRequestOptions options = AuthorizationRequestOptions.builder()
                .scopes(List.of("openid"))
                .prompt(Prompt.LOGIN)
                .build();
        AuthorizationRedirect redirect = authorizationCodeFlow(configuration, metadata)
                .authorizePushed(metadata, new ClientSecretBasicAuth(CLIENT_ID, CLIENT_SECRET), options);
        String authorizationUrl = redirect.authorizationUrl();
        LOGGER.debug("Pushed authorization redirect: %s", authorizationUrl);

        Map<String, String> query = queryParameters(authorizationUrl);
        assertAll("the redirect carries only client_id and request_uri",
                () -> assertTrue(authorizationUrl.startsWith(metadata.getAuthorizationEndpoint().orElseThrow()),
                        "the redirect must target the advertised authorization endpoint"),
                () -> assertEquals(List.of("client_id", "request_uri"), List.copyOf(query.keySet()),
                        "no pushed parameter may reach the front channel"),
                () -> assertEquals(CLIENT_ID, query.get("client_id")),
                () -> assertTrue(query.get("request_uri").startsWith(REQUEST_URI_PREFIX),
                        "the request_uri must be the opaque URN Keycloak issued, got " + query.get("request_uri")));

        HttpClient firstBrowser = newBrowser();
        FollowResult first = follow(firstBrowser, authorizationUrl);
        LOGGER.debug("First redirect follow: %s", first.describe());
        assertAll("Keycloak accepts the client_id + request_uri redirect",
                () -> assertEquals(200, first.response().statusCode(),
                        "Keycloak must serve the login page, " + first.describe()),
                () -> assertTrue(LOGIN_FORM_USERNAME.matcher(first.response().body()).find(),
                        "the response must be the login form, not an error page, " + first.describe()));

        // Keycloak consumes the request_uri when the authorization completes, so log in first.
        HttpResponse<String> loginResult = login(firstBrowser, first.response().body());
        LOGGER.debug("Login result: status=%s", loginResult.statusCode());
        assertEquals(200, loginResult.statusCode(),
                "a successful login must return the form_post auto-submit document");
        FormPostSupport.FormPost callback = FormPostSupport.parse(loginResult.body());
        assertAll("the login completes the pushed authorization",
                () -> assertTrue(callback.action().startsWith(REDIRECT_URI),
                        "the form_post target must be the pushed redirect_uri, got " + callback.action()),
                () -> assertTrue(callback.query().contains("code="),
                        "the form_post document must carry the authorization code"));

        FollowResult second = follow(newBrowser(), authorizationUrl);
        LOGGER.debug("Second redirect follow: %s", second.describe());
        assertAll("the consumed request_uri is refused",
                () -> assertFalse(second.response().statusCode() == 200
                                && LOGIN_FORM_USERNAME.matcher(second.response().body()).find(),
                        "a request_uri is single-use (RFC 9126 §4): the second follow must not serve the login page, "
                                + second.describe()),
                () -> assertEquals(400, second.response().statusCode(),
                        "Keycloak must answer a consumed request_uri with its invalid-request error page, "
                                + second.describe()));
    }

    private static ClientConfiguration clientConfiguration() {
        return ClientConfiguration.builder()
                .issuer(ISSUER)
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .redirectUri(REDIRECT_URI)
                .sslContext(RefreshEngineSupport.chainValidatingSslContext())
                // The host-mapped Keycloak is reached on localhost.
                .allowLoopbackEgress(true)
                .build();
    }

    /**
     * Wires the flow exactly as an application would. The spec never redeems a code, so the validation
     * bridges are wired but not exercised.
     */
    private static AuthorizationCodeFlow authorizationCodeFlow(ClientConfiguration configuration,
            ProviderMetadata metadata) {
        HttpJwksLoaderConfig jwksConfig = HttpJwksLoaderConfig.builder()
                .jwksUrl(metadata.jwksUri)
                .issuerIdentifier(ISSUER)
                .sslContext(RefreshEngineSupport.chainValidatingSslContext())
                .allowLoopbackEgress(true)
                .build();
        TokenValidator validator = TokenValidator.builder()
                .issuerConfig(IssuerConfig.builder()
                        .issuerIdentifier(ISSUER)
                        .audienceValidationDisabled(true)
                        .httpJwksLoaderConfig(jwksConfig)
                        .build())
                .build();
        return new AuthorizationCodeFlow(configuration, new TokenEndpointClient(configuration),
                new TokenValidationBridge(validator), new IdTokenValidationBridge(validator));
    }

    /** Parses the redirect's query into its parameters, in the order they appear. */
    private static Map<String, String> queryParameters(String url) {
        String rawQuery = URI.create(url).getRawQuery();
        Map<String, String> parameters = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            parameters.put(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
        }
        return parameters;
    }

    /**
     * A fresh browser: its own empty cookie jar, so no session cookie is shared with another browser, and
     * no automatic redirect handling, so {@link #follow} sees every hop.
     */
    private static HttpClient newBrowser() throws Exception {
        return HttpClient.newBuilder()
                .sslContext(trustAllContext())
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Follows the redirect with the given browser. Its cookie jar is kept across hops, and same-origin
     * {@code 3xx} hops (same scheme, host and port as the authorization URL) are followed transparently,
     * up to {@link #MAX_SAME_ORIGIN_HOPS}. A {@code Location} that leaves the origin is never followed:
     * that response is returned so the caller can inspect it.
     */
    private static FollowResult follow(HttpClient browser, String authorizationUrl) throws Exception {
        URI origin = URI.create(authorizationUrl);
        URI current = origin;
        List<String> locations = new ArrayList<>();
        for (int hop = 0; ; hop++) {
            HttpRequest request = HttpRequest.newBuilder().uri(current)
                    .header("Accept", "text/html").GET().build();
            HttpResponse<String> response = browser.send(request, HttpResponse.BodyHandlers.ofString());
            Optional<String> location = isRedirect(response.statusCode())
                    ? response.headers().firstValue("Location")
                    : Optional.empty();
            LOGGER.debug("Redirect follow hop %s: status=%s, Location=%s", hop, response.statusCode(),
                    location.orElse("<none>"));
            if (location.isEmpty()) {
                return new FollowResult(response, locations);
            }
            locations.add(location.get());
            URI next = current.resolve(location.get());
            if (!sameOrigin(origin, next) || hop + 1 >= MAX_SAME_ORIGIN_HOPS) {
                return new FollowResult(response, locations);
            }
            current = next;
        }
    }

    /**
     * Submits the integration user's credentials to the login form served by the first follow, with the
     * same browser, so the authentication-session cookies of that follow stay valid. Keycloak builds the
     * form action from its static public hostname, so the POST follows it as emitted.
     */
    private static HttpResponse<String> login(HttpClient browser, String loginPage) throws Exception {
        Matcher action = FORM_ACTION.matcher(loginPage);
        assertTrue(action.find(), "the Keycloak login page must expose a form action URL");
        String actionUrl = action.group(1).replace("&amp;", "&");
        String form = param("username", USERNAME) + "&" + param("password", PASSWORD);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(actionUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        return browser.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String param(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean isRedirect(int status) {
        return status >= 300 && status < 400;
    }

    private static boolean sameOrigin(URI origin, URI candidate) {
        return origin.getScheme().equalsIgnoreCase(String.valueOf(candidate.getScheme()))
                && origin.getHost().equalsIgnoreCase(String.valueOf(candidate.getHost()))
                && effectivePort(origin) == effectivePort(candidate);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /**
     * The final response of one redirect follow together with the {@code Location} of every
     * {@code 3xx} hop seen on the way, so an assertion failure is diagnosable from its message alone.
     */
    private record FollowResult(HttpResponse<String> response, List<String> locations) {

        String describe() {
            return "final status=" + response.statusCode() + ", final Location="
                    + response.headers().firstValue("Location").orElse("<none>")
                    + ", Location chain=" + locations;
        }
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
}
