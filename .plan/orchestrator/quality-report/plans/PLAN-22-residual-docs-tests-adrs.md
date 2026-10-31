# PLAN-22: Residual documentation, test and ADR items left open by PLAN-01 to PLAN-06

epic: quality-report
workstream: WS-05

## Objective

About 18 documentation, test, comment and ADR residuals were merged unfixed by the plans that found them
(landing messages and PR bodies, recorded as Watches). Under the operator's standing rule that findings are
fixed immediately, close them in one hygiene plan: wording that over-states or over-generalises, test gaps
and weak or mislabelled tests, an over-budget test layout, stale comments, ADR wording and ADR drafts that
were never created, and the CWE-117 sanitiser that `JweDecryptor` and `JwkKey` still duplicate. No finding
ID: these are residuals, not report findings.

## Deliverables

1. **Wording** — `token-decryption.adoc` over-generalises the `kty` rejection (a non-string `kty` fails
   earlier with `FAILED_TO_DECODE_JWT`); the validation-quarkus README wording for apps that supply their
   own `TokenValidator`; ADR-0005 and ADR-0010 still call the post-redemption clear unconditional; the
   `CLIENT-21` versus `CLIENT-20` mix-up in the `TokenSheriffClientProcessor` Javadoc.
2. **Test gaps** — add the missing tests: non-zero clock skew combined with a max age (cache); a null or
   blank `epk` coordinate; `apu`/`apv` decryption; an inner-JWS payload decode failure; the
   unsupported-compression matrix row; the startup initializer test pinning the proxy dereference.
3. **Test hygiene** — swapped `@DisplayName` V-9/V-2 labels in `AccessTokenValidationPipelineTest`; the stale
   comment on `RefreshAdversarialTest.shouldFailClosedWhenRevocationThrowsUnchecked`; two test lines holding
   raw U+2028/U+2029 characters (replace with escapes); the `HealthCheck` catch-suppression placement per
   `AGENTS.md`; the three test classes over the 400-line budget (split, or record why not — if a split is
   large, do the smallest class and record the others).
4. **ADRs** — write the two PLAN-03 draft ADRs (per-entry issuer policy captured at store time; rejection
   inside the cache before DPoP) and a PLAN-06 ADR (single-use `FlowContext`, and the shared
   `ErrorTextSanitizer` living in the validation module), through the ADR tooling. Check whether ADRs
   0016–0018 still carry provisional numbers; the tree holds 0001–0019.
5. **Sanitiser duplication** — the CWE-117 header sanitiser is duplicated in `JweDecryptor` and `JwkKey`:
   route both through the shared `ErrorTextSanitizer` (PLAN-06), keeping the truncation and escaping each
   call site relies on. Test: both sites produce the same output for the same hostile header text.

## Standing Constraints

- Every claim below came from a landing message or PR body, not from a code read; re-verify each against the
  implementing source at outline before scoping on it (refutation loops back to re-scope; a residual that no
  longer exists is recorded and dropped). The original report text is in
  `.plan/orchestrator/quality-report/source-report/`.
- Do NOT edit `source-report/` or `findings-disposition.md` — the orchestrator records dispositions.
- Fail-closed posture is non-negotiable: deliverable 5 must not weaken any sanitisation.
- Every behavioural change ships with a test that fails before and passes after (JUnit 5, CUI test
  conventions); build and verify via Maven only. Documentation states a guarantee only at the scope the
  enforcing code gives it.
- Operator policy: tool findings (OpenRewrite markers, Sonar issues) are fixed immediately; suppression only
  in the rule's sanctioned syntax with a stated reason.
- SonarCloud analyses PRs (`sonar: configured`): an open Sonar issue is fixed in a pushed commit,
  or dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress
  record and transmitted via `plan-marshall:workflow-integration-sonar:sonar_rest transition
  --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is
  a blocker. The merge gate reads the open-issue count at the head — `0` confirmed or every
  remaining issue dismissed; an `undecidable` read blocks.
- Scope guard: five deliverables, but item-heavy. If the outline finds the test-gap or test-hygiene
  deliverable growing past the rest, split it into its own plan.

## Claim Labels

- OBSERVED: the ADR directory holds 0001 to 0019, with 0016 to 0018 from PLAN-01 and 0019 from PLAN-04 — listed at `doc/adr/`
- OBSERVED: `token-decryption.adoc` lives at `doc/validation/specification/token-decryption.adoc`
- HYPOTHESIS: line 200 of `token-decryption.adoc` over-generalises the `kty` rejection — PLAN-05 landing residual (verify-at-outline)
- HYPOTHESIS: the CWE-117 sanitiser is duplicated in `JweDecryptor` and `JwkKey`; `ErrorTextSanitizer` in `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/error/ErrorTextSanitizer.java` is the shared implementation (verify-at-outline whether it fits both call sites)
- HYPOTHESIS: ADR-0005 and ADR-0010 still call the post-redemption clear unconditional — PLAN-02 landing residual (verify-at-outline)
- HYPOTHESIS: two PLAN-03 draft ADRs were never created, and PLAN-06 skipped `adr-propose` — landing messages (verify-at-outline against `doc/adr/`)
- HYPOTHESIS: the remaining test, comment and Javadoc residuals exist as described in the PLAN-01, 02, 03, 04 and 05 landing records under `landings/` (verify-at-outline)

## Expected Surface

- OBSERVED: `doc/validation/specification/token-decryption.adoc`
- OBSERVED: `doc/adr/0005-A_refresh_failure_quarantines_the_session_after_authorization-server_redemption_but_preserves_it_before.adoc`
- OBSERVED: `doc/adr/0010-A_refresh_token_the_authorization_server_declares_invalid_is_a_third_classification_state.adoc`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwe/JweDecryptor.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/JwkKey.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwe/JweDecryptorTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCacheTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/AccessTokenValidationPipelineTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/RefreshAdversarialTest.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/README.adoc`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/health/TokenValidatorHealthCheck.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/producer/TokenValidatorStartupInitializerTest.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus-deployment/src/main/java/de/cuioss/sheriff/token/client/quarkus/deployment/TokenSheriffClientProcessor.java`
- HYPOTHESIS: new ADR files under `doc/adr/` (numbers allocated by the ADR tooling at outline)

## Dependencies and Sequencing

- Depends on: PLAN-21 (single shared file, `AccessTokenCacheTest.java`; PLAN-21 runs first)
- Overlaps with: PLAN-17 (`TokenSheriffClientProcessor.java`) — read by `corpus declaration-currency` at staging; PLAN-17 runs after. PLAN-19 declares no path here but indexes the ADRs, so it allocates ADR numbers by rule at outline; run PLAN-22 before it
- Adjacent to: PLAN-21 (behaviour residuals of the same plans)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-22-residual-docs-tests-adrs.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
