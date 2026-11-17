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
package de.cuioss.sheriff.token.client.auth;

import de.cuioss.sheriff.token.client.auth.KeyPairPolicy.KeyType;
import de.cuioss.sheriff.token.client.dpop.JwkPublicMembers;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * Loads a key pair from one bounded, strictly parsed PEM file.
 * <p>
 * The file holds exactly one unencrypted PKCS#8 private-key block
 * ({@code -----BEGIN PRIVATE KEY-----}) and one X.509 {@code SubjectPublicKeyInfo} public-key block
 * ({@code -----BEGIN PUBLIC KEY-----}), in either order, separated by nothing but blank lines. LF and
 * CRLF line endings are both accepted. The loader:
 * <ul>
 *   <li>refuses a missing path, a directory or any other non-regular file before reading;</li>
 *   <li>reads at most {@value #MAX_FILE_BYTES} bytes and refuses the file as soon as one byte beyond
 *       that bound is read — it never buffers more;</li>
 *   <li>requires printable US-ASCII content;</li>
 *   <li>refuses text outside a block, unknown or repeated labels, mismatched {@code BEGIN}/{@code END}
 *       labels, boundary lines inside a body, base64 the JDK decoder rejects (characters outside the
 *       base64 alphabet or invalid padding; the decoder is lenient about omitted padding), and the encrypted PKCS#8
 *       ({@code ENCRYPTED PRIVATE KEY}), PKCS#1 ({@code RSA PRIVATE KEY}) and SEC1
 *       ({@code EC PRIVATE KEY}) encodings;</li>
 *   <li>decodes the blocks with the JDK {@link KeyFactory} for the key types the
 *       {@link KeyPairPolicy} admits only, and applies the policy — RSA modulus minimum, EC keys on
 *       curve P-256 with an on-curve point (checked by {@link JwkPublicMembers#of}), Ed25519 only
 *       when admitted;</li>
 *   <li>proves that both halves belong to one key by signing a fixed challenge with the private key
 *       and verifying it with the public key.</li>
 * </ul>
 * <p>
 * Every refusal is an {@link IllegalArgumentException} carrying a fixed text: it never contains the
 * file path or any file content, and no {@link IOException} or {@link GeneralSecurityException} is
 * attached as its cause. The returned pair is accepted by
 * {@link de.cuioss.sheriff.token.client.dpop.DpopProofGenerator} ({@code RS256}, {@code ES256},
 * {@code EdDSA}) and, for RSA and EC keys, by {@link PrivateKeyJwtAuth}. The class is stateless and
 * safe for concurrent use; it uses the JDK only.
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7468">RFC 7468 - Textual Encodings of PKIX, PKCS, and CMS Structures</a>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PemKeyPairLoader {

    /** The hard upper bound on the size of a PEM key file, in bytes (16 KiB). */
    public static final int MAX_FILE_BYTES = 16 * 1024;

    /** Refusal text for a missing path, a directory or another non-regular file. */
    static final String NOT_A_REGULAR_FILE = "PEM key file is missing or not a regular file";

    /** Refusal text for a file that could not be read. */
    static final String UNREADABLE = "PEM key file could not be read";

    /** Refusal text for a file larger than {@link #MAX_FILE_BYTES}. */
    static final String TOO_LARGE = "PEM key file exceeds the 16 KiB size bound";

    /** Refusal text for content that is not printable US-ASCII. */
    static final String NOT_ASCII = "PEM key file must contain printable US-ASCII text only";

    /** Refusal text for text outside a PEM block. */
    static final String TEXT_OUTSIDE_BLOCK = "PEM key file contains text outside a PEM block";

    /** Refusal text for a PEM label other than {@code PRIVATE KEY} or {@code PUBLIC KEY}. */
    static final String UNKNOWN_LABEL = "PEM key file contains an unsupported PEM label";

    /** Refusal text for an encrypted PKCS#8, PKCS#1 or SEC1 private-key block. */
    static final String UNSUPPORTED_PRIVATE_KEY_ENCODING =
            "PEM private key must be an unencrypted PKCS#8 PRIVATE KEY block";

    /** Refusal text for a second block carrying an already-seen label. */
    static final String REPEATED_BLOCK = "PEM key file contains a repeated PEM block";

    /** Refusal text for an {@code END} line whose label differs from its {@code BEGIN} line. */
    static final String LABEL_MISMATCH = "PEM key file contains an END line that does not match its BEGIN line";

    /** Refusal text for a boundary line inside a block body. */
    static final String BOUNDARY_INSIDE_BLOCK = "PEM key file contains a boundary line inside a PEM block";

    /** Refusal text for a block without an {@code END} line. */
    static final String UNTERMINATED_BLOCK = "PEM key file contains an unterminated PEM block";

    /**
     * Refusal text for a block body the JDK base64 decoder rejects (a character outside the base64 alphabet
     * or invalid padding), for an empty body, and for a blank line inside a body.
     */
    static final String INVALID_BASE64 = "PEM key block body is not valid base64";

    /** Refusal text for a file lacking the private-key or the public-key block. */
    static final String MISSING_BLOCK = "PEM key file must contain one PRIVATE KEY and one PUBLIC KEY block";

    /** Refusal text for a public key whose type the policy does not admit or that does not decode. */
    static final String KEY_TYPE_NOT_ALLOWED = "PEM public key is not of a key type the policy admits";

    /** Refusal text for an RSA key shorter than the policy minimum. */
    static final String RSA_MODULUS_TOO_SHORT = "PEM RSA key is shorter than the policy minimum";

    /** Refusal text for an EC key that is not on curve P-256. */
    static final String EC_KEY_NOT_P256 = "PEM EC key must be a valid point on curve P-256";

    /** Refusal text for halves that do not form one key pair. */
    static final String KEY_PAIR_MISMATCH = "PEM private and public keys do not form one key pair";

    private static final String PRIVATE_KEY_LABEL = "PRIVATE KEY";
    private static final String PUBLIC_KEY_LABEL = "PUBLIC KEY";
    private static final String BOUNDARY_DASHES = "-----";
    private static final String BEGIN_PREFIX = BOUNDARY_DASHES + "BEGIN ";
    private static final String END_PREFIX = BOUNDARY_DASHES + "END ";

    /** The fixed challenge signed and verified to prove that both halves belong to one key. */
    private static final byte[] CONSISTENCY_CHALLENGE =
            "TokenSheriff PEM key-pair consistency probe".getBytes(StandardCharsets.US_ASCII);

    /**
     * Loads a key pair under the {@link KeyPairPolicy#defaults() TokenSheriff defaults}.
     *
     * @param file the PEM file; must not be {@code null}
     * @return the loaded key pair
     * @throws IllegalArgumentException if the file is refused; the message is a fixed text
     */
    public static KeyPair load(Path file) {
        return load(file, KeyPairPolicy.defaults());
    }

    /**
     * Loads a key pair under a caller-supplied policy.
     *
     * @param file   the PEM file; must not be {@code null}
     * @param policy the key types and minimum strength to admit; must not be {@code null}
     * @return the loaded key pair
     * @throws IllegalArgumentException if the file is refused; the message is a fixed text
     */
    public static KeyPair load(Path file, KeyPairPolicy policy) {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        String content = readBounded(file);
        PemBlocks blocks = parse(content);
        return decode(blocks, policy);
    }

    /**
     * Reads the file through a stream bounded at one byte beyond {@link #MAX_FILE_BYTES} and checks
     * the content is printable US-ASCII.
     */
    private static String readBounded(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException(NOT_A_REGULAR_FILE);
        }
        byte[] bytes;
        try (InputStream in = Files.newInputStream(file)) {
            bytes = in.readNBytes(MAX_FILE_BYTES + 1);
        } catch (IOException e) {
            throw new IllegalArgumentException(UNREADABLE);
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException(TOO_LARGE);
        }
        for (byte b : bytes) {
            boolean printable = b >= 0x20 && b <= 0x7E;
            if (!printable && b != '\n' && b != '\r') {
                throw new IllegalArgumentException(NOT_ASCII);
            }
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    /**
     * Splits the content into its private-key and public-key block bodies.
     */
    private static PemBlocks parse(String content) {
        PemParser parser = new PemParser();
        for (String line : content.split("\r?\n", -1)) {
            parser.accept(line);
        }
        return parser.finish();
    }

    /**
     * Reads the label of a {@code BEGIN} line, admitting only {@code PRIVATE KEY} and
     * {@code PUBLIC KEY}.
     */
    private static String beginLabel(String line) {
        if (!line.startsWith(BEGIN_PREFIX) || !line.endsWith(BOUNDARY_DASHES)
                || line.length() < BEGIN_PREFIX.length() + BOUNDARY_DASHES.length()) {
            throw new IllegalArgumentException(TEXT_OUTSIDE_BLOCK);
        }
        String label = line.substring(BEGIN_PREFIX.length(), line.length() - BOUNDARY_DASHES.length());
        return switch (label) {
            case PRIVATE_KEY_LABEL, PUBLIC_KEY_LABEL -> label;
            case "ENCRYPTED PRIVATE KEY", "RSA PRIVATE KEY", "EC PRIVATE KEY" ->
                throw new IllegalArgumentException(UNSUPPORTED_PRIVATE_KEY_ENCODING);
            default -> throw new IllegalArgumentException(UNKNOWN_LABEL);
        };
    }

    private static byte[] decodeBase64(String body) {
        if (body.isEmpty()) {
            throw new IllegalArgumentException(INVALID_BASE64);
        }
        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            // The decoder's message names the offending character; it is not propagated.
            throw new IllegalArgumentException(INVALID_BASE64);
        }
    }

    /**
     * Decodes both halves with the key factory of the admitted key type the public half belongs to,
     * applies the policy and probes that the halves form one key pair.
     */
    private static KeyPair decode(PemBlocks blocks, KeyPairPolicy policy) {
        for (KeyType keyType : KeyType.values()) {
            if (!policy.allows(keyType)) {
                continue;
            }
            PublicKey publicKey = decodePublic(keyType, blocks.publicKey());
            if (publicKey != null) {
                requirePolicy(keyType, publicKey, policy);
                PrivateKey privateKey = decodePrivate(keyType, blocks.privateKey());
                requireOneKeyPair(keyType, privateKey, publicKey);
                return new KeyPair(publicKey, privateKey);
            }
        }
        throw new IllegalArgumentException(KEY_TYPE_NOT_ALLOWED);
    }

    private static @Nullable PublicKey decodePublic(KeyType keyType, byte[] der) {
        try {
            return KeyFactory.getInstance(factoryAlgorithm(keyType)).generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            // Not a public key of this type; the next admitted type is tried.
            return null;
        }
    }

    private static PrivateKey decodePrivate(KeyType keyType, byte[] der) {
        try {
            return KeyFactory.getInstance(factoryAlgorithm(keyType)).generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(KEY_PAIR_MISMATCH);
        }
    }

    private static void requirePolicy(KeyType keyType, PublicKey publicKey, KeyPairPolicy policy) {
        switch (keyType) {
            case RSA -> requireRsaModulus((RSAPublicKey) publicKey, policy);
            case EC_P256 -> {
                try {
                    JwkPublicMembers.of(publicKey);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(EC_KEY_NOT_P256);
                }
            }
            case ED25519 -> {
                // The Ed25519 key factory decodes Ed25519 keys only; nothing further to check.
            }
        }
    }

    private static void requireRsaModulus(RSAPublicKey publicKey, KeyPairPolicy policy) {
        if (publicKey.getModulus().bitLength() < policy.minimumRsaModulusBits()) {
            throw new IllegalArgumentException(RSA_MODULUS_TOO_SHORT);
        }
    }

    /**
     * Signs the fixed challenge with the private key and verifies it with the public key.
     */
    private static void requireOneKeyPair(KeyType keyType, PrivateKey privateKey, PublicKey publicKey) {
        boolean verified;
        try {
            Signature signer = Signature.getInstance(signatureAlgorithm(keyType));
            signer.initSign(privateKey);
            signer.update(CONSISTENCY_CHALLENGE);
            byte[] signature = signer.sign();
            Signature verifier = Signature.getInstance(signatureAlgorithm(keyType));
            verifier.initVerify(publicKey);
            verifier.update(CONSISTENCY_CHALLENGE);
            verified = verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            verified = false;
        }
        if (!verified) {
            throw new IllegalArgumentException(KEY_PAIR_MISMATCH);
        }
    }

    private static String factoryAlgorithm(KeyType keyType) {
        return switch (keyType) {
            case RSA -> "RSA";
            case EC_P256 -> "EC";
            case ED25519 -> "Ed25519";
        };
    }

    private static String signatureAlgorithm(KeyType keyType) {
        return switch (keyType) {
            case RSA -> "SHA256withRSA";
            case EC_P256 -> "SHA256withECDSA";
            case ED25519 -> "Ed25519";
        };
    }

    /**
     * Line-by-line state of {@link #parse(String)}: the label of the open block, the body collected
     * for it and the decoded halves seen so far. Each line is checked in the order the refusals are
     * documented — outside a block, the {@code END} line, then a body line — so the first refusal a
     * file earns does not depend on how the checks are split.
     */
    private static final class PemParser {

        private byte @Nullable [] privateKeyDer;
        private byte @Nullable [] publicKeyDer;
        private @Nullable String openLabel;
        private final StringBuilder body = new StringBuilder();

        private void accept(String line) {
            String label = openLabel;
            if (label == null) {
                acceptOutsideBlock(line);
            } else if (line.startsWith(END_PREFIX)) {
                acceptEnd(label, line);
            } else {
                acceptBodyLine(line);
            }
        }

        private void acceptOutsideBlock(String line) {
            if (line.isEmpty()) {
                return;
            }
            String label = beginLabel(line);
            boolean repeated = PRIVATE_KEY_LABEL.equals(label) ? privateKeyDer != null : publicKeyDer != null;
            if (repeated) {
                throw new IllegalArgumentException(REPEATED_BLOCK);
            }
            openLabel = label;
            body.setLength(0);
        }

        private void acceptEnd(String label, String line) {
            if (!line.equals(END_PREFIX + label + BOUNDARY_DASHES)) {
                throw new IllegalArgumentException(LABEL_MISMATCH);
            }
            byte[] der = decodeBase64(body.toString());
            if (PRIVATE_KEY_LABEL.equals(label)) {
                privateKeyDer = der;
            } else {
                publicKeyDer = der;
            }
            openLabel = null;
        }

        private void acceptBodyLine(String line) {
            if (line.startsWith(BOUNDARY_DASHES)) {
                throw new IllegalArgumentException(BOUNDARY_INSIDE_BLOCK);
            }
            if (line.isEmpty()) {
                throw new IllegalArgumentException(INVALID_BASE64);
            }
            body.append(line);
        }

        private PemBlocks finish() {
            if (openLabel != null) {
                throw new IllegalArgumentException(UNTERMINATED_BLOCK);
            }
            byte[] privateKey = privateKeyDer;
            byte[] publicKey = publicKeyDer;
            if (privateKey == null || publicKey == null) {
                throw new IllegalArgumentException(MISSING_BLOCK);
            }
            return new PemBlocks(privateKey, publicKey);
        }
    }

    /** The decoded DER bodies of the two PEM blocks. */
    private static final class PemBlocks {

        private final byte[] privateKeyDer;
        private final byte[] publicKeyDer;

        private PemBlocks(byte[] privateKeyDer, byte[] publicKeyDer) {
            this.privateKeyDer = privateKeyDer;
            this.publicKeyDer = publicKeyDer;
        }

        private byte[] privateKey() {
            return privateKeyDer;
        }

        private byte[] publicKey() {
            return publicKeyDer;
        }
    }
}
