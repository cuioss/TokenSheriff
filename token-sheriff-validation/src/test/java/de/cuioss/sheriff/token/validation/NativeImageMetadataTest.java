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
package de.cuioss.sheriff.token.validation;

import jakarta.json.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drift guard for the GraalVM native-image metadata this module ships.
 * <p>
 * The core library declares its own reflection contract under
 * {@code META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-validation/}, where GraalVM
 * auto-detects it from the packaged jar. Because that contract is a hand-maintained list of class
 * names, it can silently rot when a listed type is renamed, moved or deleted. This test resolves
 * every listed name against the module's own classpath so such a drift fails the build here rather
 * than at native-image build time in a downstream consumer.
 * <p>
 * The shipped contract is additionally pinned exactly: the registered type names and the reflection
 * flags each type declares are written out literally in {@link #EXPECTED_REFLECTION_FLAGS} and
 * compared against the file. A presence check alone would accept a silently added registration or a
 * silently widened reflection flag, both of which enlarge the native-image surface without review.
 *
 * @author Oliver Wolff
 */
@DisplayName("Tests for the shipped native-image metadata")
class NativeImageMetadataTest {

    private static final String METADATA_DIR =
            "META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-validation/";
    private static final String REFLECT_CONFIG = METADATA_DIR + "reflect-config.json";
    private static final String NATIVE_IMAGE_PROPERTIES = METADATA_DIR + "native-image.properties";
    private static final String CORE_PACKAGE_PREFIX = "de.cuioss.sheriff.token.";
    private static final String RUNTIME_INITIALIZED_CLASS =
            "de.cuioss.sheriff.token.validation.jwks.http.HttpJwksLoader";
    private static final String NAME_ATTRIBUTE = "name";
    private static final String CONDITION_ATTRIBUTE = "condition";
    private static final String ALL_DECLARED_METHODS = "allDeclaredMethods";
    private static final String ALL_DECLARED_FIELDS = "allDeclaredFields";
    private static final String ALL_DECLARED_CONSTRUCTORS = "allDeclaredConstructors";

    private static final Set<String> METHODS_ONLY = Set.of(ALL_DECLARED_METHODS);
    private static final Set<String> CONSTRUCTORS_ONLY = Set.of(ALL_DECLARED_CONSTRUCTORS);
    private static final Set<String> METHODS_AND_CONSTRUCTORS =
            Set.of(ALL_DECLARED_METHODS, ALL_DECLARED_CONSTRUCTORS);
    private static final Set<String> METHODS_FIELDS_AND_CONSTRUCTORS =
            Set.of(ALL_DECLARED_METHODS, ALL_DECLARED_FIELDS, ALL_DECLARED_CONSTRUCTORS);

    /**
     * The reflection contract the core module ships, written out literally.
     * <p>
     * The key set is the exact set of registered type names; each value is the exact set of
     * reflection flags that type enables. Both are the assertion, so neither may be derived from
     * {@code reflect-config.json} — a value read back out of the file under test would agree with
     * it unconditionally and pin nothing.
     */
    private static final Map<String, Set<String>> EXPECTED_REFLECTION_FLAGS = Map.ofEntries(
            Map.entry("de.cuioss.sheriff.token.validation.TokenValidator", METHODS_AND_CONSTRUCTORS),
            Map.entry("de.cuioss.sheriff.token.validation.IssuerConfigCache", METHODS_AND_CONSTRUCTORS),
            Map.entry("de.cuioss.sheriff.token.commons.events.SecurityEventCounter", METHODS_AND_CONSTRUCTORS),
            Map.entry("de.cuioss.sheriff.token.validation.IssuerConfig", METHODS_ONLY),
            Map.entry("de.cuioss.sheriff.token.commons.transport.ParserConfig", METHODS_ONLY),
            Map.entry("de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig", METHODS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.pipeline.NonValidatingJwtParser", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.pipeline.validator.TokenSignatureValidator", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.pipeline.validator.TokenHeaderValidator", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.pipeline.validator.TokenClaimValidator", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.pipeline.TokenBuilder", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.pipeline.DecodedJwt", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.jwks.http.HttpJwksLoader", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.jwks.key.JWKSKeyLoader", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.jwks.key.KeyInfo", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.jwks.parser.JwksParser", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.security.SignatureAlgorithmPreferences", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.security.JwkAlgorithmPreferences", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.jwe.JweDecryptor", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.jwe.JweAlgorithmPreferences", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent", METHODS_FIELDS_AND_CONSTRUCTORS),
            Map.entry("de.cuioss.sheriff.token.validation.domain.token.IdTokenContent", METHODS_FIELDS_AND_CONSTRUCTORS),
            Map.entry("de.cuioss.sheriff.token.validation.domain.token.UnvalidatedRefreshToken", METHODS_FIELDS_AND_CONSTRUCTORS),
            Map.entry("de.cuioss.sheriff.token.validation.domain.token.TokenContent", METHODS_FIELDS_AND_CONSTRUCTORS),
            Map.entry("de.cuioss.sheriff.token.validation.domain.token.BaseTokenContent", METHODS_FIELDS_AND_CONSTRUCTORS),
            Map.entry("de.cuioss.sheriff.token.validation.domain.token.MinimalTokenContent", METHODS_FIELDS_AND_CONSTRUCTORS),
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.ClaimValue", METHODS_FIELDS_AND_CONSTRUCTORS),
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.ClaimName", METHODS_FIELDS_AND_CONSTRUCTORS),
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.ClaimValueType", METHODS_FIELDS_AND_CONSTRUCTORS),
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.mapper.IdentityMapper", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.mapper.JsonCollectionMapper", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.mapper.KeycloakDefaultGroupsMapper", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.mapper.KeycloakDefaultRolesMapper", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.mapper.OffsetDateTimeMapper", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.mapper.ScopeMapper", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.mapper.StringSplitterMapper", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.mapper.StrictStringMapper", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.commons.transport._WellKnownResult_DslJsonConverter", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.commons.transport._Jwks_DslJsonConverter", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.commons.transport._JwkKey_DslJsonConverter", CONSTRUCTORS_ONLY),
            Map.entry("de.cuioss.sheriff.token.validation.json._JwtHeader_DslJsonConverter", CONSTRUCTORS_ONLY));

    @Nested
    @DisplayName("reflect-config.json")
    class ReflectConfig {

        @Test
        @DisplayName("Should parse as a JSON array of objects each carrying a name")
        void shouldParseAsArrayOfObjectsEachCarryingAName() {
            JsonArray entries = readReflectConfig();

            assertFalse(entries.isEmpty(), "reflect-config.json should declare at least one entry");
            List<Executable> assertions = new ArrayList<>(entries.size());
            for (JsonValue entry : entries) {
                assertions.add(() -> {
                    assertEquals(JsonValue.ValueType.OBJECT, entry.getValueType(),
                            "Entry should be a JSON object: " + entry);
                    JsonObject object = entry.asJsonObject();
                    assertTrue(object.containsKey(NAME_ATTRIBUTE),
                            "Entry should carry a name attribute: " + object);
                    assertFalse(object.getString(NAME_ATTRIBUTE).isBlank(),
                            "Entry name should not be blank: " + object);
                });
            }
            assertAll("every entry is an object carrying a non-blank name", assertions);
        }

        @Test
        @DisplayName("Should resolve every listed name on the core module classpath")
        void shouldResolveEveryListedNameOnTheCoreClasspath() {
            List<String> names = registeredNames();

            List<Executable> assertions = new ArrayList<>(names.size());
            for (String name : names) {
                assertions.add(() -> assertDoesNotThrow(
                        () -> Class.forName(name, false, classLoader()),
                        "Registered type is not resolvable on the core classpath: " + name));
            }
            assertAll("every registered type resolves", assertions);
        }

        @Test
        @DisplayName("Should list core types only")
        void shouldListCoreTypesOnly() {
            List<String> names = registeredNames();

            List<Executable> assertions = new ArrayList<>(names.size());
            for (String name : names) {
                assertions.add(() -> assertTrue(name.startsWith(CORE_PACKAGE_PREFIX),
                        "Only core types belong in the core reflection contract: " + name));
            }
            assertAll("every registered type is core-owned", assertions);
        }

        @Test
        @DisplayName("Should list every name at most once")
        void shouldListEveryNameAtMostOnce() {
            List<String> names = registeredNames();

            Set<String> distinctNames = new HashSet<>(names);

            assertEquals(distinctNames.size(), names.size(),
                    "reflect-config.json should not declare the same name twice: " + names);
        }

        @Test
        @DisplayName("Should register exactly the expected set of names")
        void shouldRegisterExactlyTheExpectedSetOfNames() {
            Set<String> registered = new TreeSet<>(registeredNames());

            Set<String> missing = new TreeSet<>(EXPECTED_REFLECTION_FLAGS.keySet());
            missing.removeAll(registered);
            Set<String> unexpected = new TreeSet<>(registered);
            unexpected.removeAll(EXPECTED_REFLECTION_FLAGS.keySet());

            assertAll("reflect-config.json registers exactly the expected names",
                    () -> assertTrue(missing.isEmpty(),
                            "Expected names absent from reflect-config.json: " + missing),
                    () -> assertTrue(unexpected.isEmpty(),
                            "Names registered in reflect-config.json but not expected: " + unexpected));
        }

        @Test
        @DisplayName("Should declare the expected reflection flags for every registered name")
        void shouldDeclareTheExpectedReflectionFlagsForEveryRegisteredName() {
            Map<String, Set<String>> declared = declaredReflectionFlags(readReflectConfig());

            List<Executable> assertions = new ArrayList<>(EXPECTED_REFLECTION_FLAGS.size());
            for (Map.Entry<String, Set<String>> expected : EXPECTED_REFLECTION_FLAGS.entrySet()) {
                assertions.add(() -> assertEquals(new TreeSet<>(expected.getValue()),
                        declared.get(expected.getKey()),
                        "Reflection flags should match the expected contract for " + expected.getKey()));
            }
            assertAll("every registered name declares its expected reflection flags", assertions);
        }
    }

    @Nested
    @DisplayName("reflection-flag discovery")
    class ReflectionFlagDiscovery {

        @Test
        @DisplayName("Should count a non-boolean widening and ignore an explicit false")
        void shouldCountNonBooleanWideningAndIgnoreExplicitFalse() {
            JsonArray entries = parseEntries("""
                    [
                      {"name": "com.example.Widened", "methods": [{"name": "foo"}]},
                      {"name": "com.example.Disabled", "allDeclaredFields": false}
                    ]
                    """);

            Map<String, Set<String>> declared = declaredReflectionFlags(entries);

            assertAll("a non-boolean widening is declared, an explicit false is not",
                    () -> assertEquals(Set.of("methods"), declared.get("com.example.Widened"),
                            "A non-boolean methods widening enlarges the reflective surface exactly as "
                                    + "a boolean flag does and must surface as a declared flag"),
                    () -> assertEquals(Set.<String>of(), declared.get("com.example.Disabled"),
                            "An explicit false enables nothing, so it must not surface as a declared flag"));
        }

        @Test
        @DisplayName("Should ignore the condition attribute while still counting real flags")
        void shouldIgnoreConditionAttribute() {
            JsonArray entries = parseEntries("""
                    [
                      {"name": "com.example.Conditional",
                       "condition": {"typeReached": "com.example.Trigger"},
                       "allDeclaredMethods": true}
                    ]
                    """);

            Map<String, Set<String>> declared = declaredReflectionFlags(entries);

            assertEquals(METHODS_ONLY, declared.get("com.example.Conditional"),
                    "The condition attribute gates when an entry applies rather than granting "
                            + "reflective access, so it must not surface as a declared flag");
        }
    }

    @Nested
    @DisplayName("native-image.properties")
    class NativeImageProperties {

        @Test
        @DisplayName("Should be shipped and initialize HttpJwksLoader at run time")
        void shouldBeShippedAndInitializeHttpJwksLoaderAtRunTime() {
            String properties = readResource(NATIVE_IMAGE_PROPERTIES);

            assertTrue(properties.contains("--initialize-at-run-time=" + RUNTIME_INITIALIZED_CLASS),
                    "native-image.properties should declare --initialize-at-run-time="
                            + RUNTIME_INITIALIZED_CLASS);
        }
    }

    private static List<String> registeredNames() {
        JsonArray entries = readReflectConfig();
        List<String> names = new ArrayList<>(entries.size());
        for (JsonValue entry : entries) {
            names.add(entry.asJsonObject().getString(NAME_ATTRIBUTE));
        }
        return names;
    }

    /**
     * Reads the reflection flags each entry actually enables, discovered from the file rather than
     * looked up against a fixed list of attribute names.
     * <p>
     * Every attribute except {@code name} and {@code condition} counts unless it is an explicit
     * {@code false}. GraalVM's {@code condition} attribute gates when an entry is applied rather
     * than granting reflective access, so counting it would fail the comparison against
     * {@link #EXPECTED_REFLECTION_FLAGS} over metadata that widens nothing. Discovering
     * them is what lets the caller catch a widened flag it has never heard of: an entry that adds
     * {@code "allPublicMethods": true} surfaces as an unexpected member of that type's declared set
     * and fails the comparison against {@link #EXPECTED_REFLECTION_FLAGS}, where iterating a fixed
     * list of the three known attributes would have ignored it and reported green.
     * <p>
     * Counting every non-{@code false} value, rather than only boolean {@code true}, is what extends
     * that reach to GraalVM's non-boolean widenings. {@code "methods": [...]} and
     * {@code "fields": [...]} enlarge the reflective surface exactly as a boolean flag does, so a
     * boolean-only predicate would have let one be added while this test stayed green.
     * <p>
     * A flag written as an explicit {@code false} is deliberately not collected: it enables nothing
     * and enlarges no surface, so treating it as declared would fail the build over a no-op.
     *
     * @param entries the parsed reflect-config entries to read. Taken as a parameter so the
     * discovery can be exercised against synthetic entries without mutating the shipped file.
     * @return the set of flags each registered name declares, keyed by name
     */
    private static Map<String, Set<String>> declaredReflectionFlags(JsonArray entries) {
        Map<String, Set<String>> flagsByName = new LinkedHashMap<>();
        for (JsonValue entry : entries) {
            JsonObject object = entry.asJsonObject();
            Set<String> enabledFlags = new TreeSet<>();
            for (Map.Entry<String, JsonValue> attribute : object.entrySet()) {
                if (!NAME_ATTRIBUTE.equals(attribute.getKey())
                        && !CONDITION_ATTRIBUTE.equals(attribute.getKey())
                        && attribute.getValue().getValueType() != JsonValue.ValueType.FALSE) {
                    enabledFlags.add(attribute.getKey());
                }
            }
            flagsByName.put(object.getString(NAME_ATTRIBUTE), enabledFlags);
        }
        return flagsByName;
    }

    private static JsonArray readReflectConfig() {
        return parseEntries(readResource(REFLECT_CONFIG));
    }

    private static JsonArray parseEntries(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readArray();
        }
    }

    private static String readResource(String resource) {
        try (InputStream stream = classLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, "Shipped native-image metadata is missing: " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read " + resource, e);
        }
    }

    private static ClassLoader classLoader() {
        return NativeImageMetadataTest.class.getClassLoader();
    }
}
