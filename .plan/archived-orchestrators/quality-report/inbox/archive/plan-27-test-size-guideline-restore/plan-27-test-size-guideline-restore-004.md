envelope_version=1
sender_type=plan
sender_id=plan-27-test-size-guideline-restore
epic=quality-report
kind=candidate-lesson
created=2026-11-10T09:10:04Z

# Candidate lesson: weakened_test at IdentifierClaimNullRejectionTest.java (functional-review)

- Source signal: Q-Gate finding `12620f` (phase 6-finalize, rule `functional-review`, severity error, resolution `accepted`)
- Plan: plan-27-test-size-guideline-restore (PR #887)
- Component: plan-marshall:phase-6-finalize

## Record

15 assertions of the deleted class gone from every file the branch carries (replaced by `IdentifierClaimNullEquivalenceTest`) [fix_kind correction].

## Resolution

Operator accepted: the whole test class pinned the PLAN-25 explicit-null refusal that the PLAN-27 spec reverts; its successor class covers the restored equivalence.

## Observation for the orchestrator

Same class as finding `04a2ea`. Note the detector counts a deleted-and-replaced class as a pure assertion loss; it does not credit the replacement class.
