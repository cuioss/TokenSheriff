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
package de.cuioss.sheriff.token.quarkus.config;

import de.cuioss.sheriff.token.validation.IssuerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static de.cuioss.sheriff.token.quarkus.config.IssuerConfigResolverJwksLoaderOptInTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the HTTP-only {@code jwks.http.unknown-kid-refresh-window-seconds} and
 * {@code jwks.http.initial-load-retry} keys of {@link IssuerConfigResolver} fail startup, naming the key,
 * when set on an issuer that does not load its JWKS over HTTP (ADR-0026, ADR-0030). The value cases live in
 * {@link IssuerConfigResolverJwksLoaderOptInTest}.
 */
@DisplayName("IssuerConfigResolver HTTP-only JWKS loader keys orphaned on a non-HTTP issuer")
class IssuerConfigResolverJwksLoaderOrphanedTest {

    private static final String EMPTY_JWKS = "{\"keys\":[]}";

    @TempDir
    Path tempDir;

    private Path jwksFile() throws IOException {
        return Files.writeString(tempDir.resolve("jwks.json"), EMPTY_JWKS);
    }

    private static Map<String, String> issuerWithFilePath(Path jwksFile) {
        Map<String, String> properties = issuerWithJwksUrl();
        properties.remove(JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(ISSUER));
        properties.put(JwtPropertyKeys.ISSUERS.JWKS_FILE_PATH.formatted(ISSUER), jwksFile.toString());
        return properties;
    }

    private static Map<String, String> issuerWithInlineContent() {
        Map<String, String> properties = issuerWithJwksUrl();
        properties.remove(JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(ISSUER));
        properties.put(JwtPropertyKeys.ISSUERS.JWKS_CONTENT.formatted(ISSUER), EMPTY_JWKS);
        return properties;
    }

    /**
     * Each HTTP-only property with a value that is valid on an HTTP issuer.
     */
    static Stream<Arguments> httpOnlyProperties() {
        return Stream.of(
                Arguments.of(WINDOW_PROPERTY, "60"),
                Arguments.of(RETRY_PROPERTY, "true"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("httpOnlyProperties")
    @DisplayName("should fail startup for the property on an issuer with a JWKS file path")
    void shouldFailWhenOrphanedOnFilePathIssuer(String property, String value) throws Exception {
        Map<String, String> properties = issuerWithFilePath(jwksFile());
        properties.put(property, value);
        IssuerConfigResolver resolver = resolverFor(properties);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                resolver::resolveIssuerConfigs);

        assertTrue(exception.getMessage().contains(property),
                "The failure must name the orphaned property: " + exception.getMessage());
    }

    @Test
    @DisplayName("should resolve the issuer with a JWKS file path when neither property is set")
    void shouldResolveFilePathIssuerWithoutProperties() throws Exception {
        List<IssuerConfig> result = resolverFor(issuerWithFilePath(jwksFile())).resolveIssuerConfigs();

        assertEquals(1, result.size(), "A file-path issuer without the HTTP-only properties must resolve");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("httpOnlyProperties")
    @DisplayName("should fail startup for the property on an issuer with inline JWKS content")
    void shouldFailWhenOrphanedOnContentIssuer(String property, String value) {
        Map<String, String> properties = issuerWithInlineContent();
        properties.put(property, value);
        IssuerConfigResolver resolver = resolverFor(properties);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                resolver::resolveIssuerConfigs);

        assertTrue(exception.getMessage().contains(property),
                "The failure must name the orphaned property: " + exception.getMessage());
    }

    @Test
    @DisplayName("should resolve the issuer with inline JWKS content when neither property is set")
    void shouldResolveContentIssuerWithoutProperties() {
        List<IssuerConfig> result = resolverFor(issuerWithInlineContent()).resolveIssuerConfigs();

        assertEquals(1, result.size(), "An inline-content issuer without the HTTP-only properties must resolve");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("httpOnlyProperties")
    @DisplayName("should resolve the issuer for the same property on an HTTP issuer")
    void shouldResolveSamePropertyOnHttpIssuer(String property, String value) {
        Map<String, String> properties = issuerWithJwksUrl();
        properties.put(property, value);

        List<IssuerConfig> result = resolverFor(properties).resolveIssuerConfigs();

        assertEquals(1, result.size(), "The same property is valid on an issuer that loads its JWKS over HTTP");
    }
}
