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

import de.cuioss.sheriff.token.quarkus.test.TestConfig;
import io.smallrye.config.EnvConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ConfigValueParser")
class ConfigValueParserTest {

    @Nested
    @DisplayName("splitCsv")
    class SplitCsv {

        @Test
        @DisplayName("should split and trim comma-separated values")
        void shouldSplitAndTrim() {
            assertEquals(List.of("a", "b", "c"), ConfigValueParser.splitCsv("a, b ,c"));
        }

        @Test
        @DisplayName("should filter empty segments consistently")
        void shouldFilterEmptySegments() {
            assertEquals(List.of("a", "b"), ConfigValueParser.splitCsv("a,,b"));
            assertEquals(List.of("a", "b"), ConfigValueParser.splitCsv(",a, ,b,"));
            assertEquals(List.of(), ConfigValueParser.splitCsv(""));
            assertEquals(List.of(), ConfigValueParser.splitCsv(" , ,"));
        }

        @Test
        @DisplayName("should handle single value without separator")
        void shouldHandleSingleValue() {
            assertEquals(List.of("only"), ConfigValueParser.splitCsv(" only "));
        }
    }

    @Nested
    @DisplayName("discoverNameSegments")
    class DiscoverNameSegments {

        @Test
        @DisplayName("should discover name segments after prefix")
        void shouldDiscoverNameSegments() {
            TestConfig config = new TestConfig(Map.of(
                    "sheriff.token.issuers.default.enabled", "true",
                    "sheriff.token.issuers.default.issuer-identifier", "https://example.com",
                    "sheriff.token.issuers.keycloak.enabled", "false",
                    "sheriff.token.other.property", "value"
            ));

            Set<String> names = ConfigValueParser.discoverNameSegments(config, "sheriff.token.issuers.");

            assertEquals(Set.of("default", "keycloak"), names);
        }

        @Test
        @DisplayName("should ignore properties without a dot after the prefix")
        void shouldIgnorePropertiesWithoutDot() {
            TestConfig config = new TestConfig(Map.of(
                    "prefix.name.sub", "value",
                    "prefix.plain", "value"
            ));

            Set<String> names = ConfigValueParser.discoverNameSegments(config, "prefix.");

            assertEquals(Set.of("name"), names);
        }

        @Test
        @DisplayName("should return empty set when no properties match")
        void shouldReturnEmptySetWhenNoMatch() {
            TestConfig config = new TestConfig(Map.of("unrelated.property", "value"));

            assertTrue(ConfigValueParser.discoverNameSegments(config, "prefix.").isEmpty());
        }
    }

    @Nested
    @DisplayName("discoverEnvNameSegments")
    class DiscoverEnvNameSegments {

        private static final String ISSUERS_PREFIX = "sheriff.token.issuers.";
        private static final List<String> ANCHORS = List.of("dpop.enabled", "enabled");

        @Test
        @DisplayName("should discover an issuer configured only through environment variables")
        void shouldDiscoverEnvOnlyIssuer() {
            Config config = envConfig(Map.of(
                    "SHERIFF_TOKEN_ISSUERS_MY_KEYCLOAK_ENABLED", "true",
                    "SHERIFF_TOKEN_ISSUERS_MY_KEYCLOAK_ISSUER_IDENTIFIER", "https://keycloak.example.com"));

            Set<String> names = ConfigValueParser.discoverEnvNameSegments(config, ISSUERS_PREFIX, ANCHORS, Set.of());

            assertEquals(Set.of("my_keycloak"), names);
        }

        @Test
        @DisplayName("should not create a phantom issuer from _DPOP_ENABLED")
        void shouldNotCreatePhantomIssuerFromDpopEnabled() {
            Config config = envConfig(Map.of(
                    "SHERIFF_TOKEN_ISSUERS_KEYCLOAK_ENABLED", "true",
                    "SHERIFF_TOKEN_ISSUERS_KEYCLOAK_DPOP_ENABLED", "true"));

            Set<String> names = ConfigValueParser.discoverEnvNameSegments(config, ISSUERS_PREFIX, ANCHORS, Set.of());

            assertEquals(Set.of("keycloak"), names);
        }

        @Test
        @DisplayName("should drop the environment form of an issuer already discovered in dotted form")
        void shouldDropEnvFormOfDottedIssuer() {
            Config config = new SmallRyeConfigBuilder()
                    .withDefaultValues(Map.of("sheriff.token.issuers.my-idp.enabled", "true"))
                    .withSources(new EnvConfigSource(Map.of("SHERIFF_TOKEN_ISSUERS_MY_IDP_ENABLED", "true"), 300))
                    .build();
            Set<String> dotted = ConfigValueParser.discoverNameSegments(config, ISSUERS_PREFIX);

            Set<String> names = ConfigValueParser.discoverEnvNameSegments(config, ISSUERS_PREFIX, ANCHORS, dotted);

            assertTrue(dotted.contains("my-idp"), "Dotted discovery must find my-idp: " + dotted);
            assertTrue(names.isEmpty(), "Env form of my-idp must not be reported again: " + names);
        }

        @Test
        @DisplayName("should not report an empty issuer name when nothing lies between the prefix and the anchor")
        void shouldNotReportEmptyNameBetweenPrefixAndAnchor() {
            // The remainder after the prefix is exactly "_ENABLED", the anchor suffix itself, so
            // stripping the anchor would leave the empty string as a name.
            Config config = envConfig(Map.of(
                    "SHERIFF_TOKEN_ISSUERS__ENABLED", "true",
                    "SHERIFF_TOKEN_ISSUERS_KEYCLOAK_ENABLED", "true"));

            Set<String> names = ConfigValueParser.discoverEnvNameSegments(config, ISSUERS_PREFIX, ANCHORS, Set.of());

            assertEquals(Set.of("keycloak"), names);
        }

        @Test
        @DisplayName("should ignore environment variables outside the prefix")
        void shouldIgnoreEnvVarsOutsidePrefix() {
            Config config = envConfig(Map.of(
                    "SHERIFF_TOKEN_OTHER_ENABLED", "true",
                    "UNRELATED_ISSUERS_FOO_ENABLED", "true"));

            Set<String> names = ConfigValueParser.discoverEnvNameSegments(config, ISSUERS_PREFIX, ANCHORS, Set.of());

            assertTrue(names.isEmpty(), "No name expected outside the prefix: " + names);
        }

        private Config envConfig(Map<String, String> envVars) {
            return new SmallRyeConfigBuilder()
                    .withSources(new EnvConfigSource(envVars, 300))
                    .build();
        }
    }
}
