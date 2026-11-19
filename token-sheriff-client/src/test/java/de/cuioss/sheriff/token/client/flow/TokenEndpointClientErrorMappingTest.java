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
import de.cuioss.sheriff.token.client.dpop.SenderConstraint;
import de.cuioss.sheriff.token.client.token.TokenResponse;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Error-response mapping of {@link TokenEndpointClient}: which refusals surface as typed exceptions and
 * which stay a plain {@link TransportException}.
 * <p>
 * <strong>Client authentication:</strong> a {@code 4xx} naming {@code invalid_client}, or a {@code 401}
 * naming {@code unauthorized_client} (Keycloak's wrong-secret answer), is the typed
 * {@link ClientAuthenticationFailedException}; every shape outside that exact allow-list — a {@code 403}
 * {@code unauthorized_client}, a {@code 5xx}, an unknown body — stays a plain {@link TransportException},
 * and {@code invalid_grant} stays a {@link CredentialRejectedException}.
 * <p>
 * <strong>Scope refusal:</strong> a {@code 4xx} naming exactly {@code invalid_scope} is the typed
 * {@link InvalidScopeException} for every grant, carrying the code and the {@code error_description};
 * a {@code 5xx}, another code or an unknown body stays a plain {@link TransportException}.
 * <p>
 * <strong>Content-Type:</strong> a body is read as JSON only when every {@code Content-Type} line
 * carries {@code application/json}. An absent, other or conflicting {@code Content-Type} on a
 * {@code 200} is a {@link RedeemedResponseException}; on an error body it is an unknown error code.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("TokenEndpointClient client-authentication, scope and Content-Type error mapping")
class TokenEndpointClientErrorMappingTest {

    @Getter
    private final TokenEndpointStubDispatcher moduleDispatcher = new TokenEndpointStubDispatcher();

    @BeforeEach
    void setUp() {
        moduleDispatcher.reset();
    }

    private static ClientConfiguration config() {
        return ClientConfiguration.builder()
                .issuer("https://issuer.example.com")
                .clientId(Generators.nonBlankStrings().next())
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .redirectUri("https://rp.example.com/callback")
                .allowInsecureHttp(true)
                .allowLoopbackEgress(true)
                .build();
    }

    /**
     * Redeems against the stub endpoint under the supplied constraint and returns the surfaced
     * transport failure, so each case asserts only on what distinguishes it.
     */
    private TransportException redeemExpectingFailure(URIBuilder uriBuilder, SenderConstraint constraint) {
        var client = new TokenEndpointClient(config());
        String endpoint = uriBuilder.addPathSegment("token").buildAsString();
        Map<String, String> requestParams = Map.of("grant_type", "authorization_code");
        Map<String, String> requestHeaders = Map.of();

        return assertThrows(TransportException.class,
                () -> client.requestToken(endpoint, requestParams, requestHeaders, constraint),
                "an unrecoverable token-endpoint response must surface as a TransportException");
    }

    @ParameterizedTest(name = "HTTP {0} error={1} -> ClientAuthenticationFailedException")
    @CsvSource({
            // RFC 6749 §5.2 names invalid_client for a failed client authentication, on 401 and on 400.
            "401, invalid_client",
            "400, invalid_client",
            // Keycloak's answer to a wrong client secret: the 401 is the failed authentication.
            "401, unauthorized_client"})
    @DisplayName("a failed client authentication is surfaced as the typed refusal, carrying no body content")
    void shouldRaiseClientAuthenticationFailed(int status, String errorCode, URIBuilder uriBuilder) {
        String marker = Generators.letterStrings(16, 32).next();
        moduleDispatcher.failWith(status,
                "{\"error\":\"" + errorCode + "\",\"error_description\":\"" + marker + "\"}");

        TransportException thrown = redeemExpectingFailure(uriBuilder, null);

        assertAll("typed client-authentication refusal",
                () -> assertInstanceOf(ClientAuthenticationFailedException.class, thrown,
                        "the refusal must be the typed client-authentication failure, but was: " + thrown),
                () -> assertFalse(thrown instanceof CredentialRejectedException,
                        "a refused client says nothing about the presented grant"),
                () -> assertTrue(thrown.getMessage().contains("HTTP " + status),
                        "the message must name the observed status, but was: " + thrown.getMessage()),
                () -> assertFalse(thrown.getMessage().contains(marker),
                        "no part of the AS-controlled body may reach the message (CWE-117)"),
                () -> assertEquals(1, moduleDispatcher.getCallCounter(), "no retry without a nonce challenge"));
    }

    @ParameterizedTest(name = "HTTP {0} body={1} -> plain TransportException")
    @CsvSource(delimiter = '|', value = {
            // An authenticated client refused a grant type: the RFC meaning, not a failed authentication.
            "403 | {\"error\":\"unauthorized_client\"}",
            // A 5xx is the server reporting its own failure, whatever error body it carries.
            "500 | {\"error\":\"invalid_client\"}",
            "503 | {\"error\":\"invalid_client\"}",
            // No usable error code on a 401: each is an unknown, and an unknown is never a match.
            "401 | { not json",
            "401 | ",
            "401 | {\"error\":\"invalid_client\",\"error\":\"invalid_client\"}"})
    @DisplayName("a refusal outside the exact-shape allow-list stays a plain transport failure")
    void shouldNotRaiseClientAuthenticationFailedOutsideAllowList(int status, String body, URIBuilder uriBuilder) {
        moduleDispatcher.failWith(status, body == null ? "" : body);

        TransportException thrown = redeemExpectingFailure(uriBuilder, null);

        assertAll("plain non-success outcome",
                () -> assertFalse(thrown instanceof ClientAuthenticationFailedException,
                        "only the exact allow-listed shapes may be reported as a failed client authentication"),
                () -> assertFalse(thrown instanceof CredentialRejectedException,
                        "the refusal must not be read as a dead credential either"),
                () -> assertTrue(thrown.getMessage().contains("unexpected HTTP status " + status),
                        "the failure must be the ordinary non-success outcome, but was: " + thrown.getMessage()));
    }

    @Test
    @DisplayName("invalid_grant on a 4xx is the credential-rejected refusal, not a client-authentication one")
    void shouldKeepInvalidGrantAsCredentialRejected(URIBuilder uriBuilder) {
        moduleDispatcher.failWith(400, "{\"error\":\"invalid_grant\"}");

        TransportException thrown = redeemExpectingFailure(uriBuilder, null);

        assertAll("credential-rejected refusal",
                () -> assertInstanceOf(CredentialRejectedException.class, thrown),
                () -> assertFalse(thrown instanceof ClientAuthenticationFailedException,
                        "a dead grant is not a failed client authentication"));
    }

    @ParameterizedTest(name = "grant {0}: HTTP 400 invalid_scope -> InvalidScopeException")
    @CsvSource({"refresh_token", "client_credentials"})
    @DisplayName("invalid_scope on a 4xx is the typed scope refusal, carrying the code and the description")
    void shouldRaiseInvalidScopeForEveryGrant(String grantType, URIBuilder uriBuilder) {
        String description = Generators.letterStrings(16, 32).next();
        moduleDispatcher.failWith(400,
                "{\"error\":\"invalid_scope\",\"error_description\":\"" + description + "\"}");
        var client = new TokenEndpointClient(config());
        String endpoint = uriBuilder.addPathSegment("token").buildAsString();
        Map<String, String> requestParams = Map.of("grant_type", grantType);
        Map<String, String> requestHeaders = Map.of();

        InvalidScopeException thrown = assertThrows(InvalidScopeException.class,
                () -> client.requestToken(endpoint, requestParams, requestHeaders),
                "an invalid_scope refusal must surface as the typed scope refusal");

        assertAll("typed scope refusal",
                () -> assertEquals("invalid_scope", thrown.getErrorCode()),
                () -> assertEquals(Optional.of(description), thrown.getErrorDescription(),
                        "the error_description must be carried on the exception"),
                () -> assertTrue(thrown.getMessage().contains("HTTP 400"),
                        "the message must name the observed status, but was: " + thrown.getMessage()),
                () -> assertFalse(thrown.getMessage().contains(description),
                        "no part of the AS-controlled body may reach the message (CWE-117)"),
                () -> assertEquals(1, moduleDispatcher.getCallCounter(), "no retry without a nonce challenge"));
    }

    @Test
    @DisplayName("invalid_scope without an error_description carries an empty description")
    void shouldRaiseInvalidScopeWithoutDescription(URIBuilder uriBuilder) {
        moduleDispatcher.failWith(400, "{\"error_uri\":\"https://as.example/e\",\"error\":\"invalid_scope\"}");

        TransportException thrown = redeemExpectingFailure(uriBuilder, null);

        InvalidScopeException scopeRefusal = assertInstanceOf(InvalidScopeException.class, thrown);
        assertEquals(Optional.empty(), scopeRefusal.getErrorDescription(),
                "an absent error_description must not be fabricated");
    }

    @ParameterizedTest(name = "HTTP {0} body={1} -> not InvalidScopeException")
    @CsvSource(delimiter = '|', value = {
            // A 5xx is the server reporting its own failure, whatever error body it carries.
            "500 | {\"error\":\"invalid_scope\"}",
            "503 | {\"error\":\"invalid_scope\"}",
            // Another code on a 4xx is not a scope refusal.
            "400 | {\"error\":\"invalid_request\"}",
            "400 | {\"error\":\"INVALID_SCOPE\"}",
            // No usable error code: each is an unknown, and an unknown is never a match.
            "400 | { not json",
            "400 | ",
            "400 | {\"error\":\"invalid_scope\",\"error\":\"invalid_scope\"}"})
    @DisplayName("a refusal outside the invalid_scope allow-list stays a plain transport failure")
    void shouldNotRaiseInvalidScopeOutsideAllowList(int status, String body, URIBuilder uriBuilder) {
        moduleDispatcher.failWith(status, body == null ? "" : body);

        TransportException thrown = redeemExpectingFailure(uriBuilder, null);

        assertAll("plain non-success outcome",
                () -> assertFalse(thrown instanceof InvalidScopeException,
                        "only a 4xx naming exactly invalid_scope may be reported as a scope refusal"),
                () -> assertTrue(thrown.getMessage().contains("unexpected HTTP status " + status),
                        "the failure must be the ordinary non-success outcome, but was: " + thrown.getMessage()));
    }

    /**
     * Splits a {@code ,}-separated table cell into {@code Content-Type} lines; an empty cell is a
     * response with no {@code Content-Type} at all.
     */
    private static String[] contentTypeLines(String cell) {
        return cell == null || cell.isEmpty() ? new String[0] : cell.split(",");
    }

    @ParameterizedTest(name = "HTTP 200 Content-Type [{0}] -> RedeemedResponseException")
    @CsvSource(delimiter = '|', value = {
            "''",
            "text/plain",
            "application/jrd+json",
            "application/json,text/html"})
    @DisplayName("a 200 whose Content-Type is not application/json is refused before its body is parsed")
    void shouldRefuseSuccessResponseWithoutJsonContentType(String contentTypes, URIBuilder uriBuilder) {
        moduleDispatcher.withContentTypes(contentTypeLines(contentTypes));
        moduleDispatcher.respondWith(tokenResponse(Generators.letterStrings(30, 60).next(), "Bearer"));

        TransportException thrown = redeemExpectingFailure(uriBuilder, null);

        assertAll("refused success response",
                () -> assertInstanceOf(RedeemedResponseException.class, thrown,
                        "after a success status the grant is redeemed, but was: " + thrown),
                () -> assertTrue(thrown.getMessage().contains("Content-Type is not application/json"),
                        "the refusal must name the media type, but was: " + thrown.getMessage()),
                () -> assertEquals(1, moduleDispatcher.getCallCounter(), "a refused success is not retried"));
    }

    @Test
    @DisplayName("a 200 whose Content-Type carries a charset parameter is accepted")
    void shouldAcceptSuccessResponseWithJsonCharset(URIBuilder uriBuilder) {
        String accessToken = Generators.letterStrings(30, 60).next();
        moduleDispatcher.withContentTypes("application/json; charset=UTF-8");
        moduleDispatcher.respondWith(tokenResponse(accessToken, "Bearer"));

        TokenResponse response = redeem(uriBuilder, null);

        assertEquals(accessToken, response.accessToken, "parameters on application/json are allowed");
    }

    @ParameterizedTest(name = "HTTP 400 invalid_scope Content-Type [{0}] -> plain TransportException")
    @CsvSource(delimiter = '|', value = {
            "''",
            "text/plain",
            "application/jrd+json",
            "application/json,text/html"})
    @DisplayName("an invalid_scope body whose Content-Type is not application/json is an unknown error code")
    void shouldNotClassifyErrorBodyWithoutJsonContentType(String contentTypes, URIBuilder uriBuilder) {
        moduleDispatcher.withContentTypes(contentTypeLines(contentTypes));
        moduleDispatcher.failWith(400, "{\"error\":\"invalid_scope\"}");

        TransportException thrown = redeemExpectingFailure(uriBuilder, null);

        assertAll("unknown error code",
                () -> assertFalse(thrown instanceof InvalidScopeException,
                        "a body not declared as JSON must never be read as a scope refusal"),
                () -> assertFalse(thrown instanceof RedeemedResponseException,
                        "a non-success response never redeemed the grant"),
                () -> assertTrue(thrown.getMessage().contains("unexpected HTTP status 400"),
                        "the failure must be the ordinary non-success outcome, but was: " + thrown.getMessage()));
    }

    @Test
    @DisplayName("an invalid_scope body served as application/json with a charset is the typed refusal")
    void shouldClassifyErrorBodyWithJsonCharset(URIBuilder uriBuilder) {
        moduleDispatcher.withContentTypes("application/json;charset=utf-8");
        moduleDispatcher.failWith(400, "{\"error\":\"invalid_scope\"}");

        TransportException thrown = redeemExpectingFailure(uriBuilder, null);

        assertInstanceOf(InvalidScopeException.class, thrown);
    }

    private TokenResponse redeem(URIBuilder uriBuilder, SenderConstraint constraint) {
        var client = new TokenEndpointClient(config());
        String endpoint = uriBuilder.addPathSegment("token").buildAsString();
        return client.requestToken(endpoint, Map.of("grant_type", "authorization_code"), Map.of(), constraint);
    }

    private static String tokenResponse(String accessToken, String tokenType) {
        return "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"" + tokenType
                + "\",\"expires_in\":300}";
    }
}
