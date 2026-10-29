envelope_version=1
sender_type=plan
sender_id=implement-plan-03-cache-hit-policy
epic=quality-report
kind=landing
created=2026-10-29T13:13:00Z

## What landed

implement-plan-03-cache-hit-policy shipped as #833 (merged). Access-token cache hits now re-apply the issuing issuer's own maxTokenAgeSeconds (V-2, TOKEN_AGE_EXCEEDED) and the issuer's own clock skew (V-9) instead of the cross-issuer maximum skew. Squash commit 2ea3ef2f via the merge queue.

```landing-facts
schema=landing-facts/1
plan_id=implement-plan-03-cache-hit-policy
epic=quality-report
pr=#833
merge_state=merged
cleanup_owed=false
deliverables_total=2
deliverables_done=2
total_tokens=13740985
total_wall_seconds=22091.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2027
executor_version=0.1.2027
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:pending
step.functional-review.deliverables_met=2
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=2
step.code-review.rules_cited=2
```

## Residue

- Merged under a recorded barrier-ask-override (gap class review-barrier-gap, bound to head 467a857b). The required reviewer CodeRabbit hit its review quota (1 included review per hour) on the final head, so that head was not reviewed by it; CodeRabbit had reviewed an earlier head, and both of its comments were fixed (TASK-4, commit 467a857b) and answered. The operator chose "Merge anyway - proceed unreviewed" at the re-review timeout prompt and said: "if there was at least one valid Coderabbit review: merge". Sourcery (optional) was quota-refused as well.
- Open advisories, deliberately not fixed: (1) the @DisplayName V-9/V-2 labels on the two new AccessTokenValidationPipelineTest tests are swapped; (2) AccessTokenCache.put() does not require iat when a max age is set, so a direct public-API put with a max age and no iat would make eviction throw MISSING_CLAIM (the pipeline cannot reach this); (3) no test combines a non-zero clock skew with a max age.
- Pre-push gate degradations: the whole-tree test-compile arm ran module-scoped only (a bare whole-tree test-compile is red on the unmodified base too, an inherited test-jar resolution limit), and the module-tests arm was DEGRADED. Compensating evidence: a whole-tree verify -Ppre-commit with 3197 tests green, plus 1775 in the module, on head 467a857b. Pull-request CI was green on that head; the post-merge JMH benchmark job was still running when the wait timed out.
- Two draft ADRs (per-entry issuer policy captured at store time; rejection inside the cache before DPoP) were not created: the operator gave no go-ahead.
- Orchestrator-owned dispositions: this plan did not edit source-report/ or findings-disposition.md for V-2 / V-9.
- The footprint capture before worktree removal was skipped (worktree-remove ran first); merge_commit_sha 2ea3ef2f is recorded as the fallback.
- Token total is measured over one session; metrics rows for a few dispatched agents carry interim usage figures, so the total is approximate.
- archive-plan was still pending when this landing was written (it runs after this step by design).
