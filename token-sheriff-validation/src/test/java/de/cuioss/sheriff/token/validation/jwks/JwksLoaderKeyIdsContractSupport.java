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
package de.cuioss.sheriff.token.validation.jwks;

import de.cuioss.http.client.adapter.RetryConfig;
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.validation.jwks.http.HttpJwksLoader;
import de.cuioss.sheriff.token.validation.test.InMemoryJWKSFactory;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fixtures for {@link JwksLoaderKeyIdsContractTest}: the JWKS documents each contract point is
 * asserted against, the loader types the factory returns, and the configuration of the HTTP loader
 * served by the MockWebServer {@code JwksResolveDispatcher}.
 */
@UtilityClass
class JwksLoaderKeyIdsContractSupport {

    static final String FIRST_KID = "first-kid";
    static final String SECOND_KID = "second-kid";
    static final String BLANK_KID = " ";
    static final String ISSUER = "https://contract.example.com";

    /**
     * The loader types {@link JwksLoaderFactory} returns, one per factory method.
     */
    enum LoaderType {
        /** {@link JwksLoaderFactory#createFileLoader(String)}. */
        FILE,
        /** {@link JwksLoaderFactory#createInMemoryLoader(String)}. */
        IN_MEMORY,
        /** {@link JwksLoaderFactory#createHttpLoader(HttpJwksLoaderConfig)}. */
        HTTP
    }

    /**
     * Argument source of the contract points that hold for every loader type.
     *
     * @return every loader type the factory returns
     */
    static Stream<LoaderType> loaderTypes() {
        return Arrays.stream(LoaderType.values());
    }

    /**
     * @return a JWKS with two keys, {@value #FIRST_KID} and {@value #SECOND_KID}
     */
    static String twoKeyJwks() {
        return combineJwks(
                InMemoryJWKSFactory.createValidJwksWithKeyId(InMemoryKeyMaterialHandler.Algorithm.RS256, FIRST_KID),
                InMemoryJWKSFactory.createValidJwksWithKeyId(InMemoryKeyMaterialHandler.Algorithm.RS384, SECOND_KID));
    }

    /**
     * @return a JWKS with the key {@value #FIRST_KID} and a second key whose {@code kid} is whitespace only
     */
    static String blankKidJwks() {
        return combineJwks(
                InMemoryJWKSFactory.createValidJwksWithKeyId(InMemoryKeyMaterialHandler.Algorithm.RS256, FIRST_KID),
                InMemoryJWKSFactory.createValidJwksWithKeyId(InMemoryKeyMaterialHandler.Algorithm.RS384, BLANK_KID));
    }

    /**
     * @return a JWKS with two keys sharing the {@code kid} {@value #FIRST_KID}: an RS256 key first, an RS384
     *         key second
     */
    static String duplicateKidJwks() {
        return combineJwks(
                InMemoryJWKSFactory.createValidJwksWithKeyId(InMemoryKeyMaterialHandler.Algorithm.RS256, FIRST_KID),
                InMemoryJWKSFactory.createValidJwksWithKeyId(InMemoryKeyMaterialHandler.Algorithm.RS384, FIRST_KID));
    }

    /**
     * @return a JWKS with the single key {@value #SECOND_KID}, the key set a rotation installs
     */
    static String rotatedJwks() {
        return InMemoryJWKSFactory.createValidJwksWithKeyId(InMemoryKeyMaterialHandler.Algorithm.RS384, SECOND_KID);
    }

    /**
     * @return a well-formed JWKS without any usable key
     */
    static String noUsableKeyJwks() {
        return InMemoryJWKSFactory.createEmptyJwks();
    }

    /**
     * Writes JWKS content to a file for the file loader.
     *
     * @param directory the directory to write to
     * @param content   the JWKS content
     * @return the path of the written file
     */
    static Path writeJwksFile(Path directory, String content) {
        try {
            return Files.writeString(Files.createTempFile(directory, "jwks", ".json"), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Configuration of the HTTP loader served by the dispatcher: loopback and cleartext HTTP allowed,
     * issuer set, a single fetch attempt.
     *
     * @param jwksUrl                the dispatcher's JWKS URL
     * @param refreshIntervalSeconds the background refresh interval, {@code 0} to disable it
     * @param gracePeriod            the key rotation grace period
     * @return the configuration
     */
    static HttpJwksLoaderConfig httpConfig(String jwksUrl, int refreshIntervalSeconds, Duration gracePeriod) {
        return HttpJwksLoaderConfig.builder()
                .allowLoopbackEgress(true)
                .allowInsecureHttp(true)
                .jwksUrl(jwksUrl)
                .issuerIdentifier(ISSUER)
                .retryConfig(RetryConfig.builder().maxAttempts(1).build())
                .refreshIntervalSeconds(refreshIntervalSeconds)
                .keyRotationGracePeriod(gracePeriod)
                .build();
    }

    /**
     * Stops the background refresh of an HTTP loader; other loaders hold no resources.
     *
     * @param loader the loader to release
     */
    static void release(JwksLoader loader) {
        if (loader instanceof HttpJwksLoader httpLoader) {
            httpLoader.close();
        }
    }

    /**
     * Merges the key arrays of single-key JWKS documents into one JWKS document.
     */
    private static String combineJwks(String... jwksDocuments) {
        String prefix = "{\"keys\":[";
        String suffix = "]}";
        return Arrays.stream(jwksDocuments)
                .map(document -> document.substring(prefix.length(), document.length() - suffix.length()))
                .collect(Collectors.joining(",", prefix, suffix));
    }
}
