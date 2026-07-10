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
package de.cuioss.sheriff.token.client.token;

import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimValue;
import de.cuioss.sheriff.token.validation.domain.context.IdTokenRequest;
import de.cuioss.sheriff.token.validation.domain.token.IdTokenContent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * Bridges an OpenID Connect ID token into the {@code token-sheriff-validation} pipeline and binds it
 * to the flow {@code nonce} ({@code CLIENT-2} / {@code CLIENT-15}).
 * <p>
 * The ID token is validated through the same multi-issuer pipeline that guards inbound tokens
 * (signature, {@code iss}, {@code aud}, {@code exp} / {@code iat}), and the {@code nonce} claim is
 * then compared, in constant time, against the {@code nonce} the client generated for this flow. A
 * mismatch — the signature of a replayed or injected ID token — fails closed. The client never
 * trusts an ID token on the strength of a successful HTTP exchange alone.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation">OIDC Core §3.1.3.7</a>
 */
public class IdTokenValidationBridge {

    private static final String NONCE_CLAIM = "nonce";

    private final TokenValidator tokenValidator;

    /**
     * @param tokenValidator the configured multi-issuer validator; must not be {@code null}
     */
    public IdTokenValidationBridge(TokenValidator tokenValidator) {
        this.tokenValidator = Objects.requireNonNull(tokenValidator, "tokenValidator must not be null");
    }

    /**
     * Validates an ID token and asserts its {@code nonce} matches the expected flow nonce.
     *
     * @param idToken       the raw ID token string; must not be {@code null}
     * @param expectedNonce the {@code nonce} the client generated for this flow; must not be
     *                      {@code null}
     * @return the validated ID token content
     * @throws de.cuioss.sheriff.token.validation.exception.TokenValidationException if the token
     *         fails pipeline validation
     * @throws IllegalStateException if the ID token carries no {@code nonce} or it does not match
     */
    public IdTokenContent validateIdToken(String idToken, String expectedNonce) {
        Objects.requireNonNull(idToken, "idToken must not be null");
        Objects.requireNonNull(expectedNonce, "expectedNonce must not be null");

        IdTokenContent content = tokenValidator.createIdToken(IdTokenRequest.of(idToken));

        ClaimValue nonceClaim = content.getClaims().get(NONCE_CLAIM);
        String actualNonce = nonceClaim == null ? null : nonceClaim.getOriginalString();
        if (!noncesMatch(expectedNonce, actualNonce)) {
            throw new IllegalStateException("ID token 'nonce' does not match the flow nonce");
        }
        return content;
    }

    private static boolean noncesMatch(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
