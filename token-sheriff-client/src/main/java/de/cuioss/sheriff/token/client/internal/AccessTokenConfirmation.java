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
package de.cuioss.sheriff.token.client.internal;

import com.dslplatform.json.CompiledJson;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A decode-only view of the {@code cnf} (confirmation) claim of a JWT access token, mapped down to
 * the single member the client acts on: the DPoP proof-key thumbprint {@code cnf.jkt} (RFC 9449 §6.1).
 * <p>
 * The client uses this view for one purpose only — the issuance-time sender-constraint binding check
 * at the token-endpoint seam, which compares the {@code cnf.jkt} the authorization server put into a
 * freshly issued DPoP-bound access token against the thumbprint of the proof key the client holds.
 * Decoding a payload into this type is <strong>never a validation of the token</strong>: no signature,
 * issuer, audience or lifetime is checked here. Validating a presented token, including its
 * confirmation on the resource side, remains the validation pipeline's job ({@code VALIDATION-8.7}).
 * <p>
 * Every other payload claim, and every other {@code cnf} member, is skipped by the parser. Parsing
 * uses DSL-JSON's compile-time {@code @CompiledJson} mapping; fields are {@code public} because
 * DSL-JSON class-based deserialization requires it, and callers use the accessor methods.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9449#section-6.1">RFC 9449 §6.1 - JWK Thumbprint Confirmation Method</a>
 */
@CompiledJson
@SuppressWarnings("java:S1104") // Public fields required by DSL-JSON @CompiledJson for class-based deserialization
public class AccessTokenConfirmation {

    /** The {@code cnf} (confirmation) claim object, or {@code null} when the payload carried none. */
    public @Nullable Confirmation cnf;

    /**
     * @return the {@code cnf.jkt} JWK thumbprint, or {@link Optional#empty()} when the payload carried
     *         no {@code cnf} claim or its {@code cnf} carried no {@code jkt} member
     */
    public Optional<String> getJkt() {
        return Optional.ofNullable(cnf).map(confirmation -> confirmation.jkt);
    }

    /**
     * The {@code cnf} claim object, mapped down to its {@code jkt} member.
     */
    @CompiledJson
    @SuppressWarnings("java:S1104") // Public field required by DSL-JSON @CompiledJson for class-based deserialization
    public static class Confirmation {

        /** The base64url-encoded RFC 7638 JWK thumbprint of the DPoP proof key. */
        public @Nullable String jkt;
    }
}
