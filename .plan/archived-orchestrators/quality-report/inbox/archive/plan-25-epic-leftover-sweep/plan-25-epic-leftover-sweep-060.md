envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=landing
created=2026-11-09T13:29:07Z

## What landed

plan-25-epic-leftover-sweep shipped its validation half as #883 (merged, 470450317ceb6d326669ec96e808fe2eec957fbc). The client half and all documentation are still owed as a follow-up PR, because the full change (165 files) exceeded the 100-file cap of the required review bot.

```landing-facts
schema=landing-facts/1
plan_id=plan-25-epic-leftover-sweep
epic=quality-report
pr=#883
merge_state=merged
cleanup_owed=false
deliverables_total=26
deliverables_done=16
total_tokens=44985861
total_wall_seconds=197430.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2064
executor_version=0.1.2064
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:skipped,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:pending
```

## Residue

- **Part 2 of 2 is owed and not yet a PR.** CodeRabbit (required bot) refused the 163-file head ("Too many files", cap 100); the operator chose to split. #883 carries 74 files (validation module, validation-quarkus, BearerAuthSpecIT and its fixture, the e2e stop script, AGENTS.md). Part 2 carries the other 91 paths: the client module, the client Quarkus extension, the client integration tests, all of `doc/` (ADR-0027, ADR-0028, ADR-0010/0017 amendments, the corrected validation, commons and client documents, `test-impl-mapping.adoc`) and the quarkus-parent README. Its source is the local branch `plan-25-full-state` (commit 941b275e, the full branch state before the split); Part 2 builds on #883 because the client depends on the validation module. The ten deliverables 10, 11, 12, 13, 14, 17, 19, 21, 22 and 23 are graded `superseded` in the functional review for that reason, so `deliverables_done=16` counts only what #883 delivered.
- **Operator decisions differ from the mailbox message.** The inbox message `orchestrator-quality-report-001` (2026-11-07) recorded four operator decisions for this plan (keep the 8 KiB JWE parser limit, treat explicit JSON null identifier claims as absent, add no client egress policy, 500-line test guideline). The operator answered the same questions in the run with the opposite choices (widen the JWE limit to 32 KiB, refuse null identifier claims, add a client egress policy, make the 400-line test limit a rule); the run followed the in-run answers. Reconcile the epic record.
- **CodeRabbit review of #883:** 22 inline comments plus two summaries, all answered and resolved; two fix tasks (TASK-42, TASK-43) and five inline edits landed in #883. One design question was accepted as-is and is left to the epic: the DPoP replay cache never evicts an in-window jti, so it can exceed its configured capacity under a flood of fresh jtis (replay-safe but memory-unbounded; CodeRabbit would have it fail closed at capacity).
- **Review gates were closed as operator overrides**, on four accepted test-class splits (`weakened_test` rows), the unevidenced D16.C1 (e2e hang cause never named; the stop script was hardened and CI verified it) and D26.C1.
- **Owed after the merge:** two consecutive green `e2e-tests` runs on `main`, and a branch-scoped `resolved=false` Sonar read of `main`. The merge queue ejected the PR once on a Playwright screenshot flake (one accessibility spec) and a re-dispatch of the enqueue while queued cancelled the in-flight run; the third enqueue landed.
