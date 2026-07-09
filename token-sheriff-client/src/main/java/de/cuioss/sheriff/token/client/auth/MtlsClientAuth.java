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
package de.cuioss.sheriff.token.client.auth;

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;

import java.util.Map;
import java.util.Objects;

/**
 * Mutual-TLS certificate-bound client authentication (RFC 8705, {@code tls_client_auth}).
 * <p>
 * The client is authenticated by the TLS client certificate presented during the handshake — a
 * transport-layer binding configured on the {@code HttpHandler} SSL context, not a request
 * credential. This strategy therefore only identifies the client in the request body ({@code
 * client_id}); it carries no secret.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8705">RFC 8705 - OAuth 2.0 Mutual-TLS</a>
 */
public class MtlsClientAuth implements ClientAuthentication {

    private static final String PARAM_CLIENT_ID = "client_id";

    private final String clientId;

    /**
     * @param clientId the OAuth 2.0 client id; must not be {@code null}
     */
    public MtlsClientAuth(String clientId) {
        this.clientId = Objects.requireNonNull(clientId, "clientId must not be null");
    }

    @Override
    public ClientAuthMethod method() {
        return ClientAuthMethod.TLS_CLIENT_AUTH;
    }

    @Override
    public void decorate(Map<String, String> formParameters, Map<String, String> requestHeaders) {
        formParameters.put(PARAM_CLIENT_ID, clientId);
    }
}
