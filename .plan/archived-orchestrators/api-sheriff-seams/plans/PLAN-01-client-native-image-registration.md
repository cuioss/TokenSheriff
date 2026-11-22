# PLAN-01: Client native-image registration

epic: api-sheriff-seams
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-01-client-native-image-registration.md` and is queued as one row file, `queue/PLAN-01.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Make the `token-sheriff-client` engine work in a GraalVM native image without the consuming application
carrying native-image workarounds. Today the client engine's DSL-JSON response converters are not
registered for reflection and two engine classes that hold a static `SecureRandom` are not marked
runtime-initialized, so every native consumer has to add both itself; API Sheriff does exactly that. This
plan moves the registration into TokenSheriff and proves, in a native integration test, that the two
fail-open symptoms (a missed `invalid_grant` and a skipped DPoP `cnf.jkt` comparison) are closed. All new
behaviour is additive: it changes build-time native-image metadata only and no JVM-mode runtime path.

## Deliverables

1. **DSL-JSON converter registration.** Every generated `_*_DslJsonConverter` of `token-sheriff-client`
   (the consumer lists eight: `_ProviderMetadata_`, `_ParResponse_`, `_TokenResponse_`,
   `_TokenErrorResponse_`, `_UserInfoResponse_`, `_AccessTokenConfirmation_`,
   `_AccessTokenConfirmation$Confirmation_`, `_AccessTokenConfirmation$JsonObjectProbe_`) and the
   `@CompiledJson` DTO classes they serve are registered for reflection by TokenSheriff itself. Verification:
   a unit test enumerates every `_*_DslJsonConverter` class of the client engine jar (nested-type converters
   included, the converters' own `$ObjectFormatConverter` classes excluded) and fails when one is absent
   from the registration set; a second assertion proves the enumeration is non-empty and contains the
   `TokenErrorResponse` and `JsonObjectProbe` converters, so the guard cannot pass vacuously.
2. **Runtime initialization of the static-`SecureRandom` classes.** `FlowContext` and `PkceChallenge` are
   registered as initialized at run time, not at image build time. Verification: a unit test enumerates the
   client engine's classes that declare a `static` `SecureRandom` field and fails when one is not in the
   runtime-initialization set; the same non-vacuity assertion applies (the enumeration must find both known
   classes).
3. **Delivery mechanism, chosen once and documented.** The registration reaches a Quarkus consumer that depends
   on `token-sheriff-client-quarkus` (API Sheriff's dependency shape) with no application-side entry. The
   mechanism is one of: `@BuildStep` methods in `TokenSheriffClientProcessor` producing
   `ReflectiveClassBuildItem` and `RuntimeInitializedClassBuildItem`, or a `META-INF/native-image` resource
   set in the `token-sheriff-client` jar (the form `token-sheriff-validation` already ships). Verification:
   the choice and its reason are recorded in the doc update (deliverable 5), and the processor test (or a
   resource-presence test) asserts the chosen delivery exists.
4. **Native integration test that closes both fail-open cases.** The native executable built by the
   `integration-tests` profile exercises the client engine and proves: (a) a token-endpoint `4xx` body with
   `error=invalid_grant` is classified as a credential rejection, not as an unparseable body / transport
   failure; (b) a DPoP-bound token whose `cnf.jkt` names another key is refused, not accepted. Each
   assertion has a negative control showing it fails when the registration is removed (documented in the
   test, not shipped as a second build). Verification: the integration test passes in the native lane and the
   removal control is demonstrated once during implementation and recorded in the PR body.
5. **Documentation.** One client requirement or specification paragraph states the native-image contract
   (what is registered, why a missing entry fails open, how the guard tests keep it current). Verification:
   the requirement id is the next free `CLIENT-N` at write time, listed in `doc/client/requirements.adoc`,
   and `doc/client/specification/test-strategy.adoc` names the guard tests.

Done when: unit guards, the native integration test and the full `-Ppre-commit` plus `clean install`
sequence of `AGENTS.md` are green, and the Sonar open-issue read at the PR head is `0` or every remaining
issue is dismissed with a recorded reason (`unknown` for a failed read, never `0`).

## Standing Constraints

- Pre-1.0 rules of `AGENTS.md` apply: no `@Deprecated`, no transitional comments, no compatibility shims.
- Default behaviour is unchanged. The plan adds native-image build metadata only. JVM-mode behaviour is
  proven unchanged by the existing `token-sheriff-client` and `token-sheriff-client-quarkus-deployment`
  suites passing without edits to existing assertions.
- Registration must name classes by symbol (class literal) wherever the delivery mechanism allows it, so a
  rename in the engine fails the build rather than silently dropping the registration. A string-name form
  (for example a JSON resource) needs the guard tests of deliverables 1 and 2 as the compensating control.
- Logging follows `AGENTS.md`: `CuiLogger`, `%s` only, `LogRecord` for INFO/WARN/ERROR, new messages
  documented in `doc/LogMessages.adoc`. Any new LogRecord identifier is the next free one in its range at
  write time; none is assumed here.
- Tests: JUnit 5, AAA, no Mockito/PowerMock/Hamcrest, parameterized tests for three or more similar variants,
  new or grown test classes stay at or under 500 lines. `-Ppre-commit` auto-fixes files: check `git status`
  after it. No OpenRewrite marker may be committed.
- Never gate on `test-compile`/`test`; use `verify` (`token-sheriff-validation` publishes its test fixtures
  only in `package`).
- No new dependency without operator approval.
- Sonar applies (`.github/project.yml` declares `sonar.project-key`, `AGENTS.md` names the SonarCloud
  new-code gate the coverage authority): an open Sonar issue is fixed in a pushed commit, or dismissed with a
  stated reason recorded as `{issue-key}: {reason}` in the run's progress record and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition
  {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker, never a silent pass.
- Security: the plan must not weaken a fail-closed path. The fail-open symptoms are the reason the plan
  exists, so each is asserted from the closing side (the refusal happens), never only from the absence of an
  error.

## Claim Labels

- OBSERVED: `TokenSheriffClientProcessor` has exactly three `@BuildStep` methods (`feature`,
  `additionalBeans`, `registerClientExceptionMapper`) and its imports contain no `ReflectiveClassBuildItem`
  and no `RuntimeInitializedClassBuildItem` — read at
  `token-sheriff-quarkus-parent/token-sheriff-client-quarkus-deployment/src/main/java/de/cuioss/sheriff/token/client/quarkus/deployment/TokenSheriffClientProcessor.java`
  § `feature`, `additionalBeans`, `registerClientExceptionMapper`
- OBSERVED: asserted absence — `ReflectiveClassBuildItem` and `RuntimeInitializedClass` occur in exactly two
  Java files in the repository (the validation processor and its test), neither in the client deployment
  module — read by repository-wide search, confirmed at
  `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/src/main/java/de/cuioss/sheriff/token/quarkus/deployment/TokenSheriffProcessor.java`
  § `registerQuarkusSpecificClassesForReflection` (the precedent; it registers Quarkus-specific classes only,
  no `RuntimeInitializedClassBuildItem` anywhere)
- OBSERVED: asserted absence — `token-sheriff-client/src/main/resources` does not exist, so the client jar
  ships no `META-INF/native-image` metadata; only `token-sheriff-validation` does — read at
  `token-sheriff-validation/src/main/resources/META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-validation/`
  § `native-image.properties` (`Args=--initialize-at-run-time=...HttpJwksLoader`) and `reflect-config.json`
  (which lists the validation converters `_WellKnownResult_`, `_Jwks_`, `_JwkKey_`, `_JwtHeader_`)
- OBSERVED: the client engine has six `@CompiledJson` DTOs: `ParResponse`, `UserInfoResponse`,
  `TokenErrorResponse`, `ProviderMetadata`, `TokenResponse`, `AccessTokenConfirmation`, the last with two
  nested `@CompiledJson` classes (`Confirmation`, `JsonObjectProbe`), which yields the eight converters the
  consumer lists — read by search of `@CompiledJson` under `token-sheriff-client/src/main`, with
  `AccessTokenConfirmation` at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/AccessTokenConfirmation.java`
  § `Confirmation`, `JsonObjectProbe`
- OBSERVED: the two fail-open symptoms exist in the engine's code: an unreadable `4xx` error body yields a
  `null` error code (`TokenEndpointClient.errorCode` returns `null` on the `IOException` of the parse), and an unreadable
  access-token payload returns `Optional.empty()` from `jwtPayload`, which skips the `cnf.jkt` comparison —
  read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/TokenEndpointClient.java`
  § `jwtPayload` (:350-374) and § `errorCode` (:513-545)
- OBSERVED: exactly two static `SecureRandom` fields exist in `token-sheriff-client` main sources:
  `FlowContext.SECURE_RANDOM` (:55) and `PkceChallenge.SECURE_RANDOM` (:43) — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/FlowContext.java` § `SECURE_RANDOM`
  and `.../flow/PkceChallenge.java` § `SECURE_RANDOM`
- OBSERVED: the consumer's workaround is as the findings describe, with one line correction —
  `TokenClientDslJsonReflection` registers the eight converters plus eight DTO classes (it is 109 lines, with
  a guard test `TokenClientDslJsonReflectionTest`), and the `--initialize-at-run-time=de.cuioss.sheriff.token.client.flow`
  build argument sits in the `quarkus.native.additional-build-args` property at line 245 of
  `api-sheriff/src/main/resources/application.properties` (the explaining comment starts at line 239) — read
  at `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/quarkus/TokenClientDslJsonReflection.java` §
  `@RegisterForReflection` and `api-sheriff/src/main/resources/application.properties` §
  `quarkus.native.additional-build-args`
- OBSERVED: the consumer's `--initialize-at-run-time` entry is a package prefix over the whole `flow`
  package, whereas the TokenSheriff need found is two classes; a package-level entry also covers future
  flow classes, so this plan's deliverable 2 guard test is what replaces that safety margin — read at
  `api-sheriff/src/main/resources/application.properties` § `quarkus.native.additional-build-args`
- OBSERVED: the `token-sheriff-quarkus-integration-tests` module already depends on
  `token-sheriff-client-quarkus` and `token-sheriff-client-quarkus-deployment`, but its `src/main` tree has
  no reference to `de.cuioss.sheriff.token.client` (no endpoint drives the engine) and its
  `application.properties` carries no `sheriff.client.*` property; the existing client `*IT` classes run the
  engine in the test JVM against Keycloak — read at
  `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/pom.xml` § `dependencies` and by
  search of `src/main` for `token.client`
- HYPOTHESIS: a native executable of that module therefore does not exercise the client engine at all, so
  the native integration test of deliverable 4 needs a new test endpoint in `src/main` that calls
  `TokenEndpointClient` against a controlled IdP response — confirm/refute at
  `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/main/java/de/cuioss/sheriff/token/integration/endpoint/`
  and `.../src/test/java/de/cuioss/sheriff/token/integration/client/RefreshEngineSupport.java` § engine
  construction (verify-at-outline)
- HYPOTHESIS: the `@CompiledJson` annotation processor emits a `META-INF/services/com.dslplatform.json.Configuration`
  entry for the client jar, and `TokenSheriffProcessor.registerDslJsonServiceProviders`
  (`ServiceProviderBuildItem.allProvidersFromClassPath`) may already cover the service-loader half of
  converter discovery for the client converters when the validation extension is present — confirm/refute by
  listing `token-sheriff-client/target/classes/META-INF/services` after a build, and at
  `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/src/main/java/de/cuioss/sheriff/token/quarkus/deployment/TokenSheriffProcessor.java`
  § `registerDslJsonServiceProviders` (verify-at-outline). The consumer's Javadoc says the reflective
  `loadClass` lookup (`ExternalConverterAnalyzer`) is what fails in native; whether the service path is
  enough on its own was not read. `token-sheriff-client/target/classes` did not exist in the checkout at
  authoring time, so this could not be checked
- HYPOTHESIS: a client-only Quarkus consumer (no validation extension) gets no DSL-JSON service-provider
  registration from `token-sheriff-client-quarkus-deployment` — confirm/refute at
  `token-sheriff-quarkus-parent/token-sheriff-client-quarkus-deployment/src/main/java/de/cuioss/sheriff/token/client/quarkus/deployment/TokenSheriffClientProcessor.java`
  (no such step today, observed above) and the client deployment `pom.xml` dependency on
  `token-sheriff-validation-quarkus-deployment` (it is declared with no `<scope>`, i.e. compile scope, at
  `token-sheriff-quarkus-parent/token-sheriff-client-quarkus-deployment/pom.xml:34`; so the validation
  deployment module's service-provider step may run for any client consumer, which is the point the outline
  must settle) (verify-at-outline)
- Verify-first clause: before scoping, settle (1) which delivery mechanism reaches API Sheriff's dependency
  shape (`token-sheriff-client` + `token-sheriff-client-quarkus` + `token-sheriff-validation-quarkus`, per
  `api-sheriff/pom.xml:119-130`) and is also correct for a non-Quarkus GraalVM consumer of the bare
  `token-sheriff-client` jar, (2) whether the service-loader path already registers the client converters
  (hypotheses above), (3) whether the native test endpoint exists or must be created, and (4) that no other
  class in the client jar needs runtime initialization (re-run the static-`SecureRandom` search at HEAD, and
  also search for other `static final` fields of types the native image refuses to bake in). A refutation
  loops back to re-scope.

## Expected Surface

- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus-deployment/src/main/java/de/cuioss/sheriff/token/client/quarkus/deployment/TokenSheriffClientProcessor.java`:75 — `feature`, `additionalBeans`, `registerClientExceptionMapper` (new `@BuildStep` methods land here if the BuildStep mechanism is chosen)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus-deployment/src/test/java/de/cuioss/sheriff/token/client/quarkus/deployment/TokenSheriffClientProcessorTest.java`:1 — `TokenSheriffClientProcessorTest` (extended for the new steps)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus-deployment/pom.xml`:1 — module dependencies (a Quarkus native-image build-item artifact may need to be confirmed present; no dependency is added without approval)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/FlowContext.java`:55 — `SECURE_RANDOM` (read only; registration target)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/PkceChallenge.java`:43 — `SECURE_RANDOM` (read only; registration target)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/AccessTokenConfirmation.java`:44 — `AccessTokenConfirmation`, `Confirmation`, `JsonObjectProbe` (read only)
- HYPOTHESIS: `token-sheriff-client/src/main/resources/META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-client/` — new directory, only if the `META-INF/native-image` mechanism is chosen (verify-at-outline; the parent `token-sheriff-client/src/main/resources` does not exist today)
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/` — new guard test classes for the converter enumeration and the static-`SecureRandom` enumeration (names chosen at outline; the guard may instead live in the deployment module's test tree) (verify-at-outline)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/main/java/de/cuioss/sheriff/token/integration/endpoint/JwtValidationEndpoint.java`:1 — sibling of the endpoint style a new client test endpoint would follow
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/main/java/de/cuioss/sheriff/token/integration/endpoint/` — new client-engine test endpoint (to be created) (verify-at-outline)
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/` — new native `*IT` class for the two fail-open cases (to be created) (verify-at-outline)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/main/resources/application.properties`:154 — `quarkus.native.additional-build-args` (read; the plan must not need a change here, which is the point of the plan)
- OBSERVED: `doc/client/requirements.adoc`:1 — `Client Requirements` (new requirement or paragraph, next free `CLIENT-N`)
- OBSERVED: `doc/client/specification/test-strategy.adoc`:1 — `Client Specification — Test Strategy` (guard tests named)
- OBSERVED: `doc/LogMessages.adoc`:1 — `Log Messages for Token-Sheriff`, only if a new LogRecord is added

## Dependencies and Sequencing

- Target release: 0.9.10 (the repository is at `0.9.10-SNAPSHOT`).
- `parallelization_scope` is 1: plans of this epic run sequentially, one at a time, in ordinal order. This
  plan runs first.
- Depends on: none.
- Overlaps with: PLAN-02, PLAN-03 and PLAN-04 all live in `token-sheriff-client`; this plan edits no file
  those three edit in `token-sheriff-client/src/main`. The one shared surface is the
  `token-sheriff-quarkus-integration-tests` module (PLAN-04 may add an IT there) and `doc/client/requirements.adoc`
  and `doc/client/specification/test-strategy.adoc` (every plan of the epic edits these; the requirement id
  is taken as next free at write time, never pre-assigned).
- Adjacent to: `token-sheriff-validation-quarkus-deployment` `TokenSheriffProcessor` (precedent, untouched
  unless the service-provider hypothesis requires a change); `FlowContext` and `PkceChallenge` themselves
  stay untouched (the fix is registration, not a change to the static field).
- API Sheriff's matching deletion (`TokenClientDslJsonReflection`, its test, and the `--initialize-at-run-time`
  build argument) is its own plan, AS PLAN-34, which waits for a released 0.9.10. It is NOT part of this
  spec. Until then both registrations coexist, which is harmless.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/api-sheriff-seams/plans/PLAN-01-client-native-image-registration.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
