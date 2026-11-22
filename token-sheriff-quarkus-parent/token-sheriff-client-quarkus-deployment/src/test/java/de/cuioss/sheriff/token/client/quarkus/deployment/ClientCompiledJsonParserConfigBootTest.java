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
package de.cuioss.sheriff.token.client.quarkus.deployment;

import de.cuioss.sheriff.token.client.quarkus.config.ClientRuntimeConfig;
import de.cuioss.sheriff.token.client.token.TokenResponse;
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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Pins that a {@code @CompiledJson} type of {@code token-sheriff-client} parses through the {@code DslJson} of
 * one {@link ParserConfig} inside a booted Quarkus application when that instance is first built on a thread whose
 * context class loader is the platform class loader.
 * <p>
 * {@link ParserConfig} lives in {@code token-sheriff-validation} and looks its generated DSL-JSON converters up
 * through its own class loader. The client parses its token-endpoint responses through the same instance, so the
 * client converters must be reachable from that lookup as well, not only the validation converters that
 * {@code JwksParserConfigBootTest} in {@code token-sheriff-validation-quarkus-deployment} pins. A {@link Jwks}
 * parsed through the same instance is the matched control.
 * <p>
 * In this booted application both artifacts are loaded by one class loader, so the lookup through the class loader
 * of {@link ParserConfig} reaches the client converters because that loader also loaded the client. The test
 * asserts that shared loader as the stated precondition: a client loaded by a class loader the {@link ParserConfig}
 * loader does not see is the limit described on {@code ParserConfig#createDslJson}, and it cannot be built here
 * without a class-path change. Should the two loaders ever differ, the precondition assertion fails and the limit
 * has to be revisited.
 * <p>
 * The test lives in {@code token-sheriff-client-quarkus-deployment} because the validation deployment module has no
 * client on its class path.
 */
@Tag("quarkus-boot")
@DisplayName("A client @CompiledJson type parses through ParserConfig in a booted application from a foreign context class loader")
class ClientCompiledJsonParserConfigBootTest {

    private static final String ACCESS_TOKEN = "boot-test-access-token";

    private static final String TOKEN_RESPONSE = """
            {"access_token":"%s","token_type":"Bearer","expires_in":3600}""".formatted(ACCESS_TOKEN);

    private static final String KID = "boot-test-key";

    private static final String JWKS = """
            {"keys":[{"kty":"RSA","kid":"%s","alg":"RS256","n":"AQAB","e":"AQAB"}]}""".formatted(KID);

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withEmptyApplication()
            .overrideConfigKey(ClientRuntimeConfig.ISSUER, "https://issuer.example.com/realms/demo")
            .overrideConfigKey(ClientRuntimeConfig.CLIENT_ID, "demo-client");

    @Test
    @DisplayName("Should parse a client TokenResponse and a JWKS when the DslJson instance is first built under a foreign context class loader")
    void shouldParseClientTypeWhenBuiltUnderForeignContextClassLoader() throws Exception {
        ClassLoader foreign = ClassLoader.getPlatformClassLoader();
        ParserConfig config = ParserConfig.builder().build();

        TokenResponse tokenResponse = parseOnThreadWith(foreign, config, TokenResponse.class, TOKEN_RESPONSE);
        Jwks jwks = parseOnThreadWith(foreign, config, Jwks.class, JWKS);

        assertAll("client type and matched validation control through one ParserConfig",
                () -> assertNotSame(ParserConfig.class.getClassLoader(), foreign,
                        "the thread's context class loader must not be the one that loaded ParserConfig"),
                () -> assertSame(ParserConfig.class.getClassLoader(), TokenResponse.class.getClassLoader(),
                        "precondition: the client type shares the class loader of ParserConfig in this application; "
                                + "a client from a loader ParserConfig does not see is the limit on createDslJson"),
                () -> assertEquals(ACCESS_TOKEN, tokenResponse.getAccessToken().orElse(null),
                        "the client token response must parse with its access token"),
                () -> assertEquals(KID, jwks.keys().getFirst().kid(),
                        "the matched control: the JWKS must parse through the same instance"));
    }

    private static <T> T parseOnThreadWith(ClassLoader contextClassLoader, ParserConfig config, Class<T> type,
            String json) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "client-parser-config-boot-test");
            thread.setContextClassLoader(contextClassLoader);
            return thread;
        });
        try {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            return executor.submit(() -> config.getDslJson().deserialize(type, body, body.length))
                    .get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
}
