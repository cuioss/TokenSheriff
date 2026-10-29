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
package de.cuioss.sheriff.token.client.internal;

import de.cuioss.sheriff.token.commons.error.ClientProtocolException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the shared front-channel TLS policy {@link EndpointSchemePolicy} (C-4).
 */
@DisplayName("EndpointSchemePolicy front-channel TLS policy")
class EndpointSchemePolicyTest {

    private static final String LABEL = "test endpoint";

    @ParameterizedTest(name = "accepts {0}")
    @ValueSource(strings = {"https://as.example.com/authorize", "HTTPS://as.example.com/authorize"})
    @DisplayName("Should accept https in any case, with and without the http opt-in")
    void shouldAcceptHttps(String endpoint) {
        assertAll("https accepted",
                () -> assertDoesNotThrow(() -> EndpointSchemePolicy.requireTls(endpoint, LABEL, false)),
                () -> assertDoesNotThrow(() -> EndpointSchemePolicy.requireTls(endpoint, LABEL, true)));
    }

    @Test
    @DisplayName("Should refuse http without the opt-in")
    void shouldRefuseHttpWithoutOptIn() {
        assertThrows(ClientProtocolException.class,
                () -> EndpointSchemePolicy.requireTls("http://localhost:8080/authorize", LABEL, false));
    }

    @Test
    @DisplayName("Should accept http with the opt-in")
    void shouldAcceptHttpWithOptIn() {
        assertDoesNotThrow(() -> EndpointSchemePolicy.requireTls("http://localhost:8080/authorize", LABEL, true));
    }

    @ParameterizedTest(name = "refuses {0}")
    @ValueSource(strings = {
            "ftp://as.example.com/authorize",
            "javascript:alert(1)",
            "file:///etc/passwd",
            "as.example.com/authorize",
            "https://as.example.com/a b"})
    @DisplayName("Should refuse non-http(s), scheme-less and unparsable URLs even with the opt-in")
    void shouldRefuseOtherSchemesEvenWithOptIn(String endpoint) {
        assertAll("refused regardless of opt-in",
                () -> assertThrows(ClientProtocolException.class,
                        () -> EndpointSchemePolicy.requireTls(endpoint, LABEL, false)),
                () -> assertThrows(ClientProtocolException.class,
                        () -> EndpointSchemePolicy.requireTls(endpoint, LABEL, true)));
    }

    @ParameterizedTest(name = "refuses {0}")
    @ValueSource(strings = {"https:/logout", "https:///logout", "https:foo"})
    @DisplayName("Should refuse a hostless https endpoint even with the opt-in")
    void shouldRefuseHostlessEndpoint(String endpoint) {
        var withoutOptIn = assertThrows(ClientProtocolException.class,
                () -> EndpointSchemePolicy.requireTls(endpoint, LABEL, false));
        var withOptIn = assertThrows(ClientProtocolException.class,
                () -> EndpointSchemePolicy.requireTls(endpoint, LABEL, true));

        assertAll("hostless endpoint refused by label only",
                () -> assertTrue(withoutOptIn.getMessage().contains(LABEL), "names the endpoint label"),
                () -> assertTrue(withoutOptIn.getMessage().contains("host"), "names the missing host"),
                () -> assertFalse(withoutOptIn.getMessage().contains(endpoint),
                        "does not echo the untrusted endpoint"),
                () -> assertTrue(withOptIn.getMessage().contains(LABEL), "names the endpoint label"));
    }

    @Test
    @DisplayName("Should accept a registry-based authority that is not a server host name")
    void shouldAcceptRegistryBasedAuthority() {
        assertDoesNotThrow(() -> EndpointSchemePolicy.requireTls("https://my_host/logout", LABEL, false));
    }

    @Test
    @DisplayName("Should keep the scheme refusal message for a hostless non-TLS endpoint")
    void shouldKeepSchemeRefusalForHostlessNonTlsEndpoint() {
        var thrown = assertThrows(ClientProtocolException.class,
                () -> EndpointSchemePolicy.requireTls("http:/logout", LABEL, false));

        assertTrue(thrown.getMessage().contains("is not a TLS (https) URL"),
                "a scheme refusal takes precedence over the host check");
    }

    @Test
    @DisplayName("Should name the label and not echo the endpoint in the refusal message")
    void shouldNameLabelInMessage() {
        String endpoint = "ftp://attacker.example.com/authorize";

        var thrown = assertThrows(ClientProtocolException.class,
                () -> EndpointSchemePolicy.requireTls(endpoint, LABEL, true));

        assertAll("refusal message",
                () -> assertTrue(thrown.getMessage().contains(LABEL), "names the endpoint label"),
                () -> assertFalse(thrown.getMessage().contains("attacker.example.com"),
                        "does not echo the untrusted endpoint"));
    }

    @Test
    @DisplayName("Should name the label in the refusal message for an unparsable URL")
    void shouldNameLabelForUnparsableUrl() {
        var thrown = assertThrows(ClientProtocolException.class,
                () -> EndpointSchemePolicy.requireTls("https://as.example.com/a b", LABEL, false));

        assertTrue(thrown.getMessage().contains(LABEL), "names the endpoint label");
    }
}
