# Landing Analysis: PLAN-03 — Re-apply issuer policy on access-token cache hits

epic: quality-report
workstream: WS-01
pr: #833 (https://github.com/cuioss/TokenSheriff/pull/833)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan) after
> corroborating the landing message `implement-plan-03-cache-hit-policy-006.md` against the merged
> PR and the merge commit. Corroborated by this analysis: PR state `merged`, merge commit
> `2ea3ef2f`, the PR title/body, and the merged file footprint (`git show --name-only 2ea3ef2f`).
> NOT corroborated: code-level behaviour (no build or test was run here) and every figure that
> comes only from the landing message (tokens, wall time, review-step counts, Sonar outcome).

## Deliverable Fidelity vs Spec

Evidence is file-level (the merged footprint and the PR body), not a re-read of the semantics.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| V-2 cache hit re-checks token age against the issuer's `maxTokenAgeSeconds` | shipped-as-specified | PR body "Closes V-2"; `AccessTokenCache.java`, `CachedToken.java` changed; the hit path and background eviction reject with `TOKEN_AGE_EXCEEDED`, counted once. Regression tests in `AccessTokenCacheTest.java` and `AccessTokenValidationPipelineTest.java` |
| V-9 entries carry their issuer's clock skew; the hit check uses it | shipped-as-specified | PR body "Closes V-9"; `TokenValidator.java` no longer computes the cross-issuer maximum skew, `AccessTokenValidationPipeline.java` passes the issuer's `ValidationContext` on store |
| Metrics/events stay consistent on a policy rejection at hit | shipped-as-specified | PR body: the rejection emits the same event as the uncached path and is counted once; asserted in the end-to-end pipeline tests |
| Added, unplanned | added-unplanned | `AccessTokenCacheConfig.java` adjusted; **public-surface change**: `AccessTokenCache` drops its global `clockSkewSeconds` constructor parameter and `put()` takes the per-issuer `ValidationContext` |
| Declared but not touched | n/a | `ExpirationValidator.java` and `ExpirationValidatorTest.java` (spec: HYPOTHESIS, verify-at-outline) — not in the merged footprint, so the hypothesis that they needed a change was refuted |

Surface delta: 8 merged paths against 6 declared. Four declared paths were touched; the two untouched
ones are both HYPOTHESIS (`ExpirationValidator.java`, `ExpirationValidatorTest.java`). Four merged
paths were not declared: `AccessTokenCacheConfig.java`, `CachedToken.java`,
`AccessTokenCacheConfigTest.java`, `AccessTokenValidationPipelineTest.java`. The PR states the V-17c
`isExpired` boundary stayed out of scope, as the spec required.

## Metrics and Anomalies

- Tokens: 13,740,985 (landing message; not independently verifiable here)
- Duration: 22,091 s wall (landing message)
- Effort: `medium`; model `claude-sonnet-5-5`
- Anomalies:
  - A Sonar round-trip forced one loop-back from finalize to execute (landing lesson 002).
  - An inline review-fix commit lacked a change-ledger row, so the 5-execute → 6-finalize transition was refused until the row was appended (landing lesson 004). Plan-marshall tooling, not this repo.
  - Pre-push gate degradations: the whole-tree test-compile arm ran module-scoped only (a bare whole-tree test-compile is red on the unmodified base too), and the module-tests arm was DEGRADED. The landing cites a whole-tree `verify -Ppre-commit` with 3197 tests green plus 1775 in the module on head `467a857b`.
  - The footprint capture before worktree removal was skipped, so the plan's own `surface_delta` is `unmeasured`; this record's delta comes from the merge commit instead.

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed head `9648063c` and used its one included review per hour. The fix-up head `467a857b` was therefore not reviewed by it; both of its comments were fixed and answered. Sourcery (optional) was quota-refused too. The operator merged under a recorded barrier-ask-override (gap class `review-barrier-gap`, bound to `467a857b`). Landing message; not independently verified.
- CI/merge: merged through the merge queue as `2ea3ef2f` (read from the PR: `pr_state: merged`, `mergedAt 2026-10-29T12:57:08Z`). Pull-request CI green on the head per the landing; the post-merge JMH benchmark job was still running when the plan's wait timed out.
- Sonar: the landing carries no `step.sonar-roundtrip.*` keys, so no open-issue figure is available for this merge head. Treat as `unknown`, not `0`. The landing lessons say `java:S5778` was fixed and `java:S2093` was suppressed in code with a rationale; neither is a transmitted dismissal, and a code suppression is not what the spec's dismissal rule names.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-03 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-03 --field pr --value 833`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-03 --field landing --value landings/PLAN-03.md`
- [x] row `plan_marshall_plan_id` stamped — value `implement-plan-03-cache-hit-policy`
- [x] `findings-disposition.md`: V-2 and V-9 → FIXED (per PR, code not re-read)
- [x] epic.md narrative reconciled against the queue rows
- [x] Watches opened (open advisories, review-coverage gap, Sonar)
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- `corpus declaration-currency` against the merged footprint: of the 18 other staged specs only PLAN-17 declares a path this plan changed (`TokenValidator.java`; PLAN-01 also declares it but already shipped). PLAN-17 re-verifies at outline.
- PLAN-09's HYPOTHESIS that the `exp` boundary change leaves the cache-hit expiry semantics alone must be re-checked: hit-time expiry now uses each entry's own issuer skew, not a global maximum.
- Open advisories deliberately not fixed (landing message, not independently verified): swapped `@DisplayName` V-9/V-2 labels on two new `AccessTokenValidationPipelineTest` tests; `AccessTokenCache.put()` does not require `iat` when a max age is set (the pipeline cannot reach it, a direct public-API `put` could make eviction throw `MISSING_CLAIM`); no test combines non-zero clock skew with a max age. Recorded as Watches.
- Two draft ADRs (per-entry issuer policy captured at store time; rejection inside the cache before DPoP) were not created for lack of a go-ahead. Recorded as a Watch.
- Five `candidate-lesson` messages from this plan are queued and not yet dispositioned; they join the open routing decision for the earlier thirteen.
