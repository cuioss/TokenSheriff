/*
 * Copyright © 2022 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.token.client.internal;

import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;
import lombok.experimental.UtilityClass;

/**
 * DSL-style {@link LogRecord} catalogue for the Token-Sheriff client engine.
 * <p>
 * Structured {@code INFO} / {@code WARN} / {@code ERROR} messages carry the
 * {@code TokenSheriffClient} prefix and a stable numeric identifier, so they are greppable and
 * assertable via {@code LogAsserts}. {@code DEBUG} / {@code TRACE} diagnostics use the logger
 * directly and are not catalogued here.
 *
 * @since 1.0
 */
@UtilityClass
public final class ClientLogMessages {

    private static final String PREFIX = "TokenSheriffClient";

    /**
     * Info-level messages (INFO range 1-99).
     */
    @UtilityClass
    public static final class INFO {

        /** Provider metadata successfully resolved for the given issuer. */
        public static final LogRecord DISCOVERY_RESOLVED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(1)
                .template("Resolved OIDC provider metadata for issuer '%s'")
                .build();
    }

    /**
     * Warn-level messages (WARN range 100-199).
     */
    @UtilityClass
    public static final class WARN {

        /** A non-TLS issuer was rejected for discovery. */
        public static final LogRecord NON_TLS_ISSUER = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(100)
                .template("Rejecting non-TLS issuer '%s' for discovery; enable allowInsecureHttp only for local test setups")
                .build();

        /** The discovery request returned a non-success HTTP status. */
        public static final LogRecord DISCOVERY_STATUS = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(101)
                .template("Discovery for issuer '%s' returned unexpected HTTP status %s")
                .build();

        /** The AS does not advertise the PKCE 'S256' code-challenge method. */
        public static final LogRecord S256_NOT_ADVERTISED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(102)
                .template("Authorization server '%s' does not advertise PKCE 'S256'; interactive authorization_code flows will be refused")
                .build();

        /** The discovery document's issuer does not match the configured issuer. */
        public static final LogRecord ISSUER_MISMATCH = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(103)
                .template("Discovery document issuer '%s' does not match configured issuer '%s'")
                .build();
    }

    /**
     * Error-level messages (ERROR range 200-299).
     */
    @UtilityClass
    public static final class ERROR {

        /** OIDC discovery failed for the given issuer. */
        public static final LogRecord DISCOVERY_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(200)
                .template("OIDC discovery failed for issuer '%s': %s")
                .build();
    }
}
