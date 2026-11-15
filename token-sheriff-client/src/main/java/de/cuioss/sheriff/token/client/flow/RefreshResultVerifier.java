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

import de.cuioss.sheriff.token.client.internal.ClientLogMessages;
import de.cuioss.sheriff.token.client.token.IdTokenValidationBridge;
import de.cuioss.sheriff.token.client.token.RotationResult;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import de.cuioss.sheriff.token.validation.domain.token.IdTokenContent;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Optional;

/**
 * Verifies the identity a successful {@code refresh_token} exchange returned, before the caller stores
 * any of it (OIDC Core §12.2).
 * <p>
 * {@link RefreshFlow#refresh(de.cuioss.sheriff.token.client.discovery.ProviderMetadata, String)}
 * validates the refreshed access token but deliberately leaves the refreshed ID token, and the question
 * of whose session the refresh belongs to, to its caller. Every caller that stores a refresh result —
 * {@link de.cuioss.sheriff.token.client.lifecycle.TokenLifecycleManager} or a gateway driving the flow
 * itself — owes the same checks, so they are published here once rather than re-implemented per caller.
 * <p>
 * {@link #verify(RotationResult, IdTokenValidationBridge, String)} runs three steps, in this order, and
 * stops at the first refusal:
 * <ol>
 *   <li><strong>ID-token validation.</strong> When the exchange returned a raw ID token, it is validated
 *       through {@link IdTokenValidationBridge#validateRefreshedIdToken(String)} (signature, {@code iss},
 *       {@code aud}, {@code exp} / {@code iat}; no {@code nonce}, per §12.2). An absent or blank ID token
 *       skips this step and step 2.</li>
 *   <li><strong>§12.2 consistency.</strong> The validated ID token is refused when its {@code iss}
 *       differs from the refreshed access token's issuer, or when the access token carries a
 *       {@code sub} that differs from the ID token's {@code sub}. An access token without a {@code sub}
 *       is not cross-checked, because {@code sub} is optional on an access token.</li>
 *   <li><strong>Subject binding.</strong> When the caller names the subject its session is bound to,
 *       the refresh is refused unless the refreshed access token carries a {@code sub} equal to it. The
 *       comparison is constant-time over the UTF-8 octets. A {@code null} bound subject skips this
 *       step.</li>
 * </ol>
 * <p>
 * <strong>Every refusal is raised after redemption.</strong> The authorization server has already
 * answered, so it may already have rotated the presented refresh token. Each refusal therefore carries the
 * {@link RefreshRedemption} of the exchange — {@link RefreshRedemption#rotated(String)} with the rotated
 * successor when {@link RotationResult#rotated()}, otherwise {@link RefreshRedemption#notRotated()} — as a
 * {@link RedeemedRefreshFailure}: a validation refusal as {@link RedeemedValidationRefusalException}
 * (a {@link TokenValidationException}, cause preserved), a consistency or binding refusal as
 * {@link RedeemedIdentityRefusalException} (an {@link IllegalStateException}). Callers route a refusal
 * through {@link RefreshFlow#classify(Throwable)}, which classifies both as
 * {@link RefreshFailureClassification.Kind#REDEEMED} by the redemption they carry, and decide from that
 * whether the session must be quarantined.
 * <p>
 * The verifier is a pure check. It never stores, revokes or clears anything, never touches a refresh
 * token's storage or its {@link de.cuioss.sheriff.token.client.token.RefreshTokenFamily}, and logs no
 * token and no subject: only the §12.2 inconsistency is logged, once, at {@code WARN}
 * ({@link ClientLogMessages.WARN#REFRESHED_ID_TOKEN_INCONSISTENT}); a binding refusal is logged at
 * {@code DEBUG} only.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#RefreshTokenResponse">OIDC Core §12.2</a>
 */
public final class RefreshResultVerifier {

    private static final CuiLogger LOGGER = new CuiLogger(RefreshResultVerifier.class);

    private static final String INCONSISTENT_ID_TOKEN_MESSAGE =
            "refreshed ID token is inconsistent with the refreshed access token (OIDC Core §12.2)";

    private static final String SUBJECT_MISMATCH_MESSAGE =
            "refreshed access token does not name the subject the session is bound to";

    private RefreshResultVerifier() {
        // utility class
    }

    /**
     * Verifies a refresh result in the three steps documented on the class and returns the ID token to
     * carry forward.
     *
     * @param rotation                the result of a successful refresh exchange; must not be
     *                                {@code null}
     * @param idTokenValidationBridge the bridge validating a refreshed ID token; must not be
     *                                {@code null}
     * @param boundSubject            the subject the caller's session is bound to, or {@code null} when it
     *                                is bound to none and the binding step is skipped
     * @return the verified raw ID token, or {@code null} when the exchange issued none (absent or blank)
     * @throws RedeemedValidationRefusalException if the refreshed ID token fails pipeline validation;
     *                                            carries the original refusal as its cause
     * @throws RedeemedIdentityRefusalException   if the refreshed ID token is inconsistent with the
     *                                            refreshed access token (OIDC Core §12.2), or if
     *                                            {@code boundSubject} is non-{@code null} and the
     *                                            refreshed access token carries no {@code sub} or a
     *                                            different one
     */
    @Nullable
    public static String verify(RotationResult rotation, IdTokenValidationBridge idTokenValidationBridge,
            @Nullable String boundSubject) {
        Objects.requireNonNull(rotation, "rotation must not be null");
        Objects.requireNonNull(idTokenValidationBridge, "idTokenValidationBridge must not be null");
        RefreshRedemption redemption = redemptionOf(rotation);
        AccessTokenContent accessToken = rotation.accessToken();

        String rawIdToken = rotation.idToken();
        boolean idTokenIssued = rawIdToken != null && !rawIdToken.isBlank();
        if (idTokenIssued) {
            IdTokenContent idToken;
            try {
                idToken = idTokenValidationBridge.validateRefreshedIdToken(rawIdToken);
            } catch (TokenValidationException refused) {
                throw new RedeemedValidationRefusalException(refused, redemption);
            }
            if (!consistentWithAccessToken(accessToken, idToken)) {
                LOGGER.warn(ClientLogMessages.WARN.REFRESHED_ID_TOKEN_INCONSISTENT);
                throw new RedeemedIdentityRefusalException(
                        new IllegalStateException(INCONSISTENT_ID_TOKEN_MESSAGE), redemption);
            }
        }

        if (boundSubject != null && !subjectMatches(boundSubject, accessToken.getSubject().orElse(null))) {
            LOGGER.debug("Refusing refresh result: the refreshed access token does not name the bound subject");
            throw new RedeemedIdentityRefusalException(new IllegalStateException(SUBJECT_MISMATCH_MESSAGE),
                    redemption);
        }
        return idTokenIssued ? rawIdToken : null;
    }

    private static RefreshRedemption redemptionOf(RotationResult rotation) {
        return rotation.rotated()
                ? RefreshRedemption.rotated(rotation.refreshToken())
                : RefreshRedemption.notRotated();
    }

    private static boolean consistentWithAccessToken(AccessTokenContent accessToken, IdTokenContent idToken) {
        if (!accessToken.getIssuer().equals(idToken.getIssuer())) {
            return false;
        }
        // 'sub' is mandatory on an OIDC ID token but optional on an access token: cross-check it only
        // when the access token actually carries a subject (§12.2 "the access token's sub").
        Optional<String> accessSubject = accessToken.getSubject();
        return accessSubject.isEmpty() || accessSubject.equals(idToken.getSubject());
    }

    /**
     * Constant-time comparison of the bound subject with the refreshed one; an absent refreshed subject
     * never matches (fail closed).
     */
    private static boolean subjectMatches(String boundSubject, @Nullable String refreshedSubject) {
        if (refreshedSubject == null) {
            return false;
        }
        return MessageDigest.isEqual(boundSubject.getBytes(StandardCharsets.UTF_8),
                refreshedSubject.getBytes(StandardCharsets.UTF_8));
    }
}
