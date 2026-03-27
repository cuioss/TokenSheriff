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
package de.cuioss.sheriff.oauth.core.jwks.http;

import com.dslplatform.json.DslJson;
import de.cuioss.http.client.ContentType;
import de.cuioss.http.client.converter.StringContentConverter;
import de.cuioss.sheriff.oauth.core.ParserConfig;
import de.cuioss.sheriff.oauth.core.json.Jwks;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static de.cuioss.sheriff.oauth.core.JWTValidationLogMessages.WARN;

/**
 * HTTP content converter for JSON Web Key Set (JWKS) content.
 * <p>
 * This converter handles the transformation of HTTP String responses
 * containing JWKS JSON data into Jwks objects using DSL-JSON for
 * high-performance parsing.
 * <p>
 * The converter is thread-safe and reusable across multiple HTTP requests.
 * It follows the CUI converter pattern by extending StringContentConverter
 * and provides proper empty value semantics for error cases.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public class JwksHttpContentConverter extends StringContentConverter<Jwks> {

    private static final CuiLogger LOGGER = new CuiLogger(JwksHttpContentConverter.class);

    private final DslJson<Object> dslJson;

    /**
     * Creates a new JWKS content converter with specified parser configuration.
     *
     * @param parserConfig the parser configuration to use for DSL-JSON
     */
    public JwksHttpContentConverter(ParserConfig parserConfig) {
        super(StandardCharsets.UTF_8);
        this.dslJson = parserConfig.getDslJson();
    }

    @Override
    protected Optional<Jwks> convertString(String rawContent) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            LOGGER.debug("Empty or null JWKS content received");
            return Optional.empty();
        }

        try {
            byte[] bodyBytes = rawContent.getBytes(StandardCharsets.UTF_8);
            Jwks jwks = dslJson.deserialize(Jwks.class, bodyBytes, bodyBytes.length);

            if (jwks == null) {
                LOGGER.warn(WARN.JWKS_PARSE_NULL_RESULT);
                return Optional.empty();
            }

            LOGGER.debug("Successfully parsed JWKS with %s keys",
                    jwks.keys() != null ? jwks.keys().size() : 0);
            return Optional.of(jwks);

        } catch (IOException e) {
            LOGGER.warn(WARN.JWKS_PARSE_IO_ERROR, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public ContentType contentType() {
        return ContentType.APPLICATION_JSON;
    }
}