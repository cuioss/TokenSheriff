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

import com.dslplatform.json.JsonReader;
import com.dslplatform.json.JsonWriter;
import com.dslplatform.json.NumberConverter;

/**
 * DSL-JSON field converter for an integer member that must be a JSON <em>number</em>.
 * <p>
 * DSL-JSON's built-in {@code long} reader also accepts a quoted integer such as {@code "3600"}. A
 * member the specification types as a number — {@code expires_in} in RFC 6749 §5.1 and RFC 9126
 * §2.2 — is read through this converter instead, which refuses a JSON string with a parse error, so
 * the payload takes the caller's existing malformed-response path. Every other value is read exactly
 * as the built-in reader reads it.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public final class JsonNumberLongConverter {

    /** Reads a JSON number into a {@code long}; a JSON string is a parse error. */
    @SuppressWarnings("java:S2386") // DSL-JSON resolves converters through these public static fields
    public static final JsonReader.ReadObject<Long> JSON_READER = reader -> {
        if (reader.last() == '"') {
            throw reader.newParseError("Expecting a JSON number, found a string");
        }
        return NumberConverter.deserializeLong(reader);
    };

    /** Writes the value as a JSON number. */
    @SuppressWarnings("java:S2386") // DSL-JSON resolves converters through these public static fields
    public static final JsonWriter.WriteObject<Long> JSON_WRITER = (writer, value) -> {
        if (value == null) {
            writer.writeNull();
        } else {
            NumberConverter.serialize(value, writer);
        }
    };

    private JsonNumberLongConverter() {
        // converter holder — never instantiated
    }
}
