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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
