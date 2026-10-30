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

import de.cuioss.sheriff.token.commons.error.ErrorTextSanitizer;
import org.jspecify.annotations.Nullable;

/**
 * Sanitizes an externally-sourced value before it is interpolated into a {@code %s} log template,
 * defending against log injection / log forging (CWE-117).
 * <p>
 * Several call sites log a value that crossed a trust boundary before it was rejected — the RFC 9207
 * {@code iss} authorization-response parameter (fully attacker-controlled via the browser callback),
 * a caller-supplied {@code post_logout_redirect_uri}, or the {@code issuer} of an authorization
 * server's discovery document. Parameterized logging alone does not neutralize embedded {@code CR} /
 * {@code LF} on a plain-text appender — the substituted value is still written verbatim, so an
 * attacker who embeds {@code %0d%0a} can forge a new, seemingly legitimate log entry. This sanitizer
 * escapes (rather than drops) {@code CR}, {@code LF}, tab, the remaining ASCII control characters, DEL,
 * the C1 control characters U+0080 to U+009F (including U+0085 NEL), and the Unicode line separators
 * U+2028 (LS) and U+2029 (PS) — which Unicode-aware log viewers and appenders treat as line breaks —
 * preserving the value's investigative content while
 * making it impossible to inject a line break.
 * <p>
 * The value is also truncated to {@value ErrorTextSanitizer#MAX_LENGTH} characters before escaping, so
 * an attacker cannot flood the log with an arbitrarily long value (L18). When truncation occurs the
 * marker {@value ErrorTextSanitizer#TRUNCATION_MARKER} is appended so the elision is visible in the log.
 * <p>
 * The escaping and truncation are implemented once, in the commons base layer's
 * {@link ErrorTextSanitizer}; this class is the client engine's entry point and delegates to it, so the
 * client and the inbound error normalizer can never drift apart.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://owasp.org/www-community/attacks/Log_Injection">OWASP Log Injection</a>
 */
public final class LogSanitizer {

    private LogSanitizer() {
        // utility class — never instantiated
    }

    /**
     * Escapes {@code CR}, {@code LF}, every other ASCII control character, DEL, the C1 control
     * characters U+0080 to U+009F (including U+0085 NEL), and the Unicode line separators U+2028 (LS)
     * and U+2029 (PS) in {@code value} — all but {@code CR}, {@code LF} and tab as a
     * backslash-{@code u} four-digit hex escape — so it cannot
     * forge a new log line when substituted into a log template, and truncates the value to
     * {@value ErrorTextSanitizer#MAX_LENGTH} characters to bound log flooding. Delegates to
     * {@link ErrorTextSanitizer#sanitize(String)}.
     *
     * @param value the untrusted value to sanitize; may be {@code null}
     * @return the sanitized (and, when over-long, truncated) value, or {@code null} when {@code value}
     *         is {@code null}
     */
    public static @Nullable String sanitize(@Nullable String value) {
        return ErrorTextSanitizer.sanitize(value);
    }
}
