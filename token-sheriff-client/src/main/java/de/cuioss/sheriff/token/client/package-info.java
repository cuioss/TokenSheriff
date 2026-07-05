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
 * Root package of the framework-agnostic Token-Sheriff OIDC/OAuth <em>client</em> engine.
 * <p>
 * This module is the client-side counterpart to {@code token-sheriff-validation}. Where validation
 * consumes and verifies inbound tokens, the client engine drives the outbound OIDC/OAuth flows —
 * token retrieval (RFC 6749), userinfo (OIDC Core), token revocation (RFC 7009), introspection
 * (RFC 7662), RP-initiated end-session and pushed authorization requests (RFC 9126).
 * <p>
 * It is pure Java with no framework dependency (see the module-architecture decision:
 * {@code doc/oidc/decision-oidc-module.adoc}) so it can be wired into any runtime — the Quarkus
 * extension {@code token-sheriff-client-quarkus} is one such binding; a CDI/RESTEasy portal adapter
 * is another. The engine builds on the {@code commons} base layer (transport, error model, events,
 * metrics) that ships inside {@code token-sheriff-validation}; it adds flows, not HTTP plumbing —
 * outbound bytes travel through {@code de.cuioss.sheriff.token.commons.transport}. Because it
 * validates the tokens it retrieves, it depends on {@code token-sheriff-validation} and gets the
 * {@code commons} base layer transitively.
 * <p>
 * <strong>Status:</strong> wired, buildable skeleton (Plan 05). Functional content is added per
 * {@code doc/client/} and {@code doc/oidc/04-client-spec/} in later increments (Plan 06); no client
 * behaviour lives here yet.
 *
 * @author Oliver Wolff
 */
package de.cuioss.sheriff.token.client;
