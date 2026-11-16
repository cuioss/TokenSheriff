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
package de.cuioss.sheriff.token.validation.logout;

import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import io.jsonwebtoken.Jwts;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Fixtures shared by {@link LogoutTokenValidatorTest} and {@link LogoutTokenValidatorReplayTest}: the trusted
 * issuers, a logout token that passes every check, the two ways of signing it, and the refusal assertion.
 */
final class LogoutTokenValidatorSupport {

    static final String ISSUER = "https://logout-issuer.example.com";
    static final String OTHER_ISSUER = "https://other-logout-issuer.example.com";
    static final String AUDIENCE = "logout-client";
    static final Duration WINDOW = Duration.ofMinutes(5);
    static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private LogoutTokenValidatorSupport() {
    }

    /**
     * @return a token validator trusting {@link #ISSUER} and {@link #OTHER_ISSUER}; the caller closes it
     */
    static TokenValidator newTokenValidator() {
        return TokenValidator.builder()
                .issuerConfig(issuerConfig(ISSUER))
                .issuerConfig(issuerConfig(OTHER_ISSUER))
                .build();
    }

    /**
     * @return a logout token validator expecting {@link #ISSUER} and {@link #AUDIENCE} with the {@link #WINDOW}
     * freshness window
     */
    static LogoutTokenValidator logoutValidator(TokenValidator tokenValidator, int replayGuardCapacity, Clock clock) {
        return LogoutTokenValidator.builder()
                .tokenValidator(tokenValidator)
                .expectedIssuer(ISSUER)
                .expectedAudience(AUDIENCE)
                .freshnessWindow(WINDOW)
                .replayGuardCapacity(replayGuardCapacity)
                .clock(clock)
                .build();
    }

    /**
     * @return mutable claims of a logout token that passes every check
     */
    static Map<String, Object> validClaims() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", ISSUER);
        claims.put("sub", "subject-1");
        claims.put("sid", "session-1");
        claims.put("aud", AUDIENCE);
        claims.put("iat", NOW.getEpochSecond());
        claims.put("exp", NOW.plusSeconds(120).getEpochSecond());
        claims.put("jti", "jti-1");
        claims.put("events", Map.of(LogoutTokenValidator.BACKCHANNEL_LOGOUT_EVENT, Map.of()));
        return claims;
    }

    static String signed(Map<String, Object> claims, @Nullable String typ) {
        var header = Jwts.builder().header().keyId(InMemoryKeyMaterialHandler.DEFAULT_KEY_ID);
        if (typ != null) {
            header.type(typ);
        }
        return header.and()
                .claims(claims)
                .signWith(InMemoryKeyMaterialHandler.getDefaultPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * @return mutable members of a logout token that passes every check, each value as literal JSON text
     */
    static Map<String, String> validJsonMembers() {
        Map<String, String> members = new LinkedHashMap<>();
        members.put("iss", quoted(ISSUER));
        members.put("sub", quoted("subject-1"));
        members.put("sid", quoted("session-1"));
        members.put("aud", quoted(AUDIENCE));
        members.put("iat", String.valueOf(NOW.getEpochSecond()));
        members.put("exp", String.valueOf(NOW.plusSeconds(120).getEpochSecond()));
        members.put("jti", quoted("jti-1"));
        members.put("events", "{" + quoted(LogoutTokenValidator.BACKCHANNEL_LOGOUT_EVENT) + ":{}}");
        return members;
    }

    /**
     * Signs a payload written as literal JSON with the default RS256 test key and {@code typ: logout+jwt},
     * so a JSON {@code null} or a string-typed date reaches the parser exactly as written.
     *
     * @param members the payload members, each value as literal JSON text
     * @return the compact JWS
     */
    static String signedJson(Map<String, String> members) {
        String header = "{\"alg\":\"RS256\",\"kid\":" + quoted(InMemoryKeyMaterialHandler.DEFAULT_KEY_ID)
                + ",\"typ\":\"logout+jwt\"}";
        String payload = members.entrySet().stream()
                .map(member -> quoted(member.getKey()) + ":" + member.getValue())
                .collect(Collectors.joining(",", "{", "}"));
        String signingInput = base64Url(header) + "." + base64Url(payload);
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(InMemoryKeyMaterialHandler.getDefaultPrivateKey());
            signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign the test token", e);
        }
    }

    static String quoted(String text) {
        return "\"" + text + "\"";
    }

    static void assertRefused(LogoutTokenCheck expected, LogoutTokenValidationResult result) {
        assertFalse(result.isAccepted(), () -> "expected refusal as " + expected + " but was " + result);
        assertEquals(Optional.of(expected), result.getFailedCheck());
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static IssuerConfig issuerConfig(String issuer) {
        return IssuerConfig.builder()
                .issuerIdentifier(issuer)
                .expectedAudience(AUDIENCE)
                .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                .build();
    }
}
