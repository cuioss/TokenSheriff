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

import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Immutable per-call options for a single authorization request.
 * <p>
 * Every option is opt-in. With no option set ({@link #DEFAULTS}) a request is exactly the request the
 * client sends without options: the configured scopes, {@code response_mode=form_post} and no
 * {@code prompt}. The options apply to one call only; they never change the shared
 * {@link ClientConfiguration}, so concurrent calls with different options do not interfere.
 * <ul>
 *   <li>{@link #getScopes() scopes} replaces the configured scopes for this call. It is validated by
 *       the same scope-token rule as the configured scopes
 *       ({@link ClientConfiguration#validateScopes(List)}), and an empty list is refused.</li>
 *   <li>{@link #getResponseMode() responseMode} selects {@link ResponseMode#FORM_POST} (the default) or
 *       {@link ResponseMode#QUERY}. {@code query} places {@code code}, {@code state} and {@code iss} in
 *       the redirect URL and re-opens {@code T-URL-LEAK}; the mitigation is left to the caller.</li>
 *   <li>{@link #getPrompt() prompt} adds one OpenID Connect {@code prompt} value.</li>
 * </ul>
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@Value
@Builder
public class AuthorizationRequestOptions {

    /** The options with nothing set: configured scopes, {@code form_post}, no {@code prompt}. */
    public static final AuthorizationRequestOptions DEFAULTS = AuthorizationRequestOptions.builder().build();

    /**
     * The scopes requested by this call instead of the configured ones, in request order, or
     * {@code null} to request the configured scopes. A present list is immutable, non-empty and holds
     * one scope-token per entry.
     */
    @Nullable
    List<String> scopes;

    /**
     * The {@code response_mode} this call requests. Defaults to {@link ResponseMode#FORM_POST}; never
     * {@code null}.
     */
    @Builder.Default
    ResponseMode responseMode = ResponseMode.FORM_POST;

    /** The {@code prompt} this call requests, or {@code null} to send no {@code prompt} parameter. */
    @Nullable
    Prompt prompt;

    /**
     * All-args constructor invoked by the Lombok-generated builder. It validates the options at
     * construction, so invalid options can never reach a request.
     *
     * @param scopes       the per-call scopes, or {@code null} for the configured scopes
     * @param responseMode the response mode; must not be {@code null}
     * @param prompt       the prompt, or {@code null} for none
     * @throws IllegalArgumentException if {@code scopes} is empty or holds a {@code null}, blank or
     *                                  multi-token entry
     * @throws NullPointerException     if {@code responseMode} is {@code null}
     */
    AuthorizationRequestOptions(@Nullable List<String> scopes, ResponseMode responseMode, @Nullable Prompt prompt) {
        this.scopes = validateScopeOverride(scopes);
        this.responseMode = Objects.requireNonNull(responseMode, "responseMode must not be null");
        this.prompt = prompt;
    }

    private static @Nullable List<String> validateScopeOverride(@Nullable List<String> scopes) {
        if (scopes == null) {
            return null;
        }
        List<String> validated = ClientConfiguration.validateScopes(scopes);
        if (validated.isEmpty()) {
            throw new IllegalArgumentException(
                    "a scope override must not be empty; omit it to request the configured scopes");
        }
        return validated;
    }
}
