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

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.ToString;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Immutable configuration describing how this confidential client talks to a single
 * OpenID Connect / OAuth 2.0 authorization server (AS).
 * <p>
 * A {@code ClientConfiguration} is a value object: it is created once (typically at
 * application start) and then shared, read-only, across all concurrent flows for that
 * issuer. Immutability makes it inherently thread-safe (satisfying {@code CLIENT-22}) — no
 * flow can mutate shared client state, so there is no cross-flow interference over the
 * configuration.
 * <p>
 * Only the fields needed to bootstrap discovery and client authentication are modelled here;
 * flow-specific parameters (PKCE, {@code state}/{@code nonce}, DPoP keys) are created per flow
 * and are not part of the shared configuration.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@Value
@Builder
public class ClientConfiguration {

    /**
     * The authorization server's issuer identifier URL (for example
     * {@code https://issuer.example.com/realms/demo}). Discovery is performed against
     * {@code {issuer}/.well-known/openid-configuration}. Must not be {@code null}.
     */
    @NonNull
    String issuer;

    /**
     * The OAuth 2.0 {@code client_id} registered for this client at the AS. Must not be {@code null}.
     */
    @NonNull
    String clientId;

    /**
     * The client secret, used by the shared-secret authentication methods
     * ({@link ClientAuthMethod#CLIENT_SECRET_BASIC} / {@link ClientAuthMethod#CLIENT_SECRET_POST}).
     * {@code null} for the key-based methods ({@link ClientAuthMethod#PRIVATE_KEY_JWT} /
     * {@link ClientAuthMethod#TLS_CLIENT_AUTH}), where no shared secret exists. The secret is
     * never placed in a URL and never logged — excluded from the Lombok-generated
     * {@code toString()} so an incidental {@code toString()} of the whole configuration (a debug
     * log, an assertion failure message) can never leak it.
     */
    @Nullable
    @ToString.Exclude
    String clientSecret;

    /**
     * The client authentication method this client presents to the AS token endpoint. Must not
     * be {@code null}.
     */
    @NonNull
    ClientAuthMethod authMethod;

    /**
     * The OAuth 2.0 scopes this client requests, in request order. May be empty. The stored list
     * is immutable.
     */
    @Singular
    List<String> scopes;

    /**
     * The single, exact {@code redirect_uri} registered for the interactive
     * {@code authorization_code} flow, or {@code null} for non-interactive clients (for example a
     * pure {@code client_credentials} client). When present it is matched exactly — never by
     * prefix or pattern.
     */
    @Nullable
    String redirectUri;

    /**
     * Whether plaintext {@code http://} endpoints are permitted for discovery and back-channel
     * calls. Defaults to {@code false}, so a non-TLS issuer is rejected. Set to {@code true} only
     * for local test setups against a cleartext authorization server.
     */
    boolean allowInsecureHttp;
}
