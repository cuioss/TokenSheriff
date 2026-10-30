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
import de.cuioss.sheriff.token.commons.error.InboundErrorNormalizer;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * Validates the {@code authorization_code} redirect callback against its originating
 * {@link FlowContext} and returns the authorization code (RFC 6749 §4.1.2).
 * <p>
 * The handler fails closed on every adversarial condition before the code is ever used. The checks
 * run in this order:
 * <ol>
 *   <li>a {@link FlowContext} that has already been used by an earlier {@code handle} call is
 *       rejected (single use, C-7): a context is used up by its first call, whatever that call's
 *       outcome, so a replayed callback can never be redeemed twice;</li>
 *   <li>a missing or non-matching {@code state} is rejected (CSRF defence), compared in constant
 *       time; this applies to error callbacks too, so a forged error callback is refused as a state
 *       mismatch;</li>
 *   <li>an {@code error} callback with a matching {@code state} is rejected with the normalized,
 *       sanitized authorization-server error;</li>
 *   <li>a missing authorization {@code code} is rejected.</li>
 * </ol>
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.2">RFC 6749 §4.1.2 - Authorization Response</a>
 */
public class CallbackHandler {

    private static final CuiLogger LOGGER = new CuiLogger(CallbackHandler.class);

    /**
     * Validates the callback and returns the authorization code to redeem.
     *
     * @param context    the flow context that issued the request; must not be {@code null}
     * @param parameters the parsed callback parameters; must not be {@code null}
     * @return the validated authorization code
     * @throws ClientProtocolException checked in this order: if the context has already been used by
     *                               an earlier call, if the {@code state} is missing or does not match
     *                               (also for an error callback), if the callback signals an error, or
     *                               if the authorization code is absent
     */
    public String handle(FlowContext context, CallbackParameters parameters) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");

        // Single use: the claim is one compare-and-set, so exactly one call per context gets past
        // this point, and the context is used up whatever the outcome of the checks below.
        if (!context.markConsumed()) {
            LOGGER.debug("Rejecting authorization callback: flow context already used");
            throw new ClientProtocolException(
                    "authorization callback rejected: the flow context has already been used");
        }

        // The state is checked before the error branch: an error callback without the CSRF binding
        // is refused as a mismatch instead of being surfaced as an authorization-server error.
        if (!statesMatch(context.state(), parameters.state())) {
            LOGGER.debug("Rejecting authorization callback: state mismatch");
            throw new ClientProtocolException("authorization callback 'state' does not match the flow context");
        }
        if (parameters.hasError()) {
            // Route both error and error_description through the shared commons normalizer so the
            // inbound and outbound error surfaces render one sanitized contract (COMMONS-11).
            throw InboundErrorNormalizer.normalize(parameters.error(), parameters.errorDescription());
        }
        return parameters.getCode()
                .filter(code -> !code.isBlank())
                .orElseThrow(() -> new ClientProtocolException("authorization callback is missing the authorization code"));
    }

    private static boolean statesMatch(String expected, @Nullable String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
