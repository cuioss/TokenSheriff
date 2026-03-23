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
package de.cuioss.sheriff.oauth.core.exception;

import java.io.Serial;

/**
 * Exception thrown when internal cache operations fail unexpectedly.
 * <p>
 * This exception indicates an internal error in the caching layer,
 * not a validation failure of the token itself.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public class InternalCacheException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new InternalCacheException with the specified detail message.
     *
     * @param message the detail message
     */
    public InternalCacheException(String message) {
        super(message);
    }

    /**
     * Constructs a new InternalCacheException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public InternalCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}