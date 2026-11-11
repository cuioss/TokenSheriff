envelope_version=1
sender_type=plan
sender_id=plan-11-audience-semantics
epic=quality-report
kind=landing
created=2026-11-04T17:29:26Z

## What landed

plan-11-audience-semantics shipped as #853 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-11-audience-semantics
epic=quality-report
pr=#853
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=9081038
total_wall_seconds=11397.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2046
executor_version=0.1.2046
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:pending
step.functional-review.deliverables_met=3
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=3
step.code-review.rules_cited=3
```

## Residue

- Closes V-17f, TV-2 and gap VAL 4. Behaviour change: with `accessTokenAudienceOptional(true)` a token carrying `aud: []` was accepted before and is now rejected (named in the PR body). `azp` is now mapped by a new `StrictStringMapper` (rejects any non-string JSON value). `client_id` was deliberately left on `IdentityMapper` (operator decision), named in the PR as a follow-up.
- The merge was authorised explicitly by the operator for this one PR; the project default (`final_merge_without_asking`) would have merged unasked, which conflicts with the repo's "no auto-merge unless told" rule. The merge went through the merge queue (squash), merge commit a9ce05df.
- Pre-push gate ran degraded on two arms, recorded as such: a stand-alone Maven `test-compile` cannot work in this reactor (the validation test-jar exists only after `package`), and no stand-alone `module-tests` canonical resolves at the default scope. The whole-tree `verify -Ppre-commit` (3621 tests, same HEAD) was green; the operator chose to count it as the cover. The failed stand-alone run auto-filed 120 `build-error` findings that blocked the first merge dispatch; they were resolved as `rejected` as artifacts of a non-gate invocation (two candidate-lesson messages carry this).
- The ADR step returned one borderline proposal (claim type strictness placed at claim mapping); the operator declined it, so no ADR was written.
- Code-review advisories, not acted on: the non-string `azp` tests assert only the exception supertype; `client_id` still stringifies non-string values; a `StrictStringMapper` rejection is neither counted nor logged (follows the existing TokenBuilder malformed-claim convention).
