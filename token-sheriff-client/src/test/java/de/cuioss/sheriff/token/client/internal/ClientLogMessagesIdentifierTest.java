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
package de.cuioss.sheriff.token.client.internal;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.logging.LogRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the identifier form the runtime emits for the client {@code INFO} records with the identifiers
 * {@code 1} to {@code 3}, as {@code CachingDiscoveryResolverRefreshPathTest} does for the
 * recovery record {@code 4}: the identifier is written without leading zeros, so the form
 * {@code doc/LogMessages.adoc} lists is the form an operator searches the log for.
 */
@EnableTestLogger
@DisplayName("ClientLogMessages INFO identifiers are emitted without leading zeros")
class ClientLogMessagesIdentifierTest {

    private static final CuiLogger LOGGER = new CuiLogger(ClientLogMessagesIdentifierTest.class);

    private static final String ARGUMENT = "argument";

    static Stream<Arguments> infoRecords() {
        return Stream.of(
                Arguments.of("DISCOVERY_RESOLVED", ClientLogMessages.INFO.DISCOVERY_RESOLVED, 1),
                Arguments.of("LOGOUT_TOKENS_CLEARED", ClientLogMessages.INFO.LOGOUT_TOKENS_CLEARED, 2),
                Arguments.of("REFUSED_REFRESH_KEPT_NEWER_GRANT",
                        ClientLogMessages.INFO.REFUSED_REFRESH_KEPT_NEWER_GRANT, 3));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("infoRecords")
    @DisplayName("Should emit the INFO record under TokenSheriffClient-<identifier>")
    void shouldEmitIdentifierWithoutLeadingZeros(String name, LogRecord logRecord, int identifier) {
        String expected = "TokenSheriffClient-" + identifier;

        LOGGER.info(logRecord, ARGUMENT);

        String formatted = logRecord.format(ARGUMENT);
        assertAll(name,
                () -> assertEquals(expected, logRecord.resolveIdentifierString(),
                        "the documented identifier of the record"),
                () -> assertTrue(formatted.startsWith(expected),
                        () -> "the formatted record must start with " + expected + ": " + formatted));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO, expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("infoRecords")
    @DisplayName("Control: should not emit the zero-padded form of the identifier")
    void shouldNotEmitZeroPaddedIdentifier(String name, LogRecord logRecord, int identifier) {
        String zeroPadded = "TokenSheriffClient-%03d".formatted(identifier);

        LOGGER.info(logRecord, ARGUMENT);

        String formatted = logRecord.format(ARGUMENT);
        assertAll(name,
                () -> assertNotEquals(zeroPadded, logRecord.resolveIdentifierString(),
                        "the identifier is not zero-padded"),
                () -> assertFalse(formatted.startsWith(zeroPadded),
                        () -> "the formatted record must not start with " + zeroPadded + ": " + formatted));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, zeroPadded);
    }
}
