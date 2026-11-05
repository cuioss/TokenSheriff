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
package de.cuioss.sheriff.token.client.token;

import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.ProviderMetadata;
import de.cuioss.sheriff.token.client.flow.RefreshFlow;
import de.cuioss.sheriff.token.client.lifecycle.StoredToken;
import de.cuioss.sheriff.token.client.lifecycle.TokenLifecycleManager;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.test.dispatcher.TokenDispatcher;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fail-closed contract for a refreshed ID token that the validation pipeline rejects
 * (signature, {@code iss}, {@code aud}, {@code exp} / {@code iat}) after the authorization server has
 * answered the refresh {@code 2xx}.
 * <p>
 * The matched pair mirrors the neighbouring {@link RefreshAdversarialTest} case for an ID token that
 * validates but is inconsistent with the refreshed access token ({@code IllegalStateException}): both
 * refusals happen after redemption, so both must end in the same disposition. When the exchange rotated
 * the refresh token the presented token is already burned at the authorization server, so the rotated
 * successor is revoked and the session cleared; when it did not rotate the presented token is still
 * usable, so nothing is revoked or cleared. Either way the original {@link TokenValidationException}
 * reaches the caller unchanged in this test, because the cleanup completes here; in general the
 * exception is preserved only when that cleanup completes without an uncaught exception (an unchecked
 * exception from a revocation call, or from the store's removal, would replace it).
 * <p>
 * The pipeline refusal is produced by an ID token minted for a foreign issuer, which the validator
 * built over the access token's issuer configuration does not know. The inconsistent-ID-token refusal
 * stays pinned in {@link RefreshAdversarialTest} and is deliberately not duplicated here.
 * <p>
 * Held in its own class rather than folded into {@link RefreshAdversarialTest}, per the
 * {@link RefreshTestSupport} precedent of splitting the wired refresh contract by behaviour cluster
 * instead of growing one class past the module's 400-line budget.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("Wired refresh: a refreshed ID token the pipeline rejects fails closed only after rotation")
class RefreshIdTokenValidationQuarantineTest extends RefreshTestSupport {

    @BeforeEach
    void setUp() {
        initRefreshFixture();
    }

    @Test
    @DisplayName("Should quarantine the rotated session when the refreshed ID token fails pipeline validation")
    void shouldQuarantineWhenRefreshedIdTokenFailsPipelineValidation(URIBuilder uriBuilder) {
        ClientConfiguration config = config();
        ProviderMetadata metadata = metadata(uriBuilder);
        RefreshFlow flow = refreshFlow(config);
        var revocationClient = new RecordingRevocationClient(config);
        TokenLifecycleManager manager = manager();
        String session = Generators.letterStrings(10, 20).next();
        String rt1 = Generators.letterStrings(20, 40).next();
        manager.store(session, bearerBundle(rt1, Generators.letterStrings(20, 40).next()));
        String rawIdToken = foreignIssuerIdToken();
        TokenValidationException pipelineRefusal = assertThrows(TokenValidationException.class,
                () -> idBridge.validateRefreshedIdToken(rawIdToken),
                "the fixture's ID token must be one the validation pipeline itself rejects");
        // The AS rotates the refresh token, burning rt1 on its own side, and only then does the client
        // pipeline reject the refreshed ID token. Keeping rt1 would leave the client holding a credential
        // the AS has already invalidated, and the rotated successor would be orphaned.
        String rotatedToken = Generators.letterStrings(20, 40).next();
        getModuleDispatcher().respondWith(
                TokenDispatcher.tokenResponse(accessHolder.getRawToken(), rotatedToken, rawIdToken, 300));
        var clientAuth = clientAuth(config);

        var thrown = assertThrows(TokenValidationException.class,
                () -> manager.refresh(session, metadata, flow, revocationClient, idBridge, clientAuth));

        assertAll("the pipeline refusal after rotation is quarantined, not left holding the burned token",
                () -> assertEquals(pipelineRefusal.getMessage(), thrown.getMessage(),
                        "the original refusal propagates with its message unchanged"),
                () -> assertEquals(pipelineRefusal.getEventType(), thrown.getEventType(),
                        "the original refusal propagates with its event type unchanged"),
                () -> assertTrue(manager.get(session).isEmpty(),
                        "the session is cleared rather than left holding the AS-burned rt1"),
                () -> assertTrue(revocationClient.revoked(rotatedToken),
                        "the AS-issued successor is revoked at the AS (RFC 7009)"),
                () -> assertFalse(revocationClient.revoked(rt1),
                        "the already-burned presented token is not what gets revoked"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                "clearing the store and rotation family");
    }

    @Test
    @DisplayName("Should leave the stored bundle usable when the refreshed ID token fails pipeline validation without rotation")
    void shouldKeepStoredBundleWhenPipelineRefusalFollowsNoRotation(URIBuilder uriBuilder) {
        ClientConfiguration config = config();
        ProviderMetadata metadata = metadata(uriBuilder);
        RefreshFlow flow = refreshFlow(config);
        var revocationClient = new RecordingRevocationClient(config);
        TokenLifecycleManager manager = manager();
        String session = Generators.letterStrings(10, 20).next();
        String rt1 = Generators.letterStrings(20, 40).next();
        String originalIdToken = Generators.letterStrings(20, 40).next();
        manager.store(session, bearerBundle(rt1, originalIdToken));
        String rawIdToken = foreignIssuerIdToken();
        TokenValidationException pipelineRefusal = assertThrows(TokenValidationException.class,
                () -> idBridge.validateRefreshedIdToken(rawIdToken),
                "the fixture's ID token must be one the validation pipeline itself rejects");
        // The matched negative control: the refusal IS post-redemption, but the AS omitted a new refresh
        // token (RFC 6749 §6 permits that), so rt1 survived the exchange and stays usable. Only rotation
        // — not the mere fact of redemption — may quarantine, and this is what pins that.
        getModuleDispatcher().respondWith(
                TokenDispatcher.tokenResponse(accessHolder.getRawToken(), null, rawIdToken, 300));
        var clientAuth = clientAuth(config);

        var thrown = assertThrows(TokenValidationException.class,
                () -> manager.refresh(session, metadata, flow, revocationClient, idBridge, clientAuth));

        StoredToken kept = manager.get(session).orElseThrow();
        assertAll("a refused but non-rotating redemption leaves the session untouched",
                () -> assertEquals(pipelineRefusal.getMessage(), thrown.getMessage(),
                        "the original refusal propagates with its message unchanged"),
                () -> assertEquals(rt1, kept.refreshToken(),
                        "a refresh token the AS did not rotate is still valid and must be kept"),
                () -> assertEquals(originalIdToken, kept.idToken(),
                        "the refused ID token is not carried forward over the stored one"),
                () -> assertFalse(revocationClient.revokedAny(),
                        "nothing is revoked while the presented token is still live"));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, TokenLifecycleManager.class);

        // The kept token is genuinely usable: a retry presenting it rotates normally instead of being
        // read as reuse.
        String rt2 = Generators.letterStrings(20, 40).next();
        getModuleDispatcher().respondWith(TokenDispatcher.tokenResponse(accessHolder.getRawToken(), rt2, null, 300));

        StoredToken retried = manager
                .refresh(session, metadata, flow, revocationClient, idBridge, clientAuth(config))
                .orElseThrow();

        assertEquals(rt2, retried.refreshToken(),
                "the retained rt1 is still usable and rotates normally on the next refresh");
    }

    /**
     * An ID token whose {@code iss} names an issuer the validator under test does not know, so the
     * pipeline rejects it on its own merits before any access-token consistency check can run.
     */
    private String foreignIssuerIdToken() {
        idHolder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("https://attacker.example.com"));
        return idHolder.getRawToken();
    }
}
