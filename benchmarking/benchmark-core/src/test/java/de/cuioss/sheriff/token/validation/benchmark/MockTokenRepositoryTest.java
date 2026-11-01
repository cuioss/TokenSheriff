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
package de.cuioss.sheriff.token.validation.benchmark;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MockTokenRepository token pool lookup")
class MockTokenRepositoryTest {

    private static MockTokenRepository repository;
    private static String[] pool;

    @BeforeAll
    static void createRepository() {
        repository = new MockTokenRepository(MockTokenRepository.Config.builder()
                .tokenPoolSize(6)
                .build());
        pool = repository.getTokenPool();
    }

    @Test
    @DisplayName("Should wrap an index beyond the pool size")
    void shouldWrapIndexBeyondPoolSize() {
        assertEquals(pool[1], repository.getToken(pool.length + 1));
    }

    @Test
    @DisplayName("Should wrap a negative index to the end of the pool")
    void shouldWrapNegativeIndex() {
        assertEquals(pool[pool.length - 1], repository.getToken(-1));
    }

    @Test
    @DisplayName("Should return a pool entry for an overflowed rotation counter")
    void shouldHandleOverflowedRotationCounter() {
        String token = assertDoesNotThrow(() -> repository.getToken(Integer.MIN_VALUE));

        assertEquals(pool[Math.floorMod(Integer.MIN_VALUE, pool.length)], token);
    }
}
