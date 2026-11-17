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
package de.cuioss.sheriff.token.commons.transport;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Decides whether an egress allow-list entry is a bare host.
 * <p>
 * An entry of {@link EgressPolicy.EgressPolicyBuilder#allowedHost(String)} — and of every builder
 * that feeds it — names a host exactly as {@link URI#getHost()} reports it. {@link #refusal(String)}
 * admits:
 * <ul>
 *   <li>an ASCII host name of dot-separated letter-digit-hyphen labels (each 1 to 63 characters, not
 *       starting or ending with {@code -}, at most 253 characters in total);</li>
 *   <li>a dotted IPv4 literal ({@code 10.0.0.5});</li>
 *   <li>a bare IPv6 literal — two or more colons, validated without DNS by parsing it inside brackets
 *       through {@link URI} ({@code ::1});</li>
 *   <li>a bracketed IPv6 literal ({@code [::1]}).</li>
 * </ul>
 * Everything else is refused: {@code null}, blank, a port in any form ({@code name:8080},
 * {@code [::1]:8080}, any other single-colon form), whitespace, control characters, non-ASCII
 * characters, and the URL syntax characters {@code /}, {@code @}, {@code ?}, {@code #} and
 * {@code %}. A refusal is one of the fixed texts declared here; none echoes the entry.
 * <p>
 * {@link #requireBareHost(String)} returns the canonical form {@link EgressPolicy} matches against:
 * lower-case, with a bare IPv6 literal wrapped in brackets, because {@link URI#getHost()} reports an
 * IPv6 host bracketed. The class is stateless and safe for concurrent use.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EgressHostValidator {

    /** Refusal text for a {@code null} entry. */
    static final String NULL_ENTRY = "egress allow-list host must not be null";

    /** Refusal text for a blank entry. */
    static final String BLANK_ENTRY = "egress allow-list host must not be blank";

    /** Refusal text for an entry carrying a control character. */
    static final String CONTROL_CHARACTER = "egress allow-list host must not contain control characters";

    /** Refusal text for an entry carrying whitespace. */
    static final String WHITESPACE = "egress allow-list host must not contain whitespace";

    /** Refusal text for an entry carrying a non-ASCII character. */
    static final String NON_ASCII = "egress allow-list host must contain ASCII characters only";

    /** Refusal text for an entry carrying a URL syntax character. */
    static final String URL_SYNTAX =
            "egress allow-list host must be a bare host, not a URL (no '/', '@', '?', '#' or '%')";

    /** Refusal text for an entry carrying a port, or any other single-colon form. */
    static final String PORT = "egress allow-list host must be a bare host without a port";

    /** Refusal text for an entry that is not a valid IPv6 literal. */
    static final String INVALID_IPV6 = "egress allow-list host is not a valid IPv6 literal";

    /** Refusal text for an entry that is not a valid host name or IPv4 literal. */
    static final String INVALID_HOST = "egress allow-list host is not a valid host name or IPv4 literal";

    private static final int MAX_HOST_NAME_LENGTH = 253;
    private static final int MAX_LABEL_LENGTH = 63;
    private static final int IPV4_OCTETS = 4;
    private static final int MAX_OCTET = 255;
    private static final String URL_SYNTAX_CHARACTERS = "/@?#%";

    /**
     * Checks whether an entry is a bare host.
     *
     * @param entry the allow-list entry; may be {@code null}
     * @return empty when the entry is a bare host name, IPv4 literal or IPv6 literal, otherwise the
     *         fixed refusal text; the text never contains the entry
     */
    public static Optional<String> refusal(@Nullable String entry) {
        if (entry == null) {
            return Optional.of(NULL_ENTRY);
        }
        if (entry.isBlank()) {
            return Optional.of(BLANK_ENTRY);
        }
        Optional<String> characterRefusal = characterRefusal(entry);
        if (characterRefusal.isPresent()) {
            return characterRefusal;
        }
        if (entry.startsWith("[")) {
            return bracketedIpv6Refusal(entry);
        }
        long colons = entry.chars().filter(c -> c == ':').count();
        if (colons == 1) {
            return Optional.of(PORT);
        }
        if (colons > 1) {
            return isIpv6Literal(entry) ? Optional.empty() : Optional.of(INVALID_IPV6);
        }
        return isHostNameOrIpv4(entry) ? Optional.empty() : Optional.of(INVALID_HOST);
    }

    /**
     * Requires an entry to be a bare host and returns its canonical form.
     *
     * @param entry the allow-list entry; may be {@code null}, which is refused
     * @return the entry lower-cased, a bare IPv6 literal wrapped in brackets
     * @throws IllegalArgumentException with the fixed refusal text if the entry is not a bare host;
     *                                  the message never contains the entry
     */
    public static String requireBareHost(@Nullable String entry) {
        Optional<String> refusal = refusal(entry);
        if (refusal.isPresent()) {
            throw new IllegalArgumentException(refusal.get());
        }
        // refusal(null) is present, so entry is non-null here.
        String canonical = Objects.requireNonNull(entry).toLowerCase(Locale.ROOT);
        if (canonical.indexOf(':') >= 0 && !canonical.startsWith("[")) {
            return "[" + canonical + "]";
        }
        return canonical;
    }

    private static Optional<String> characterRefusal(String entry) {
        for (int i = 0; i < entry.length(); i++) {
            char c = entry.charAt(i);
            if (c > 0x7E) {
                return Optional.of(c == 0x7F ? CONTROL_CHARACTER : NON_ASCII);
            }
            if (c < 0x20) {
                return Optional.of(CONTROL_CHARACTER);
            }
            if (c == ' ') {
                return Optional.of(WHITESPACE);
            }
            if (URL_SYNTAX_CHARACTERS.indexOf(c) >= 0) {
                return Optional.of(URL_SYNTAX);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> bracketedIpv6Refusal(String entry) {
        int close = entry.indexOf(']');
        if (close > 0 && close < entry.length() - 1 && entry.charAt(close + 1) == ':') {
            return Optional.of(PORT);
        }
        if (close != entry.length() - 1) {
            return Optional.of(INVALID_IPV6);
        }
        String literal = entry.substring(1, close);
        return literal.indexOf(':') >= 0 && isIpv6Literal(literal) ? Optional.empty() : Optional.of(INVALID_IPV6);
    }

    /**
     * Validates an IPv6 literal without DNS by parsing it as the bracketed host of a URI.
     */
    private static boolean isIpv6Literal(String literal) {
        if (literal.indexOf('[') >= 0 || literal.indexOf(']') >= 0) {
            return false;
        }
        try {
            return new URI("http://[" + literal + "]").getHost() != null;
        } catch (URISyntaxException e) {
            // Not a valid IPv6 literal; the parser's message is not propagated.
            return false;
        }
    }

    private static boolean isHostNameOrIpv4(String entry) {
        if (entry.length() > MAX_HOST_NAME_LENGTH) {
            return false;
        }
        String[] labels = entry.split("\\.", -1);
        boolean allNumeric = true;
        for (String label : labels) {
            if (!isLdhLabel(label)) {
                return false;
            }
            allNumeric &= label.chars().allMatch(Character::isDigit);
        }
        return !allNumeric || isIpv4Literal(labels);
    }

    private static boolean isLdhLabel(String label) {
        if (label.isEmpty() || label.length() > MAX_LABEL_LENGTH
                || label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') {
            return false;
        }
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            boolean ldh = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-';
            if (!ldh) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv4Literal(String[] labels) {
        if (labels.length != IPV4_OCTETS) {
            return false;
        }
        for (String octet : labels) {
            if (octet.length() > 3 || Integer.parseInt(octet) > MAX_OCTET) {
                return false;
            }
        }
        return true;
    }
}
