/*
 * Copyright © 2022 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.token.client.flow;

import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.ProviderMetadata;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Wired-flow scaffold ({@code S0}): proves the {@link WiredFlowTestSupport} harness compiles and can
 * drive the real {@link AuthorizationCodeFlow} orchestrator end to end through the recording
 * dispatcher, exactly as the CDI-produced bean graph would.
 * <p>
 * It carries the two shapes the per-defence wiring deliverables (mix-up, DPoP, refresh-reuse) build
 * on:
 * <ul>
 *   <li>a <strong>guarded exchange</strong> that redeems a valid callback and returns validated,
 *       nonce-bound tokens — the token endpoint is called exactly once;</li>
 *   <li>a <strong>negative path</strong> where a tampered callback is refused <em>before</em> the
 *       token endpoint is ever called ({@link WiredFlowTestSupport#assertRefusedBeforeTokenEndpoint}),
 *       so a defence deleted from the wired path would fail here.</li>
 * </ul>
 * Named {@code *IT} so it runs in the integration-test phase; the per-defence negative cases are added
 * inside their corresponding wiring deliverables.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("Wired-flow scaffold: real AuthorizationCodeFlow driven end to end")
class WiredFlowNegativePathIT extends WiredFlowTestSupport {

    @BeforeEach
    void setUp() {
        initWiredFlow();
    }

    @Test
    @DisplayName("Should drive a guarded exchange end to end and call the token endpoint exactly once")
    void shouldDriveGuardedExchange(URIBuilder uriBuilder) {
        ClientConfiguration config = config();
        FlowContext context = FlowContext.create(REDIRECT_URI);
        idHolder.withClaim("nonce", ClaimValue.forPlainString(context.nonce()));
        getModuleDispatcher().success(accessHolder.getRawToken(), idHolder.getRawToken(), null, 300);
        String code = Generators.letterStrings(20, 40).next();
        var callback = new CallbackParameters(code, context.state(), null, null, null);

        AuthorizationCodeFlow.AuthenticationResult result =
                authorizationCodeFlow(config).exchange(metadataWithTokenEndpoint(uriBuilder), context, callback,
                        clientAuth(config));

        assertAll("guarded exchange",
                () -> assertNotNull(result.accessToken(), "a validated access token must be returned"),
                () -> assertNotNull(result.idToken(), "a validated, nonce-bound ID token must be returned"),
                () -> getModuleDispatcher().assertCalled(1));
    }

    @Test
    @DisplayName("Should refuse a state-mismatched callback before the token endpoint is called")
    void shouldRefuseStateMismatchBeforeTokenCall(URIBuilder uriBuilder) {
        ClientConfiguration config = config();
        FlowContext context = FlowContext.create(REDIRECT_URI);
        getModuleDispatcher().success(accessHolder.getRawToken(), idHolder.getRawToken(), null, 300);
        var forgedCallback = new CallbackParameters(Generators.letterStrings(20, 40).next(),
                Generators.letterStrings(20, 40).next(), null, null, null);
        var flow = authorizationCodeFlow(config);
        var metadata = metadataWithTokenEndpoint(uriBuilder);
        var clientAuth = clientAuth(config);

        assertRefusedBeforeTokenEndpoint(IllegalStateException.class,
                () -> flow.exchange(metadata, context, forgedCallback, clientAuth));
    }

    @Test
    @DisplayName("Should refuse to redeem the code when the callback iss is another AS (RFC 9207 mix-up, T-MIXUP)")
    void shouldRefuseIssMismatchBeforeTokenCall(URIBuilder uriBuilder) {
        ClientConfiguration config = config();
        FlowContext context = FlowContext.create(REDIRECT_URI);
        getModuleDispatcher().success(accessHolder.getRawToken(), idHolder.getRawToken(), null, 300);
        var mixedUpCallback = new CallbackParameters(Generators.letterStrings(20, 40).next(), context.state(),
                null, null, "https://attacker.example.com");
        var flow = authorizationCodeFlow(config);
        var metadata = metadataWithTokenEndpoint(uriBuilder);
        var clientAuth = clientAuth(config);

        assertRefusedBeforeTokenEndpoint(IllegalStateException.class,
                () -> flow.exchange(metadata, context, mixedUpCallback, clientAuth));
    }

    @Test
    @DisplayName("Should refuse redemption when the AS advertises iss support but the callback omits it")
    void shouldRefuseMissingRequiredIssBeforeTokenCall(URIBuilder uriBuilder) {
        ClientConfiguration config = config();
        FlowContext context = FlowContext.create(REDIRECT_URI);
        getModuleDispatcher().success(accessHolder.getRawToken(), idHolder.getRawToken(), null, 300);
        ProviderMetadata metadata = metadataWithTokenEndpoint(uriBuilder);
        metadata.authorizationResponseIssParameterSupported = true;
        var callbackWithoutIss = new CallbackParameters(Generators.letterStrings(20, 40).next(), context.state(),
                null, null, null);
        var flow = authorizationCodeFlow(config);
        var clientAuth = clientAuth(config);

        assertRefusedBeforeTokenEndpoint(IllegalStateException.class,
                () -> flow.exchange(metadata, context, callbackWithoutIss, clientAuth));
    }
}
