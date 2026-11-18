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
package de.cuioss.sheriff.token.quarkus.config;

import de.cuioss.http.client.adapter.RetryConfig;
import de.cuioss.sheriff.token.quarkus.test.TestConfig;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.dpop.DpopConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests which property the orphaned-DPoP startup failure of {@link IssuerConfigResolver} names when several
 * {@code dpop.*} sub-properties are configured while DPoP is not enabled.
 */
@DisplayName("IssuerConfigResolver: orphaned DPoP sub-properties")
class IssuerConfigResolverOrphanedDpopTest {

    private static final String TEST_ISSUER = "test";

    @Test
    @DisplayName("should name the first orphaned property in natural sort order when several are set")
    void shouldNameFirstOrphanedPropertyInSortOrder() {
        String required = JwtPropertyKeys.ISSUERS.DPOP_REQUIRED.formatted(TEST_ISSUER);
        String proofMaxAge = JwtPropertyKeys.ISSUERS.DPOP_PROOF_MAX_AGE_SECONDS.formatted(TEST_ISSUER);
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "https://example.com",
                JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true",
                required, "true",
                proofMaxAge, "120"
        ));
        IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                resolver::resolveIssuerConfigs);

        assertAll("named property",
                () -> assertTrue(exception.getMessage().contains("'" + proofMaxAge + "'"),
                        "names dpop.proof-max-age-seconds, which sorts before dpop.required: "
                                + exception.getMessage()),
                () -> assertFalse(exception.getMessage().contains(required),
                        "does not name dpop.required: " + exception.getMessage()));
    }

    @Test
    @DisplayName("negative control: an enabled DPoP configuration with every sub-property resolves")
    void shouldResolveValidDpopConfiguration() {
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), "https://example.com",
                JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                JwtPropertyKeys.ISSUERS.AUDIENCE_VALIDATION_DISABLED.formatted(TEST_ISSUER), "true",
                JwtPropertyKeys.ISSUERS.DPOP_ENABLED.formatted(TEST_ISSUER), "true",
                JwtPropertyKeys.ISSUERS.DPOP_REQUIRED.formatted(TEST_ISSUER), "true",
                JwtPropertyKeys.ISSUERS.DPOP_PROOF_MAX_AGE_SECONDS.formatted(TEST_ISSUER), "120",
                JwtPropertyKeys.ISSUERS.DPOP_NONCE_CACHE_SIZE.formatted(TEST_ISSUER), "5000",
                JwtPropertyKeys.ISSUERS.DPOP_NONCE_CACHE_TTL_SECONDS.formatted(TEST_ISSUER), "600"
        ));
        IssuerConfigResolver resolver = new IssuerConfigResolver(config, RetryConfig.defaults(), null, null);

        List<IssuerConfig> result = resolver.resolveIssuerConfigs();

        assertEquals(1, result.size());
        DpopConfig dpopConfig = result.getFirst().getDpopConfig();
        assertNotNull(dpopConfig, "DPoP config is present");
        assertAll("configured values",
                () -> assertTrue(dpopConfig.isRequired()),
                () -> assertEquals(120, dpopConfig.getProofMaxAgeSeconds()),
                () -> assertEquals(5000, dpopConfig.getNonceCacheSize()),
                () -> assertEquals(600, dpopConfig.getNonceCacheTtlSeconds()));
    }
}
