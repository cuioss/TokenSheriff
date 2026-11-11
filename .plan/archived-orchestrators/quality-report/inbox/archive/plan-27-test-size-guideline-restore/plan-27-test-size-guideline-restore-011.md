envelope_version=1
sender_type=plan
sender_id=plan-27-test-size-guideline-restore
epic=quality-report
kind=candidate-lesson
created=2026-11-10T09:10:21Z

# Candidate lesson: code-review weakened_test at TokenBuilderExplicitNullTest.java

- Source signal: Q-Gate finding `147e05` (phase 6-finalize, rule `code-review`, severity error, resolution `accepted`)
- Plan: plan-27-test-size-guideline-restore (PR #887)
- Component: plan-marshall:phase-6-finalize

## Record

weakened_test: 7 assertions gone from `shouldRefuseBodyOfOnlyNullJti`, `shouldRefuseExplicitNullCustomClaimWithStrictMapper` [fix_kind correction].

## Resolution

Operator accepted (same removal as functional-review finding `cc2f27` at this head).

## Observation for the orchestrator

Duplicate across functional-review and code-review; see candidate for `1677df`.
