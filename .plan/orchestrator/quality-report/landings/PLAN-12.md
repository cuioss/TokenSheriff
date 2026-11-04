# Landing Analysis: PLAN-12 — Benchmark validity, BOM scope and a flaky timing bound

epic: quality-report
workstream: WS-04
pr: #846 (https://github.com/cuioss/TokenSheriff/pull/846)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan) after
> corroborating the landing message `plan-12-benchmark-build-006.md` against the merged PR and the
> merge commit. Corroborated by this analysis: PR state `merged`, merge commit `17a3ef62`, the PR
> title/body, the merged file footprint (`git show --name-only 17a3ef62`), the Sonar quality gate and a
> direct unresolved-issue query. NOT corroborated: code-level behaviour (no build or test was run here) and
> every figure that comes only from the landing message (tokens, wall time, review-step counts).

## Deliverable Fidelity vs Spec

Evidence is file-level (the merged footprint and the PR body), not a re-read of the semantics.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Q-10 shared-validator state for the concurrency benchmarks | shipped-as-specified (convert arm), wider than listed | PR body "Q-10"; five benchmark classes moved to `@State(Scope.Benchmark)`: `SimpleCoreValidationBenchmark`, `SimpleErrorLoadBenchmark` (declared) and `CoreJfrBenchmark`, `ErrorJfrBenchmark`, `MixedJfrBenchmark` (not declared). Javadoc corrected. New `BenchmarkStateScopeTest`; the PR reports it fails 5 of 5 on the pre-change sources |
| Q-11 step metrics no longer last-writer-wins | shipped-modified | PR body "Q-11"; the spec offered "merge in `LibraryMetricsExporter` or export from shared state". The PR took the second arm: `AbstractBenchmark` and `AbstractJfrBenchmark` export once per trial under a per-method key (`Class.method`) from the shared state. `LibraryMetricsExporter.java` is untouched (stated non-goal); `LibraryMetricsExporterTest` gained a multi-thread aggregation test and a key-isolation test |
| Q-13 stale class and package names | shipped-as-specified | PR body "Q-13"; `SimpleCoreValidationBenchmark`, `JmhBenchmarkConverterTest`, `TestDataFactory` changed |
| Q-7 drop `<scope>test</scope>` from the BOM's `jjwt-*` entries | shipped-as-specified | PR body "Q-7"; `bom/pom.xml` changed. The PR states every consuming module declares its own scope; no consumer POM is in the footprint, so the HYPOTHESIS paths were not needed |
| Flaky timing bound (added to the spec by the operator) | shipped-as-specified (synthetic-duration arm) | PR body "Flaky bound"; the sub-100 ms `await().atMost(...)` waits in `LibraryMetricsExporterTest` are replaced by fixed synthetic durations |
| Added, unplanned | added-unplanned | `MockTokenRepository.java` and its test, `AbstractJfrBenchmark.java`, `CoreValidationDelegate.java`, the two other JFR benchmarks, `AbstractBenchmarkTest.java`. Code review found `CoreJfrBenchmark` recording JFR metadata for a different token than the one validated once state became shared; `CoreValidationDelegate` and `CoreJfrBenchmark` now select the token once and validate that same token (the sender corrected its own earlier statement that this was unfixed, message `plan-12-benchmark-build-003`) |
| Declared but not touched | n/a | `LibraryMetricsExporter.java` (deliberately), and the HYPOTHESIS consumer POMs `token-sheriff-validation/pom.xml`, `token-sheriff-client/pom.xml` |

Surface delta: 16 merged paths against 10 declared. Seven declared paths were touched; three were not
(`LibraryMetricsExporter.java` and the two HYPOTHESIS consumer POMs). Nine merged paths were not declared.

## Metrics and Anomalies

- Tokens: 19,496,272 (landing message; not independently verifiable here).
- Duration: 83,910 s wall (landing message), including a pause of about 21 hours from a weekly usage limit.
- Effort: `unknown` — never stamped, so the landing is incomplete at that key (see Open Defects in `epic.md`). Fifth of the nine landings so far without it (PLAN-01, 02, 04, 05, 12).
- Anomalies:
  - Three review loop-backs: functional review (no red-before-green evidence for `BenchmarkStateScopeTest`, closed by TASK-008), code review (`CoreJfrBenchmark` token mismatch, TASK-009) and three CodeRabbit follow-ups (TASK-010).
  - The pre-push gate was satisfied by the whole-tree `verify -Ppre-commit` on the committed head (3443 tests) instead of the separate per-bundle arms; disclosed as an orchestrator deviation.
  - Two logged slips: a stray head stamp on `finalize-step-sync-baseline` made `pre_merge_gate` refuse once, and a phase boundary was stamped one call early. The first TASK-5 commit lacks the Claude trailer.
  - The installed executor is 0.1.2027 while the workflow docs read were 0.1.2040: `pre_merge_gate consequence-text` / `record-merge` and `review_completeness roster` do not exist, so `branch-cleanup` was recorded with `mark-step-done`.

## Routing and Merge Behavior

- Review: merged under the operator's "Accept the coverage gap (record reason)" ruling at head `145f838c`. CodeRabbit (required) had reviewed the main change, its three follow-ups were fixed, and it was quota-limited for the last two fix commits `1a6aa8f8` and `145f838c`. Landing message; not independently verified.
- Code review: 0 blocking, 3 advisory, 2 rules cited (landing message). One advisory is named in the PR body: the rotation-counter wraparound in the token pool is left as is.
- CI/merge: merged through the merge queue as `17a3ef62` (read from the PR: `state: merged`).
- Sonar: the landing carries no `step.sonar-roundtrip.*` keys, but it says 0 new-code issues. Read here: quality gate `OK` on PR 846 and a direct `resolved=false` query returned 0 open issues.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-12 --status shipped`
- [x] row `pr` stamped — value 846
- [x] row `landing` stamped — value `landings/PLAN-12.md`
- [x] row `plan_marshall_plan_id` stamped — value `plan-12-benchmark-build`
- [x] `findings-disposition.md`: Q-7, Q-10, Q-11, Q-13 → FIXED (per PR, code not re-read)
- [x] epic.md narrative reconciled against the queue rows
- [x] Open Defect for the incomplete landing (`effort`); Watches opened
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- `corpus declaration-currency` against the merged footprint: no staged spec declares a path this plan changed.
- Advisory not fixed: the token pool's rotation counter can wrap around (PR body). Under the fix-immediately policy it was added to PLAN-22 (test hygiene).
- Five messages (001 to 005) were queued; all name `plan-marshall:*` components: executor older than the docs, no applier for a code-review correction finding when the review step cannot mutate source, a pre-commit formatter leaving the tree dirty after the green run, the CodeRabbit quota, and message 003, which corrects a statement in 002. They were dispositioned as recurrences or addenda, see `epic.md`.
