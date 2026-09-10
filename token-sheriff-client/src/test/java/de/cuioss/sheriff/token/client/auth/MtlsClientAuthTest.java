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
package de.cuioss.sheriff.token.client.auth;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Alpha-surface contract test for {@link MtlsClientAuth} ({@code tls_client_auth}, RFC 8705) — H4.
 * <p>
 * {@code tls_client_auth} is an alpha capability: declared, never selected, and not a coverage
 * obligation while unexercised. Its fail-fast constructor is what holds that classification, so
 * these tests pin the contract of the alpha surface rather than the behaviour of a usable strategy.
 * Mutual-TLS authenticates the client at the transport layer via a client certificate carried by an
 * {@code SSLContext} on the {@code HttpHandler}. No code path plumbs such an {@code SSLContext} into
 * the transport, so a {@code tls_client_auth} request would leave the client without a bound
 * certificate — an unauthenticated request. The strategy refuses construction rather than producing
 * that request.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("Mutual-TLS (tls_client_auth) alpha-surface contract")
class MtlsClientAuthTest {

    @Test
    @DisplayName("Should refuse construction — the alpha surface is declared but never constructible")
    void shouldFailFastAtConstruction() {
        String clientId = Generators.letterStrings(5, 12).next();

        var exception = assertThrows(UnsupportedOperationException.class,
                () -> new MtlsClientAuth(clientId),
                "constructing the alpha tls_client_auth strategy must fail fast");

        assertTrue(exception.getMessage().contains("tls_client_auth"),
                "the failure must name the unsupported method");
    }

    @Test
    @DisplayName("Should refuse construction for a null client id — no client id makes the alpha surface usable")
    void shouldFailFastForNullClientId() {
        assertThrows(UnsupportedOperationException.class, () -> new MtlsClientAuth(null),
                "no tls_client_auth strategy is constructible regardless of the client id");
    }
}
