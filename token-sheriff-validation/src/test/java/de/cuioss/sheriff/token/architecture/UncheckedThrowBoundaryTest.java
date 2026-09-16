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
package de.cuioss.sheriff.token.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Keeps every failure a per-request validation call can raise a declared
 * {@code TokenValidationException} rather than a bare unchecked exception.
 * <p>
 * The door this closes is the one PLAN-16 recorded and PR #746 left open: the client's
 * {@code RefreshFlow.refresh} narrows only {@code TokenValidationException} around
 * {@code validateAccessToken}, and its {@code classify} answers {@code PRE_REDEMPTION} — "the
 * authorization server never processed the grant, leave the session intact" — for anything else. An
 * undeclared unchecked exception raised inside the validation pipeline after a 2xx therefore reported
 * a refresh token the server had already burned as untouched. Making the request path throw only the
 * declared type removes that class of failure by construction instead of containing it.
 * <p>
 * <strong>The scan is whole-module and the exemptions are explicit.</strong> Rather than enumerate the
 * request path — which would silently stop covering a class added beside it — every production type in
 * {@code de.cuioss.sheriff.token} is scanned for the construction of an {@link IllegalStateException},
 * {@link IllegalArgumentException}, {@link NullPointerException} or {@link UnsupportedOperationException},
 * and any type holding one must appear in {@link #EXEMPT} with the reason it is not a request-path
 * failure. A new unchecked throw anywhere in the module therefore fails this test until someone either
 * converts it or records why it is exempt. Scanning constructor calls rather than {@code throw}
 * statements is what lets it see the {@code orElseThrow(() -> new IllegalStateException(...))} form,
 * which a {@code throw new} search misses.
 * <p>
 * Three non-vacuity legs keep a passing verdict meaningful: the scanned universe must be non-empty, the
 * detector must find sites at all, and every {@link #EXEMPT} entry must still bind to a live site — so a
 * conversion that makes an exemption obsolete fails here until the entry is removed.
 *
 * @see de.cuioss.sheriff.token.validation.exception.TokenValidationException
 */
@DisplayName("Request-path guards throw a declared type, and every unchecked throw is accounted for")
class UncheckedThrowBoundaryTest {

    private static final String PRODUCTION_ROOT_PACKAGE = "de.cuioss.sheriff.token";

    /** The unchecked types a request-path guard must never raise. */
    private static final Set<String> UNCHECKED_TYPES = Set.of(
            IllegalStateException.class.getName(),
            IllegalArgumentException.class.getName(),
            NullPointerException.class.getName(),
            UnsupportedOperationException.class.getName());

    /** Reason category: the guard fires only on reflective instantiation of a utility class. */
    private static final String UTILITY_CLASS_GUARD =
            "Lombok @UtilityClass / private-constructor instantiation guard — fires only on reflective"
                    + " instantiation, never as the outcome of a validation call";

    /** Reason category: reached only while assembling the validator. This is the plan's out-of-bounds set. */
    private static final String CONSTRUCTION_TIME =
            "construction-time contract violation by the embedder — converting it would turn a"
                    + " programming error into a per-token validation failure";

    /**
     * Reason category: runs on the JWKS loader-init or background-refresh thread. Wall-clock that is
     * "while serving", but never inside a {@code TokenValidator.create*Token} call stack.
     */
    private static final String LOADER_THREAD_ONLY =
            "JWKS loader-init / background-refresh thread only — never inside a create*Token call stack";

    /** Reason category: on the request path, but an existing narrow catch already translates it. */
    private static final String ALREADY_TRANSLATED =
            "on the request path, but translated to TokenValidationException by an existing narrow catch"
                    + " at the boundary that owns the SecurityEventCounter";

    /** Reason category: invoked by a consumer of the returned content, after validation has returned. */
    private static final String POST_VALIDATION_ACCESSOR =
            "post-validation accessor on returned token content — outside the create*Token call";

    /**
     * Every production type that still constructs an unchecked exception, with the reason it is not a
     * request-path failure. Derived from the PLAN-17 partition; see the PR body for the per-site
     * evidence. An entry that stops binding to a live site is a failure, not a leftover.
     */
    private static final Map<String, String> EXEMPT = Map.ofEntries(
            // --- utility-class instantiation guards ---
            Map.entry("de.cuioss.sheriff.token.commons.metrics.MetricIdentifier", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.commons.metrics.MetricIdentifier$BEARERTOKEN", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.commons.metrics.MetricIdentifier$VALIDATION", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.commons.transport.TransportLogMessages", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.commons.transport.TransportLogMessages$ERROR", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.commons.transport.TransportLogMessages$WARN", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.validation.JWTValidationLogMessages", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.validation.JWTValidationLogMessages$ERROR", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.validation.JWTValidationLogMessages$INFO", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.validation.JWTValidationLogMessages$WARN", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.validation.jwe.ConcatKdf", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.validation.jwks.JwksLoaderFactory", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.validation.metrics.MetricsTickerFactory", UTILITY_CLASS_GUARD),

            // --- construction-time: builders, configs, the validator's own argument checks ---
            Map.entry("de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig",
                    CONSTRUCTION_TIME + " (getHttpHandler is reached only from HttpJwksLoader.resolveJWKSAdapter,"
                            + " which runs on the construction-triggered async init)"),
            Map.entry("de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig$HttpJwksLoaderConfigBuilder",
                    CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.commons.transport.ParserConfig$ParserConfigBuilder", CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.commons.transport.WellKnownConfig$WellKnownConfigBuilder",
                    CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.IssuerConfig$IssuerConfigBuilder", CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.TokenValidator", CONSTRUCTION_TIME
                    + " (the @Builder constructor's issuer-configuration argument checks)"),
            Map.entry("de.cuioss.sheriff.token.validation.TokenValidator$TokenValidatorBuilder",
                    CONSTRUCTION_TIME + " (Lombok @Singular null checks)"),
            Map.entry("de.cuioss.sheriff.token.validation.cache.AccessTokenCacheConfig", CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.dpop.DpopConfig$DpopConfigBuilder", CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig$JweDecryptionConfigBuilder",
                    CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.jwks.key.JWKSKeyLoader$JWKSKeyLoaderBuilder",
                    CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.metrics.TokenValidatorMonitor", CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.metrics.TokenValidatorMonitorConfig"
                    + "$TokenValidatorMonitorConfigBuilder", CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.security.JwkAlgorithmPreferences", CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.security.SignatureAlgorithmPreferences", CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.pipeline.SignatureTemplateManager", CONSTRUCTION_TIME
                    + " (createSignatureTemplate is called only from the constructor; the per-request"
                    + " getSignatureInstance was converted by PLAN-17)"),

            // --- loader-init / background-refresh thread only ---
            Map.entry("de.cuioss.sheriff.token.validation.jwks.key.JwkKeyHandler", LOADER_THREAD_ONLY
                    + " (determineEcAlgorithm is called only from KeyProcessor.processEcKey; the"
                    + " per-request getKeyFactory now raises the checked InvalidKeySpecException its"
                    + " callers already declare)"),
            Map.entry("de.cuioss.sheriff.token.validation.jwks.key.KeyInfo", LOADER_THREAD_ONLY
                    + " (constructed only by KeyProcessor; the request path only reads existing records)"),
            Map.entry("de.cuioss.sheriff.token.validation.jwks.parser.KeyProcessor", LOADER_THREAD_ONLY),

            // --- request path, already translated by an existing narrow catch ---
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.mapper.OffsetDateTimeMapper",
                    ALREADY_TRANSLATED + " — TokenBuilder.extractClaims, the module's single sanctioned"
                            + " broad catch for claim mappers (AGENTS.md)"),
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.mapper.ScopeMapper",
                    ALREADY_TRANSLATED + " — TokenBuilder.extractClaims"),
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.mapper.StringSplitterMapper",
                    ALREADY_TRANSLATED + " — TokenBuilder.extractClaims"),
            Map.entry("de.cuioss.sheriff.token.validation.jwe.JweDecryptor",
                    ALREADY_TRANSLATED + " — JweDecryptor.decrypt narrows"
                            + " GeneralSecurityException|IllegalArgumentException around every one of these"
                            + " sites and increments JWE_DECRYPTION_FAILED"),

            // --- request path, but an argument-contract guard on a construction-validated value ---
            Map.entry("de.cuioss.sheriff.token.validation.domain.context.ValidationContext",
                    "request-path but not a token failure: the clock-skew guards re-check a value"
                            + " IssuerConfig already validated at build time (Preconditions.checkArgument in"
                            + " IssuerConfigBuilder.clockSkewSeconds), and isTokenTooOld's guard is dead on its"
                            + " only chain because ExpirationValidator.validateTokenAge early-returns on the"
                            + " same predicate"),

            // --- outside the validation call ---
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.CollectionClaimHandler",
                    POST_VALIDATION_ACCESSOR + " — reached only from AccessTokenContent's"
                            + " providesScopes/Roles/Groups helpers"),
            Map.entry("de.cuioss.sheriff.token.validation.domain.token.IdTokenContent",
                    POST_VALIDATION_ACCESSOR + " — getAudience has no main-source caller; the audience"
                            + " claim is validated through getClaimOption, not this accessor"),

            // --- dual-use utility ---
            Map.entry("de.cuioss.sheriff.token.validation.util.JwkThumbprintUtil",
                    "dual-use: construction-time for the client's DpopProofGenerator constructor, where a"
                            + " malformed JWK is an embedder contract violation. Its request-path reach"
                            + " (DpopProofValidator's thumbprint check) is narrowed there to DPOP_PROOF_INVALID"));

    /**
     * The request-path types PLAN-17 converted. Listing them explicitly is the positive control for the
     * conversion itself: {@link #shouldLeaveNoUncheckedThrowInAConvertedRequestPathType()} fails if any
     * of them regains an unchecked throw, and names the type rather than reporting a generic drift.
     */
    private static final List<String> CONVERTED_REQUEST_PATH_TYPES = List.of(
            "de.cuioss.sheriff.token.validation.pipeline.DecodedJwt",
            "de.cuioss.sheriff.token.validation.pipeline.ValidatorLookup",
            "de.cuioss.sheriff.token.validation.pipeline.validator.TokenSignatureValidator",
            "de.cuioss.sheriff.token.validation.domain.token.TokenContent",
            "de.cuioss.sheriff.token.validation.dpop.DpopProofValidator",
            "de.cuioss.sheriff.token.validation.util.Sha256Util",
            "de.cuioss.sheriff.token.validation.jwks.key.JWKSKeyLoader");

    /** Every scanned production type, by binary name. */
    private static Map<String, JavaClass> productionTypes;

    /** The unchecked-construction sites found, keyed by the binary name of the type holding them. */
    private static Map<String, List<String>> sitesByType;

    @BeforeAll
    static void scanProductionClasses() {
        JavaClasses imported = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(PRODUCTION_ROOT_PACKAGE);
        productionTypes = new TreeMap<>();
        sitesByType = new TreeMap<>();
        for (JavaClass type : imported) {
            productionTypes.put(type.getName(), type);
            List<String> sites = uncheckedConstructionSitesIn(type);
            if (!sites.isEmpty()) {
                sitesByType.put(type.getName(), sites);
            }
        }
    }

    @Test
    @DisplayName("the scan is neither empty nor blind — it sees a universe and finds sites in it")
    void shouldScanANonEmptyUniverseAndFindSites() {
        assertFalse(productionTypes.isEmpty(),
                "no production classes were imported from " + PRODUCTION_ROOT_PACKAGE
                        + " — every other leg of this test would pass over an empty universe");
        assertFalse(sitesByType.isEmpty(),
                "the scan found no construction of " + UNCHECKED_TYPES + " anywhere in "
                        + PRODUCTION_ROOT_PACKAGE + ". The module is known to hold construction-time"
                        + " guards, so zero sites means the detector stopped matching — probably because"
                        + " getConstructorCallsFromSelf no longer reports these calls — not that the tree"
                        + " is clean.");
        assertTrue(sitesByType.containsKey("de.cuioss.sheriff.token.validation.IssuerConfig$IssuerConfigBuilder"),
                "the detector did not find the known unchecked throws in IssuerConfigBuilder.build()."
                        + " That is this test's positive control: if the detector cannot see a site it is"
                        + " pointed straight at, its silence about the request path proves nothing.");
    }

    @Test
    @DisplayName("no production type raises an unchecked exception without a recorded reason")
    void shouldRejectAnUnaccountedUncheckedThrow() {
        List<String> unaccounted = new ArrayList<>();
        sitesByType.forEach((type, sites) -> {
            if (!EXEMPT.containsKey(type)) {
                unaccounted.add(type + " " + sites);
            }
        });

        assertTrue(unaccounted.isEmpty(),
                "production types constructing " + UNCHECKED_TYPES + " with no entry in this test's"
                        + " EXEMPT map: " + unaccounted
                        + ". Scanned " + productionTypes.size() + " types and found "
                        + totalSites() + " sites across " + sitesByType.size() + " types, of which "
                        + EXEMPT.size() + " types are exempt. If the new site is on the per-request path"
                        + " reachable from TokenValidator.createAccessToken / createIdToken /"
                        + " createRefreshToken or a DPoP validation entry point, throw"
                        + " TokenValidationException with a fitting EventType instead — an undeclared"
                        + " unchecked exception escaping validateAccessToken is what makes a burned"
                        + " refresh token classify as PRE_REDEMPTION. If it is construction-time, add it"
                        + " to EXEMPT with the reason.");
    }

    @Test
    @DisplayName("no exemption has outlived the site it excuses")
    void shouldRejectAStaleExemption() {
        List<String> unknownType = new ArrayList<>();
        List<String> withoutSites = new ArrayList<>();
        EXEMPT.forEach((type, reason) -> {
            if (!productionTypes.containsKey(type)) {
                unknownType.add(type);
            } else if (!sitesByType.containsKey(type)) {
                withoutSites.add(type);
            }
        });

        assertTrue(unknownType.isEmpty(),
                "EXEMPT names types that no longer exist in " + PRODUCTION_ROOT_PACKAGE + ": "
                        + unknownType + ". An exemption for a deleted or renamed type excuses nothing and"
                        + " hides the fact that the list is no longer maintained.");
        assertTrue(withoutSites.isEmpty(),
                "EXEMPT names types that no longer construct any of " + UNCHECKED_TYPES + ": "
                        + withoutSites + ". The site was converted or removed, so the exemption is now"
                        + " inert — delete the entry. Left in place it would silently pre-approve a future"
                        + " unchecked throw in that type.");
    }

    @Test
    @DisplayName("no converted request-path type has regained an unchecked throw")
    void shouldLeaveNoUncheckedThrowInAConvertedRequestPathType() {
        List<String> missing = new ArrayList<>();
        List<String> regressed = new ArrayList<>();
        for (String type : CONVERTED_REQUEST_PATH_TYPES) {
            if (!productionTypes.containsKey(type)) {
                missing.add(type);
            } else if (sitesByType.containsKey(type)) {
                regressed.add(type + " " + sitesByType.get(type));
            }
        }

        assertTrue(missing.isEmpty(),
                "types PLAN-17 converted are absent from the scan: " + missing
                        + " — a rename would make this leg vacuous, so it fails instead");
        assertTrue(regressed.isEmpty(),
                "request-path types converted to TokenValidationException by PLAN-17 that construct an"
                        + " unchecked exception again: " + regressed
                        + ". These sit on the path reachable from TokenValidator's public entry points,"
                        + " where RefreshFlow.refresh narrows only TokenValidationException.");
    }

    @Test
    @DisplayName("every exemption records a reason, so the list cannot grow by silence")
    void shouldRequireAReasonForEveryExemption() {
        List<String> withoutReason = new ArrayList<>();
        EXEMPT.forEach((type, reason) -> {
            if (reason == null || reason.isBlank()) {
                withoutReason.add(type);
            }
        });
        assertTrue(withoutReason.isEmpty(),
                "EXEMPT entries carrying no reason: " + withoutReason);

        List<String> siteWithoutExemption = new ArrayList<>(sitesByType.keySet());
        siteWithoutExemption.removeAll(EXEMPT.keySet());
        List<String> exemptionWithoutSite = new ArrayList<>(EXEMPT.keySet());
        exemptionWithoutSite.removeAll(sitesByType.keySet());
        assertEquals(EXEMPT.size(), sitesByType.size(),
                "the set of types holding an unchecked-construction site and the set of exemptions must"
                        + " be identical, so neither can drift behind the other. Sites with no exemption: "
                        + siteWithoutExemption + "; exemptions with no site: " + exemptionWithoutSite
                        + " (" + totalSites() + " sites across " + sitesByType.size() + " of "
                        + productionTypes.size() + " scanned types).");
    }

    /**
     * @return one entry per construction of an unchecked type inside {@code type}, naming the exception,
     *         the declaring code unit and the line — lambda bodies included, because a lambda compiles
     *         into a synthetic method of the same class and its calls are reported with it
     */
    private static List<String> uncheckedConstructionSitesIn(JavaClass type) {
        List<String> sites = new ArrayList<>();
        for (JavaConstructorCall call : type.getConstructorCallsFromSelf()) {
            if (UNCHECKED_TYPES.contains(call.getTargetOwner().getName())) {
                sites.add(call.getTargetOwner().getSimpleName() + " at "
                        + call.getOrigin().getName() + ":" + call.getLineNumber());
            }
        }
        return sites;
    }

    private static int totalSites() {
        return sitesByType.values().stream().mapToInt(List::size).sum();
    }
}
