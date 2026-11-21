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
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Observes whether the request timeout of a back-channel request bounds the body read on the running JDK, and
 * pins that the wait for the response headers is bounded by it, as {@link BackChannelHttp#exchange} states.
 * <p>
 * A loopback server sends the status line, the headers and the first part of the body, then holds the rest
 * until the test releases it. A call through {@link BackChannelHttp#send} is then awaited for twice the
 * configured read timeout, and two outcomes are accepted, because the JDKs differ: on JDK 21 and 25 the call is
 * still pending after the bounded wait, so the request timeout did not end it, and once the server is released
 * the whole body arrives; on JDK 27 the call ends within the bounded wait with an {@link IOException} as its
 * cause and nothing broader: an {@link HttpTimeoutException} when the request timeout wins, or the plain
 * {@link IOException} the JDK reports for the body it cut short when it closes the connection at the timeout,
 * and which of the two it is varies from run to run. The fixed-text {@link IOException}s of
 * {@link BackChannelHttp}, which stand for an unrecognised or a cancelled exchange, are not accepted. Any other
 * outcome fails the test. The test publishes which outcome it saw,
 * together with the feature version of the running JDK, as a report entry and in its assertion messages, so a
 * build log names the observed behaviour. The positive control uses the same server holding everything, the
 * headers included: that call ends through the request timeout within the same bounded wait on JDK 21, 25
 * and 27.
 * {@link RawHttpServer} writes its whole answer at once and cannot hold part of it, so the stalling server is
 * local to this class. No test sleeps: the bounded waits are the subject of the test.
 */
@DisplayName("BackChannelHttp body phase")
class BackChannelHttpBodyPhaseTest {

    private static final int READ_TIMEOUT_SECONDS = 1;
    private static final long BOUNDED_WAIT_SECONDS = 2L * READ_TIMEOUT_SECONDS;
    private static final long SIGNAL_WAIT_SECONDS = 30;
    private static final String FIRST_PART = "first-part-of-the-body;";
    private static final String REST = "rest-of-the-body";
    private static final String BODY_PHASE_ENTRY = "body-phase-outcome";

    private final CountDownLatch headSent = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final ExecutorService serverThread = Executors.newSingleThreadExecutor();
    private final ExecutorService callerThread = Executors.newSingleThreadExecutor();
    private ServerSocket serverSocket;

    @AfterEach
    void tearDown() throws Exception {
        release.countDown();
        if (serverSocket != null) {
            serverSocket.close();
        }
        serverThread.shutdownNow();
        callerThread.shutdownNow();
        assertAll("Teardown",
                () -> assertTrue(serverThread.awaitTermination(SIGNAL_WAIT_SECONDS, TimeUnit.SECONDS),
                        "The server thread stops"),
                () -> assertTrue(callerThread.awaitTermination(SIGNAL_WAIT_SECONDS, TimeUnit.SECONDS),
                        "The caller thread stops"));
    }

    @Test
    @DisplayName("should observe whether the request timeout bounds a stalled body read on the running JDK")
    void shouldObserveWhetherRequestTimeoutBoundsBodyRead(TestReporter reporter) throws Exception {
        String jdk = "JDK " + Runtime.version().feature();
        String endpoint = startServer(true);

        Future<HttpResponse<String>> call = callerThread.submit(() -> send(endpoint));
        assertTrue(headSent.await(SIGNAL_WAIT_SECONDS, TimeUnit.SECONDS),
                "The server sent the status line, the headers and the first part of the body");

        try {
            HttpResponse<String> early = call.get(BOUNDED_WAIT_SECONDS, TimeUnit.SECONDS);
            fail(jdk + ": the call returned status " + early.statusCode()
                    + " before the server released the rest of the body");
        } catch (TimeoutException stillPending) {
            reporter.publishEntry(BODY_PHASE_ENTRY, jdk + ": the request timeout does not bound the body read");
            release.countDown();
            HttpResponse<String> response = call.get(SIGNAL_WAIT_SECONDS, TimeUnit.SECONDS);

            assertAll("Released body on " + jdk,
                    () -> assertEquals(200, response.statusCode(), jdk + ": the status line arrived"),
                    () -> assertEquals(FIRST_PART + REST, response.body(),
                            jdk + ": the call was still pending after twice the read timeout, "
                                    + "and the whole body arrives after the release"));
        } catch (ExecutionException ended) {
            reporter.publishEntry(BODY_PHASE_ENTRY, jdk + ": the request timeout bounds the body read");
            Throwable cause = ended.getCause();

            assertNotNull(cause, jdk + ": the call ended within twice the read timeout with a cause");
            assertInstanceOf(IOException.class, cause,
                    jdk + ": the call ended within twice the read timeout, so its cause is the request timeout "
                            + "or the body the JDK cut short at it, and nothing broader, not " + cause);
            assertNotEquals(BackChannelHttp.EXCHANGE_FAILED, cause.getMessage(),
                    jdk + ": the cause is the JDK's own failure, not the fixed text of an unrecognised failure");
            assertNotEquals(BackChannelHttp.EXCHANGE_CANCELLED, cause.getMessage(),
                    jdk + ": the cause is the JDK's own failure, not the fixed text of a cancelled exchange");
            reporter.publishEntry(BODY_PHASE_ENTRY, jdk + ": the call ended with " + cause.getClass().getName());
        }
    }

    @Test
    @DisplayName("positive control: a server that holds the headers ends the call through the request timeout")
    void shouldBoundHeadersWaitByRequestTimeout() throws Exception {
        String endpoint = startServer(false);

        Future<HttpResponse<String>> call = callerThread.submit(() -> send(endpoint));
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> call.get(BOUNDED_WAIT_SECONDS, TimeUnit.SECONDS),
                "The call ends within twice the read timeout");

        assertInstanceOf(HttpTimeoutException.class, failure.getCause(),
                "The wait for the headers ends with the request timeout, not with an answer");
    }

    private static HttpResponse<String> send(String endpoint) throws IOException, InterruptedException {
        BackChannelHttp backChannel = new BackChannelHttp(config(endpoint), 1024,
                BackChannelHttp.FIXED_PARSER_CONFIG_ORIGIN);
        HttpHandler handler = backChannel.validatedHandler(endpoint, "test endpoint request failed");
        HttpClient client = backChannel.sharedClient(handler);
        HttpRequest request = handler.requestBuilder().GET().build();
        return backChannel.send(client, request, "test endpoint request failed");
    }

    private static ClientConfiguration config(String issuer) {
        return ClientConfiguration.builder()
                .issuer(issuer)
                .clientId("test-client")
                .clientSecret("test-secret")
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(true)
                .allowLoopbackEgress(true)
                .readTimeoutSeconds(READ_TIMEOUT_SECONDS)
                .build();
    }

    /**
     * Starts a loopback server that serves one connection: it reads the request head, then, when
     * {@code sendHead} is set, writes the status line, the headers and the first part of the body and signals
     * {@link #headSent}. Either way it then holds the connection until the test counts {@link #release} down
     * or {@link #tearDown()} stops the thread, and never closes it on its own, so a failure the client sees
     * cannot come from the fixture. When {@code sendHead} is set it writes the rest of the body after the
     * release.
     *
     * @return the URL of the {@code /endpoint} path on the server
     */
    private String startServer(boolean sendHead) throws IOException {
        serverSocket = new ServerSocket(0, 50, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
        byte[] head = ("HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/plain\r\n"
                + "Content-Length: " + (FIRST_PART.length() + REST.length()) + "\r\n"
                + "\r\n"
                + FIRST_PART).getBytes(StandardCharsets.US_ASCII);
        byte[] rest = REST.getBytes(StandardCharsets.US_ASCII);
        serverThread.execute(() -> {
            try (Socket socket = serverSocket.accept()) {
                readRequestHead(socket.getInputStream());
                OutputStream out = socket.getOutputStream();
                if (sendHead) {
                    out.write(head);
                    out.flush();
                    headSent.countDown();
                }
                // No bound on this wait: a connection the fixture closed on its own would end the client's
                // body read with the same premature-EOF IOException the probe accepts as the JDK's cut-short body.
                release.await();
                if (sendHead) {
                    out.write(rest);
                    out.flush();
                }
            } catch (IOException connectionEnded) {
                // The client ended the connection, or teardown closed the server socket.
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            }
        });
        return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/endpoint";
    }

    /** Reads up to and including the blank line that ends the request head; a GET carries no body. */
    private static void readRequestHead(InputStream raw) throws IOException {
        InputStream in = new BufferedInputStream(raw);
        int matched = 0;
        int current;
        while (matched < 4 && (current = in.read()) != -1) {
            boolean expected = current == (matched % 2 == 0 ? '\r' : '\n');
            matched = expected ? matched + 1 : (current == '\r' ? 1 : 0);
        }
    }
}
