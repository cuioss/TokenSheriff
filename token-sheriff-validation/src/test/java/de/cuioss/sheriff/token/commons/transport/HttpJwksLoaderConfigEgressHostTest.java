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
package de.cuioss.sheriff.token.commons.transport;

import de.cuioss.sheriff.token.commons.error.TransportException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the egress allow-list of {@link HttpJwksLoaderConfig}: the secure-default refusal of a blocked
 * host, the bypass for an allow-listed host, the bare-host rule of
 * {@link HttpJwksLoaderConfig.HttpJwksLoaderConfigBuilder#allowedEgressHost(String)} over every refusal
 * class of {@link EgressHostValidator}, and the bracketed match of a bare IPv6 entry. The other
 * endpoint settings are covered by {@link HttpJwksLoaderConfigTest}.
 */
@DisplayName("Tests HttpJwksLoaderConfig egress allow-list")
@SuppressWarnings("java:S5778")
// owolff: Suppressing because for a builder this is not a problem
class HttpJwksLoaderConfigEgressHostTest {

    private static final String VALID_URL = "https://example.com/.well-known/jwks.json";

    @Test
    @DisplayName("Should reject a JWKS host resolving to a blocked range when no egress opt-in is set")
    void shouldRejectBlockedHostWithoutEgressOptIn() {
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer")
                .build();
        // "localhost" resolves to a loopback address, which the secure-default egress guard blocks.
        assertThrows(TransportException.class,
                () -> config.getEgressPolicy().check(URI.create("https://localhost:8443/certs")),
                "A loopback-resolving host must be blocked when no egress opt-in is configured");
    }

    @Test
    @DisplayName("Should permit a JWKS host that is on the explicit egress allow-list")
    void shouldPermitAllowedEgressHost() {
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer")
                .allowedEgressHost("localhost")
                .build();
        // The allow-list bypasses the address-range checks entirely, so the otherwise-blocked
        // loopback resolution of "localhost" is permitted.
        assertDoesNotThrow(
                () -> config.getEgressPolicy().check(URI.create("https://localhost:8443/certs")),
                "An allow-listed host must bypass the egress address-range checks");
    }

    @Test
    @DisplayName("Should refuse a null egress allow-list entry at the setter")
    void shouldRefuseNullAllowedEgressHost() {
        var builder = HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer");

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.allowedEgressHost(null),
                "a null entry must be refused at the setter instead of being ignored");
        assertEquals(EgressHostValidator.Refusal.NULL_ENTRY.text(), exception.getMessage());
    }

    @ParameterizedTest(name = "refuses [{0}]")
    @ValueSource(strings = {"", "   ", "localhost:8443", "[::1]:8443"})
    @DisplayName("Should refuse a blank or port-carrying egress allow-list entry at the setter")
    void shouldRefuseMalformedAllowedEgressHost(String entry) {
        var builder = HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer");

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.allowedEgressHost(entry),
                "a malformed entry must be refused at the setter, before build()");
        assertEquals(EgressHostValidator.refusal(entry).orElseThrow(), exception.getMessage());
        if (!entry.isBlank()) {
            for (Throwable current = exception; current != null; current = current.getCause()) {
                String message = current.getMessage();
                assertFalse(message != null && message.contains(entry),
                        "no exception in the cause chain may echo the entry, but found: " + message);
            }
        }
    }

    @ParameterizedTest(name = "refuses [{0}] with its fixed text")
    @MethodSource("de.cuioss.sheriff.token.commons.transport.EgressHostValidatorTest#builderRefusalClasses")
    @DisplayName("Should refuse every refusal class of the validator at the setter, never echoing the entry")
    void shouldRefuseEveryRefusalClass(@Nullable String entry, String expectedRefusal) {
        var builder = HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer");

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.allowedEgressHost(entry),
                "the allow-list setter must refuse the entry before build()");
        EgressHostValidatorTest.assertFixedRefusal(exception, entry, expectedRefusal);
    }

    @Test
    @DisplayName("Should store a bare IPv6 egress allow-list entry so it matches the bracketed URL host")
    void shouldMatchBareIpv6AllowedEgressHost() {
        HttpJwksLoaderConfig config = HttpJwksLoaderConfig.builder()
                .jwksUrl(VALID_URL)
                .issuerIdentifier("test-issuer")
                .allowedEgressHost("::1")
                .build();

        assertDoesNotThrow(
                () -> config.getEgressPolicy().check(URI.create("https://[::1]:8443/certs")),
                "a bare ::1 entry must match the [::1] host URI.getHost() reports");
    }
}
