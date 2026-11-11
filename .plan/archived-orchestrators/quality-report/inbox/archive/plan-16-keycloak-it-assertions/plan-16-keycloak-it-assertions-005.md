envelope_version=1
sender_type=plan
sender_id=plan-16-keycloak-it-assertions
epic=quality-report
kind=landing
created=2026-11-06T06:05:48Z

## What landed

plan-16-keycloak-it-assertions shipped as #868 (merged, squash commit 306e605 on main).

```landing-facts
schema=landing-facts/1
plan_id=plan-16-keycloak-it-assertions
epic=quality-report
pr=#868
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=20285892
total_wall_seconds=53212.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2046
executor_version=0.1.2046
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
step.functional-review.deliverables_met=4
step.functional-review.deliverables_met_with_residuals=1
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=1
step.code-review.rules_cited=1
```

## Residue

- **PR number.** `create-pr` recorded `pr_number=866`. That PR was closed after the CodeRabbit quota refusals and replaced by #868, which is the PR that merged. The landing carries #868 because it is the PR the merge state refers to.
- **Production change in a "test-only" plan.** The spec asserted `invalid_client` for a wrong client secret. Keycloak answers 401 `unauthorized_client`. The operator chose to normalise this in the client library, so `token-sheriff-client` gained `ClientAuthenticationFailedException`, raised by `TokenEndpointClient` for a 4xx `invalid_client` or exactly 401 `unauthorized_client`. This is disclosed in the PR body.
- **Draft ADR parked, not landed.** `adr-propose` drafted ADR-0020 (typed failed-client-authentication refusal recognised by an exact-code allow-list). It was NOT included in the PR, because adding it would have cost another full finalize gate cycle. Upstream commit 38aefc0a added ADRs 0020-0026 meanwhile, so the draft must be renumbered to 0027 and filed in a follow-up PR. The draft sits at `.plan/temp/0020-A_failed_client_authentication_is_a_typed_pre-redemption_refusal_recognised_by_an_exact-code_allow-list.adoc` in the main checkout, untracked.
- **Effort not stamped.** `effort` is `unknown` because phase-1-init recorded no effort stamp and the refresh could not measure the model (`model_unknown`, `model_source=self_reported`). The landing is incomplete at that key, by design.
- **CodeRabbit nitpicks.** Two low-value nitpicks (MixUpSpecIT:116, LogoutSpecIT:127) were accepted with a reasoned reply and no code change. Four earlier nitpicks (finding 4d8748) were fixed in 589e08a.
- **CodeRabbit rate window.** CodeRabbit refused on quota twice. `review_rate_window_await=true` was set as a step-params override for this plan only (not a project-wide change), and the run waited about 55 minutes.
- **Evidence caveats.** The pre-push gate reported module-tests DEGRADED. Some build results carried `required_coverage_unknown`. The post-merge "Run JMH Benchmarks" job was still pending when `checks wait --adaptive` timed out, which the doc treats as warn-and-continue.
- **Not in `steps`.** `archive-plan` had not run when this landing was written, so it is not listed.
- **Lessons.** Four `candidate-lesson` messages went to this epic's inbox (`plan-16-keycloak-it-assertions-001` to `-004`).
