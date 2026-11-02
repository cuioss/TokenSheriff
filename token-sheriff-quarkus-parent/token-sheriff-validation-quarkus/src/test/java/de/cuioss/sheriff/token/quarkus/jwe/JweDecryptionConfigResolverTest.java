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
package de.cuioss.sheriff.token.quarkus.jwe;

import de.cuioss.sheriff.token.quarkus.config.JwtPropertyKeys;
import de.cuioss.sheriff.token.quarkus.test.TestConfig;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.smallrye.config.EnvConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JweDecryptionConfigResolver Tests")
@EnableTestLogger(rootLevel = TestLogLevel.DEBUG)
class JweDecryptionConfigResolverTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should return null when no JWE properties configured")
    void shouldReturnNullWhenNotConfigured() {
        TestConfig config = new TestConfig(Map.of());
        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(config);

        assertNull(resolver.resolveJweDecryptionConfig());
    }

    @Test
    @DisplayName("Should load single key from PEM path")
    void shouldLoadSingleKeyFromPem() throws Exception {
        Path pemFile = createRsaPemFile("single-key.pem");

        Map<String, String> props = new HashMap<>();
        props.put(JwtPropertyKeys.JWE.DECRYPTION_KEY_PATH, pemFile.toString());
        props.put(JwtPropertyKeys.JWE.DECRYPTION_KEY_ID, "my-key-1");

        TestConfig config = new TestConfig(props);
        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(config);

        JweDecryptionConfig result = resolver.resolveJweDecryptionConfig();

        assertNotNull(result);
        assertEquals(1, result.getKeyCount());
        assertTrue(result.resolveKey("my-key-1").isPresent());
        assertTrue(result.resolveKey(null).isPresent()); // default key set
    }

    @Test
    @DisplayName("Should load multiple keys for rotation")
    void shouldLoadMultipleKeys() throws Exception {
        Path pemFile1 = createRsaPemFile("key-1.pem");
        Path pemFile2 = createRsaPemFile("key-2.pem");

        Map<String, String> props = new HashMap<>();
        props.put(JwtPropertyKeys.JWE.MULTI_KEY_PREFIX + "key-1.path", pemFile1.toString());
        props.put(JwtPropertyKeys.JWE.MULTI_KEY_PREFIX + "key-2.path", pemFile2.toString());
        props.put(JwtPropertyKeys.JWE.DEFAULT_KEY_ID, "key-1");

        TestConfig config = new TestConfig(props);
        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(config);

        JweDecryptionConfig result = resolver.resolveJweDecryptionConfig();

        assertNotNull(result);
        assertTrue(result.resolveKey("key-1").isPresent());
        assertTrue(result.resolveKey("key-2").isPresent());
        assertTrue(result.resolveKey(null).isPresent()); // default key set
    }

    @Test
    @DisplayName("Should configure custom algorithm preferences")
    void shouldConfigureAlgorithmPreferences() throws Exception {
        Path pemFile = createRsaPemFile("alg-test.pem");

        Map<String, String> props = new HashMap<>();
        props.put(JwtPropertyKeys.JWE.DECRYPTION_KEY_PATH, pemFile.toString());
        props.put(JwtPropertyKeys.JWE.KEY_MANAGEMENT_ALGORITHMS, "RSA-OAEP");
        props.put(JwtPropertyKeys.JWE.CONTENT_ENCRYPTION_ALGORITHMS, "A256GCM");

        TestConfig config = new TestConfig(props);
        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(config);

        JweDecryptionConfig result = resolver.resolveJweDecryptionConfig();

        assertNotNull(result);
        assertTrue(result.getAlgorithmPreferences().isKeyManagementSupported("RSA-OAEP"));
        assertFalse(result.getAlgorithmPreferences().isKeyManagementSupported("RSA-OAEP-256"));
        assertTrue(result.getAlgorithmPreferences().isContentEncryptionSupported("A256GCM"));
        assertFalse(result.getAlgorithmPreferences().isContentEncryptionSupported("A128GCM"));
    }

    @Test
    @DisplayName("Should configure custom max encrypted token size")
    void shouldConfigureMaxTokenSize() throws Exception {
        Path pemFile = createRsaPemFile("size-test.pem");

        Map<String, String> props = new HashMap<>();
        props.put(JwtPropertyKeys.JWE.DECRYPTION_KEY_PATH, pemFile.toString());
        props.put(JwtPropertyKeys.JWE.MAX_ENCRYPTED_TOKEN_SIZE, "65536");

        TestConfig config = new TestConfig(props);
        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(config);

        JweDecryptionConfig result = resolver.resolveJweDecryptionConfig();

        assertNotNull(result);
        assertEquals(65536, result.getMaxEncryptedTokenSize());
    }

    @Test
    @DisplayName("Should load single key without explicit kid")
    void shouldLoadSingleKeyWithoutKid() throws Exception {
        Path pemFile = createRsaPemFile("no-kid-key.pem");

        Map<String, String> props = new HashMap<>();
        props.put(JwtPropertyKeys.JWE.DECRYPTION_KEY_PATH, pemFile.toString());

        TestConfig config = new TestConfig(props);
        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(config);

        JweDecryptionConfig result = resolver.resolveJweDecryptionConfig();

        assertNotNull(result);
        // Default key should still be set even without explicit kid
        assertTrue(result.resolveKey(null).isPresent());
    }

    @Test
    @DisplayName("Should load multiple keys without explicit default kid")
    void shouldLoadMultipleKeysWithoutDefaultKid() throws Exception {
        Path pemFile1 = createRsaPemFile("multi-1.pem");
        Path pemFile2 = createRsaPemFile("multi-2.pem");

        Map<String, String> props = new HashMap<>();
        props.put(JwtPropertyKeys.JWE.MULTI_KEY_PREFIX + "first.path", pemFile1.toString());
        props.put(JwtPropertyKeys.JWE.MULTI_KEY_PREFIX + "second.path", pemFile2.toString());

        TestConfig config = new TestConfig(props);
        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(config);

        JweDecryptionConfig result = resolver.resolveJweDecryptionConfig();

        assertNotNull(result);
        assertTrue(result.resolveKey("first").isPresent());
        assertTrue(result.resolveKey("second").isPresent());
        // First key should be default
        assertTrue(result.resolveKey(null).isPresent());
    }

    @Test
    @DisplayName("Should throw when keystore configured without alias — fail fast naming key-alias")
    void shouldThrowWhenKeystoreAliasMissing() throws Exception {
        Path dummyKeystore = tempDir.resolve("dummy.p12");
        Files.writeString(dummyKeystore, "dummy-content");

        Map<String, String> props = new HashMap<>();
        props.put(JwtPropertyKeys.JWE.KEYSTORE_PATH, dummyKeystore.toString());
        // No KEY_ALIAS configured

        TestConfig config = new TestConfig(props);
        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(config);

        // Keystore path without key alias must fail fast, mirroring the other
        // key-loading failure paths of this resolver
        var exception = assertThrows(IllegalStateException.class,
                resolver::resolveJweDecryptionConfig);
        assertTrue(exception.getMessage().contains("key loading failed"),
                "Should be wrapped in the fail-closed message: " + exception.getMessage());
        assertTrue(exception.getMessage().contains(JwtPropertyKeys.JWE.KEY_ALIAS),
                "Should name the missing property sheriff.token.jwe.key-alias: " + exception.getMessage());
    }

    @Test
    @DisplayName("Should throw when key loading fails for explicitly configured JWE")
    void shouldThrowWhenKeyLoadingFails() {
        Map<String, String> props = new HashMap<>();
        props.put(JwtPropertyKeys.JWE.DECRYPTION_KEY_PATH, "/nonexistent/path/key.pem");

        TestConfig config = new TestConfig(props);
        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(config);

        var exception = assertThrows(IllegalStateException.class,
                resolver::resolveJweDecryptionConfig);
        assertTrue(exception.getMessage().contains("key loading failed"));
    }

    @Test
    @DisplayName("Should throw when default-key-id names no configured multi-key kid")
    void shouldThrowWhenDefaultKeyIdUnknown() throws Exception {
        Path pemFile1 = createRsaPemFile("known-1.pem");
        Path pemFile2 = createRsaPemFile("known-2.pem");

        Map<String, String> props = new HashMap<>();
        props.put(JwtPropertyKeys.JWE.MULTI_KEY_PREFIX + "key-1.path", pemFile1.toString());
        props.put(JwtPropertyKeys.JWE.MULTI_KEY_PREFIX + "key-2.path", pemFile2.toString());
        props.put(JwtPropertyKeys.JWE.DEFAULT_KEY_ID, "unknown-kid");

        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(new TestConfig(props));

        var exception = assertThrows(IllegalStateException.class,
                resolver::resolveJweDecryptionConfig);
        String message = exception.getMessage();
        assertTrue(message.contains("key loading failed"),
                "Should be wrapped in the fail-closed message: " + message);
        assertTrue(message.contains("unknown-kid"), "Should name the configured kid: " + message);
        assertTrue(message.contains("key-1") && message.contains("key-2"),
                "Should name the available kids: " + message);
        assertTrue(message.contains(JwtPropertyKeys.JWE.DEFAULT_KEY_ID),
                "Should name the property key: " + message);
    }

    @ParameterizedTest(name = "{0} without any key source")
    @MethodSource("ancillaryPropertiesRequiringAnyKeySource")
    @DisplayName("Should throw when an ancillary property is set without any key source")
    void shouldThrowWhenAncillaryPropertyHasNoKeySource(String property, String value) {
        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(
                new TestConfig(Map.of(property, value)));

        var exception = assertThrows(IllegalStateException.class,
                resolver::resolveJweDecryptionConfig);
        assertTrue(exception.getMessage().contains(property),
                "Should name the orphaned property: " + exception.getMessage());
    }

    static Stream<Arguments> ancillaryPropertiesRequiringAnyKeySource() {
        return Stream.of(
                Arguments.of(JwtPropertyKeys.JWE.DECRYPTION_KEY_ID, "orphan-kid"),
                Arguments.of(JwtPropertyKeys.JWE.KEYSTORE_PASSWORD, "secret"),
                Arguments.of(JwtPropertyKeys.JWE.KEY_ALIAS, "alias"),
                Arguments.of(JwtPropertyKeys.JWE.KEY_PASSWORD, "secret"),
                Arguments.of(JwtPropertyKeys.JWE.DEFAULT_KEY_ID, "key-1"),
                Arguments.of(JwtPropertyKeys.JWE.KEY_MANAGEMENT_ALGORITHMS, "RSA-OAEP"),
                Arguments.of(JwtPropertyKeys.JWE.CONTENT_ENCRYPTION_ALGORITHMS, "A256GCM"),
                Arguments.of(JwtPropertyKeys.JWE.MAX_ENCRYPTED_TOKEN_SIZE, "65536"));
    }

    @ParameterizedTest(name = "{0} with only a single key path")
    @MethodSource("keystoreOrMultiKeyPropertiesWithSingleKey")
    @DisplayName("Should throw when a keystore or multi-key property is set with only a single key path")
    void shouldThrowWhenPropertySetWithWrongKeySource(String property, String value) throws Exception {
        Path pemFile = createRsaPemFile("single-only.pem");

        Map<String, String> props = new HashMap<>();
        props.put(JwtPropertyKeys.JWE.DECRYPTION_KEY_PATH, pemFile.toString());
        props.put(property, value);

        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(new TestConfig(props));

        var exception = assertThrows(IllegalStateException.class,
                resolver::resolveJweDecryptionConfig);
        assertTrue(exception.getMessage().contains(property),
                "Should name the orphaned property: " + exception.getMessage());
    }

    static Stream<Arguments> keystoreOrMultiKeyPropertiesWithSingleKey() {
        return Stream.of(
                Arguments.of(JwtPropertyKeys.JWE.KEYSTORE_PASSWORD, "secret"),
                Arguments.of(JwtPropertyKeys.JWE.KEY_ALIAS, "alias"),
                Arguments.of(JwtPropertyKeys.JWE.KEY_PASSWORD, "secret"),
                Arguments.of(JwtPropertyKeys.JWE.DEFAULT_KEY_ID, "key-1"));
    }

    @Test
    @DisplayName("Should throw when decryption-key-id is set with only multi-key paths")
    void shouldThrowWhenDecryptionKeyIdSetWithOnlyMultiKeys() throws Exception {
        Path pemFile = createRsaPemFile("multi-only.pem");

        Map<String, String> props = new HashMap<>();
        props.put(JwtPropertyKeys.JWE.MULTI_KEY_PREFIX + "key-1.path", pemFile.toString());
        props.put(JwtPropertyKeys.JWE.DECRYPTION_KEY_ID, "key-1");

        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(new TestConfig(props));

        var exception = assertThrows(IllegalStateException.class,
                resolver::resolveJweDecryptionConfig);
        assertTrue(exception.getMessage().contains(JwtPropertyKeys.JWE.DECRYPTION_KEY_ID),
                "Should name the orphaned property: " + exception.getMessage());
    }

    @Test
    @DisplayName("Should throw when a multi-key entry has no path")
    void shouldThrowWhenMultiKeyEntryHasNoPath() throws Exception {
        Path pemFile = createRsaPemFile("with-path.pem");
        String pathlessProperty = JwtPropertyKeys.JWE.MULTI_KEY_PREFIX + "pathless.path";

        Map<String, String> props = new HashMap<>();
        props.put(JwtPropertyKeys.JWE.MULTI_KEY_PREFIX + "key-1.path", pemFile.toString());
        props.put(JwtPropertyKeys.JWE.MULTI_KEY_PREFIX + "pathless.pth", "/typo/key.pem");

        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(new TestConfig(props));

        var exception = assertThrows(IllegalStateException.class,
                resolver::resolveJweDecryptionConfig);
        assertTrue(exception.getMessage().contains(pathlessProperty),
                "Should name the missing path property: " + exception.getMessage());
    }

    @Test
    @DisplayName("Should throw when only a bare decryption-keys.<kid> value is set without .path")
    void shouldThrowWhenOnlyBareMultiKeyPropertySet() {
        String bareProperty = JwtPropertyKeys.JWE.MULTI_KEY_PREFIX + "k1";
        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(
                new TestConfig(Map.of(bareProperty, "/some/path.pem")));

        var exception = assertThrows(IllegalStateException.class,
                resolver::resolveJweDecryptionConfig);
        assertTrue(exception.getMessage().contains(bareProperty + ".path"),
                "Should name the missing path property: " + exception.getMessage());
    }

    @Test
    @DisplayName("Should still load a multi-key entry given in the dotted .path form")
    void shouldLoadDottedPathFormForSameKid() throws Exception {
        Path pemFile = createRsaPemFile("dotted-k1.pem");
        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(
                new TestConfig(Map.of(JwtPropertyKeys.JWE.MULTI_KEY_PREFIX + "k1.path", pemFile.toString())));

        JweDecryptionConfig result = resolver.resolveJweDecryptionConfig();

        assertNotNull(result);
        assertEquals(1, result.getKeyCount());
        assertTrue(result.resolveKey("k1").isPresent());
    }

    @Test
    @DisplayName("Should not turn an env-only decryption-keys path into a multi-key entry")
    void shouldNotDiscoverEnvOnlyMultiKey() throws Exception {
        Path pemFile = createRsaPemFile("env-only.pem");
        Config config = new SmallRyeConfigBuilder()
                .withSources(new EnvConfigSource(
                        Map.of("SHERIFF_TOKEN_JWE_DECRYPTION_KEYS_ENVKID_PATH", pemFile.toString()), 300))
                .build();
        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(config);

        assertNull(resolver.resolveJweDecryptionConfig(),
                "Kid discovery is dotted-only: an env-only multi-key path must not configure JWE");
    }

    @Test
    @DisplayName("Should keep dotted multi-key discovery unchanged on a SmallRye config")
    void shouldKeepDottedMultiKeyDiscovery() throws Exception {
        Path firstPem = createRsaPemFile("dotted-1.pem");
        Path secondPem = createRsaPemFile("dotted-2.pem");
        Config config = new SmallRyeConfigBuilder()
                .withDefaultValues(Map.of(
                        JwtPropertyKeys.JWE.MULTI_KEY_PREFIX + "first.path", firstPem.toString(),
                        JwtPropertyKeys.JWE.MULTI_KEY_PREFIX + "second.path", secondPem.toString()))
                .build();
        JweDecryptionConfigResolver resolver = new JweDecryptionConfigResolver(config);

        JweDecryptionConfig result = resolver.resolveJweDecryptionConfig();

        assertNotNull(result);
        assertEquals(2, result.getKeyCount(), "Both dotted kids must be loaded");
        assertTrue(result.resolveKey("first").isPresent());
        assertTrue(result.resolveKey("second").isPresent());
    }

    private Path createRsaPemFile(String filename) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();

        Path pemFile = tempDir.resolve(filename);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Files.writeString(pemFile, pem);
        return pemFile;
    }
}
