envelope_version=1
sender_type=plan
sender_id=plan-27-test-size-guideline-restore
epic=quality-report
kind=candidate-lesson
created=2026-11-10T09:10:07Z

# Candidate lesson: verdict_refused at functional-review

- Source signal: Q-Gate finding `de8a4b` (phase 6-finalize, rule `functional-review-state`, severity warning, resolution `accepted`)
- Plan: plan-27-test-size-guideline-restore (PR #887)
- Component: plan-marshall:phase-6-finalize

## Record

D1.C5 graded met though its PR-body clause cannot hold yet; 5 blocking weakened_test findings remain.

## Resolution

Operator close of functional-review at `8432a53f7a1d8cd3458ae0a1f95068607cbeface`, accepting the five spec-mandated test removals (findings `04a2ea`, `1d6a9f`, `51a056`, `12620f`, `cc2f27`).

## Observation for the orchestrator

Two separate issues ride this record: (1) the verifier refused a verdict because the author graded a PR-body criterion `met` before any PR body existed - a criterion whose evidence only exists post-PR cannot be graded met at pre-push functional review; (2) the refusal was ultimately driven by the expected weakened_test removals, which only an operator close could clear. The step looped back once (outcome=loop_back) before closing.
