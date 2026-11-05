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

import de.cuioss.sheriff.token.commons.error.TransportException;

import java.io.Serial;

/**
 * Signals that the authorization server refused the token request because it does not accept the
 * credential that was presented — a client-error status ({@code 4xx}) whose RFC 6749 §5.2 body names
 * the error code {@code invalid_grant}.
 * <p>
 * This is a <em>terminal</em> failure for the presented credential and it is <strong>not</strong> a
 * redemption: the authorization server never processed the grant, so nothing was rotated and there is
 * no successor. It is nonetheless categorically different from every other pre-redemption failure,
 * which is why it has a type of its own. A connection failure, a DNS failure, an SSRF-blocked target
 * or a {@code 5xx} all leave the presented refresh token in exactly the state it was in — retrying
 * later may well succeed. An {@code invalid_grant} refusal says the opposite: the server has looked at
 * this credential and declared it dead (expired, revoked, or already single-use redeemed), so a caller
 * that keeps the session alive is holding something no retry will revive.
 * <p>
 * <strong>The recognition rule is an allow-list of exactly one code.</strong> Only
 * {@code invalid_grant} on a {@code 4xx} raises this type. Every other shape — an unparseable body, an
 * absent body, a body missing the {@code error} member, an oversize body, an unrecognized code such as
 * {@code use_dpop_nonce}, and any {@code 5xx} even when it carries an {@code invalid_grant} body — falls
 * through to a plain {@link TransportException} and stays pre-redemption. A failed client
 * authentication ({@code invalid_client}) is reported as the sibling
 * {@link ClientAuthenticationFailedException}, which is likewise pre-redemption. The fail-safe direction is to preserve the session: mistaking a transient fault for
 * a dead credential destroys a working session, which is the inversion this whole classification
 * exists to prevent.
 * <p>
 * It carries <strong>no authorization-server-controlled state</strong>. The detail message is composed
 * from the observed HTTP status and fixed text only; neither the parsed error code nor any part of the
 * response body is interpolated into it, so no AS-controlled string can reach a log appender or an
 * exception message through this type (CWE-117).
 * <p>
 * It extends {@link TransportException} so every existing caller and every documented
 * {@code @throws TransportException} contract keeps holding unchanged; only a caller that needs the
 * credential-rejected distinction catches this narrower type. Callers must classify through
 * {@link RefreshFlow#classify(Throwable)} rather than by listing exception types.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-5.2">RFC 6749 §5.2 - Error Response</a>
 */
// java:S110 — the inheritance depth is inherited wholesale from the library's existing exception
// taxonomy (TransportException and its ancestors); this type adds exactly one level to it, exactly as
// RedeemedResponseException does. The subtype relation is load-bearing for source compatibility: it is
// what lets the credential-rejected signal be added without touching a single existing catch block,
// assertThrows or documented @throws.
@SuppressWarnings("java:S110")
public class CredentialRejectedException extends TransportException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param message the caller-safe detail message; must be composed without interpolating any
     *                authorization-server-controlled value
     */
    public CredentialRejectedException(String message) {
        super(message);
    }
}
