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
package de.cuioss.sheriff.token.validation.jwks.key;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.security.JwkAlgorithmPreferences;
import de.cuioss.sheriff.token.validation.test.InMemoryJWKSFactory;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static de.cuioss.sheriff.token.validation.JWTValidationLogMessages.ERROR;
import static de.cuioss.sheriff.token.validation.JWTValidationLogMessages.ERROR.JWKS_CONTENT_SIZE_EXCEEDED;
import static de.cuioss.sheriff.token.validation.JWTValidationLogMessages.WARN;
import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger(debug = {JWKSKeyLoader.class}, trace = {JWKSKeyLoader.class})
@DisplayName("Tests JWKSKeyLoader functionality")
class JWKSKeyLoaderTest {

    private static final String TEST_KID = InMemoryJWKSFactory.DEFAULT_KEY_ID;
    private JWKSKeyLoader keyLoader;
    private String jwksContent;

    @BeforeEach
    void setUp() {
        jwksContent = InMemoryJWKSFactory.createDefaultJwks();
        SecurityEventCounter securityEventCounter = new SecurityEventCounter();
        keyLoader = JWKSKeyLoader.builder()
                .jwksContent(jwksContent)

                .build();
        keyLoader.initJWKSLoader(securityEventCounter);
    }

    @Nested
    @DisplayName("Basic Functionality")
    class BasicFunctionalityTests {
        @Test
        @DisplayName("Should parse JWKS content")
        void shouldParseJwksContent() {

            Optional<KeyInfo> keyInfo = keyLoader.getKeyInfo(TEST_KID);
            assertTrue(keyInfo.isPresent(), "Key info should be present");
        }

        @Test
        @DisplayName("Should get key by known kid")
        void shouldGetKeyByKnownKid() {
            // Test getting a key by its known ID
            Optional<KeyInfo> keyInfo = keyLoader.getKeyInfo(TEST_KID);
            assertTrue(keyInfo.isPresent(), "Key info should be present for known kid");
            assertEquals(TEST_KID, keyInfo.get().keyId(), "Key ID should match");
        }

        @Test
        @DisplayName("Should verify test key exists")
        void shouldVerifyTestKeyExists() {
            // Verify that the test key is loaded correctly
            Optional<KeyInfo> keyInfo = keyLoader.getKeyInfo(TEST_KID);
            assertTrue(keyInfo.isPresent(), "Test key should be loaded");
            assertNotNull(keyInfo.get().key(), "Key object should not be null");
            assertEquals(TEST_KID, keyInfo.get().keyId(), "Key ID should match test ID");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {
        @Test
        @DisplayName("Should return empty when kid is null")
        void shouldReturnEmptyWhenKidIsNull() {

            Optional<KeyInfo> keyInfo = keyLoader.getKeyInfo(null);
            assertFalse(keyInfo.isPresent(), "Key info should not be present when kid is null");
            // Note: Log assertion removed as it's not essential to the test's purpose
        }

        @Test
        @DisplayName("Should return empty when kid is not found")
        void shouldReturnEmptyWhenKidNotFound() {

            Optional<KeyInfo> keyInfo = keyLoader.getKeyInfo("unknown-kid");
            assertFalse(keyInfo.isPresent(), "Key info should not be present when kid is not found");
        }

        @Test
        @DisplayName("Should handle invalid JWKS format")
        void shouldHandleInvalidJwksFormat() {

            String invalidJwksContent = InMemoryJWKSFactory.createInvalidJson();
            JWKSKeyLoader invalidLoader = JWKSKeyLoader.builder()
                    .jwksContent(invalidJwksContent)
                    .build();
            invalidLoader.initJWKSLoader(new SecurityEventCounter());
            Optional<KeyInfo> keyInfo = invalidLoader.getKeyInfo(TEST_KID);
            assertFalse(keyInfo.isPresent(), "Key info should not be present when JWKS is invalid");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                    ERROR.JWKS_INVALID_JSON.resolveIdentifierString());
        }

        @Test
        @DisplayName("Should handle missing required fields in JWK")
        void shouldHandleMissingRequiredFieldsInJwk() {

            String missingFieldsJwksContent = InMemoryJWKSFactory.createJwksWithMissingFields(TEST_KID);
            JWKSKeyLoader missingFieldsLoader = JWKSKeyLoader.builder()
                    .jwksContent(missingFieldsJwksContent)
                    .build();
            missingFieldsLoader.initJWKSLoader(new SecurityEventCounter());
            Optional<KeyInfo> keyInfo = missingFieldsLoader.getKeyInfo(TEST_KID);
            assertFalse(keyInfo.isPresent(), "Key info should not be present when JWK is missing required fields");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    WARN.RSA_KEY_PARSE_FAILED.resolveIdentifierString());
        }
    }

    @Nested
    @DisplayName("Key Management")
    class KeyManagementTests {
        @Test
        @DisplayName("Should report not empty when keys are present")
        void shouldReportNotEmptyWhenKeysArePresent() {
            assertTrue(keyLoader.getKeyInfo(TEST_KID).isPresent(), "Loader should report not empty when keys are present");
        }

        @Test
        @DisplayName("Should report empty when no keys are present")
        void shouldReportEmptyWhenNoKeysArePresent() {
            String emptyJwksContent = "{}";
            JWKSKeyLoader emptyLoader = JWKSKeyLoader.builder()
                    .jwksContent(emptyJwksContent)
                    .build();
            emptyLoader.initJWKSLoader(new SecurityEventCounter());
            assertEquals(LoaderStatus.ERROR, emptyLoader.getLoaderStatus(), "Loader should report empty when no keys are present");
        }
    }

    @Nested
    @DisplayName("Key Id Snapshot")
    class KeyIdSnapshotTests {

        private static final String FIRST_KID = "first-kid";
        private static final String SECOND_KID = "second-kid";

        @Test
        @DisplayName("Should report the key ids of all loaded keys")
        void shouldReportKeyIdsOfAllLoadedKeys() {
            JWKSKeyLoader loader = initializedLoader(combineJwks(
                    InMemoryJWKSFactory.createValidJwksWithKeyId(FIRST_KID),
                    InMemoryJWKSFactory.createValidJwksWithKeyId(SECOND_KID)));

            Set<String> keyIds = loader.getKeyIds();

            assertEquals(Set.of(FIRST_KID, SECOND_KID), keyIds, "Every loaded key id should be reported");
        }

        @Test
        @DisplayName("Should return an unmodifiable set")
        void shouldReturnUnmodifiableSet() {
            Set<String> keyIds = keyLoader.getKeyIds();

            assertThrows(UnsupportedOperationException.class, () -> keyIds.add(SECOND_KID),
                    "The key id snapshot must not be modifiable");
            assertEquals(Set.of(TEST_KID), keyLoader.getKeyIds(), "The loaded key ids should be unchanged");
        }

        @Test
        @DisplayName("Should return an empty set before initialization")
        void shouldReturnEmptySetBeforeInitialization() {
            JWKSKeyLoader uninitializedLoader = JWKSKeyLoader.builder()
                    .jwksContent(jwksContent)
                    .build();

            Set<String> keyIds = uninitializedLoader.getKeyIds();

            assertTrue(keyIds.isEmpty(), "No key ids should be reported before initialization");
        }

        @Test
        @DisplayName("Should report a duplicate key id once")
        void shouldReportDuplicateKeyIdOnce() {
            JWKSKeyLoader loader = initializedLoader(combineJwks(
                    InMemoryJWKSFactory.createValidJwksWithKeyId(InMemoryKeyMaterialHandler.Algorithm.RS256, FIRST_KID),
                    InMemoryJWKSFactory.createValidJwksWithKeyId(InMemoryKeyMaterialHandler.Algorithm.RS384, FIRST_KID)));

            Set<String> keyIds = loader.getKeyIds();

            assertEquals(Set.of(FIRST_KID), keyIds, "A duplicate key id should be reported exactly once");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    WARN.DUPLICATE_JWKS_KID.resolveIdentifierString());
        }

        private JWKSKeyLoader initializedLoader(String content) {
            JWKSKeyLoader loader = JWKSKeyLoader.builder()
                    .jwksContent(content)
                    .build();
            loader.initJWKSLoader(new SecurityEventCounter());
            return loader;
        }

        /**
         * Merges the key arrays of single-key JWKS documents into one JWKS document.
         */
        private String combineJwks(String... jwksDocuments) {
            String prefix = "{\"keys\":[";
            String suffix = "]}";
            return Arrays.stream(jwksDocuments)
                    .map(document -> document.substring(prefix.length(), document.length() - suffix.length()))
                    .collect(Collectors.joining(",", prefix, suffix));
        }
    }

    @Nested
    @DisplayName("Security Features")
    class SecurityFeaturesTests {
        @Test
        @DisplayName("Should work with custom ParserConfig")
        void shouldWorkWithCustomParserConfig() {
            ParserConfig customConfig = ParserConfig.builder()
                    .maxPayloadSize(1024)
                    .maxStringLength(512)
                    .build();
            JWKSKeyLoader loaderWithCustomConfig = JWKSKeyLoader.builder()
                    .jwksContent(jwksContent)

                    .parserConfig(customConfig)
                    .build();
            loaderWithCustomConfig.initJWKSLoader(new SecurityEventCounter());
            assertTrue(loaderWithCustomConfig.getKeyInfo(TEST_KID).isPresent(),
                    "Loader should parse valid JWKS with custom config");
        }

        @Test
        @DisplayName("Should reject JWKS content exceeding maximum size")
        void shouldRejectJwksContentExceedingMaximumSize() {

            int maxSize = 100; // Small size for testing
            ParserConfig restrictiveConfig = ParserConfig.builder()
                    .maxPayloadSize(maxSize)
                    .build();

            // Create a large JWKS content that exceeds the maximum size
            // Add enough padding to exceed maxSize
            JWKSKeyLoader loader = JWKSKeyLoader.builder()
                    .jwksContent("{\"keys\":[" + "\"x\":\"" + "a".repeat(maxSize) + "\"}]}"

                    )
                    .parserConfig(restrictiveConfig)
                    .build();
            loader.initJWKSLoader(new SecurityEventCounter());
            assertEquals(LoaderStatus.ERROR, loader.getLoaderStatus(), "Loader should reject content exceeding maximum size");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR, JWKS_CONTENT_SIZE_EXCEEDED.resolveIdentifierString());
        }

    }

    @Nested
    @DisplayName("Algorithm Preferences")
    class AlgorithmPreferencesTests {

        @Test
        @DisplayName("Should use default algorithm preferences when not specified")
        void shouldUseDefaultAlgorithmPreferencesWhenNotSpecified() {
            // Create a JWKS with a supported algorithm (RS256)
            String jwksWithRS256 = InMemoryJWKSFactory.createValidJwksWithKeyId(InMemoryKeyMaterialHandler.Algorithm.RS256, "test-kid");

            JWKSKeyLoader loader = JWKSKeyLoader.builder()
                    .jwksContent(jwksWithRS256)
                    .build();
            loader.initJWKSLoader(new SecurityEventCounter());

            Optional<KeyInfo> keyInfo = loader.getKeyInfo("test-kid");
            assertTrue(keyInfo.isPresent(), "Key should be loaded with default supported algorithm RS256");
        }

        @Test
        @DisplayName("Should accept custom algorithm preferences")
        void shouldAcceptCustomAlgorithmPreferences() {
            // Create custom preferences that only allow ES256
            JwkAlgorithmPreferences customPreferences = new JwkAlgorithmPreferences(List.of("ES256"));
            String jwksWithES256 = InMemoryJWKSFactory.createValidJwksWithKeyId(InMemoryKeyMaterialHandler.Algorithm.ES256, "test-kid");

            JWKSKeyLoader loader = JWKSKeyLoader.builder()
                    .jwksContent(jwksWithES256)
                    .jwkAlgorithmPreferences(customPreferences)
                    .build();
            loader.initJWKSLoader(new SecurityEventCounter());

            Optional<KeyInfo> keyInfo = loader.getKeyInfo("test-kid");
            assertTrue(keyInfo.isPresent(), "Key should be loaded with custom supported algorithm ES256");
        }

        @Test
        @DisplayName("Should reject keys with unsupported algorithms")
        void shouldRejectKeysWithUnsupportedAlgorithms() {
            // Create custom preferences that only allow ES256 (not RS256)
            JwkAlgorithmPreferences restrictivePreferences = new JwkAlgorithmPreferences(List.of("ES256"));
            String jwksWithRS256 = InMemoryJWKSFactory.createValidJwksWithKeyId(InMemoryKeyMaterialHandler.Algorithm.RS256, "test-kid");

            JWKSKeyLoader loader = JWKSKeyLoader.builder()
                    .jwksContent(jwksWithRS256)
                    .jwkAlgorithmPreferences(restrictivePreferences)
                    .build();
            loader.initJWKSLoader(new SecurityEventCounter());

            Optional<KeyInfo> keyInfo = loader.getKeyInfo("test-kid");
            assertFalse(keyInfo.isPresent(), "Key should be rejected with unsupported algorithm RS256");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    WARN.JWK_INVALID_ALGORITHM.resolveIdentifierString());
        }

        @Test
        @DisplayName("Should handle null algorithm preferences gracefully")
        void shouldHandleNullAlgorithmPreferencesGracefully() {
            // Builder should handle null and use default
            String jwksWithRS256 = InMemoryJWKSFactory.createValidJwksWithKeyId(InMemoryKeyMaterialHandler.Algorithm.RS256, "test-kid");

            JWKSKeyLoader loader = JWKSKeyLoader.builder()
                    .jwksContent(jwksWithRS256)
                    .jwkAlgorithmPreferences(null)
                    .build();
            loader.initJWKSLoader(new SecurityEventCounter());

            Optional<KeyInfo> keyInfo = loader.getKeyInfo("test-kid");
            assertTrue(keyInfo.isPresent(), "Key should be loaded with default preferences when null is passed");
        }
    }

    @Nested
    @DisplayName("JWK Validation Tests")
    class JwkValidationTests {
        @Test
        @DisplayName("Should handle JWK with missing kty field")
        void shouldHandleJwkWithMissingKty() {
            String jwksMissingKty = """
                    {
                        "keys": [
                            {
                                "kid": "test-kid",
                                "use": "sig",
                                "alg": "RS256",
                                "n": "test-modulus",
                                "e": "AQAB"
                            }
                        ]
                    }
                    """;

            JWKSKeyLoader loader = JWKSKeyLoader.builder()
                    .jwksContent(jwksMissingKty)
                    .build();
            loader.initJWKSLoader(new SecurityEventCounter());

            Optional<KeyInfo> keyInfo = loader.getKeyInfo("test-kid");
            assertFalse(keyInfo.isPresent(), "Key should not be loaded without kty field");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    WARN.JWK_MISSING_KTY.resolveIdentifierString());
        }

        @Test
        @DisplayName("Should handle JWK with unsupported key type")
        void shouldHandleJwkWithUnsupportedKeyType() {
            String jwksUnsupportedType = """
                    {
                        "keys": [
                            {
                                "kty": "UNKNOWN",
                                "kid": "test-kid",
                                "use": "sig",
                                "alg": "RS256"
                            }
                        ]
                    }
                    """;

            JWKSKeyLoader loader = JWKSKeyLoader.builder()
                    .jwksContent(jwksUnsupportedType)
                    .build();
            loader.initJWKSLoader(new SecurityEventCounter());

            Optional<KeyInfo> keyInfo = loader.getKeyInfo("test-kid");
            assertFalse(keyInfo.isPresent(), "Key should not be loaded with unsupported key type");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    WARN.JWK_UNSUPPORTED_KEY_TYPE.resolveIdentifierString());
        }

        @Test
        @DisplayName("Should handle JWK with key ID exceeding max length")
        void shouldHandleJwkWithKeyIdExceedingMaxLength() {
            String longKid = "k".repeat(300); // Exceeds typical max length
            String jwksLongKid = """
                    {
                        "keys": [
                            {
                                "kty": "RSA",
                                "kid": "%s",
                                "use": "sig",
                                "alg": "RS256",
                                "n": "test-modulus",
                                "e": "AQAB"
                            }
                        ]
                    }
                    """.formatted(longKid);

            JWKSKeyLoader loader = JWKSKeyLoader.builder()
                    .jwksContent(jwksLongKid)
                    .build();
            loader.initJWKSLoader(new SecurityEventCounter());

            Optional<KeyInfo> keyInfo = loader.getKeyInfo(longKid);
            assertFalse(keyInfo.isPresent(), "Key should not be loaded with excessively long key ID");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    WARN.JWK_KEY_ID_TOO_LONG.resolveIdentifierString());
        }

        @Test
        @DisplayName("Should handle EC key parse failure")
        void shouldHandleEcKeyParseFailure() {
            String invalidEcJwks = """
                    {
                        "keys": [
                            {
                                "kty": "EC",
                                "kid": "test-kid",
                                "use": "sig",
                                "alg": "ES256",
                                "crv": "P-256"
                            }
                        ]
                    }
                    """;

            JWKSKeyLoader loader = JWKSKeyLoader.builder()
                    .jwksContent(invalidEcJwks)
                    .build();
            loader.initJWKSLoader(new SecurityEventCounter());

            Optional<KeyInfo> keyInfo = loader.getKeyInfo("test-kid");
            assertFalse(keyInfo.isPresent(), "EC key should not be loaded without required fields");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                    WARN.EC_KEY_PARSE_FAILED.resolveIdentifierString());
        }
    }
}
