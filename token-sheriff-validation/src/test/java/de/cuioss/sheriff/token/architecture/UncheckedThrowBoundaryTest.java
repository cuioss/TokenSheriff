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
import de.cuioss.sheriff.token.commons.error.TokenSheriffException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Keeps every failure a per-request validation call can raise a declared
 * {@link de.cuioss.sheriff.token.validation.exception.TokenValidationException} rather than a bare
 * unchecked exception.
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
 * {@code de.cuioss.sheriff.token} is scanned for the construction of an unchecked exception, and any
 * code unit holding one must appear in {@link #EXEMPT} with the reason it is not a request-path
 * failure. A new unchecked throw anywhere in the module therefore fails this test until someone either
 * converts it or records why it is exempt.
 * <p>
 * Two details make the difference between a guard and a formality:
 * <ul>
 *   <li><strong>Constructor calls, not {@code throw} statements.</strong> Scanning the call lets it see
 *       the {@code orElseThrow(() -> new IllegalStateException(...))} and {@code default -> throw} forms
 *       that a {@code throw new} text search misses — which is how the two most dangerous request-path
 *       sites in this module (the {@code kid} / {@code alg} preconditions in
 *       {@code TokenSignatureValidator}) went unlisted in the first place.</li>
 *   <li><strong>Exemptions are keyed by code unit, not by type.</strong> Several classes are dual-use:
 *       {@code SignatureTemplateManager} validates algorithms both in its constructor
 *       (construction-time) and in {@code getSignatureInstance} (once per request). A type-level
 *       exemption would hand the request-path method the construction-time method's free pass, so the
 *       key is {@code Type#method}.</li>
 * </ul>
 * Non-vacuity legs keep a passing verdict meaningful: the scanned universe must be non-empty, the
 * detector must find sites at all, it must find a site it is pointed straight at, and every
 * {@link #EXEMPT} entry must still bind to a live site — so a conversion that makes an exemption
 * obsolete fails here until the entry is removed.
 */
@DisplayName("Request-path guards throw a declared type, and every unchecked throw is accounted for")
class UncheckedThrowBoundaryTest {

    private static final String PRODUCTION_ROOT_PACKAGE = "de.cuioss.sheriff.token";

    /**
     * The unchecked types the PLAN-17 partition enumerated, and the population the module-wide leg
     * accounts for. {@link #CONVERTED_REQUEST_PATH_TYPES} is held to the wider standard below.
     */
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
     * Every production code unit that still constructs one of {@link #UNCHECKED_TYPES}, with the reason
     * it is not a request-path failure. Derived from the PLAN-17 partition; see PR #747 for the
     * per-site evidence. An entry that stops binding to a live site is a failure, not a leftover.
     */
    private static final Map<String, String> EXEMPT = Map.ofEntries(
            // --- utility-class instantiation guards ---
            Map.entry("de.cuioss.sheriff.token.commons.metrics.MetricIdentifier#<init>", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.commons.metrics.MetricIdentifier$BEARERTOKEN#<init>", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.commons.metrics.MetricIdentifier$VALIDATION#<init>", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.commons.transport.TransportLogMessages#<init>", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.commons.transport.TransportLogMessages$ERROR#<init>", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.commons.transport.TransportLogMessages$WARN#<init>", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.validation.JWTValidationLogMessages#<init>", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.validation.JWTValidationLogMessages$ERROR#<init>", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.validation.JWTValidationLogMessages$INFO#<init>", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.validation.JWTValidationLogMessages$WARN#<init>", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.validation.jwe.ConcatKdf#<init>", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.validation.jwks.JwksLoaderFactory#<init>", UTILITY_CLASS_GUARD),
            Map.entry("de.cuioss.sheriff.token.validation.metrics.MetricsTickerFactory#<init>", UTILITY_CLASS_GUARD),

            // --- construction-time: builders, configs, the validator's own argument checks ---
            Map.entry("de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig#getHttpHandler",
                    CONSTRUCTION_TIME + " (reached only from HttpJwksLoader.resolveJWKSAdapter, which runs"
                            + " on the construction-triggered async init)"),
            Map.entry("de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig$HttpJwksLoaderConfigBuilder"
                    + "#validateEndpointExclusivity", CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig$HttpJwksLoaderConfigBuilder"
                    + "#build", CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.commons.transport.ParserConfig$ParserConfigBuilder#build",
                    CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.commons.transport.WellKnownConfig$WellKnownConfigBuilder#build",
                    CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.IssuerConfig$IssuerConfigBuilder#build",
                    CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.IssuerConfig$IssuerConfigBuilder#validateConfiguration",
                    CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.TokenValidator#<init>", CONSTRUCTION_TIME
                    + " (the @Builder constructor's issuer-configuration argument checks)"),
            Map.entry("de.cuioss.sheriff.token.validation.TokenValidator$TokenValidatorBuilder#issuerConfigs",
                    CONSTRUCTION_TIME + " (Lombok @Singular null check)"),
            Map.entry("de.cuioss.sheriff.token.validation.TokenValidator$TokenValidatorBuilder"
                    + "#tokenValidationRules", CONSTRUCTION_TIME + " (Lombok @Singular null check)"),
            Map.entry("de.cuioss.sheriff.token.validation.cache.AccessTokenCacheConfig#validate",
                    CONSTRUCTION_TIME + " (called once from the AccessTokenCache constructor)"),
            Map.entry("de.cuioss.sheriff.token.validation.dpop.DpopConfig$DpopConfigBuilder#build",
                    CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig$JweDecryptionConfigBuilder"
                    + "#build", CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.jwks.key.JWKSKeyLoader$JWKSKeyLoaderBuilder#build",
                    CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.jwks.key.JWKSKeyLoader$JWKSKeyLoaderBuilder"
                    + "#loadJwksFromFile", CONSTRUCTION_TIME + " (only reached for a configured jwksFilePath)"),
            Map.entry("de.cuioss.sheriff.token.validation.metrics.TokenValidatorMonitor#<init>",
                    CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.metrics.TokenValidatorMonitorConfig"
                    + "$TokenValidatorMonitorConfigBuilder#measurementTypes",
                    CONSTRUCTION_TIME + " (Lombok @Singular null check)"),
            Map.entry("de.cuioss.sheriff.token.validation.security.JwkAlgorithmPreferences#<init>",
                    CONSTRUCTION_TIME),
            Map.entry("de.cuioss.sheriff.token.validation.security.SignatureAlgorithmPreferences#<init>",
                    CONSTRUCTION_TIME),
            // Dual-use, and the reason this map is keyed by code unit: createSignatureTemplate is called
            // only from the constructor, while getSignatureInstance runs once per request and was
            // converted by PLAN-17. A type-level exemption would have covered both.
            Map.entry("de.cuioss.sheriff.token.validation.pipeline.SignatureTemplateManager"
                    + "#createSignatureTemplate", CONSTRUCTION_TIME
                    + " (constructor-only; the per-request getSignatureInstance was converted by PLAN-17"
                    + " and must stay free of unchecked throws)"),

            // --- loader-init / background-refresh thread only ---
            Map.entry("de.cuioss.sheriff.token.validation.jwks.key.JwkKeyHandler#determineEcAlgorithm",
                    LOADER_THREAD_ONLY + " (called only from KeyProcessor.processEcKey; the per-request"
                            + " getKeyFactory raises the checked InvalidKeySpecException its callers declare)"),
            Map.entry("de.cuioss.sheriff.token.validation.jwks.key.KeyInfo#<init>", LOADER_THREAD_ONLY
                    + " (constructed only by KeyProcessor; the request path only reads existing records)"),
            Map.entry("de.cuioss.sheriff.token.validation.jwks.parser.KeyProcessor#determineEcAlgorithm",
                    LOADER_THREAD_ONLY),

            // --- request path, already translated by an existing narrow catch ---
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.mapper.OffsetDateTimeMapper#map",
                    ALREADY_TRANSLATED + " — TokenBuilder.extractClaims, the module's single sanctioned"
                            + " broad catch for claim mappers (AGENTS.md)"),
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.mapper.ScopeMapper#map",
                    ALREADY_TRANSLATED + " — TokenBuilder.extractClaims"),
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.mapper.StringSplitterMapper#map",
                    ALREADY_TRANSLATED + " — TokenBuilder.extractClaims"),
            Map.entry("de.cuioss.sheriff.token.validation.jwe.JweDecryptor#decryptCek",
                    ALREADY_TRANSLATED + " — JweDecryptor.decrypt narrows"
                            + " GeneralSecurityException|IllegalArgumentException and increments"
                            + " JWE_DECRYPTION_FAILED"),
            Map.entry("de.cuioss.sheriff.token.validation.jwe.JweDecryptor#decryptContent",
                    ALREADY_TRANSLATED + " — JweDecryptor.decrypt"),
            Map.entry("de.cuioss.sheriff.token.validation.jwe.JweDecryptor#getContentEncryptionKeyLength",
                    ALREADY_TRANSLATED + " — JweDecryptor.decrypt, via deriveCekEcdhEs"),
            Map.entry("de.cuioss.sheriff.token.validation.jwe.JweDecryptor#parseEcPublicKeyFromJwk",
                    ALREADY_TRANSLATED + " — JweDecryptor.decrypt, via deriveCekEcdhEs"),

            // --- request path, but an argument-contract guard on a construction-validated value ---
            Map.entry("de.cuioss.sheriff.token.validation.domain.context.ValidationContext#<init>",
                    "request-path but not a token failure: the clock-skew guards re-check a value"
                            + " IssuerConfigBuilder.clockSkewSeconds already validates with"
                            + " Preconditions.checkArgument at build time. The context record is simply"
                            + " rebuilt per request."),
            Map.entry("de.cuioss.sheriff.token.validation.domain.context.ValidationContext#isTokenTooOld",
                    "request-path but dead on its only chain: ExpirationValidator.validateTokenAge"
                            + " early-returns on !isTokenAgeValidationEnabled(), the same predicate this"
                            + " guard tests"),

            // --- outside the validation call ---
            Map.entry("de.cuioss.sheriff.token.validation.domain.claim.CollectionClaimHandler#getValues",
                    POST_VALIDATION_ACCESSOR + " — reached only from AccessTokenContent's"
                            + " providesScopes/Roles/Groups helpers"),
            Map.entry("de.cuioss.sheriff.token.validation.domain.token.IdTokenContent#getAudience",
                    POST_VALIDATION_ACCESSOR + " — getAudience has no main-source caller; the audience"
                            + " claim is validated through getClaimOption, not this accessor"),

            // --- dual-use utility, unreachable from the request path ---
            Map.entry("de.cuioss.sheriff.token.validation.util.JwkThumbprintUtil#canonicalJson",
                    "dual-use and unreachable from the request path: construction-time for the client's"
                            + " DpopProofGenerator constructor, and DpopProofValidator.parsePublicKey"
                            + " already rejects any kty outside RSA/EC/OKP before the thumbprint is taken"),
            Map.entry("de.cuioss.sheriff.token.validation.util.JwkThumbprintUtil#getRequired",
                    "dual-use and unreachable from the request path: JwkKeyHandler requires exactly the"
                            + " members RFC 7638 canonicalization needs per key type ({e,n} / {crv,x,y} /"
                            + " {crv,x}), so by the thumbprint call every required member is present"));

    /**
     * The request-path types PLAN-17 converted. These are held to a stricter standard than the rest of
     * the module by {@link #shouldLeaveNoUncheckedThrowInAConvertedRequestPathType()}: they must
     * construct <em>no</em> unchecked exception at all, not merely none of {@link #UNCHECKED_TYPES}.
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

    /** Sites constructing one of {@link #UNCHECKED_TYPES}, keyed by {@code Type#method}. */
    private static Map<String, List<String>> sitesByCodeUnit;

    @BeforeAll
    static void scanProductionClasses() {
        JavaClasses imported = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(PRODUCTION_ROOT_PACKAGE);
        productionTypes = new TreeMap<>();
        sitesByCodeUnit = new TreeMap<>();
        for (JavaClass type : imported) {
            productionTypes.put(type.getName(), type);
            for (JavaConstructorCall call : type.getConstructorCallsFromSelf()) {
                if (UNCHECKED_TYPES.contains(call.getTargetOwner().getName())) {
                    sitesByCodeUnit
                            .computeIfAbsent(codeUnitKey(call), key -> new ArrayList<>())
                            .add(describe(call));
                }
            }
        }
    }

    @Test
    @DisplayName("the scan is neither empty nor blind — it sees a universe and finds sites in it")
    void shouldScanANonEmptyUniverseAndFindSites() {
        assertFalse(productionTypes.isEmpty(),
                "no production classes were imported from " + PRODUCTION_ROOT_PACKAGE
                        + " — every other leg of this test would pass over an empty universe");
        assertFalse(sitesByCodeUnit.isEmpty(),
                "the scan found no construction of " + UNCHECKED_TYPES + " anywhere in "
                        + PRODUCTION_ROOT_PACKAGE + ". The module is known to hold construction-time"
                        + " guards, so zero sites means the detector stopped matching — probably because"
                        + " getConstructorCallsFromSelf no longer reports these calls — not that the tree"
                        + " is clean.");
        assertTrue(sitesByCodeUnit.containsKey(
                        "de.cuioss.sheriff.token.validation.IssuerConfig$IssuerConfigBuilder#build"),
                "the detector did not find the known unchecked throws in IssuerConfigBuilder.build()."
                        + " That is this test's positive control: if the detector cannot see a site it is"
                        + " pointed straight at, its silence about the request path proves nothing.");
    }

    @Test
    @DisplayName("no production code unit raises an unchecked exception without a recorded reason")
    void shouldRejectAnUnaccountedUncheckedThrow() {
        List<String> unaccounted = new ArrayList<>();
        sitesByCodeUnit.forEach((codeUnit, sites) -> {
            if (!EXEMPT.containsKey(codeUnit)) {
                unaccounted.add(codeUnit + " " + sites);
            }
        });

        assertTrue(unaccounted.isEmpty(),
                "production code units constructing " + UNCHECKED_TYPES + " with no entry in this test's"
                        + " EXEMPT map: " + unaccounted
                        + ". Scanned " + productionTypes.size() + " types and found " + totalSites()
                        + " sites across " + sitesByCodeUnit.size() + " code units, of which "
                        + EXEMPT.size() + " are exempt. If the new site is on the per-request path"
                        + " reachable from TokenValidator.createAccessToken / createIdToken /"
                        + " createRefreshToken or a DPoP validation entry point, throw"
                        + " TokenValidationException with a fitting EventType instead — an undeclared"
                        + " unchecked exception escaping validateAccessToken is what makes a burned"
                        + " refresh token classify as PRE_REDEMPTION. If it is construction-time, add it"
                        + " to EXEMPT with the reason. Note the key is Type#method: an exemption for one"
                        + " method of a class deliberately does not cover another.");
    }

    @Test
    @DisplayName("no exemption has outlived the site it excuses")
    void shouldRejectAStaleExemption() {
        List<String> stale = new ArrayList<>();
        EXEMPT.forEach((codeUnit, reason) -> {
            if (!sitesByCodeUnit.containsKey(codeUnit)) {
                stale.add(codeUnit);
            }
        });

        assertTrue(stale.isEmpty(),
                "EXEMPT names code units that no longer construct any of " + UNCHECKED_TYPES + ": "
                        + stale + ". Either the site was converted or removed — in which case delete the"
                        + " entry, because left in place it silently pre-approves a future unchecked throw"
                        + " in that method — or the class or method was renamed, in which case the"
                        + " exemption is no longer excusing anything and the key must be updated.");
    }

    @Test
    @DisplayName("no converted request-path type has regained an unchecked throw of any kind")
    void shouldLeaveNoUncheckedThrowInAConvertedRequestPathType() {
        List<String> missing = new ArrayList<>();
        List<String> regressed = new ArrayList<>();
        for (String typeName : CONVERTED_REQUEST_PATH_TYPES) {
            JavaClass type = productionTypes.get(typeName);
            if (type == null) {
                missing.add(typeName);
                continue;
            }
            for (JavaConstructorCall call : type.getConstructorCallsFromSelf()) {
                if (isUndeclaredRuntimeException(call.getTargetOwner())) {
                    regressed.add(describe(call));
                }
            }
        }

        assertTrue(missing.isEmpty(),
                "types PLAN-17 converted are absent from the scan: " + missing
                        + " — a rename would make this leg vacuous, so it fails instead");
        assertTrue(regressed.isEmpty(),
                "request-path types converted by PLAN-17 that construct an unchecked exception again: "
                        + regressed + ". These sit on the path reachable from TokenValidator's public"
                        + " entry points, where RefreshFlow.refresh narrows only TokenValidationException"
                        + " — so anything outside the TokenSheriffException hierarchy is read as"
                        + " PRE_REDEMPTION and leaves the session holding a burned refresh token. This leg"
                        + " rejects ANY RuntimeException, not just the four the partition enumerated,"
                        + " because a NoSuchElementException or ClassCastException escapes that catch"
                        + " exactly as an IllegalStateException does.");
    }

    @Test
    @DisplayName("every exemption records a reason, and the exemption set matches the site set exactly")
    void shouldRequireAReasonForEveryExemption() {
        List<String> withoutReason = new ArrayList<>();
        EXEMPT.forEach((codeUnit, reason) -> {
            if (reason == null || reason.isBlank()) {
                withoutReason.add(codeUnit);
            }
        });
        assertTrue(withoutReason.isEmpty(), "EXEMPT entries carrying no reason: " + withoutReason);

        List<String> siteWithoutExemption = new ArrayList<>(sitesByCodeUnit.keySet());
        siteWithoutExemption.removeAll(EXEMPT.keySet());
        List<String> exemptionWithoutSite = new ArrayList<>(EXEMPT.keySet());
        exemptionWithoutSite.removeAll(sitesByCodeUnit.keySet());
        assertEquals(EXEMPT.size(), sitesByCodeUnit.size(),
                "the set of code units holding an unchecked-construction site and the set of exemptions"
                        + " must be identical, so neither can drift behind the other. Sites with no"
                        + " exemption: " + siteWithoutExemption + "; exemptions with no site: "
                        + exemptionWithoutSite + " (" + totalSites() + " sites across "
                        + sitesByCodeUnit.size() + " code units of " + productionTypes.size()
                        + " scanned types).");
    }

    /**
     * @return {@code true} when {@code thrown} is a {@link RuntimeException} outside the library's own
     *         {@link TokenSheriffException} hierarchy — that hierarchy is the declared contract every
     *         caller of {@code TokenValidator} already handles, so it is what the request path is
     *         allowed to raise
     */
    private static boolean isUndeclaredRuntimeException(JavaClass thrown) {
        return thrown.isAssignableTo(RuntimeException.class)
                && !thrown.isAssignableTo(TokenSheriffException.class);
    }

    /** @return the {@code Type#method} key an exemption is recorded under */
    private static String codeUnitKey(JavaConstructorCall call) {
        return call.getOriginOwner().getName() + "#" + call.getOrigin().getName();
    }

    /**
     * @return a human-readable site, naming the exception, the declaring code unit and the line —
     *         lambda bodies included, because a lambda compiles into a synthetic method of the same
     *         class and its calls are reported with it
     */
    private static String describe(JavaConstructorCall call) {
        return call.getTargetOwner().getSimpleName() + " at " + call.getOriginOwner().getSimpleName()
                + "#" + call.getOrigin().getName() + ":" + call.getLineNumber();
    }

    private static int totalSites() {
        return sitesByCodeUnit.values().stream().mapToInt(List::size).sum();
    }
}
