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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Predicate table for the shared back-channel {@code Content-Type} check {@link JsonResponseMediaType}.
 */
@DisplayName("JsonResponseMediaType Content-Type predicate")
class JsonResponseMediaTypeTest {

    private static final String APPLICATION_JWT = "application/jwt";

    private static HttpHeaders contentType(String... values) {
        return HttpHeaders.of(Map.of(JsonResponseMediaType.CONTENT_TYPE, List.of(values)), (name, value) -> true);
    }

    private static HttpHeaders noHeaders() {
        return HttpHeaders.of(Map.of(), (name, value) -> true);
    }

    static Stream<Arguments> jsonTable() {
        return Stream.of(
                Arguments.of("plain application/json", List.of("application/json"), true),
                Arguments.of("upper case", List.of("APPLICATION/JSON"), true),
                Arguments.of("charset parameter", List.of("application/json; charset=UTF-8"), true),
                Arguments.of("padded with whitespace", List.of("  application/json  ;charset=utf-8"), true),
                Arguments.of("two agreeing lines", List.of("application/json", "application/json;charset=utf-8"),
                        true),
                Arguments.of("text/plain", List.of("text/plain"), false),
                Arguments.of("structured-syntax suffix", List.of("application/jrd+json"), false),
                Arguments.of("application/jwt", List.of(APPLICATION_JWT), false),
                Arguments.of("empty value", List.of(""), false),
                Arguments.of("media type as parameter only", List.of("text/html; x=application/json"), false),
                Arguments.of("conflicting second line", List.of("application/json", "text/html"), false),
                Arguments.of("conflicting first line", List.of("text/html", "application/json"), false),
                // HttpHeaders.of trims each value, so the control characters sit inside the value.
                Arguments.of("carriage return", List.of("application/json;\rcharset=utf-8"), false),
                Arguments.of("line feed in a parameter", List.of("application/json; charset=utf-8\nX-Injected: 1"),
                        false),
                Arguments.of("obs-fold continuation", List.of("application/json;\r\n charset=utf-8"), false),
                Arguments.of("NUL", List.of("application/json;\0charset=utf-8"), false));
    }

    @ParameterizedTest(name = "{0} -> {2}")
    @MethodSource("jsonTable")
    @DisplayName("Should judge isJson by every Content-Type line")
    void shouldJudgeIsJson(String label, List<String> values, boolean expected) {
        HttpHeaders headers = contentType(values.toArray(String[]::new));

        assertEquals(expected, JsonResponseMediaType.isJson(headers), label);
    }

    @Test
    @DisplayName("Should refuse a response with no Content-Type")
    void shouldRefuseAbsentContentType() {
        HttpHeaders headers = noHeaders();

        assertAll("absent Content-Type",
                () -> assertFalse(JsonResponseMediaType.isJson(headers), "isJson"),
                () -> assertFalse(JsonResponseMediaType.carriesOnly(headers, APPLICATION_JWT), "carriesOnly jwt"));
    }

    @Test
    @DisplayName("Should match the header name case-insensitively")
    void shouldMatchHeaderNameCaseInsensitively() {
        HttpHeaders headers = HttpHeaders.of(Map.of("content-type", List.of("application/json")),
                (name, value) -> true);

        assertTrue(JsonResponseMediaType.isJson(headers));
    }

    @Test
    @DisplayName("Should apply the same rule for another expected media type")
    void shouldApplySameRuleForOtherMediaType() {
        assertAll("carriesOnly application/jwt",
                () -> assertTrue(JsonResponseMediaType.carriesOnly(contentType(APPLICATION_JWT), APPLICATION_JWT),
                        "plain jwt"),
                () -> assertTrue(JsonResponseMediaType.carriesOnly(
                        contentType("Application/JWT; charset=utf-8"), APPLICATION_JWT), "jwt with parameter"),
                () -> assertFalse(JsonResponseMediaType.carriesOnly(
                        contentType(APPLICATION_JWT, "application/json"), APPLICATION_JWT), "mixed jwt and json"),
                () -> assertFalse(JsonResponseMediaType.carriesOnly(contentType("application/json"), APPLICATION_JWT),
                        "json only"),
                () -> assertFalse(JsonResponseMediaType.carriesOnly(
                        contentType("application/jwt;\ncharset=utf-8"), APPLICATION_JWT), "line feed"));
    }
}
