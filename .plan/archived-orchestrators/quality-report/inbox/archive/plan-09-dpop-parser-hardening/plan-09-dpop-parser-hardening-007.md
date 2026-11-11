envelope_version=1
sender_type=plan
sender_id=plan-09-dpop-parser-hardening
epic=quality-report
kind=landing
created=2026-11-04T12:35:27Z

## What landed

plan-09-dpop-parser-hardening shipped as #851 (merged via the merge queue as 49ba673d).

```landing-facts
schema=landing-facts/1
plan_id=plan-09-dpop-parser-hardening
epic=quality-report
pr=#851
merge_state=merged
cleanup_owed=false
deliverables_total=7
deliverables_done=7
total_tokens=37464467
total_wall_seconds=346688.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2046
executor_version=0.1.2046
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done
step.functional-review.deliverables_met=7
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=1
step.code-review.rules_cited=1
```

## Residue

- Closed findings: V-11, V-12, V-17b, V-17c, V-17e, plus the decode-mode residual (no finding ID).
- Real security gain found beyond the spec: a DPoP proof whose JWK `alg` and `crv` disagreed was accepted before; it is now rejected (commit ca342f9c, tested).
- A pending 5-execute scope-creep warning (364f9d, six files outside declared scope) blocked the first merge attempt at the pre-merge findings gate; it was resolved as accepted with a per-file rationale and the merge re-ran once (branch-cleanup loop-back 1 of 5).
- Advisory, not fixed: the non-EC-key and null-domain-parameters guards in SignatureVerificationUtil.checkEcdsaComponentRange are shadowed by the earlier initVerify call and no test reaches them; the token-path PsychicSignatureAttackTest cannot fail if the explicit range check is removed because the JDK rejects all-zero signatures itself.
- Degraded gate arms: no standalone module-tests canonical exists, and a standalone whole-tree test-compile is not valid in this reactor (the validation test-jar is built only at package); test compilation was covered by the whole-tree verify (3592 tests).
- The merge-queue squash commit message is platform-composed; the repository merge-commit-message default was not changed by this plan.
- The post-merge "Run JMH Benchmarks" workflow was still running when cleanup finished; the post-merge CI wait hit its deadline on it. All 23 PR checks were green.
- Metrics: two dispatch-boundary rows (5-execute and lessons-capture) carry placeholder figures written before the agent usage arrived; each is followed by a corrected row and a decision-log note. The effort stamp was absent, so effort is recorded as unknown.
- Six candidate-lesson messages were sent from lessons-capture (001 to 006).
