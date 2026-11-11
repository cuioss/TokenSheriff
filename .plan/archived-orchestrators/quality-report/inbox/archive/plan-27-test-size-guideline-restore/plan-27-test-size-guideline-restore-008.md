envelope_version=1
sender_type=plan
sender_id=plan-27-test-size-guideline-restore
epic=quality-report
kind=candidate-lesson
created=2026-11-10T09:10:17Z

# Candidate lesson: code-review weakened_test at StrictStringMapperTest.java

- Source signal: Q-Gate finding `bb4555` (phase 6-finalize, rule `code-review`, severity error, resolution `accepted`)
- Plan: plan-27-test-size-guideline-restore (PR #887)
- Component: plan-marshall:phase-6-finalize

## Record

weakened_test: 4 assertions gone from `shouldRejectJsonNullClaim`, `shouldReturnNullForAbsentClaimBesideExplicitNull` [fix_kind correction].

## Resolution

Operator accepted (same removal as functional-review finding `1d6a9f` at this head).

## Observation for the orchestrator

Duplicate across functional-review and code-review; see candidate for `1677df`.
