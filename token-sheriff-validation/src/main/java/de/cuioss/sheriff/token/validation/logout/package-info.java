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
 * Validates logout tokens of OpenID Connect Back-Channel Logout 1.0.
 * <p>
 * A logout token is a signed JWT an OpenID Provider sends to a relying party to end a session. Its
 * validation follows
 * <a href="https://openid.net/specs/openid-connect-backchannel-1_0.html#LogoutToken">section 2.4</a> and
 * <a href="https://openid.net/specs/openid-connect-backchannel-1_0.html#Validation">section 2.6</a> of the
 * specification. The validator reaches no verdict through a {@code TokenType}: it verifies the signature with
 * {@link de.cuioss.sheriff.token.validation.TokenValidator#verifySignature(String)} and checks the native JSON
 * claims of the returned
 * {@link de.cuioss.sheriff.token.validation.domain.token.SignatureVerifiedToken} itself.
 * <p>
 * The checks run in this fixed order, and the first one that fails names the refusal:
 * <ol>
 *   <li>the signature, issuer and header checks of the signature-only entry</li>
 *   <li>a present {@code typ} header is {@code logout+jwt}, with or without the {@code application/} prefix and
 *   compared case-insensitively; an absent {@code typ} is accepted</li>
 *   <li>{@code iss} equals the expected issuer</li>
 *   <li>{@code aud}, a string or an array, contains the expected audience</li>
 *   <li>{@code iat} is a number within the freshness window around now</li>
 *   <li>{@code exp} is a number after now; an {@code exp} exactly now is refused</li>
 *   <li>{@code events} is a JSON object whose
 *   {@code http://schemas.openid.net/event/backchannel-logout} member is a JSON object</li>
 *   <li>{@code nonce} is absent, not even present as a JSON {@code null}</li>
 *   <li>{@code sub} and/or {@code sid} is present, and each of them that is present is a non-blank string</li>
 *   <li>{@code jti} is present as a non-blank string</li>
 *   <li>the {@code jti} has not been seen within its retention, and the replay guard has room for it</li>
 * </ol>
 * <p>
 * The {@code events} and absent-{@code nonce} checks keep an ID token from being accepted as a logout token.
 * The replay guard is consulted last, so a token refused by any other check never takes guard capacity.
 * <p>
 * Key components:
 * <ul>
 *   <li>{@link de.cuioss.sheriff.token.validation.logout.LogoutTokenValidator} - Runs the checks over one raw
 *   logout token</li>
 *   <li>{@link de.cuioss.sheriff.token.validation.logout.LogoutTokenValidationResult} - The accepted claims or the
 *   one failed check</li>
 *   <li>{@link de.cuioss.sheriff.token.validation.logout.LogoutTokenCheck} - The bounded set of checks a refusal
 *   names</li>
 *   <li>{@code LogoutTokenReplayGuard} - A bounded, fail-closed store of seen {@code jti} digests, internal to the
 *   package; each validator builds its own from its configured capacity and its own clock, so the guard and the
 *   {@code iat} check always read the same time source</li>
 * </ul>
 * <p>
 * The package does not log and puts no remote text into a result; the caller logs or meters the returned
 * {@link de.cuioss.sheriff.token.validation.logout.LogoutTokenCheck}. The HTTP receiver endpoint that accepts
 * the logout request and the termination of the session it names stay with the consuming application.
 * <p>
 * For the decisions behind this package, see
 * <a href="https://github.com/cuioss/TokenSheriff/tree/main/doc/adr">ADR-0029</a>.
 *
 * @since 1.0
 * @see <a href="https://openid.net/specs/openid-connect-backchannel-1_0.html">OpenID Connect Back-Channel Logout 1.0</a>
 */
package de.cuioss.sheriff.token.validation.logout;
