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
package de.cuioss.sheriff.token.validation.jwks.http;

import de.cuioss.http.client.adapter.RetryConfig;
import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.EgressPolicy;
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.validation.JWTValidationLogMessages;
import de.cuioss.sheriff.token.validation.test.dispatcher.JwksResolveDispatcher;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.TestLoggerFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import lombok.Getter;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link HttpJwksLoader} failure tests around an advertised {@code jwks_uri}: an unusable one, and one
 * that must not reach other loader records. Split out of {@link HttpJwksLoaderFailureTest} by topic.
 */
@EnableTestLogger
@DisplayName("HttpJwksLoader Failure Tests: advertised jwks_uri")
@EnableMockWebServer
class HttpJwksLoaderFailureAdvertisedJwksUriTest {

    /** Distinctive segment carried by advertised jwks_uri fixtures that must never reach a loader record. */
    private static final String ADVERTISED_URI_MARKER = "advertised-uri-marker";

    /** The fixed reason of JWKS_URI_UNUSABLE, pinned verbatim. */
    private static final String UNUSABLE_URI_REASON =
            "the advertised jwks_uri is malformed, uses a scheme other than http or https, "
                    + "or uses cleartext http while insecure HTTP is not allowed";

    /** The fixed detail logged when the egress guard blocks the advertised jwks_uri, pinned verbatim. */
    private static final String ADVERTISED_URI_EGRESS_BLOCKED =
            "SSRF egress blocked: the advertised jwks_uri has no host or resolves to a disallowed address";

    @Getter
    private final JwksResolveDispatcher moduleDispatcher = new JwksResolveDispatcher();

    private SecurityEventCounter securityEventCounter;

    @BeforeEach
    void setUp() {
        moduleDispatcher.setCallCounter(0);
        moduleDispatcher.returnDefault();
        securityEventCounter = new SecurityEventCounter();
    }

    /**
     * Returns every captured record of the {@link HttpJwksLoader} logger, at any level, whose rendered
     * message contains {@code text}.
     */
    private static List<LogRecord> loaderRecordsContaining(String text) {
        return Arrays.stream(TestLogLevel.values())
                .flatMap(level -> TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(level, text).stream())
                .filter(logRecord -> HttpJwksLoader.class.getName().equals(logRecord.getLoggerName()))
                .toList();
    }

    /**
     * Pins that an advertised {@code jwks_uri} no HTTP handler can be built for ends the loader in a
     * terminal {@link LoaderStatus#ERROR}, logged once as {@code JWKS_URI_UNUSABLE}, and is not retried:
     * the refresh task is cancelled or never scheduled, so the discovery endpoint is not contacted again.
     * The absence of retries is observed on the real scheduler over a short refresh interval, not through
     * {@code performBackgroundRefresh()}, which would bypass a cancelled task.
     */
    @Nested
    @DisplayName("Unusable advertised jwks_uri")
    class UnusableAdvertisedJwksUri {

        /** A jwks_uri no HTTP handler can be built for. */
        private static final String MALFORMED_JWKS_URI = "http://[malformed";
        private static final String LOCALHOST = "localhost";
        private static final int SHORT_REFRESH_INTERVAL_SECONDS = 1;
        private static final int TIMEOUT_SECONDS = 5;
        private static final Duration NO_RETRY_WINDOW = Duration.ofMillis(2500);
        private static final Duration NO_RETRY_DEADLINE = Duration.ofSeconds(5);

        /** Certificate of the HTTPS discovery server, issued for the host name the loader contacts. */
        private static final HeldCertificate SERVER_CERTIFICATE = new HeldCertificate.Builder()
                .commonName(LOCALHOST)
                .addSubjectAlternativeName(LOCALHOST)
                .build();

        private final RetryConfig fastRetry = RetryConfig.builder()
                .initialDelay(Duration.ofMillis(1))
                .maxDelay(Duration.ofMillis(5))
                .build();

        private MockWebServer server;

        /**
         * Exposes the enclosing class's dispatcher to the MockWebServer extension, which resolves the
         * module dispatcher on the nested test instance.
         *
         * @return the shared JWKS dispatcher
         */
        public JwksResolveDispatcher getModuleDispatcher() {
            return moduleDispatcher;
        }

        @BeforeEach
        void createServer() {
            server = new MockWebServer();
        }

        @AfterEach
        void stopServer() {
            server.close();
        }

        @Test
        @DisplayName("A cleartext jwks_uri while insecure HTTP is not allowed ends in ERROR and is not retried")
        void cleartextJwksUriWithoutInsecureHttpIsTerminalAndNotRetried() throws Exception {
            server.useHttps(new HandshakeCertificates.Builder()
                    .heldCertificate(SERVER_CERTIFICATE)
                    .build()
                    .sslSocketFactory());
            UnusableJwksUriDispatcher dispatcher = new UnusableJwksUriDispatcher(
                    port -> "http://%s:%d/jwks.json".formatted(LOCALHOST, port));
            startOnLoopback(dispatcher);
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true)
                    .allowInsecureHttp(false)
                    .sslContext(new HandshakeCertificates.Builder()
                            .addTrustedCertificate(SERVER_CERTIFICATE.certificate())
                            .build()
                            .sslContext())
                    .wellKnownUrl("https://%s:%d%s".formatted(LOCALHOST, server.getPort(),
                            UnusableJwksUriDispatcher.WELL_KNOWN_PATH))
                    .connectTimeoutSeconds(TIMEOUT_SECONDS)
                    .readTimeoutSeconds(TIMEOUT_SECONDS)
                    .retryConfig(fastRetry)
                    .refreshIntervalSeconds(SHORT_REFRESH_INTERVAL_SECONDS)
                    .build();

            assertTerminalAtInitializationAndNotRetried(config, dispatcher);
        }

        @Test
        @DisplayName("A malformed jwks_uri ends in ERROR and is not retried")
        void malformedJwksUriIsTerminalAndNotRetried() throws Exception {
            UnusableJwksUriDispatcher dispatcher = new UnusableJwksUriDispatcher(port -> MALFORMED_JWKS_URI);
            startOnLoopback(dispatcher);
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                    .wellKnownUrl(server.url(UnusableJwksUriDispatcher.WELL_KNOWN_PATH).toString())
                    .retryConfig(fastRetry)
                    .refreshIntervalSeconds(SHORT_REFRESH_INTERVAL_SECONDS)
                    .build();

            assertTerminalAtInitializationAndNotRetried(config, dispatcher);
        }

        @Test
        @DisplayName("A refresh cycle that finds an unusable jwks_uri after an unavailable discovery ends in ERROR and cancels the refresh")
        void unusableJwksUriFoundByRefreshIsTerminalAndCancelsTheRefresh() throws Exception {
            UnusableJwksUriDispatcher dispatcher = new UnusableJwksUriDispatcher(port -> MALFORMED_JWKS_URI);
            dispatcher.setAvailable(false);
            startOnLoopback(dispatcher);
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                    .wellKnownUrl(server.url(UnusableJwksUriDispatcher.WELL_KNOWN_PATH).toString())
                    .retryConfig(fastRetry)
                    .refreshIntervalSeconds(SHORT_REFRESH_INTERVAL_SECONDS)
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                LoaderStatus initStatus = loader.initJWKSLoader(securityEventCounter).join();
                assertAll("an unavailable discovery is retried",
                        () -> assertEquals(LoaderStatus.UNDEFINED, initStatus),
                        () -> assertTrue(loader.isBackgroundRefreshActive(), "the background refresh must be scheduled"));
                LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                        JWTValidationLogMessages.WARN.JWKS_URI_UNUSABLE.resolveIdentifierString());

                dispatcher.setAvailable(true);
                Awaitility.await().atMost(Duration.ofSeconds(10))
                        .until(() -> loader.getLoaderStatus() == LoaderStatus.ERROR && unusableUriLogCount() == 1);

                assertAll("the unusable jwks_uri found by the refresh is terminal",
                        () -> assertEquals(LoaderStatus.ERROR, loader.getLoaderStatus()),
                        () -> assertFalse(loader.isBackgroundRefreshActive(), "the refresh task must be cancelled"),
                        () -> assertTrue(loader.getKeyIds().isEmpty(), "no key may be installed"));
                assertNotRetried(dispatcher);
            }
        }

        @Test
        @DisplayName("JWKS_URI_UNUSABLE for a malformed jwks_uri logs the fixed reason, not the advertised URI")
        void malformedJwksUriIsNotRenderedInTheRecord() throws Exception {
            // The handler refusal of this URI quotes it verbatim ("Expected closing bracket ...: http://[...")
            UnusableJwksUriDispatcher dispatcher = new UnusableJwksUriDispatcher(port -> "http://[" + ADVERTISED_URI_MARKER);
            startOnLoopback(dispatcher);
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                    .wellKnownUrl(server.url(UnusableJwksUriDispatcher.WELL_KNOWN_PATH).toString())
                    .retryConfig(fastRetry)
                    .refreshIntervalSeconds(0)
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                assertEquals(LoaderStatus.ERROR, loader.initJWKSLoader(securityEventCounter).join());
                assertUnusableUriLoggedWithFixedReasonOnly();
            }
        }

        @Test
        @DisplayName("JWKS_URI_UNUSABLE for a cleartext jwks_uri logs the fixed reason, not the advertised URI")
        void cleartextJwksUriIsNotRenderedInTheRecord() throws Exception {
            server.useHttps(new HandshakeCertificates.Builder()
                    .heldCertificate(SERVER_CERTIFICATE)
                    .build()
                    .sslSocketFactory());
            // The handler refusal of this URI quotes it verbatim, userinfo and query included
            UnusableJwksUriDispatcher dispatcher = new UnusableJwksUriDispatcher(
                    port -> "http://%s:secret@%s:%d/%s/jwks.json?token=%s".formatted(ADVERTISED_URI_MARKER, LOCALHOST,
                            port, ADVERTISED_URI_MARKER, ADVERTISED_URI_MARKER));
            startOnLoopback(dispatcher);
            HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true)
                    .allowInsecureHttp(false)
                    .sslContext(new HandshakeCertificates.Builder()
                            .addTrustedCertificate(SERVER_CERTIFICATE.certificate())
                            .build()
                            .sslContext())
                    .wellKnownUrl("https://%s:%d%s".formatted(LOCALHOST, server.getPort(),
                            UnusableJwksUriDispatcher.WELL_KNOWN_PATH))
                    .connectTimeoutSeconds(TIMEOUT_SECONDS)
                    .readTimeoutSeconds(TIMEOUT_SECONDS)
                    .retryConfig(fastRetry)
                    .refreshIntervalSeconds(0)
                    .build();

            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                assertEquals(LoaderStatus.ERROR, loader.initJWKSLoader(securityEventCounter).join());
                assertUnusableUriLoggedWithFixedReasonOnly();
            }
        }

        /**
         * Asserts that {@code JWKS_URI_UNUSABLE} was logged once with the fixed reason, and that no record
         * of the loader renders the advertised URI.
         */
        private static void assertUnusableUriLoggedWithFixedReasonOnly() {
            List<LogRecord> records = TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(TestLogLevel.WARN,
                    JWTValidationLogMessages.WARN.JWKS_URI_UNUSABLE.resolveIdentifierString());
            assertEquals(1, records.size(), "JWKS_URI_UNUSABLE is logged exactly once");
            String rendered = records.getFirst().getMessage();
            assertAll("the unusable-URI warning carries the fixed reason only",
                    () -> assertTrue(rendered.endsWith("the loader stops retrying: " + UNUSABLE_URI_REASON),
                            "the reason must be the fixed text: " + rendered),
                    () -> assertFalse(rendered.contains(ADVERTISED_URI_MARKER),
                            "the advertised jwks_uri must not be rendered: " + rendered),
                    () -> assertNull(records.getFirst().getThrown(), "no refusal may be attached"),
                    () -> assertEquals(List.of(), loaderRecordsContaining(ADVERTISED_URI_MARKER).stream()
                            .map(LogRecord::getMessage).toList(), "no loader record may render the advertised jwks_uri"));
        }

        private void startOnLoopback(UnusableJwksUriDispatcher dispatcher) throws IOException {
            server.setDispatcher(dispatcher);
            server.start(InetAddress.getLoopbackAddress(), 0);
            dispatcher.setPort(server.getPort());
        }

        private void assertTerminalAtInitializationAndNotRetried(HttpJwksLoaderConfig config,
                UnusableJwksUriDispatcher dispatcher) {
            try (HttpJwksLoader loader = new HttpJwksLoader(config)) {
                LoaderStatus initStatus = loader.initJWKSLoader(securityEventCounter).join();

                assertAll("an unusable advertised jwks_uri is a terminal rejection",
                        () -> assertTrue(dispatcher.discoveryRequests() >= 1, "the discovery document was fetched"),
                        () -> assertEquals(LoaderStatus.ERROR, initStatus, "the future completes with the status in effect"),
                        () -> assertEquals(LoaderStatus.ERROR, loader.getLoaderStatus()),
                        () -> assertFalse(loader.isBackgroundRefreshActive(), "no refresh may be scheduled"),
                        () -> assertTrue(loader.getKeyIds().isEmpty(), "no key may be installed"),
                        () -> assertEquals(1, unusableUriLogCount(), "JWKS_URI_UNUSABLE is logged exactly once"));
                assertNotRetried(dispatcher);
            }
        }

        /**
         * Asserts that over several refresh intervals the discovery endpoint is not contacted again and
         * neither {@code JWKS_URI_UNUSABLE} nor {@code BACKGROUND_REFRESH_FAILED} is logged again.
         */
        private void assertNotRetried(UnusableJwksUriDispatcher dispatcher) {
            int discoveriesAtTerminal = dispatcher.discoveryRequests();
            int refreshFailuresAtTerminal = refreshFailureLogCount();

            Awaitility.await().during(NO_RETRY_WINDOW).atMost(NO_RETRY_DEADLINE)
                    .until(() -> dispatcher.discoveryRequests() == discoveriesAtTerminal);

            assertAll("the terminal rejection is not retried",
                    () -> assertEquals(discoveriesAtTerminal, dispatcher.discoveryRequests(),
                            "no further discovery request may be sent"),
                    () -> assertEquals(1, unusableUriLogCount(), "JWKS_URI_UNUSABLE must not be logged again"),
                    () -> assertEquals(refreshFailuresAtTerminal, refreshFailureLogCount(),
                            "BACKGROUND_REFRESH_FAILED must not be logged again"));
        }

        private static int unusableUriLogCount() {
            return TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(TestLogLevel.WARN,
                    JWTValidationLogMessages.WARN.JWKS_URI_UNUSABLE.resolveIdentifierString()).size();
        }

        private static int refreshFailureLogCount() {
            return TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(TestLogLevel.WARN,
                    JWTValidationLogMessages.WARN.BACKGROUND_REFRESH_FAILED.resolveIdentifierString()).size();
        }
    }

    /**
     * Pins that the further loader records which could carry the advertised {@code jwks_uri} do not render it:
     * an egress block of the advertised URI at initialization and on a background refresh logs a fixed
     * detail without the rejection, and a re-discovery that meets an unusable advertised URI logs no refusal
     * text at any level.
     */
    @Nested
    @DisplayName("Advertised jwks_uri in other loader records")
    class AdvertisedJwksUriInOtherRecords {

        private static final String PUBLIC_IP = "93.184.216.34";
        private static final String METADATA_IP = "169.254.169.254";
        private static final String BLOCKED_HOST = "blocked.example.com";
        private static final String MARKED_JWKS_PATH = "/jwks-" + ADVERTISED_URI_MARKER + ".json";

        private final RetryConfig fastRetry = RetryConfig.builder()
                .initialDelay(Duration.ofMillis(1))
                .maxDelay(Duration.ofMillis(5))
                .build();

        private MockWebServer server;

        /**
         * Exposes the enclosing class's dispatcher to the MockWebServer extension, which resolves the
         * module dispatcher on the nested test instance.
         *
         * @return the shared JWKS dispatcher
         */
        public JwksResolveDispatcher getModuleDispatcher() {
            return moduleDispatcher;
        }

        @BeforeEach
        void createServer() {
            server = new MockWebServer();
        }

        @AfterEach
        void stopServer() {
            server.close();
        }

        private HttpJwksLoaderConfig wellKnownConfig(DiscoveryGenerationDispatcher dispatcher) throws IOException {
            server.setDispatcher(dispatcher);
            server.start();
            return HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                    .wellKnownUrl(server.url(DiscoveryGenerationDispatcher.WELL_KNOWN_PATH).toString())
                    .retryConfig(fastRetry)
                    .refreshIntervalSeconds(0)
                    .build();
        }

        @Test
        @DisplayName("An egress block of the advertised jwks_uri at initialization logs a fixed detail, not the URI")
        void egressBlockAtInitializationDoesNotRenderTheAdvertisedUri() throws Exception {
            // The egress rejection message quotes the full URI it blocked
            DiscoveryGenerationDispatcher dispatcher = new DiscoveryGenerationDispatcher(List.of(
                    "https://%s/%s/jwks.json?token=%s".formatted(BLOCKED_HOST, ADVERTISED_URI_MARKER, ADVERTISED_URI_MARKER)));
            EgressPolicy blockedHostPolicy = EgressPolicy.builder().resolver(host -> new InetAddress[]{
                    InetAddress.getByName(BLOCKED_HOST.equals(host) ? METADATA_IP : PUBLIC_IP)}).build();

            try (HttpJwksLoader loader = new HttpJwksLoader(wellKnownConfig(dispatcher), blockedHostPolicy)) {
                assertEquals(LoaderStatus.ERROR, loader.initJWKSLoader(securityEventCounter).get(10, TimeUnit.SECONDS));

                List<LogRecord> egressRecords = TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(
                        TestLogLevel.ERROR, ADVERTISED_URI_EGRESS_BLOCKED);
                assertAll("the egress block is logged with the fixed detail only",
                        () -> assertEquals(1, egressRecords.size(), "JWKS_INITIALIZATION_FAILED carries the fixed detail"),
                        () -> assertTrue(egressRecords.getFirst().getMessage().contains(
                                JWTValidationLogMessages.ERROR.JWKS_INITIALIZATION_FAILED.resolveIdentifierString())),
                        () -> assertNull(egressRecords.getFirst().getThrown(), "the rejection may not be attached"),
                        () -> assertEquals(List.of(), loaderRecordsContaining(ADVERTISED_URI_MARKER).stream()
                                .map(LogRecord::getMessage).toList(), "no loader record may render the advertised jwks_uri"));
            }
        }

        @Test
        @DisplayName("An egress block of the advertised jwks_uri on a refresh logs a fixed detail, not the URI")
        void egressBlockOnRefreshDoesNotRenderTheAdvertisedUri() throws Exception {
            DiscoveryGenerationDispatcher dispatcher = new DiscoveryGenerationDispatcher(List.of(MARKED_JWKS_PATH));
            AtomicBoolean rebound = new AtomicBoolean(false);
            EgressPolicy reboundPolicy = EgressPolicy.builder().resolver(host -> new InetAddress[]{
                    InetAddress.getByName(rebound.get() ? METADATA_IP : PUBLIC_IP)}).build();

            try (HttpJwksLoader loader = new HttpJwksLoader(wellKnownConfig(dispatcher), reboundPolicy)) {
                assertEquals(LoaderStatus.OK, loader.initJWKSLoader(securityEventCounter).get(10, TimeUnit.SECONDS));

                rebound.set(true);
                loader.performBackgroundRefresh();

                List<LogRecord> egressRecords = TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(
                        TestLogLevel.WARN, ADVERTISED_URI_EGRESS_BLOCKED);
                assertAll("the refresh egress block is logged with the fixed detail only",
                        () -> assertEquals(1, dispatcher.jwksFetches(MARKED_JWKS_PATH), "the refresh fetches nothing"),
                        () -> assertEquals(1, egressRecords.size(), "BACKGROUND_REFRESH_FAILED carries the fixed detail"),
                        () -> assertTrue(egressRecords.getFirst().getMessage().contains(
                                JWTValidationLogMessages.WARN.BACKGROUND_REFRESH_FAILED.resolveIdentifierString())),
                        () -> assertNull(egressRecords.getFirst().getThrown(), "the rejection may not be attached"),
                        () -> assertEquals(List.of(), loaderRecordsContaining(ADVERTISED_URI_MARKER).stream()
                                .map(LogRecord::getMessage).toList(), "no loader record may render the advertised jwks_uri"));
            }
        }

        @Test
        @DisplayName("A re-discovery meeting an unusable advertised jwks_uri logs no refusal text at any level")
        void refusedRediscoveryDoesNotRenderTheAdvertisedUri() throws Exception {
            TestLogLevel.DEBUG.addLogger(HttpJwksLoader.class);
            String currentPath = "/jwks-current.json";
            DiscoveryGenerationDispatcher dispatcher = new DiscoveryGenerationDispatcher(
                    List.of(currentPath, "http://[" + ADVERTISED_URI_MARKER));

            try (HttpJwksLoader loader = new HttpJwksLoader(wellKnownConfig(dispatcher))) {
                assertEquals(LoaderStatus.OK, loader.initJWKSLoader(securityEventCounter).get(10, TimeUnit.SECONDS));

                dispatcher.failJwks(currentPath);
                for (int cycle = 0; cycle <= HttpJwksLoader.REDISCOVERY_FAILURE_THRESHOLD; cycle++) {
                    loader.performBackgroundRefresh();
                }

                assertAll("the refused re-discovery keeps the endpoint and renders no advertised URI",
                        () -> assertEquals(2, dispatcher.discoveryRequests(), "the re-discovery consulted the document"),
                        () -> assertEquals(Optional.of(currentPath), loader.getJwksFetchUri().map(URI::getPath)),
                        () -> assertEquals(List.of(), loaderRecordsContaining(ADVERTISED_URI_MARKER).stream()
                                .map(LogRecord::getMessage).toList(), "no loader record may render the advertised jwks_uri"));
                LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                        JWTValidationLogMessages.WARN.JWKS_URI_RESOLUTION_FAILED.resolveIdentifierString());
            }
        }
    }

    /**
     * Discovery dispatcher advertising an unusable {@code jwks_uri}. Discovery requests are counted; while
     * the dispatcher is marked unavailable they are answered with HTTP 500, a transient discovery failure.
     */
    private static final class UnusableJwksUriDispatcher extends Dispatcher {

        static final String WELL_KNOWN_PATH = "/.well-known/openid-configuration";

        private final IntFunction<String> advertisedJwksUri;
        private final AtomicInteger discoveryRequests = new AtomicInteger();
        private volatile boolean available = true;
        private volatile int port;

        /**
         * @param advertisedJwksUri computes the advertised {@code jwks_uri} from the server port
         */
        UnusableJwksUriDispatcher(IntFunction<String> advertisedJwksUri) {
            this.advertisedJwksUri = advertisedJwksUri;
        }

        void setAvailable(boolean available) {
            this.available = available;
        }

        void setPort(int port) {
            this.port = port;
        }

        int discoveryRequests() {
            return discoveryRequests.get();
        }

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            HttpUrl url = request.getUrl();
            if (!WELL_KNOWN_PATH.equals(url.encodedPath())) {
                return new MockResponse(404, Headers.of(), "");
            }
            discoveryRequests.incrementAndGet();
            if (!available) {
                return new MockResponse(500, Headers.of(), "");
            }
            String baseUrl = url.newBuilder().encodedPath("/").build().toString();
            String issuer = baseUrl.substring(0, baseUrl.length() - 1);
            return new MockResponse(200, Headers.of("Content-Type", "application/json"), """
                    {
                      "issuer": "%s",
                      "jwks_uri": "%s"
                    }
                    """.formatted(issuer, advertisedJwksUri.apply(port)));
        }
    }
}
