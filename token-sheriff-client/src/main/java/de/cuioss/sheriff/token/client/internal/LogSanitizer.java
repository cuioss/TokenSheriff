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
package de.cuioss.sheriff.token.client.internal;

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
 * escapes (rather than drops) {@code CR}, {@code LF}, tab, and the remaining ASCII control characters,
 * preserving the value's investigative content while making it impossible to inject a line break.
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
     * Escapes {@code CR}, {@code LF} and every other ASCII control character in {@code value} so it
     * cannot forge a new log line when substituted into a log template.
     *
     * @param value the untrusted value to sanitize; may be {@code null}
     * @return the sanitized value, or {@code null} when {@code value} is {@code null}
     */
    public static @Nullable String sanitize(@Nullable String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sanitized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\r' -> sanitized.append("\\r");
                case '\n' -> sanitized.append("\\n");
                case '\t' -> sanitized.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7F) {
                        sanitized.append(String.format("\\u%04x", (int) c));
                    } else {
                        sanitized.append(c);
                    }
                }
            }
        }
        return sanitized.toString();
    }
}
