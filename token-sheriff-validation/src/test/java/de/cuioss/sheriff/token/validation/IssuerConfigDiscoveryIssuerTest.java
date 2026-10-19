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

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.validation.jwks.JwksLoader;
import de.cuioss.sheriff.token.validation.test.dispatcher.JwksResolveDispatcher;
import de.cuioss.sheriff.token.validation.test.dispatcher.WellKnownDispatcher;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.dispatcher.CombinedDispatcher;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the discovery issuer check of an {@link IssuerConfig} that states its issuer
 * identifier only once, on the {@code IssuerConfig} itself.
 * <p>
 * Before the fix, such a configuration built a well-known loader that never compared the issuer
 * advertised by the discovery document, so a tampered discovery document could steer key loading. The
 * loader is therefore built through {@link IssuerConfig} here, never directly, with no identifier on the
 * {@link HttpJwksLoaderConfig}.
 * <p>
 * Supports requirement <a href="../../../../../../../../../doc/validation/requirements.adoc#VALIDATION-3.1">VALIDATION-3.1: Issuer Configuration</a>.
 */
@EnableTestLogger
@DisplayName("IssuerConfig compares the advertised discovery issuer with its own identifier")
class IssuerConfigDiscoveryIssuerTest {

    private final WellKnownDispatcher wellKnownDispatcher = new WellKnownDispatcher();
    private final JwksResolveDispatcher jwksDispatcher = new JwksResolveDispatcher();

    private MockWebServer server;
    private SecurityEventCounter securityEventCounter;
    private String wellKnownUrl;
    private String advertisedIssuer;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new CombinedDispatcher(wellKnownDispatcher, jwksDispatcher));
        server.start();
        wellKnownUrl = server.url(WellKnownDispatcher.LOCAL_PATH).toString();
        // WellKnownDispatcher advertises the request URL without the discovery path as issuer
        advertisedIssuer = wellKnownUrl.substring(0, wellKnownUrl.indexOf(WellKnownDispatcher.LOCAL_PATH));
        securityEventCounter = new SecurityEventCounter();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    @DisplayName("Should load no keys and never fetch jwks_uri when the discovery document advertises another issuer")
    void shouldRejectMismatchingAdvertisedIssuer() {
        wellKnownDispatcher.returnInvalidIssuer();
        JwksLoader loader = buildLoaderWithIdentifierOnlyOnIssuerConfig(advertisedIssuer);

        LoaderStatus status = loader.initJWKSLoader(securityEventCounter).join();

        assertAll("the discovery issuer check runs although the identifier is set only on IssuerConfig",
                () -> assertEquals(LoaderStatus.ERROR, status),
                () -> assertTrue(loader.getKeyIds().isEmpty(), "no keys may be loaded"),
                () -> assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.ISSUER_MISMATCH),
                        "the mismatch must be counted exactly once"),
                () -> assertEquals(1, wellKnownDispatcher.getCallCounter(), "the discovery document must be fetched"),
                () -> assertEquals(0, jwksDispatcher.getCallCounter(), "the advertised jwks_uri must never be fetched"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                JWTValidationLogMessages.WARN.ISSUER_MISMATCH.resolveIdentifierString());
    }

    @Test
    @DisplayName("Should load keys when the discovery document advertises the configured issuer")
    void shouldLoadKeysForMatchingAdvertisedIssuer() {
        wellKnownDispatcher.returnDefault();
        JwksLoader loader = buildLoaderWithIdentifierOnlyOnIssuerConfig(advertisedIssuer);

        LoaderStatus status = loader.initJWKSLoader(securityEventCounter).join();

        assertAll("a matching advertised issuer loads keys",
                () -> assertEquals(LoaderStatus.OK, status),
                () -> assertFalse(loader.getKeyIds().isEmpty(), "keys must be loaded"),
                () -> assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.ISSUER_MISMATCH)),
                () -> assertEquals(1, jwksDispatcher.getCallCounter(), "the advertised jwks_uri must be fetched"));
    }

    @Test
    @DisplayName("Should load keys when the configured identifier differs from the advertised issuer only by a trailing slash")
    void shouldTolerateTrailingSlashDifference() {
        wellKnownDispatcher.returnDefault();
        JwksLoader loader = buildLoaderWithIdentifierOnlyOnIssuerConfig(advertisedIssuer + "/");

        LoaderStatus status = loader.initJWKSLoader(securityEventCounter).join();

        assertAll("a trailing-slash difference is not a mismatch",
                () -> assertEquals(LoaderStatus.OK, status),
                () -> assertFalse(loader.getKeyIds().isEmpty(), "keys must be loaded"),
                () -> assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.ISSUER_MISMATCH)));
    }

    private JwksLoader buildLoaderWithIdentifierOnlyOnIssuerConfig(String issuerIdentifier) {
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier(issuerIdentifier)
                .audienceValidationDisabled(true)
                .httpJwksLoaderConfig(HttpJwksLoaderConfig.builder()
                        .wellKnownUrl(wellKnownUrl)
                        .allowInsecureHttp(true)
                        .allowLoopbackEgress(true)
                        .refreshIntervalSeconds(0)
                        .build())
                .build();
        JwksLoader loader = issuerConfig.getJwksLoader();
        assertNotNull(loader, "IssuerConfig must build a JWKS loader");
        return loader;
    }
}
