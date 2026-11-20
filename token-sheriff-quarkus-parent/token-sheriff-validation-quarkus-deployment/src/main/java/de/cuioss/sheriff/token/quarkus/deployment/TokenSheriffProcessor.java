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
package de.cuioss.sheriff.token.quarkus.deployment;

import de.cuioss.sheriff.token.commons.error.ErrorTextSanitizer;
import de.cuioss.sheriff.token.commons.http.BearerChallenge;
import de.cuioss.sheriff.token.quarkus.annotation.BearerAuth;
import de.cuioss.sheriff.token.quarkus.annotation.BearerToken;
import de.cuioss.sheriff.token.quarkus.config.AccessLogFilterConfigResolver;
import de.cuioss.sheriff.token.quarkus.config.JwtPropertyKeys;
import de.cuioss.sheriff.token.quarkus.error.ProblemDetailExceptionMapper;
import de.cuioss.sheriff.token.quarkus.health.JwksEndpointHealthCheck;
import de.cuioss.sheriff.token.quarkus.health.TokenValidatorHealthCheck;
import de.cuioss.sheriff.token.quarkus.interceptor.BearerTokenInterceptor;
import de.cuioss.sheriff.token.quarkus.logging.CustomAccessLogFilter;
import de.cuioss.sheriff.token.quarkus.mapper.ClaimMapperRegistry;
import de.cuioss.sheriff.token.quarkus.mapper.DiscoverableClaimMapper;
import de.cuioss.sheriff.token.quarkus.mapper.keycloak.KeycloakGroupsMapperBean;
import de.cuioss.sheriff.token.quarkus.mapper.keycloak.KeycloakRolesMapperBean;
import de.cuioss.sheriff.token.quarkus.metrics.JwtMetricsCollector;
import de.cuioss.sheriff.token.quarkus.observability.ObservedValidatorResolver;
import de.cuioss.sheriff.token.quarkus.producer.BearerTokenProducer;
import de.cuioss.sheriff.token.quarkus.producer.BearerTokenResponseFactory;
import de.cuioss.sheriff.token.quarkus.producer.JsonWebTokenAdapter;
import de.cuioss.sheriff.token.quarkus.producer.TokenValidatorProducer;
import de.cuioss.sheriff.token.quarkus.producer.TokenValidatorStartupInitializer;
import de.cuioss.sheriff.token.quarkus.runtime.TokenSheriffDevUIRuntimeService;
import de.cuioss.sheriff.token.quarkus.servlet.VertxServletObjectsResolver;
import de.cuioss.sheriff.token.quarkus.validation.DiscoverableTokenValidationRule;
import de.cuioss.sheriff.token.quarkus.validation.TokenValidationRuleRegistry;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanArchiveIndexBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.resteasy.common.spi.ResteasyJaxrsProviderBuildItem;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.spi.DeploymentException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Processor for the Token-Sheriff Quarkus extension.
 * <p>
 * This class handles the build-time processing for the extension: registering the feature, the CDI
 * and JAX-RS wiring, the Quarkus-specific reflection registrations and the DevUI integration.
 * </p>
 * <p>
 * It does <em>not</em> declare the core library's reflection contract. {@code token-sheriff-validation}
 * ships its own GraalVM metadata under
 * {@code META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-validation/}, which GraalVM
 * auto-detects from the jar — for Quarkus and non-Quarkus native builds alike. Core types therefore
 * belong in that metadata, never in this processor.
 * </p>
 *
 * @since 1.0
 */
public class TokenSheriffProcessor {

    /**
     * The feature name for the Token-Sheriff extension.
     */
    private static final String FEATURE = "token-sheriff";

    /**
     * Logger for build-time processing.
     */
    private static final CuiLogger LOGGER = new CuiLogger(TokenSheriffProcessor.class);

    /**
     * LogRecord for feature registration.
     */
    private static final LogRecord TOKEN_SHERIFF_FEATURE_REGISTERED = LogRecordModel.builder()
            .template("Token-Sheriff feature registered")
            .prefix("TokenSheriff_Q_D")
            .identifier(1)
            .build();

    private static final DotName BEARER_AUTH = DotName.createSimple(BearerAuth.class.getName());

    private static final DotName BEARER_TOKEN = DotName.createSimple(BearerToken.class.getName());

    private static final DotName VETOED = DotName.createSimple(Vetoed.class.getName());

    private static final String PACKAGE_INFO = ".package-info";

    private static final String REQUIRED_SCOPES = "requiredScopes";

    /**
     * Register the Token-Sheriff feature.
     *
     * @return A {@link FeatureBuildItem} for the Token-Sheriff feature
     */
    @BuildStep
    public FeatureBuildItem feature() {
        LOGGER.info(TOKEN_SHERIFF_FEATURE_REGISTERED);
        return new FeatureBuildItem(FEATURE);
    }


    /**
     * Register the Quarkus-specific classes that need reflection.
     * <p>
     * Only types this extension owns or bridges are registered here. The core library's own
     * reflection contract ships with {@code token-sheriff-validation} under
     * {@code META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-validation/} and is
     * auto-detected by GraalVM, so it must not be duplicated in this processor.
     * <p>
     * {@link JwtPropertyKeys.ISSUERS} is registered with its fields because its key definitions are read
     * reflectively: {@link JwtPropertyKeys.ISSUERS#DPOP_SUB_PROPERTIES} is derived from them, and the
     * orphaned-DPoP guard checks exactly that list. This registration is what makes the list derivable in
     * a native build. The runtime jar is not indexed, so the {@code @RegisterForReflection} on
     * {@code ISSUERS} alone is not enough: without its fields the derivation would come up short and fail
     * the initialization of {@code ISSUERS}, and so the startup.
     *
     * @param reflectiveClasses producer for reflective class build items
     */
    @BuildStep
    public void registerQuarkusSpecificClassesForReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
        // Micrometer registry for metrics
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(MeterRegistry.class)
                .methods(true)
                .fields(false)
                .constructors(true)
                .build());

        // MicroProfile JWT interface and adapter for CDI injection
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
                JsonWebToken.class,
                JsonWebTokenAdapter.class)
                .methods(true)
                .fields(true)
                .constructors(true)
                .build());

        // CDI-discoverable claim mapper interface
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(DiscoverableClaimMapper.class)
                .methods(false)
                .fields(false)
                .constructors(true)
                .build());

        // Issuer key definitions whose static String constants are read reflectively to derive the
        // DPoP orphan guard's key list. The runtime jar is not indexed, so @RegisterForReflection on
        // ISSUERS alone does not reach a native build; without this the list would be empty.
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(JwtPropertyKeys.ISSUERS.class)
                .methods(false)
                .fields(true)
                .constructors(false)
                .build());
    }

    /**
     * Register DSL-JSON service providers for native image.
     * This ensures DSL-JSON converters can be found via service loader at runtime.
     */
    @BuildStep
    public void registerDslJsonServiceProviders(BuildProducer<ServiceProviderBuildItem> serviceProvider) {
        // Register all DSL-JSON configurations from classpath
        serviceProvider.produce(ServiceProviderBuildItem.allProvidersFromClassPath("com.dslplatform.json.Configuration"));
    }


    /**
     * Register additional CDI beans for JWT validation.
     *
     * @return A {@link AdditionalBeanBuildItem} for CDI beans that need explicit registration
     */
    @BuildStep
    public AdditionalBeanBuildItem additionalBeans() {
        return AdditionalBeanBuildItem.builder()
                // Explicitly register the CDI producer classes to ensure they're discovered
                .addBeanClasses(
                        TokenValidatorProducer.class,
                        // Eagerly builds the property-configured validator on StartupEvent. Nothing
                        // injects it, so it relies on the unremovable flag below to survive bean removal.
                        TokenValidatorStartupInitializer.class,
                        BearerTokenProducer.class,
                        VertxServletObjectsResolver.class,
                        // Observation target resolution for the metrics, health and DevUI beans
                        ObservedValidatorResolver.class,
                        JwtMetricsCollector.class,
                        // MicroProfile Health checks. The runtime jar carries no bean archive
                        // marker, so without this registration SmallRye Health never sees them and
                        // the application answers its health endpoints with an empty check list.
                        JwksEndpointHealthCheck.class,
                        TokenValidatorHealthCheck.class,
                        // CDI-based claim mapper infrastructure
                        ClaimMapperRegistry.class,
                        KeycloakRolesMapperBean.class,
                        KeycloakGroupsMapperBean.class,
                        // CDI-based token validation rule infrastructure
                        TokenValidationRuleRegistry.class
                )
                // Register additional configuration producers using class references
                .addBeanClass(AccessLogFilterConfigResolver.class)
                .addBeanClass(CustomAccessLogFilter.class)
                // Register interceptor infrastructure
                .addBeanClass(BearerTokenInterceptor.class)
                .setUnremovable()
                .build();
    }

    /**
     * Register the CustomAccessLogFilter as a JAX-RS provider.
     * This is required for Quarkus extensions to properly register JAX-RS providers.
     *
     * @return A {@link ResteasyJaxrsProviderBuildItem} for the CustomAccessLogFilter
     */
    @BuildStep
    public ResteasyJaxrsProviderBuildItem registerCustomAccessLogFilter() {
        return new ResteasyJaxrsProviderBuildItem(CustomAccessLogFilter.class.getName());
    }

    /**
     * Register the {@link ProblemDetailExceptionMapper} as a JAX-RS provider.
     * <p>
     * The mapper lives in the extension's runtime jar, which is not part of the application's index, so
     * its {@code @Provider} annotation alone does not register it. Without this registration a
     * {@code TokenSheriffException} escaping a resource is not rendered as an RFC 9457 problem
     * document.
     * </p>
     * <p>
     * <strong>RESTEasy Classic only:</strong> {@link ResteasyJaxrsProviderBuildItem} is consumed by
     * RESTEasy Classic. Applications running on Quarkus REST (RESTEasy Reactive) do not pick up this
     * registration.
     * </p>
     *
     * @return A {@link ResteasyJaxrsProviderBuildItem} for the ProblemDetailExceptionMapper
     */
    @BuildStep
    public ResteasyJaxrsProviderBuildItem registerProblemDetailExceptionMapper() {
        return new ResteasyJaxrsProviderBuildItem(ProblemDetailExceptionMapper.class.getName());
    }

    /**
     * Register core JWT validation beans as unremovable to ensure they're available for injection.
     * This is critical for native image compilation where CDI discovery can be limited.
     *
     * @param unremovableBeans producer for unremovable bean build items
     */
    @BuildStep
    public void registerUnremovableBeans(BuildProducer<UnremovableBeanBuildItem> unremovableBeans) {
        // Ensure core library beans are never removed from the CDI container
        unremovableBeans.produce(UnremovableBeanBuildItem.beanTypes(
                DotName.createSimple(TokenValidator.class.getName()),
                DotName.createSimple(JwtMetricsCollector.class.getName()),
                DotName.createSimple(MeterRegistry.class.getName()),
                // Ensure user-provided DiscoverableClaimMapper implementations are discovered
                DotName.createSimple(DiscoverableClaimMapper.class.getName()),
                // Ensure user-provided DiscoverableTokenValidationRule implementations are discovered
                DotName.createSimple(DiscoverableTokenValidationRule.class.getName())
        ));
    }


    /**
     * Refuses the deployment when an annotation declares required scopes that cannot be sent in an RFC 6750
     * {@code insufficient_scope} challenge.
     * <p>
     * {@link BearerTokenResponseFactory#insufficientScopeChallenge} builds that challenge through
     * {@link BearerChallenge}, which refuses a scope name outside the RFC 6749 §3.3 {@code scope-token}
     * grammar, more than {@value BearerChallenge#MAX_SCOPE_TOKENS} distinct scopes and a challenge longer than
     * {@value BearerChallenge#MAX_LENGTH} characters once the realm and the {@code error_description} are
     * added. Without this step such an annotation would surface as an unhandled
     * {@link IllegalArgumentException} on the first request that misses a scope; with it, the
     * misconfiguration fails the deployment, with one error per refused scope set naming its declaring class
     * and member. Class-level and method-level {@code @BearerAuth} (merged per bean class as the interceptor
     * merges them) and every {@code @BearerToken} injection point are checked.
     * <p>
     * The step scans the bean-archive index and the combined index together. The bean-archive index holds the
     * bean archives and the beans ArC generates during the build (for example from a
     * {@code GeneratedBeanBuildItem}), which the combined index does not, so an invalid scope on a generated
     * bean fails the deployment. The combined index holds every indexed application archive, including a
     * dependency that is no bean archive because it carries neither a {@code beans.xml} nor a bean-defining
     * annotation, so a {@code @BearerAuth} method that an application bean class inherits from a base class in
     * such a dependency is merged with that bean class's own scopes and checked as well. The bean-archive index
     * resolves such a base class on demand when it is asked for it by name, but does not enumerate its
     * annotations. A class in both indexes yields the same message twice, which is reported once.
     * <p>
     * The package veto that keeps a class from being merged as a bean class is read from the same composite: a
     * {@code {package}.package-info} declaring {@code @Vetoed} in either index vetoes the classes of that package
     * and not those of its subpackages. ArC was shown to register no bean for such a class when the
     * {@code package-info} sits in the application root or in a dependency archive next to the class; a
     * {@code package-info} indexed only in the combined index while its classes sit in a bean archive was not
     * shown.
     * <p>
     * A class in neither index is not checked, for example a base class in a dependency that is not indexed at
     * all.
     *
     * @param validationPhase  orders this step into the ArC validation phase; not otherwise read
     * @param beanArchiveIndex the bean-archive Jandex index, generated beans included
     * @param combinedIndex    the combined Jandex index of the indexed application archives
     * @param validationErrors producer for the deployment errors, one per refused scope set
     */
    @BuildStep
    public void validateAnnotationScopes(ValidationPhaseBuildItem validationPhase,
            BeanArchiveIndexBuildItem beanArchiveIndex, CombinedIndexBuildItem combinedIndex,
            BuildProducer<ValidationErrorBuildItem> validationErrors) {
        List<String> violations = findScopeViolations(
                scannedIndex(beanArchiveIndex.getIndex(), combinedIndex.getIndex()));
        if (!violations.isEmpty()) {
            validationErrors.produce(new ValidationErrorBuildItem(violations.stream()
                    .map(DeploymentException::new)
                    .toArray(Throwable[]::new)));
        }
    }

    /**
     * Builds the index {@link #validateAnnotationScopes} scans: the composite of the bean-archive index and the
     * combined index, so a class, an annotation or a {@code package-info} from either of the two is seen.
     *
     * @param beanArchiveIndex the bean-archive Jandex index, generated beans included
     * @param combinedIndex    the combined Jandex index of the indexed application archives
     * @return the composite of the two indexes
     */
    static IndexView scannedIndex(IndexView beanArchiveIndex, IndexView combinedIndex) {
        return CompositeIndex.create(beanArchiveIndex, combinedIndex);
    }

    /**
     * Checks the required scopes of every {@link BearerAuth} and {@link BearerToken} annotation in the index.
     * <p>
     * A class-level {@code @BearerAuth} is checked on its own. A method-level {@code @BearerAuth} is checked
     * once per bean class that runs the method, together with the class-level {@code @BearerAuth} of that bean
     * class, as the union {@code BearerTokenInterceptor} enforces (ADR-0024): the interceptor reads the
     * class-level annotation from the bean class, not from the class declaring the method, and
     * {@code @BearerAuth} is not {@code @Inherited}, so an inherited method is merged with the subclass's own
     * annotation only. An interface, an abstract class, a class declaring {@code @Vetoed} and a class in a
     * package whose {@code package-info} declares {@code @Vetoed} are no bean classes — ArC registers no bean
     * for them — so their class-level scopes are never merged. A {@code @BearerToken} on a field, a method, a method parameter or a producer parameter
     * is checked on its own. Each scope set is checked by building the challenge through
     * {@link BearerTokenResponseFactory#insufficientScopeChallenge}, so the rule and the length bound are
     * exactly the ones applied to the challenge that is sent.
     * <p>
     * Each message names the declaring class and member, for a merged method also the bean class whose
     * class-level annotation was merged, and the reason. A scope name outside the grammar is rendered through
     * {@link ErrorTextSanitizer#sanitize(String)}, never raw.
     *
     * @param index the index to scan
     * @return one message per refused scope set, sorted and each message once, also when the index lists an
     *         annotation or a bean class more than once; empty when every annotation is valid
     */
    static List<String> findScopeViolations(IndexView index) {
        Set<String> violations = new TreeSet<>();
        for (AnnotationInstance bearerAuth : index.getAnnotations(BEARER_AUTH)) {
            AnnotationTarget target = bearerAuth.target();
            if (target.kind() == AnnotationTarget.Kind.METHOD) {
                checkMethodLevel(index, target.asMethod(), requiredScopes(bearerAuth), violations);
            } else {
                refusal(requiredScopes(bearerAuth)).ifPresent(reason -> violations.add(
                        declaresMessage("@BearerAuth", target, reason)));
            }
        }
        for (AnnotationInstance bearerToken : index.getAnnotations(BEARER_TOKEN)) {
            refusal(requiredScopes(bearerToken)).ifPresent(reason -> violations.add(
                    declaresMessage("@BearerToken", bearerToken.target(), reason)));
        }
        return new ArrayList<>(violations);
    }

    /**
     * Checks a method-level {@code @BearerAuth} once per bean class that runs the method, merged with that bean
     * class's own class-level {@code @BearerAuth} as {@code BearerTokenInterceptor} merges them.
     * <p>
     * A bean class runs the method when it is the declaring class or a subclass of it that neither it nor a
     * class between it and the declaring class overrides; interfaces, abstract classes, classes declaring
     * {@code @Vetoed} and classes in a {@code @Vetoed} package are no bean classes. When no such bean class is in the index, the method's own scopes are
     * checked alone. A bean class whose class-level annotation is refused on its own is already reported at
     * that class, so for it only the method's own scopes are checked: a bad class-level scope is never reported
     * as the method's.
     */
    private static void checkMethodLevel(IndexView index, MethodInfo method, Set<String> methodScopes,
            Set<String> violations) {
        List<ClassInfo> beanClasses = beanClassesRunning(index, method);
        boolean checkedAlone = beanClasses.isEmpty();
        for (ClassInfo beanClass : beanClasses) {
            AnnotationInstance classLevel = beanClass.declaredAnnotation(BEARER_AUTH);
            Set<String> classScopes = classLevel == null ? Set.of() : requiredScopes(classLevel);
            if (classScopes.isEmpty() || refusal(classScopes).isPresent()) {
                checkedAlone = true;
                continue;
            }
            Set<String> union = new TreeSet<>(methodScopes);
            union.addAll(classScopes);
            refusal(union).ifPresent(reason -> violations.add("@BearerAuth on " + describe(method)
                    + " together with the class-level @BearerAuth of bean class " + beanClass.name()
                    + " requires scopes that an insufficient_scope challenge cannot carry: " + reason));
        }
        if (checkedAlone) {
            refusal(methodScopes).ifPresent(reason -> violations.add(declaresMessage("@BearerAuth", method,
                    reason)));
        }
    }

    /**
     * Lists the bean classes in the index that run the method: the declaring class and every subclass that
     * inherits the method unchanged, each only when it is a bean class candidate. An interface, an abstract
     * class, a class declaring {@code @Vetoed} and a class in a package whose {@code package-info} declares
     * {@code @Vetoed} are no bean classes, whether it declares the method or inherits it.
     *
     * @return the bean classes running the method, empty when there is none in the index
     */
    private static List<ClassInfo> beanClassesRunning(IndexView index, MethodInfo method) {
        ClassInfo declaringClass = method.declaringClass();
        List<ClassInfo> beanClasses = new ArrayList<>();
        if (isBeanClassCandidate(index, declaringClass)) {
            beanClasses.add(declaringClass);
        }
        if (declaringClass.isInterface()) {
            return beanClasses;
        }
        for (ClassInfo subclass : index.getAllKnownSubclasses(declaringClass.name())) {
            if (isBeanClassCandidate(index, subclass) && inheritsUnchanged(index, subclass, method)) {
                beanClasses.add(subclass);
            }
        }
        return beanClasses;
    }

    /**
     * A bean class candidate is a concrete class that is not vetoed: interfaces, abstract classes, classes
     * declaring {@code @Vetoed} and classes in a {@code @Vetoed} package are no bean classes.
     * <p>
     * The package veto is read from the {@code @Vetoed} annotation of the class {@code {package}.package-info} in
     * the scanned index, which in the build step is the composite {@link #scannedIndex} builds from the
     * bean-archive and the combined index, so a {@code package-info} from either of the two vetoes the class. A
     * package without a {@code package-info} in the index is not vetoed, and neither is a subpackage of a vetoed
     * package: CDI applies a package-level {@code @Vetoed} to that package alone.
     * <p>
     * ArC was shown to agree in three cases. For a {@code package-info} in the application root it registers no
     * bean for the class of that package ({@code BearerScopeValidationVetoedPackageBootTest}). For a
     * {@code package-info} in a dependency archive that also holds the class it registers no bean for the class
     * ({@code BearerScopeValidationDependencyVetoedPackageBootTest}). For a class in a subpackage of a vetoed
     * package it registers the class ({@code BearerScopeValidationDependencyVetoedPackageBootTest}). A
     * {@code package-info} indexed only in the combined index while its classes sit in a bean archive was not
     * shown.
     */
    private static boolean isBeanClassCandidate(IndexView index, ClassInfo classInfo) {
        return !classInfo.isInterface() && !Modifier.isAbstract(classInfo.flags())
                && classInfo.declaredAnnotation(VETOED) == null && !inVetoedPackage(index, classInfo);
    }

    private static boolean inVetoedPackage(IndexView index, ClassInfo classInfo) {
        String packageName = classInfo.name().packagePrefix();
        if (packageName == null) {
            return false;
        }
        ClassInfo packageInfo = index.getClassByName(DotName.createSimple(packageName + PACKAGE_INFO));
        return packageInfo != null && packageInfo.declaredAnnotation(VETOED) != null;
    }

    private static boolean inheritsUnchanged(IndexView index, ClassInfo subclass, MethodInfo method) {
        DotName declaringClass = method.declaringClass().name();
        ClassInfo current = subclass;
        while (current != null && !current.name().equals(declaringClass)) {
            if (current.method(method.name(), method.parameterTypes()) != null) {
                return false;
            }
            DotName superName = current.superName();
            current = superName == null ? null : index.getClassByName(superName);
        }
        return current != null;
    }

    private static Set<String> requiredScopes(AnnotationInstance annotation) {
        AnnotationValue value = annotation.value(REQUIRED_SCOPES);
        return value == null ? Set.of() : new TreeSet<>(Arrays.asList(value.asStringArray()));
    }

    private static String declaresMessage(String annotation, AnnotationTarget target, String reason) {
        return annotation + " on " + describe(target)
                + " declares requiredScopes that an insufficient_scope challenge cannot carry: " + reason;
    }

    /**
     * Builds the challenge the producer sends for the given scopes, through
     * {@link BearerTokenResponseFactory#insufficientScopeChallenge}, so the length bound is checked on the
     * value that carries the realm and the {@code error_description} as well.
     *
     * @return the reason the challenge is refused, or empty when it builds
     */
    private static Optional<String> refusal(Set<String> scopes) {
        try {
            BearerTokenResponseFactory.insufficientScopeChallenge(scopes);
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.of(reason(scopes, e));
        }
    }

    private static String reason(Set<String> scopes, IllegalArgumentException refusal) {
        for (String scope : scopes) {
            try {
                BearerChallenge.insufficientScope().scope(scope);
            } catch (IllegalArgumentException e) {
                return "the scope name '" + ErrorTextSanitizer.sanitize(scope)
                        + "' is not an RFC 6749 section 3.3 scope-token";
            }
        }
        return refusal.getMessage();
    }

    private static String describe(AnnotationTarget target) {
        return switch (target.kind()) {
            case CLASS -> "class " + target.asClass().name();
            case METHOD -> "method " + methodName(target.asMethod());
            case FIELD -> "field " + target.asField().declaringClass().name() + "#" + target.asField().name();
            case METHOD_PARAMETER -> {
                MethodParameterInfo parameter = target.asMethodParameter();
                yield "parameter " + parameter.position() + " of method " + methodName(parameter.method());
            }
            default -> target.toString();
        };
    }

    private static String methodName(MethodInfo method) {
        return method.declaringClass().name() + "#" + method.name();
    }

    /**
     * Create DevUI card page for JWT validation monitoring and debugging.
     *
     * @return A {@link CardPageBuildItem} for the JWT DevUI card
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    public CardPageBuildItem createJwtDevUICard() {
        CardPageBuildItem cardPageBuildItem = new CardPageBuildItem();

        // Status & Config page (merged view of validation status, JWKS endpoints, and configuration)
        cardPageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:shield-halved")
                .title("Status & Config")
                .componentLink("qwc-jwt-status-config.js"));

        // Token Debugging Tools page
        cardPageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:bug")
                .title("Token Debugger")
                .componentLink("qwc-jwt-debugger.js"));

        return cardPageBuildItem;
    }

    /**
     * Register JSON-RPC providers for DevUI runtime data access.
     *
     * @return A {@link JsonRPCProvidersBuildItem} for JWT DevUI JSON-RPC methods
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    public JsonRPCProvidersBuildItem createJwtDevUIJsonRPCService() {
        return new JsonRPCProvidersBuildItem("TokenSheriffDevUI", TokenSheriffDevUIRuntimeService.class);
    }

}
