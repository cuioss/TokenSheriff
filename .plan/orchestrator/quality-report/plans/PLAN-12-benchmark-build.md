# PLAN-12: Benchmark validity and BOM scope

epic: quality-report
workstream: WS-04

## Objective

The "concurrent load" JMH benchmarks use `@State(Scope.Thread)` with 100 threads, so they measure 100
independent validators rather than the shared singleton (Q-10); per-thread trial teardown makes the
exported step metrics last-writer-wins (Q-11); stale class/package names remain (Q-13); and the
published BOM pins `jjwt-*` with `test` scope, leaking a scope into consumers (Q-7). Closes Q-7, Q-10, Q-11, Q-13.
Also removes a flaky wall-clock bound in `LibraryMetricsExporterTest` that failed CI on PLAN-05's branch and
ejected PLAN-06's PR from the merge queue (PLAN-06 candidate lesson 004; operator rule: fix immediately).

## Deliverables

1. **Q-10** — add a `@State(Scope.Benchmark)` shared-validator variant for the concurrency benchmarks
   (or convert the existing ones) and correct the Javadoc claims.
2. **Q-11** — metrics are aggregated across threads (merge in `LibraryMetricsExporter` or export from
   shared state) — no per-thread overwrite; unit test of the merge.
3. **Q-13** — fix the stale references in `SimpleCoreValidationBenchmark`, `JmhBenchmarkConverterTest`,
   `TestDataFactory`.
4. **Q-7** — drop `<scope>test</scope>` from the BOM's `jjwt-*` entries; consuming module POMs declare
   test scope themselves (verify each consumer still builds).
5. **Flaky timing bound** — `LibraryMetricsExporterTest` waits with `await().atMost(50 ms)` and
   `atMost(20 ms)` on `until(() -> true)` only to simulate elapsed time, so a loaded CI runner can exceed the
   bound and fail the test. Remove the sensitivity: either drop the wait and record a fixed synthetic
   duration, or use a bound in seconds with a small poll interval. No sub-100 ms wall-clock upper bound may
   remain in `benchmark-core` tests. No behaviour change to the exporter. Not tied to a finding ID.

## Standing Constraints

- Every claim below was re-grounded at HEAD `aa77acd9`; re-verify each against the implementing
  source at outline before scoping on it (refutation loops back to re-scope). The original report
  text is in `.plan/orchestrator/quality-report/source-report/` (read-only, stale line numbers).
- The PR title/body references every finding ID it closes (e.g. "Fix V-3"). Do NOT edit
  `source-report/` or `findings-disposition.md` — the orchestrator records dispositions.
- Fail-closed posture is non-negotiable: no fix may widen acceptance of a token, a callback or a
  configuration that is rejected today, unless the deliverable says so explicitly.
- Every behavioural fix ships with a test that fails before and passes after (JUnit 5, CUI test
  conventions, generators where variation matters); build and verify via Maven only.
- Log changes follow CUI logging: LogRecord in the module's LogMessages class, and
  `doc/LogMessages.adoc` updated in the same PR for any new/renumbered identifier.
- SonarCloud analyses PRs (`sonar: configured`): an open Sonar issue is fixed in a pushed commit,
  or dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress
  record and transmitted via `plan-marshall:workflow-integration-sonar:sonar_rest transition
  --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is
  a blocker. The merge gate reads the open-issue count at the head — `0` confirmed or every
  remaining issue dismissed; an `undecidable` read blocks.
- Do not run the full benchmark suite in CI; a compile + unit-test build of `benchmarking/` suffices.

## Claim Labels

- OBSERVED: `LibraryMetricsExporterTest` waits with `atMost(50, MILLISECONDS)` and `atMost(20, MILLISECONDS)` on `until(() -> true)`, with `pollDelay` 10 ms and 5 ms — read at `benchmarking/benchmark-core/src/test/java/de/cuioss/sheriff/token/validation/benchmark/LibraryMetricsExporterTest.java` § the two `await()` calls
- OBSERVED: the test failed in CI on PLAN-05's branch and in merge_group run 41108666343, which ejected PR #840 — landing messages PLAN-05 and PLAN-06 (verify-at-outline: re-run the test under load to confirm the bound is the cause)
- OBSERVED: `@State(Scope.Thread)` on `SimpleCoreValidationBenchmark` / `SimpleErrorLoadBenchmark`; `jmh.threads=100` — read at `benchmarking/benchmark-core/src/main/java/de/cuioss/sheriff/token/validation/benchmark/standard/SimpleCoreValidationBenchmark.java`
- OBSERVED: `AbstractBenchmark` creates a `MockTokenRepository` per state; `@TearDown(Level.Trial)` calls `updateAggregatedMetrics` under one key per method — read at `benchmarking/benchmark-core/src/main/java/de/cuioss/sheriff/token/validation/benchmark/base/AbstractBenchmark.java`
- OBSERVED: `bom/pom.xml` declares `jjwt-api`, `jjwt-impl`, `jjwt-jackson` with `<scope>test</scope>` — read at `bom/pom.xml`
- OBSERVED: stale names "PerformanceIndicatorBenchmark", `de.cuioss.sheriff.token.quarkus.benchmark.benchmarks`, `de.cuioss.jwt.benchmark.Test` — read at the three Q-13 files below

## Expected Surface

- OBSERVED: `benchmarking/benchmark-core/src/main/java/de/cuioss/sheriff/token/validation/benchmark/base/AbstractBenchmark.java`
- OBSERVED: `benchmarking/benchmark-core/src/main/java/de/cuioss/sheriff/token/validation/benchmark/LibraryMetricsExporter.java`
- OBSERVED: `benchmarking/benchmark-core/src/test/java/de/cuioss/sheriff/token/validation/benchmark/LibraryMetricsExporterTest.java`
- OBSERVED: `benchmarking/benchmark-core/src/main/java/de/cuioss/sheriff/token/validation/benchmark/standard/SimpleCoreValidationBenchmark.java`
- OBSERVED: `benchmarking/benchmark-core/src/main/java/de/cuioss/sheriff/token/validation/benchmark/standard/SimpleErrorLoadBenchmark.java`
- OBSERVED: `benchmarking/benchmarking-common/src/test/java/de/cuioss/benchmarking/common/converter/JmhBenchmarkConverterTest.java`
- OBSERVED: `benchmarking/benchmarking-common/src/test/java/de/cuioss/benchmarking/common/test/TestDataFactory.java`
- OBSERVED: `bom/pom.xml`
- HYPOTHESIS: consumer POMs `token-sheriff-validation/pom.xml`, `token-sheriff-client/pom.xml` (verify-at-outline — only if they rely on the BOM scope)

## Dependencies and Sequencing

- Depends on: none
- Overlaps with: none
- Adjacent to: `token-sheriff-quarkus-parent/doc/performance/` prose (D-13) — PLAN-18

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-12-benchmark-build.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
