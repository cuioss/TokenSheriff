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
import de.cuioss.sheriff.token.client.dpop.DpopProofGenerator;
import de.cuioss.sheriff.token.client.dpop.SenderConstraint;
import de.cuioss.sheriff.token.client.token.TokenResponse;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import lombok.Getter;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Transport-level coverage for {@link TokenEndpointClient}: the M8 log-injection defense on the
 * parse-error path, and the boundaries of the RFC 9449 §8 {@code use_dpop_nonce} retry.
 * <p>
 * <strong>M8:</strong> the token endpoint response body is authorization-server-controlled. When it is
 * malformed, DSL-JSON raises a parse error whose message echoes the offending fragment verbatim —
 * including any raw {@code CR}/{@code LF} the AS embedded. {@link TokenEndpointClient} must route that
 * fragment through {@code LogSanitizer.sanitize} (CWE-117) before it reaches the log appender or the
 * {@link TransportException} message, so the AS cannot forge a log line.
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
 * <p>
 * <strong>Nonce retry:</strong> a {@code use_dpop_nonce} challenge is retried exactly <em>once</em>, and
 * only when the challenge is genuine. The three cases below pin the boundaries the happy-path retry
 * coverage does not reach: a repeated challenge must not loop, a blank {@code DPoP-Nonce} must not be
 * treated as a challenge, and a challenge presented against an mTLS constraint must not be answered
 * with a DPoP proof.
 * <p>
 * <strong>Issuance-time binding:</strong> under a DPoP constraint the issued token must be bound to the
 * proof key — {@code token_type=DPoP}, and a JWT access token's {@code cnf.jkt} equal to the proof-key
 * thumbprint. A {@code Bearer} downgrade, a foreign {@code jkt}, a missing {@code cnf.jkt} and a
 * malformed {@code cnf} (not an object, or a non-string {@code jkt}) are each rejected with
 * {@link RedeemedResponseException}; an access token that is not a JWS with a JSON-object payload
 * (opaque, encrypted, empty or non-base64url payload part, non-object JSON payload) skips only the
 * {@code jkt} comparison; mTLS and unconstrained requests are not checked.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("TokenEndpointClient parse-error sanitization (M8) and use_dpop_nonce retry boundaries")
class TokenEndpointClientTest {

    /** Cached 2048-bit RSA key pair, generated once for the whole class (DPoP proof material). */
    private static final KeyPair RSA_KEY_PAIR;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            RSA_KEY_PAIR = generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key pair generation failed", e);
        }
    }

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

    private static SenderConstraint dpopConstraint() {
        return SenderConstraint.dpop(new DpopProofGenerator(RSA_KEY_PAIR, "RS256"));
    }

    /**
     * Redeems against the stub endpoint under the supplied constraint and returns the surfaced
     * transport failure, so each nonce-boundary case asserts only on what distinguishes it: the
     * number of attempts the client actually made.
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

    @Test
    @DisplayName("a malformed response with an embedded CR/LF is sanitized in the TransportException")
    void shouldSanitizeParseErrorFragment(URIBuilder uriBuilder) {
        var config = config();
        var client = new TokenEndpointClient(config);
        String endpoint = uriBuilder.addPathSegment("token").buildAsString();
        Map<String, String> requestParams = Map.of("grant_type", "authorization_code");
        Map<String, String> requestHeaders = Map.of();

        TransportException thrown = assertThrows(TransportException.class,
                () -> client.requestToken(endpoint, requestParams, requestHeaders),
                "a malformed token response must surface as a TransportException");

        String message = thrown.getMessage();
        assertAll("sanitized parse-error fragment",
                () -> assertFalse(message.indexOf('\r') >= 0,
                        "a raw CR from the AS-controlled body must not reach the exception message"),
                () -> assertFalse(message.indexOf('\n') >= 0,
                        "a raw LF from the AS-controlled body must not reach the exception message"),
                () -> assertTrue(message.contains("\\r") && message.contains("\\n"),
                        "the injected CR/LF must survive in escaped form, proving the fragment was carried and sanitized"));
    }

    @Test
    @DisplayName("a 200 body truncated inside a string escape is rejected as a redeemed parse failure")
    void shouldRejectSuccessBodyTruncatedMidEscape(URIBuilder uriBuilder) {
        moduleDispatcher.respondWith("{\"access_token\":\"abc\\");
        var client = new TokenEndpointClient(config());
        String endpoint = uriBuilder.addPathSegment("token").buildAsString();
        Map<String, String> requestParams = Map.of("grant_type", "refresh_token");
        Map<String, String> requestHeaders = Map.of();

        RedeemedResponseException thrown = assertThrows(RedeemedResponseException.class,
                () -> client.requestToken(endpoint, requestParams, requestHeaders),
                "an unreadable 200 body must surface as the post-redemption parse failure, never unchecked");

        assertTrue(thrown.getMessage().startsWith("Failed to parse token endpoint response"),
                "the rejection must take the existing parse-failure path, but was: " + thrown.getMessage());
    }

    @Test
    @DisplayName("a 4xx error body truncated inside a string escape is an unknown error, not invalid_grant")
    void shouldTreatErrorBodyTruncatedMidEscapeAsUnparseable(URIBuilder uriBuilder) {
        moduleDispatcher.failWith("{\"error\":\"invalid_grant\\");

        TransportException thrown = redeemExpectingFailure(uriBuilder, null);

        assertAll("unparseable error body",
                () -> assertFalse(thrown instanceof CredentialRejectedException,
                        "an unreadable error body must never be classified as a dead credential"),
                () -> assertFalse(thrown instanceof RedeemedResponseException,
                        "a non-success response never redeemed the grant"),
                () -> assertTrue(thrown.getMessage().contains("unexpected HTTP status 400"),
                        "the failure must be the ordinary non-success outcome, but was: " + thrown.getMessage()));
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
    @DisplayName("invalid_grant on a 4xx is still the credential-rejected refusal, not a client-authentication one")
    void shouldKeepInvalidGrantAsCredentialRejected(URIBuilder uriBuilder) {
        moduleDispatcher.failWith(400, "{\"error\":\"invalid_grant\"}");

        TransportException thrown = redeemExpectingFailure(uriBuilder, null);

        assertAll("credential-rejected refusal unchanged",
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
    @DisplayName("a refusal outside the invalid_scope allow-list keeps today's plain transport failure")
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
    @DisplayName("an invalid_scope body served as application/json with a charset is still the typed refusal")
    void shouldClassifyErrorBodyWithJsonCharset(URIBuilder uriBuilder) {
        moduleDispatcher.withContentTypes("application/json;charset=utf-8");
        moduleDispatcher.failWith(400, "{\"error\":\"invalid_scope\"}");

        TransportException thrown = redeemExpectingFailure(uriBuilder, null);

        assertInstanceOf(InvalidScopeException.class, thrown);
    }

    @Test
    @DisplayName("a second use_dpop_nonce challenge on the retry does not trigger a further retry")
    void shouldNotRetryTwiceOnRepeatedNonceChallenge(URIBuilder uriBuilder) {
        moduleDispatcher.challengeWithNonce(Generators.letterStrings(16, 32).next());

        TransportException thrown = redeemExpectingFailure(uriBuilder, dpopConstraint());

        assertAll("single bounded retry",
                () -> assertEquals(2, moduleDispatcher.getCallCounter(),
                        "the challenge must be answered exactly once, never retried in a loop"),
                () -> assertTrue(thrown.getMessage().contains("400"),
                        "the unanswered challenge must surface as the endpoint's own status"));
    }

    @Test
    @DisplayName("a blank DPoP-Nonce header on a failed response does not trigger a retry")
    void shouldNotRetryOnBlankNonceHeader(URIBuilder uriBuilder) {
        moduleDispatcher.challengeWithNonce("   ");

        redeemExpectingFailure(uriBuilder, dpopConstraint());

        assertEquals(1, moduleDispatcher.getCallCounter(),
                "a blank nonce is not a usable challenge and must not be echoed into a retry");
    }

    @Test
    @DisplayName("a DPoP-Nonce challenge against an mTLS constraint does not trigger a retry")
    void shouldNotRetryNonceChallengeUnderMtlsConstraint(URIBuilder uriBuilder) {
        moduleDispatcher.challengeWithNonce(Generators.letterStrings(16, 32).next());
        var mtls = SenderConstraint.mtls(Generators.letterStrings(20, 40).next());

        redeemExpectingFailure(uriBuilder, mtls);

        assertEquals(1, moduleDispatcher.getCallCounter(),
                "an mTLS binding is transport-level; a DPoP nonce challenge is not answerable and must not be retried");
    }

    @Test
    @DisplayName("a Bearer token_type issued under a DPoP constraint is rejected as a redeemed downgrade")
    void shouldRejectBearerTokenTypeUnderDpopConstraint(URIBuilder uriBuilder) {
        SenderConstraint constraint = dpopConstraint();
        moduleDispatcher.respondWith(tokenResponse(jws(cnfPayload(jktOf(constraint))), "Bearer"));

        RedeemedResponseException thrown = redeemExpectingRedeemedRejection(uriBuilder, constraint);

        assertTrue(thrown.getMessage().contains("token_type 'Bearer'"),
                "the rejection must name the downgraded token_type, but was: " + thrown.getMessage());
    }

    @Test
    @DisplayName("a DPoP JWT access token whose cnf.jkt differs from the proof-key thumbprint is rejected")
    void shouldRejectMismatchedJktUnderDpopConstraint(URIBuilder uriBuilder) {
        SenderConstraint constraint = dpopConstraint();
        String foreignJkt = Generators.letterStrings(43, 43).next();
        moduleDispatcher.respondWith(tokenResponse(jws(cnfPayload(foreignJkt)), "DPoP"));

        RedeemedResponseException thrown = redeemExpectingRedeemedRejection(uriBuilder, constraint);

        assertTrue(thrown.getMessage().contains("does not match the DPoP proof-key thumbprint"),
                "the rejection must name the jkt mismatch, but was: " + thrown.getMessage());
    }

    @Test
    @DisplayName("a DPoP JWT access token without a cnf claim is rejected as unbound")
    void shouldRejectJwtWithoutCnfUnderDpopConstraint(URIBuilder uriBuilder) {
        SenderConstraint constraint = dpopConstraint();
        moduleDispatcher.respondWith(tokenResponse(jws(subjectOnlyPayload()), "DPoP"));

        RedeemedResponseException thrown = redeemExpectingRedeemedRejection(uriBuilder, constraint);

        assertTrue(thrown.getMessage().contains("without a cnf.jkt binding"),
                "the rejection must name the missing binding, but was: " + thrown.getMessage());
    }

    @Test
    @DisplayName("a DPoP JWT access token whose cnf carries no jkt member is rejected as unbound")
    void shouldRejectCnfWithoutJktUnderDpopConstraint(URIBuilder uriBuilder) {
        SenderConstraint constraint = dpopConstraint();
        String x5tOnly = "{\"sub\":\"" + Generators.letterStrings(8, 16).next()
                + "\",\"cnf\":{\"x5t#S256\":\"" + Generators.letterStrings(43, 43).next() + "\"}}";
        moduleDispatcher.respondWith(tokenResponse(jws(x5tOnly), "DPoP"));

        RedeemedResponseException thrown = redeemExpectingRedeemedRejection(uriBuilder, constraint);

        assertTrue(thrown.getMessage().contains("without a cnf.jkt binding"),
                "a cnf without jkt is not a DPoP binding, but the rejection was: " + thrown.getMessage());
    }

    @Test
    @DisplayName("a DPoP JWT access token bound to the proof-key thumbprint is accepted")
    void shouldAcceptMatchingJktUnderDpopConstraint(URIBuilder uriBuilder) {
        SenderConstraint constraint = dpopConstraint();
        String accessToken = jws(cnfPayload(jktOf(constraint)));
        moduleDispatcher.respondWith(tokenResponse(accessToken, "DPoP"));

        TokenResponse response = redeem(uriBuilder, constraint);

        assertEquals(accessToken, response.accessToken, "the bound access token must be returned unchanged");
    }

    @Test
    @DisplayName("the DPoP token_type is matched case-insensitively (RFC 6749 §5.1)")
    void shouldAcceptLowerCaseDpopTokenType(URIBuilder uriBuilder) {
        SenderConstraint constraint = dpopConstraint();
        moduleDispatcher.respondWith(tokenResponse(jws(cnfPayload(jktOf(constraint))), "dpop"));

        TokenResponse response = redeem(uriBuilder, constraint);

        assertEquals("dpop", response.tokenType, "a lower-case DPoP token_type is the DPoP type");
    }

    @Test
    @DisplayName("an opaque access token with token_type DPoP is accepted; only the jkt comparison is skipped")
    void shouldAcceptOpaqueAccessTokenUnderDpopConstraint(URIBuilder uriBuilder) {
        String opaque = Generators.letterStrings(30, 60).next();
        moduleDispatcher.respondWith(tokenResponse(opaque, "DPoP"));

        TokenResponse response = redeem(uriBuilder, dpopConstraint());

        assertEquals(opaque, response.accessToken, "an opaque DPoP token carries no cnf to compare");
    }

    @Test
    @DisplayName("an encrypted (five-part JWE) access token with token_type DPoP skips the jkt comparison")
    void shouldAcceptJweAccessTokenUnderDpopConstraint(URIBuilder uriBuilder) {
        String jwe = String.join(".", segment(), segment(), segment(), segment(), segment());
        moduleDispatcher.respondWith(tokenResponse(jwe, "DPoP"));

        TokenResponse response = redeem(uriBuilder, dpopConstraint());

        assertEquals(jwe, response.accessToken, "a JWE payload cannot be read, so only token_type is checked");
    }

    @Test
    @DisplayName("a DPoP JWT access token whose cnf is a string is rejected, not skipped as undecodable")
    void shouldRejectStringCnfUnderDpopConstraint(URIBuilder uriBuilder) {
        assertMalformedCnfRejected(uriBuilder, "\"" + Generators.letterStrings(8, 16).next() + "\"");
    }

    @Test
    @DisplayName("a DPoP JWT access token whose cnf is an array is rejected, not skipped as undecodable")
    void shouldRejectArrayCnfUnderDpopConstraint(URIBuilder uriBuilder) {
        assertMalformedCnfRejected(uriBuilder, "[\"" + Generators.letterStrings(8, 16).next() + "\"]");
    }

    @Test
    @DisplayName("a DPoP JWT access token whose cnf.jkt is a number is rejected, not skipped as undecodable")
    void shouldRejectNumericJktUnderDpopConstraint(URIBuilder uriBuilder) {
        assertMalformedCnfRejected(uriBuilder, "{\"jkt\":" + Generators.integers(1, 100_000).next() + "}");
    }

    @Test
    @DisplayName("a DPoP JWT access token whose cnf.jkt is an array is rejected, not skipped as undecodable")
    void shouldRejectArrayJktUnderDpopConstraint(URIBuilder uriBuilder) {
        SenderConstraint constraint = dpopConstraint();
        assertMalformedCnfRejected(uriBuilder, constraint, "{\"jkt\":[\"" + jktOf(constraint) + "\"]}");
    }

    @Test
    @DisplayName("a DPoP JWT access token whose cnf.jkt is null is rejected as unbound")
    void shouldRejectNullJktUnderDpopConstraint(URIBuilder uriBuilder) {
        SenderConstraint constraint = dpopConstraint();
        moduleDispatcher.respondWith(tokenResponse(jws(payloadWithCnf("{\"jkt\":null}")), "DPoP"));

        RedeemedResponseException thrown = redeemExpectingRedeemedRejection(uriBuilder, constraint);

        assertTrue(thrown.getMessage().contains("without a cnf.jkt binding"),
                "a null jkt is no binding, but the rejection was: " + thrown.getMessage());
    }

    @Test
    @DisplayName("a three-part token with an empty payload part skips only the jkt comparison")
    void shouldAcceptEmptyPayloadPartUnderDpopConstraint(URIBuilder uriBuilder) {
        assertJktComparisonSkipped(uriBuilder, segment() + ".." + segment());
    }

    @Test
    @DisplayName("a three-part token whose payload part is not base64url skips only the jkt comparison")
    void shouldAcceptNonBase64UrlPayloadUnderDpopConstraint(URIBuilder uriBuilder) {
        assertJktComparisonSkipped(uriBuilder, segment() + ".!!not*base64url!!." + segment());
    }

    @Test
    @DisplayName("a JWS whose payload is a JSON array skips only the jkt comparison")
    void shouldAcceptJsonArrayPayloadUnderDpopConstraint(URIBuilder uriBuilder) {
        String foreignJkt = Generators.letterStrings(43, 43).next();
        assertJktComparisonSkipped(uriBuilder, jws("[{\"cnf\":{\"jkt\":\"" + foreignJkt + "\"}}]"));
    }

    @Test
    @DisplayName("a JWS whose payload is a JSON string skips only the jkt comparison")
    void shouldAcceptJsonStringPayloadUnderDpopConstraint(URIBuilder uriBuilder) {
        assertJktComparisonSkipped(uriBuilder, jws("\"" + Generators.letterStrings(8, 16).next() + "\""));
    }

    @Test
    @DisplayName("a JWS whose payload is the JSON literal null skips only the jkt comparison")
    void shouldAcceptJsonNullPayloadUnderDpopConstraint(URIBuilder uriBuilder) {
        String accessToken = jws("null");
        moduleDispatcher.respondWith(tokenResponse(accessToken, "DPoP"));

        TokenResponse response = redeem(uriBuilder, dpopConstraint());

        assertAll("JSON null payload",
                () -> assertEquals(accessToken, response.accessToken,
                        "a null payload carries no readable cnf, so the token is accepted unchanged"),
                () -> assertEquals("DPoP", response.tokenType, "the token_type check still applies"));
    }

    @Test
    @DisplayName("an mTLS-constrained request still accepts a Bearer token")
    void shouldAcceptBearerUnderMtlsConstraint(URIBuilder uriBuilder) {
        moduleDispatcher.respondWith(tokenResponse(jws(subjectOnlyPayload()), "Bearer"));
        var mtls = SenderConstraint.mtls(Generators.letterStrings(20, 40).next());

        TokenResponse response = redeem(uriBuilder, mtls);

        assertEquals("Bearer", response.tokenType, "an mTLS-bound token is presented as Bearer");
    }

    @Test
    @DisplayName("an unconstrained request still accepts a Bearer JWT without cnf")
    void shouldAcceptBearerWithoutConstraint(URIBuilder uriBuilder) {
        moduleDispatcher.respondWith(tokenResponse(jws(subjectOnlyPayload()), "Bearer"));

        TokenResponse response = redeem(uriBuilder, null);

        assertEquals("Bearer", response.tokenType, "no sender-constraint means no binding check");
    }

    private TokenResponse redeem(URIBuilder uriBuilder, SenderConstraint constraint) {
        var client = new TokenEndpointClient(config());
        String endpoint = uriBuilder.addPathSegment("token").buildAsString();
        return client.requestToken(endpoint, Map.of("grant_type", "authorization_code"), Map.of(), constraint);
    }

    private RedeemedResponseException redeemExpectingRedeemedRejection(URIBuilder uriBuilder,
            SenderConstraint constraint) {
        var client = new TokenEndpointClient(config());
        String endpoint = uriBuilder.addPathSegment("token").buildAsString();
        Map<String, String> requestParams = Map.of("grant_type", "authorization_code");
        Map<String, String> requestHeaders = Map.of();

        RedeemedResponseException thrown = assertThrows(RedeemedResponseException.class,
                () -> client.requestToken(endpoint, requestParams, requestHeaders, constraint),
                "an unbound token issued under a DPoP constraint must be rejected after redemption");
        assertEquals(1, moduleDispatcher.getCallCounter(), "the rejection follows a single, successful redemption");
        return thrown;
    }

    private void assertMalformedCnfRejected(URIBuilder uriBuilder, String cnfJson) {
        assertMalformedCnfRejected(uriBuilder, dpopConstraint(), cnfJson);
    }

    /**
     * A payload that is a JSON object is a JWT, so a {@code cnf} that cannot be read as an object with
     * a string {@code jkt} must fail closed exactly like a missing {@code cnf.jkt} — it must never fall
     * into the "not a JWT" skip, which would accept the token unchecked.
     */
    private void assertMalformedCnfRejected(URIBuilder uriBuilder, SenderConstraint constraint, String cnfJson) {
        moduleDispatcher.respondWith(tokenResponse(jws(payloadWithCnf(cnfJson)), "DPoP"));

        RedeemedResponseException thrown = redeemExpectingRedeemedRejection(uriBuilder, constraint);

        assertTrue(thrown.getMessage().contains("cnf claim is not an object with a string jkt"),
                "a malformed cnf must be rejected as unbound, but the rejection was: " + thrown.getMessage());
    }

    /**
     * An access token that is not a JWS with a JSON-object payload skips only the {@code jkt}
     * comparison: with {@code token_type=DPoP} it is accepted unchanged.
     */
    private void assertJktComparisonSkipped(URIBuilder uriBuilder, String accessToken) {
        moduleDispatcher.respondWith(tokenResponse(accessToken, "DPoP"));

        TokenResponse response = redeem(uriBuilder, dpopConstraint());

        assertEquals(accessToken, response.accessToken, "a token that is not a JWT carries no readable cnf");
    }

    private static String payloadWithCnf(String cnfJson) {
        return "{\"sub\":\"" + Generators.letterStrings(8, 16).next() + "\",\"cnf\":" + cnfJson + "}";
    }

    private static String jktOf(SenderConstraint constraint) {
        return constraint.binding().confirmation();
    }

    private static String tokenResponse(String accessToken, String tokenType) {
        return "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"" + tokenType
                + "\",\"expires_in\":300}";
    }

    private static String cnfPayload(String jkt) {
        return "{\"sub\":\"" + Generators.letterStrings(8, 16).next() + "\",\"cnf\":{\"jkt\":\"" + jkt + "\"}}";
    }

    private static String subjectOnlyPayload() {
        return "{\"sub\":\"" + Generators.letterStrings(8, 16).next() + "\"}";
    }

    /**
     * Builds a compact JWS around {@code payloadJson}. The signature segment is filler: the binding
     * check only decodes the payload and never verifies the signature.
     */
    private static String jws(String payloadJson) {
        return base64Url("{\"alg\":\"RS256\",\"typ\":\"at+jwt\"}") + "." + base64Url(payloadJson) + "." + segment();
    }

    private static String segment() {
        return base64Url(Generators.letterStrings(16, 32).next());
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Serves the token endpoint in one of three shapes: a syntactically malformed 200 body whose parse
     * error echoes an embedded {@code CR}/{@code LF} (the default, driving the M8 case); an RFC 9449
     * §8 {@code use_dpop_nonce} challenge — a 400 carrying a {@code DPoP-Nonce} header — served on
     * <em>every</em> call, so a client that retried more than once is caught by the call counter; or a
     * configured 200 success body, driving the issuance-time sender-constraint binding cases.
     */
    static final class TokenEndpointStubDispatcher implements ModuleDispatcherElement {

        // The unparseable numeric expires_in embeds a raw CR/LF the AS controls; DSL-JSON's parse error
        // echoes that fragment verbatim, so it exercises the sanitizer on the parse-error path.
        private static final String MALFORMED_BODY =
                "{\"access_token\":\"a\",\"token_type\":\"Bearer\",\"expires_in\":9\r\nINJECTED9}";

        private static final String NONCE_CHALLENGE_BODY = "{\"error\":\"use_dpop_nonce\"}";

        private static final int HTTP_BAD_REQUEST = 400;

        private final AtomicInteger callCounter = new AtomicInteger();

        /** The {@code DPoP-Nonce} value to challenge with, or {@code null} to serve the malformed body. */
        private volatile String challengeNonce;

        /** The 200 success body to serve, or {@code null} to serve the malformed body. */
        private volatile String successBody;

        /** The error body to serve (without a nonce), or {@code null} for the other shapes. */
        private volatile String errorBody;

        /** The status the {@link #errorBody} is served with. */
        private volatile int errorStatus = HTTP_BAD_REQUEST;

        private static final List<String> DEFAULT_CONTENT_TYPES = List.of("application/json");

        /** The {@code Content-Type} lines the success and error bodies are served with. */
        private volatile List<String> contentTypes = DEFAULT_CONTENT_TYPES;

        /** Serve {@code body} as a 400 error response without a {@code DPoP-Nonce} on every call. */
        void failWith(String body) {
            failWith(HTTP_BAD_REQUEST, body);
        }

        /** Serve {@code body} with the non-success {@code status} and no {@code DPoP-Nonce} on every call. */
        void failWith(int status, String body) {
            this.errorStatus = status;
            this.errorBody = body;
        }

        /** Serve a {@code use_dpop_nonce} challenge carrying {@code nonce} on every call. */
        void challengeWithNonce(String nonce) {
            this.challengeNonce = nonce;
        }

        /** Serve {@code body} as a 200 success response on every call. */
        void respondWith(String body) {
            this.successBody = body;
        }

        /** @return how many times the endpoint was called, i.e. the attempt count including retries */
        int getCallCounter() {
            return callCounter.get();
        }

        /**
         * Serve the success and error bodies with exactly these {@code Content-Type} lines; no value
         * serves no {@code Content-Type} at all.
         */
        void withContentTypes(String... values) {
            this.contentTypes = List.of(values);
        }

        private Headers contentTypeHeaders() {
            Headers.Builder builder = new Headers.Builder();
            contentTypes.forEach(value -> builder.add("Content-Type", value));
            return builder.build();
        }

        void reset() {
            this.challengeNonce = null;
            this.successBody = null;
            this.errorBody = null;
            this.errorStatus = HTTP_BAD_REQUEST;
            this.contentTypes = DEFAULT_CONTENT_TYPES;
            this.callCounter.set(0);
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
            callCounter.incrementAndGet();
            String error = this.errorBody;
            if (error != null) {
                return Optional.of(new MockResponse(errorStatus, contentTypeHeaders(), error));
            }
            String nonce = this.challengeNonce;
            if (nonce == null) {
                String body = this.successBody;
                return Optional.of(new MockResponse(200, contentTypeHeaders(),
                        body == null ? MALFORMED_BODY : body));
            }
            return Optional.of(new MockResponse(400,
                    Headers.of("Content-Type", "application/json", "DPoP-Nonce", nonce),
                    NONCE_CHALLENGE_BODY));
        }
    }
}
