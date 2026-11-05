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
package de.cuioss.sheriff.token.validation.jwks;

import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for {@link LoaderStatus} enum.
 * <p>
 * Only the overridden {@code toString()} is tested: it yields the lowercase values that health
 * output reports.
 *
 * @author Oliver Wolff
 */
@EnableGeneratorController
class LoaderStatusTest {

    @Test
    void shouldProvideCorrectStringRepresentation() {
        assertEquals("ok", LoaderStatus.OK.toString());
        assertEquals("error", LoaderStatus.ERROR.toString());
        assertEquals("loading", LoaderStatus.LOADING.toString());
        assertEquals("undefined", LoaderStatus.UNDEFINED.toString());
    }
}
