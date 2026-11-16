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

import de.cuioss.sheriff.token.quarkus.servlet.HttpRequestResolverMock;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.easymock.Capture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static de.cuioss.test.juli.LogAsserts.assertSingleLogMessagePresentContaining;
import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the {@code Authorization} scheme rules {@link BearerTokenProducer} adopted with
 * {@code BearerCredentials}, each a default-behaviour change itemised in ADR-0031:
 * <ul>
 *   <li>OD-1: a lower-case or mixed-case scheme ({@code bearer}, {@code BEARER}, {@code bEaReR}) is now a
 *       presented token, where it was treated as no token.</li>
 *   <li>OD-2: leading and trailing whitespace of the field value is now ignored, and a token holding an
 *       internal space is now {@link BearerTokenStatus#INVALID_REQUEST} (HTTP 400) where it reached the
 *       validator and became {@link BearerTokenStatus#PARSING_ERROR} (HTTP 401).</li>
 * </ul>
 * Each refused input is paired with the nearest accepted one.
 */
@EnableTestLogger(rootLevel = TestLogLevel.DEBUG)
@DisplayName("BearerTokenProducer applies the RFC scheme rules of BearerCredentials")
class BearerTokenProducerSchemeRuleTest {

    private static final String TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature";

    private TokenValidator tokenValidator;
    private HttpRequestResolverMock servletResolverMock;
    private BearerTokenProducer producer;

    @BeforeEach
    void setUp() {
        tokenValidator = createMock(TokenValidator.class);
        servletResolverMock = new HttpRequestResolverMock();
        producer = new BearerTokenProducer(tokenValidator, servletResolverMock,
                () -> EmptyJsonWebToken.INSTANCE, "Authorization", "Bearer");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"Bearer", "bearer", "BEARER", "bEaReR"})
    @DisplayName("OD-1: every case spelling of the scheme presents the token")
    void shouldAcceptSchemeCaseInsensitively(String scheme) {
        servletResolverMock.setHeader("Authorization", scheme + " " + TOKEN);

        assertEquals(TOKEN, validateAndCaptureToken(), "OD-1: the scheme is matched case-insensitively");
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"  Bearer " + TOKEN, "Bearer " + TOKEN + "  ", "\tBearer   " + TOKEN + "\t"})
    @DisplayName("OD-2: leading and trailing whitespace of the field value is ignored")
    void shouldIgnoreSurroundingWhitespace(String value) {
        servletResolverMock.setHeader("Authorization", value);

        assertEquals(TOKEN, validateAndCaptureToken(), "OD-2: the validator receives the token without whitespace");
    }

    @Test
    @DisplayName("OD-2: a token holding an internal space is INVALID_REQUEST and never reaches the validator")
    void shouldRefuseInternalSpaceAsInvalidRequest() {
        servletResolverMock.setHeader("Authorization", "Bearer abc def");
        replay(tokenValidator);

        BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

        assertEquals(BearerTokenStatus.INVALID_REQUEST, result.getStatus(),
                "OD-2: an internal space is malformed credentials (400), no longer a PARSING_ERROR (401)");
        assertEquals(400, BearerTokenResponseFactory.createResponse(result).getStatus(), "answered with HTTP 400");
        assertEquals(BearerTokenProducer.MALFORMED_TOKEN_MESSAGE, result.getErrorMessage().orElse(null),
                "the message names the refused character class, not an empty token");
        assertSingleLogMessagePresentContaining(TestLogLevel.DEBUG,
                BearerTokenProducer.MALFORMED_TOKEN_MESSAGE + " - invalid request per RFC 6750");
        verify(tokenValidator);
    }

    @Test
    @DisplayName("OD-2: a control character in the token is INVALID_REQUEST without the cookie fallback")
    void shouldRefuseControlCharacterWithoutCookieFallback() {
        BearerTokenProducer cookieProducer = new BearerTokenProducer(tokenValidator, servletResolverMock,
                () -> EmptyJsonWebToken.INSTANCE, "Cookie", "Bearer");
        servletResolverMock.setHeader("Authorization", "Bearer abc\u0000def");
        servletResolverMock.setHeader("Cookie", "Bearer=" + TOKEN);
        replay(tokenValidator);

        BearerTokenResult result = cookieProducer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

        assertEquals(BearerTokenStatus.INVALID_REQUEST, result.getStatus(),
                "presented but malformed credentials are refused; the cookie is not consulted");
        verify(tokenValidator);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"Bearer\t" + TOKEN, "Bearerx " + TOKEN, "Bearer"})
    @DisplayName("OD-2: a tab separator, a longer scheme and the bare scheme are no token at all")
    void shouldTreatNonBearerCredentialsAsNoToken(String value) {
        servletResolverMock.setHeader("Authorization", value);
        replay(tokenValidator);

        BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

        assertEquals(BearerTokenStatus.NO_TOKEN_GIVEN, result.getStatus(),
                "OD-2: only the scheme followed by 1*SP carries Bearer credentials");
        verify(tokenValidator);
    }

    /**
     * Runs the producer against a validator that accepts any token and returns the token string it was
     * handed, so a case asserts what reached the validator rather than only the resulting status.
     */
    private String validateAndCaptureToken() {
        Capture<AccessTokenRequest> capture = Capture.newInstance();
        expect(tokenValidator.createAccessToken(capture(capture))).andReturn(createAccessToken());
        replay(tokenValidator);

        BearerTokenResult result = producer.getBearerTokenResult(Set.of(), Set.of(), Set.of());

        assertEquals(BearerTokenStatus.FULLY_VERIFIED, result.getStatus(), "the presented token is validated");
        verify(tokenValidator);
        return capture.getValue().tokenString();
    }

    private static AccessTokenContent createAccessToken() {
        Map<String, ClaimValue> claims = new HashMap<>();
        claims.put(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("test-issuer"));
        claims.put(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString("test-subject"));
        var now = OffsetDateTime.now();
        var expires = now.plusHours(1);
        claims.put(ClaimName.EXPIRATION.getName(), ClaimValue.forDateTime(
                String.valueOf(expires.toEpochSecond()), expires));
        claims.put(ClaimName.ISSUED_AT.getName(), ClaimValue.forDateTime(
                String.valueOf(now.toEpochSecond()), now));
        return new AccessTokenContent(claims, "raw-token");
    }
}
