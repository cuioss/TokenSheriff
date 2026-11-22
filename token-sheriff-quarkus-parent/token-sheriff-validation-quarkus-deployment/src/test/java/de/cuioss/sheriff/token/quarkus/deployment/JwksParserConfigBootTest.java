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
package de.cuioss.sheriff.token.quarkus.deployment;

import de.cuioss.sheriff.token.commons.transport.Jwks;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Pins that a JWKS parses through {@link ParserConfig} inside a booted Quarkus application when the
 * {@code DslJson} instance of the configuration is first built on a thread whose context class loader does not see
 * {@code token-sheriff-validation}.
 * <p>
 * That is the thread a JWKS load runs on: a {@code ForkJoinPool} common-pool worker carries the system class
 * loader, while Quarkus dev mode loads {@code token-sheriff-validation} through a Quarkus class loader. A converter
 * lookup through the context class loader of such a thread finds no generated DSL-JSON converter, and the parse
 * fails with {@code TokenSheriff-141} ("Unable to find reader for provided type: class ...Jwks"); the test pins
 * that the lookup goes through the class loader of {@link ParserConfig} instead. The thread here carries the
 * platform class loader, which sees none of the application's artifacts, so the case does not depend on how the
 * test class path is assembled.
 * <p>
 * The test lives in the deployment module because a Quarkus boot test needs the extension's deployment class
 * path, while {@link ParserConfig} is in {@code token-sheriff-validation}.
 */
@Tag("quarkus-boot")
@DisplayName("A JWKS parses through ParserConfig in a booted application from a foreign context class loader")
class JwksParserConfigBootTest {

    private static final String KID = "boot-test-key";

    private static final String JWKS = """
            {"keys":[{"kty":"RSA","kid":"%s","alg":"RS256","n":"AQAB","e":"AQAB"}]}""".formatted(KID);

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withEmptyApplication()
            .withConfigurationResource("application-integration.properties");

    @Test
    @DisplayName("Should parse a JWKS when the DslJson instance is first built under a foreign context class loader")
    void shouldParseJwksWhenBuiltUnderForeignContextClassLoader() throws Exception {
        ClassLoader foreign = ClassLoader.getPlatformClassLoader();
        ParserConfig config = ParserConfig.builder().build();

        Jwks jwks = parseOnThreadWith(foreign, config);

        assertNotSame(ParserConfig.class.getClassLoader(), foreign,
                "the thread's context class loader must not be the one that loaded ParserConfig");
        assertEquals(1, jwks.keys().size(), "the JWKS must parse with its one key: " + jwks);
        assertEquals(KID, jwks.keys().getFirst().kid(), "the key must carry the kid of the JWKS");
    }

    private static Jwks parseOnThreadWith(ClassLoader contextClassLoader, ParserConfig config) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "jwks-parser-config-boot-test");
            thread.setContextClassLoader(contextClassLoader);
            return thread;
        });
        try {
            byte[] body = JWKS.getBytes(StandardCharsets.UTF_8);
            return executor.submit(() -> config.getDslJson().deserialize(Jwks.class, body, body.length))
                    .get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
}
