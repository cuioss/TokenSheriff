envelope_version=1
sender_type=plan
sender_id=client-refresh-verification-discovery-cache
epic=api-sheriff-seams
kind=landing
created=2026-11-15T15:11:51Z

## What landed

client-refresh-verification-discovery-cache shipped as #907 (merged via merge queue).

```landing-facts
schema=landing-facts/1
plan_id=client-refresh-verification-discovery-cache
epic=api-sheriff-seams
pr=#907
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=20460947
total_wall_seconds=173973.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2106
executor_version=0.1.2106
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=unknown
step.functional-review.deliverables_met=6
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=6
step.code-review.rules_cited=5
step.branch-cleanup.merge_mechanism=merge_queue
```

## Residue

- The lifecycle publishes no `review-gate` facts, so the three review-surface counts are `unknown` rather than recalled. Narrative only: every CodeRabbit comment on #907 carries a recorded reply and disposition (one inline comment declined with a stated reason, two nitpicks accepted as known test-hardening gaps).
- `sonar_open_new_issues` is `unknown` because the lifecycle `sonar-roundtrip` step counts issues without hotspots. Its own recorded fact is `new_code_issue_count=0` (confirmed), hotspots not read.
- `total_tokens` is a mixed-population figure: the `1-init` phase row ran unmeasured (`totals_tokens_unknown_phases: 1-init`), so the total is a floor.
- `archive-plan` had not yet run when this landing was written; `steps` therefore ends at `emit-landing`.
- The `e2e-tests` job failed twice on one Playwright screenshot protocol error (`accessibility-wcag-compliance.spec.js:60`, heads fd77ed55 and 36b61921) and passed on rerun both times — a recurring environmental flake worth an epic-level look; no branch code is implicated.
- The code review found a real single-flight race in `CachingDiscoveryResolver` (stale read overwriting a newer entry); it was fixed in this PR with a regression test and a negative control.
