envelope_version=1
sender_type=plan
sender_id=plan-27-test-size-guideline-restore
epic=quality-report
kind=candidate-lesson
created=2026-11-10T09:10:02Z

# Candidate lesson: weakened_test at MapRepresentationTest.java (functional-review)

- Source signal: Q-Gate finding `51a056` (phase 6-finalize, rule `functional-review`, severity error, resolution `accepted`)
- Plan: plan-27-test-size-guideline-restore (PR #887)
- Component: plan-marshall:phase-6-finalize

## Record

12 assertions gone from `shouldHaveNoExplicitNullsWhenConstructedDirectly`, `shouldKeepNestedExplicitNulls`, `shouldRetainParsedExplicitNull`, `shouldTreatOnlyNullBodyAsEmpty` [fix_kind correction].

## Resolution

Operator accepted: the PLAN-27 spec reverts the PLAN-25 explicit-null retention/refusal behaviour (explicit null is again read as an absent member) and replaces these tests with `IdentifierClaimNullEquivalenceTest`.

## Observation for the orchestrator

Same class as finding `04a2ea`: a spec-mandated behaviour reversal tripped the blocking weakened_test gate.
