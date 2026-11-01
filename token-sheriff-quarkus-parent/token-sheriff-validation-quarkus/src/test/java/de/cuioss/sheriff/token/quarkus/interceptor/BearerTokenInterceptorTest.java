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
package de.cuioss.sheriff.token.quarkus.interceptor;

import de.cuioss.sheriff.token.quarkus.annotation.BearerAuth;
import de.cuioss.sheriff.token.quarkus.producer.BearerTokenProducer;
import de.cuioss.sheriff.token.quarkus.producer.BearerTokenResult;
import de.cuioss.sheriff.token.quarkus.producer.BearerTokenStatus;
import de.cuioss.sheriff.token.validation.TokenType;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.generator.ClaimControlParameter;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.quarkus.arc.Subclass;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BearerTokenInterceptor}.
 */
@EnableTestLogger
class BearerTokenInterceptorTest {

    private BearerTokenInterceptor interceptor;
    private BearerTokenProducer bearerTokenProducer;
    private InvocationContext invocationContext;

    private final TestResource testResource = new TestResource();

    @BeforeEach
    void setUp() {
        ClassLevelBearerAuthRegistry.clear();
        bearerTokenProducer = createMock(BearerTokenProducer.class);
        invocationContext = createMock(InvocationContext.class);
        interceptor = new BearerTokenInterceptor(bearerTokenProducer);
    }

    @AfterEach
    void tearDown() {
        ClassLevelBearerAuthRegistry.clear();
    }

    @Test
    @DisplayName("Should proceed when validation succeeds")
    void shouldProceedWhenValidationSucceeds() throws Exception {
        expectInvocation(TestResource.class.getMethod("methodWithScopes"), testResource);
        expectAuthorized(Set.of("read"), Set.of(), Set.of());
        expect(invocationContext.proceed()).andReturn("success");
        replay(invocationContext, bearerTokenProducer);

        Object result = interceptor.validateBearerToken(invocationContext);

        assertEquals("success", result);
        verify(invocationContext, bearerTokenProducer);
    }

    @Test
    @DisplayName("Should throw WebApplicationException when validation fails")
    void shouldThrowWebApplicationExceptionWhenValidationFails() throws Exception {
        expectInvocation(TestResource.class.getMethod("methodWithScopes"), testResource);
        expect(bearerTokenProducer.getBearerTokenResult(Set.of("read"), Set.of(), Set.of()))
                .andReturn(BearerTokenResult.noTokenGiven(Set.of("read"), Set.of(), Set.of()));
        replay(invocationContext, bearerTokenProducer);

        WebApplicationException exception = assertThrows(WebApplicationException.class,
                () -> interceptor.validateBearerToken(invocationContext));

        assertEquals(401, exception.getResponse().getStatus());
        verify(invocationContext, bearerTokenProducer);
    }

    @Test
    @DisplayName("Should extract multiple scopes correctly")
    void shouldExtractMultipleScopesCorrectly() throws Exception {
        expectInvocation(TestResource.class.getMethod("methodWithMultipleScopes"), testResource);
        expectAuthorized(Set.of("read", "write", "admin"), Set.of(), Set.of());
        expect(invocationContext.proceed()).andReturn("success");
        replay(invocationContext, bearerTokenProducer);

        interceptor.validateBearerToken(invocationContext);

        verify(bearerTokenProducer);
    }

    @Test
    @DisplayName("Should extract multiple roles correctly")
    void shouldExtractMultipleRolesCorrectly() throws Exception {
        expectInvocation(TestResource.class.getMethod("methodWithRoles"), testResource);
        expectAuthorized(Set.of(), Set.of("user", "admin"), Set.of());
        expect(invocationContext.proceed()).andReturn("success");
        replay(invocationContext, bearerTokenProducer);

        interceptor.validateBearerToken(invocationContext);

        verify(bearerTokenProducer);
    }

    @Test
    @DisplayName("Should extract multiple groups correctly")
    void shouldExtractMultipleGroupsCorrectly() throws Exception {
        expectInvocation(TestResource.class.getMethod("methodWithGroups"), testResource);
        expectAuthorized(Set.of(), Set.of(), Set.of("developers", "managers"));
        expect(invocationContext.proceed()).andReturn("success");
        replay(invocationContext, bearerTokenProducer);

        interceptor.validateBearerToken(invocationContext);

        verify(bearerTokenProducer);
    }

    @Test
    @DisplayName("Should handle all requirements combined")
    void shouldHandleAllRequirementsCombined() throws Exception {
        expectInvocation(TestResource.class.getMethod("methodWithAllRequirements"), testResource);
        expectAuthorized(Set.of("read", "write"), Set.of("user"), Set.of("team-a"));
        expect(invocationContext.proceed()).andReturn("success");
        replay(invocationContext, bearerTokenProducer);

        interceptor.validateBearerToken(invocationContext);

        verify(bearerTokenProducer);
    }

    @Test
    @DisplayName("Should handle empty annotation (no requirements)")
    void shouldHandleEmptyAnnotation() throws Exception {
        expectInvocation(TestResource.class.getMethod("methodWithEmptyAnnotation"), testResource);
        expectAuthorized(Set.of(), Set.of(), Set.of());
        expect(invocationContext.proceed()).andReturn("success");
        replay(invocationContext, bearerTokenProducer);

        interceptor.validateBearerToken(invocationContext);

        verify(bearerTokenProducer);
    }

    @Test
    @DisplayName("Should throw IllegalStateException when annotation not found (fail closed)")
    void shouldThrowWhenAnnotationNotFound() throws Exception {
        expectInvocation(TestResource.class.getMethod("methodWithoutAnnotation"), testResource);
        replay(invocationContext, bearerTokenProducer);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> interceptor.validateBearerToken(invocationContext));

        assertTrue(exception.getMessage().contains("@BearerAuth annotation not found"),
                "Exception message should mention missing annotation");
        verify(invocationContext, bearerTokenProducer);
    }

    @Test
    @DisplayName("Should extract annotation from class level")
    void shouldExtractAnnotationFromClassLevel() throws Exception {
        expectInvocation(TestResourceClassLevel.class.getMethod("methodWithoutAnnotation"), new TestResourceClassLevel());
        expectAuthorized(Set.of("class-scope"), Set.of(), Set.of());
        expect(invocationContext.proceed()).andReturn("success");
        replay(invocationContext, bearerTokenProducer);

        interceptor.validateBearerToken(invocationContext);

        verify(bearerTokenProducer);
    }

    @Test
    @DisplayName("Should return status 401 for missing scopes")
    void shouldReturn401ForMissingScopes() throws Exception {
        expectInvocation(TestResource.class.getMethod("methodWithScopes"), testResource);
        expect(bearerTokenProducer.getBearerTokenResult(Set.of("read"), Set.of(), Set.of()))
                .andReturn(BearerTokenResult.builder()
                        .status(BearerTokenStatus.CONSTRAINT_VIOLATION)
                        .requiredScopes(Set.of("read"))
                        .missingScopes(Set.of("read"))
                        .requiredRoles(Set.of())
                        .requiredGroups(Set.of())
                        .build());
        replay(invocationContext, bearerTokenProducer);

        WebApplicationException exception = assertThrows(WebApplicationException.class,
                () -> interceptor.validateBearerToken(invocationContext));

        assertEquals(401, exception.getResponse().getStatus());
        verify(invocationContext, bearerTokenProducer);
    }

    @Test
    @DisplayName("Should return status 403 for missing roles")
    void shouldReturn403ForMissingRoles() throws Exception {
        expectInvocation(TestResource.class.getMethod("methodWithRoles"), testResource);
        expect(bearerTokenProducer.getBearerTokenResult(Set.of(), Set.of("user", "admin"), Set.of()))
                .andReturn(missingRole("user"));
        replay(invocationContext, bearerTokenProducer);

        WebApplicationException exception = assertThrows(WebApplicationException.class,
                () -> interceptor.validateBearerToken(invocationContext));

        assertEquals(403, exception.getResponse().getStatus());
        verify(invocationContext, bearerTokenProducer);
    }

    /**
     * Cases where the class-level annotation is reachable reflectively on the bean class behind a
     * simulated ArC subclass, as in JVM mode.
     */
    @Nested
    @DisplayName("Reflective class-level lookup")
    class ReflectiveClassLevel {

        @Test
        @DisplayName("Should read class-level annotation from bean class behind an ArC subclass")
        void shouldReadClassLevelAnnotationThroughArcSubclass() throws Exception {
            expectInvocation(TestResourceClassLevel.class.getMethod("methodWithoutAnnotation"), new ArcSubclassOfClassLevel());
            expectAuthorized(Set.of("class-scope"), Set.of(), Set.of());
            expect(invocationContext.proceed()).andReturn("success");
            replay(invocationContext, bearerTokenProducer);

            Object result = interceptor.validateBearerToken(invocationContext);

            assertEquals("success", result);
            verify(invocationContext, bearerTokenProducer);
        }

        @Test
        @DisplayName("Should merge class and method annotations when target is an ArC subclass")
        void shouldMergeClassAndMethodAnnotationsThroughArcSubclass() throws Exception {
            expectInvocation(TestResourceClassAndMethodLevel.class.getMethod("methodWithScopes"),
                    new ArcSubclassOfClassAndMethodLevel());
            expectAuthorized(Set.of("class-scope", "method-scope"), Set.of("class-role"), Set.of());
            expect(invocationContext.proceed()).andReturn("success");
            replay(invocationContext, bearerTokenProducer);

            interceptor.validateBearerToken(invocationContext);

            verify(invocationContext, bearerTokenProducer);
        }

        @Test
        @DisplayName("Should apply bean-class requirements to a method declared on a superclass")
        void shouldApplyBeanClassRequirementsToSuperclassMethod() throws Exception {
            expectInvocation(BaseResource.class.getMethod("inheritedMethod"), new ArcSubclassOfInheritingResource());
            expectAuthorized(Set.of("bean-scope"), Set.of(), Set.of());
            expect(invocationContext.proceed()).andReturn("success");
            replay(invocationContext, bearerTokenProducer);

            interceptor.validateBearerToken(invocationContext);

            verify(invocationContext, bearerTokenProducer);
        }

        @Test
        @DisplayName("Should fail closed when neither level is annotated behind an ArC subclass")
        void shouldThrowWhenAnnotationNotFoundThroughArcSubclass() throws Exception {
            expectInvocation(TestResource.class.getMethod("methodWithoutAnnotation"), new ArcSubclassOfUnannotated());
            replay(invocationContext, bearerTokenProducer);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> interceptor.validateBearerToken(invocationContext));

            assertTrue(exception.getMessage().contains("@BearerAuth annotation not found"),
                    "Exception message should mention missing annotation");
            verify(invocationContext, bearerTokenProducer);
        }

        @Test
        @DisplayName("Should not walk the hierarchy for a target that is not an ArC subclass")
        void shouldNotWalkHierarchyForPlainSubclass() throws Exception {
            expectInvocation(TestResourceClassLevel.class.getMethod("methodWithoutAnnotation"), new PlainSubclassOfClassLevel());
            replay(invocationContext, bearerTokenProducer);

            assertThrows(IllegalStateException.class,
                    () -> interceptor.validateBearerToken(invocationContext));

            verify(invocationContext, bearerTokenProducer);
        }
    }

    /**
     * Cases modelling the native container: the bean class exposes no {@code @BearerAuth} through
     * reflection, so the class-level requirement reaches the interceptor only through the build-time
     * {@link ClassLevelBearerAuthRegistry} or the interceptor bindings of the invocation.
     */
    @Nested
    @DisplayName("Class-level requirement without reflective access")
    class WithoutReflectiveClassAnnotation {

        @Test
        @DisplayName("Should apply the class requirement from the interceptor bindings to a method without annotation")
        void shouldApplyClassRequirementFromBindings() throws Exception {
            expectInvocation(NativeBean.class.getMethod("classOnly"), new ArcSubclassOfNativeBean(),
                    classLevelBinding());
            expectAuthorized(Set.of(), Set.of("user"), Set.of());
            expect(invocationContext.proceed()).andReturn("success");
            replay(invocationContext, bearerTokenProducer);

            Object result = interceptor.validateBearerToken(invocationContext);

            assertEquals("success", result);
            verify(invocationContext, bearerTokenProducer);
        }

        @Test
        @DisplayName("Should apply the recorded class requirement to a method without annotation")
        void shouldApplyRecordedClassRequirementToUnannotatedMethod() throws Exception {
            registerClassLevel(NativeBean.class, "user");
            expectInvocation(NativeBean.class.getMethod("classOnly"), new ArcSubclassOfNativeBean());
            expectAuthorized(Set.of(), Set.of("user"), Set.of());
            expect(invocationContext.proceed()).andReturn("success");
            replay(invocationContext, bearerTokenProducer);

            interceptor.validateBearerToken(invocationContext);

            verify(invocationContext, bearerTokenProducer);
        }

        @Test
        @DisplayName("Should pass the union of the recorded class and the method requirement")
        void shouldMergeRecordedClassAndMethodRequirement() throws Exception {
            registerClassLevel(NativeBean.class, "user");
            // The container hands over only the method-level binding: CDI lets it replace the class-level one
            expectInvocation(NativeBean.class.getMethod("union"), new ArcSubclassOfNativeBean(),
                    NativeBean.class.getMethod("union").getAnnotation(BearerAuth.class));
            expectAuthorized(Set.of("read"), Set.of("user"), Set.of());
            expect(invocationContext.proceed()).andReturn("success");
            replay(invocationContext, bearerTokenProducer);

            interceptor.validateBearerToken(invocationContext);

            verify(invocationContext, bearerTokenProducer);
        }

        @Test
        @DisplayName("Should apply the recorded bean-class requirement to a method declared on a superclass")
        void shouldApplyRecordedRequirementToSuperclassMethod() throws Exception {
            registerClassLevel(NativeBean.class, "user");
            expectInvocation(BaseResource.class.getMethod("inheritedMethod"), new ArcSubclassOfNativeBean(),
                    classLevelBinding());
            expectAuthorized(Set.of(), Set.of("user"), Set.of());
            expect(invocationContext.proceed()).andReturn("success");
            replay(invocationContext, bearerTokenProducer);

            interceptor.validateBearerToken(invocationContext);

            verify(invocationContext, bearerTokenProducer);
        }

        @Test
        @DisplayName("Should not drop an unsatisfied class requirement when the method carries its own annotation")
        void shouldRefuseWhenRecordedClassRequirementIsUnsatisfied() throws Exception {
            registerClassLevel(NativeBean.class, "nonexistent-role");
            expectInvocation(NativeBean.class.getMethod("union"), new ArcSubclassOfNativeBean(),
                    NativeBean.class.getMethod("union").getAnnotation(BearerAuth.class));
            expect(bearerTokenProducer.getBearerTokenResult(Set.of("read"), Set.of("nonexistent-role"), Set.of()))
                    .andReturn(missingRole("nonexistent-role"));
            replay(invocationContext, bearerTokenProducer);

            WebApplicationException exception = assertThrows(WebApplicationException.class,
                    () -> interceptor.validateBearerToken(invocationContext));

            assertEquals(403, exception.getResponse().getStatus());
            verify(invocationContext, bearerTokenProducer);
        }

        @Test
        @DisplayName("Should fail closed when no source yields a requirement")
        void shouldThrowWhenNoSourceYieldsRequirement() throws Exception {
            expectInvocation(NativeBean.class.getMethod("classOnly"), new ArcSubclassOfNativeBean());
            replay(invocationContext, bearerTokenProducer);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> interceptor.validateBearerToken(invocationContext));

            assertTrue(exception.getMessage().contains("@BearerAuth annotation not found on method 'classOnly'"),
                    "Exception message should name the method and the missing annotation");
            verify(invocationContext, bearerTokenProducer);
        }

        @Test
        @DisplayName("Should not apply a requirement recorded for a different class")
        void shouldIgnoreRequirementRecordedForOtherClass() throws Exception {
            registerClassLevel(TestResource.class, "user");
            expectInvocation(NativeBean.class.getMethod("classOnly"), new ArcSubclassOfNativeBean());
            replay(invocationContext, bearerTokenProducer);

            assertThrows(IllegalStateException.class,
                    () -> interceptor.validateBearerToken(invocationContext));

            verify(invocationContext, bearerTokenProducer);
        }
    }

    private void expectInvocation(Method method, Object target, Annotation... bindings) {
        expect(invocationContext.getMethod()).andReturn(method);
        expect(invocationContext.getTarget()).andReturn(target);
        expect(invocationContext.getInterceptorBindings()).andReturn(Set.of(bindings));
    }

    private void expectAuthorized(Set<String> scopes, Set<String> roles, Set<String> groups) {
        expect(bearerTokenProducer.getBearerTokenResult(scopes, roles, groups))
                .andReturn(BearerTokenResult.builder()
                        .status(BearerTokenStatus.FULLY_VERIFIED)
                        .accessTokenContent(createValidToken().asAccessTokenContent())
                        .build());
    }

    private static BearerTokenResult missingRole(String role) {
        return BearerTokenResult.builder()
                .status(BearerTokenStatus.CONSTRAINT_VIOLATION)
                .requiredScopes(Set.of())
                .requiredRoles(Set.of(role))
                .missingRoles(Set.of(role))
                .requiredGroups(Set.of())
                .build();
    }

    private static void registerClassLevel(Class<?> beanClass, String role) {
        new BearerAuthRecorder().registerClassLevelRequirement(beanClass.getName(),
                new String[0], new String[]{role}, new String[0]);
    }

    private static BearerAuth classLevelBinding() {
        return ClassLevelBindingCarrier.class.getAnnotation(BearerAuth.class);
    }

    private TestTokenHolder createValidToken() {
        return new TestTokenHolder(TokenType.ACCESS_TOKEN,
                ClaimControlParameter.defaultForTokenType(TokenType.ACCESS_TOKEN));
    }

    public static class TestResource {
        @BearerAuth(requiredScopes = {"read"})
        public String methodWithScopes() {
            return "result";
        }

        @BearerAuth(requiredScopes = {"read", "write", "admin"})
        public String methodWithMultipleScopes() {
            return "result";
        }

        @BearerAuth(requiredRoles = {"user", "admin"})
        public String methodWithRoles() {
            return "result";
        }

        @BearerAuth(requiredGroups = {"developers", "managers"})
        public String methodWithGroups() {
            return "result";
        }

        @BearerAuth(requiredScopes = {"read", "write"}, requiredRoles = {"user"}, requiredGroups = {"team-a"})
        public String methodWithAllRequirements() {
            return "result";
        }

        @BearerAuth
        public String methodWithEmptyAnnotation() {
            return "result";
        }

        public String methodWithoutAnnotation() {
            return "result";
        }
    }

    @BearerAuth(requiredScopes = {"class-scope"})
    public static class TestResourceClassLevel {
        public String methodWithoutAnnotation() {
            return "result";
        }
    }

    @BearerAuth(requiredScopes = {"class-scope"}, requiredRoles = {"class-role"})
    public static class TestResourceClassAndMethodLevel {
        @BearerAuth(requiredScopes = {"method-scope"})
        public String methodWithScopes() {
            return "result";
        }
    }

    public static class BaseResource {
        public String inheritedMethod() {
            return "result";
        }
    }

    @BearerAuth(requiredScopes = {"bean-scope"})
    public static class TestResourceInheritingMethod extends BaseResource {
    }

    /**
     * Stands in for a bean whose class-level {@code @BearerAuth(requiredRoles = "user")} is not visible
     * to runtime reflection, as observed in the native container: the class itself carries no annotation,
     * and the class-level requirement is supplied through the registry or the bindings.
     */
    public static class NativeBean extends BaseResource {
        public String classOnly() {
            return "result";
        }

        @BearerAuth(requiredScopes = {"read"})
        public String union() {
            return "result";
        }
    }

    /**
     * Supplies the {@code @BearerAuth} instance the container reports as class-level interceptor binding
     * of {@link NativeBean}.
     */
    @BearerAuth(requiredRoles = {"user"})
    static class ClassLevelBindingCarrier {
    }

    /**
     * Simulates the subclass ArC generates for an intercepted bean: it implements
     * {@link Subclass} and does not carry the bean's class-level annotations.
     */
    static class ArcSubclassOfClassLevel extends TestResourceClassLevel implements Subclass {
    }

    static class ArcSubclassOfClassAndMethodLevel extends TestResourceClassAndMethodLevel implements Subclass {
    }

    static class ArcSubclassOfInheritingResource extends TestResourceInheritingMethod implements Subclass {
    }

    static class ArcSubclassOfUnannotated extends TestResource implements Subclass {
    }

    static class ArcSubclassOfNativeBean extends NativeBean implements Subclass {
    }

    /**
     * A subclass that is not ArC-generated: its superclass annotations must not be consulted.
     */
    static class PlainSubclassOfClassLevel extends TestResourceClassLevel {
    }
}
