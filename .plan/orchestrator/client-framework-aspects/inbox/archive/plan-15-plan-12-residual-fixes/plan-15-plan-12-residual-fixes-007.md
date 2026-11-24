envelope_version=1
sender_type=plan
sender_id=plan-15-plan-12-residual-fixes
epic=client-framework-aspects
kind=landing
created=2026-11-24T11:13:34Z

## What landed

plan-15-plan-12-residual-fixes shipped as #954 (merged through the merge queue as 0545cffd). Documentation-only: 16 files, no requirement-id, behaviour or test change.

```landing-facts
schema=landing-facts/1
plan_id=plan-15-plan-12-residual-fixes
epic=client-framework-aspects
pr=#954
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=19824494
total_wall_seconds=14174.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2133
executor_version=0.1.2133
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=unknown
step.functional-review.deliverables_met=4
step.functional-review.deliverables_met_with_residuals=2
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=1
step.code-review.rules_cited=1
```

## Residue

- **Unmeasured keys (recorded as `unknown`, not guessed):** `unresolved_review_threads`, `review_body_findings_total` and `review_body_findings_handled` are published by no lifecycle step. `effort` was never stamped on this plan. `sonar_open_new_issues` is `unknown` because the lifecycle `sonar-roundtrip` read counts issues only: it returned 0 new-code issues (count_status confirmed) but did not read security hotspots, which this key includes.
- **Review state at merge, as observed (not measured by the merge gate):** the pending pr-comment finding count was 0 and CodeRabbit and Sourcery had both participated before the enqueue. One pre-merge loop-back round was spent because CodeRabbit edited its walkthrough comment in place after the PR-body update, which reopened one finding.
- **Open follow-ups are in inbox message `plan-15-plan-12-residual-fixes-001`:** 4 declined review comments that sit outside the plan's surface, and 3 code-side follow-ups. Two more documentation gaps are listed in the PR description: ADR-0010 lines 64-66 state the DPoP nonce retry without its conditions, and `doc/validation/architecture.adoc:262` still says `maxPayloadSize` bounds token parts only.
- **Behaviour change declined:** a reviewer asked that an AccessTokenCache hit re-verify the signature. That is a code change, outside this documentation-only plan, and is left as a follow-up.
- **CI:** the first CI run failed on a transient Maven Central HTTP 429 in `build / quarkus-alignment` (confirmed from the job log) and passed on re-run. At merge time the post-merge Performance Benchmark job (JMH) was still running and is not a required check; the post-merge CI wait timed out on it alone after 757 s, with every other check green.
- **Environment:** the installed plugin is 0.1.2184 while this plan was stamped 0.1.2133 and `marshal.json` is stale against it (run `/marshall-steward`). `session_id` was captured late rather than at init. No Asciidoctor render was run for the new `token-type-validation-rfc-9068` anchor link.
- **Not yet run when this was written:** `archive-plan`, so it is absent from `steps`.
