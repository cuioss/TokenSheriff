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
package de.cuioss.sheriff.token.client.discovery;

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.internal.ClientLogMessages;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.validation.test.dispatcher.WellKnownDispatcher;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The stale policy of {@link CachingDiscoveryResolver}: what a failing refresh serves, when it stops
 * serving and fails closed, how often it retries, and what it logs. Time is driven by an injected,
 * mutable {@link InstantSource}; no case sleeps.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("CachingDiscoveryResolver stale policy")
class CachingDiscoveryResolverStalePolicyTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration GRACE = Duration.ofMinutes(10);
    private static final String TRANSPORT_EXCEPTION = TransportException.class.getName();
    private static final String SERVING_STALE =
            ClientLogMessages.WARN.DISCOVERY_REFRESH_FAILED_SERVING_STALE.resolveIdentifierString();
    private static final String EXPIRED =
            ClientLogMessages.WARN.DISCOVERY_CACHE_EXPIRED_FAILING_CLOSED.resolveIdentifierString();

    @Getter
    private final WellKnownDispatcher moduleDispatcher = new WellKnownDispatcher();

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final InstantSource clock = now::get;

    private static DiscoveryResolver delegateFor(URIBuilder uriBuilder) {
        return new DiscoveryResolver(ClientConfiguration.builder()
                .issuer(uriBuilder.buildAsString())
                .clientId(Generators.nonBlankStrings().next())
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(true)
                .allowLoopbackEgress(true)
                .build());
    }

    private CachingDiscoveryResolver resolver(URIBuilder uriBuilder, Duration grace, Duration minRefreshInterval) {
        return new CachingDiscoveryResolver(delegateFor(uriBuilder), TTL, grace, minRefreshInterval, Duration.ZERO,
                clock);
    }

    /** Resolves once successfully, then moves past the TTL and lets every further fetch fail. */
    private ProviderMetadata resolveThenFailRefreshes(CachingDiscoveryResolver resolver) {
        var good = resolver.resolve();
        advance(TTL);
        moduleDispatcher.returnError();
        return good;
    }

    private void advance(Duration duration) {
        now.set(now.get().plus(duration));
    }

    @Test
    @DisplayName("Should serve the last good metadata while a refresh fails within grace")
    void shouldServeStaleWithinGrace(URIBuilder uriBuilder) {
        var resolver = resolver(uriBuilder, GRACE, Duration.ZERO);
        var good = resolveThenFailRefreshes(resolver);

        var served = resolver.resolve();

        var status = resolver.status();
        assertAll("stale served",
                () -> assertEquals(good.getTokenEndpoint(), served.getTokenEndpoint(), "last good content"),
                () -> assertEquals(2, moduleDispatcher.getCallCounter(), "the refresh was attempted"),
                () -> assertEquals(DiscoveryCacheStatus.State.FAILED, status.state()),
                () -> assertEquals(TRANSPORT_EXCEPTION, status.lastFailureCause(), "cause is the class name"),
                () -> assertEquals(START, status.lastSuccess(), "lastSuccess is kept"));
    }

    @Test
    @DisplayName("Should log one serving-stale WARN per failure episode, not one per attempt")
    void shouldWarnOncePerEpisode(URIBuilder uriBuilder) {
        var resolver = resolver(uriBuilder, GRACE, Duration.ZERO);
        resolveThenFailRefreshes(resolver);

        resolver.resolve();
        advance(Duration.ofSeconds(10));
        resolver.resolve();
        advance(Duration.ofSeconds(10));
        resolver.resolve();

        assertEquals(4, moduleDispatcher.getCallCounter(), "each stale call retries without an interval");
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN, SERVING_STALE);
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, EXPIRED);
    }

    @Test
    @DisplayName("Should not re-fetch before the minimum refresh interval has elapsed after a failure")
    void shouldHonourMinRefreshInterval(URIBuilder uriBuilder) {
        var interval = Duration.ofMinutes(1);
        var resolver = resolver(uriBuilder, GRACE, interval);
        var good = resolveThenFailRefreshes(resolver);
        resolver.resolve();
        assertEquals(2, moduleDispatcher.getCallCounter(), "the first stale call refreshes");

        advance(interval.minusSeconds(1));
        var served = resolver.resolve();
        assertEquals(2, moduleDispatcher.getCallCounter(), "no refresh within the interval");
        assertEquals(good.getTokenEndpoint(), served.getTokenEndpoint(), "stale content served meanwhile");

        advance(Duration.ofSeconds(1));
        resolver.resolve();
        assertEquals(3, moduleDispatcher.getCallCounter(), "the interval elapsed, so it refreshes again");
    }

    @Test
    @DisplayName("Should fail closed after the grace period with the expiry WARN, logged once")
    void shouldFailClosedAfterGrace(URIBuilder uriBuilder) {
        var resolver = resolver(uriBuilder, GRACE, Duration.ZERO);
        resolveThenFailRefreshes(resolver);
        resolver.resolve();

        advance(GRACE);
        var first = assertThrows(TransportException.class, resolver::resolve);
        var second = assertThrows(TransportException.class, resolver::resolve);

        assertAll("failed closed",
                () -> assertTrue(first.getMessage().contains("expired"), first.getMessage()),
                () -> assertTrue(first.getMessage().contains(TRANSPORT_EXCEPTION),
                        "the message names the last failure cause class: " + first.getMessage()),
                () -> assertInstanceOf(TransportException.class, first.getCause(), "the last failure is attached"),
                () -> assertEquals(first.getMessage(), second.getMessage(), "every later call fails the same way"));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN, EXPIRED);
    }

    @Test
    @DisplayName("Should fail closed at TTL expiry when the grace period is zero")
    void shouldFailClosedAtTtlWithoutGrace(URIBuilder uriBuilder) {
        var resolver = resolver(uriBuilder, Duration.ZERO, Duration.ZERO);
        resolveThenFailRefreshes(resolver);

        var thrown = assertThrows(TransportException.class, resolver::resolve);

        assertTrue(thrown.getMessage().contains("expired"), thrown.getMessage());
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, SERVING_STALE);
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN, EXPIRED);
    }

    @Test
    @DisplayName("Should propagate the refresh failure when nothing was ever resolved")
    void shouldThrowWhenNothingWasResolved(URIBuilder uriBuilder) {
        moduleDispatcher.returnError();
        var resolver = resolver(uriBuilder, GRACE, Duration.ZERO);

        var thrown = assertThrows(TransportException.class, resolver::resolve);
        assertThrows(TransportException.class, resolver::resolve);

        var status = resolver.status();
        assertAll("failure before any success",
                () -> assertTrue(thrown.getMessage().contains("unexpected HTTP status 500"), thrown.getMessage()),
                () -> assertEquals(2, moduleDispatcher.getCallCounter(), "each call retries"),
                () -> assertEquals(DiscoveryCacheStatus.State.FAILED, status.state()),
                () -> assertNull(status.lastSuccess()),
                () -> assertEquals(TRANSPORT_EXCEPTION, status.lastFailureCause()));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, SERVING_STALE);
    }

    @Test
    @DisplayName("Should fail every call within the interval without a fetch when nothing was ever resolved")
    void shouldThrowWithinIntervalWithoutFetch(URIBuilder uriBuilder) {
        moduleDispatcher.returnError();
        var resolver = resolver(uriBuilder, GRACE, Duration.ofMinutes(1));

        assertThrows(TransportException.class, resolver::resolve);
        advance(Duration.ofSeconds(30));
        assertThrows(TransportException.class, resolver::resolve);

        assertEquals(1, moduleDispatcher.getCallCounter(), "no refresh within the interval");
    }

    @Test
    @DisplayName("Should recover with one INFO record and start a new failure episode afterwards")
    void shouldStartNewEpisodeAfterRecovery(URIBuilder uriBuilder) {
        var resolver = resolver(uriBuilder, GRACE, Duration.ZERO);
        resolveThenFailRefreshes(resolver);
        resolver.resolve();

        moduleDispatcher.returnDefault();
        advance(Duration.ofSeconds(1));
        resolver.resolve();
        assertEquals(DiscoveryCacheStatus.State.RESOLVED, resolver.status().state(), "recovered");
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                ClientLogMessages.INFO.DISCOVERY_CACHE_RECOVERED.resolveIdentifierString());

        advance(TTL);
        moduleDispatcher.returnError();
        resolver.resolve();
        assertEquals(DiscoveryCacheStatus.State.FAILED, resolver.status().state(), "failing again");
        assertEquals(START.plus(TTL).plusSeconds(1), resolver.status().lastSuccess(),
                "lastSuccess is the recovery instant");
    }
}
