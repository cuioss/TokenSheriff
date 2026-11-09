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
package de.cuioss.sheriff.token.validation.pipeline;

import com.dslplatform.json.DslJson;
import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.events.SecurityEventCounter.EventType;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.domain.claim.mapper.IdentityMapper;
import de.cuioss.sheriff.token.validation.domain.claim.mapper.StrictStringMapper;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.json.JwtHeader;
import de.cuioss.sheriff.token.validation.json.MapRepresentation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The explicit-null and subject-type refusals of {@link TokenBuilder}, exercised on the builder
 * itself: the {@code TokenValidator} entry points refuse a body without {@code iss} first, so a body
 * of only {@code {"jti":null}} and a custom mapper configured for {@code sub} are reachable here only.
 * Every refusal is a typed {@code MISSING_CLAIM} rejection and is counted once.
 */
@DisplayName("TokenBuilder refusals of explicit nulls and non-string subjects")
class TokenBuilderExplicitNullTest {

    private static final String CUSTOM_CLAIM = "tenant_id";

    private SecurityEventCounter counter;

    @BeforeEach
    void setUp() {
        counter = new SecurityEventCounter();
    }

    private static IssuerConfig.IssuerConfigBuilder issuerConfig() {
        return IssuerConfig.builder()
                .issuerIdentifier("test-issuer")
                .jwksContent("{\"keys\":[]}")
                .audienceValidationDisabled(true);
    }

    private static DecodedJwt decoded(String bodyJson) {
        try {
            DslJson<Object> dslJson = ParserConfig.builder().build().getDslJson();
            var emptyHeader = new JwtHeader(null, null, null, null, null, null, null, null, null, null);
            return new DecodedJwt(emptyHeader, MapRepresentation.fromJson(dslJson, bodyJson), null,
                    new String[]{"", "", ""}, "test-validation");
        } catch (IOException e) {
            throw new AssertionError("Failed to parse the test body", e);
        }
    }

    private static void assertMissingClaim(TokenValidationException exception, String expectedMessage) {
        assertEquals(EventType.MISSING_CLAIM, exception.getEventType());
        assertEquals(expectedMessage, exception.getMessage());
    }

    private void assertRefusedOnce(TokenValidationException exception, String expectedMessage) {
        assertMissingClaim(exception, expectedMessage);
        assertEquals(1, counter.getCount(EventType.MISSING_CLAIM));
    }

    @Test
    @DisplayName("A body of only a null jti is refused, not returned as an empty token")
    void shouldRefuseBodyOfOnlyNullJti() {
        var builder = new TokenBuilder(issuerConfig().build(), counter);
        var accessToken = decoded("{\"jti\":null}");
        var idToken = decoded("{\"jti\":null}");

        assertMissingClaim(assertThrows(TokenValidationException.class, () -> builder.createAccessToken(accessToken)),
                "Malformed claim 'jti': expected a JSON string but got null");
        assertMissingClaim(assertThrows(TokenValidationException.class, () -> builder.createIdToken(idToken)),
                "Malformed claim 'jti': expected a JSON string but got null");
        assertEquals(2, counter.getCount(EventType.MISSING_CLAIM), "Each refusal is counted once");
    }

    @Test
    @DisplayName("Non-vacuity: a genuinely empty body still returns an empty token")
    void shouldStillReturnEmptyForEmptyBody() {
        var builder = new TokenBuilder(issuerConfig().build(), counter);

        assertTrue(builder.createAccessToken(decoded("{}")).isEmpty());
        assertTrue(builder.createIdToken(decoded("{}")).isEmpty());
        assertEquals(0, counter.getCount(EventType.MISSING_CLAIM));
    }

    @Test
    @DisplayName("An explicit null custom claim reaches its strict mapper and is refused")
    void shouldRefuseExplicitNullCustomClaimWithStrictMapper() {
        var builder = new TokenBuilder(issuerConfig().claimMapper(CUSTOM_CLAIM, new StrictStringMapper()).build(), counter);
        var body = decoded("{\"iss\":\"test-issuer\",\"" + CUSTOM_CLAIM + "\":null}");

        assertRefusedOnce(assertThrows(TokenValidationException.class, () -> builder.createAccessToken(body)),
                "Malformed claim '" + CUSTOM_CLAIM + "': expected a JSON string but got null");
    }

    @Test
    @DisplayName("An explicit null custom claim without a refusing mapper still reads as absent")
    void shouldReadExplicitNullCustomClaimAsAbsent() {
        var builder = new TokenBuilder(issuerConfig().claimMapper(CUSTOM_CLAIM, new IdentityMapper()).build(), counter);
        var body = decoded("{\"iss\":\"test-issuer\",\"" + CUSTOM_CLAIM + "\":null}");

        var token = builder.createAccessToken(body).orElseThrow();

        assertFalse(token.getClaims().containsKey(CUSTOM_CLAIM));
        assertEquals(0, counter.getCount(EventType.MISSING_CLAIM));
    }

    @Test
    @DisplayName("A numeric sub is refused although a custom mapper replaces the strict one")
    void shouldRefuseNumericSubjectWithCustomMapper() {
        var builder = new TokenBuilder(issuerConfig().claimMapper("sub", new IdentityMapper()).build(), counter);
        var body = decoded("{\"iss\":\"test-issuer\",\"sub\":123}");

        assertRefusedOnce(assertThrows(TokenValidationException.class, () -> builder.createAccessToken(body)),
                "Malformed claim 'sub': expected a JSON string but got Long");
    }

    @Test
    @DisplayName("Non-vacuity: a string sub still reaches the custom mapper")
    void shouldMapStringSubjectWithCustomMapper() {
        var builder = new TokenBuilder(issuerConfig().claimMapper("sub", new IdentityMapper()).build(), counter);

        var token = builder.createAccessToken(decoded("{\"iss\":\"test-issuer\",\"sub\":\"123\"}")).orElseThrow();

        assertEquals("123", token.getSubject().orElseThrow());
        assertEquals(0, counter.getCount(EventType.MISSING_CLAIM));
    }
}
