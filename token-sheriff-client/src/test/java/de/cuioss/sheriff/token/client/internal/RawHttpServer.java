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
 * loop ends only when the server socket is closed. {@link #close()} closes the socket, stops the thread and
 * waits for it to end.
 */
public final class RawHttpServer implements AutoCloseable {

    /** How long {@link #close()} waits for the server thread to end. */
    private static final long STOP_TIMEOUT_SECONDS = 10;

    private final ServerSocket serverSocket;
    private final ExecutorService serverThread = Executors.newSingleThreadExecutor();
    private final AtomicInteger receivedRequests = new AtomicInteger();

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
     * Closes the server socket, stops the server thread and waits for it to end.
     *
     * @throws IOException           if closing the server socket fails
     * @throws IllegalStateException if the server thread did not end within the wait, or the wait was
     *                               interrupted
     */
    @Override
    public void close() throws IOException {
        serverSocket.close();
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
                    if (readRequest(socket.getInputStream())) {
                        receivedRequests.incrementAndGet();
                        OutputStream out = socket.getOutputStream();
                        out.write(answer);
                        out.flush();
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
