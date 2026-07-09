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
package de.cuioss.sheriff.token.client.auth;

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MtlsClientAuth} ({@code tls_client_auth}, RFC 8705) — deliverable 3.
 * <p>
 * Mutual-TLS authenticates the client at the transport layer via the presented client certificate,
 * so the request itself only <em>identifies</em> the client ({@code client_id}) and carries no
 * request credential. These tests confirm that transport-only contract.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("Mutual-TLS (tls_client_auth) client authentication")
class MtlsClientAuthTest {

    @Test
    @DisplayName("Should report the tls_client_auth method")
    void shouldReportMethod() {
        var auth = new MtlsClientAuth(Generators.letterStrings(5, 12).next());

        assertEquals(ClientAuthMethod.TLS_CLIENT_AUTH, auth.method(), "strategy must report tls_client_auth");
    }

    @Test
    @DisplayName("Should identify the client with client_id only and add no request credential")
    void shouldDecorateWithClientIdOnly() {
        String clientId = Generators.letterStrings(5, 12).next();
        var auth = new MtlsClientAuth(clientId);
        Map<String, String> form = new HashMap<>();
        Map<String, String> headers = new HashMap<>();

        auth.decorate(form, headers);

        assertAll("mtls identification",
                () -> assertEquals(clientId, form.get("client_id"), "client_id must identify the client"),
                () -> assertEquals(1, form.size(), "no other form parameters must be added"),
                () -> assertTrue(headers.isEmpty(), "no Authorization header — the binding is at the TLS layer"),
                () -> assertTrue(form.values().stream().noneMatch(v -> v.contains("secret")),
                        "mutual-TLS carries no shared secret"));
    }

    @Test
    @DisplayName("Should reject a null client id")
    void shouldRejectNullClientId() {
        assertThrows(NullPointerException.class, () -> new MtlsClientAuth(null));
    }
}
