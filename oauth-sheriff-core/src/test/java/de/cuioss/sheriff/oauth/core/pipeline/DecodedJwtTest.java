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
package de.cuioss.sheriff.oauth.core.pipeline;

import com.dslplatform.json.DslJson;
import de.cuioss.sheriff.oauth.core.ParserConfig;
import de.cuioss.sheriff.oauth.core.json.JwtHeader;
import de.cuioss.sheriff.oauth.core.json.MapRepresentation;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DecodedJwt}.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("Tests for DecodedJwt")
class DecodedJwtTest {

    private static final String ISSUER = "https://test-issuer.com";
    private static final String KID = "test-key-id";
    private static final String ALG = "RS256";
    private static final String SIGNATURE = "test-signature";
    private static final String RAW_TOKEN = "header.payload.signature";
    private static final String[] PARTS = {"header", "payload", "signature"};

    @Test
    @DisplayName("Should create DecodedJwt with all values")
    void shouldCreateDecodedJwtWithAllValues() {

        JwtHeader header = createTestHeader();
        MapRepresentation body = createTestBody();
        DecodedJwt jwt = new DecodedJwt(header, body, SIGNATURE, PARTS, RAW_TOKEN);

        JwtHeader actualHeader = jwt.header();
        assertNotNull(actualHeader);
        assertEquals(ALG, actualHeader.alg());
        assertEquals(KID, actualHeader.getKid().orElse(null));

        MapRepresentation actualBody = jwt.body();
        assertNotNull(actualBody);
        assertEquals(ISSUER, actualBody.getString("iss").orElse(null));
        assertEquals("test-subject", actualBody.getString("sub").orElse(null));

        assertNotNull(jwt.signature());
        assertEquals(SIGNATURE, jwt.signature());

        assertTrue(jwt.getIssuer().isPresent());
        assertEquals(ISSUER, jwt.getIssuer().get());

        assertTrue(jwt.getKid().isPresent());
        assertEquals(KID, jwt.getKid().get());

        assertTrue(jwt.getAlg().isPresent());
        assertEquals(ALG, jwt.getAlg().get());

        assertEquals(PARTS, jwt.parts());
        assertEquals(RAW_TOKEN, jwt.rawToken());
    }

    @Test
    @DisplayName("Should reject null body")
    void shouldRejectNullBody() {
        assertThrows(NullPointerException.class,
                () -> new DecodedJwt(null, null, null, PARTS, RAW_TOKEN));
    }

    @Test
    @DisplayName("Should create DecodedJwt with null header but non-null body")
    void shouldCreateDecodedJwtWithNullHeader() {

        MapRepresentation body = MapRepresentation.empty();
        DecodedJwt jwt = new DecodedJwt(null, body, SIGNATURE, PARTS, RAW_TOKEN);
        JwtHeader actualHeader = jwt.header();
        assertNull(actualHeader);

        assertNotNull(jwt.body());

        assertNotNull(jwt.signature());
        assertEquals(SIGNATURE, jwt.signature());

        assertFalse(jwt.getIssuer().isPresent());
        assertFalse(jwt.getKid().isPresent());
        assertFalse(jwt.getAlg().isPresent());

        assertEquals(PARTS, jwt.parts());
        assertEquals(RAW_TOKEN, jwt.rawToken());
    }

    @Test
    @DisplayName("Should create DecodedJwt using builder")
    void shouldCreateDecodedJwtUsingBuilder() {

        JwtHeader header = createTestHeader();
        MapRepresentation body = createTestBody();
        DecodedJwt jwt = DecodedJwt.builder()
                .header(header)
                .body(body)
                .signature(SIGNATURE)
                .parts(PARTS)
                .rawToken(RAW_TOKEN)
                .build();
        JwtHeader actualHeader = jwt.header();
        assertNotNull(actualHeader);
        assertEquals(ALG, actualHeader.alg());
        assertEquals(KID, actualHeader.getKid().orElse(null));

        MapRepresentation actualBody = jwt.body();
        assertNotNull(actualBody);
        assertEquals(ISSUER, actualBody.getString("iss").orElse(null));
        assertEquals("test-subject", actualBody.getString("sub").orElse(null));

        assertNotNull(jwt.signature());
        assertEquals(SIGNATURE, jwt.signature());

        assertTrue(jwt.getIssuer().isPresent());
        assertEquals(ISSUER, jwt.getIssuer().get());

        assertTrue(jwt.getKid().isPresent());
        assertEquals(KID, jwt.getKid().get());

        assertTrue(jwt.getAlg().isPresent());
        assertEquals(ALG, jwt.getAlg().get());

        assertEquals(PARTS, jwt.parts());
        assertEquals(RAW_TOKEN, jwt.rawToken());
    }

    @Test
    @DisplayName("Builder should reject null header")
    void builderShouldRejectNullHeader() {
        var builder = DecodedJwt.builder()
                .body(createTestBody())
                .signature(SIGNATURE)
                .parts(PARTS)
                .rawToken(RAW_TOKEN);
        assertThrows(NullPointerException.class, builder::build,
                "Builder should throw NullPointerException when header is null");
    }

    @Test
    @DisplayName("Builder should reject null rawToken")
    void builderShouldRejectNullRawToken() {
        var builder = DecodedJwt.builder()
                .header(createTestHeader())
                .body(createTestBody())
                .signature(SIGNATURE)
                .parts(PARTS);
        assertThrows(NullPointerException.class, builder::build,
                "Builder should throw NullPointerException when rawToken is null");
    }

    @Test
    @DisplayName("Should have proper equals and hashCode")
    void shouldHaveProperEqualsAndHashCode() {

        JwtHeader header1 = createTestHeader();
        MapRepresentation body1 = createTestBody();
        DecodedJwt jwt1 = new DecodedJwt(header1, body1, SIGNATURE, PARTS, RAW_TOKEN);

        JwtHeader header2 = createTestHeader();
        MapRepresentation body2 = createTestBody();
        DecodedJwt jwt2 = new DecodedJwt(header2, body2, SIGNATURE, PARTS, RAW_TOKEN);
        assertEquals(jwt1, jwt2);
        assertEquals(jwt1.hashCode(), jwt2.hashCode());
    }

    @Test
    @DisplayName("Should properly compare array fields in equals")
    void shouldProperlyCompareArrayFieldsInEquals() {
        JwtHeader header = createTestHeader();
        MapRepresentation body = createTestBody();

        // Create two identical arrays with same content but different references
        String[] parts1 = {"header", "payload", "signature"};
        String[] parts2 = {"header", "payload", "signature"};

        DecodedJwt jwt1 = new DecodedJwt(header, body, SIGNATURE, parts1, RAW_TOKEN);
        DecodedJwt jwt2 = new DecodedJwt(header, body, SIGNATURE, parts2, RAW_TOKEN);

        // Should be equal based on content, not reference
        assertEquals(jwt1, jwt2, "DecodedJwt objects should be equal when arrays have same content");
        assertEquals(jwt1.hashCode(), jwt2.hashCode(), "Hash codes should be equal for equal objects");

        // Test with different array content
        String[] differentParts = {"different", "content", "here"};
        DecodedJwt jwt3 = new DecodedJwt(header, body, SIGNATURE, differentParts, RAW_TOKEN);

        assertNotEquals(jwt1, jwt3, "DecodedJwt objects should not be equal when arrays have different content");
    }

    @Test
    @DisplayName("Should have proper toString")
    void shouldHaveProperToString() {

        JwtHeader header = createTestHeader();
        MapRepresentation body = createTestBody();
        DecodedJwt jwt = new DecodedJwt(header, body, SIGNATURE, PARTS, RAW_TOKEN);
        String toString = jwt.toString();
        assertNotNull(toString, "toString() result should not be null");
        assertTrue(toString.contains(ISSUER));
        assertTrue(toString.contains(KID));
        assertTrue(toString.contains(ALG));
        assertTrue(toString.contains(SIGNATURE));

        // Verify array is properly represented in toString
        assertTrue(toString.contains("[header, payload, signature]"),
                "toString should include array content representation");
        assertTrue(toString.contains("DecodedJwt["),
                "toString should follow expected format");
    }

    @Test
    @DisplayName("Should decode signature bytes correctly")
    void shouldDecodeSignatureBytesCorrectly() {
        // Create a test JWT with proper Base64URL-encoded parts
        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString("header".getBytes());
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString("payload".getBytes());
        String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString("test-signature-bytes".getBytes());
        String[] validParts = {encodedHeader, encodedPayload, encodedSignature};

        JwtHeader header = createTestHeader();
        MapRepresentation body = createTestBody();
        DecodedJwt jwt = new DecodedJwt(header, body, encodedSignature, validParts, "header.payload.signature");

        // Test successful decoding
        byte[] decodedBytes = jwt.getSignatureAsDecodedBytes();
        assertNotNull(decodedBytes);
        assertArrayEquals("test-signature-bytes".getBytes(), decodedBytes);
    }

    @Test
    @DisplayName("Should throw IllegalStateException for invalid JWT format")
    void shouldThrowIllegalStateExceptionForInvalidFormat() {
        JwtHeader header = createTestHeader();
        MapRepresentation body = createTestBody();

        // Test with null parts
        DecodedJwt jwtNullParts = new DecodedJwt(header, body, SIGNATURE, null, RAW_TOKEN);
        IllegalStateException exception = assertThrows(IllegalStateException.class, jwtNullParts::getSignatureAsDecodedBytes);
        assertTrue(exception.getMessage().contains("JWT format is invalid"));
        assertTrue(exception.getMessage().contains("null"));

        // Test with wrong number of parts
        String[] wrongParts = {"header", "payload"};
        DecodedJwt jwtWrongParts = new DecodedJwt(header, body, SIGNATURE, wrongParts, RAW_TOKEN);
        exception = assertThrows(IllegalStateException.class, jwtWrongParts::getSignatureAsDecodedBytes);
        assertTrue(exception.getMessage().contains("JWT format is invalid"));
        assertTrue(exception.getMessage().contains("2"));
    }

    @Test
    @DisplayName("Should throw IllegalStateException for invalid Base64URL signature")
    void shouldThrowIllegalStateExceptionForInvalidBase64() {
        JwtHeader header = createTestHeader();
        MapRepresentation body = createTestBody();

        // Create parts with invalid Base64URL in signature part
        String[] invalidBase64Parts = {"header", "payload", "invalid@base64!signature"};
        DecodedJwt jwt = new DecodedJwt(header, body, "invalid@base64!signature", invalidBase64Parts, RAW_TOKEN);

        IllegalStateException exception = assertThrows(IllegalStateException.class, jwt::getSignatureAsDecodedBytes);
        assertTrue(exception.getMessage().contains("Failed to decode signature from Base64URL format"));
        assertNotNull(exception.getCause());
        assertEquals(IllegalArgumentException.class, exception.getCause().getClass());
    }

    @Test
    @DisplayName("Should get data to verify correctly")
    void shouldGetDataToVerifyCorrectly() {
        // Create a test JWT with proper parts
        String[] validParts = {"encodedHeader", "encodedPayload", "encodedSignature"};

        JwtHeader header = createTestHeader();
        MapRepresentation body = createTestBody();
        DecodedJwt jwt = new DecodedJwt(header, body, "encodedSignature", validParts, "header.payload.signature");

        // Test successful data extraction
        String dataToVerify = jwt.getDataToVerify();
        assertEquals("encodedHeader.encodedPayload", dataToVerify);
    }

    @Test
    @DisplayName("Should throw IllegalStateException for invalid JWT format when getting data to verify")
    void shouldThrowIllegalStateExceptionForInvalidFormatWhenGettingDataToVerify() {
        JwtHeader header = createTestHeader();
        MapRepresentation body = createTestBody();

        // Test with null parts
        DecodedJwt jwtNullParts = new DecodedJwt(header, body, SIGNATURE, null, RAW_TOKEN);
        IllegalStateException exception = assertThrows(IllegalStateException.class, jwtNullParts::getDataToVerify);
        assertTrue(exception.getMessage().contains("JWT format is invalid"));
        assertTrue(exception.getMessage().contains("null"));

        // Test with wrong number of parts
        String[] wrongParts = {"header", "payload"};
        DecodedJwt jwtWrongParts = new DecodedJwt(header, body, SIGNATURE, wrongParts, RAW_TOKEN);
        exception = assertThrows(IllegalStateException.class, jwtWrongParts::getDataToVerify);
        assertTrue(exception.getMessage().contains("JWT format is invalid"));
        assertTrue(exception.getMessage().contains("2"));
    }

    private JwtHeader createTestHeader() {
        try {
            String headerJson = "{\"alg\":\"" + ALG + "\",\"kid\":\"" + KID + "\"}";
            DslJson<Object> dslJson = ParserConfig.builder().build().getDslJson();
            byte[] headerBytes = headerJson.getBytes();
            return dslJson.deserialize(JwtHeader.class, headerBytes, headerBytes.length);
        } catch (IOException e) {
            throw new AssertionError("Failed to create test header", e);
        }
    }

    private MapRepresentation createTestBody() {
        try {
            String bodyJson = "{\"iss\":\"" + ISSUER + "\",\"sub\":\"test-subject\"}";
            DslJson<Object> dslJson = ParserConfig.builder().build().getDslJson();
            return MapRepresentation.fromJson(dslJson, bodyJson);
        } catch (IOException e) {
            throw new AssertionError("Failed to create test body", e);
        }
    }

}