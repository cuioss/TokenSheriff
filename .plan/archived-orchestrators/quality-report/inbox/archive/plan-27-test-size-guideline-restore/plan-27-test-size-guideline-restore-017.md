envelope_version=1
sender_type=plan
sender_id=plan-27-test-size-guideline-restore
epic=quality-report
kind=landing
created=2026-11-10T09:12:20Z

## What landed

plan-27-test-size-guideline-restore shipped as #887 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-27-test-size-guideline-restore
epic=quality-report
pr=#887
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=12922347
total_wall_seconds=42276.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2090
executor_version=0.1.2090
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=0
```

## Residue

- The merge landed under a recorded ruling (`barrier-ask-override`, review-barrier-gap) for the last commit `abef336`: CodeRabbit (rate-limited) and Sourcery (quota) had not reviewed it. The code was reviewed by CodeRabbit on `757856a`; `abef336` only changes `CLAUDE.md` and one `security-reference.adoc` paragraph. CI was fully green.
- The five PLAN-25 explicit-null refusal tests were removed on purpose (the spec reverts that behaviour). The finalize test-integrity check flagged them as weakened tests in both reviews, at two heads, and could not store the accepted test names, so the operator had to accept the same five removals each time. Candidate for the epic: let a spec declare test removals the review can read.
- `jti`, `azp` and `client_id` are optional claims, so a `null` for them is now accepted exactly like an omitted member (deliberate, stated in the PR body). `iss` and `sub` stay refused when null.
- Review and Sonar counts could not be read as recorded facts (no finalize step publishes them), so three of the four are `unknown`.
- A reactor-wide `mvnw test-compile` or `mvnw test` fails identically on `main` (the validation `generators` test jar is only built in the package phase). The pre-push gate's compile-only and test arms were therefore not run; whole-tree `verify -Ppre-commit` (4149 tests) was green. Noted in `CLAUDE.md`. Candidate fix outside this plan: point the gate's test-compile canonical at `package -DskipTests`.
- `AGENTS.md` says the largest test class has 1024 lines; its actual count is 1023. The "30 classes above 500 lines" figure was not independently re-counted.
- `MapRepresentation` is byte-identical to `2eceb09e` as required, so an explicit null inside a nested object is still kept; the documented "null reads as absent" guarantee holds for top-level claim members only.
- The post-merge CI wait timed out on the still-running post-merge benchmark job; every PR check and the merge-queue run were green.
