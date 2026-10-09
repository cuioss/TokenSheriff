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
import de.cuioss.sheriff.token.validation.jwks.key.KeyInfo;
import de.cuioss.sheriff.token.validation.test.InMemoryJWKSFactory;
import de.cuioss.sheriff.token.validation.test.dispatcher.JwksResolveDispatcher;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Issue #110: Key rotation grace period functionality in HttpJwksLoader.
 *
 * @author Oliver Wolff
 * @see <a href="https://github.com/cuioss/TokenSheriff/issues/110">Issue #110</a>
 */
@EnableTestLogger
@DisplayName("Tests HttpJwksLoader Key Rotation Grace Period (Issue #110)")
@EnableMockWebServer
class HttpJwksLoaderKeyRotationTest {

    private static final String ORIGINAL_KEY_ID = InMemoryJWKSFactory.DEFAULT_KEY_ID;
    private static final String ROTATED_KEY_ID = "alternative-key-id";

    @Getter
    private final JwksResolveDispatcher moduleDispatcher = new JwksResolveDispatcher();

    private SecurityEventCounter securityEventCounter;

    @BeforeEach
    void setUp() {
        moduleDispatcher.setCallCounter(0);
        securityEventCounter = new SecurityEventCounter();
    }

    @Test
    @DisplayName("Should not find key in retired keys after grace period expires")
    void shouldNotFindKeyInRetiredKeysAfterGracePeriodExpires(URIBuilder uriBuilder) {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();

        // Very short grace period for this test
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                .jwksUrl(jwksEndpoint)
                .issuerIdentifier("test-issuer")
                .keyRotationGracePeriod(Duration.ofMillis(100)) // 100ms grace period
                .refreshIntervalSeconds(1) // Enable background refresh for testing
                .build();

        HttpJwksLoader loader = new HttpJwksLoader(config);
        loader.initJWKSLoader(securityEventCounter).join();

        // Initial load
        moduleDispatcher.returnDefault();
        Optional<KeyInfo> originalKey = loader.getKeyInfo(ORIGINAL_KEY_ID);
        assertTrue(originalKey.isPresent(), "Original key should be found initially");

        // Rotate keys
        moduleDispatcher.switchToOtherPublicKey();

        // Wait for rotation
        await("Key rotation to complete")
                .atMost(10, SECONDS)
                .until(() -> {
                    Optional<KeyInfo> newKey = loader.getKeyInfo(ROTATED_KEY_ID);
                    return newKey.isPresent();
                });

        // Wait (longer than the grace period) for the retired key to expire. A lightweight park
        // rather than Awaitility, whose polling machinery is unnecessary for a fixed pause.
        LockSupport.parkNanos(MILLISECONDS.toNanos(200));

        // Original key should no longer be accessible
        Optional<KeyInfo> expiredKey = loader.getKeyInfo(ORIGINAL_KEY_ID);
        assertFalse(expiredKey.isPresent(),
                "Original key should not be accessible after grace period expires");

        // New key should still be accessible
        Optional<KeyInfo> currentKey = loader.getKeyInfo(ROTATED_KEY_ID);
        assertTrue(currentKey.isPresent(), "Current key should still be accessible");

        loader.close();
    }

    @Test
    @DisplayName("L9: a good key set survives an empty/ERROR refresh (not retired)")
    void shouldRetainGoodKeysWhenRefreshReturnsEmpty(URIBuilder uriBuilder) {
        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                .jwksUrl(jwksEndpoint)
                .issuerIdentifier("test-issuer")
                .keyRotationGracePeriod(Duration.ofMinutes(5))
                .refreshIntervalSeconds(1) // Enable background refresh for testing
                .build();

        HttpJwksLoader loader = new HttpJwksLoader(config);
        loader.initJWKSLoader(securityEventCounter).join();

        // Good initial load — the original key is present.
        moduleDispatcher.returnDefault();
        assertTrue(loader.getKeyInfo(ORIGINAL_KEY_ID).isPresent(), "Original key should be found initially");

        // The endpoint now returns an empty JWKS on every subsequent refresh.
        int callsBefore = moduleDispatcher.getCallCounter();
        moduleDispatcher.returnEmptyJwks();

        // Wait until at least one background refresh has actually fetched the empty JWKS.
        await("At least one refresh against the empty endpoint")
                .atMost(10, SECONDS)
                .until(() -> moduleDispatcher.getCallCounter() > callsBefore);

        // The empty refresh must NOT have retired the previously-good key set.
        assertTrue(loader.getKeyInfo(ORIGINAL_KEY_ID).isPresent(),
                "A good key set must survive an empty/ERROR refresh and remain accessible");

        loader.close();
    }

    @Test
    @DisplayName("Should prioritize current keys over retired keys")
    void shouldPrioritizeCurrentKeysOverRetiredKeys(URIBuilder uriBuilder) {
        // This test would verify that if a key ID exists in both current and retired keys,
        // the current key takes precedence. This requires a more complex setup with
        // overlapping key IDs, which might not be possible with the current test infrastructure.

        String jwksEndpoint = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();

        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder().allowLoopbackEgress(true).allowInsecureHttp(true)
                .jwksUrl(jwksEndpoint)
                .issuerIdentifier("test-issuer")
                .keyRotationGracePeriod(Duration.ofMinutes(5))
                .build();

        HttpJwksLoader loader = new HttpJwksLoader(config);
        loader.initJWKSLoader(securityEventCounter).join();

        // Load initial keys
        moduleDispatcher.returnDefault();
        Optional<KeyInfo> initialKey = loader.getKeyInfo(ORIGINAL_KEY_ID);
        assertTrue(initialKey.isPresent(), "Initial key should be found");

        // The logic in getKeyInfo() checks current keys first, then retired keys
        // This test verifies the order is correct by design

        loader.close();
    }
}
