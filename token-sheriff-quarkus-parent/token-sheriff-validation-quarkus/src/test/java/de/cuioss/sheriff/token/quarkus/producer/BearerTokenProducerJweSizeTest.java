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
package de.cuioss.sheriff.token.quarkus.producer;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.quarkus.servlet.HttpRequestResolverMock;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig;
import de.cuioss.sheriff.token.validation.test.JweTestTokenFactory;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the JWE size behaviour of {@link BearerTokenProducer} with a real, JWE-enabled
 * {@link TokenValidator}: a valid JWE above the 8 KiB JWS limit and within the 32 KiB JWE limit yields a
 * verified result, and a 5-part token above the JWE limit yields a counted {@code TOKEN_SIZE_EXCEEDED}
 * parsing error. Neither case leaves the producer as an exception: both are reported through the
 * {@link BearerTokenResult} status.
 */
@EnableTestLogger(rootLevel = TestLogLevel.DEBUG)
@DisplayName("JWE size with a real validator")
class BearerTokenProducerJweSizeTest {

    private static final int JWE_LIMIT = JweDecryptionConfig.DEFAULT_MAX_ENCRYPTED_TOKEN_SIZE;

    private HttpRequestResolverMock servletResolverMock;
    private KeyPair encryptionKeyPair;
    private TestTokenHolder holder;
    private TokenValidator realValidator;
    private BearerTokenProducer realProducer;

    @BeforeEach
    void setUpRealValidator() {
        servletResolverMock = new HttpRequestResolverMock();
        encryptionKeyPair = JweTestTokenFactory.generateRsaKeyPair();
        holder = TestTokenGenerators.accessTokens().next();
        realValidator = TokenValidator.builder()
                .issuerConfig(holder.getIssuerConfig())
                .jweDecryptionConfig(JweDecryptionConfig.builder()
                        .defaultDecryptionKey(encryptionKeyPair.getPrivate())
                        .build())
                .build();
        realProducer = new BearerTokenProducer(realValidator, servletResolverMock,
                () -> EmptyJsonWebToken.INSTANCE, "Authorization", "Bearer");
    }

    @AfterEach
    void closeRealValidator() {
        realValidator.close();
    }

    @Test
    @DisplayName("A valid JWE of about 10 KiB yields a verified bearer token result")
    void shouldVerifyJweAboveJwsLimit() {
        for (int i = 0; i < 4; i++) {
            holder.withClaim("pad" + i, ClaimValue.forPlainString("p".repeat(1000)));
        }
        String jwe = JweTestTokenFactory.encryptAsJwe(holder.getRawToken(),
                encryptionKeyPair.getPublic(), "RSA-OAEP", "A256GCM", null);
        assertTrue(jwe.length() > ParserConfig.DEFAULT_MAX_TOKEN_SIZE && jwe.length() <= JWE_LIMIT,
                "Non-vacuity: the JWE must lie between the two limits, was " + jwe.length());
        servletResolverMock.setBearerToken(jwe);

        BearerTokenResult result = realProducer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

        assertEquals(BearerTokenStatus.FULLY_VERIFIED, result.getStatus());
        assertEquals("p".repeat(1000),
                result.getAccessTokenContent().orElseThrow().getClaims().get("pad0").getOriginalString());
    }

    @Test
    @DisplayName("A 5-part token above the JWE limit yields a counted TOKEN_SIZE_EXCEEDED parsing error")
    void shouldRejectJweAboveJweLimit() {
        String tail = ".b.c.d.e";
        servletResolverMock.setBearerToken("a".repeat(JWE_LIMIT + 1 - tail.length()) + tail);

        BearerTokenResult result = realProducer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

        assertEquals(BearerTokenStatus.PARSING_ERROR, result.getStatus());
        assertEquals(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED, result.getErrorEventType().orElseThrow());
        assertEquals(1, realValidator.getSecurityEventCounter()
                .getCount(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED));
    }
}
