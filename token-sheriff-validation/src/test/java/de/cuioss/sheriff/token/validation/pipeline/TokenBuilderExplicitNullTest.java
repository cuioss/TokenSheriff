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
 * The explicit-null handling and the subject-type refusal of {@link TokenBuilder}, exercised on the
 * builder itself: the {@code TokenValidator} entry points refuse a body without {@code iss} first, so a
 * body of only {@code {"jti":null}} is reachable here only; the builder is also the one place that sees
 * every mapper configuration, including a custom mapper for {@code sub}, without building a validator.
 * An explicit JSON {@code null} reads exactly like an absent member, whatever mapper the claim has; a
 * non-string {@code sub} is a typed {@code MISSING_CLAIM} rejection and is counted once.
 */
@DisplayName("TokenBuilder reads explicit nulls as absent and refuses non-string subjects")
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
    @DisplayName("A body of only a null jti returns an empty token exactly like an empty body")
    void shouldReturnEmptyForBodyOfOnlyNullJti() {
        var builder = new TokenBuilder(issuerConfig().build(), counter);

        assertTrue(builder.createAccessToken(decoded("{\"jti\":null}")).isEmpty());
        assertTrue(builder.createIdToken(decoded("{\"jti\":null}")).isEmpty());
        assertEquals(0, counter.getCount(EventType.MISSING_CLAIM));
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
    @DisplayName("An explicit null custom claim with a strict mapper reads as absent")
    void shouldReadExplicitNullCustomClaimWithStrictMapperAsAbsent() {
        var builder = new TokenBuilder(issuerConfig().claimMapper(CUSTOM_CLAIM, new StrictStringMapper()).build(), counter);
        var body = decoded("{\"iss\":\"test-issuer\",\"" + CUSTOM_CLAIM + "\":null}");

        var token = builder.createAccessToken(body).orElseThrow();

        assertFalse(token.getClaims().containsKey(CUSTOM_CLAIM));
        assertEquals(0, counter.getCount(EventType.MISSING_CLAIM));
    }

    @Test
    @DisplayName("An explicit null custom claim with a stringifying mapper reads as absent")
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
