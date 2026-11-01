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

import de.cuioss.sheriff.token.quarkus.annotation.BearerAuth;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Quarkus recorder that hands the class-level {@link BearerAuth} requirements found in the application
 * index at build time to {@link ClassLevelBearerAuthRegistry} at runtime.
 * <p>
 * The deployment processor calls {@link #registerClassLevelRequirement} once per class that carries a
 * class-level {@code @BearerAuth}. The recorded calls replay during static initialization, before any
 * request reaches {@link BearerTokenInterceptor}.
 */
@Recorder
public class BearerAuthRecorder {

    /**
     * Records the class-level requirement of one annotated class.
     *
     * @param className      the fully qualified name of the class carrying the class-level annotation
     * @param requiredScopes the scopes the annotation declares
     * @param requiredRoles  the roles the annotation declares
     * @param requiredGroups the groups the annotation declares
     */
    public void registerClassLevelRequirement(String className, String[] requiredScopes, String[] requiredRoles,
            String[] requiredGroups) {
        ClassLevelBearerAuthRegistry.register(className,
                BearerAuthRequirement.of(requiredScopes, requiredRoles, requiredGroups));
    }
}
