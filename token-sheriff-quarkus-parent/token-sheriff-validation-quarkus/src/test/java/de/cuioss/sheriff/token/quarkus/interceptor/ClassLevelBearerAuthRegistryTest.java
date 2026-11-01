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
package de.cuioss.sheriff.token.quarkus.interceptor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ClassLevelBearerAuthRegistry} and the {@link BearerAuthRecorder} that fills it.
 */
class ClassLevelBearerAuthRegistryTest {

    private static final String CLASS_NAME = "com.example.SecuredResource";

    private final BearerAuthRecorder recorder = new BearerAuthRecorder();

    @BeforeEach
    @AfterEach
    void clearRegistry() {
        ClassLevelBearerAuthRegistry.clear();
    }

    @Test
    @DisplayName("Should return the requirement recorded for a class")
    void shouldReturnRecordedRequirement() {
        recorder.registerClassLevelRequirement(CLASS_NAME,
                new String[]{"read"}, new String[]{"user"}, new String[]{"team-a"});

        BearerAuthRequirement requirement = ClassLevelBearerAuthRegistry.lookup(CLASS_NAME).orElseThrow();

        assertEquals(Set.of("read"), requirement.requiredScopes());
        assertEquals(Set.of("user"), requirement.requiredRoles());
        assertEquals(Set.of("team-a"), requirement.requiredGroups());
    }

    @Test
    @DisplayName("Should return empty for a class without recorded requirement")
    void shouldReturnEmptyForUnknownClass() {
        recorder.registerClassLevelRequirement(CLASS_NAME, new String[0], new String[]{"user"}, new String[0]);

        assertTrue(ClassLevelBearerAuthRegistry.lookup("com.example.Other").isEmpty());
    }

    @Test
    @DisplayName("Should combine repeated registrations instead of replacing them")
    void shouldCombineRepeatedRegistrations() {
        recorder.registerClassLevelRequirement(CLASS_NAME, new String[]{"read"}, new String[]{"user"}, new String[0]);
        recorder.registerClassLevelRequirement(CLASS_NAME, new String[]{"write"}, new String[0], new String[0]);

        BearerAuthRequirement requirement = ClassLevelBearerAuthRegistry.lookup(CLASS_NAME).orElseThrow();

        assertEquals(Set.of("read", "write"), requirement.requiredScopes());
        assertEquals(Set.of("user"), requirement.requiredRoles());
    }

    @Test
    @DisplayName("Should tolerate duplicate values in a declaration")
    void shouldTolerateDuplicateValues() {
        recorder.registerClassLevelRequirement(CLASS_NAME,
                new String[]{"read", "read"}, new String[0], new String[0]);

        assertEquals(Set.of("read"), ClassLevelBearerAuthRegistry.lookup(CLASS_NAME).orElseThrow().requiredScopes());
    }
}
