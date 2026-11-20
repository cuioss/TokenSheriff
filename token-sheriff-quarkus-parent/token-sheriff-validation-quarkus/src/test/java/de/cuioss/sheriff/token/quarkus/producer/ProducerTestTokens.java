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

import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Test fixture: the validated access token the {@link BearerTokenProducer} tests let their mocked validator
 * return — issuer, subject, an expiry one hour ahead, issued now, and a fixed raw token text.
 */
final class ProducerTestTokens {

    private ProducerTestTokens() {
    }

    /**
     * @return an {@link AccessTokenContent} carrying the mandatory claims only
     */
    static AccessTokenContent createAccessToken() {
        return createAccessToken(Map.of());
    }

    /**
     * @param additionalClaims claims added to the mandatory ones, replacing a mandatory claim of the same name
     * @return an {@link AccessTokenContent} carrying the mandatory claims and {@code additionalClaims}
     */
    static AccessTokenContent createAccessToken(Map<String, ClaimValue> additionalClaims) {
        Map<String, ClaimValue> claims = new HashMap<>();
        claims.put(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("test-issuer"));
        claims.put(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString("test-subject"));
        var now = OffsetDateTime.now();
        var expires = now.plusHours(1);
        claims.put(ClaimName.EXPIRATION.getName(), ClaimValue.forDateTime(
                String.valueOf(expires.toEpochSecond()), expires));
        claims.put(ClaimName.ISSUED_AT.getName(), ClaimValue.forDateTime(
                String.valueOf(now.toEpochSecond()), now));
        claims.putAll(additionalClaims);
        return new AccessTokenContent(claims, "raw-token");
    }
}
