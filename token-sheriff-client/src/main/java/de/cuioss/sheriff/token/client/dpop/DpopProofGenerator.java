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
package de.cuioss.sheriff.token.client.dpop;

import de.cuioss.sheriff.token.client.internal.ClientLogMessages;
import de.cuioss.sheriff.token.client.internal.JsonEscaper;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Builds RFC 9449 DPoP proof JWTs binding the token request to a client-held proof key.
 * <p>
 * A DPoP proof is a JWT signed by the client's proof key, carrying the target HTTP method
 * ({@code htm}), the target URI ({@code htu}), a unique identifier ({@code jti}), and the issue time
 * ({@code iat}); its header embeds the proof-key's public JWK. Presenting it on the token request
 * makes the authorization server issue an access token bound to the proof key via {@code cnf.jkt}
 * ({@link #jkt()}), so a stolen bearer token cannot be replayed without the private key
 * ({@code CLIENT-11}, {@code T-TOKEN-REPLAY}).
 * <p>
 * The generator signs with a JDK {@link Signature} (RSA {@code RS256}/{@code RS384}/{@code RS512}) —
 * the engine adds no cryptographic library of its own — and mirrors the {@code DpopProofHelper}
 * conventions already used by the integration harness.
 * <p>
 * <strong>Replay defence:</strong> every emitted proof carries a fresh, single-use {@code jti}. The
 * generator tracks the identifiers it has issued and fails closed if its {@code jti} source ever
 * repeats a value, so it can never emit two proofs sharing a {@code jti} that an attacker could
 * replay. Instances are safe for concurrent use.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9449">RFC 9449 - OAuth 2.0 Demonstrating Proof of Possession (DPoP)</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7638">RFC 7638 - JSON Web Key (JWK) Thumbprint</a>
 */
public class DpopProofGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(DpopProofGenerator.class);

    /** The DPoP proof JWT header {@code typ}. */
    static final String DPOP_TYP = "dpop+jwt";
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    /**
     * Upper bound on the number of recently-issued {@code jti} values tracked for reuse detection.
     * The set is a bounded LRU rather than an unbounded set so a long-lived generator cannot leak
     * memory; reuse is still detected fail-closed within this most-recent window.
     */
    private static final int MAX_TRACKED_JTIS = 10_000;

    private final KeyPair keyPair;
    private final String jwtAlgorithm;
    private final String jcaAlgorithm;
    private final String jwkJson;
    private final String jkt;
    private final Supplier<String> jtiSource;
    // Bounded, thread-safe LRU of recently-issued jti values: access-ordered LinkedHashMap that evicts
    // its eldest entry once it exceeds MAX_TRACKED_JTIS, wrapped in a synchronized view for concurrent
    // use. Explicit java.util imports keep the construction stable under OpenRewrite.
    private final Map<String, Boolean> issuedJtis = Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_TRACKED_JTIS;
                }
            });

    /**
     * Creates a generator that mints a random {@code jti} for every proof.
     *
     * @param keyPair   the RSA proof key pair; must not be {@code null}
     * @param algorithm the JWT signing algorithm ({@code RS256} / {@code RS384} / {@code RS512})
     */
    public DpopProofGenerator(KeyPair keyPair, String algorithm) {
        this(keyPair, algorithm, () -> UUID.randomUUID().toString());
    }

    /**
     * Creates a generator with an explicit {@code jti} source. The overload exists so tests can force
     * a repeated identifier and assert the reuse defence; production code uses the random-{@code jti}
     * constructor above.
     *
     * @param keyPair   the RSA proof key pair; must not be {@code null}, public key must be RSA
     * @param algorithm the JWT signing algorithm ({@code RS256} / {@code RS384} / {@code RS512})
     * @param jtiSource the source of proof identifiers; must not be {@code null}
     */
    public DpopProofGenerator(KeyPair keyPair, String algorithm, Supplier<String> jtiSource) {
        this.keyPair = Objects.requireNonNull(keyPair, "keyPair must not be null");
        this.jwtAlgorithm = Objects.requireNonNull(algorithm, "algorithm must not be null");
        this.jtiSource = Objects.requireNonNull(jtiSource, "jtiSource must not be null");
        this.jcaAlgorithm = toJcaAlgorithm(algorithm);
        if (!(keyPair.getPublic() instanceof RSAPublicKey)) {
            throw new IllegalArgumentException("DPoP proof key must be an RSA key pair");
        }
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        this.jwkJson = rsaJwkJson(publicKey);
        this.jkt = computeJkt(publicKey);
    }

    /**
     * Builds a fresh, single-use DPoP proof for a token-endpoint request (no {@code ath}, no
     * {@code nonce}).
     *
     * @param htm the request HTTP method (e.g. {@code POST}); must not be {@code null} or blank
     * @param htu the request URI (e.g. the token endpoint); must not be {@code null} or blank
     * @return the compact-serialized, signed DPoP proof JWT
     * @throws IllegalStateException if the {@code jti} source repeats a previously issued identifier
     *         (a proof with a reused {@code jti} would be replayable and is refused)
     */
    public String generateProof(String htm, String htu) {
        return generateProof(htm, htu, null, null);
    }

    /**
     * Builds a fresh, single-use DPoP proof, optionally carrying a server-supplied {@code nonce}
     * (RFC 9449 §8) and an access-token hash {@code ath} for a protected-resource proof
     * (RFC 9449 §4.3).
     * <p>
     * The {@code htu} is normalized per RFC 9449 §4.2 — its query and fragment components are stripped
     * so the proof binds only the scheme, authority and path of the target URI.
     *
     * @param htm         the request HTTP method (e.g. {@code POST}); must not be {@code null} or blank
     * @param htu         the request URI; must not be {@code null} or blank. Query/fragment are stripped
     * @param nonce       the server-provided DPoP nonce to echo, or {@code null} when none was challenged
     * @param accessToken the access token whose {@code ath} hash binds a protected-resource proof, or
     *                    {@code null} for a token-endpoint proof (which carries no {@code ath})
     * @return the compact-serialized, signed DPoP proof JWT
     * @throws IllegalStateException if the {@code jti} source repeats a previously issued identifier
     *         (a proof with a reused {@code jti} would be replayable and is refused)
     */
    public String generateProof(String htm, String htu, @Nullable String nonce, @Nullable String accessToken) {
        Objects.requireNonNull(htm, "htm must not be null");
        Objects.requireNonNull(htu, "htu must not be null");
        if (htm.isBlank() || htu.isBlank()) {
            throw new IllegalArgumentException("htm and htu must not be blank");
        }
        String jti = jtiSource.get();
        if (issuedJtis.put(jti, Boolean.TRUE) != null) {
            LOGGER.warn(ClientLogMessages.WARN.DPOP_JTI_REUSE);
            throw new IllegalStateException(
                    "DPoP proof 'jti' reuse detected; refusing to emit a replayable proof");
        }
        long now = Instant.now().getEpochSecond();
        String header = "{\"typ\":\"" + DPOP_TYP + "\",\"alg\":\"" + jwtAlgorithm + "\",\"jwk\":" + jwkJson + "}";
        // htu is the request URI, sourced from the AS's own discovery metadata; strip its query and
        // fragment (RFC 9449 §4.2) then escape every JSON control character per RFC 8259.
        StringBuilder payload = new StringBuilder("{\"jti\":\"").append(JsonEscaper.escape(jti))
                .append("\",\"htm\":\"").append(JsonEscaper.escape(htm))
                .append("\",\"htu\":\"").append(JsonEscaper.escape(normalizeHtu(htu)))
                .append("\",\"iat\":").append(now);
        if (accessToken != null) {
            payload.append(",\"ath\":\"").append(JsonEscaper.escape(computeAth(accessToken))).append('"');
        }
        if (nonce != null) {
            payload.append(",\"nonce\":\"").append(JsonEscaper.escape(nonce)).append('"');
        }
        payload.append('}');
        String signingInput = encode(header) + "." + encode(payload.toString());
        return signingInput + "." + sign(signingInput);
    }

    /**
     * Strips the query and fragment from a request URI so the {@code htu} claim binds only the
     * scheme, authority and path (RFC 9449 §4.2).
     */
    private static String normalizeHtu(String htu) {
        int cut = htu.length();
        int query = htu.indexOf('?');
        if (query >= 0) {
            cut = query;
        }
        int fragment = htu.indexOf('#');
        if (fragment >= 0 && fragment < cut) {
            cut = fragment;
        }
        return htu.substring(0, cut);
    }

    /**
     * Computes the RFC 9449 §4.3 {@code ath} claim — the base64url-encoded SHA-256 hash of the ASCII
     * encoding of the access-token value — binding a protected-resource proof to the token it presents.
     */
    private static String computeAth(String accessToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(accessToken.getBytes(StandardCharsets.US_ASCII));
            return BASE64_URL.encodeToString(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 not available for DPoP ath claim", e);
        }
    }

    /**
     * @return the RFC 7638 base64url JWK thumbprint of the proof key — the {@code cnf.jkt} value the
     *         issued access token is bound to
     */
    public String jkt() {
        return jkt;
    }

    private String sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance(jcaAlgorithm);
            signature.initSign(keyPair.getPrivate());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return BASE64_URL.encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign DPoP proof", e);
        }
    }

    private static String rsaJwkJson(RSAPublicKey publicKey) {
        // Canonical RFC 7638 member order for RSA: e, kty, n. Reused as the header 'jwk' too.
        return "{\"e\":\"" + base64UrlUnsigned(publicKey.getPublicExponent())
                + "\",\"kty\":\"RSA\",\"n\":\"" + base64UrlUnsigned(publicKey.getModulus()) + "\"}";
    }

    private static String computeJkt(RSAPublicKey publicKey) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rsaJwkJson(publicKey).getBytes(StandardCharsets.UTF_8));
            return BASE64_URL.encodeToString(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 not available for JWK thumbprint", e);
        }
    }

    private static String base64UrlUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return BASE64_URL.encodeToString(bytes);
    }

    private static String encode(String json) {
        return BASE64_URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String toJcaAlgorithm(String jwtAlgorithm) {
        return switch (jwtAlgorithm) {
            case "RS256" -> "SHA256withRSA";
            case "RS384" -> "SHA384withRSA";
            case "RS512" -> "SHA512withRSA";
            default -> throw new IllegalArgumentException("unsupported DPoP signing algorithm: " + jwtAlgorithm);
        };
    }
}
