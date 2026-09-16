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
import de.cuioss.sheriff.token.client.flow.RefreshFailureClassification.Kind;
import de.cuioss.sheriff.token.client.token.RefreshTokenFamily;
import de.cuioss.sheriff.token.client.token.RefreshTokenFamilyRevokedException;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The classification in two tiers.
 * <p>
 * The nested groups pin it as a <em>type contract</em>: which kind carries a redemption, which two do
 * not, and that neither disagreeing pairing is constructible through any entry point. The rejected
 * pairings are the load-bearing part. A {@link Kind#REDEEMED} instance with no redemption would read
 * downstream as "nothing is known about the presented token", which is the session-preserving
 * disposition — so a value that lost its state would silently become the exact inversion the
 * classification exists to prevent. A redemption attached to a kind that redeemed nothing is the mirror
 * defect: a fabricated successor for an exchange the authorization server never processed.
 * <p>
 * The top-level tests pin the <em>wired</em> half: what {@link RefreshFlow#classify(Throwable)} answers
 * for a failure the real {@link TokenEndpointClient} actually raised against a live token endpoint. The
 * type contract cannot see that half, because it says nothing about which HTTP answer produces which
 * kind. Three boundaries matter and none of them is observable from a hand-constructed exception: the
 * one-code allow-list is gated on the {@code 4xx} family, so the same {@code invalid_grant} body on a
 * {@code 503} must stay pre-redemption; a failure raised before any response exists — an unreachable
 * port, or a target the egress control refuses — can never be a rejected credential, because the code
 * that would classify it is downstream of a status check that never runs; and an RFC 9449 §8
 * {@code use_dpop_nonce} challenge is a recoverable protocol step, so the classification must describe
 * the answer to the <em>retry</em>, not the challenge that provoked it.
 */
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("Refresh failure classification: the type contract and what the wired endpoint produces")
class RefreshFailureClassificationTest {

    /** The issuer the assembled configuration is bound to. */
    private static final String ISSUER = "https://issuer.example.com";

    /** A parseable, complete token endpoint success body — enough for the transport to normalize. */
    private static final String SUCCESS_BODY =
            "{\"access_token\":\"a\",\"token_type\":\"Bearer\",\"expires_in\":300}";

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
    private final ClassifyingTokenEndpointDispatcher moduleDispatcher = new ClassifyingTokenEndpointDispatcher();

    @BeforeEach
    void resetDispatcher() {
        moduleDispatcher.reset();
    }

    @Nested
    @DisplayName("factories")
    class Factories {

        @Test
        @DisplayName("Should build a pre-redemption classification carrying no redemption")
        void shouldBuildPreRedemption() {
            var classification = RefreshFailureClassification.preRedemption();

            assertAll("nothing was redeemed, so no state is carried",
                    () -> assertEquals(Kind.PRE_REDEMPTION, classification.kind()),
                    () -> assertNull(classification.redemption()));
        }

        @Test
        @DisplayName("Should build a credential-rejected classification carrying no redemption")
        void shouldBuildCredentialRejected() {
            var classification = RefreshFailureClassification.credentialRejected();

            assertAll("the server refused the credential without redeeming it",
                    () -> assertEquals(Kind.CREDENTIAL_REJECTED, classification.kind()),
                    () -> assertNull(classification.redemption(),
                            "there is no successor because nothing was rotated"));
        }

        @Test
        @DisplayName("Should build a redeemed classification carrying the redemption it was raised under")
        void shouldBuildRedeemed() {
            RefreshRedemption redemption =
                    RefreshRedemption.rotated(Generators.letterStrings(20, 40).next());

            var classification = RefreshFailureClassification.redeemed(redemption);

            assertAll("the redemption travels with the classification",
                    () -> assertEquals(Kind.REDEEMED, classification.kind()),
                    () -> assertSame(redemption, classification.redemption()));
        }

        @Test
        @DisplayName("Should reject a null redemption on the redeemed factory")
        void shouldRejectNullRedemptionOnFactory() {
            assertThrows(NullPointerException.class, () -> RefreshFailureClassification.redeemed(null));
        }
    }

    @Nested
    @DisplayName("canonical constructor")
    class CanonicalConstructor {

        @Test
        @DisplayName("Should reject a null kind rather than construct an unclassifiable failure")
        void shouldRejectNullKind() {
            assertThrows(NullPointerException.class,
                    () -> new RefreshFailureClassification(null, null));
        }

        @Test
        @DisplayName("Should reject REDEEMED without a redemption, which would read as pre-redemption")
        void shouldRejectRedeemedWithoutRedemption() {
            var rejected = assertThrows(IllegalArgumentException.class,
                    () -> new RefreshFailureClassification(Kind.REDEEMED, null));

            assertTrue(rejected.getMessage().contains("REDEEMED"),
                    "the refusal must name the kind it applies to, was: " + rejected.getMessage());
        }

        @ParameterizedTest(name = "kind={0}")
        @EnumSource(value = Kind.class, names = {"PRE_REDEMPTION", "CREDENTIAL_REJECTED"})
        @DisplayName("Should reject a redemption on a kind that redeemed nothing")
        void shouldRejectRedemptionOnUnredeemedKind(Kind kind) {
            RefreshRedemption redemption = RefreshRedemption.notRotated();

            var rejected = assertThrows(IllegalArgumentException.class,
                    () -> new RefreshFailureClassification(kind, redemption));

            assertTrue(rejected.getMessage().contains(kind.name()),
                    "the refusal must name the offending kind, was: " + rejected.getMessage());
        }

        @Test
        @DisplayName("Should accept the rotation-unknown redemption on REDEEMED, the fail-closed state")
        void shouldAcceptRotationUnknownOnRedeemed() {
            var classification =
                    new RefreshFailureClassification(Kind.REDEEMED, RefreshRedemption.rotationUnknown());

            assertAll("an unparseable success is redeemed with no successor to revoke",
                    () -> assertEquals(Kind.REDEEMED, classification.kind()),
                    () -> assertTrue(classification.redemption().presentedTokenBurned()),
                    () -> assertNull(classification.redemption().rotatedRefreshToken()));
        }
    }

    @Nested
    @DisplayName("refresh-token family refusal")
    class FamilyRefusal {

        @Test
        @DisplayName("Should classify a reuse refusal from rotate as REDEEMED, naming the successor to revoke")
        void shouldClassifyReuseOnRotateAsRedeemed() {
            String initial = Generators.letterStrings(20, 40).next();
            String replaySuccessor = Generators.letterStrings(20, 40).next();
            var family = new RefreshTokenFamily(initial);
            family.rotate(initial, Generators.letterStrings(20, 40).next());
            var reuse = assertThrows(RefreshTokenFamilyRevokedException.class,
                    () -> family.rotate(initial, replaySuccessor));

            var classification = RefreshFlow.classify(reuse);

            assertRedeemedWithSuccessor(classification, replaySuccessor);
        }

        @Test
        @DisplayName("Should classify a rotate refusal on an already-revoked family as REDEEMED with the successor")
        void shouldClassifyRotateOnRevokedFamilyAsRedeemed() {
            String initial = Generators.letterStrings(20, 40).next();
            String next = Generators.letterStrings(20, 40).next();
            String laterSuccessor = Generators.letterStrings(20, 40).next();
            var family = new RefreshTokenFamily(initial);
            family.rotate(initial, next);
            assertThrows(RefreshTokenFamilyRevokedException.class,
                    () -> family.rotate(initial, Generators.letterStrings(20, 40).next()));
            var revoked = assertThrows(RefreshTokenFamilyRevokedException.class,
                    () -> family.rotate(next, laterSuccessor));

            var classification = RefreshFlow.classify(revoked);

            assertRedeemedWithSuccessor(classification, laterSuccessor);
        }

        @Test
        @DisplayName("Should classify a currentToken refusal on a revoked family as CREDENTIAL_REJECTED")
        void shouldClassifyCurrentTokenOnRevokedFamilyAsCredentialRejected() {
            String initial = Generators.letterStrings(20, 40).next();
            var family = new RefreshTokenFamily(initial);
            family.rotate(initial, Generators.letterStrings(20, 40).next());
            assertThrows(RefreshTokenFamilyRevokedException.class,
                    () -> family.rotate(initial, Generators.letterStrings(20, 40).next()));
            var revoked = assertThrows(RefreshTokenFamilyRevokedException.class, family::currentToken);

            var classification = RefreshFlow.classify(revoked);

            assertAll("nothing was redeemed, but the session is over",
                    () -> assertEquals(Kind.CREDENTIAL_REJECTED, classification.kind()),
                    () -> assertNull(classification.redemption()));
        }

        @Test
        @DisplayName("Should classify a rotation refusal that lost its successor as REDEEMED with rotation unknown")
        void shouldClassifyRotationRefusalWithoutSuccessorAsRotationUnknown() throws Exception {
            var original = new RefreshTokenFamilyRevokedException("refresh token family is revoked",
                    Generators.letterStrings(20, 40).next());
            var roundTripped = (RefreshTokenFamilyRevokedException) roundTrip(original);

            var classification = RefreshFlow.classify(roundTripped);

            assertAll("still post-redemption and fail-closed, with no token to name",
                    () -> assertEquals(Kind.REDEEMED, classification.kind()),
                    () -> assertTrue(classification.redemption().presentedTokenBurned()),
                    () -> assertNull(classification.redemption().rotatedRefreshToken()));
        }

        private static void assertRedeemedWithSuccessor(RefreshFailureClassification classification,
                String successor) {
            assertAll("the server already rotated, so the session ends and the successor is revoked",
                    () -> assertEquals(Kind.REDEEMED, classification.kind(),
                            "a family refusal must never read as the session-preserving PRE_REDEMPTION"),
                    () -> assertTrue(classification.redemption().presentedTokenBurned()),
                    () -> assertEquals(successor, classification.redemption().rotatedRefreshToken()));
        }

        private static Object roundTrip(Object value) throws IOException, ClassNotFoundException {
            var bytes = new ByteArrayOutputStream();
            try (var out = new ObjectOutputStream(bytes)) {
                out.writeObject(value);
            }
            try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                return in.readObject();
            }
        }
    }

    @Test
    @DisplayName("Should not render live token material through the record's own toString")
    void shouldNotRenderTheSuccessor() {
        String successor = Generators.letterStrings(20, 40).next();

        String rendered = RefreshFailureClassification
                .redeemed(RefreshRedemption.rotated(successor)).toString();

        assertAll("the successor is a usable credential and must not reach a log or a debugger dump",
                () -> assertTrue(rendered.contains("REDEEMED")),
                () -> assertTrue(rendered.contains("<redacted>")),
                () -> assertEquals(-1, rendered.indexOf(successor),
                        "the rotated refresh token must not appear in the rendering"));
    }

    @ParameterizedTest(name = "HTTP {0}, body={1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            // The single shape the allow-list recognizes: a client error the AS attributed to the
            // presented credential itself.
            "400 | {\"error\":\"invalid_grant\"}          | CREDENTIAL_REJECTED",
            // The same body on a server error. A 5xx is the AS reporting its OWN failure, so the error
            // code it happens to carry is not evidence about the credential — this row is what proves
            // the 4xx gate is there at all.
            "503 | {\"error\":\"invalid_grant\"}          | PRE_REDEMPTION",
            // Recognized RFC 6749 §5.2 codes outside the one-code allow-list, attributed to the request,
            // the client registration, the grant type, and a missing DPoP nonce respectively.
            "400 | {\"error\":\"invalid_request\"}        | PRE_REDEMPTION",
            "400 | {\"error\":\"invalid_client\"}         | PRE_REDEMPTION",
            "400 | {\"error\":\"unsupported_grant_type\"} | PRE_REDEMPTION",
            "400 | {\"error\":\"use_dpop_nonce\"}         | PRE_REDEMPTION",
            // No usable error code at all. An unparseable body, an absent body and a well-formed body
            // with no error member are each an "unknown", and unknown must never be read as a match.
            "400 | { not json                            | PRE_REDEMPTION",
            "400 |                                       | PRE_REDEMPTION",
            "400 | {\"error_description\":\"denied\"}     | PRE_REDEMPTION"})
    @DisplayName("Should classify the answer the authorization server actually produced")
    void shouldClassifyTheServersOwnAnswer(int status, String body, Kind expected, URIBuilder uriBuilder) {
        moduleDispatcher.respondWith(status, body == null ? "" : body);

        RefreshFailureClassification classification = classifyRefusalFrom(tokenEndpoint(uriBuilder), null);

        assertAll("the status family and the error code decide together, never either alone",
                () -> assertEquals(expected, classification.kind()),
                () -> assertNull(classification.redemption(),
                        "the server never answered a success status, so nothing was redeemed"),
                () -> assertEquals(1, moduleDispatcher.getCallCount(),
                        "a bearer exchange makes exactly one attempt"));
    }

    @Test
    @DisplayName("Should classify an unreachable port as pre-redemption, leaving the presented token in use")
    void shouldClassifyAnUnreachablePortAsPreRedemption() throws Exception {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            closedPort = probe.getLocalPort();
        }

        RefreshFailureClassification classification =
                classifyRefusalFrom("http://localhost:" + closedPort + "/token", null);

        assertAll("no response ever existed, so the credential cannot have been refused",
                () -> assertEquals(Kind.PRE_REDEMPTION, classification.kind(),
                        "a connection failure must never be reported as a dead credential"),
                () -> assertNull(classification.redemption()),
                () -> assertEquals(0, moduleDispatcher.getCallCount(),
                        "the request never reached a token endpoint"));
    }

    @Test
    @DisplayName("Should classify an egress-refused target as pre-redemption, refusing before any request")
    void shouldClassifyAnEgressRefusedTargetAsPreRedemption(URIBuilder uriBuilder) {
        // A traversal in the advertised path is refused by the egress pipeline inside validatedHandler,
        // which runs before the handler exists and therefore before anything is sent. The target host is
        // deliberately the live mock server: BackChannelHttp refuses on path structure and scheme, never
        // on host reachability, so pointing this case at an unreachable host would prove nothing about
        // the egress control and would silently degrade into the connection-failure case above.
        String refusedEndpoint = tokenEndpoint(uriBuilder) + "/../../etc/passwd";

        var client = new TokenEndpointClient(config());
        Map<String, String> form = refreshForm();
        Map<String, String> headers = Map.of();
        TransportException thrown = assertThrows(TransportException.class,
                () -> client.requestToken(refusedEndpoint, form, headers, null));

        assertAll("a target the egress control refused was never presented to the server",
                () -> assertEquals(Kind.PRE_REDEMPTION, RefreshFlow.classify(thrown).kind()),
                () -> assertTrue(thrown.getMessage().contains("egress security validation"),
                        "the refusal must name the control that raised it, was: " + thrown.getMessage()),
                () -> assertEquals(0, moduleDispatcher.getCallCount(),
                        "the egress refusal must precede the request, not follow the response"));
    }

    @Test
    @DisplayName("Should answer a use_dpop_nonce challenge with one retry and classify that retry's answer")
    void shouldClassifyTheRetriedAnswerAfterANonceChallenge(URIBuilder uriBuilder) {
        moduleDispatcher.challengeWithNonceOnce(Generators.letterStrings(16, 32).next());
        moduleDispatcher.respondWith(400, "{\"error\":\"invalid_grant\"}");

        RefreshFailureClassification classification =
                classifyRefusalFrom(tokenEndpoint(uriBuilder), dpopConstraint());

        assertAll("the challenge is a protocol step, so the retry's answer is what gets classified",
                () -> assertEquals(2, moduleDispatcher.getCallCount(),
                        "the challenge must provoke exactly one retry"),
                () -> assertEquals(Kind.CREDENTIAL_REJECTED, classification.kind(),
                        "a client that classified the challenge itself would answer PRE_REDEMPTION here"));
    }

    @Test
    @DisplayName("Should raise no classifiable failure when the retry after a nonce challenge succeeds")
    void shouldRaiseNoFailureWhenTheNonceRetrySucceeds(URIBuilder uriBuilder) {
        moduleDispatcher.challengeWithNonceOnce(Generators.letterStrings(16, 32).next());
        moduleDispatcher.respondWith(200, SUCCESS_BODY);
        var client = new TokenEndpointClient(config());

        var response = assertDoesNotThrow(() -> client.requestToken(
                tokenEndpoint(uriBuilder), refreshForm(), Map.of(), dpopConstraint()));

        assertAll("a recoverable challenge must not surface as a failure at all",
                () -> assertEquals("a", response.accessToken),
                () -> assertEquals(2, moduleDispatcher.getCallCount()));
    }

    /**
     * Drives the real transport against {@code endpoint} and classifies the failure it raised, so each
     * wired case asserts only on the kind the exchange produced.
     */
    private RefreshFailureClassification classifyRefusalFrom(String endpoint, SenderConstraint constraint) {
        var client = new TokenEndpointClient(config());
        Map<String, String> form = refreshForm();

        TransportException thrown = assertThrows(TransportException.class,
                () -> client.requestToken(endpoint, form, Map.of(), constraint),
                "the exchange must be refused, otherwise there is nothing to classify");

        return RefreshFlow.classify(thrown);
    }

    private static Map<String, String> refreshForm() {
        return Map.of("grant_type", "refresh_token",
                "refresh_token", Generators.letterStrings(20, 40).next());
    }

    private static String tokenEndpoint(URIBuilder uriBuilder) {
        return uriBuilder.addPathSegment("token").buildAsString();
    }

    private static SenderConstraint dpopConstraint() {
        return SenderConstraint.dpop(new DpopProofGenerator(RSA_KEY_PAIR, "RS256"));
    }

    private static ClientConfiguration config() {
        return ClientConfiguration.builder()
                .issuer(ISSUER)
                .clientId(Generators.nonBlankStrings().next())
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .redirectUri("https://rp.example.com/callback")
                .allowInsecureHttp(true)
                .build();
    }

    /**
     * Serves the token endpoint with a caller-chosen status and body, optionally preceded by a one-shot
     * RFC 9449 §8 {@code use_dpop_nonce} challenge, and counts the attempts — so a case can tell a
     * refusal the server produced from one raised before any request was ever sent.
     */
    static final class ClassifyingTokenEndpointDispatcher implements ModuleDispatcherElement {

        private static final int HTTP_BAD_REQUEST = 400;
        private static final String DPOP_NONCE_HEADER = "DPoP-Nonce";
        private static final String NONCE_CHALLENGE_BODY = "{\"error\":\"use_dpop_nonce\"}";

        private final AtomicInteger callCount = new AtomicInteger();

        private volatile int status = HTTP_BAD_REQUEST;
        private volatile String body = "";

        /** The {@code DPoP-Nonce} to challenge the first call with, or {@code null} for no challenge. */
        private volatile String pendingNonceChallenge;

        void reset() {
            this.status = HTTP_BAD_REQUEST;
            this.body = "";
            this.pendingNonceChallenge = null;
            this.callCount.set(0);
        }

        void respondWith(int status, String body) {
            this.status = status;
            this.body = body;
        }

        /**
         * Arms a single {@code use_dpop_nonce} challenge: the first call is refused with HTTP 400 and
         * the given nonce, and every later call serves the configured status and body.
         *
         * @param nonce the {@code DPoP-Nonce} to challenge with
         */
        void challengeWithNonceOnce(String nonce) {
            this.pendingNonceChallenge = nonce;
        }

        /** @return how many times the endpoint was called, i.e. the attempt count including retries */
        int getCallCount() {
            return callCount.get();
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
            callCount.incrementAndGet();
            String challenge = this.pendingNonceChallenge;
            if (challenge != null) {
                this.pendingNonceChallenge = null;
                return Optional.of(new MockResponse(HTTP_BAD_REQUEST,
                        Headers.of("Content-Type", "application/json", DPOP_NONCE_HEADER, challenge),
                        NONCE_CHALLENGE_BODY));
            }
            return Optional.of(new MockResponse(status,
                    Headers.of("Content-Type", "application/json"), body));
        }
    }
}
