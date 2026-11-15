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
package de.cuioss.sheriff.token.client.flow;

/**
 * Implemented by every exception {@link RefreshFlow#refresh(
 * de.cuioss.sheriff.token.client.discovery.ProviderMetadata, String)} raises <em>after</em> the
 * authorization server has answered and therefore after it has redeemed the presented refresh token,
 * and by every refusal {@link RefreshResultVerifier} raises over the result of such an exchange — a
 * refreshed ID token that fails validation ({@link RedeemedValidationRefusalException}), or one that is
 * inconsistent with the refreshed access token or a refreshed access token that does not name the bound
 * subject ({@link RedeemedIdentityRefusalException}). Those checks run after the server answered too, so
 * they owe the caller the same redemption state.
 * <p>
 * <strong>Why the signal rides on the exception.</strong> Several client-side checks run after the
 * {@code 2xx} — access-token validation and the strict scope-reconciliation refusal — and each of them
 * throws before a {@link de.cuioss.sheriff.token.client.token.RotationResult} exists. A caller holding
 * a session therefore cannot read rotation off a result and cannot tell from the exception type alone
 * whether the token it still has stored is alive or dead. Carrying the state on the thrown exception
 * closes that gap without a second, competing entry point into the flow: {@code refresh} stays the one
 * overridable method, so a subclass that intercepts it stays on the production path.
 * <p>
 * <strong>Absence is as meaningful as presence — but it is not one state.</strong> A failure raised
 * <em>before</em> the server processed the request — a connection failure, a DNS failure, an
 * SSRF-blocked target, or a non-success HTTP status — does not implement this interface. That absence
 * is what stops a transient network fault from being mistaken for a burned credential and destroying a
 * working session. It does not, however, mean the presented token is necessarily alive: one shape
 * inside it, a {@code 4xx} whose RFC 6749 §5.2 body names {@code invalid_grant}, is the server
 * declaring this credential dead without ever redeeming it, and is carried by
 * {@link CredentialRejectedException}. Callers must therefore classify through
 * {@link RefreshFlow#classify(Throwable)} rather than by listing exception types, so all three
 * situations — {@link RefreshFailureClassification.Kind#PRE_REDEMPTION},
 * {@link RefreshFailureClassification.Kind#CREDENTIAL_REJECTED} and
 * {@link RefreshFailureClassification.Kind#REDEEMED}, the last of which folds in the
 * unparseable-success case ({@link RedeemedResponseException}) — are decided in one place.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public interface RedeemedRefreshFailure {

    /**
     * @return what the authorization server did to the presented refresh token before this refusal was
     *         raised; never {@code null}
     */
    RefreshRedemption redemption();
}
