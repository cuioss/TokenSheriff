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
package de.cuioss.sheriff.token.client.hardening;

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.flow.CredentialRejectedException;
import de.cuioss.sheriff.token.client.flow.InvalidScopeException;
import de.cuioss.sheriff.token.client.flow.RedeemedResponseException;
import de.cuioss.sheriff.token.client.flow.TokenEndpointClient;
import de.cuioss.sheriff.token.client.token.TokenResponse;
import de.cuioss.sheriff.token.commons.error.ErrorTextSanitizer;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.test.dispatcher.TokenDispatcher;
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
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valid-JSON adversarial token-endpoint responses (TC-7). Every body below is well-formed JSON served
 * with HTTP 200 and {@code application/json}, so neither the status check nor the JSON syntax rejects
 * it: the response <em>shape</em> is what is hostile. Each case pins the exact typed failure
 * {@link TokenEndpointClient} raises. Because every body arrives after a success status, the grant is
 * already redeemed, and every refusal is a {@link RedeemedResponseException}.
 * <p>
 * The error-path cases serve HTTP 400 instead: an RFC 6749 §5.2 error body that repeats its top-level
 * {@code error} member is ambiguous, so it must stay a plain {@link TransportException} and never be
 * read as a {@link CredentialRejectedException}, with an unambiguous {@code invalid_grant} body as the
 * positive control. The same rule holds for {@code invalid_scope}: a repeated member or an oversize body
 * is an unknown and never an {@code InvalidScopeException}, and the {@code error_description} a
 * recognized scope refusal carries is sanitized and truncated, never raw.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("TokenEndpointClient against valid-JSON hostile token responses (TC-7)")
class TokenResponseAdversarialTest {

    private static final String VALID_PREFIX = "{\"access_token\":\"token\",\"token_type\":\"Bearer\"";
    private static final String DUPLICATE_MEMBER_REFUSAL = "Token endpoint response repeats the top-level member ";

    /**
     * The shared token-endpoint dispatcher; {@link TokenDispatcher#respondWith(String)} serves each case's
     * body with HTTP 200 and {@code application/json}, so the response shape — not the status or the
     * media type — is what must be refused.
     */
    @Getter
    private final TokenDispatcher moduleDispatcher = new TokenDispatcher();

    @BeforeEach
    void resetDispatcher() {
        moduleDispatcher.reset();
    }

    @Test
    @DisplayName("Should refuse a response without access_token")
    void shouldRefuseMissingAccessToken(URIBuilder uriBuilder) {
        assertRedeemedFailure("{\"token_type\":\"Bearer\",\"expires_in\":300}", uriBuilder);
    }

    @ParameterizedTest(name = "expires_in = {0}")
    @ValueSource(strings = {"\"soon\"", "300.5", "true", "{\"seconds\":300}", "[300]", "99999999999999999999"})
    @DisplayName("Should refuse a wrong-typed or out-of-range expires_in")
    void shouldRefuseWrongTypedExpiresIn(String expiresIn, URIBuilder uriBuilder) {
        assertRedeemedFailure(VALID_PREFIX + ",\"expires_in\":" + expiresIn + "}", uriBuilder);
    }

    @Test
    @DisplayName("Should accept a quoted numeric expires_in as the observed lenient behaviour")
    void shouldAcceptQuotedNumericExpiresIn(URIBuilder uriBuilder) {
        // Pinned as observed: the JSON library reads a quoted integer into the long field. This
        // lenient acceptance is a risk named in the pull request for this hardening pass, not an
        // endorsed contract.
        TokenResponse response = request(VALID_PREFIX + ",\"expires_in\":\"300\"}", uriBuilder);

        assertEquals(300L, response.getExpiresIn(), "a quoted integer is read as its numeric value");
    }

    @Test
    @DisplayName("Should refuse a response that repeats access_token with different values")
    void shouldRefuseDuplicateAccessToken(URIBuilder uriBuilder) {
        RedeemedResponseException thrown = assertRedeemedFailure(
                "{\"access_token\":\"first\",\"token_type\":\"Bearer\",\"access_token\":\"second\"}", uriBuilder);

        assertEquals(DUPLICATE_MEMBER_REFUSAL + "'access_token'", thrown.getMessage(),
                "the refusal must name the repeated member");
    }

    @Test
    @DisplayName("Should refuse a response that repeats token_type as Bearer and DPoP")
    void shouldRefuseDuplicateTokenType(URIBuilder uriBuilder) {
        RedeemedResponseException thrown = assertRedeemedFailure(
                "{\"access_token\":\"token\",\"token_type\":\"Bearer\",\"token_type\":\"DPoP\"}", uriBuilder);

        assertEquals(DUPLICATE_MEMBER_REFUSAL + "'token_type'", thrown.getMessage(),
                "the refusal must name the repeated member");
    }

    @Test
    @DisplayName("Should refuse an access_token longer than the parser string bound")
    void shouldRefuseOversizeAccessToken(URIBuilder uriBuilder) {
        String oversizeToken = "a".repeat(ParserConfig.DEFAULT_MAX_STRING_LENGTH + 1);

        assertRedeemedFailure("{\"access_token\":\"" + oversizeToken + "\",\"token_type\":\"Bearer\"}", uriBuilder);
    }

    @Test
    @DisplayName("Should refuse a body larger than the maximum payload size")
    void shouldRefuseOversizeBody(URIBuilder uriBuilder) {
        String padding = "p".repeat(ParserConfig.DEFAULT_MAX_PAYLOAD_SIZE);

        assertRedeemedFailure(VALID_PREFIX + ",\"padding\":\"" + padding + "\"}", uriBuilder);
    }

    @ParameterizedTest(name = "token_type = \"{0}\"")
    @ValueSource(strings = {"mac", "", "bearerx"})
    @DisplayName("Should refuse an unknown token_type")
    void shouldRefuseUnknownTokenType(String tokenType, URIBuilder uriBuilder) {
        assertRedeemedFailure("{\"access_token\":\"token\",\"token_type\":\"" + tokenType + "\"}", uriBuilder);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{\"error\":\"temporarily_unavailable\",\"error\":\"invalid_grant\"}",
            "{\"error\":\"invalid_grant\",\"error\":\"temporarily_unavailable\"}"})
    @DisplayName("Should not read a 4xx error body repeating the error member as a rejected credential")
    void shouldNotRejectCredentialOnDuplicateErrorMember(String body, URIBuilder uriBuilder) {
        moduleDispatcher.respondWith(400, body);
        var client = new TokenEndpointClient(config());
        String tokenEndpoint = tokenEndpoint(uriBuilder);
        Map<String, String> formParameters = Map.of("grant_type", "client_credentials");
        Map<String, String> requestHeaders = Map.of();

        TransportException thrown = assertThrows(TransportException.class,
                () -> client.requestToken(tokenEndpoint, formParameters, requestHeaders));

        assertEquals(TransportException.class, thrown.getClass(),
                "an ambiguous error body must stay a plain transport failure, never a CredentialRejectedException");
        assertEquals("Token endpoint returned unexpected HTTP status 400", thrown.getMessage());
    }

    @Test
    @DisplayName("Should read a 4xx error body with a single invalid_grant member as a rejected credential")
    void shouldRejectCredentialOnSingleInvalidGrant(URIBuilder uriBuilder) {
        moduleDispatcher.respondWith(400, "{\"error\":\"invalid_grant\",\"error_description\":\"expired\"}");
        var client = new TokenEndpointClient(config());
        String tokenEndpoint = tokenEndpoint(uriBuilder);
        Map<String, String> formParameters = Map.of("grant_type", "client_credentials");
        Map<String, String> requestHeaders = Map.of();

        CredentialRejectedException thrown = assertThrows(CredentialRejectedException.class,
                () -> client.requestToken(tokenEndpoint, formParameters, requestHeaders),
                "the positive control: an unambiguous invalid_grant body is a rejected credential");

        assertEquals("Token endpoint rejected the presented credential with HTTP 400 (RFC 6749 §5.2 'invalid_grant')",
                thrown.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{\"error\":\"invalid_scope\",\"error\":\"invalid_scope\"}",
            "{\"error\":\"temporarily_unavailable\",\"error\":\"invalid_scope\"}",
            "{\"error\":\"invalid_scope\",\"error_description\":\"a\",\"error_description\":\"b\"}"})
    @DisplayName("Should not read a 4xx invalid_scope body repeating a top-level member as a scope refusal")
    void shouldNotRefuseScopeOnDuplicateMember(String body, URIBuilder uriBuilder) {
        TransportException thrown = requestFailing(400, body, uriBuilder);

        assertEquals(TransportException.class, thrown.getClass(),
                "an ambiguous error body must stay a plain transport failure, never an InvalidScopeException");
        assertEquals("Token endpoint returned unexpected HTTP status 400", thrown.getMessage());
    }

    @Test
    @DisplayName("Should not read an oversize 4xx invalid_scope body as a scope refusal")
    void shouldNotRefuseScopeOnOversizeBody(URIBuilder uriBuilder) {
        String padding = "p".repeat(ParserConfig.DEFAULT_MAX_PAYLOAD_SIZE);

        TransportException thrown = requestFailing(400,
                "{\"error\":\"invalid_scope\",\"padding\":\"" + padding + "\"}", uriBuilder);

        assertFalse(thrown instanceof InvalidScopeException,
                "a body past the payload bound is an unknown, never a scope refusal");
    }

    @Test
    @DisplayName("Should carry an invalid_scope error_description with control characters only in escaped form")
    void shouldSanitizeControlCharactersInScopeDescription(URIBuilder uriBuilder) {
        // JSON escapes: the decoded description holds a real CR, LF, NUL and U+2028.
        TransportException thrown = requestFailing(400,
                "{\"error\":\"invalid_scope\",\"error_description\":\"bad\\r\\nINFO forged\\u0000\\u2028x\"}",
                uriBuilder);

        InvalidScopeException scopeRefusal = assertInstanceOf(InvalidScopeException.class, thrown);
        String description = scopeRefusal.getErrorDescription().orElseThrow();
        assertAll("sanitized error_description",
                () -> assertFalse(description.chars().anyMatch(c -> c < 0x20 || c == 0x2028),
                        "no raw control character or line separator may survive, but was: " + description),
                () -> assertEquals("bad\\r\\nINFO forged\\u0000\\u2028x", description,
                        "each unsafe character must survive in escaped form"));
    }

    @Test
    @DisplayName("Should truncate an over-long invalid_scope error_description")
    void shouldTruncateLongScopeDescription(URIBuilder uriBuilder) {
        String longDescription = "d".repeat(ErrorTextSanitizer.MAX_LENGTH + 50);

        TransportException thrown = requestFailing(400,
                "{\"error\":\"invalid_scope\",\"error_description\":\"" + longDescription + "\"}", uriBuilder);

        InvalidScopeException scopeRefusal = assertInstanceOf(InvalidScopeException.class, thrown);
        assertEquals("d".repeat(ErrorTextSanitizer.MAX_LENGTH) + ErrorTextSanitizer.TRUNCATION_MARKER,
                scopeRefusal.getErrorDescription().orElseThrow());
    }

    @ParameterizedTest(name = "error_description = {0}")
    @ValueSource(strings = {"null", "42", "{\"text\":\"x\"}", "[\"x\"]"})
    @DisplayName("Should keep the scope refusal but carry no description when error_description is not a string")
    void shouldDropNonStringScopeDescription(String descriptionValue, URIBuilder uriBuilder) {
        TransportException thrown = requestFailing(400,
                "{\"error_description\":" + descriptionValue + ",\"error\":\"invalid_scope\"}", uriBuilder);

        InvalidScopeException scopeRefusal = assertInstanceOf(InvalidScopeException.class, thrown);
        assertTrue(scopeRefusal.getErrorDescription().isEmpty(),
                "a non-string error_description must not be carried");
    }

    private TransportException requestFailing(int status, String body, URIBuilder uriBuilder) {
        moduleDispatcher.respondWith(status, body);
        var client = new TokenEndpointClient(config());
        String tokenEndpoint = tokenEndpoint(uriBuilder);
        Map<String, String> formParameters = Map.of("grant_type", "refresh_token");
        Map<String, String> requestHeaders = Map.of();

        return assertThrows(TransportException.class,
                () -> client.requestToken(tokenEndpoint, formParameters, requestHeaders));
    }

    private RedeemedResponseException assertRedeemedFailure(String body, URIBuilder uriBuilder) {
        moduleDispatcher.respondWith(body);
        var client = new TokenEndpointClient(config());
        String tokenEndpoint = tokenEndpoint(uriBuilder);
        Map<String, String> formParameters = Map.of("grant_type", "client_credentials");
        Map<String, String> requestHeaders = Map.of();

        return assertThrows(RedeemedResponseException.class,
                () -> client.requestToken(tokenEndpoint, formParameters, requestHeaders),
                "a hostile token response must be refused after the grant was redeemed");
    }

    private TokenResponse request(String body, URIBuilder uriBuilder) {
        moduleDispatcher.respondWith(body);
        var client = new TokenEndpointClient(config());
        return client.requestToken(tokenEndpoint(uriBuilder), Map.of("grant_type", "client_credentials"), Map.of());
    }

    /** The mock token endpoint, at the shared {@link TokenDispatcher#LOCAL_PATH}. */
    private static String tokenEndpoint(URIBuilder uriBuilder) {
        return uriBuilder.addPathSegments("oidc", "token").buildAsString();
    }

    private static ClientConfiguration config() {
        return ClientConfiguration.builder()
                .issuer("https://" + Generators.letterStrings(3, 10).next() + ".example.com")
                .clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(true)
                .allowLoopbackEgress(true)
                .build();
    }
}
