envelope_version=1
sender_type=plan
sender_id=plan-22-residual-docs-tests-adrs
epic=quality-report
kind=landing
created=2026-11-05T23:06:16Z

## What landed

plan-22-residual-docs-tests-adrs shipped as #863 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-22-residual-docs-tests-adrs
epic=quality-report
pr=#863
merge_state=merged
cleanup_owed=false
deliverables_total=9
deliverables_done=9
total_tokens=50315372
total_wall_seconds=64587.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2046
executor_version=0.1.2046
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done
step.branch-cleanup.merge_mechanism=merge_queue
step.branch-cleanup.merge_state=merged
step.branch-cleanup.cleanup_owed=false
step.create-pr.pr_number=863
step.record-metrics.any_phase_missing_end_time=false
step.sonar-roundtrip.count_status=confirmed
step.sonar-roundtrip.new_code_issue_count=0
step.finalize-step-sync-baseline.action=rebased
step.finalize-step-sync-baseline.upstream_commit_count=2
step.code-review.acceptance=operator_override
step.functional-review.acceptance=operator_override
```

## Residue

- `effort` is `unknown`: the plan status carried no `effort` stamp, and `refresh-runtime-stamp` at landing returned `refreshed: false, reason: model_unknown` (model kept as the self-reported start-time value). The landing is INCOMPLETE at that key by design.
- `steps` lists the steps recorded before this emission; `emit-landing` (this step) and `archive-plan` had not yet recorded an outcome when the payload was assembled.
- `functional-review` and `code-review` were both closed as operator overrides (`may_close=operator_override`) after loop-back rounds, each accepting one residual finding. `pre-push-quality-gate` reused a fresh whole-tree verify with no own build (test-compile/module-tests UNRUN per its detail).
- `total_tokens` mixes populations (inline, main + sub-agent): 1-init is inline-only, the other phases are main + sub-agent. 3-outline and 5-execute were re-entered (close_count > 1) and a finalize loop-back made the 6-finalize boundary non-monotonic; the sum is across every close.
- The preference-emitter produced no owed hints (6 candidate dispositions, none recurring on an attributed module).
