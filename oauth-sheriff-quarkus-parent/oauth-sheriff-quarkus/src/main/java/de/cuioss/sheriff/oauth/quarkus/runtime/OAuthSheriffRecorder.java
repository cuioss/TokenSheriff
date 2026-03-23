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
package de.cuioss.sheriff.oauth.quarkus.runtime;

import io.quarkus.runtime.annotations.Recorder;

/**
 * Recorder for the OAuth Sheriff Quarkus extension.
 * <p>
 * This class is currently empty as all runtime initialization is handled by CDI.
 * Health checks are automatically discovered by Quarkus through their annotations
 * ({@code @ApplicationScoped}, {@code @Readiness}, {@code @Liveness}).
 * <p>
 * If future runtime initialization is needed, methods can be added here and called
 * from the deployment processor.
 *
 * @since 1.0
 */
@Recorder
public class OAuthSheriffRecorder {
    // No initialization needed - all components are managed by CDI
}
