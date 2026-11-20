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
package de.cuioss.sheriff.token.client.config;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the {@link ClientConfiguration} value object: its fields, defaults, construction-time guards
 * and value semantics. The scope-token rule is covered by {@link ClientConfigurationScopeValidationTest},
 * the transport settings ({@code sslContext}, back-channel origin, egress policy) by
 * {@link ClientConfigurationTransportTest}.
 */
@EnableGeneratorController
@DisplayName("ClientConfiguration value object")
class ClientConfigurationTest {

    private static String issuer() {
        return "https://" + Generators.letterStrings(3, 12).next() + ".example.com";
    }

    @Test
    @DisplayName("Should expose all configured fields through its builder")
    void shouldExposeConfiguredFields() {
        var issuer = issuer();
        var clientId = Generators.nonBlankStrings().next();
        var secret = Generators.nonBlankStrings().next();
        var redirect = issuer() + "/callback";

        var config = ClientConfiguration.builder()
                .issuer(issuer)
                .clientId(clientId)
                .clientSecret(secret)
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .scope("profile")
                .redirectUri(redirect)
                .allowInsecureHttp(false)
                .build();

        assertAll("configuration fields",
                () -> assertEquals(issuer, config.getIssuer()),
                () -> assertEquals(clientId, config.getClientId()),
                () -> assertEquals(secret, config.getClientSecret()),
                () -> assertEquals(ClientAuthMethod.CLIENT_SECRET_BASIC, config.getAuthMethod()),
                () -> assertEquals(2, config.getScopes().size()),
                () -> assertEquals(redirect, config.getRedirectUri()),
                () -> assertFalse(config.isAllowInsecureHttp()));
    }

    @Test
    @DisplayName("Should permit a null secret and redirect for non-interactive clients")
    void shouldPermitNullSecretAndRedirect() {
        var config = ClientConfiguration.builder()
                .issuer(issuer())
                .clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.PRIVATE_KEY_JWT)
                .build();

        assertAll(
                () -> assertTrue(config.getScopes().isEmpty()),
                () -> assertNull(config.getClientSecret()),
                () -> assertNull(config.getRedirectUri()));
    }

    @Test
    @DisplayName("Should reject a blank client secret and permit an absent (null) one — blank != absent")
    void shouldRejectBlankSecretButPermitAbsentSecret() {
        var blankSecret = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .clientSecret("   ").authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC);
        var emptySecret = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .clientSecret("").authMethod(ClientAuthMethod.CLIENT_SECRET_POST);
        // A key-based method carries no shared secret: an absent (null) secret must remain valid.
        var absentSecret = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.PRIVATE_KEY_JWT).build();

        assertAll("blank-secret rejection, null-secret acceptance",
                () -> assertThrows(IllegalArgumentException.class, blankSecret::build,
                        "a whitespace-only client secret is a misconfiguration and must be rejected at construction"),
                () -> assertThrows(IllegalArgumentException.class, emptySecret::build,
                        "an empty client secret must be rejected at construction"),
                () -> assertNull(absentSecret.getClientSecret(),
                        "an absent (null) secret stays valid for a key-based auth method"));
    }

    @Test
    @DisplayName("Should reject a non-positive connect or read timeout at construction")
    void shouldRejectNonPositiveTimeouts() {
        var zeroConnect = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC).connectTimeoutSeconds(0);
        var negativeConnect = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC).connectTimeoutSeconds(-1);
        var zeroRead = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC).readTimeoutSeconds(0);
        var negativeRead = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC).readTimeoutSeconds(-5);

        assertAll("timeout guards",
                () -> assertThrows(IllegalArgumentException.class, zeroConnect::build,
                        "a zero connect timeout is invalid"),
                () -> assertThrows(IllegalArgumentException.class, negativeConnect::build,
                        "a negative connect timeout is invalid"),
                () -> assertThrows(IllegalArgumentException.class, zeroRead::build,
                        "a zero read timeout is invalid"),
                () -> assertThrows(IllegalArgumentException.class, negativeRead::build,
                        "a negative read timeout is invalid"));
    }

    @Test
    @DisplayName("Should build with explicit positive timeouts and a valid secret, and default the timeouts otherwise")
    void shouldBuildWithValidTimeoutsAndSecret() {
        var explicit = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .clientSecret(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .connectTimeoutSeconds(7).readTimeoutSeconds(11).build();
        var defaulted = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC).build();

        assertAll("valid timeout configuration",
                () -> assertEquals(7, explicit.getConnectTimeoutSeconds()),
                () -> assertEquals(11, explicit.getReadTimeoutSeconds()),
                () -> assertEquals(ClientConfiguration.DEFAULT_CONNECT_TIMEOUT_SECONDS,
                        defaulted.getConnectTimeoutSeconds()),
                () -> assertEquals(ClientConfiguration.DEFAULT_READ_TIMEOUT_SECONDS,
                        defaulted.getReadTimeoutSeconds()));
    }

    @Test
    @DisplayName("Should enable hostname verification by default and retain an explicit opt-out")
    void shouldDefaultAndRetainVerifyHostname() {
        var defaulted = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC).build();
        var relaxed = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .verifyHostname(false).build();

        assertAll("hostname verification is an opt-out",
                () -> assertTrue(defaulted.isVerifyHostname(),
                        "an unconfigured client must verify hostnames"),
                () -> assertFalse(relaxed.isVerifyHostname(),
                        "an explicit verifyHostname(false) must be retained"));
    }

    @Test
    @DisplayName("Should reject verifyHostname(false) combined with a caller-supplied sslContext at build time")
    void shouldRejectVerifyHostnameFalseWithSslContext() {
        var conflicting = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .verifyHostname(false)
                .sslContext(assertDoesNotThrow(SSLContext::getDefault));

        var exception = assertThrows(IllegalArgumentException.class, conflicting::build,
                "the incompatible combination must fail at build(), not at the first token exchange");

        assertTrue(exception.getMessage().contains("verifyHostname(false) cannot be combined with sslContext(...)"),
                "the message must name the conflicting pair, but was: " + exception.getMessage());
    }

    @Test
    @DisplayName("Should accept a caller-supplied sslContext while hostname verification stays enabled")
    void shouldAcceptSslContextWithHostnameVerificationEnabled() {
        var config = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .sslContext(assertDoesNotThrow(SSLContext::getDefault)).build();

        assertAll("per-client trust and hostname verification are independent axes",
                () -> assertNotNull(config.getSslContext()),
                () -> assertTrue(config.isVerifyHostname()));
    }

    @Test
    @DisplayName("Should default the discovery-document ceiling above the JWT payload ceiling and keep it settable")
    void shouldExposeDiscoveryDocumentMaxSize() {
        var defaulted = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC).build();
        var explicit = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .discoveryDocumentMaxSize(256 * 1024).build();

        assertAll("discovery-document ceiling",
                () -> assertEquals(ClientConfiguration.DEFAULT_DISCOVERY_DOCUMENT_MAX_SIZE,
                        defaulted.getDiscoveryDocumentMaxSize()),
                // The 8 KiB JWT payload bound is one an ordinary Keycloak realm already exceeds, so the
                // discovery default must leave real documents more room than that.
                () -> assertTrue(defaulted.getDiscoveryDocumentMaxSize() > 8 * 1024,
                        "the default must exceed the 8 KiB JWT payload ceiling"),
                () -> assertEquals(256 * 1024, explicit.getDiscoveryDocumentMaxSize(),
                        "an embedder facing a larger document can raise the ceiling"));
    }

    @Test
    @DisplayName("Should reject a non-positive discovery-document ceiling at construction")
    void shouldRejectNonPositiveDiscoveryDocumentMaxSize() {
        var zero = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC).discoveryDocumentMaxSize(0);
        var negative = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC).discoveryDocumentMaxSize(-1);

        assertAll("discovery-document ceiling guards",
                () -> assertThrows(IllegalArgumentException.class, zero::build,
                        "a zero ceiling would reject every discovery document"),
                () -> assertThrows(IllegalArgumentException.class, negative::build,
                        "a negative ceiling is not a size"));
    }

    @Test
    @DisplayName("Should default strict scope reconciliation to false so a broadened grant is reported, not refused")
    void shouldDefaultStrictScopeReconciliationToFalse() {
        var defaulted = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC).build();

        assertFalse(defaulted.isStrictScopeReconciliation(),
                "the lenient posture is the default: refusing a broadened grant out of the box would turn an "
                        + "authorization-server quirk into an authentication outage with no attacker in the loop");
    }

    @Test
    @DisplayName("Should round-trip an explicitly enabled strict scope reconciliation")
    void shouldRoundTripExplicitStrictScopeReconciliation() {
        var strict = ClientConfiguration.builder()
                .issuer(issuer()).clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .strictScopeReconciliation(true).build();

        assertTrue(strict.isStrictScopeReconciliation(),
                "a deployment that opts in to the strict posture must see the flag survive construction");
    }

    @Test
    @DisplayName("Should reject a null issuer, client id or auth method")
    void shouldRejectNullRequiredFields() {
        var clientId = Generators.nonBlankStrings().next();
        var missingIssuer = ClientConfiguration.builder()
                .clientId(clientId).authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC);
        var missingClientId = ClientConfiguration.builder()
                .issuer(issuer()).authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC);
        var missingAuthMethod = ClientConfiguration.builder()
                .issuer(issuer()).clientId(clientId);
        assertAll(
                () -> assertThrows(NullPointerException.class, missingIssuer::build),
                () -> assertThrows(NullPointerException.class, missingClientId::build),
                () -> assertThrows(NullPointerException.class, missingAuthMethod::build));
    }

    @Test
    @DisplayName("Should keep the scopes list immutable")
    void shouldKeepScopesImmutable() {
        var config = ClientConfiguration.builder()
                .issuer(issuer())
                .clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .build();

        var scopes = config.getScopes();
        assertThrows(UnsupportedOperationException.class, () -> scopes.add("extra"));
    }

    @Test
    @DisplayName("Should honour value-based equals and hashCode")
    void shouldHonourValueEquality() {
        var issuer = issuer();
        var clientId = Generators.nonBlankStrings().next();

        var first = ClientConfiguration.builder()
                .issuer(issuer).clientId(clientId).authMethod(ClientAuthMethod.CLIENT_SECRET_POST).build();
        var second = ClientConfiguration.builder()
                .issuer(issuer).clientId(clientId).authMethod(ClientAuthMethod.CLIENT_SECRET_POST).build();
        var different = ClientConfiguration.builder()
                .issuer(issuer).clientId(clientId).authMethod(ClientAuthMethod.PRIVATE_KEY_JWT).build();

        assertAll(
                () -> assertEquals(first, second),
                () -> assertEquals(first.hashCode(), second.hashCode()),
                () -> assertNotEquals(first, different));
    }

    @Test
    @DisplayName("Should exclude the client secret from toString so an incidental dump cannot leak it (TEST-16, H8)")
    void shouldExcludeSecretFromToString() {
        var secret = Generators.letterStrings(20, 40).next();
        var clientId = Generators.nonBlankStrings().next();
        var config = ClientConfiguration.builder()
                .issuer(issuer())
                .clientId(clientId)
                .clientSecret(secret)
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .build();

        String rendered = config.toString();

        assertAll("secret never leaks through toString",
                () -> assertFalse(rendered.contains(secret),
                        "the client secret value must never appear in the string representation"),
                () -> assertFalse(rendered.contains("clientSecret"),
                        "the excluded field is not even named in the string representation"),
                () -> assertTrue(rendered.contains(clientId),
                        "non-secret fields remain visible for diagnostics"));
    }

    @Nested
    @DisplayName("ClientAuthMethod metadata")
    class ClientAuthMethodTest {

        @Test
        @DisplayName("Should rank key-based methods above shared-secret methods")
        void shouldRankKeyBasedMethodsStronger() {
            // The TLS_CLIENT_AUTH leg pins the declared alpha surface: the constant keeps its
            // key-based rank even though the selector never picks it.
            assertAll(
                    () -> assertTrue(ClientAuthMethod.PRIVATE_KEY_JWT.getStrength()
                            > ClientAuthMethod.CLIENT_SECRET_BASIC.getStrength()),
                    () -> assertTrue(ClientAuthMethod.TLS_CLIENT_AUTH.getStrength()
                            > ClientAuthMethod.CLIENT_SECRET_POST.getStrength()));
        }

        @Test
        @DisplayName("Should resolve a method from its advertised metadata value")
        void shouldResolveFromMetadataValue() {
            // The tls_client_auth round-trip pins the declared alpha surface: an advertised
            // tls_client_auth entry still resolves to a named method even though it is never selected.
            assertAll(
                    () -> assertEquals(ClientAuthMethod.PRIVATE_KEY_JWT,
                            ClientAuthMethod.fromMetadataValue("private_key_jwt").orElseThrow()),
                    () -> assertEquals(ClientAuthMethod.TLS_CLIENT_AUTH,
                            ClientAuthMethod.fromMetadataValue("tls_client_auth").orElseThrow()),
                    () -> assertTrue(ClientAuthMethod.fromMetadataValue("none").isEmpty()),
                    () -> assertTrue(ClientAuthMethod.fromMetadataValue(null).isEmpty()));
        }
    }

    @Nested
    @DisplayName("Construction-time issuer validation (L14)")
    class IssuerValidation {

        private ClientConfiguration.ClientConfigurationBuilder builderWithIssuer(String issuerValue) {
            return ClientConfiguration.builder()
                    .issuer(issuerValue)
                    .clientId(Generators.nonBlankStrings().next())
                    .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                    .clientSecret(Generators.nonBlankStrings().next());
        }

        @Test
        @DisplayName("Should reject a blank issuer or client id — a present-but-empty value is not a value")
        void shouldRejectBlankIssuerAndClientId() {
            var blankIssuer = builderWithIssuer("   ");
            var blankClientId = ClientConfiguration.builder()
                    .issuer(issuer()).clientId("  ")
                    .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC);

            assertAll("blank required fields",
                    () -> assertThrows(IllegalArgumentException.class, blankIssuer::build,
                            "a whitespace-only issuer must be rejected at construction"),
                    () -> assertThrows(IllegalArgumentException.class, blankClientId::build,
                            "a whitespace-only client id must be rejected at construction"));
        }

        @Test
        @DisplayName("Should reject a relative issuer that carries no scheme at all")
        void shouldRejectRelativeIssuer() {
            var relative = builderWithIssuer("issuer.example.com/auth");

            assertThrows(IllegalArgumentException.class, relative::build,
                    "a relative reference cannot identify an authorization server");
        }

        @Test
        @DisplayName("Should reject an absolute issuer URI that carries no host")
        void shouldRejectIssuerWithoutHost() {
            var hostless = builderWithIssuer("urn:example:issuer");

            assertThrows(IllegalArgumentException.class, hostless::build,
                    "an absolute URI without a host cannot be dereferenced for discovery");
        }

        @Test
        @DisplayName("Should reject an issuer whose scheme is neither http nor https")
        void shouldRejectNonHttpIssuerScheme() {
            var ftp = builderWithIssuer("ftp://issuer.example.com");

            assertThrows(IllegalArgumentException.class, ftp::build,
                    "only http(s) issuers can be resolved over the discovery transport");
        }

        @Test
        @DisplayName("Should reject a malformed issuer URL that cannot be parsed at all")
        void shouldRejectUnparseableIssuer() {
            var malformed = builderWithIssuer("https://issuer.example.com/ a path");

            assertThrows(IllegalArgumentException.class, malformed::build,
                    "an unparseable issuer must fail at construction, not later on the wire");
        }

        @Test
        @DisplayName("Should accept an http issuer so a cleartext local test setup stays configurable")
        void shouldAcceptHttpIssuer() {
            var config = builderWithIssuer("http://localhost:8080/auth").allowInsecureHttp(true).build();

            assertEquals("http://localhost:8080/auth", config.getIssuer(),
                    "a plaintext http issuer remains constructible for local test setups");
        }
    }
}
