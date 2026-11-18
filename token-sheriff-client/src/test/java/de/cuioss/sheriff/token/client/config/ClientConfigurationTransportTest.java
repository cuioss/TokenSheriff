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

import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.commons.transport.EgressPolicy;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.SSLContext;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the transport-facing fields of {@link ClientConfiguration}: the per-client TLS trust
 * ({@code sslContext}), the back-channel origin and the host-address egress policy.
 */
@EnableGeneratorController
@DisplayName("ClientConfiguration transport settings")
class ClientConfigurationTransportTest {

    private static String issuer() {
        return "https://" + Generators.letterStrings(3, 12).next() + ".example.com";
    }

    private static ClientConfiguration.ClientConfigurationBuilder minimalBuilder() {
        return ClientConfiguration.builder()
                .issuer(issuer())
                .clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC);
    }

    static SSLContext defaultSslContext() {
        return assertDoesNotThrow(SSLContext::getDefault);
    }

    static SSLContext freshSslContext() {
        return assertDoesNotThrow(() -> {
            var context = SSLContext.getInstance("TLSv1.3");
            context.init(null, null, null);
            return context;
        });
    }

    /**
     * Allow-list entries that are not a bare host: blank, port-carrying, carrying a control
     * character, whitespace, a non-ASCII character or URL syntax.
     */
    static Stream<String> nonBareEgressHosts() {
        return Stream.of(
                "",
                "   ",
                "keycloak:8080",
                "[::1]:443",
                "keycloak\r\nX-Injected: yes",
                "key\u0000cloak",
                "key\tcloak",
                "k" + Character.toString(0x00E9) + "ycloak",
                "https://keycloak",
                "user@keycloak");
    }

    @Nested
    @DisplayName("Per-client TLS trust (sslContext)")
    class SslContextTest {

        @Test
        @DisplayName("Should leave the SSL context absent when the builder never sets one")
        void shouldDefaultToAbsentSslContext() {
            var config = minimalBuilder().build();

            assertNull(config.getSslContext(),
                    "an unconfigured sslContext means 'use the cui-http / JVM default truststore'");
        }

        @Test
        @DisplayName("Should return the supplied SSL context identically — trust material is never copied")
        void shouldReturnSuppliedSslContextIdentically() {
            var supplied = defaultSslContext();

            var config = minimalBuilder().sslContext(supplied).build();

            assertSame(supplied, config.getSslContext(),
                    "the caller's trust material must reach the transport as supplied");
        }

        @Test
        @DisplayName("Should exclude the SSL context from toString so a dump cannot expose the trust material")
        void shouldExcludeSslContextFromToString() {
            var supplied = defaultSslContext();
            var config = minimalBuilder().sslContext(supplied).build();

            String rendered = config.toString();

            assertAll("sslContext never appears in the string representation",
                    () -> assertFalse(rendered.contains(supplied.toString()),
                            "the SSLContext's own string form must not be rendered"),
                    () -> assertFalse(rendered.contains("sslContext"),
                            "the excluded field is not even named in the string representation"));
        }

        @Test
        @DisplayName("Should keep two configurations equal when they differ only in their SSL context")
        void shouldIgnoreSslContextForEquality() {
            var issuer = issuer();
            var clientId = Generators.nonBlankStrings().next();
            var withDefaultTrust = ClientConfiguration.builder()
                    .issuer(issuer).clientId(clientId).authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                    .sslContext(defaultSslContext()).build();
            var withPrivateTrust = ClientConfiguration.builder()
                    .issuer(issuer).clientId(clientId).authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                    .sslContext(freshSslContext()).build();
            var withoutTrust = ClientConfiguration.builder()
                    .issuer(issuer).clientId(clientId).authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC).build();

            // An SSLContext has identity semantics and no value form, so it is deliberately excluded
            // from the generated equals/hashCode — configurations stay comparable by their value fields.
            assertAll("sslContext is excluded from value equality by design",
                    () -> assertEquals(withDefaultTrust, withPrivateTrust),
                    () -> assertEquals(withDefaultTrust.hashCode(), withPrivateTrust.hashCode()),
                    () -> assertEquals(withDefaultTrust, withoutTrust),
                    () -> assertEquals(withDefaultTrust.hashCode(), withoutTrust.hashCode()));
        }
    }

    @Nested
    @DisplayName("Back-channel origin (backChannelOrigin)")
    class BackChannelOriginValidation {

        private ClientConfiguration.ClientConfigurationBuilder builderWithOrigin(String origin) {
            return minimalBuilder().backChannelOrigin(origin);
        }

        @Test
        @DisplayName("Should leave the back-channel origin absent when the builder never sets one")
        void shouldDefaultToAbsentOrigin() {
            var config = minimalBuilder().build();

            assertNull(config.getBackChannelOrigin(),
                    "an unconfigured back-channel origin means 'contact every endpoint where it is advertised'");
        }

        @ParameterizedTest(name = "accepts {0}")
        @ValueSource(strings = {"https://keycloak.internal", "http://keycloak:8080", "https://10.0.0.7:8443"})
        @DisplayName("Should accept a bare http(s) origin and store it as configured")
        void shouldAcceptBareOrigin(String origin) {
            var config = builderWithOrigin(origin).build();

            assertEquals(origin, config.getBackChannelOrigin());
        }

        @Test
        @DisplayName("Should accept a single trailing slash and normalise it away")
        void shouldNormaliseSingleTrailingSlash() {
            var config = builderWithOrigin("http://keycloak:8080/").build();

            assertEquals("http://keycloak:8080", config.getBackChannelOrigin(),
                    "the root path is not a path; the stored origin never ends with '/'");
        }

        @Test
        @DisplayName("Should keep the configured issuer when a back-channel origin is set")
        void shouldKeepIssuerUnchanged() {
            var issuer = issuer() + "/realms/demo";

            var config = ClientConfiguration.builder()
                    .issuer(issuer).clientId(Generators.nonBlankStrings().next())
                    .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                    .backChannelOrigin("http://keycloak:8080")
                    .build();

            assertAll("identity and reachability are separate values",
                    () -> assertEquals(issuer, config.getIssuer(),
                            "the issuer identity must not be rewritten by the back-channel origin"),
                    () -> assertEquals("http://keycloak:8080", config.getBackChannelOrigin()));
        }

        @ParameterizedTest(name = "rejects \"{0}\"")
        @ValueSource(strings = {
                "",
                "   ",
                "keycloak:8080",
                "/realms/demo",
                "ftp://keycloak.internal",
                "urn:example:keycloak",
                "https:///no-host",
                "https://keycloak.internal/realms/demo",
                "https://keycloak.internal//",
                "https://keycloak.internal?x=1",
                "https://keycloak.internal#frag",
                "https://user:secret@keycloak.internal",
                "https://keycloak.internal/ a path"
        })
        @DisplayName("Should reject every value that is not a bare absolute http(s) origin, naming the field")
        void shouldRejectNonOriginValue(String origin) {
            var builder = builderWithOrigin(origin);

            var exception = assertThrows(IllegalArgumentException.class, builder::build,
                    "a malformed back-channel origin must fail at build(), not on the wire");

            assertTrue(exception.getMessage().contains("backChannelOrigin"),
                    "the message must name backChannelOrigin, but was: " + exception.getMessage());
        }

        @ParameterizedTest(name = "does not echo the secret of \"{0}\"")
        @ValueSource(strings = {
                "https://user:%s@keycloak.internal",
                "ftp://user:%s@keycloak.internal",
                "https://user:%s@keycloak.internal/path",
                "https://user:%s@keycloak.internal?x=1",
                "https://user:%s@keycloak.internal#f",
                "https://user:%s@keycloak.internal/ a path",
                "https://keycloak.internal?token=%s",
                "https://keycloak.internal#%s",
                "https://keycloak.internal/%s"
        })
        @DisplayName("Should never echo the configured value, whichever check rejects it")
        void shouldNotEchoConfiguredValue(String template) {
            var secret = Generators.letterStrings(12, 20).next();
            var builder = builderWithOrigin(template.formatted(secret));

            var exception = assertThrows(IllegalArgumentException.class, builder::build);

            var messages = new ArrayList<String>();
            for (Throwable current = exception; current != null; current = current.getCause()) {
                messages.add(String.valueOf(current.getMessage()));
            }
            assertAll("the rejection names the field but never the value",
                    () -> assertTrue(exception.getMessage().contains("backChannelOrigin"),
                            "the message must name backChannelOrigin, but was: " + exception.getMessage()),
                    () -> assertTrue(messages.stream().noneMatch(message -> message.contains(secret)),
                            "the value may carry a credential and must never reach an exception message"
                                    + " or its cause chain"));
        }

        @Test
        @DisplayName("Should take the back-channel origin into value equality")
        void shouldIncludeOriginInEquality() {
            var issuer = issuer();
            var clientId = Generators.nonBlankStrings().next();
            var withOrigin = ClientConfiguration.builder()
                    .issuer(issuer).clientId(clientId).authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                    .backChannelOrigin("http://keycloak:8080").build();
            var withoutOrigin = ClientConfiguration.builder()
                    .issuer(issuer).clientId(clientId).authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC).build();

            assertNotEquals(withOrigin, withoutOrigin,
                    "a configuration that reaches the AS elsewhere is a different configuration");
        }
    }

    @Nested
    @DisplayName("Host-address egress policy (allowLoopbackEgress, allowedEgressHosts, egressHostResolver)")
    class EgressPolicyConfiguration {

        private static final URI LOOPBACK = URI.create("http://127.0.0.1:8080/token");
        private static final URI SITE_LOCAL = URI.create("http://10.0.0.1:8080/token");
        private static final URI METADATA = URI.create("http://169.254.169.254/latest");

        @Test
        @DisplayName("Should default to the secure policy: no loopback, no allow-list, no custom resolver")
        void shouldDefaultToSecurePolicy() {
            var config = minimalBuilder().build();
            var policy = config.getEgressPolicy();

            assertAll("every relaxation is off by default",
                    () -> assertFalse(config.isAllowLoopbackEgress()),
                    () -> assertTrue(config.getAllowedEgressHosts().isEmpty()),
                    () -> assertNull(config.getEgressHostResolver()),
                    () -> assertThrows(TransportException.class, () -> policy.check(LOOPBACK)),
                    () -> assertThrows(TransportException.class, () -> policy.check(SITE_LOCAL)),
                    () -> assertThrows(TransportException.class, () -> policy.check(METADATA)));
        }

        @Test
        @DisplayName("Should lift only the loopback refusal when allowLoopbackEgress is set")
        void shouldRelaxOnlyLoopback() {
            var config = minimalBuilder().allowLoopbackEgress(true).build();
            var policy = config.getEgressPolicy();

            assertAll("the loopback opt-in admits loopback and nothing else",
                    () -> assertTrue(config.isAllowLoopbackEgress()),
                    () -> assertDoesNotThrow(() -> policy.check(LOOPBACK)),
                    () -> assertThrows(TransportException.class, () -> policy.check(SITE_LOCAL)),
                    () -> assertThrows(TransportException.class, () -> policy.check(METADATA)));
        }

        @Test
        @DisplayName("Should admit exactly the allow-listed hosts, case-insensitively, and keep the list immutable")
        void shouldAdmitOnlyAllowListedHosts() {
            var config = minimalBuilder().allowedEgressHost("10.0.0.1").build();
            var policy = config.getEgressPolicy();
            var hosts = config.getAllowedEgressHosts();

            assertAll("the allow-list names what it admits",
                    () -> assertEquals(List.of("10.0.0.1"), hosts),
                    () -> assertDoesNotThrow(() -> policy.check(SITE_LOCAL)),
                    () -> assertThrows(TransportException.class, () -> policy.check(METADATA)),
                    () -> assertThrows(TransportException.class, () -> policy.check(LOOPBACK)),
                    () -> assertThrows(UnsupportedOperationException.class, () -> hosts.add("extra")));
        }

        @ParameterizedTest(name = "rejects allow-list entry #{index}")
        @NullSource
        @MethodSource("de.cuioss.sheriff.token.client.config.ClientConfigurationTransportTest#nonBareEgressHosts")
        @DisplayName("Should reject a null, blank, port-carrying or malformed allow-listed host without echoing it")
        void shouldRejectNonBareAllowedHost(String host) {
            var hosts = new ArrayList<String>();
            hosts.add(host);
            var builder = minimalBuilder().allowedEgressHosts(hosts);

            var exception = assertThrows(IllegalArgumentException.class, builder::build,
                    "an entry that is not a bare host matches no contacted host and only hides a misconfiguration");

            String message = exception.getMessage();
            assertAll("the refusal names the field and the entry index, never the entry",
                    () -> assertTrue(message.startsWith("allowedEgressHosts entry 0 is refused: "),
                            "the message must name the field and the zero-based index, but was: " + message),
                    () -> assertTrue(host == null || host.isBlank() || !message.contains(host),
                            "the rejected entry must never be echoed into the exception message"),
                    () -> assertTrue(message.chars().noneMatch(Character::isISOControl),
                            "the message must not carry any control character, but was: " + message),
                    () -> assertNull(exception.getCause(), "no cause may carry the entry"));
        }

        @Test
        @DisplayName("Should name the zero-based index of the first refused allow-list entry")
        void shouldNameIndexOfRefusedAllowedHost() {
            var builder = minimalBuilder().allowedEgressHost("keycloak").allowedEgressHost("keycloak:8080");

            var exception = assertThrows(IllegalArgumentException.class, builder::build);

            assertAll("the index points at the refused entry",
                    () -> assertTrue(exception.getMessage().startsWith("allowedEgressHosts entry 1 is refused: "),
                            "the second entry is the refused one, but was: " + exception.getMessage()),
                    () -> assertFalse(exception.getMessage().contains("keycloak"),
                            "neither entry may be echoed, but was: " + exception.getMessage()));
        }

        @Test
        @DisplayName("Should accept bare host names, IPv4 and IPv6 literals and store them in the canonical form")
        void shouldAcceptBareAllowedHostsInCanonicalForm() {
            var configured = List.of("Keycloak", "idp.internal", "10.0.0.5", "::1", "[2001:db8::1]");
            var builder = minimalBuilder();

            var config = builder.allowedEgressHosts(configured).build();
            var upperCase = builder.clearAllowedEgressHosts().allowedEgressHost("KEYCLOAK").build();
            var lowerCase = builder.clearAllowedEgressHosts().allowedEgressHost("keycloak").build();

            assertAll("the stored list holds the form EgressHostValidator.requireBareHost returns",
                    () -> assertEquals(List.of("keycloak", "idp.internal", "10.0.0.5", "[::1]", "[2001:db8::1]"),
                            config.getAllowedEgressHosts(),
                            "each entry is stored lower-case, a bare IPv6 literal in brackets"),
                    () -> assertEquals(lowerCase, upperCase,
                            "two configurations differing only in an entry's letter case are equal"));
        }

        @Test
        @DisplayName("Should admit a trailing-dot host as URI reports it and refuse a host URI cannot report")
        void shouldAdmitTrailingDotHostAndRefuseUnreportableHost() {
            EgressPolicy.HostResolver toSiteLocal = host -> new InetAddress[]{InetAddress.getByName("10.0.0.1")};
            var config = minimalBuilder().allowedEgressHost("idp.example.com.").egressHostResolver(toSiteLocal).build();
            var policy = config.getEgressPolicy();
            var unreportable = minimalBuilder().allowedEgressHost("idp.123");

            assertAll("the allow-list follows the host URI.getHost() reports",
                    () -> assertEquals(List.of("idp.example.com."), config.getAllowedEgressHosts()),
                    () -> assertDoesNotThrow(() -> policy.check(URI.create("https://idp.example.com./token")),
                            "the trailing-dot entry must match the trailing-dot host and bypass the range check"),
                    () -> assertThrows(IllegalArgumentException.class, unreportable::build,
                            "URI reports no host for idp.123, so the entry must fail at configuration time"));
        }

        @Test
        @DisplayName("Should consult an injected host resolver for the egress check")
        void shouldUseInjectedResolver() {
            EgressPolicy.HostResolver toMetadata = host -> new InetAddress[]{InetAddress.getByName("169.254.169.254")};
            var config = minimalBuilder().allowLoopbackEgress(true).egressHostResolver(toMetadata).build();
            var policy = config.getEgressPolicy();

            assertThrows(TransportException.class, () -> policy.check(LOOPBACK),
                    "the injected resolver maps the loopback host onto the metadata address, which stays refused");
        }

        @Test
        @DisplayName("Should take the egress relaxations into value equality but exclude the resolver")
        void shouldIncludeRelaxationsInEquality() {
            var issuer = issuer();
            var clientId = Generators.nonBlankStrings().next();
            var secure = ClientConfiguration.builder()
                    .issuer(issuer).clientId(clientId).authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC).build();
            var loopback = ClientConfiguration.builder()
                    .issuer(issuer).clientId(clientId).authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                    .allowLoopbackEgress(true).build();
            var allowListed = ClientConfiguration.builder()
                    .issuer(issuer).clientId(clientId).authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                    .allowedEgressHost("keycloak").build();
            var withResolver = ClientConfiguration.builder()
                    .issuer(issuer).clientId(clientId).authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                    .egressHostResolver(InetAddress::getAllByName).build();

            assertAll("each relaxation is part of the configuration's value",
                    () -> assertNotEquals(secure, loopback),
                    () -> assertNotEquals(secure, allowListed),
                    () -> assertEquals(secure, withResolver,
                            "a resolver has identity semantics and is excluded like the sslContext"));
        }
    }
}
