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

import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Objects;

/**
 * A refresh result failed an identity check of {@link RefreshResultVerifier} — the refreshed ID token is
 * inconsistent with the refreshed access token (OIDC Core §12.2), or the refreshed access token does not
 * name the subject the session is bound to — raised after the authorization server had already redeemed
 * the presented refresh token.
 * <p>
 * It is an {@link IllegalStateException} carrying the original refusal's message, with the refusal
 * itself as its cause: a {@code catch (IllegalStateException)} catches it and a documented
 * {@code @throws IllegalStateException} covers it. A caller that needs the pre-/post-redemption
 * distinction reads {@link #redemption()}, normally through {@link RefreshFlow#classify(Throwable)}.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public class RedeemedIdentityRefusalException extends IllegalStateException implements RedeemedRefreshFailure {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Deliberately {@code transient}: {@link RefreshRedemption#rotatedRefreshToken()} is live
     * credential material, and an exception is serializable, so leaving it in the serialized form
     * would carry a usable refresh token onto whatever channel serialized the throwable (session
     * replication, remoting, a serializing log appender). {@link #redemption()} degrades to the
     * fail-closed state instead, which quarantines without naming a token to revoke.
     */
    @Nullable
    private final transient RefreshRedemption redemption;

    /**
     * @param refusal    the identity refusal to carry forward; its message becomes this exception's
     *                   message and it becomes the cause; must not be {@code null}
     * @param redemption what the authorization server did to the presented refresh token before the
     *                   refusal was raised; must not be {@code null}
     */
    public RedeemedIdentityRefusalException(IllegalStateException refusal, RefreshRedemption redemption) {
        super(Objects.requireNonNull(refusal, "refusal must not be null").getMessage(), refusal);
        this.redemption = Objects.requireNonNull(redemption, "redemption must not be null");
    }

    /**
     * {@inheritDoc}
     * <p>
     * On a deserialized instance the carried state is gone by construction (see the field), so this
     * reports {@link RefreshRedemption#rotationUnknown()}: still redeemed, still fail-closed, but with
     * no successor to revoke. It never reports {@link RefreshRedemption#notRotated()} on that path,
     * because that is the one state which would leave the session holding the presented token.
     */
    @Override
    public RefreshRedemption redemption() {
        return redemption == null ? RefreshRedemption.rotationUnknown() : redemption;
    }
}
