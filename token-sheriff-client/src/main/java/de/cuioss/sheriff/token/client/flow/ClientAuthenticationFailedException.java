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
 * Signals that the authorization server refused the token request because the <em>client</em> failed
 * to authenticate itself — RFC 6749 §5.2 {@code invalid_client}.
 * <p>
 * <strong>The recognition rule is an allow-list of exact shapes.</strong> This type is raised for a
 * non-success response in exactly two cases:
 * <ul>
 *   <li>a client-error status ({@code 4xx}) whose RFC 6749 §5.2 body names the error code
 *       {@code invalid_client} — the code the specification assigns to a failed client
 *       authentication;</li>
 *   <li>HTTP {@code 401} whose body names {@code unauthorized_client}. Keycloak answers a wrong client
 *       secret with exactly this pair; the {@code 401} status is the server's own statement that the
 *       client authentication failed, which is what distinguishes it from the RFC meaning of
 *       {@code unauthorized_client} (an authenticated client that is not permitted to use the grant
 *       type).</li>
 * </ul>
 * Every other shape falls through to a plain {@link TransportException}: {@code unauthorized_client} on
 * any status other than {@code 401} (a {@code 403}, for example, is an authenticated client refused a
 * grant), any {@code 5xx} even when it carries one of these codes, and an unparseable, absent, oversize
 * or duplicate-member body, which is an unknown and never a match. {@code invalid_grant} keeps raising
 * {@link CredentialRejectedException}.
 * <p>
 * It is <strong>not</strong> a redemption and it says nothing about the presented grant: the server
 * refused the client before it processed the grant, so a refresh token presented with the request is
 * untouched. {@link RefreshFlow#classify(Throwable)} therefore classifies this type as
 * {@link RefreshFailureClassification.Kind#PRE_REDEMPTION} — a misconfigured client credential is not a
 * dead refresh credential, and ending the session over it would destroy a session that becomes usable
 * again once the client configuration is corrected.
 * <p>
 * It carries <strong>no authorization-server-controlled state</strong>. The detail message is composed
 * from the observed HTTP status and fixed text only; neither the parsed error code nor any part of the
 * response body is interpolated into it (CWE-117).
 * <p>
 * It extends {@link TransportException} so every existing caller and every documented
 * {@code @throws TransportException} contract keeps holding unchanged; only a caller that needs the
 * client-authentication distinction catches this narrower type.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-5.2">RFC 6749 §5.2 - Error Response</a>
 */
// java:S110 — the inheritance depth is inherited wholesale from the library's existing exception
// taxonomy (TransportException and its ancestors); this type adds exactly one level to it, exactly as
// CredentialRejectedException does. The subtype relation is load-bearing for source compatibility.
@SuppressWarnings("java:S110")
public class ClientAuthenticationFailedException extends TransportException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param message the caller-safe detail message; must be composed without interpolating any
     *                authorization-server-controlled value
     */
    public ClientAuthenticationFailedException(String message) {
        super(message);
    }
}
