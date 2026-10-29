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
package de.cuioss.sheriff.token.client.internal;

import de.cuioss.sheriff.token.commons.error.ClientProtocolException;

import java.net.URI;

/**
 * The shared TLS policy for front-channel endpoints the client sends the user agent to, such as the
 * authorization endpoint and the {@code end_session_endpoint} (L5, C-4).
 * <p>
 * A front-channel URL carries security-relevant parameters — {@code state}, {@code nonce}, PKCE, an
 * {@code id_token_hint} — so a cleartext endpoint would expose them to a network observer. The policy
 * accepts {@code https} (case-insensitive). Literal {@code http} is accepted only when the caller opts
 * in through {@link de.cuioss.sheriff.token.client.config.ClientConfiguration#allowInsecureHttp} for a
 * local test setup. Every other scheme ({@code ftp}, {@code file}, {@code javascript}, …) and a missing
 * scheme are refused in every case. An accepted endpoint must also be an absolute URL with a host
 * (an authority component): a hostless value such as {@code https:/logout}, {@code https:///logout} or
 * the opaque {@code https:foo} is refused, since the user agent would be sent to no host at all.
 * <p>
 * The refusal message names the endpoint by its label only. The raw endpoint may come from a discovery
 * document and is not trusted, so it is never echoed into the message.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public final class EndpointSchemePolicy {

    private static final String SCHEME_HTTPS = "https";
    private static final String SCHEME_HTTP = "http";

    private EndpointSchemePolicy() {
        // utility class — never instantiated
    }

    /**
     * Refuses an endpoint that is not an absolute TLS ({@code https}) URL with a host.
     * <p>
     * The scheme is checked first; only an endpoint whose scheme is accepted is then required to carry
     * an authority component. The authority is read raw, so a registry-based authority that is not a
     * valid server host name (for example {@code https://my_host/logout}) is still accepted.
     *
     * @param endpoint          the endpoint URL to check; must not be {@code null}
     * @param endpointLabel     the human-readable name of the endpoint used in the refusal message, for
     *                          example {@code "authorization endpoint"}
     * @param allowInsecureHttp whether a literal {@code http} endpoint is tolerated for a local test
     *                          setup
     * @throws ClientProtocolException if the endpoint is not a valid URL, if its scheme is neither
     *                                 {@code https} nor an opted-in {@code http}, or if it has no host
     *                                 (authority)
     */
    public static void requireTls(String endpoint, String endpointLabel, boolean allowInsecureHttp) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new ClientProtocolException(endpointLabel + " is not a valid URL", e);
        }
        String scheme = uri.getScheme();
        boolean schemeAccepted = SCHEME_HTTPS.equalsIgnoreCase(scheme)
                || (allowInsecureHttp && SCHEME_HTTP.equalsIgnoreCase(scheme));
        if (!schemeAccepted) {
            throw new ClientProtocolException(
                    endpointLabel + " is not a TLS (https) URL; refusing to use it over an insecure channel");
        }
        if (uri.getRawAuthority() == null) {
            throw new ClientProtocolException(
                    endpointLabel + " is not an absolute URL with a host; refusing to use it");
        }
    }
}
