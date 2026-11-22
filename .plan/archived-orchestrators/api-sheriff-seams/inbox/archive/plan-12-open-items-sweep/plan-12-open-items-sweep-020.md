envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=landing
created=2026-11-19T06:33:14Z

## What landed

plan-12-open-items-sweep shipped as #925 (merged, queue merge ae493e00 on main).

```landing-facts
schema=landing-facts/1
plan_id=plan-12-open-items-sweep
epic=api-sheriff-seams
pr=#925
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=41296404
total_wall_seconds=58208.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2133
executor_version=0.1.2133
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
unresolved_review_threads=0
review_body_findings_total=5
review_body_findings_handled=0
sonar_open_new_issues=0
step.functional-review.deliverables_met=1
step.functional-review.deliverables_met_with_residuals=5
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=11
step.code-review.rules_cited=6
```

## Residue

- **Review-gate numbers are measured, and they disagree with the plan's own record.** `runbook_lane review-gate --pr-number 925` answered `merge_allowed: false, reason: body_findings_undisposed` with 5 review-body findings (additional-1, additional-2, inline-1, inline-2, inline-3) and 0 handled. The plan did disposition them: the three inline comments are the findings `1d18b5`, `1d6c14` and `b115be` (fixed by TASK-016, an inline edit and TASK-018, each replied to and stamped), and the two `additional` items are in finding `79ec3f` (taken into account, declined with reasons, replied in one batched comment). The gate does not read those dispositions, so `review_body_findings_handled=0` is what it measured, not what the plan recorded. The merge path (`pre_merge_gate merge`) ran its own findings and participation gates and was clean (0 pending findings, `participation_gate: complete`).
- **`sonar_open_new_issues=0` is the Sonar scan of the plan's last scanned head (33e68ac0, count_status confirmed, 0 new-code issues); security hotspots were not read.**
- **CodeRabbit did not review the commit 97096a5e** (quota refusal, one included review per hour); it reviewed the rebased final head 33e68ac0 (files changed since 0e84e8a5) and posted no actionable comments. Participation proves a review was published, not its quality.
- **One rebase happened inside finalize** (content conflict with #926, JDK 27 malformed Content-Length framing, resolved by an agent with the operator's authorization). The rebase rewrote every commit SHA, so the change ledger no longer matches the branch and `review_support attribution` answered `declared_only / commit_not_on_branch` for the last three review rounds. The branch-cleanup step record renders `rebase deferred to queue`, which describes the final firing (queue route, rebase skipped) and not the earlier firing that rebased and force-pushed.
- **The post-merge JMH benchmark workflow (Performance Benchmark) was still running when cleanup ended and was not awaited**; the other 24 checks were green.
- **Corrections owed to the 19 candidate-lesson messages already in this epic's inbox** (reported by the lessons-capture agent; they carry statements its logs do not back): message 015 says branch cleanup "did not run" `manage-change-ledger remap` (the logs only show that no remap was recorded); 016 says most of the 257 stored pr-comment findings are re-observations of edited comments (not checked, only the total is known); 005 contains a stray fragment ("133 routing through execute", meant decision 01c99d's note that the fix was sent back through execute); 012 lists the `[ERROR] ... manage-change-ledger` lines under the phase-5-execute notation (a guess at how `signal-scan` grouped them).
- **Two figures fed to the finalize usage accumulator were estimates before the measured ones arrived** (a Sonar agent and a triage agent); both were corrected by decision-log entries and the dispatch-boundary rows carry the measured figures, but the accumulator's tool-use and duration totals stay slightly high because it only adds.
