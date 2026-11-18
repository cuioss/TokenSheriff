envelope_version=1
sender_type=plan
sender_id=plan-11-final-residuals-sweep
epic=api-sheriff-seams
kind=landing
created=2026-11-18T11:40:29Z

## What landed

plan-11-final-residuals-sweep shipped as #921 (merged via the merge queue as d8aba512).

```landing-facts
schema=landing-facts/1
plan_id=plan-11-final-residuals-sweep
epic=api-sheriff-seams
pr=#921
merge_state=merged
cleanup_owed=false
deliverables_total=7
deliverables_done=7
total_tokens=25812570
total_wall_seconds=63184.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2133
executor_version=0.1.2133
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=0
```

## Residue

- Operator overrides: functional-review and code-review were closed by operator override (five `weakened_test` flags are test splits, moves or parameterizations; verifiers confirmed no assertion was lost). automatic-review was closed with an operator skip: CodeRabbit's last review covers f6bb6db9, not the final head 97ddb582.
- Deliverable 7: the Playwright ten-consecutive-run check was NOT executed (the dev environment could not run the branch build; reason recorded in the progress record).
- Advisory finding F1: the ParClient, UserInfoClient and DiscoveryResolver call sites have no malformed Content-Length test of their own.
- `doc/developer/test-impl-mapping.adoc` still lacks rows for some pre-existing client test classes (for example the CachingDiscoveryResolver tests).
- The announcement to PLAN-10 queued in this epic's inbox was never delivered.
- Five candidate-lesson messages (002-006) are in the inbox: weakened_test re-raise on every head advance, commit gate refusal in 6-finalize, the missing `--record-change` flag, `.plan/` evidence rejected as nonexistent, and E2E using the stale `~/.m2` snapshot.
