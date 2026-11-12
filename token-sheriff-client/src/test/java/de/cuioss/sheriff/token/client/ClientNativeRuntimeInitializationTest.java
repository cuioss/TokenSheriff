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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drift guard for the run-time initialization the client ships for GraalVM native image.
 * <p>
 * A class holding a {@link Random} (including {@link java.security.SecureRandom}) in a static
 * field must not be initialized at image build time: the seeded instance would be frozen into the
 * image heap and every process started from that image would share it. The client therefore lists
 * such classes in the {@code --initialize-at-run-time=} argument of
 * {@code META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-client/native-image.properties}.
 * <p>
 * The expected list is derived from the compiled main classes: every class declaring a static
 * field whose type is assignable to {@link Random}. Classes are loaded without being initialized,
 * so the scan itself creates no random generator. A newly added static generator without a
 * matching entry, and an entry left behind after its generator was removed, both fail here.
 *
 * @author Oliver Wolff
 */
@DisplayName("Client native-image run-time initialization")
class ClientNativeRuntimeInitializationTest {

    private static final String NATIVE_IMAGE_PROPERTIES =
            "META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-client/native-image.properties";
    private static final String ARGS_PROPERTY = "Args";
    private static final String INITIALIZE_AT_RUN_TIME = "--initialize-at-run-time=";

    private static final String FLOW_CONTEXT = "de.cuioss.sheriff.token.client.flow.FlowContext";
    private static final String PKCE_CHALLENGE = "de.cuioss.sheriff.token.client.flow.PkceChallenge";

    @Test
    @DisplayName("Should initialize exactly the classes holding a static Random at run time")
    void shouldInitializeExactlyTheStaticRandomHoldersAtRunTime() {
        SortedSet<String> holders = staticRandomHolders();
        SortedSet<String> declared = runTimeInitializedClasses();

        SortedSet<String> missing = new TreeSet<>(holders);
        missing.removeAll(declared);
        SortedSet<String> unexpected = new TreeSet<>(declared);
        unexpected.removeAll(holders);

        assertAll("--initialize-at-run-time lists exactly the static Random holders",
                () -> assertTrue(missing.isEmpty(),
                        "Classes holding a static Random absent from --initialize-at-run-time: " + missing),
                () -> assertTrue(unexpected.isEmpty(),
                        "Classes listed in --initialize-at-run-time holding no static Random: " + unexpected));
    }

    @Test
    @DisplayName("Should find the known static Random holders in the compiled main classes")
    void shouldFindTheKnownStaticRandomHolders() {
        SortedSet<String> holders = staticRandomHolders();

        assertAll("the static Random enumeration is not vacuous",
                () -> assertTrue(holders.contains(FLOW_CONTEXT),
                        "Enumeration should contain " + FLOW_CONTEXT + ": " + holders),
                () -> assertTrue(holders.contains(PKCE_CHALLENGE),
                        "Enumeration should contain " + PKCE_CHALLENGE + ": " + holders));
    }

    @Test
    @DisplayName("Should ship native-image.properties with an --initialize-at-run-time argument")
    void shouldShipNativeImagePropertiesWithRunTimeInitialization() {
        String properties = NativeImageClassScanner.readResource(NATIVE_IMAGE_PROPERTIES);

        assertAll("native-image.properties is shipped and declares run-time initialization",
                () -> assertTrue(properties.contains(INITIALIZE_AT_RUN_TIME),
                        "native-image.properties should declare " + INITIALIZE_AT_RUN_TIME),
                () -> assertFalse(runTimeInitializedClasses().isEmpty(),
                        "--initialize-at-run-time should list at least one class"));
    }

    @Test
    @DisplayName("Should parse every comma-separated class of the run-time argument")
    void shouldParseEveryCommaSeparatedClassOfTheRunTimeArgument() {
        String properties = "Args=--enable-url-protocols=https --initialize-at-run-time=a.B,c.D\n";

        SortedSet<String> parsed = parseRunTimeInitializedClasses(properties);

        assertEquals(new TreeSet<>(List.of("a.B", "c.D")), parsed,
                "Only the classes of the --initialize-at-run-time argument should be parsed");
    }

    private static SortedSet<String> staticRandomHolders() {
        SortedSet<String> holders = new TreeSet<>();
        ClassLoader loader = NativeImageClassScanner.classLoader();
        for (String className : NativeImageClassScanner.mainClassNames()) {
            if (declaresStaticRandom(loadUninitialized(className, loader))) {
                holders.add(className);
            }
        }
        return holders;
    }

    private static Class<?> loadUninitialized(String className, ClassLoader loader) {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Compiled main class is not loadable: " + className, e);
        }
    }

    private static boolean declaresStaticRandom(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Random.class.isAssignableFrom(field.getType())) {
                return true;
            }
        }
        return false;
    }

    private static SortedSet<String> runTimeInitializedClasses() {
        return parseRunTimeInitializedClasses(NativeImageClassScanner.readResource(NATIVE_IMAGE_PROPERTIES));
    }

    private static SortedSet<String> parseRunTimeInitializedClasses(String propertiesContent) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(propertiesContent));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to parse native-image.properties", e);
        }
        SortedSet<String> classes = new TreeSet<>();
        for (String argument : properties.getProperty(ARGS_PROPERTY, "").trim().split("\\s+")) {
            if (argument.startsWith(INITIALIZE_AT_RUN_TIME)) {
                for (String className : argument.substring(INITIALIZE_AT_RUN_TIME.length()).split(",")) {
                    if (!className.isBlank()) {
                        classes.add(className.trim());
                    }
                }
            }
        }
        return classes;
    }
}
