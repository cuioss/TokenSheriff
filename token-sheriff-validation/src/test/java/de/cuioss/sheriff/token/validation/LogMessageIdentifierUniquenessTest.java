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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the log identifiers of the two message classes that share the {@code TokenSheriff} prefix:
 * {@link TransportLogMessages} and {@link JWTValidationLogMessages}. Every record must carry a unique
 * rendered identifier, and every identifier must lie in the range of its severity class.
 * <p>
 * Placed in the {@code validation} test package because the {@code commons} layer must not depend on
 * {@code validation}.
 */
@DisplayName("Log message identifiers of TransportLogMessages and JWTValidationLogMessages")
class LogMessageIdentifierUniquenessTest {

    /** Identifier ranges per severity class, as documented in doc/LogMessages.adoc. */
    private static final Map<String, int[]> SEVERITY_RANGES = Map.of(
            "INFO", new int[]{1, 99},
            "WARN", new int[]{100, 199},
            "ERROR", new int[]{200, 299});

    private static final List<Class<?>> MESSAGE_CLASSES = List.of(TransportLogMessages.class, JWTValidationLogMessages.class);

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
    @DisplayName("Every severity class of both message classes contributes records")
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
                    int modifiers = field.getModifiers();
                    if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                            && LogRecord.class.isAssignableFrom(field.getType())) {
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
