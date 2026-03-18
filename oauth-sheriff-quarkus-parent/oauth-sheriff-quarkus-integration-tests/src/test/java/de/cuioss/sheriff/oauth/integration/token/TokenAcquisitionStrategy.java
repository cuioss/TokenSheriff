/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.oauth.integration.token;

import de.cuioss.sheriff.oauth.integration.TestRealm.TokenResponse;

/**
 * Strategy for acquiring tokens from an OIDC provider. Different providers
 * support different grant types — ROPC (Keycloak, Dex), client_credentials
 * (Zitadel), etc.
 * <p>
 * Implementations capture provider-specific credentials in their constructors.
 * The {@link #acquireToken} method receives the common parameters shared
 * across all strategies.
 */
@FunctionalInterface
public interface TokenAcquisitionStrategy {

    /**
     * Acquires tokens from the provider's token endpoint.
     *
     * @param baseUrl       provider base URL (e.g. {@code https://localhost:1443})
     * @param tokenEndpoint token endpoint path (e.g. {@code /realms/integration/protocol/openid-connect/token})
     * @param clientId      OIDC application client ID
     * @param clientSecret  OIDC application client secret
     * @param scopes        space-separated scope string
     * @return token response containing access token, ID token, and optional refresh token
     */
    TokenResponse acquireToken(String baseUrl, String tokenEndpoint,
                               String clientId, String clientSecret, String scopes);
}
