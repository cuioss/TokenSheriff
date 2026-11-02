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
package de.cuioss.sheriff.token.client.quarkus.deployment;

import de.cuioss.sheriff.token.client.lifecycle.RefreshScheduler;
import de.cuioss.sheriff.token.client.lifecycle.StoredToken;
import de.cuioss.sheriff.token.client.lifecycle.TokenLifecycleManager;
import de.cuioss.sheriff.token.client.lifecycle.TokenStore;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Container-level regression test for Q-5: an application that declares its own {@link TokenStore} and
 * {@link RefreshScheduler} beans replaces the extension's defaults, and the produced
 * {@link TokenLifecycleManager} is wired with the application's beans.
 * <p>
 * Without {@code @DefaultBean} on the two lifecycle producers of {@code TokenSheriffClientProducer},
 * the user beans and the producers both satisfy the same injection points and the deployment fails
 * with an ambiguous-dependency error, so this test does not start at all.
 * <p>
 * No configuration is supplied: the validation extension keeps booting with an empty issuer namespace
 * (ADR-019), and the client producers are lazy, so the unconfigured client never demands its
 * {@code ClientConfiguration} for the lifecycle beans exercised here.
 */
@Tag("quarkus-boot")
@EnableTestLogger
@DisplayName("Application TokenStore / RefreshScheduler beans replace the client defaults")
class DefaultBeanReplacementTest {

    private static final String SESSION_ID = "session-under-test";

    /**
     * Lead time of the application scheduler. It is far wider than the default 30-second lead, so a
     * token expiring in {@link #TOKEN_LIFETIME} is refresh-eligible only under the application scheduler.
     */
    private static final Duration USER_REFRESH_LEAD = Duration.ofDays(1);

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(1);

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(UserTokenStore.class, UserRefreshScheduler.class));

    @Inject
    TokenStore tokenStore;

    @Inject
    RefreshScheduler refreshScheduler;

    @Inject
    TokenLifecycleManager tokenLifecycleManager;

    @Test
    @DisplayName("Should resolve the application beans instead of the extension defaults")
    void shouldResolveApplicationBeans() {
        assertAll("replaced default beans",
                () -> assertInstanceOf(UserTokenStore.class, ClientProxy.unwrap(tokenStore),
                        "the application TokenStore must replace the default in-memory store"),
                () -> assertInstanceOf(UserRefreshScheduler.class, ClientProxy.unwrap(refreshScheduler),
                        "the application RefreshScheduler must replace the default scheduler"));
    }

    @Test
    @DisplayName("Should wire the TokenLifecycleManager with the application store and scheduler")
    void shouldWireLifecycleManagerWithApplicationBeans() {
        Instant now = Instant.now();
        StoredToken token = new StoredToken("access-token", null, null, null, now.plus(TOKEN_LIFETIME), null);
        UserTokenStore userStore = (UserTokenStore) ClientProxy.unwrap(tokenStore);

        tokenLifecycleManager.store(SESSION_ID, token);

        assertAll("lifecycle manager wiring",
                () -> assertTrue(userStore.holds(SESSION_ID),
                        "the lifecycle manager must write through the application TokenStore"),
                () -> assertFalse(new RefreshScheduler().needsRefresh(token, now),
                        "control: the default 30-second lead does not consider the token refresh-eligible"),
                () -> assertTrue(tokenLifecycleManager.needsRefresh(SESSION_ID, now),
                        "the lifecycle manager must consult the application RefreshScheduler's wider lead"));
    }

    /**
     * Application-supplied token store, standing in for a shared, persistent store.
     */
    @ApplicationScoped
    public static class UserTokenStore implements TokenStore {

        private final Map<String, StoredToken> tokens = new ConcurrentHashMap<>();

        boolean holds(String sessionId) {
            return tokens.containsKey(sessionId);
        }

        @Override
        public void store(String sessionId, StoredToken token) {
            tokens.put(sessionId, token);
        }

        @Override
        public Optional<StoredToken> retrieve(String sessionId) {
            return Optional.ofNullable(tokens.get(sessionId));
        }

        @Override
        public Optional<StoredToken> remove(String sessionId) {
            return Optional.ofNullable(tokens.remove(sessionId));
        }

        @Override
        public Optional<StoredToken> update(String sessionId, UnaryOperator<StoredToken> updater) {
            return Optional.ofNullable(tokens.computeIfPresent(sessionId, (key, current) -> updater.apply(current)));
        }
    }

    /**
     * Application-supplied refresh scheduler with a wider refresh-lead window than the default.
     */
    @ApplicationScoped
    public static class UserRefreshScheduler extends RefreshScheduler {

        public UserRefreshScheduler() {
            super(USER_REFRESH_LEAD);
        }
    }
}
