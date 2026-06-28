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
/**
 * Provides classes for handling OpenID Connect Discovery, specifically the
 * retrieval and processing of OIDC Provider Configuration Information from
 * well-known endpoints.
 * <p>
 * The main components include:
 * <ul>
 *   <li>{@link de.cuioss.sheriff.token.validation.wellknown.WellKnownConfig} - Configuration for well-known endpoint discovery</li>
 *   <li>{@link de.cuioss.sheriff.token.validation.json.WellKnownResult} - Data structure for OIDC discovery document</li>
 *   <li>Support classes for HTTP operations, JSON parsing, and endpoint resolution</li>
 * </ul>
 * <p>
 * This package provides:
 * <ul>
 *   <li>Simple well-known configuration loading</li>
 *   <li>Configurable HTTP timeouts and SSL settings</li>
 *   <li>Direct integration with ResilientHttpHandler for caching</li>
 *   <li>DSL-JSON based parsing for optimal performance</li>
 * </ul>
 */
package de.cuioss.sheriff.token.validation.wellknown;
