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
package de.cuioss.sheriff.token.client.config;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableGeneratorController
@DisplayName("ClientConfiguration value object")
class ClientConfigurationTest {

    private static String issuer() {
        return "https://" + Generators.letterStrings(3, 12).next() + ".example.com";
    }

    @Test
    @DisplayName("Should expose all configured fields through its builder")
    void shouldExposeConfiguredFields() {
        var issuer = issuer();
        var clientId = Generators.nonBlankStrings().next();
        var secret = Generators.nonBlankStrings().next();
        var redirect = issuer() + "/callback";

        var config = ClientConfiguration.builder()
                .issuer(issuer)
                .clientId(clientId)
                .clientSecret(secret)
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .scope("profile")
                .redirectUri(redirect)
                .allowInsecureHttp(false)
                .build();

        assertAll("configuration fields",
                () -> assertEquals(issuer, config.getIssuer()),
                () -> assertEquals(clientId, config.getClientId()),
                () -> assertEquals(secret, config.getClientSecret()),
                () -> assertEquals(ClientAuthMethod.CLIENT_SECRET_BASIC, config.getAuthMethod()),
                () -> assertEquals(2, config.getScopes().size()),
                () -> assertEquals(redirect, config.getRedirectUri()),
                () -> assertFalse(config.isAllowInsecureHttp()));
    }

    @Test
    @DisplayName("Should permit a null secret and redirect for non-interactive clients")
    void shouldPermitNullSecretAndRedirect() {
        var config = ClientConfiguration.builder()
                .issuer(issuer())
                .clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.PRIVATE_KEY_JWT)
                .build();

        assertAll(
                () -> assertTrue(config.getScopes().isEmpty()),
                () -> assertNull(config.getClientSecret()),
                () -> assertNull(config.getRedirectUri()));
    }

    @Test
    @DisplayName("Should reject a null issuer, client id or auth method")
    void shouldRejectNullRequiredFields() {
        var clientId = Generators.nonBlankStrings().next();
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> ClientConfiguration.builder()
                        .clientId(clientId).authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC).build()),
                () -> assertThrows(NullPointerException.class, () -> ClientConfiguration.builder()
                        .issuer(issuer()).authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC).build()),
                () -> assertThrows(NullPointerException.class, () -> ClientConfiguration.builder()
                        .issuer(issuer()).clientId(clientId).build()));
    }

    @Test
    @DisplayName("Should keep the scopes list immutable")
    void shouldKeepScopesImmutable() {
        var config = ClientConfiguration.builder()
                .issuer(issuer())
                .clientId(Generators.nonBlankStrings().next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .scope("openid")
                .build();

        assertThrows(UnsupportedOperationException.class, () -> config.getScopes().add("extra"));
    }

    @Test
    @DisplayName("Should honour value-based equals and hashCode")
    void shouldHonourValueEquality() {
        var issuer = issuer();
        var clientId = Generators.nonBlankStrings().next();

        var first = ClientConfiguration.builder()
                .issuer(issuer).clientId(clientId).authMethod(ClientAuthMethod.CLIENT_SECRET_POST).build();
        var second = ClientConfiguration.builder()
                .issuer(issuer).clientId(clientId).authMethod(ClientAuthMethod.CLIENT_SECRET_POST).build();
        var different = ClientConfiguration.builder()
                .issuer(issuer).clientId(clientId).authMethod(ClientAuthMethod.PRIVATE_KEY_JWT).build();

        assertAll(
                () -> assertEquals(first, second),
                () -> assertEquals(first.hashCode(), second.hashCode()),
                () -> assertNotEquals(first, different));
    }

    @Nested
    @DisplayName("ClientAuthMethod metadata")
    class ClientAuthMethodTest {

        @Test
        @DisplayName("Should rank key-based methods above shared-secret methods")
        void shouldRankKeyBasedMethodsStronger() {
            assertAll(
                    () -> assertTrue(ClientAuthMethod.PRIVATE_KEY_JWT.getStrength()
                            > ClientAuthMethod.CLIENT_SECRET_BASIC.getStrength()),
                    () -> assertTrue(ClientAuthMethod.TLS_CLIENT_AUTH.getStrength()
                            > ClientAuthMethod.CLIENT_SECRET_POST.getStrength()));
        }

        @Test
        @DisplayName("Should resolve a method from its advertised metadata value")
        void shouldResolveFromMetadataValue() {
            assertAll(
                    () -> assertEquals(ClientAuthMethod.PRIVATE_KEY_JWT,
                            ClientAuthMethod.fromMetadataValue("private_key_jwt").orElseThrow()),
                    () -> assertEquals(ClientAuthMethod.TLS_CLIENT_AUTH,
                            ClientAuthMethod.fromMetadataValue("tls_client_auth").orElseThrow()),
                    () -> assertTrue(ClientAuthMethod.fromMetadataValue("none").isEmpty()),
                    () -> assertTrue(ClientAuthMethod.fromMetadataValue(null).isEmpty()));
        }
    }
}
