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
package de.cuioss.sheriff.token.validation.domain.token;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter.EventType;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.cache.CachedToken;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.test.JweTestTokenFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the native-JSON claim reader: {@link TokenValidator#getRawClaims(TokenContent)} for a validated
 * token and {@link SignatureVerifiedToken#getRawClaims()} for a signature-verified one.
 * <p>
 * Every payload is written as literal JSON and signed directly, so member order, nesting, {@code null}
 * members and number shapes reach the parser exactly as written. Each bound refusal is paired with a
 * positive control that reads a payload within the bound.
 */
@DisplayName("Native JSON claims of validated and signature-verified tokens")
class RawClaimsTest {

    private static final String ISSUER = "https://raw-claims.example.com";
    private static final String AUDIENCE = "raw-claims-audience";

    private final List<TokenValidator> validators = new ArrayList<>();

    @AfterEach
    void closeValidators() {
        validators.forEach(TokenValidator::close);
    }

    @Nested
    @DisplayName("JSON structure")
    class Structure {

        @Test
        @DisplayName("Nested objects keep their type and member order")
        void shouldKeepNestedObjectTypeAndOrder() {
            Map<String, Object> claims = readValidated("\"outer\":{\"z\":\"last-letter\",\"a\":{\"m\":1,\"b\":2},\"k\":true}");

            Map<?, ?> outer = assertInstanceOf(Map.class, claims.get("outer"));
            assertEquals(List.of("z", "a", "k"), List.copyOf(outer.keySet()));
            Map<?, ?> inner = assertInstanceOf(Map.class, outer.get("a"));
            assertEquals(List.of("m", "b"), List.copyOf(inner.keySet()));
            assertEquals("last-letter", outer.get("z"));
        }

        @Test
        @DisplayName("Arrays keep their type and element order, including nested arrays and objects")
        void shouldKeepArrayTypeAndOrder() {
            Map<String, Object> claims = readValidated("\"list\":[\"c\",\"a\",[3,1],{\"x\":\"y\"},\"b\"]");

            List<?> list = assertInstanceOf(List.class, claims.get("list"));
            assertEquals(5, list.size());
            assertEquals("c", list.getFirst());
            assertEquals("a", list.get(1));
            assertEquals(List.of(3L, 1L), list.get(2));
            assertEquals(Map.of("x", "y"), list.get(3));
            assertEquals("b", list.get(4));
        }

        @Test
        @DisplayName("Top-level members keep the order of the payload")
        void shouldKeepTopLevelOrder() {
            Map<String, Object> claims = readValidated("\"zeta\":1,\"alpha\":2");

            List<String> keys = List.copyOf(claims.keySet());
            assertTrue(keys.indexOf("zeta") < keys.indexOf("alpha"), "zeta is written before alpha: " + keys);
            assertEquals(ISSUER, claims.get("iss"), "the standard claims are part of the payload map");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("The map, nested maps and nested lists all refuse mutation")
        void shouldBeDeeplyImmutable() {
            Map<String, Object> claims = readValidated("\"obj\":{\"inner\":{\"k\":\"v\"}},\"arr\":[[\"x\"],{\"k\":\"v\"}]");

            Map<String, Object> obj = asMap(claims.get("obj"));
            Map<String, Object> inner = asMap(obj.get("inner"));
            List<Object> arr = asList(claims.get("arr"));
            List<Object> nestedList = asList(arr.getFirst());
            Map<String, Object> mapInList = asMap(arr.get(1));

            assertThrows(UnsupportedOperationException.class, () -> claims.put("new", "value"));
            assertThrows(UnsupportedOperationException.class, () -> claims.remove("iss"));
            assertThrows(UnsupportedOperationException.class, () -> obj.put("new", "value"));
            assertThrows(UnsupportedOperationException.class, () -> inner.put("k", "changed"));
            assertThrows(UnsupportedOperationException.class, () -> arr.add("new"));
            assertThrows(UnsupportedOperationException.class, () -> arr.set(0, "changed"));
            assertThrows(UnsupportedOperationException.class, () -> nestedList.add("new"));
            assertThrows(UnsupportedOperationException.class, mapInList::clear);
        }

        @Test
        @DisplayName("Each call returns a fresh copy equal to the previous one")
        void shouldReturnEqualCopiesPerCall() {
            TokenValidator validator = validator();
            AccessTokenContent content = validator.createAccessToken(AccessTokenRequest.of(token("\"obj\":{\"k\":[1,2]}")));

            assertEquals(validator.getRawClaims(content), validator.getRawClaims(content));
        }

        @Test
        @DisplayName("The signature-verified raw claims refuse mutation at every level")
        void shouldBeDeeplyImmutableForSignatureVerifiedToken() {
            Map<String, Object> claims = validator().verifySignature(token("\"obj\":{\"arr\":[1]}")).getRawClaims();
            Map<String, Object> obj = asMap(claims.get("obj"));
            List<Object> arr = asList(obj.get("arr"));

            assertThrows(UnsupportedOperationException.class, () -> claims.put("new", "value"));
            assertThrows(UnsupportedOperationException.class, () -> obj.remove("arr"));
            assertThrows(UnsupportedOperationException.class, () -> arr.add(2L));
        }
    }

    @Nested
    @DisplayName("null members")
    class NullMembers {

        @Test
        @DisplayName("A top-level null member is omitted")
        void shouldOmitTopLevelNull() {
            Map<String, Object> claims = readValidated("\"gone\":null,\"kept\":\"value\"");

            assertFalse(claims.containsKey("gone"));
            assertEquals("value", claims.get("kept"), "positive control: the sibling member is read");
        }

        @Test
        @DisplayName("A null nested inside an object or an array is kept")
        void shouldKeepNestedNull() {
            Map<String, Object> claims = readValidated("\"obj\":{\"empty\":null,\"k\":\"v\"},\"arr\":[\"a\",null,\"b\"]");

            Map<String, Object> obj = asMap(claims.get("obj"));
            assertTrue(obj.containsKey("empty"), "the nested null member is kept");
            assertNull(obj.get("empty"));
            assertEquals(Arrays.asList("a", null, "b"), claims.get("arr"));
        }
    }

    @Nested
    @DisplayName("JSON scalar types")
    class Scalars {

        @Test
        @DisplayName("Large, negative and fractional numbers keep their value")
        void shouldKeepNumbers() {
            Map<String, Object> claims = readValidated("\"large\":9007199254740993,\"negative\":-42,\"fraction\":3.25");

            assertEquals(9007199254740993L, assertInstanceOf(Long.class, claims.get("large")),
                    "an integer above 2^53 keeps every digit");
            assertEquals(-42L, assertInstanceOf(Long.class, claims.get("negative")));
            Number fraction = assertInstanceOf(Number.class, claims.get("fraction"));
            assertEquals(0, new BigDecimal("3.25").compareTo(new BigDecimal(fraction.toString())),
                    "the fraction keeps its value: " + fraction);
        }

        @Test
        @DisplayName("Booleans, numbers and strings stay distinguishable")
        void shouldKeepScalarTypesDistinguishable() {
            Map<String, Object> claims = readValidated(
                    "\"boolTrue\":true,\"boolFalse\":false,\"stringTrue\":\"true\",\"number\":7,\"stringNumber\":\"7\"");

            assertEquals(Boolean.TRUE, claims.get("boolTrue"));
            assertEquals(Boolean.FALSE, claims.get("boolFalse"));
            assertEquals("true", claims.get("stringTrue"));
            assertEquals(7L, claims.get("number"));
            assertEquals("7", claims.get("stringNumber"));
            assertNotEquals(claims.get("boolTrue"), claims.get("stringTrue"));
            assertNotEquals(claims.get("number"), claims.get("stringNumber"));
        }
    }

    @Nested
    @DisplayName("ParserConfig bounds of the reading validator")
    class Bounds {

        @Test
        @DisplayName("A payload nested deeper than maxNestingDepth is refused")
        void shouldRefuseTooDeepPayload() {
            assertBoundApplied(ParserConfig.builder().maxNestingDepth(3).build(), EventType.JSON_STRUCTURE_BOUNDS_EXCEEDED,
                    "\"deep\":{\"a\":{\"b\":{\"c\":1}}}", "\"flat\":{\"a\":{\"b\":1}}");
        }

        @Test
        @DisplayName("A payload larger than maxPayloadSize is refused")
        void shouldRefuseTooLargePayload() {
            String filler = "x".repeat(600);
            assertBoundApplied(ParserConfig.builder().maxPayloadSize(700).build(), EventType.DECODED_PART_SIZE_EXCEEDED,
                    "\"filler\":\"" + filler + "\"", "\"small\":\"x\"");
        }

        @Test
        @DisplayName("An array larger than maxArraySize is refused")
        void shouldRefuseTooLargeArray() {
            assertBoundApplied(ParserConfig.builder().maxArraySize(3).build(), EventType.JSON_STRUCTURE_BOUNDS_EXCEEDED,
                    "\"arr\":[1,2,3,4,5]", "\"arr\":[1,2,3]");
        }

        @Test
        @DisplayName("A string longer than maxStringLength is refused")
        void shouldRefuseTooLongString() {
            assertBoundApplied(ParserConfig.builder().maxStringLength(64).build(), EventType.FAILED_TO_DECODE_JWT,
                    "\"long\":\"" + "y".repeat(300) + "\"", "\"short\":\"y\"");
        }

        /**
         * Validates both tokens with a default validator, then reads them with a validator bound by
         * {@code tight}: the over-limit payload is refused as a whole with {@code expected}, the
         * within-limit one is read.
         */
        private void assertBoundApplied(ParserConfig tight, EventType expected, String overLimitMembers,
                String withinLimitMembers) {
            TokenValidator lenient = validator();
            AccessTokenContent overLimit = lenient.createAccessToken(AccessTokenRequest.of(token(overLimitMembers)));
            AccessTokenContent withinLimit = lenient.createAccessToken(AccessTokenRequest.of(token(withinLimitMembers)));
            assertNotNull(lenient.getRawClaims(overLimit), "precondition: the default bounds admit the payload");
            TokenValidator bounded = register(TokenValidator.builder()
                    .parserConfig(tight)
                    .issuerConfig(issuerConfig())
                    .build());

            TokenValidationException refused = assertThrows(TokenValidationException.class,
                    () -> bounded.getRawClaims(overLimit));

            assertEquals(expected, refused.getEventType());
            assertEquals(ISSUER, bounded.getRawClaims(withinLimit).get("iss"), "positive control within the bound");
        }
    }

    @Nested
    @DisplayName("JWE")
    class Jwe {

        @Test
        @DisplayName("For a JWE the payload of the decrypted inner token is returned")
        void shouldReadInnerPayloadOfJwe() {
            KeyPair encryptionKeys = JweTestTokenFactory.generateRsaKeyPair();
            String jwe = JweTestTokenFactory.encryptAsJwe(token("\"inner\":{\"secret\":\"payload\"}"),
                    encryptionKeys.getPublic(), "RSA-OAEP", "A256GCM", null);
            TokenValidator validator = register(TokenValidator.builder()
                    .issuerConfig(issuerConfig())
                    .jweDecryptionConfig(JweDecryptionConfig.builder()
                            .defaultDecryptionKey(encryptionKeys.getPrivate()).build())
                    .build());
            AccessTokenContent content = validator.createAccessToken(AccessTokenRequest.of(jwe));

            Map<String, Object> claims = validator.getRawClaims(content);

            assertEquals(jwe, content.getRawToken(), "precondition: the content carries the JWE as presented");
            assertEquals(Map.of("secret", "payload"), claims.get("inner"));
            assertEquals(ISSUER, claims.get("iss"));
            assertEquals(claims, validator.verifySignature(jwe).getRawClaims(),
                    "the signature-only entry reads the same inner payload");
        }
    }

    @Nested
    @DisplayName("Footprint of the validated token")
    class Footprint {

        @Test
        @DisplayName("AccessTokenContent and CachedToken carry no field for raw claims")
        void shouldAddNoField() {
            assertEquals(Set.of("claims", "rawToken", "tokenType"), instanceFieldNames(AccessTokenContent.class));
            assertEquals(Set.of("rawToken", "content", "expirationTime", "clockSkewSeconds", "maxTokenAgeSeconds"),
                    instanceFieldNames(CachedToken.class));
        }

        @Test
        @DisplayName("The serialized size of a cached content is unchanged by reading its raw claims")
        void shouldKeepSerializedSize() {
            TokenValidator validator = validator();
            String token = token("\"obj\":{\"k\":[1,2,3]}");
            AccessTokenContent content = validator.createAccessToken(AccessTokenRequest.of(token));
            AccessTokenContent cached = validator.createAccessToken(AccessTokenRequest.of(token));
            assertSame(content, cached, "precondition: the second call is served from the cache");
            int before = serializedSize(cached);

            assertFalse(validator.getRawClaims(cached).isEmpty());

            assertEquals(before, serializedSize(cached));
            assertSame(cached, validator.createAccessToken(AccessTokenRequest.of(token)),
                    "the cache entry is the same instance after the read");
        }
    }

    @Nested
    @DisplayName("Signature-verified token")
    class SignatureVerified {

        @Test
        @DisplayName("The raw claims equal the validator re-parse of the same token")
        void shouldEqualValidatorReParse() {
            TokenValidator validator = validator();
            String token = token("\"obj\":{\"empty\":null,\"arr\":[1,\"two\",false]},\"gone\":null,\"fraction\":-0.5");

            Map<String, Object> verified = validator.verifySignature(token).getRawClaims();
            Map<String, Object> reParsed = validator.getRawClaims(validator.createAccessToken(AccessTokenRequest.of(token)));

            assertEquals(reParsed, verified);
            assertEquals(List.copyOf(reParsed.keySet()), List.copyOf(verified.keySet()), "same member order");
            assertFalse(verified.containsKey("gone"));
        }
    }

    // === Fixtures ===

    private Map<String, Object> readValidated(String extraMembers) {
        TokenValidator validator = validator();
        return validator.getRawClaims(validator.createAccessToken(AccessTokenRequest.of(token(extraMembers))));
    }

    private static IssuerConfig issuerConfig() {
        return IssuerConfig.builder()
                .issuerIdentifier(ISSUER)
                .expectedAudience(AUDIENCE)
                .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                .build();
    }

    private TokenValidator validator() {
        return register(TokenValidator.builder().issuerConfig(issuerConfig()).build());
    }

    private TokenValidator register(TokenValidator validator) {
        validators.add(validator);
        return validator;
    }

    /**
     * Signs a payload of the valid standard claims followed by {@code extraMembers} with the default RS256
     * test key. The payload is literal JSON, so member order, {@code null} members and number shapes are
     * exactly as written.
     *
     * @param extraMembers comma-separated JSON members appended after the standard claims
     * @return the compact JWS
     */
    private static String token(String extraMembers) {
        long now = Instant.now().getEpochSecond();
        String header = "{\"alg\":\"RS256\",\"kid\":\"" + InMemoryKeyMaterialHandler.DEFAULT_KEY_ID + "\"}";
        String payload = "{\"iss\":\"" + ISSUER + "\",\"sub\":\"raw-subject\",\"aud\":\"" + AUDIENCE
                + "\",\"azp\":\"raw-client\",\"iat\":" + now + ",\"exp\":" + (now + 3600)
                + ",\"scope\":\"openid\"," + extraMembers + "}";
        String signingInput = base64Url(header) + "." + base64Url(payload);
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(InMemoryKeyMaterialHandler.getDefaultPrivateKey());
            signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign the test token", e);
        }
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return assertInstanceOf(Map.class, value);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return assertInstanceOf(List.class, value);
    }

    private static Set<String> instanceFieldNames(Class<?> type) {
        Set<String> names = new TreeSet<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    names.add(field.getName());
                }
            }
        }
        return names;
    }

    private static int serializedSize(Serializable value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize " + value.getClass().getSimpleName(), e);
        }
        return bytes.size();
    }
}
