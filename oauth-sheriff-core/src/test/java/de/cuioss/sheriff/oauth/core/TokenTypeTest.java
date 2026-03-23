/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.oauth.core;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnableGeneratorController
@EnableTestLogger
@DisplayName("Tests the TokenType enum functionality")
class TokenTypeTest {

    @ParameterizedTest
    @EnumSource(TokenType.class)
    @DisplayName("Should correctly parse valid type claims")
    void shouldHandleValidTokenTypes(TokenType tokenType) {
        assertNotNull(tokenType.getTypeClaimName(), "Type claim name should not be null");
        assertNotNull(tokenType.getMandatoryClaims(), "Mandatory claims should not be null");
        assertEquals(tokenType, TokenType.fromTypeClaim(tokenType.getTypeClaimName()), "Should parse back to same token type");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Should return UNKNOWN for null or empty type claims without logging")
    void shouldDefaultToUnknownForNullOrEmpty(String invalidType) {
        assertEquals(TokenType.UNKNOWN, TokenType.fromTypeClaim(invalidType), "Invalid type should return UNKNOWN");
        // No warning expected for null or empty
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "not_a_token_type", "custom_token"})
    @DisplayName("Should return UNKNOWN for invalid type claims and log warning")
    void shouldDefaultToUnknownAndLogWarning(String invalidType) {
        assertEquals(TokenType.UNKNOWN, TokenType.fromTypeClaim(invalidType), "Invalid type should return UNKNOWN");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                JWTValidationLogMessages.WARN.UNKNOWN_TOKEN_TYPE.resolveIdentifierString());
    }
}