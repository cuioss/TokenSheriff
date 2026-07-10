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
package de.cuioss.sheriff.token.client.discovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ProviderMetadata capability flags")
class ProviderMetadataTest {

    @Test
    @DisplayName("Should report S256 support only when advertised")
    void shouldReportS256Support() {
        var withS256 = new ProviderMetadata();
        withS256.codeChallengeMethodsSupported = List.of("plain", "S256");

        var withoutS256 = new ProviderMetadata();
        withoutS256.codeChallengeMethodsSupported = List.of("plain");

        var absent = new ProviderMetadata();

        assertAll(
                () -> assertTrue(withS256.supportsS256()),
                () -> assertFalse(withoutS256.supportsS256()),
                () -> assertFalse(absent.supportsS256()));
    }

    @Test
    @DisplayName("Should report PAR support based on the endpoint presence")
    void shouldReportParSupport() {
        var withPar = new ProviderMetadata();
        withPar.pushedAuthorizationRequestEndpoint = "https://issuer.example.com/par";

        assertAll(
                () -> assertTrue(withPar.supportsPushedAuthorizationRequests()),
                () -> assertTrue(withPar.getPushedAuthorizationRequestEndpoint().isPresent()),
                () -> assertFalse(new ProviderMetadata().supportsPushedAuthorizationRequests()));
    }

    @Test
    @DisplayName("Should report DPoP support when a signing algorithm is advertised")
    void shouldReportDpopSupport() {
        var withDpop = new ProviderMetadata();
        withDpop.dpopSigningAlgValuesSupported = List.of("ES256");

        assertAll(
                () -> assertTrue(withDpop.supportsDpop()),
                () -> assertFalse(new ProviderMetadata().supportsDpop()));
    }

    @Test
    @DisplayName("Should report end-session support based on the endpoint presence")
    void shouldReportEndSessionSupport() {
        var withEndSession = new ProviderMetadata();
        withEndSession.endSessionEndpoint = "https://issuer.example.com/logout";

        assertAll(
                () -> assertTrue(withEndSession.supportsEndSession()),
                () -> assertTrue(withEndSession.getEndSessionEndpoint().isPresent()),
                () -> assertFalse(new ProviderMetadata().supportsEndSession()));
    }

    @Test
    @DisplayName("Should expose absent endpoints as empty Optionals")
    void shouldExposeAbsentEndpointsAsEmpty() {
        var empty = new ProviderMetadata();

        assertAll(
                () -> assertFalse(empty.getIssuer().isPresent()),
                () -> assertFalse(empty.getTokenEndpoint().isPresent()),
                () -> assertFalse(empty.getAuthorizationEndpoint().isPresent()),
                () -> assertFalse(empty.getUserinfoEndpoint().isPresent()),
                () -> assertFalse(empty.getJwksUri().isPresent()),
                () -> assertFalse(empty.getRevocationEndpoint().isPresent()),
                () -> assertFalse(empty.getIntrospectionEndpoint().isPresent()));
    }
}
