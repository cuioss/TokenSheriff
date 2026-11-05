envelope_version=1
sender_type=plan
sender_id=plan-10-dpop-binding-stepup
epic=quality-report
kind=landing
created=2026-11-04T20:54:43Z

## What landed

plan-10-dpop-binding-stepup shipped as #856 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-10-dpop-binding-stepup
epic=quality-report
pr=#856
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=23092820
total_wall_seconds=23793.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2046
executor_version=0.1.2046
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
step.functional-review.deliverables_met=4
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=1
step.code-review.rules_cited=1
```

## Residue

- Merged past an unreviewed head: CodeRabbit (required reviewer) was quota-limited on the final commit 310669e, so branch-cleanup merged under a barrier-ask-override (gap class review-barrier-gap, unproven bots coderabbit and sourcery). The operator's condition was that CodeRabbit had reviewed the PR at least once; it had, twice on 25e892e (its findings were fixed in 310669e) and with empty-body reviews on 310669e.
- Gate commit 25e892e (comment-only OpenRewrite suppression marker) post-dated the functional and code reviews.
- Deferred advisories: the step-up spec text (doc/client/specification/step-up-and-logout.adoc) and the StepUpHandler.verifyResult javadoc do not yet mention that a negative max_age is rejected; commit 3f1ab1b's message overstates CLIENT-11 coverage; one ADR candidate was proposed but not created (adr-propose: "1 candidate awaits confirmation"); very deep JSON nesting inside the 8 KiB bound may still overflow a constrained stack (pre-existing, not addressed here).
- Post-merge CI wait on the PR head hit its deadline with only the non-gating "Run JMH Benchmarks" job still running; all other checks green, and the merge-group run on the merge commit 02c0fd3a succeeded.
- effort could not be read from the runtime stamp and is recorded as unknown; the model refresh found model_unknown, so the self-reported model was kept.
- total_tokens spans mixed populations (totals_tokens_population=mixed); phase-boundary token figures for 5-execute were likely over-counted by about 125k because interim usage figures were recorded before final totals.
- archive-plan runs after this landing and is therefore not listed in steps.
