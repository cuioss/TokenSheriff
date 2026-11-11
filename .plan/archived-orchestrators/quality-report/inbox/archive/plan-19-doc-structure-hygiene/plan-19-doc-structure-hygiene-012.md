envelope_version=1
sender_type=plan
sender_id=plan-19-doc-structure-hygiene
epic=quality-report
kind=landing
created=2026-11-06T22:46:05Z

## What landed

plan-19-doc-structure-hygiene shipped as #879 (merged through the merge queue as d1af3ebb).

```landing-facts
schema=landing-facts/1
plan_id=plan-19-doc-structure-hygiene
epic=quality-report
pr=#879
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=16357504
total_wall_seconds=10396.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2064
executor_version=0.1.2064
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:pending,archive-plan:pending
step.functional-review.deliverables_met=5
step.functional-review.deliverables_met_with_residuals=1
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=2
step.code-review.rules_cited=1
```

## Residue

- Findings closed by this PR: D-5, Q-12, D-7, D-10, D-11, D-12, D-15. Deliverable 6 (not in the original spec) set all 22 implemented Proposed ADRs to Accepted, on the operator's answer to the outline question; ADR-0007, ADR-0010 and ADR-0026 also carry body corrections made in response to review comments, so the frozen criterion "only the Status line of each ADR changes" holds for the deliverable's own commit but not for the branch net diff (functional review advisory D6.R1).
- No Maven build, test run or Sonar scan happened: the repository's change check skips them for documentation-only PRs. The pre-push gate and the push freshness gate were recorded as "no build owed", not as green. Sonar's open-issue count at the head was undecidable (no analysis exists), and the operator accepted the gap explicitly; the sonar-roundtrip step is recorded done with work_performed=false and no count facts.
- CodeRabbit reviewed the first pushed head; its allowance (one included review per hour) was spent, so it left no fresh review at the final head 56710e6a. The pre-merge participation check resolved participated from the first-pass review.
- Open follow-ups, deliberately not fixed in this PR: token-sheriff-quarkus-parent/README.adoc line 14 still credits ClientRuntimeConfig with the configuration mapping (ClientConfigMapper does it); doc/commons/requirements.adoc (COMMONS-2) and doc/commons/specification/transport.adoc say plain HTTP is always rejected without the per-issuer allowInsecureHttp opt-in now documented in the validation docs; RefreshFailureClassification javadoc may still describe a pre-redemption token as untouched and still valid, which ADR-0010 now qualifies.
- The merge queue offered no per-enqueue squash-message override and the repository merge-commit-message default was not changed, so the squash commit message may carry the PR body including CodeRabbit's generated summary.
- Lessons: 11 candidate-lesson messages were sent to this inbox before the landing.
