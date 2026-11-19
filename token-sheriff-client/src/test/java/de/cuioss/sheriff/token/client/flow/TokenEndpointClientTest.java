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
import de.cuioss.sheriff.token.client.internal.BackChannelHttp;
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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transport-level coverage for {@link TokenEndpointClient}: the M8 log-injection defense on the
 * parse-error path, malformed responses, and the boundaries of the RFC 9449 §8 {@code use_dpop_nonce}
 * retry.
 * <p>
 * <strong>M8:</strong> the token endpoint response body is authorization-server-controlled. When it is
 * malformed, DSL-JSON raises a parse error whose message echoes the offending fragment verbatim —
 * including any raw {@code CR}/{@code LF} the AS embedded. {@link TokenEndpointClient} must route that
 * fragment through {@code LogSanitizer.sanitize} (CWE-117) before it reaches the log appender or the
 * {@link TransportException} message, so the AS cannot forge a log line.
 * <p>
 * <strong>Nonce retry:</strong> a {@code use_dpop_nonce} challenge is retried exactly <em>once</em>, and
 * only when the challenge is genuine. The three cases below pin the boundaries the happy-path retry
 * coverage does not reach: a repeated challenge must not loop, a blank {@code DPoP-Nonce} must not be
 * treated as a challenge, and a challenge presented against an mTLS constraint must not be answered
 * with a DPoP proof.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("TokenEndpointClient parse-error sanitization (M8) and use_dpop_nonce retry boundaries")
class TokenEndpointClientTest {

    private static final String MALFORMED_CONTENT_LENGTH = "12raw34remote56";

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
    @DisplayName("a 204 with a Content-Length that is not a number is a pre-redemption malformed-response failure")
    void shouldReportMalformedContentLengthAfterNoContentAsNotRedeemed(URIBuilder uriBuilder) {
        moduleDispatcher.respondWithRawContentLength(204, MALFORMED_CONTENT_LENGTH);

        TransportException thrown = redeemExpectingFailure(uriBuilder, null);

        assertAll("malformed no-body response whose status is never observed",
                () -> assertFalse(thrown instanceof RedeemedResponseException,
                        "the JDK refuses the header of a 204 before any body handler sees the status, so no "
                                + "redemption is observed"),
                () -> assertEquals("Token endpoint request failed: " + BackChannelHttp.MALFORMED_RESPONSE,
                        thrown.getMessage(), "the failure carries the fixed text only, not an IllegalArgumentException"),
                () -> assertFalse(thrown.getMessage().contains(MALFORMED_CONTENT_LENGTH),
                        "the raw header value must not reach the caller"),
                () -> assertNull(thrown.getCause(), "the JDK exception carrying the raw value is not chained"));
    }

    @Test
    @DisplayName("negative control: a 400 with a Content-Length that is not a number is a pre-redemption failure")
    void shouldReportMalformedContentLengthAfterBadRequestAsNotRedeemed(URIBuilder uriBuilder) {
        moduleDispatcher.respondWithRawContentLength(400, MALFORMED_CONTENT_LENGTH);

        TransportException thrown = redeemExpectingFailure(uriBuilder, null);

        assertAll("malformed response after an error status",
                () -> assertFalse(thrown instanceof RedeemedResponseException, "an error status is no redemption"),
                () -> assertEquals("Token endpoint request failed: " + BackChannelHttp.MALFORMED_RESPONSE,
                        thrown.getMessage(), "the failure carries the fixed text only"),
                () -> assertNull(thrown.getCause(), "the JDK exception carrying the raw value is not chained"));
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
}
