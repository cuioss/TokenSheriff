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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the loopback fixture {@link RawHttpServer}: it keeps serving after a client resets its connection
 * before the request is complete, answers and counts a complete request, and on {@link RawHttpServer#close()}
 * closes its socket and ends its thread, also while a client leaves an incomplete request open.
 * <p>
 * The requests are written over a plain {@link Socket} with a read timeout, so a server that stopped serving
 * fails the test with a timeout instead of hanging it.
 */
@DisplayName("RawHttpServer")
class RawHttpServerTest {

    private static final String BODY = "{\"ok\":true}";
    private static final String RESPONSE = "HTTP/1.1 200 OK\r\n"
            + "Content-Type: application/json\r\n"
            + "Content-Length: " + BODY.length() + "\r\n"
            + "Connection: close\r\n"
            + "\r\n"
            + BODY;
    private static final String REQUEST = """
            POST /endpoint HTTP/1.1\r
            Host: 127.0.0.1\r
            Content-Length: 4\r
            \r
            a=bc""";
    private static final int READ_TIMEOUT_MILLIS = 10_000;

    private static int port(RawHttpServer server) {
        return URI.create(server.url()).getPort();
    }

    private static Socket connect(RawHttpServer server) throws IOException {
        return new Socket(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), port(server));
    }

    /** Sends one complete request over a fresh connection and returns the whole response. */
    private static String send(RawHttpServer server) throws IOException {
        try (Socket socket = connect(server)) {
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            OutputStream out = socket.getOutputStream();
            out.write(REQUEST.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.US_ASCII);
        }
    }

    @Test
    @DisplayName("positive control: a complete request is answered and counted")
    void shouldAnswerAndCountCompleteRequest() throws Exception {
        try (RawHttpServer server = RawHttpServer.answering(RESPONSE)) {
            String response = send(server);

            assertAll("Complete request",
                    () -> assertEquals(RESPONSE, response, "The fixed raw response is written"),
                    () -> assertEquals(1, server.receivedRequests(), "The request is counted once"));
        }
    }

    @Test
    @DisplayName("should serve the next request after a client resets its connection during the request")
    void shouldServeNextRequestAfterClientReset() throws Exception {
        try (RawHttpServer server = RawHttpServer.answering(RESPONSE)) {
            try (Socket resetting = connect(server)) {
                OutputStream out = resetting.getOutputStream();
                out.write("POST /endpoint HTTP/1.1\r\nHost: 127.0.0.1\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                // SO_LINGER 0 makes close() send a reset instead of an orderly end of stream.
                resetting.setSoLinger(true, 0);
            }

            String response = send(server);

            assertAll("After a client reset",
                    () -> assertEquals(RESPONSE, response, "The next request is answered"),
                    () -> assertEquals(1, server.receivedRequests(),
                            "Only the complete request is counted, the reset one is not"));
        }
    }

    @Test
    @DisplayName("should close its socket and end its thread on close")
    void shouldCloseSocketAndEndThreadOnClose() throws Exception {
        try (RawHttpServer server = RawHttpServer.answering(RESPONSE)) {
            send(server);

            server.close();

            assertAll("Closed server",
                    () -> assertTrue(server.isStopped(), "The socket is closed and the server thread has ended"),
                    () -> assertThrows(ConnectException.class, () -> connect(server).close(),
                            "Nothing accepts connections on the closed server's port"));
        }
    }

    @Test
    @DisplayName("should end its thread on close while a client leaves an incomplete request open")
    void shouldEndThreadOnCloseWhileClientLeavesRequestIncomplete() throws Exception {
        try (RawHttpServer server = RawHttpServer.answering(RESPONSE);
             Socket stalling = connect(server)) {
            OutputStream out = stalling.getOutputStream();
            // The head announces a four-byte body that is never sent, and the connection stays open.
            out.write("POST /endpoint HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 4\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            assertTrue(server.awaitAcceptedConnection(READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                    "The server accepts the stalling connection");

            assertDoesNotThrow(server::close, "close() ends the server despite the open incomplete request");

            assertAll("Closed server with a stalled client",
                    () -> assertTrue(server.isStopped(), "The socket is closed and the server thread has ended"),
                    () -> assertEquals(0, server.receivedRequests(), "The incomplete request is not counted"));
        }
    }
}
