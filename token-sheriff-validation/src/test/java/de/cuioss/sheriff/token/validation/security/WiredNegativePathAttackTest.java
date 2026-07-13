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
package de.cuioss.sheriff.token.validation.security;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter.EventType;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.dpop.DpopConfig;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.test.JweTestTokenFactory;
import de.cuioss.sheriff.token.validation.util.JwkThumbprintUtil;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Wired negative-path attack tier (H4).
 * <p>
 * Every test here drives the <em>real</em> {@link TokenValidator} entry point with a crafted attack
 * and asserts the <em>specific</em> rejection {@link EventType} the corresponding defense emits — never
 * a trivially-true {@code >= 0} counter check. Each attack is constructed so that the assertion cannot
 * be satisfied by an incidental failure earlier in the pipeline (for example the embedded-JWK token is
 * re-signed so a naive verifier would accept it, and the cross-issuer substitution uses a real signature
 * from the wrong key rather than a corrupted one).
 * <p>
 * The six defenses proven on the wired path:
 * <ul>
 *   <li>VTEST-2 — HMAC/RSA algorithm confusion (HS256 signed over the RSA public key)</li>
 *   <li>VTEST-3 — JWE decompression bomb ({@code zip:"DEF"} inflating past the 256&nbsp;KB ceiling)</li>
 *   <li>VTEST-6 — cross-issuer key substitution (same {@code kid}, different key material)</li>
 *   <li>VTEST-7 — JWE key-management algorithm allow-list ({@code RSA1_5})</li>
 *   <li>VTEST-8 — DPoP sender-binding (cnf.jkt mismatch, stale iat, and HS256 proof)</li>
 *   <li>VTEST-9 — embedded-JWK header injection with a re-signed token</li>
 * </ul>
 */
@DisplayName("Wired Negative-Path Attack Tests (H4)")
class WiredNegativePathAttackTest {

    private static final String TEST_ISSUER = "https://wired-attack-test.example.com";
    private static final String TEST_AUDIENCE = "test-audience";
    private static final String DEFAULT_KEY_ID = InMemoryKeyMaterialHandler.DEFAULT_KEY_ID;
    private static final String RESOURCE_URI = "https://resource.example.org/protectedresource";
    private static final String RESOURCE_METHOD = "GET";

    @Test
    @DisplayName("VTEST-2: HMAC/RSA algorithm confusion is rejected as UNSUPPORTED_ALGORITHM")
    void shouldRejectHmacRsaAlgorithmConfusion() {
        // Attacker forges an HS256 token, using the issuer's RSA *public* key bytes as the HMAC secret.
        RSAPublicKey issuerPublicKey = (RSAPublicKey) InMemoryKeyMaterialHandler.getDefaultPublicKey();
        var hmacKey = new SecretKeySpec(issuerPublicKey.getEncoded(), "HmacSHA256");
        String forged = Jwts.builder()
                .header().keyId(DEFAULT_KEY_ID).and()
                .issuer(TEST_ISSUER)
                .subject("attacker")
                .audience().add(TEST_AUDIENCE).and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(hmacKey, Jwts.SIG.HS256)
                .compact();

        TokenValidator validator = plainValidator();
        var request = AccessTokenRequest.of(forged);
        var ex = assertThrows(TokenValidationException.class, () -> validator.createAccessToken(request));
        assertEquals(EventType.UNSUPPORTED_ALGORITHM, ex.getEventType(),
                "HS256-over-RSA-public-key confusion must be rejected as an unsupported algorithm");
    }

    @Test
    @DisplayName("VTEST-3: JWE decompression bomb is rejected as JWE_DECRYPTION_FAILED")
    void shouldRejectJweDecompressionBomb() {
        KeyPair jweKeyPair = JweTestTokenFactory.generateRsaKeyPair();
        // A tiny compressed payload that inflates to 512 KB — past the decryptor's 256 KB ceiling.
        byte[] bomb = new byte[512 * 1024];
        Arrays.fill(bomb, (byte) 'A');
        String jwe = JweTestTokenFactory.createCompressedJweFromPlaintext(
                bomb, jweKeyPair.getPublic(), "RSA-OAEP", "A256GCM");

        TokenValidator validator = jweValidator(jweKeyPair.getPrivate());
        var request = AccessTokenRequest.of(jwe);
        var ex = assertThrows(TokenValidationException.class, () -> validator.createAccessToken(request));
        assertEquals(EventType.JWE_DECRYPTION_FAILED, ex.getEventType(),
                "A JWE whose decompressed content exceeds the ceiling must be rejected before parsing");
    }

    @Test
    @DisplayName("VTEST-6: cross-issuer key substitution is rejected as SIGNATURE_VALIDATION_FAILED")
    void shouldRejectCrossIssuerKeySubstitution() {
        // Sign with an attacker RSA key but present the issuer's kid; the issuer is configured with a
        // different key under the same kid, so the real signature verifies against the wrong material.
        KeyPair attackerKeyPair = generateRsaKeyPair();
        String forged = Jwts.builder()
                .header().keyId(DEFAULT_KEY_ID).and()
                .issuer(TEST_ISSUER)
                .subject("attacker")
                .audience().add(TEST_AUDIENCE).and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(attackerKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        TokenValidator validator = plainValidator();
        var request = AccessTokenRequest.of(forged);
        var ex = assertThrows(TokenValidationException.class, () -> validator.createAccessToken(request));
        assertEquals(EventType.SIGNATURE_VALIDATION_FAILED, ex.getEventType(),
                "A real signature from a substituted key under the same kid must fail verification");
    }

    @Test
    @DisplayName("VTEST-7: unsupported JWE key-management algorithm is rejected as JWE_UNSUPPORTED_ALGORITHM")
    void shouldRejectUnsupportedJweAlgorithm() {
        KeyPair jweKeyPair = JweTestTokenFactory.generateRsaKeyPair();
        // RSA1_5 (RSAES-PKCS1-v1_5) is off the allow-list; the alg check fires before any decryption.
        String header = base64Url("{\"alg\":\"RSA1_5\",\"enc\":\"A256GCM\"}".getBytes(StandardCharsets.UTF_8));
        String jwe = header + ".AAAA.AAAA.AAAA.AAAA";

        TokenValidator validator = jweValidator(jweKeyPair.getPrivate());
        var request = AccessTokenRequest.of(jwe);
        var ex = assertThrows(TokenValidationException.class, () -> validator.createAccessToken(request));
        assertEquals(EventType.JWE_UNSUPPORTED_ALGORITHM, ex.getEventType(),
                "A JWE using an off-allow-list key-management algorithm must be rejected");
    }

    @Test
    @DisplayName("VTEST-8: DPoP sender-binding violations are each rejected with their specific event")
    void shouldRejectDpopSenderBindingViolations() {
        KeyPair dpopKeyPair = generateRsaKeyPair();
        Map<String, Object> dpopJwk = rsaPublicKeyToJwkMap((RSAPublicKey) dpopKeyPair.getPublic());
        String boundThumbprint = JwkThumbprintUtil.computeThumbprint(dpopJwk);
        long now = System.currentTimeMillis() / 1000;

        // DP2 — cnf.jkt mismatch: the access token is bound to a *different* key thumbprint than the proof.
        String tokenWrongCnf = signedAccessToken("a-different-thumbprint-value");
        String freshProof = dpopProof(dpopKeyPair, dpopJwk, tokenWrongCnf, now);
        var mismatchEx = assertThrows(TokenValidationException.class,
                () -> dpopValidator().createAccessToken(dpopRequest(tokenWrongCnf, freshProof)));
        assertEquals(EventType.DPOP_THUMBPRINT_MISMATCH, mismatchEx.getEventType(),
                "A cnf.jkt not matching the proof key must be rejected as a thumbprint mismatch");

        // DP3 — stale iat: the proof is correctly bound but its iat is far outside the freshness window.
        String tokenBound = signedAccessToken(boundThumbprint);
        String staleProof = dpopProof(dpopKeyPair, dpopJwk, tokenBound, now - 100_000);
        var staleEx = assertThrows(TokenValidationException.class,
                () -> dpopValidator().createAccessToken(dpopRequest(tokenBound, staleProof)));
        assertEquals(EventType.DPOP_PROOF_EXPIRED, staleEx.getEventType(),
                "A DPoP proof with a stale iat must be rejected as expired");

        // DP4 — symmetric proof algorithm: an HS256 proof is off the asymmetric allow-list.
        String hsProof = hs256DpopProof(tokenBound);
        var hsEx = assertThrows(TokenValidationException.class,
                () -> dpopValidator().createAccessToken(dpopRequest(tokenBound, hsProof)));
        assertEquals(EventType.DPOP_PROOF_INVALID, hsEx.getEventType(),
                "An HS256 DPoP proof must be rejected as an invalid proof");
    }

    @Test
    @DisplayName("VTEST-9: embedded-JWK header injection with a re-signed token is rejected as UNSUPPORTED_ALGORITHM")
    void shouldRejectEmbeddedJwkHeaderInjection() {
        // Attacker embeds their own public key in the header and re-signs the token with the matching
        // private key, so a verifier that trusted the embedded key would accept it. The library must
        // reject any token carrying a jwk header outright, before ever reaching signature verification.
        // The jwk header parameter is carried as a String (JwtHeader stores it as a String for DSL-JSON
        // compatibility); the embedded key material below is the attacker public key.
        KeyPair attackerKeyPair = generateRsaKeyPair();
        RSAPublicKey attackerPublicKey = (RSAPublicKey) attackerKeyPair.getPublic();
        String embeddedKey = base64Url(toUnsignedBytes(attackerPublicKey.getModulus()));
        String headerJson = "{\"alg\":\"RS256\",\"kid\":\"%s\",\"jwk\":\"%s\"}".formatted(DEFAULT_KEY_ID, embeddedKey);
        String payloadJson = ("{\"iss\":\"%s\",\"sub\":\"attacker\",\"aud\":\"%s\",\"iat\":%d,\"exp\":%d}").formatted(
                TEST_ISSUER, TEST_AUDIENCE,
                System.currentTimeMillis() / 1000, (System.currentTimeMillis() / 1000) + 3600);
        String signingInput = base64Url(headerJson.getBytes(StandardCharsets.UTF_8)) + "."
                + base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
        String forged = signingInput + "." + base64Url(rsaSign(signingInput, attackerKeyPair.getPrivate()));

        TokenValidator validator = plainValidator();
        var request = AccessTokenRequest.of(forged);
        var ex = assertThrows(TokenValidationException.class, () -> validator.createAccessToken(request));
        assertEquals(EventType.UNSUPPORTED_ALGORITHM, ex.getEventType(),
                "A token carrying an embedded JWK header must be rejected regardless of a valid re-signature");
    }

    // === Validators ===

    private TokenValidator plainValidator() {
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier(TEST_ISSUER)
                .expectedAudience(TEST_AUDIENCE)
                .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                .build();
        return TokenValidator.builder().parserConfig(ParserConfig.builder().build())
                .issuerConfig(issuerConfig).build();
    }

    private TokenValidator jweValidator(PrivateKey decryptionKey) {
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier(TEST_ISSUER)
                .expectedAudience(TEST_AUDIENCE)
                .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                .build();
        return TokenValidator.builder().parserConfig(ParserConfig.builder().build())
                .issuerConfig(issuerConfig)
                .jweDecryptionConfig(JweDecryptionConfig.builder().defaultDecryptionKey(decryptionKey).build())
                .build();
    }

    private TokenValidator dpopValidator() {
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier(TEST_ISSUER)
                .expectedAudience(TEST_AUDIENCE)
                .dpopConfig(DpopConfig.builder().build())
                .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                .build();
        return TokenValidator.builder().parserConfig(ParserConfig.builder().build())
                .issuerConfig(issuerConfig).build();
    }

    // === Token / proof crafting ===

    private String signedAccessToken(String cnfThumbprint) {
        return Jwts.builder()
                .header().keyId(DEFAULT_KEY_ID).and()
                .issuer(TEST_ISSUER)
                .subject("test-subject")
                .audience().add(TEST_AUDIENCE).and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .claim("cnf", Map.of("jkt", cnfThumbprint))
                .signWith(InMemoryKeyMaterialHandler.getDefaultPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    private String dpopProof(KeyPair keyPair, Map<String, Object> jwkMap, String accessToken, long iatSeconds) {
        String headerJson = "{\"typ\":\"dpop+jwt\",\"alg\":\"RS256\",\"jwk\":%s}".formatted(mapToJson(jwkMap));
        String bodyJson = ("{\"jti\":\"%s\",\"iat\":%d,\"ath\":\"%s\",\"htm\":\"%s\",\"htu\":\"%s\"}").formatted(
                UUID.randomUUID(), iatSeconds, computeAth(accessToken), RESOURCE_METHOD, RESOURCE_URI);
        String signingInput = base64Url(headerJson.getBytes(StandardCharsets.UTF_8)) + "."
                + base64Url(bodyJson.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + base64Url(rsaSign(signingInput, keyPair.getPrivate()));
    }

    private String hs256DpopProof(String accessToken) {
        String headerJson = "{\"typ\":\"dpop+jwt\",\"alg\":\"HS256\"}";
        String bodyJson = ("{\"jti\":\"%s\",\"iat\":%d,\"ath\":\"%s\",\"htm\":\"%s\",\"htu\":\"%s\"}").formatted(
                UUID.randomUUID(), System.currentTimeMillis() / 1000, computeAth(accessToken),
                RESOURCE_METHOD, RESOURCE_URI);
        String signingInput = base64Url(headerJson.getBytes(StandardCharsets.UTF_8)) + "."
                + base64Url(bodyJson.getBytes(StandardCharsets.UTF_8));
        return signingInput + ".c2lnbmF0dXJl";
    }

    private AccessTokenRequest dpopRequest(String accessToken, String proof) {
        return new AccessTokenRequest(accessToken, Map.of("dpop", List.of(proof)),
                RESOURCE_URI, RESOURCE_METHOD);
    }

    // === Low-level helpers ===

    private static byte[] rsaSign(String signingInput, PrivateKey privateKey) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signer.sign();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Failed to RSA-sign", e);
        }
    }

    private static String computeAth(String accessToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(accessToken.getBytes(StandardCharsets.US_ASCII));
            return base64Url(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }

    private static Map<String, Object> rsaPublicKeyToJwkMap(RSAPublicKey publicKey) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kty", "RSA");
        map.put("n", base64Url(toUnsignedBytes(publicKey.getModulus())));
        map.put("e", base64Url(toUnsignedBytes(publicKey.getPublicExponent())));
        return map;
    }

    private static String mapToJson(Map<String, Object> map) {
        var sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey()).append("\":\"").append(entry.getValue()).append('"');
        }
        return sb.append('}').toString();
    }

    private static byte[] toUnsignedBytes(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes[0] == 0) {
            return Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
