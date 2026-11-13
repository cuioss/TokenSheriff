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

import de.cuioss.sheriff.token.client.internal.LogSanitizer;

import java.util.function.Function;

/**
 * Parses the wire value of a closed-set authorization-request parameter ({@link ResponseMode},
 * {@link Prompt}) into its constant.
 * <p>
 * Only an exact, case-sensitive match of a supported wire value is accepted. Everything else —
 * {@code null}, a blank value, an unsupported value, a value in another letter case, and a value
 * carrying a delimiter such as {@code &}, {@code #}, {@code =}, a space, {@code CR} or {@code LF} — is
 * refused, so a caller-supplied string can never smuggle a second parameter into the request. The
 * refused value is passed through {@link LogSanitizer} before it is placed in the exception message,
 * so {@code CR}/{@code LF} never reach a log or a message raw.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
final class WireValues {

    private WireValues() {
        // utility class — never instantiated
    }

    /**
     * Resolves the constant whose wire value equals {@code value} exactly.
     *
     * @param parameter the request parameter name, used in the refusal message
     * @param value     the wire value to resolve; {@code null} is not a valid value and is refused like a
     *                  blank one
     * @param constants the closed set of constants
     * @param wireValue maps a constant to its wire value
     * @param <E>       the enum type
     * @return the matching constant
     * @throws IllegalArgumentException if {@code value} is {@code null}, blank or not an exact match of
     *                                  a supported wire value
     */
    static <E extends Enum<E>> E parse(String parameter, String value, E[] constants,
            Function<E, String> wireValue) {
        // value is declared non-null because null is never an accepted value; the null check stays as
        // the defensive boundary check for callers outside null-checked code.
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(parameter + " must not be null or blank");
        }
        for (E constant : constants) {
            if (wireValue.apply(constant).equals(value)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("unsupported " + parameter + " value: " + LogSanitizer.sanitize(value));
    }
}
