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
package de.cuioss.sheriff.token.validation.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.SignatureException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EcdsaSignatureFormatConverter}.
 */
@DisplayName("Tests EcdsaSignatureFormatConverter functionality")
class EcdsaSignatureFormatConverterTest {

    @Test
    @DisplayName("Should convert ES256 IEEE P1363 signature to ASN.1/DER format")
    void shouldConvertES256Signature() throws Exception {
        // Create test IEEE P1363 signature for ES256 (64 bytes: 32 for R + 32 for S)
        byte[] ieeeP1363Signature = new byte[64];

        // R component (32 bytes) - example value
        Arrays.fill(ieeeP1363Signature, 0, 32, (byte) 0x12);
        ieeeP1363Signature[0] = (byte) 0x01; // Ensure non-zero MSB
        
        // S component (32 bytes) - example value  
        Arrays.fill(ieeeP1363Signature, 32, 64, (byte) 0x34);
        ieeeP1363Signature[32] = (byte) 0x02; // Ensure non-zero MSB
        
        // Convert to ASN.1/DER format
        byte[] asn1Signature = EcdsaSignatureFormatConverter.toJCACompatibleSignature(ieeeP1363Signature, "ES256");

        // Verify the result is not null and has reasonable length
        assertNotNull(asn1Signature, "Converted signature should not be null");
        assertTrue(asn1Signature.length > 64, "ASN.1/DER signature should be longer than IEEE P1363 due to structure");
        assertTrue(asn1Signature.length < 80, "ASN.1/DER signature should not be excessively long");

        // Verify ASN.1/DER structure
        assertEquals(0x30, asn1Signature[0], "First byte should be SEQUENCE tag (0x30)");

        // Find R component (first INTEGER)
        int rStart = 2; // Skip SEQUENCE tag and length
        assertEquals(0x02, asn1Signature[rStart], "R component should start with INTEGER tag (0x02)");

        // Verify structure is parseable by extracting components
        verifyAsn1Structure(asn1Signature);
    }

    @Test
    @DisplayName("Should convert ES384 IEEE P1363 signature to ASN.1/DER format")
    void shouldConvertES384Signature() throws Exception {
        // Create test IEEE P1363 signature for ES384 (96 bytes: 48 for R + 48 for S)
        byte[] ieeeP1363Signature = new byte[96];

        // R component (48 bytes)
        Arrays.fill(ieeeP1363Signature, 0, 48, (byte) 0x56);
        ieeeP1363Signature[0] = (byte) 0x03; // Ensure non-zero MSB
        
        // S component (48 bytes)
        Arrays.fill(ieeeP1363Signature, 48, 96, (byte) 0x78);
        ieeeP1363Signature[48] = (byte) 0x04; // Ensure non-zero MSB
        
        // Convert to ASN.1/DER format
        byte[] asn1Signature = EcdsaSignatureFormatConverter.toJCACompatibleSignature(ieeeP1363Signature, "ES384");

        // Verify the result
        assertNotNull(asn1Signature, "Converted signature should not be null");
        assertTrue(asn1Signature.length > 96, "ASN.1/DER signature should be longer than IEEE P1363");
        assertEquals(0x30, asn1Signature[0], "First byte should be SEQUENCE tag (0x30)");

        // Verify structure is parseable
        verifyAsn1Structure(asn1Signature);
    }

    @Test
    @DisplayName("Should convert ES512 IEEE P1363 signature to ASN.1/DER format")
    void shouldConvertES512Signature() throws Exception {
        // Create test IEEE P1363 signature for ES512 (132 bytes: 66 for R + 66 for S)
        byte[] ieeeP1363Signature = new byte[132];

        // R component (66 bytes)
        Arrays.fill(ieeeP1363Signature, 0, 66, (byte) 0x9A);
        ieeeP1363Signature[0] = (byte) 0x05; // Ensure non-zero MSB
        
        // S component (66 bytes)
        Arrays.fill(ieeeP1363Signature, 66, 132, (byte) 0xBC);
        ieeeP1363Signature[66] = (byte) 0x06; // Ensure non-zero MSB
        
        // Convert to ASN.1/DER format
        byte[] asn1Signature = EcdsaSignatureFormatConverter.toJCACompatibleSignature(ieeeP1363Signature, "ES512");

        // Verify the result
        assertNotNull(asn1Signature, "Converted signature should not be null");
        assertTrue(asn1Signature.length > 132, "ASN.1/DER signature should be longer than IEEE P1363");
        assertEquals(0x30, asn1Signature[0], "First byte should be SEQUENCE tag (0x30)");

        // Verify structure is parseable
        verifyAsn1Structure(asn1Signature);
    }

    @Test
    @DisplayName("Should handle signatures with leading zeros in R component")
    void shouldHandleLeadingZerosInR() throws Exception {
        // Create IEEE P1363 signature with leading zeros in R component
        byte[] ieeeP1363Signature = new byte[64];

        // R component with leading zeros
        ieeeP1363Signature[0] = 0x00;
        ieeeP1363Signature[1] = 0x00;
        ieeeP1363Signature[2] = (byte) 0x12; // First non-zero byte
        Arrays.fill(ieeeP1363Signature, 3, 32, (byte) 0x34);

        // S component (normal case)
        Arrays.fill(ieeeP1363Signature, 32, 64, (byte) 0x56);
        ieeeP1363Signature[32] = (byte) 0x07; // Ensure non-zero MSB
        
        // Convert should succeed
        byte[] asn1Signature = EcdsaSignatureFormatConverter.toJCACompatibleSignature(ieeeP1363Signature, "ES256");

        assertNotNull(asn1Signature, "Conversion should succeed with leading zeros");
        verifyAsn1Structure(asn1Signature);
    }

    @Test
    @DisplayName("Should handle signatures with leading zeros in S component")
    void shouldHandleLeadingZerosInS() throws Exception {
        // Create IEEE P1363 signature with leading zeros in S component
        byte[] ieeeP1363Signature = new byte[64];

        // R component (normal case)
        Arrays.fill(ieeeP1363Signature, 0, 32, (byte) 0x78);
        ieeeP1363Signature[0] = (byte) 0x08; // Ensure non-zero MSB
        
        // S component with leading zeros
        ieeeP1363Signature[32] = 0x00;
        ieeeP1363Signature[33] = 0x00;
        ieeeP1363Signature[34] = (byte) 0x9A; // First non-zero byte
        Arrays.fill(ieeeP1363Signature, 35, 64, (byte) 0xBC);

        // Convert should succeed
        byte[] asn1Signature = EcdsaSignatureFormatConverter.toJCACompatibleSignature(ieeeP1363Signature, "ES256");

        assertNotNull(asn1Signature, "Conversion should succeed with leading zeros");
        verifyAsn1Structure(asn1Signature);
    }

    @Test
    @DisplayName("Should handle all-zero R or S components")
    void shouldHandleZeroComponents() throws Exception {
        // Create signature with zero R component (edge case)
        byte[] ieeeP1363Signature = new byte[64];

        // R component: all zeros
        Arrays.fill(ieeeP1363Signature, 0, 32, (byte) 0x00);

        // S component: non-zero
        Arrays.fill(ieeeP1363Signature, 32, 64, (byte) 0xDE);
        ieeeP1363Signature[32] = (byte) 0x09; // Ensure non-zero MSB
        
        // Convert should succeed (BigInteger handles zero correctly)
        byte[] asn1Signature = EcdsaSignatureFormatConverter.toJCACompatibleSignature(ieeeP1363Signature, "ES256");

        assertNotNull(asn1Signature, "Conversion should succeed with zero R component");
        verifyAsn1Structure(asn1Signature);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ES256", "es256", "Es256", "ES384", "es384", "ES512", "es512"})
    @DisplayName("Should handle case-insensitive algorithm names")
    void shouldHandleCaseInsensitiveAlgorithms(String algorithm) throws Exception {
        // Determine expected length based on normalized algorithm
        String normalizedAlg = algorithm.toUpperCase();
        int expectedLength = switch (normalizedAlg) {
            case "ES256" -> 64;
            case "ES384" -> 96;
            case "ES512" -> 132;
            default -> throw new IllegalStateException("Unexpected algorithm: " + normalizedAlg);
        };

        // Create appropriate signature
        byte[] ieeeP1363Signature = new byte[expectedLength];
        Arrays.fill(ieeeP1363Signature, (byte) 0xAB);
        ieeeP1363Signature[0] = (byte) 0x0A; // Ensure non-zero MSB
        
        // Convert should succeed regardless of case
        byte[] asn1Signature = EcdsaSignatureFormatConverter.toJCACompatibleSignature(ieeeP1363Signature, algorithm);

        assertNotNull(asn1Signature, "Conversion should succeed with case variations");
        verifyAsn1Structure(asn1Signature);
    }

    @Test
    @DisplayName("Should throw SignatureException for null signature")
    void shouldThrowForNullSignature() {
        var exception = assertThrows(SignatureException.class, () ->
                EcdsaSignatureFormatConverter.toJCACompatibleSignature(null, "ES256"));

        assertEquals("Signature cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw SignatureException for unsupported algorithm")
    void shouldThrowForUnsupportedAlgorithm() {
        byte[] signature = new byte[64];

        var exception = assertThrows(SignatureException.class, () ->
                EcdsaSignatureFormatConverter.toJCACompatibleSignature(signature, "RS256"));

        assertTrue(exception.getMessage().contains("Unsupported ECDSA algorithm"));
    }

    @Test
    @DisplayName("Should throw SignatureException for wrong signature length - ES256")
    void shouldThrowForWrongSignatureLengthES256() {
        // ES256 expects 64 bytes, provide 32
        byte[] shortSignature = new byte[32];

        var exception = assertThrows(SignatureException.class, () ->
                EcdsaSignatureFormatConverter.toJCACompatibleSignature(shortSignature, "ES256"));

        assertTrue(exception.getMessage().contains("Invalid ES256 signature length"));
        assertTrue(exception.getMessage().contains("expected 64 bytes, got 32 bytes"));
    }

    @Test
    @DisplayName("Should throw SignatureException for wrong signature length - ES384")
    void shouldThrowForWrongSignatureLengthES384() {
        // ES384 expects 96 bytes, provide 64
        byte[] shortSignature = new byte[64];

        var exception = assertThrows(SignatureException.class, () ->
                EcdsaSignatureFormatConverter.toJCACompatibleSignature(shortSignature, "ES384"));

        assertTrue(exception.getMessage().contains("Invalid ES384 signature length"));
        assertTrue(exception.getMessage().contains("expected 96 bytes, got 64 bytes"));
    }

    @Test
    @DisplayName("Should throw SignatureException for wrong signature length - ES512")
    void shouldThrowForWrongSignatureLengthES512() {
        // ES512 expects 132 bytes, provide 96
        byte[] shortSignature = new byte[96];

        var exception = assertThrows(SignatureException.class, () ->
                EcdsaSignatureFormatConverter.toJCACompatibleSignature(shortSignature, "ES512"));

        assertTrue(exception.getMessage().contains("Invalid ES512 signature length"));
        assertTrue(exception.getMessage().contains("expected 132 bytes, got 96 bytes"));
    }

    @Test
    @DisplayName("Should throw SignatureException for empty signature")
    void shouldThrowForEmptySignature() {
        byte[] emptySignature = new byte[0];

        var exception = assertThrows(SignatureException.class, () ->
                EcdsaSignatureFormatConverter.toJCACompatibleSignature(emptySignature, "ES256"));

        assertTrue(exception.getMessage().contains("Invalid ES256 signature length"));
    }

    @Test
    @DisplayName("Should produce deterministic output for same input")
    void shouldProduceDeterministicOutput() throws Exception {
        byte[] ieeeP1363Signature = new byte[64];
        Arrays.fill(ieeeP1363Signature, 0, 32, (byte) 0x11);
        Arrays.fill(ieeeP1363Signature, 32, 64, (byte) 0x22);

        // Convert multiple times
        byte[] result1 = EcdsaSignatureFormatConverter.toJCACompatibleSignature(ieeeP1363Signature, "ES256");
        byte[] result2 = EcdsaSignatureFormatConverter.toJCACompatibleSignature(ieeeP1363Signature, "ES256");

        assertArrayEquals(result1, result2, "Conversion should produce deterministic results");
    }

    /**
     * Verifies that the ASN.1/DER signature has the correct basic structure.
     * This is a simple structural verification, not a complete ASN.1 parser.
     */
    private void verifyAsn1Structure(byte[] asn1Signature) {
        assertTrue(asn1Signature.length >= 8, "ASN.1 signature should have minimum length");

        // Should start with SEQUENCE tag
        assertEquals(0x30, asn1Signature[0], "Should start with SEQUENCE tag (0x30)");

        // Parse length
        int lengthByte = asn1Signature[1] & 0xFF;
        int contentStart = 2;

        if ((lengthByte & 0x80) != 0) {
            // Long form length
            int lengthBytes = lengthByte & 0x7F;
            contentStart = 2 + lengthBytes;
            assertTrue(contentStart < asn1Signature.length, "Content should start within signature bounds");
        }

        // Should contain two INTEGER components
        int pos = contentStart;

        // First INTEGER (R component)
        assertEquals(0x02, asn1Signature[pos], "R component should start with INTEGER tag (0x02)");

        // Skip R component
        pos++; // Skip INTEGER tag
        assertTrue(pos < asn1Signature.length, "Position should be valid after skipping INTEGER tag");
        int rLength = asn1Signature[pos] & 0xFF;
        pos++; // Skip length byte

        if ((rLength & 0x80) != 0) {
            // Long form length for R - we need to skip the length bytes but keep the actual length value
            int rLengthBytes = rLength & 0x7F;
            assertTrue(pos + rLengthBytes <= asn1Signature.length, "Long form length bytes should be within bounds");
            // For this basic verification, we'll just skip past the long form
            pos += rLengthBytes;
            // In real ASN.1 parsing, we'd read the actual length from these bytes
            // For now, just use a reasonable estimate for ECDSA signatures
            rLength = 32; // Typical for P-256
        }

        assertTrue(pos + rLength <= asn1Signature.length, "R component content should be within bounds");
        pos += rLength; // Skip R content

        // Second INTEGER (S component)
        assertTrue(pos < asn1Signature.length, "S component position should be valid");
        assertEquals(0x02, asn1Signature[pos], "S component should start with INTEGER tag (0x02)");
    }
}