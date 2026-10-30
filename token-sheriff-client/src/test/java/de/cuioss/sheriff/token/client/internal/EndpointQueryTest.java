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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the fragment-aware query joining of {@link EndpointQuery} (C-4).
 */
@DisplayName("EndpointQuery fragment-aware query joining")
class EndpointQueryTest {

    private static final String QUERY = "state=s1&nonce=n1";

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "https://as.example.com/authorize | https://as.example.com/authorize?state=s1&nonce=n1",
            "https://as.example.com/authorize?a=b | https://as.example.com/authorize?a=b&state=s1&nonce=n1",
            "https://as.example.com/authorize#frag | https://as.example.com/authorize?state=s1&nonce=n1#frag",
            "https://as.example.com/authorize?a=b#frag | https://as.example.com/authorize?a=b&state=s1&nonce=n1#frag",
            "https://as.example.com/authorize#frag?x=y | https://as.example.com/authorize?state=s1&nonce=n1#frag?x=y",
            "https://as.example.com/authorize# | https://as.example.com/authorize?state=s1&nonce=n1#"})
    @DisplayName("Should insert the query before any fragment and choose the separator from the part before it")
    void shouldInsertQueryBeforeFragment(String endpoint, String expected) {
        assertEquals(expected, EndpointQuery.append(endpoint, QUERY));
    }
}
