/*
 * Copyright © 2022 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.token.client.token;

import de.cuioss.sheriff.token.client.lifecycle.StoredToken;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableGeneratorController
@DisplayName("TokenResponse value object")
class TokenResponseTest {

    @Test
    @DisplayName("Should expose all populated fields through its accessors")
    void shouldExposePopulatedFields() {
        var response = new TokenResponse();
        response.accessToken = "access-token";
        response.tokenType = "Bearer";
        response.expiresIn = 300L;
        response.refreshToken = "refresh-token";
        response.idToken = "id-token";
        response.scope = "openid profile";

        assertAll("token response fields",
                () -> assertEquals("access-token", response.getAccessToken().orElseThrow()),
                () -> assertEquals("Bearer", response.getTokenType().orElseThrow()),
                () -> assertEquals(300L, response.getExpiresIn()),
                () -> assertEquals("refresh-token", response.getRefreshToken().orElseThrow()),
                () -> assertEquals("id-token", response.getIdToken().orElseThrow()),
                () -> assertEquals("openid profile", response.getScope().orElseThrow()));
    }

    @Test
    @DisplayName("Should expose grant-dependent optional fields as empty when absent")
    void shouldExposeAbsentOptionalsAsEmpty() {
        var response = new TokenResponse();
        response.accessToken = "access-token";
        response.tokenType = "Bearer";

        assertAll(
                () -> assertTrue(response.getAccessToken().isPresent()),
                () -> assertEquals(0L, response.getExpiresIn()),
                () -> assertFalse(response.getRefreshToken().isPresent()),
                () -> assertFalse(response.getIdToken().isPresent()),
                () -> assertFalse(response.getScope().isPresent()));
    }

    /**
     * H8: {@code toString()} on the token-carrying value objects must never leak live credential
     * material. A stray render — a log statement, an exception message, or a debugger dump — is the
     * canonical accidental-leak surface, so the secret-bearing fields are asserted absent and the
     * {@code <redacted>} marker asserted present.
     */
    @Nested
    @DisplayName("Secret redaction in toString() (H8)")
    class SecretRedaction {

        @Test
        @DisplayName("Should redact the access, refresh, and ID tokens in StoredToken.toString()")
        void shouldRedactStoredTokenSecrets() {
            String access = Generators.letterStrings(20, 40).next();
            String refresh = Generators.letterStrings(20, 40).next();
            String idToken = Generators.letterStrings(20, 40).next();
            var stored = new StoredToken(access, refresh, idToken, null, null);

            String rendered = stored.toString();

            assertAll("redacted StoredToken rendering",
                    () -> assertFalse(rendered.contains(access), "the access token must not appear in toString()"),
                    () -> assertFalse(rendered.contains(refresh), "the refresh token must not appear in toString()"),
                    () -> assertFalse(rendered.contains(idToken), "the ID token must not appear in toString()"),
                    () -> assertTrue(rendered.contains("<redacted>"), "live token material is redacted"));
        }

        @Test
        @DisplayName("Should render absent StoredToken secrets as null rather than a redacted marker")
        void shouldRenderAbsentStoredTokenSecretsAsNull() {
            var stored = new StoredToken(Generators.letterStrings(20, 40).next(), null, null, null, null);

            String rendered = stored.toString();

            assertAll("absent optional secrets",
                    () -> assertTrue(rendered.contains("refreshToken=null"), "an absent refresh token reads as null"),
                    () -> assertTrue(rendered.contains("idToken=null"), "an absent ID token reads as null"),
                    () -> assertTrue(rendered.contains("accessToken=<redacted>"),
                            "the mandatory access token is always redacted"));
        }

        @Test
        @DisplayName("Should redact the access, refresh, and ID tokens in RotationResult.toString()")
        void shouldRedactRotationResultSecrets() {
            TestTokenHolder holder = TestTokenGenerators.accessTokens().next();
            AccessTokenContent access = holder.asAccessTokenContent();
            String rawAccess = holder.getRawToken();
            String refresh = Generators.letterStrings(20, 40).next();
            String idToken = Generators.letterStrings(20, 40).next();
            var result = new RotationResult(access, refresh, idToken, 300L, true);

            String rendered = result.toString();

            assertAll("redacted RotationResult rendering",
                    () -> assertFalse(rendered.contains(rawAccess),
                            "the raw access token must not appear in toString()"),
                    () -> assertFalse(rendered.contains(refresh), "the refresh token must not appear in toString()"),
                    () -> assertFalse(rendered.contains(idToken), "the ID token must not appear in toString()"),
                    () -> assertTrue(rendered.contains("<redacted>"), "live token material is redacted"),
                    () -> assertTrue(rendered.contains("accessTokenExpiresInSeconds=300"),
                            "the non-secret lifetime is shown for diagnostics"),
                    () -> assertTrue(rendered.contains("rotated=true"),
                            "the non-secret rotation flag is shown for diagnostics"));
        }

        @Test
        @DisplayName("Should render an absent RotationResult ID token as null rather than a redacted marker")
        void shouldRenderAbsentRotationIdTokenAsNull() {
            TestTokenHolder holder = TestTokenGenerators.accessTokens().next();
            var result = new RotationResult(holder.asAccessTokenContent(),
                    Generators.letterStrings(20, 40).next(), null, 0L, false);

            String rendered = result.toString();

            assertAll("absent optional ID token",
                    () -> assertTrue(rendered.contains("idToken=null"), "an absent ID token reads as null"),
                    () -> assertTrue(rendered.contains("refreshToken=<redacted>"),
                            "the mandatory refresh token is always redacted"));
        }
    }
}
