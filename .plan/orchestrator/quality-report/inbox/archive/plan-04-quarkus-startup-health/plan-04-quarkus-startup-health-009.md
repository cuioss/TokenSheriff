envelope_version=1
sender_type=plan
sender_id=plan-04-quarkus-startup-health
epic=quality-report
kind=landing
created=2026-10-29T15:45:31Z

## What landed

plan-04-quarkus-startup-health shipped as #834 (merged via merge queue, squash ef720fd0).

```landing-facts
schema=landing-facts/1
plan_id=plan-04-quarkus-startup-health
epic=quality-report
pr=#834
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=25399213
total_wall_seconds=27953.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2027
executor_version=0.1.2027
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
```

## Residue

- Merge went through under a recorded `barrier-ask-override` (review-barrier-gap): required reviewer CodeRabbit was rate limited (quota) and optional Sourcery refused on head 0053b11c; both had reviewed the previous head 7b0d80cc and the delta was a single test assertion CodeRabbit requested. The operator chose "Merge anyway (record reason)".
- functional-review and code-review were closed by the operator at head 0053b11c (`may_close: operator_override`), not re-run on that head. Functional review carried an accepted coverage gap (D5.C3), written into the PR description. Code review: 0 blocking, 4 advisory notes deliberately left unfixed.
- `pre-push-quality-gate` recorded "whole-tree verify green (3201 tests); test-compile arm unusable": the bare whole-tree test-compile arm fails identically on main (missing test-jar classes), so it was recorded as an honest degradation rather than a pass.
- Token figure caveat: a phase-5 dispatch boundary absorbed 327956 triage tokens that belong to finalize, so per-phase attribution is skewed (the total is unaffected). Runtime `effort` was never stamped, hence `unknown`.
- Post-merge `checks wait --adaptive` failed with "gh CLI not found" because it ran inside the already-removed worktree; this is a tooling failure, not a CI result. CI was green before merge.
- 120 stale pending `build-error` console-noise findings (phase-5 build-runner producer, Maven banner noise) remain in the plan store; `pending_findings_blocking_count` was 0.
- `archive-plan` had not yet run when this landing was written.
