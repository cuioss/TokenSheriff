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
package de.cuioss.sheriff.token.client;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drift guard for the reflection metadata the client ships for GraalVM native image.
 * <p>
 * The DSL-JSON annotation processor emits one {@code _<Type>_DslJsonConverter} per
 * {@code @CompiledJson} type, and DSL-JSON locates those converters reflectively at run time. In a
 * native image each converter, and the type it instantiates, must therefore be registered in
 * {@code META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-client/reflect-config.json}.
 * <p>
 * The expected registration set is derived from the compiled main classes rather than written
 * out: every converter found in the main output, plus the type each one serves. The served type is
 * read off the converter name ({@code pkg._Outer$Inner_DslJsonConverter} serves
 * {@code pkg.Outer$Inner}), so the guard does not depend on {@code @CompiledJson} being retained at
 * run time. A converter added without a registration, and a registration left behind after its
 * type was removed, both fail here.
 *
 * @author Oliver Wolff
 */
@DisplayName("Client native-image reflection metadata")
class ClientNativeReflectionMetadataTest {

    private static final String REFLECT_CONFIG =
            "META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-client/reflect-config.json";
    private static final String NAME_ATTRIBUTE = "name";
    private static final Set<String> CONVERTER_FLAGS = Set.of("allDeclaredConstructors");

    /**
     * Matches the processor's converter naming on the part of the binary name after the package.
     * The anchor on {@code _DslJsonConverter} excludes the nested
     * {@code _<Type>_DslJsonConverter$ObjectFormatConverter} classes, which DSL-JSON reaches
     * through the outer converter rather than reflectively.
     */
    private static final Pattern CONVERTER_NAME = Pattern.compile("_(.+)_DslJsonConverter");

    private static final String TOKEN_ERROR_RESPONSE_CONVERTER =
            "de.cuioss.sheriff.token.client.token._TokenErrorResponse_DslJsonConverter";
    private static final String JSON_OBJECT_PROBE_CONVERTER =
            "de.cuioss.sheriff.token.client.internal._AccessTokenConfirmation$JsonObjectProbe_DslJsonConverter";

    @Test
    @DisplayName("Should register exactly the compiled converters and the types they serve")
    void shouldRegisterExactlyTheCompiledConvertersAndTheirTypes() {
        SortedSet<String> expected = expectedRegistrations(compiledConverters());
        Set<String> registered = new TreeSet<>(registeredNames());

        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(registered);
        Set<String> unexpected = new TreeSet<>(registered);
        unexpected.removeAll(expected);

        assertAll("reflect-config.json registers exactly the compiled converters and their types",
                () -> assertTrue(missing.isEmpty(),
                        "Compiled converters or served types absent from reflect-config.json: " + missing),
                () -> assertTrue(unexpected.isEmpty(),
                        "Names registered in reflect-config.json with no compiled converter: " + unexpected));
    }

    @Test
    @DisplayName("Should find the known converters in the compiled main classes")
    void shouldFindTheKnownConvertersInTheCompiledMainClasses() {
        SortedSet<String> converters = compiledConverters();

        assertAll("the converter enumeration is not vacuous",
                () -> assertTrue(converters.contains(TOKEN_ERROR_RESPONSE_CONVERTER),
                        "Enumeration should contain " + TOKEN_ERROR_RESPONSE_CONVERTER + ": " + converters),
                () -> assertTrue(converters.contains(JSON_OBJECT_PROBE_CONVERTER),
                        "Enumeration should contain " + JSON_OBJECT_PROBE_CONVERTER + ": " + converters));
    }

    @Test
    @DisplayName("Should resolve every registered name on the client classpath")
    void shouldResolveEveryRegisteredNameOnTheClientClasspath() {
        List<String> names = registeredNames();

        List<Executable> assertions = new ArrayList<>(names.size());
        for (String name : names) {
            assertions.add(() -> assertDoesNotThrow(
                    () -> Class.forName(name, false, NativeImageClassScanner.classLoader()),
                    "Registered type is not resolvable on the client classpath: " + name));
        }
        assertAll("every registered type resolves", assertions);
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
    @DisplayName("Should enable only allDeclaredConstructors for every registered name")
    void shouldEnableOnlyAllDeclaredConstructorsForEveryRegisteredName() {
        Map<String, Set<String>> declared = declaredReflectionFlags(readReflectConfig());

        List<Executable> assertions = new ArrayList<>(declared.size());
        for (Map.Entry<String, Set<String>> entry : declared.entrySet()) {
            assertions.add(() -> assertEquals(new TreeSet<>(CONVERTER_FLAGS), entry.getValue(),
                    "Reflection flags should be exactly " + CONVERTER_FLAGS + " for " + entry.getKey()));
        }
        assertAll("every registered name enables only its declared constructors", assertions);
    }

    private static SortedSet<String> compiledConverters() {
        SortedSet<String> converters = new TreeSet<>();
        for (String className : NativeImageClassScanner.mainClassNames()) {
            if (CONVERTER_NAME.matcher(simplePart(className)).matches()) {
                converters.add(className);
            }
        }
        return converters;
    }

    private static SortedSet<String> expectedRegistrations(Set<String> converters) {
        SortedSet<String> expected = new TreeSet<>(converters);
        for (String converter : converters) {
            Matcher matcher = CONVERTER_NAME.matcher(simplePart(converter));
            assertTrue(matcher.matches(), "Not a converter name: " + converter);
            expected.add(packagePrefix(converter) + matcher.group(1));
        }
        return expected;
    }

    private static String simplePart(String binaryName) {
        return binaryName.substring(binaryName.lastIndexOf('.') + 1);
    }

    private static String packagePrefix(String binaryName) {
        return binaryName.substring(0, binaryName.lastIndexOf('.') + 1);
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
     * Reads the reflection flags each entry enables, discovered from the file: every attribute
     * except {@code name} counts unless it is an explicit {@code false}. A flag the guard has never
     * heard of therefore surfaces as a widening instead of being ignored. A {@code condition}
     * attribute is deliberately counted as well: a conditional entry is registered only when its
     * condition type is reachable in the native application, so the exact-set guard must not treat
     * a conditional entry as an unconditional registration.
     *
     * @param entries the parsed reflect-config entries
     * @return the enabled flags per registered name
     */
    private static Map<String, Set<String>> declaredReflectionFlags(JsonArray entries) {
        Map<String, Set<String>> flagsByName = new LinkedHashMap<>();
        for (JsonValue entry : entries) {
            JsonObject object = entry.asJsonObject();
            Set<String> enabledFlags = new TreeSet<>();
            for (Map.Entry<String, JsonValue> attribute : object.entrySet()) {
                if (!NAME_ATTRIBUTE.equals(attribute.getKey())
                        && attribute.getValue().getValueType() != JsonValue.ValueType.FALSE) {
                    enabledFlags.add(attribute.getKey());
                }
            }
            flagsByName.put(object.getString(NAME_ATTRIBUTE), enabledFlags);
        }
        return flagsByName;
    }

    private static JsonArray readReflectConfig() {
        try (JsonReader reader = Json.createReader(
                     new StringReader(NativeImageClassScanner.readResource(REFLECT_CONFIG)))) {
            return reader.readArray();
        }
    }
}
