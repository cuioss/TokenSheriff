envelope_version=1
sender_type=plan
sender_id=plan-13-renumber-commons-validation
epic=client-framework-aspects
kind=landing
created=2026-11-24T22:41:31Z

## What landed

plan-13-renumber-commons-validation shipped as #956 (merged via the platform merge queue as 1022071f).

```landing-facts
schema=landing-facts/1
plan_id=plan-13-renumber-commons-validation
epic=client-framework-aspects
pr=#956
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=21031778
total_wall_seconds=37310.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2133
executor_version=0.1.2133
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done
unresolved_review_threads=0
review_body_findings_total=2
review_body_findings_handled=2
sonar_open_new_issues=0
step.functional-review.deliverables_met=4
step.functional-review.deliverables_met_with_residuals=2
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=5
step.code-review.rules_cited=5
```

## Residue

- **Review-body counts are derived, not a review-gate fact.** No `review-gate` fact was recorded on this run, so `review_body_findings_total` / `_handled` are the two `kind: review_body` pr-comment findings in the plan's findings store (CodeRabbit 6ccba2, Sourcery 009dea), both carrying a disposition with a stated reason. `unresolved_review_threads=0` was read live from the merged PR (2 threads, 0 unresolved).
- **Deferred follow-up: xref-target validation.** CodeRabbit (6ccba2) asked the `RequirementIdIntegrityTest` guard to also validate xref targets against the target document's anchors. Not added here because it extends the guard beyond its three documented checks (monotonic order, no dangling reference, no duplicate definition). Needs a follow-up item.
- **PLAN-14 still owes the CLIENT ids.** The guard's CLIENT prefix is pending until PLAN-14; the `.plan/orchestrator` corpus still carries the old COMMONS/VALIDATION ids (excluded from the renumber by request).
- **Realized footprint is 33 files, the declared footprint 66** (declared over-estimates the renumber's reach; PR stayed under the 100-file cap).
- **Code-review advisories (non-blocking):** `RequirementIdIntegrityTest` is 713 lines (> 500 guideline); parameterization opportunity; skip message references PLAN-14; the COMMONS "Former identifiers" intro mentions sub-ids which COMMONS lacks (`doc/commons/requirements.adoc:334`); one uncovered no-markers fall-through.
- **Functional advisories (non-blocking):** COMMONS-1..8 ranges restated as 1..9 in four client-doc places; no full-reactor run at the final head beyond the whole-tree `verify`/`test`; commit b2a538c3's message over-claims.
- **Run incidents:** a kernel OOM killed the first whole-tree quality gate (a concurrent Maven build from another session; re-run with `-T 4`); one code-review blocking finding (F1, 0 used as the no-parent sentinel) was fixed in 3ff7df48; the post-merge `Run JMH Benchmarks` workflow was still running when the post-merge CI wait timed out (non-gating).
