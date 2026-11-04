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
package de.cuioss.sheriff.token.validation.domain.claim.mapper;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValueType;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.json.MapRepresentation;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StrictStringMapper}.
 */
@EnableGeneratorController
@DisplayName("StrictStringMapper")
class StrictStringMapperTest {

    private static final String CLAIM_NAME = "azp";

    private final StrictStringMapper underTest = new StrictStringMapper();

    private static MapRepresentation parse(String json) {
        try {
            return MapRepresentation.fromJson(ParserConfig.builder().build().getDslJson(), json);
        } catch (IOException e) {
            throw new AssertionError("Failed to parse test JSON: " + json, e);
        }
    }

    private static MapRepresentation bodyWithClaimJson(String valueJson) {
        return parse("{\"" + CLAIM_NAME + "\":" + valueJson + "}");
    }

    @Test
    @DisplayName("Should pass a JSON string through unchanged")
    void shouldPassStringThrough() {
        String value = Generators.letterStrings(1, 40).next();

        ClaimValue result = underTest.map(bodyWithClaimJson("\"" + value + "\""), CLAIM_NAME);

        assertNotNull(result, "A string claim must be mapped");
        assertEquals(value, result.getOriginalString());
        assertEquals(ClaimValueType.STRING, result.getType());
    }

    @Test
    @DisplayName("Should pass an empty JSON string through unchanged")
    void shouldPassEmptyStringThrough() {
        ClaimValue result = underTest.map(bodyWithClaimJson("\"\""), CLAIM_NAME);

        assertNotNull(result, "An empty string claim is still a string claim");
        assertEquals("", result.getOriginalString());
        assertEquals(ClaimValueType.STRING, result.getType());
    }

    @Test
    @DisplayName("Should return null for an absent claim")
    void shouldReturnNullForAbsentClaim() {
        assertNull(underTest.map(parse("{\"other\":\"value\"}"), CLAIM_NAME));
    }

    @Test
    @DisplayName("Should return null for a JSON null claim, which the parser drops")
    void shouldReturnNullForJsonNullClaim() {
        assertNull(underTest.map(bodyWithClaimJson("null"), CLAIM_NAME));
    }

    static Stream<Arguments> nonStringValues() {
        return Stream.of(
                Arguments.of("array", "[\"" + Generators.letterStrings(1, 20).next() + "\"]"),
                Arguments.of("empty array", "[]"),
                Arguments.of("integer", String.valueOf(Generators.integers(Integer.MIN_VALUE, Integer.MAX_VALUE).next())),
                Arguments.of("decimal", Generators.integers(0, 1_000_000).next() + ".5"),
                Arguments.of("boolean", String.valueOf(Generators.booleans().next())),
                Arguments.of("object", "{\"id\":\"" + Generators.letterStrings(1, 20).next() + "\"}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonStringValues")
    @DisplayName("Should reject any non-string JSON value")
    void shouldRejectNonStringValue(String jsonType, String valueJson) {
        MapRepresentation body = bodyWithClaimJson(valueJson);

        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> underTest.map(body, CLAIM_NAME), jsonType + " must be rejected");

        assertEquals(SecurityEventCounter.EventType.MISSING_CLAIM, exception.getEventType());
        assertTrue(exception.getMessage().contains(CLAIM_NAME),
                "The message must name the claim: " + exception.getMessage());
    }
}
