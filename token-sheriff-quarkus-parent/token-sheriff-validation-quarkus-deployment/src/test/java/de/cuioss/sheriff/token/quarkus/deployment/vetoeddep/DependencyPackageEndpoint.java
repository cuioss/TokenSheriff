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
package de.cuioss.sheriff.token.quarkus.deployment.vetoeddep;

import de.cuioss.sheriff.token.quarkus.annotation.BearerAuth;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A concrete class declaring a bean-defining annotation in a {@code @Vetoed} package that is deployed as a
 * dependency archive: it declares no {@code @Vetoed} of its own, so only the package annotation can veto it.
 * <p>
 * Its 33 class-level scopes plus its method's 32 are 65, over the bound of an {@code insufficient_scope}
 * challenge, while each set alone is within it. The scope check merges the two only for a bean class, so the
 * class is refused exactly when the check takes it for one.
 */
@ApplicationScoped
@BearerAuth(requiredScopes = {"c01", "c02", "c03", "c04", "c05", "c06", "c07", "c08", "c09", "c10", "c11", "c12",
        "c13", "c14", "c15", "c16", "c17", "c18", "c19", "c20", "c21", "c22", "c23", "c24", "c25", "c26", "c27",
        "c28", "c29", "c30", "c31", "c32", "c33"})
public class DependencyPackageEndpoint {

    /** @return a fixed value, so a resolved instance can be told apart from a missing one */
    @BearerAuth(requiredScopes = {"m01", "m02", "m03", "m04", "m05", "m06", "m07", "m08", "m09", "m10", "m11",
            "m12", "m13", "m14", "m15", "m16", "m17", "m18", "m19", "m20", "m21", "m22", "m23", "m24", "m25", "m26",
            "m27", "m28", "m29", "m30", "m31", "m32"})
    public String answer() {
        return "dependency-package";
    }
}
