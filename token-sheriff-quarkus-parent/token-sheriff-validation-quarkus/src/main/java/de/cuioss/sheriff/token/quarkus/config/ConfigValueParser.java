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
package de.cuioss.sheriff.token.quarkus.config;

import lombok.experimental.UtilityClass;
import org.eclipse.microprofile.config.Config;

import java.util.*;

/**
 * Shared parsing helpers for configuration values used by the resolver classes.
 * <p>
 * Centralizes the CSV splitting and prefix-based name discovery logic that was
 * previously duplicated across {@code IssuerConfigResolver},
 * {@code AccessLogFilterConfigResolver}, and {@code JweDecryptionConfigResolver},
 * ensuring consistent handling of whitespace and empty segments.
 * </p>
 *
 * @since 1.0
 */
@UtilityClass
public final class ConfigValueParser {

    /**
     * Splits a comma-separated configuration value into its segments.
     * <p>
     * Each segment is trimmed, and empty segments (e.g. from {@code "a,,b"} or
     * trailing commas) are filtered out.
     * </p>
     *
     * @param value the raw comma-separated configuration value
     * @return immutable list of trimmed, non-empty segments; empty list if no segments remain
     */
    public static List<String> splitCsv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Discovers the distinct name segments that directly follow a property prefix.
     * <p>
     * For every property name starting with {@code prefix}, the substring up to the
     * next dot is collected. Properties without a further dot after the prefix are
     * ignored. For example, with prefix {@code "sheriff.token.issuers."} the property
     * {@code "sheriff.token.issuers.default.enabled"} yields the segment {@code "default"}.
     * </p>
     *
     * @param config the configuration to scan
     * @param prefix the property name prefix, typically ending with a dot
     * @return set of discovered name segments in encounter order
     */
    public static Set<String> discoverNameSegments(Config config, String prefix) {
        Set<String> names = new LinkedHashSet<>();
        for (String propertyName : config.getPropertyNames()) {
            if (propertyName.startsWith(prefix)) {
                String remainder = propertyName.substring(prefix.length());
                int firstDot = remainder.indexOf('.');
                if (firstDot > 0) {
                    names.add(remainder.substring(0, firstDot));
                }
            }
        }
        return names;
    }

    /**
     * Discovers name segments that are configured only through environment variables.
     * <p>
     * MicroProfile Config exposes an environment variable under its raw name, for example
     * {@code SHERIFF_TOKEN_ISSUERS_KEYCLOAK_ENABLED}, so {@link #discoverNameSegments(Config, String)}
     * does not see it. This method converts {@code prefix} and every anchor leaf key to
     * environment form (upper-case, every non-alphanumeric character replaced by {@code _}).
     * For every property name that starts with the environment prefix, the longest matching
     * {@code _<ANCHOR>} suffix is stripped and the remaining part is lower-cased to become the
     * candidate name.
     * </p>
     * <p>
     * <strong>Lossy reconstruction:</strong> environment form cannot represent dots, dashes
     * or case, so {@code SHERIFF_TOKEN_ISSUERS_MY_IDP_ENABLED} yields {@code my_idp}, never
     * {@code my-idp}. The reconstruction still works because the dotted lookup of every key
     * built from the candidate, for example {@code sheriff.token.issuers.my_idp.enabled}, is
     * resolved by the environment config source through the same mangling, and so finds the
     * original variable. A candidate whose environment form equals that of a name in
     * {@code alreadyDiscovered}, or of a candidate found earlier, is skipped, so a name
     * configured both dotted and through environment variables is reported once.
     * </p>
     * <p>
     * <strong>Limitation:</strong> only properties ending in one of the anchor leaf keys are
     * considered, and the longest anchor wins. With the anchors {@code dpop.enabled} and
     * {@code enabled}, an environment-only name whose last segment is {@code dpop} — for
     * example {@code SHERIFF_TOKEN_ISSUERS_MY_DPOP_ENABLED} — is read as {@code my} with
     * {@code dpop.enabled}. Such a name must be configured in dotted form.
     * </p>
     *
     * @param config the configuration to scan
     * @param prefix the dotted property name prefix, typically ending with a dot
     * @param anchorLeafKeys the dotted leaf keys that identify a name, for example {@code enabled}
     * @param alreadyDiscovered names already discovered in dotted form, which must not be reported again
     * @return set of discovered name segments in encounter order, excluding {@code alreadyDiscovered}
     */
    public static Set<String> discoverEnvNameSegments(Config config, String prefix, List<String> anchorLeafKeys,
            Collection<String> alreadyDiscovered) {
        String envPrefix = toEnvForm(prefix);
        List<String> envAnchorSuffixes = anchorLeafKeys.stream()
                .map(anchor -> "_" + toEnvForm(anchor))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        Set<String> seenEnvForms = new HashSet<>();
        alreadyDiscovered.forEach(name -> seenEnvForms.add(toEnvForm(name)));

        Set<String> names = new LinkedHashSet<>();
        for (String propertyName : config.getPropertyNames()) {
            if (!propertyName.startsWith(envPrefix)) {
                continue;
            }
            String remainder = propertyName.substring(envPrefix.length());
            for (String anchorSuffix : envAnchorSuffixes) {
                if (remainder.length() > anchorSuffix.length() && remainder.endsWith(anchorSuffix)) {
                    String candidate = remainder.substring(0, remainder.length() - anchorSuffix.length())
                            .toLowerCase(Locale.ROOT);
                    if (seenEnvForms.add(toEnvForm(candidate))) {
                        names.add(candidate);
                    }
                    break;
                }
            }
        }
        return names;
    }

    /**
     * Converts a dotted property name or fragment to environment variable form, following
     * the MicroProfile Config mapping rule: upper-case, every non-alphanumeric character
     * replaced by {@code _}.
     */
    private static String toEnvForm(String name) {
        StringBuilder envForm = new StringBuilder(name.length());
        for (char character : name.toCharArray()) {
            boolean asciiAlphanumeric = (character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9');
            envForm.append(asciiAlphanumeric ? Character.toUpperCase(character) : '_');
        }
        return envForm.toString();
    }
}
