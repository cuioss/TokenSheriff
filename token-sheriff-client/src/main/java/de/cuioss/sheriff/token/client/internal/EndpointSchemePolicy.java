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
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * The shared TLS policy for front-channel endpoints the client sends the user agent to, such as the
 * authorization endpoint and the {@code end_session_endpoint} (L5, C-4).
 * <p>
 * A front-channel URL carries security-relevant parameters — {@code state}, {@code nonce}, PKCE, an
 * {@code id_token_hint} — so a cleartext endpoint would expose them to a network observer. The policy
 * accepts {@code https} (case-insensitive). Literal {@code http} is accepted only when the caller opts
 * in through {@link de.cuioss.sheriff.token.client.config.ClientConfiguration#allowInsecureHttp} for a
 * local test setup. Every other scheme ({@code ftp}, {@code file}, {@code javascript}, …) and a missing
 * scheme are refused in every case. An accepted endpoint must also be an absolute URL with a host:
 * a value without an authority such as {@code https:/logout}, {@code https:///logout} or the opaque
 * {@code https:foo} is refused, and so is an authority that carries no host once its user-info and port
 * are removed, such as {@code https://:443/logout}, {@code https://user@/x} or {@code https://;/x}, and an
 * authority whose remainder starts with {@code :}, such as {@code https://:abc/logout}, since the user
 * agent would be sent to no host at all.
 * <p>
 * The refusal message names the endpoint by its label only. The raw endpoint may come from a discovery
 * document and is not trusted, so it is never echoed into the message, and a refusal carries no cause
 * whose message could echo it either.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public final class EndpointSchemePolicy {

    private static final String SCHEME_HTTPS = "https";
    private static final String SCHEME_HTTP = "http";

    /** A trailing port: a {@code :} followed by digits, or a bare {@code :}. */
    private static final Pattern PORT_SUFFIX = Pattern.compile(":\\d*$");

    private EndpointSchemePolicy() {
        // utility class — never instantiated
    }

    /**
     * Refuses an endpoint that is not an absolute TLS ({@code https}) URL with a host.
     * <p>
     * The scheme is checked first; only an endpoint whose scheme is accepted is then required to carry
     * a host. The host is read from the raw authority with any user-info (up to and including the last
     * {@code @}) and any port (a trailing {@code :} followed by digits, or a bare {@code :}) removed. It
     * must not start with {@code :} — a host never does, an IPv6 host is bracketed — and must contain at
     * least one letter or digit, so a non-numeric port with no host in front of it, such as
     * {@code https://:abc/logout}, is refused. Reading the raw authority keeps a registry-based
     * authority that is not a valid server host name (for example {@code https://my_host/logout})
     * accepted.
     * <p>
     * An unparsable endpoint is refused without a cause: the parser's own exception message embeds the
     * raw endpoint, so attaching it would echo the untrusted value through a logged stack trace.
     *
     * @param endpoint          the endpoint URL to check; must not be {@code null}
     * @param endpointLabel     the human-readable name of the endpoint used in the refusal message, for
     *                          example {@code "authorization endpoint"}
     * @param allowInsecureHttp whether a literal {@code http} endpoint is tolerated for a local test
     *                          setup
     * @throws ClientProtocolException if the endpoint is not a valid URL, if its scheme is neither
     *                                 {@code https} nor an opted-in {@code http}, or if it has no host
     */
    public static void requireTls(String endpoint, String endpointLabel, boolean allowInsecureHttp) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            // The cause is deliberately dropped: its message embeds the raw, untrusted endpoint.
            throw new ClientProtocolException(endpointLabel + " is not a valid URL");
        }
        String scheme = uri.getScheme();
        boolean schemeAccepted = SCHEME_HTTPS.equalsIgnoreCase(scheme)
                || (allowInsecureHttp && SCHEME_HTTP.equalsIgnoreCase(scheme));
        if (!schemeAccepted) {
            throw new ClientProtocolException(
                    endpointLabel + " is not a TLS (https) URL; refusing to use it over an insecure channel");
        }
        if (!hasHost(uri.getRawAuthority())) {
            throw new ClientProtocolException(
                    endpointLabel + " is not an absolute URL with a host; refusing to use it");
        }
    }

    /**
     * Tells whether a raw authority carries a host once its user-info and port are removed.
     *
     * @param rawAuthority the raw authority component, {@code null} when the URL has none
     * @return {@code true} if the remaining host part does not start with {@code :} and contains at
     *         least one letter or digit
     */
    private static boolean hasHost(@Nullable String rawAuthority) {
        if (rawAuthority == null) {
            return false;
        }
        String host = rawAuthority.substring(rawAuthority.lastIndexOf('@') + 1);
        host = PORT_SUFFIX.matcher(host).replaceFirst("");
        return !host.startsWith(":") && host.chars().anyMatch(Character::isLetterOrDigit);
    }
}
