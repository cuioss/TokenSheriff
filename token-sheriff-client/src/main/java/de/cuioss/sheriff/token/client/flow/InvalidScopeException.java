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

import de.cuioss.sheriff.token.commons.error.ErrorTextSanitizer;
import de.cuioss.sheriff.token.commons.error.TransportException;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Objects;
import java.util.Optional;

/**
 * Signals that the authorization server refused the token request because the requested
 * <em>scope</em> is invalid, unknown, malformed or exceeds what the resource owner granted — RFC 6749
 * §5.2 {@code invalid_scope}.
 * <p>
 * <strong>The recognition rule is an allow-list of one exact shape.</strong> This type is raised for a
 * non-success response only when the status is a client error ({@code 4xx}) and the RFC 6749 §5.2 body
 * names exactly the error code {@code invalid_scope}. Every other shape keeps raising a plain
 * {@link TransportException}: any {@code 5xx} even when it carries this code, any other code, and an
 * unparseable, absent, oversize or duplicate-member body, which is an unknown and never a match.
 * <p>
 * It is <strong>not</strong> a redemption and it says nothing about the presented grant: the server
 * judged the requested scope before it applied any grant, so no new token was issued and the client has
 * no evidence that a refresh token presented with the request was rotated. The refusal does not show that
 * the presented token is still valid server-side. Because the answer depends on the request rather than
 * on network conditions, repeating
 * the same request will not change it. {@link RefreshFlow#classify(Throwable)} therefore classifies this
 * type as {@link RefreshFailureClassification.Kind#SCOPE_REFUSED}: the session is kept, and no retry is
 * owed.
 * <p>
 * The name follows the RFC error code on purpose. {@link RedeemedScopeRefusalException} is a different
 * situation — a client-side refusal of a scope the server <em>granted</em>, raised after the grant was
 * redeemed — and the two must not be read as one.
 * <p>
 * It carries the parsed error code and the server's {@code error_description}, the latter only after
 * {@link ErrorTextSanitizer#sanitize(String)} neutralized it (ADR-0022); the raw response body is never
 * carried. The detail message is composed from the observed HTTP status and fixed text only, so no
 * authorization-server-controlled value is interpolated into it (CWE-117).
 * <p>
 * It extends {@link TransportException} so every existing caller and every documented
 * {@code @throws TransportException} contract keeps holding unchanged (ADR-0006); only a caller that
 * needs the scope-refusal distinction catches this narrower type.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-5.2">RFC 6749 §5.2 - Error Response</a>
 */
// java:S110 — the inheritance depth is inherited wholesale from the library's existing exception
// taxonomy (TransportException and its ancestors); this type adds exactly one level to it, exactly as
// ClientAuthenticationFailedException does. The subtype relation is load-bearing for source compatibility.
@SuppressWarnings("java:S110")
public class InvalidScopeException extends TransportException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The RFC 6749 §5.2 error code this type is raised for. */
    public static final String ERROR_CODE = "invalid_scope";

    private final String errorCode;

    @Nullable
    private final String errorDescription;

    /**
     * @param message          the caller-safe detail message; must be composed without interpolating any
     *                         authorization-server-controlled value
     * @param errorCode        the parsed RFC 6749 §5.2 error code; must not be {@code null}
     * @param errorDescription the server's {@code error_description}, or {@code null} when the response
     *                         carried none; it is sanitized here, so the raw value may be passed
     */
    public InvalidScopeException(String message, String errorCode, @Nullable String errorDescription) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.errorDescription = ErrorTextSanitizer.sanitize(errorDescription);
    }

    /**
     * @return the parsed RFC 6749 §5.2 error code, {@value #ERROR_CODE} for every instance the token
     *         endpoint client raises
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * @return the server's {@code error_description}, sanitized against log injection and truncated to
     *         {@value ErrorTextSanitizer#MAX_LENGTH} characters, or empty when the response carried none
     */
    public Optional<String> getErrorDescription() {
        return Optional.ofNullable(errorDescription);
    }
}
