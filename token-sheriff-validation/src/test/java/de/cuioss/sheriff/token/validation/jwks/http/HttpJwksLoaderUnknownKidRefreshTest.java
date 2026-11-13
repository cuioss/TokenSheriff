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

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.EgressPolicy;
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.validation.JWTValidationLogMessages;
import de.cuioss.sheriff.token.validation.test.InMemoryJWKSFactory;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Functional behaviour of the opt-in unknown-kid refresh of {@link HttpJwksLoader}: one fetch per
 * claimed window, a swap only when the fetched key set holds the requested key id, the retirement of the
 * previous key set, and the window bound measured on an injected monotonic clock.
 * <p>
 * Every fetch is counted on the server side by {@link UnknownKidJwksDispatcher}. The unknown-kid fetch
 * runs on a caller-supplied single-thread executor, so draining that executor waits for it
 * deterministically.
 */
@EnableTestLogger
@EnableMockWebServer
@DisplayName("HttpJwksLoader unknown-kid refresh")
class HttpJwksLoaderUnknownKidRefreshTest {

    private static final String DEFAULT_KID = InMemoryJWKSFactory.DEFAULT_KEY_ID;
    private static final String ROTATED_KID = UnknownKidJwksDispatcher.ROTATED_KEY_ID;
    private static final Duration WINDOW = Duration.ofSeconds(30);

    @Getter
    private final UnknownKidJwksDispatcher moduleDispatcher = new UnknownKidJwksDispatcher();

    private final AtomicLong clock = new AtomicLong(1_000L);
    private ScheduledExecutorService executor;

    @BeforeEach
    void setUp() {
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.DEFAULT_KEY);
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private HttpJwksLoaderConfig.HttpJwksLoaderConfigBuilder configBuilder(URIBuilder uriBuilder) {
        return HttpJwksLoaderConfig.builder()
                .allowLoopbackEgress(true)
                .allowInsecureHttp(true)
                .jwksUrl(uriBuilder.addPathSegment(UnknownKidJwksDispatcher.LOCAL_PATH).buildAsString())
                .issuerIdentifier("test-issuer")
                .refreshIntervalSeconds(3600)
                .scheduledExecutorService(executor)
                .unknownKidRefreshWindow(WINDOW);
    }

    private HttpJwksLoader initializedLoader(HttpJwksLoaderConfig config) {
        HttpJwksLoader loader = new HttpJwksLoader(config, config.getEgressPolicy(), clock::get);
        assertEquals(LoaderStatus.OK, loader.initJWKSLoader(new SecurityEventCounter()).join(),
                "the initial load must install the default key set");
        return loader;
    }

    private void drainExecutor() throws Exception {
        executor.submit(() -> {
        }).get(30, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("A claimed miss schedules one fetch and installs a key set holding the kid")
    void shouldInstallKeySetHoldingRequestedKid(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).build());
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.ROTATED_KEY);

        assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty(), "the triggering lookup is still refused");
        drainExecutor();

        assertAll("the rotated key set is installed, the previous one retired",
                () -> assertEquals(2, moduleDispatcher.getRequestCount(), "exactly one unknown-kid fetch"),
                () -> assertTrue(loader.getKeyInfo(ROTATED_KID).isPresent(), "the next lookup succeeds"),
                () -> assertTrue(loader.getKeyInfo(DEFAULT_KID).isPresent(),
                        "the previous key set stays resolvable within the grace period"),
                () -> assertEquals(Set.of(ROTATED_KID), loader.getKeyIds(),
                        "getKeyIds reports the active key set only"),
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                JWTValidationLogMessages.INFO.UNKNOWN_KID_REFRESH_SCHEDULED.resolveIdentifierString());
        loader.close();
    }

    @Test
    @DisplayName("A fetched key set without the kid changes nothing")
    void shouldDiscardKeySetWithoutRequestedKid(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).build());
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.ROTATED_KEY);

        assertTrue(loader.getKeyInfo("invented-kid").isEmpty());
        drainExecutor();

        assertAll("the installed state is unchanged",
                () -> assertEquals(2, moduleDispatcher.getRequestCount(), "the fetch did reach the server"),
                () -> assertEquals(Set.of(DEFAULT_KID), loader.getKeyIds()),
                () -> assertTrue(loader.getKeyInfo(DEFAULT_KID).isPresent()),
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()),
                () -> assertTrue(loader.getJwksFetchUri().isPresent(), "the endpoint is kept"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                JWTValidationLogMessages.WARN.UNKNOWN_KID_REFRESH_DISCARDED.resolveIdentifierString());
        loader.close();
    }

    @Test
    @DisplayName("An unchanged key set is discarded without a swap")
    void shouldDiscardUnchangedKeySet(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).build());

        assertTrue(loader.getKeyInfo("invented-kid").isEmpty());
        drainExecutor();

        assertAll(
                () -> assertEquals(2, moduleDispatcher.getRequestCount()),
                () -> assertEquals(Set.of(DEFAULT_KID), loader.getKeyIds()),
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, HttpJwksLoader.DISCARD_UNCHANGED);
        loader.close();
    }

    @Test
    @DisplayName("With a zero grace period the previous key set is dropped by the swap")
    void shouldNotRetainPreviousKeySetWithoutGracePeriod(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).keyRotationGracePeriod(Duration.ZERO).build());
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.ROTATED_KEY);

        loader.getKeyInfo(ROTATED_KID);
        drainExecutor();

        assertAll(
                () -> assertTrue(loader.getKeyInfo(ROTATED_KID).isPresent()),
                () -> assertTrue(loader.getKeyInfo(DEFAULT_KID).isEmpty(), "no retired key set is kept"));
        loader.close();
    }

    @Test
    @DisplayName("A second miss within the window fetches nothing, a miss after it fetches again")
    void shouldFetchAtMostOncePerWindow(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).build());

        loader.getKeyInfo("first-invented-kid");
        drainExecutor();
        clock.addAndGet(WINDOW.toNanos() - 1);
        loader.getKeyInfo("second-invented-kid");
        drainExecutor();
        int withinWindow = moduleDispatcher.getRequestCount();
        clock.addAndGet(1);
        loader.getKeyInfo("third-invented-kid");
        drainExecutor();

        assertAll(
                () -> assertEquals(2, withinWindow, "the second miss falls into the claimed window"),
                () -> assertEquals(3, moduleDispatcher.getRequestCount(), "the window expired exactly at its end"));
        loader.close();
    }

    @Test
    @DisplayName("The window comparison stays correct across the overflow of the monotonic clock")
    void shouldCompareWindowOverflowSafely(URIBuilder uriBuilder) throws Exception {
        clock.set(Long.MAX_VALUE - Duration.ofSeconds(1).toNanos());
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).build());

        loader.getKeyInfo("first-invented-kid");
        drainExecutor();
        clock.addAndGet(WINDOW.toNanos() - Duration.ofSeconds(1).toNanos());
        loader.getKeyInfo("second-invented-kid");
        drainExecutor();
        int beforeExpiry = moduleDispatcher.getRequestCount();
        clock.addAndGet(Duration.ofSeconds(1).toNanos());
        loader.getKeyInfo("third-invented-kid");
        drainExecutor();

        assertAll(
                () -> assertTrue(clock.get() < 0, "the clock value has overflowed"),
                () -> assertEquals(2, beforeExpiry, "a claimed window is not released by the overflow"),
                () -> assertEquals(3, moduleDispatcher.getRequestCount(), "the window expires after the overflow"));
        loader.close();
    }

    @Test
    @DisplayName("Two loaders claim independent windows")
    void shouldKeepIndependentWindowsPerLoader(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoaderConfig config = configBuilder(uriBuilder).build();
        HttpJwksLoader first = initializedLoader(config);
        HttpJwksLoader second = initializedLoader(config);
        int afterInit = moduleDispatcher.getRequestCount();

        first.getKeyInfo("invented-kid");
        drainExecutor();
        second.getKeyInfo("invented-kid");
        drainExecutor();

        assertEquals(afterInit + 2, moduleDispatcher.getRequestCount(), "each loader fetches once");
        first.close();
        second.close();
    }

    @Test
    @DisplayName("An unexpected runtime failure of the fetch is discarded, uncounted, and releases the in-flight guard")
    void shouldDiscardUnexpectedRuntimeFailure(URIBuilder uriBuilder) throws Exception {
        String remoteContent = "remote-jwks-content-\nforged-line";
        AtomicBoolean failing = new AtomicBoolean(false);
        EgressPolicy.HostResolver resolver = host -> {
            if (failing.get()) {
                throw new UnsupportedOperationException(remoteContent);
            }
            return InetAddress.getAllByName(host);
        };
        // A maximum staleness this small degrades the loader on the first counted failed cycle
        HttpJwksLoaderConfig config = configBuilder(uriBuilder).maxStaleness(Duration.ofMillis(1)).build();
        HttpJwksLoader loader = new HttpJwksLoader(config,
                EgressPolicy.builder().allowLoopback(true).resolver(resolver).build(), clock::get);
        assertEquals(LoaderStatus.OK, loader.initJWKSLoader(new SecurityEventCounter()).join());
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.ROTATED_KEY);
        int afterInit = moduleDispatcher.getRequestCount();

        failing.set(true);
        assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty());
        drainExecutor();

        assertAll("the failure is discarded and changes nothing",
                () -> assertEquals(afterInit, moduleDispatcher.getRequestCount(), "the fetch failed before the request"),
                () -> assertEquals(Set.of(DEFAULT_KID), loader.getKeyIds()),
                () -> assertTrue(loader.getJwksFetchUri().isPresent(), "the endpoint is kept"),
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus(),
                        "an uncounted failure cannot trigger the staleness degradation"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                JWTValidationLogMessages.WARN.UNKNOWN_KID_REFRESH_DISCARDED.resolveIdentifierString());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, HttpJwksLoader.DISCARD_FETCH_FAILED);
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, remoteContent);

        failing.set(false);
        clock.addAndGet(WINDOW.toNanos());
        assertTrue(loader.getKeyInfo(ROTATED_KID).isEmpty());
        drainExecutor();

        assertAll("the in-flight guard was released, so the next window fetches again",
                () -> assertEquals(afterInit + 1, moduleDispatcher.getRequestCount()),
                () -> assertTrue(loader.getKeyInfo(ROTATED_KID).isPresent()));
        loader.close();
    }

    @Test
    @DisplayName("A miss before any key set is installed schedules nothing")
    void shouldNotFetchWithoutInstalledKeySet(URIBuilder uriBuilder) throws Exception {
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.SERVER_ERROR);
        HttpJwksLoaderConfig config = configBuilder(uriBuilder).build();
        HttpJwksLoader loader = new HttpJwksLoader(config, config.getEgressPolicy(), clock::get);
        loader.initJWKSLoader(new SecurityEventCounter()).join();
        int afterInit = moduleDispatcher.getRequestCount();

        loader.getKeyInfo("invented-kid");
        drainExecutor();

        assertAll(
                () -> assertTrue(loader.getKeyIds().isEmpty()),
                () -> assertEquals(afterInit, moduleDispatcher.getRequestCount(),
                        "only an installed key set can be refreshed on an unknown kid"));
        loader.close();
    }
}
