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
package de.cuioss.sheriff.token.validation.security;

import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Optional;

/**
 * The JWS signing algorithms this library accepts, defined once.
 * <p>
 * {@link SignatureAlgorithmPreferences} reads the constants in declaration order as its default list
 * of supported algorithms. The order is informational only, since acceptance is by membership;
 * adding a constant adds the algorithm to that list.
 * <p>
 * Each constant carries what a consumer needs to act on the algorithm without re-deriving it: the
 * JWA {@code alg} name, the JWK key type ({@code kty}) the algorithm requires, the message digest
 * it is built on, the JCA signature-algorithm name where that name is single-valued, and the PSS
 * parameters for the {@code PS} family.
 * <p>
 * The EC family deliberately carries <em>no</em> JCA signature-algorithm name: JCA offers two
 * spellings for the same algorithm — {@code SHA256withECDSA}, which produces ASN.1/DER, and
 * {@code SHA256withECDSAinP1363Format}, which produces the JOSE R||S concatenation — so the name is
 * not a property of the algorithm alone and the consumer composes the spelling its signature format
 * requires from {@link #getDigest()}. {@code EdDSA} is the mirror case: it carries a JCA name but no
 * separate digest, because the digest is not selectable.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7518">RFC 7518 - JSON Web Algorithms</a>
 */
public enum JwsAlgorithm {

    /** ECDSA using P-521 and SHA-512. */
    ES512("ES512", "EC", Jca.SHA_512, null, null, 0),
    /** ECDSA using P-384 and SHA-384. */
    ES384("ES384", "EC", Jca.SHA_384, null, null, 0),
    /** ECDSA using P-256 and SHA-256. */
    ES256("ES256", "EC", Jca.SHA_256, null, null, 0),
    /** EdDSA over curve Ed25519; the digest is fixed by the curve rather than selected. */
    EDDSA("EdDSA", "OKP", null, "EdDSA", null, 0),
    /** RSASSA-PSS using SHA-512 and MGF1 with SHA-512. */
    PS512("PS512", "RSA", Jca.SHA_512, Jca.RSASSA_PSS, MGF1ParameterSpec.SHA512, 64),
    /** RSASSA-PSS using SHA-384 and MGF1 with SHA-384. */
    PS384("PS384", "RSA", Jca.SHA_384, Jca.RSASSA_PSS, MGF1ParameterSpec.SHA384, 48),
    /** RSASSA-PSS using SHA-256 and MGF1 with SHA-256. */
    PS256("PS256", "RSA", Jca.SHA_256, Jca.RSASSA_PSS, MGF1ParameterSpec.SHA256, 32),
    /** RSASSA-PKCS1-v1_5 using SHA-512. */
    RS512("RS512", "RSA", Jca.SHA_512, "SHA512withRSA", null, 0),
    /** RSASSA-PKCS1-v1_5 using SHA-384. */
    RS384("RS384", "RSA", Jca.SHA_384, "SHA384withRSA", null, 0),
    /** RSASSA-PKCS1-v1_5 using SHA-256. */
    RS256("RS256", "RSA", Jca.SHA_256, "SHA256withRSA", null, 0);

    /**
     * The JCA names shared by several constants above, held in a nested type because an enum
     * constant's arguments cannot refer to a field of the enum itself by simple name.
     * <p>
     * They are named rather than repeated because this is a security catalog: a digest mistyped in
     * one of nine positions would still compile and would silently select a different algorithm, so
     * every constant that means SHA-256 refers to one name instead of spelling it again.
     */
    private static final class Jca {

        private static final String SHA_512 = "SHA-512";
        private static final String SHA_384 = "SHA-384";
        private static final String SHA_256 = "SHA-256";
        private static final String RSASSA_PSS = "RSASSA-PSS";

        private Jca() {
        }
    }

    private final String jwaName;
    private final String keyType;
    private final String digest;
    private final String jcaSignatureAlgorithm;
    private final PSSParameterSpec pssParameters;

    JwsAlgorithm(String jwaName, String keyType, String digest, String jcaSignatureAlgorithm,
            MGF1ParameterSpec maskGenerationDigest, int saltLengthBytes) {
        this.jwaName = jwaName;
        this.keyType = keyType;
        this.digest = digest;
        this.jcaSignatureAlgorithm = jcaSignatureAlgorithm;
        this.pssParameters = maskGenerationDigest == null
                ? null
                : new PSSParameterSpec(digest, "MGF1", maskGenerationDigest, saltLengthBytes, 1);
    }

    /**
     * @return the JWA {@code alg} header value naming this algorithm, e.g. {@code ES256}
     */
    public String getJwaName() {
        return jwaName;
    }

    /**
     * @return the JWK key type ({@code kty}) this algorithm requires — {@code EC}, {@code RSA} or
     *         {@code OKP}
     */
    public String getKeyType() {
        return keyType;
    }

    /**
     * @return the JCA message-digest name this algorithm is built on, e.g. {@code SHA-256}, or an
     *         empty optional for an algorithm whose digest is not separately selectable
     *         ({@code EdDSA})
     */
    public Optional<String> getDigest() {
        return Optional.ofNullable(digest);
    }

    /**
     * @return the JCA signature-algorithm name, or an empty optional where JCA offers more than one
     *         name for this algorithm and the consumer must compose the one its signature format
     *         requires (the EC family — see the type documentation)
     */
    public Optional<String> getJcaSignatureAlgorithm() {
        return Optional.ofNullable(jcaSignatureAlgorithm);
    }

    /**
     * @return the PSS parameters this algorithm's signatures are produced and verified with, or an
     *         empty optional for a non-PSS algorithm
     */
    public Optional<PSSParameterSpec> getPssParameters() {
        return Optional.ofNullable(pssParameters);
    }

    /**
     * Looks a catalog entry up by its JWA {@code alg} name.
     *
     * @param jwaName the JWA {@code alg} value to resolve; may be {@code null}
     * @return the matching entry, or an empty optional when no entry carries that name — an unknown
     *         name is not an error here, it is an algorithm this library does not accept
     */
    public static Optional<JwsAlgorithm> fromJwaName(String jwaName) {
        for (JwsAlgorithm algorithm : values()) {
            if (algorithm.jwaName.equals(jwaName)) {
                return Optional.of(algorithm);
            }
        }
        return Optional.empty();
    }
}
