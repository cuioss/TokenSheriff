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
/**
 * Test fixture package vetoed as a whole and deployed as a dependency archive rather than the application root:
 * {@code @Vetoed} on the package makes every type in it no bean, as the CDI specification states for a
 * package-level veto. {@code BearerScopeValidationDependencyVetoedPackageBootTest} shows how ArC treats a
 * concrete class in it when the package sits in a dependency, and
 * {@code BearerScopeValidationVetoedPackageBuildStepTest} pins which index the scope check reads this
 * {@code package-info} from.
 */
@Vetoed
package de.cuioss.sheriff.token.quarkus.deployment.vetoeddep;

import jakarta.enterprise.inject.Vetoed;
