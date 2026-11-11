envelope_version=1
sender_type=plan
sender_id=plan-27-test-size-guideline-restore
epic=quality-report
kind=candidate-lesson
created=2026-11-10T09:10:05Z

# Candidate lesson: weakened_test at TokenBuilderExplicitNullTest.java (functional-review)

- Source signal: Q-Gate finding `cc2f27` (phase 6-finalize, rule `functional-review`, severity error, resolution `accepted`)
- Plan: plan-27-test-size-guideline-restore (PR #887)
- Component: plan-marshall:phase-6-finalize

## Record

7 assertions gone from `shouldRefuseBodyOfOnlyNullJti`, `shouldRefuseExplicitNullCustomClaimWithStrictMapper` [fix_kind correction].

## Resolution

Operator accepted: the PLAN-27 spec reverts the PLAN-25 explicit-null refusal behaviour and replaces these tests with `IdentifierClaimNullEquivalenceTest`.

## Observation for the orchestrator

Same class as finding `04a2ea`: a spec-mandated behaviour reversal tripped the blocking weakened_test gate.
