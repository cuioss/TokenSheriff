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

import de.cuioss.sheriff.token.client.auth.ClientSecretBasicAuth;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.DiscoveryResolver;
import de.cuioss.sheriff.token.client.internal.RawHttpServer;
import de.cuioss.sheriff.token.client.lifecycle.RevocationClient;
import de.cuioss.sheriff.token.client.token.UserInfoClient;
import de.cuioss.sheriff.token.commons.error.TransportException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shows how each back-channel endpoint client treats an {@link IOException} of the exchange that follows the
 * response status line.
 * <p>
 * The token endpoint client is driven through its package-private constructor with
 * {@link FailingAfterStatusHttpClient}, whose body handler call records the status before the exchange fails
 * with an injected cause: after a success status a checked or runtime cause is a
 * {@link RedeemedResponseException} whose cause is the fixed-text {@link IOException} of the back channel,
 * with no chained cause and no part of the injected message. The positive controls show that the same failure
 * after an error status, or with no status recorded, is a pre-redemption {@link TransportException}.
 * <p>
 * The discovery, PAR, revocation and userinfo clients are built through their public constructors and sent
 * to a {@link RawHttpServer} that answers {@code 200} with a {@code Content-Length} larger than the body it
 * sends before closing the connection. That case is a characterisation of the JDK: the exchange fails with the
 * JDK's own premature-EOF {@link IOException} after the status, which the back channel passes on as it is, and
 * each client reports a {@link TransportException} with its failure context and that {@link IOException}, not
 * the back channel's fixed text, as cause. It does not exercise the fixed-text mapping of the back channel,
 * which the token endpoint cases above and the {@code BackChannelHttp} exchange tests pin. The positive control
 * is the same server answering a complete body, which gives the client's normal result. Every client reaches the loopback server through the configured back-channel origin, so the
 * advertised URLs and the discovery document name a fixed issuer.
 */
@DisplayName("Back-channel client treatment of a failed exchange")
class BackChannelClientExchangeFailureTest {

    /** The fixed text of the back channel's {@link IOException} for a failed exchange. */
    private static final String EXCHANGE_FAILED = "the HTTP exchange failed";

    /** Stands for remote content a failure message could carry; it must not reach the caller. */
    private static final String REMOTE_CONTENT = "remote-content-from-the-response";

    private static final String ISSUER = "http://idp.example.test";

    private static ClientConfiguration.ClientConfigurationBuilder configuration(String issuer) {
        return ClientConfiguration.builder()
                .issuer(issuer)
                .clientId("test-client")
                .clientSecret("test-secret")
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(true)
                .allowLoopbackEgress(true);
    }

    @Nested
    @DisplayName("Token endpoint client after a recorded status")
    class TokenEndpoint {

        private static final String TOKEN_ENDPOINT = "http://127.0.0.1:1/token";
        private static final Map<String, String> FORM = Map.of("grant_type", "client_credentials");

        private TransportException requestFailingWith(@Nullable Integer status, Throwable cause) {
            TokenEndpointClient client = new TokenEndpointClient(configuration(TOKEN_ENDPOINT).build(),
                    handler -> new FailingAfterStatusHttpClient(status, cause));
            return assertThrows(TransportException.class,
                    () -> client.requestToken(TOKEN_ENDPOINT, FORM, Map.of()));
        }

        static Stream<Arguments> causes() {
            return Stream.of(
                    Arguments.of("a checked cause", new TimeoutException(REMOTE_CONTENT)),
                    Arguments.of("a runtime cause", new IllegalStateException(REMOTE_CONTENT)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("causes")
        @DisplayName("should report a failure after HTTP 200 as redeemed, with the fixed text and no injected text")
        void shouldReportFailureAfterSuccessStatusAsRedeemed(String description, Throwable cause) {
            TransportException failure = requestFailingWith(200, cause);

            assertAll(description,
                    () -> assertInstanceOf(RedeemedResponseException.class, failure,
                            "A success status was recorded: the grant was redeemed"),
                    () -> assertEquals("Token endpoint response body could not be read after HTTP 200: "
                            + EXCHANGE_FAILED, failure.getMessage(), "The message ends with the fixed text"),
                    () -> assertInstanceOf(IOException.class, failure.getCause(),
                            "The cause is the back channel's IOException"),
                    () -> assertEquals(EXCHANGE_FAILED, failure.getCause().getMessage(),
                            "The IOException carries the fixed text"),
                    () -> assertNull(failure.getCause().getCause(), "The injected cause is not chained"),
                    () -> assertFalse(failure.getMessage().contains(REMOTE_CONTENT),
                            "No part of the injected message reaches the failure"));
        }

        static Stream<Arguments> preRedemptionStatuses() {
            return Stream.of(
                    Arguments.of("after HTTP 500", 500),
                    Arguments.of("with no status recorded", null));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("preRedemptionStatuses")
        @DisplayName("positive control: the same failure without a success status is a pre-redemption failure")
        void shouldReportFailureWithoutSuccessStatusAsPreRedemption(String description, @Nullable Integer status) {
            TransportException failure = requestFailingWith(status, new TimeoutException(REMOTE_CONTENT));

            assertAll(description,
                    () -> assertFalse(failure instanceof RedeemedResponseException,
                            "No success status was recorded: nothing was redeemed"),
                    () -> assertEquals("Token endpoint request failed: " + EXCHANGE_FAILED, failure.getMessage(),
                            "The pre-redemption failure carries the fixed text"),
                    () -> assertNull(failure.getCause().getCause(), "The injected cause is not chained"));
        }
    }

    @Nested
    @DisplayName("Discovery, PAR, revocation and userinfo clients after HTTP 200")
    class OtherClients {

        private RawHttpServer server;

        @AfterEach
        void tearDown() throws IOException {
            if (server != null) {
                server.close();
            }
        }

        /** Starts the server and returns the configuration that routes the issuer origin to it. */
        private ClientConfiguration serving(String rawResponse) throws IOException {
            server = RawHttpServer.answering(rawResponse);
            URI serverUri = URI.create(server.url());
            return configuration(ISSUER)
                    .backChannelOrigin(serverUri.getScheme() + "://" + serverUri.getRawAuthority())
                    .build();
        }

        private static String answer(String body, int contentLength) {
            return "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + contentLength + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n"
                    + body;
        }

        static Stream<Arguments> clients() {
            return Stream.of(
                    Arguments.of("DiscoveryResolver", ISSUER,
                            "{\"issuer\":\"" + ISSUER + "\",\"token_endpoint\":\"" + ISSUER + "/token\"}",
                            (CallSite) configuration -> new DiscoveryResolver(configuration).resolve()),
                    Arguments.of("ParClient", "PAR request failed: ",
                            "{\"request_uri\":\"urn:ietf:params:oauth:request_uri:abc\",\"expires_in\":60}",
                            (CallSite) configuration -> new ParClient(configuration).pushAuthorizationRequest(
                                    ISSUER + "/par", Map.of("response_type", "code"), basicAuth(configuration))),
                    Arguments.of("RevocationClient", "Revocation request failed: ", "{}",
                            (CallSite) configuration -> {
                                new RevocationClient(configuration).revoke(ISSUER + "/revoke", "token-value",
                                        null, basicAuth(configuration));
                                return Boolean.TRUE;
                            }),
                    Arguments.of("UserInfoClient", "userinfo request failed: ", "{\"sub\":\"user-1\"}",
                            (CallSite) configuration -> new UserInfoClient(configuration).fetchUserInfo(
                                    ISSUER + "/userinfo", "access-token-value")));
        }

        /**
         * Characterises the JDK, not the back channel's mapping: the premature end of the body after HTTP 200
         * is the JDK's own {@link IOException}, which the back channel passes on as it is, so this case does
         * not reach the fixed-text mapping and passes with that mapping reverted.
         */
        @ParameterizedTest(name = "{0}")
        @MethodSource("clients")
        @DisplayName("characterisation: the JDK's own premature-EOF IOException after HTTP 200 reaches each client as cause")
        void shouldPassJdkPrematureEofAfterStatusToClientAsCause(String clientName, String failureContext,
                String completeBody, CallSite callSite) throws Exception {
            // The body announced is longer than the body sent before the server closes the connection.
            ClientConfiguration configuration = serving(answer("{", completeBody.length() + 64));

            TransportException failure = assertThrows(TransportException.class, () -> callSite.call(configuration));

            assertAll(clientName,
                    () -> assertTrue(failure.getMessage().contains(failureContext),
                            "The failure carries the client's failure context: " + failure.getMessage()),
                    () -> assertInstanceOf(IOException.class, failure.getCause(),
                            "The JDK's IOException of the exchange is the cause"),
                    () -> assertNotEquals(EXCHANGE_FAILED, failure.getCause().getMessage(),
                            "The cause is the JDK's own premature-EOF failure, not the back channel's fixed text"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("clients")
        @DisplayName("positive control: the same server answering a complete body gives the client's normal result")
        void shouldReturnNormalResultForCompleteBody(String clientName, String failureContext, String completeBody,
                CallSite callSite) throws Exception {
            ClientConfiguration configuration = serving(answer(completeBody, completeBody.length()));

            Object result = assertDoesNotThrow(() -> callSite.call(configuration), clientName);

            assertAll(clientName,
                    () -> assertNotNull(result, "The client returns its result"),
                    () -> assertEquals(1, server.receivedRequests(), "The server answered one request"));
        }

        private static ClientSecretBasicAuth basicAuth(ClientConfiguration configuration) {
            return new ClientSecretBasicAuth(configuration.getClientId(), configuration.getClientSecret());
        }
    }

    /** One call of an endpoint client configured by {@code configuration}, returning its result. */
    @FunctionalInterface
    interface CallSite {
        Object call(ClientConfiguration configuration);
    }
}
