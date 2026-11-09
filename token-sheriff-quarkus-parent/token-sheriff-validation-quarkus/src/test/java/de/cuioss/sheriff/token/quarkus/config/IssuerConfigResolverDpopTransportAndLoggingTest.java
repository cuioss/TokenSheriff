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
package de.cuioss.sheriff.token.quarkus.config;

import de.cuioss.http.client.adapter.RetryConfig;
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.quarkus.test.TestConfig;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.dpop.DpopConfig;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.smallrye.config.EnvConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static de.cuioss.sheriff.token.quarkus.TokenSheriffQuarkusLogMessages.INFO;
import static de.cuioss.test.juli.LogAsserts.assertLogMessagePresent;
import static de.cuioss.test.juli.LogAsserts.assertLogMessagePresentContaining;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests IssuerConfigResolver functionality: DPoP configuration, the secure transport opt-ins, logging,
 * and issuer discovery from environment variables. Split out of {@link IssuerConfigResolverTest} by topic.
 */
@DisplayName("IssuerConfigResolver: DPoP, transport, logging and environment")
@EnableTestLogger
class IssuerConfigResolverDpopTransportAndLoggingTest {

    private static final String TEST_ISSUER = "test";
    private static final String ANOTHER_ISSUER = "another";

    @Nested
    @DisplayName("DPoP Configuration (RFC 9449)")
    class DpopConfiguration {

        @Test
        @DisplayName("should configure DPoP when enabled with defaults")
        void shouldConfigureDpopWhenEnabled() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "https://example.com",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                    JwtPropertyKeys.ISSUERS.DPOP_ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(1, result.size());
            DpopConfig dpopConfig = result.getFirst().getDpopConfig();
            assertNotNull(dpopConfig, "DPoP config should be present when enabled");
            assertFalse(dpopConfig.isRequired(), "Should default to not required");
            assertEquals(DpopConfig.DEFAULT_PROOF_MAX_AGE_SECONDS, dpopConfig.getProofMaxAgeSeconds());
            assertEquals(DpopConfig.DEFAULT_NONCE_CACHE_SIZE, dpopConfig.getNonceCacheSize());
            assertEquals(DpopConfig.DEFAULT_NONCE_CACHE_TTL_SECONDS, dpopConfig.getNonceCacheTtlSeconds());
        }

        @Test
        @DisplayName("should not configure DPoP when disabled")
        void shouldNotConfigureDpopWhenDisabled() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "https://example.com",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                    JwtPropertyKeys.ISSUERS.DPOP_ENABLED.formatted(TEST_ISSUER), "false",
                    JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(1, result.size());
            assertNull(result.getFirst().getDpopConfig(), "DPoP config should be null when disabled");
        }

        @Test
        @DisplayName("should not configure DPoP when not specified")
        void shouldNotConfigureDpopWhenNotSpecified() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "https://example.com",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                    JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(1, result.size());
            assertNull(result.getFirst().getDpopConfig(), "DPoP config should be null when not specified");
        }

        @Test
        @DisplayName("should configure DPoP with custom values")
        void shouldConfigureDpopWithCustomValues() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "https://example.com",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                    JwtPropertyKeys.ISSUERS.DPOP_ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.DPOP_REQUIRED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.DPOP_PROOF_MAX_AGE_SECONDS.formatted(TEST_ISSUER), "120",
                    JwtPropertyKeys.ISSUERS.DPOP_NONCE_CACHE_SIZE.formatted(TEST_ISSUER), "5000",
                    JwtPropertyKeys.ISSUERS.DPOP_NONCE_CACHE_TTL_SECONDS.formatted(TEST_ISSUER), "600",
                    JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(1, result.size());
            DpopConfig dpopConfig = result.getFirst().getDpopConfig();
            assertNotNull(dpopConfig, "DPoP config should be present");
            assertTrue(dpopConfig.isRequired(), "Should be required");
            assertEquals(120, dpopConfig.getProofMaxAgeSeconds());
            assertEquals(5000, dpopConfig.getNonceCacheSize());
            assertEquals(600, dpopConfig.getNonceCacheTtlSeconds());
        }

        @Test
        @DisplayName("should fail fast when dpop.required is set without dpop.enabled")
        void shouldFailWhenDpopRequiredWithoutEnabled() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "https://example.com",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                    JwtPropertyKeys.ISSUERS.DPOP_REQUIRED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    resolver::resolveIssuerConfigs,
                    "Should fail fast instead of silently disabling DPoP");
            assertTrue(exception.getMessage().contains(
                            JwtPropertyKeys.ISSUERS.DPOP_REQUIRED.formatted(TEST_ISSUER)),
                    "Exception should name the offending property: " + exception.getMessage());
            assertTrue(exception.getMessage().contains(
                            JwtPropertyKeys.ISSUERS.DPOP_ENABLED.formatted(TEST_ISSUER)),
                    "Exception should instruct to set dpop.enabled=true: " + exception.getMessage());
        }

        @Test
        @DisplayName("should fail fast when a dpop sub-property is set with dpop.enabled=false")
        void shouldFailWhenDpopSubPropertyWithEnabledFalse() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "https://example.com",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                    JwtPropertyKeys.ISSUERS.DPOP_ENABLED.formatted(TEST_ISSUER), "false",
                    JwtPropertyKeys.ISSUERS.DPOP_PROOF_MAX_AGE_SECONDS.formatted(TEST_ISSUER), "120",
                    JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    resolver::resolveIssuerConfigs,
                    "Should fail fast instead of silently disabling DPoP");
            assertTrue(exception.getMessage().contains(
                            JwtPropertyKeys.ISSUERS.DPOP_PROOF_MAX_AGE_SECONDS.formatted(TEST_ISSUER)),
                    "Exception should name the offending property: " + exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Secure Transport Opt-Ins (H1/C1)")
    @EnableTestLogger(debug = IssuerConfigResolver.class)
    class SecureTransportOptIns {

        @Test
        @DisplayName("should reject cleartext http JWKS URL by default (H1 secure default)")
        void shouldRejectInsecureHttpByDefault() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "http://localhost:8080/realms/test",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "http://localhost:8080/jwks",
                    JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            assertThrows(IllegalArgumentException.class, resolver::resolveIssuerConfigs,
                    "Cleartext http JWKS URL must be rejected when allow-insecure-http is unset (default false)");
        }

        @Test
        @DisplayName("should accept cleartext http JWKS URL when allow-insecure-http=true")
        void shouldAllowInsecureHttpWhenOptedIn() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "http://localhost:8080/realms/test",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "http://localhost:8080/jwks",
                    JwtPropertyKeys.ISSUERS.ALLOW_INSECURE_HTTP.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ALLOW_LOOPBACK_EGRESS.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(1, result.size(), "Should resolve one issuer");
            assertNotNull(result.getFirst().getJwksLoader(),
                    "Should have JWKS loader when cleartext http is opted in");
            assertLogMessagePresentContaining(TestLogLevel.DEBUG,
                    "Enabled cleartext HTTP for " + TEST_ISSUER);
            assertLogMessagePresentContaining(TestLogLevel.DEBUG,
                    "Enabled loopback egress for " + TEST_ISSUER);
        }

        @Test
        @DisplayName("should reject cleartext http well-known URL by default (H1 secure default)")
        void shouldRejectInsecureWellKnownByDefault() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "http://localhost:8080/realms/test",
                    JwtPropertyKeys.ISSUERS.WELL_KNOWN_URL.formatted(TEST_ISSUER),
                    "http://localhost:8080/realms/test/.well-known/openid-configuration",
                    JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            assertThrows(IllegalArgumentException.class, resolver::resolveIssuerConfigs,
                    "Cleartext http well-known URL must be rejected when allow-insecure-http is unset (default false)");
        }

        @Test
        @DisplayName("should accept cleartext http well-known URL when opted in")
        void shouldAllowInsecureWellKnownWhenOptedIn() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "http://localhost:8080/realms/test",
                    JwtPropertyKeys.ISSUERS.WELL_KNOWN_URL.formatted(TEST_ISSUER),
                    "http://localhost:8080/realms/test/.well-known/openid-configuration",
                    JwtPropertyKeys.ISSUERS.ALLOW_INSECURE_HTTP.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ALLOW_LOOPBACK_EGRESS.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(1, result.size(), "Should resolve one issuer");
            assertNotNull(result.getFirst().getJwksLoader(),
                    "Should have JWKS loader when cleartext http well-known is opted in");
        }

        @Test
        @DisplayName("should thread allowed-egress-hosts allow-list into the JWKS config")
        void shouldConfigureAllowedEgressHosts() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "https://keycloak:8443/realms/test",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER),
                    "https://keycloak:8443/realms/test/protocol/openid-connect/certs",
                    JwtPropertyKeys.ISSUERS.ALLOWED_EGRESS_HOSTS.formatted(TEST_ISSUER), "keycloak, dex",
                    JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(1, result.size(), "Should resolve one issuer");
            assertNotNull(result.getFirst().getJwksLoader(),
                    "Should have JWKS loader when egress hosts are allow-listed");
            assertLogMessagePresentContaining(TestLogLevel.DEBUG,
                    "Allow-listed egress hosts for " + TEST_ISSUER);
        }

        /**
         * Negative control of the verify-hostname matched pair. Unlike its sibling opt-ins,
         * verify-hostname defaults to ON, so the resolver reads it with {@code .orElse(true)}.
         * Copying the sibling {@code .orElse(false)} shape would silently disable hostname
         * verification by default; this assertion is what turns that inversion red.
         */
        @Test
        @DisplayName("should keep hostname verification enforced when verify-hostname is unset")
        void shouldVerifyHostnameByDefault() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "https://example.com",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                    JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            HttpJwksLoaderConfig httpConfig = resolver.createHttpJwksLoaderConfig(
                    TEST_ISSUER, "https://example.com/jwks", null, Optional.of("https://example.com"));

            assertTrue(httpConfig.getHttpHandler().isVerifyHostname(),
                    "Omitting verify-hostname must leave TLS hostname verification enforced");
        }

        @Test
        @DisplayName("should relax hostname verification when verify-hostname=false")
        void shouldDisableHostnameVerificationWhenOptedOut() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "https://example.com",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                    JwtPropertyKeys.ISSUERS.VERIFY_HOSTNAME.formatted(TEST_ISSUER), "false",
                    JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            HttpJwksLoaderConfig httpConfig = resolver.createHttpJwksLoaderConfig(
                    TEST_ISSUER, "https://example.com/jwks", null, Optional.of("https://example.com"));

            assertFalse(httpConfig.getHttpHandler().isVerifyHostname(),
                    "verify-hostname=false must reach the built JWKS HttpHandler");
            assertLogMessagePresentContaining(TestLogLevel.DEBUG,
                    "Disabled TLS hostname verification for " + TEST_ISSUER);
        }
    }

    @Nested
    @DisplayName("Logging Validation")
    @EnableTestLogger(debug = IssuerConfigResolver.class)
    class LoggingValidation {

        @Test
        @DisplayName("should log discovery and resolution process")
        void shouldLogDiscoveryAndResolution() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "https://example.com",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                    JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            resolver.resolveIssuerConfigs();

            assertLogMessagePresent(TestLogLevel.INFO, INFO.RESOLVING_ISSUER_CONFIGURATIONS.format());
            assertLogMessagePresentContaining(TestLogLevel.DEBUG, "Discovered issuer names:");
            assertLogMessagePresent(TestLogLevel.INFO, INFO.RESOLVED_ISSUER_CONFIGURATION.format(TEST_ISSUER));
            assertLogMessagePresent(TestLogLevel.INFO, INFO.RESOLVED_ENABLED_ISSUER_CONFIGURATIONS.format("1"));
        }

        @Test
        @DisplayName("should log JWKS source configuration")
        void shouldLogJwksSourceConfiguration() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "https://example.com",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                    JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            resolver.resolveIssuerConfigs();

            assertLogMessagePresentContaining(TestLogLevel.DEBUG, "Configured HTTP JWKS URL for " + TEST_ISSUER);
        }

        @Test
        @DisplayName("should log when skipping disabled issuers")
        void shouldLogDisabledIssuerSkipping() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "https://example.com",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                    JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(ANOTHER_ISSUER), "false",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(ANOTHER_ISSUER), "https://other.com",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(ANOTHER_ISSUER), "https://other.com/jwks"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            resolver.resolveIssuerConfigs();

            assertLogMessagePresentContaining(TestLogLevel.DEBUG, "Skipping disabled issuer: " + ANOTHER_ISSUER);
            assertLogMessagePresent(TestLogLevel.INFO, INFO.RESOLVED_ISSUER_CONFIGURATION.format(TEST_ISSUER));
        }

        @Test
        @DisplayName("should log claimSubOptional configuration")
        void shouldLogClaimSubOptionalConfiguration() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "https://example.com",
                    JwtPropertyKeys.ISSUERS.CLAIM_SUB_OPTIONAL.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                    JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            resolver.resolveIssuerConfigs();

            assertLogMessagePresentContaining(TestLogLevel.DEBUG, "Set claim subject optional for " + TEST_ISSUER + ": true");
        }
    }

    @Nested
    @DisplayName("Environment Variable Discovery")
    class EnvironmentVariableDiscovery {

        /**
         * A multi-word name: its environment form {@code ENV_IDP} cannot be split back into
         * {@code env-idp} by a dotted scan, which is what the environment discovery is for.
         */
        private static final String ENV_ISSUER = "env-idp";
        private static final String MIXED_ISSUER = "mixed";
        private static final String JWKS_CONTENT = "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"kid\":\"env-key-1\",\"alg\":\"RS256\",\"n\":\"0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw\",\"e\":\"AQAB\"}]}";

        @Test
        @DisplayName("should resolve an issuer configured only through environment variables")
        void shouldResolveEnvOnlyIssuer() {
            Config config = new SmallRyeConfigBuilder()
                    .withSources(new EnvConfigSource(envOnlyIssuer(ENV_ISSUER, "https://env.example.com"), 300))
                    .build();
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(1, result.size(), "Env-only issuer should be resolved");
            assertEquals("https://env.example.com", result.getFirst().getIssuerIdentifier());
            assertTrue(IssuerConfigResolver.hasEnabledIssuers(config),
                    "hasEnabledIssuers must see the env-only issuer");
        }

        @Test
        @DisplayName("should yield exactly one issuer per logical name for mixed properties and env vars")
        void shouldYieldOneIssuerPerNameForMixedConfiguration() {
            Map<String, String> envVars = new HashMap<>(envOnlyIssuer(ENV_ISSUER, "https://env.example.com"));
            envVars.put(toEnvName(JwtPropertyKeys.ISSUERS.ENABLED.formatted(MIXED_ISSUER)), "true");
            Config config = new SmallRyeConfigBuilder()
                    .withDefaultValues(Map.of(
                            JwtPropertyKeys.ISSUERS.ENABLED.formatted(MIXED_ISSUER), "true",
                            JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(MIXED_ISSUER), "https://mixed.example.com",
                            JwtPropertyKeys.ISSUERS.JWKS_CONTENT.formatted(MIXED_ISSUER), JWKS_CONTENT,
                            JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(MIXED_ISSUER), "true"))
                    .withSources(new EnvConfigSource(envVars, 300))
                    .build();
            IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(List.of("https://mixed.example.com", "https://env.example.com"),
                    result.stream().map(IssuerConfig::getIssuerIdentifier).toList(),
                    "Each logical issuer must be resolved exactly once, dotted names first");
        }

        private Map<String, String> envOnlyIssuer(String issuerName, String issuerIdentifier) {
            return Map.of(
                    toEnvName(JwtPropertyKeys.ISSUERS.ENABLED.formatted(issuerName)), "true",
                    toEnvName(JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(issuerName)), issuerIdentifier,
                    toEnvName(JwtPropertyKeys.ISSUERS.JWKS_CONTENT.formatted(issuerName)), JWKS_CONTENT,
                    toEnvName(JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(issuerName)), "true");
        }

        private String toEnvName(String dottedName) {
            return dottedName.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
        }
    }
}
