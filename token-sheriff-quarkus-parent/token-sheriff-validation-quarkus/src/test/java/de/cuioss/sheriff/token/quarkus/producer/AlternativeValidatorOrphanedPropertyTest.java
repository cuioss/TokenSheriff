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
package de.cuioss.sheriff.token.quarkus.producer;

import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.quarkus.config.JwtPropertyKeys;
import de.cuioss.sheriff.token.quarkus.config.JwtTestProfile;
import de.cuioss.sheriff.token.quarkus.observability.ObservedValidatorResolver;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenType;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.generator.ClaimControlParameter;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Settles the ADR-0026 question for an application that replaces the extension's default-qualified
 * {@link TokenValidator} with its own enabled {@link Alternative}, while the
 * {@code sheriff.token.issuers.*} namespace carries an enabled issuer and an orphaned
 * {@code dpop.required} property (set without {@code dpop.enabled}).
 * <p>
 * That the class runs at all is the first observation: the application starts, because
 * {@link TokenValidatorStartupInitializer} resolves the default {@code TokenValidator}, which is the
 * alternative, so the extension's {@link TokenValidatorProducer} is not built and its resolvers do not
 * run at startup. The tests pin what happens afterwards.
 */
@QuarkusTest
@Tag("quarkus-boot")
@TestProfile(AlternativeValidatorOrphanedPropertyTest.AlternativeValidatorProfile.class)
@EnableTestLogger
@DisplayName("Application-supplied @Alternative TokenValidator with an orphaned property")
class AlternativeValidatorOrphanedPropertyTest {

    private static final String ISSUER = "default";

    private static final String ORPHANED_PROPERTY = JwtPropertyKeys.ISSUERS.DPOP_REQUIRED.formatted(ISSUER);

    /**
     * Test-only replacement of the default-qualified validator. Enabled solely through
     * {@link AlternativeValidatorProfile#getEnabledAlternatives()}, so it does not leak into other
     * profiles' applications.
     */
    @Alternative
    @ApplicationScoped
    public static class ApplicationValidatorProducer {

        static final AtomicReference<TokenValidator> PRODUCED = new AtomicReference<>();

        @Produces
        @Singleton
        TokenValidator applicationValidator() {
            TestTokenHolder tokenHolder = new TestTokenHolder(TokenType.ACCESS_TOKEN,
                    ClaimControlParameter.defaultForTokenType(TokenType.ACCESS_TOKEN));
            TokenValidator validator = TokenValidator.builder()
                    .parserConfig(ParserConfig.builder().build())
                    .issuerConfig(tokenHolder.getIssuerConfig())
                    .build();
            PRODUCED.set(validator);
            return validator;
        }
    }

    /**
     * The regular test issuer plus {@code dpop.required=true} without {@code dpop.enabled}, which
     * {@code IssuerConfigResolver} rejects whenever it runs.
     */
    public static class AlternativeValidatorProfile extends JwtTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> config = new HashMap<>(super.getConfigOverrides());
            config.put(ORPHANED_PROPERTY, "true");
            return config;
        }

        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(ApplicationValidatorProducer.class);
        }
    }

    @Inject
    TokenValidator validatorInUse;

    @Inject
    ObservedValidatorResolver resolver;

    @Inject
    Instance<List<IssuerConfig>> producedIssuerConfigs;

    @Test
    @DisplayName("the validator in use is the application's alternative, not the extension's producer")
    void validatorInUseIsTheAlternative() {
        assertSame(ApplicationValidatorProducer.PRODUCED.get(), validatorInUse,
                "The default-qualified TokenValidator must be the enabled alternative");
    }

    @Test
    @DisplayName("the observability resolver observes the alternative without building the producer")
    void observabilityObservesTheAlternative() {
        assertAll("observed validator",
                () -> assertEquals(ObservedValidatorResolver.Outcome.EXTERNAL_VALIDATOR, resolver.outcome(),
                        "Only the enabled alternative remains eligible, so the resolver reports it as external"),
                () -> assertSame(validatorInUse, resolver.observedValidator().orElseThrow(),
                        "The observed validator must be the validator in use"));
    }

    @Test
    @DisplayName("the orphaned property is reported only when a producer by-product is dereferenced")
    void orphanedPropertyFailsOnFirstProducerDereference() {
        List<IssuerConfig> issuerConfigs = producedIssuerConfigs.get();

        RuntimeException failure = assertThrows(RuntimeException.class, issuerConfigs::size,
                "Dereferencing a by-product of TokenValidatorProducer builds it and runs the resolvers");

        IllegalStateException orphaned = findIllegalState(failure);
        assertNotNull(orphaned, "The failure must carry the resolver's IllegalStateException, was: " + failure);
        assertTrue(orphaned.getMessage().contains(ORPHANED_PROPERTY),
                "The error must name the orphaned property, was: " + orphaned.getMessage());
    }

    private static IllegalStateException findIllegalState(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof IllegalStateException illegalState) {
                return illegalState;
            }
            current = current.getCause();
        }
        return null;
    }
}
