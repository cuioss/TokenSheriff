/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.token.validation.util;

import de.cuioss.tools.logging.CuiLogger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SignatureException;

/**
 * Utility class for converting ECDSA signatures between IEEE P1363 and ASN.1/DER formats.
 *
 * <p>This class addresses the incompatibility between JWT standard ECDSA signatures
 * (IEEE P1363 format - raw R,S concatenation) and JDK ECDSA verification
 * (ASN.1/DER format - structured encoding).</p>
 *
 * <h3>Format Details:</h3>
 * <ul>
 *   <li><strong>IEEE P1363:</strong> Raw concatenation of R and S components (R || S)</li>
 *   <li><strong>ASN.1/DER:</strong> Structured encoding: SEQUENCE { r INTEGER, s INTEGER }</li>
 * </ul>
 *
 * <h3>Algorithm Support:</h3>
 * <ul>
 *   <li><strong>ES256 (P-256):</strong> 32-byte R and S components (64 bytes total in P1363)</li>
 *   <li><strong>ES384 (P-384):</strong> 48-byte R and S components (96 bytes total in P1363)</li>
 *   <li><strong>ES512 (P-521):</strong> 66-byte R and S components (132 bytes total in P1363)</li>
 * </ul>
 * @since 1.0
 */
public class EcdsaSignatureFormatConverter {

    private static final CuiLogger LOGGER = new CuiLogger(EcdsaSignatureFormatConverter.class);

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private EcdsaSignatureFormatConverter() {
        // Utility class
    }

    /**
     * Converts an ECDSA signature from IEEE P1363 format to ASN.1/DER format.
     *
     * <p>This conversion is necessary when validating JWT tokens with ECDSA signatures
     * using the JDK's ECDSA signature verification, which expects ASN.1/DER format.</p>
     *
     * @param ieeeP1363Signature the signature in IEEE P1363 format (raw R||S concatenation)
     * @param algorithm the ECDSA algorithm (ES256, ES384, or ES512)
     * @return the signature in ASN.1/DER format
     * @throws SignatureException if the conversion fails or the signature format is invalid
     */
    @SuppressWarnings("java:S125")
    public static byte[] toJCACompatibleSignature(byte[] ieeeP1363Signature, String algorithm) throws SignatureException {
        if (ieeeP1363Signature == null) {
            throw new SignatureException("Signature cannot be null");
        }

        // Determine expected signature length and component size based on algorithm
        int componentSize = getComponentSize(algorithm);
        int expectedLength = componentSize * 2;

        if (ieeeP1363Signature.length != expectedLength) {
            throw new SignatureException("Invalid %s signature length: expected %s bytes, got %s bytes".formatted(
                    algorithm, expectedLength, ieeeP1363Signature.length));
        }

        try {
            // Extract R and S components
            byte[] rBytes = new byte[componentSize];
            byte[] sBytes = new byte[componentSize];

            System.arraycopy(ieeeP1363Signature, 0, rBytes, 0, componentSize);
            System.arraycopy(ieeeP1363Signature, componentSize, sBytes, 0, componentSize);

            // Convert to BigInteger (handles leading zeros correctly)
            BigInteger r = new BigInteger(1, rBytes); // Positive BigInteger
            BigInteger s = new BigInteger(1, sBytes); // Positive BigInteger

            // Encode as ASN.1/DER SEQUENCE { r INTEGER, s INTEGER }
            return encodeAsn1Signature(r, s);

        } catch (IOException e) {
            throw new SignatureException("Failed to convert IEEE P1363 signature to ASN.1/DER format: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the component size in bytes for the specified ECDSA algorithm.
     *
     * @param algorithm the ECDSA algorithm (ES256, ES384, or ES512)
     * @return the component size in bytes
     * @throws SignatureException if the algorithm is not supported
     */
    private static int getComponentSize(String algorithm) throws SignatureException {
        return switch (algorithm.toUpperCase()) {
            case "ES256" -> 32; // P-256: 256 bits / 8 = 32 bytes
            case "ES384" -> 48; // P-384: 384 bits / 8 = 48 bytes
            case "ES512" -> 66; // P-521: 521 bits / 8 = 65.125 -> 66 bytes (rounded up)
            default -> throw new SignatureException("Unsupported ECDSA algorithm: " + algorithm);
        };
    }

    /**
     * Encodes R and S components as ASN.1/DER SEQUENCE { r INTEGER, s INTEGER }.
     *
     * <p>ASN.1 DER encoding rules:
     * <ul>
     *   <li>SEQUENCE tag: 0x30</li>
     *   <li>Length encoding (definite form)</li>
     *   <li>INTEGER tag: 0x02</li>
     *   <li>Length of integer</li>
     *   <li>Integer value (with padding byte if MSB is 1 to ensure positive)</li>
     * </ul></p>
     *
     * @param r the R component as a BigInteger
     * @param s the S component as a BigInteger
     * @return the ASN.1/DER encoded signature
     * @throws IOException if encoding fails
     */
    private static byte[] encodeAsn1Signature(BigInteger r, BigInteger s) throws IOException {
        // Encode R component
        byte[] rEncoded = encodeAsn1Integer(r);

        // Encode S component
        byte[] sEncoded = encodeAsn1Integer(s);

        // Calculate total content length
        int contentLength = rEncoded.length + sEncoded.length;

        // Build the complete SEQUENCE
        ByteArrayOutputStream sequence = new ByteArrayOutputStream();

        // SEQUENCE tag
        sequence.write(0x30);

        // Length of content
        writeLength(sequence, contentLength);

        // R component
        sequence.write(rEncoded);

        // S component
        sequence.write(sEncoded);

        byte[] result = sequence.toByteArray();

        LOGGER.debug("Converted IEEE P1363 signature to ASN.1/DER format (length: %s bytes)", result.length);

        return result;
    }

    /**
     * Encodes a BigInteger as ASN.1/DER INTEGER.
     *
     * @param value the BigInteger value to encode
     * @return the ASN.1/DER encoded INTEGER
     * @throws IOException if encoding fails
     */
    private static byte[] encodeAsn1Integer(BigInteger value) throws IOException {
        byte[] valueBytes = value.toByteArray(); // BigInteger.toByteArray() handles sign bit correctly

        ByteArrayOutputStream integerEncoding = new ByteArrayOutputStream();

        // INTEGER tag
        integerEncoding.write(0x02);

        // Length of value
        writeLength(integerEncoding, valueBytes.length);

        // Value
        integerEncoding.write(valueBytes);

        return integerEncoding.toByteArray();
    }

    /**
     * Writes a length value in ASN.1/DER definite form.
     *
     * <p>DER length encoding:
     * <ul>
     *   <li>Short form (length &lt; 128): single byte with the length value</li>
     *   <li>Long form (length ≥ 128): first byte = 0x80 | number_of_length_bytes, followed by length bytes</li>
     * </ul></p>
     *
     * @param out the output stream to write to
     * @param length the length value to encode
     */
    private static void writeLength(ByteArrayOutputStream out, int length) {
        if (length < 128) {
            // Short form
            out.write(length);
        } else {
            // Long form - determine how many bytes needed
            int bytesNeeded = 0;
            int temp = length;
            while (temp > 0) {
                bytesNeeded++;
                temp >>= 8;
            }

            // Write first byte: 0x80 | number of length bytes
            out.write(0x80 | bytesNeeded);

            // Write length bytes (most significant first)
            for (int i = bytesNeeded - 1; i >= 0; i--) {
                out.write((length >> (i * 8)) & 0xFF);
            }
        }
    }
}