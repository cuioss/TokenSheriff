envelope_version=1
sender_type=plan
sender_id=plan-21-residual-behaviour-fixes
epic=quality-report
kind=landing
created=2026-11-02T10:31:17Z

## What landed

plan-21-residual-behaviour-fixes shipped as #849 (merged, squash via the merge queue, c308f7c0).

```landing-facts
schema=landing-facts/1
plan_id=plan-21-residual-behaviour-fixes
epic=quality-report
pr=#849
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=24239491
total_wall_seconds=51824.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2046
executor_version=0.1.2046
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:pending
step.functional-review.deliverables_met=5
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=2
step.code-review.rules_cited=2
```

## Residue

- The pre-push gate recorded `test-compile` and the separate `module-tests` run as UN-GATED: the Maven reactor `test-compile` cannot resolve the `token-sheriff-validation` generators test-jar (built at package), so it fails independently of this change. The whole-project `verify -Ppre-commit` ran 3476 tests green at the merged head. Operator chose to record this rather than stop.
- The 120 `build-error` findings that failed attempt filed were resolved `accepted` with that reason; they are one event, not 120.
- The final lesson-shaped output of this run (7 `candidate-lesson` messages) is already in this epic's inbox.
- Two advisory code-review points were left open by decision (JWE test row cannot fail for the message leak; `applyRefresh` Javadoc does not say a compare-and-set miss orphans the rotated refresh token). Earlier advisories accepted: AccessTokenCache Javadoc wording, TokenValidator.close dropping the first of two failures, HttpJwksLoader retrying an unusable jwks_uri, DecodeMode gap on size/structure rejections.
- No ADR was recorded for the applyRefresh compare-and-set decision (operator declined); ADR-0017 could be amended to say retry-pending applies only when a recovering refresh is scheduled.
- PLAN-21 was specified to depend on PLAN-20 (same file, `TokenLifecycleManager.java`); that ordering was not verified by this run.
- Post-merge `Run JMH Benchmarks` workflow was still in progress when the post-merge wait reached its deadline; every merge-gating check had passed.
