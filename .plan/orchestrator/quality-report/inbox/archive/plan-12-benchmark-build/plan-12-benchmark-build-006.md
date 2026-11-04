envelope_version=1
sender_type=plan
sender_id=plan-12-benchmark-build
epic=quality-report
kind=landing
created=2026-11-01T17:33:39Z

## What landed

plan-12-benchmark-build shipped as #846 (merged by the platform merge queue as 17a3ef62, under the operator's recorded coverage-gap ruling).

```landing-facts
schema=landing-facts/1
plan_id=plan-12-benchmark-build
epic=quality-report
pr=#846
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=19496272
total_wall_seconds=83910.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2027
executor_version=0.1.2027
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done
step.functional-review.deliverables_met=4
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=3
step.code-review.rules_cited=2
```

## Residue

- Review gate closed by an operator override, not by a clean pass: the required bot CodeRabbit was quota-limited and had not reviewed the last two fix commits (1a6aa8f8, 145f838c); the operator ruled "Accept the coverage gap (record reason)" at head 145f838c. CodeRabbit had reviewed the main change; its three follow-ups were fixed (TASK-10) and answered on the PR. CI, Sonar (0 new-code issues) and the plan's own functional and code reviews were clean at that head.
- Three review loop-backs occurred: functional review round 1 (missing red-before-green evidence for BenchmarkStateScopeTest, closed by TASK-008), code review round 1 (CoreJfrBenchmark token mismatch, fixed by TASK-009), and the CodeRabbit follow-ups (TASK-010). The wall-clock total includes a roughly 21-hour pause caused by a weekly usage limit.
- The pre-push quality gate was satisfied from the whole-tree `verify -Ppre-commit` run on the exact committed head (3443 tests) instead of re-running the per-bundle, test-compile and module-tests arms separately; this Maven project's whole-tree verify subsumes them. Orchestrator deviation, disclosed in the decision log.
- Installed plan-marshall executor is 0.1.2027 while the workflow docs read were 0.1.2040: `pre_merge_gate consequence-text` / `record-merge` and `review_completeness roster` do not exist in the installed scripts; branch-cleanup was recorded with `mark-step-done` directly. See the candidate-lesson messages from this plan.
- Effort was never stamped for this run (written as unknown above). Two orchestrator slips were logged and corrected: a stray head stamp on finalize-step-sync-baseline (made pre_merge_gate refuse once; re-written without it) and a phase-boundary stamped one call early.
- Commit trailers differ: the first TASK-5 commit carries only the plan-marshall trailer; later commits carry the Claude co-author lines.
