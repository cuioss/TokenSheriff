# PLAN-08: Quarkus CDI binding and configuration correctness

epic: quality-report
workstream: WS-03

## Objective

Class-level `@BearerAuth` requirements are silently dropped under ArC because the interceptor reads the
annotation from the proxy subclass (Q-1). Client beans documented as replaceable are not `@DefaultBean`
(Q-5). JWE configuration silently falls back on a typo'd default kid or orphaned properties (Q-6). Issuer
discovery may miss env-var-only configuration (Q-9). The DPoP nonce-cache TTL default is documented as
300 while the code uses 360 with a floor (D-8/J-8). Closes Q-1, Q-5, Q-6, Q-9, D-8, J-8.

## Deliverables

1. **Q-1** — interceptor resolves `@BearerAuth` from the declaring class / interceptor bindings, not the
   ArC subclass; unit test plus an integration-test endpoint with class-level `@BearerAuth` requirements.
2. **Q-5** — `tokenStore()`/`refreshScheduler()` producers annotated `@DefaultBean`; test that a user bean
   replaces them.
3. **Q-6** — unknown default kid and orphaned ancillary JWE properties fail configuration loudly
   (mirroring the DPoP orphan check); tests.
4. **Q-9** — verify at outline whether env-var-only issuer config is discovered via
   `getPropertyNames()`; if not, fix discovery; either way a test with an env-var config source pins it.
5. **D-8 / J-8** — config doc row and `JwtPropertyKeys` Javadoc state default 360 and the
   `proofMaxAge + 60` floor.

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

## Claim Labels

- OBSERVED: interceptor reads `ctx.getTarget().getClass().getAnnotation(BearerAuth.class)`; `BearerAuth` is not `@Inherited` — read at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/interceptor/BearerTokenInterceptor.java`
- OBSERVED: `tokenStore()`/`refreshScheduler()` are plain `@Produces` under a "replaces the bean" Javadoc — read at `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/TokenSheriffClientProducer.java`
- OBSERVED: `defaultKid.map(loadedKeys::get).orElseGet(first key)`; null returned when only ancillary props set — read at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/jwe/JweDecryptionConfigResolver.java`
- HYPOTHESIS: env-var-only properties are absent from `getPropertyNames()` for this config source setup, so such issuers are not discovered — confirm/refute at `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/config/ConfigValueParser.java` § `discoverNameSegments` (verify-at-outline)
- OBSERVED: config doc shows nonce-cache TTL default `300`; `DpopConfig.DEFAULT_NONCE_CACHE_TTL_SECONDS = 360` raised to `max(ttl, proofMaxAge+60)` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/dpop/DpopConfig.java`

## Expected Surface

- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/interceptor/BearerTokenInterceptor.java`
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/annotation/BearerAuth.java` (verify-at-outline)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/TokenSheriffClientProducer.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/jwe/JweDecryptionConfigResolver.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/config/ConfigValueParser.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/config/JwtPropertyKeys.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/resources/META-INF/quarkus-config-doc.adoc`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/interceptor/BearerTokenInterceptorTest.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/jwe/JweDecryptionConfigResolverTest.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/config/ConfigValueParserTest.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/test/java/de/cuioss/sheriff/token/client/quarkus/TokenSheriffClientProducerTest.java`
- HYPOTHESIS: a class-level endpoint under `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/main/java/de/cuioss/sheriff/token/integration/endpoint/` (verify-at-outline)

## Dependencies and Sequencing

- Depends on: none
- Overlaps with: PLAN-04 (`quarkus-config-doc.adoc`, single file); PLAN-17 (`BearerAuth.java` Javadoc J-10, single file — PLAN-17 later)
- Adjacent to: `DpopConfig.java` — read-only reference here, not modified

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-08-quarkus-config-cdi.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
