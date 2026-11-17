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
package de.cuioss.sheriff.token.client.dpop;

import de.cuioss.sheriff.token.client.internal.ClientLogMessages;
import de.cuioss.sheriff.token.client.internal.JsonEscaper;
import de.cuioss.sheriff.token.validation.security.JwsAlgorithm;
import de.cuioss.sheriff.token.validation.util.EcdsaSignatureFormatConverter;
import de.cuioss.sheriff.token.validation.util.JwkThumbprintUtil;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.PSSParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 * The {@code htu} is the target URI of the request the proof is attached to (RFC 9449 §4.2): the URL
 * actually dialled, after any back-channel mapping, not the endpoint URL the authorization server
 * advertised. The generator signs whatever {@code htu} its caller supplies.
 * <p>
 * The generator signs with a JDK {@link Signature} — the engine adds no cryptographic library of its
 * own — and mirrors the {@code DpopProofHelper} conventions already used by the integration harness.
 * The supported proof-key and algorithm matrix is:
 * <table border="1">
 *   <caption>Supported proof keys and signing algorithms</caption>
 *   <tr><th>Proof key</th><th>Signing algorithms</th></tr>
 *   <tr><td>RSA</td><td>{@code RS256}, {@code RS384}, {@code RS512}, {@code PS256}</td></tr>
 *   <tr><td>EC, curve P-256</td><td>{@code ES256}</td></tr>
 *   <tr><td>OKP, curve Ed25519</td><td>{@code EdDSA}</td></tr>
 * </table>
 * <p>
 * Passing {@code PS256}, {@code ES256} or {@code EdDSA} reaches FAPI 2.0 §5.4.1 conformance through
 * the DPoP route. The algorithm and the proof key are checked against each other at construction
 * time, so a mismatch fails fast rather than surfacing later from the JCA layer.
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

    /**
     * The {@link JwsAlgorithm} subset this generator accepts. The shared catalog is deliberately
     * wider than DPoP: admitting {@code ES384} / {@code ES512} would reach the P-256-only member set
     * of {@link JwkPublicMembers}, and {@code PS384} / {@code PS512} are outside the matrix documented on the type,
     * so the subset is declared here rather than inherited from the catalog wholesale.
     */
    static final Set<JwsAlgorithm> SUPPORTED_ALGORITHMS = EnumSet.of(
            JwsAlgorithm.RS256,
            JwsAlgorithm.RS384,
            JwsAlgorithm.RS512,
            JwsAlgorithm.PS256,
            JwsAlgorithm.ES256,
            JwsAlgorithm.EDDSA);

    private final KeyPair keyPair;
    private final JwsAlgorithm algorithm;
    private final String jcaAlgorithm;
    private final String jwkJson;
    private final String jkt;
    private final Supplier<String> jtiSource;
    /** The bound on {@link #issuedJtis}; {@link #MAX_TRACKED_JTIS} unless injected. */
    private final int maxTrackedJtis;
    // Bounded, thread-safe LRU of recently-issued jti values: access-ordered LinkedHashMap that evicts
    // its eldest entry once it exceeds maxTrackedJtis, wrapped in a synchronized view for concurrent
    // use. Explicit java.util imports keep the construction stable under OpenRewrite.
    private final Map<String, Boolean> issuedJtis = Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > maxTrackedJtis;
                }
            });

    /**
     * Creates a generator that mints a random {@code jti} for every proof.
     *
     * @param keyPair   the proof key pair (RSA, EC P-256 or OKP Ed25519); must not be {@code null}
     * @param algorithm the JWT signing algorithm ({@code RS256} / {@code RS384} / {@code RS512} /
     *                  {@code PS256} / {@code ES256} / {@code EdDSA})
     * @throws IllegalArgumentException if the algorithm is unsupported, the proof key is not an RSA,
     *         EC P-256 or OKP Ed25519 key, or the algorithm does not match the proof-key type
     */
    public DpopProofGenerator(KeyPair keyPair, String algorithm) {
        this(keyPair, algorithm, () -> UUID.randomUUID().toString());
    }

    /**
     * Creates a generator with an explicit {@code jti} source. The overload exists so tests can force
     * a repeated identifier and assert the reuse defence; production code uses the random-{@code jti}
     * constructor above.
     *
     * @param keyPair   the proof key pair (RSA, EC P-256 or OKP Ed25519); must not be {@code null}
     * @param algorithm the JWT signing algorithm ({@code RS256} / {@code RS384} / {@code RS512} /
     *                  {@code PS256} / {@code ES256} / {@code EdDSA})
     * @param jtiSource the source of proof identifiers; must not be {@code null}
     * @throws IllegalArgumentException if the algorithm is unsupported, the proof key is not an RSA,
     *         EC P-256 or OKP Ed25519 key, or the algorithm does not match the proof-key type
     */
    public DpopProofGenerator(KeyPair keyPair, String algorithm, Supplier<String> jtiSource) {
        this(keyPair, algorithm, jtiSource, MAX_TRACKED_JTIS);
    }

    /**
     * Creates a generator with an explicit {@code jti} source and an explicit bound on the tracked
     * {@code jti} window, so tests can exercise the window's eviction without reflection.
     *
     * @param keyPair        the proof key pair (RSA, EC P-256 or OKP Ed25519); must not be {@code null}
     * @param algorithm      the JWT signing algorithm ({@code RS256} / {@code RS384} / {@code RS512} /
     *                       {@code PS256} / {@code ES256} / {@code EdDSA})
     * @param jtiSource      the source of proof identifiers; must not be {@code null}
     * @param maxTrackedJtis the number of recently-issued {@code jti} values tracked for reuse detection;
     *                       must be positive
     * @throws IllegalArgumentException if {@code maxTrackedJtis} is not positive, the algorithm is
     *         unsupported, the proof key is not an RSA, EC P-256 or OKP Ed25519 key, or the algorithm
     *         does not match the proof-key type
     */
    DpopProofGenerator(KeyPair keyPair, String algorithm, Supplier<String> jtiSource, int maxTrackedJtis) {
        if (maxTrackedJtis <= 0) {
            throw new IllegalArgumentException("maxTrackedJtis must be positive");
        }
        this.maxTrackedJtis = maxTrackedJtis;
        this.keyPair = Objects.requireNonNull(keyPair, "keyPair must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        this.jtiSource = Objects.requireNonNull(jtiSource, "jtiSource must not be null");
        this.algorithm = requireSupportedAlgorithm(algorithm);
        this.jcaAlgorithm = jcaSignatureAlgorithmOf(this.algorithm);
        // One JWK map feeds both the header 'jwk' and the cnf.jkt thumbprint, so a proof can never
        // advertise a key whose thumbprint does not match the embedded JWK.
        // JWK admission (JwkPublicMembers) stays independent of the signing catalog: the algorithm is
        // matched against the admitted key's type here, not inside the member builder (ADR-0009).
        Map<String, Object> jwk = JwkPublicMembers.of(keyPair.getPublic());
        requireKeyTypeMatches(this.algorithm, (String) jwk.get("kty"));
        this.jwkJson = JwkThumbprintUtil.canonicalJson(jwk);
        this.jkt = JwkThumbprintUtil.computeThumbprint(jwk);
    }

    /**
     * Resolves a JWA {@code alg} name against the shared catalog and refuses anything outside
     * {@link #SUPPORTED_ALGORITHMS} — the catalog admits algorithms this generator cannot serve.
     *
     * @param algorithm the requested JWT signing algorithm
     * @return the catalog entry for the algorithm
     * @throws IllegalArgumentException if the algorithm is unknown to the catalog or outside the
     *         DPoP-supported subset
     */
    private static JwsAlgorithm requireSupportedAlgorithm(String algorithm) {
        return JwsAlgorithm.fromJwaName(algorithm)
                .filter(SUPPORTED_ALGORITHMS::contains)
                .orElseThrow(() -> new IllegalArgumentException(
                        "unsupported DPoP signing algorithm: " + algorithm));
    }

    /**
     * Resolves the JCA signature-algorithm name to instantiate for a catalog entry. The catalog
     * leaves the EC family's name open because JCA spells the same algorithm two ways; this
     * generator signs in ASN.1/DER and converts to the JOSE {@code R||S} form afterwards, so it
     * composes the DER spelling.
     *
     * @param algorithm the catalog entry to resolve
     * @return the JCA signature-algorithm name
     */
    private static String jcaSignatureAlgorithmOf(JwsAlgorithm algorithm) {
        return algorithm.getJcaSignatureAlgorithm().orElseGet(
                () -> algorithm.getDigest().orElseThrow().replace("-", "") + "withECDSA");
    }

    /**
     * Fails fast when the requested signing algorithm does not match the proof key's type — for
     * example {@code ES256} with an RSA pair, or {@code RS256} with an EC pair.
     *
     * @param algorithm     the requested signing algorithm's catalog entry
     * @param actualKeyType the {@code kty} of the supplied proof key
     * @throws IllegalArgumentException if the algorithm requires a different key type
     */
    private static void requireKeyTypeMatches(JwsAlgorithm algorithm, String actualKeyType) {
        String requiredKeyType = algorithm.getKeyType();
        if (!actualKeyType.equals(requiredKeyType)) {
            throw new IllegalArgumentException(
                    "DPoP signing algorithm %s requires a %s proof key, but the key pair is %s".formatted(
                            algorithm.getJwaName(), requiredKeyType, actualKeyType));
        }
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
        String header = "{\"typ\":\"" + DPOP_TYP + "\",\"alg\":\"" + algorithm.getJwaName()
                + "\",\"jwk\":" + jwkJson + "}";
        // htu is the URL the request is actually sent to, as the caller passes it; strip its query and
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
            Optional<PSSParameterSpec> pssParameters = algorithm.getPssParameters();
            if (pssParameters.isPresent()) {
                // The JCA RSASSA-PSS instance carries no defaults — the parameters are explicit.
                signature.setParameter(pssParameters.get());
            }
            signature.initSign(keyPair.getPrivate());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] rawSignature = signature.sign();
            if (algorithm == JwsAlgorithm.ES256) {
                // SHA256withECDSA emits ASN.1/DER; JOSE requires the IEEE P1363 R||S concatenation.
                rawSignature = EcdsaSignatureFormatConverter.toJoseSignature(
                        rawSignature, algorithm.getJwaName());
            }
            return BASE64_URL.encodeToString(rawSignature);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign DPoP proof", e);
        }
    }

    private static String encode(String json) {
        return BASE64_URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
