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
package de.cuioss.sheriff.token.client.auth;

import de.cuioss.sheriff.token.client.config.ClientAuthMethod;

import java.util.Map;

/**
 * Mutual-TLS certificate-bound client authentication (RFC 8705, {@code tls_client_auth}).
 * <p>
 * <strong>Alpha capability.</strong> The strategy is declared, never selected, and leaving it
 * unexercised is not a coverage obligation; {@code private_key_jwt} is the supported
 * client-authentication route. DPoP (RFC 9449) is the sender-constraining direction and is
 * explicitly not a client-authentication method — it constrains how an issued token may be
 * presented, leaving the choice of client-authentication method untouched. The fail-fast
 * constructor is the alpha contract — it refuses construction outright, so no configuration can
 * route a request through this strategy.
 * <p>
 * Mutual-TLS authenticates the client by the TLS client certificate presented during the
 * handshake — a transport-layer binding that must be configured as an {@code SSLContext} carrying
 * the client key material on the {@code HttpHandler}. No code path plumbs such an
 * {@code SSLContext} into the transport, so a {@code tls_client_auth} request would leave the
 * client without a bound certificate — an <em>unauthenticated</em> request. Rather than silently
 * producing that request (or leaning on a process-global default {@code SSLContext}), this strategy
 * <strong>fails fast at construction</strong> (H4). No {@code SSLContext} plumbing is added
 * speculatively.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8705">RFC 8705 - OAuth 2.0 Mutual-TLS</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9449">RFC 9449 - OAuth 2.0 Demonstrating
 *      Proof of Possession (DPoP)</a>
 */
public class MtlsClientAuth implements ClientAuthentication {

    /**
     * Always throws — an alpha strategy is not constructible, so the refusal is unconditional
     * rather than a state the caller can configure its way out of.
     *
     * @param clientId the OAuth 2.0 client id (unused; an alpha strategy is not constructible)
     * @throws UnsupportedOperationException always, because no {@code SSLContext} client key
     *         material is plumbed into the transport, so {@code tls_client_auth} cannot be honored
     */
    public MtlsClientAuth(String clientId) {
        throw new UnsupportedOperationException(
                "tls_client_auth is not supported: no SSLContext client key material is plumbed into "
                        + "the transport, so mutual-TLS cannot be honored — refusing to construct a "
                        + "strategy that would produce an unauthenticated request");
    }

    @Override
    public ClientAuthMethod method() {
        return ClientAuthMethod.TLS_CLIENT_AUTH;
    }

    @Override
    public void decorate(Map<String, String> formParameters, Map<String, String> requestHeaders) {
        throw new UnsupportedOperationException("tls_client_auth is not supported");
    }
}
