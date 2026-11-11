envelope_version=1
sender_type=plan
sender_id=plan-20-rewrite-marker-cleanup
epic=quality-report
kind=landing
created=2026-11-01T19:36:41Z

## What landed

plan-20-rewrite-marker-cleanup shipped as #847 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-20-rewrite-marker-cleanup
epic=quality-report
pr=#847
merge_state=merged
cleanup_owed=false
deliverables_total=2
deliverables_done=2
total_tokens=16668570
total_wall_seconds=91245.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2027
executor_version=0.1.2027
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done
step.functional-review.deliverables_met=2
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=2
step.code-review.rules_cited=2
```

## Residue

- Spec result: the OpenRewrite marker in `TokenLifecycleManager.holdsNewerGrant` was answered with the sanctioned `// cui-rewrite:disable InvalidExceptionUsageRecipe` suppression (SPI carve-out, arm b); the `-Ppre-commit` run did not re-inject a marker. Sonar showed 0 open issues on the PR at both pushed heads (11e83782 and aa9d64f2). The merge landed through the merge queue as 5349a3bc.
- Two open code-review advisories on `AGENTS.md` (not fixed in this plan): (1) the carve-out sentence's clause "or an equivalent boundary whose thrower declares nothing to narrow against" is wider than the verified sites and conflicts with `TokenValidatorProducer`'s own comment that its catch is not covered by the carve-out; (2) the placement paragraph omits the two live non-catch-position markers in `RefreshConcurrentRedemptionSpecIT` (method level) and `RefreshProductionPathSpecIT` (class level), so "class-level and method-level suppression were never tested" is imprecise.
- Wall clock (25h20m) is dominated by waiting: CodeRabbit's free plan allows one included review per hour, so it refused twice with quota notices; plus a docs-only follow-up commit after the pre-push gate re-ran every head-dependent finalize step.
- Tooling drift: the installed plan-marshall executor is 0.1.2027 while the skill docs are 0.1.2046. `review_completeness roster`, `pre_merge_gate record-merge`, `manage-solution-outline get-deliverable --number` and the `generate_trigger` selector result are missing or differ, so branch-cleanup was recorded with `mark-step-done` and the same facts. Regenerating the executor (marshall-steward) is the likely fix.
- Operator rulings made during the run: merge via the merge queue was explicitly authorized (overriding the CLAUDE.md no-auto-merge default for this PR); `review_rate_window_await` was switched on for this plan after CodeRabbit's first quota refusal; the AGENTS.md wording fix for CodeRabbit's inline comment was pushed and the whole head-dependent chain re-run on the operator's choice.
- Quality note: the functional review's closing round for the second head skipped the separate delta round and ran the full round directly; the code-review author was dispatched rather than run inline (21 rule-source skills to load).
