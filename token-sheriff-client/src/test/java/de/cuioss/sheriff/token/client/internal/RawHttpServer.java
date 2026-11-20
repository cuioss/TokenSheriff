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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A loopback HTTP server for back-channel tests that writes raw response bytes no HTTP library would produce,
 * such as a {@code Content-Length} that is not a number.
 * <p>
 * The server accepts connections one after another on a single thread. For each one it reads the request
 * head and the request body its {@code Content-Length} announces, counts the request, writes the fixed raw
 * response and closes the connection. A connection the client resets or ends before its request is complete
 * is closed without an answer and is not counted; the server then serves the next connection. The accept
 * loop ends when {@link #close()} is called: it closes the server socket and the connection being served,
 * so a client that leaves an incomplete request open does not keep the thread blocked in its read, then
 * stops the thread and waits for it to end. A connection accepted while {@link #close()} runs is closed
 * unserved.
 */
public final class RawHttpServer implements AutoCloseable {

    /** How long {@link #close()} waits for the server thread to end. */
    private static final long STOP_TIMEOUT_SECONDS = 10;

    private final ServerSocket serverSocket;
    private final ExecutorService serverThread = Executors.newSingleThreadExecutor();
    private final AtomicInteger receivedRequests = new AtomicInteger();
    private final Semaphore acceptedConnections = new Semaphore(0);
    /** Guards {@link #activeConnection} and {@link #closing}. */
    private final Object connectionLock = new Object();
    private Socket activeConnection;
    private boolean closing;

    private RawHttpServer(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    /**
     * Starts a loopback server that answers every request with {@code rawResponse}, sent as US-ASCII bytes.
     *
     * @param rawResponse the complete raw HTTP response, status line, headers and body
     * @return the started server; close it to stop it
     * @throws IOException if the loopback server socket cannot be opened
     */
    public static RawHttpServer answering(String rawResponse) throws IOException {
        RawHttpServer server = new RawHttpServer(
                new ServerSocket(0, 50, InetAddress.getByAddress(new byte[]{127, 0, 0, 1})));
        server.start(rawResponse.getBytes(StandardCharsets.US_ASCII));
        return server;
    }

    /** @return the URL of the {@code /endpoint} path on this server */
    public String url() {
        return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/endpoint";
    }

    /** @return the number of requests the server has read completely */
    public int receivedRequests() {
        return receivedRequests.get();
    }

    /** @return {@code true} once the server socket is closed and the server thread has ended */
    boolean isStopped() {
        return serverSocket.isClosed() && serverThread.isTerminated();
    }

    /**
     * Waits until the server has accepted one more connection and registered it as the connection being
     * served, so a test can act on a connection the server is known to hold.
     *
     * @param timeout how long to wait
     * @param unit    the unit of {@code timeout}
     * @return {@code true} if a connection was accepted within the wait, {@code false} otherwise
     * @throws InterruptedException if the wait is interrupted
     */
    boolean awaitAcceptedConnection(long timeout, TimeUnit unit) throws InterruptedException {
        return acceptedConnections.tryAcquire(timeout, unit);
    }

    /**
     * Closes the server socket and the connection being served, stops the server thread and waits for it
     * to end. Closing the connection being served is what ends a read a client left blocked on an incomplete
     * request: {@link ExecutorService#shutdownNow()} does not interrupt a socket read. Calling this method
     * again has no further effect.
     *
     * @throws IOException           if closing the server socket or the connection being served fails
     * @throws IllegalStateException if the server thread did not end within the wait, or the wait was
     *                               interrupted
     */
    @Override
    public void close() throws IOException {
        synchronized (connectionLock) {
            closing = true;
            serverSocket.close();
            if (activeConnection != null) {
                activeConnection.close();
            }
        }
        serverThread.shutdownNow();
        try {
            if (!serverThread.awaitTermination(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The raw HTTP server thread did not stop within "
                        + STOP_TIMEOUT_SECONDS + " seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the raw HTTP server thread to stop", e);
        }
    }

    private void start(byte[] answer) {
        serverThread.execute(() -> {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    if (!register(socket)) {
                        return;
                    }
                    try {
                        serve(socket, answer);
                    } finally {
                        unregister();
                    }
                } catch (SocketException connectionEnded) {
                    // A closed server socket ends the loop; a reset client connection ends that connection only.
                    if (serverSocket.isClosed()) {
                        return;
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    /**
     * Registers {@code socket} as the connection being served, under the same lock {@link #close()} takes,
     * so a connection accepted while the server closes is either closed by {@link #close()} or refused here.
     *
     * @return {@code true} if the connection is registered, {@code false} if the server is closing and the
     *         connection is to be closed unserved
     */
    private boolean register(Socket socket) {
        synchronized (connectionLock) {
            if (closing) {
                return false;
            }
            activeConnection = socket;
        }
        acceptedConnections.release();
        return true;
    }

    private void unregister() {
        synchronized (connectionLock) {
            activeConnection = null;
        }
    }

    private void serve(Socket socket, byte[] answer) throws IOException {
        if (readRequest(socket.getInputStream())) {
            receivedRequests.incrementAndGet();
            OutputStream out = socket.getOutputStream();
            out.write(answer);
            out.flush();
        }
    }

    /**
     * Reads the request head and the request body its Content-Length announces.
     *
     * @return {@code true} if the whole request was read, {@code false} if the connection ended before it
     */
    private static boolean readRequest(InputStream raw) throws IOException {
        InputStream in = new BufferedInputStream(raw);
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int bodyLength = 0;
        int previous = -1;
        int current;
        boolean headComplete = false;
        while ((current = in.read()) != -1) {
            if (previous == '\r' && current == '\n') {
                String header = line.toString(StandardCharsets.US_ASCII).trim();
                if (header.isEmpty()) {
                    headComplete = true;
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
        return headComplete && in.readNBytes(bodyLength).length == bodyLength;
    }
}
