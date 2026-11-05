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

import de.cuioss.sheriff.token.commons.transport.TransportLogMessages;
import de.cuioss.tools.logging.LogRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the log identifiers of every message class of this module that shares the {@code TokenSheriff}
 * prefix — today {@link TransportLogMessages} and {@link JWTValidationLogMessages}. Every record must carry
 * a unique rendered identifier, and every identifier must lie in the range of its severity class.
 * <p>
 * The holder set is not hardcoded: it is derived from the module's compiled classes, read from the code
 * source location of the known holders (a classes directory or a jar). A class is a holder when one of its
 * nested classes declares a {@code public static final} {@link LogRecord} with exactly the
 * {@value #TOKEN_SHERIFF_PREFIX} prefix, so a new holder is covered by both checks without being registered
 * here.
 * <p>
 * Placed in the {@code validation} test package because the {@code commons} layer must not depend on
 * {@code validation}.
 */
@DisplayName("Log message identifiers of the TokenSheriff-prefix message classes")
class LogMessageIdentifierUniquenessTest {

    /** The log prefix whose identifier space the guard protects. */
    static final String TOKEN_SHERIFF_PREFIX = "TokenSheriff";

    /** Identifier ranges per severity class, as documented in doc/LogMessages.adoc. */
    private static final Map<String, int[]> SEVERITY_RANGES = Map.of(
            "INFO", new int[]{1, 99},
            "WARN", new int[]{100, 199},
            "ERROR", new int[]{200, 299});

    /** Holders known at the time of writing; the discovery must find at least these. */
    private static final List<Class<?>> KNOWN_HOLDERS = List.of(TransportLogMessages.class, JWTValidationLogMessages.class);

    private static final String CLASS_FILE_SUFFIX = ".class";

    private static final List<Class<?>> MESSAGE_CLASSES = discoverHolders();

    @Test
    @DisplayName("Every rendered identifier is used by exactly one record")
    void shouldUseEveryIdentifierOnce() {
        Map<String, List<String>> fieldsByIdentifier = new TreeMap<>();
        for (NamedRecord namedRecord : collectRecords()) {
            fieldsByIdentifier.computeIfAbsent(namedRecord.logRecord().resolveIdentifierString(), key -> new ArrayList<>())
                    .add(namedRecord.fieldName());
        }

        String duplicates = fieldsByIdentifier.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + " used by " + entry.getValue())
                .collect(Collectors.joining("; "));

        assertTrue(duplicates.isEmpty(), "Duplicate log identifiers: " + duplicates);
    }

    @Test
    @DisplayName("Every identifier lies in the range of its severity class")
    void shouldKeepEveryIdentifierInItsSeverityRange() {
        List<String> violations = new ArrayList<>();
        for (NamedRecord namedRecord : collectRecords()) {
            int[] range = SEVERITY_RANGES.get(namedRecord.severity());
            if (range == null) {
                violations.add(namedRecord.fieldName() + " belongs to a severity class without a documented range");
                continue;
            }
            int identifier = namedRecord.logRecord().getIdentifier();
            if (identifier < range[0] || identifier > range[1]) {
                violations.add("%s has identifier %s outside %s-%s".formatted(
                        namedRecord.fieldName(), identifier, range[0], range[1]));
            }
        }

        assertTrue(violations.isEmpty(), "Identifiers outside their severity range: " + violations);
    }

    @Test
    @DisplayName("The derived holder set contains the known holders and every holder is covered by the checks")
    void shouldDeriveTheHolderSetFromTheCompiledClasses() {
        assertTrue(MESSAGE_CLASSES.containsAll(KNOWN_HOLDERS),
                "The discovery must find the known holders %s, found %s".formatted(names(KNOWN_HOLDERS), names(MESSAGE_CLASSES)));

        List<NamedRecord> records = collectRecords();
        String uncovered = MESSAGE_CLASSES.stream()
                .filter(holder -> records.stream().noneMatch(namedRecord -> namedRecord.owner() == holder))
                .map(Class::getName)
                .collect(Collectors.joining(", "));
        assertTrue(uncovered.isEmpty(), "TokenSheriff-prefix holders not covered by the identifier checks: " + uncovered);
    }

    @Test
    @DisplayName("Every severity class of every message class contributes records")
    void shouldCollectRecordsOfEverySeverityClass() {
        List<NamedRecord> records = collectRecords();
        for (Class<?> messageClass : MESSAGE_CLASSES) {
            for (String severity : declaredSeverityClasses(messageClass)) {
                long collected = records.stream()
                        .filter(namedRecord -> namedRecord.owner() == messageClass && namedRecord.severity().equals(severity))
                        .count();
                assertTrue(collected > 0, "No record collected from %s.%s".formatted(
                        messageClass.getSimpleName(), severity));
            }
        }
        assertAll("both message classes expose the severity classes the guard relies on",
                () -> assertTrue(declaredSeverityClasses(JWTValidationLogMessages.class)
                        .containsAll(SEVERITY_RANGES.keySet()), "JWTValidationLogMessages must declare INFO, WARN and ERROR"),
                () -> assertTrue(declaredSeverityClasses(TransportLogMessages.class)
                        .containsAll(List.of("WARN", "ERROR")), "TransportLogMessages must declare WARN and ERROR"));
    }

    private static List<String> declaredSeverityClasses(Class<?> messageClass) {
        List<String> names = new ArrayList<>();
        for (Class<?> nested : messageClass.getDeclaredClasses()) {
            names.add(nested.getSimpleName());
        }
        return names;
    }

    private static List<NamedRecord> collectRecords() {
        List<NamedRecord> records = new ArrayList<>();
        for (Class<?> messageClass : MESSAGE_CLASSES) {
            for (Class<?> severityClass : messageClass.getDeclaredClasses()) {
                for (Field field : severityClass.getDeclaredFields()) {
                    if (isPublicConstantLogRecord(field)) {
                        records.add(new NamedRecord(messageClass, severityClass.getSimpleName(),
                                "%s.%s.%s".formatted(messageClass.getSimpleName(), severityClass.getSimpleName(), field.getName()),
                                readRecord(field)));
                    }
                }
            }
        }
        assertFalse(records.isEmpty(), "No LogRecord collected, the guard would pass vacuously");
        return records;
    }

    /**
     * Derives the holder set from the compiled classes at the code source locations of the known holders.
     *
     * @return the holders, sorted by name
     */
    private static List<Class<?>> discoverHolders() {
        Set<Path> locations = new LinkedHashSet<>();
        for (Class<?> knownHolder : KNOWN_HOLDERS) {
            locations.add(codeSourceLocation(knownHolder));
        }
        Set<String> classNames = new TreeSet<>();
        for (Path location : locations) {
            classNames.addAll(compiledClassNames(location));
        }
        List<Class<?>> holders = new ArrayList<>();
        for (String className : classNames) {
            Class<?> candidate = loadWithoutInitializing(className);
            if (isTokenSheriffHolder(candidate)) {
                holders.add(candidate);
            }
        }
        holders.sort(Comparator.comparing(Class::getName));
        return List.copyOf(holders);
    }

    private static Path codeSourceLocation(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError("Cannot resolve the code source location of " + type.getName(), e);
        }
    }

    /**
     * Lists the binary names of the classes compiled into a classes directory or a jar.
     */
    private static List<String> compiledClassNames(Path location) {
        try {
            if (Files.isDirectory(location)) {
                return classNamesBelow(location);
            }
            try (FileSystem jar = FileSystems.newFileSystem(location)) {
                return classNamesBelow(jar.getPath("/"));
            }
        } catch (IOException e) {
            throw new AssertionError("Cannot list the compiled classes at " + location, e);
        }
    }

    private static List<String> classNamesBelow(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString())
                    .filter(name -> name.endsWith(CLASS_FILE_SUFFIX) && !name.endsWith("module-info.class")
                            && !name.endsWith("package-info.class") && !name.startsWith("META-INF"))
                    .map(name -> name.substring(0, name.length() - CLASS_FILE_SUFFIX.length())
                            .replace('\\', '/').replace('/', '.'))
                    .toList();
        }
    }

    private static Class<?> loadWithoutInitializing(String className) {
        try {
            return Class.forName(className, false, LogMessageIdentifierUniquenessTest.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            throw new AssertionError("Cannot load compiled class " + className
                    + "; the holder discovery would silently skip it", e);
        }
    }

    /**
     * A holder is a class with a nested class declaring a {@code public static final} {@link LogRecord}
     * whose prefix is exactly {@value #TOKEN_SHERIFF_PREFIX}.
     */
    private static boolean isTokenSheriffHolder(Class<?> candidate) {
        for (Class<?> nested : candidate.getDeclaredClasses()) {
            for (Field field : nested.getDeclaredFields()) {
                if (isPublicConstantLogRecord(field) && TOKEN_SHERIFF_PREFIX.equals(readRecord(field).getPrefix())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPublicConstantLogRecord(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                && LogRecord.class.isAssignableFrom(field.getType());
    }

    private static List<String> names(List<Class<?>> classes) {
        return classes.stream().map(Class::getSimpleName).toList();
    }

    private static LogRecord readRecord(Field field) {
        try {
            return (LogRecord) field.get(null);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Cannot read " + field, e);
        }
    }

    private record NamedRecord(Class<?> owner, String severity, String fieldName, LogRecord logRecord) {
    }
}
