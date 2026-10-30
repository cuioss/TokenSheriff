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
package de.cuioss.sheriff.token.validation;

import de.cuioss.http.client.adapter.RetryConfig;
import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.commons.transport.JwksType;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.jwks.JwksLoader;
import de.cuioss.sheriff.token.validation.jwks.http.HttpJwksLoader;
import de.cuioss.sheriff.token.validation.jwks.key.KeyInfo;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.test.dispatcher.JwksResolveDispatcher;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.dispatcher.CombinedDispatcher;
import io.jsonwebtoken.Jwts;
import mockwebserver3.MockWebServer;
import org.awaitility.Awaitility;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that an issuer whose initial JWKS load fails becomes resolvable again
 * once its loader recovers (e.g. through background refresh), instead of being
 * permanently unavailable until process restart.
 * <p>
 * It also pins the user-visible acceptance policy of the opt-in maximum key staleness: with the bound
 * configured, an issuer stops being admitted once its keys go unrefreshed for longer than the bound and is
 * re-admitted after the next successful refresh; without it, the issuer stays admitted through the outage.
 */
@EnableTestLogger
class IssuerConfigCacheRecoveryTest {

    private static final String ISSUER = "https://recovering-issuer.example.com";
    private static final String AUDIENCE = "test-audience";

    /** Staleness bound shorter than the one-second refresh interval, so the first failed cycle exceeds it. */
    private static final Duration STALENESS_BOUND = Duration.ofMillis(500);

    /** Outage length for the default-availability test: the bound plus two refresh intervals. */
    private static final Duration OUTAGE_DURATION = STALENESS_BOUND.plusSeconds(2);

    /**
     * Minimal JwksLoader stub whose status can be flipped externally, simulating
     * a loader that fails its initial load but recovers via background refresh.
     */
    private static final class StatusControlledLoader implements JwksLoader {

        private final AtomicReference<LoaderStatus> status = new AtomicReference<>(LoaderStatus.UNDEFINED);

        void setStatus(LoaderStatus newStatus) {
            status.set(newStatus);
        }

        @Override
        public LoaderStatus getLoaderStatus() {
            return status.get();
        }

        @Override
        public Optional<KeyInfo> getKeyInfo(String kid) {
            return Optional.empty();
        }

        @Override
        public Set<String> getKeyIds() {
            return Set.of();
        }

        @Override
        public JwksType getJwksType() {
            return JwksType.HTTP;
        }

        @Override
        public Optional<String> getIssuerIdentifier() {
            return Optional.of(ISSUER);
        }

        @Override
        public CompletableFuture<LoaderStatus> initJWKSLoader(SecurityEventCounter securityEventCounter) {
            // Simulates a failed initial load: status stays non-OK, background refresh may recover later
            return CompletableFuture.completedFuture(status.get());
        }
    }

    @Test
    @DisplayName("Issuer with failed initial load is resolvable after loader recovers")
    void recoversIssuerAfterLoaderBecomesHealthy() {
        StatusControlledLoader loader = new StatusControlledLoader();
        IssuerConfig config = IssuerConfig.builder()
                .issuerIdentifier(ISSUER)
                .expectedAudience("test-audience")
                .jwksLoader(loader)
                .build();

        IssuerConfigCache cache = new IssuerConfigCache(List.of(config), new SecurityEventCounter());

        // Initial load failed: resolution must fail closed
        assertThrows(TokenValidationException.class, () -> cache.resolveConfig(ISSUER));

        // Background refresh succeeds: the issuer must become resolvable without restart
        loader.setStatus(LoaderStatus.OK);
        IssuerConfig resolved = cache.resolveConfig(ISSUER);
        assertSame(config, resolved);

        // And it stays resolvable through the regular cache path
        assertSame(config, cache.resolveConfig(ISSUER));
    }

    @Test
    @DisplayName("Token validates without restart once a real HttpJwksLoader recovers from a failed first load")
    void recoversIssuerThroughRealHttpJwksLoader() throws Exception {
        JwksResolveDispatcher jwksDispatcher = new JwksResolveDispatcher();
        jwksDispatcher.returnError();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new CombinedDispatcher(jwksDispatcher));
            server.start();
            HttpJwksLoader loader = new HttpJwksLoader(HttpJwksLoaderConfig.builder()
                    .allowLoopbackEgress(true).allowInsecureHttp(true)
                    .jwksUrl(server.url(JwksResolveDispatcher.LOCAL_PATH).toString())
                    .issuerIdentifier(ISSUER)
                    .refreshIntervalSeconds(1)
                    .retryConfig(RetryConfig.builder()
                            .initialDelay(Duration.ofMillis(1))
                            .maxDelay(Duration.ofMillis(5))
                            .build())
                    .build());
            String token = signedAccessToken();

            try (TokenValidator validator = TokenValidator.builder()
                         .issuerConfig(IssuerConfig.builder()
                                 .issuerIdentifier(ISSUER)
                                 .expectedAudience(AUDIENCE)
                                 .jwksLoader(loader)
                                 .build())
                         .build()) {
                AccessTokenRequest request = AccessTokenRequest.of(token);
                assertThrows(TokenValidationException.class, () -> validator.createAccessToken(request),
                        "the token must be rejected while the IdP serves no keys");

                jwksDispatcher.returnDefault();
                Awaitility.await("the background refresh brings the loader to OK")
                        .atMost(Duration.ofSeconds(5))
                        .until(() -> loader.getLoaderStatus() == LoaderStatus.OK);

                AccessTokenContent content = validator.createAccessToken(request);
                assertEquals(ISSUER, content.getIssuer(), "the same token must validate after recovery");
            }
        }
    }

    /**
     * Creates an access token for {@link #ISSUER} and {@link #AUDIENCE}, signed with the module's default
     * test key, which the JWKS endpoint serves once it recovers.
     */
    private static String signedAccessToken() {
        return Jwts.builder()
                .header().keyId(InMemoryKeyMaterialHandler.DEFAULT_KEY_ID).and()
                .issuer(ISSUER)
                .subject("recovery-subject")
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(InMemoryKeyMaterialHandler.getDefaultPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Builds a direct-mode loader against the given server with a one-second refresh interval and, when
     * {@code maxStaleness} is non-null, that staleness bound.
     */
    private static HttpJwksLoader directLoader(MockWebServer server, @Nullable Duration maxStaleness) {
        HttpJwksLoaderConfig.HttpJwksLoaderConfigBuilder builder = HttpJwksLoaderConfig.builder()
                .allowLoopbackEgress(true).allowInsecureHttp(true)
                .jwksUrl(server.url(JwksResolveDispatcher.LOCAL_PATH).toString())
                .issuerIdentifier(ISSUER)
                .refreshIntervalSeconds(1)
                .retryConfig(RetryConfig.builder()
                        .initialDelay(Duration.ofMillis(1))
                        .maxDelay(Duration.ofMillis(5))
                        .build());
        if (maxStaleness != null) {
            builder.maxStaleness(maxStaleness);
        }
        return new HttpJwksLoader(builder.build());
    }

    private static IssuerConfig issuerConfig(HttpJwksLoader loader) {
        return IssuerConfig.builder()
                .issuerIdentifier(ISSUER)
                .expectedAudience(AUDIENCE)
                .jwksLoader(loader)
                .build();
    }

    @Test
    @DisplayName("A configured staleness bound stops admitting the issuer during an outage and re-admits it on recovery")
    void stalenessBoundRejectsIssuerUntilRefreshSucceeds() throws Exception {
        JwksResolveDispatcher jwksDispatcher = new JwksResolveDispatcher();
        jwksDispatcher.returnDefault();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new CombinedDispatcher(jwksDispatcher));
            server.start();
            try (HttpJwksLoader loader = directLoader(server, STALENESS_BOUND)) {
                IssuerConfig config = issuerConfig(loader);
                IssuerConfigCache cache = new IssuerConfigCache(List.of(config), new SecurityEventCounter());
                Awaitility.await("the issuer resolves while its keys are fresh")
                        .atMost(Duration.ofSeconds(5)).ignoreExceptions()
                        .until(() -> cache.resolveConfig(ISSUER) == config);

                jwksDispatcher.returnError();
                Awaitility.await("a failed refresh past the bound degrades the loader")
                        .atMost(Duration.ofSeconds(10))
                        .until(() -> loader.getLoaderStatus() == LoaderStatus.UNDEFINED);

                TokenValidationException rejected = assertThrows(TokenValidationException.class,
                        () -> cache.resolveConfig(ISSUER), "a stale issuer must not be admitted");
                assertEquals(SecurityEventCounter.EventType.NO_ISSUER_CONFIG, rejected.getEventType());
                assertTrue(loader.getKeyInfo(InMemoryKeyMaterialHandler.DEFAULT_KEY_ID).isPresent(),
                        "the degradation drops no key");

                jwksDispatcher.returnDefault();
                Awaitility.await("a successful refresh restores OK")
                        .atMost(Duration.ofSeconds(10))
                        .until(() -> loader.getLoaderStatus() == LoaderStatus.OK);
                assertSame(config, cache.resolveConfig(ISSUER), "the recovered issuer is re-admitted");
            }
        }
    }

    @Test
    @DisplayName("Without a staleness bound the issuer stays admitted through the same outage")
    void withoutStalenessBoundIssuerStaysResolvableThroughOutage() throws Exception {
        JwksResolveDispatcher jwksDispatcher = new JwksResolveDispatcher();
        jwksDispatcher.returnDefault();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new CombinedDispatcher(jwksDispatcher));
            server.start();
            try (HttpJwksLoader loader = directLoader(server, null)) {
                IssuerConfig config = issuerConfig(loader);
                IssuerConfigCache cache = new IssuerConfigCache(List.of(config), new SecurityEventCounter());
                Awaitility.await("the issuer resolves while its keys are fresh")
                        .atMost(Duration.ofSeconds(5)).ignoreExceptions()
                        .until(() -> cache.resolveConfig(ISSUER) == config);
                Instant outageStart = Instant.now();

                jwksDispatcher.returnError();
                // With one cycle per second, an outage of OUTAGE_DURATION contains a completed failed cycle
                // that started after STALENESS_BOUND had elapsed, i.e. one the bound would have degraded on.
                Awaitility.await("the outage outlasts the staleness bound by more than a refresh interval")
                        .pollDelay(OUTAGE_DURATION).atMost(OUTAGE_DURATION.plusSeconds(5))
                        .until(() -> Duration.between(outageStart, Instant.now()).compareTo(OUTAGE_DURATION) >= 0);
                LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                        JWTValidationLogMessages.WARN.BACKGROUND_REFRESH_FAILED.resolveIdentifierString());

                assertAll("the default keeps the issuer available",
                        () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus()),
                        () -> assertSame(config, cache.resolveConfig(ISSUER)));
            }
        }
    }

    @Test
    @DisplayName("Unknown issuer still fails after recovery path")
    void unknownIssuerStillFails() {
        StatusControlledLoader loader = new StatusControlledLoader();
        loader.setStatus(LoaderStatus.OK);
        IssuerConfig config = IssuerConfig.builder()
                .issuerIdentifier(ISSUER)
                .expectedAudience("test-audience")
                .jwksLoader(loader)
                .build();

        IssuerConfigCache cache = new IssuerConfigCache(List.of(config), new SecurityEventCounter());

        assertThrows(TokenValidationException.class, () -> cache.resolveConfig("https://unknown.example.com"));
    }
}
