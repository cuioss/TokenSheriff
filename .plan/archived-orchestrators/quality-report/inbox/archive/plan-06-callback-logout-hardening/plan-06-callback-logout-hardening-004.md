envelope_version=1
sender_type=plan
sender_id=plan-06-callback-logout-hardening
epic=quality-report
kind=candidate-lesson
created=2026-10-30T19:09:38Z

component=benchmark-core
category=bug
created=2026-10-30

# Flaky 20 ms Awaitility timing test in benchmarking/benchmark-core ejects unrelated PRs from the merge queue

## Observation

PR #840 changed only token-sheriff-client and token-sheriff-validation. The merge queue still ejected it. Work log 18:46:01Z: "PR #840 enqueued but not merged after 433s ... merge_group run 41108666343 completed with failure". The decision log (18:47:29Z) names the cause: an unrelated timing test in benchmark-core, `LibraryMetricsExporterTest`, which uses a 20 ms Awaitility bound. The operator re-enqueued once at the unchanged head aa72e8cb. That run (41109960201) passed, and the PR merged as a56b1b59 after 505s.

## Defect shape

A 20 ms wall-clock bound in a test that runs in the shared merge-group build fails whenever the CI runner is under load. One ejection costs a full merge-queue cycle (about 7-8 minutes plus operator attention) for every PR in the queue, whichever module it touches.

## Suggested direction

- Increase the Awaitility `atMost` in `LibraryMetricsExporterTest` to a value that tolerates a loaded runner (for example seconds rather than milliseconds), and keep the poll interval small, so the test still fails fast on a real defect.
- More generally, no test in a module that runs in the merge-group build should use a sub-100 ms wall-clock upper bound.
