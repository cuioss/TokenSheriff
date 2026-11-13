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
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.validation.JWTValidationLogMessages;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.json.JwtHeader;
import de.cuioss.sheriff.token.validation.json.MapRepresentation;
import de.cuioss.sheriff.token.validation.pipeline.DecodedJwt;
import de.cuioss.sheriff.token.validation.pipeline.SignatureTemplateManager;
import de.cuioss.sheriff.token.validation.pipeline.validator.TokenSignatureValidator;
import de.cuioss.sheriff.token.validation.security.SignatureAlgorithmPreferences;
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

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves that {@link HttpJwksLoader} behaves as before when neither the unknown-kid refresh window nor
 * the initial-load retry is configured.
 * <p>
 * The proof is taken on the server side: {@link UnknownKidJwksDispatcher} counts every request that
 * actually reaches the JWKS endpoint, so a default that silently fetched would show up regardless of
 * what the loader reports about itself. Every negative case is paired with a positive control that
 * configures the setting and shows the same action does reach the server, so a counter that could not
 * move would fail the control rather than pass the proof.
 */
@EnableTestLogger
@EnableMockWebServer
@DisplayName("HttpJwksLoader default behaviour without the opt-in settings")
class HttpJwksLoaderDefaultBehaviourUnchangedTest {

    private static final String DEFAULT_KID = InMemoryJWKSFactory.DEFAULT_KEY_ID;
    private static final Duration WINDOW = Duration.ofSeconds(30);
    private static final int LOOKUPS = 50;
    private static final int SHORT_REFRESH_INTERVAL_SECONDS = 2;

    @Getter
    private final UnknownKidJwksDispatcher moduleDispatcher = new UnknownKidJwksDispatcher();

    private CountingScheduler executor;

    @BeforeEach
    void setUp() {
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.DEFAULT_KEY);
        executor = new CountingScheduler();
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
                .retryConfig(RetryConfig.builder()
                        .maxAttempts(1)
                        .initialDelay(Duration.ofMillis(1))
                        .maxDelay(Duration.ofMillis(5))
                        .build())
                .scheduledExecutorService(executor);
    }

    private static HttpJwksLoader initializedLoader(HttpJwksLoaderConfig config, LoaderStatus expected) {
        HttpJwksLoader loader = new HttpJwksLoader(config);
        assertEquals(expected, loader.initJWKSLoader(new SecurityEventCounter()).join());
        return loader;
    }

    private void drainExecutor() throws Exception {
        executor.submit(() -> {
        }).get(30, TimeUnit.SECONDS);
    }

    private static void lookUpUnknownKids(HttpJwksLoader loader) {
        for (int lookup = 0; lookup < LOOKUPS; lookup++) {
            assertTrue(loader.getKeyInfo("unknown-kid-" + lookup).isEmpty(), "an unknown kid is never resolved");
        }
    }

    private static DecodedJwt tokenWithKid(String kid) {
        var header = new JwtHeader("RS256", null, kid, null, null, null, null, null, null, null);
        return new DecodedJwt(header, new MapRepresentation(Map.of()), null, new String[]{"", "", ""}, "");
    }

    @Test
    @DisplayName("Unset: unknown-kid lookups never reach the server and schedule nothing")
    void shouldNotFetchOnUnknownKidByDefault(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).build(), LoaderStatus.OK);
        int afterInit = moduleDispatcher.getRequestCount();
        int submissionsAfterInit = executor.getSubmissions();

        lookUpUnknownKids(loader);
        int submissionsAfterLookups = executor.getSubmissions();
        drainExecutor();

        assertAll("the default loader stays silent",
                () -> assertEquals(afterInit, moduleDispatcher.getRequestCount(), "no request reaches the server"),
                () -> assertEquals(submissionsAfterInit, submissionsAfterLookups,
                        "the lookups hand nothing to the executor"),
                () -> assertEquals(Set.of(DEFAULT_KID), loader.getKeyIds()),
                () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO,
                JWTValidationLogMessages.INFO.UNKNOWN_KID_REFRESH_SCHEDULED.resolveIdentifierString());
        loader.close();
    }

    @Test
    @DisplayName("Control: with the window configured, the same lookups do reach the server")
    void shouldFetchOnUnknownKidWithWindow(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).unknownKidRefreshWindow(WINDOW).build(),
                LoaderStatus.OK);
        int afterInit = moduleDispatcher.getRequestCount();
        int submissionsAfterInit = executor.getSubmissions();

        lookUpUnknownKids(loader);
        int submissionsAfterLookups = executor.getSubmissions();
        drainExecutor();

        assertAll(
                () -> assertEquals(afterInit + 1, moduleDispatcher.getRequestCount(), "one fetch for the claimed window"),
                () -> assertEquals(submissionsAfterInit + 1, submissionsAfterLookups,
                        "the claimed window hands exactly one fetch to the executor"));
        loader.close();
    }

    @Test
    @DisplayName("Unset: a failed first load is retried at the fixed refresh interval, not before")
    void shouldKeepFixedIntervalCadenceAfterFailedFirstLoadByDefault(URIBuilder uriBuilder) {
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.SERVER_ERROR);
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder)
                .refreshIntervalSeconds(SHORT_REFRESH_INTERVAL_SECONDS).build(), LoaderStatus.UNDEFINED);
        int afterInit = moduleDispatcher.getRequestCount();

        await("no request inside the refresh interval")
                .during(Duration.ofMillis(1200))
                .atMost(Duration.ofMillis(1600))
                .until(() -> moduleDispatcher.getRequestCount() == afterInit);
        await("the periodic cycle at the refresh interval")
                .atMost(Duration.ofSeconds(4))
                .until(() -> moduleDispatcher.getRequestCount() > afterInit);

        assertEquals(LoaderStatus.UNDEFINED, loader.getLoaderStatus());
        loader.close();
    }

    @Test
    @DisplayName("Control: with initial-load retry configured, the first retry arrives inside the interval")
    void shouldRetryInsideIntervalWithInitialLoadRetry(URIBuilder uriBuilder) {
        moduleDispatcher.serve(UnknownKidJwksDispatcher.Mode.SERVER_ERROR);
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).initialLoadRetry(true).build(),
                LoaderStatus.UNDEFINED);
        int afterInit = moduleDispatcher.getRequestCount();

        await("the first retry after 1 s")
                .atMost(Duration.ofSeconds(4))
                .until(() -> moduleDispatcher.getRequestCount() > afterInit);
        loader.close();
    }

    @Test
    @DisplayName("Unset: TokenSignatureValidator refuses an unknown kid with KEY_NOT_FOUND and no fetch")
    void shouldRefuseUnknownKidWithoutFetchByDefault(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).build(), LoaderStatus.OK);
        SecurityEventCounter counter = new SecurityEventCounter();
        TokenSignatureValidator validator = new TokenSignatureValidator(loader, counter,
                new SignatureTemplateManager(new SignatureAlgorithmPreferences()));
        int afterInit = moduleDispatcher.getRequestCount();
        DecodedJwt forgedToken = tokenWithKid("unknown-kid");

        TokenValidationException refusal = assertThrows(TokenValidationException.class,
                () -> validator.validateSignature(forgedToken));
        drainExecutor();

        assertAll(
                () -> assertEquals(SecurityEventCounter.EventType.KEY_NOT_FOUND, refusal.getEventType()),
                () -> assertEquals(1, counter.getCount(SecurityEventCounter.EventType.KEY_NOT_FOUND)),
                () -> assertEquals(afterInit, moduleDispatcher.getRequestCount(), "the refusal fetches nothing"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                JWTValidationLogMessages.WARN.KEY_NOT_FOUND.resolveIdentifierString());
        loader.close();
    }

    @Test
    @DisplayName("Control: with the window configured, the same refusal holds and a fetch reaches the server")
    void shouldRefuseUnknownKidAndFetchWithWindow(URIBuilder uriBuilder) throws Exception {
        HttpJwksLoader loader = initializedLoader(configBuilder(uriBuilder).unknownKidRefreshWindow(WINDOW).build(),
                LoaderStatus.OK);
        SecurityEventCounter counter = new SecurityEventCounter();
        TokenSignatureValidator validator = new TokenSignatureValidator(loader, counter,
                new SignatureTemplateManager(new SignatureAlgorithmPreferences()));
        int afterInit = moduleDispatcher.getRequestCount();
        DecodedJwt forgedToken = tokenWithKid("unknown-kid");

        TokenValidationException refusal = assertThrows(TokenValidationException.class,
                () -> validator.validateSignature(forgedToken));
        drainExecutor();

        assertAll(
                () -> assertEquals(SecurityEventCounter.EventType.KEY_NOT_FOUND, refusal.getEventType()),
                () -> assertEquals(1, counter.getCount(SecurityEventCounter.EventType.KEY_NOT_FOUND)),
                () -> assertEquals(afterInit + 1, moduleDispatcher.getRequestCount(),
                        "the miss schedules one background fetch"));
        loader.close();
    }

    /**
     * Single-thread scheduler that counts every task handed to it through {@code execute}, {@code submit}
     * or a one-shot {@code schedule} (all of which route through {@link #schedule(Runnable, long, TimeUnit)}).
     * The count is taken synchronously on the caller's thread, so it is exact at any point, unlike the
     * approximate {@link #getTaskCount()}.
     */
    private static final class CountingScheduler extends ScheduledThreadPoolExecutor {

        private final AtomicInteger submissions = new AtomicInteger();

        CountingScheduler() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            submissions.incrementAndGet();
            return super.schedule(command, delay, unit);
        }

        int getSubmissions() {
            return submissions.get();
        }
    }
}
