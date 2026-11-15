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
 * HTTP authentication helpers of the {@code commons} base layer for the RFC 6750 {@code Bearer} scheme.
 * <p>
 * The package holds the framework-neutral parts of a protected resource's {@code Bearer} handling, so an
 * integration (the Quarkus producer, a gateway) shares one implementation instead of keeping a private
 * copy:
 * <ul>
 *   <li>{@link de.cuioss.sheriff.token.commons.http.BearerCredentials} - extracts the {@code Bearer} token
 *       from an {@code Authorization} field value and reports whether credentials are absent, malformed or
 *       present</li>
 *   <li>{@link de.cuioss.sheriff.token.commons.http.BearerChallenge} - builds the {@code WWW-Authenticate}
 *       value of an RFC 6750 §3.1 {@code insufficient_scope} challenge</li>
 * </ul>
 * The helpers return plain Java values; none of them depends on a web framework, produces a response or
 * decides an HTTP status. Mapping a result to a status code stays with the integration.
 * <p>
 * As part of the {@code commons} base layer, this package must not depend on the
 * {@code de.cuioss.sheriff.token.validation} packages (the ArchUnit boundary - see
 * {@code doc/commons/architecture.adoc}).
 *
 * @author Oliver Wolff
 * @since 1.0
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6750">RFC 6750</a>
 */
@NullMarked
package de.cuioss.sheriff.token.commons.http;

import org.jspecify.annotations.NullMarked;
