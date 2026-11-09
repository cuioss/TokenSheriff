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

/**
 * The line-break rule for a response header value the client reads for a security decision
 * ({@code WWW-Authenticate}, {@code DPoP-Nonce}).
 * <p>
 * A field value never legitimately carries {@code CR}, {@code LF} or {@code NUL} (RFC 9110 §5.5).
 * One that does was split, folded or truncated on its way in — an obs-fold continuation (RFC 9112
 * §5.2) is a {@code CRLF} followed by whitespace, so it is covered by the same rule — and must be
 * refused rather than repaired, because stripping the separator would turn a malformed value into an
 * actionable one.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public final class HeaderValues {

    private HeaderValues() {
        // utility class — never instantiated
    }

    /**
     * @param value the header value; must not be {@code null}
     * @return whether {@code value} carries a {@code CR}, {@code LF} or {@code NUL}
     */
    public static boolean containsLineBreakOrNul(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\0') >= 0;
    }
}
