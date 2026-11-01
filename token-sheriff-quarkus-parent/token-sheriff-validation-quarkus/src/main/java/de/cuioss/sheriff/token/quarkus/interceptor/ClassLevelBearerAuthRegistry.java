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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the class-level {@link BearerAuth} requirements the Quarkus build discovered in the application
 * index, keyed by the fully qualified name of the annotated class.
 * <p>
 * The table is filled once during static initialization by {@link BearerAuthRecorder} from build-time
 * index data, so it does not depend on runtime reflection over the bean class. Inside a native image
 * the class-level annotation of a bean is not reliably reachable through {@link Class#getAnnotation},
 * and the CDI interceptor bindings of a method that carries its own {@code @BearerAuth} do not include
 * the class-level one. This table is the source that closes both gaps for
 * {@link BearerTokenInterceptor}.
 */
final class ClassLevelBearerAuthRegistry {

    private static final Map<String, BearerAuthRequirement> REQUIREMENTS = new ConcurrentHashMap<>();

    private ClassLevelBearerAuthRegistry() {
    }

    /**
     * Records the class-level requirement declared on a class. A second registration for the same class
     * is combined with the first one, so the recorded requirement never shrinks.
     *
     * @param className   the fully qualified name of the annotated class
     * @param requirement the requirement its class-level {@code @BearerAuth} declares
     */
    static void register(String className, BearerAuthRequirement requirement) {
        REQUIREMENTS.merge(className, requirement, BearerAuthRequirement::union);
    }

    /**
     * Looks up the class-level requirement recorded for a class.
     *
     * @param className the fully qualified name of the class
     * @return the recorded requirement, or empty when the build found no class-level {@code @BearerAuth}
     * on that class
     */
    static Optional<BearerAuthRequirement> lookup(String className) {
        return Optional.ofNullable(REQUIREMENTS.get(className));
    }

    /**
     * Removes every recorded requirement. Intended for tests only.
     */
    static void clear() {
        REQUIREMENTS.clear();
    }
}
