envelope_version=1
sender_type=plan
sender_id=plan-06-callback-logout-hardening
epic=quality-report
kind=landing
created=2026-10-30T19:12:06Z

## What landed

plan-06-callback-logout-hardening shipped as #840 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-06-callback-logout-hardening
epic=quality-report
pr=#840
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=39378383
total_wall_seconds=154547
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2027
executor_version=0.1.2027
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:skipped,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
step.functional-review.deliverables_met=6
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
```

## Residue

- The merge landed as squash a56b1b59 through the merge queue under a recorded `barrier-ask-override` over `review-barrier-gap`, bound to head aa72e8cb: required reviewer coderabbit had no review of that head (it reviewed 0ad8f597; its hourly quota, shared with plan-07, was spent), and sourcery was quota-limited. The last commit only adds one test input for a CodeRabbit nitpick. The operator chose this explicitly. `automatic-review` and `code-review` carry operator-override records at aa72e8cb.
- The merge queue ejected the PR once (merge_group run 41108666343, an unrelated 20 ms Awaitility timing test in benchmarking/benchmark-core, LibraryMetricsExporterTest); one re-enqueue at the same head merged it. `cleanup_owed` is false: worktree removed, local branch and tracking ref pruned, main pulled.
- `adr-propose` was skipped by the orchestrator: the plan's scope forbids repo documentation writes and an ADR file would have moved HEAD past the verified tree. Whether an ADR is wanted is for the operator to decide separately.
- The installed finalize scripts lack verbs the docs name (`pre_merge_gate record-merge` and `consequence-text`), so `branch-cleanup` was recorded through `mark-step-done` with the same facts. Five candidate-lesson messages from `lessons-capture` are in this epic's inbox.
- `archive-plan` runs after this landing, so it is not listed in `steps`.
- Token total: the 6-finalize accumulator was completed after the fact with six agent dispatches that had not been accumulated, so the figure is a floor for any dispatch still unrecorded (the phase-5 TASK-19 envelope, 192197 tokens, was not folded into the closed 5-execute row).
