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
package de.cuioss.sheriff.token.validation.pipeline.validator;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenType;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.domain.claim.mapper.IdentityMapper;
import de.cuioss.sheriff.token.validation.domain.token.TokenContent;
import de.cuioss.sheriff.token.validation.json.JwtHeader;
import de.cuioss.sheriff.token.validation.json.MapRepresentation;
import de.cuioss.sheriff.token.validation.pipeline.DecodedJwt;
import de.cuioss.sheriff.token.validation.pipeline.TokenBuilder;
import de.cuioss.test.generator.Generators;
import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Builds tokens whose {@code azp} or {@code client_id} identifier claim carries a real JSON type,
 * including the non-string types (array, number, boolean, object), by parsing a JSON body with the
 * production DSL-JSON configuration and running it through {@link TokenBuilder}.
 * <p>
 * Validator tests that construct {@link ClaimValue} instances directly cannot express the JSON type
 * of a claim; this fixture exercises the real mapping path.
 */
final class JsonTypedIdentifierClaimFixture {

    /** The client id the array and object cases carry inside their structure. */
    static final String EMBEDDED_CLIENT_ID = "client1";

    private static final String RAW_TOKEN = "json-typed-identifier-claim-token";

    private static final String BODY_PREFIX = "{\"iss\":\"test-issuer\",\"sub\":\"test-subject\"";

    private JsonTypedIdentifierClaimFixture() {
    }

    /**
     * Provides one case per non-string JSON type: a display name and the JSON literal of the value.
     * The cases serve both the azp and the client_id claim.
     *
     * @return the array, number, boolean and object cases
     */
    static Stream<Arguments> nonStringIdentifierValues() {
        return Stream.of(
                Arguments.of("array", "[\"" + EMBEDDED_CLIENT_ID + "\"]"),
                Arguments.of("number", String.valueOf(Generators.integers(1, 1_000_000).next())),
                Arguments.of("boolean", String.valueOf(Generators.booleans().next())),
                Arguments.of("object", "{\"client_id\":\"" + EMBEDDED_CLIENT_ID + "\"}"));
    }

    /**
     * Parses a token body carrying the given azp JSON literal and no audience claim.
     *
     * @param azpJson the JSON literal of the azp value
     * @return the parsed body
     */
    static MapRepresentation bodyWithAzp(String azpJson) {
        return parse(BODY_PREFIX + ",\"azp\":" + azpJson + "}");
    }

    /**
     * Parses a token body carrying the given client_id JSON literal, no azp and no audience claim.
     *
     * @param clientIdJson the JSON literal of the client_id value
     * @return the parsed body
     */
    static MapRepresentation bodyWithClientId(String clientIdJson) {
        return parse(BODY_PREFIX + ",\"client_id\":" + clientIdJson + "}");
    }

    /**
     * Parses a token body carrying a JSON-string azp together with the given client_id JSON literal,
     * and no audience claim.
     *
     * @param azp          the azp value, written as a JSON string
     * @param clientIdJson the JSON literal of the client_id value
     * @return the parsed body
     */
    static MapRepresentation bodyWithStringAzpAndClientId(String azp, String clientIdJson) {
        return parse(BODY_PREFIX + ",\"azp\":\"" + azp + "\",\"client_id\":" + clientIdJson + "}");
    }

    private static MapRepresentation parse(String json) {
        try {
            return MapRepresentation.fromJson(ParserConfig.builder().build().getDslJson(), json);
        } catch (IOException e) {
            throw new AssertionError("Failed to parse test token body: " + json, e);
        }
    }

    /**
     * The string an identity mapping would derive from the value of the given claim, i.e. the value
     * a token would have carried before the claim became strictly string-typed.
     *
     * @param body  the parsed body
     * @param claim the claim whose value is stringified
     * @return the stringified claim value
     */
    static String identityMappedValue(MapRepresentation body, ClaimName claim) {
        ClaimValue mapped = Objects.requireNonNull(
                new IdentityMapper().map(body, claim.getName()),
                "body must carry a " + claim.getName() + " claim");
        return mapped.getOriginalString();
    }

    /**
     * The configured ids for a case: the embedded client id plus the stringified value of the given
     * claim, so a token is only rejected because the claim is not a JSON string, never because
     * nothing matches.
     *
     * @param body  the parsed body
     * @param claim the claim whose stringified value is configured
     * @return the configured ids
     */
    static Set<String> idsMatchingStringifiedValue(MapRepresentation body, ClaimName claim) {
        return Set.of(EMBEDDED_CLIENT_ID, identityMappedValue(body, claim));
    }

    /**
     * Builds a token of the given type from the parsed body through {@link TokenBuilder}.
     *
     * @param tokenType            ACCESS_TOKEN or ID_TOKEN
     * @param body                 the parsed body
     * @param securityEventCounter the counter the builder records a mapper rejection on
     * @return the built token
     */
    static TokenContent buildToken(TokenType tokenType, MapRepresentation body,
            SecurityEventCounter securityEventCounter) {
        TokenBuilder tokenBuilder = new TokenBuilder(IssuerConfig.builder()
                .issuerIdentifier("test-issuer")
                .jwksContent("{\"keys\":[]}")
                .audienceValidationDisabled(true)
                .build(), securityEventCounter);
        var header = new JwtHeader(null, null, null, null, null, null, null, null, null, null);
        var decodedJwt = new DecodedJwt(header, body, null, new String[]{"", "", ""}, RAW_TOKEN);
        if (TokenType.ID_TOKEN.equals(tokenType)) {
            return tokenBuilder.createIdToken(decodedJwt).orElseThrow();
        }
        return tokenBuilder.createAccessToken(decodedJwt).orElseThrow();
    }
}
