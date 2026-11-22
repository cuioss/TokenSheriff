envelope_version=1
sender_type=plan
sender_id=implement-plan-14-closing-sweep
epic=api-sheriff-seams
kind=landing
created=2026-11-20T15:13:31Z

## What landed

implement-plan-14-closing-sweep shipped as #938 (merged through the merge queue as 57a50058).

```landing-facts
schema=landing-facts/1
plan_id=implement-plan-14-closing-sweep
epic=api-sheriff-seams
pr=#938
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=27563968
total_wall_seconds=76715.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2133
executor_version=0.1.2133
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:pending
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=1
step.functional-review.deliverables_met=2
step.functional-review.deliverables_met_with_residuals=3
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=11
step.code-review.advisory_count=3
step.code-review.rules_cited=3
```

## Residue

- Deliverable 5 (ten Playwright runs per accessibility spec) was NOT run: the start script ran once, Keycloak came up, Quarkus dev mode never became ready (health 404 for 120 s, "ArC container not initialized", TokenSheriff-141: no DslJson reader for `commons.transport.Jwks`); the machine has no Linux npm and the Windows npm refuses a UNC working directory. The flake entry stays open. Whether the dev-mode failure is environmental or a defect of the branch build is unsettled and needs an operator read.
- Both reviews were closed by operator override at each of three heads (c157a844, d21fb040, 132e969d) on the same rows: eleven renamed-test rows the counter reads as lost assertions (names only), the ~20 framing renames landed as three per-module commits instead of one commit each, and the conflict between "rename every hit" and "no changed class over 500 lines" for three classes already over 500. The deliverable-4 criterion "pinned by a stalling-server test" was changed by the operator to the per-JDK probe after the JDK 27 CI leg failed.
- The body-phase request-timeout statements are per JDK as observed (21 and 25 not bounded, 27 bounded); the probe test accepts either outcome.
- Open in Sonar after the merge: java:S8714 (MINOR) in BackChannelHttpBodyPhaseTest (accepted, not suppressed: the probe must accept two outcomes). The sonar count excludes hotspots.
- The three review-and-thread counts are `unknown`: no finalize step records them, and no merge-gate read of them was taken in this run.
- Required reviewer coverage: CodeRabbit's check on the final head 132e969d reported NEUTRAL/skipped (quota); its last review on record covers d21fb040, and the pre-merge barrier read participation as complete. Sourcery posted quota notices. Whether either bot reviewed the final head cannot be told from the bot output.
- Advisories carried: TokenSheriffProcessor Javadoc cites a second boot test for the subpackage case that only the dependency boot test holds; the mapping test repeats four positive controls of BackChannelHttpExchangeTest; RawHttpServer.close() returns before shutting the thread down if closing a socket throws; the size-bound statements say "on every JDK" with no per-JDK run; the probe sends the first part of the body so a stall with no body byte was not run; cui-http source claims are attested only by the corrected text.
- Tool defects hit in this run were sent as six candidate-lesson messages by lessons-capture (that step was marked done with --force by its leaf).
- A post-merge `ci checks wait` ended at its deadline with only a non-gating Performance Benchmark (JMH) workflow still running; every gating check was green.
