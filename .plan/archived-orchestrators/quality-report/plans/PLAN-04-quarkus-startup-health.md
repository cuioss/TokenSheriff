# PLAN-04: Quarkus startup validation, honest liveness, provider registration

epic: quality-report
workstream: WS-03

## Objective

The extension documents "fails at application startup" for invalid configuration but validates
lazily, and invalid config then drives the `@Liveness` check DOWN — since #795 registers the check
in every build this can put any deployment into a restart loop (Q-3, Q-4). Exception mappers are
still not registered by a build step (Q-2 remainder), the JAX-RS integration is RESTEasy-Classic-only
without saying so (Q-8), and the validation-quarkus README YAML examples omit `enabled: true` so they
fail at first use (D-1 remainder). Closes Q-2 (rem.), Q-3, Q-4, Q-8, D-1 (rem.).

## Deliverables

1. **Q-3** — eager startup validation: the validator is built at startup (`@Startup` / `StartupEvent`
   observer) so invalid config fails boot. `@QuarkusTest`-style startup-failure test.
2. **Q-4** — `TokenValidatorHealthCheck` never throws from `call()`; a resolver failure is reported
   without failing liveness (catch + report, or move to `@Readiness` — decide at outline and update
   the class Javadoc's "always UP" claim accordingly). Unit test for the failing-resolver case.
3. **Q-2 remainder** — register `ProblemDetailExceptionMapper` (validation) and `ClientExceptionMapper`
   (client) through build steps; processor tests pin the registration; one IT asserts
   `application/problem+json`.
4. **Q-8** — either add Quarkus REST (reactive) provider registration alongside the Classic one, or
   document the Classic-only limitation in the extension docs (decide at outline; documenting is the
   minimum).
5. **D-1 remainder** — add `enabled: true` to every issuer in the README "Basic Configuration" and
   "Multiple Issuers" YAML blocks; align the startup-failure wording with deliverable 1.

## Standing Constraints

- Every claim below was re-grounded at HEAD `aa77acd9`; re-verify each against the implementing
  source at outline before scoping on it (refutation loops back to re-scope). The original report
  text is in `.plan/orchestrator/quality-report/source-report/` (read-only, stale line numbers).
- The PR title/body references every finding ID it closes (e.g. "Fix V-3"). Do NOT edit
  `source-report/` or `findings-disposition.md` — the orchestrator records dispositions.
- Fail-closed posture is non-negotiable: no fix may widen acceptance of a token, a callback or a
  configuration that is rejected today, unless the deliverable says so explicitly.
- Every behavioural fix ships with a test that fails before and passes after (JUnit 5, CUI test
  conventions, generators where variation matters); build and verify via Maven only.
- Log changes follow CUI logging: LogRecord in the module's LogMessages class, and
  `doc/LogMessages.adoc` updated in the same PR for any new/renumbered identifier.
- SonarCloud analyses PRs (`sonar: configured`): an open Sonar issue is fixed in a pushed commit,
  or dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress
  record and transmitted via `plan-marshall:workflow-integration-sonar:sonar_rest transition
  --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is
  a blocker. The merge gate reads the open-issue count at the head — `0` confirmed or every
  remaining issue dismissed; an `undecidable` read blocks.
- Liveness must reflect process health only; configuration errors surface at boot (deliverable 1)
  or on readiness, never as a liveness restart loop.

## Claim Labels

- OBSERVED: no `StartupEvent`, `@Startup` or `@Observes` in quarkus runtime/deployment main; `TokenValidatorProducer` is lazy `@ApplicationScoped` with `@PostConstruct` — read at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/TokenValidatorProducer.java`
- OBSERVED: `TokenValidatorHealthCheck` is `@Liveness`; `call()` → `ObservedValidatorResolver` → `producerHandle.get()` uncaught — read at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/health/TokenValidatorHealthCheck.java` § `call`
- OBSERVED: #795 registers both health checks as unremovable beans; mappers are not registered — read at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/src/main/java/de/cuioss/sheriff/token/quarkus/deployment/TokenSheriffProcessor.java` § `additionalBeans`
- OBSERVED: `TokenSheriffClientProcessor` registers only `TokenSheriffClientProducer` — read at `token-sheriff-quarkus-parent/token-sheriff-client-quarkus-deployment/src/main/java/de/cuioss/sheriff/token/client/quarkus/deployment/TokenSheriffClientProcessor.java`
- OBSERVED: `registerCustomAccessLogFilter` returns a `ResteasyJaxrsProviderBuildItem` (Classic only) — read at `TokenSheriffProcessor.java` § `registerCustomAccessLogFilter`
- OBSERVED: README YAML blocks lack `enabled: true` while `IssuerConfigResolver` defaults to disabled — read at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/README.adoc` § Basic Configuration
- HYPOTHESIS: the mappers are currently not active at runtime (no jandex index / beans.xml in runtime jars) — confirm/refute at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/pom.xml` and an IT asserting `application/problem+json` (verify-at-outline)

## Expected Surface

- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/producer/TokenValidatorProducer.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/health/TokenValidatorHealthCheck.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/observability/ObservedValidatorResolver.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/src/main/java/de/cuioss/sheriff/token/quarkus/deployment/TokenSheriffProcessor.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus-deployment/src/main/java/de/cuioss/sheriff/token/client/quarkus/deployment/TokenSheriffClientProcessor.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/README.adoc`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/resources/META-INF/quarkus-config-doc.adoc`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/health/TokenValidatorHealthCheckTest.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus-deployment/src/test/java/de/cuioss/sheriff/token/quarkus/deployment/TokenSheriffProcessorBuildStepTest.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/health/HealthCheckIntegrationIT.java`

## Dependencies and Sequencing

- Depends on: none
- Overlaps with: PLAN-08 (`quarkus-config-doc.adoc`, single file — D-8 row vs startup wording)
- Adjacent to: `IssuerConfigResolver.java` Javadoc startup claim — touched only if wording must change; else left to PLAN-08

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-04-quarkus-startup-health.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
