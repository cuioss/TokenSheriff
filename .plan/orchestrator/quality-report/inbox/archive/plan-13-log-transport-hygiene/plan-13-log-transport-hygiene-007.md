envelope_version=1
sender_type=plan
sender_id=plan-13-log-transport-hygiene
epic=quality-report
kind=landing
created=2026-11-05T13:22:48Z

## What landed

plan-13-log-transport-hygiene shipped as #861 (merged, squash via the platform merge queue as 7145aebb).

```landing-facts
schema=landing-facts/1
plan_id=plan-13-log-transport-hygiene
epic=quality-report
pr=#861
merge_state=merged
cleanup_owed=false
deliverables_total=7
deliverables_done=7
total_tokens=34670813
total_wall_seconds=116534.0
harness=claude
model=claude-sonnet-5-5
effort=low
plugin_version=0.1.2046
executor_version=0.1.2046
step.functional-review.deliverables_met=6
step.functional-review.deliverables_met_with_residuals=1
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=9
step.code-review.rules_cited=7
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
```

## Residue

- Findings closed by this landing: V-13, V-14, V-15, V-17a, V-17h, D-3, D-4, plus the PLAN-21 residual (unusable `jwks_uri` retried forever; new log record 172).
- The metrics total is a mixed-population figure: execute-phase subagent usage was accumulated into the 6-finalize accumulator and one zero-usage dispatch-boundary row was recorded before real usage arrived, so the per-phase token split is skewed; `model` is self-reported (`refresh-runtime-stamp` reported `model_unknown`).
- The first merge-queue enqueue was ejected when the merge-group `e2e-tests / test` job hung to its 30-minute timeout and was cancelled (the same job passed in 185 s on the PR head and on the second enqueue); one `branch-cleanup` loop-back was spent on the re-enqueue. The post-merge CI wait was stopped after 600 s because the only pending item was the post-merge Performance Benchmark workflow.
- Advisory review residuals left open (none blocking): `maxHttpResponseSize` not mentioned in `architecture.adoc` / `developing.adoc`; `ParserConfig` Javadoc on `maxPayloadSize` vs HTTP bodies; an oversized-content test that also passes on a missing `jwks_uri`; some sanitization sites untested; `UNUSABLE_URI_REASON` omits the unsupported-scheme cause; `ADVERTISED_URI_EGRESS_BLOCKED` omits the hostless-URI cause.
- Recommended follow-ups: (1) a plan sweeping remaining unsanitized remote-supplied text in log records (`KeyProcessor`, `JWKSKeyLoader` DUPLICATE_JWKS_KID kid, `JwksParser`) with a guard test; (2) an ADR-0017 amendment recording an unusable advertised `jwks_uri` as a fourth terminal rejection.
- Six `candidate-lesson` messages were sent separately by `lessons-capture`.
