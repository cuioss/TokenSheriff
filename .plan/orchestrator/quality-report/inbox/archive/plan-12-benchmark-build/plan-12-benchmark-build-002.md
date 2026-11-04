envelope_version=1
sender_type=plan
sender_id=plan-12-benchmark-build
epic=quality-report
kind=candidate-lesson
created=2026-11-01T17:31:15Z

# A code-review "correction" finding has no applier when the review step is mutates_source:false

Signal: Q-Gate finding 2d077f (6-finalize, code-review, fix_kind correction).

## What happened

Code review flagged `CoreJfrBenchmark.java:91`: once benchmark state is shared across threads, the JFR metadata is recorded for a different token than the one validated. The finding carried `fix_kind correction`, which loops back for an in-place correction. But the code-review finalize step declares `mutates_source: false`, and no step owns applying a `correction` from it. The loop-back target had no consumer. The finding was finally closed as `taken_into_account` ("verifier accepted findings with no blocking finding") roughly 20 hours after it was raised. The defect was not fixed in the merged change.

Related: functional-review findings 133f01 and 52791c (no failing pre-fix run recorded for BenchmarkStateScopeTest, because the test was added after the production change) were closed the same way: accepted as non-blocking, not evidenced.

## Suggested direction

- Route every `fix_kind` a review step can emit to a step that applies it: either a fix task (as in 5-execute) or an explicit operator escalation. Never a loop-back target that nothing reads.
- Make "accepted with no blocking finding" closure of an error-severity correction visible in the landing, so it is not silently treated as resolved.

## Component

plan-marshall:phase-6-finalize (code-review / functional-review loop-back routing)
