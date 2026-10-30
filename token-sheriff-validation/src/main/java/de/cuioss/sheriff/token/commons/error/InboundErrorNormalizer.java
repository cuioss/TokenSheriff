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
package de.cuioss.sheriff.token.commons.error;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Normalizes an inbound OAuth 2.0 error response ({@code error} plus optional
 * {@code error_description}, RFC 6749 §4.1.2.1 / §5.2) into the typed {@link ClientProtocolException}
 * the error model renders — <em>one contract, both directions</em> (COMMONS-11).
 * <p>
 * The raw upstream {@code error} and {@code error_description} are authorization-server-controlled and
 * are never surfaced verbatim: both are neutralized for log/response injection (CWE-117) before they
 * reach the caller-safe exception message. The neutralization is the shared {@link ErrorTextSanitizer},
 * which lives in this {@code commons} base layer because the base layer must not depend on the client
 * engine (the ArchUnit boundary, ADR-0001); the client's own log sanitizer delegates to it. It escapes
 * CR, LF, tab and the other ASCII controls, DEL, the C1 control characters U+0080 to U+009F (including
 * U+0085 NEL) and the Unicode line separators U+2028 (LS) and U+2029 (PS), which Unicode-aware log
 * viewers and appenders treat as line breaks, and truncates each field to
 * {@value ErrorTextSanitizer#MAX_LENGTH} characters.
 * <p>
 * Whether a field is <em>absent</em> is decided on the raw value, not on its escaped rendering: a value
 * that is {@code null} or consists solely of white space that the sanitizer leaves untouched (for
 * example spaces, LS or PS) counts as blank, so escaping a separator never turns an empty field into
 * reported content.
 * <p>
 * The utility is stateless and has no configuration surface.
 *
 * @since 1.0
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.2.1">RFC 6749 §4.1.2.1</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-5.2">RFC 6749 §5.2</a>
 */
public final class InboundErrorNormalizer {

    /**
     * The registered OAuth 2.0 error codes across the authorization endpoint (RFC 6749 §4.1.2.1) and
     * the token endpoint (RFC 6749 §5.2). A code outside this set is reported as unrecognized rather
     * than trusted as a known protocol condition.
     */
    private static final Set<String> REGISTERED_ERROR_CODES = Set.of(
            "invalid_request",
            "invalid_client",
            "invalid_grant",
            "unauthorized_client",
            "unsupported_grant_type",
            "unsupported_response_type",
            "access_denied",
            "invalid_scope",
            "server_error",
            "temporarily_unavailable");

    private InboundErrorNormalizer() {
        // utility class — never instantiated
    }

    /**
     * Normalizes a raw inbound error response into a caller-safe {@link ClientProtocolException}.
     *
     * @param error            the raw {@code error} code from the authorization server, or {@code null}
     * @param errorDescription the raw {@code error_description}, or {@code null}
     * @return a {@link ClientProtocolException} whose message names the (recognized-or-unrecognized)
     *         error code and, when present, the sanitized description — never the raw upstream content
     */
    public static ClientProtocolException normalize(@Nullable String error, @Nullable String errorDescription) {
        String safeError = ErrorTextSanitizer.sanitize(error);
        String message;
        if (safeError == null || isBlankBeforeEscaping(error)) {
            message = "authorization server returned an error callback without an error code";
        } else if (isRegistered(error)) {
            message = "authorization server returned an error callback: " + safeError;
        } else {
            message = "authorization server returned an unrecognized error callback: " + safeError;
        }
        String safeDescription = ErrorTextSanitizer.sanitize(errorDescription);
        if (safeDescription != null && !isBlankBeforeEscaping(errorDescription)) {
            message = message + " (" + safeDescription + ")";
        }
        return new ClientProtocolException(message);
    }

    private static boolean isRegistered(@Nullable String error) {
        return error != null && REGISTERED_ERROR_CODES.contains(error);
    }

    /**
     * Decides blankness on the raw value, so the escaping done by {@link ErrorTextSanitizer#sanitize(String)}
     * cannot turn a field that carries no content into reported content.
     *
     * @param value the raw untrusted value; may be {@code null}
     * @return {@code true} when {@code value} is {@code null} or holds only white space (in the sense
     *         of {@link String#isBlank()}) that is not a control character, such as spaces, LS (U+2028)
     *         or PS (U+2029); {@code false} as soon as one character is not white space or is a control
     *         character
     */
    private static boolean isBlankBeforeEscaping(@Nullable String value) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (ErrorTextSanitizer.isControlCharacter(c) || !Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }
}
