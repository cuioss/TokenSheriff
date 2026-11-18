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

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.logging.LogRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the identifier form the runtime emits for every {@link JWTValidationLogMessages.INFO} record:
 * the identifier is written without leading zeros ({@code TokenSheriff-1}, not {@code TokenSheriff-001}),
 * so the form {@code doc/LogMessages.adoc} lists is the form an operator searches the log for.
 * <p>
 * The records are read from the {@code INFO} class, so a record added later is covered without being
 * registered here; the source asserts that it found records, so the tests cannot pass vacuously.
 */
@EnableTestLogger
@DisplayName("JWTValidationLogMessages INFO identifiers are emitted without leading zeros")
class JWTValidationLogMessagesIdentifierTest {

    private static final CuiLogger LOGGER = new CuiLogger(JWTValidationLogMessagesIdentifierTest.class);

    private static final String PREFIX = "TokenSheriff";

    private static final String ARGUMENT = "argument";

    static Stream<Arguments> infoRecords() {
        List<Arguments> records = new ArrayList<>();
        for (Field field : JWTValidationLogMessages.INFO.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)
                    && LogRecord.class.isAssignableFrom(field.getType())) {
                records.add(Arguments.of(field.getName(), readRecord(field)));
            }
        }
        assertFalse(records.isEmpty(), "No INFO record found, the identifier tests would pass vacuously");
        return records.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("infoRecords")
    @DisplayName("Should emit the INFO record under TokenSheriff-<identifier>")
    void shouldEmitIdentifierWithoutLeadingZeros(String name, LogRecord logRecord) {
        String expected = PREFIX + "-" + logRecord.getIdentifier();

        LOGGER.info(logRecord, ARGUMENT, ARGUMENT, ARGUMENT);

        String formatted = logRecord.format(ARGUMENT, ARGUMENT, ARGUMENT);
        assertAll(name,
                () -> assertEquals(PREFIX, logRecord.getPrefix(), "the prefix of the record"),
                () -> assertEquals(expected, logRecord.resolveIdentifierString(),
                        "the documented identifier of the record"),
                () -> assertTrue(formatted.startsWith(expected + ":"),
                        () -> "the formatted record must start with " + expected + ": " + formatted));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO, expected + ":");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("infoRecords")
    @DisplayName("Control: should not emit the zero-padded form of the identifier")
    void shouldNotEmitZeroPaddedIdentifier(String name, LogRecord logRecord) {
        String zeroPadded = "%s-%03d".formatted(PREFIX, logRecord.getIdentifier());

        LOGGER.info(logRecord, ARGUMENT, ARGUMENT, ARGUMENT);

        String formatted = logRecord.format(ARGUMENT, ARGUMENT, ARGUMENT);
        assertAll(name,
                () -> assertNotEquals(zeroPadded, logRecord.resolveIdentifierString(),
                        "the identifier is not zero-padded"),
                () -> assertFalse(formatted.startsWith(zeroPadded),
                        () -> "the formatted record must not start with " + zeroPadded + ": " + formatted));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, zeroPadded);
    }

    private static LogRecord readRecord(Field field) {
        try {
            return (LogRecord) field.get(null);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Cannot read " + field, e);
        }
    }
}
