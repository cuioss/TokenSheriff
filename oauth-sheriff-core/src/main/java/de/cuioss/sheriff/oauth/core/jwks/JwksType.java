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
package de.cuioss.sheriff.oauth.core.jwks;

/**
 * Enum representing the different types of JWKS sources.
 * @since 1.0
 */
public enum JwksType {
    /** HTTP JWKS endpoint */
    HTTP("http", false),
    /** Well-known discovery JWKS endpoint */
    WELL_KNOWN("well-known", true),
    /** File-based JWKS */
    FILE("file", false),
    /** In-memory JWKS */
    MEMORY("memory", false),
    /** No JWKS configured */
    NONE("none", false);

    private final String value;
    private final boolean providesIssuerIdentifier;

    JwksType(String value, boolean providesIssuerIdentifier) {
        this.value = value;
        this.providesIssuerIdentifier = providesIssuerIdentifier;
    }

    /**
     * Returns true if this JWKS type provides its own issuer identifier.
     * Currently only true for well-known discovery endpoints.
     *
     * @return true if this type provides issuer identifier, false otherwise
     */
    public boolean providesIssuerIdentifier() {
        return providesIssuerIdentifier;
    }

    @Override
    public String toString() {
        return value;
    }
}
