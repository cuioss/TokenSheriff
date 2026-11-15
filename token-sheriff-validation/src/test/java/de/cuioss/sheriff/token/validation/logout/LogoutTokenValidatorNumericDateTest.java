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
package de.cuioss.sheriff.token.validation.logout;

import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.test.InMemoryKeyMaterialHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the bounds {@link LogoutTokenValidator} puts on the NumericDate claims {@code iat} and {@code exp}
 * (RFC 7519 section 2) and on its freshness window.
 * <p>
 * A NumericDate is a JSON number, so a token may carry an exponent such as {@code 1e99999999}. Expanding such
 * a value to its digits takes minutes and, inside the validator's decision lock, stalls every other
 * validation. The extreme-value tests therefore run the hostile token and a valid one on two threads and
 * require both to finish within {@link #PROMPT}, a deliberately loose bound: a validator that expands the
 * value misses it by orders of magnitude.
 */
@DisplayName("LogoutTokenValidator NumericDate and freshness window bounds")
class LogoutTokenValidatorNumericDateTest {

    private static final String ISSUER = "https://logout-issuer.example.com";
    private static final String AUDIENCE = "logout-client";
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration PROMPT = Duration.ofSeconds(10);

    private TokenValidator tokenValidator;
    private LogoutTokenValidator underTest;

    @BeforeEach
    void setUp() {
        tokenValidator = TokenValidator.builder()
                .issuerConfig(IssuerConfig.builder()
                        .issuerIdentifier(ISSUER)
                        .expectedAudience(AUDIENCE)
                        .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                        .build())
                .build();
        underTest = validator(WINDOW);
    }

    @AfterEach
    void closeValidator() {
        tokenValidator.close();
    }

    @Nested
    @DisplayName("Extreme exponents")
    class ExtremeExponents {

        @ParameterizedTest(name = "iat {0} is refused as ISSUED_AT promptly")
        @ValueSource(strings = {"1e99999999", "1e-99999999", "-1e99999999", "123456e99999999", "1e2147483647",
                "1e-2147483647"})
        @DisplayName("An iat with an extreme exponent is refused as ISSUED_AT without stalling another validation")
        void shouldRefuseExtremeIssuedAtPromptly(String issuedAt) throws Exception {
            assertRefusedPromptlyBesideValidToken(LogoutTokenCheck.ISSUED_AT, withMember("iat", issuedAt, "jti-iat"));
        }

        @ParameterizedTest(name = "exp {0} is refused as EXPIRATION promptly")
        @ValueSource(strings = {"1e99999999", "1e-99999999", "-1e99999999", "123456e99999999", "1e2147483647",
                "1e-2147483647"})
        @DisplayName("An exp with an extreme exponent is refused as EXPIRATION without stalling another validation")
        void shouldRefuseExtremeExpirationPromptly(String expiration) throws Exception {
            assertRefusedPromptlyBesideValidToken(LogoutTokenCheck.EXPIRATION, withMember("exp", expiration, "jti-exp"));
        }

        @ParameterizedTest(name = "{0} carrying a 16-digit mantissa with a large exponent is refused promptly")
        @ValueSource(strings = {"iat", "exp"})
        @DisplayName("A 16-digit mantissa with a large exponent is refused promptly, before the logout checks")
        void shouldRefuseSixteenDigitMantissaWithLargeExponentPromptly(String claim) throws Exception {
            // The token parser admits at most 16 digits per JSON number (ParserConfig limitDigitsBuffer), so this
            // value never reaches the NumericDate bound: the signature-only entry refuses it, which the
            // validator reports as SIGNATURE (documented on LogoutTokenCheck#SIGNATURE)
            assertRefusedPromptlyBesideValidToken(LogoutTokenCheck.SIGNATURE,
                    withMember(claim, "1234567890123456e99999999", "jti-mantissa"));
        }

        @Test
        @DisplayName("An exp one integer digit beyond the Instant second range is refused as EXPIRATION")
        void shouldRefuseExpirationBeyondInstantRange() {
            assertRefused(LogoutTokenCheck.EXPIRATION, underTest.validate(withMember("exp", "1e17", "jti-exp-1e17")));
        }

        /**
         * Validates {@code hostile} on one thread and a valid token with its own {@code jti} on another, and
         * requires both results within {@link #PROMPT}.
         */
        private void assertRefusedPromptlyBesideValidToken(LogoutTokenCheck expected, String hostile)
                throws Exception {
            String valid = withMember("jti", quoted("jti-concurrent-control"), "jti-concurrent-control");
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<LogoutTokenValidationResult> hostileResult = executor.submit(() -> underTest.validate(hostile));
                Future<LogoutTokenValidationResult> validResult = executor.submit(() -> underTest.validate(valid));

                assertRefused(expected, hostileResult.get(PROMPT.toSeconds(), TimeUnit.SECONDS));
                assertTrue(validResult.get(PROMPT.toSeconds(), TimeUnit.SECONDS).isAccepted(),
                        "positive control: the concurrent valid token is accepted");
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("Representable values")
    class RepresentableValues {

        @Test
        @DisplayName("exp nine tenths of a second after the clock is accepted")
        void shouldAcceptExpirationNineTenthsAfterClock() {
            assertTrue(underTest.validate(withMember("exp", NOW.getEpochSecond() + ".9", "jti-exp-n-0.9"))
                    .isAccepted());
        }

        @Test
        @DisplayName("exp one microsecond before the clock is refused as EXPIRATION")
        void shouldRefuseExpirationOneMicrosecondBeforeClock() {
            assertRefused(LogoutTokenCheck.EXPIRATION,
                    underTest.validate(withMember("exp", (NOW.getEpochSecond() - 1) + ".999999", "jti-exp-us")));
        }

        @Test
        @DisplayName("A large but representable exp written with an exponent is accepted")
        void shouldAcceptLargeRepresentableExpiration() {
            // 9999-12-31T23:59:59Z, far beyond any real token but well inside the Instant range
            assertTrue(underTest.validate(withMember("exp", "2.53402300799e11", "jti-exp-large")).isAccepted());
        }

        @Test
        @DisplayName("An iat written with a negative exponent that reads as now is accepted")
        void shouldAcceptIssuedAtWithNegativeExponent() {
            String scaled = NOW.getEpochSecond() + "000e-3";

            assertTrue(underTest.validate(withMember("iat", scaled, "jti-iat-scaled")).isAccepted());
        }

        @Test
        @DisplayName("iat at the earliest window edge is accepted, one microsecond earlier is refused")
        void shouldHonourEarliestEdgeToTheMicrosecond() {
            long edge = NOW.minus(WINDOW).getEpochSecond();

            assertTrue(underTest.validate(withMember("iat", edge + ".0", "jti-early-edge")).isAccepted());
            assertRefused(LogoutTokenCheck.ISSUED_AT,
                    underTest.validate(withMember("iat", (edge - 1) + ".999999", "jti-early-beyond")));
        }

        @Test
        @DisplayName("iat at the latest window edge is accepted, one microsecond later is refused")
        void shouldHonourLatestEdgeToTheMicrosecond() {
            long edge = NOW.plus(WINDOW).getEpochSecond();

            assertTrue(underTest.validate(withMember("iat", edge + ".0", "jti-late-edge")).isAccepted());
            assertRefused(LogoutTokenCheck.ISSUED_AT,
                    underTest.validate(withMember("iat", edge + ".000001", "jti-late-beyond")));
        }
    }

    @Nested
    @DisplayName("Freshness window")
    class FreshnessWindow {

        @Test
        @DisplayName("A freshness window of exactly MAX_FRESHNESS_WINDOW is accepted and validates a token")
        void shouldAcceptWindowAtLimit() {
            LogoutTokenValidator atLimit = validator(LogoutTokenValidator.MAX_FRESHNESS_WINDOW);

            assertTrue(atLimit.validate(withMember("jti", quoted("jti-at-limit"), "jti-at-limit")).isAccepted());
        }

        @Test
        @DisplayName("A freshness window one nanosecond beyond MAX_FRESHNESS_WINDOW is rejected")
        void shouldRejectWindowBeyondLimit() {
            Duration beyond = LogoutTokenValidator.MAX_FRESHNESS_WINDOW.plusNanos(1);

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> validator(beyond));
            assertTrue(thrown.getMessage().contains(beyond.toString()), thrown::getMessage);
        }

        @Test
        @DisplayName("At the largest window an extreme iat is refused as ISSUED_AT and nothing throws")
        void shouldRefuseExtremeIssuedAtAtLargestWindow() {
            LogoutTokenValidator atLimit = validator(LogoutTokenValidator.MAX_FRESHNESS_WINDOW);

            assertRefused(LogoutTokenCheck.ISSUED_AT, atLimit.validate(withMember("iat", "3e16", "jti-iat-3e16")));
            assertRefused(LogoutTokenCheck.ISSUED_AT, atLimit.validate(withMember("iat", "-3e16", "jti-iat-neg")));
        }
    }

    // === Fixtures ===

    private LogoutTokenValidator validator(Duration freshnessWindow) {
        return LogoutTokenValidator.builder()
                .tokenValidator(tokenValidator)
                .expectedIssuer(ISSUER)
                .expectedAudience(AUDIENCE)
                .freshnessWindow(freshnessWindow)
                .replayGuardCapacity(100)
                .clock(CLOCK)
                .build();
    }

    /**
     * @return a signed logout token that passes every check except for {@code claim}, whose value is the
     * literal JSON text {@code json}; its {@code jti} is {@code jti} unless {@code claim} is {@code jti}
     */
    private static String withMember(String claim, String json, String jti) {
        Map<String, String> members = new LinkedHashMap<>();
        members.put("iss", quoted(ISSUER));
        members.put("sub", quoted("subject-1"));
        members.put("sid", quoted("session-1"));
        members.put("aud", quoted(AUDIENCE));
        members.put("iat", String.valueOf(NOW.getEpochSecond()));
        members.put("exp", String.valueOf(NOW.plusSeconds(120).getEpochSecond()));
        members.put("jti", quoted(jti));
        members.put("events", "{" + quoted(LogoutTokenValidator.BACKCHANNEL_LOGOUT_EVENT) + ":{}}");
        members.put(claim, json);
        return signedJson(members);
    }

    /**
     * Signs a payload written as literal JSON with the default RS256 test key and {@code typ: logout+jwt}, so
     * a number reaches the parser exactly as written, exponent included.
     */
    private static String signedJson(Map<String, String> members) {
        String header = "{\"alg\":\"RS256\",\"kid\":" + quoted(InMemoryKeyMaterialHandler.DEFAULT_KEY_ID)
                + ",\"typ\":\"logout+jwt\"}";
        String payload = members.entrySet().stream()
                .map(member -> quoted(member.getKey()) + ":" + member.getValue())
                .collect(Collectors.joining(",", "{", "}"));
        String signingInput = base64Url(header) + "." + base64Url(payload);
        try {
            java.security.Signature signer = java.security.Signature.getInstance("SHA256withRSA");
            signer.initSign(InMemoryKeyMaterialHandler.getDefaultPrivateKey());
            signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign the test token", e);
        }
    }

    private static String quoted(String text) {
        return "\"" + text + "\"";
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertRefused(LogoutTokenCheck expected, LogoutTokenValidationResult result) {
        assertFalse(result.isAccepted(), () -> "expected refusal as " + expected + " but was " + result);
        assertEquals(Optional.of(expected), result.getFailedCheck());
    }
}
