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
package de.cuioss.sheriff.token.quarkus.observability;

import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.quarkus.config.IssuerConfigResolver;
import de.cuioss.sheriff.token.quarkus.config.JwtPropertyKeys;
import de.cuioss.sheriff.token.quarkus.producer.TokenValidatorProducer;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.IssuerStatusView;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.tools.logging.CuiLogger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static de.cuioss.sheriff.token.quarkus.TokenSheriffQuarkusLogMessages.INFO;
import static de.cuioss.sheriff.token.quarkus.TokenSheriffQuarkusLogMessages.WARN;

/**
 * Resolves which {@link TokenValidator} the extension's observability beans should report against.
 * <p>
 * The extension's own {@link TokenValidatorProducer} initializes from the
 * {@code sheriff.token.issuers.*} property namespace and fails fast when that namespace yields no
 * enabled issuer. Observability beans must therefore not bind to that producer's by-products:
 * an embedder that supplies its own qualified {@code TokenValidator} and leaves the namespace
 * unpopulated would otherwise trigger the producer's failure on every health probe, metrics tick
 * and DevUI call.
 * </p>
 * <p>
 * This resolver answers "which validator is in use, and is anything configured at all?" without
 * eagerly instantiating the extension's producer:
 * </p>
 * <ol>
 *   <li>Probe {@link IssuerConfigResolver#hasEnabledIssuers(Config)} — a pure {@link Config} read
 *       that touches no CDI bean.</li>
 *   <li>Namespace populated &rarr; {@link Outcome#PROPERTY_CONFIGURED}: the extension's own
 *       produced validator, issuer configurations and parser configuration are the observation
 *       target.</li>
 *   <li>Namespace unpopulated &rarr; enumerate {@code @Any Instance<TokenValidator>} handles and
 *       skip the handle whose bean class is {@link TokenValidatorProducer}. Reading a handle's bean
 *       metadata does not instantiate it, so the failing producer is never initialized. Exactly one
 *       surviving handle &rarr; {@link Outcome#EXTERNAL_VALIDATOR}; several &rarr; a warning naming
 *       the candidates and {@link Outcome#NOT_CONFIGURED}.</li>
 *   <li>Nothing found &rarr; {@link Outcome#NOT_CONFIGURED}.</li>
 * </ol>
 * <p>
 * On the {@link Outcome#EXTERNAL_VALIDATOR} and {@link Outcome#NOT_CONFIGURED} outcomes
 * {@link #observedIssuerConfigs()} is empty and {@link #observedParserConfig()} is
 * {@link Optional#empty()}: the core {@code TokenValidator} exposes neither its issuer
 * configurations nor its parser configuration. On {@link Outcome#EXTERNAL_VALIDATOR},
 * {@link #observedIssuerStatuses()} reports the external validator's read-only issuer status views
 * when the {@value JwtPropertyKeys.HEALTH.JWKS#REPORT_EXTERNAL_VALIDATOR} opt-in is set.
 * </p>
 * <p>
 * The resolution is memoized, but a {@link Outcome#NOT_CONFIGURED} outcome is re-resolved on every
 * call so a validator that becomes available later is still picked up.
 * </p>
 *
 * @since 1.0
 */
@ApplicationScoped
public class ObservedValidatorResolver {

    private static final CuiLogger LOGGER = new CuiLogger(ObservedValidatorResolver.class);

    /**
     * The resolution outcome.
     */
    public enum Outcome {
        /** The {@code sheriff.token.issuers.*} namespace is populated; the extension's own producer is observed. */
        PROPERTY_CONFIGURED,
        /** The namespace is empty and exactly one foreign {@link TokenValidator} bean is observable. */
        EXTERNAL_VALIDATOR,
        /** Neither a configured namespace nor an unambiguous foreign validator exists. */
        NOT_CONFIGURED
    }

    private record Resolution(
    Outcome outcome,
    @Nullable
    TokenValidator validator,
    List<IssuerConfig> issuerConfigs,
    @Nullable
    ParserConfig parserConfig) {
    }

    /**
     * The lazily-consulted CDI dependencies. {@code null} when this resolver was constructed with an
     * already-resolved outcome, in which case no resolution ever runs.
     */
    private record CdiHandles(
    Config config,
    Instance<TokenValidator> tokenValidators,
    Instance<List<IssuerConfig>> issuerConfigs,
    Instance<ParserConfig> parserConfig) {
    }

    private static final Resolution UNRESOLVED =
            new Resolution(Outcome.NOT_CONFIGURED, null, List.of(), null);

    private final @Nullable CdiHandles cdi;

    /**
     * The {@value JwtPropertyKeys.HEALTH.JWKS#REPORT_EXTERNAL_VALIDATOR} opt-in of a resolver pinned to
     * an already-resolved outcome. A CDI-constructed resolver reads the key from its {@link Config}.
     */
    private final boolean pinnedReportExternalValidator;

    private final AtomicReference<Resolution> resolution = new AtomicReference<>(UNRESOLVED);
    private volatile @Nullable Outcome lastLoggedOutcome;
    private final AtomicReference<List<String>> lastWarnedCandidates = new AtomicReference<>(List.of());

    /**
     * CDI constructor. Every producer-sourced dependency is a lazy {@link Instance} handle, so
     * constructing this bean instantiates nothing.
     *
     * @param config              the MicroProfile configuration used for the property probe
     * @param tokenValidators     all {@link TokenValidator} beans, regardless of qualifier
     * @param issuerConfigsHandle lazy handle to the produced issuer configurations
     * @param parserConfigHandle  lazy handle to the produced parser configuration
     */
    @Inject
    public ObservedValidatorResolver(Config config, @Any Instance<TokenValidator> tokenValidators,
            Instance<List<IssuerConfig>> issuerConfigsHandle, Instance<ParserConfig> parserConfigHandle) {
        this.cdi = new CdiHandles(config, tokenValidators, issuerConfigsHandle, parserConfigHandle);
        this.pinnedReportExternalValidator = false;
    }

    /**
     * Creates a resolver pinned to an already-resolved outcome, so this bean and its consumers can be
     * exercised without a CDI container.
     *
     * @param outcome       the fixed resolution outcome
     * @param validator     the observed validator, or {@code null} when nothing is observable
     * @param issuerConfigs the observed issuer configurations
     * @param parserConfig  the observed parser configuration, or {@code null} when unavailable
     */
    public ObservedValidatorResolver(Outcome outcome, @Nullable TokenValidator validator,
            List<IssuerConfig> issuerConfigs, @Nullable ParserConfig parserConfig) {
        this(outcome, validator, issuerConfigs, parserConfig, false);
    }

    /**
     * Creates a resolver pinned to an already-resolved outcome and to a fixed value of the
     * {@value JwtPropertyKeys.HEALTH.JWKS#REPORT_EXTERNAL_VALIDATOR} opt-in, so this bean and its
     * consumers can be exercised without a CDI container.
     *
     * @param outcome                 the fixed resolution outcome
     * @param validator               the observed validator, or {@code null} when nothing is observable
     * @param issuerConfigs           the observed issuer configurations
     * @param parserConfig            the observed parser configuration, or {@code null} when unavailable
     * @param reportExternalValidator whether {@link #observedIssuerStatuses()} reports the issuers of an
     *                                external validator
     */
    public ObservedValidatorResolver(Outcome outcome, @Nullable TokenValidator validator,
            List<IssuerConfig> issuerConfigs, @Nullable ParserConfig parserConfig, boolean reportExternalValidator) {
        this.cdi = null;
        this.pinnedReportExternalValidator = reportExternalValidator;
        this.resolution.set(new Resolution(outcome, validator, List.copyOf(issuerConfigs), parserConfig));
    }

    /**
     * @return the current resolution outcome
     */
    public Outcome outcome() {
        return resolve().outcome();
    }

    /**
     * @return the observed validator, or {@link Optional#empty()} when nothing is observable
     */
    public Optional<TokenValidator> observedValidator() {
        return Optional.ofNullable(resolve().validator());
    }

    /**
     * @return the observed issuer configurations; empty unless the outcome is
     *         {@link Outcome#PROPERTY_CONFIGURED}
     */
    public List<IssuerConfig> observedIssuerConfigs() {
        return resolve().issuerConfigs();
    }

    /**
     * @return the observed parser configuration; empty unless the outcome is
     *         {@link Outcome#PROPERTY_CONFIGURED}
     */
    public Optional<ParserConfig> observedParserConfig() {
        return Optional.ofNullable(resolve().parserConfig());
    }

    /**
     * Returns the read-only issuer status views of an externally produced validator.
     * <p>
     * The views are reported only when the outcome is {@link Outcome#EXTERNAL_VALIDATOR} and the
     * {@value JwtPropertyKeys.HEALTH.JWKS#REPORT_EXTERNAL_VALIDATOR} opt-in is {@code true}. Reading
     * them fetches no keys and exposes no JWKS loader.
     * </p>
     *
     * @return the external validator's {@link TokenValidator#getIssuerStatuses()}; empty for every
     *         other outcome and when the opt-in is not set
     */
    public List<IssuerStatusView> observedIssuerStatuses() {
        Resolution current = resolve();
        TokenValidator validator = current.validator();
        if (current.outcome() != Outcome.EXTERNAL_VALIDATOR || validator == null || !reportExternalValidator()) {
            return List.of();
        }
        return validator.getIssuerStatuses();
    }

    private boolean reportExternalValidator() {
        CdiHandles handles = cdi;
        if (handles == null) {
            return pinnedReportExternalValidator;
        }
        return handles.config()
                .getOptionalValue(JwtPropertyKeys.HEALTH.JWKS.REPORT_EXTERNAL_VALIDATOR, Boolean.class)
                .orElse(false);
    }

    private Resolution resolve() {
        CdiHandles handles = cdi;
        if (handles == null) {
            return resolution.get();
        }
        Resolution current = resolution.get();
        if (current.outcome() != Outcome.NOT_CONFIGURED) {
            return current;
        }
        Resolution resolved = doResolve(handles);
        resolution.set(resolved);
        return resolved;
    }

    private Resolution doResolve(CdiHandles handles) {
        Instance.Handle<TokenValidator> producerHandle = null;
        List<Instance.Handle<TokenValidator>> foreignHandles = new ArrayList<>();
        for (Instance.Handle<TokenValidator> handle : handles.tokenValidators().handles()) {
            if (TokenValidatorProducer.class.equals(handle.getBean().getBeanClass())) {
                producerHandle = handle;
            } else {
                foreignHandles.add(handle);
            }
        }

        if (producerHandle != null && IssuerConfigResolver.hasEnabledIssuers(handles.config())) {
            return logged(new Resolution(Outcome.PROPERTY_CONFIGURED, producerHandle.get(),
                    handles.issuerConfigs().get(), handles.parserConfig().get()), List.of());
        }

        if (foreignHandles.size() == 1) {
            return logged(new Resolution(Outcome.EXTERNAL_VALIDATOR, foreignHandles.getFirst().get(),
                    List.of(), null), List.of());
        }

        List<String> candidates = foreignHandles.stream()
                .map(handle -> handle.getBean().getBeanClass().getName())
                .toList();
        return logged(UNRESOLVED, candidates);
    }

    /**
     * Emits the resolution transition once per distinct outcome rather than on every probe.
     * <p>
     * The ambiguity warning is gated separately, on the candidate set rather than on the outcome
     * transition: {@link Outcome#NOT_CONFIGURED} is re-resolved on every call, so an outcome-gated
     * warning would stay silent for an ambiguity that only appears after an earlier zero-candidate
     * probe already logged {@code NOT_CONFIGURED}.
     * </p>
     */
    private Resolution logged(Resolution resolved, List<String> ambiguousCandidates) {
        if (ambiguousCandidates.size() > 1 && !ambiguousCandidates.equals(lastWarnedCandidates.get())) {
            LOGGER.warn(WARN.AMBIGUOUS_EXTERNAL_VALIDATORS, String.join(", ", ambiguousCandidates));
            lastWarnedCandidates.set(ambiguousCandidates);
        }
        if (resolved.outcome() != lastLoggedOutcome) {
            LOGGER.info(INFO.OBSERVED_VALIDATOR_RESOLUTION, resolved.outcome());
            lastLoggedOutcome = resolved.outcome();
        }
        return resolved;
    }
}
