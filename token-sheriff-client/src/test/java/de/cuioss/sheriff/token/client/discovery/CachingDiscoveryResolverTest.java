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
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caching, refresh and status behaviour of {@link CachingDiscoveryResolver} against a mocked
 * well-known endpoint. Time is driven by an injected, mutable {@link InstantSource}; no case sleeps.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("CachingDiscoveryResolver caches, refreshes and reports its state")
class CachingDiscoveryResolverTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration GRACE = Duration.ofMinutes(10);
    private static final String TRANSPORT_EXCEPTION = TransportException.class.getName();

    @Getter
    private final WellKnownDispatcher moduleDispatcher = new WellKnownDispatcher();

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final InstantSource clock = now::get;

    private static ClientConfiguration configFor(String issuer) {
        return ClientConfiguration.builder()
                .issuer(issuer)
                .clientId(Generators.nonBlankStrings().next())
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(true)
                .allowLoopbackEgress(true)
                .build();
    }

    private CachingDiscoveryResolver resolverFor(URIBuilder uriBuilder) {
        return new CachingDiscoveryResolver(new DiscoveryResolver(configFor(uriBuilder.buildAsString())),
                TTL, GRACE, clock);
    }

    private void advance(Duration duration) {
        now.set(now.get().plus(duration));
    }

    static Stream<Arguments> invalidDurations() {
        return Stream.of(
                Arguments.of("zero ttl", Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                Arguments.of("negative ttl", Duration.ofSeconds(-1), Duration.ZERO, Duration.ZERO, Duration.ZERO),
                Arguments.of("negative grace", TTL, Duration.ofSeconds(-1), Duration.ZERO, Duration.ZERO),
                Arguments.of("negative min refresh interval", TTL, GRACE, Duration.ofSeconds(-1), Duration.ZERO),
                Arguments.of("negative jitter", TTL, GRACE, Duration.ZERO, Duration.ofSeconds(-1)),
                Arguments.of("jitter equal to ttl", TTL, GRACE, Duration.ZERO, TTL),
                Arguments.of("jitter above ttl", TTL, GRACE, Duration.ZERO, TTL.plusSeconds(1)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDurations")
    @DisplayName("Should refuse an invalid duration at construction")
    void shouldRefuseInvalidDuration(String label, Duration ttl, Duration grace, Duration minRefreshInterval,
            Duration jitter) {
        var delegate = new DiscoveryResolver(configFor("https://issuer.example"));

        assertThrows(IllegalArgumentException.class,
                () -> new CachingDiscoveryResolver(delegate, ttl, grace, minRefreshInterval, jitter, clock),
                () -> label + " must be refused");
    }

    @Test
    @DisplayName("Should refuse a null argument at construction")
    @SuppressWarnings("DataFlowIssue") // deliberately violating the non-null contract
    void shouldRefuseNullArgument() {
        var delegate = new DiscoveryResolver(configFor("https://issuer.example"));

        assertAll("null arguments",
                () -> assertThrows(NullPointerException.class,
                        () -> new CachingDiscoveryResolver(null, TTL, GRACE, clock)),
                () -> assertThrows(NullPointerException.class,
                        () -> new CachingDiscoveryResolver(delegate, null, GRACE, clock)),
                () -> assertThrows(NullPointerException.class,
                        () -> new CachingDiscoveryResolver(delegate, TTL, null, clock)),
                () -> assertThrows(NullPointerException.class,
                        () -> new CachingDiscoveryResolver(delegate, TTL, GRACE, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new CachingDiscoveryResolver(delegate, TTL, GRACE, null, Duration.ZERO, clock)),
                () -> assertThrows(NullPointerException.class,
                        () -> new CachingDiscoveryResolver(delegate, TTL, GRACE, Duration.ZERO, null, clock)));
    }

    @Test
    @DisplayName("Should fetch on the first call and serve later calls within the TTL from the cache")
    void shouldServeFromCacheWithinTtl(URIBuilder uriBuilder) {
        var resolver = resolverFor(uriBuilder);

        var first = resolver.resolve();
        advance(TTL.minusSeconds(1));
        var second = resolver.resolve();

        assertAll("cached within ttl",
                () -> assertEquals(1, moduleDispatcher.getCallCounter(), "only the first call may fetch"),
                () -> assertEquals(first.getTokenEndpoint(), second.getTokenEndpoint(), "same content served"));
    }

    @Test
    @DisplayName("Should refresh once the TTL has elapsed")
    void shouldRefreshAfterTtl(URIBuilder uriBuilder) {
        var resolver = resolverFor(uriBuilder);

        resolver.resolve();
        advance(TTL);
        resolver.resolve();

        assertEquals(2, moduleDispatcher.getCallCounter(), "a call at the TTL boundary must refresh");
        assertEquals(START.plus(TTL), resolver.status().lastSuccess(), "the refresh moves lastSuccess");
    }

    @Test
    @DisplayName("Should shorten the freshness window by less than the jitter, never more")
    void shouldBoundJitter(URIBuilder uriBuilder) {
        var jitter = Duration.ofSeconds(30);
        for (int round = 0; round < 5; round++) {
            now.set(START);
            moduleDispatcher.setCallCounter(0);
            var resolver = new CachingDiscoveryResolver(
                    new DiscoveryResolver(configFor(uriBuilder.buildAsString())), TTL, GRACE, Duration.ZERO, jitter, clock);
            resolver.resolve();

            now.set(START.plus(TTL).minus(jitter));
            resolver.resolve();
            assertEquals(1, moduleDispatcher.getCallCounter(), "still fresh at ttl - jitter");

            now.set(START.plus(TTL));
            resolver.resolve();
            assertEquals(2, moduleDispatcher.getCallCounter(), "stale at the latest at ttl");
        }
    }

    @Test
    @DisplayName("Should hand out a defensive copy, so a mutated result never reaches the next call")
    void shouldReturnDefensiveCopy(URIBuilder uriBuilder) {
        var resolver = resolverFor(uriBuilder);

        var first = resolver.resolve();
        String original = first.tokenEndpoint;
        first.tokenEndpoint = "https://attacker.example/token";
        var second = resolver.resolve();

        assertAll("defensive copy",
                () -> assertNotSame(first, second, "every call returns its own instance"),
                () -> assertEquals(original, second.tokenEndpoint, "the cache keeps the resolved value"),
                () -> assertNotEquals(first.tokenEndpoint, second.tokenEndpoint, "the mutation stays local"),
                () -> assertEquals(1, moduleDispatcher.getCallCounter(), "served from the cache"));
    }

    @Test
    @DisplayName("Should report NOT_ATTEMPTED as UNDEFINED before the first call, without fetching")
    void shouldReportNotAttempted(URIBuilder uriBuilder) {
        var resolver = resolverFor(uriBuilder);

        assertAll("not attempted",
                () -> assertEquals(DiscoveryCacheStatus.notAttempted(), resolver.status()),
                () -> assertEquals(LoaderStatus.UNDEFINED, resolver.getLoaderStatus()),
                () -> assertEquals(0, moduleDispatcher.getCallCounter(), "status must not fetch"));
    }

    @Test
    @DisplayName("Should report RESOLVED as OK, and keep reporting it past the TTL without fetching")
    void shouldReportResolved(URIBuilder uriBuilder) {
        var resolver = resolverFor(uriBuilder);
        resolver.resolve();

        advance(TTL.plus(GRACE).plusSeconds(1));
        var status = resolver.status();

        assertAll("resolved",
                () -> assertEquals(DiscoveryCacheStatus.State.RESOLVED, status.state()),
                () -> assertEquals(START, status.lastSuccess()),
                () -> assertNull(status.lastFailureCause()),
                () -> assertEquals(LoaderStatus.OK, resolver.getLoaderStatus()),
                () -> assertTrue(resolver.isLoaderStatusOK()),
                () -> assertEquals(1, moduleDispatcher.getCallCounter(), "status must not fetch"));
    }

    @Test
    @DisplayName("Should report FAILED as UNDEFINED within grace and as ERROR after it, without fetching")
    void shouldReportFailedAcrossGrace(URIBuilder uriBuilder) {
        var resolver = resolverFor(uriBuilder);
        resolver.resolve();
        advance(TTL);
        moduleDispatcher.returnError();
        resolver.resolve();

        var status = resolver.status();
        assertAll("failed within grace",
                () -> assertEquals(DiscoveryCacheStatus.State.FAILED, status.state()),
                () -> assertEquals(START, status.lastSuccess(), "lastSuccess is kept"),
                () -> assertEquals(TRANSPORT_EXCEPTION, status.lastFailureCause()),
                () -> assertEquals(LoaderStatus.UNDEFINED, resolver.getLoaderStatus()));

        advance(GRACE);
        assertAll("failed after grace",
                () -> assertEquals(LoaderStatus.ERROR, resolver.getLoaderStatus()),
                () -> assertEquals(2, moduleDispatcher.getCallCounter(), "status must not fetch"));
    }

    @Test
    @DisplayName("Should report FAILED as ERROR when nothing was ever resolved")
    void shouldReportFailedWithNothingServable(URIBuilder uriBuilder) {
        moduleDispatcher.returnError();
        var resolver = resolverFor(uriBuilder);
        assertThrows(TransportException.class, resolver::resolve);

        var status = resolver.status();
        assertAll("failed, nothing servable",
                () -> assertEquals(DiscoveryCacheStatus.State.FAILED, status.state()),
                () -> assertNull(status.lastSuccess()),
                () -> assertEquals(TRANSPORT_EXCEPTION, status.lastFailureCause()),
                () -> assertEquals(LoaderStatus.ERROR, resolver.getLoaderStatus()));
    }

    @Test
    @DisplayName("Should log the recovery INFO record once, on the first success after a failure")
    void shouldLogRecoveryOnce(URIBuilder uriBuilder) {
        var resolver = resolverFor(uriBuilder);
        resolver.resolve();
        advance(TTL);
        moduleDispatcher.returnError();
        resolver.resolve();
        advance(Duration.ofSeconds(1));
        resolver.resolve();
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO,
                ClientLogMessages.INFO.DISCOVERY_CACHE_RECOVERED.resolveIdentifierString());

        moduleDispatcher.returnDefault();
        advance(Duration.ofSeconds(1));
        resolver.resolve();
        advance(TTL);
        resolver.resolve();

        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                ClientLogMessages.INFO.DISCOVERY_CACHE_RECOVERED.resolveIdentifierString());
        assertEquals(DiscoveryCacheStatus.State.RESOLVED, resolver.status().state());
        assertNull(resolver.status().lastFailureCause(), "a success clears the failure cause");
    }
}
