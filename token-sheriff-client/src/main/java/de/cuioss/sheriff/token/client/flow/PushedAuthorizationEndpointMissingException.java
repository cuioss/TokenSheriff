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
package de.cuioss.sheriff.token.client.flow;

import de.cuioss.sheriff.token.commons.error.ClientProtocolException;

import java.io.Serial;

/**
 * A pushed authorization request (RFC 9126) was asked for, but the provider metadata advertises no
 * {@code pushed_authorization_request_endpoint}. It is raised by
 * {@link AuthorizationCodeFlow#authorizePushed(de.cuioss.sheriff.token.client.discovery.ProviderMetadata,
 * de.cuioss.sheriff.token.client.auth.ClientAuthentication, AuthorizationRequestOptions)} before any HTTP
 * request; there is no fallback to a front-channel request.
 * <p>
 * It extends {@link ClientProtocolException}, so every existing {@code catch (ClientProtocolException)}
 * and every documented {@code @throws ClientProtocolException} contract keeps holding unchanged — the
 * refusal still maps to the same RFC 9457 status at an HTTP edge. Only a caller that needs to tell the
 * missing PAR endpoint apart from the other protocol refusals catches this type.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
// java:S110 — the inheritance depth is inherited wholesale from the library's existing exception
// taxonomy (ClientProtocolException and its ancestors); this type adds exactly one level to it.
// The subtype relation is load-bearing for source compatibility: it is what lets the dedicated reason
// be added without touching a single existing catch block, assertThrows or documented @throws.
@SuppressWarnings("java:S110")
public class PushedAuthorizationEndpointMissingException extends ClientProtocolException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param message the caller-safe detail message; must not be {@code null}
     */
    public PushedAuthorizationEndpointMissingException(String message) {
        super(message);
    }
}
