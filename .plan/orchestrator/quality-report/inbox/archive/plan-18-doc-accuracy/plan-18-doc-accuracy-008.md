envelope_version=1
sender_type=plan
sender_id=plan-18-doc-accuracy
epic=quality-report
kind=landing
created=2026-11-06T18:48:33Z

## What landed

plan-18-doc-accuracy shipped as #877 (merged via the platform merge queue as af19b9c2).

```landing-facts
schema=landing-facts/1
plan_id=plan-18-doc-accuracy
epic=quality-report
pr=#877
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=19870958
total_wall_seconds=17054.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2064
executor_version=0.1.2064
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done
step.functional-review.deliverables_met=5
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=6
step.code-review.rules_cited=4
```

## Residue

- Closes D-2, D-6, D-9, D-13, D-14, V-17d. **D-13 was re-instated**: the spec marked it dropped (already fixed in the benchmarking JFR analysis doc), but the uncaveated Quarkus 3.34.0 figure was still in `token-sheriff-quarkus-parent/doc/performance/jwt-validation-performance.adoc` line 18; it now carries the "version at recording time" caveat. Record D-13 against PR #877, not against `4f8f3eb6`.
- The operator extended scope twice: the `<description>` text of `token-sheriff-client/pom.xml` (D-2/D-14 sibling), and `token-sheriff-validation/.../commons/transport/package-info.java` (Javadoc comment text, found by the 4-plan scope check). Review-comment fixes also touched `DiscoveryResolver.java` Javadoc only.
- Functional review round 1 was closed by operator override over the criterion "D-13 is recorded against this PR in the PR body" (unevidenceable before a PR exists); later rounds evidenced it from the PR body and closed clean.
- Open advisory follow-ups for a later documentation pass (same defect class, not fixed here): `doc/commons/specification/transport.adoc` plain-HTTP wording (~L53-54) and SSRF-defence section (~L127-132); `doc/commons/requirements.adoc` COMMONS-3 host-allow-list scope; `token-sheriff-client/README.adoc` L30-31 two "every discovery and back-channel call" claims; `DiscoveryResolver` class Javadoc L47-48 ("adds no new transport control of its own"); `doc/client/specification/test-strategy.adoc` L129/L234 and `step-up-and-logout.adoc` L112 crediting client tests with the introspection and end-session dispatchers.
- The squash commit message on main carries the CodeRabbit "Summary by CodeRabbit" block and a second Co-authored-by trailer; the merge-queue path offers no per-enqueue message override and `ci repo` has no verb for the default merge-message setting.
- effort is `unknown`: the effort stamp was never written at init. The `emit-landing` and `archive-plan` steps are not in the steps list because they had not yet recorded when this landing was assembled.
- Two usage figures in the phase-6 accumulator were entered as estimates before the real subagent usage arrived (about 1,000 tokens and about 13,000 tokens), so the finalize-phase token total is a slight overstatement.
