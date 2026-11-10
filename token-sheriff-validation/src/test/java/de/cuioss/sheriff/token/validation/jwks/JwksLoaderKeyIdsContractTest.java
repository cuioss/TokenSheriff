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
package de.cuioss.sheriff.token.validation.jwks;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.validation.jwks.JwksLoaderKeyIdsContractSupport.LoaderType;
import de.cuioss.sheriff.token.validation.jwks.key.KeyInfo;
import de.cuioss.sheriff.token.validation.test.dispatcher.JwksResolveDispatcher;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.TestLoggerFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static de.cuioss.sheriff.token.validation.JWTValidationLogMessages.WARN;
import static de.cuioss.sheriff.token.validation.jwks.JwksLoaderKeyIdsContractSupport.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Index of the {@link JwksLoader#getKeyIds()} contract, asserted through the {@link JwksLoaderFactory}
 * entry points only.
 * <p>
 * The contract, as documented on {@link JwksLoader#getKeyIds()}: the result is an immutable snapshot
 * taken at the moment of the call, later key loads or refreshes do not change a set already returned,
 * every id in it is resolved by {@link JwksLoader#getKeyInfo(String)} for the active key set, key sets
 * retired during a key-rotation grace period are not included, and the set is empty if no keys are loaded
 * yet and never {@code null}. Each point below has exactly one test method:
 * <ol type="a">
 *   <li>never {@code null}, empty before {@code initJWKSLoader} and after a load without a usable key</li>
 *   <li>exactly the active ids, each resolving through {@code getKeyInfo}</li>
 *   <li>a blank or whitespace-only {@code kid} is omitted</li>
 *   <li>a duplicate {@code kid} is reported once, the later key resolving through {@code getKeyInfo}</li>
 *   <li>the snapshot refuses {@code add} and {@code remove}</li>
 *   <li>a returned snapshot is unchanged by a later key rotation</li>
 *   <li>a retired key set's id is excluded while {@code getKeyInfo} still resolves it within the grace
 *       period, and is not resolved after it</li>
 *   <li>{@code getLoaderStatus()} is {@code OK} only while a usable key set is installed, and a refresh
 *       returning no usable key leaves the installed set in place</li>
 * </ol>
 * Points (a) to (e) hold for every loader type the factory returns; points (f) to (h) need a refresh and
 * are asserted through {@link JwksLoaderFactory#createHttpLoader} with its background refresh.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("JwksLoader.getKeyIds() contract through JwksLoaderFactory")
class JwksLoaderKeyIdsContractTest {

    private static final int REFRESH_INTERVAL_SECONDS = 1;
    private static final Duration GRACE_PERIOD = Duration.ofSeconds(3);
    private static final long AWAIT_SECONDS = 10;

    @Getter
    private final JwksResolveDispatcher moduleDispatcher = new JwksResolveDispatcher();

    @TempDir
    Path tempDir;

    private String jwksUrl;
    private SecurityEventCounter securityEventCounter;

    @BeforeEach
    void setUp(URIBuilder uriBuilder) {
        jwksUrl = uriBuilder.addPathSegment(JwksResolveDispatcher.LOCAL_PATH).buildAsString();
        moduleDispatcher.returnDefault();
        moduleDispatcher.setCallCounter(0);
        securityEventCounter = new SecurityEventCounter();
    }

    // Already pinned for JWKSKeyLoader by JWKSKeyLoaderTest ("Should return an empty set before
    // initialization") and for HttpJwksLoader by HttpJwksLoaderKeyRotationTest ("Should report no key ids
    // before the first load"); asserted here through the factory for every loader type.
    @ParameterizedTest(name = "{0}")
    @MethodSource("de.cuioss.sheriff.token.validation.jwks.JwksLoaderKeyIdsContractSupport#loaderTypes")
    @DisplayName("(a) never null, empty before initJWKSLoader and after a load without a usable key")
    void pointA_neverNullAndEmptyWithoutLoadedKeys(LoaderType type) {
        JwksLoader notInitialized = createLoader(type, twoKeyJwks(), 0);
        JwksLoader withoutUsableKey = createLoader(type, noUsableKeyJwks(), 0);
        try {
            Set<String> beforeInit = notInitialized.getKeyIds();
            withoutUsableKey.initJWKSLoader(securityEventCounter).join();
            Set<String> afterLoadWithoutUsableKey = withoutUsableKey.getKeyIds();

            assertAll("(a) for " + type,
                    () -> assertNotNull(beforeInit, "getKeyIds must never return null"),
                    () -> assertTrue(beforeInit.isEmpty(), "No key ids before initJWKSLoader"),
                    () -> assertNotNull(afterLoadWithoutUsableKey, "getKeyIds must never return null"),
                    () -> assertTrue(afterLoadWithoutUsableKey.isEmpty(),
                            "No key ids after a load that yielded no usable key"));
        } finally {
            release(notInitialized);
            release(withoutUsableKey);
        }
    }

    // Already pinned for JWKSKeyLoader by JWKSKeyLoaderTest ("Should report the key ids of all loaded keys").
    @ParameterizedTest(name = "{0}")
    @MethodSource("de.cuioss.sheriff.token.validation.jwks.JwksLoaderKeyIdsContractSupport#loaderTypes")
    @DisplayName("(b) exactly the active ids, each resolving through getKeyInfo")
    void pointB_exactlyTheActiveIdsEachResolving(LoaderType type) {
        JwksLoader loader = initializedLoader(type, twoKeyJwks());
        try {
            Set<String> keyIds = loader.getKeyIds();

            assertEquals(Set.of(FIRST_KID, SECOND_KID), keyIds, "Exactly the loaded key ids for " + type);
            assertTrue(keyIds.stream().allMatch(kid -> loader.getKeyInfo(kid).isPresent()),
                    "Every reported key id must resolve through getKeyInfo for " + type);
        } finally {
            release(loader);
        }
    }

    // Already pinned for JWKSKeyLoader by JWKSKeyLoaderTest ("Should omit a blank key id that getKeyInfo
    // cannot resolve").
    @ParameterizedTest(name = "{0}")
    @MethodSource("de.cuioss.sheriff.token.validation.jwks.JwksLoaderKeyIdsContractSupport#loaderTypes")
    @DisplayName("(c) a blank or whitespace-only kid is omitted")
    void pointC_blankKidOmitted(LoaderType type) {
        JwksLoader loader = initializedLoader(type, blankKidJwks());
        try {
            assertEquals(Set.of(FIRST_KID), loader.getKeyIds(),
                    "A whitespace-only kid must not be reported for " + type);
            assertTrue(loader.getKeyInfo(BLANK_KID).isEmpty(),
                    "getKeyInfo never resolves a blank kid, so it must not be reported for " + type);
        } finally {
            release(loader);
        }
    }

    // Already pinned for JWKSKeyLoader by JWKSKeyLoaderTest ("Should report a duplicate key id once",
    // "Should resolve a duplicate key id to the last key and count the duplicate").
    @ParameterizedTest(name = "{0}")
    @MethodSource("de.cuioss.sheriff.token.validation.jwks.JwksLoaderKeyIdsContractSupport#loaderTypes")
    @DisplayName("(d) a duplicate kid is reported once, the later key resolving through getKeyInfo")
    void pointD_duplicateKidReportedOnceLaterKeyResolving(LoaderType type) {
        JwksLoader loader = initializedLoader(type, duplicateKidJwks());
        try {
            Optional<KeyInfo> keyInfo = loader.getKeyInfo(FIRST_KID);

            assertEquals(Set.of(FIRST_KID), loader.getKeyIds(), "A duplicate kid must be reported once for " + type);
            assertTrue(keyInfo.isPresent(), "The duplicate kid must resolve for " + type);
            assertEquals("RS384", keyInfo.get().algorithm(), "The later key must win for " + type);
        } finally {
            release(loader);
        }
    }

    // Already pinned for JWKSKeyLoader by JWKSKeyLoaderTest ("Should return an unmodifiable set").
    @ParameterizedTest(name = "{0}")
    @MethodSource("de.cuioss.sheriff.token.validation.jwks.JwksLoaderKeyIdsContractSupport#loaderTypes")
    @DisplayName("(e) the snapshot refuses add and remove")
    void pointE_snapshotRefusesModification(LoaderType type) {
        JwksLoader loader = initializedLoader(type, twoKeyJwks());
        try {
            Set<String> keyIds = loader.getKeyIds();

            assertAll("(e) for " + type,
                    () -> assertThrows(UnsupportedOperationException.class, () -> keyIds.add("added-kid"),
                            "add must be refused"),
                    () -> assertThrows(UnsupportedOperationException.class, () -> keyIds.remove(FIRST_KID),
                            "remove must be refused"),
                    () -> assertEquals(Set.of(FIRST_KID, SECOND_KID), loader.getKeyIds(),
                            "The loader's key ids must be unchanged"));
        } finally {
            release(loader);
        }
    }

    // Already pinned by HttpJwksLoaderKeyRotationTest ("Should report only the active key set's ids after
    // a rotation, as a snapshot"), which drives the refresh directly.
    @Test
    @DisplayName("(f) a returned snapshot is unchanged by a later key rotation")
    void pointF_snapshotUnchangedByRotation() {
        JwksLoader loader = createLoader(LoaderType.HTTP, twoKeyJwks(), REFRESH_INTERVAL_SECONDS);
        try {
            loader.initJWKSLoader(securityEventCounter).join();
            Set<String> snapshot = loader.getKeyIds();
            assertEquals(Set.of(FIRST_KID, SECOND_KID), snapshot, "Precondition: the initial key set is installed");

            moduleDispatcher.setCustomResponse(rotatedJwks());
            awaitKeyIds(loader, Set.of(SECOND_KID));

            assertEquals(Set.of(FIRST_KID, SECOND_KID), snapshot,
                    "A snapshot taken before the rotation must be unchanged by it");
        } finally {
            release(loader);
        }
    }

    // Already pinned by HttpJwksLoaderKeyRotationTest and HttpJwksLoaderGracePeriodTest ("Should keep
    // retired keys accessible within grace period"), which drive the refresh directly.
    @Test
    @DisplayName("(g) a retired id is excluded, resolved within the grace period and not after it")
    void pointG_retiredIdExcludedResolvedOnlyWithinGracePeriod() {
        JwksLoader loader = createLoader(LoaderType.HTTP, twoKeyJwks(), REFRESH_INTERVAL_SECONDS);
        try {
            loader.initJWKSLoader(securityEventCounter).join();

            moduleDispatcher.setCustomResponse(rotatedJwks());
            awaitKeyIds(loader, Set.of(SECOND_KID));

            assertFalse(loader.getKeyIds().contains(FIRST_KID), "A retired key set's id must not be reported");
            assertTrue(loader.getKeyInfo(FIRST_KID).isPresent(),
                    "The retired id must still resolve within the grace period");
            await("grace period of the retired key set ends")
                    .pollInterval(100, MILLISECONDS)
                    .atMost(AWAIT_SECONDS, SECONDS)
                    .until(() -> loader.getKeyInfo(FIRST_KID).isEmpty());
            assertEquals(Set.of(SECOND_KID), loader.getKeyIds(), "Only the active key set's id is reported");
        } finally {
            release(loader);
        }
    }

    // Already pinned by HttpJwksLoaderFailureTest ("Status reflects usable keys") and, for the retained key
    // set, HttpJwksLoaderKeyRotationTest ("L9: a good key set survives an empty/ERROR refresh").
    @Test
    @DisplayName("(h) status OK only with a usable key set, a refresh without usable key keeps the installed set")
    void pointH_okOnlyWithUsableKeysAndInstalledSetKept() {
        JwksLoader loader = createLoader(LoaderType.HTTP, noUsableKeyJwks(), REFRESH_INTERVAL_SECONDS);
        try {
            loader.initJWKSLoader(securityEventCounter).join();
            assertNotEquals(LoaderStatus.OK, loader.getLoaderStatus(), "No usable key set installed: not OK");
            assertTrue(loader.getKeyIds().isEmpty(), "No usable key set installed: no key ids");

            moduleDispatcher.setCustomResponse(twoKeyJwks());
            await("a usable key set is installed")
                    .pollInterval(50, MILLISECONDS)
                    .atMost(AWAIT_SECONDS, SECONDS)
                    .until(() -> loader.getLoaderStatus() == LoaderStatus.OK);
            assertEquals(Set.of(FIRST_KID, SECOND_KID), loader.getKeyIds(), "OK with the usable key set installed");

            int noKeyWarningsBefore = noKeyRefreshWarnings();
            moduleDispatcher.setCustomResponse(noUsableKeyJwks());
            await("a refresh returns no usable key")
                    .pollInterval(50, MILLISECONDS)
                    .atMost(AWAIT_SECONDS, SECONDS)
                    .until(() -> noKeyRefreshWarnings() > noKeyWarningsBefore);

            assertAll("Installed key set kept after a refresh without usable key",
                    () -> assertEquals(Set.of(FIRST_KID, SECOND_KID), loader.getKeyIds(), "Key ids must be kept"),
                    () -> assertEquals(LoaderStatus.OK, loader.getLoaderStatus(), "Status must stay OK"));
        } finally {
            release(loader);
        }
    }

    private JwksLoader initializedLoader(LoaderType type, String content) {
        JwksLoader loader = createLoader(type, content, 0);
        loader.initJWKSLoader(securityEventCounter).join();
        return loader;
    }

    /**
     * Creates a loader of the given type through the factory. For {@link LoaderType#HTTP} the dispatcher
     * serves {@code content}; nothing is loaded before {@code initJWKSLoader}.
     */
    private JwksLoader createLoader(LoaderType type, String content, int refreshIntervalSeconds) {
        return switch (type) {
            case FILE -> JwksLoaderFactory.createFileLoader(writeJwksFile(tempDir, content).toString());
            case IN_MEMORY -> JwksLoaderFactory.createInMemoryLoader(content);
            case HTTP -> {
                moduleDispatcher.setCustomResponse(content);
                yield JwksLoaderFactory.createHttpLoader(httpConfig(jwksUrl, refreshIntervalSeconds, GRACE_PERIOD));
            }
        };
    }

    private static void awaitKeyIds(JwksLoader loader, Set<String> expected) {
        await("key rotation installs " + expected)
                .pollInterval(50, MILLISECONDS)
                .atMost(AWAIT_SECONDS, SECONDS)
                .until(() -> expected.equals(loader.getKeyIds()));
    }

    private static int noKeyRefreshWarnings() {
        return TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(TestLogLevel.WARN,
                WARN.JWKS_REFRESH_RETURNED_NO_KEYS.resolveIdentifierString()).size();
    }
}
