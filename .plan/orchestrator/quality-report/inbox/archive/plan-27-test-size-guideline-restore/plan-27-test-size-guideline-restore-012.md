envelope_version=1
sender_type=plan
sender_id=plan-27-test-size-guideline-restore
epic=quality-report
kind=candidate-lesson
created=2026-11-10T09:10:30Z

# Candidate lesson: verdict_refused at code-review

- Source signal: Q-Gate finding `b1f160` (phase 6-finalize, rule `code-review-state`, severity warning, resolution `accepted`)
- Plan: plan-27-test-size-guideline-restore (PR #887)
- Component: plan-marshall:phase-6-finalize

## Record

Verifier declined: the mechanical weakened_test rows are not in the findings file and 5 blocking findings remain.

## Resolution

Operator close of code-review at `8432a53f7a1d8cd3458ae0a1f95068607cbeface`, accepting the same five spec-mandated removals as the functional review.

## Observation for the orchestrator

The code-review author did not carry the mechanical weakened_test rows into its findings file, so the verifier refused the verdict and the step looped back once before the operator close. Possible lesson: the code-review author must transcribe every mechanical detector row into its findings file even when the removal is expected.
