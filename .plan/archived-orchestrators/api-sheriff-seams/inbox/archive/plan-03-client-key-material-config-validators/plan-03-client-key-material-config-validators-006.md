envelope_version=1
sender_type=plan
sender_id=plan-03-client-key-material-config-validators
epic=api-sheriff-seams
kind=landing
created=2026-11-17T14:58:24Z

## What landed

plan-03-client-key-material-config-validators shipped as #915 (merged) through the merge queue as dfab40cb.

```landing-facts
schema=landing-facts/1
plan_id=plan-03-client-key-material-config-validators
epic=api-sheriff-seams
pr=#915
merge_state=merged
cleanup_owed=false
deliverables_total=9
deliverables_done=9
total_tokens=22262128
total_wall_seconds=27229.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2133
executor_version=0.1.2133
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=unknown
```

## Residue

- **Review and Sonar counts are `unknown` because no lifecycle step records them as facts.** What the run itself observed, for the epic's information and not as the measured keys: at the merge gate there were 0 pending `pr-comment` findings; CodeRabbit's 4 stored findings (trailing-dot host, final-label rule, a `KeyType` test derivation, and a review body with two nitpicks) were all fixed and replied to; the Sonar round-trip recorded 0 new-code issues (confirmed at the head) but does not count security hotspots, so `sonar_open_new_issues` is left `unknown`.
- **Merge went through on a recorded waiver.** The functional-review re-run at the final head 67672997 was waived by the operator (`head_binding_waived=functional-review`); that commit is a pure move of two private helpers into `PemKeyPairLoader.PemParser`, and the review had passed at f0f7cf41.
- **Operator-accepted deviation.** A single-label `1example` stays admitted by `EgressHostValidator` although CodeRabbit suggested refusing it; dotted names must end in a label that starts with a letter.
- **Optional reviewer.** Sourcery declined (diff over its 150000-character cap, 3521 changed lines); it is configured optional, so it did not block.
- **Post-merge CI.** The only check still running at the post-merge wait deadline was the non-gating `Run JMH Benchmarks` workflow; all gating checks were green.
- **Verification commands.** `manage-tasks` could not edit task verification commands, so the per-task `test-compile` and `test` commands remained; every task also carries the `verify -Ppre-commit` gate.
- **Steps list.** `emit-landing` and `archive-plan` record their own outcome after this message is written, so they are not in `steps`.
