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
package de.cuioss.sheriff.token.validation;

import de.cuioss.sheriff.token.commons.events.SecurityEventCounter.EventType;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.validation.domain.claim.ClaimName;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.sheriff.token.validation.test.JweTestTokenFactory;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static de.cuioss.sheriff.token.validation.TokenValidatorVerifySignatureTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial tests for {@link TokenValidator#verifySignature(String)}.
 * <p>
 * The signature-only entry skips the claim verdict, so its header, issuer and signature defences are the
 * only barrier it has. Each attack is refused with the event type of the defence it targets, and each test
 * carries a positive control proving the validator accepts a genuine token under the same configuration.
 */
@DisplayName("TokenValidator#verifySignature attack resistance")
class TokenValidatorVerifySignatureAttackTest {

    private static final String KEY_ID = InMemoryKeyMaterialHandler.DEFAULT_KEY_ID;
    private static final KeyPair ATTACKER_KEY_PAIR = generateRsaKeyPair();

    private TokenValidator validator;

    @BeforeEach
    void setUp() {
        validator = TokenValidator.builder()
                .issuerConfig(IssuerConfig.builder()
                        .issuerIdentifier(ISSUER)
                        .expectedAudience(AUDIENCE)
                        .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                        .build())
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    @DisplayName("alg=none is refused as UNSUPPORTED_ALGORITHM")
    void shouldRefuseAlgNone() {
        String forged = encode("{\"alg\":\"none\",\"kid\":\"%s\"}".formatted(KEY_ID)) + "."
                + encode(payloadJson()) + ".AAAA";

        assertRefused(EventType.UNSUPPORTED_ALGORITHM, () -> validator.verifySignature(forged));
        assertGenuineTokenAccepted();
    }

    @Test
    @DisplayName("An HMAC-signed token is refused as UNSUPPORTED_ALGORITHM")
    void shouldRefuseHmacToken() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        String forged = Jwts.builder()
                .header().keyId(KEY_ID).and()
                .claims(validClaims())
                .signWith(new SecretKeySpec(secret, "HmacSHA256"), Jwts.SIG.HS256)
                .compact();

        assertRefused(EventType.UNSUPPORTED_ALGORITHM, () -> validator.verifySignature(forged));
        assertGenuineTokenAccepted();
    }

    @Test
    @DisplayName("HMAC/RSA algorithm confusion over the issuer's public key is refused as UNSUPPORTED_ALGORITHM")
    void shouldRefuseAlgorithmConfusion() {
        RSAPublicKey issuerPublicKey = (RSAPublicKey) InMemoryKeyMaterialHandler.getDefaultPublicKey();
        String forged = Jwts.builder()
                .header().keyId(KEY_ID).and()
                .claims(validClaims())
                .signWith(new SecretKeySpec(issuerPublicKey.getEncoded(), "HmacSHA256"), Jwts.SIG.HS256)
                .compact();

        assertRefused(EventType.UNSUPPORTED_ALGORITHM, () -> validator.verifySignature(forged));
        assertGenuineTokenAccepted();
    }

    @Test
    @DisplayName("An embedded jwk header on a re-signed token is refused as UNSUPPORTED_ALGORITHM")
    void shouldRefuseEmbeddedJwk() {
        RSAPublicKey attackerPublicKey = (RSAPublicKey) ATTACKER_KEY_PAIR.getPublic();
        String headerJson = "{\"alg\":\"RS256\",\"kid\":\"%s\",\"jwk\":\"%s\"}"
                .formatted(KEY_ID, base64Url(toUnsignedBytes(attackerPublicKey.getModulus())));
        String signingInput = encode(headerJson) + "." + encode(payloadJson());
        String forged = signingInput + "." + base64Url(rsaSign(signingInput, ATTACKER_KEY_PAIR.getPrivate()));

        assertRefused(EventType.UNSUPPORTED_ALGORITHM, () -> validator.verifySignature(forged));
        assertGenuineTokenAccepted();
    }

    @Test
    @DisplayName("jku and x5u headers are ignored: an attacker-signed token naming them fails the signature check")
    void shouldIgnoreJkuAndX5u() {
        for (String header : new String[]{"jku", "x5u"}) {
            String forged = Jwts.builder()
                    .header().keyId(KEY_ID).add(header, "https://attacker.example.com/keys").and()
                    .claims(validClaims())
                    .signWith(ATTACKER_KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
                    .compact();
            assertRefused(EventType.SIGNATURE_VALIDATION_FAILED, () -> validator.verifySignature(forged));

            String genuine = Jwts.builder()
                    .header().keyId(KEY_ID).add(header, "https://attacker.example.com/keys").and()
                    .claims(validClaims())
                    .signWith(InMemoryKeyMaterialHandler.getDefaultPrivateKey(), Jwts.SIG.RS256)
                    .compact();
            assertNotNull(validator.verifySignature(genuine),
                    "positive control: the configured key verifies the same header shape");
        }
    }

    @Test
    @DisplayName("A 5-part token is refused as JWE_DECRYPTION_NOT_CONFIGURED when JWE is not configured")
    void shouldRefuseFivePartTokenWithoutJwe() {
        String jwe = JweTestTokenFactory.encryptAsJwe(signed(validClaims(), null),
                ATTACKER_KEY_PAIR.getPublic(), "RSA-OAEP", "A256GCM", null);

        assertRefused(EventType.JWE_DECRYPTION_NOT_CONFIGURED, () -> validator.verifySignature(jwe));
        assertGenuineTokenAccepted();
    }

    @Test
    @DisplayName("A token of an issuer whose loader is UNDEFINED is refused as NO_ISSUER_CONFIG")
    void shouldRefuseUndefinedLoader() {
        var loader = new TokenValidatorVerifySignatureTest.StatusControlledLoader(LoaderStatus.UNDEFINED);
        try (TokenValidator undefinedValidator = TokenValidator.builder()
                     .issuerConfig(IssuerConfig.builder()
                             .issuerIdentifier(ISSUER)
                             .expectedAudience(AUDIENCE)
                             .jwksLoader(loader)
                             .build())
                     .build()) {
            String token = signed(validClaims(), null);

            assertRefused(EventType.NO_ISSUER_CONFIG, () -> undefinedValidator.verifySignature(token));

            loader.setStatus(LoaderStatus.OK);
            assertNotNull(undefinedValidator.verifySignature(token), "positive control: the same loader once OK");
        }
    }

    @Test
    @DisplayName("An oversized signed token is refused as TOKEN_SIZE_EXCEEDED before parsing")
    void shouldRefuseOversizedToken() {
        Map<String, Object> claims = validClaims();
        char[] padding = new char[64 * 1024];
        Arrays.fill(padding, 'x');
        claims.put("padding", new String(padding));
        String oversized = signed(claims, null);

        assertRefused(EventType.TOKEN_SIZE_EXCEEDED, () -> validator.verifySignature(oversized));
        assertGenuineTokenAccepted();
    }

    @Test
    @DisplayName("A flood of distinct unknown issuers is refused one by one and does not affect a genuine token")
    void shouldRefuseUnknownIssuerFlood() {
        int flood = 200;
        for (int i = 0; i < flood; i++) {
            Map<String, Object> claims = validClaims();
            claims.put(ClaimName.ISSUER.getName(), "https://unknown-" + i + ".example.com");
            String token = signed(claims, null);
            assertRefused(EventType.NO_ISSUER_CONFIG, () -> validator.verifySignature(token));
        }

        assertEquals(flood, validator.getSecurityEventCounter().getCount(EventType.NO_ISSUER_CONFIG),
                "every unknown issuer is counted once");
        assertGenuineTokenAccepted();
    }

    // === Helpers ===

    private void assertGenuineTokenAccepted() {
        assertEquals(ISSUER, validator.verifySignature(signed(validClaims(), null))
                .getClaims().get(ClaimName.ISSUER.getName()).getOriginalString(), "positive control");
    }

    private static void assertRefused(EventType expected, Executable call) {
        TokenValidationException refused = assertThrows(TokenValidationException.class, call);
        assertEquals(expected, refused.getEventType());
    }

    private static String payloadJson() {
        long now = System.currentTimeMillis() / 1000;
        return "{\"iss\":\"%s\",\"sub\":\"attacker\",\"aud\":\"%s\",\"iat\":%d,\"exp\":%d}"
                .formatted(ISSUER, AUDIENCE, now, now + 3600);
    }

    private static String encode(String json) {
        return base64Url(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] toUnsignedBytes(BigInteger value) {
        byte[] bytes = value.toByteArray();
        return bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }

    private static byte[] rsaSign(String signingInput, PrivateKey privateKey) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signer.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to RSA-sign", e);
        }
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }
}
