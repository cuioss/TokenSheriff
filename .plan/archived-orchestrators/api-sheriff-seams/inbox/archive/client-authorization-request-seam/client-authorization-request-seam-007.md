envelope_version=1
sender_type=plan
sender_id=client-authorization-request-seam
epic=api-sheriff-seams
kind=landing
created=2026-11-13T13:59:47Z

## What landed

client-authorization-request-seam shipped as #901 (merged by the platform merge queue as a60a9a48).

```landing-facts
schema=landing-facts/1
plan_id=client-authorization-request-seam
epic=api-sheriff-seams
pr=#901
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=42298314
total_wall_seconds=123375.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2106
executor_version=0.1.2106
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=0
step.functional-review.deliverables_met=5
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=9
step.code-review.rules_cited=8
```

## Residue

- The unresolved-thread and review-body counts are `unknown`: this plan's manifest carries no `review-gate` step, so no run read them. CodeRabbit's walkthrough comment was accepted; its one inline thread (validateScopes character set) was fixed by TASK-14 and the thread was replied to and resolved on GitHub.
- The branch was rebased onto origin/main and force-pushed once (operator-authorized at the conflict gate: overlap without a content conflict under the `no_overlap_only` threshold) and was then merged through the queue. The final `branch-cleanup` record therefore reads "rebase deferred to queue", which describes the last firing only.
- Nine code-review advisories remain unaddressed by design (all non-blocking): the `requirements.adoc` security-exclusions table still lists codes-in-query as excluded while CLIENT-27 adds the opt-in `response_mode=query`; the DEFAULTS Javadoc identity claim versus a subclass overriding the 3-argument `build`; a duplicated redirect-URI guard and an unreachable endpoint lookup in `authorizePushed`; missing `@since` on the new refresh overload and on the now-public `validateScopes`; history-framed wording in `retrieval-flow.adoc` and tests; raw U+00A0 characters in two test literals; narrow assertions on the rejection message class; an unasserted `LogSanitizer` output in `WireValues`.
- The post-merge CI wait on the merged PR timed out with checks still pending and was treated as warn-and-continue.
- The build-maven reactor-root canonical fix (module-tests/test-compile resolve to `package` when a module consumes a sibling test-jar) exists only as a local patch in the operator's plugin cache; it is lost on the next plugin update (see candidate-lesson message 001).
