envelope_version=1
sender_type=plan
sender_id=plan-24-client-id-strict-typing
epic=quality-report
kind=landing
created=2026-11-06T16:58:58Z

## What landed

plan-24-client-id-strict-typing shipped as #876 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-24-client-id-strict-typing
epic=quality-report
pr=#876
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=10007629
total_wall_seconds=10459.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2064
executor_version=0.1.2064
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done
step.functional-review.deliverables_met=3
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=3
step.code-review.rules_cited=1
```

## Residue

- The e2e CI job was cancelled by its 30-minute job timeout after all 30 tests passed (teardown hang, recurs on main). Cleared with a rerun; a follow-up on the teardown hang is owed.
- The pre-push gate recorded "test-compile not run" (stand-alone test-compile and module-tests arms degraded; the whole-tree verify was green).
- Advisory doc points left unfixed in `doc/validation/security-reference.adoc` (sub and jti sentences, lines 35 and 47).
- Post-merge `checks wait --adaptive` hung for 10 minutes with no output and was stopped.
- `model` and `effort` are self-reported (the runtime stamp refresh returned `model_unknown`).
