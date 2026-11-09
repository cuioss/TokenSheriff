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
package de.cuioss.sheriff.token.client.hardening;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.support.ParameterDeclarations;

import java.util.List;
import java.util.stream.Stream;

/**
 * Attack database of the line-break class for a response header value: the curated set of
 * separators an attacker can use to split, fold or truncate a header the client reads for a security
 * decision ({@code Content-Type}, {@code WWW-Authenticate}, {@code DPoP-Nonce}).
 * <p>
 * It follows the shape of the {@code de.cuioss.http.security.database} attack databases — a fixed
 * corpus of described test cases plus a nested {@link ArgumentsProvider} for
 * {@code @ArgumentsSource} — but is local to the client, because those databases target URL paths
 * and parameters and carry a URL failure type that has no meaning for a header value.
 * <p>
 * Every separator is built from its code point, never embedded raw, so a reviewer sees which control
 * character a case carries.
 *
 * @author Oliver Wolff
 */
public final class HeaderLineBreakAttackDatabase {

    /** A bare carriage return. */
    public static final HeaderLineBreakAttack CR = new HeaderLineBreakAttack("CR", "\r",
            "bare carriage return: a lenient parser ends the field line here");

    /** A bare line feed. */
    public static final HeaderLineBreakAttack LF = new HeaderLineBreakAttack("LF", "\n",
            "bare line feed: RFC 9112 §2.2 lets a recipient treat it as a line terminator");

    /** A full line terminator. */
    public static final HeaderLineBreakAttack CRLF = new HeaderLineBreakAttack("CRLF", "\r\n",
            "carriage return and line feed: splits the value into a second field line (response splitting)");

    /** An obs-fold continuation. */
    public static final HeaderLineBreakAttack OBS_FOLD = new HeaderLineBreakAttack("obs-fold", "\r\n ",
            "obs-fold continuation (RFC 9112 §5.2): a line terminator followed by whitespace");

    /** An embedded NUL. */
    public static final HeaderLineBreakAttack NUL = new HeaderLineBreakAttack("NUL", "\0",
            "embedded NUL: a C-string consumer truncates the value here");

    private static final List<HeaderLineBreakAttack> ATTACKS = List.of(CR, LF, CRLF, OBS_FOLD, NUL);

    private HeaderLineBreakAttackDatabase() {
        // attack database — never instantiated
    }

    /**
     * @return every line-break attack of the class, in a fixed order
     */
    public static List<HeaderLineBreakAttack> getAttackTestCases() {
        return ATTACKS;
    }

    /**
     * @return {@link #getAttackTestCases()} as a stream
     */
    public static Stream<HeaderLineBreakAttack> streamTestCases() {
        return ATTACKS.stream();
    }

    /**
     * One line-break attack: a named separator and why it is dangerous inside a header value.
     *
     * @param name        the short name a parameterized test reports
     * @param separator   the control characters the attack inserts
     * @param description what the separator does to a header consumer
     */
    public record HeaderLineBreakAttack(String name, String separator, String description) {

        /**
         * @param prefix the part of the header value before the separator
         * @param suffix the part of the header value after the separator
         * @return {@code prefix}, the separator and {@code suffix}, joined
         */
        public String inject(String prefix, String suffix) {
            return prefix + separator + suffix;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Supplies every {@link HeaderLineBreakAttack} to an {@code @ArgumentsSource} parameterized test.
     */
    public static final class ArgumentsProvider implements org.junit.jupiter.params.provider.ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ParameterDeclarations parameters,
                ExtensionContext context) {
            return streamTestCases().map(Arguments::of);
        }
    }
}
