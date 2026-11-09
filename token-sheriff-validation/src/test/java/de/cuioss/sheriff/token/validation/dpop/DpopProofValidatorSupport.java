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
package de.cuioss.sheriff.token.validation.dpop;

import com.dslplatform.json.DslJson;
import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.json.JwtHeader;
import de.cuioss.sheriff.token.validation.json.MapRepresentation;
import de.cuioss.sheriff.token.validation.pipeline.DecodedJwt;
import de.cuioss.sheriff.token.validation.pipeline.SignatureTemplateManager;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureAlgorithm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.util.*;

/**
 * Shared fixture of the {@link DpopProofValidator} tests: the validator under test, its event counter
 * and replay protection, and the proof and key builders. Split out of {@code DpopProofValidatorTest}
 * so that its tests can be grouped by topic over several classes without changing any of them.
 */
abstract class DpopProofValidatorSupport {

    static final String TEST_ISSUER = "https://test-issuer.example.com";

    /**
     * The proof key of every test in which the key itself is not the subject, generated once for the
     * class. Replay protection stays per test, and every proof carries its own {@code jti}.
     */
    static final KeyPair FIXTURE_KEY_PAIR = newRsaKeyPair();

    SecurityEventCounter securityEventCounter;
    DpopReplayProtection replayProtection;
    DpopProofValidator validator;

    @BeforeEach
    void setUp() {
        securityEventCounter = new SecurityEventCounter();
        replayProtection = new DpopReplayProtection(300, 10_000);

        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier(TEST_ISSUER)
                .dpopConfig(DpopConfig.builder().build())
                .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                .audienceValidationDisabled(true)
                .build();

        validator = new DpopProofValidator(issuerConfig, securityEventCounter, replayProtection, new SignatureTemplateManager(issuerConfig.getAlgorithmPreferences()), ParserConfig.builder().build(), new DslJson<>(new DslJson.Settings<>()));
    }

    @AfterEach
    void tearDown() {
        replayProtection.close();
    }

    static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    // === Helper Methods ===

    DecodedJwt createAccessTokenJwt(String cnfJkt) {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("iss", TEST_ISSUER);
        bodyMap.put("sub", "test-subject");
        bodyMap.put("exp", (System.currentTimeMillis() / 1000) + 3600);
        bodyMap.put("iat", System.currentTimeMillis() / 1000);
        if (cnfJkt != null) {
            bodyMap.put("cnf", Map.of("jkt", cnfJkt));
        }

        var body = new MapRepresentation(bodyMap);
        var header = new JwtHeader(
                "RS256", null, "test-key-id", null, null, null, null, null, null, null);

        return new DecodedJwt(header, body, "dummy-sig", new String[]{"a", "b", "c"}, "a.b.c");
    }

    String buildDpopProof(KeyPair keyPair, Map<String, Object> jwkMap, String alg, String accessToken) {
        return buildDpopProofInternal(keyPair, jwkMap, alg, "dpop+jwt",
                UUID.randomUUID().toString(), System.currentTimeMillis() / 1000,
                computeAth(accessToken));
    }

    String buildDpopProofWithCustomTyp(KeyPair keyPair, Map<String, Object> jwkMap, String alg,
            String accessToken, String typ) {
        return buildDpopProofInternal(keyPair, jwkMap, alg, typ,
                UUID.randomUUID().toString(), System.currentTimeMillis() / 1000,
                computeAth(accessToken));
    }

    String buildDpopProofWithCustomTypAndHtuHtm(KeyPair keyPair, Map<String, Object> jwkMap, String alg,
            String accessToken, String typ, String htm, String htu) {
        return buildDpopProofInternalWithHtuHtm(keyPair, jwkMap, alg, typ,
                UUID.randomUUID().toString(), System.currentTimeMillis() / 1000,
                computeAth(accessToken), htm, htu);
    }

    String buildDpopProofWithJtiAndHtuHtm(KeyPair keyPair, Map<String, Object> jwkMap, String alg,
            String accessToken, String jti, String htm, String htu) {
        return buildDpopProofInternalWithHtuHtm(keyPair, jwkMap, alg, "dpop+jwt",
                jti, System.currentTimeMillis() / 1000, computeAth(accessToken), htm, htu);
    }

    String buildDpopProofWithIat(KeyPair keyPair, Map<String, Object> jwkMap, String alg,
            String accessToken, long iat) {
        return buildDpopProofInternal(keyPair, jwkMap, alg, "dpop+jwt",
                UUID.randomUUID().toString(), iat, computeAth(accessToken));
    }

    String buildDpopProofWithAth(KeyPair keyPair, Map<String, Object> jwkMap, String alg,
            String accessTokenForAth) {
        return buildDpopProofInternal(keyPair, jwkMap, alg, "dpop+jwt",
                UUID.randomUUID().toString(), System.currentTimeMillis() / 1000,
                computeAth(accessTokenForAth));
    }

    String buildDpopProofWithMismatchedKey(KeyPair signingKeyPair, Map<String, Object> headerJwkMap,
            String alg, String accessToken) {
        return buildDpopProofInternal(signingKeyPair, headerJwkMap, alg, "dpop+jwt",
                UUID.randomUUID().toString(), System.currentTimeMillis() / 1000,
                computeAth(accessToken));
    }

    String buildDpopProofWithHtuHtm(KeyPair keyPair, Map<String, Object> jwkMap, String alg,
            String accessToken, String htm, String htu) {
        return buildDpopProofInternalWithHtuHtm(keyPair, jwkMap, alg, "dpop+jwt",
                UUID.randomUUID().toString(), System.currentTimeMillis() / 1000,
                computeAth(accessToken), htm, htu);
    }

    String buildDpopProofInternalWithHtuHtm(KeyPair signingKeyPair, Map<String, Object> headerJwkMap,
            String alg, String typ,
            String jti, long iat, String ath, String htm, String htu) {
        // Build header JSON
        String jwkJson = mapToJson(headerJwkMap);
        String headerJson = """
                {"typ":"%s","alg":"%s","jwk":%s}""".formatted(typ, alg, jwkJson);

        // Build body JSON with htm and htu
        String bodyJson = """
                {"jti":"%s","iat":%d,"ath":"%s","htm":"%s","htu":"%s"}""".formatted(jti, iat, ath, htm, htu);

        // Encode
        String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String encodedBody = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bodyJson.getBytes(StandardCharsets.UTF_8));

        String dataToSign = encodedHeader + "." + encodedBody;

        // Sign
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(signingKeyPair.getPrivate());
            signer.update(dataToSign.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signer.sign();

            String encodedSignature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(signatureBytes);

            return dataToSign + "." + encodedSignature;
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new IllegalStateException("Failed to sign DPoP proof", e);
        }
    }

    String buildDpopProofInternal(KeyPair signingKeyPair, Map<String, Object> headerJwkMap,
            String alg, String typ,
            String jti, long iat, String ath) {
        // Build header JSON
        String jwkJson = mapToJson(headerJwkMap);
        String headerJson = """
                {"typ":"%s","alg":"%s","jwk":%s}""".formatted(typ, alg, jwkJson);

        // Build body JSON
        String bodyJson = """
                {"jti":"%s","iat":%d,"ath":"%s"}""".formatted(jti, iat, ath);

        // Encode
        String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String encodedBody = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bodyJson.getBytes(StandardCharsets.UTF_8));

        String dataToSign = encodedHeader + "." + encodedBody;

        // Sign
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(signingKeyPair.getPrivate());
            signer.update(dataToSign.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signer.sign();

            String encodedSignature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(signatureBytes);

            return dataToSign + "." + encodedSignature;
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new IllegalStateException("Failed to sign DPoP proof", e);
        }
    }

    String computeAth(String accessToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(accessToken.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    String mapToJson(Map<String, Object> map) {
        var sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(entry.getKey()).append("\":\"").append(entry.getValue()).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    static KeyPair newRsaKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }

    Map<String, Object> rsaPublicKeyToJwkMap(RSAPublicKey publicKey) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kty", "RSA");
        map.put("n", Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsignedBytes(publicKey.getModulus())));
        map.put("e", Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsignedBytes(publicKey.getPublicExponent())));
        return map;
    }

    byte[] toUnsignedBytes(BigInteger bigInteger) {
        byte[] bytes = bigInteger.toByteArray();
        if (bytes[0] == 0) {
            return Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }

    // --- EC / EdDSA helpers ---

    Map<String, Object> ecPublicKeyToJwkMap(ECPublicKey publicKey, String curve, int coordSize) {
        ECPoint w = publicKey.getW();
        byte[] xBytes = normalizeCoordinate(w.getAffineX().toByteArray(), coordSize);
        byte[] yBytes = normalizeCoordinate(w.getAffineY().toByteArray(), coordSize);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kty", "EC");
        map.put("crv", curve);
        map.put("x", Base64.getUrlEncoder().withoutPadding().encodeToString(xBytes));
        map.put("y", Base64.getUrlEncoder().withoutPadding().encodeToString(yBytes));
        return map;
    }

    static KeyPair newEcKeyPair(String curve) {
        String jdkCurve = switch (curve) {
            case "P-256" -> "secp256r1";
            case "P-384" -> "secp384r1";
            case "P-521" -> "secp521r1";
            default -> throw new IllegalArgumentException("Unknown curve: " + curve);
        };
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec(jdkCurve));
            return gen.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("EC curve " + curve + " not available", e);
        }
    }

    static int ecCoordinateSize(String curve) {
        return switch (curve) {
            case "P-256" -> 32;
            case "P-384" -> 48;
            case "P-521" -> 66;
            default -> throw new IllegalArgumentException("Unknown curve: " + curve);
        };
    }

    static String ecCurveForAlgorithm(String alg) {
        return switch (alg) {
            case "ES256" -> "P-256";
            case "ES384" -> "P-384";
            case "ES512" -> "P-521";
            default -> throw new IllegalArgumentException("Unknown ECDSA algorithm: " + alg);
        };
    }

    /**
     * Builds a correctly bound DPoP proof (jti, iat, ath, htm, htu) whose header carries {@code alg}
     * and is signed with the given key using the hash {@code alg} names. The raw {@code r || s}
     * components are each padded to the wider of the algorithm's and the key curve's component
     * width, so a proof with a smaller-curve key carries the exact signature length {@code alg}
     * expects and is a genuine signature over its header and payload.
     */
    String buildEcdsaDpopProof(KeyPair keyPair, Map<String, Object> jwkMap, String alg,
            String accessToken, String htu) {
        String headerJson = """
                {"typ":"dpop+jwt","alg":"%s","jwk":%s}""".formatted(alg, mapToJson(jwkMap));
        String bodyJson = """
                {"jti":"%s","iat":%d,"ath":"%s","htm":"GET","htu":"%s"}"""
                .formatted(UUID.randomUUID().toString(), System.currentTimeMillis() / 1000,
                        computeAth(accessToken), htu);
        String dataToSign = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8)) + "."
                + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bodyJson.getBytes(StandardCharsets.UTF_8));
        String jcaAlgorithm = "SHA%swithECDSAinP1363Format".formatted(alg.substring(2));
        try {
            Signature signer = Signature.getInstance(jcaAlgorithm);
            signer.initSign(keyPair.getPrivate());
            signer.update(dataToSign.getBytes(StandardCharsets.UTF_8));
            byte[] curveWidthSignature = signer.sign();
            int curveWidth = curveWidthSignature.length / 2;
            int width = Math.max(curveWidth, ecCoordinateSize(ecCurveForAlgorithm(alg)));
            byte[] signature = new byte[width * 2];
            System.arraycopy(curveWidthSignature, 0, signature, width - curveWidth, curveWidth);
            System.arraycopy(curveWidthSignature, curveWidth, signature, 2 * width - curveWidth, curveWidth);
            return dataToSign + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign DPoP proof with " + jcaAlgorithm, e);
        }
    }

    Map<String, Object> edDsaPublicKeyToJwkMap(EdECPublicKey publicKey) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kty", "OKP");
        map.put("crv", publicKey.getParams().getName());
        map.put("x", InMemoryKeyMaterialHandler.encodeEdECPublicKey(publicKey));
        return map;
    }

    byte[] normalizeCoordinate(byte[] coordinate, int expectedSize) {
        int startIndex = 0;
        while (startIndex < coordinate.length && coordinate[startIndex] == 0) {
            startIndex++;
        }
        byte[] result = new byte[expectedSize];
        int sourceLength = coordinate.length - startIndex;
        int destStart = Math.max(0, expectedSize - sourceLength);
        int copyLength = Math.min(sourceLength, expectedSize);
        System.arraycopy(coordinate, startIndex, result, destStart, copyLength);
        return result;
    }

    /**
     * Builds a DPoP proof JWT using jjwt for proper signature format handling (EC/EdDSA).
     */
    @SuppressWarnings("unchecked")
    <A extends SignatureAlgorithm> String buildDpopProofWithJjwt(
            PrivateKey privateKey, Map<String, Object> jwkMap,
            A sigAlgorithm, String accessToken, String htm, String htu) {
        return Jwts.builder()
                .header().type("dpop+jwt").add("jwk", jwkMap).and()
                .claim("jti", UUID.randomUUID().toString())
                .claim("iat", System.currentTimeMillis() / 1000)
                .claim("ath", computeAth(accessToken))
                .claim("htm", htm)
                .claim("htu", htu)
                .signWith(privateKey, sigAlgorithm)
                .compact();
    }

    /**
     * Builds a DPoP proof with selective claims (for testing missing claim validation).
     * Uses RSA signing with valid signature so validation reaches the claim checks.
     */
    String buildDpopProofWithSelectiveClaims(KeyPair signingKeyPair, Map<String, Object> headerJwkMap,
            String alg, String rawAccessToken, boolean includeJti, boolean includeIat, boolean includeAth) {
        // Build header JSON
        String jwkJson = mapToJson(headerJwkMap);
        String headerJson = """
                {"typ":"dpop+jwt","alg":"%s","jwk":%s}""".formatted(alg, jwkJson);

        // Build body with selective claims
        var parts = new ArrayList<String>();
        if (includeJti) {
            parts.add("\"jti\":\"%s\"".formatted(UUID.randomUUID().toString()));
        }
        if (includeIat) {
            parts.add("\"iat\":%d".formatted(System.currentTimeMillis() / 1000));
        }
        if (includeAth) {
            parts.add("\"ath\":\"%s\"".formatted(computeAth(rawAccessToken)));
        }
        String bodyJson = "{" + String.join(",", parts) + "}";

        // Encode
        String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String encodedBody = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bodyJson.getBytes(StandardCharsets.UTF_8));

        String dataToSign = encodedHeader + "." + encodedBody;

        // Sign with RSA
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(signingKeyPair.getPrivate());
            signer.update(dataToSign.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signer.sign();

            String encodedSignature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(signatureBytes);

            return dataToSign + "." + encodedSignature;
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new IllegalStateException("Failed to sign DPoP proof", e);
        }
    }
}
