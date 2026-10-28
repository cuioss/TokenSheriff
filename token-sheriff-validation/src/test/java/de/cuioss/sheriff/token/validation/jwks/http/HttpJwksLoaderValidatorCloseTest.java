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
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.commons.transport.JwksType;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.jwks.JwksLoader;
import de.cuioss.sheriff.token.validation.jwks.key.KeyInfo;
import de.cuioss.sheriff.token.validation.test.dispatcher.JwksResolveDispatcher;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link TokenValidator#close()} releases the JWKS loaders of its issuer configurations:
 * the background refresh of an {@link HttpJwksLoader} stops, and a loader that is not
 * {@link AutoCloseable} does not break the close. A loader failing to close — by throwing, or by being
 * interrupted — neither breaks the close nor stops the remaining loaders.
 * <p>
 * Lives in the {@code jwks.http} package to read the package-private
 * {@link HttpJwksLoader#isBackgroundRefreshActive()}.
 */
@EnableTestLogger
@EnableMockWebServer
@DisplayName("TokenValidator.close() releases the JWKS loaders")
class HttpJwksLoaderValidatorCloseTest {

    private static final String HTTP_ISSUER = "https://http-issuer.example.com";
    private static final String STUB_ISSUER = "https://stub-issuer.example.com";

    @Getter
    private final JwksResolveDispatcher moduleDispatcher = new JwksResolveDispatcher();

    @BeforeEach
    void setUp() {
        moduleDispatcher.setCallCounter(0);
        moduleDispatcher.returnDefault();
    }

    @Test
    @DisplayName("Closing the validator stops the background refresh and resets the loader")
    void closeShouldStopBackgroundRefreshOfHttpLoader(URIBuilder uriBuilder) {
        HttpJwksLoader httpLoader = new HttpJwksLoader(HttpJwksLoaderConfig.builder()
                .allowLoopbackEgress(true).allowInsecureHttp(true)
                .jwksUrl(uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString())
                .issuerIdentifier(HTTP_ISSUER)
                .refreshIntervalSeconds(60)
                .build());
        TokenValidator validator = TokenValidator.builder()
                .issuerConfig(IssuerConfig.builder()
                        .issuerIdentifier(HTTP_ISSUER)
                        .expectedAudience("test-audience")
                        .jwksLoader(httpLoader)
                        .build())
                .issuerConfig(IssuerConfig.builder()
                        .issuerIdentifier(STUB_ISSUER)
                        .expectedAudience("test-audience")
                        .jwksLoader(new NonCloseableLoader())
                        .build())
                .build();

        // Idempotent: returns the initialization the validator started, or starts it
        assertEquals(LoaderStatus.OK, httpLoader.initJWKSLoader(new SecurityEventCounter()).join());
        assertTrue(httpLoader.isBackgroundRefreshActive(), "the background refresh must run before close()");

        assertDoesNotThrow(validator::close, "a loader that is not AutoCloseable must not break close()");

        assertAll("the validator owns and releases its JWKS loaders",
                () -> assertFalse(httpLoader.isBackgroundRefreshActive(), "the background refresh must be stopped"),
                () -> assertEquals(LoaderStatus.UNDEFINED, httpLoader.getLoaderStatus()),
                () -> assertTrue(httpLoader.getKeyIds().isEmpty(), "no key may remain installed"));
    }

    @Test
    @DisplayName("A loader whose close() and accessors throw does not stop the remaining loaders")
    void closeShouldContinueWhenLoaderCloseAndAccessorThrow() {
        RecordingCloseableLoader recordingLoader = new RecordingCloseableLoader(STUB_ISSUER);
        TokenValidator validator = TokenValidator.builder()
                .issuerConfig(IssuerConfig.builder()
                        .issuerIdentifier(HTTP_ISSUER)
                        .expectedAudience("test-audience")
                        .jwksLoader(new FailingCloseableLoader(HTTP_ISSUER, new IllegalStateException("close failed"), true))
                        .build())
                .issuerConfig(IssuerConfig.builder()
                        .issuerIdentifier(STUB_ISSUER)
                        .expectedAudience("test-audience")
                        .jwksLoader(recordingLoader)
                        .build())
                .build();

        assertDoesNotThrow(validator::close, "a loader failing to close must not break close()");

        assertTrue(recordingLoader.isClosed(), "the loader after the failing one must still be closed");
    }

    @Test
    @DisplayName("An interrupted loader close restores the interrupt flag and does not stop the remaining loaders")
    void closeShouldRestoreInterruptFlagAndContinue() {
        RecordingCloseableLoader recordingLoader = new RecordingCloseableLoader(STUB_ISSUER);
        TokenValidator validator = TokenValidator.builder()
                .issuerConfig(IssuerConfig.builder()
                        .issuerIdentifier(HTTP_ISSUER)
                        .expectedAudience("test-audience")
                        .jwksLoader(new FailingCloseableLoader(HTTP_ISSUER, new InterruptedException("close interrupted"), false))
                        .build())
                .issuerConfig(IssuerConfig.builder()
                        .issuerIdentifier(STUB_ISSUER)
                        .expectedAudience("test-audience")
                        .jwksLoader(recordingLoader)
                        .build())
                .build();

        try {
            assertDoesNotThrow(validator::close, "an interrupted loader close must not break close()");

            assertAll("the interrupt is preserved and the remaining loaders are closed",
                    () -> assertTrue(Thread.currentThread().isInterrupted(), "the interrupt flag must be restored"),
                    () -> assertTrue(recordingLoader.isClosed(), "the loader after the interrupted one must still be closed"));
        } finally {
            // Clear the flag so it does not leak into other tests on this thread
            Thread.interrupted();
        }
    }

    /**
     * An {@link AutoCloseable} JWKS loader whose {@code close()} throws the given exception. When
     * {@code accessorFailsAfterClose} is set, {@link #getIssuerIdentifier()} throws once {@code close()}
     * was called, modelling a custom loader whose accessors fail after it was shut down.
     */
    private static final class FailingCloseableLoader extends NonCloseableLoader implements AutoCloseable {

        private final Exception closeFailure;
        private final boolean accessorFailsAfterClose;
        private volatile boolean closeCalled;

        FailingCloseableLoader(String issuer, Exception closeFailure, boolean accessorFailsAfterClose) {
            super(issuer);
            this.closeFailure = closeFailure;
            this.accessorFailsAfterClose = accessorFailsAfterClose;
        }

        @Override
        public Optional<String> getIssuerIdentifier() {
            if (accessorFailsAfterClose && closeCalled) {
                throw new IllegalStateException("accessor fails after close");
            }
            return super.getIssuerIdentifier();
        }

        @Override
        public void close() throws Exception {
            closeCalled = true;
            throw closeFailure;
        }
    }

    /**
     * An {@link AutoCloseable} JWKS loader that records whether it was closed.
     */
    private static final class RecordingCloseableLoader extends NonCloseableLoader implements AutoCloseable {

        @Getter
        private volatile boolean closed;

        RecordingCloseableLoader(String issuer) {
            super(issuer);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /**
     * A JWKS loader that is not {@link AutoCloseable}, always reporting {@link LoaderStatus#OK}.
     */
    private static class NonCloseableLoader implements JwksLoader {

        private final String issuer;

        NonCloseableLoader() {
            this(STUB_ISSUER);
        }

        NonCloseableLoader(String issuer) {
            this.issuer = issuer;
        }

        @Override
        public LoaderStatus getLoaderStatus() {
            return LoaderStatus.OK;
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
            return CompletableFuture.completedFuture(LoaderStatus.OK);
        }
    }
}
