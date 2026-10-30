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

    @ParameterizedTest(name = "refuses {0}")
    @ValueSource(strings = {"https://:443/logout", "https://@/x", "https://user@/x", "https://:/x", "https://;/x",
            "https://user@:443/x", "https://:abc/logout"})
    @DisplayName("Should refuse an endpoint whose authority carries no host even with the opt-in")
    void shouldRefuseAuthorityWithoutHost(String endpoint) {
        var withoutOptIn = assertThrows(ClientProtocolException.class,
                () -> EndpointSchemePolicy.requireTls(endpoint, LABEL, false));
        var withOptIn = assertThrows(ClientProtocolException.class,
                () -> EndpointSchemePolicy.requireTls(endpoint, LABEL, true));

        assertAll("authority without host refused by label only",
                () -> assertTrue(withoutOptIn.getMessage().contains("host"), "names the missing host"),
                () -> assertFalse(withoutOptIn.getMessage().contains(endpoint),
                        "does not echo the untrusted endpoint"),
                () -> assertTrue(withOptIn.getMessage().contains(LABEL), "names the endpoint label"));
    }

    @ParameterizedTest(name = "accepts {0}")
    @ValueSource(strings = {"https://my_host/logout", "https://user@as.example.com:8443/logout",
            "https://as.example.com:/logout", "https://[::1]:8443/logout", "https://127.0.0.1/logout"})
    @DisplayName("Should accept an authority with a host, including a registry-based one, user-info and port")
    void shouldAcceptAuthorityWithHost(String endpoint) {
        assertDoesNotThrow(() -> EndpointSchemePolicy.requireTls(endpoint, LABEL, false));
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

    @Test
    @DisplayName("Should not echo an unparsable endpoint through the cause chain")
    void shouldNotEchoUnparsableEndpointThroughCause() {
        String endpoint = "https://attacker.example.com/a b\r\nInjected: line";

        var thrown = assertThrows(ClientProtocolException.class,
                () -> EndpointSchemePolicy.requireTls(endpoint, LABEL, false));

        assertTrue(thrown.getMessage().contains("is not a valid URL"), "keeps the unparsable-URL message");
        Throwable current = thrown;
        while (current != null) {
            String message = current.getMessage();
            assertFalse(message != null && message.contains("attacker.example.com"),
                    "no message in the cause chain echoes the untrusted endpoint: " + current.getClass().getName());
            current = current.getCause();
        }
    }
}
