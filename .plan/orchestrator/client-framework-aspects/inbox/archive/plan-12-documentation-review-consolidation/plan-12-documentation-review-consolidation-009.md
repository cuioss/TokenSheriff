envelope_version=1
sender_type=plan
sender_id=plan-12-documentation-review-consolidation
epic=client-framework-aspects
kind=landing
created=2026-11-23T20:31:01Z

## What landed

plan-12-documentation-review-consolidation shipped as #952 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-12-documentation-review-consolidation
epic=client-framework-aspects
pr=#952
merge_state=merged
cleanup_owed=false
deliverables_total=12
deliverables_done=12
total_tokens=31499996
total_wall_seconds=26708.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2133
executor_version=0.1.2133
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=unknown
step.functional-review.deliverables_met=7
step.functional-review.deliverables_met_with_residuals=5
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=4
step.code-review.rules_cited=3
```

## Residue

- **Review and Sonar counts are gaps, not zeros.** The lifecycle records none of `unresolved_review_threads`, `review_body_findings_total` or `review_body_findings_handled`, and its Sonar step counts issues without hotspots, so those four keys are `unknown`. For the record: the Sonar step confirmed 0 new-code issues at the merged head (hotspots not read), and CodeRabbit's one inline thread was replied to and resolved on the provider.
- **CodeRabbit review-body comments mostly untriaged.** One CodeRabbit review body carried 23 minor comments; the ingest length cap clamped it, so only about four were triaged and fixed. The rest were never individually triaged or answered, yet the pre-merge barrier reported zero pending findings.
- **Known advisory leftovers shipped in the merged PR** (listed in its body, not fixed): `benchmarking/benchmarking-common/README.adoc` history placement and a non-existent `BenchmarkConfiguration.defaults()` example; a troubleshooting row in `benchmarking/benchmark-integration-wrk/README.adoc` whose symptom and cause cannot co-occur; `benchmarking/doc/local-testing.adoc` stop command without its working directory.
- **Follow-up candidate outside this plan's footprint:** the `AccessTokenCache` class Javadoc still over-claims that a cache hit is equivalent to a fresh validation; a hit does not re-verify the signature, so a token whose key left the JWKS sets stays accepted until it expires or is evicted.
- **Two comment-only Java edits** retargeted citations of deleted ADRs (`TokenLifecycleManager.java`, `DefaultBeanReplacementTest.java`); the second was outside the declared file set and flagged as an advisory by the functional review.
- **Post-merge CI:** the Performance Benchmark workflow (Run JMH Benchmarks, run 45257159120) was still running when the post-merge wait reached its deadline; nothing had failed.
