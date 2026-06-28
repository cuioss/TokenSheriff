/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.token.validation.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

/**
 * Utility for computing JWK Thumbprints per RFC 7638.
 * <p>
 * The thumbprint is computed by:
 * <ol>
 *   <li>Extracting the required members based on the key type ({@code kty})</li>
 *   <li>Constructing a minimal JSON representation with members in lexicographic order</li>
 *   <li>Hashing with SHA-256</li>
 *   <li>Base64url encoding without padding</li>
 * </ol>
 * <p>
 * Uses only {@link MessageDigest} and {@link Base64} — no external dependencies.
 *
 * @since 1.0
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7638">RFC 7638 - JSON Web Key (JWK) Thumbprint</a>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JwkThumbprintUtil {

    private static final String SHA_256 = "SHA-256";

    /**
     * Computes the JWK Thumbprint (RFC 7638) for the given JWK represented as a Map.
     *
     * @param jwkMap the JWK as a {@code Map<String, Object>} containing at least {@code kty}
     *               and the required members for that key type
     * @return the Base64url-encoded (no padding) SHA-256 thumbprint
     * @throws IllegalArgumentException if the JWK is missing required fields
     */
    public static String computeThumbprint(Map<String, Object> jwkMap) {
        String kty = getRequired(jwkMap, "kty");
        String minimalJson = buildMinimalJson(jwkMap, kty);
        byte[] hash = sha256(minimalJson.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    /**
     * Builds the minimal JSON representation with members in lexicographic order
     * as required by RFC 7638 Section 3.2.
     */
    private static String buildMinimalJson(Map<String, Object> jwkMap, String kty) {
        return switch (kty) {
            case "RSA" -> buildJson(
                    "e", getRequired(jwkMap, "e"),
                    "kty", kty,
                    "n", getRequired(jwkMap, "n")
            );
            case "EC" -> buildJson(
                    "crv", getRequired(jwkMap, "crv"),
                    "kty", kty,
                    "x", getRequired(jwkMap, "x"),
                    "y", getRequired(jwkMap, "y")
            );
            case "OKP" -> buildJson(
                    "crv", getRequired(jwkMap, "crv"),
                    "kty", kty,
                    "x", getRequired(jwkMap, "x")
            );
            default -> throw new IllegalArgumentException("Unsupported key type for JWK Thumbprint: " + kty);
        };
    }

    /**
     * Builds a JSON object string from key-value pairs.
     * Keys must already be in lexicographic order.
     */
    private static String buildJson(String... keyValuePairs) {
        var sb = new StringBuilder("{");
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(keyValuePairs[i]).append("\":\"").append(keyValuePairs[i + 1]).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String getRequired(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("JWK is missing required field for thumbprint: " + key);
        }
        return value.toString();
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance(SHA_256).digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
