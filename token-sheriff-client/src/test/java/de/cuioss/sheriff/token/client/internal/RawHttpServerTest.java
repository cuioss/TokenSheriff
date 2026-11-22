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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the loopback fixture {@link RawHttpServer}: it keeps serving after a client resets its connection
 * before the request is complete, answers and counts a complete request, and on {@link RawHttpServer#close()}
 * closes its socket and ends its thread, also while a client leaves an incomplete request open. A close that
 * fails, with an {@link IOException} or unchecked, still closes the served connection, ends the thread and
 * rethrows the first failure with any later one suppressed. A persistent accept failure ends the thread without
 * {@code close()} after a bounded number of consecutive attempts, which the server records; a successful accept
 * resets that count, and a client reset while a connection is served is not counted as an accept failure.
 * Those cases run on injected server sockets whose {@code close()} or {@code accept()} fails.
 * <p>
 * The requests are written over a plain {@link Socket} with a read timeout, so a server that stopped serving
 * fails the test with a timeout instead of hanging it. A server and a stalling client a test opens on an
 * injected server socket are released by {@link #releaseInjectedServer()} on every path through the test,
 * tolerating only the close failure the test injected.
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
    private static final int PROBE_CONTROL_TIMEOUT_MILLIS = 200;

    private @Nullable RawHttpServer injectedServer;
    private @Nullable Exception toleratedCloseFailure;
    private @Nullable Socket stallingClient;

    @AfterEach
    void releaseInjectedServer() throws IOException {
        try {
            if (stallingClient != null) {
                stallingClient.close();
            }
        } finally {
            if (injectedServer != null) {
                closeToleratingInjectedFailure(injectedServer);
            }
        }
    }

    /** Closes {@code server}; the failure the test injected is thrown on every call and is the only one tolerated. */
    private void closeToleratingInjectedFailure(RawHttpServer server) throws IOException {
        try {
            server.close();
        } catch (IOException | IllegalStateException failure) {
            if (failure != toleratedCloseFailure) {
                throw failure;
            }
        }
    }

    /** Starts a server on an injected server socket and registers it for release after the test. */
    private RawHttpServer servingInjected(ServerSocket serverSocket, Exception injectedCloseFailure) {
        toleratedCloseFailure = injectedCloseFailure;
        injectedServer = RawHttpServer.serving(serverSocket, RESPONSE);
        return injectedServer;
    }

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
                    () -> assertFalse(server.endedAtAcceptBound(),
                            "An end through close() is not recorded as an end at the accept-failure bound"),
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

    /**
     * Opens the head of a request whose announced body is never sent, so the server holds the connection, and
     * registers the client for release after the test.
     */
    private Socket stall(RawHttpServer server) throws IOException, InterruptedException {
        Socket stalling = connect(server);
        stallingClient = stalling;
        stalling.setSoTimeout(READ_TIMEOUT_MILLIS);
        OutputStream out = stalling.getOutputStream();
        out.write("POST /endpoint HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 4\r\n\r\n"
                .getBytes(StandardCharsets.US_ASCII));
        out.flush();
        assertTrue(server.awaitAcceptedConnection(READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                "The server accepts the stalling connection");
        return stalling;
    }

    /**
     * Reads on the stalling client. The server closing the connection it served shows as end of stream, or as
     * a reset; a connection that is still open runs into the client's read timeout, and the
     * {@link SocketTimeoutException} fails the test.
     *
     * @return {@code true} if the read saw end of stream or a reset, {@code false} if it read a byte
     */
    private static boolean seesServedConnectionClosed(Socket client) throws IOException {
        try {
            return client.getInputStream().read() == -1;
        } catch (SocketException reset) {
            return true;
        }
    }

    @Test
    @DisplayName("negative control: the close probe on a connection the server still holds runs into the read timeout")
    void probeShouldTimeOutWhileServerHoldsConnection() throws Exception {
        try (RawHttpServer server = RawHttpServer.answering(RESPONSE)) {
            Socket stalling = stall(server);
            stalling.setSoTimeout(PROBE_CONTROL_TIMEOUT_MILLIS);

            assertThrows(SocketTimeoutException.class, () -> seesServedConnectionClosed(stalling),
                    "A connection the server has not closed yields neither end of stream nor a reset");
        }
    }

    @Test
    @DisplayName("should end its thread on close and rethrow the failure when closing the served connection fails")
    void shouldEndThreadAndRethrowWhenConnectionCloseFails() throws Exception {
        IOException connectionFailure = new IOException("closing the served connection failed");
        RawHttpServer server = servingInjected(new FailingServerSocket(null, connectionFailure), connectionFailure);
        Socket stalling = stall(server);

        IOException thrown = assertThrows(IOException.class, server::close);

        assertAll("Failing connection close",
                () -> assertSame(connectionFailure, thrown, "The failure of the connection close is rethrown"),
                () -> assertTrue(seesServedConnectionClosed(stalling),
                        "The served connection was closed: the stalling client reads its end"),
                () -> assertTrue(server.isStopped(), "The socket is closed and the server thread has ended"));
    }

    @Test
    @DisplayName("should rethrow the first close failure with the second attached as suppressed")
    void shouldRethrowFirstCloseFailureWithSecondSuppressed() throws Exception {
        IOException serverSocketFailure = new IOException("closing the server socket failed");
        IOException connectionFailure = new IOException("closing the served connection failed");
        RawHttpServer server = servingInjected(
                new FailingServerSocket(serverSocketFailure, connectionFailure), serverSocketFailure);
        Socket stalling = stall(server);

        IOException thrown = assertThrows(IOException.class, server::close);

        assertAll("Two failing closes",
                () -> assertSame(serverSocketFailure, thrown, "The first failure is rethrown"),
                () -> assertArrayEquals(new Throwable[]{connectionFailure}, thrown.getSuppressed(),
                        "The second failure is attached as suppressed"),
                () -> assertTrue(seesServedConnectionClosed(stalling),
                        "The served connection was closed: the stalling client reads its end"),
                () -> assertTrue(server.isStopped(), "The socket is closed and the server thread has ended"));
    }

    @Test
    @DisplayName("should close the served connection and rethrow when closing the server socket fails unchecked")
    void shouldCloseConnectionAndRethrowWhenServerSocketCloseFailsUnchecked() throws Exception {
        IllegalStateException serverSocketFailure = new IllegalStateException("closing the server socket failed");
        UncheckedClosingServerSocket serverSocket = new UncheckedClosingServerSocket(serverSocketFailure);
        RawHttpServer server = servingInjected(serverSocket, serverSocketFailure);
        Socket stalling = stall(server);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, server::close);

        assertAll("Unchecked server socket close failure",
                () -> assertSame(serverSocketFailure, thrown, "The unchecked failure is rethrown as it is"),
                () -> assertTrue(serverSocket.connectionClosed.get(),
                        "The served connection is closed although closing the server socket failed"),
                () -> assertTrue(seesServedConnectionClosed(stalling),
                        "The served connection was closed: the stalling client reads its end"),
                () -> assertTrue(server.isStopped(), "The socket is closed and the server thread has ended"));
    }

    @Test
    @DisplayName("negative control: consecutive accept failures with no accept between them end the thread at the bound")
    void shouldEndThreadAfterPersistentAcceptFailure() throws Exception {
        ScriptedAcceptServerSocket serverSocket = ScriptedAcceptServerSocket.failingEveryAttempt();
        try (RawHttpServer server = RawHttpServer.serving(serverSocket, RESPONSE)) {
            assertTrue(server.awaitThreadEnded(READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                    "The server thread ends without close()");

            assertAll("Persistent accept failure",
                    () -> assertEquals(RawHttpServer.MAX_CONSECUTIVE_ACCEPT_FAILURES, serverSocket.attempts.get(),
                            "The loop ends after the bound of consecutive accept failures"),
                    () -> assertTrue(server.endedAtAcceptBound(), "The end at the accept-failure bound is recorded"),
                    () -> assertFalse(serverSocket.isClosed(), "The loop ended with the server socket still open"));
        }
    }

    /**
     * The persistent-failure test above is the negative control: without the accept between the failures the
     * loop ends after {@code N} attempts, not {@code 2N}.
     */
    @Test
    @DisplayName("should reset the consecutive accept-failure count on a successful accept")
    void shouldResetAcceptFailureCountOnSuccessfulAccept() throws Exception {
        int bound = RawHttpServer.MAX_CONSECUTIVE_ACCEPT_FAILURES;
        ScriptedAcceptServerSocket serverSocket = new ScriptedAcceptServerSocket(bound, endedConnection());
        try (RawHttpServer server = RawHttpServer.serving(serverSocket, RESPONSE)) {
            assertTrue(server.awaitThreadEnded(READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                    "The server thread ends without close()");

            assertAll("Accept between failures",
                    () -> assertEquals(2 * bound, serverSocket.attempts.get(),
                            "N-1 failures, the accept, then N further failures before the loop ends"),
                    () -> assertTrue(server.endedAtAcceptBound(), "The loop ended at the accept-failure bound"));
        }
    }

    /**
     * The persistent-failure test above is the negative control: an accept failure in place of the reset
     * connection is counted, and the loop ends after {@code N} attempts, not {@code N + 1}.
     */
    @Test
    @DisplayName("should not count a client reset while a connection is served as an accept failure")
    void shouldNotCountClientResetWhileServedAsAcceptFailure() throws Exception {
        int bound = RawHttpServer.MAX_CONSECUTIVE_ACCEPT_FAILURES;
        ScriptedAcceptServerSocket serverSocket = new ScriptedAcceptServerSocket(1, resettingConnection());
        try (RawHttpServer server = RawHttpServer.serving(serverSocket, RESPONSE)) {
            assertTrue(server.awaitThreadEnded(READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                    "The server thread ends without close()");

            assertAll("Reset while served",
                    () -> assertEquals(bound + 1, serverSocket.attempts.get(),
                            "The accept of the resetting connection, then N accept failures before the loop ends"),
                    () -> assertEquals(0, server.receivedRequests(), "The reset connection is not counted as a request"),
                    () -> assertTrue(server.endedAtAcceptBound(), "The loop ended at the accept-failure bound"));
        }
    }

    /** An unconnected connection whose client ended before sending anything: its input is at end of stream. */
    private static Socket endedConnection() {
        return new Socket() {
            @Override
            public InputStream getInputStream() {
                return InputStream.nullInputStream();
            }

            @Override
            public OutputStream getOutputStream() {
                return OutputStream.nullOutputStream();
            }
        };
    }

    /** An unconnected connection whose client resets it while it is served: reading it fails with the reset. */
    private static Socket resettingConnection() {
        return new Socket() {
            @Override
            public InputStream getInputStream() {
                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new SocketException("Connection reset");
                    }
                };
            }
        };
    }

    /**
     * A loopback server socket whose own {@code close()} and whose accepted connections' {@code close()} each
     * close for real and then throw the given failure, when one is given.
     */
    private static final class FailingServerSocket extends ServerSocket {

        private final @Nullable IOException serverSocketFailure;
        private final IOException connectionFailure;

        FailingServerSocket(@Nullable IOException serverSocketFailure, IOException connectionFailure)
                throws IOException {
            super(0, 50, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
            this.serverSocketFailure = serverSocketFailure;
            this.connectionFailure = connectionFailure;
        }

        @Override
        public Socket accept() throws IOException {
            Socket accepted = new Socket() {
                @Override
                public synchronized void close() throws IOException {
                    super.close();
                    throw connectionFailure;
                }
            };
            implAccept(accepted);
            return accepted;
        }

        @Override
        public void close() throws IOException {
            super.close();
            if (serverSocketFailure != null) {
                throw serverSocketFailure;
            }
        }
    }

    /**
     * A loopback server socket whose own {@code close()} closes for real and then throws the given unchecked
     * failure, and which records whether a connection it accepted was closed.
     */
    private static final class UncheckedClosingServerSocket extends ServerSocket {

        private final RuntimeException closeFailure;
        private final AtomicBoolean connectionClosed = new AtomicBoolean();

        UncheckedClosingServerSocket(RuntimeException closeFailure) throws IOException {
            super(0, 50, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
            this.closeFailure = closeFailure;
        }

        @Override
        public Socket accept() throws IOException {
            Socket accepted = new Socket() {
                @Override
                public synchronized void close() throws IOException {
                    super.close();
                    connectionClosed.set(true);
                }
            };
            implAccept(accepted);
            return accepted;
        }

        @Override
        public void close() throws IOException {
            super.close();
            throw closeFailure;
        }
    }

    /**
     * A loopback server socket whose {@code accept()} fails with a {@link SocketException} on every attempt while
     * it stays open, except the attempt numbered {@code acceptedAttempt}, which returns the given connection;
     * the attempts are counted.
     */
    private static final class ScriptedAcceptServerSocket extends ServerSocket {

        private static final int NO_ACCEPT = 0;

        private final int acceptedAttempt;
        private final @Nullable Socket connection;
        private final AtomicInteger attempts = new AtomicInteger();

        ScriptedAcceptServerSocket(int acceptedAttempt, @Nullable Socket connection) throws IOException {
            super(0, 50, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
            this.acceptedAttempt = acceptedAttempt;
            this.connection = connection;
        }

        static ScriptedAcceptServerSocket failingEveryAttempt() throws IOException {
            return new ScriptedAcceptServerSocket(NO_ACCEPT, null);
        }

        @Override
        public Socket accept() throws IOException {
            if (attempts.incrementAndGet() == acceptedAttempt && connection != null) {
                return connection;
            }
            throw new SocketException("accept failed");
        }
    }
}
