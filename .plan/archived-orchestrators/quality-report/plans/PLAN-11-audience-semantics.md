# PLAN-11: Audience semantics — empty `aud` array and list-typed `azp`

epic: quality-report
workstream: WS-01

## Objective

An empty `aud` array is treated as "audience missing", which opens the `azp` fallback, so a token
with `aud: []` and a matching `azp` is accepted — a security fork with zero tests (V-17f, TV-2).
`azp` is read via `getOriginalString()` with no test for a list or non-string value (gap VAL 4).
Decide the semantics, implement them fail-closed, and test both directions. Closes V-17f, TV-2, gap VAL 4.

## Deliverables

1. **V-17f** — decide at outline (recommendation: empty `aud` array = present-but-mismatched, never
   eligible for the `azp` fallback); implement in `AudienceValidator` / `ClaimValue` as needed.
2. **TV-2** — parameterized test over ACCESS/ID token × {fallback on + matching azp, fallback off, no azp}
   for `aud: []`, asserting outcome and the `SecurityEventCounter` event.
3. **gap VAL 4** — tests (and fix if needed) for `azp` as a JSON array and as a non-string; must reject.

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
- This plan may only narrow acceptance; any token accepted today via `aud: []` + `azp` is the defect.

## Claim Labels

- OBSERVED: `ClaimValue.isNotPresentForClaimValueType()` treats an empty `STRING_LIST` as missing — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/claim/ClaimValue.java` § `isNotPresentForClaimValueType`
- OBSERVED: `AudienceValidator` routes missing aud to `handleMissingAudience`, which accepts when `azpAudienceFallbackEnabled` and azp matches — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/validator/AudienceValidator.java` § `handleMissingAudience`
- OBSERVED: no test uses an empty audience list — swept `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/validator/`
- OBSERVED: `isAzpClaimMatchingExpectedAudience` uses `getOriginalString()` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/validator/AudienceValidator.java`
- HYPOTHESIS: `MandatoryClaimsValidator` also treats `aud: []` as missing and must change consistently — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/validator/MandatoryClaimsValidator.java` (verify-at-outline)

## Expected Surface

- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/validator/AudienceValidator.java`
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/validator/MandatoryClaimsValidator.java` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/claim/ClaimValue.java` (verify-at-outline — prefer a validator-local change)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/validator/AudienceValidatorTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/validator/MandatoryClaimsValidatorTest.java`

## Dependencies and Sequencing

- Depends on: none
- Overlaps with: none
- Adjacent to: `doc/validation/security-reference.adoc` audience section — update if semantics change (single paragraph, allowed here)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-11-audience-semantics.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
