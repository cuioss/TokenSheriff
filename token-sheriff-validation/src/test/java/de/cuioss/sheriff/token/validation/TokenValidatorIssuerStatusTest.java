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
import de.cuioss.sheriff.token.validation.jwks.JwksLoader;
import de.cuioss.sheriff.token.validation.jwks.http.HttpJwksLoader;
import de.cuioss.sheriff.token.validation.jwks.key.KeyInfo;
import de.cuioss.sheriff.token.validation.test.dispatcher.JwksResolveDispatcher;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.Closeable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link TokenValidator#getIssuerStatuses()} and the {@link IssuerStatusView} it returns: the
 * views list the enabled issuers in configuration order as an immutable list, expose no way to close or
 * reach a JWKS loader, read the current loader status on every call without fetching keys, and leave
 * disabled issuers out.
 */
@EnableTestLogger
@EnableMockWebServer
@DisplayName("TokenValidator.getIssuerStatuses() offers a read-only, non-fetching issuer view")
class TokenValidatorIssuerStatusTest {

    private static final String FIRST_ISSUER = "https://first-issuer.example.com";
    private static final String SECOND_ISSUER = "https://second-issuer.example.com";
    private static final String THIRD_ISSUER = "https://third-issuer.example.com";
    private static final String DISABLED_ISSUER = "https://disabled-issuer.example.com";
    private static final String HTTP_ISSUER = "https://http-issuer.example.com";
    private static final String AUDIENCE = "test-audience";

    /** Number of status reads the non-fetching assertion performs. */
    private static final int STATUS_READS = 50;

    @Getter
    private final JwksResolveDispatcher moduleDispatcher = new JwksResolveDispatcher();

    @BeforeEach
    void setUp() {
        moduleDispatcher.setCallCounter(0);
        moduleDispatcher.returnDefault();
    }

    @Test
    @DisplayName("The views list the enabled issuers in configuration order, leaving disabled issuers out")
    void shouldListEnabledIssuersInConfigurationOrder() {
        try (TokenValidator validator = TokenValidator.builder()
                .issuerConfig(stubIssuerConfig(new StatusControlledLoader(FIRST_ISSUER, LoaderStatus.OK)))
                .issuerConfig(IssuerConfig.builder().enabled(false).issuerIdentifier(DISABLED_ISSUER).build())
                .issuerConfig(stubIssuerConfig(new StatusControlledLoader(SECOND_ISSUER, LoaderStatus.OK)))
                .issuerConfig(stubIssuerConfig(new StatusControlledLoader(THIRD_ISSUER, LoaderStatus.OK)))
                .build()) {

            List<String> issuers = validator.getIssuerStatuses().stream()
                    .map(IssuerStatusView::issuerIdentifier)
                    .toList();

            assertEquals(List.of(FIRST_ISSUER, SECOND_ISSUER, THIRD_ISSUER), issuers,
                    "enabled issuers in configuration order, the disabled issuer absent");
            assertFalse(issuers.contains(DISABLED_ISSUER), "a disabled issuer must not be listed");
            assertEquals(issuers, validator.getIssuerStatuses().stream().map(IssuerStatusView::issuerIdentifier).toList(),
                    "repeated calls return the same order");
        }
    }

    @Test
    @DisplayName("The returned list is immutable")
    void shouldReturnImmutableList() {
        try (TokenValidator validator = TokenValidator.builder()
                .issuerConfig(stubIssuerConfig(new StatusControlledLoader(FIRST_ISSUER, LoaderStatus.OK)))
                .build()) {
            List<IssuerStatusView> statuses = validator.getIssuerStatuses();
            IssuerStatusView view = statuses.getFirst();

            assertAll("no mutation of the returned list is possible",
                    () -> assertThrows(UnsupportedOperationException.class, () -> statuses.add(view)),
                    () -> assertThrows(UnsupportedOperationException.class, () -> statuses.remove(view)),
                    () -> assertThrows(UnsupportedOperationException.class, () -> statuses.set(0, view)),
                    () -> assertThrows(UnsupportedOperationException.class, statuses::clear));
            assertEquals(1, validator.getIssuerStatuses().size(), "the validator's views stay unchanged");
        }
    }

    @Test
    @DisplayName("The view names the issuer and its JWKS type and exposes neither loader nor configuration")
    void shouldExposeNoLoaderOrConfiguration() {
        StatusControlledLoader loader = new StatusControlledLoader(FIRST_ISSUER, LoaderStatus.OK);
        try (TokenValidator validator = TokenValidator.builder()
                .issuerConfig(stubIssuerConfig(loader))
                .build()) {
            IssuerStatusView view = validator.getIssuerStatuses().getFirst();

            assertEquals(FIRST_ISSUER, view.issuerIdentifier());
            assertEquals(JwksType.MEMORY, view.jwksType());
            assertFalse(AutoCloseable.class.isAssignableFrom(IssuerStatusView.class),
                    "the view must not be closeable");
            assertFalse(Closeable.class.isAssignableFrom(IssuerStatusView.class),
                    "the view must not be closeable");

            List<Method> publicMethods = Arrays.stream(IssuerStatusView.class.getDeclaredMethods())
                    .filter(method -> Modifier.isPublic(method.getModifiers()))
                    .toList();
            assertAll("no public method hands out the loader or the configuration, or closes anything",
                    publicMethods.stream().map(method -> (Executable) () -> {
                        assertFalse(JwksLoader.class.isAssignableFrom(method.getReturnType()),
                                method.getName() + " must not return the JWKS loader");
                        assertFalse(IssuerConfig.class.isAssignableFrom(method.getReturnType()),
                                method.getName() + " must not return the issuer configuration");
                        assertNotEquals("close", method.getName(), "the view must not offer close()");
                    }));
            assertEquals(Set.of("issuerIdentifier", "jwksType", "loaderStatus", "toString"),
                    publicMethods.stream().map(Method::getName).collect(Collectors.toSet()),
                    "the view offers read accessors only");
            assertFalse(loader.isClosed(), "reading the view must not close the loader");
        }
        assertTrue(loader.isClosed(), "only TokenValidator.close() closes the loader");
    }

    @Test
    @DisplayName("The loader keeps refreshing after the caller drops the view; only close() stops it")
    void shouldKeepRefreshingAfterViewIsDroppedUntilClose(URIBuilder uriBuilder) {
        HttpJwksLoader httpLoader = new HttpJwksLoader(HttpJwksLoaderConfig.builder()
                .allowLoopbackEgress(true).allowInsecureHttp(true)
                .jwksUrl(uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString())
                .issuerIdentifier(HTTP_ISSUER)
                .refreshIntervalSeconds(1)
                .retryConfig(fastRetry())
                .build());
        TokenValidator validator = TokenValidator.builder()
                .issuerConfig(IssuerConfig.builder()
                        .issuerIdentifier(HTTP_ISSUER)
                        .expectedAudience(AUDIENCE)
                        .jwksLoader(httpLoader)
                        .build())
                .build();
        try {
            assertEquals(LoaderStatus.OK, httpLoader.initJWKSLoader(new SecurityEventCounter()).join());
            readAndDropViews(validator);

            int callsAfterDrop = moduleDispatcher.getCallCounter();
            Awaitility.await("the background refresh keeps fetching after the views were dropped")
                    .atMost(Duration.ofSeconds(10))
                    .until(() -> moduleDispatcher.getCallCounter() >= callsAfterDrop + 2);
            assertEquals(LoaderStatus.OK, httpLoader.getLoaderStatus(), "the loader stays active");
        } finally {
            validator.close();
        }

        assertEquals(LoaderStatus.UNDEFINED, httpLoader.getLoaderStatus(), "close() resets the loader");
        int callsAfterClose = moduleDispatcher.getCallCounter();
        Awaitility.await("no refresh happens after close()")
                .during(Duration.ofMillis(2500))
                .atMost(Duration.ofSeconds(5))
                .until(() -> moduleDispatcher.getCallCounter() == callsAfterClose);
    }

    @Test
    @DisplayName("Reading the status performs no JWKS request")
    void shouldNotFetchWhenReadingStatus(URIBuilder uriBuilder) {
        HttpJwksLoader httpLoader = new HttpJwksLoader(HttpJwksLoaderConfig.builder()
                .allowLoopbackEgress(true).allowInsecureHttp(true)
                .jwksUrl(uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString())
                .issuerIdentifier(HTTP_ISSUER)
                .refreshIntervalSeconds(3600)
                .build());
        try (TokenValidator validator = TokenValidator.builder()
                .issuerConfig(IssuerConfig.builder()
                        .issuerIdentifier(HTTP_ISSUER)
                        .expectedAudience(AUDIENCE)
                        .jwksLoader(httpLoader)
                        .build())
                .build()) {
            assertEquals(LoaderStatus.OK, httpLoader.initJWKSLoader(new SecurityEventCounter()).join());
            int callsBeforeReads = moduleDispatcher.getCallCounter();
            assertTrue(callsBeforeReads > 0, "the initial load must have reached the counting server");

            IssuerStatusView view = validator.getIssuerStatuses().getFirst();
            assertEquals(JwksType.HTTP, view.jwksType());
            for (int i = 0; i < STATUS_READS; i++) {
                assertEquals(LoaderStatus.OK, view.loaderStatus());
                assertEquals(LoaderStatus.OK, validator.getIssuerStatuses().getFirst().loaderStatus());
            }

            moduleDispatcher.assertCallsAnswered(callsBeforeReads);
        }
    }

    @Test
    @DisplayName("The view reflects OK, ERROR and UNDEFINED transitions of the loader")
    void shouldReflectStatusTransitions() {
        StatusControlledLoader loader = new StatusControlledLoader(FIRST_ISSUER, LoaderStatus.UNDEFINED);
        try (TokenValidator validator = TokenValidator.builder()
                .issuerConfig(stubIssuerConfig(loader))
                .build()) {
            IssuerStatusView view = validator.getIssuerStatuses().getFirst();
            assertEquals(LoaderStatus.UNDEFINED, view.loaderStatus());

            loader.setStatus(LoaderStatus.OK);
            assertEquals(LoaderStatus.OK, view.loaderStatus(), "the view reads the current status, not a snapshot");

            loader.setStatus(LoaderStatus.ERROR);
            assertEquals(LoaderStatus.ERROR, view.loaderStatus());

            loader.setStatus(LoaderStatus.UNDEFINED);
            assertEquals(LoaderStatus.UNDEFINED, view.loaderStatus());

            loader.setStatus(LoaderStatus.OK);
            assertEquals(LoaderStatus.OK, validator.getIssuerStatuses().getFirst().loaderStatus(),
                    "a freshly obtained view reads the same current status");
        }
    }

    /**
     * Reads every view once and lets them go out of scope, modelling a caller that drops the views.
     */
    private static void readAndDropViews(TokenValidator validator) {
        validator.getIssuerStatuses().forEach(view -> assertEquals(LoaderStatus.OK, view.loaderStatus()));
    }

    private static RetryConfig fastRetry() {
        return RetryConfig.builder()
                .initialDelay(Duration.ofMillis(1))
                .maxDelay(Duration.ofMillis(5))
                .build();
    }

    private static IssuerConfig stubIssuerConfig(StatusControlledLoader loader) {
        return IssuerConfig.builder()
                .issuerIdentifier(loader.getIssuerIdentifier().orElseThrow())
                .expectedAudience(AUDIENCE)
                .jwksLoader(loader)
                .build();
    }

    /**
     * An {@link AutoCloseable} in-memory JWKS loader whose status can be set externally and which records
     * whether it was closed.
     */
    private static final class StatusControlledLoader implements JwksLoader, AutoCloseable {

        private final String issuer;
        private final AtomicReference<LoaderStatus> status;
        @Getter
        private volatile boolean closed;

        StatusControlledLoader(String issuer, LoaderStatus initialStatus) {
            this.issuer = issuer;
            this.status = new AtomicReference<>(initialStatus);
        }

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
            return JwksType.MEMORY;
        }

        @Override
        public Optional<String> getIssuerIdentifier() {
            return Optional.of(issuer);
        }

        @Override
        public CompletableFuture<LoaderStatus> initJWKSLoader(SecurityEventCounter securityEventCounter) {
            return CompletableFuture.completedFuture(status.get());
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
