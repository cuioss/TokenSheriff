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
package de.cuioss.sheriff.token.client.internal;

import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.sheriff.token.client.auth.ClientSecretBasicAuth;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.flow.RedeemedResponseException;
import de.cuioss.sheriff.token.client.flow.TokenEndpointClient;
import de.cuioss.sheriff.token.client.lifecycle.RevocationClient;
import de.cuioss.sheriff.token.commons.error.TransportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ProtocolException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that a response the JDK HTTP client refuses as malformed — a {@code Content-Length} that is not a
 * number — reaches a caller of the back channel only as the fixed {@link BackChannelHttp#MALFORMED_RESPONSE}
 * failure, never as the JDK's {@link IllegalArgumentException} carrying the raw header value.
 * <p>
 * The JDK client parses {@code Content-Length} itself after the body handler returned, so the case is only
 * observable over a real socket: a loopback server writes the raw response bytes, which no HTTP library would
 * produce. The negative control sends the same response through {@link HttpClient#send} directly and shows the
 * JDK exception that carries the raw value.
 */
@DisplayName("BackChannelHttp malformed response framing")
class BackChannelHttpMalformedResponseTest {

    private static final String RAW_VALUE = "12raw34remote56";
    private static final String FAILURE_CONTEXT = "test endpoint request failed";

    private final ExecutorService serverThread = Executors.newSingleThreadExecutor();
    private ServerSocket serverSocket;

    @AfterEach
    void tearDown() throws IOException {
        if (serverSocket != null) {
            serverSocket.close();
        }
        serverThread.shutdownNow();
    }

    private static ClientConfiguration config() {
        return ClientConfiguration.builder()
                .issuer("https://idp.example.com/realms/demo")
                .clientId("test-client")
                .clientSecret("test-secret")
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(true)
                .allowLoopbackEgress(true)
                .build();
    }

    private static String response(int status, String contentLength) {
        return "HTTP/1.1 " + status + " Status\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + contentLength + "\r\n"
                + "Connection: close\r\n"
                + "\r\n"
                + "{}";
    }

    /**
     * Starts a loopback server that answers every request with {@code rawResponse} and returns its URL.
     */
    private String serve(String rawResponse) throws IOException {
        serverSocket = new ServerSocket(0, 50, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
        byte[] answer = rawResponse.getBytes(StandardCharsets.US_ASCII);
        serverThread.execute(() -> {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    readRequest(socket.getInputStream());
                    OutputStream out = socket.getOutputStream();
                    out.write(answer);
                    out.flush();
                } catch (SocketException closed) {
                    return;
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/endpoint";
    }

    /** Reads the request head and the request body its Content-Length announces. */
    private static void readRequest(InputStream raw) throws IOException {
        InputStream in = new BufferedInputStream(raw);
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int bodyLength = 0;
        int previous = -1;
        int current;
        while ((current = in.read()) != -1) {
            if (previous == '\r' && current == '\n') {
                String header = line.toString(StandardCharsets.US_ASCII).trim();
                if (header.isEmpty()) {
                    break;
                }
                if (header.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                    bodyLength = Integer.parseInt(header.substring("content-length:".length()).trim());
                }
                line.reset();
            } else if (current != '\r') {
                line.write(current);
            }
            previous = current;
        }
        in.readNBytes(bodyLength);
    }

    private static HttpRequest get(HttpHandler handler) {
        return handler.requestBuilder().GET().build();
    }

    @Test
    @DisplayName("should map a Content-Length that is not a number to the fixed transport failure")
    void shouldMapMalformedContentLengthToFixedFailure() throws Exception {
        String url = serve(response(200, RAW_VALUE));
        BackChannelHttp backChannel = new BackChannelHttp(config(), 1024, BackChannelHttp.FIXED_PARSER_CONFIG_ORIGIN);
        HttpHandler handler = backChannel.validatedHandler(url, FAILURE_CONTEXT);
        HttpClient client = backChannel.sharedClient(handler);
        HttpRequest request = get(handler);

        TransportException failure = assertThrows(TransportException.class,
                () -> backChannel.send(client, request, FAILURE_CONTEXT));

        assertAll("Fixed failure",
                () -> assertEquals(FAILURE_CONTEXT + ": " + BackChannelHttp.MALFORMED_RESPONSE, failure.getMessage(),
                        "The caller sees the failure context and the fixed text only"),
                () -> assertNull(failure.getCause(), "The JDK exception carrying the raw value is not chained"));
    }

    @Test
    @DisplayName("negative control: the same response sent directly surfaces the JDK exception with the raw value")
    void shouldSurfaceRawValueWhenSentWithoutTheMapping() throws Exception {
        String url = serve(response(200, RAW_VALUE));
        BackChannelHttp backChannel = new BackChannelHttp(config(), 1024, BackChannelHttp.FIXED_PARSER_CONFIG_ORIGIN);
        HttpHandler handler = backChannel.validatedHandler(url, FAILURE_CONTEXT);
        HttpClient client = backChannel.sharedClient(handler);
        HttpRequest request = get(handler);
        HttpResponse.BodyHandler<String> bodyHandler = backChannel.bodyHandler();

        // IllegalArgumentException caused by a NumberFormatException up to JDK 26, ProtocolException chain
        // ending in a ProtocolException caused by a NumberFormatException since JDK 27
        Exception failure = assertThrows(Exception.class, () -> client.send(request, bodyHandler));

        assertTrue(BackChannelHttp.isMalformedFraming(failure), "The JDK refuses it as malformed framing");

        String text = failure.getMessage() + " " + (failure.getCause() == null ? "" : failure.getCause().getMessage());
        assertTrue(text.contains(RAW_VALUE), "Without the mapping the JDK exception carries the raw header value");

        if (Runtime.version().feature() < 27) {
            assertAll("The JDK's own form up to JDK 26",
                    () -> assertEquals(IllegalArgumentException.class, failure.getClass(),
                            "HttpClient.send throws an IllegalArgumentException"),
                    () -> assertInstanceOf(NumberFormatException.class, failure.getCause(),
                            "caused by the NumberFormatException of the Content-Length parser"));
        } else {
            assertAll("The JDK's own form since JDK 27",
                    () -> assertInstanceOf(ProtocolException.class, failure,
                            "HttpClient.send throws a ProtocolException"),
                    () -> assertInstanceOf(ProtocolException.class, failure.getCause(),
                            "which wraps the ProtocolException of the Content-Length parser"),
                    () -> assertInstanceOf(NumberFormatException.class, failure.getCause().getCause(),
                            "caused by the NumberFormatException"));
        }
    }

    @Test
    @DisplayName("should recognise the JDK's refusal of a Content-Length that is not a number, on every JDK")
    void shouldRecogniseMalformedFramingOfEveryJdk() {
        ProtocolException parser = new ProtocolException("invalid \"Content-Length\": " + RAW_VALUE);
        parser.initCause(new NumberFormatException(RAW_VALUE));
        ProtocolException wrapped = new ProtocolException("invalid \"Content-Length\": " + RAW_VALUE);
        wrapped.initCause(parser);
        ProtocolException unrelated = new ProtocolException("other protocol error");
        unrelated.initCause(new IOException("connection reset"));

        assertAll("Malformed framing",
                () -> assertTrue(BackChannelHttp.isMalformedFraming(new NumberFormatException(RAW_VALUE)),
                        "The NumberFormatException the sendAsync future fails with up to JDK 26"),
                () -> assertTrue(BackChannelHttp.isMalformedFraming(
                                new IllegalArgumentException(RAW_VALUE, new NumberFormatException(RAW_VALUE))),
                        "The IllegalArgumentException HttpClient.send throws up to JDK 26"),
                () -> assertFalse(BackChannelHttp.isMalformedFraming(new IllegalArgumentException(RAW_VALUE)),
                        "An IllegalArgumentException without a NumberFormatException is a different failure"),
                () -> assertTrue(BackChannelHttp.isMalformedFraming(parser),
                        "A ProtocolException caused by a NumberFormatException"),
                () -> assertTrue(BackChannelHttp.isMalformedFraming(wrapped),
                        "JDK 27: send's ProtocolException wraps the parser's, which carries the NumberFormatException"),
                () -> assertFalse(BackChannelHttp.isMalformedFraming(unrelated),
                        "A ProtocolException of another cause is a different failure"),
                () -> assertFalse(BackChannelHttp.isMalformedFraming(new IOException("connection reset")),
                        "Other IOExceptions are not malformed framing"),
                () -> assertFalse(BackChannelHttp.isMalformedFraming(null), "Null is not malformed framing"));
    }

    @Test
    @DisplayName("positive control: a valid Content-Length is read unchanged")
    void shouldReadValidResponse() throws Exception {
        String url = serve(response(200, "2"));
        BackChannelHttp backChannel = new BackChannelHttp(config(), 1024, BackChannelHttp.FIXED_PARSER_CONFIG_ORIGIN);
        HttpHandler handler = backChannel.validatedHandler(url, FAILURE_CONTEXT);

        HttpResponse<String> answer = backChannel.send(backChannel.sharedClient(handler), get(handler),
                FAILURE_CONTEXT);

        assertAll("Valid response",
                () -> assertEquals(200, answer.statusCode(), "The status is returned"),
                () -> assertEquals("{}", answer.body(), "The body is returned unchanged"));
    }

    @Test
    @DisplayName("should report the fixed failure through an endpoint client that sends through the back channel")
    void shouldReportFixedFailureThroughRevocationClient() throws Exception {
        String url = serve(response(200, RAW_VALUE));
        ClientConfiguration configuration = config();
        RevocationClient revocationClient = new RevocationClient(configuration);
        ClientSecretBasicAuth auth = new ClientSecretBasicAuth(configuration.getClientId(),
                configuration.getClientSecret());

        TransportException failure = assertThrows(TransportException.class,
                () -> revocationClient.revoke(url, "token-value", null, auth));

        assertAll("Revocation client",
                () -> assertEquals("Revocation request failed: " + BackChannelHttp.MALFORMED_RESPONSE,
                        failure.getMessage(), "The revocation client reports the fixed text"),
                () -> assertNull(failure.getCause(), "The JDK exception is not chained"));
    }

    @Test
    @DisplayName("should report a malformed response after a success status as a post-redemption failure")
    void shouldReportPostRedemptionFailureThroughTokenEndpoint() throws Exception {
        String url = serve(response(200, RAW_VALUE));
        TokenEndpointClient tokenEndpointClient = new TokenEndpointClient(config());
        Map<String, String> form = Map.of("grant_type", "client_credentials");
        Map<String, String> headers = Map.of();

        RedeemedResponseException failure = assertThrows(RedeemedResponseException.class,
                () -> tokenEndpointClient.requestToken(url, form, headers));

        assertAll("Token endpoint after a success status",
                () -> assertEquals("Token endpoint response body could not be read after HTTP 200: "
                        + BackChannelHttp.MALFORMED_RESPONSE, failure.getMessage(),
                        "The token endpoint reports the fixed text as a post-redemption failure"),
                () -> assertNull(failure.getCause(), "The JDK exception is not chained"));
    }

    @Test
    @DisplayName("should report a malformed response after an error status as a pre-redemption failure")
    void shouldReportPreRedemptionFailureThroughTokenEndpoint() throws Exception {
        String url = serve(response(400, RAW_VALUE));
        TokenEndpointClient tokenEndpointClient = new TokenEndpointClient(config());
        Map<String, String> form = Map.of("grant_type", "client_credentials");
        Map<String, String> headers = Map.of();

        TransportException failure = assertThrows(TransportException.class,
                () -> tokenEndpointClient.requestToken(url, form, headers));

        assertAll("Token endpoint after an error status",
                () -> assertFalse(failure instanceof RedeemedResponseException,
                        "An error status is no redemption"),
                () -> assertEquals("Token endpoint request failed: " + BackChannelHttp.MALFORMED_RESPONSE,
                        failure.getMessage(), "The token endpoint reports the fixed text"),
                () -> assertNull(failure.getCause(), "The JDK exception is not chained"));
    }
}
