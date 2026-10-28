# PLAN-03: Re-apply issuer policy on access-token cache hits

epic: quality-report
workstream: WS-01

## Objective

The default-enabled access-token cache currently bypasses `maxTokenAgeSeconds` on cache hits (the
one claim check that fails open over time, V-2) and checks expiry with the largest clock skew of
any issuer rather than the token's own issuer (V-9). Make every cache hit enforce the issuing
issuer's token-age and skew policy. Closes V-2, V-9.

## Deliverables

1. **V-2** — cache hit re-checks token age against the issuer's `maxTokenAgeSeconds` (re-run
   `validateTokenAge` with the issuer context, or cap entry lifetime at `iat + maxTokenAge`). Test:
   token cached, clock advanced past max age but before `exp` → rejected with the same event as an
   uncached validation.
2. **V-9** — cache entries carry (or resolve) their issuer's clock skew; the hit check uses it.
   Test with two issuers of different skew.
3. Cache metrics/events stay consistent: a policy rejection on hit is counted exactly like the
   uncached path (assert the `SecurityEventCounter` event).

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
- The cache must never return a token the uncached pipeline would reject at the same instant.

## Claim Labels

- OBSERVED: the hit path checks only `verifyToken` and `isExpired(now, clockSkewSeconds)` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCache.java` § `computeIfAbsent` hit branch
- OBSERVED: `ExpirationValidator.validateTokenAge` is only called from `TokenClaimValidator` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/validator/ExpirationValidator.java`
- OBSERVED: `TokenValidator` computes `maxClockSkew` as the max over enabled issuers and passes it to the cache — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/TokenValidator.java` § cache construction
- HYPOTHESIS: the cached entry already holds enough issuer identity to resolve per-issuer policy without a pipeline lookup — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCache.java` § cached entry type (verify-at-outline)

## Expected Surface

- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCache.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/TokenValidator.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/AccessTokenValidationPipeline.java`
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/validator/ExpirationValidator.java` (verify-at-outline)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCacheTest.java`
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/validator/ExpirationValidatorTest.java` (verify-at-outline)

## Dependencies and Sequencing

- Depends on: none
- Overlaps with: PLAN-01 (`TokenValidator.java`, single file — concurrent OK); PLAN-09/PLAN-15 (`ExpirationValidatorTest.java`)
- Adjacent to: `ValidationContext.isExpired` boundary (V-17c) — owned by PLAN-09

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-03-cache-hit-policy.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
