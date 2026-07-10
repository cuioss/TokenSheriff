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
package de.cuioss.sheriff.token.client.flow;

import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.ProviderMetadata;
import de.cuioss.sheriff.token.client.internal.ClientLogMessages;
import de.cuioss.tools.logging.CuiLogger;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Builds the front-channel {@code authorization_code} request URL (RFC 6749 §4.1.1) with PKCE
 * {@code S256} (RFC 7636) and optional RFC 9470 step-up parameters.
 * <p>
 * The URL always carries {@code response_type=code}, the {@code client_id}, the exact
 * {@code redirect_uri}, the requested {@code scope}, the anti-CSRF {@code state}, the {@code nonce},
 * and the PKCE {@code code_challenge} / {@code code_challenge_method=S256}. When the flow context
 * requests {@code acr_values} (step-up), they are appended. The builder fails closed when the
 * authorization server does not advertise PKCE {@code S256}, so a downgrade to a non-PKCE (or
 * {@code plain}) request can never be issued ({@code CLIENT-2}).
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.1">RFC 6749 §4.1.1 - Authorization Request</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9470">RFC 9470 - OAuth 2.0 Step Up Authentication Challenge</a>
 */
public class AuthorizationRequestBuilder {

    private static final CuiLogger LOGGER = new CuiLogger(AuthorizationRequestBuilder.class);

    private static final String PARAM_RESPONSE_TYPE = "response_type";
    private static final String RESPONSE_TYPE_CODE = "code";
    private static final String PARAM_CLIENT_ID = "client_id";
    private static final String PARAM_REDIRECT_URI = "redirect_uri";
    private static final String PARAM_SCOPE = "scope";
    private static final String PARAM_STATE = "state";
    private static final String PARAM_NONCE = "nonce";
    private static final String PARAM_CODE_CHALLENGE = "code_challenge";
    private static final String PARAM_CODE_CHALLENGE_METHOD = "code_challenge_method";
    private static final String PARAM_ACR_VALUES = "acr_values";

    /**
     * Builds the authorization request URL for the given flow context.
     *
     * @param configuration the client configuration; must not be {@code null}
     * @param metadata      the resolved provider metadata carrying the authorization endpoint; must
     *                      not be {@code null}
     * @param context       the flow context carrying {@code state}, {@code nonce}, PKCE, and the
     *                      redirect URI; must not be {@code null}
     * @return the fully-formed authorization request URL
     * @throws IllegalStateException if the AS advertises no authorization endpoint or does not
     *                               advertise PKCE {@code S256}
     */
    public String build(ClientConfiguration configuration, ProviderMetadata metadata, FlowContext context) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(context, "context must not be null");

        String authorizationEndpoint = metadata.getAuthorizationEndpoint()
                .orElseThrow(() -> new IllegalStateException(
                        "provider metadata is missing the authorization endpoint"));
        if (!metadata.supportsS256()) {
            LOGGER.warn(ClientLogMessages.WARN.S256_NOT_ADVERTISED, metadata.getIssuer().orElse(authorizationEndpoint));
            throw new IllegalStateException(
                    "authorization server does not advertise PKCE 'S256'; refusing to start the authorization_code"
                            + " flow");
        }

        Map<String, String> params = new HashMap<>(Map.of(
                PARAM_RESPONSE_TYPE, RESPONSE_TYPE_CODE,
                PARAM_CLIENT_ID, configuration.getClientId(),
                PARAM_REDIRECT_URI, context.redirectUri()));
        if (!configuration.getScopes().isEmpty()) {
            params.put(PARAM_SCOPE, String.join(" ", configuration.getScopes()));
        }
        params.put(PARAM_STATE, context.state());
        params.put(PARAM_NONCE, context.nonce());
        params.put(PARAM_CODE_CHALLENGE, context.pkceChallenge().codeChallenge());
        params.put(PARAM_CODE_CHALLENGE_METHOD, context.pkceChallenge().method());
        context.acrValues().ifPresent(acr -> params.put(PARAM_ACR_VALUES, acr));

        return authorizationEndpoint + (authorizationEndpoint.indexOf('?') < 0 ? '?' : '&') + encode(params);
    }

    private static String encode(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((key, value) -> joiner.add(URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        return joiner.toString();
    }
}
