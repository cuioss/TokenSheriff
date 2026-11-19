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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A loopback HTTP server for back-channel tests that writes raw response bytes no HTTP library would produce,
 * such as a {@code Content-Length} that is not a number.
 * <p>
 * The server accepts connections one after another on a single thread. For each one it reads the request
 * head and the request body its {@code Content-Length} announces, counts the request, writes the fixed raw
 * response and closes the connection. {@link #close()} closes the socket and stops the thread.
 */
final class RawHttpServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final ExecutorService serverThread = Executors.newSingleThreadExecutor();
    private final AtomicInteger receivedRequests = new AtomicInteger();

    private RawHttpServer(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    /**
     * Starts a loopback server that answers every request with {@code rawResponse}, sent as US-ASCII bytes.
     */
    static RawHttpServer answering(String rawResponse) throws IOException {
        RawHttpServer server = new RawHttpServer(
                new ServerSocket(0, 50, InetAddress.getByAddress(new byte[]{127, 0, 0, 1})));
        server.start(rawResponse.getBytes(StandardCharsets.US_ASCII));
        return server;
    }

    /** The URL of the {@code /endpoint} path on this server. */
    String url() {
        return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/endpoint";
    }

    /** The number of requests the server has read completely. */
    int receivedRequests() {
        return receivedRequests.get();
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        serverThread.shutdownNow();
    }

    private void start(byte[] answer) {
        serverThread.execute(() -> {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    readRequest(socket.getInputStream());
                    receivedRequests.incrementAndGet();
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
}
