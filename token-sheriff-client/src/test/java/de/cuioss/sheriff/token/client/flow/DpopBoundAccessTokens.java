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

import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.Base64;

/**
 * Produces DPoP-bound access tokens for flow tests: a pipeline-validatable JWT from a
 * {@link TestTokenHolder}, re-signed with the holder's own key after a {@code cnf} claim carrying the
 * given {@code jkt} (RFC 9449 §6.1) was added to its payload.
 * <p>
 * {@link TestTokenHolder} can only emit string, list and date claims, while {@code cnf} is a JSON
 * object, so the claim is spliced into the signed payload and the token is signed again. The result
 * passes the access-token validation pipeline exactly as the holder's own raw token does, and the
 * client's issuance-time binding check reads its {@code cnf.jkt}.
 */
final class DpopBoundAccessTokens {

    private DpopBoundAccessTokens() {
        // test utility — never instantiated
    }

    /**
     * @param holder the access-token holder whose claims and signing key the bound token reuses; must
     *               sign with RS256, the holder's default
     * @param jkt    the {@code cnf.jkt} thumbprint to bind the token to
     * @return a compact JWS carrying every claim of {@code holder} plus {@code "cnf":{"jkt":jkt}}
     */
    static String boundTo(TestTokenHolder holder, String jkt) {
        if (holder.getSigningAlgorithm() != InMemoryKeyMaterialHandler.Algorithm.RS256) {
            throw new IllegalArgumentException("only RS256 holders are supported, got " + holder.getSigningAlgorithm());
        }
        String[] parts = holder.getRawToken().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String boundPayload = "{\"cnf\":{\"jkt\":\"" + jkt + "\"}," + payload.substring(1);
        String signingInput = parts[0] + "." + base64Url(boundPayload.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + base64Url(sign(holder, signingInput));
    }

    private static byte[] sign(TestTokenHolder holder, String signingInput) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(InMemoryKeyMaterialHandler.getPrivateKey(holder.getSigningAlgorithm(), holder.getKeyId()));
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to re-sign the DPoP-bound access token", e);
        }
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
