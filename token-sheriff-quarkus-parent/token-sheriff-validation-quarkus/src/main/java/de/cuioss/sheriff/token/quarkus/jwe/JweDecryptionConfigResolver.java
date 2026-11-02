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

import de.cuioss.sheriff.token.quarkus.config.ConfigValueParser;
import de.cuioss.sheriff.token.quarkus.config.JwtPropertyKeys;
import de.cuioss.sheriff.token.validation.jwe.JweAlgorithmPreferences;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig;
import de.cuioss.tools.logging.CuiLogger;
import org.eclipse.microprofile.config.Config;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.*;

import static de.cuioss.sheriff.token.quarkus.TokenSheriffQuarkusLogMessages.INFO;

/**
 * Resolver for creating {@link JweDecryptionConfig} from Quarkus configuration properties.
 * <p>
 * This resolver reads JWE decryption properties and loads private keys using
 * {@link DecryptionKeyLoader}. It supports:
 * <ul>
 *   <li>Single key: via {@code sheriff.token.jwe.decryption-key-path}</li>
 *   <li>Multiple keys: via {@code sheriff.token.jwe.decryption-keys.{kid}.path}</li>
 *   <li>KeyStore: via {@code sheriff.token.jwe.keystore-path}</li>
 * </ul>
 * <p>
 * Returns {@code null} if no JWE property is configured at all. A partial configuration
 * fails fast with an {@link IllegalStateException} instead of being silently ignored:
 * <ul>
 *   <li>an ancillary property (for example {@code keystore-password} or
 *       {@code key-management-algorithms}) set without the key source it belongs to,</li>
 *   <li>a {@code decryption-keys.{kid}} entry without a {@code .path} value,</li>
 *   <li>a {@code default-key-id} that names none of the loaded multi-key kids.</li>
 * </ul>
 *
 * @since 1.0
 * @see JwtPropertyKeys.JWE
 */
public class JweDecryptionConfigResolver {

    private static final CuiLogger LOGGER = new CuiLogger(JweDecryptionConfigResolver.class);

    private final Config config;

    public JweDecryptionConfigResolver(Config config) {
        this.config = config;
    }

    /**
     * Resolves JWE decryption configuration from properties.
     *
     * @return the resolved config, or {@code null} if no JWE property is configured. Multi-key
     *         entries given only as environment variables are not discovered, so a configuration
     *         consisting solely of such entries also yields {@code null} and JWE stays unconfigured
     * @throws IllegalStateException if a JWE property is set without its owning key source,
     *         a multi-key entry (including the bare form {@code decryption-keys.{kid}}) has no
     *         {@code .path} value, or JWE is explicitly configured but key loading
     *         fails (including a {@code default-key-id} that names no loaded key)
     */
    public @Nullable JweDecryptionConfig resolveJweDecryptionConfig() {
        // Check if any JWE property is present
        Optional<String> singleKeyPath = config.getOptionalValue(
                JwtPropertyKeys.JWE.DECRYPTION_KEY_PATH, String.class);
        Optional<String> keystorePath = config.getOptionalValue(
                JwtPropertyKeys.JWE.KEYSTORE_PATH, String.class);

        // Check for multi-key configuration
        Map<String, String> multiKeyPaths = discoverMultiKeyPaths();

        LOGGER.info(INFO.JWE_CONFIG_CHECK, singleKeyPath, keystorePath, multiKeyPaths);

        validateNoOrphanedJweProperties(singleKeyPath.isPresent(), keystorePath.isPresent(),
                !multiKeyPaths.isEmpty());

        if (singleKeyPath.isEmpty() && keystorePath.isEmpty() && multiKeyPaths.isEmpty()) {
            LOGGER.info(INFO.JWE_DECRYPTION_NOT_CONFIGURED);
            return null;
        }

        LOGGER.info(INFO.JWE_DECRYPTION_CONFIG_RESOLVING);

        try {
            JweDecryptionConfig.JweDecryptionConfigBuilder builder = JweDecryptionConfig.builder();

            // Load keys from configured source(s)
            if (singleKeyPath.isPresent()) {
                loadSingleKey(builder, singleKeyPath.get());
            }
            if (keystorePath.isPresent()) {
                loadKeystoreKey(builder, keystorePath.get());
            }
            if (!multiKeyPaths.isEmpty()) {
                loadMultipleKeys(builder, multiKeyPaths);
            }

            // Configure algorithm preferences
            configureAlgorithmPreferences(builder);

            // Configure max encrypted token size
            config.getOptionalValue(JwtPropertyKeys.JWE.MAX_ENCRYPTED_TOKEN_SIZE, Integer.class)
                    .ifPresent(builder::maxEncryptedTokenSize);

            JweDecryptionConfig jweConfig = builder.build();
            LOGGER.info(INFO.JWE_DECRYPTION_CONFIG_RESOLVED, jweConfig.getKeyCount());
            return jweConfig;
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "JWE decryption is explicitly configured but key loading failed. "
                            + "Fix the key configuration or remove JWE properties to disable JWE decryption. "
                            + "Cause: " + e.getMessage(), e);
        }
    }

    private void loadSingleKey(JweDecryptionConfig.JweDecryptionConfigBuilder builder, String keyPath) {
        PrivateKey key = DecryptionKeyLoader.loadFromPem(Path.of(keyPath));

        Optional<String> kid = config.getOptionalValue(
                JwtPropertyKeys.JWE.DECRYPTION_KEY_ID, String.class);

        if (kid.isPresent()) {
            builder.decryptionKey(kid.get(), key);
        }
        builder.defaultDecryptionKey(key);
        LOGGER.debug("Loaded single JWE decryption key from: %s", keyPath);
    }

    private void loadKeystoreKey(JweDecryptionConfig.JweDecryptionConfigBuilder builder, String keystorePath) {
        Optional<String> storePassOpt = config.getOptionalValue(
                JwtPropertyKeys.JWE.KEYSTORE_PASSWORD, String.class);
        Optional<String> alias = config.getOptionalValue(
                JwtPropertyKeys.JWE.KEY_ALIAS, String.class);
        Optional<String> keyPassOpt = config.getOptionalValue(
                JwtPropertyKeys.JWE.KEY_PASSWORD, String.class);

        if (alias.isEmpty()) {
            throw new IllegalStateException(
                    ("JWE keystore is configured at '%s' but no key alias is specified. "
                            + "Configure '%s' to select the key entry, or remove '%s' to disable keystore key loading.")
                            .formatted(keystorePath, JwtPropertyKeys.JWE.KEY_ALIAS, JwtPropertyKeys.JWE.KEYSTORE_PATH));
        }

        char[] storePass = storePassOpt.map(String::toCharArray).orElse(new char[0]);
        char[] keyPass = keyPassOpt.map(String::toCharArray).orElse(storePass.clone());

        PrivateKey key = DecryptionKeyLoader.loadFromKeyStore(
                Path.of(keystorePath), storePass, alias.get(), keyPass);

        Optional<String> kid = config.getOptionalValue(
                JwtPropertyKeys.JWE.DECRYPTION_KEY_ID, String.class);
        if (kid.isPresent()) {
            builder.decryptionKey(kid.get(), key);
        }
        builder.defaultDecryptionKey(key);
        LOGGER.debug("Loaded JWE decryption key from keystore: %s, alias: %s", keystorePath, alias.get());
    }

    private void loadMultipleKeys(JweDecryptionConfig.JweDecryptionConfigBuilder builder,
            Map<String, String> keyPaths) {
        Optional<String> defaultKid = config.getOptionalValue(
                JwtPropertyKeys.JWE.DEFAULT_KEY_ID, String.class);

        Map<String, PrivateKey> loadedKeys = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : keyPaths.entrySet()) {
            String kid = entry.getKey();
            String path = entry.getValue();
            PrivateKey key = DecryptionKeyLoader.loadFromPem(Path.of(path));
            builder.decryptionKey(kid, key);
            loadedKeys.put(kid, key);
            LOGGER.debug("Loaded JWE decryption key '%s' from: %s", kid, path);
        }

        // Set default key: use the configured default kid, or fall back to the first loaded key
        // only when no default kid is configured
        if (defaultKid.isPresent()) {
            PrivateKey defaultKey = loadedKeys.get(defaultKid.get());
            if (defaultKey == null) {
                throw new IllegalStateException(
                        "Property '%s' names key id '%s', which is not among the configured decryption keys %s."
                                .formatted(JwtPropertyKeys.JWE.DEFAULT_KEY_ID, defaultKid.get(), loadedKeys.keySet()));
            }
            builder.defaultDecryptionKey(defaultKey);
        } else if (!loadedKeys.isEmpty()) {
            builder.defaultDecryptionKey(loadedKeys.values().iterator().next());
        }
    }

    /**
     * Discovers the multi-key entries configured under
     * {@code sheriff.token.jwe.decryption-keys.{kid}.*}.
     *
     * <p>
     * A bare property {@code sheriff.token.jwe.decryption-keys.{kid}} (no further dot segment)
     * is not found by the dotted kid discovery, so it is collected separately and treated as a
     * discovered kid. It therefore fails fast for a missing {@code .path} instead of being
     * silently ignored; when the same kid also has a {@code .path} value the bare property
     * changes nothing.
     *
     * @return the key path per kid, in discovery order
     * @throws IllegalStateException if a discovered kid has no {@code .path} value
     */
    private Map<String, String> discoverMultiKeyPaths() {
        Map<String, String> keyPaths = new LinkedHashMap<>();
        String multiKeyPrefix = JwtPropertyKeys.JWE.MULTI_KEY_PREFIX;

        Set<String> kids = new LinkedHashSet<>(ConfigValueParser.discoverNameSegments(config, multiKeyPrefix));
        for (String propertyName : config.getPropertyNames()) {
            if (propertyName.startsWith(multiKeyPrefix)) {
                String remainder = propertyName.substring(multiKeyPrefix.length());
                if (!remainder.isEmpty() && remainder.indexOf('.') < 0) {
                    kids.add(remainder);
                }
            }
        }

        for (String kid : kids) {
            String pathProperty = multiKeyPrefix + kid + ".path";
            String path = config.getOptionalValue(pathProperty, String.class)
                    .orElseThrow(() -> new IllegalStateException(
                            ("JWE decryption key '%s' is configured under '%s%s.' but '%s' is not set. "
                                    + "Configure the key path, or remove the '%s%s.' properties.")
                                    .formatted(kid, multiKeyPrefix, kid, pathProperty, multiKeyPrefix, kid)));
            keyPaths.put(kid, path);
        }
        return keyPaths;
    }

    /**
     * Fails fast when an ancillary JWE property is configured without the key source it
     * belongs to. Without this check such a property would be silently ignored, for
     * example a keystore password with no keystore, or algorithm preferences with no key
     * at all. Runs before the not-configured check, so a configuration consisting only of
     * ancillary properties fails instead of disabling JWE.
     *
     * @param hasSingleKey whether {@code decryption-key-path} is configured
     * @param hasKeystore whether {@code keystore-path} is configured
     * @param hasMultiKeys whether at least one {@code decryption-keys.{kid}.path} is configured
     * @throws IllegalStateException naming the offending property and the source it requires
     */
    private void validateNoOrphanedJweProperties(boolean hasSingleKey, boolean hasKeystore, boolean hasMultiKeys) {
        String keystoreSource = "'%s'".formatted(JwtPropertyKeys.JWE.KEYSTORE_PATH);
        String singleOrKeystoreSource = "'%s' or '%s'".formatted(
                JwtPropertyKeys.JWE.DECRYPTION_KEY_PATH, JwtPropertyKeys.JWE.KEYSTORE_PATH);
        String multiKeySource = "at least one '%s{kid}.path'".formatted(JwtPropertyKeys.JWE.MULTI_KEY_PREFIX);
        String anyKeySource = "'%s', '%s' or at least one '%s{kid}.path'".formatted(
                JwtPropertyKeys.JWE.DECRYPTION_KEY_PATH, JwtPropertyKeys.JWE.KEYSTORE_PATH,
                JwtPropertyKeys.JWE.MULTI_KEY_PREFIX);
        boolean hasAnyKeySource = hasSingleKey || hasKeystore || hasMultiKeys;

        requireOwningSource(JwtPropertyKeys.JWE.DECRYPTION_KEY_ID, hasSingleKey || hasKeystore, singleOrKeystoreSource);
        requireOwningSource(JwtPropertyKeys.JWE.KEYSTORE_PASSWORD, hasKeystore, keystoreSource);
        requireOwningSource(JwtPropertyKeys.JWE.KEY_ALIAS, hasKeystore, keystoreSource);
        requireOwningSource(JwtPropertyKeys.JWE.KEY_PASSWORD, hasKeystore, keystoreSource);
        requireOwningSource(JwtPropertyKeys.JWE.DEFAULT_KEY_ID, hasMultiKeys, multiKeySource);
        requireOwningSource(JwtPropertyKeys.JWE.KEY_MANAGEMENT_ALGORITHMS, hasAnyKeySource, anyKeySource);
        requireOwningSource(JwtPropertyKeys.JWE.CONTENT_ENCRYPTION_ALGORITHMS, hasAnyKeySource, anyKeySource);
        requireOwningSource(JwtPropertyKeys.JWE.MAX_ENCRYPTED_TOKEN_SIZE, hasAnyKeySource, anyKeySource);
    }

    private void requireOwningSource(String property, boolean owningSourcePresent, String requiredSource) {
        if (!owningSourcePresent && config.getOptionalValue(property, String.class).isPresent()) {
            throw new IllegalStateException(
                    ("Property '%s' is configured but the key source it belongs to is not. "
                            + "Configure %s, or remove '%s'.")
                            .formatted(property, requiredSource, property));
        }
    }

    private void configureAlgorithmPreferences(JweDecryptionConfig.JweDecryptionConfigBuilder builder) {
        Optional<String> keyMgmtAlgs = config.getOptionalValue(
                JwtPropertyKeys.JWE.KEY_MANAGEMENT_ALGORITHMS, String.class);
        Optional<String> contentEncAlgs = config.getOptionalValue(
                JwtPropertyKeys.JWE.CONTENT_ENCRYPTION_ALGORITHMS, String.class);

        if (keyMgmtAlgs.isPresent() || contentEncAlgs.isPresent()) {
            List<String> keyAlgs = keyMgmtAlgs
                    .map(ConfigValueParser::splitCsv)
                    .orElseGet(JweAlgorithmPreferences::getDefaultKeyManagementAlgorithms);
            List<String> encAlgs = contentEncAlgs
                    .map(ConfigValueParser::splitCsv)
                    .orElseGet(JweAlgorithmPreferences::getDefaultContentEncryptionAlgorithms);

            builder.algorithmPreferences(new JweAlgorithmPreferences(keyAlgs, encAlgs));
        }
    }
}
