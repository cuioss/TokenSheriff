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
 * OpenID Connect / OAuth 2.0 authorization-server discovery for the client engine.
 * <p>
 * {@link de.cuioss.sheriff.token.client.discovery.DiscoveryResolver} fetches the AS
 * {@code .well-known/openid-configuration} document over the commons-blessed cui-http
 * {@code HttpHandler} (TLS enforced) and maps it into an immutable
 * {@link de.cuioss.sheriff.token.client.discovery.ProviderMetadata} — the resolved endpoint URLs
 * plus the capability flags (PKCE {@code S256}, PAR, DPoP, end-session) the flows branch on. The
 * package adds no transport hardening of its own; it inherits the handler's TLS/SSRF posture.
 * <p>
 * {@link de.cuioss.sheriff.token.client.discovery.CachingDiscoveryResolver} is the opt-in cache in
 * front of the resolver. It serves the last resolved document for a caller-stated time to live (there
 * is no default), then refreshes it on demand — single-flight, on the caller's thread, with no
 * background thread — through the unchanged resolver, so every refreshed document passes the same
 * checks and a refused document never replaces the cached one. Its stale policy serves the last
 * resolved document while refreshes fail until {@code lastSuccess + ttl + gracePeriod} and fails
 * closed after that, waiting at least the configured minimum interval between a failed refresh and
 * the next attempt. Its state is reported without I/O as an immutable
 * {@link de.cuioss.sheriff.token.client.discovery.DiscoveryCacheStatus} and, for health checks, as a
 * {@link de.cuioss.sheriff.token.commons.transport.LoaderStatus}.
 *
 * @since 1.0
 */
@NullMarked
package de.cuioss.sheriff.token.client.discovery;

import org.jspecify.annotations.NullMarked;
