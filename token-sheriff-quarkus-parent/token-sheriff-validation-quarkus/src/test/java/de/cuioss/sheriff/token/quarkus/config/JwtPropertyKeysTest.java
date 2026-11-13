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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtPropertyKeys")
class JwtPropertyKeysTest {

    @Nested
    @DisplayName("Base Properties")
    class BaseProperties {

        @Test
        @DisplayName("should have correct prefix")
        void shouldHaveCorrectPrefix() {
            assertEquals("sheriff.token", JwtPropertyKeys.PREFIX);
        }

        @Test
        @DisplayName("should have correct dot jwks suffix")
        void shouldHaveCorrectDotJwksSuffix() {
            assertEquals(".jwks", JwtPropertyKeys.DOT_JWKS);
        }
    }

    @Nested
    @DisplayName("Parser Properties")
    class ParserProperties {

        @Test
        @DisplayName("should have correct base path")
        void shouldHaveCorrectBasePath() {
            assertEquals("sheriff.token.parser", JwtPropertyKeys.PARSER.BASE);
        }

        @Test
        @DisplayName("should have parser properties with correct prefix")
        void shouldHaveParserPropertiesWithCorrectPrefix() {
            assertTrue(JwtPropertyKeys.PARSER.MAX_TOKEN_SIZE.startsWith(JwtPropertyKeys.PARSER.BASE));
            assertTrue(JwtPropertyKeys.PARSER.MAX_PAYLOAD_SIZE.startsWith(JwtPropertyKeys.PARSER.BASE));
            assertTrue(JwtPropertyKeys.PARSER.MAX_STRING_LENGTH.startsWith(JwtPropertyKeys.PARSER.BASE));
        }
    }

    @Nested
    @DisplayName("Issuer Properties")
    class IssuerProperties {

        @Test
        @DisplayName("should format issuer base property correctly")
        void shouldFormatIssuerBasePropertyCorrectly() {
            var issuerName = "default";
            var expected = "sheriff.token.issuers.default.";
            var actual = JwtPropertyKeys.ISSUERS.BASE.formatted(issuerName);

            assertEquals(expected, actual);
        }

        @Test
        @DisplayName("should format issuer enabled property correctly")
        void shouldFormatIssuerEnabledPropertyCorrectly() {
            var issuerName = "keycloak";
            var expected = "sheriff.token.issuers.keycloak.enabled";
            var actual = JwtPropertyKeys.ISSUERS.ENABLED.formatted(issuerName);

            assertEquals(expected, actual);
        }

        @Test
        @DisplayName("should format JWKS URL property correctly")
        void shouldFormatJwksUrlPropertyCorrectly() {
            var issuerName = "auth0";
            var expected = "sheriff.token.issuers.auth0.jwks.http.url";
            var actual = JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(issuerName);

            assertEquals(expected, actual);
        }

        @Test
        @DisplayName("should format well-known URL property correctly")
        void shouldFormatWellKnownUrlPropertyCorrectly() {
            var issuerName = "oidc";
            var expected = "sheriff.token.issuers.oidc.jwks.http.well-known-url";
            var actual = JwtPropertyKeys.ISSUERS.WELL_KNOWN_URL.formatted(issuerName);

            assertEquals(expected, actual);
        }

        @Test
        @DisplayName("should format max staleness property correctly")
        void shouldFormatMaxStalenessPropertyCorrectly() {
            var issuerName = "keycloak";
            var expected = "sheriff.token.issuers.keycloak.jwks.http.max-staleness-seconds";
            var actual = JwtPropertyKeys.ISSUERS.MAX_STALENESS_SECONDS.formatted(issuerName);

            assertEquals(expected, actual);
        }

        @Test
        @DisplayName("should place max staleness in the same key group as the refresh interval")
        void shouldPlaceMaxStalenessBesideRefreshInterval() {
            var refreshInterval = JwtPropertyKeys.ISSUERS.REFRESH_INTERVAL_SECONDS;
            var httpGroup = refreshInterval.substring(0, refreshInterval.lastIndexOf('.') + 1);

            assertTrue(JwtPropertyKeys.ISSUERS.MAX_STALENESS_SECONDS.startsWith(httpGroup),
                    "max-staleness-seconds must share the jwks.http key group with refresh-interval-seconds");
        }

        @Test
        @DisplayName("should format unknown-kid refresh window property correctly")
        void shouldFormatUnknownKidRefreshWindowPropertyCorrectly() {
            var issuerName = "keycloak";
            var expected = "sheriff.token.issuers.keycloak.jwks.http.unknown-kid-refresh-window-seconds";
            var actual = JwtPropertyKeys.ISSUERS.UNKNOWN_KID_REFRESH_WINDOW_SECONDS.formatted(issuerName);

            assertEquals(expected, actual);
        }

        @Test
        @DisplayName("should format initial-load retry property correctly")
        void shouldFormatInitialLoadRetryPropertyCorrectly() {
            var issuerName = "keycloak";
            var expected = "sheriff.token.issuers.keycloak.jwks.http.initial-load-retry";
            var actual = JwtPropertyKeys.ISSUERS.INITIAL_LOAD_RETRY.formatted(issuerName);

            assertEquals(expected, actual);
        }

        @Test
        @DisplayName("should place the unknown-kid and initial-load keys in the same key group as the refresh interval")
        void shouldPlaceLoaderOptInsBesideRefreshInterval() {
            var refreshInterval = JwtPropertyKeys.ISSUERS.REFRESH_INTERVAL_SECONDS;
            var httpGroup = refreshInterval.substring(0, refreshInterval.lastIndexOf('.') + 1);

            assertTrue(JwtPropertyKeys.ISSUERS.UNKNOWN_KID_REFRESH_WINDOW_SECONDS.startsWith(httpGroup),
                    "unknown-kid-refresh-window-seconds must share the jwks.http key group with refresh-interval-seconds");
            assertTrue(JwtPropertyKeys.ISSUERS.INITIAL_LOAD_RETRY.startsWith(httpGroup),
                    "initial-load-retry must share the jwks.http key group with refresh-interval-seconds");
        }
    }

    /**
     * Guards {@link JwtPropertyKeys.ISSUERS#HTTP_ONLY_PROPERTIES} against drift from the key definitions.
     * <p>
     * The population is derived from the {@code public static final String} templates that
     * {@link JwtPropertyKeys.ISSUERS} declares under {@link JwtPropertyKeys.ISSUERS#HTTP_BASE}, not repeated
     * here. Apart from the two source selectors, every such key must be classified: either it is HTTP-only
     * (an orphaned value fails startup) or it is one of the HTTP tuning keys explicitly tolerated on a
     * non-HTTP issuer. A newly added {@code jwks.http.*} key therefore fails this guard until it is
     * classified in one of the two sets.
     * </p>
     */
    @Nested
    @DisplayName("HTTP-only property list")
    class HttpOnlyPropertyList {

        /** The source selectors: they select HTTP JWKS loading and so are neither HTTP-only nor tolerated. */
        private static final Set<String> SOURCE_SELECTORS = Set.of(
                JwtPropertyKeys.ISSUERS.JWKS_URL,
                JwtPropertyKeys.ISSUERS.WELL_KNOWN_URL);

        /**
         * The {@code jwks.http.*} tuning keys that are tolerated on an issuer without HTTP JWKS loading,
         * i.e. deliberately not part of {@link JwtPropertyKeys.ISSUERS#HTTP_ONLY_PROPERTIES}.
         */
        private static final Set<String> TOLERATED_HTTP_TUNING_KEYS = Set.of(
                JwtPropertyKeys.ISSUERS.REFRESH_INTERVAL_SECONDS,
                JwtPropertyKeys.ISSUERS.CONNECT_TIMEOUT_SECONDS,
                JwtPropertyKeys.ISSUERS.READ_TIMEOUT_SECONDS,
                JwtPropertyKeys.ISSUERS.KEY_ROTATION_GRACE_PERIOD_SECONDS,
                JwtPropertyKeys.ISSUERS.MAX_RETIRED_KEY_SETS,
                JwtPropertyKeys.ISSUERS.ALLOW_INSECURE_HTTP,
                JwtPropertyKeys.ISSUERS.VERIFY_HOSTNAME,
                JwtPropertyKeys.ISSUERS.ALLOW_LOOPBACK_EGRESS,
                JwtPropertyKeys.ISSUERS.ALLOWED_EGRESS_HOSTS);

        /**
         * Every {@code public static final String} key template that {@link JwtPropertyKeys.ISSUERS} declares
         * under {@link JwtPropertyKeys.ISSUERS#HTTP_BASE}, the base template itself excluded.
         */
        private static Set<String> declaredHttpKeys() {
            Set<String> keys = new TreeSet<>();
            for (Field field : JwtPropertyKeys.ISSUERS.class.getFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers) || field.getType() != String.class) {
                    continue;
                }
                try {
                    String value = (String) field.get(null);
                    if (value.startsWith(JwtPropertyKeys.ISSUERS.HTTP_BASE)
                            && !JwtPropertyKeys.ISSUERS.HTTP_BASE.equals(value)) {
                        keys.add(value);
                    }
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot read public constant " + field.getName(), e);
                }
            }
            return keys;
        }

        /**
         * The keys of {@code declared} that are neither a source selector, nor HTTP-only, nor tolerated.
         */
        private static List<String> unclassified(Collection<String> declared) {
            List<String> result = new ArrayList<>();
            for (String key : declared) {
                if (!SOURCE_SELECTORS.contains(key)
                        && !JwtPropertyKeys.ISSUERS.HTTP_ONLY_PROPERTIES.contains(key)
                        && !TOLERATED_HTTP_TUNING_KEYS.contains(key)) {
                    result.add(key);
                }
            }
            return result;
        }

        @Test
        @DisplayName("should derive a non-empty jwks.http key population holding both source selectors")
        void shouldDeriveNonEmptyPopulation() {
            Set<String> declared = declaredHttpKeys();

            assertAll("Derived jwks.http population",
                    () -> assertTrue(declared.containsAll(SOURCE_SELECTORS),
                            "The derived population must contain both source selectors: " + declared),
                    () -> assertTrue(declared.containsAll(JwtPropertyKeys.ISSUERS.HTTP_ONLY_PROPERTIES),
                            "The derived population must contain every HTTP-only property: " + declared),
                    () -> assertTrue(declared.containsAll(TOLERATED_HTTP_TUNING_KEYS),
                            "Every tolerated tuning key must be a declared jwks.http key: " + declared));
        }

        @Test
        @DisplayName("should classify every declared jwks.http key as HTTP-only or tolerated")
        void shouldClassifyEveryDeclaredHttpKey() {
            List<String> unclassified = unclassified(declaredHttpKeys());

            assertTrue(unclassified.isEmpty(),
                    "Every jwks.http key other than the source selectors must be listed in "
                            + "JwtPropertyKeys.ISSUERS.HTTP_ONLY_PROPERTIES or in the tolerated tuning keys "
                            + "of this test; unclassified: " + unclassified);
        }

        @Test
        @DisplayName("should keep the HTTP-only list disjoint from the source selectors and the tolerated keys")
        void shouldKeepHttpOnlyListDisjoint() {
            for (String key : JwtPropertyKeys.ISSUERS.HTTP_ONLY_PROPERTIES) {
                assertAll(key,
                        () -> assertFalse(SOURCE_SELECTORS.contains(key),
                                "A source selector must not be HTTP-only: " + key),
                        () -> assertFalse(TOLERATED_HTTP_TUNING_KEYS.contains(key),
                                "A key must not be both HTTP-only and tolerated: " + key));
            }
        }

        @Test
        @DisplayName("should list only declared jwks.http keys, each once, in the HTTP-only list")
        void shouldListOnlyDeclaredHttpKeys() {
            Set<String> declared = declaredHttpKeys();
            List<String> httpOnly = JwtPropertyKeys.ISSUERS.HTTP_ONLY_PROPERTIES;

            assertAll("HTTP-only list content",
                    () -> assertEquals(httpOnly.size(), Set.copyOf(httpOnly).size(),
                            "The HTTP-only list must not repeat a key: " + httpOnly),
                    () -> assertTrue(declared.containsAll(httpOnly),
                            "The HTTP-only list must contain nothing but declared jwks.http keys: " + httpOnly));
        }

        @Test
        @DisplayName("should report a newly added jwks.http key that is not classified")
        void shouldReportUnclassifiedNewKey() {
            String newKey = JwtPropertyKeys.ISSUERS.HTTP_BASE + "some-new-setting";
            Set<String> declared = new TreeSet<>(declaredHttpKeys());
            declared.add(newKey);

            assertEquals(List.of(newKey), unclassified(declared),
                    "A jwks.http key outside both sets must be reported as unclassified");
        }
    }

    @Nested
    @DisplayName("Keycloak Properties")
    class KeycloakProperties {

        @Test
        @DisplayName("should have correct keycloak base path")
        void shouldHaveCorrectKeycloakBasePath() {
            assertEquals("sheriff.token.keycloak", JwtPropertyKeys.KEYCLOAK.BASE);
        }

        @Test
        @DisplayName("should have correct default roles mapper enabled property")
        void shouldHaveCorrectDefaultRolesMapperEnabledProperty() {
            assertEquals("sheriff.token.keycloak.default-roles-mapper.enabled",
                    JwtPropertyKeys.KEYCLOAK.DEFAULT_ROLES_MAPPER_ENABLED);
        }

        @Test
        @DisplayName("should have correct default groups mapper enabled property")
        void shouldHaveCorrectDefaultGroupsMapperEnabledProperty() {
            assertEquals("sheriff.token.keycloak.default-groups-mapper.enabled",
                    JwtPropertyKeys.KEYCLOAK.DEFAULT_GROUPS_MAPPER_ENABLED);
        }
    }

    @Nested
    @DisplayName("Health Properties")
    class HealthProperties {

        @Test
        @DisplayName("should have correct health base path")
        void shouldHaveCorrectHealthBasePath() {
            assertEquals("sheriff.token.health", JwtPropertyKeys.HEALTH.BASE);
        }

        @Test
        @DisplayName("should have correct JWKS health properties")
        void shouldHaveCorrectJwksHealthProperties() {
            assertEquals("sheriff.token.health.jwks", JwtPropertyKeys.HEALTH.JWKS.BASE);
            assertTrue(JwtPropertyKeys.HEALTH.JWKS.CACHE_SECONDS.startsWith(JwtPropertyKeys.HEALTH.JWKS.BASE));
        }

        @Test
        @DisplayName("should have correct external-validator reporting properties")
        void shouldHaveCorrectExternalValidatorReportingProperties() {
            assertEquals("sheriff.token.health.jwks.report-external-validator",
                    JwtPropertyKeys.HEALTH.JWKS.REPORT_EXTERNAL_VALIDATOR);
            assertEquals("sheriff.token.health.jwks.disclose-issuer",
                    JwtPropertyKeys.HEALTH.JWKS.DISCLOSE_ISSUER);
        }
    }
}