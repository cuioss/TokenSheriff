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
import de.cuioss.sheriff.token.client.flow.StepUpChallengeParser.StepUpChallenge;

import java.util.Objects;

/**
 * Drives an RFC 9470 step-up re-authentication in response to a resource server's step-up challenge.
 * <p>
 * Given a {@link StepUpChallenge} parsed from a {@code WWW-Authenticate} header, the handler starts a
 * fresh {@code authorization_code} request that carries the challenge's requested {@code acr_values},
 * yielding a new {@link FlowContext} (bound to a new {@code state}, {@code nonce}, and PKCE
 * verifier) and the authorization URL to redirect the user to. Because a step-up always mints a new
 * PKCE-protected context, the elevated re-authentication cannot be replayed onto the original flow.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9470">RFC 9470 - OAuth 2.0 Step Up Authentication Challenge</a>
 */
public class StepUpHandler {

    private final AuthorizationRequestBuilder authorizationRequestBuilder;

    /**
     * Creates a handler with a default {@link AuthorizationRequestBuilder}.
     */
    public StepUpHandler() {
        this(new AuthorizationRequestBuilder());
    }

    /**
     * @param authorizationRequestBuilder the builder used to compose the elevated authorization
     *                                    request; must not be {@code null}
     */
    public StepUpHandler(AuthorizationRequestBuilder authorizationRequestBuilder) {
        this.authorizationRequestBuilder = Objects.requireNonNull(authorizationRequestBuilder,
                "authorizationRequestBuilder must not be null");
    }

    /**
     * Starts a step-up re-authorization for the given challenge.
     *
     * @param configuration the client configuration; must not be {@code null}, and must declare a
     *                      redirect URI for the interactive flow
     * @param metadata      the resolved provider metadata; must not be {@code null}
     * @param challenge     the parsed step-up challenge; must not be {@code null}
     * @return the elevated authorization request (URL + fresh flow context)
     * @throws IllegalArgumentException if the client declares no redirect URI
     * @throws IllegalStateException    if the AS advertises no authorization endpoint or no PKCE
     *                                  {@code S256}
     */
    public StepUpRequest initiate(ClientConfiguration configuration, ProviderMetadata metadata,
            StepUpChallenge challenge) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(challenge, "challenge must not be null");

        FlowContext context = FlowContext.create(configuration.getRedirectUri(), challenge.getAcrValues().orElse(null));
        String authorizationUrl = authorizationRequestBuilder.build(configuration, metadata, context);
        return new StepUpRequest(authorizationUrl, context);
    }

    /**
     * The elevated authorization request produced for a step-up: the URL to redirect to and the
     * fresh flow context to persist until the callback returns.
     *
     * @param authorizationUrl the authorization request URL carrying the requested {@code acr_values}
     * @param context          the fresh flow context bound to this step-up
     */
    public record StepUpRequest(String authorizationUrl, FlowContext context) {

        /**
         * @param authorizationUrl the authorization request URL; must not be {@code null}
         * @param context          the fresh flow context; must not be {@code null}
         */
        public StepUpRequest {
            Objects.requireNonNull(authorizationUrl, "authorizationUrl must not be null");
            Objects.requireNonNull(context, "context must not be null");
        }
    }
}
