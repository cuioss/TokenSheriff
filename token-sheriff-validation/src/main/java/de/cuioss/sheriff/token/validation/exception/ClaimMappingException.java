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
package de.cuioss.sheriff.token.validation.exception;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter.EventType;
import lombok.Getter;

import java.io.Serial;
import java.util.Objects;

/**
 * Thrown when a claim mapper fails on a malformed claim value, for example a string-typed {@code exp}.
 * <p>
 * It is a {@link TokenValidationException} with the event type
 * {@link EventType#MISSING_CLAIM}, so a caller that handles {@code TokenValidationException} handles it
 * unchanged. It additionally names the claim whose value could not be mapped, so a caller that runs its
 * own per-claim checks, such as the logout-token validator, can attribute the refusal to that claim. A
 * rejection a claim mapper raises itself as a {@code TokenValidationException} is not wrapped into this
 * type.
 *
 * @since 1.0
 */
public class ClaimMappingException extends TokenValidationException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The name of the claim whose value could not be mapped.
     */
    @Getter
    private final String claimName;

    /**
     * Creates the exception.
     *
     * @param claimName the name of the claim whose value could not be mapped
     * @param message   the detail message
     * @param cause     the failure of the claim mapper
     */
    public ClaimMappingException(String claimName, String message, Throwable cause) {
        super(EventType.MISSING_CLAIM, message, cause);
        this.claimName = Objects.requireNonNull(claimName, "claimName must not be null");
    }
}
